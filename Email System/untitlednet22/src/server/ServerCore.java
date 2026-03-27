package server;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ServerCore {

    private int tcpPort;
    private boolean running = false;
    private ServerSocket serverSocket; // مرجع للسوكيت لإغلاقه عند الإيقاف
    private ServerGUI gui; // مرجع للواجهة

    // البيانات
    private Map<String, List<String>> inboxes = new HashMap<>();
    private Map<String, List<String>> outboxes = new HashMap<>();
    private Map<String, List<String>> archives = new HashMap<>();
    private Map<String, String> lastLoginTimes = new HashMap<>();
    private Map<String, String> accounts = new HashMap<>(); // الحسابات

    // الحالة الحية
    private Map<String, String> onlineUsersStatus = Collections.synchronizedMap(new HashMap<>());
    private Map<String, UserSession> userSessions = new HashMap<>();

    private static final String DATA_FILE = "mail_data.dat";

    class UserSession {
        InetAddress ipAddress; int udpPort;
        public UserSession(InetAddress ip, int port) { this.ipAddress = ip; this.udpPort = port; }
    }

    // Constructor معدل يقبل الواجهة
    public ServerCore(int tcpPort, ServerGUI gui) {
        this.tcpPort = tcpPort;
        this.gui = gui;
        loadData();

        // إذا لم يكن هناك حسابات (أول مرة)، ننشئ حسابات افتراضية
        if (accounts.isEmpty()) {
            addUser("alice", "1234");
            addUser("bob", "1234");
            addUser("eve", "1234");
            addUser("david", "1234");
        }
    }

    public void start() {
        running = true;
        try {
            serverSocket = new ServerSocket(tcpPort);
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client)).start();
                } catch (SocketException e) {
                    if (running) gui.log("Socket Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            gui.log("Could not listen on port: " + tcpPort);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }

    // دوال لإدارة المستخدمين من الواجهة
    public boolean addUser(String username, String password) {
        if (accounts.containsKey(username)) return false;
        accounts.put(username, password);
        inboxes.put(username, new ArrayList<>());
        outboxes.put(username, new ArrayList<>());
        archives.put(username, new ArrayList<>());
        lastLoginTimes.put(username, "Never");
        saveData();
        return true;
    }

    public void removeUser(String username) {
        accounts.remove(username);
        inboxes.remove(username);
        // لا نحذف الأرشيف ربما، لكن سنحذفه للتبسيط
        saveData();
    }

    // Getters للواجهة
    public Map<String, String> getAccounts() { return accounts; }
    public Map<String, String> getOnlineStatus() { return onlineUsersStatus; }
    public Map<String, String> getLastLoginTimes() { return lastLoginTimes; }

    private void handleClient(Socket socket) {
        String currentUser = null;
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                // طباعة الأمر في اللوج الخاص بالواجهة
                // gui.log("CMD from " + socket.getInetAddress() + ": " + line);

                if (line.startsWith("HELO")) {
                    out.println("250 READY");
                }
                else if (line.startsWith("AUTH")) {
                    String[] parts = line.split(" ");
                    if(parts.length < 3) { out.println("500 BAD SYNTAX"); continue; }
                    String user = parts[1];
                    String pass = parts[2];

                    if (accounts.containsKey(user) && accounts.get(user).equals(pass)) {
                        currentUser = user;
                        onlineUsersStatus.put(user, "Active");
                        gui.log("Auth success: " + user); // Log to GUI
                        gui.refreshUserTable(); // تحديث الجدول في الواجهة

                        String lastSeen = lastLoginTimes.getOrDefault(user, "First Login");
                        out.println("235 OK | " + lastSeen);

                        lastLoginTimes.put(user, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                        saveData();

                        if (parts.length >= 4) {
                            try {
                                int uPort = Integer.parseInt(parts[3]);
                                userSessions.put(user, new UserSession(socket.getInetAddress(), uPort));
                                gui.log("UDP registered for " + user);
                            } catch (Exception e) {}
                        }
                    } else {
                        out.println("535 AUTH FAILED");
                        gui.log("Auth failed for: " + user);
                    }
                }
                else if (line.startsWith("SETSTAT")) {
                    String newStat = line.substring(8).trim();
                    if (currentUser != null) {
                        onlineUsersStatus.put(currentUser, newStat);
                        gui.refreshUserTable(); // تحديث الجدول عند تغيير الحالة
                        out.println("250 OK");
                    }
                }
                else if (line.startsWith("SEND")) {
                    String msg = line.substring(5);
                    String[] fields = msg.split("\\|", 3);
                    if (fields.length == 3) {
                        String to = fields[0];
                        if (inboxes.containsKey(to)) {
                            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                            String savedMsg = currentUser + "|" + fields[1] + "|" + fields[2] + "|" + time;
                            inboxes.get(to).add(savedMsg);
                            outboxes.get(currentUser).add(to + "|" + fields[1] + "|" + fields[2] + "|" + time);
                            saveData();
                            out.println("250 OK");
                            sendUdpNotification(to);
                            gui.log("Msg from " + currentUser + " to " + to);
                        } else out.println("550 NO SUCH USER");
                    } else out.println("500 BAD FORMAT");
                }
                else if (line.startsWith("LIST")) {
                    String type = "INBOX";
                    if (line.contains(" ")) type = line.split(" ")[1];
                    List<String> msgs;
                    if (type.equalsIgnoreCase("SENT")) msgs = outboxes.get(currentUser);
                    else if (type.equalsIgnoreCase("ARCHIVE")) msgs = archives.get(currentUser);
                    else msgs = inboxes.get(currentUser);
                    if (msgs != null) for (String m : msgs) out.println(m);
                    out.println("213 END");
                }
                else if (line.startsWith("DELE")) {
                    try {
                        int index = Integer.parseInt(line.split(" ")[1]);
                        List<String> inbox = inboxes.get(currentUser);
                        if (index >= 0 && index < inbox.size()) {
                            archives.get(currentUser).add(inbox.remove(index));
                            saveData(); out.println("250 OK");
                        } else out.println("500 INVALID INDEX");
                    } catch (Exception e) { out.println("500 ERROR"); }
                }
                else if (line.startsWith("RESTORE")) {
                    try {
                        int index = Integer.parseInt(line.split(" ")[1]);
                        List<String> arch = archives.get(currentUser);
                        if (index >= 0 && index < arch.size()) {
                            inboxes.get(currentUser).add(arch.remove(index));
                            saveData(); out.println("250 OK");
                        } else out.println("500 INVALID INDEX");
                    } catch (Exception e) { out.println("500 ERROR"); }
                }
                else if (line.equals("WHO")) {
                    out.println("212 " + onlineUsersStatus.size());
                    synchronized (onlineUsersStatus) {
                        for (Map.Entry<String, String> entry : onlineUsersStatus.entrySet()) {
                            out.println("212U " + entry.getKey() + " " + entry.getValue());
                        }
                    }
                    out.println("212 END");
                }
                else if (line.equals("QUIT")) break;
                else out.println("500 UNKNOWN COMMAND");
            }
        } catch (Exception e) { e.printStackTrace(); }
        finally {
            if(currentUser != null) {
                onlineUsersStatus.remove(currentUser);
                userSessions.remove(currentUser);
                if(gui != null) gui.refreshUserTable(); // تحديث الجدول عند الخروج
            }
            try { socket.close(); } catch (IOException e) {}
        }
    }

    private void sendUdpNotification(String username) {
        if (userSessions.containsKey(username)) {
            UserSession session = userSessions.get(username);
            try {
                DatagramSocket dsocket = new DatagramSocket();
                String notification = "NEWMAIL";
                byte[] data = notification.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, session.ipAddress, session.udpPort);
                dsocket.send(packet);
                dsocket.close();
            } catch (Exception e) {}
        }
    }

    private void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(inboxes);
            oos.writeObject(outboxes);
            oos.writeObject(archives);
            oos.writeObject(lastLoginTimes);
            oos.writeObject(accounts); // نحفظ الحسابات أيضاً
        } catch (IOException e) { if(gui!=null) gui.log("Save Error: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                inboxes = (Map<String, List<String>>) ois.readObject();
                outboxes = (Map<String, List<String>>) ois.readObject();
                archives = (Map<String, List<String>>) ois.readObject();
                lastLoginTimes = (Map<String, String>) ois.readObject();
                accounts = (Map<String, String>) ois.readObject();
            } catch (Exception e) {}
        }
    }
}