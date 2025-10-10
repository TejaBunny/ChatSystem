//import org.java_websocket.server.WebSocketServer;
//import org.java_websocket.handshake.ClientHandshake;
//import org.java_websocket.WebSocket;
//import java.net.InetSocketAddress;
//import com.google.gson.Gson;
//
//public class ChatServer extends WebSocketServer {
//    private Gson gson = new Gson();
//    public ChatServer(int port) {
//        super(new InetSocketAddress(port));
//    }
//
//    @Override
//    public void onOpen(WebSocket conn, ClientHandshake handshake) {
//        // Called when a new client connects.
//        System.out.println("New connection from: " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
//
//        // In onOpen method
//        String path = handshake.getResourceDescriptor(); // Gets the path, e.g., "/chat/12"
//// Simple parsing to get the roomId
//        String roomId = path.substring(path.lastIndexOf('/') + 1);
//        conn.setAttachment(roomId); // Attach the roomId to the connection object
//        System.out.println("Client joined room: " + roomId);
//    }
//
//    @Override
//    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
//        // Called when a client disconnects.
//        System.out.println("Closed connection to: " + conn.getRemoteSocketAddress().getAddress().getHostAddress());
//    }
//
//    @Override
//    public void onMessage(WebSocket conn, String message) {
//        // This is where all the magic happens!
//        // Called every time a client sends a message.
//        System.out.println("Received message: " + message);
//
//        // TODO: Add validation and echo logic here.
//        conn.send("Echo from server: " + message); // Simple echo for now
//
//        // Inside the onMessage method
//
//
//        try {
//            ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);
//
//            // Perform validation
//            if (isMessageValid(chatMessage)) {
//                // If valid, echo it back with server info
//                // TODO: Create a response object and send it back
//                conn.send("Message is valid and received by server!");
//            } else {
//                // If invalid, send an error message
//                conn.send("Error: Invalid message format or content.");
//            }
//        } catch (Exception e) {
//            // If the JSON is malformed
//            conn.send("Error: Malformed JSON.");
//        }
//    }
//
//    @Override
//    public void onError(WebSocket conn, Exception ex) {
//        // Called on any error.
//        ex.printStackTrace();
//    }
//
//    @Override
//    public void onStart() {
//        System.out.println("Server started successfully on port " + getPort());
//    }
//
//    private boolean isMessageValid(ChatMessage msg) {
//        if (msg == null) return false;
//
//        // userId must be between 1 and 100000
//        try {
//            int userId = Integer.parseInt(msg.getUserId());
//            if (userId < 1 || userId > 100000) return false;
//        } catch (NumberFormatException e) {
//            return false;
//        }
//
//        // username must be 3-20 alphanumeric characters
//        if (msg.getUsername() == null || !msg.getUsername().matches("[a-zA-Z0-9]{3,20}")) {
//            return false;
//        }
//
//        // message must be 1-500 characters
//        if (msg.getMessage() == null || msg.getMessage().length() < 1 || msg.getMessage().length() > 500) {
//            return false;
//        }
//
//        // messageType must be TEXT, JOIN, or LEAVE
//        String type = msg.getMessageType();
//        if (type == null || !(type.equals("TEXT") || type.equals("JOIN") || type.equals("LEAVE"))) {
//            return false;
//        }
//
//        // TODO: Add timestamp validation (ISO-8601)
//
//        return true;
//    }
//
//    public static void main(String[] args) {
//        int port = 8080; // The port your server will listen on
//        ChatServer server = new ChatServer(port);
//        server.start();
//    }
//}

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ChatServer extends WebSocketServer {

    private final Gson gson = new Gson();

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // This method is called when a new client connects to the server.
        String remoteAddr = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("Connection opened from: " + remoteAddr);

        // The endpoint is expected to be "/chat/{roomId}"
        String path = handshake.getResourceDescriptor();
        String[] pathParts = path.split("/");

        if (pathParts.length == 3 && pathParts[1].equals("chat")) {
            String roomId = pathParts[2];
            conn.setAttachment(roomId); // Store the roomId with the connection
            System.out.println("Client joined room: " + roomId);
        } else {
            System.out.println("Invalid endpoint, closing connection: " + path);
            conn.close(1008, "Invalid endpoint. Must be in the format /chat/{roomId}");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        // Called when a client's connection is closed.
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // Called every time the server receives a message from a client.
        try {
            // Attempt to parse the incoming message string into our ChatMessage object.
            ChatMessage chatMessage = gson.fromJson(message, ChatMessage.class);

            // Validate the message object using the method we defined in ChatMessage.
            if (chatMessage != null && chatMessage.isValid()) {
                // If the message is valid, create a success response.
                Map<String, String> response = new HashMap<>();
                response.put("status", "received");
                response.put("serverTimestamp", Instant.now().toString());
                response.put("originalMessage", gson.toJson(chatMessage));

                // Echo the success response back to the sender.
                conn.send(gson.toJson(response));
            } else {
                // If the message is invalid, send an error response.
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Invalid message content or format.");
                conn.send(gson.toJson(errorResponse));
            }
        } catch (JsonSyntaxException e) {
            // This catches errors if the incoming string is not valid JSON.
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Malformed JSON.");
            conn.send(gson.toJson(errorResponse));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // Called when an error occurs on the connection.
        System.err.println("An error occurred on connection " + (conn != null ? conn.getRemoteSocketAddress() : "UNKNOWN") + ":" + ex);
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        // Called when the server is started successfully.
        System.out.println("Server started successfully on port: " + getPort());
        // A simple GET endpoint for /health can be confirmed by the server running.
        // In a real-world scenario, a lightweight HTTP server would run alongside.
        // For this assignment, if you can connect, it's "healthy".
    }

    public static void main(String[] args) {
        // Define the port the server will run on. Default to 8080.
        int port = 8080;
        try {
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number. Defaulting to 8080.");
        }

        // Create and start the server instance.
        ChatServer server = new ChatServer(port);
        server.start();
    }
}