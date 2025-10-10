//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.Random;
//
//public class LoadTesterClient {
//
//    // IMPORTANT: Replace this with your EC2 server's public IP address
//    private static final String SERVER_URL_BASE = "ws://54.201.32.50:8080/chat/";
//
//    // Warmup phase configuration
//    private static final int WARMUP_THREADS = 32;
//    private static final int MESSAGES_PER_THREAD_WARMUP = 1000;
//    private static final int WARMUP_MESSAGES = WARMUP_THREADS * MESSAGES_PER_THREAD_WARMUP;
//
//
//    // Main phase configuration (corrected counts)
//    private static final int TOTAL_MESSAGES = 500000;
//    private static final int MAIN_MESSAGES = TOTAL_MESSAGES - WARMUP_MESSAGES;
//    private static final int MAIN_THREADS = 64; // Tunable parameter - start with this
//    private static final int MESSAGES_PER_THREAD_MAIN = MAIN_MESSAGES / MAIN_THREADS;
//
//    public static void main(String[] args) throws InterruptedException {
//        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(10000);
//        MetricsCollector metrics = MetricsCollector.getInstance();
//        Random random = new Random();
//
//        // --- Warmup Phase ---
//        System.out.println("Starting Warmup Phase...");
//        long warmupStartTime = System.currentTimeMillis();
//        runPhase(WARMUP_THREADS, MESSAGES_PER_THREAD_WARMUP, messageQueue, random);
//        long warmupEndTime = System.currentTimeMillis();
//        System.out.println("Warmup Phase Complete.");
//
//        // --- Print Warmup Report ---
//        long warmupDuration = warmupEndTime - warmupStartTime;
//        printPhaseReport("Warmup Phase", WARMUP_THREADS, WARMUP_MESSAGES, warmupDuration, metrics);
//
//        // --- Reset Metrics for Main Phase ---
//        metrics.reset();
//
//        // --- Main Phase ---
//        System.out.println("\nStarting Main Testing Phase...");
//        long mainStartTime = System.currentTimeMillis();
//        runPhase(MAIN_THREADS, MESSAGES_PER_THREAD_MAIN, messageQueue, random);
//        long mainEndTime = System.currentTimeMillis();
//        System.out.println("Main Testing Phase Complete.");
//
//        // --- Print Main Phase Report (Part 2) ---
//        long mainDuration = mainEndTime - mainStartTime;
//        printPhaseReport("Main Phase", MAIN_THREADS, MAIN_MESSAGES, mainDuration, metrics);
//    }
//
//    private static void runPhase(int numThreads, int messagesPerThread, BlockingQueue<String> queue, Random random) throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(numThreads);
//        int totalMessagesInPhase = numThreads * messagesPerThread;
//
//        Thread generator = new Thread(new MessageGenerator(queue, totalMessagesInPhase));
//        generator.start();
//
//        for (int i = 0; i < numThreads; i++) {
//            String url = SERVER_URL_BASE + (random.nextInt(20) + 1);
//            Thread sender = new Thread(new MessageSender(url, queue, latch, messagesPerThread));
//            sender.start();
//        }
//
//        latch.await();
//        generator.join();
//    }
//
//    private static void printPhaseReport(String phaseName, int numThreads, int totalMessages, long duration, MetricsCollector metrics) {
//        double throughput = (double) totalMessages / (duration / 1000.0);
//        System.out.println("\n--- " + phaseName + " Results ---");
//        System.out.println("Total Threads: " + numThreads);
//        System.out.println("Successful Messages: " + metrics.getSuccessfulRequests());
//        System.out.println("Failed Messages: " + metrics.getFailedRequests());
//        System.out.println("Total Runtime (ms): " + duration);
//        System.out.println("Overall Throughput (messages/sec): " + String.format("%.2f", throughput));
//        System.out.println("Total Connections: " + metrics.getTotalConnections());
//        System.out.println("Total Reconnections: " + metrics.getTotalReconnections());
//    }
//}

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Random;

public class LoadTesterClient {
    // ... (constants remain the same) ...
    private static final String SERVER_URL_BASE = "ws://54.200.209.44:8080/chat/";
    private static final int WARMUP_THREADS = 32;
    private static final int MESSAGES_PER_THREAD_WARMUP = 1000;
//    private static final int WARMUP_MESSAGES = 32000;
    private static final int WARMUP_MESSAGES = WARMUP_THREADS * MESSAGES_PER_THREAD_WARMUP;
    private static final int TOTAL_MESSAGES = 500000;
    private static final int MAIN_MESSAGES = TOTAL_MESSAGES - WARMUP_MESSAGES;
    private static final int MAIN_THREADS = 320;
    private static final int MESSAGES_PER_THREAD_MAIN = MAIN_MESSAGES / MAIN_THREADS;

    public static void main(String[] args) throws InterruptedException {
        // ... (main method setup is the same) ...
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
        printPhaseReport("Warmup Phase", WARMUP_THREADS, WARMUP_MESSAGES, warmupDuration, metrics);

        metrics.reset();

        // --- Main Phase ---
        System.out.println("\nStarting Main Testing Phase...");
        long mainStartTime = System.currentTimeMillis();
        runPhase(MAIN_THREADS, MAIN_MESSAGES, messageQueue, random);
        long mainEndTime = System.currentTimeMillis();
        System.out.println("Main Testing Phase Complete.");

        long mainDuration = mainEndTime - mainStartTime;
        printPhaseReport("Main Phase", MAIN_THREADS, MAIN_MESSAGES, mainDuration, metrics);
    }

//    private static void runPhase(int numThreads, int messagesPerThread, BlockingQueue<String> queue, Random random) throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(numThreads);
//        int totalMessagesInPhase = numThreads * messagesPerThread;
//        List<Thread> senderThreads = new ArrayList<>();
//
//        Thread generator = new Thread(new MessageGenerator(queue, totalMessagesInPhase));
//        generator.start();
//
//        for (int i = 0; i < numThreads; i++) {
//            String url = SERVER_URL_BASE + (random.nextInt(20) + 1);
//            Thread sender = new Thread(new MessageSender(url, queue, latch, messagesPerThread));
//            senderThreads.add(sender);
//            sender.start();
//        }
//
//        latch.await(); // Wait for all threads to finish their primary work
//        generator.join(); // Wait for the generator to finish producing
//
//        // This is the new, more robust part: wait for threads to fully terminate
//        for (Thread t : senderThreads) {
//            t.join();
//        }
//    }

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

//            String roomId = String.valueOf(random.nextInt(20) + 1);
            String url = SERVER_URL_BASE + (random.nextInt(20) + 1);
            Thread sender = new Thread(new MessageSender(url, queue, latch, messagesForThisThread));
            senderThreads.add(sender);
            sender.start();
        }

        latch.await();
        generator.join();
        for (Thread t : senderThreads) {
            t.join();
        }
    }

    private static void printPhaseReport(String phaseName, int numThreads, int totalMessages, long duration, MetricsCollector metrics) {
        // ... (this method remains the same) ...
        double throughput = (double) totalMessages / (duration / 1000.0);
        System.out.println("\n--- " + phaseName + " Results ---");
        System.out.println("Total Threads: " + numThreads);
        System.out.println("Successful Messages: " + metrics.getSuccessfulRequests());
        System.out.println("Failed Messages: " + metrics.getFailedRequests());
        System.out.println("Total Runtime (ms): " + duration);
        System.out.println("Overall Throughput (messages/sec): " + String.format("%.2f", throughput));
        System.out.println("Total Connections: " + metrics.getTotalConnections());
        System.out.println("Total Reconnections: " + metrics.getTotalReconnections());
    }
}