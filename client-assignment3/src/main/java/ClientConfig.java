//package client;

public class ClientConfig {
    // AWS ALB Address or Localhost
    public static final String SERVER_URL = "ws://cs6650-alb-1497811358.us-west-2.elb.amazonaws.com:80/chat/";

    // Test Parameters
    public static final int NUM_THREADS = 256;   // High concurrency
    public static final int NUM_MESSAGES = 7000000; // Total load
//    public static final int MSGS_PER_THREAD = NUM_MESSAGES / NUM_THREADS;

    // 20 Rooms (Matches Server Logic)
    public static final int NUM_ROOMS = 20;
    public static final String ANALYTICS_URL = "http://35.94.12.179:8081/stats";
}
