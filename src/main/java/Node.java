

import util.HashHelper;
import util.SocketAddrHelper;
import interfaces.ChordNode;
import interfaces.Stabilizeable;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.sleep;

public class Node implements ChordNode {

    private long localId;
    private InetSocketAddress localAddress;
    private InetSocketAddress predecessor1;
    private InetSocketAddress predecessor2;
    private InetSocketAddress successor1;
    private InetSocketAddress successor2;
    private HashMap<Integer, InetSocketAddress> finger;
    private Map<String, String> dataStore = new HashMap<>();


    private RequestListener listener;
    private Stabilizeable stabilize;
    private UpdateFingers updateFingers;
    private PingPredecessor pingPredecessor;
    private String joinState;
    private boolean isLocked;
    private Semaphore mutex = new Semaphore(1);

    /**
     * Constructor
     * @param address: this node's local address
     */
    public Node (InetSocketAddress address) {

        localAddress = address;
        localId = HashHelper.hashSocketAddress(localAddress);

        // initialize an empty finge table
        finger = new HashMap<Integer, InetSocketAddress>();
        for (int i = 1; i <= 32; i++) {
            updateIthFinger (i, null);
        }

        // initialize predecessor
        predecessor1 = localAddress;

        // initialize threads
        listener = new RequestListener(this);
        stabilize = new Stabilize(this);
        updateFingers = new UpdateFingers(this);
        pingPredecessor = new PingPredecessor(this);
        listener.start();
    }

    /**
     * Create or join a ring
     * @param contact
     * @return true if successfully create a ring
     * or join a ring via contact
     */
    @Override
    public boolean join(InetSocketAddress contact) {

        // if contact is other node (join ring), try to contact that node
        // (contact will never be null)

        if (contact != null && !contact.equals(localAddress)) {
            lock();
            InetSocketAddress successor = SocketAddrHelper.requestAddress(contact, "FINDSUCC_" + localId);
            System.out.println("My successor is : " + successor.toString());
            if (successor == null)  {
                System.out.println("\nCannot find node you are trying to contact. Please exit.\n");
                return false;
            }
            notify(successor);
            distributeKeyValues();
            unlock();
        }

        // start all threads

        //You may want to comment following lines to test mutext fucntionality.
        Thread t = new Thread(stabilize);
        t.start();
        updateFingers.start();
        pingPredecessor.start();

        return true;
    }

    /**
     * Notify successor that this node should be its predecessor
     * @param successor
     * @return successor's response
     * successor
     */
    @Override
    public String notify(InetSocketAddress successor) {
        if (successor!=null && !successor.equals(localAddress))
            return SocketAddrHelper.sendRequest(successor, "IAMPRE_"+localAddress.getAddress().toString()+":"+localAddress.getPort());
        else
            return null;
    }

    /**
     * Being notified by another node, set it as my predecessor if it is.
     * @param newpre
     */
    @Override
    public void notified(InetSocketAddress newpre) {
        lock();
        InetSocketAddress oldPredecessor = predecessor1;
        if (getSuccessor1() == null || getSuccessor1().equals(localAddress)) {
            finger.put(1, newpre);
        }
        if (predecessor1 == null || predecessor1.equals(localAddress)) {
            this.setPredecessor1(newpre);
        }
        else {
            long oldpre_id = HashHelper.hashSocketAddress(predecessor1);
            long local_relative_id = HashHelper.getRelativeId(localId, oldpre_id);
            long newpre_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(newpre), oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id)
                this.setPredecessor1(newpre);
        }

