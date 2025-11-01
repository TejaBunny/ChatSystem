

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeoutException;

public class ChatServer extends WebSocketServer {

    private final Gson gson = new Gson();
    private final ChannelPool channelPool;
    private static final String EXCHANGE_NAME = "chat.exchange";

    private final ConcurrentHashMap<String, Set<WebSocket>> roomSessions;

    public ChatServer(int port) throws Exception {
        super(new InetSocketAddress(port));
        this.channelPool = ChannelPoolManager.getPool();
        if (this.channelPool == null) {
            throw new RuntimeException("ChannelPoolManager failed to initialize.");
        }
        this.roomSessions = new ConcurrentHashMap<>();
        startConsumers();
    }





    private void startConsumers() {
        System.out.println("Initializing session maps for all 20 rooms...");
        // Initialize the session maps for all 20 rooms
        for (int i = 1; i <= 20; i++) {
            roomSessions.put(String.valueOf(i), new CopyOnWriteArraySet<>());
        }

        // Start a small pool of consumers for this server instance.
        // These will all listen on their own unique queues.
        try {
            int numConsumers = 40; // Configurable: number of consumer threads for this server
            for (int i=0; i < numConsumers; i++) {
                Thread consumerThread = new Thread(new QueueConsumer(roomSessions));
                consumerThread.start();
            }
            System.out.println("All " + numConsumers + " consumers for this server started.");
        } catch ( Exception e) {
            System.err.println("FATAL: Failed to create QueueConsumer");
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String remoteAddr = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("Connection opened from: " + remoteAddr);

        String path = handshake.getResourceDescriptor();
        String roomId = getRoomIdFromPath(path);

        if (roomId != null) {
            conn.setAttachment(roomId);

            Set<WebSocket> sessions = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
            sessions.add(conn);
            System.out.println("Client joined room: " + roomId + ". Total clients in room: " + sessions.size());

        } else {
            System.out.println("Closing connection due to invalid path: " + path);
            conn.close(1008, "Invalid endpoint. Must be in the format /chat/{roomId}");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = conn.getAttachment();
        if (roomId != null) {
            Set<WebSocket> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(conn);
                System.out.println("Client left room: " + roomId + ". Total clients in room: " + sessions.size());
            }
        }
        System.out.println("Connection closed from: " + conn.getRemoteSocketAddress() + " Code: " + code + " Reason: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ChatMessage chatMessage = null;
        String roomId = conn.getAttachment();
        try {
            chatMessage = gson.fromJson(message, ChatMessage.class);
        } catch (Exception e) {
            System.err.println("Failed to parse incoming JSON: " + message + " Error: " + e.getMessage());
            conn.send("{\"status\":\"error\", \"message\":\"Malformed JSON\"}");
            return;
        }

        if (roomId != null && chatMessage != null && chatMessage.isValid()) {
            Channel channel = null;
            try {
                QueueMessage queueMessage = new QueueMessage(
                        UUID.randomUUID().toString(), roomId, chatMessage.getUserId(), chatMessage.getUsername(),
                        chatMessage.getMessage(), chatMessage.getTimestamp(), chatMessage.getMessageType(),
                        "server-instance-id-01", conn.getRemoteSocketAddress().getAddress().getHostAddress()
                );

                channel = channelPool.borrowChannel();
                String routingKey = "room." + roomId;
                channel.basicPublish(EXCHANGE_NAME, routingKey, null,
                        gson.toJson(queueMessage).getBytes(StandardCharsets.UTF_8));

            } catch (Exception e) {
                System.err.println("Failed to publish message to RabbitMQ: " + e.getMessage());
                conn.send("{\"status\":\"error\", \"message\":\"Failed to process message internally.\"}");
            } finally {
                if (channel != null) {
                    channelPool.returnChannel(channel);
                }
            }
        } else {
            System.out.println("Received invalid message or missing room ID. RoomID: " + roomId + ", Message: " + message);
            conn.send("{\"status\":\"error\", \"message\":\"Invalid message content or format, or missing room context.\"}");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // We no longer check for the health check exception
        System.err.println("An error occurred on connection " + (conn != null ? conn.getRemoteSocketAddress() : "UNKNOWN") + ":" + ex);
        // ex.printStackTrace(); // Optional
    }

    @Override
    public void onStart() {
//        System.out.println("Server started successfully on port: " + getPort());
        System.out.println("--- ChatServer v2.3 (Message Validation Fix) IS RUNNING ---");
        System.out.println("Server started successfully on port: " + getPort());
    }

    private String getRoomIdFromPath(String path) {
        if (path == null) return null;
        String[] pathParts = path.split("/");
        if (pathParts.length == 3 && pathParts[1].equals("chat")) {
            try {
                int rId = Integer.parseInt(pathParts[2]);
                if (rId >= 1 && rId <= 20) {
                    return pathParts[2];
                }
            } catch (NumberFormatException e) {
                // Invalid room ID format
            }
        }
        return null;
    }

    public static void main(String[] args) {
        int port = 8080;
        try {
            ChatServer server = new ChatServer(port);
            server.start();
        } catch (Exception e) {
            System.err.println("FATAL: Failed to start ChatServer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}