
import java.net.InetSocketAddress;
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

    @Override
    public boolean isNewSuccessor(long local_id, InetSocketAddress newSucc, InetSocketAddress oldSucc) {
        long successor_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(oldSucc), local_id);
        long x_relative_id = HashHelper.getRelativeId(HashHelper.hashSocketAddress(newSucc),local_id);
        if (x_relative_id>0 && x_relative_id < successor_relative_id) {
            local.updateFingers(1,newSucc);
            return true;
        }
        return false;
    }

    @Override
    public void checkAndUpdate(InetSocketAddress successor) {
        InetSocketAddress localAddr = local.getAddress();
        long local_id = HashHelper.hashSocketAddress(localAddr);

        if (successor == null || successor.equals(localAddr)) { //successor exited
            local.updateFingers(-3, null); //fill
            // checkForLeaderDown
        }

        // successor = local.getSuccessor();
        if (successor != null && !successor.equals(localAddr)) { // C

            // checkNewSuccessor
            // try to get my successor's predecessor
            InetSocketAddress x = SocketAddrHelper.requestAddress(successor, "YOURPRE");

            // if bad connection with successor! delete successor
            if (x == null) {
                local.updateFingers(-1, null);
            }

            // else if successor's predecessor is not equal to local
            else if (!x.equals(successor)) {
                if (isNewSuccessor(local_id, x, successor)) {
                    local.updateFingers(1,x);
                }
            }

            // successor's predecessor is successor itself, then notify successor
            else {
                local.notify(successor);
            }
        }
    }

    @Override
    public void run() {
        while (alive) {

            InetSocketAddress successor = local.getSuccessor();
            checkAndUpdate(successor);

            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void toDie() {
        alive = false;
    }

}