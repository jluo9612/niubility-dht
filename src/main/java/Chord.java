import com.sun.tools.corba.se.idl.toJavaPortable.Helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Chord class creates a chord node
 * the new created chord node creates a new chord ring
 * or it joins an existing chord ring
 */
public class Chord {
    private static InetAddress connectAddress;
    private static Helper helper;
    private static Node newNode;
    public static void main(String[] args) {
        helper = new Helper();
        String localIpAddress = "";
        //get local IP address
        try {
            localIpAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        //create new chord node
        newNode = new Node(Helper.createSocketAddress(localIpAddress + ":" + args[0]));

        //create a new chord ring
        if (args.length == 1) {
            connectAddress = newNode.getAddress();
        }

        //join an existing chord ring
        else if (args.length == 3) {
            connectAddress = Helper.createSocketAddress(args[1] + ":" + args[2]);
            //invalid connect address
            if (connectAddress == null) {
                System.out.println("Invalid connect address T.T System exited.");
                return;
            }
        }

        //invalid input
        else {
            System.out.println("Invalid input T.T System exited");
            //exit JVM
            System.exit(0);
        }

        //join chord ring
        boolean joinStatus = newNode.join(connectAddress);

        //join succeeded
        if (joinStatus) {
            System.out.println("Joining the chord ring LOL");
            System.out.println("Local IP address: " + localIpAddress);
            newNode.printNeighbors();
        }
        //join failed
        else {
            System.out.println("Join failure T.T Cannot connect to the target node T.T System exited. ");
            System.exit(0);
        }


        //get user command from user input
        Scanner userInput = new Scanner(System.in);
        while (true) {
            System.out.println("*****************");
            System.out.println("1. type \"info\" to check node data");
            System.out.println("2. type \"exit\" to leave the chord ring");
            String userCommand = userInput.next();
            if (userCommand.equalsIgnoreCase("info")) {
                newNode.printDataStructure();
            } else if (userCommand.equalsIgnoreCase("exit")){
                newNode.stopAllThreads();
                System.out.println("System existed.");
                System.exit(0);
            } else {
                System.out.println("Invalid input O.O please try again");
            }
        }

    }


}
