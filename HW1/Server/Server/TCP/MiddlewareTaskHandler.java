package Server.TCP;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

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
        // TODO: handle client requests
        // TODO: ensure concurrency: what happens if multiple threads like this one executes?
        String command, commandLine;

        // from the socket, get the input and output streams
        try(
                ObjectInputStream input = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
        ){
            // read the command line
            commandLine = (String) input.readObject();
            System.out.println("Received command line: " + commandLine);

            Vector<String> arguments = parse(commandLine);
            command = arguments.elementAt(0).toLowerCase();
            String result = "";

            switch (command){
                //TODO: call methods...

                // Command format: ReserveFlight,customerID,flightNum
                // Command format: DeleteFlight,flightNum
                // Command format: QueryFlight,flightNum
                case "addflight":
                case "reserveflight":
                case "deleteflight":
                case "queryflight": {
                    // Command format: AddFlight,flightNum,flightSeats,flightPrice
                    result = handleRequest("flight", commandLine);
                    break;
                }

                // Command format: AddCars,location,numCars,price
                // Command format: DeleteCars,location
                // Command format: QueryCars,location
                // Command format: ReserveCar,customerID,location
                case "addcars":
                case "deletecars":
                case "querycars":
                case "reservecar": {
                    result = handleRequest("car", commandLine);
                    break;
                }

                // Command format: AddRooms,location,numRooms,price
                // Command format: DeleteRooms,location
                // Command format: QueryRooms,location
                // Command format: ReserveRoom,customerID,location
                case "addrooms":
                case "deleterooms":
                case "queryrooms":
                case "reserveroom": {
                    result = handleRequest("room", commandLine);
                    break;
                }

                // Command format: NewCustomer
                // Command format: NewCustomerID,customerID
                // Command format: DeleteCustomer,customerID
                case "newcustomer":
                case "newcustomerid":
                case "deletecustomer": {
                    result = handleRequest("customer", commandLine);
                    break;
                }
                // Command format: Bundle,customerID,flightNum1,flightNum2,...,location,bookCar,bookRoom
                case "bundle": {
                    result = handleBundleRequest(arguments);
                    break;
                }
                default:
                    result = "Error: Unknown command.";
                    break;
            }

            //TODO: standardize response format, refer to RMI?
            output.writeObject(result);

        }catch(IOException | ClassNotFoundException e){
            System.err.println("Error processing the request on the server.");
            e.printStackTrace();

        }
    }

    // Forward the client request to the correct ResourceManager via TCP
    private String handleRequest(String resourceType, String commandLine) {
        String host = resourceManagerHosts.get(resourceType);

        try (Socket rmSocket = new Socket(host, managerPort);
             ObjectOutputStream outToRM = new ObjectOutputStream(rmSocket.getOutputStream());
             ObjectInputStream inFromRM = new ObjectInputStream(rmSocket.getInputStream())
        ){

            // Forward the request to the ResourceManager
            outToRM.writeObject(commandLine);

            // Receive the result from the ResourceManager
            return (String) inFromRM.readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return "Error connecting to ResourceManager";
        }
    }

    private String handleCustomerRequest(String command, Vector<String> arguments){
        String result = "";
        try{

            if(command.equals("newcustomer")){
                // call 3 resource managers sequentially to create customers with the same id.
                int customerID = Integer.parseInt(handleRequest("flight", "newcustomer"));
                handleRequest("car", "newcustomerid," + customerID);
                handleRequest("room", "newcustomerid," + customerID);

                result = "New customer created with ID: " + customerID;
            }else if(command.equals("newcustomerid")){

                // call 3 resource managers sequentially to create customers with the same id.
                int customerID = Integer.parseInt(handleRequest("flight", "newcustomer"));
                handleRequest("car", "newcustomerid," + customerID);
                handleRequest("room", "newcustomerid," + customerID);

                result = "New customer created with ID: " + customerID;

            }else if(command.equals("deletecustomer")){

                int customerID = Integer.parseInt(arguments.elementAt(1));
                handleRequest("flight", command + "," + customerID);
                handleRequest("car", command + "," + customerID);
                handleRequest("room", command + "," + customerID);
                result = "Customer deleted successfully.";

            }
        }catch (Exception e) {
            result = "Error processing customer request.";
        }
        return result;
    }

    private String handleBundleRequest(Vector<String> arguments) {
        if (arguments.size() < 6) {
            return "Error: Incorrect number of arguments for Bundle.";
        }

        int customerID = toInt(arguments.elementAt(1));
        Vector<String> flightNumbers = new Vector<>();
        int i = 2;

        // Collect flight numbers
        while (!isNumeric(arguments.elementAt(i))) {
            flightNumbers.add(arguments.elementAt(i));
            i++;
        }

        String location = arguments.elementAt(i++);
        boolean bookCar = toBoolean(arguments.elementAt(i++));
        boolean bookRoom = toBoolean(arguments.elementAt(i));

        boolean success = true;

        // Reserve flights
        for (String flightNum : flightNumbers) {
            String flightCommand = "reserveflight," + customerID + "," + flightNum;
            if (!handleRequest("flight", flightCommand).equals("Flight reserved successfully.")) {
                success = false;
                break;
            }
        }

        // Reserve car
        if (success && bookCar) {
            String carCommand = "reservecar," + customerID + "," + location;
            if (!handleRequest("car", carCommand).equals("Car reserved successfully.")) {
                success = false;
            }
        }

        // Reserve room
        if (success && bookRoom) {
            String roomCommand = "reserveroom," + customerID + "," + location;
            if (!handleRequest("room", roomCommand).equals("Room reserved successfully.")) {
                success = false;
            }
        }

        return success ? "Bundle reservation successful." : "Bundle reservation failed.";
    }

    public static Vector<String> parse(String command)
    {
        Vector<String> arguments = new Vector<String>();
        StringTokenizer tokenizer = new StringTokenizer(command,",");
        String argument = "";
        while (tokenizer.hasMoreTokens())
        {
            argument = tokenizer.nextToken();
            argument = argument.trim();
            arguments.add(argument);
        }
        return arguments;
    }

    public static int toInt(String string) throws NumberFormatException
    {
        return (Integer.valueOf(string)).intValue();
    }

    // Helper method to convert string to boolean
    private boolean toBoolean(String str) {
        return Boolean.parseBoolean(str);
    }

    // Helper method to check if a string is numeric
    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
