import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.*;

public class Manager extends DistProcess{

    // each task: task-taskid
    private final Queue<String> unassignedTasks = new LinkedList<>();
    // each task: task-taskid
    private final Set<String> allTasks = Collections.synchronizedSet(new HashSet<>());

    // each worker: worker-id
    private final Set<String> idleWorkers = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> workers = Collections.synchronizedSet(new HashSet<>());


    public Manager(String zkServer, ZooKeeper zk){
        super(zkServer);
        this.zk = zk;
    }


    public void initialize(){
        try{
            System.out.println("DISTAPP: This process is the manager.");

            preCheck();
            watchTasks();
            watchWorkers();

        }catch (KeeperException | InterruptedException e){
            System.out.println(e);
        }
    }

    private void preCheck() throws KeeperException, InterruptedException {
        if(zk.exists("/dist31/tasks", false) == null){
            zk.create("/dist31/tasks", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("DISTAPP: Created /dist31/tasks node.");

        }

        if(zk.exists("/dist31/workers", false) == null){
            zk.create("/dist31/workers", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            System.out.println("DISTAPP: Created /dist31/tasks node.");

        }
    }
    /**
     * Watch all worker states and update the idleWorkers set accordingly.
     */
       private void watchWorkers(){
        zk.getChildren(
                "/dist31/workers",
                new Watcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) {
                        if (watchedEvent.getType() == Event.EventType.NodeChildrenChanged){
                            // Re-install a watcher on the /dist31/workers node
                            System.out.println("DISTAPP: Worker nodes changed. Re-watching /dist31/workers.");
                            watchWorkers();
                        }
                    }
                },
                new AsyncCallback.ChildrenCallback() {
                    @Override
                    public void processResult(int rc, String path, Object ctx, List<String> children) {
                        if (rc == KeeperException.Code.OK.intValue()) {
                            //each child: worker-<worker-id>
                            // Install a watcher for state changes on each newly joined worker
                            System.out.println("DISTAPP: Active workers: " + children);
                            for (String worker : children) {
                                if (!workers.contains(worker)) {
                                    watchWorkerState(worker);
                                    workers.add(worker);
                                    System.out.println("DISTAPP: Found new worker " + worker + ". Watching its state.");
                                }
                            }
                        } else {
                            System.err.println("Failed to fetch workers: " + rc);
                        }
                    }
                },
                null);
    }


    /**
     * add a watch on a worker to monitor its data change
     * @param worker
     */
    private void watchWorkerState(String worker) {
        String workerPath = "/dist31/workers/" + worker;
        zk.getData(
                workerPath,
                new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        if (event.getType() == Event.EventType.NodeDataChanged) {
                            // Reinstall the watch
                            System.out.println("DISTAPP: Worker " + worker + " state changed. Re-watching.");
                            watchWorkerState(worker);
                        }
                    }
                },
                new AsyncCallback.DataCallback(){
                    @Override
                    public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
                        if (rc == KeeperException.Code.OK.intValue()) {
                            String state = new String(data);

                            // we only need to re-track "idle" states because we only want to reuse idle workers.
                            // Workers (who become "busy") are already assigned a task
                            // and are already removed from the idleWorkers
                            if ("idle".equals(state)) {
                                idleWorkers.add(worker);
                                System.out.println("DISTAPP: Worker " + worker + " is idle. Updated idleWorkers set: " + idleWorkers);
                                System.out.println("DISTAPP: Preparing to assign tasks to idle workers.");
                                assignTasks();

                            }
                        } else {
                            System.out.println("DISTAPP: Failed to fetch state for worker " + worker + ": " + rc);
                        }
                    }
                },
                null
        );
    }

    private void watchTasks() {
        zk.getChildren(
                "/dist31/tasks",
                new Watcher() {
                    @Override
                    public void process(WatchedEvent watchedEvent) {
                        System.out.println("DISTAPP: Tasks changed. Re-watching /dist31/tasks.");
                        watchTasks();
                    }
                },
                new AsyncCallback.ChildrenCallback() {
                    // add only unprocessed tasks (also add to the allTasks set)
                    @Override
                    public void processResult(int rc, String path, Object ctx, List<String> children) {
                        if (rc == KeeperException.Code.OK.intValue()) {
                            for (String child : children) {
                                if (!allTasks.contains(child)) {
                                    unassignedTasks.add(child);
                                    allTasks.add(child);
                                    System.out.println("DISTAPP: Task " + child + " added to the queue.");
                                }
                            }
                            assignTasks();
                        } else {
                            System.err.println("Failed to fetch tasks: " + rc);
                        }
                    }
                }
                ,
                null
        );
    }

    /**
     * If unassigned task set is not empty and there are idle workers,
     * then we create a task node: /dist31/workers/{workerNode}/{taskNode}.
     * The purpose of the task node is to trigger a worker's callback
     * so that the worker is aware of a new task assignment.
     */
    private void assignTasks(){

        while(true){

            // worker name: worker-id
            String worker;
            // task name: task-id
            String task;

            if (unassignedTasks.isEmpty() || idleWorkers.isEmpty()) {
                System.out.println("DISTAPP: No tasks or idle workers available. Stopping assignment.");
                return;
            }

            worker = idleWorkers.iterator().next();
            task = unassignedTasks.poll();

            idleWorkers.remove(worker);
            System.out.println("DISTAPP: Assigning task " + task + " to worker " + worker);

            try{
                String workerTaskPath = "/dist31/workers/" + worker + "/" + task;

                zk.create(
                        workerTaskPath,
                        "".getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL
                );

                System.out.println("DISTAPP: Manager assigned task " + task + " to worker " + worker);
            }catch (KeeperException.NodeExistsException e) {
                System.err.println("DISTAPP: Task reference already exists for worker: " + worker);
            } catch (KeeperException | InterruptedException e) {
                System.err.println("DISTAPP: Task assignment failed: " + e.getMessage());
            }
            if (!unassignedTasks.isEmpty()) {
                System.out.println("DISTAPP: No idle workers available. Waiting for a worker to become idle.");
            }
        }
    }

}
