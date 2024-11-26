import org.apache.zookeeper.*;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.UnknownHostException;
import java.util.List;

public class DistProcess implements Watcher, AsyncCallback.ChildrenCallback
{
    ZooKeeper zk;
    String zkServer, pinfo;
    boolean isManager=false;
    boolean initialized=false;

    Manager manager;
    Worker worker;

    DistProcess(String zkhost)
    {
        zkServer=zkhost;
        pinfo = ManagementFactory.getRuntimeMXBean().getName();
        System.out.println("DISTAPP : ZK Connection information : " + zkServer);
        System.out.println("DISTAPP : Process information : " + pinfo);
    }

    void startProcess() throws IOException, UnknownHostException, KeeperException, InterruptedException
    {
        zk = new ZooKeeper(zkServer, 10000, this); //connect to ZK.
    }

    void initialize()
    {
        try
        {
            runForManager();	// See if you can become the manager (i.e, no other manager exists)
            isManager=true;

            // Initialize Manager
            manager = new Manager(zkServer, zk);

            manager.initialize();

        }catch(KeeperException.NodeExistsException nee) {
            System.out.println("DISTAPP: Manager already exists.");
            isManager=false;

            // Initialize Worker
            worker = new Worker(zkServer, zk);
            worker.initialize();

        } // TODO: What else will you need if this was a worker process?
        catch(UnknownHostException | InterruptedException | KeeperException uhe) {
            System.out.println(uhe);
        }

        System.out.println("DISTAPP : Role : " + " I will be functioning as " +(isManager?"manager":"worker"));

    }

    @Override
    public void process(WatchedEvent e) {
        System.out.println("DISTAPP: Event received: " + e);

        if (e.getType() == Watcher.Event.EventType.None) {
            if (e.getPath() == null && e.getState() == Watcher.Event.KeeperState.SyncConnected && !initialized) {

                // decides the role of this node
                initialize();
                initialized = true;
            }
        }
    }

    // Try to become the manager.
    void runForManager() throws UnknownHostException, KeeperException, InterruptedException
    {
        //Try to create an ephemeral node to be the manager, put the hostname and pid of this process as the data.
        // This is an example of Synchronous API invocation as the function waits for the execution and no callback is involved..
        zk.create("/dist31/manager", pinfo.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }


    public static void main(String args[]) throws Exception
    {
        //Create a new process
        //Read the ZooKeeper ensemble information from the environment variable.

        DistProcess dt = new DistProcess(System.getenv("ZKSERVER"));

        dt.startProcess();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("DISTAPP: Shutdown hook triggered.");
            if (!dt.isManager) {
                Worker worker = new Worker(dt.zkServer, dt.zk);
                worker.cleanup();
            } else {
                System.out.println("DISTAPP: Manager does not require explicit cleanup in this implementation.");
            }
        }));

        //Replace this with an approach that will make sure that the process is up and running forever.
//        Thread.sleep(30000);

        // Keep the process running
        synchronized (dt) {
            dt.wait();
        }

        if( !dt.isManager && dt.worker != null){
            dt.worker.cleanup();
        }
    }

    @Override
    public void processResult(int i, String s, Object o, List<String> list) {

    }
}