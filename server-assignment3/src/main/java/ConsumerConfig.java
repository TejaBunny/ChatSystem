//package consumer;

public class ConsumerConfig {
    // RabbitMQ
    public static final String RMQ_HOST = "172.31.12.56"; // UPDATE THIS!
    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String QUEUE_PREFIX = "room.";
    public static final int NUM_QUEUES = 20;

    // MongoDB
    public static final String MONGO_URI = "mongodb://172.31.20.96:27017";

    // Tuning Parameters (Critical for Performance)
    public static final int BATCH_SIZE = 500;       // Max messages per bulk write
    public static final int FLUSH_INTERVAL_MS = 500; // Max wait time before writing
    public static final int WRITER_THREADS = 10;    // Number of threads writing to DB
    public static final int BUFFER_SIZE = 10000;    // Max messages in memory before blocking
}