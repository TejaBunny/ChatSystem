// The below version doesn't wait for server

//import org.java_websocket.client.WebSocketClient;




//import org.java_websocket.handshake.ServerHandshake;
//
//import java.net.URI;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//public class MessageSender implements Runnable {
//    private final String serverUrl;
//    private final BlockingQueue<String> messageQueue;
//    private final CountDownLatch latch;
//    private final int numMessagesToSend;
//    private final MetricsCollector metrics;
//
//    public MessageSender(String serverUrl, BlockingQueue<String> messageQueue, CountDownLatch latch, int numMessagesToSend) {
//        this.serverUrl = serverUrl;
//        this.messageQueue = messageQueue;
//        this.latch = latch;
//        this.numMessagesToSend = numMessagesToSend;
//        this.metrics = MetricsCollector.getInstance();
//    }
//
//    @Override
//    public void run() {
//        try {
//            WebSocketClient client = new WebSocketClient(new URI(serverUrl)) {
//                @Override public void onOpen(ServerHandshake h) {}
//                @Override public void onMessage(String m) {}
//                @Override public void onClose(int c, String r, boolean rem) {}
//                @Override public void onError(Exception e) {
//                    System.err.println("WebSocket error from thread " + Thread.currentThread().getId() + ": " + e.getMessage());
//                }
//            };
//
//            if (client.connectBlocking(5, TimeUnit.SECONDS)) {
//                metrics.incrementConnections();
//            } else {
//                System.err.println("Thread " + Thread.currentThread().getId() + " failed to connect.");
//                latch.countDown();
//                return;
//            }
//
//            for (int i = 0; i < numMessagesToSend; i++) {
//                String message = messageQueue.take();
//
//                // Retry logic for sending message
//                boolean success = false;
//                for (int attempt = 0; attempt < 5; attempt++) {
//                    if (client.isOpen()) {
//                        client.send(message);
//                        success = true;
//                        break; // Exit retry loop on success
//                    } else {
//                        // Attempt to reconnect with exponential backoff
//                        metrics.incrementReconnections();
//                        client.reconnectBlocking();
//                        Thread.sleep((long) Math.pow(2, attempt) * 100);
//                    }
//                }
//
//                if (success) {
//                    metrics.incrementSuccess();
//                } else {
//                    metrics.incrementFailure();
//                }
//            }
//            client.close();
//        } catch (Exception e) {
//            metrics.incrementFailure(); // Count exceptions as failures
//            e.printStackTrace();
//        } finally {
//            latch.countDown();
//        }
//    }
//}

// -------------------------

// This version is giving right throughput, but successful messages and failed messages are not summing up to right value


//import com.google.gson.Gson;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//
//import java.net.URI;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//public class MessageSender implements Runnable {
//    private final String serverUrl;
//    private final BlockingQueue<String> messageQueue;
//    private final CountDownLatch phaseLatch;
//    private final int numMessagesToSend;
//    private final MetricsCollector metrics;
//    private CountDownLatch messageResponseLatch;
//
//    public MessageSender(String serverUrl, BlockingQueue<String> messageQueue, CountDownLatch phaseLatch, int numMessagesToSend) {
//        this.serverUrl = serverUrl;
//        this.messageQueue = messageQueue;
//        this.phaseLatch = phaseLatch;
//        this.numMessagesToSend = numMessagesToSend;
//        this.metrics = MetricsCollector.getInstance();
//    }
//
//    @Override
//    public void run() {
//        WebSocketClient client = null;
//        try {
//            client = new WebSocketClient(new URI(serverUrl)) {
//                @Override
//                public void onOpen(ServerHandshake handshakedata) {}
//
//                @Override
//                public void onMessage(String message) {
//                    if (messageResponseLatch != null) {
//                        messageResponseLatch.countDown();
//                    }
//                }
//
//                @Override
//                public void onClose(int code, String reason, boolean remote) {}
//                @Override
//                public void onError(Exception ex) { ex.printStackTrace(); }
//            };
//
//            if (client.connectBlocking(5, TimeUnit.SECONDS)) {
//                metrics.incrementConnections();
//            } else {
//                // Mark all messages for this thread as failed if connection fails
//                for (int i = 0; i < numMessagesToSend; i++) metrics.incrementFailure();
//                phaseLatch.countDown();
//                return;
//            }
//
//            for (int i = 0; i < numMessagesToSend; i++) {
//                String message = messageQueue.take();
//                messageResponseLatch = new CountDownLatch(1);
//
//                // Send the message
//                client.send(message);
//
//                // Wait for the onMessage() callback to release the latch, with a timeout
//                if (messageResponseLatch.await(5, TimeUnit.SECONDS)) {
//                    metrics.incrementSuccess();
//                } else {
//                    metrics.incrementFailure(); // Timeout is a failure
//                }
//            }
//
//        } catch (Exception e) {
//            // Mark remaining messages as failed on major exception
//            int remaining = numMessagesToSend - (metrics.getSuccessfulRequests() + metrics.getFailedRequests());
//            for(int i = 0; i < remaining; i++) metrics.incrementFailure();
//            e.printStackTrace();
//        } finally {
//            if (client != null) {
//                client.close();
//            }
//            phaseLatch.countDown();
//        }
//    }
//}

// This code is not attempting retry thing

