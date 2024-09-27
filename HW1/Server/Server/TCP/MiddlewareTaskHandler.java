package Server.TCP;

import Client.Command;
import Client.TCPClient.Request;
import Server.Common.ResponsePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;

class MiddlewareTaskHandler extends Thread{
    private Socket clientSocket;
    private Map<String, String> resourceManagerHosts;
    private final int managerPort = 3031;
    public MiddlewareTaskHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
    }

    public MiddlewareTaskHandler(Socket clientSocket, Map<String, String> resourceManagerHosts) {
        this.clientSocket = clientSocket;
        this.resourceManagerHosts = resourceManagerHosts;
    }


    public void run(){
        // TODO: ensure concurrency: what happens if multiple threads like this one executes?
        String command, commandLine;

        // from the socket, get the input and output streams
        try(
                ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
        ){
            // read the command line
            Request request = (Request) input.readObject();
            System.out.println("Received request: " + request.getCommand());

            ResponsePacket result = forwardBundle(request);

            output.writeObject(result);

        }catch(IOException | ClassNotFoundException e){
            System.err.println("Error processing the request on the server.");
            e.printStackTrace();

        }
    }

    // Forward the client request to the correct ResourceManager via TCP
    private ResponsePacket handleRequest(Request request) {

        Command command = request.getCommand();
        Vector<String> arguments = request.getArguments();
        System.out.println("Received command: " + request.getCommand());
        ResponsePacket result;

        // Given the resource type and forward the command
        // to an RM
        switch (command) {
            case AddFlight:
            case ReserveFlight:
            case DeleteFlight:
            case QueryFlight:
            case QueryFlightPrice:
                result = forwardSingle("flight", request);
                break;

            case AddCars:
            case DeleteCars:
            case QueryCars:
            case QueryCarsPrice:
            case ReserveCar:
                result = forwardSingle("car", request);
                break;

            case AddRooms:
            case DeleteRooms:
            case QueryRooms:
            case QueryRoomsPrice:
            case ReserveRoom:
                result = forwardSingle("room", request);
                break;

            case AddCustomer:
            case AddCustomerID:
            case DeleteCustomer:
            case QueryCustomer:
                result = forwardCustomer("customer", request);
                break;

            case Bundle:
                result = forwardBundle(request);
                break;

            default:
                result = new ResponsePacket(false,"Unknown command.");
        }
        return result;
    }

    // Forward request to a resource manager
    private ResponsePacket forwardSingle(String resourceType, Request request) {
        String host = resourceManagerHosts.get(resourceType);

        try (Socket rmSocket = new Socket(host, managerPort);
             ObjectOutputStream outToRM = new ObjectOutputStream(rmSocket.getOutputStream());
             ObjectInputStream inFromRM = new ObjectInputStream(rmSocket.getInputStream())) {

            // Forward
            outToRM.writeObject(request);

            // Receive result
            return (ResponsePacket) inFromRM.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error connecting to ResourceManager");
        }
    }

    // Handle bundle request
    private ResponsePacket forwardBundle(Request request) {

        // get the arguments
        // then forward to different resource managers
        // TODO: handle flights, cars, and rooms similar as previous logic
        // TODO: Implement rollback mechanism if any operation fails, as shown before.

        Vector<String> arguments = request.getArguments();
        int customerID = Integer.parseInt(arguments.get(1));
        Vector<String> flightNumbers = new Vector<>();
        int i = 2;
        while(i < arguments.size() - 3){
            flightNumbers.add(arguments.elementAt(i));
            i++;
        }

        String location = arguments.elementAt(i++);
        boolean reserveCar = Boolean.parseBoolean(arguments.elementAt(i++));
        boolean reserveRoom = Boolean.parseBoolean(arguments.elementAt(i));

        boolean success = false;

        // Reserve flights
        for (String flightNum : flightNumbers) {
            Vector<String> arg = new Vector<>();
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(flightNum));
            ResponsePacket flightResponse = forwardSingle("flight", new Request(Command.ReserveFlight, arg));
            if (flightResponse.getStatus()) {
                success = true;
            }
        }
        // Reserve car
        if (reserveCar) {
            Vector<String> arg = new Vector<>();
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket carResponse = forwardSingle("car", new Request(Command.ReserveCar, arg));
            if (carResponse.getStatus()) {
                success = true;
            }
        }

        // Reserve room
        if (success && reserveRoom) {
            Vector<String> arg = new Vector<>();
            arg.add(String.valueOf(customerID));
            arg.add(String.valueOf(location));
            ResponsePacket roomResponse = forwardSingle("room", new Request(Command.ReserveRoom, arg));
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
    private ResponsePacket forwardCustomer(String resourceType, Request request) {
        ResponsePacket result1;
        ResponsePacket result2;
        ResponsePacket result3;

        // TODO: apply similar logic for deletecustomer etc.
        switch (request.getCommand()){
            // add a new customer in 1 resource manager, use the returned id for other resource managers
            // rollback: if one of them fails, delete the new customer in previous resource managers
            case AddCustomer:
                result1 = forwardSingle("flight", request);
                int customerID = Integer.parseInt(result1.getMessage());
                Vector<String> customerTemp = new Vector<>();
                customerTemp.add(String.valueOf(customerID));
                if(!result1.getStatus()){
                    return result1;
                }
                result2 = forwardSingle("car", new Request(Command.AddCustomerID, customerTemp));
                if(!result2.getStatus()){
                    forwardSingle("flight", new Request(Command.DeleteCustomer, customerTemp));
                    return result2;
                }
                result3 = forwardSingle("room", new Request(Command.AddCustomerID, customerTemp));
                if(!result3.getStatus()){
                    forwardSingle("flight", new Request(Command.DeleteCustomer, customerTemp));
                    forwardSingle("car", new Request(Command.DeleteCustomer, customerTemp));
                    return result3;
                }
                break;
            // rollback: delete the new customer on resource managers who created the customer
            case AddCustomerID:
                result1 = forwardSingle("flight", request);
                if(!result1.getStatus()){
                    return result1;
                }
                result2 = forwardSingle("car", request);
                if(!result2.getStatus()){
                    forwardSingle("flight", new Request(Command.DeleteCustomer, request.getArguments()));
                    return result2;
                }
                result3 = forwardSingle("room", request);
                if(!result3.getStatus()){
                    forwardSingle("flight", new Request(Command.DeleteCustomer, request.getArguments()));
                    forwardSingle("car", new Request(Command.DeleteCustomer, request.getArguments()));
                    return result3;
                }
                break;
            // success when: delete customer successfully
            // fail when: customer does not exist
            // If some of them failed, delete the customer from all managers
            // If no one succeeds, give error message
            // Thus, no need for rollback
            case DeleteCustomer:
                result1 = forwardSingle("flight", request);

                result2 = forwardSingle("car", request);

                result3 = forwardSingle("room", request);
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
                result1 = forwardSingle("flight", request);
                result2 = forwardSingle("car", request);
                result3 = forwardSingle("room", request);
                if(!result1.getStatus() || result2.getStatus() || result3.getStatus()){
                    return result1;
                }
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
