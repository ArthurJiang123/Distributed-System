package Server.TCP;

import Client.Command;
import Client.TCPClient.Request;
import Server.Common.ResponsePacket;

import java.io.*;
import java.net.Socket;
import java.util.*;

class MiddlewareTaskHandler extends Thread{

    // maintains the socket of the client connected to the middleware
    private Socket clientSocket;
    private Socket flightSocket;
    private Socket carSocket;
    private Socket roomSocket;

    private ObjectOutputStream flightOutputStream;
    private ObjectInputStream flightInputStream;

    private ObjectInputStream carInputStream;
    private ObjectOutputStream carOutputStream;

    private ObjectInputStream roomInputStream;
    private ObjectOutputStream roomOutputStream;



    // maintains sockets connected to resource managers
    // resource types(Flights, Cars, Rooms) -> Socket
    private Map<String, Socket> rmSockets;

    private final static String FLIGHTS = "Flights";
    private final static String CARS = "Cars";
    private final static String ROOMS = "Rooms";

    private int rmPort;

    /**
     *
     * @param clientSocket
     * @param flightHost
     * @param carHost
     * @param roomHost
     * @param rmPort
     */
    public MiddlewareTaskHandler(Socket clientSocket, String flightHost, String carHost, String roomHost, int rmPort){

        this.clientSocket = clientSocket;
        this.rmPort = rmPort;

        // to be safer, we initialize output streams before input streams.
        try{

            this.flightSocket = new Socket(flightHost, rmPort);
            this.flightOutputStream = new ObjectOutputStream(flightSocket.getOutputStream());
            this.flightInputStream = new ObjectInputStream(flightSocket.getInputStream());

            this.carSocket = new Socket(carHost, rmPort);
            this.carOutputStream = new ObjectOutputStream(carSocket.getOutputStream());
            this.carInputStream = new ObjectInputStream(carSocket.getInputStream());

            this.roomSocket = new Socket(roomHost, rmPort);
            this.roomOutputStream = new ObjectOutputStream(roomSocket.getOutputStream());
            this.roomInputStream = new ObjectInputStream(roomSocket.getInputStream());
        }catch(IOException e){
            System.out.println("Error on initializing middleware task handler");
            e.printStackTrace();
        }
    }

