//import com.sun.tools.corba.se.idl.toJavaPortable.Helper;
import util.HashHelper;
import util.SocketAddrHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;

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
//                try {
//                    sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
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
        else if (request.startsWith("YOUAREMYSUCC")) {
            InetSocketAddress successor = SocketAddrHelper.createSocketAddress(request.split("_")[1]);
            localNode.updateNewPre(successor);
            response = "UPDATED";
        }
        else if (request.startsWith("FINDNODE")) {
            //based on query format, might need to be changed
            //supposed to be query key
            response = findNode(SocketAddrHelper.createSocketAddress(request.split("_")[1]));
        }
        else if (request.startsWith("REQUESTKEYVALUES")) {
            response = requestKeyValues(SocketAddrHelper.createSocketAddress(request.split("_")[1]));
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

    private String findNode(InetSocketAddress query) {
        long queryId = HashHelper.hashSocketAddress(query);
        //wrap id if it's bigger than chord size
        queryId = queryId % HashHelper.getPowerOfTwo(32);

        String response = "NOT FOUND T.T";

        //if queryId is in localNode
        if (isThisMyNode(queryId)) {
            response = buildResponse(localNode.getAddress(), "NODEFOUND_");
        }
        //else if queryId is in localNode's successor
        else if (isThisNextNode(queryId)) {
            response = buildResponse(localNode.getSuccessor(), "NODEFOUND_");
        }
        //else recursive call findNode to find queryId
        else {
            InetSocketAddress closestNodeToQueryId = localNode.findClosestPrecedingFinger(queryId);
            System.out.println("Query ID: " + queryId + " on " + closestNodeToQueryId.getAddress() + " : "
                                + closestNodeToQueryId.getPort());
            String nextRequest = buildResponse(query, "FINDNODE_");
            response = SocketAddrHelper.sendRequest(closestNodeToQueryId, nextRequest);
            System.out.println("Response from node " + closestNodeToQueryId.getAddress() + ", port "
                            + closestNodeToQueryId.getPort());

        }
        return response;
    }

    private boolean isThisMyNode(long queryId) {
        boolean result = false;
        long localNodeId = HashHelper.hashSocketAddress(localNode.getAddress());
        long predecessorNodeId = HashHelper.hashSocketAddress(localNode.getPredecessor());

        //predecessor and localNode is on the same side of 0
        if (localNodeId > predecessorNodeId) {
            if ((queryId > predecessorNodeId) && (queryId <= localNodeId)) {
                result = true;
            }
        }
        //predecessor is on the left side of 0, and localNode is on the right side of 0
        else {
            if ((queryId > predecessorNodeId) || queryId <= localNodeId) {
                result = true;
            }
        }
        return result;
    }

    private boolean isThisNextNode(long queryId) {
        boolean result = false;
        long localNodeId = HashHelper.hashSocketAddress(localNode.getAddress());
        long successorNodeId = HashHelper.hashSocketAddress(localNode.getSuccessor());
        //localNode and successor is on the same side of )
        if (localNodeId < successorNodeId) {
            if ((queryId > localNodeId) && (queryId <= successorNodeId)) {
                result = true;
            }
        }
        //localNode is on the left side of 0, and successor is on the right side of 0
        else {
            if ((queryId > localNodeId) || (queryId <= successorNodeId)) {
                result = true;
            }
        }
        return result;
    }

    private String requestKeyValues(InetSocketAddress nodeSocketAdd) {
        long newNodeId = HashHelper.hashSocketAddress(nodeSocketAdd);
        StringBuffer sbResponse = new StringBuffer();
        for (Iterator<Map.Entry<String, String>> it = localNode.getDataStore().entrySet().iterator(); it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
             String strKey = entry.getKey();
             long hashedKeyEntry = HashHelper.hashString(strKey);
            long localNodeId = HashHelper.hashSocketAddress(localNode.getAddress());
             if (newNodeId < localNodeId
                && ((hashedKeyEntry > localNodeId)) || (hashedKeyEntry < newNodeId)) {
                sbResponse.append(strKey + ":" + entry.getValue());
                sbResponse.append("::");

                //remove key-value pair from the current node
                 it.remove();
            }
             else if ((newNodeId > localNodeId)
                && (hashedKeyEntry > localNodeId && hashedKeyEntry < newNodeId)) {
                 sbResponse.append(strKey + ":" + entry.getValue());
                 sbResponse.append("::");

                 //remove the key value pair from the current node
                 it.remove();
             }
        }
        String response = sbResponse.toString();
        if (response != null && !response.isEmpty() && response != "") {
            response = response.substring(0, response.length() - 2);
        }
        return response;
    }


}
