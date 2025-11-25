//package consumer;

//import database.MongoDBManager;
//import database.MessageDTO;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatabaseWriter implements Runnable {
    private final BlockingQueue<MessageTask> buffer;
    private final MongoDBManager dbManager;

    public DatabaseWriter(BlockingQueue<MessageTask> buffer, MongoDBManager dbManager) {
        this.buffer = buffer;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        List<MessageTask> batchTasks = new ArrayList<>(ConsumerConfig.BATCH_SIZE);
        List<Document> batchDocs = new ArrayList<>(ConsumerConfig.BATCH_SIZE);
        long lastFlushTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Smart Poll: Wait up to 50ms for a message.
                // This prevents tight loops burning CPU when empty.
                MessageTask task = buffer.poll(50, TimeUnit.MILLISECONDS);

                if (task != null) {
                    batchTasks.add(task);
                    batchDocs.add(MessageDTO.toDocument(task.getRawJson()));
                }

                // Check Triggers: Is Batch Full? OR Is Time Up?
                long now = System.currentTimeMillis();
                boolean sizeTrigger = batchTasks.size() >= ConsumerConfig.BATCH_SIZE;
                boolean timeTrigger = !batchTasks.isEmpty() && (now - lastFlushTime > ConsumerConfig.FLUSH_INTERVAL_MS);

                if (sizeTrigger || timeTrigger) {
                    flush(batchTasks, batchDocs);
                    lastFlushTime = now;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void flush(List<MessageTask> tasks, List<Document> docs) {
        // 1. Bulk Write to MongoDB
        dbManager.bulkInsert(docs);

        // 2. Ack all messages in RabbitMQ
        // (Since we used W1 write concern, we assume success if no exception)
        for (MessageTask task : tasks) {
            task.ack();
        }

        // 3. Reset buffers
        System.out.println("✓ Flushed batch of " + tasks.size() + " messages.");
        tasks.clear();
        docs.clear();
    }
}