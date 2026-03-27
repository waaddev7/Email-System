//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class NetworkManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUser;

    public boolean connect(String host, int port) {
        try {
            this.socket = new Socket(host, port);
            this.out = new PrintWriter(this.socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public String sendCommand(String command) {
        try {
            if (this.out == null) {
                return "ERROR: No Connection";
            } else {
                this.out.println(command);
                return this.in.readLine();
            }
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public List<String> sendQuery(String command) {
        List<String> responseList = new ArrayList();

        try {
            this.out.println(command);

            String line;
            while((line = this.in.readLine()) != null && !line.trim().endsWith("END")) {
                responseList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return responseList;
    }

    public void setCurrentUser(String user) {
        this.currentUser = user;
    }

    public String getCurrentUser() {
        return this.currentUser;
    }

    public void disconnect() {
        try {
            if (this.out != null) {
                this.out.close();
            }

            if (this.in != null) {
                this.in.close();
            }

            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
