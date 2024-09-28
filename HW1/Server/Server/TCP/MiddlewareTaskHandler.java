package Server.TCP;

import Client.Command;
import Client.TCPClient.Request;
import Server.Common.ResponsePacket;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;

class MiddlewareTaskHandler extends Thread{

    // maintains the socket of the client connected to the middleware
    private Socket clientSocket;


    // maintains sockets connected to resource managers
    // resource types(Flights, Cars, Rooms) -> Socket
    private Map<String, Socket> rmSockets;

    private final static String FLIGHTS = "Flights";
    private final static String CARS = "Cars";
    private final static String ROOMS = "Rooms";

    // maintains the input/output streams of the resource managers
    private final Map<String, ObjectOutputStream> rmOutputStreams;
    private final Map<String, ObjectInputStream> rmInputStreams;

    public MiddlewareTaskHandler(Socket clientSocket,
                                 Map<String, Socket> rmSockets,
                                 Map<String, ObjectOutputStream> rmOutputStreams,
                                 Map<String, ObjectInputStream> rmInputStreams) {
        this.rmSockets =rmSockets;
        this.clientSocket = clientSocket;
        this.rmOutputStreams = rmOutputStreams;
        this.rmInputStreams = rmInputStreams;
    }

    /**
     * Handles the request from the client
     * Retrieve: the client socket and associated streams
     * Process: forward the request, and send the response back to the client
     */
    public void run(){

        System.out.println("Handling a new client request...");

        try(
                ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
        ){

            // from the socket, get the input and output streams
            while(true){
                try {
                    // Read the request from the client
                    Request request = (Request) input.readObject();
                    System.out.println("Received request: " + request.getCommand());
                    // Handle the request
                    ResponsePacket response = handleRequest(request);
                    // Send the response back to the client
                    output.writeObject(response);
                    output.flush();
                } catch (EOFException eof) {
                    System.out.println("Client has closed the connection.");
                    break;
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        }catch (IOException e) {
            System.err.println("Error setting up client streams: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanupClient();
        }
    }

    /**
     * close the client socket upon disconnection
     */
    private void cleanupClient() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            System.out.println("Client connection closed and resources cleaned up.");
        } catch (IOException e) {
            System.err.println("Failed to close client socket.");
            e.printStackTrace();
        }
    }


    /**
     * Forward the request to a single resource manager
     * @param request
     * @return
     */
    private ResponsePacket handleRequest(Request request) {


        Command command = request.getCommand();

        Vector<String> arguments = request.getArguments();

        System.out.println("Processing command: " + command + " with arguments: " + arguments);

        ResponsePacket result;

        // Given the resource type
        // forward the request to an RM
        switch (command) {
            case AddFlight:
            case ReserveFlight:
            case DeleteFlight:
            case QueryFlight:
            case QueryFlightPrice:
                result = forwardSingle(FLIGHTS, request);
                break;

            case AddCars:
            case DeleteCars:
            case QueryCars:
            case QueryCarsPrice:
            case ReserveCar:
                result = forwardSingle(CARS, request);
                break;

            case AddRooms:
            case DeleteRooms:
            case QueryRooms:
            case QueryRoomsPrice:
            case ReserveRoom:
                result = forwardSingle(ROOMS, request);
                break;

            case AddCustomer:
            case AddCustomerID:
            case DeleteCustomer:
            case QueryCustomer:
                result = forwardCustomer(request);
                break;

            case Bundle:
                result = forwardBundle(request);
                break;

            default:
                result = new ResponsePacket(false,"Unknown command.");
        }
        return result;
    }


    /**
     * Forward a request to a specific resource manager
     * Use: the socket of a connected RM.
     * Use: the output/input stream of the RM's socket
     * @param resourceType
     * @param request
     * @return
     */
    private ResponsePacket forwardSingle(String resourceType, Request request) {

        Socket rmSocket = rmSockets.get(resourceType);

        if (rmSocket == null) {
            return new ResponsePacket(false, "No connection to ResourceManager of type: " + resourceType);
        }


        ObjectOutputStream outToRM = rmOutputStreams.get(resourceType);
        ObjectInputStream inFromRM = rmInputStreams.get(resourceType);

        if (outToRM == null || inFromRM == null) {
            return new ResponsePacket(false, "No connection to " + resourceType + " RM.");
        }

        try{
            // Forward the request
            outToRM.writeObject(request);
            outToRM.flush();

            // Receive the result
            return (ResponsePacket) inFromRM.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error connecting to ResourceManager");
        }
    }

    /**
     * Handle the bundle request
     * Allows for partial success:
     * Response contains true if at least one flight/room/car is reserved.
     * Response contains false if it failed to reserve all of them.
     * @param request
     * @return
     */
    private ResponsePacket forwardBundle(Request request) {


        Vector<String> arguments = request.getArguments();
        int customerID = Integer.parseInt(arguments.get(1));
        Vector<String> flightNumbers = new Vector<>();

        // 1st argument == command itself
        // 2nd argument == customer id
        // record flight numbers starting from the 3rd command
        int i = 2;
        while(i < arguments.size() - 3){
            flightNumbers.add(arguments.elementAt(i));
            i++;
        }

        String location = arguments.elementAt(i++);
        boolean reserveCar = Boolean.parseBoolean(arguments.elementAt(i++));
        boolean reserveRoom = Boolean.parseBoolean(arguments.elementAt(i));

        boolean success = false;

        // reserve flights
        for (String flightNum : flightNumbers) {
            Vector<String> arg = new Vector<>();

            arg.add(Command.ReserveFlight.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(flightNum));

            ResponsePacket flightResponse = forwardSingle(FLIGHTS, new Request(Command.ReserveFlight, arg));
            if (flightResponse.getStatus()) {
                success = true;
            }
        }
        // reserve a car
        if (reserveCar) {
            Vector<String> arg = new Vector<>();
            arg.add(Command.ReserveCar.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket carResponse = forwardSingle(CARS, new Request(Command.ReserveCar, arg));
            if (carResponse.getStatus()) {
                success = true;
            }
        }

        // Reserve a room
        if (success && reserveRoom) {
            Vector<String> arg = new Vector<>();
            arg.add(Command.ReserveRoom.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket roomResponse = forwardSingle(ROOMS, new Request(Command.ReserveRoom, arg));
            if (roomResponse.getStatus()) {
                success = true;
            }
        }

        if(success){
            return new ResponsePacket(true, "bundle operation partially/fully succeeded.");
        }

        return new ResponsePacket(false, "bundle operation failed completely");
    }


    // For customer creation/deletion across multiple managers

    /**
     * Handle customer request (needing multiple managers)
     * @param request
     * @return
     */
    private ResponsePacket forwardCustomer(Request request) {

        ResponsePacket result1;
        ResponsePacket result2;
        ResponsePacket result3;

        switch (request.getCommand()){
            // add a new customer in 1 resource manager, use the returned id for other resource managers
            // rollback: if one of them fails, delete the new customer in previous resource managers
            case AddCustomer:
                result1 = forwardSingle(FLIGHTS, request);
                String customerID = result1.getMessage();
                System.out.println("returned customer id:" + customerID);

                Vector<String> customerTemp = new Vector<>();
                customerTemp.add(Command.AddCustomerID.name());
                customerTemp.add(customerID);

                System.out.println("result for adding new customer to flight manager:" + result1.getStatus());

                if(!result1.getStatus()){
                    return result1;
                }

                result2 = forwardSingle(CARS, new Request(Command.AddCustomerID, customerTemp));
                System.out.println("result for adding new customer to cars manager:" + result2.getStatus());

                if(!result2.getStatus()){
                    customerTemp.set(0, Command.DeleteCustomer.name());
                    forwardSingle(FLIGHTS, new Request(Command.DeleteCustomer, customerTemp));
                    return result2;
                }

                result3 = forwardSingle(ROOMS, new Request(Command.AddCustomerID, customerTemp));
                if(!result3.getStatus()){
                    customerTemp.set(0, Command.DeleteCustomer.name());
                    forwardSingle(FLIGHTS, new Request(Command.DeleteCustomer, customerTemp));
                    forwardSingle(CARS, new Request(Command.DeleteCustomer, customerTemp));
                    return result3;
                }
                System.out.println("result for adding new customer to rooms manager:" + result3.getStatus());
                break;

            case AddCustomerID:
                result1 = forwardSingle(FLIGHTS, request);
                if(!result1.getStatus()){
                    return result1;
                }
                result2 = forwardSingle(CARS, request);
                if(!result2.getStatus()){
                    forwardSingle(FLIGHTS, new Request(Command.DeleteCustomer, request.getArguments()));
                    return result2;
                }
                result3 = forwardSingle(ROOMS, request);
                if(!result3.getStatus()){
                    forwardSingle(FLIGHTS, new Request(Command.DeleteCustomer, request.getArguments()));
                    forwardSingle(CARS, new Request(Command.DeleteCustomer, request.getArguments()));
                    return result3;
                }
                break;
            // success when: delete customer successfully
            // fail when: customer does not exist
            // If some of them failed, delete the customer from all managers
            // If no one succeeds, just give error message
            // No need for rollback
            case DeleteCustomer:
                result1 = forwardSingle(FLIGHTS, request);

                result2 = forwardSingle(CARS, request);

                result3 = forwardSingle(ROOMS, request);
                if(!result1.getStatus() && result2.getStatus() && !result3.getStatus()){
                    return new ResponsePacket(false, "Customer information is not synchronized. Data is reset.");
                }
                if(!result1.getStatus() || result2.getStatus() || result3.getStatus()){
                    return new ResponsePacket(false, "Customer does not exist.");
                }
                break;
            // no need for rollback
            // when one of them fails, return a response indicating failure
            // if all of them succeed, return a response containing the aggregation
            case QueryCustomer:
                result1 = forwardSingle(FLIGHTS, request);
                result2 = forwardSingle(CARS, request);
                result3 = forwardSingle(ROOMS, request);

                String msg =
                        result1.getMessage() +
                        result2.getMessage() +
                        result3.getMessage();
                return new ResponsePacket(true, msg);
            default:
                return new ResponsePacket(false, "Unknown command.");
        }
        return result1;
    }
}
