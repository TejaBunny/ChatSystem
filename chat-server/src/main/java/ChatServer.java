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
