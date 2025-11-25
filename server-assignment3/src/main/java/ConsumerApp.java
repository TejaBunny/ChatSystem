//package consumer;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
//import database.MongoDBManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class ConsumerApp {
    public static void main(String[] args) {
        try {
            System.out.println("Starting Consumer Service (Gemini Hybrid V3)...");

            // 1. Initialize Resources
            BlockingQueue<MessageTask> buffer = new LinkedBlockingQueue<>(ConsumerConfig.BUFFER_SIZE);
            MongoDBManager dbManager = new MongoDBManager(ConsumerConfig.MONGO_URI);

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ConsumerConfig.RMQ_HOST);
            factory.setUsername("admin");        // From your previous code
            factory.setPassword("Ringster18@1");
            Connection rmqConnection = factory.newConnection();

            // 2. Start Readers (1 per Room Queue)
            ExecutorService readerPool = Executors.newFixedThreadPool(ConsumerConfig.NUM_QUEUES);
            for (int i = 1; i <= ConsumerConfig.NUM_QUEUES; i++) {
                String queueName = ConsumerConfig.QUEUE_PREFIX + i;
                readerPool.submit(new QueueReader(rmqConnection, queueName, buffer));
            }

            // 3. Start Writers
            ExecutorService writerPool = Executors.newFixedThreadPool(ConsumerConfig.WRITER_THREADS);
            for (int i = 0; i < ConsumerConfig.WRITER_THREADS; i++) {
                writerPool.submit(new DatabaseWriter(buffer, dbManager));
            }

            System.out.println("🚀 Consumer Service Running: " + ConsumerConfig.NUM_QUEUES + " Readers, " +
                    ConsumerConfig.WRITER_THREADS + " Writers.");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}