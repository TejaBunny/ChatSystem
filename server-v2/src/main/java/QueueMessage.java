public class QueueMessage {
    // Note: Using private fields and getters/setters is good practice,
    // but public fields are simpler for a pure data-transfer-object.
    public String messageId;
    public String roomId;
    public String userId;
    public String username;
    public String message;
    public String timestamp;
    public String messageType;
    public String serverId; // ID of the server instance that received it
    public String clientIp; // IP of the client that sent it

    // A constructor can be useful
    public QueueMessage(String messageId, String roomId, String userId, String username,
                        String message, String timestamp, String messageType,
                        String serverId, String clientIp) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.serverId = serverId;
        this.clientIp = clientIp;
    }
}