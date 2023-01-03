import javax.script.ScriptEngineManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;


class PortListener extends Thread {
    public int port;
    protected boolean isRunning;
    private final Node node;

    private Semaphore semaphore;

    PortListener(int port, Node node, Semaphore semaphore) {
        this.port = port;
        this.node = node;
        this.semaphore = semaphore;
        this.isRunning = true;
    }

    public void startListening(){
        this.isRunning = true;
    }

    public void stopListening(){
        this.isRunning = false;
    }

    @Override
    public void run(){
        try {
            ServerSocket serverSocket = new ServerSocket(this.port);
            startListening();
            while(this.isRunning) {
                System.out.println("node: " + node.getNodeID() + " TCP listening on: " + this.port);

                Socket clientSocket = serverSocket.accept();

                System.out.println("Just connected to " + clientSocket.getRemoteSocketAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this.node, semaphore);
                clientHandler.start();

            }
        } catch (SocketTimeoutException s) {
            System.out.println("Socket timed out!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler extends Thread {
    Socket socket;
    Node node;
    Semaphore semaphore;
    ClientHandler(Socket socket, Node node, Semaphore semaphore) {
        this.socket = socket;
        this.node = node;
        this.semaphore = semaphore;
    }

    public void run(){
        try {
            DataInputStream input = new DataInputStream(this.socket.getInputStream());
            DataOutputStream output = new DataOutputStream(this.socket.getOutputStream());
            // process the received message
            // ...
            // send a response back to the client

            int num = Integer.parseInt(input.readUTF());
            HashMap<String, Number> requestedTable = this.node.RoutingTable.get(num);
            output.writeUTF(requestedTable.toString());
            input.close();
            output.close();
            this.socket.close();

            // decrement the semaphore count
            semaphore.release();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException ignore){
        }
    }
}
