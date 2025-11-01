import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ConnectionManager {
    private static Connection connection;
    // IMPORTANT: Replace this with your RabbitMQ server's IP
    private static final String RABBITMQ_HOST = "172.31.12.56";

    static {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setUsername("admin");
        factory.setPassword("Ringster18@1"); // Use the password you set
        try {
            connection = factory.newConnection();
        } catch (IOException | TimeoutException e) {
            System.err.println("Failed to create RabbitMQ connection");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}