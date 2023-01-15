import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;


class PortListener extends Thread {
    public int port;
    private ServerSocket serverSocket;
    protected boolean isRunning;  // used as a flag to signal this PortListener to start or stop running
    protected boolean isListening;  // used as a flag to signal the node that this port is listening
    private final Node node;
    private final Semaphore listenerSignal;

    PortListener(int port, Node node, Semaphore listenerSignal) {
        this.port = port;
        this.node = node;
        this.listenerSignal = listenerSignal;
        this.isRunning = false;
    }

    public void startListening() {
        this.isRunning = true;
    }

    public void stopListening(){
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (SocketException ignore) {
            //System.out.println("PortListener on port" + this.port + " successfully interrupted the ServerSocket");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        serverSocket = null;
        try {
            serverSocket = new ServerSocket(this.port);
            this.isListening = true;

            startListening();
            while(this.isRunning) {

                Socket clientSocket = serverSocket.accept();  // blocks until message is received

                ClientHandler clientHandler = new ClientHandler(clientSocket, this.node, listenerSignal);
                clientHandler.start();
            }
            serverSocket.close();
        } catch (SocketTimeoutException ignore) {
            //System.out.println("Socket timed out at port " + this.port + " in node " + this.node.nodeID);
        } catch (SocketException ignore) {
            //System.out.println("ServerSocket in port " + this.port + " closed by interruption in node " + node.nodeID);
        } catch (IOException e) {
            //System.out.println("IOException at port " + this.port + " in node " + this.node.nodeID);
            e.printStackTrace();
        }  finally {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                //System.out.println("IOException while trying to close serverSocket at port " + this.port + " in node " + this.node.nodeID);
                e.printStackTrace();
            }
        }
    }
}

class ClientHandler extends Thread {
    final Socket socket;
    final Node node;
    final Semaphore listenerSignal;
    ClientHandler(Socket socket, Node node, Semaphore listenerSignal) {
        this.socket = socket;
        this.node = node;
        this.listenerSignal = listenerSignal;
    }

    public void run() {
        ObjectInputStream input = null;
        ObjectOutputStream output = null;
        try {
            input = new ObjectInputStream(new DataInputStream(this.socket.getInputStream()));
            output = new ObjectOutputStream(new DataOutputStream(this.socket.getOutputStream()));
            Object msgObject = input.readObject();
            //noinspection unchecked
            Message<TYPES, HashMap<String, Serializable>> msg =
                    (Message<TYPES, HashMap<String, Serializable>>) msgObject;
            if (msg.type == TYPES.BROADCAST) {
                HashMap<String, Serializable> msgContent = msg.getContent();
                Integer source = (Integer) msgContent.get("Source");
                synchronized (this.node) {
                    if ((Integer) msgContent.get("Sequence") > this.node.SequenceCounter.get(source)) {
                        this.node.broadcast(msg, this.socket.getPort());
                        this.node.updateNodeInfo(msgContent);
                    }
                }
            } else {
                throw new WTFException("There shouldn't be any " + msg.type + " messages in this network!");
            }
        } catch (NumberFormatException | EOFException ignore) {

        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null)
                    input.close();
                if (output != null)
                    output.close();
                this.socket.close();
            } catch (IOException e) {
                //System.out.println("IOException while trying to close input, output, or socket in ClientHandler");
                e.printStackTrace();
            }
        }
    }
}

class WTFException extends RuntimeException {
    public WTFException(String errorMessage) {
        super(errorMessage);
    }
}
