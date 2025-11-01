# Assignment 2: Monitoring Strategy

This document outlines the tools and methods used to monitor the chat server system during high-volume load testing, as required by the assignment.

### 1. RabbitMQ Management UI
- **Purpose**: This was the primary tool for monitoring the health and performance of the message broker.
- **Metrics Tracked**:
    - **Queue Depth (Ready Messages)**: Watched to identify consumer lag and system bottlenecks.
    - **Message Rates (Publish/Consume)**: Used to verify that producers were successfully publishing messages and that consumers were processing them.

### 2. AWS EC2 Monitoring Tab
- **Purpose**: Used to monitor the hardware-level health of all instances in the system.
- **Metrics Tracked**:
    - **CPU Utilization**: This was the most critical system metric. It was used to identify which component (App Server vs. RabbitMQ Server) was the system bottleneck.

### 3. Client Application Console
- **Purpose**: The `LoadTesterClient` itself was the main tool for measuring end-to-end performance.
- **Metrics Tracked**:
    - **Throughput (messages/sec)**: The primary measure of system performance.
    - **Latency (ms)**: Mean, median, and percentile latencies for all successful messages.
    - **Success/Failure Count**: To ensure system reliability.

### 4. Server Logs (`nohup.out`)
- **Purpose**: Used for debugging and verifying the real-time status of the `server-v2` instances.
- **Metrics Tracked**:
    - Server startup confirmation.
    - Consumer thread startup confirmation (`[*] Waiting for messages...`).
    - Any runtime exceptions (e.g., `AuthenticationFailureException`).