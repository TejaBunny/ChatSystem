//package server;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
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
    private final String serverId;
    private final Gson gson = new Gson();
    private final ConcurrentHashMap<String, Set<WebSocket>> roomSessions = new ConcurrentHashMap<>();

    private Connection rmqConnection;
    private Channel publisherChannel;

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        this.serverId = "server-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public void onStart() {
        System.out.println("✅ ChatServer started on port: " + getPort());
        try {
            setupRabbitMQ();
            startBroadcastListener();
        } catch (Exception e) {
            System.err.println("❌ Fatal RabbitMQ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private void setupRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(ServerConfig.RMQ_HOST);
        // FIX 1: Add Credentials
        factory.setUsername("admin");
        factory.setPassword("Ringster18@1");

        this.rmqConnection = factory.newConnection();
        this.publisherChannel = rmqConnection.createChannel();
        publisherChannel.exchangeDeclare(ServerConfig.EXCHANGE_NAME, "topic", true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String path = handshake.getResourceDescriptor();
        String roomId = parseRoomId(path);
        if (roomId == null) {
            conn.close(1008, "Invalid Room ID");
            return;
        }
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(conn);
        conn.setAttachment(roomId);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String roomId = conn.getAttachment();
        if (roomId != null && roomSessions.containsKey(roomId)) {
            roomSessions.get(roomId).remove(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String roomId = conn.getAttachment();
        try {
            ChatMessage chatMsg = gson.fromJson(message, ChatMessage.class);
            if (!chatMsg.isValid()) return;

//            chatMsg.setServerId(this.serverId);
//            chatMsg.setRoomId(roomId);

            chatMsg.setServerId(this.serverId);
            chatMsg.setRoomId(roomId);
            String routingKey = "room." + roomId;
            String jsonPayload = gson.toJson(chatMsg);

            synchronized (publisherChannel) {
                publisherChannel.basicPublish(
                        ServerConfig.EXCHANGE_NAME,
                        routingKey,
                        null,
                        jsonPayload.getBytes(StandardCharsets.UTF_8)
                );
            }

            // FIX 2: Send ACK back to client
            // Simple ACK format: "ACK:<MessageID>"
            conn.send("ACK:" + chatMsg.getMessageId());

        } catch (Exception e) {
            System.err.println("Publish Error: " + e.getMessage());
            conn.close(1011, "Server Error"); // Close conn so client knows it failed
        }
    }

    // ... (rest of onError, startBroadcastListener, parseRoomId, main same as before) ...
    @Override public void onError(WebSocket conn, Exception ex) {}

    private void startBroadcastListener() {
        new Thread(() -> {
            try {
                Channel broadcastChannel = rmqConnection.createChannel();
                String queueName = broadcastChannel.queueDeclare().getQueue();
                broadcastChannel.queueBind(queueName, ServerConfig.EXCHANGE_NAME, "room.#");

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    String rKey = delivery.getEnvelope().getRoutingKey();
                    String targetRoom = rKey.replace("room.", "");
                    broadcastToLocalUsers(targetRoom, json);
                };
                broadcastChannel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void broadcastToLocalUsers(String roomId, String jsonMessage) {
        Set<WebSocket> clients = roomSessions.get(roomId);
        if (clients != null) {
            for (WebSocket client : clients) {
                if (client.isOpen()) client.send(jsonMessage);
            }
        }
    }

    private String parseRoomId(String path) {
        String[] parts = path.split("/");
        return (parts.length == 3 && "chat".equals(parts[1])) ? parts[2] : null;
    }

    public static void main(String[] args) {
        int port = ServerConfig.PORT;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        new ChatServer(port).start();

//        try {
//            new AnalyticsServer().start();
//        } catch (IOException e) {
//            System.err.println("❌ Failed to start Analytics API: " + e.getMessage());
//        }
    }
}