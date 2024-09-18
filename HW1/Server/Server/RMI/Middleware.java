package Server.RMI;

import Server.Common.ResourceManager;
import Server.Interface.IResourceManager;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Middleware extends ResourceManager {

    private static String s_serverName = "Server";
    //TODO: ADD YOUR GROUP NUMBER TO COMPLETE
    private static String s_rmiPrefix = "group_31_";

    public Middleware(String p_name) {
        super(p_name);
    }

    public static void main(String args[])
    {
        if (args.length > 0)
        {
            s_serverName = args[0];
        }

        // Create the RMI server entry
        try {
            // Create a new Server object
            Middleware middleware = new Middleware(s_serverName);

            // Dynamically generate the stub (client proxy)
            IResourceManager resourceManager = (IResourceManager) UnicastRemoteObject.exportObject(middleware, 0);


            // Bind the remote object's stub in the registry; adjust port if appropriate
            Registry l_registry;


            try {

                l_registry = LocateRegistry.createRegistry(3031);

            } catch (RemoteException e) {

                l_registry = LocateRegistry.getRegistry(3031);

            }

            final Registry registry = l_registry;

            registry.rebind(s_rmiPrefix + s_serverName, resourceManager);

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
}
