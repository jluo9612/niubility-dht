import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * RequestListener class listens to a port
 * accepts requests
 * and sends to RequestHandler class to process requests
 */
public class RequestListener extends Thread{
    private ServerSocket serverSocket;
    private Node localNode;
    private boolean status;

    public RequestListener(Node node) {
        localNode = node;
        status = true;
        InetSocketAddress localSocketAddress = localNode.getAddress();
        int port = localIpAddress.getPort();

        //open socket
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("***************");
            throw new RuntimeException("Cannot connect to request listener port: " + port + " T.T System exited.", e);
        }
    }

    @Override
    public void run() {
        while (status) {
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                System.out.println("***************");
                throw new RuntimeException("Cannot accept connection T.T", e);
            }
            //start a new thread for request handler
            new Thread(new RequestHandler(clientSocket, localNode).start);
        }
    }

    public void closeListener() {
        status = false;
    }

}
