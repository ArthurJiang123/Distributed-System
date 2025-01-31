package Server.Interface;

import Server.Common.Car;
import Server.Common.Flight;
import Server.Common.Room;

import java.rmi.Remote;
import java.rmi.RemoteException;

import java.util.*;

/** 
 * Simplified version from CSE 593 Univ. of Washington
 *
 * Distributed  System in Java.
 * 
 * failure reporting is done using two pieces, exceptions and boolean 
 * return values.  Exceptions are used for systemy things. Return
 * values are used for operations that would affect the consistency
 * 
 * If there is a boolean return value and you're not sure how it 
 * would be used in your implementation, ignore it.  I used boolean
 * return values in the interface generously to allow flexibility in 
 * implementation.  But don't forget to return true when the operation
 * has succeeded.
 */

public interface IResourceManager extends Remote 
{
    /**
     * Add seats to a flight.
     *
     * In general this will be used to create a new
     * flight, but it should be possible to add seats to an existing flight.
     * Adding to an existing flight should overwrite the current price of the
     * available seats.
     *
     * @return Success
     */
    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) 
	throws RemoteException; 
    
    /**
     * Add car at a location.
     *
     * This should look a lot like addFlight, only keyed on a string location
     * instead of a flight number.
     *
     * @return Success
     */
    public boolean addCars(String location, int numCars, int price) 
	throws RemoteException; 
   
    /**
     * Add room at a location.
     *
     * This should look a lot like addFlight, only keyed on a string location
     * instead of a flight number.
     *
     * @return Success
     */
    public boolean addRooms(String location, int numRooms, int price) 
	throws RemoteException; 			    
			    
    /**
     * Add customer.
     *
     * @return Unique customer identifier
     */
    public int newCustomer() 
	throws RemoteException; 
    
    /**
     * Add customer with id.
     *
     * @return Success
     */
    public boolean newCustomer(int cid)
        throws RemoteException;

    /**
     * Delete the flight.
     *
     * deleteFlight implies whole deletion of the flight. If there is a
     * reservation on the flight, then the flight cannot be deleted
     *
     * @return Success
     */   
    public boolean deleteFlight(int flightNum) 
	throws RemoteException; 
    
    /**
     * Delete all cars at a location.
     *
     * It may not succeed if there are reservations for this location
     *
     * @return Success
     */		    
    public boolean deleteCars(String location) 
	throws RemoteException; 

    /**
     * Delete all rooms at a location.
     *
     * It may not succeed if there are reservations for this location.
     *
     * @return Success
     */
    public boolean deleteRooms(String location) 
	throws RemoteException; 
    
    /**
     * Delete a customer and associated reservations.
     *
     * @return Success
     */
    public boolean deleteCustomer(int customerID) 
	throws RemoteException; 

    /**
     * Query the status of a flight.
     *
     * @return Number of empty seats
     */
    public int queryFlight(int flightNumber) 
	throws RemoteException; 

    /**
     * Query the status of a car location.
     *
     * @return Number of available cars at this location
     */
    public int queryCars(String location) 
	throws RemoteException; 

    /**
     * Query the status of a room location.
     *
     * @return Number of available rooms at this location
     */
    public int queryRooms(String location) 
	throws RemoteException; 

    /**
     * Query the customer reservations.
     *
     * @return A formatted bill for the customer
     */
    public String queryCustomerInfo(int customerID) 
	throws RemoteException; 
    
    /**
     * Query the status of a flight.
     *
     * @return Price of a seat in this flight
     */
    public int queryFlightPrice(int flightNumber) 
	throws RemoteException; 

    /**
     * Query the status of a car location.
     *
     * @return Price of car
     */
    public int queryCarsPrice(String location) 
	throws RemoteException; 

    /**
     * Query the status of a room location.
     *
     * @return Price of a room
     */
    public int queryRoomsPrice(String location) 
	throws RemoteException; 

    /**
     * Reserve a seat on this flight.
     *
     * @return Success
     */
    public boolean reserveFlight(int customerID, int flightNumber) 
	throws RemoteException; 

    /**
     * Reserve a car at this location.
     *
     * @return Success
     */
    public boolean reserveCar(int customerID, String location) 
	throws RemoteException; 

    /**
     * Reserve a room at this location.
     *
     * @return Success
     */
    public boolean reserveRoom(int customerID, String location) 
	throws RemoteException;

    /**
     * Cancel reservation of a flight
     * @param customerID
     * @param flightNum
     * @return
     * @throws RemoteException
     */
    public boolean cancelReserveFlight(int customerID, int flightNum)
    throws RemoteException;

    /**
     * Cancel reservation of a car
     * @param customerID
     * @param location
     * @return
     * @throws RemoteException
     */
    public boolean cancelReserveCar(int customerID, String location)
    throws RemoteException;

    /**
     * Cancel reservation of a room
     * @param customerID
     * @param location
     * @return
     * @throws RemoteException
     */
    public boolean cancelReserveRoom(int customerID, String location)
    throws RemoteException;

    /**
     * Reserve a bundle for the trip.
     *
     * @return Success
     */
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room)
	throws RemoteException; 

    /**
     * Convenience for probing the resource manager.
     *
     * @return Name
     */
    public String getName()
        throws RemoteException;
}
