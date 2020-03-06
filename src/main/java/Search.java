//import com.sun.tools.corba.se.idl.toJavaPortable.Helper;
import util.HashHelper;
import util.SocketAddrHelper;

import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * Search class searches chord nodes requested by users
 */
public class Search {
    private static InetSocketAddress localSocketAddress;
//    private static Helper helper;

    public static void main(String[] args) {
//        helper = new Helper();
        if (args.length != 2) {
            System.out.println("Invalid input O.O System exited.");
            System.exit(0);
        }
        //check user input

        localSocketAddress = SocketAddrHelper.createSocketAddress(args[0] + ":" + args[1]);
        if (localSocketAddress == null) {
            System.out.println("*****************");
            System.out.println("Invalid connect address T.T System exited.");
            System.exit(0);
        }
        //send socket address of the node being searched, and check if its status
        String response = SocketAddrHelper.sendRequest(localSocketAddress, "KEEP");

        //node status is negative
        if (response == null || !response.equals("ALIVE")) {
            System.out.println("*****************");
            System.out.println("Cannot find the target node T.T System exited.");
            System.exit(0);
        }

        //node status is positive
        System.out.println("*****************");
        System.out.println("Successfully connected to node address: " + localSocketAddress.getAddress().toString());
        System.out.println("port: " + localSocketAddress.getPort());
        System.out.println("position: " + HashHelper.hexIdAndLocation(localSocketAddress));


        //check the stability of system by getting predecessor and successor
        InetSocketAddress predecessorAddress = SocketAddrHelper.requestAddress(localSocketAddress, "YOURPRE");
        InetSocketAddress successorAddress = SocketAddrHelper.requestAddress(localSocketAddress, "YOURSUCC");

        if (predecessorAddress == null || successorAddress == null) {
            System.out.println("*****************");
            System.out.println("The target node is disconnected T.T System exited.");
            System.exit(0);
        }
        //chord is stable if there is only one chord node which is local node
        //so the predecessor and the successor are both local node
        //or chord is stable if there is more than one chord node
        //so the predecessor and the successor are both different than local node
        while ((!predecessorAddress.equals(localSocketAddress) && successorAddress.equals(localSocketAddress))
                || (predecessorAddress.equals(localSocketAddress) && !successorAddress.equals(localSocketAddress))) {
            System.out.println("*****************");
            System.out.println("Searching...just 2 seconds ^^");
            predecessorAddress = SocketAddrHelper.requestAddress(localSocketAddress, "YOURPRE");
            successorAddress = SocketAddrHelper.requestAddress(localSocketAddress, "YOURSUCC");

            if (predecessorAddress == null || successorAddress == null) {
                System.out.println("*****************");
                System.out.println("The target node is disconnected T.T System exited.");
                System.exit(0);
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }

            //get user input
            Scanner userInput = new Scanner(System.in);
            while (true) {
                System.out.println("*****************");
                System.out.println("Type search key or type \"exit\" anytime to exit.");
                String userCommand = userInput.nextLine();

                //exit the chord ring
                if (userCommand.equalsIgnoreCase("exit")) {
                    System.out.println("System exited.");
                    System.exit(0);
                }
                //search target node
                else if (userCommand.length() > 0) {
                    long hash = HashHelper.hashString(userCommand);
                    System.out.println("User command hash is: " + Long.toHexString(hash));
                    InetSocketAddress result = SocketAddrHelper.requestAddress(localSocketAddress, "FINDSUCC_"+hash);

                    if (result == null) {
                        System.out.println("*****************");
                        System.out.println("The target node is disconnected T.T System exited.");
                        System.exit(0);
                    }
                    System.out.println("*****************");
                    System.out.println("Response from node IP address: " + localSocketAddress.getAddress().toString());
                    System.out.println("port: " + localSocketAddress.getPort());
                    System.out.println("position: " + HashHelper.hexIdAndLocation(localSocketAddress));
                    System.out.println("==================");
                    System.out.println("Target node IP address: " + result.getAddress().toString());
                    System.out.println("port: " + result.getPort());
                    System.out.println("position: " + HashHelper.hexIdAndLocation(result));
                }
            }
        }
    }
}
