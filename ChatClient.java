import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatClient extends JFrame {

    private String serverIp = "localhost";
    private int serverPort = 5000;

    private String username;
    private String password;
    private String currentRoom = "General";

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton fileButton;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton leaveRoomButton;

    private JComboBox<String> receiverDropdown;
    private JComboBox<String> roomDropdown;

    private JLabel statusLabel;
    private JLabel serverInfoLabel;
    private JLabel currentRoomLabel;
    private JLabel typingLabel;

    private DefaultListModel<String> onlineUserModel;

    private long lastTypingTime = 0;

    public ChatClient() {
        if (!showLoginDialog()) {
            System.exit(0);
        }

        setupWindow();
        setupUI();
        setVisible(true);
        connectToServer();
    }

    private boolean showLoginDialog() {
        JTextField ipField = new JTextField("localhost");
        JTextField portField = new JTextField("5000");
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
        panel.add(new JLabel("Server IP:"));
        panel.add(ipField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Login",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            try {
                serverIp = ipField.getText().trim();
                serverPort = Integer.parseInt(portField.getText().trim());
                username = usernameField.getText().trim();
                password = new String(passwordField.getPassword());
                return username.length() > 0;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port number.");
            }
        }

        return false;
    }

    private void setupWindow() {
        setTitle("Chat Client - " + username);
        setSize(840, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
    }

    private void setupUI() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

        JLabel titleLabel = new JLabel("Java Chat App");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel statusPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        statusLabel = new JLabel("Disconnected");
        serverInfoLabel = new JLabel("Server: " + serverIp + " | Port: " + serverPort);
        statusPanel.add(statusLabel);
        statusPanel.add(serverInfoLabel);

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(statusPanel, BorderLayout.EAST);

        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        roomPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        currentRoomLabel = new JLabel("Current Room: General");
        roomDropdown = new JComboBox<String>();
        roomDropdown.addItem("General");

        createRoomButton = new JButton("Create Room");
        joinRoomButton = new JButton("Join Room");
        leaveRoomButton = new JButton("Leave Room");

        roomPanel.add(currentRoomLabel);
        roomPanel.add(new JLabel("Rooms:"));
        roomPanel.add(roomDropdown);
        roomPanel.add(createRoomButton);
        roomPanel.add(joinRoomButton);
        roomPanel.add(leaveRoomButton);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(topPanel, BorderLayout.NORTH);
        northPanel.add(roomPanel, BorderLayout.SOUTH);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Messages"));

        onlineUserModel = new DefaultListModel<String>();
        JList<String> onlineUsers = new JList<String>(onlineUserModel);

        JPanel onlinePanel = new JPanel(new BorderLayout());
        onlinePanel.setPreferredSize(new Dimension(180, 0));
        onlinePanel.setBorder(BorderFactory.createTitledBorder("Online Users"));
        onlinePanel.add(new JScrollPane(onlineUsers), BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(onlinePanel, BorderLayout.EAST);

        typingLabel = new JLabel(" ");
        typingLabel.setForeground(Color.GRAY);

        receiverDropdown = new JComboBox<String>();
        receiverDropdown.addItem("Echo Server");
        receiverDropdown.addItem("All");
        receiverDropdown.setPreferredSize(new Dimension(140, 28));

        messageField = new JTextField();
        sendButton = new JButton("Send");
        fileButton = new JButton("Send File");

        JPanel inputPanel = new JPanel(new BorderLayout(8, 8));
        inputPanel.add(receiverDropdown, BorderLayout.WEST);
        inputPanel.add(messageField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 6, 6));
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        bottomPanel.add(typingLabel, BorderLayout.NORTH);
        bottomPanel.add(inputPanel, BorderLayout.CENTER);

        add(northPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        messageField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        fileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendFile();
            }
        });

        createRoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                createRoom();
            }
        });

        joinRoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                joinRoom();
            }
        });

        leaveRoomButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                leaveRoom();
            }
        });

        messageField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                sendTypingNotice();
            }

            public void removeUpdate(DocumentEvent e) {
                sendTypingNotice();
            }

            public void changedUpdate(DocumentEvent e) {
                sendTypingNotice();
            }
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverIp, serverPort);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            ChatPacket loginPacket = new ChatPacket(ChatPacket.Type.LOGIN);
            loginPacket.username = username;
            loginPacket.password = password;

            output.writeObject(loginPacket);
            output.flush();

            ChatPacket response = (ChatPacket) input.readObject();

            if (response.type == ChatPacket.Type.LOGIN_RESPONSE && response.success) {
                statusLabel.setText("Connected to server");
                appendChat("[" + timeNow() + "] Server: " + response.message);

                Thread receiveThread = new Thread(new Runnable() {
                    public void run() {
                        receiveMessages();
                    }
                });

                receiveThread.setName("Client-Receiver-Thread");
                receiveThread.start();

            } else {
                statusLabel.setText("Disconnected");
                JOptionPane.showMessageDialog(this, response.message);
                dispose();
            }

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
            JOptionPane.showMessageDialog(this, "Cannot connect to server.");
        } catch (ClassNotFoundException e) {
            statusLabel.setText("Disconnected");
            JOptionPane.showMessageDialog(this, "Invalid server response.");
        }
    }

    private String timeNow() {
        return new SimpleDateFormat("hh:mm a").format(new Date());
    }

    private void receiveMessages() {
        try {
            while (true) {
                ChatPacket packet = (ChatPacket) input.readObject();

                switch (packet.type) {
                    case MESSAGE:
                    case PRIVATE_MESSAGE:
                    case ECHO:
                    case SYSTEM:
                        appendChat(packet.message);
                        break;

                    case HISTORY:
                        if (!packet.history.isEmpty()) {
                            appendChat("----- Chat History Loaded -----");
                            for (String line : packet.history) {
                                appendChat(line);
                            }
                            appendChat("----- End of History -----");
                        }
                        break;

                    case USER_LIST:
                        updateUserList(packet);
                        break;

                    case ROOM_LIST:
                        updateRoomList(packet);
                        break;

                    case TYPING:
                        showTyping(packet.message);
                        break;

                    case FILE_DATA:
                        receiveFile(packet);
                        break;

                    default:
                        break;
                }
            }

        } catch (IOException e) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    statusLabel.setText("Disconnected");
                    appendChat("[" + timeNow() + "] Server: Disconnected from server.");
                }
            });
        } catch (ClassNotFoundException e) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    statusLabel.setText("Disconnected");
                    appendChat("[" + timeNow() + "] Server: Invalid message received.");
                }
            });
        }
    }

    private String applyEmoji(String text) {
        return text
                .replace(":)", "\uD83D\uDE0A")
                .replace(":D", "\uD83D\uDE04")
                .replace(":(", "\u2639\uFE0F")
                .replace("<3", "\u2764\uFE0F")
                .replace(":P", "\uD83D\uDE1B");
    }

    private void appendChat(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                String displayText = applyEmoji(text);
                chatArea.append(displayText + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }

    private void updateUserList(final ChatPacket packet) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                onlineUserModel.clear();
                receiverDropdown.removeAllItems();
                receiverDropdown.addItem("Echo Server");
                receiverDropdown.addItem("All");

                for (String user : packet.users) {
                    onlineUserModel.addElement(user);

                    if (!user.equals(username)) {
                        receiverDropdown.addItem(user);
                    }
                }
            }
        });
    }

    private void updateRoomList(final ChatPacket packet) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Object selected = roomDropdown.getSelectedItem();
                roomDropdown.removeAllItems();

                for (String room : packet.rooms) {
                    roomDropdown.addItem(room);
                }

                if (selected != null) {
                    roomDropdown.setSelectedItem(selected);
                }
            }
        });
    }

    private void showTyping(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                typingLabel.setText(message);

                Timer timer = new Timer(2000, new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        typingLabel.setText(" ");
                    }
                });

                timer.setRepeats(false);
                timer.start();
            }
        });
    }

    private void sendTypingNotice() {
        if (messageField.getText().trim().length() == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTypingTime < 1000) {
            return;
        }

        lastTypingTime = now;

        try {
            ChatPacket packet = new ChatPacket(ChatPacket.Type.TYPING);
            packet.username = username;
            output.writeObject(packet);
            output.flush();
        } catch (IOException e) {
            statusLabel.setText("Disconnected");
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();

        if (message.length() == 0) {
            return;
        }

        String receiver = (String) receiverDropdown.getSelectedItem();

        try {
            if (receiver == null || receiver.equals("All")) {
                ChatPacket packet = new ChatPacket(ChatPacket.Type.MESSAGE);
                packet.username = username;
                packet.room = currentRoom;
                packet.message = message;
                output.writeObject(packet);

            } else if (receiver.equals("Echo Server")) {
                appendChat("[" + timeNow() + "] You: " + message);

                ChatPacket packet = new ChatPacket(ChatPacket.Type.ECHO);
                packet.username = username;
                packet.message = message;
                output.writeObject(packet);

            } else {
                ChatPacket packet = new ChatPacket(ChatPacket.Type.PRIVATE_MESSAGE);
                packet.username = username;
                packet.receiver = receiver;
                packet.message = message;
                output.writeObject(packet);
            }

            output.flush();
            messageField.setText("");

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
        }
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        String receiver = (String) receiverDropdown.getSelectedItem();

        if (receiver != null && receiver.equals("Echo Server")) {
            receiver = "All";
        }

        try {
            ChatPacket packet = new ChatPacket(ChatPacket.Type.FILE_DATA);
            packet.username = username;
            packet.receiver = receiver;
            packet.room = currentRoom;
            packet.fileName = file.getName();
            packet.fileData = Files.readAllBytes(file.toPath());

            output.writeObject(packet);
            output.flush();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "File sending failed.");
        }
    }

    private void receiveFile(final ChatPacket packet) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int choice = JOptionPane.showConfirmDialog(
                        ChatClient.this,
                        packet.username + " sent you a file: " + packet.fileName + "\nDo you want to save it?",
                        "Incoming File",
                        JOptionPane.YES_NO_OPTION
                );

                if (choice == JOptionPane.YES_OPTION) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new File(packet.fileName));

                    int result = chooser.showSaveDialog(ChatClient.this);

                    if (result == JFileChooser.APPROVE_OPTION) {
                        try {
                            Files.write(chooser.getSelectedFile().toPath(), packet.fileData);
                            appendChat(packet.message);
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(ChatClient.this, "Failed to save file.");
                        }
                    }
                }
            }
        });
    }

    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(this, "Enter room name:");

        if (roomName == null || roomName.trim().length() == 0) {
            return;
        }

        try {
            currentRoom = roomName.trim();
            currentRoomLabel.setText("Current Room: " + currentRoom);

            ChatPacket packet = new ChatPacket(ChatPacket.Type.CREATE_ROOM);
            packet.username = username;
            packet.room = currentRoom;
            output.writeObject(packet);
            output.flush();

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
        }
    }

    private void joinRoom() {
        String selectedRoom = (String) roomDropdown.getSelectedItem();

        if (selectedRoom == null) {
            return;
        }

        try {
            currentRoom = selectedRoom;
            currentRoomLabel.setText("Current Room: " + currentRoom);

            ChatPacket packet = new ChatPacket(ChatPacket.Type.JOIN_ROOM);
            packet.username = username;
            packet.room = currentRoom;
            output.writeObject(packet);
            output.flush();

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
        }
    }

    private void leaveRoom() {
        try {
            currentRoom = "General";
            currentRoomLabel.setText("Current Room: General");

            ChatPacket packet = new ChatPacket(ChatPacket.Type.LEAVE_ROOM);
            packet.username = username;
            packet.room = "General";
            output.writeObject(packet);
            output.flush();

        } catch (IOException e) {
            statusLabel.setText("Disconnected");
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default Java look and feel if system look and feel is unavailable.
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ChatClient();
            }
        });
    }
}
