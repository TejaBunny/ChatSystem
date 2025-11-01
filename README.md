# ChatSystem

## Steps for Running and Deploying the Server

* Run the ChatServer Java file, which is located in the Java files (src/main/java)
* Run the EC2 instance and connect to it through SSH
* Deploy the running server into the EC2 instance public IP, using the wscat command, or another compatible command

## Steps for Running the Part-1 client

* After deploying the server, run the BaselineLatencyTester Java file to get the mean latency value for Little's Law analysis.
* Run the LoadTesterClient Java file to see the part-1 results output.

## Steps for Running the Part-2 client

* Run the LoadTesterClient Java file to see the part-2 results output.
* After running this file, a CSV file will be automatically created in the directory, named "performance_results.csv", with all the required statistical measures for each sent message.

## Points to be noted when connecting with server-v2 (modified server for assignment-2)

* When running clients for server-v2, and if there is a load balancer as well, then we need to add the Load balancer DNS with port 80, in place of the EC2 server public IP.
* In the ConnectionManager class in server-v2, we need to add the Rabbit server private IP address to the "RABBITMQ_HOST".
