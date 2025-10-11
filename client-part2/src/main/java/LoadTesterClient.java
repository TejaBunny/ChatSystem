
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;

public class LoadTesterClient {

    // IMPORTANT: Replace this with your EC2 server's public IP address
    private static final String SERVER_URL_BASE = "ws://<EC2_PUBLIC_IP>:8080/chat/";

    // Warmup phase configuration
    private static final int WARMUP_THREADS = 32;
    private static final int MESSAGES_PER_THREAD_WARMUP = 1000;

    private static final int WARMUP_MESSAGES = WARMUP_THREADS * MESSAGES_PER_THREAD_WARMUP;

    // Main phase configuration
    private static final int TOTAL_MESSAGES = 500000;
    private static final int MAIN_MESSAGES = TOTAL_MESSAGES - WARMUP_MESSAGES;
    private static final int MAIN_THREADS = 128; // Your optimal thread count
    private static final int MESSAGES_PER_THREAD_MAIN = MAIN_MESSAGES / MAIN_THREADS;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(10000);
        MetricsCollector metrics = MetricsCollector.getInstance();
        Random random = new Random();

        // --- Warmup Phase ---
        System.out.println("Starting Warmup Phase...");
        long warmupStartTime = System.currentTimeMillis();
        runPhase(WARMUP_THREADS, WARMUP_MESSAGES, messageQueue, random);
        long warmupEndTime = System.currentTimeMillis();
        System.out.println("Warmup Phase Complete.");

        long warmupDuration = warmupEndTime - warmupStartTime;
        // basic report for the warmup
        printBasicReport("Warmup Phase", WARMUP_THREADS, warmupDuration, metrics);

        metrics.reset(); // Reset metrics for the main phase

        // --- Main Phase ---
        System.out.println("\nStarting Main Testing Phase...");
        long mainStartTime = System.currentTimeMillis();
        runPhase(MAIN_THREADS, MAIN_MESSAGES, messageQueue, random);
        long mainEndTime = System.currentTimeMillis();
        System.out.println("Main Testing Phase Complete.");

        // --- FINAL REPORTING (PART 3) ---
        
        PerformanceReport report = new PerformanceReport(metrics, mainEndTime - mainStartTime);
        report.generateReport();
    }


    private static void runPhase(int numThreads, int totalMessagesInPhase, BlockingQueue<String> queue, Random random) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Thread> senderThreads = new ArrayList<>();

        Thread generator = new Thread(new MessageGenerator(queue, totalMessagesInPhase));
        generator.start();

        int baseMessagesPerThread = totalMessagesInPhase / numThreads;
        int remainderMessages = totalMessagesInPhase % numThreads;

        for (int i = 0; i < numThreads; i++) {
            int messagesForThisThread = baseMessagesPerThread;
            if (i == numThreads - 1) {
                // Give the last thread the remainder
                messagesForThisThread += remainderMessages;
            }

            String roomId = String.valueOf(random.nextInt(20) + 1);
            String url = SERVER_URL_BASE + roomId;
            Thread sender = new Thread(new MessageSender(url, roomId, queue, latch, messagesForThisThread));
            senderThreads.add(sender);
            sender.start();
        }

        latch.await();
        generator.join();
        for (Thread t : senderThreads) {
            t.join();
        }
    }

    // A simplified basic report for the warmup phase
    private static void printBasicReport(String phaseName, int numThreads, long duration, MetricsCollector metrics) {
        System.out.println("\n--- " + phaseName + " Basic Results ---");
        System.out.println("Total Threads: " + numThreads);
        System.out.println("Successful Messages: " + metrics.getSuccessfulRequests());
        System.out.println("Failed Messages: " + metrics.getFailedRequests());
    }
}