        String req = "YOUCANJOIN_"+localAddress.getAddress().toString()+":"+localAddress.getPort();
        if (oldPredecessor != null && !oldPredecessor.equals(localAddress)) {
            req += "_"+oldPredecessor.getAddress().toString()+":"+oldPredecessor.getPort();
        }
        SocketAddrHelper.sendRequest(newpre, req);
//        try {
//            sleep(20000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        unlock();
    }

    @Override
    public void joinAndHint(InetSocketAddress newSucc, InetSocketAddress oldPredOfSucc) {
        finger.put(1, newSucc);
        if (oldPredOfSucc != null) {
            setPredecessor1(oldPredOfSucc);
            SocketAddrHelper.sendRequest(oldPredOfSucc, "IAMNEWSUCC_"+localAddress.getAddress().toString()+":"+localAddress.getPort());
        } else {
            setPredecessor1(newSucc);
        }
    }

    @Override
    public void hinted(InetSocketAddress successor) {

        finger.put(1, successor);
    }

    public String setNewSucc(InetSocketAddress successor) {
        if (successor!=null && !successor.equals(localAddress))
            return SocketAddrHelper.sendRequest(successor, "YOUAREMYSUCC_"+localAddress.getAddress().toString()+":"+localAddress.getPort());
        else
            return null;
    }

    public void updateNewPre(InetSocketAddress newPred) {
        if (predecessor1 == null || predecessor1.equals(localAddress)) {
            this.setPredecessor1(newPred);
        }
        else {
            long oldpre_id = HashHelper.hashSocketAddress(predecessor1);
            long local_relative_id = HashHelper.getRelativeId(localId, oldpre_id);
            long newpre_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(newPred), oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id)
                this.setPredecessor1(newPred);
        }
    }

    private void distributeKeyValues() {
        System.out.println("distributing key values");
        String serverResponse = SocketAddrHelper.sendRequest(getSuccessor1(), "REQUESTKEYVALUES_" + localAddress.getAddress().toString()+":"+localAddress.getPort());
        if (serverResponse != null && !serverResponse.isEmpty() && serverResponse != "") {
            String[] keyValuePairs = serverResponse.split("::");
            lock();

            for (int i = 0; i < keyValuePairs.length; i++) {
                String[] keyValue = keyValuePairs[i].split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];

                    this.getDataStore().put(key, value);

                }
            }
            unlock();
        }
    }


    /**
     * Ask current node to find id's successor.
     * @param id
     * @return id's successor's socket address
     */
    @Override
    public InetSocketAddress findSuccessor(long id) {

        // initialize return value as this node's successor (might be null)
        InetSocketAddress ret = this.getSuccessor1();

        // find predecessor
        InetSocketAddress pre = findPredecessor(id);

        // if other node found, ask it for its successor
        if (!pre.equals(localAddress)) {
            ret = SocketAddrHelper.requestAddress(pre, "YOURSUCC");
        }

        // if ret is still null, set it as local node, return
        if (ret == null)
            ret = localAddress;

        return ret;
    }

    /**
     * Ask current node to find id's predecessor
     * @param findid
     * @return id's successor's socket address
     */
    private InetSocketAddress findPredecessor(long findid) {
        InetSocketAddress n = this.localAddress;
        InetSocketAddress n_successor = this.getSuccessor1();
        InetSocketAddress most_recently_alive = this.localAddress;
        long n_successor_relative_id = 0;
        if (n_successor != null)
            n_successor_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(n_successor), HashHelper.hashSocketAddress(n));
        long findid_relative_id = HashHelper.getRelativeId(findid, HashHelper.hashSocketAddress(n));

        while (!(findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id)) {

            // temporarily save current node
            InetSocketAddress pre_n = n;

            // if current node is local node, find my closest
            if (n.equals(this.localAddress)) {
                n = this.findClosestPrecedingFinger(findid);
            }

            // else current node is remote node, sent request to it for its closest
            else {
                InetSocketAddress result = SocketAddrHelper.requestAddress(n, "CLOSEST_" + findid);

                // if fail to get response, set n to most recently
                if (result == null) {
                    n = most_recently_alive;
                    n_successor = SocketAddrHelper.requestAddress(n, "YOURSUCC");
                    if (n_successor==null) {
                        System.out.println("It's not possible.");
                        return localAddress;
                    }
                    continue;
                }

                // if n's closest is itself, return n
                else if (result.equals(n))
                    return result;

                    // else n's closest is other node "result"
                else {
                    // set n as most recently alive
                    most_recently_alive = n;
                    // ask "result" for its successor
                    n_successor = SocketAddrHelper.requestAddress(result, "YOURSUCC");
                    // if we can get its response, then "result" must be our next n
                    if (n_successor!=null) {
                        n = result;
                    }
                    // else n sticks, ask n's successor
                    else {
                        n_successor = SocketAddrHelper.requestAddress(n, "YOURSUCC");
                    }
                }

                // compute relative ids for while loop judgement
                n_successor_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(n_successor), HashHelper.hashSocketAddress(n));
                findid_relative_id = HashHelper.getRelativeId(findid, HashHelper.hashSocketAddress(n));
            }
            if (pre_n.equals(n))
                break;
        }
        return n;
    }

    /**
     * Return closest finger preceding node.
     * @param findid
     * @return closest finger preceding node's socket address
     */
    @Override
    public InetSocketAddress findClosestPrecedingFinger(long findid) {
        long findid_relative = HashHelper.getRelativeId(findid, localId);

        // check from last item in finger table
        for (int i = 32; i > 0; i--) {
            InetSocketAddress ith_finger = finger.get(i);
            if (ith_finger == null) {
                continue;
            }
            long ith_finger_id = HashHelper.hashSocketAddress(ith_finger);
            long ith_finger_relative_id = HashHelper.getRelativeId(ith_finger_id, localId);

            // if its relative id is the closest, check if its alive
            if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative)  {
                String response  = SocketAddrHelper.sendRequest(ith_finger, "KEEP");

                //it is alive, return it
                if (response!=null &&  response.equals("ALIVE")) {
                    return ith_finger;
                }

                // else, remove its existence from finger table
                else {
                    updateFingers(-2, ith_finger);
                }
            }
        }
        return localAddress;
    }

    /**
     * Update the finger table based on parameters.
     * Synchronize, all threads trying to modify
     * finger table only through this method.
     * @param i: index or command code
     * @param value
     */
    @Override
    public synchronized void updateFingers(int i, InetSocketAddress value) {

        // valid index in [1, 32], just update the ith finger
        if (i > 0 && i <= 32) {
            updateIthFinger(i, value);
        }

        // caller wants to delete
        else if (i == -1) {
            deleteSuccessor();
        }

        // caller wants to delete a finger in table
        else if (i == -2) {
            deleteCertainFinger(value);

        }

        // caller wants to fill successor
        else if (i == -3) {
            fillSuccessor();
        }

    }

    /**
     * Update ith finger in finger table using new value
     * @param i: index
     * @param value
     */
    private void updateIthFinger(int i, InetSocketAddress value) {
        finger.put(i, value);

        if (i == 1 && value != null && !value.equals(localAddress)) {
            setNewSucc(value);
        }

    }

    /**
     * Delete successor and all following fingers equal to successor
     */
    private void deleteSuccessor() {
        InetSocketAddress successor = getSuccessor1();

        //nothing to delete, just return
        if (successor == null)
            return;

        // find the last existence of successor in the finger table
        int i = 32;
        for (i = 32; i > 0; i--) {
            InetSocketAddress ithfinger = finger.get(i);
            if (ithfinger != null && ithfinger.equals(successor))
                break;
        }

        // delete it, from the last existence to the first one
        for (int j = i; j >= 1 ; j--) {
            updateIthFinger(j, null);
        }

        // if predecessor is successor, delete it
        if (predecessor1 != null && predecessor1.equals(successor))
            setPredecessor1(null);

        // try to fill successor
        fillSuccessor();
        successor = getSuccessor1();
        // if successor is still null or local node,
        // and the predecessor is another node, keep asking
        // it's predecessor until find local node's new successor
        if ((successor == null || successor.equals(successor)) && predecessor1 !=null && !predecessor1.equals(localAddress)) {
            InetSocketAddress p = predecessor1;
            InetSocketAddress p_pre = null;
            while (true) {
                p_pre = SocketAddrHelper.requestAddress(p, "YOURPRE");
                if (p_pre == null)
                    break;

                // if p's predecessor is node is just deleted,
                // or itself (nothing found in p), or local address,
                // p is current node's new successor, break
                if (p_pre.equals(p) || p_pre.equals(localAddress)|| p_pre.equals(successor)) {
                    break;
                }

                // else, keep asking
                else {
                    p = p_pre;
                }
            }

            // update successor
            updateIthFinger(1, p);
        }
    }

    /**
     * Delete a node from the finger table, here "delete" means deleting all existence of this node
     * @param f
     */
    private void deleteCertainFinger(InetSocketAddress f) {
        for (int i = 32; i > 0; i--) {
            InetSocketAddress ithfinger = finger.get(i);
            if (ithfinger != null && ithfinger.equals(f))
                finger.put(i, null);
        }
    }

    /**
     * Try to fill successor with candidates in finger table or even predecessor
     */
    private void fillSuccessor() {
        //System.out.println("fillSuccessor");
        InetSocketAddress successor = this.getSuccessor1();
        if (successor == null || successor.equals(localAddress)) {
            for (int i = 2; i <= 32; i++) {
                InetSocketAddress ithfinger = finger.get(i);
                if (ithfinger!=null && !ithfinger.equals(localAddress)) {
                    for (int j = i-1; j >=1; j--) {
                        updateIthFinger(j, ithfinger);
                    }
                    break;
                }
            }
        }
        successor = getSuccessor1();
        if ((successor == null || successor.equals(localAddress)) && predecessor1 !=null && !predecessor1.equals(localAddress)) {
            updateIthFinger(1, predecessor1);
        }

    }


    /**
     * Clear predecessor.
     */
    @Override
    public void clearPredecessor() {
        setPredecessor1(null);
    }

    /**
     * Set predecessor using a new value.
     * @param pre
     */
    private synchronized void setPredecessor1(InetSocketAddress pre) {
        predecessor1 = pre;
    }


    /**
     * Getters
     * @return the variable caller wants
     */

    @Override
    public long getNodeId() {
        return localId;
    }

    @Override
    public InetSocketAddress getAddress() {
        return localAddress;
    }

    public String getNodeIpAddress() {
        return localAddress.getAddress().toString().substring(1);
    }

    public int getPort() {
        return localAddress.getPort();
    }


    @Override
    public InetSocketAddress getPredecessor1() {
        return predecessor1;
    }

    @Override
    public InetSocketAddress
    getSuccessor1() {
        if (finger != null && finger.size() > 0) {
            return finger.get(1);
        }
        return null;
    }

    /**
     * Print functions
     */

    @Override
    public void printNeighbors() {
        System.out.println("\nYou are listening on port "+localAddress.getPort()+"."
                + "\nYour position is "+HashHelper.hexIdAndLocation(localAddress)+".");
        InetSocketAddress successor = finger.get(1);

        // if it cannot find both predecessor and successor
        if ((predecessor1 == null || predecessor1.equals(localAddress)) && (successor == null || successor.equals(localAddress))) {
            System.out.println("Your predecessor is yourself.");
            System.out.println("Your successor is yourself.");

        }

        // else, it can find either predecessor or successor
        else {
            if (predecessor1 != null) {
                System.out.println("Your predecessor is node "+ predecessor1.getAddress().toString()+", "
                        + "port "+ predecessor1.getPort()+ ", position "+HashHelper.hexIdAndLocation(predecessor1)+".");
            }
            else {
                System.out.println("Your predecessor is updating.");
            }

            if (successor != null) {
                System.out.println("Your successor is node "+successor.getAddress().toString()+", "
                        + "port "+successor.getPort()+ ", position "+HashHelper.hexIdAndLocation(successor)+".");
            }
            else {
                System.out.println("Your successor is updating.");
            }
        }
    }

    @Override
    public void printDataStructure() {
        System.out.println("\n==============================================================");
        System.out.println("\nLOCAL:\t\t\t\t"+localAddress.toString()+"\t"+HashHelper.hexIdAndLocation(localAddress));
        System.out.println("Is Locked?");
        if(isLocked()) {
            System.out.println("YES");
        }else {
            System.out.println("NO");
        }
        if (predecessor1 != null)
            System.out.println("\nPREDECESSOR:\t\t\t"+ predecessor1.toString()+"\t"+HashHelper.hexIdAndLocation(predecessor1));
        else
            System.out.println("\nPREDECESSOR:\t\t\tNULL");
        System.out.println("\nFINGER TABLE:\n");
        for (int i = 1; i <= 32; i++) {
            long ithstart = HashHelper.ithInFingerTable(HashHelper.hashSocketAddress(localAddress),i);
            InetSocketAddress f = finger.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append(i+"\t"+ HashHelper.hexLongDigit(ithstart)+"\t\t");
            if (f!= null)
                sb.append(f.toString()+"\t"+HashHelper.hexIdAndLocation(f));

            else
                sb.append("NULL");
            System.out.println(sb.toString());
        }
        System.out.println("Datastore**************");
        lock();
        for (String key: getDataStore().keySet()) {
            System.out.println("key:" + key);
        }
        unlock();
        System.out.println("\n==============================================================\n");
    }

    /**
     * Stop this node's all threads.
     */
    @Override
    public void stopAllThreads() {
        if (listener != null)
            listener.closeListener();
        if (updateFingers != null)
            updateFingers.toDie();
        if (stabilize != null)
            stabilize.toDie();
        if (pingPredecessor != null)
            pingPredecessor.toDie();
    }

    @Override
    public String getJoinState() {
        return joinState;
    }

    @Override
    public boolean isLocked() {
        return mutex.availablePermits() != 1;
    }

    public void lock() {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void unlock() {
        mutex.release();
    }

    @Override
    public Map<String, String> getDataStore() {
        return dataStore;
    }

    @Override
    public void setDataStore(Map<String, String> dataStore) {
        this.dataStore = dataStore;
    }
}
