import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Worker extends DistProcess{

    // /dist31/workers/worker-
    private String workerNode;
    private final Set<String> processedTasks = Collections.synchronizedSet(new HashSet<>());

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public Worker(String zkServer, ZooKeeper zk){
        super(zkServer);
        this.zk = zk;
    }

    /**
     * Upon creation of a worker:
     * - Creates a persistent sequential node under /dist31/workers.
     * - Sets data for the worker node to "idle".
     * - Calls `watchTasks` to monitor incoming tasks.
     */
    @Override
    protected void initialize(){
        try{
            workerNode =  zk.create("/dist31/workers/worker-", "idle".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);

            System.out.println("DISTAPP: Worker node created: " + workerNode);
            System.out.println("DISTAPP: Worker set to idle and ready to fetch tasks.");

            watchTasks();

        }catch (KeeperException | InterruptedException e){
            System.err.println("DISTAPP: Worker initialization failed: " + e.getMessage());
        }
    }

    /**
     * Watches for new tasks to be assigned to this worker's node.
     * Re-installs the watcher after changes are detected.
     */
    private void watchTasks(){
        System.out.println("DISTAPP: Worker watching for tasks on: " + workerNode);
        zk.getChildren(
                workerNode,
                new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        if (event.getType() == Event.EventType.NodeChildrenChanged) {
                            // Reinstall the watch for future changes
                            System.out.println("DISTAPP: Tasks changed for worker " + workerNode + ". Re-watching.");
                            watchTasks();
                        }
                    }
                },
                new AsyncCallback.ChildrenCallback(){
                    @Override
                    public void processResult(int rc, String path, Object ctx, List<String> children) {
                        if (rc == KeeperException.Code.OK.intValue() && !children.isEmpty()){
                            for (String taskNode : children) {
                                // Skip already-processed tasks
                                // the string of the task : {taskNode}
                                if (!processedTasks.contains(taskNode)) {
                                    processedTasks.add(taskNode);
                                    System.out.println("DISTAPP: New task detected: " + taskNode + " for worker: " + workerNode);
                                    processTask(taskNode);
                                }
                            }
                        }else if (rc == KeeperException.Code.OK.intValue()) {
                            System.out.println("DISTAPP: No tasks available for worker: " + workerNode);
                        } else {
                            System.err.println("DISTAPP: Failed to fetch tasks for worker: " + workerNode + ". RC: " + rc);
                        }
                    }
                },
                null
        );
    }

    /**
     * Fetches data for the assigned task and processes it in another thread.
     * We fetch the actual task data from /dist31/tasks/{taskNode}
     * @param taskNode The name of the task node.
     */
    private void processTask(String taskNode){

        // we want to fetch actual data from /dist31/tasks/{taskNode}
        String originalTaskPath = "/dist31/tasks/" + taskNode;
        System.out.println("DISTAPP: Fetching task data for task: " + taskNode);

        zk.setData(workerNode, "busy".getBytes(), -1, new AsyncCallback.StatCallback(){

            @Override
            public void processResult(int rc, String path, Object ctx, Stat stat) {
                if (rc == KeeperException.Code.OK.intValue()) {
                    System.out.println("DISTAPP: Worker " + workerNode + " set to busy for task: " + taskNode);
                    zk.getData(
                            originalTaskPath,
                            false,
                            new AsyncCallback.DataCallback() {
                                @Override
                                public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
                                    if (rc == KeeperException.Code.OK.intValue()) {
                                        System.out.println("DISTAPP: Task data fetched for task: " + taskNode);
                                        handleTaskData(taskNode, data);

                                    } else {
                                        System.out.println("DISTAPP: Failed to fetch task data for " + taskNode + ": " + rc);
                                    }
                                }
                            },
                            null
                    );
                } else {
                    System.err.println("DISTAPP: Failed to set worker to busy: " + rc);
                }
            }
        }, null);
    }

    /**
     * Processes the task data in a separate thread and marks the worker as idle upon completion.
     * This avoids blocking the zookeeper's single thread
     * @param taskNode The name of the task node.
     * @param data The serialized task data.
     */
    private void handleTaskData(String taskNode, byte[] data){
        executorService.submit(() -> {
            try {
                // Deserialize the task
                System.out.println("DISTAPP: Start processing task: " + taskNode);
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bis);
                DistTask task = (DistTask) ois.readObject();

                // Perform the computation
                System.out.println("DISTAPP: Worker executing task: " + taskNode);
                task.compute();

                // Serialize the updated task (including the computed result)
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(task);
                oos.flush();
                byte[] resultData = bos.toByteArray();

                // Create the result node asynchronously
                // Store the result under /dist31/tasks/{taskNode}/result
                zk.create(
                        "/dist31/tasks/" + taskNode + "/result",
                        resultData,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT
                );

                // Mark the worker state as idle
                zk.setData(workerNode, "idle".getBytes(), -1);

                System.out.println("DISTAPP: Task " + taskNode + " completed and result saved.");
                System.out.println("DISTAPP: Worker completed task: " + taskNode);

            } catch (Exception e) {
                System.out.println("DISTAPP: Task handling failed: " + e.getMessage());
            }
        } );
    }

    /**
     * cleaning up the worker node recursively.
     * used for testing consecutively.
     */
    void cleanup() {
        try {
            if (workerNode != null) {
                // Recursively delete all task nodes under this worker
                List<String> taskNodes = zk.getChildren(workerNode, false);
                for (String taskNode : taskNodes) {
                    String taskPath = workerNode + "/" + taskNode;
                    zk.delete(taskPath, -1);
                    System.out.println("DISTAPP: Deleted task node: " + taskPath);
                }

                zk.delete(workerNode, -1);
                System.out.println("DISTAPP: Worker node deleted: " + workerNode);
            }
        } catch (KeeperException | InterruptedException e) {
            System.err.println("DISTAPP: Failed to delete worker node or its children: " + e.getMessage());
        }
    }
}
