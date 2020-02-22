package interfaces;

import java.net.InetSocketAddress;

/**
 * Stabilizeable interface defines actions during stabilization.
 * Used for different stabilization strategies for leader nodes and common nodes.
 * @author Jiabei Luo
 */

public interface Stabilizeable {

    /**
     * Check if the current successor of the local node has been deleted or changed. If so, update the local node's finger table.
     * @param currentSuccessor
     */
    void checkAndUpdate(InetSocketAddress currentSuccessor); // calls isNewSuccessor

    /**
     * If the current successor(oldSucc)'s predecessor is a different node (newSucc) than local, compare the relative id of newSucc and oldSucc
     * @param local_id
     * @param newSucc
     * @param oldSucc
     * @return true if newSucc's relative id is greater than 0 and less than oldSucc
     */
    boolean isNewSuccessor(long local_id, InetSocketAddress newSucc, InetSocketAddress oldSucc);
}