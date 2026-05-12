import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ChatServer extends JFrame {

    private static final int PORT = 5000;
    private static final String HISTORY_FILE = "chat_history.txt";

    private final Map<String, ClientHandler> clients =
            Collections.synchronizedMap(new LinkedHashMap<String, ClientHandler>());

    private final Map<String, Set<String>> rooms =
            Collections.synchronizedMap(new LinkedHashMap<String, Set<String>>());

    private JTextArea monitorArea;
    private JLabel statusLabel;
    private JLabel portLabel;
    private JLabel threadCountLabel;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> threadListModel;

    public ChatServer() {
        rooms.put("General", new LinkedHashSet<String>());

        setupWindow();
        setupUI();
        startServer();

        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Chat Server Monitor");
        setSize(800, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
    }

    private void setupUI() {
        JPanel topPanel = new JPanel(new GridLayout(3, 1, 3, 3));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JLabel titleLabel = new JLabel("Chat Server Monitor");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        statusLabel = new JLabel("Server Status: Offline");
        portLabel = new JLabel("Server Port: " + PORT);
        threadCountLabel = new JLabel("Active Threads: 0");

        JPanel infoPanel = new JPanel(new GridLayout(1, 3, 8, 8));
        infoPanel.add(statusLabel);
        infoPanel.add(portLabel);
        infoPanel.add(threadCountLabel);

        topPanel.add(titleLabel);
        topPanel.add(infoPanel);
        topPanel.add(new JSeparator());

        monitorArea = new JTextArea();
        monitorArea.setEditable(false);
        monitorArea.setLineWrap(true);
        monitorArea.setWrapStyleWord(true);
        monitorArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane monitorScroll = new JScrollPane(monitorArea);
        monitorScroll.setBorder(BorderFactory.createTitledBorder("Server Logs"));

        userListModel = new DefaultListModel<String>();
        JList<String> userList = new JList<String>(userListModel);

        threadListModel = new DefaultListModel<String>();
        JList<String> threadList = new JList<String>(threadListModel);

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        rightPanel.setPreferredSize(new Dimension(250, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));

        rightPanel.add(wrapInPanel("Connected Users", new JScrollPane(userList)));
        rightPanel.add(wrapInPanel("Active Threads", new JScrollPane(threadList)));

        add(topPanel, BorderLayout.NORTH);
        add(monitorScroll, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    private JPanel wrapInPanel(String title, Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void startServer() {
        Thread serverThread = new Thread(new Runnable() {
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                    updateStatus("Server Status: Online");
                    log("Server started on port " + PORT);

                    while (true) {
                        Socket socket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(socket);
                        handler.start();
                    }

                } catch (IOException e) {
                    updateStatus("Server Status: Offline");
                    log("Server error: " + e.getMessage());
                }
            }
        });

        serverThread.setName("Server-Acceptor-Thread");
        serverThread.start();
    }

    private boolean validateLogin(String username, String password) {
        return username != null
                && username.trim().length() > 0
                && password != null
                && password.equals("1234")
                && !clients.containsKey(username);
    }

    private String timeNow() {
        return new SimpleDateFormat("hh:mm a").format(new Date());
    }

    private void log(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                monitorArea.append(message + "\n");
                monitorArea.setCaretPosition(monitorArea.getDocument().getLength());
            }
        });
    }

    private void updateStatus(final String status) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    private void updateAdminLists() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                userListModel.clear();
                threadListModel.clear();

                synchronized (clients) {
                    for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                        userListModel.addElement(entry.getKey());
                        threadListModel.addElement(entry.getValue().getName());
                    }

                    threadCountLabel.setText("Active Threads: " + clients.size());
                }
            }
        });
    }

    private void saveHistory(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            writer.println(message);
        } catch (IOException e) {
            log("History save error: " + e.getMessage());
        }
    }

    private ArrayList<String> loadHistory() {
        ArrayList<String> history = new ArrayList<String>();
        File file = new File(HISTORY_FILE);

        if (!file.exists()) {
            return history;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                history.add(line);
            }
        } catch (IOException e) {
            log("History load error: " + e.getMessage());
        }

        return history;
    }

    private void sendUserListToAll() {
        ChatPacket packet = new ChatPacket(ChatPacket.Type.USER_LIST);

        synchronized (clients) {
            packet.users.addAll(clients.keySet());

            for (ClientHandler client : clients.values()) {
                client.send(packet);
            }
        }
    }

    private void sendRoomListToAll() {
        ChatPacket packet = new ChatPacket(ChatPacket.Type.ROOM_LIST);

        synchronized (rooms) {
            packet.rooms.addAll(rooms.keySet());
        }

        synchronized (clients) {
            for (ClientHandler client : clients.values()) {
                client.send(packet);
            }
        }
    }

    private void sendToRoom(String room, ChatPacket packet) {
        synchronized (rooms) {
            Set<String> members = rooms.get(room);

            if (members == null) {
                return;
            }

            for (String user : members) {
                ClientHandler client = clients.get(user);

                if (client != null) {
                    client.send(packet);
                }
            }
        }
    }

    private void sendToRoomExcept(String room, String excludedUser, ChatPacket packet) {
        synchronized (rooms) {
            Set<String> members = rooms.get(room);

            if (members == null) {
                return;
            }

            for (String user : members) {
                if (!user.equals(excludedUser)) {
                    ClientHandler client = clients.get(user);

                    if (client != null) {
                        client.send(packet);
                    }
                }
            }
        }
    }

    private void systemMessageToRoom(String room, String message) {
        String fullMessage = "[" + timeNow() + "] Server: " + message;

        ChatPacket packet = new ChatPacket(ChatPacket.Type.SYSTEM);
        packet.message = fullMessage;
        packet.room = room;

        log(fullMessage);
        saveHistory(fullMessage);
        sendToRoom(room, packet);
    }

    class ClientHandler extends Thread {

        private Socket socket;
        private ObjectOutputStream output;
        private ObjectInputStream input;
        private String username = "";
        private String currentRoom = "General";
        private boolean disconnected = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                output = new ObjectOutputStream(socket.getOutputStream());
                output.flush();
                input = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    ChatPacket packet = (ChatPacket) input.readObject();
                    handlePacket(packet);
                }

            } catch (IOException e) {
                disconnectClient();
            } catch (ClassNotFoundException e) {
                disconnectClient();
            }
        }

        private void handlePacket(ChatPacket packet) {
            if (packet == null || packet.type == null) {
                return;
            }

            switch (packet.type) {
                case LOGIN:
                    handleLogin(packet);
                    break;

                case MESSAGE:
                    handlePublicMessage(packet.message);
                    break;

                case PRIVATE_MESSAGE:
                    handlePrivateMessage(packet.receiver, packet.message);
                    break;

                case ECHO:
                    handleEcho(packet.message);
                    break;

                case CREATE_ROOM:
                    handleCreateRoom(packet.room);
                    break;

                case JOIN_ROOM:
                    handleJoinRoom(packet.room);
                    break;

                case LEAVE_ROOM:
                    handleJoinRoom("General");
                    break;

                case TYPING:
                    handleTyping();
                    break;

                case FILE_DATA:
                    handleFileTransfer(packet);
                    break;

                default:
                    break;
            }
        }

        private void handleLogin(ChatPacket packet) {
            ChatPacket response = new ChatPacket(ChatPacket.Type.LOGIN_RESPONSE);
            String requestedUsername = packet.username.trim();

            synchronized (clients) {
                if (validateLogin(requestedUsername, packet.password)) {
                    username = requestedUsername;
                    clients.put(username, this);

                    synchronized (rooms) {
                        rooms.get("General").add(username);
                    }

                    setName("Thread-" + getId() + " handles " + username);

                    response.success = true;
                    response.message = "Login successful.";
                    send(response);

                    ChatPacket historyPacket = new ChatPacket(ChatPacket.Type.HISTORY);
                    historyPacket.history.addAll(loadHistory());
                    send(historyPacket);

                    sendRoomListToAll();
                    sendUserListToAll();
                    updateAdminLists();

                    log(username + " connected.");
                    systemMessageToRoom("General", username + " joined the chat.");

                } else {
                    response.success = false;
                    response.message = "Invalid login. Use password 1234 or choose another username.";
                    send(response);
                }
            }
        }

        private void handlePublicMessage(String message) {
            String fullMessage = "[" + timeNow() + "] " + username + ": " + message;

            ChatPacket packet = new ChatPacket(ChatPacket.Type.MESSAGE);
            packet.username = username;
            packet.room = currentRoom;
            packet.message = fullMessage;

            log(fullMessage);
            saveHistory(fullMessage);
            sendToRoom(currentRoom, packet);
        }

        private void handlePrivateMessage(String receiver, String message) {
            String fullMessage = "[" + timeNow() + "] [PRIVATE] "
                    + username + " -> " + receiver + ": " + message;

            ChatPacket packet = new ChatPacket(ChatPacket.Type.PRIVATE_MESSAGE);
            packet.username = username;
            packet.receiver = receiver;
            packet.message = fullMessage;

            synchronized (clients) {
                ClientHandler target = clients.get(receiver);
                ClientHandler sender = clients.get(username);

                if (target != null) {
                    target.send(packet);

                    if (sender != null) {
                        sender.send(packet);
                    }

                    log(fullMessage);
                    saveHistory(fullMessage);

                } else if (sender != null) {
                    ChatPacket error = new ChatPacket(ChatPacket.Type.SYSTEM);
                    error.message = "[" + timeNow() + "] Server: User is not available.";
                    sender.send(error);
                }
            }
        }

        private void handleEcho(String message) {
            String echoMessage = "[" + timeNow() + "] Server: " + message;

            ChatPacket packet = new ChatPacket(ChatPacket.Type.ECHO);
            packet.message = echoMessage;

            log("[ECHO] " + username + " -> Server: " + message);
            send(packet);
        }

        private void handleCreateRoom(String roomName) {
            if (roomName == null || roomName.trim().length() == 0) {
                return;
            }

            roomName = roomName.trim();

            synchronized (rooms) {
                if (!rooms.containsKey(roomName)) {
                    rooms.put(roomName, new LinkedHashSet<String>());
                }
            }

            handleJoinRoom(roomName);
            sendRoomListToAll();
        }

        private void handleJoinRoom(String newRoom) {
            if (newRoom == null || newRoom.trim().length() == 0) {
                return;
            }

            newRoom = newRoom.trim();

            synchronized (rooms) {
                if (!rooms.containsKey(newRoom)) {
                    rooms.put(newRoom, new LinkedHashSet<String>());
                }

                Set<String> oldMembers = rooms.get(currentRoom);
                if (oldMembers != null) {
                    oldMembers.remove(username);
                }

                currentRoom = newRoom;
                rooms.get(currentRoom).add(username);
            }

            ChatPacket packet = new ChatPacket(ChatPacket.Type.SYSTEM);
            packet.message = "[" + timeNow() + "] Server: You joined room " + currentRoom;
            packet.room = currentRoom;
            send(packet);

            systemMessageToRoom(currentRoom, username + " is now in room " + currentRoom + ".");
            sendRoomListToAll();
        }

        private void handleTyping() {
            ChatPacket packet = new ChatPacket(ChatPacket.Type.TYPING);
            packet.username = username;
            packet.room = currentRoom;
            packet.message = username + " is typing...";

            sendToRoomExcept(currentRoom, username, packet);
        }

        private void handleFileTransfer(ChatPacket packet) {
            String fullMessage = "[" + timeNow() + "] Server: "
                    + username + " sent a file: " + packet.fileName;

            ChatPacket filePacket = new ChatPacket(ChatPacket.Type.FILE_DATA);
            filePacket.username = username;
            filePacket.receiver = packet.receiver;
            filePacket.room = currentRoom;
            filePacket.fileName = packet.fileName;
            filePacket.fileData = packet.fileData;
            filePacket.message = fullMessage;

            if (packet.receiver == null || packet.receiver.equals("All")) {
                sendToRoomExcept(currentRoom, username, filePacket);
            } else {
                ClientHandler target = clients.get(packet.receiver);

                if (target != null) {
                    target.send(filePacket);
                }
            }

            ChatPacket senderNotice = new ChatPacket(ChatPacket.Type.SYSTEM);
            senderNotice.message = fullMessage;
            send(senderNotice);

            log(fullMessage);
            saveHistory(fullMessage);
        }

        private void disconnectClient() {
            if (disconnected) {
                return;
            }

            disconnected = true;

            if (username == null || username.length() == 0) {
                closeSocket();
                return;
            }

            synchronized (clients) {
                clients.remove(username);
            }

            synchronized (rooms) {
                Set<String> members = rooms.get(currentRoom);
                if (members != null) {
                    members.remove(username);
                }
            }

            log(username + " disconnected.");
            systemMessageToRoom(currentRoom, username + " left the chat.");

            sendUserListToAll();
            sendRoomListToAll();
            updateAdminLists();
            closeSocket();
        }

        private void closeSocket() {
            try {
                socket.close();
            } catch (IOException e) {
                log("Socket close error: " + e.getMessage());
            }
        }

        public synchronized void send(ChatPacket packet) {
            try {
                if (output != null) {
                    output.writeObject(packet);
                    output.flush();
                    output.reset();
                }
            } catch (IOException e) {
                disconnectClient();
            }
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
                new ChatServer();
            }
        });
    }
}
