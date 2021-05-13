import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private ZooKeeper zooKeeper;
    private static final String ELECTION_NAMESPACE = "/election";
    private String currentZnodeName;

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        LeaderElection leaderElection = new LeaderElection();
        leaderElection.connectToZookeeper();
        leaderElection.volunteerForLeadership();
        leaderElection.electLeader();
        leaderElection.run();
        leaderElection.close();
        System.out.println("Disconnected from Zookeeper, exiting application");
    }

    public void volunteerForLeadership() throws InterruptedException, KeeperException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";
        // disconnect from zookeeper, the node will be deleted
        String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

        System.out.println("znode name " + znodeFullPath);
        this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
    }

    public void electLeader() throws InterruptedException, KeeperException {
        List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);

        // find which is smallest(earliest)
        Collections.sort(children);
        String smallestChild = children.get(0);
        if (smallestChild.equals(currentZnodeName)) {
            System.out.println("I am the leader");
            return;
        }
        System.out.println("I am not the leader, " + smallestChild + " is the leader");
    }

    public void run() throws InterruptedException {
        // put main thread into a wait state, because connection is on another thread
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    public void connectToZookeeper() throws IOException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {
//        will be called by zookeeper on a separate thread whenever there is a new event coming from zookeeper server
        switch (event.getType()) {
            case None:
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to Zookeeper");
                } else {
                    //lose connection to zookeeper, we want to wake up main thread and allow application to close resources and exit
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from Zookeeper event");
                        zooKeeper.notifyAll();
                    }
                }
        }
    }
}
