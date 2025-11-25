//package client;

import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
//import server.ChatMessage;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UserThread implements Runnable {
    private final int threadId;
    private final int numMessages; // New field
    private final CountDownLatch startLatch;
    private final CountDownLatch endLatch;
    private final MetricsCollector metrics;
    private final Gson gson = new Gson();

    // Synchronization for ACKs
    private final AtomicReference<String> pendingMessageId = new AtomicReference<>(null);
    private final AtomicReference<CountDownLatch> ackLatch = new AtomicReference<>(null);

    public UserThread(int threadId, int numMessages, CountDownLatch startLatch, CountDownLatch endLatch) {
        this.threadId = threadId;
        this.numMessages = numMessages;
        this.startLatch = startLatch;
        this.endLatch = endLatch;
        this.metrics = MetricsCollector.getInstance();
    }

    @Override
    public void run() {
        String roomId = String.valueOf((threadId % ClientConfig.NUM_ROOMS) + 1);
        String wsUrl = ClientConfig.SERVER_URL + roomId;
        WebSocketClient client = null;

        try {
            client = new WebSocketClient(new URI(wsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) { }

                @Override
                public void onMessage(String message) {
                    // Check for ACK
                    if (message.startsWith("ACK:")) {
                        String ackId = message.substring(4);
                        String expectedId = pendingMessageId.get();
                        if (expectedId != null && expectedId.equals(ackId)) {
                            CountDownLatch l = ackLatch.get();
                            if (l != null) l.countDown(); // Release the waiting thread
                        }
                    }
                }
                @Override public void onClose(int code, String reason, boolean remote) { }
                @Override public void onError(Exception ex) { }
            };

            client.connectBlocking(5, TimeUnit.SECONDS);
            startLatch.countDown();
            startLatch.await();

            for (int i = 0; i < numMessages; i++) {
                String msgId = UUID.randomUUID().toString();
                ChatMessage msg = new ChatMessage();

                msg.setUserId(String.valueOf(threadId + 1)); // Assuming ChatMessage has this field

                msg.setMessageId(msgId);           // Need to add this to ChatMessage DTO if missing
                msg.setUsername("tester" + threadId);
                msg.setMessage("Test " + i);
                msg.setMessageType("TEXT");
                msg.setTimestamp(Instant.now().toString());

                String json = gson.toJson(msg);

                // Prepare for ACK
                pendingMessageId.set(msgId);
                ackLatch.set(new CountDownLatch(1));

                long start = System.nanoTime();
                client.send(json);

                // Wait for ACK (Max 5 seconds)
                boolean received = ackLatch.get().await(5, TimeUnit.SECONDS);
                long latency = (System.nanoTime() - start) / 1000000;

                if (received) {
                    metrics.record(latency, true);
//                    try { Thread.sleep(20); } catch (InterruptedException e) {}  // THROTTLING For Endurance test
                } else {
                    System.err.println("Thread " + threadId + " timed out waiting for ACK");
                    metrics.record(5000, false);
                }
            }

        } catch (Exception e) {
            metrics.record(0, false);
        } finally {
            if (client != null) client.close();
            endLatch.countDown();
        }
    }
}