//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//
//import java.net.URI;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//public class MessageSender implements Runnable {
//    private final String serverUrl;
//    private final BlockingQueue<String> messageQueue;
//    private final CountDownLatch phaseLatch;
//    private final int numMessagesToSend;
//    private final MetricsCollector metrics;
//    private CountDownLatch messageResponseLatch;
//
//    public MessageSender(String serverUrl, BlockingQueue<String> messageQueue, CountDownLatch phaseLatch, int numMessagesToSend) {
//        this.serverUrl = serverUrl;
//        this.messageQueue = messageQueue;
//        this.phaseLatch = phaseLatch;
//        this.numMessagesToSend = numMessagesToSend;
//        this.metrics = MetricsCollector.getInstance();
//    }
//
//    @Override
//    public void run() {
//        WebSocketClient client = null;
//        int messagesProcessed = 0;
//        try {
//            client = new WebSocketClient(new URI(serverUrl)) {
//                @Override
//                public void onOpen(ServerHandshake handshakedata) {}
//
//                @Override
//                public void onMessage(String message) {
//                    // When a response is received, release the latch for the waiting send loop.
//                    if (messageResponseLatch != null) {
//                        messageResponseLatch.countDown();
//                    }
//                }
//
//                @Override
//                public void onClose(int code, String reason, boolean remote) {}
//                @Override
//                public void onError(Exception ex) {
//                    System.err.println("WebSocket error: " + ex.getMessage());
//                }
//            };
//
//            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
//                throw new RuntimeException("Thread " + Thread.currentThread().getId() + " failed to connect.");
//            }
//            metrics.incrementConnections();
//
//            for (int i = 0; i < numMessagesToSend; i++) {
//                String message = messageQueue.take();
//                messagesProcessed++;
//
//                // Create a new latch for each message to wait for its specific response.
//                messageResponseLatch = new CountDownLatch(1);
//
//                client.send(message);
//
//                // **THIS IS THE CRITICAL FIX**: Wait for the onMessage() callback to release the latch.
//                // If it takes longer than 5 seconds, it's a timeout (a failure).
//                if (messageResponseLatch.await(5, TimeUnit.SECONDS)) {
//                    metrics.incrementSuccess();
//                } else {
//                    metrics.incrementFailure();
//                }
//            }
//
//        } catch (Exception e) {
//            System.err.println("Thread " + Thread.currentThread().getId() + " failed with exception: " + e.getMessage());
//            // Account for all messages this thread was supposed to send but didn't.
//            int unprocessed = numMessagesToSend - messagesProcessed;
//            for(int i = 0; i < unprocessed; i++) {
//                metrics.incrementFailure();
//            }
//        } finally {
//            if (client != null) {
//                client.close();
//            }
//            phaseLatch.countDown();
//        }
//    }
//}

// This version is suitable with retries

//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//
//import java.net.URI;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//public class MessageSender implements Runnable {
//    private final String serverUrl;
//    private final BlockingQueue<String> messageQueue;
//    private final CountDownLatch phaseLatch;
//    private final int numMessagesToSend;
//    private final MetricsCollector metrics;
//    private CountDownLatch messageResponseLatch;
//
//    public MessageSender(String serverUrl, BlockingQueue<String> messageQueue, CountDownLatch phaseLatch, int numMessagesToSend) {
//        this.serverUrl = serverUrl;
//        this.messageQueue = messageQueue;
//        this.phaseLatch = phaseLatch;
//        this.numMessagesToSend = numMessagesToSend;
//        this.metrics = MetricsCollector.getInstance();
//    }
//
//    @Override
//    public void run() {
//        WebSocketClient client = null;
//        int messagesProcessed = 0;
//        try {
//            client = new WebSocketClient(new URI(serverUrl)) {
//                @Override
//                public void onOpen(ServerHandshake h) {}
//                @Override
//                public void onMessage(String m) {
//                    if (messageResponseLatch != null) messageResponseLatch.countDown();
//                }
//                @Override
//                public void onClose(int c, String r, boolean rem) {}
//                @Override
//                public void onError(Exception e) { e.printStackTrace(); }
//            };
//
//            if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
//                throw new RuntimeException("Thread " + Thread.currentThread().getId() + " failed to connect.");
//            }
//            metrics.incrementConnections();
//
//            for (int i = 0; i < numMessagesToSend; i++) {
//                String message = messageQueue.take();
//                messagesProcessed++;
//                boolean messageSucceeded = false;
//
//                // **THIS IS THE CORRECTED RETRY LOOP**
//                for (int attempt = 0; attempt < 6; attempt++) {
//                    // Ensure connection is open before attempting to send
//                    if (!client.isOpen()) {
//                        metrics.incrementReconnections();
//                        if (!client.reconnectBlocking()) {
//                            // Wait before next reconnect attempt
//                            Thread.sleep(100);
//                            continue; // Skip to next attempt
//                        }
//                    }
//
//                    messageResponseLatch = new CountDownLatch(1);
//                    client.send(message);
//
//                    // Wait for the server's confirmation on this attempt
//                    if (messageResponseLatch.await(5, TimeUnit.SECONDS)) {
//                        messageSucceeded = true;
//                        break; // Success! Exit the retry loop.
//                    } else {
//                        System.out.println("Retried");
//                        // This attempt timed out. Loop will continue to the next attempt.
//                        // Apply exponential backoff only if it's not the last attempt
//                        if (attempt < 5) {
//                            Thread.sleep((long) Math.pow(2, attempt) * 100);
//                        }
//                    }
//                }
//
//                if (messageSucceeded) {
//                    metrics.incrementSuccess();
//                } else {
//                    metrics.incrementFailure(); // Mark as failed only after all 5 attempts fail
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Thread " + Thread.currentThread().getId() + " failed: " + e.getMessage());
//            int unprocessed = numMessagesToSend - messagesProcessed;
//            for (int i = 0; i < unprocessed; i++) {
//                metrics.incrementFailure();
//            }
//        } finally {
//            if (client != null) {
//                client.close();
//            }
//            phaseLatch.countDown();
//        }
//    }
//}

// the above version is client part-1 code


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