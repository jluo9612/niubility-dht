package query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import util.HashHelper;
import util.SocketAddrHelper;

public class LoadData {
    private InetSocketAddress chordNode;

    public LoadData(InetSocketAddress address) {
        chordNode = address;

        System.out.println("Connecting to the node" + chordNode.getAddress() + ":" + chordNode.getPort() + 
            ", located in " + HashHelper.hexIdAndLocation(chordNode));

        writeToChord();
    }

    private void writeToChord() {
        try {
            String pathname = "src/main/java/query/resource/data.txt";
            File filename = new File(pathname);
            InputStreamReader reader = new InputStreamReader(new FileInputStream(filename));
            BufferedReader br = new BufferedReader(reader);
            String line = br.readLine();
            String response = "";
            System.out.println("Starting to load data from data.txt!");
            while (line != null) {
                response = SocketAddrHelper.sendRequest(chordNode, "PUTVALUE_" + line);
                line = br.readLine();
                System.out.println(response);
            }
            System.out.println("The data is loaded!!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // check input length
        if (args.length == 2) {
            new LoadData(SocketAddrHelper.createSocketAddress(args[0] + ":" + args[1]));
        } else {
            System.err.println("Use the format: LoadData [nodeAddress] [nodePort]");
            System.exit(1);
        }
    }
}