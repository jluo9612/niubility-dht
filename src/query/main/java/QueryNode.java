package query.main.java;

import java.io.BufferedReader;
import java.net.InetSocketAddress;

import jdk.internal.jline.internal.InputStreamReader;

public class QueryNode {
    private InetSocketAddress chordNode;

    public QueryNode(InetSocketAddress address) {
        chordNode = address;

        System.out.println("Connecting to the node" + chordNode.getAddress() + ":" + chordNode.getPort() + 
            ", located in " + HashHelper.hexIdAndLocation(chordNode));
        // start seaching
        readFromChord();
    }

    private void readFromChord() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String key = "";
            String response = "";

            System.out.println("Starting to search data by key in the chord!");
            while (true) {
                System.out.println("Please enter the search key, type \"exit\" to leave.");
                
                // read search key from console
                key = br.readLine();
                // exit while loop, end the query process
                if (key.equals("exit")) {
                    break;
                }
                // send request to chord, and get response
                response = SocketAddrHelper.sendRequest(chordNode, "GETVALUE_" + key);
                System.out.println("The query result: ");
                System.out.println(response);
            }
            System.out.println("Exit searching query!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}