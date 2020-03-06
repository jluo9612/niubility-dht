import interfaces.ChordNode;
import util.SocketAddrHelper;
import java.net.InetSocketAddress;
/**
 * Thread that periodically asks for predecessor's keep-alive,
 * and delete predecessor if it's dead.
 * @author Jiabei Luo
 *
 */
public class PingPredecessor extends Thread {
	
	private ChordNode local;
	private boolean alive;
	
	public PingPredecessor(ChordNode _local) {
		local = _local;
		alive = true;
	}
	
	@Override
	public void run() {
		while (alive) {
			InetSocketAddress predecessor = local.getPredecessor1();
			if (predecessor != null) {
				String response = SocketAddrHelper.sendRequest(predecessor, "KEEP");
				if (response == null || !response.equals("ALIVE")) {
					local.clearPredecessor();	
				}

			}
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


