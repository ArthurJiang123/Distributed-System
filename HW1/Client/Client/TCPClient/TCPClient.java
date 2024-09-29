package Client.TCPClient;

import Client.Client;

import java.io.*;
import java.net.*;
import java.util.*;
import Client.Command;
import Server.Common.ResponsePacket;

public class TCPClient extends Client {

    private String serverHost;
    private int serverPort;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public TCPClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public static void main(String[] args) {
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);

        TCPClient client = new TCPClient(serverHost, serverPort);

        // Register a shutdown hook to ensure the socket is closed properly
        // Socket will be closed when the Java process running your TCPClient application is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered. Closing resources...");
            client.closeResources();  // Ensure all resources are closed on shutdown
        }));

        client.connectServer();

        client.start();
    }

    public TCPClient() {
        super();
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
            // Read the next commandf
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
            switch (cmd) {
                case Help: {
                    if (arguments.size() == 1) {
                        System.out.println(Command.description());
                    } else if (arguments.size() == 2) {
                        Command l_cmd = Command.fromString(arguments.elementAt(1));
                        System.out.println(l_cmd.toString());
                    } else {
                        System.err.println((char) 27 + "[31;1mCommand exception: " + (char) 27 + "[0mImproper use of help command. Location \"help\" or \"help,<CommandName>\"");
                    }
                    break;
                }
                case AddFlight: {
                    checkArgumentsCount(4, arguments.size());

                    System.out.println("Adding a new flight ");
                    System.out.println("-Flight Number: " + arguments.elementAt(1));
                    System.out.println("-Flight Seats: " + arguments.elementAt(2));
                    System.out.println("-Flight Price: " + arguments.elementAt(3));

                    int flightNum = toInt(arguments.elementAt(1));
                    int flightSeats = toInt(arguments.elementAt(2));
                    int flightPrice = toInt(arguments.elementAt(3));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case AddCars: {
                    checkArgumentsCount(4, arguments.size());

                    System.out.println("Adding new cars");
                    System.out.println("-Car Location: " + arguments.elementAt(1));
                    System.out.println("-Number of Cars: " + arguments.elementAt(2));
                    System.out.println("-Car Price: " + arguments.elementAt(3));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case AddRooms: {
                    checkArgumentsCount(4, arguments.size());

                    System.out.println("Adding new rooms");
                    System.out.println("-Room Location: " + arguments.elementAt(1));
                    System.out.println("-Number of Rooms: " + arguments.elementAt(2));
                    System.out.println("-Room Price: " + arguments.elementAt(3));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case AddCustomer: {
                    checkArgumentsCount(1, arguments.size());

                    System.out.println("Adding a new customer");

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case AddCustomerID: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Adding a new customer");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case DeleteFlight: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Deleting a flight");
                    System.out.println("-Flight Number: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case DeleteCars: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Deleting all cars at a particular location");
                    System.out.println("-Car Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case DeleteRooms: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Deleting all rooms at a particular location");
                    System.out.println("-Room Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case DeleteCustomer: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Deleting a customer from the database");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryFlight: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying a flight");
                    System.out.println("-Flight Number: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryCars: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying cars location");
                    System.out.println("-Car Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryRooms: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying rooms location");
                    System.out.println("-Room Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryCustomer: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying customer information");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryFlightPrice: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying a flight price");
                    System.out.println("-Flight Number: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryCarsPrice: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying cars price");
                    System.out.println("-Car Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case QueryRoomsPrice: {
                    checkArgumentsCount(2, arguments.size());

                    System.out.println("Querying rooms price");
                    System.out.println("-Room Location: " + arguments.elementAt(1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case ReserveFlight: {
                    checkArgumentsCount(3, arguments.size());

                    System.out.println("Reserving seat in a flight");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));
                    System.out.println("-Flight Number: " + arguments.elementAt(2));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case ReserveCar: {
                    checkArgumentsCount(3, arguments.size());

                    System.out.println("Reserving a car at a location");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));
                    System.out.println("-Car Location: " + arguments.elementAt(2));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case ReserveRoom: {
                    checkArgumentsCount(3, arguments.size());

                    System.out.println("Reserving a room at a location");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));
                    System.out.println("-Room Location: " + arguments.elementAt(2));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case Bundle: {
                    if (arguments.size() < 6) {
                        System.err.println((char) 27 + "[31;1mCommand exception: " + (char) 27 + "[0mBundle command expects at least 6 arguments. Location \"help\" or \"help,<CommandName>\"");
                        break;
                    }

                    System.out.println("Reserving a bundle");
                    System.out.println("-Customer ID: " + arguments.elementAt(1));
                    for (int i = 0; i < arguments.size() - 5; ++i) {
                        System.out.println("-Flight Number: " + arguments.elementAt(2 + i));
                    }
                    System.out.println("-Location for Car/Room: " + arguments.elementAt(arguments.size() - 3));
                    System.out.println("-Book Car: " + arguments.elementAt(arguments.size() - 2));
                    System.out.println("-Book Room: " + arguments.elementAt(arguments.size() - 1));

                    sendRequestToServer(cmd, arguments);
                    break;
                }
                case Quit:
                    checkArgumentsCount(1, arguments.size());

                    System.out.println("Quitting client");
                    System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("Error during command execution: " + e.getMessage());
        }
    }

    private void sendRequestToServer(Command cmd, Vector<String> arguments) {
        // Create and send the Request object to the server
        Request request = new Request(cmd, arguments);
        sendObject(request);

        // Receive the response from the server
        ResponsePacket response = (ResponsePacket) receiveObject();
        System.out.println("Server response: " + response.getMessage());
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