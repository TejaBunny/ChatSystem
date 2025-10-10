import java.time.Instant;
import java.time.format.DateTimeParseException;

public class ChatMessage {
    private String userId;
    private String username;
    private String message;
    private String timestamp;
    private String messageType;

    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getMessageType() { return messageType; }

    // Setters
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setMessage(String message) { this.message = message; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    /**
     * Validates the contents of the ChatMessage based on assignment specifications.
     * @return true if the message is valid, false otherwise.
     */
    public boolean isValid() {
        // 1. Validate userId: Must be a number between 1 and 100000
        try {
            int id = Integer.parseInt(this.userId);
            if (id < 1 || id > 100000) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // 2. Validate username: Must be 3-20 alphanumeric characters
        if (this.username == null || !this.username.matches("^[a-zA-Z0-9]{3,20}$")) {
            return false;
        }

        // 3. Validate message: Must be 1-500 characters
        if (this.message == null || this.message.length() < 1 || this.message.length() > 500) {
            return false;
        }

        // 4. Validate timestamp: Must be a valid ISO-8601 timestamp
        try {
            if (this.timestamp == null) return false;
            Instant.parse(this.timestamp);
        } catch (DateTimeParseException e) {
            return false;
        }

        // 5. Validate messageType: Must be one of the specified values
        if (this.messageType == null ||
                !(this.messageType.equals("TEXT") || this.messageType.equals("JOIN") || this.messageType.equals("LEAVE"))) {
            return false;
        }

        // If all checks pass, the message is valid
        return true;
    }
}