package Server.TCP;

import Client.Command;
import Client.TCPClient.Request;
import Server.Common.ResourceManager;
import Server.Common.ResponsePacket;

import java.io.*;
import java.net.Socket;
import java.util.Vector;

class RMTaskHandler extends Thread {

    // maintains the socket of the middleware, after connecting with a middleware
    private final Socket middlewareSocket;
    // maintains the reference to the corresponding resource manager
    private final ResourceManager resourceManager;

    public RMTaskHandler(Socket clientSocket, ResourceManager resourceManager){
        this.middlewareSocket = clientSocket;
        this.resourceManager = resourceManager;
    }

    /**
     * Automatically starts when the thread starts running.
     * Keep listening for incoming requests from the Middleware
     * Then process them.
     * Use: the socket of the middleware connected to this manager
     */
    @Override
    public void run(){
        System.out.println("Started handling a new connection from Middleware...");

        try (
                ObjectOutputStream output = new ObjectOutputStream(middlewareSocket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(middlewareSocket.getInputStream());
        ) {
            // Keep handling request
            // until the middleware closes the connection
            while (!middlewareSocket.isClosed()) {

                try {
                    Request request = (Request) input.readObject();
                    Vector<String> arguments = request.getArguments();
                    Command command = request.getCommand();

                    System.out.println("Received command: " + command + " with arguments: " + arguments);
                    ResponsePacket response = processCommand(command, arguments);

                    output.writeObject(response);
                    output.flush();

                } catch (EOFException eof) {
                    System.out.println("Middleware has closed the connection.");
                    break;
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error setting up RMTaskHandler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                middlewareSocket.close();
                System.out.println("Middleware socket closed for the Resource Manager.");
            } catch (IOException e) {
                System.err.println("Failed to close middleware socket.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Process the command and return the response.
     * argument format: [command, param1, param2, ...]
     * @param command
     * @param arguments
     * @return
     */
    private ResponsePacket processCommand(Command command, Vector<String> arguments) {
        try {
            switch (command) {
                case Help:

                case AddFlight:
                    boolean flightAdded = resourceManager.addFlight(
                            Integer.parseInt(arguments.get(1)),
                            Integer.parseInt(arguments.get(2)),
                            Integer.parseInt(arguments.get(3))
                    );
                    return new ResponsePacket(flightAdded, flightAdded ? "Flight added successfully." : "Failed to add flight.");

                case ReserveFlight:
                    boolean flightReserved = resourceManager.reserveFlight(
                            Integer.parseInt(arguments.get(1)),
                            Integer.parseInt(arguments.get(2))
                    );
                    return new ResponsePacket(flightReserved, flightReserved ? "Flight reserved successfully." : "Failed to reserve flight.");

                case DeleteFlight:
                    boolean flightDeleted = resourceManager.deleteFlight(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(flightDeleted, flightDeleted ? "Flight deleted successfully." : "Failed to delete flight.");

                case QueryFlight:
                    int seats = resourceManager.queryFlight(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(true, String.valueOf(seats));

                case QueryFlightPrice:
                    int price = resourceManager.queryFlightPrice(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(true, String.valueOf(price));

                case AddCars:
                    boolean carsAdded = resourceManager.addCars(
                            arguments.get(1),
                            Integer.parseInt(arguments.get(2)),
                            Integer.parseInt(arguments.get(3))
                    );
                    return new ResponsePacket(carsAdded, carsAdded ? "Cars added successfully." : "Failed to add cars.");

                case ReserveCar:
                    boolean carReserved = resourceManager.reserveCar(
                            Integer.parseInt(arguments.get(1)),
                            arguments.get(2)
                    );
                    return new ResponsePacket(carReserved, carReserved ? "Car reserved successfully." : "Failed to reserve car.");

                case DeleteCars:
                    boolean carsDeleted = resourceManager.deleteCars(arguments.get(1));
                    return new ResponsePacket(carsDeleted, carsDeleted ? "Cars deleted successfully." : "Failed to delete cars.");

                case QueryCars:
                    int availableCars = resourceManager.queryCars(arguments.get(1));
                    return new ResponsePacket(true, String.valueOf(availableCars));

                case QueryCarsPrice:
                    int carPrice = resourceManager.queryCarsPrice(arguments.get(1));
                    return new ResponsePacket(true,  String.valueOf(carPrice));

                case AddRooms:
                    boolean roomsAdded = resourceManager.addRooms(
                            arguments.get(1),
                            Integer.parseInt(arguments.get(2)),
                            Integer.parseInt(arguments.get(3))
                    );
                    return new ResponsePacket(roomsAdded, roomsAdded ? "Rooms added successfully." : "Failed to add rooms.");

                case ReserveRoom:
                    boolean roomReserved = resourceManager.reserveRoom(
                            Integer.parseInt(arguments.get(1)),
                            arguments.get(2)
                    );
                    return new ResponsePacket(roomReserved, roomReserved ? "Room reserved successfully." : "Failed to reserve room.");

                case DeleteRooms:
                    boolean roomsDeleted = resourceManager.deleteRooms(arguments.get(1));
                    return new ResponsePacket(roomsDeleted, roomsDeleted ? "Rooms deleted successfully." : "Failed to delete rooms.");

                case QueryRooms:
                    int availableRooms = resourceManager.queryRooms(arguments.get(1));
                    return new ResponsePacket(true, String.valueOf(availableRooms));

                case QueryRoomsPrice:
                    int roomPrice = resourceManager.queryRoomsPrice(arguments.get(1));
                    return new ResponsePacket(true, String.valueOf(roomPrice));

                case AddCustomer:
                    int customerID = resourceManager.newCustomer();
                    return new ResponsePacket(true, String.valueOf(customerID));

                case AddCustomerID:
                    boolean customerAdded = resourceManager.newCustomer(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(customerAdded, customerAdded ? "Customer added successfully." : "Failed to add customer.");

                case DeleteCustomer:
                    boolean customerDeleted = resourceManager.deleteCustomer(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(customerDeleted, customerDeleted ? "Customer deleted successfully." : "Failed to delete customer.");

                case QueryCustomer:
                    String bill = resourceManager.queryCustomerInfo(Integer.parseInt(arguments.get(1)));
                    return new ResponsePacket(true, bill);
                default:
                    return new ResponsePacket(false, "Unknown command.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponsePacket(false, "Error processing request.");
        }
    }

}
