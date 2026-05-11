import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer extends JFrame {

    private static final int PORT = 5000;

    private JTextArea chatMonitor;
    private JLabel statusLabel;
    private DefaultListModel<String> userListModel;

    private static final Map<String, ClientHandler> clients = new HashMap<>();

    public ChatServer() {
        setTitle("Chat Server Monitor");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatMonitor = new JTextArea();
        chatMonitor.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(chatMonitor);

        statusLabel = new JLabel("Server Status: Offline");

        userListModel = new DefaultListModel<>();
        JList<String> onlineUsers = new JList<>(userListModel);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Online Users"), BorderLayout.NORTH);
        rightPanel.add(new JScrollPane(onlineUsers), BorderLayout.CENTER);

        add(statusLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        setVisible(true);

        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {

                log("Server started on port " + PORT);
                statusLabel.setText("Server Status: Online");

                while (true) {
                    Socket socket = serverSocket.accept();

                    ClientHandler client = new ClientHandler(socket);
                    client.start();
                }

            } catch (IOException e) {
                log("Server error: " + e.getMessage());
                statusLabel.setText("Server Status: Offline");
            }
        }).start();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            chatMonitor.append(message + "\n");
        });
    }

    private void updateUserList() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();

            for (String user : clients.keySet()) {
                userListModel.addElement(user);
            }
        });
    }

    private void broadcast(String message) {
        log(message);

        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    private void sendPrivate(String sender, String receiver, String message) {

        ClientHandler target = clients.get(receiver);

        if (target != null) {

            String privateMsg =
                    "[PRIVATE] " + sender + " -> " + receiver + ": " + message;

            target.send(privateMsg);

            ClientHandler senderClient = clients.get(sender);

            if (senderClient != null) {
                senderClient.send(privateMsg);
            }

            log(privateMsg);
        }
    }

    class ClientHandler extends Thread {

        private Socket socket;
        private BufferedReader input;
        private PrintWriter output;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {

            try {

                input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                output = new PrintWriter(
                        socket.getOutputStream(), true);

                username = input.readLine();

                synchronized (clients) {
                    clients.put(username, this);
                }

                log(username + " connected.");
                updateUserList();

                broadcast("Server: " + username + " joined the chat.");

                String message;

                while ((message = input.readLine()) != null) {

                    if (message.startsWith("PRIVATE:")) {

                        String[] parts = message.split(":", 3);

                        String receiver = parts[1];
                        String privateText = parts[2];

                        sendPrivate(username, receiver, privateText);

                    } else {

                        String fullMessage =
                                username + ": " + message;

                        broadcast(fullMessage);
                    }
                }

            } catch (IOException e) {

                log(username + " disconnected.");

            } finally {

                try {

                    synchronized (clients) {
                        clients.remove(username);
                    }

                    updateUserList();

                    broadcast("Server: " + username + " left the chat.");

                    socket.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void send(String message) {
            output.println(message);
        }
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new ChatServer();
        });
    }
}