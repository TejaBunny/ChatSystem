

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.google.gson.Gson;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class QueueConsumer implements Runnable {
    private final ConcurrentMap<String, Set<WebSocket>> roomSessions;
    private final Connection connection;
    private final Gson gson = new Gson();
    private static final String EXCHANGE_NAME = "chat.exchange";
    private String queueName; // Will be set in the loop

    public QueueConsumer(ConcurrentMap<String, Set<WebSocket>> roomSessions) {
        this.roomSessions = roomSessions;
        this.connection = ConnectionManager.getConnection();
    }

    @Override
    public void run() {
        // ** ADDED while(true) LOOP FOR RESILIENCY **
        while (true) {
            try (Channel channel = connection.createChannel()) {
                // Create a non-durable, exclusive, auto-delete queue.
                // This is a "fan-out" queue: unique to this consumer.
                this.queueName = channel.queueDeclare().getQueue();

                // Bind this unique queue to the exchange to get messages for ALL rooms.
                channel.queueBind(queueName, EXCHANGE_NAME, "room.#");

                channel.basicQos(10); // Prefetch count

                System.out.println(" [*] Consumer thread " + Thread.currentThread().getId() + " waiting for messages on queue: " + queueName);

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);

                    try {
                        QueueMessage queueMessage = gson.fromJson(messageJson, QueueMessage.class);
                        String roomId = queueMessage.roomId;
                        Set<WebSocket> sessions = roomSessions.get(roomId);

                        if (sessions != null && !sessions.isEmpty()) {
                            for (WebSocket session : sessions) {
                                if (session.isOpen()) {
                                    session.send(messageJson);
                                }
                            }
                        }
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (Exception e) {
                        System.err.println("Failed to process/broadcast message: " + e.getMessage());
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    }
                };

                // Start consuming. This call will block until the channel is closed or crashes.
                channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});

            } catch (Exception e) {
                // If the channel or connection closes, the loop will catch the exception
                System.err.println("Consumer thread " + Thread.currentThread().getId() + " crashed: " + e.getMessage());
                try {
                    // Wait 5 seconds before trying to reconnect
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("Consumer thread " + Thread.currentThread().getId() + " restarting...");
            }
        }
    }
}
