//package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
//import database.MongoDBManager;
//import consumer.ConsumerConfig; // Reuse config for DB URI

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsServer {
    private static final int PORT = 8081;
    private final MongoDBManager dbManager;
    private final Gson gson;

    public AnalyticsServer() {
        // Reuse the MongoDB URI from Config
        this.dbManager = new MongoDBManager(ConsumerConfig.MONGO_URI);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/stats", new StatsHandler());
        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("✅ Analytics API running on port " + PORT);
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> response = new HashMap<>();

                // 1. Core Metrics
                response.put("total_messages", dbManager.getTotalMessageCount());

                // 2. Analytics
                response.put("top_5_users", dbManager.getTopActiveUsers(5));
                response.put("top_5_rooms", dbManager.getTopActiveRooms(5));

                // 3. Sample Data (Verification)
                response.put("sample_room_1_history", dbManager.getRoomHistory("1"));

                String jsonResponse = gson.toJson(response);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(jsonResponse.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    // Main method to run purely for testing,
    // BUT you should call 'new AnalyticsServer().start()' from your ChatServer.main()
    public static void main(String[] args) throws IOException {
        new AnalyticsServer().start();
    }
}