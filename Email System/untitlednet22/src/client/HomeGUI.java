//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class HomeGUI extends JFrame {
    JTable mailTable;
    JTextArea messageArea;
    JTextField toField;
    JTextField subjectField;
    JTextArea bodyArea;
    JLabel lastLoginLabel;
    DefaultTableModel tableModel;
    DefaultListModel<String> userListModel;
    JList<String> userList;
    JButton actionBtn;
    JComboBox<String> statusCombo;
    JTextField searchField;
    TableRowSorter<DefaultTableModel> sorter;
    private String lastLoginTimeStr = "Never";
    private final String SERVER_HOST = "localhost";
    private final int SERVER_PORT = 1234;
    private long loginStartTime;
    private JLabel connectionStatusLabel;
    private JLabel loginDurationLabel;
    private JLabel currentStatusLabel;
    private Timer durationTimer;
    private Timer idleTimer;
    private static final int IDLE_THRESHOLD = 30000;
    private long lastActivityTime;
    private String currentBox = "INBOX";
    NetworkManager netManager = new NetworkManager();
    private DatagramSocket udpSocket;
    private int myUdpPort;
    private Map<String, Color> userColors = new HashMap();
    private volatile boolean isRunning = true;

    public HomeGUI() {
        if (!this.netManager.connect("172.23.192.70", 1234)) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server!");
            System.exit(0);
        }

        this.userColors.put("bob", new Color(220, 240, 255));
        this.userColors.put("alice", new Color(255, 230, 240));
        this.userColors.put("eve", new Color(230, 255, 230));
        this.userColors.put("david", new Color(255, 250, 200));
        this.setupUdpListener();
        this.showLoginDialog();
        this.startDurationTimer();
        this.initUI();
        this.setupIdleMonitor();
        this.refreshTable();
        this.refreshOnlineUsers();
        (new Timer(3000, (e) -> {
            if (this.isVisible()) {
                this.refreshOnlineUsers();
            }

        })).start();
    }

    private void setupUdpListener() {
        try {
            this.udpSocket = new DatagramSocket();
            this.myUdpPort = this.udpSocket.getLocalPort();
            this.isRunning = true;
            (new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];

                    while(this.isRunning && !this.udpSocket.isClosed()) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        this.udpSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.equals("NEWMAIL") && this.currentBox.equals("INBOX")) {
                            SwingUtilities.invokeLater(() -> {
                                this.refreshTable();
                                JOptionPane.showMessageDialog(this, "\ud83d\udce7 New Email Received!");
                            });
                        }
                    }
                } catch (Exception var4) {
                }

            })).start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void showLoginDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JTextField userField = new JTextField("");
        JPasswordField passField = new JPasswordField("");
        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passField);
        int result = JOptionPane.showConfirmDialog((Component)null, panel, "Login MailLite", 2, -1);
        if (result == 0) {
            String user = userField.getText().trim();
            String pass = (new String(passField.getPassword())).trim();
            if (user.isEmpty()) {
                System.exit(0);
            }

            this.netManager.sendCommand("HELO " + user);
            String response = this.netManager.sendCommand("AUTH " + user + " " + pass + " " + this.myUdpPort);
            if (response != null && response.startsWith("235")) {
                this.netManager.setCurrentUser(user);
                this.loginStartTime = System.currentTimeMillis();
                if (response.contains("|")) {
                    this.lastLoginTimeStr = response.split("\\|")[1].trim();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Login Failed!");
                this.showLoginDialog();
            }
        } else {
            System.exit(0);
        }

    }

    private void startDurationTimer() {
        this.durationTimer = new Timer(1000, (e) -> {
            long durationMillis = System.currentTimeMillis() - this.loginStartTime;
            long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60L;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60L;
            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            this.loginDurationLabel.setText("Logged in for " + timeStr);
            this.currentStatusLabel.setText((String)this.statusCombo.getSelectedItem());
        });
        this.durationTimer.start();
    }

    private void initUI() {
        this.setTitle("MailLite Client - " + this.netManager.getCurrentUser());
        this.setSize(1100, 700);
        this.setDefaultCloseOperation(3);
        this.setLayout(new BorderLayout());
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export Current View to .txt");
        exportItem.addActionListener((e) -> this.exportHistory());
        fileMenu.add(exportItem);
        menuBar.add(fileMenu);
        this.setJMenuBar(menuBar);
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 600));
        DefaultListModel<String> folderModel = new DefaultListModel();
        folderModel.addElement("Inbox");
        folderModel.addElement("Sent");
        folderModel.addElement("Archive");
        JList<String> folderList = new JList(folderModel);
        folderList.setSelectedIndex(0);
        folderList.setBorder(BorderFactory.createTitledBorder("Folders"));
        folderList.addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                String sel = (String)folderList.getSelectedValue();
                if ("Inbox".equals(sel)) {
                    this.currentBox = "INBOX";
                } else if ("Sent".equals(sel)) {
                    this.currentBox = "SENT";
                } else if ("Archive".equals(sel)) {
                    this.currentBox = "ARCHIVE";
                }

                this.refreshTable();
            }

        });
        String[] statuses = new String[]{"Active", "Busy", "Away"};
        this.statusCombo = new JComboBox(statuses);
        this.statusCombo.addActionListener((e) -> {
            String selected = (String)this.statusCombo.getSelectedItem();
            this.netManager.sendCommand("SETSTAT " + selected);
        });
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("My Status"));
        statusPanel.add(this.statusCombo, "Center");
        this.userListModel = new DefaultListModel();
        this.userList = new JList(this.userListModel);
        this.userList.setBorder(BorderFactory.createTitledBorder("Online Users"));
        this.userList.setBackground(new Color(245, 255, 245));
        JPanel topPart = new JPanel(new BorderLayout());
        topPart.add(folderList, "Center");
        topPart.add(statusPanel, "South");
        leftPanel.add(topPart, "North");
        leftPanel.add(new JScrollPane(this.userList), "Center");
        this.add(leftPanel, "West");
        JPanel centerPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("\ud83d\udd0d Search Filter: "), "West");
        this.searchField = new JTextField();
        searchPanel.add(this.searchField, "Center");
        centerPanel.add(searchPanel, "North");
        String[] columnNames = new String[]{"From/To", "Subject", "Time", "Body (Hidden)"};
        this.tableModel = new DefaultTableModel(columnNames, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        this.mailTable = new JTable(this.tableModel);
        this.sorter = new TableRowSorter(this.tableModel);
        this.mailTable.setRowSorter(this.sorter);
        this.searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                String text = HomeGUI.this.searchField.getText();
                if (text.trim().length() == 0) {
                    HomeGUI.this.sorter.setRowFilter((RowFilter)null);
                } else {
                    HomeGUI.this.sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, new int[0]));
                }

            }
        });
        this.mailTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                String name = (String)HomeGUI.this.tableModel.getValueAt(modelRow, 0);
                if (!isSelected) {
                    if (HomeGUI.this.userColors.containsKey(name.toLowerCase())) {
                        c.setBackground((Color)HomeGUI.this.userColors.get(name.toLowerCase()));
                    } else {
                        int hash = name.hashCode();
                        c.setBackground(new Color(((hash & 16711680) >> 271) / 2, ((hash & '\uff00') >> 263) / 2, ((hash & 255) + 255) / 2));
                    }
                } else {
                    c.setBackground(table.getSelectionBackground());
                }

                return c;
            }
        });
        this.mailTable.getColumnModel().getColumn(3).setMinWidth(0);
        this.mailTable.getColumnModel().getColumn(3).setMaxWidth(0);
        this.mailTable.getSelectionModel().addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting() && this.mailTable.getSelectedRow() != -1) {
                int viewRow = this.mailTable.getSelectedRow();
                int modelRow = this.mailTable.convertRowIndexToModel(viewRow);
                this.messageArea.setText(this.tableModel.getValueAt(modelRow, 3).toString());
                this.actionBtn.setEnabled(true);
            } else {
                this.actionBtn.setEnabled(false);
            }

        });
        centerPanel.add(new JScrollPane(this.mailTable), "Center");
        JPanel readingPanel = new JPanel(new BorderLayout());
        this.messageArea = new JTextArea();
        this.messageArea.setEditable(false);
        this.messageArea.setBorder(BorderFactory.createTitledBorder("Message Content"));
        readingPanel.add(new JScrollPane(this.messageArea), "Center");
        this.actionBtn = new JButton("Delete to Archive");
        this.actionBtn.setEnabled(false);
        this.actionBtn.setBackground(new Color(255, 200, 200));
        this.actionBtn.addActionListener((e) -> this.performAction());
        readingPanel.add(this.actionBtn, "South");
        readingPanel.setPreferredSize(new Dimension(100, 200));
        centerPanel.add(readingPanel, "South");
        this.add(centerPanel, "Center");
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(300, 600));
        JPanel composeFields = new JPanel(new GridLayout(6, 1));
        this.toField = new JTextField();
        this.subjectField = new JTextField();
        this.bodyArea = new JTextArea();
        this.bodyArea.setLineWrap(true);
        composeFields.add(new JLabel("To (username):"));
        composeFields.add(this.toField);
        composeFields.add(new JLabel("Subject:"));
        composeFields.add(this.subjectField);
        composeFields.add(new JLabel("Body:"));
        rightPanel.add(composeFields, "North");
        rightPanel.add(new JScrollPane(this.bodyArea), "Center");
        JPanel buttonsPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        JButton sendBtn = new JButton("Send Email");
        sendBtn.setBackground(new Color(100, 200, 100));
        sendBtn.addActionListener((e) -> this.sendEmail());
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(255, 100, 100));
        logoutBtn.addActionListener((e) -> this.performLogout());
        buttonsPanel.add(sendBtn);
        buttonsPanel.add(logoutBtn);
        rightPanel.add(buttonsPanel, "South");
        this.add(rightPanel, "East");
        JPanel footer = new JPanel(new FlowLayout(0, 10, 5));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        footer.setBackground(Color.WHITE);
        this.connectionStatusLabel = new JLabel("Connected to localhost:1234");
        this.connectionStatusLabel.setFont(this.connectionStatusLabel.getFont().deriveFont(1, 10.0F));
        footer.add(this.connectionStatusLabel);
        footer.add(new JSeparator(1));
        this.loginDurationLabel = new JLabel("Logged in for 00:00:00");
        footer.add(this.loginDurationLabel);
        footer.add(new JSeparator(1));
        this.lastLoginLabel = new JLabel("Last login " + this.lastLoginTimeStr);
        footer.add(this.lastLoginLabel);
        footer.add(new JSeparator(1));
        this.currentStatusLabel = new JLabel((String)this.statusCombo.getSelectedItem());
        this.currentStatusLabel.setForeground(Color.BLUE);
        footer.add(this.currentStatusLabel);
        this.add(footer, "South");
        this.setVisible(true);
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Logout", 0);
        if (confirm == 0) {
            if (this.durationTimer != null) {
                this.durationTimer.stop();
            }

            if (this.idleTimer != null) {
                this.idleTimer.stop();
            }

            this.isRunning = false;
            if (this.udpSocket != null && !this.udpSocket.isClosed()) {
                this.udpSocket.close();
            }

            this.netManager.sendCommand("QUIT");
            this.netManager.disconnect();
            this.dispose();
            SwingUtilities.invokeLater(() -> new HomeGUI());
        }

    }

    private void exportHistory() {
        if (this.tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Nothing to export!");
        } else {
            String filename = "history_" + this.currentBox + ".txt";

            try (FileWriter writer = new FileWriter(filename)) {
                writer.write("--- Exported " + this.currentBox + " ---\n\n");

                for(int i = 0; i < this.tableModel.getRowCount(); ++i) {
                    String var10001 = String.valueOf(this.tableModel.getValueAt(i, 0));
                    writer.write("From/To: " + var10001 + "\nTime: " + String.valueOf(this.tableModel.getValueAt(i, 2)) + "\nSubject: " + String.valueOf(this.tableModel.getValueAt(i, 1)) + "\nBody: " + String.valueOf(this.tableModel.getValueAt(i, 3)) + "\n-----------------\n");
                }

                JOptionPane.showMessageDialog(this, "Exported to " + filename);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
            }

        }
    }

    private void setupIdleMonitor() {
        this.lastActivityTime = System.currentTimeMillis();
        MouseAdapter activityListener = new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                HomeGUI.this.resetIdleTimer();
            }
        };
        this.addMouseMotionListener(activityListener);
        this.mailTable.addMouseMotionListener(activityListener);
        this.addMouseListener(activityListener);
        this.statusCombo.addMouseListener(activityListener);
        this.idleTimer = new Timer(1000, (e) -> {
            long idleTime = System.currentTimeMillis() - this.lastActivityTime;
            if (idleTime > 30000L && !"Away".equals(this.statusCombo.getSelectedItem())) {
                this.statusCombo.setSelectedItem("Away");
            }

        });
        this.idleTimer.start();
    }

    private void resetIdleTimer() {
        this.lastActivityTime = System.currentTimeMillis();
        if ("Away".equals(this.statusCombo.getSelectedItem())) {
            this.statusCombo.setSelectedItem("Active");
        }

    }

    private void sendEmail() {
        String to = this.toField.getText().trim();
        String sub = this.subjectField.getText().trim();
        String body = this.bodyArea.getText().trim();
        if (!to.isEmpty() && !sub.isEmpty()) {
            String cmd = "SEND " + to + "|" + sub + "|" + body.replace("|", "/");
            if (this.netManager.sendCommand(cmd).startsWith("250")) {
                JOptionPane.showMessageDialog(this, "Email Sent!");
                this.toField.setText("");
                this.subjectField.setText("");
                this.bodyArea.setText("");
                if (this.currentBox.equals("SENT")) {
                    this.refreshTable();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Error sending email.");
            }

        }
    }

    private void performAction() {
        int row = this.mailTable.getSelectedRow();
        if (row != -1) {
            int modelRow = this.mailTable.convertRowIndexToModel(row);
            String cmd = this.currentBox.equals("INBOX") ? "DELE " + modelRow : (this.currentBox.equals("ARCHIVE") ? "RESTORE " + modelRow : null);
            if (cmd != null && this.netManager.sendCommand(cmd).startsWith("250")) {
                this.refreshTable();
                JOptionPane.showMessageDialog(this, "Success");
            }

        }
    }

    private void refreshTable() {
        this.tableModel.setRowCount(0);
        this.messageArea.setText("");
        this.actionBtn.setEnabled(false);
        if (this.currentBox.equals("INBOX")) {
            this.actionBtn.setText("Delete to Archive");
            this.actionBtn.setVisible(true);
        } else if (this.currentBox.equals("ARCHIVE")) {
            this.actionBtn.setText("Restore to Inbox");
            this.actionBtn.setVisible(true);
        } else {
            this.actionBtn.setVisible(false);
        }

        String var10001 = this.currentBox;
        List<String> msgs = this.netManager.sendQuery("LIST " + var10001);
        this.mailTable.getColumnModel().getColumn(0).setHeaderValue(this.currentBox.equals("SENT") ? "To" : "From");
        this.mailTable.getTableHeader().repaint();

        for(String msg : msgs) {
            String[] parts = msg.split("\\|", 4);
            if (parts.length >= 3) {
                String time = parts.length > 3 ? parts[3] : "N/A";
                this.tableModel.addRow(new Object[]{parts[0], parts[1], time, parts[2]});
            }
        }

    }

    private void refreshOnlineUsers() {
        List<String> res = this.netManager.sendQuery("WHO");
        this.userListModel.clear();

        for(String line : res) {
            if (line.startsWith("212U")) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    String u = parts[1];
                    String s = parts[2];
                    String icon = s.equalsIgnoreCase("Busy") ? "\ud83d\udd34" : (s.equalsIgnoreCase("Away") ? "\ud83d\udfe1" : "\ud83d\udfe2");
                    if (!u.equals(this.netManager.getCurrentUser())) {
                        this.userListModel.addElement(icon + " " + u + " (" + s + ")");
                    }
                }
            }
        }

    }
}
