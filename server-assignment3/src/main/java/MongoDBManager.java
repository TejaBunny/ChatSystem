import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Collections;
import org.bson.conversions.Bson;
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;


public class MongoDBManager {
    private static final String DB_NAME = "chat_system";
    private static final String COLLECTION_NAME = "messages";

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;

    public MongoDBManager(String connectionString) {
        // 1. Configure Connection Pool
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(50) // High concurrency for writers
                                .minSize(10)
                                .maxConnectionIdleTime(60, TimeUnit.SECONDS))
                .writeConcern(WriteConcern.W1) // Acknowledge by primary only (Fast)
                .build();

        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(DB_NAME);
        this.collection = database.getCollection(COLLECTION_NAME);

        // 2. Initialize Schema & Indexes
        initIndexes();
    }

    private void initIndexes() {
        try {
            // Index A: Room History (Core Query 1)
            collection.createIndex(Indexes.compoundIndex(
                    Indexes.ascending("roomId"),
                    Indexes.descending("timestamp")
            ));

            // Index B: User History & Rooms (Core Query 2 & 4)
            collection.createIndex(Indexes.compoundIndex(
                    Indexes.ascending("userId"),
                    Indexes.descending("timestamp")
            ));

            // Index C: Analytics & Active Users (Core Query 3)
            collection.createIndex(Indexes.ascending("timestamp"));

            // Index D: Idempotency (Unique Message ID)
            IndexOptions uniqueOptions = new IndexOptions().unique(true);
            collection.createIndex(Indexes.ascending("messageId"), uniqueOptions);

            System.out.println("✅ Database Indexes verified successfully.");
        } catch (MongoException e) {
            System.err.println("⚠ Index initialization warning: " + e.getMessage());
        }
    }

    /**
     * The Cold Path: High-throughput Bulk Insert
     */
    public void bulkInsert(List<Document> batch) {
        if (batch.isEmpty()) return;
        try {
            // Ordered=false allows the batch to continue even if one item fails (e.g., duplicate)
            // This is crucial for resilience.
            collection.insertMany(batch, new com.mongodb.client.model.InsertManyOptions().ordered(false));
        } catch (MongoException e) {
            // Log error but don't crash. Duplicates are expected in distributed systems.
            System.err.println("⚠ Batch write partial failure: " + e.getMessage());
        }
    }

    public void close() {
        if (mongoClient != null) mongoClient.close();
    }

    // --- QUERY 1: Messages for a room ---
    public List<Document> getRoomHistory(String roomId) {
        return collection.find(eq("roomId", roomId))
                .sort(descending("timestamp"))
                .limit(50) // Limit to last 50 for API performance
                .into(new ArrayList<>());
    }

    // --- ANALYTICS 2: Top N Active Users ---
    public List<Document> getTopActiveUsers(int limit) {
        return collection.aggregate(Arrays.asList(
                group("$userId", sum("count", 1)),
                sort(descending("count")),
                limit(limit)
        )).into(new ArrayList<>());
    }

    // --- ANALYTICS 3: Top N Active Rooms ---
    public List<Document> getTopActiveRooms(int limit) {
        return collection.aggregate(Arrays.asList(
                group("$roomId", sum("count", 1)),
                sort(descending("count")),
                limit(limit)
        )).into(new ArrayList<>());
    }

    // --- ANALYTICS 1: Message Traffic Stats (Count) ---
    public long getTotalMessageCount() {
        return collection.countDocuments();
    }
}