package server;

import javax.swing.SwingUtilities;

public class ServerMain {
    public static void main(String[] args) {
        // تشغيل الواجهة الرسومية للسيرفر
        SwingUtilities.invokeLater(() -> new ServerGUI());
    }
}