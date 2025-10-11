
import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessageSender implements Runnable {
    private final String serverUrl;
    private final String roomId;
    private final BlockingQueue<String> messageQueue;
    private final CountDownLatch phaseLatch;
    private final int numMessagesToSend;
    private final MetricsCollector metrics;
    private final Gson gson = new Gson();
    private CountDownLatch messageResponseLatch;

    public MessageSender(String serverUrl, String roomId, BlockingQueue<String> messageQueue, CountDownLatch phaseLatch, int numMessagesToSend) {
        this.serverUrl = serverUrl;
        this.roomId = roomId;
        this.messageQueue = messageQueue;
        this.phaseLatch = phaseLatch;
        this.numMessagesToSend = numMessagesToSend;
        this.metrics = MetricsCollector.getInstance();
    }

    @Override
    public void run() {
        WebSocketClient client = null;
        int messagesProcessed = 0;
        try {
            client = new WebSocketClient(new URI(serverUrl)) {
                @Override public void onOpen(ServerHandshake h) {}
                @Override public void onMessage(String m) {
                    if (messageResponseLatch != null) messageResponseLatch.countDown();
                }
                @Override public void onClose(int c, String r, boolean rem) {}
                @Override public void onError(Exception e) { e.printStackTrace(); }
            };

            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Thread " + Thread.currentThread().getId() + " failed to connect.");
            }
            metrics.incrementConnections();
            for (int i = 0; i < numMessagesToSend; i++) {
                String messageJson = messageQueue.take();
                messagesProcessed++;

                // Extract message type for reporting
                String messageType = gson.fromJson(messageJson, ChatMessage.class).getMessageType();
                boolean messageSucceeded = false;
                long latency = -1;
                long startTime = 0;

                for (int attempt = 0; attempt < 6; attempt++) {
                    if (!client.isOpen()) {
                        metrics.incrementReconnections();
                        if (!client.reconnectBlocking()) {
                            Thread.sleep(100); continue;
                        }
                    }

                    messageResponseLatch = new CountDownLatch(1);
                    startTime = System.nanoTime();
                    client.send(messageJson);

                    if (messageResponseLatch.await(5, TimeUnit.SECONDS)) {
                        long endTime = System.nanoTime();
                        latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                        messageSucceeded = true;
                        break;
                    } else {
                        if (attempt < 5) {
                            Thread.sleep((long) Math.pow(2, attempt) * 100);
                        }
                    }
                }

                long requestTimestamp = System.currentTimeMillis();
                if (messageSucceeded) {
                    metrics.recordRequest(requestTimestamp, messageType, latency, 200, roomId);
                } else {
                    metrics.recordRequest(requestTimestamp, messageType, -1, 500, roomId);
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + Thread.currentThread().getId() + " failed: " + e.getMessage());
            int unprocessed = numMessagesToSend - messagesProcessed;
            for (int i = 0; i < unprocessed; i++) {
                metrics.recordRequest(System.currentTimeMillis(), "UNKNOWN", -1, 500, roomId);
            }
        } finally {
            if (client != null) {
                client.close();
            }
            phaseLatch.countDown();
        }
    }
}
