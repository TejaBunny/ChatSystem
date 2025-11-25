//package consumer;

import com.rabbitmq.client.Channel;

public class MessageTask {
    private final String rawJson;
    private final long deliveryTag;
    private final Channel channel;

    public MessageTask(String rawJson, long deliveryTag, Channel channel) {
        this.rawJson = rawJson;
        this.deliveryTag = deliveryTag;
        this.channel = channel;
    }

    public String getRawJson() { return rawJson; }

    // Called by Writer after successful DB insert
    public void ack() {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            System.err.println("⚠ Failed to ACK message: " + e.getMessage());
        }
    }
}