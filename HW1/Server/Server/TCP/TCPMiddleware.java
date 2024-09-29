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


    public TCPMiddleware() {}


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
        String FLIGHTSHOST = args[0];
        String CARSHOST = args[1];
        String ROOMSHOST = args[2];

        TCPMiddleware middleware = new TCPMiddleware();

        // start
        middleware.start(FLIGHTSHOST, CARSHOST, ROOMSHOST);
    }

    /**
     * As the "Server" of clients:
     * waits and accepts a connection from the client
     * start a handler(i.e. a thread) to handle the connection with the client.
     */
    public void start(String flightHost, String carHost, String roomHost ){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Middleware listening on port:" + port);

            // accept client connection
            while(true){
                // listens and waits for a connection
                // returned socket is used for communicating with the client
                Socket clientSocket = serverSocket.accept();
                System.out.println("A new client is connected...");

                // Create a new MiddlewareTaskHandler to handle the client
                // and to communicate with RMs
                new MiddlewareTaskHandler(clientSocket, flightHost, carHost, roomHost, rmPort).start();
            }

        }catch(IOException e){
            System.err.println("Error starting the middleware.");
            e.printStackTrace();
        }
    }
}
