package server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerGUI extends JFrame {

    private JTextArea logArea;
    private JTable usersTable;
    private DefaultTableModel tableModel;
    private ServerCore server;
    private JButton startBtn, stopBtn, addUserBtn, removeUserBtn;

    // حقول الإعدادات
    private JTextField cleanupDaysField;
    private JTextField udpPortField;

    public ServerGUI() {
        setTitle("MailLite Server Control");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- Top: Start/Stop Buttons ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startBtn = new JButton("Start Server");
        stopBtn = new JButton("Stop Server");
        stopBtn.setEnabled(false);

        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        topPanel.add(startBtn);
        topPanel.add(stopBtn);
        add(topPanel, BorderLayout.NORTH);

        // --- Center: Split Pane (Left: Users, Right: Logs) ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.4);

        // Left Side: Online Users
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Online Users / Accounts"));

        String[] cols = {"Username", "Status", "Last Seen"};
        tableModel = new DefaultTableModel(cols, 0);
        usersTable = new JTable(tableModel);
        leftPanel.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        JPanel userBtns = new JPanel(new GridLayout(1, 2, 5, 5));
        addUserBtn = new JButton("Add User");
        removeUserBtn = new JButton("Remove User");

        addUserBtn.addActionListener(e -> addNewUser());
        removeUserBtn.addActionListener(e -> removeUser());

        userBtns.add(addUserBtn);
        userBtns.add(removeUserBtn);
        leftPanel.add(userBtns, BorderLayout.SOUTH);

        // Right Side: Logs + Config
        JPanel rightPanel = new JPanel(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Logs"));
        rightPanel.add(logScroll, BorderLayout.CENTER);

        // Config Area (Bottom Right)
        JPanel configPanel = new JPanel(new GridLayout(2, 4, 5, 5));
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));

        cleanupDaysField = new JTextField("30");
        udpPortField = new JTextField("10000");

        configPanel.add(new JLabel("Cleanup Days:"));
        configPanel.add(cleanupDaysField);
        configPanel.add(new JLabel("UDP Port:"));
        configPanel.add(udpPortField);
        JButton saveConfigBtn = new JButton("Save");
        saveConfigBtn.addActionListener(e -> log("Configuration saved."));
        configPanel.add(new JLabel("")); // Spacer
        configPanel.add(saveConfigBtn);

        rightPanel.add(configPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        setVisible(true);
    }

    private void startServer() {
        try {
            int port = 1234; // TCP Port
            server = new ServerCore(port, this); // نمرر الواجهة للسيرفر
            new Thread(() -> server.start()).start();

            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            log("Server started on port " + port);

            // تحديث الجدول بالحسابات الموجودة
            refreshUserTable();

        } catch (Exception e) {
            log("Error starting server: " + e.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            log("Server stopped.");
        }
    }

    // الدالة المعدلة لطلب اسم المستخدم وكلمة المرور
    private void addNewUser() {
        if (server == null) {
            JOptionPane.showMessageDialog(this, "Please start server first to load accounts.");
            return;
        }

        // إنشاء حقول الإدخال لاسم المستخدم وكلمة المرور
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.add(new JLabel("Username:"));
        inputPanel.add(usernameField);
        inputPanel.add(new JLabel("Password:"));
        inputPanel.add(passwordField);

        // عرض مربع حوار مخصص
        int result = JOptionPane.showConfirmDialog(
                this,
                inputPanel,
                "Add New Mail Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String user = usernameField.getText().trim();
            // الحصول على كلمة المرور من JPasswordField وتحويلها إلى String
            String pass = new String(passwordField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean success = server.addUser(user, pass);
            if (success) {
                log("User added: " + user);
                refreshUserTable();
            } else {
                JOptionPane.showMessageDialog(this, "User already exists!");
            }
        }
    }

    private void removeUser() {
        if (server == null) return;
        int row = usersTable.getSelectedRow();
        if (row != -1) {
            String user = (String) tableModel.getValueAt(row, 0);
            server.removeUser(user);
            log("User removed: " + user);
            refreshUserTable();
        } else {
            JOptionPane.showMessageDialog(this, "Select a user first.");
        }
    }

    // دالة لتحديث الجدول من السيرفر
    public void refreshUserTable() {
        if (server == null) return;
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            var accounts = server.getAccounts(); // قائمة الحسابات
            var onlineMap = server.getOnlineStatus(); // من متصل
            var lastSeenMap = server.getLastLoginTimes(); // متى شوهد

            for (String user : accounts.keySet()) {
                String status = onlineMap.getOrDefault(user, "Offline");
                String lastSeen = lastSeenMap.getOrDefault(user, "Never");
                tableModel.addRow(new Object[]{user, status, lastSeen});
            }
        });
    }

    // دالة الطباعة في الـ Log
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}