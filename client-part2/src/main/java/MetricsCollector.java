// Client part 1 code

// import java.util.concurrent.atomic.AtomicInteger;

//public class MetricsCollector {
//    private static final MetricsCollector instance = new MetricsCollector();
//    private final AtomicInteger successfulRequests;
//    private final AtomicInteger failedRequests;
//    private final AtomicInteger totalConnections;
//    private final AtomicInteger totalReconnections;
//
//    private MetricsCollector() {
//        this.successfulRequests = new AtomicInteger(0);
//        this.failedRequests = new AtomicInteger(0);
//        this.totalConnections = new AtomicInteger(0);
//        this.totalReconnections = new AtomicInteger(0);
//    }
//
//    public static MetricsCollector getInstance() {
//        return instance;
//    }
//
//    public void incrementSuccess() {
//        successfulRequests.incrementAndGet();
//    }
//
//    public void incrementFailure() {
//        failedRequests.incrementAndGet();
//    }
//
//    public void incrementConnections() {
//        totalConnections.incrementAndGet();
//    }
//
//    public void incrementReconnections() {
//        totalReconnections.incrementAndGet();
//    }
//
//    public int getSuccessfulRequests() {
//        return successfulRequests.get();
//    }
//
//    public int getFailedRequests() {
//        return failedRequests.get();
//    }
//
//    public int getTotalConnections() {
//        return totalConnections.get();
//    }
//
//    public int getTotalReconnections() {
//        return totalReconnections.get();
//    }
//
//    public void reset() {
//        successfulRequests.set(0);
//        failedRequests.set(0);
//        totalConnections.set(0);
//        totalReconnections.set(0);
//    }
//}

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    private static final MetricsCollector instance = new MetricsCollector();

    private final ConcurrentLinkedQueue<String> csvData;
    private final AtomicInteger successfulRequests;
    private final AtomicInteger failedRequests;
    private final AtomicInteger totalConnections;
    private final AtomicInteger totalReconnections;

    private MetricsCollector() {
        this.csvData = new ConcurrentLinkedQueue<>();
        this.successfulRequests = new AtomicInteger(0);
        this.failedRequests = new AtomicInteger(0);
        this.totalConnections = new AtomicInteger(0);
        this.totalReconnections = new AtomicInteger(0);
        this.csvData.add("Timestamp,MessageType,Latency,StatusCode,RoomId");
    }

    public static MetricsCollector getInstance() {
        return instance;
    }

    public void recordRequest(long startTime, String messageType, long latency, int statusCode, String roomId) {
        this.csvData.add(String.join(",",
                String.valueOf(startTime),
                messageType,
                String.valueOf(latency),
                String.valueOf(statusCode),
                roomId));

        if (statusCode == 200) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }
    }

    public void incrementConnections() {
        totalConnections.incrementAndGet();
    }

    public void incrementReconnections() {
        totalReconnections.incrementAndGet();
    }

    public ConcurrentLinkedQueue<String> getCsvData() {
        return csvData;
    }

    public int getSuccessfulRequests() {
        return successfulRequests.get();
    }

    public int getFailedRequests() {
        return failedRequests.get();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public int getTotalReconnections() {
        return totalReconnections.get();
    }

    public void reset() {
        csvData.clear();
        csvData.add("Timestamp,MessageType,Latency,StatusCode,RoomId");
        successfulRequests.set(0);
        failedRequests.set(0);
        totalConnections.set(0);
        totalReconnections.set(0);
    }
}