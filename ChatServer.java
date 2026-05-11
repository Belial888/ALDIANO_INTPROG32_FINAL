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
        setupWindow();
        setupComponents();
        startServer();

        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Chat Server Monitor");
        setSize(750, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(245, 247, 250));
    }

    private void setupComponents() {
        Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font titleFont = new Font("Segoe UI", Font.BOLD, 18);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(37, 99, 235));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Server Monitor");
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(Color.WHITE);

        statusLabel = new JLabel("Server Status: Offline");
        statusLabel.setFont(mainFont);
        statusLabel.setForeground(Color.WHITE);

        JLabel portLabel = new JLabel("Port: " + PORT);
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        portLabel.setForeground(new Color(220, 230, 255));

        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusPanel.setOpaque(false);
        statusPanel.add(statusLabel);
        statusPanel.add(portLabel);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        chatMonitor = new JTextArea();
        chatMonitor.setEditable(false);
        chatMonitor.setFont(mainFont);
        chatMonitor.setLineWrap(true);
        chatMonitor.setWrapStyleWord(true);
        chatMonitor.setBackground(Color.WHITE);
        chatMonitor.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JScrollPane chatScrollPane = new JScrollPane(chatMonitor);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 5));

        userListModel = new DefaultListModel<>();
        JList<String> onlineUsers = new JList<>(userListModel);
        onlineUsers.setFont(mainFont);

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBackground(Color.WHITE);
        usersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel usersLabel = new JLabel("Online Users");
        usersLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        usersLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        usersPanel.add(usersLabel, BorderLayout.NORTH);
        usersPanel.add(new JScrollPane(onlineUsers), BorderLayout.CENTER);
        usersPanel.setPreferredSize(new Dimension(180, 0));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(chatScrollPane, BorderLayout.CENTER);
        centerPanel.add(usersPanel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
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
            chatMonitor.setCaretPosition(chatMonitor.getDocument().getLength());
        });
    }

    private void updateUserList() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();

            synchronized (clients) {
                for (String user : clients.keySet()) {
                    userListModel.addElement(user);
                }
            }
        });
    }

    private void sendUserListToClients() {
        synchronized (clients) {
            StringBuilder userList = new StringBuilder("USERLIST:");

            for (String user : clients.keySet()) {
                userList.append(user).append(",");
            }

            for (ClientHandler client : clients.values()) {
                client.send(userList.toString());
            }
        }
    }

    private void broadcast(String message) {
        log(message);

        synchronized (clients) {
            for (ClientHandler client : clients.values()) {
                client.send(message);
            }
        }
    }

    private void sendPrivate(String sender, String receiver, String message) {
        synchronized (clients) {
            ClientHandler target = clients.get(receiver);
            ClientHandler senderClient = clients.get(sender);

            if (target != null) {
                String privateMsg = "[PRIVATE] " + sender + " -> " + receiver + ": " + message;

                target.send(privateMsg);

                if (senderClient != null) {
                    senderClient.send(privateMsg);
                }

                log(privateMsg);
            } else {
                if (senderClient != null) {
                    senderClient.send("Server: User " + receiver + " is not available.");
                }
            }
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
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);

                username = input.readLine();

                synchronized (clients) {
                    clients.put(username, this);
                }

                log(username + " connected.");
                updateUserList();
                sendUserListToClients();

                broadcast("Server: " + username + " joined the chat.");

                String message;

                while ((message = input.readLine()) != null) {
                    if (message.startsWith("PRIVATE:")) {
                        String[] parts = message.split(":", 3);

                        if (parts.length == 3) {
                            String receiver = parts[1];
                            String privateText = parts[2];

                            sendPrivate(username, receiver, privateText);
                        }

                    } else {
                        broadcast(username + ": " + message);
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
                    sendUserListToClients();

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
        SwingUtilities.invokeLater(ChatServer::new);
    }
}