package interfaces;

import java.net.InetSocketAddress;

public interface ChordNode {
    /**
     * Create or join a ring
     * @param contact
     * @return true if successfully create a ring
     * or join a ring via contact
     */
    boolean join(InetSocketAddress contact);

    /**
     * Notify successor that this node should be its predecessor
     * @param successor
     * @return successor's response
     * successor
     */
    String notify(InetSocketAddress successor);

    /**
     * Being notified by another node, set it as my predecessor if it is.
     * @param newpre
     */
    void notified(InetSocketAddress newpre);

    void joinAndHint(InetSocketAddress newSucc, InetSocketAddress oldPredOfSucc);

    void hinted(InetSocketAddress successor);

    /**
     * Ask current node to find id's successor.
     * @param id
     * @return id's successor's socket address
     */
    InetSocketAddress findSuccessor(long id);

    /**
     * Return closest finger preceding node.
     * @param findid
     * @return closest finger preceding node's socket address
     */
    InetSocketAddress findClosestPrecedingFinger(long findid);

    /**
     * Update the finger table based on parameters.
     * Synchronize, all threads trying to modify
     * finger table only through this method.
     * @param i: index or command code
     * @param value
     */
    void updateFingers(int i, InetSocketAddress value);

    /**
     * Clear predecessor.
     */
    void clearPredecessor();

    /**
     * Getters
     * @return the variable caller wants
     */

    long getId();

    InetSocketAddress getAddress();

    InetSocketAddress getPredecessor();

    InetSocketAddress getSuccessor();

    /**
     * Print functions
     */

    void printNeighbors();

    void printDataStructure();

    /**
     * Stop this node's all threads.
     */
    void stopAllThreads();

    String getJoinState();

    boolean isLocked();

}
