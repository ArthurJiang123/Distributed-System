package Server.TCP;

import Server.Common.ResourceManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPResourceManager extends ResourceManager {

    private static final int port = 3031;
    private static String s_serverName = "Server";

    public TCPResourceManager(String p_name) {
        super(p_name);
    }

    public static void main(String[] args) {

        if (args.length > 0) {
            s_serverName = args[0];
        }

        TCPResourceManager resourceManager = new TCPResourceManager(s_serverName);

        resourceManager.start();
    }

    /**
     * Start the server.
     */
    public void start(){
        try(ServerSocket serverSocket = new ServerSocket(port)){

            System.out.println("ResourceManager \'" + s_serverName +"\' listening on port:" + port);

            // for every connection established,
            // create a new thread to take care of the connection
            while(true){
                Socket middlewareSocket = serverSocket.accept();

                new RMTaskHandler(middlewareSocket, this).start();
            }
        }catch (IOException e){
            e.printStackTrace();
        }

    }
}
