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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TCPMiddleware {

    private static final int port = 3031;
    private Map<String, String> resourceManagerHosts; // Maps resource types to hosts

    private final Lock flightLock = new ReentrantLock();
    private final Lock carLock = new ReentrantLock();
    private final Lock roomLock = new ReentrantLock();


    public TCPMiddleware(Map<String, String> resourceManagerHosts) {
        this.resourceManagerHosts = resourceManagerHosts;
    }

    public static void main(String[] args) throws IOException {

        if(args.length < 3){
            System.err.println("Usage: java TCPMiddleware <flightHost> <carHost> <roomHost>");
            System.exit(1);
        }

        // Set up host and port maps
        Map<String, String> resourceManagerHosts = new HashMap<>();
        resourceManagerHosts.put("flight", args[0]);
        resourceManagerHosts.put("car", args[1]);
        resourceManagerHosts.put("room", args[2]);

        // TODO: make sure implementing correctly how to connect with resource managers

        // start the middleware
        TCPMiddleware middleware = new TCPMiddleware(resourceManagerHosts);

        middleware.start();

    }

    public void start(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Middleware listening on port:" + port);

            // keep accepting connections. If there is no connection, it will block
            while(true){
                // listens and waits for a connection
                // returned socket is used for communicating with the client
                Socket clientSocket = serverSocket.accept();

                new MiddlewareTaskHandler(clientSocket).start();
            }

        }catch(IOException e){
            //TODO: how to handle error gracefully without termination?
            System.err.println("Error starting the middleware.");
            e.printStackTrace();
        }
    }
}
