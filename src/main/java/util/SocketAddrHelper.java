package util;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * SocketAddrHelper:
 * 1. Send request and acquire response through socket,
 * 2. Create socket address from input stream
 */

public final class SocketAddrHelper {

    /**
     * Create requested address by server and request
     * @param server
     * @param req
     * @return
     */
    public static InetSocketAddress requestAddress(InetSocketAddress server, String req) {

        // invalid input
        if (server == null || req == null) {
            return null;
        }

        // send request to server
        String response = sendRequest(server, req);

        // if response is null, return null
        if (response == null) {
            return null;
        }
        // server cannot find anything, return server itself
        else if (response.startsWith("NULL")) {
            return server;
        }
        // server get response, and create socket
        else {
            InetSocketAddress ret = createSocketAddress(response.split("_")[1]);
            return ret;
        }
    }

    /**
     * Send requset to server and get response
     * @param server
     * @param req: request
     * @return response
     */
    public static String sendRequest(InetSocketAddress server, String req) {

        // invalid input
        if (server == null || req == null) {
            return null;
        }



        // Create a talkSocket to output request to server's socket
        // return null if fail
        Socket talkSocket = null;
        try {
            talkSocket = new Socket(server.getAddress(), server.getPort());
            PrintStream output = new PrintStream(talkSocket.getOutputStream());
            output.println(req);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // sleep for a while, wait for response
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // get input stream, try to read response
        InputStream input = null;
        try {
            input = talkSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String response = readInputStream(input);

        // try to close talk socket
        try {
            talkSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    /**
     * Create socket address by using ip address and port
     * @param addr: ip_address:port
     * @return InetSocketAddress
     */
    public static InetSocketAddress createSocketAddress(String addr) {

        // invalid input
        if (addr == null) {
            return null;
        }

        String[] splitted = addr.split(":");

        // addr contains ip and port
        if (splitted.length == 2) {
            // get ip and port
            String ip = splitted[0];
            String port = splitted[1];

            InetAddress socketIP = null;

            if (ip.startsWith("/")) {
                ip = ip.substring(1);
            }

            try {
                socketIP = InetAddress.getByName(ip);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return null;
            }
            // combine ip and port
            return new InetSocketAddress(socketIP, Integer.valueOf(port));
        } else {
            return null;
        }
    }

    /**
     * Read from input stream
     * @param in: input stream
     * @return line
     */
    public static String readInputStream(InputStream in) {

        // invalid input
        if (in == null) {
            return null;
        }

        // read line from input stream
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        try {
            line = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return line;
    }
}

