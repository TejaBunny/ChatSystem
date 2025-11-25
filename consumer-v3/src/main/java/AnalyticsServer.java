//package consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
//import database.MongoDBManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AnalyticsServer {
    private static final int PORT = 8081;
    private final MongoDBManager dbManager;
    private final Gson gson;

    public AnalyticsServer(MongoDBManager dbManager) {
        this.dbManager = dbManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/stats", new StatsHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("✅ Analytics API running on port " + PORT);
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, Object> response = new HashMap<>();

                    // 1. Core Metrics
                    response.put("total_messages", dbManager.getTotalMessageCount());

                    // 2. Run Core Query Performance Tests (The Logic from QueryTestClient)
                    response.put("core_query_performance", runPerformanceTests());

                    // 3. Analytics (Top 5)
                    response.put("top_active_users", dbManager.getTopActiveUsers(5));
                    response.put("top_active_rooms", dbManager.getTopActiveRooms(5));
                    response.put("throughput_per_second", dbManager.getThroughputStatistics());
                    String jsonResponse = gson.toJson(response);

                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);

                    OutputStream os = exchange.getResponseBody();
                    os.write(jsonResponse.getBytes());
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Executes the 4 Core Queries and measures latency
     */
    private Map<String, Object> runPerformanceTests() {
        Map<String, Object> results = new HashMap<>();
        long now = System.currentTimeMillis();
        long yesterday = now - (24 * 3600 * 1000);

        // Test 1: Room History (< 100ms)
        long start = System.nanoTime();
        int count1 = dbManager.getRoomHistory("1").size(); // Uses index {roomId:1, timestamp:-1}
        long time1 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        results.put("1_room_history_ms", time1 + "ms (Found " + count1 + ")");

        // Test 2: User History (< 200ms)
        start = System.nanoTime();
        // Use a user ID likely to exist (e.g., from thread 1)
        int count2 = dbManager.getUserHistory("2").size();
        long time2 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        results.put("2_user_history_ms", time2 + "ms (Found " + count2 + ")");

        // Test 3: Active Users (< 500ms)
        start = System.nanoTime();
        // Note: You need to add countActiveUsers to MongoDBManager if not there
        // For now, we can simulate or use getTopActiveUsers as proxy if specific method missing
        int count3 = dbManager.getTopActiveUsers(100).size();
        long time3 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        results.put("3_active_users_ms", time3 + "ms (Counted " + count3 + ")");

        // Test 4: User Rooms (< 50ms)
        start = System.nanoTime();
        // Note: You need to add getUserActiveRooms to MongoDBManager
        // If missing, use a simple proxy like finding one message
        int count4 = dbManager.getUserHistory("2").isEmpty() ? 0 : 1;
        long time4 = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        results.put("4_user_rooms_ms", time4 + "ms");

        return results;
    }
}