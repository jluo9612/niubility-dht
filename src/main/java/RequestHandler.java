
//import com.sun.tools.corba.se.idl.toJavaPortable.Helper;
import util.HashHelper;

import util.SocketAddrHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
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
            result = localNode.getSuccessor1();
            if (result != null) {
                response = buildResponse(result, "MYSUCC_");
            } else {
                response = "NULL";
            }
        }
        //get predecessor
        else if (request.startsWith("YOURPRE")) {
            result = localNode.getPredecessor1();
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
        else if (request.startsWith("YOUAREMYSUCC")) {
            InetSocketAddress predecessor = SocketAddrHelper.createSocketAddress(request.split("_")[1]);
            localNode.updateNewPre(predecessor);
            response = "UPDATED";
        }

        // load data
        else if (request.startsWith("PUTVALUE")) {
            String data = request.split("_")[1];

            response = putValue(data);
        }
        // search value
        else if (request.startsWith("GETVALUE")) {
            String key = request.split("_")[1];
            response = getValue(key);
        }


        else if (request.startsWith("FINDNODE")) {
            //based on query format, might need to be changed
            //supposed to be query key

            response = findNode(Long.valueOf(request.split("_")[1]));
        }
        else if (request.startsWith("REQUESTKEYVALUES")) {
            response = requestKeyValues(SocketAddrHelper.createSocketAddress(request.split("_")[1]));
        }
        else if (request.startsWith("PUTREPLICA")) {
            String data = request.split("_")[1];
            String key = data.split(":")[0];
            String value = data.split(":")[1];

            localNode.lock();

            // put key,value to dataStore
            localNode.getDataStore().put(key, value);
            System.out.println("Replicated " + key + "-" + value + " to "
                    + localNode.getNodeIpAddress() + ":" + localNode.getPort());

            localNode.unlock();
            response = "REPLICATED";
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


    /**
     * get value from node's datastore
     * @param key search key
     * @return search result, and position of node
     */
    private String getValue(String key) {
        String response = "Data Not Found, please try again...";
        long keyHash = HashHelper.hashString(key);
        String value = null;
        
        // if the query is between predecessor and local node
        // try to get value in local node
        if (isThisMyNode(keyHash)) {
            System.out.println("Located the node: " + localNode.getNodeId());
            value = localNode.getDataStore().get(key);
            if (value != null) {
                response = "Found the value: " + value + ", on the node: " + localNode.getNodeId() + ", IP: " +
                    localNode.getAddress().getAddress() + ":" + localNode.getAddress().getPort();
            }
        }
        // if the query is between local node and successor, 
        // try to get value in successor
        else if (isThisNextNode(keyHash)) {
            response = SocketAddrHelper.sendRequest(localNode.getSuccessor1(), "GETVALUE_" + key);
        }
        // search the correct node by finger table
        else {
            localNode.lock();
            InetSocketAddress chordNode = localNode.findClosestPrecedingFinger(keyHash);
            System.out.println("Searching node on chord to get value");
            response = SocketAddrHelper.sendRequest(chordNode, "GETVALUE_" + key);
            localNode.unlock();
        }

        return response;
    }

    /**
     * load data in node's datastore
     * @param data combined key and value
     * @return position of node
     */
    private String putValue(String data) {
        // split data to key and value
        String[] keyValue = data.split(":");
        long keyHash = HashHelper.hashString(keyValue[0]);
        String response = "Error: Not Found";

        // if the query is between predecessor and local node, 
        // put value in local node
        if (isThisMyNode(keyHash)) {
            localNode.lock();
            localNode.getDataStore().put(keyValue[0], keyValue[1]);
            localNode.unlock();
            response = "{" + keyValue[0] + "," + keyValue[1] + "} stored in " + keyHash + 
                ", which on the node" + localNode.getNodeId();
        }
        // if the query is between local node and successor, 
        // put value in successor
        else if (isThisNextNode(keyHash)) {
            response = SocketAddrHelper.sendRequest(localNode.getSuccessor1(), "PUTVALUE_" + data);
        }
        // search the correct node by using finger table
        else {
            localNode.lock();
            InetSocketAddress chordNode = localNode.findClosestPrecedingFinger(keyHash);
            System.out.println("Searching node on chord to put value");
            response = SocketAddrHelper.sendRequest(chordNode, "PUTVALUE_" + data);
            localNode.unlock();
        }
        
        return response;
    }

//    /**
//     * check whether the key's position is between predecessor and local node
//     * @param keyHash
//     * @return boolean
//     */
//    private boolean isThisMyNode(long keyHash) {
//        boolean res = false;
//        long preId = HashHelper.hashSocketAddress(localNode.getPredecessor1());
//        // check the position of keyHash
//        if ((localNode.getNodeId() > preId && keyHash > preId && keyHash <= localNode.getNodeId()) ||
//            (localNode.getNodeId() < preId && (keyHash > preId || keyHash <= localNode.getNodeId()))) {
//            res = true;
//        }
//
//        return res;
//    }
//
//    /**
//     * check whether the key's position is between local node and successor
//     * @param keyHash
//     * @return boolean
//     */
//    private boolean isThisNextNode(long keyHash) {
//        boolean res = false;
//        long sucId = HashHelper.hashSocketAddress(localNode.getSuccessor1());
//        // check the position of keyHash
//        if ((localNode.getNodeId() < sucId && keyHash > localNode.getNodeId() && keyHash <= sucId) ||
//            (localNode.getNodeId() > sucId && (keyHash > localNode.getNodeId() || keyHash <= sucId))) {
//            res = true;
//        }
//
//        return res;
//    }


    private String findNode(long queryId) {


        String response = "NOT FOUND T.T";

        //if queryId is in localNode
        if (isThisMyNode(queryId)) {
            response = buildResponse(localNode.getAddress(), "NODEFOUND_");
        }
        //else if queryId is in localNode's successor
        else if (isThisNextNode(queryId)) {
            response = buildResponse(localNode.getSuccessor1(), "NODEFOUND_");
        }
        //else recursive call findNode to find queryId
        else {
            localNode.lock();
            InetSocketAddress closestNodeToQueryId = localNode.findClosestPrecedingFinger(queryId);
            System.out.println("Query ID: " + queryId + " on " + closestNodeToQueryId.getAddress() + " : "
                    + closestNodeToQueryId.getPort());
            response = SocketAddrHelper.sendRequest(closestNodeToQueryId, "FINDNODE_" + queryId);
            System.out.println("Response from node " + closestNodeToQueryId.getAddress() + ", port "
                    + closestNodeToQueryId.getPort());
            localNode.unlock();

            //InetSocketAddress closestNodeToQueryId = localNode.findClosestPrecedingFinger(queryId);

            //String nextRequest = buildResponse(query, "FINDNODE_");
            //response = SocketAddrHelper.sendRequest(closestNodeToQueryId, nextRequest);


        }
        return response;
    }

    private boolean isThisMyNode(long queryId) {
        boolean result = false;
        long localNodeId = HashHelper.hashSocketAddress(localNode.getAddress());
        long predecessorNodeId = HashHelper.hashSocketAddress(localNode.getPredecessor1());

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
        long successorNodeId = HashHelper.hashSocketAddress(localNode.getSuccessor1());
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
        Map<String, String> map = localNode.getDataStore();
        for (Map.Entry<String, String> entry : map.entrySet()) {
             String strKey = entry.getKey();
             long hashedKeyEntry = HashHelper.hashString(strKey);
             long localNodeId = HashHelper.hashSocketAddress(localNode.getAddress());
             if (newNodeId < localNodeId
                && ((hashedKeyEntry > localNodeId)) || (hashedKeyEntry < newNodeId)) {
                sbResponse.append(strKey + ":" + entry.getValue());
                sbResponse.append("::");

                //remove key-value pair from the current node
                 localNode.lock();
                 map.remove(strKey);
                 localNode.unlock();
            }
             else if ((newNodeId > localNodeId)
                && (hashedKeyEntry > localNodeId && hashedKeyEntry < newNodeId)) {
                 sbResponse.append(strKey + ":" + entry.getValue());
                 sbResponse.append("::");

                 //remove the key value pair from the current node
                 localNode.lock();
                 map.remove(strKey);
                 localNode.unlock();
             }
        }
        String response = sbResponse.toString();
        if (response != null && !response.isEmpty() && response != "") {
            response = response.substring(0, response.length() - 2);
        }
        return response;
    }

}
