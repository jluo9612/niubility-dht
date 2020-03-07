
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import interfaces.Stabilizeable;

import interfaces.ChordNode;
import util.HashHelper;
import util.SocketAddrHelper;

/**
 * Stabilize thread that periodically asks successor and check if its predecessor is the current node.
 * If there is any change, update its own finger table.
 * Also determines if current node should update or delete its successor.
 * @author Jiabei Luo
 *
 */

public class Stabilize implements Stabilizeable {

    private ChordNode local;
    private boolean alive;

    public Stabilize(ChordNode _local) {
        local = _local;
        alive = true;
    }

    /**
     *
     * @param local_id my node id
     * @param newSucc new successor addr obtained from old successor's predecessor
     * @param oldSucc old successor addr obtained from local
     * @return true if newSucc is the new successor, false if not
     */
    @Override
    public boolean isNewSuccessor(long local_id, InetSocketAddress newSucc, InetSocketAddress oldSucc) {
        long successor_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(oldSucc), local_id);
        long x_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(newSucc),local_id);
        if (x_relative_id>0 && x_relative_id < successor_relative_id) {
            return true;
        }
        return false;
    }

    /**
     * Maintain local data store
     * Move a replica of corresponding key-values to successor and delete incorrect replicas in local data store
     */
    private void manageReplica() {
        local.lock();
        // loop through data map entries of this node
        Map<String, String> map = local.getDataStore();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                String dataKey = entry.getKey();
                String dataValue = entry.getValue();
                long keyNodeId = HashHelper.hashString(dataKey);



                if (isThisMyNode(keyNodeId)) {

                    // this node's data
                    System.out.println("this node's data");
                    System.out.println("localNode is :" + local.getAddress());
                    System.out.println("key is :" + dataKey);
                    System.out.println("%%%%%%%%%%%%%%%%%%%%");
                    replicateToSuccessor(local.getSuccessor1(), dataKey, dataValue);
                } else {
                    System.out.println();
                    System.out.println("localNode is :" + local.getAddress());
                    System.out.println("key is :" + dataKey);
                    System.out.println("%%%%%%%%%%%%%%%%%%%%");
                    // if currentnode not a successor, i'm not supposed to keep it 
                    // delete dataKey 
                    if (amIASuccessor(keyNodeId)) {
                        local.lock();
                        map.remove(dataKey); // !!!
                        local.unlock();
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        local.unlock();

    }

    /**
     * Check if local is a successor of another node
     * @param keyNodeId the id of the current key in the map
     * @return canDelete - true if it isn't, false if it is
     */
    private boolean amIASuccessor(long keyNodeId) {
        boolean canDelete = false;
        String response = null;
        try {

            InetSocketAddress successor = local.getSuccessor1();
            // request format?!!!!!

            // find the correct node address (which keyNodeId belongs to)

            response = SocketAddrHelper.sendRequest(successor,"FINDNODE_" + keyNodeId);

            System.out.println(response);
            System.out.println("keyNodeId:" + keyNodeId);

            InetSocketAddress targetAddr = null;
            // default = empty or Not found?
            if (response.startsWith("NODEFOUND")) {
                // Parse for target address
                String[] ipAddrPort = response.split("_")[1].substring(1).split(":");

                targetAddr = new InetSocketAddress(ipAddrPort[0], Integer.parseInt(ipAddrPort[1]));
            }

            if (targetAddr != null) {
                String placeholder = "content"; // avoid indexOutOfRange exception
                // get the successor
                response = SocketAddrHelper.sendRequest(targetAddr, "YOURSUCC_" + placeholder);
                System.out.println("YOURSUCC response:" + response);
                // delete if local is not a successor
                if (response.startsWith("MYSUCC")) {
                    String[] ip = response.split("_")[1].substring(1).split(":");
                    InetSocketAddress successorAddr = new InetSocketAddress(ip[0], Integer.parseInt(ip[1])); // response is String from sendRequest!!
                    System.out.println("successorAddr:" + successorAddr.toString());
                    long successorId = HashHelper.hashSocketAddress(successorAddr);
                    long localId = local.getNodeId();
                    System.out.println("localId: " + localId + "successorId: " + successorId);
                    if (localId != successorId) {
                        canDelete = true;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("canDelete is" + canDelete);
        return canDelete;
    }

    /**
     * Check if a keyNodeId being queried belongs to local
     * @param queryNodeId the id of the current key in the map
     * @return true if it does, false if it doesn't
     */
    private boolean isThisMyNode(long queryNodeId) {
        boolean myNode = false;
        if (local.getPredecessor1() == null) {
            System.out.println("local.getPredecessor() is null");
        }
        long predId = HashHelper.hashSocketAddress(local.getPredecessor1());

        if (local.getNodeId() > predId) {
            // predecessor < queryNodeId <= local
            if ((queryNodeId > predId) && (queryNodeId <= local.getNodeId())) {
                myNode = true;
            }
        } else { // wrapping
            // local < predecessor && (predecessor < queryNodeId or queryNodeId <= local)
            if ((queryNodeId > predId) || (queryNodeId <= local.getNodeId())) {
                myNode = true;
            }
        }

        return myNode;
    }

    /**
     *
     * @param address
     * @param key
     * @param value
     */
    private void replicateToSuccessor(InetSocketAddress address, String key, String value) {

        try {
            SocketAddrHelper.sendRequest(address,"PUTREPLICA_" + key + ":" + value);
        } catch (Exception ex) {
            System.err.println("Error from replicateToSuccessor():" + ex.getMessage() + " when connecting to " + address);
        }

    }


    /**
     * Checks if successor is alive and update it if it has changed
     * @param successor current successor addr obtained from local
     */
    @Override
    public void checkAndUpdate(InetSocketAddress successor) {
        InetSocketAddress localAddr = local.getAddress();

        if (successor == null || successor.equals(localAddr)) { //successor exited
            local.updateFingers(-3, null);
        }

        successor = local.getSuccessor1();
        if (successor != null && !successor.equals(localAddr)) { 

            // checkNewSuccessor
            // try to get my successor's predecessor
            InetSocketAddress x = SocketAddrHelper.requestAddress(successor, "YOURPRE");

            // if bad connection with successor! delete successor
            if (x == null) {
                local.updateFingers(-1, null);
            }

            // else if successor's predecessor is not equal to local
            else if (!x.equals(successor)) {
                long local_id = HashHelper.hashSocketAddress(localAddr);
                long successor_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(successor), local_id);
                long x_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(x),local_id);
                if (x_relative_id>0 && x_relative_id < successor_relative_id) {
                    local.updateFingers(1,x);
                }

            }

            // successor's predecessor is successor itself, then notify successor
            else {
                local.setNewSucc(successor);
            }
        }
    }

    @Override
    public void run() {
        while (alive) {

            InetSocketAddress successor = local.getSuccessor1();
            if (!local.getAddress().equals(successor)) {
//                System.out.println("Stabilization is now running.");

                // successor = local.getSuccessor1();
                checkAndUpdate(successor);
                manageReplica();

            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void toDie() {
        alive = false;
    }

}