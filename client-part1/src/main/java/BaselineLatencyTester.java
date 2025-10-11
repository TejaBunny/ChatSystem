import com.google.gson.Gson;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BaselineLatencyTester {

    // IMPORTANT: Replace this with your EC2 server's public IP address
    private static final String SERVER_URL = "ws://<EC2_PUBLIC_IP>:8080/chat/1";
    private static final int NUM_TESTS = 10;

    public static void main(String[] args) throws InterruptedException {
        long totalLatency = 0;
        System.out.println("Starting baseline latency test against " + SERVER_URL);

        for (int i = 0; i < NUM_TESTS; i++) {
            totalLatency += getSingleRequestLatency();
            // A small pause between tests
            Thread.sleep(200);
        }

        long averageLatency = totalLatency / NUM_TESTS;
        System.out.println("\n-------------------------------------");
        System.out.println("Average Baseline Latency: " + averageLatency + " ms");
        System.out.println("-------------------------------------");
    }

    private static long getSingleRequestLatency() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final long[] latency = new long[1];

        try {
            WebSocketClient client = new WebSocketClient(new URI(SERVER_URL)) {
                private long startTime;

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    ChatMessage msg = new ChatMessage();
                    msg.setUserId("101");
                    msg.setUsername("latency_tester");
                    msg.setMessage("ping");
                    msg.setTimestamp(Instant.now().toString());
                    msg.setMessageType("TEXT");

                    startTime = System.nanoTime();
                    send(new Gson().toJson(msg));
                }

                @Override
                public void onMessage(String message) {
                    long endTime = System.nanoTime();
                    latency[0] = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                    latch.countDown();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {}

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                    latch.countDown();
                }
            };

            client.connect();
            if (latch.await(10, TimeUnit.SECONDS)) {
                System.out.print(".");
            } else {
                System.err.println("\nTest timed out.");
            }
            client.close();
            return latency[0];

        } catch (URISyntaxException e) {
            System.err.println("Invalid server URL: " + SERVER_URL);
            return -1;
        }
    }
}
