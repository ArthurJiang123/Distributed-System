package Server.RMI;

import Server.Common.*;
import Server.Interface.IResourceManager;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Middleware extends ResourceManager {

    private static String s_serverName = "Middleware";
    //TODO: ADD YOUR GROUP NUMBER TO COMPLETE
    private static String s_rmiPrefix = "group_31_";
    private static int port = 3031;

    // managers
    private IResourceManager flightManager;
    private IResourceManager carManager;
    private IResourceManager roomManager;

    // lock acquisition order for acquiring multiple locks: flightLock -> carLock -> roomLock
    private final Lock flightLock = new ReentrantLock();
    private final Lock carLock = new ReentrantLock();
    private final Lock roomLock = new ReentrantLock();


    public Middleware(String p_name, IResourceManager flightManager, IResourceManager carManager, IResourceManager roomManager) {
        super(p_name);
        this.flightManager = flightManager;
        this.carManager = carManager;
        this.roomManager = roomManager;
    }

    public static void main(String args[]) {

        if (args.length < 3) {
            System.err.println ("<usage> java Server.RMI.Middleware <flightHost> <carHost> <roomHost>");
            System.exit(1);
        }

        // host name + complete name(prefix + server name) of flight manager
        String flightHost = args[0];
        String flightName = "Flights";

        // host name + complete name(prefix + server name) of car manager
        String carHost = args[1];
        String carName = "Cars";

        // host name + complete name(prefix + server name) of room manager
        String roomHost = args[2];
        String roomName = "Rooms";

        // Create the RMI server entry
        try {

            // IMPORTANT: assume each manager runs on a different host with the same port number.
            IResourceManager flightManager = connectToResourceManager(flightHost, flightName);
            IResourceManager carManager = connectToResourceManager(carHost, carName);
            IResourceManager roomManager = connectToResourceManager(roomHost, roomName);

            // Create a middleware
            // using customized server name and the received 3 resource managers
            Middleware middleware = new Middleware(s_serverName, flightManager, carManager, roomManager);

            // Dynamically export the object, generate the stub (client proxy)
            IResourceManager middlewareStub = (IResourceManager) UnicastRemoteObject.exportObject(middleware, 0);

            // Bind the remote object's stub in the registry;
            // if a registry does not exist in that port, create it.
            // if a registry exists already, then get it.
            Registry l_registry;
            try {
                l_registry = LocateRegistry.createRegistry(3031);
            } catch (RemoteException e) {
                l_registry = LocateRegistry.getRegistry(3031);
            }
            final Registry registry = l_registry;

            // registry name == prefix + server name, binding the stub to that registry
            registry.rebind(s_rmiPrefix + s_serverName, middlewareStub);

            // upon shutdown, adding a hook to unbind registry and unexport the stub.
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        registry.unbind(s_rmiPrefix + s_serverName);
                        System.out.println("'" + s_serverName + "' resource manager unbound");
                    }
                    catch(Exception e) {
                        System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
                        e.printStackTrace();
                    }
                }
            });

            System.out.println("'" + s_serverName + "' resource manager server ready and bound to '" + s_rmiPrefix + s_serverName + "'");
        }
        catch (Exception e) {
            System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static IResourceManager connectToResourceManager(String host, String name) {
        try {
            boolean first = true;
            while (true) {
                try {

                    Registry registry = LocateRegistry.getRegistry(host, port);
                    IResourceManager manager = (IResourceManager) registry.lookup(s_rmiPrefix + name);

                    System.out.println("Connected to '" + name + "' server [" + host + ":" + port + "/" + s_rmiPrefix + name + "]");
                    return manager;
                }
                catch (NotBoundException | RemoteException e) {
                    if (first) {
                        System.out.println("Waiting for '" + name + "' server [" + host + ":" + port + "/" + s_rmiPrefix + name + "]");
                        first = false;
                    }
                }

                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    @Override
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean reserveCar, boolean reserveRoom) throws RemoteException {

        flightLock.lock();
        carLock.lock();
        roomLock.lock();
        boolean success = false;

        System.out.println("Starting bundle reservation for customer: " + customerID);
        System.out.println("Location: " + location + ", Reserve Car: " + reserveCar + ", Reserve Room: " + reserveRoom);

        try{
            // Try to reserve flights
            for (String flightNumStr : flightNumbers) {
                int flightNum = Integer.parseInt(flightNumStr);
                boolean flightReserved = flightManager.reserveFlight(customerID, flightNum);

                if (flightReserved) {
                    System.out.println("Flight " + flightNum + " reserved for customer " + customerID);
                    success = true;
                } else {
                    System.out.println("Failed to reserve flight " + flightNum + " for customer " + customerID);
                }
            }

            // reserve a car if the customer wants
            if (reserveCar) {
                boolean carReserved = carManager.reserveCar(customerID, location);
                if (carReserved) {
                    System.out.println("Car reserved at " + location + " for customer " + customerID);
                    success = true;
                } else {
                    System.out.println("Failed to reserve car at " + location + " for customer " + customerID);
                }
            }

            // reserve a room if the customer wants
            if (reserveRoom) {
                boolean roomReserved = roomManager.reserveRoom(customerID, location);
                if (roomReserved) {
                    System.out.println("Room reserved at " + location + " for customer " + customerID);
                    success = true;
                } else {
                    System.out.println("Failed to reserve room at " + location + " for customer " + customerID);
                }
            }
        }finally {
            flightLock.unlock();
            carLock.unlock();
            roomLock.unlock();
        }

        return success;
    }

    @Override
    public boolean deleteCustomer(int customerID) throws RemoteException {
        // Delete customer from all managers
        flightLock.lock();
        carLock.lock();
        roomLock.lock();
        boolean flightDeleted, carDeleted, roomDeleted;
        try{
            flightDeleted = flightManager.deleteCustomer(customerID);
            carDeleted = carManager.deleteCustomer(customerID);
            roomDeleted = roomManager.deleteCustomer(customerID);
        }finally {
            flightLock.unlock();
            carLock.unlock();
            roomLock.unlock();
        }

        return flightDeleted && carDeleted && roomDeleted;
    }

    @Override
    public int newCustomer() throws RemoteException{
        flightLock.lock();
        carLock.lock();
        roomLock.lock();
        int customerID = -1;
        try{
            customerID = flightManager.newCustomer();

            boolean carCreated = false;
            boolean roomCreated = false;

            try{
                carCreated = carManager.newCustomer(customerID);
                roomCreated = roomManager.newCustomer(customerID);
            }catch (RemoteException  e){
                rollbackCustomerCreation(customerID);
                System.err.println("Error creating customer in one of the ResourceManagers: " + e.getMessage());
                return -1;
            }
            if(!carCreated || !roomCreated){
                rollbackCustomerCreation(customerID);
                System.err.println("Failed to create customer in all ResourceManagers.");
                return -1;
            }
        }finally {
            flightLock.unlock();
            carLock.unlock();
            roomLock.unlock();
        }
        return customerID;
    }

    @Override
    public boolean newCustomer(int customerID) throws RemoteException{
        flightLock.lock();
        carLock.lock();
        roomLock.lock();
        try{
            boolean flightCreated = flightManager.newCustomer(customerID);
            boolean carCreated = false;
            boolean roomCreated = false;
            try{
                carCreated = carManager.newCustomer(customerID);
                roomCreated = roomManager.newCustomer(customerID);

            }catch (RemoteException  e){
                rollbackCustomerCreation(customerID);
                System.err.println("Error creating customer in carManager or roomManager: " + e.getMessage());
                return false;
            }

            if (!flightCreated || !carCreated || !roomCreated) {
                rollbackCustomerCreation(customerID);
                System.err.println("Failed to create customer across all ResourceManagers.");
                return false;
            }
        }finally {
            flightLock.unlock();
            carLock.unlock();
            roomLock.unlock();
        }

        return true;
    }

    // helper method only
    private void rollbackCustomerCreation(int customerId) {

        // Rollback customer creation in all managers
        try {
            flightManager.deleteCustomer(customerId);
        } catch (RemoteException e) {
            System.err.println("Failed to rollback customer creation in flightManager: " + e.getMessage());
        }

        try {
            carManager.deleteCustomer(customerId);
        } catch (RemoteException e) {
            System.err.println("Failed to rollback customer creation in carManager: " + e.getMessage());
        }

        try {
            roomManager.deleteCustomer(customerId);
        } catch (RemoteException e) {
            System.err.println("Failed to rollback customer creation in roomManager: " + e.getMessage());
        }
    }

    @Override
    public String queryCustomerInfo(int customerID) throws RemoteException{

        flightLock.lock();
        carLock.lock();
        roomLock.lock();
        String flightInfo, carInfo, roomInfo;
        try{
            // Fetch customer information from all managers
            flightInfo = flightManager.queryCustomerInfo(customerID);
            carInfo = carManager.queryCustomerInfo(customerID);
            roomInfo = roomManager.queryCustomerInfo(customerID);
        }finally {
            flightLock.unlock();
            carLock.unlock();
            roomLock.unlock();
        }

        return flightInfo + carInfo + roomInfo;
    }


    @Override
    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
        flightLock.lock();
        try{
            return flightManager.addFlight(flightNum, flightSeats, flightPrice);
        }finally {
            flightLock.unlock();
        }
    }

    @Override
    public boolean addCars(String location, int count, int price) throws RemoteException{
        carLock.lock();
        try{
            return carManager.addCars(location, count, price);
        }finally {
            carLock.unlock();
        }
    }

    @Override
    public boolean addRooms(String location, int count, int price) throws RemoteException{
        roomLock.lock();
        try {
            return roomManager.addRooms(location, count, price);
        }finally {
            roomLock.unlock();
        }
    }

    @Override
    public boolean deleteFlight(int flightNum) throws RemoteException {
        flightLock.lock();
        try{
            return flightManager.deleteFlight(flightNum);
        }finally {
            flightLock.unlock();
        }
    }
    @Override
    public boolean deleteCars(String location) throws RemoteException {
        carLock.lock();
        try{
            return carManager.deleteCars(location);
        }finally {
            carLock.unlock();
        }
    }
    @Override
    public boolean deleteRooms(String location) throws RemoteException{
        roomLock.lock();
        try{
            return roomManager.deleteRooms(location);
        }finally {
            roomLock.unlock();
        }
    }

    @Override
    public boolean reserveRoom(int customerID, String location) throws RemoteException{
        roomLock.lock();
        try{
            return roomManager.reserveRoom(customerID, location);
        }finally {
            roomLock.unlock();
        }
    }

    @Override
    public boolean reserveFlight(int customerID, int flightNum) throws RemoteException{
        flightLock.lock();
        try{
            return flightManager.reserveFlight(customerID, flightNum);
        }finally {
            flightLock.unlock();
        }
    }

    @Override
    public boolean reserveCar(int customerID, String location) throws RemoteException{
        carLock.lock();
        try{
            return carManager.reserveCar(customerID, location);
        }finally {
            carLock.unlock();
        }
    }

    @Override
    public int queryFlightPrice(int flightNum) throws RemoteException
    {
        flightLock.lock();
        try{
            return flightManager.queryFlightPrice(flightNum);
        }finally {
            flightLock.unlock();
        }
    }

    // Returns price of cars at this location
    public int queryCarsPrice(String location) throws RemoteException
    {
        carLock.lock();
        try{
            return carManager.queryCarsPrice(location);
        }finally {
            carLock.unlock();
        }
    }

    // Returns room price at this location
    public int queryRoomsPrice(String location) throws RemoteException
    {
        roomLock.lock();
        try{
            return roomManager.queryRoomsPrice(location);
        }finally {
            roomLock.unlock();
        }
    }


    public int queryFlight(int flightNum) throws RemoteException
    {
        flightLock.lock();
        try{
            return flightManager.queryFlight(flightNum);
        }finally {
            flightLock.unlock();
        }
    }

    // Returns the number of cars available at a location
    public int queryCars(String location) throws RemoteException
    {
        carLock.lock();
        try {
            return carManager.queryCars(location);
        }finally {
            carLock.unlock();
        }
    }

    // Returns the amount of rooms available at a location
    public int queryRooms(String location) throws RemoteException
    {
        roomLock.lock();
        try{
            return roomManager.queryRooms(location);
        }finally {
            roomLock.unlock();
        }
    }

}
