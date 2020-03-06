package query;

import util.SocketAddrHelper;

public class Query {
    public static void main(String[] args) {
        // check input length
        if (args.length == 2) {
            new QueryNode(SocketAddrHelper.createSocketAddress(args[0] + ":" + args[1]));
        } else {
            System.err.println("Use the format: Query [nodeAddress] [nodePort]");
            System.exit(1);
        }
    }
} 