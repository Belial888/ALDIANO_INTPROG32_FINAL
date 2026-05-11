import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ChatClient extends JFrame {

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JComboBox<String> userDropdown;
    private JLabel statusLabel;
    private JLabel serverInfoLabel;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private String username;

    private final String SERVER_IP = "localhost";
    private final int SERVER_PORT = 5000;

    public ChatClient() {
        username = JOptionPane.showInputDialog(this, "Enter username:");

        if (username == null || username.trim().isEmpty()) {
            username = "Anonymous" + System.currentTimeMillis();
        }

        setupWindow();
        setupComponents();
        connectToServer();

        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Chat App - " + username);
        setSize(650, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(245, 247, 250));
        setLayout(new BorderLayout(10, 10));
    }

    private void setupComponents() {
        Font mainFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font titleFont = new Font("Segoe UI", Font.BOLD, 18);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(37, 99, 235));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Group Chat");
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(Color.WHITE);

        statusLabel = new JLabel("Disconnected");
        statusLabel.setFont(mainFont);
        statusLabel.setForeground(Color.WHITE);

        serverInfoLabel = new JLabel("Server: " + SERVER_IP + " | Port: " + SERVER_PORT);
        serverInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        serverInfoLabel.setForeground(new Color(220, 230, 255));

        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusPanel.setOpaque(false);
        statusPanel.add(statusLabel);
        statusPanel.add(serverInfoLabel);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusPanel, BorderLayout.EAST);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(mainFont);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(Color.WHITE);
        chatArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(new Color(245, 247, 250));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));

        userDropdown = new JComboBox<>();
        userDropdown.addItem("All");
        userDropdown.setFont(mainFont);
        userDropdown.setPreferredSize(new Dimension(130, 40));

        messageField = new JTextField();
        messageField.setFont(mainFont);
        messageField.setPreferredSize(new Dimension(300, 40));
        messageField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(new Color(37, 99, 235));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setPreferredSize(new Dimension(90, 40));

        bottomPanel.add(userDropdown, BorderLayout.WEST);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);

            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            output.println(username);

            statusLabel.setText("Connected to server");
            chatArea.append("Connected to server.\n");

            Thread receiveThread = new Thread(() -> {
                try {
                    String message;

                    while ((message = input.readLine()) != null) {
                        if (message.startsWith("USERLIST:")) {
                            updateUserDropdown(message);
                        } else {
                            chatArea.append(message + "\n");
                        }
                    }

                } catch (IOException e) {
                    statusLabel.setText("Disconnected");
                    chatArea.append("Disconnected from server.\n");
                }
            });

            receiveThread.start();

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
            JOptionPane.showMessageDialog(this, "Cannot connect to server.");
        }
    }

    private void updateUserDropdown(String message) {
        SwingUtilities.invokeLater(() -> {
            userDropdown.removeAllItems();
            userDropdown.addItem("All");

            String users = message.substring("USERLIST:".length());

            if (!users.isEmpty()) {
                String[] userList = users.split(",");

                for (String user : userList) {
                    user = user.trim();

                    if (!user.isEmpty() && !user.equals(username)) {
                        userDropdown.addItem(user);
                    }
                }
            }
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();

        if (!message.isEmpty()) {
            String selectedUser = (String) userDropdown.getSelectedItem();

            if (selectedUser == null || selectedUser.equals("All")) {
                output.println(message);
                chatArea.append("You: " + message + "\n");
            } else {
                output.println("PRIVATE:" + selectedUser + ":" + message);
                chatArea.append("[Private to " + selectedUser + "] You: " + message + "\n");
            }

            messageField.setText("");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::new);
    }
}