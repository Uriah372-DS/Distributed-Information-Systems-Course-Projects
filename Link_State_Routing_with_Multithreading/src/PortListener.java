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
        this.isRunning = false;
    }

    public void startListening(){
        this.isRunning = true;
        start();
    }

    public void stopListening(){
        this.isRunning = false;
    }

    @Override
    public void run(){
        try {
            ServerSocket serverSocket = new ServerSocket(this.port);
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

    private void updateNodeInfo(Message<TYPES, HashMap<String, Serializable>> msg) {
        if (!this.node.updatedNodes.contains((Integer) msg.getContent().get("Origin"))) {
            // update the weights of all the edges
            //noinspection unchecked
            HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates =
                    (HashSet<Pair<Pair<Integer, Integer>, Number>>) msg.getContent().get("LinkStates");
            for (Pair<Pair<Integer, Integer>, Number> linkState : linkStates) {
                HashMap<String, Number> nodeAttributes = new HashMap<>();
                this.node.RoutingTable.put();
            }
        }
    }

    private void forward(Message<TYPES, HashMap<String, Serializable>> msg) {
        HashMap<String, Serializable> msgContent = new HashMap<>();
        msgContent.put("Origin", msg.getContent().get("Origin"));
        msgContent.put("HopCounter", ((Integer) msg.getContent().get("HopCounter")) - 1);
        msgContent.put("LinkStates", msg.getContent().get("LinkStates"));
    }

    public void run() {
        try {
            ObjectInputStream input = new ObjectInputStream(new DataInputStream(this.socket.getInputStream()));
            ObjectOutputStream output = new ObjectOutputStream(new DataOutputStream(this.socket.getOutputStream()));
            Object msgObject = input.readObject();
            if (msgObject instanceof Message) {
                //noinspection unchecked
                Message<TYPES, HashMap<String, Serializable>> msg =
                        (Message<TYPES, HashMap<String, Serializable>>) input.readObject();
                switch (msg.type) {
                    case BROADCAST:
                        if (((Integer) msg.getContent().get("HopCounter")) > 0) forward(msg);
                        else updateNodeInfo(msg);

                }
            }
            input.close();
            output.close();
            this.socket.close();

            // decrement the semaphore count
            semaphore.release();
        } catch (NumberFormatException ignore){

        } catch (ClassNotFoundException | IOException e){
            e.printStackTrace();
        }
    }
}
