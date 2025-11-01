import java.io.IOException;

public class ChannelPoolManager {
    private static ChannelPool instance;

    static {
        try {
            instance = new ChannelPool();
        } catch (IOException e) {
            System.err.println("Failed to initialize ChannelPool");
            e.printStackTrace();
        }
    }

    public static ChannelPool getPool() {
        return instance;
    }
}