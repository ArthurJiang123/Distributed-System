package Server.TCP;

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

            String commandLine = (String)input.readObject();
            Vector<String> arguments = parse(commandLine);
            String command = arguments.elementAt(0).toLowerCase();

            ResponsePacket result = new ResponsePacket();
            boolean success;
            String message;
            switch (command) {

                // Command format: ReserveFlight,customerID,flightNum
                // Command format: DeleteFlight,flightNum
                // Command format: QueryFlight,flightNum
                case "addflight": {
                    if (arguments.size() != 4) {
                        success = false;
                        message = "Error: Incorrect number of arguments for AddFlight.";
                        break;
                    }

                    int flightNum = Integer.parseInt(arguments.elementAt(1));
                    int flightSeats = Integer.parseInt(arguments.elementAt(2));
                    int flightPrice = Integer.parseInt(arguments.elementAt(3));
                    success = resourceManager.addFlight(flightNum, flightSeats, flightPrice);
                    message = success ? "Flight added successfully." : "Failed to add flight.";
                    break;
                }
                case "reserveflight": {
                    if(arguments.size() != 2){
                        success = false;
                        message = "Error: Incorrect number of arguments for ReserveFlight.";
                        break;
                    }
                    int customerID = Integer.parseInt(arguments.elementAt(1));
                    int flightNum = Integer.parseInt(arguments.elementAt(2));

                    success = resourceManager.reserveFlight(customerID, flightNum);
                    message = success ? "Flight reserved successfully." : "Failed to reserve flight.";
                    break;
                }

                case "deleteflight": {
                    if (arguments.size() != 2) {
                        success = Boolean.FALSE;
                        message = "Error: Incorrect number of arguments for DeleteFlight.";
                        break;
                    }

                    int flightNum = Integer.parseInt(arguments.elementAt(1));
                    success = resourceManager.deleteFlight(flightNum);
                    message = success ? "Flight deleted successfully." : "Failed to delete flight.";
                    break;
                }

                case "queryflight": {
                    if(arguments.size()!=1){
                        success = Boolean.FALSE;
                        message = "Error: Incorrect number of arguments for QueryFlight.";
                        break;
                    }
                    int flightNum = Integer.parseInt(arguments.elementAt(1));
                    message =String.valueOf(resourceManager.queryFlight(flightNum));
                    success = Boolean.TRUE;
                    break;
                }

                // Command format: AddCars,location,numCars,price
                // Command format: DeleteCars,location
                // Command format: QueryCars,location
                // Command format: ReserveCar,customerID,location
                case "addcars": {
                    if(arguments.size() != 3){
                        message = "Error: Incorrect number of arguments for AddCar.";
                        success = Boolean.FALSE;
                        break;
                    }
                    String location = arguments.elementAt(1);
                    int numCars = Integer.parseInt(arguments.elementAt(2));
                    int price = Integer.parseInt(arguments.elementAt(3));
                    success = resourceManager.addCars(location, numCars, price);
                    message = success? "Cars created successfully." : "Failed to create a car.";
                    break;
                }

                case "deletecars" :{
                    if(arguments.size() != 1){
                        message = "Error: Incorrect number of arguments for DeleteCar.";
                        success = Boolean.FALSE;
                        break;
                    }
                    String location = arguments.elementAt(1);
                    success = resourceManager.deleteCars(location);
                    message = success ? "Cars deleted successfully." : "Failed to delete a car.";
                    break;
                }

                case "querycars": {
                    if(arguments.size() != 1){
                        message = "Error: Incorrect number of arguments for QueryCars.";
                        success = Boolean.FALSE;
                        break;
                    }
                    String location = arguments.elementAt(1);
                    message = String.valueOf(resourceManager.queryCars(location));
                    success = Boolean.TRUE;
                    break;
                }

                case "reservecar":{
                    if(arguments.size() != 2){
                        message = "Error: Incorrect number of arguments for QueryCars.";
                        success = Boolean.FALSE;
                        break;
                    }

                    int customerID = Integer.parseInt(arguments.elementAt(1));
                    String location = arguments.elementAt(2);
                    success = resourceManager.reserveCar(customerID, location);
                    message = success ? "Cars reserved successfully." : "Failed to reserve a car.";
                    break;
                }

                // TODO:Add more cases like queryFlight, reserveCar, etc.
                default:
                    success = Boolean.FALSE;
                    message = "Error: Unknown command.";
                    break;
            }

            result.setMessage(message);
            result.setStatus(success);

            // Send the result back to the Middleware
            output.writeObject(result);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Vector<String> parse(String commandLine) {
        Vector<String> arguments = new Vector<>();
        StringTokenizer tokenizer = new StringTokenizer(commandLine, ",");
        while (tokenizer.hasMoreTokens()) {
            arguments.add(tokenizer.nextToken().trim());
        }
        return arguments;
    }

}
