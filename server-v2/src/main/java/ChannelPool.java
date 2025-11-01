import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ChannelPool {
    private final BlockingQueue<Channel> pool;
    private static final int POOL_SIZE = 100;
    private final Connection connection;

    public ChannelPool() throws IOException {
        this.connection = ConnectionManager.getConnection();
        this.pool = new ArrayBlockingQueue<>(POOL_SIZE);

        // Pre-create channels and add them to the pool
        for (int i = 0; i < POOL_SIZE; i++) {
            Channel channel = connection.createChannel();
            // We'll set up the exchange here for simplicity
            channel.exchangeDeclare("chat.exchange", "topic", true);
            this.pool.offer(channel); // Use offer, it's non-blocking
        }
    }

    public Channel borrowChannel() throws InterruptedException {
        // Get a channel from the pool, waiting if necessary
        return pool.take();
    }

    public void returnChannel(Channel channel) {
        // Return a channel to the pool, non-blocking
        pool.offer(channel);
    }
}