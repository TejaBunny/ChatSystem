//package client;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    private static final MetricsCollector instance = new MetricsCollector();

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final Queue<String> csvRows = new ConcurrentLinkedQueue<>();

    private MetricsCollector() {
        csvRows.add("timestamp,latency,type"); // CSV Header
    }

    public static MetricsCollector getInstance() { return instance; }

    public void record(long latency, boolean success) {
        if (success) {
            successCount.incrementAndGet();
            csvRows.add(System.currentTimeMillis() + "," + latency + ",SUCCESS");
        } else {
            failCount.incrementAndGet();
            csvRows.add(System.currentTimeMillis() + ",0,FAILURE");
        }
    }

    public void printSummary(long totalDurationMs) {
        int s = successCount.get();
        int f = failCount.get();
        double throughput = (s + f) / (totalDurationMs / 1000.0);

        System.out.println("\n=== Test Results ===");
        System.out.println("Total Messages: " + (s + f));
        System.out.println("Successful: " + s);
        System.out.println("Failed: " + f);
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " msg/sec");

        writeToCSV();
    }

    private void writeToCSV() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("results.csv"))) {
            for (String row : csvRows) {
                pw.println(row);
            }
            System.out.println("✓ Metrics saved to results.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}