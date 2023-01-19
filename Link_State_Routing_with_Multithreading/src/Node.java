import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class Node implements Runnable {

    public final int nodeID;
    private final int numOfNodes;
    private  int roundNumber;
    private boolean establishedConnections;
    private HashMap<Integer, PortListener> portListeners;
    public Double[][] adjacencyMatrix;
    public HashMap<Integer, HashMap<String, Number>> NeighborsTable;
    public Map<Integer, Integer> SequenceCounter;
    private CountDownLatch listenerSignal;
    private CountDownLatch finishRoundSignal;
    private final ReentrantLock sendLock;
    private final ReentrantLock floodingLock;

    /**
     * Construct a single Node in the network.
     * @param nodeID - a unique ID number for this node, ranging between 1 and the number of nodes in the network.
     * @param numOfNodes - the number of nodes in the network (this implementation is non-uniform)
     * @param NeighborsTable - the initial neighborhood data of this node.
     */
    public Node(int nodeID,
                int numOfNodes,
                HashMap<Integer, HashMap<String, Number>> NeighborsTable) {

        this.nodeID = nodeID;
        this.numOfNodes = numOfNodes;
        this.NeighborsTable = NeighborsTable;
        this.roundNumber = 0;
        this.establishedConnections = false;
        this.sendLock = new ReentrantLock();
        this.floodingLock = new ReentrantLock();
        // initialize SequenceCounter
        this.SequenceCounter = Collections.synchronizedMap(new HashMap<>());
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.SequenceCounter.put(id, 0);
        }
        this.SequenceCounter.put(nodeID, nodeID);

        // initialize the fields of the routing table, assume they will be filled later in the run.
        this.adjacencyMatrix = new Double[this.numOfNodes][this.numOfNodes];
        for (int id1 = 1; id1 <= this.numOfNodes; id1++) {
            for (int id2 = 1; id2 <= this.numOfNodes; id2++) {
                // initialize weight to neighbor's weight if id1 or id2 are neighbors of this node, or -1.0 otherwise.
                double weight = -1.0;
                if (this.nodeID == id1 && NeighborsTable.containsKey(id2)) {
                    weight = (Double) NeighborsTable.get(id2).get("weight");
                }
                else if (this.nodeID == id2 && NeighborsTable.containsKey(id1)) {
                    weight = (Double) NeighborsTable.get(id1).get("weight");
                }
                this.adjacencyMatrix[id1 - 1][id2 - 1] = weight;
            }
        }
    }

    public void setFinishRoundSignal(CountDownLatch finishRoundSignal) {
        this.finishRoundSignal = finishRoundSignal;
    }

    public HashSet<Pair<Pair<Integer, Integer>, Double>> createLinkStates() {

        // create new empty set of pairs
        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates = new HashSet<>();

        // fill the set with edges and their weights
        for (int neighborId : this.NeighborsTable.keySet()) {
            linkStates.add(
                    new Pair<>(
                            new Pair<>(this.nodeID, neighborId),
                            (Double) this.NeighborsTable.get(neighborId).get("weight")
                    )
            );
        }

        // return a set of the form: {<(u, v), w(u, v)> | u in N(v)}, where v == this node
        return linkStates;
    }

    public void updateWeight(int neighborId, Number newWeight) {
        NeighborsTable.get(neighborId).put("weight", newWeight);
        adjacencyMatrix[this.nodeID - 1][neighborId - 1] = (Double) newWeight;
        adjacencyMatrix[neighborId - 1][this.nodeID - 1] = (Double) newWeight;
    }

    public void establishConnections() {
        if (this.establishedConnections) return;

        // use CountDownLatch to make the node wait until it receives a message from all other nodes in the network.
        this.listenerSignal = new CountDownLatch(this.numOfNodes - 1);
        CountDownLatch initiatedAndListening = new CountDownLatch(NeighborsTable.size());
        CountDownLatch signalClosed = new CountDownLatch(NeighborsTable.size());

        // initialize a port listener for every neighbor to establish communication
        portListeners = new HashMap<>();
        for (int neighborId : this.NeighborsTable.keySet()) {
            int neighborPort = (Integer) (this.NeighborsTable.get(neighborId).get("listen port"));
            this.portListeners.put(neighborId, new PortListener(neighborPort, this,
                    initiatedAndListening, signalClosed));
        }

        // start all PortListener threads
        for (PortListener listener : portListeners.values()) { listener.start(); }

        // validate that they are all actually listening
        try {
            initiatedAndListening.await();
        } catch (InterruptedException e) {
            for (PortListener listener : portListeners.values()) { listener.close(); }
            e.printStackTrace();
        }

        this.establishedConnections = true;
        System.out.println("node " + this.nodeID + " connections established!");
    }

    public void terminateConnections() {
        if (!this.establishedConnections) return;

        for (PortListener listener : portListeners.values()) { listener.close(); }

        this.establishedConnections = false;
        System.out.println("node " + this.nodeID + " connections terminated!");
    }

    public void floodingWithSequenceNumbers(Message msg, int excludePort) {
        floodingLock.lock();
        HashMap<String, Serializable> msgContent = msg.getContent();
        Integer source = (Integer) msgContent.get("Source");
        Integer msgSequenceNumber = (Integer) msgContent.get("Sequence");
        if (msgSequenceNumber > SequenceCounter.get(source)) {
            updateNodeInfo(msgContent);
            broadcast(msg, excludePort);
            // after updating the data with the new link state, we signal it to the node
            this.listenerSignal.countDown();
        }
        floodingLock.unlock();
    }

    public void updateNodeInfo(HashMap<String, Serializable> msgContent) {
        // System.out.println("node " + this.nodeID + " has received a message from node " + msgContent.get("Source"));
        // update the node's SequenceCounter with the new message's sequence field
        this.SequenceCounter.put((Integer) msgContent.get("Source"), (Integer) msgContent.get("Sequence"));

        // update the weights of all the edges
        //noinspection unchecked
        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates =
                (HashSet<Pair<Pair<Integer, Integer>, Double>>) msgContent.get("LinkStates");
        for (Pair<Pair<Integer, Integer>, Double> linkState : linkStates) {
            int nodeID1 = linkState.getKey().getKey();
            int nodeID2 = linkState.getKey().getValue();
            double weight = linkState.getValue();
            this.adjacencyMatrix[nodeID1 - 1][nodeID2 - 1] = weight;
            this.adjacencyMatrix[nodeID2 - 1][nodeID1 - 1] = weight;
        }
    }

    /**
     * sends a message to a port.
     * @param msg - the message.
     * @param port - the port.
     */
    public void send(Message msg, int port) {

        Socket socket = null;
        ObjectOutputStream output;
        try {
            socket = new Socket("localhost", port);

            output = new ObjectOutputStream(socket.getOutputStream());
            // need to open an ObjectInputStream to avoid aborted connections
            new ObjectInputStream(socket.getInputStream());
            output.writeObject(msg);
            output.flush();
        } catch (ConnectException | BindException e) {
            System.out.println(e.getClass().getName() + " in node " + nodeID
                    + " when attempting to send a message to port " + port
                    + " - Exception Message: " + e.getMessage());
        } catch (EOFException | SocketException ignore) {

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void broadcast(Message msg, Integer clientPort) {

        for (Integer neighborId : this.NeighborsTable.keySet()) {
            Integer sendPort = (Integer) (this.NeighborsTable.get(neighborId).get("send port"));
            boolean sentFromThisNeighbor = clientPort.equals(sendPort);
            if (sentFromThisNeighbor) {
                Message newMsg = Message.copy(msg);
                sendLock.lock();
                this.send(newMsg, sendPort);
                sendLock.unlock();
            }
        }
    }

    private void broadcast(HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates) {
        // create a Message instance to broadcast from this node
        TYPES msgType = TYPES.BROADCAST;
        HashMap<String, Serializable> msgContent = new HashMap<>();
        msgContent.put("Source", this.nodeID);
        msgContent.put("Sequence", this.roundNumber);
        msgContent.put("LinkStates", linkStates);
        Message msg = new Message(msgType, msgContent);
        broadcast(msg, -1);
    }

    @Override
    public void run() {
        this.roundNumber += 1;

        linkStateRound();

        // we look at this node's SequenceCounter,
        // and check if all the sequence numbers in it are equal to roundNumber.
        // this will indicate that the node has received all of its links and is ready to finish the round.
        SequenceCounter.put(nodeID, roundNumber);
        //noinspection StatementWithEmptyBody
        while (new HashSet<>(SequenceCounter.values()).size() > 1);

        // signal to manager that this node has finished its round.
        this.finishRoundSignal.countDown();
    }

    private void linkStateRound() {
        // create the local link state of the node
        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates = createLinkStates();

        // broadcast the link state to all nodes in the network.
        broadcast(linkStates);

        // wait until link states from all other n - 1 nodes have been received
        try {
            listenerSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Print the current routing table of this node.
     */
    public void print_graph() {
        for (Double[] matrix : this.adjacencyMatrix) {
            System.out.print(matrix[0]);
            for (int j = 1; j < this.adjacencyMatrix.length; j++) {
                System.out.print(", " + matrix[j]);
            }
            System.out.print(System.lineSeparator());
        }
    }
}
