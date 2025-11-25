//package client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileWriter;
import java.io.IOException;

public class LoadTester {
    // Add to LoadTester class
    // Add/Update this method in LoadTester.java
    private static void fetchAndLogStats() {
        System.out.println("\n--- Fetching Analytics from Consumer ---");
        System.out.println("Target: " + ClientConfig.ANALYTICS_URL);

        try {
            java.net.URL url = new java.net.URL(ClientConfig.ANALYTICS_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); // 5 sec timeout

            int status = conn.getResponseCode();
            if (status != 200) {
                System.err.println("⚠ API Request Failed. Status: " + status);
                return;
            }

            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            String jsonOutput = content.toString();

            // 1. Print to Console (for immediate check)
            System.out.println("✅ ANALYTICS REPORT:");
            System.out.println(jsonOutput);

            // 2. Save to File (NEW!)
            try (FileWriter file = new FileWriter("server_stats.json")) {
                file.write(jsonOutput);
                System.out.println("\n✅ Saved full analytics to: server_stats.json");
            } catch (IOException e) {
                System.err.println("❌ Failed to write server_stats.json: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("⚠ Could not fetch stats: " + e.getMessage());
            System.err.println("  1. Check if Consumer is running");
            System.err.println("  2. Check Consumer Security Group (Allow Port 8081)");
            System.err.println("  3. Check ClientConfig.ANALYTICS_URL is correct");
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting Load Test (Gemini Hybrid V3 Client)");
        System.out.println("Threads: " + ClientConfig.NUM_THREADS);
        System.out.println("Total Messages: " + ClientConfig.NUM_MESSAGES);

        ExecutorService executor = Executors.newFixedThreadPool(ClientConfig.NUM_THREADS);
        CountDownLatch startLatch = new CountDownLatch(ClientConfig.NUM_THREADS);
        CountDownLatch endLatch = new CountDownLatch(ClientConfig.NUM_THREADS);

        int baseMsgs = ClientConfig.NUM_MESSAGES / ClientConfig.NUM_THREADS;
        int remainder = ClientConfig.NUM_MESSAGES % ClientConfig.NUM_THREADS;

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < ClientConfig.NUM_THREADS; i++) {
            // Give the remainder messages to the last thread
            int msgsForThisThread = baseMsgs + (i == ClientConfig.NUM_THREADS - 1 ? remainder : 0);

            executor.submit(new UserThread(i, msgsForThisThread, startLatch, endLatch));
        }

        try {
            endLatch.await();
            long duration = System.currentTimeMillis() - startTime;

            executor.shutdown();
            MetricsCollector.getInstance().printSummary(duration);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        fetchAndLogStats();
    }
}