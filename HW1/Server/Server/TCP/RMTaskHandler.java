package Server.TCP;

import Client.Command;
import Client.TCPClient.Request;
import Server.Common.ResourceManager;
import Server.Common.ResponsePacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Vector;

class RMTaskHandler extends Thread {


    private final Socket middlewareSocket;
    private final ResourceManager resourceManager;

    public RMTaskHandler(Socket clientSocket, ResourceManager resourceManager){
        this.middlewareSocket = clientSocket;
        this.resourceManager = resourceManager;
    }

    @Override
    public void run(){
        try(
                ObjectInputStream input = new ObjectInputStream(middlewareSocket.getInputStream());
                ObjectOutput output = new ObjectOutputStream(middlewareSocket.getOutputStream());
        ){

            Request request = (Request) input.readObject();
            Vector<String> arguments = request.getArguments();
            Command command = request.getCommand();

            ResponsePacket response = processCommand(command, arguments);


            // Send the result back to the Middleware
            output.writeObject(response);

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

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

                // Car commands
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

                // Room commands
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

                // Customer commands
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
