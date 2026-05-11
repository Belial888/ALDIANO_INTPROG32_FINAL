import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
   private static final int PORT = 8000;
   private static final Map<String, ClientHandler> clients = new HashMap<>();


   public static void main(String[] args) {
      System.out.println("Server started on port " + PORT);
   
      try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      
         while (true) {
         
            Socket socket = serverSocket.accept();
         
            new ClientHandler(socket).start();
         }
      
      } catch (IOException e) {
      
         e.printStackTrace();
      
      }
   
   }

   static synchronized void broadcast(String message) {
   
      for (ClientHandler client : clients.values()) {
      
         client.send(message);
      
      }
   }

   static synchronized void privateMessage(String sender, String receiver, String message) {
   
      ClientHandler target = clients.get(receiver);
   
   
   
      if (target != null) {
      
         target.send("[Private] " + sender + ": " + message);
      
         clients.get(sender).send("[Private to " + receiver + "] You: " + message);
      }
   }


   static synchronized void updateUserList() {
   
      String users = "USERLIST:" + String.join(",", clients.keySet());
   
   
      for (ClientHandler client : clients.values()) {
      
         client.send(users);
      
      }
   }

   static class ClientHandler extends Thread {
   
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
         
            synchronized (ChatServer.class) {
            
               clients.put(username, this);
            
            }
         
         
         
            broadcast("Server: " + username + " joined the chat.");
         
            updateUserList();
         
         
            String message;
         
         
         
            while ((message = input.readLine()) != null) {
            
               if (message.startsWith("PRIVATE:")) {
               
                  String[] parts = message.split(":", 3);
               
                  String receiver = parts[1];
               
                  String text = parts[2];
               
               
               
                  privateMessage(username, receiver, text);
               
               } else {
               
                  broadcast(username + ": " + message);
               
               }
            
            }
         
         
         
         } catch (IOException e) {
         
            System.out.println(username + " disconnected.");
         
         } finally {
         
            try {
            
               synchronized (ChatServer.class) {
               
                  clients.remove(username);
               
               }
            
            
            
               broadcast("Server: " + username + " left the chat.");
            
               updateUserList();
            
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

}