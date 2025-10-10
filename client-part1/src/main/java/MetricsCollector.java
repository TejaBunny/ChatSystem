import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector {
    private static final MetricsCollector instance = new MetricsCollector();
    private final AtomicInteger successfulRequests;
    private final AtomicInteger failedRequests;
    private final AtomicInteger totalConnections;
    private final AtomicInteger totalReconnections;

    private MetricsCollector() {
        this.successfulRequests = new AtomicInteger(0);
        this.failedRequests = new AtomicInteger(0);
        this.totalConnections = new AtomicInteger(0);
        this.totalReconnections = new AtomicInteger(0);
    }

    public static MetricsCollector getInstance() {
        return instance;
    }

    public void incrementSuccess() {
        successfulRequests.incrementAndGet();
    }

    public void incrementFailure() {
        failedRequests.incrementAndGet();
    }

    public void incrementConnections() {
        totalConnections.incrementAndGet();
    }

    public void incrementReconnections() {
        totalReconnections.incrementAndGet();
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
        successfulRequests.set(0);
        failedRequests.set(0);
        totalConnections.set(0);
        totalReconnections.set(0);
    }
}