    /**
     * Handles the request from the client
     * Using: the client socket and associated streams
     * Process: forward the request, and send the response back to the client
     */
    public void run(){

        System.out.println("Handling a new client request...");

        try(
                ObjectOutputStream clientOut = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream clientIn = new ObjectInputStream(clientSocket.getInputStream());
        ){

            // from the socket, get the input and output streams
            while(true){
                try {
                    // Read the request from the client
                    Request request = (Request) clientIn.readObject();
                    System.out.println("Received request: " + request.getCommand());

                    // Handle the request
                    ResponsePacket response = handleRequest(request);

                    // Send the response back to the client
                    clientOut.writeObject(response);
                    clientOut.flush();

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
           clientSocket.close();
           flightSocket.close();
           carSocket.close();
           roomSocket.close();
           System.out.println("Client connection closed and resources cleaned up.");
        } catch (IOException e) {
            System.err.println("Failed to close client and resource manager socket(s).");
            e.printStackTrace();
        }
    }

    private ResponsePacket forwardToFlight(Request request) {
        try {

            flightOutputStream.writeObject(request);
            flightOutputStream.flush();
            return (ResponsePacket) flightInputStream.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error forwarding request to Flight RM.");
        }
    }

    private ResponsePacket forwardToCar(Request request) {
        try {

            carOutputStream.writeObject(request);
            carOutputStream.flush();
            return (ResponsePacket) carInputStream.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error forwarding request to Flight RM.");
        }
    }

    private ResponsePacket forwardToRoom(Request request) {
        try {
            roomOutputStream.writeObject(request);
            roomOutputStream.flush();
            return (ResponsePacket) roomInputStream.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error forwarding request to Flight RM.");
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
                result = forwardToFlight(request);
                break;

            case AddCars:
            case DeleteCars:
            case QueryCars:
            case QueryCarsPrice:
            case ReserveCar:
                result = forwardToCar(request);
                break;

            case AddRooms:
            case DeleteRooms:
            case QueryRooms:
            case QueryRoomsPrice:
            case ReserveRoom:
                result = forwardToRoom(request);
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
     * Handle the bundle request
     * All-or-Nothing method.
     * Response contains true if all flight/room/car are reserved.
     * Response contains false if it failed to reserve any of them.
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

        Vector<String> reservedFlights = new Vector<>();
        // reserve flights
        for (String flightNum : flightNumbers) {
            Vector<String> arg = new Vector<>();

            arg.add(Command.ReserveFlight.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(flightNum));

            ResponsePacket flightResponse = forwardToFlight(new Request(Command.ReserveFlight, arg));

            // rollback
            // for each reserved flight, make request to cancel the reservation.
            if (!flightResponse.getStatus()) {

                System.out.println("Failed to reserve flight " + flightNum + " for customer " + customerID);
                System.out.println("Rollback:");
                if(!reservedFlights.isEmpty()){

                    for(String reservedFlight : reservedFlights){
                        Vector<String> reservedFlightsArgs = new Vector<>();

                        reservedFlightsArgs.add(Command.CancelReserveFlight.name());
                        reservedFlightsArgs.add(String.valueOf(customerID));
                        reservedFlightsArgs.add(reservedFlight);

                         ResponsePacket rollback = forwardToFlight(new Request(Command.CancelReserveFlight, reservedFlightsArgs ));

                        System.out.println("Flight reservation canceled for flight number " + reservedFlight);
                    }

                    System.out.println("Rollback completes for customer:" + customerID );
                }

                return new ResponsePacket(false, "bundle operation failed.");
            }

            reservedFlights.add(flightNum);
        }
        // reserve a car
        if (reserveCar) {
            Vector<String> arg = new Vector<>();
            arg.add(Command.ReserveCar.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket carResponse = forwardToCar(new Request(Command.ReserveCar, arg));

            if (!carResponse.getStatus()) {

                System.out.println("Failed to reserve car at " + location + " for customer " + customerID);
                System.out.println("Rollback:");
                if(!reservedFlights.isEmpty()){

                    for(String reservedFlight : reservedFlights){
                        Vector<String> reservedFlightsArgs = new Vector<>();

                        reservedFlightsArgs.add(Command.CancelReserveFlight.name());
                        reservedFlightsArgs.add(String.valueOf(customerID));
                        reservedFlightsArgs.add(reservedFlight);

                        ResponsePacket rollback = forwardToFlight(new Request(Command.CancelReserveFlight, reservedFlightsArgs ));

                        System.out.println("Flight reservation canceled for flight number " + reservedFlight);
                    }
                    System.out.println("Rollback completes for customer:" + customerID );
                }
                return new ResponsePacket(false, "bundle operation failed.");
            }
        }

        // Reserve a room
        if (reserveRoom) {
            Vector<String> arg = new Vector<>();
            arg.add(Command.ReserveRoom.name());
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket roomResponse = forwardToRoom(new Request(Command.ReserveRoom, arg));

            if (!roomResponse.getStatus()) {
                System.out.println("Failed to reserve room at " + location + " for customer " + customerID);
                System.out.println("Rollback:");
                if(!reservedFlights.isEmpty()){

                    for(String reservedFlight : reservedFlights){
                        Vector<String> reservedFlightsArgs = new Vector<>();

                        reservedFlightsArgs.add(Command.CancelReserveFlight.name());
                        reservedFlightsArgs.add(String.valueOf(customerID));
                        reservedFlightsArgs.add(reservedFlight);

                        ResponsePacket rollback = forwardToFlight(new Request(Command.CancelReserveFlight, reservedFlightsArgs ));

                        System.out.println("Flight reservation canceled for flight number " + reservedFlight);
                    }
                }

                if(reserveCar){
                    Vector<String> carArg = new Vector<>();
                    carArg.add(Command.CancelReserveCar.name());
                    carArg.add(String.valueOf(customerID));
                    carArg.add(String.valueOf(location));
                    ResponsePacket carResponse = forwardToCar(new Request(Command.CancelReserveCar, carArg));
                    System.out.println("Car reservation canceled at location  " + location);
                    return new ResponsePacket(false, "bundle operation failed.");
                }

                System.out.println("Rollback completes for customer:" + customerID );
                return new ResponsePacket(false, "bundle operation failed.");
            }
        }

        return new ResponsePacket(true, "bundle operation succeeded.");
    }

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
                result1 = forwardToFlight(request);
                String customerID = result1.getMessage();
                System.out.println("returned customer id:" + customerID);

                Vector<String> customerTemp = new Vector<>();
                customerTemp.add(Command.AddCustomerID.name());
                customerTemp.add(customerID);

                System.out.println("result for adding new customer to flight manager:" + result1.getStatus());

                if(!result1.getStatus()){
                    return result1;
                }

                result2 = forwardToCar(new Request(Command.AddCustomerID, customerTemp));
                System.out.println("result for adding new customer to cars manager:" + result2.getStatus());

                if(!result2.getStatus()){
                    customerTemp.set(0, Command.DeleteCustomer.name());
                    forwardToFlight(new Request(Command.DeleteCustomer, customerTemp));
                    return result2;
                }

                result3 = forwardToRoom(new Request(Command.AddCustomerID, customerTemp));
                if(!result3.getStatus()){
                    customerTemp.set(0, Command.DeleteCustomer.name());
                    forwardToFlight(new Request(Command.DeleteCustomer, customerTemp));
                    forwardToRoom(new Request(Command.DeleteCustomer, customerTemp));
                    return result3;
                }
                System.out.println("result for adding new customer to rooms manager:" + result3.getStatus());
                break;

            case AddCustomerID:
                result1 = forwardToFlight(request);
                if(!result1.getStatus()){
                    return result1;
                }
                result2 = forwardToCar(request);
                if(!result2.getStatus()){
                    forwardToFlight(new Request(Command.DeleteCustomer, request.getArguments()));
                    return result2;
                }

                result3 = forwardToRoom(request);
                if(!result3.getStatus()){
                    forwardToFlight(new Request(Command.DeleteCustomer, request.getArguments()));
                    forwardToCar(new Request(Command.DeleteCustomer, request.getArguments()));
                    return result3;
                }
                break;
            // success when: delete customer successfully
            // fail when: customer does not exist
            case DeleteCustomer:
                result1 = forwardToFlight(request);

                result2 = forwardToCar(request);

                result3 = forwardToRoom(request);

                break;
            // when one of them fails, return a response indicating failure
            // if all of them succeed, return a response containing the aggregation
            case QueryCustomer:
                result1 = forwardToFlight(request);
                result2 = forwardToCar(request);
                result3 = forwardToRoom(request);

                String msg =
                        "\nFlights " + result1.getMessage() +
                        "Cars " + result2.getMessage() +
                        "Rooms " + result3.getMessage();
                return new ResponsePacket(true, msg);
            default:
                return new ResponsePacket(false, "Unknown command.");
        }
        return result1;
    }


}
