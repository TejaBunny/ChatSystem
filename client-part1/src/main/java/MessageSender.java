

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessageSender implements Runnable {
    private final String serverUrl;
    private final BlockingQueue<String> messageQueue;
    private final CountDownLatch phaseLatch;
    private final int numMessagesToSend;
    private final MetricsCollector metrics;
    private CountDownLatch messageResponseLatch;

    public MessageSender(String serverUrl, BlockingQueue<String> messageQueue, CountDownLatch phaseLatch, int numMessagesToSend) {
        this.serverUrl = serverUrl;
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
                @Override
                public void onOpen(ServerHandshake h) {}
                @Override
                public void onMessage(String m) {
                    if (messageResponseLatch != null) messageResponseLatch.countDown();
                }
                @Override
                public void onClose(int c, String r, boolean rem) {}
                @Override
                public void onError(Exception e) { e.printStackTrace(); }
            };

            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Thread " + Thread.currentThread().getId() + " failed to connect.");
            }
            metrics.incrementConnections();

            for (int i = 0; i < numMessagesToSend; i++) {
                String message = messageQueue.take();
                messagesProcessed++;
                boolean messageSucceeded = false;

                // THIS IS THE RETRY LOOP
                for (int attempt = 0; attempt < 6; attempt++) {
                    // Ensure connection is open before attempting to send
                    if (!client.isOpen()) {
                        metrics.incrementReconnections();
                        if (!client.reconnectBlocking()) {
                            // Wait before next reconnect attempt
                            Thread.sleep(100);
                            continue; // Skip to next attempt
                        }
                    }

                    messageResponseLatch = new CountDownLatch(1);
                    client.send(message);

                    // Wait for the server's confirmation on this attempt
                    if (messageResponseLatch.await(5, TimeUnit.SECONDS)) {
                        messageSucceeded = true;
                        break; // Success! Exit the retry loop.
                    } else {
                        System.out.println("Retried");
                        // This attempt timed out. Loop will continue to the next attempt.
                        // Apply exponential backoff only if it's not the last attempt
                        if (attempt < 5) {
                            Thread.sleep((long) Math.pow(2, attempt) * 100);
                        }
                    }
                }

                if (messageSucceeded) {
                    metrics.incrementSuccess();
                } else {
                    metrics.incrementFailure(); // Mark as failed only after all 6 attempts fail
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + Thread.currentThread().getId() + " failed: " + e.getMessage());
            int unprocessed = numMessagesToSend - messagesProcessed;
            for (int i = 0; i < unprocessed; i++) {
                metrics.incrementFailure();
            }
        } finally {
            if (client != null) {
                client.close();
            }
            phaseLatch.countDown();
        }
    }
}
