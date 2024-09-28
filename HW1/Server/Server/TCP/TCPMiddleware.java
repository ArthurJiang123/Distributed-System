package Server.TCP;

/*
 *  Handle requests from the client.
 *  Delegate requests to the appropriate resource manager.
 *  Uses ServerScoket to listen for client requests.
 *  Forwarding is done using TCP sockets.
 * */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class TCPMiddleware {

    private static final int port = 3031;
    private static final int rmPort = 3031;
    private final static String FLIGHTS = "Flights";
    private final static String CARS = "Cars";
    private final static String ROOMS = "Rooms";
    private final Map<String, String> resourceManagerHosts; // Maps resource types to hosts
    private final Map<String, Socket> rmSockets;


    public TCPMiddleware(Map<String, String> resourceManagerHosts) {
        this.resourceManagerHosts = resourceManagerHosts;
        this.rmSockets = new HashMap<>();
    }

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

        // TODO: make sure implementing correctly how to connect with resource managers

        TCPMiddleware middleware = new TCPMiddleware(resourceManagerHosts);
        // Check connections to all Resource Managers
        if (!middleware.connectToResourceManagers()) {
            System.err.println("Failed to connect to one or more Resource Managers. Exiting...");
            System.exit(1);
        }

        // start the middleware
        middleware.start();

    }
    /** As a "Client" of Resource Managers
     * Establishes connections to Resource Managers
     * true if all connections are successful, otherwise false.
     */
    private boolean connectToResourceManagers() {
        boolean allConnected = false;
        int maxRetries = 5;
        int retryInterval = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {

            System.out.println("Attempting to connect to Resource Managers (Attempt " + attempt + "/" + maxRetries + ")");
            allConnected = true;

            for (Map.Entry<String, String> entry : resourceManagerHosts.entrySet()) {
                String resourceType = entry.getKey();
                String host = entry.getValue();

                if (!rmSockets.containsKey(resourceType)) {
                    try {
                        System.out.println("Connecting to " + resourceType + " Resource Manager at " + host + ":" + rmPort);

                        Socket rmSocket = new Socket(host, rmPort);
                        rmSockets.put(resourceType, rmSocket);

                        System.out.println("Successfully connected to " + resourceType + " Resource Manager.");
                    } catch (IOException e) {
                        System.err.println("Failed to connect to " + resourceType + " Resource Manager at " + host + ":" + rmPort);
                        allConnected = false;
                    }
                }
            }

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

        return false;  // Return false if unable to connect to all RMs after max retries
    }

    /**
     * As a "Server" for the client
     */
    public void start(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Middleware listening on port:" + port);

            // Recheck all connections before starting
            if (!connectToResourceManagers()) {
                System.err.println("Some Resource Manager connections failed. Middleware cannot start.");
                return;
            }

            // now it can accept client tasks
            while(true){
                // listens and waits for a connection
                // returned socket is used for communicating with the client
                Socket clientSocket = serverSocket.accept();

                new MiddlewareTaskHandler(clientSocket, resourceManagerHosts, rmSockets).start();
            }

        }catch(IOException e){
            System.err.println("Error starting the middleware.");
            e.printStackTrace();
        }
    }
}
