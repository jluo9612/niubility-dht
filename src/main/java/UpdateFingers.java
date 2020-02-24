import java.net.InetSocketAddress;
import interfaces.ChordNode;
import util.HashHelper;

/**
 * Thread that periodically access successive entries in finger table and fix them.
 * @author Jiabei Luo
 *
 */

public class UpdateFingers extends Thread{

	private ChordNode local;
	int next;
	boolean alive;

	public UpdateFingers(ChordNode node) {
		local = node;
		alive = true;
		next = 1;
	}

	@Override
	public void run() {
		while (alive) {
			next += 1;
			if (next > 32) {
				next = 1;
			} // next's value will be [1-32]; if next > 32, we start over
			InetSocketAddress ithfinger = local.findSuccessor(HashHelper.ithInFingerTable(local.getId(), next));
			local.updateFingers(next, ithfinger);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void toDie() {
		alive = false;
	}

}
