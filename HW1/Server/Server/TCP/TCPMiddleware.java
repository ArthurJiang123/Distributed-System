package Server.TCP;

/*
 *  Handle requests from the client.
 *  Delegate requests to the appropriate resource manager.
 *  Uses ServerScoket to listen for client requests.
 *  Forwarding is done using TCP sockets.
 * */

import Server.Common.ResourceManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class TCPMiddleware{

    private static final int port = 3031;
    private static final int rmPort = 3031;
    // resource types
    private final static String FLIGHTS = "Flights";
    private final static String CARS = "Cars";
    private final static String ROOMS = "Rooms";
    // resource types -> host names
    private final Map<String, String> resourceManagerHosts;

    // resource types -> sockets
    private final Map<String, Socket> rmSockets;
    // resource types -> output streams
    private final Map<String, ObjectOutputStream> rmOutputStreams;
    private final Map<String, ObjectInputStream> rmInputStreams;

    public TCPMiddleware(Map<String, String> resourceManagerHosts) {
        this.resourceManagerHosts = resourceManagerHosts;
        this.rmSockets = new HashMap<>();
        this.rmOutputStreams = new HashMap<>();
        this.rmInputStreams = new HashMap<>();
    }


    /**
     * Middleware bootstrap
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        if(args.length < 3){
            System.err.println("Usage: java TCPMiddleware <flightHost> <carHost> <roomHost>");
            System.exit(1);
        }

        // Set up host and port maps
        Map<String, String> resourceManagerHosts = new HashMap<>();
        resourceManagerHosts.put(FLIGHTS, args[0]);
        resourceManagerHosts.put(CARS, args[1]);
        resourceManagerHosts.put(ROOMS, args[2]);


        TCPMiddleware middleware = new TCPMiddleware(resourceManagerHosts);

        // connect to all resource managers
        if (!middleware.connectToResourceManagers()) {
            System.err.println("Failed to connect to one or more Resource Managers. Exiting...");
            System.exit(1);
        }

        // start
        middleware.start();

    }

    /**
     * As a "Client" of Resource Managers:
     * Establishes connections to Resource Managers
     * return true if all connections are successful, otherwise false.
     * @return
     */
    private boolean connectToResourceManagers() {

        boolean allConnected = false;
        int maxRetries = 5;
        int retryInterval = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            System.out.println("Attempting to connect to Resource Managers (Attempt " + attempt + "/" + maxRetries + ")");
            allConnected = true;

            // Attempt to connect to each resource manager
            for (Map.Entry<String, String> entry : resourceManagerHosts.entrySet()) {
                String resourceType = entry.getKey();
                String host = entry.getValue();

                // Check if the connection is already established
                if (!rmSockets.containsKey(resourceType)) {
                    try {

                        System.out.println("Connecting to " + resourceType + " Resource Manager at " + host + ":" + rmPort);

                        Socket rmSocket = new Socket(host, rmPort);

                        // after connecting, put the socket, and output/input streams into the map
                        rmSockets.put(resourceType, rmSocket);
                        rmOutputStreams.put(resourceType, new ObjectOutputStream(rmSocket.getOutputStream()));
                        rmInputStreams.put(resourceType, new ObjectInputStream(rmSocket.getInputStream()));

                        System.out.println("Successfully connected to " + resourceType + " Resource Manager.");
                    } catch (IOException e) {
                        System.err.println("Failed to connect to " + resourceType + " Resource Manager at " + host + ":" + rmPort);
                        allConnected = false;
                    }
                }
            }

            // if all connected, return
            // otherwise, retry the connection later.
            if (allConnected) {
                return true;
            } else {
                System.out.println("Retrying connection in " + (retryInterval / 1000) + " seconds...");
                try {
                    Thread.sleep(retryInterval);  // Wait before retrying
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    /**
     * As the "Server" of clients:
     * waits and accepts a connection from the client
     * start a handler(i.e. a thread) to handle the connection with the client.
     */
    public void start(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Middleware listening on port:" + port);

            // now it can accept client tasks
            while(true){
                // listens and waits for a connection
                // returned socket is used for communicating with the client
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted new client connection...");

                new MiddlewareTaskHandler(clientSocket, rmSockets, rmOutputStreams, rmInputStreams).start();
            }

        }catch(IOException e){
            System.err.println("Error starting the middleware.");
            e.printStackTrace();
        }
    }
}
