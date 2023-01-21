import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A node in the network is used as a shared resource and as a synchronizer and manager of its own neighbors.
 * This means that it synchronizes the order in which it updates itself and handles sending messages to its neighbors.
 * in our implementation, as was recommended in class, we used flooding with sequence numbers
 * to broadcast the link states of every node in the graph, and this is the main component of the algorithm.
 * I accomplished the synchronization of sending messages from this node by creating a single method called send()
 * that is locked by the node whenever there is a MessageHandler that is using it to send a message to another neighbor.
 * this is important in order to avoid two threads trying to connect to the same socket at the same time, which causes
 * ConnectException because this is not a distributed system, it's my laptop... my poor poor laptop......
 * This is inter-node synchronization, so it doesn't affect the other nodes in the simulation.
 */
public class Node implements Runnable {

    public final int nodeId;
    public final int numOfNodes;
    public  int roundNumber;
    public int[] SequenceCounter;
    public volatile boolean establishedConnections;
    public double[][] adjacencyMatrix;
    public HashMap<Integer, HashMap<String, Number>> neighborsInfo;
    public HashMap<Integer, Neighbor> neighbors;
    private CountDownLatch finishRoundSignal;
    private final ReentrantLock sendLock;

    public Node(int nodeId,
                int numOfNodes,
                HashMap<Integer, HashMap<String, Number>> neighborsInfo) {

        this.nodeId = nodeId;
        this.numOfNodes = numOfNodes;
        this.neighborsInfo = neighborsInfo;
        sendLock = new ReentrantLock();
        SequenceCounter = new int[numOfNodes];
        roundNumber = 0;
        establishedConnections = false;
        initiateNeighbors();

        adjacencyMatrix = new double[numOfNodes][numOfNodes];
        double weight;
        for (int id1 = 1; id1 <= this.numOfNodes; id1++) {
            for (int id2 = 1; id2 <= this.numOfNodes; id2++) {
                if (this.nodeId == id1 && neighborsInfo.containsKey(id2))
                    weight = (Double) neighborsInfo.get(id2).get("weight");
                else if (this.nodeId == id2 && neighborsInfo.containsKey(id1))
                    weight = (Double) neighborsInfo.get(id1).get("weight");
                else
                    weight = -1.0;
                adjacencyMatrix[id1 - 1][id2 - 1] = weight;
            }
        }
    }

    public void updateWeight(int neighborId, double newWeight) {
        neighborsInfo.get(neighborId).put("weight", newWeight);
        adjacencyMatrix[this.nodeId - 1][neighborId - 1] = newWeight;
        adjacencyMatrix[neighborId - 1][this.nodeId - 1] = newWeight;
    }

    public void print_graph() {
        for (double[] matrix : this.adjacencyMatrix) {
            System.out.print(matrix[0]);
            for (int j = 1; j < this.adjacencyMatrix.length; j++) {
                System.out.print(", " + matrix[j]);
            }
            System.out.print(System.lineSeparator());
        }
    }

    public void initiateNeighbors() {
        neighbors = new HashMap<>();
        for (int neighborID : neighborsInfo.keySet()) {
            int listenPort = (Integer) neighborsInfo.get(neighborID).get("listen port");
            int sendPort = (Integer) neighborsInfo.get(neighborID).get("send port");
            Neighbor neighbor = new Neighbor(neighborID, listenPort, sendPort, this);
            neighbors.put(neighborID, neighbor);
        }
    }

    public void setFinishRoundSignal(CountDownLatch signal) {
        this.finishRoundSignal = signal;
    }

    public void startListening() {
        if (establishedConnections) return;
        for (Neighbor neighbor : neighbors.values())
            neighbor.start();
        establishedConnections = true;
    }

    public void stopListening(CountDownLatch signal) {
        for (Neighbor neighbor : neighbors.values())
            neighbor.close();
        signal.countDown();
    }

    private boolean isNodeFullyUpdated() {
        for (int i = 1; i < SequenceCounter.length; i++)
            if (SequenceCounter[i - 1] != SequenceCounter[i])
                return false;
        return true;
    }

    /**
     * creates the node's link state, as defined in class.
     * @return - a set in the form { [(u, v), w(u, v)] for u in N(v) } where v is this node
     */
    public HashSet<Pair<Pair<Integer, Integer>, Double>> createLinkStates() {
        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates = new HashSet<>();
        for (int neighborId : neighborsInfo.keySet()) {
            linkStates.add(
                    new Pair<>(
                            new Pair<>(nodeId, neighborId),
                            (Double) neighborsInfo.get(neighborId).get("weight")
                    )
            );
        }
        return linkStates;
    }

    public Message getMessageToBroadcast() {
        TYPES msgType = TYPES.BROADCAST;
        HashMap<String, Serializable> msgContent = new HashMap<>();
        msgContent.put("Source", nodeId);
        msgContent.put("Sequence", roundNumber);
        msgContent.put("LinkStates", createLinkStates());
        return new Message(msgType, msgContent);
    }

    private void broadcast(Message msg) {
        for (Neighbor neighbor : neighbors.values())
            send(msg, neighbor.sendPort);
    }

    public void send(Message msg, int toPort) {
        sendLock.lock();
        try (Socket connection = new Socket("localhost", toPort)) {
            ObjectOutputStream output = new ObjectOutputStream(connection.getOutputStream());
            new ObjectInputStream((connection.getInputStream()));
            output.writeObject(msg);
            output.flush();

        } catch (IOException ignored) {}
        sendLock.unlock();
    }

    @Override
    public void run() {
        roundNumber += 1;
        broadcast(getMessageToBroadcast());
        SequenceCounter[nodeId - 1] = roundNumber;
        while (!isNodeFullyUpdated()) Thread.onSpinWait();
        finishRoundSignal.countDown();
    }
}
