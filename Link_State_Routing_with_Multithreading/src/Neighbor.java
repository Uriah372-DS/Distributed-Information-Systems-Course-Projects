import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is a class used to contain information about a node's neighbor. It is also used to listen for any messages
 * from other neighboring nodes and handle them accordingly.
 * The communication logic between the nodes is contained in this class,
 * and is similar to a standard multithreaded server architecture, in which every neighbor listens on its ServerSocket
 * for any incoming messages and when it receives a message it immediately creates a MessageHandler thread
 * to handle the message and then continues listening. this way all the messages are received by all the nodes
 * very quickly, and no thread has to perform very heavy computation on its own,
 * which helped in the optimization part and made it possible to finish example 5 without freezing my laptop...
 * Every neighbor keeps track of all the MessageHandlers it has created during it's lifetime, and terminates them
 * when it stops listening for connections (when close() is called).
 */
public class Neighbor extends Thread implements Closeable {

    int neighborID;
    public int listenPort;
    public int sendPort;
    private ServerSocket server;
    private final Node node;
    private volatile boolean initiated;
    public volatile boolean receiving;
    public volatile boolean accepting;
    private final ConcurrentLinkedQueue<MessageHandler> messageHandlers;

    public Neighbor(int id,
                    int listenPort,
                    int sendPort,
                    Node node) {
        this.neighborID = id;
        this.listenPort = listenPort;
        this.sendPort = sendPort;
        this.node = node;
        initiated = false;
        receiving = false;
        accepting = false;
        messageHandlers = new ConcurrentLinkedQueue<>();
        initiate();
    }

    public void initiate() {
        try {
            server = new ServerSocket();
        } catch (IOException e) {
            System.out.println("IOException while constructing server at port " + listenPort);
            throw new RuntimeException(e.getMessage());
        }
        try {
            server.setReuseAddress(true);
        } catch (SocketException e) {
            System.out.println("SocketException while calling server.setReuseAddress(true) at port "
                    + listenPort + ". see method setReuseAddress() description for causes.");
        }
        try {
            server.bind(new InetSocketAddress(listenPort));
        } catch (IOException e) {
            System.out.println("IOException while calling server.bind(new InetSocketAddress(port)) at port "
                    + listenPort + ". see method bind() description for causes.");
            e.printStackTrace();
        }
        initiated = true;
    }

    public void run() {
        if (!isInitiated())
            throw new RuntimeException("server " + listenPort + " wasn't initiated yet!");

        ObjectInputStream input;
        receiving = true;
        MessageHandler messageHandler = null;
        while (receiving && (!server.isClosed()) && server.isBound()) {
            try {
                accepting = true;
                Socket connection = server.accept();  // blocking
                accepting = false;


                input = new ObjectInputStream(connection.getInputStream());
                new ObjectOutputStream(connection.getOutputStream());
                Message msg = (Message) input.readObject();
                messageHandler = new MessageHandler(sendPort, msg, node);
                messageHandlers.add(messageHandler);
                messageHandler.start();
            }
            catch (SocketException ignored) {}
            catch (IOException e) {
                if (!e.getMessage().equals("Socket closed"))
                    System.out.println("server.accept() threw " + e.getClass().getName() + " at port "
                            + listenPort + " - Exception Message: " + e.getMessage());
                else
                    e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                System.out.println("readObject() threw " + e.getClass().getName() + " at port "
                        + listenPort + " - Exception Message: " + e.getMessage());
            }
            finally {
                if (messageHandler != null && messageHandler.isAlive()) {
                    messageHandler.close();
                }
            }
        }
    }

    public void close() {
        try {
            while (!accepting)
                Thread.onSpinWait();

            for (MessageHandler messageHandler : messageHandlers)
                messageHandler.close();

            receiving = false;
            interrupt();
            if (server != null && !server.isClosed())
                server.close();
        } catch (IOException ignored) {}
    }

    public boolean isInitiated() {
        return initiated;
    }
}

/**
 * This class handles a message that arrives from some port that the node listens to.
 * Inside this class the whole broadcasting headache is encapsulated, where access to a shared resource (the node)
 * for forwarding broadcast messages is done. A MessageHandler receives a message
 * and the port from which the message was sent so that it won't send it back to where it came from.
 * It updates the node's information about the network using this message and decides whether to forward the message
 * to all the nodes neighbors using the logic of the "flooding with sequence numbers" algorithm.
 */
class MessageHandler extends Thread implements Closeable {
    private final int fromSendPort;
    private final Message msg;
    private final Node node;

    public MessageHandler(int fromSendPort, Message msg, Node node) {
        this.fromSendPort = fromSendPort;
        this.msg = msg;
        this.node = node;
    }

    public void updateNodeInfo(HashMap<String, Serializable> msgContent) {
        int source = (Integer) msgContent.get("Source");
        int sequence = (Integer) msgContent.get("Sequence");
        node.SequenceCounter[source - 1] = sequence;

        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates;
        //noinspection unchecked
        linkStates = (HashSet<Pair<Pair<Integer, Integer>, Double>>) msgContent.get("LinkStates");

        for (Pair<Pair<Integer, Integer>, Double> linkState : linkStates) {
            int nodeID1 = linkState.getKey().getKey();
            int nodeID2 = linkState.getKey().getValue();
            double weight = linkState.getValue();
            node.adjacencyMatrix[nodeID1 - 1][nodeID2 - 1] = weight;
            node.adjacencyMatrix[nodeID2 - 1][nodeID1 - 1] = weight;
        }
    }

    private void forward(Message msg) {
        int toPort;
        for (Neighbor neighbor : node.neighbors.values()) {
            toPort = neighbor.sendPort;
            if (toPort != fromSendPort)
                node.send(msg, toPort);
        }
    }

    private void floodingWithSequenceNumbers() {
        HashMap<String, Serializable> msgContent = msg.getContent();
        int source = (Integer) msgContent.get("Source");
        int sequence = (Integer) msgContent.get("Sequence");
        if (sequence > node.SequenceCounter[source - 1]) {
            updateNodeInfo(msgContent);
            forward(msg);
        }
    }

    public void run() {
        floodingWithSequenceNumbers();
    }

    public void close() {
        interrupt();
    }
}
