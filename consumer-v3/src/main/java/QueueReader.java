//package consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

public class QueueReader implements Runnable {
    private final Connection connection;
    private final String queueName;
    private final BlockingQueue<MessageTask> buffer;

    public QueueReader(Connection connection, String queueName, BlockingQueue<MessageTask> buffer) {
        this.connection = connection;
        this.queueName = queueName;
        this.buffer = buffer;
    }

    @Override
    public void run() {
        try {
            Channel channel = connection.createChannel();
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, ConsumerConfig.EXCHANGE_NAME, queueName);

            // Prefetch: Don't overwhelm this worker. Get 2x batch size.
            channel.basicQos(ConsumerConfig.BATCH_SIZE * 2);

            System.out.println("✅ Reader started for: " + queueName);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                // Manual ACK is handled by the Writer later
                MessageTask task = new MessageTask(message, delivery.getEnvelope().getDeliveryTag(), channel);

                try {
                    buffer.put(task); // Blocks if buffer is full (Backpressure)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            // autoAck = false (We only ACK after DB write)
            channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});

        } catch (IOException e) {
            System.err.println("❌ Error in Reader " + queueName + ": " + e.getMessage());
        }
    }
}