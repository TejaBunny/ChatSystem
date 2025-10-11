import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class PerformanceReport {
    private final MetricsCollector metrics;
    private final long totalRuntime;

    // A simple inner class to hold parsed request data
    private static class RequestRecord {
        long timestamp;
        String messageType;
        long latency;
        int statusCode;
        String roomId;

        RequestRecord(String csvLine) {
            String[] parts = csvLine.split(",");
            this.timestamp = Long.parseLong(parts[0]);
            this.messageType = parts[1];
            this.latency = Long.parseLong(parts[2]);
            this.statusCode = Integer.parseInt(parts[3]);
            this.roomId = parts[4];
        }
    }

    public PerformanceReport(MetricsCollector metrics, long totalRuntime) {
        this.metrics = metrics;
        this.totalRuntime = totalRuntime;
    }

    public void generateReport() {
        List<RequestRecord> records = metrics.getCsvData().stream()
                .skip(1) // Skip header line
                .map(RequestRecord::new)
                .collect(Collectors.toList());

        if (records.isEmpty()) {
            System.out.println("No data collected to generate a report.");
            return;
        }

        // --- Latency Statistics ---
        DescriptiveStatistics stats = new DescriptiveStatistics();
        records.stream()
                .filter(r -> r.statusCode == 200 && r.latency >= 0)
                .mapToDouble(r -> r.latency)
                .forEach(stats::addValue);

        System.out.println("\n--- Response Time (ms) Statistics ---");
        System.out.println("Mean response time: " + String.format("%.2f", stats.getMean()));
        System.out.println("Median response time: " + String.format("%.2f", stats.getPercentile(50)));
        System.out.println("Min response time: " + (long) stats.getMin());
        System.out.println("Max response time: " + (long) stats.getMax());
        System.out.println("95th percentile response time: " + String.format("%.2f", stats.getPercentile(95)));
        System.out.println("99th percentile response time: " + String.format("%.2f", stats.getPercentile(99)));

        // --- Throughput per Room ---
        System.out.println("\n--- Throughput per Room (messages/sec) ---");
        Map<String, Long> requestsByRoom = records.stream()
                .collect(Collectors.groupingBy(r -> r.roomId, Collectors.counting()));

        requestsByRoom.forEach((roomId, count) -> {
            double throughput = (double) count / (totalRuntime / 1000.0);
            System.out.println("Room " + roomId + ": " + String.format("%.2f", throughput));
        });

        // --- Message Type Distribution ---
        System.out.println("\n--- Message Type Distribution ---");
        Map<String, Long> messagesByType = records.stream()
                .collect(Collectors.groupingBy(r -> r.messageType, Collectors.counting()));

        long totalMessages = records.size();
        messagesByType.forEach((type, count) -> {
            double percentage = (double) count * 100 / totalMessages;
            System.out.println(type + ": " + count + " messages (" + String.format("%.2f", percentage) + "%)");
        });

        // THROUGHPUT OVER TIME
        calculateAndPrintThroughputOverTime(records);

        // --- Final Summary ---
        int successful = metrics.getSuccessfulRequests();
        int failed = metrics.getFailedRequests();
        double overallThroughput = (double) (successful + failed) / (totalRuntime / 1000.0);

        System.out.println("\n--- Final Summary ---");
        System.out.println("Total Successful Messages: " + successful);
        System.out.println("Total Failed Messages: " + failed);
        System.out.println("Total Runtime (ms): " + totalRuntime);
        System.out.println("Overall Throughput (messages/sec): " + String.format("%.2f", overallThroughput));

        writeCsv();
    }

    private void calculateAndPrintThroughputOverTime(List<RequestRecord> records) {
        if (records.isEmpty()) return;

        // Find the first timestamp to use as the start of the test
        long testStartTime = records.stream()
                .min(Comparator.comparingLong(r -> r.timestamp))
                .get().timestamp;

        // Group requests into 10-second buckets
        Map<Long, Long> requestsPerBucket = records.stream()
                .collect(Collectors.groupingBy(
                        r -> (r.timestamp - testStartTime) / 10000, // Integer division creates the bucket index
                        Collectors.counting()
                ));

        System.out.println("\n--- Throughput Over Time (in 10-second buckets) ---");
        // Use a TreeMap to sort the buckets by time
        new TreeMap<>(requestsPerBucket).forEach((bucket, count) -> {
            long bucketStartTime = bucket * 10;
            long bucketEndTime = bucketStartTime + 10;
            double throughput = (double) count / 10.0; // count / 10 seconds
            System.out.println("Time [" + bucketStartTime + "s - " + bucketEndTime + "s]: " +
                    String.format("%.2f", throughput) + " msg/sec");
        });
    }

    private void writeCsv() {
        try (FileWriter out = new FileWriter("performance_results.csv");
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {

            for (String line : metrics.getCsvData()) {
                printer.printRecord((Object[]) line.split(","));
            }
            System.out.println("\nSuccessfully wrote all performance data to performance_results.csv");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
