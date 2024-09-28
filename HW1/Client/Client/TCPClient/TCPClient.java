package Client.TCPClient;

import Client.Client;

import java.io.*;
import java.net.*;
import java.util.*;
import Client.Command;
import Server.Common.ResponsePacket;

public class TCPClient extends Client {

    private String serverHost = "tr-open-08";
    private int serverPort = 3031;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public static void main(String[] args) {
        TCPClient client = new TCPClient();

        // Register a shutdown hook to ensure the socket is closed properly
        // Socket will be closed when the Java process running your TCPClient application is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered. Closing resources...");
            client.closeResources();  // Ensure all resources are closed on shutdown
        }));

        // Connect to the server
        client.connectServer();

        // Start the client interaction (from the Client superclass)
        client.start();  // This method is inherited and handles user interaction
    }

    public TCPClient() {
        super();  // Call to the parent constructor
    }

    @Override
    public void connectServer() {
        try {
            System.out.println("Connecting to server at " + serverHost + ":" + serverPort);
            socket = new Socket(serverHost, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("Connected to server.");
        } catch (IOException e) {
            System.err.println("Failed to connect to the server: " + e.getMessage());
        }
    }

    @Override
    public void start(){
        // Prepare for reading commands
        System.out.println();
        System.out.println("Location \"help\" for list of supported commands");

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        while (true)
        {
            // Read the next command
            String command = "";
            Vector<String> arguments = new Vector<String>();
            try {
                System.out.print((char)27 + "[32;1m\n>] " + (char)27 + "[0m");
                command = stdin.readLine().trim();
            }
            catch (IOException io) {
                System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0m" + io.getLocalizedMessage());
                io.printStackTrace();
                System.exit(1);
            }

            try {
                arguments = parse(command);
                Command cmd = Command.fromString((String)arguments.elementAt(0));
                try {
                    execute(cmd, arguments);
                } catch (NumberFormatException e) {
                    throw new RuntimeException(e);
                }
            }
            catch (IllegalArgumentException e) {
                System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0m" + e.getLocalizedMessage());
            }
            catch (Exception e) {
                System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mUncaught exception");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void execute(Command cmd, Vector<String> arguments) throws NumberFormatException {
        try {
            if (cmd == Command.Help) {
                if (arguments.size() == 1) {
                    System.out.println(Command.description());
                } else if (arguments.size() == 2) {
                    Command l_cmd = Command.fromString((String) arguments.elementAt(1));
                    System.out.println(l_cmd.toString());
                } else {
                    System.err.println((char) 27 + "[31;1mCommand exception: " + (char) 27 + "[0mImproper use of help command. Location \"help\" or \"help,<CommandName>\"");
                }
            } else if(cmd == Command.Quit){
                checkArgumentsCount(1, arguments.size());

                System.out.println("Quitting client");
                System.exit(0);
            } else { // Concrete commands

                // Input validation
                validateArguments(cmd, arguments);
                // Send a Request object to the server
                Request request = new Request(cmd, arguments);
                sendObject(request);

                // Receive a response object from the server
                Object response = receiveObject();
                System.out.println("response" + response);

                // Check if the response is a string and display it
                if (response instanceof ResponsePacket) {
                    System.out.println("Server response: " + ((ResponsePacket) response).getMessage());
                } else {
                    System.err.println("Unexpected response from server.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error during command execution: " + e.getMessage());
        }
    }

    public void sendObject(Object obj) {
        try {
            out.writeObject(obj);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending object: " + e.getMessage());
        }
    }

    public Object receiveObject() {
        try {
            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error receiving object: " + e.getMessage());
        }
        return null;
    }

    public static void validateArguments(Command cmd, Vector<String> arguments) throws IllegalArgumentException, NumberFormatException {
        switch (cmd) {
            case AddFlight:
                // AddFlight expects exactly 4 arguments
                checkArgumentsCount(4, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Flight Number
                toInt(arguments.elementAt(2)); // Flight Seats
                toInt(arguments.elementAt(3)); // Flight Price
                break;

            case AddCars:
                // AddCars expects exactly 4 arguments
                checkArgumentsCount(4, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Car Location (String)
                toInt(arguments.elementAt(2)); // Number of Cars
                toInt(arguments.elementAt(3)); // Car Price
                break;

            case AddRooms:
                // AddRooms expects exactly 4 arguments
                checkArgumentsCount(4, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Room Location (String)
                toInt(arguments.elementAt(2)); // Number of Rooms
                toInt(arguments.elementAt(3)); // Room Price
                break;

            case AddCustomer:
                // AddCustomer expects 1 argument
                checkArgumentsCount(1, arguments.size());
                break;

            case AddCustomerID:
                // AddCustomerID expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                break;

            case DeleteFlight:
                // DeleteFlight expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Flight Number
                break;

            case DeleteCars:
                // DeleteCars expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Car Location (String)
                break;

            case DeleteRooms:
                // DeleteRooms expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Room Location (String)
                break;

            case DeleteCustomer:
                // DeleteCustomer expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                break;

            case QueryFlight:
                // QueryFlight expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Flight Number
                break;

            case QueryCars:
                // QueryCars expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Car Location (String)
                break;

            case QueryRooms:
                // QueryRooms expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Room Location (String)
                break;

            case QueryCustomer:
                // QueryCustomer expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                break;

            case QueryFlightPrice:
                // QueryFlightPrice expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Flight Number
                break;

            case QueryCarsPrice:
                // QueryCarsPrice expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Car Location (String)
                break;

            case QueryRoomsPrice:
                // QueryRoomsPrice expects exactly 2 arguments
                checkArgumentsCount(2, arguments.size());

                // Validate argument types
                arguments.elementAt(1); // Room Location (String)
                break;

            case ReserveFlight:
                // ReserveFlight expects exactly 3 arguments
                checkArgumentsCount(3, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                toInt(arguments.elementAt(2)); // Flight Number
                break;

            case ReserveCar:
                // ReserveCar expects exactly 3 arguments
                checkArgumentsCount(3, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                arguments.elementAt(2); // Car Location (String)
                break;

            case ReserveRoom:
                // ReserveRoom expects exactly 3 arguments
                checkArgumentsCount(3, arguments.size());

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                arguments.elementAt(2); // Room Location (String)
                break;

            case Bundle:
                // Bundle expects at least 6 arguments
                if (arguments.size() < 6) {
                    throw new IllegalArgumentException("Bundle command expects at least 6 arguments.");
                }

                // Validate argument types
                toInt(arguments.elementAt(1)); // Customer ID
                for (int i = 2; i < arguments.size() - 3; i++) {
                    toInt(arguments.elementAt(i)); // Flight Numbers
                }
                arguments.elementAt(arguments.size() - 3); // Location (String)
                toBoolean(arguments.elementAt(arguments.size() - 2)); // Reserve Car (Y/N)
                toBoolean(arguments.elementAt(arguments.size() - 1)); // Reserve Room (Y/N)
                break;

            default:
                throw new IllegalArgumentException("Invalid command.");
        }
    }

    // Method to close all resources (socket, input/output streams)
    public void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Socket and streams closed successfully.");
            }
        } catch (IOException e) {
            System.err.println("Error while closing resources: " + e.getMessage());
        }
    }

}