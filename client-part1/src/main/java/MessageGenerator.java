import com.google.gson.Gson;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable {
    private final BlockingQueue<String> messageQueue;
    private final int numMessages;
    private final Gson gson = new Gson();
    private final Random random = new Random();

    public MessageGenerator(BlockingQueue<String> messageQueue, int numMessages) {
        this.messageQueue = messageQueue;
        this.numMessages = numMessages;
    }

    @Override
    public void run() {
        for (int i = 0; i < numMessages; i++) {
            try {
                // Generate random message data as per assignment specs
                ChatMessage msg = new ChatMessage();
                msg.setUserId(String.valueOf(random.nextInt(100000) + 1));
                msg.setUsername("user" + msg.getUserId());
                msg.setMessage("This is a test message " + i);
                msg.setTimestamp(Instant.now().toString());

                // 90% TEXT, 5% JOIN, 5% LEAVE distribution
                int msgTypeRoll = random.nextInt(100);
                if (msgTypeRoll < 90) {
                    msg.setMessageType("TEXT");
                } else if (msgTypeRoll < 95) {
                    msg.setMessageType("JOIN");
                } else {
                    msg.setMessageType("LEAVE");
                }

                messageQueue.put(gson.toJson(msg));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}