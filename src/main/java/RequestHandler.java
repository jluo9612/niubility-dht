import util.SocketAddrHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            System.out.println("Located the node: " + localNode.getId());
            value = localNode.getDataStore().get(key);
            if (value != null) {
                response = "Found the value: " + value + ", on the node: " + localNode.getId() + ", IP: " + 
                    localNode.getAddress().getAddress() + ":" + localNode.getAddress().getPort();
            }
        }
        // if the query is between local node and successor, 
        // try to get value in successor
        else if (isThisNextNode(keyHash)) {
            response = SocketAddrHelper.sendRequest(localNode.getSuccessor1(), "GETVALUE_" + data);
        }
        // search the correct node by finger table
        else {
            InetSocketAddress chordNode = localNode.findClosestPrecedingFinger(keyHash);
            System.out.println("Searching node on chord to get value");
            response = SocketAddrHelper.sendRequest(chordNode, "GETVALUE_" + data);
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
            localNode.getDataStore().put(keyValue[0], keyValue[1]);
            response = "{" + keyValue[0] + "," + keyValue[1] + "} stored in " + keyHash + 
                ", which on the node" + localNode.getId();
        }
        // if the query is between local node and successor, 
        // put value in successor
        else if (isThisNextNode(keyHash)) {
            response = SocketAddrHelper.sendRequest(localNode.getSuccessor1(), "PUTVALUE_" + data);
        }
        // search the correct node by using finger table
        else {
            InetSocketAddress chordNode = localNode.findClosestPrecedingFinger(keyHash);
            System.out.println("Searching node on chord to put value");
            response = SocketAddrHelper.sendRequest(chordNode, "PUTVALUE_" + data);
        }
        
        return response;
    }

    /**
     * check whether the key's position is between predecessor and local node
     * @param keyHash
     * @return boolean
     */
    private boolean isThisMyNode(long keyHash) {
        boolean res = false;
        long preId = HashHelper.hashSocketAddress(localNode.getPredecessor());
        // check the position of keyHash
        if ((localNode.getId() > preId && keyHash > preId && keyHash <= localNode.getId()) || 
            (localNode.getId() < preId && (keyHash > preId || keyHash <= localNode.getId()))) {
            res = true;
        }

        return res;
    }

    /**
     * check whether the key's position is between local node and successor
     * @param keyHash
     * @return boolean
     */
    private boolean isThisNextNode(long keyHash) {
        boolean res = false;
        long sucId = HashHelper.hashSocketAddress(localNode.getSuccessor());
        // check the position of keyHash
        if ((localNode.getId() < sucId && keyHash > localNode.getId() && keyHash <= sucId) || 
            (localNode.getId() > sucId && (keyHash > localNode.getId() || keyHash <= sucId))) {
            res = true;
        }

        return res;
    }

}
