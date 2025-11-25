import org.bson.Document;
import java.time.Instant;

public class MessageDTO {
    public static Document toDocument(String jsonMessage) {
        // We parse the raw JSON from Queue to a BSON Document for Mongo
        Document doc = Document.parse(jsonMessage);

        // Ensure timestamp is stored as a Date object for efficient Range Queries (Index C)
        // This is a common mistake; storing as String kills query performance.
        String ts = doc.getString("timestamp");
        if (ts != null) {
            doc.put("timestamp", Instant.parse(ts));
        }

        return doc;
    }
}