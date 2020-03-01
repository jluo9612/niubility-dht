import com.sun.tools.corba.se.idl.toJavaPortable.Helper;
import util.HashHelper;
import util.SocketAddrHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import static java.lang.Thread.sleep;

/**
 * RequestHandler class processes requests sent by RequestListener
 * and writes responses back
 */
public class RequestHandler implements Runnable{
    private Socket clientSocket;
    private Node localNode;

    public RequestHandler(Socket clientSocket, Node localNode) {
        this.clientSocket = clientSocket;
        this.localNode = localNode;
    }

    @Override
    public void run() {
        try {
            InputStream input = clientSocket.getInputStream();
            String request = SocketAddrHelper.readInputStream(input);
            String response = process(request);
            if (request != null) {
                OutputStream output = clientSocket.getOutputStream();
                output.write(response.getBytes());
            }
            input.close();
        } catch (IOException e) {
            System.out.println("***************");
            throw new RuntimeException("Cannot get input stream T.T \n server node port: " + localNode.getAddress().getPort() + "\nclient node port: " + clientSocket.getPort(), e);

        }
    }
    //process request
    private String process(String request) {
        InetSocketAddress result = null;
        String response = "";
        if (request == null) {
            return null;
        }
        //get successor
        if (request.startsWith("YOURSUCC")) {
            result = localNode.getSuccessor();
            if (result != null) {
                response = buildResponse(result, "MYSUCC_");
            } else {
                response = "NULL";
            }
        }
        //get predecessor
        else if (request.startsWith("YOURPRE")) {
            result = localNode.getPredecessor();
            if (result != null) {
                response = buildResponse(result, "MYPRE_");
            } else {
                response = "NULL";
            }
        }
        //find successor
        else if (request.startsWith("FINDSUCC")) {
            long key = Long.parseLong(request.split("_")[1]);
            result = localNode.findSuccessor(key);
            response = buildResponse(result, "FOUNDSUCC_");
        }
        //get closest node
        else if (request.startsWith("CLOSEST")) {
            long key = Long.parseLong(request.split("_")[1]);
            result = localNode.findClosestPrecedingFinger(key);
            response = buildResponse(result, "MYCLOSEST_");
        }
        //claim as predecessor
        else if (request.startsWith("IAMPRE")) {
            InetSocketAddress newPredecessor = SocketAddrHelper.createSocketAddress(request.split("_")[1]);
            while(localNode.isLocked()) {
                try {
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Node at port:"+localNode.getAddress().getPort()+"is being locked from:" + newPredecessor.toString());
            }
            //System.out.println("unlocked at port:"+localNode.getAddress().getPort());
            localNode.notified(newPredecessor);
            response = "NOTIFIED";
        }
        else if (request.startsWith("KEEP")) {
            response = "ALIVE";
        }
        else if (request.startsWith("ISLOCKED")) {
            if (localNode.isLocked()) {
                response = "LOCKED";
            } else {
                response = "UNLOCKED";

            }
        }
        else if (request.startsWith("YOUCANJOIN")) {
            String[] IPAddress = request.split("_");
            InetSocketAddress successor = SocketAddrHelper.createSocketAddress(IPAddress[1]);
            InetSocketAddress oldPredOfSucc = null;
            if (IPAddress.length > 2) {
                oldPredOfSucc = SocketAddrHelper.createSocketAddress(IPAddress[2]);
            }
            localNode.joinAndHint(successor, oldPredOfSucc);
            response = "BEALLOWED";
        }
        else if (request.startsWith("IAMNEWSUCC")) {
            InetSocketAddress successor = SocketAddrHelper.createSocketAddress(request.split("_")[1]);
            localNode.hinted(successor);
            response = "HINTED";
        }
        return response;
    }

    /**
     * buildResponse builds responses
     * @param result node address in the chord ring
     * @param prefix response prefix
     * @return response string
     */
    private String buildResponse(InetSocketAddress result, String prefix) {
        String ipAddress = result.getAddress().toString();
        int port = result.getPort();
        return prefix + ipAddress + ":" + port;
    }
}
