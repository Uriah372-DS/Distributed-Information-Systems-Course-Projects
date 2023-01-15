import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;  // still from java.util
import java.util.concurrent.locks.ReentrantLock;

public class Node implements Runnable {

    public final int nodeID;
    private final int numOfNodes;
    private  int roundNumber;
    private boolean establishedConnections;
    private HashMap<Integer, PortListener> portListeners;
    private HashMap<Integer, ReentrantLock> sendPortLocks;
    public Double[][] adjacencyMatrix;
    public HashMap<Integer, HashMap<String, Number>> NeighborsTable;
    public Map<Integer, Integer> SequenceCounter;
    private Semaphore listenerSignal;
    private Semaphore finishRoundSignal;

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
        initializeSequenceCounter();
        this.establishedConnections = false;

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

    public void setSendPortLocks() {
        sendPortLocks = new HashMap<>();
        for (int id : NeighborsTable.keySet()) {
            int sendPort = (Integer) NeighborsTable.get(id).get("send port");
            sendPortLocks.put(sendPort, new ReentrantLock());
        }
    }

    public synchronized void initializeSequenceCounter() {
        this.SequenceCounter = Collections.synchronizedMap(new HashMap<>());
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.SequenceCounter.put(id, 0);
        }
        this.SequenceCounter.put(nodeID, nodeID);
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

    public void setFinishRoundSignal(Semaphore finishRoundSignal) {
        this.finishRoundSignal = finishRoundSignal;
    }

    public void updateWeight(int neighborId, Number newWeight) {
        NeighborsTable.get(neighborId).put("weight", newWeight);
        adjacencyMatrix[this.nodeID - 1][neighborId - 1] = (Double) newWeight;
        adjacencyMatrix[neighborId - 1][this.nodeID - 1] = (Double) newWeight;
    }

    public void establishConnections() {

        // use semaphore to make the node wait until it receives a message from all other nodes in the network.
        this.listenerSignal = new Semaphore(2 - this.numOfNodes);

        // initialize a port listener for every neighbor to establish communication
        portListeners = new HashMap<>();
        for (int neighborId : this.NeighborsTable.keySet()) {
            int neighborPort = (Integer)(this.NeighborsTable.get(neighborId).get("listen port"));
            this.portListeners.put(neighborId, new PortListener(neighborPort, this, listenerSignal));
        }

        // start all PortListener threads
        for (PortListener listener : portListeners.values()) { listener.start(); }

        // validate that they are all actually listening
        boolean allListening = false;
        while (!allListening) {
            allListening = true;
            for (PortListener listener : portListeners.values()) {
                allListening = allListening && listener.isListening;
            }
        }

        this.establishedConnections = true;
        //System.out.println("node " + this.nodeID + " connections established!");
    }

    public void terminateConnections() {
        if (!this.establishedConnections) return;

        for (PortListener listener : portListeners.values()) {
            try {
                listener.stopListening();
                listener.join();
            } catch (InterruptedException ignore) { }
        }
        this.establishedConnections = false;
        //System.out.println("node " + this.nodeID + " connections terminated!");

    }
    public synchronized void updateNodeInfo(HashMap<String, Serializable> msgContent) {

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
        // after updating the data with the received link state,
        // we signal it to the node and update the set of updated nodes
        this.listenerSignal.release();
    }

    /**
     * sends a message to a port.
     * @param msg - the message.
     * @param port - the port.
     */
    public synchronized void send(Message<TYPES, HashMap<String, Serializable>> msg, int port) {
        Socket socket = null;
        ObjectOutputStream output = null;
        ObjectInputStream input = null;
        try {
            socket = new Socket("localhost", port);
            output = new ObjectOutputStream(new DataOutputStream(socket.getOutputStream()));
            output.flush();
            // need to open ObjectInputStream to avoid aborted connections
            input = new ObjectInputStream(new DataInputStream(socket.getInputStream()));
            output.writeObject(msg);
        } catch (ConnectException ignore) {
            System.out.println("ConnectException in node " + nodeID + " when attempting to connect to port " + port);
        } catch (BindException e) {
            System.out.println("BindException in node " + nodeID + " when attempting to connect to port " + port);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void broadcast(Message<TYPES, HashMap<String, Serializable>> msg, int clientPort) {

        // forward link states to all neighbors except the neighbor from which this message was received
        for (Integer neighborId : this.NeighborsTable.keySet()) {

            int listenPort = (Integer) (this.NeighborsTable.get(neighborId).get("listen port"));
            int sendPort = (Integer) (this.NeighborsTable.get(neighborId).get("send port"));

            // I always forget which port is connected to which socket :(
            // so I do this stupid check to avoid the need to look it up :)
            if (listenPort != clientPort && sendPort != clientPort) {
                // we create a Message instance to send to this neighbor node.
                // we need to create a different instance for every port because threads use shared memory,
                // so they could potentially access the same message instance (which is BAD).
                TYPES msgType = msg.type;
                HashMap<String, Serializable> msgContent = msg.getContent();
                HashMap<String, Serializable> newMsgContent = new HashMap<>();
                newMsgContent.put("Source", msgContent.get("Source"));
                newMsgContent.put("Sequence", msgContent.get("Sequence"));
                newMsgContent.put("LinkStates", msgContent.get("LinkStates"));
                Message<TYPES, HashMap<String, Serializable>> newMsg = new Message<>(msgType, newMsgContent);

                // send link state message to this neighbor
                //System.out.println("node " + nodeID + " sends message to node " + neighborId + " through port " + sendPort);
                ReentrantLock portLock = sendPortLocks.get(sendPort);
                portLock.lock();
                this.send(newMsg, sendPort);
                portLock.unlock();
            }
        }
    }

    private synchronized void initBroadcast(HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates) {
        // create a Message instance to broadcast from this node
        TYPES msgType = TYPES.BROADCAST;
        HashMap<String, Serializable> msgContent = new HashMap<>();
        msgContent.put("Source", this.nodeID);
        msgContent.put("Sequence", this.roundNumber);
        msgContent.put("LinkStates", linkStates);
        Message<TYPES, HashMap<String, Serializable>> msg = new Message<>(msgType, msgContent);
        broadcast(msg, -1);
    }

    @Override
    public void run() {

        this.roundNumber += 1;

        linkStateRound();

        // signal to manager that this node has finished its round.
        this.finishRoundSignal.release();
    }
    private void linkStateRound() {
        // create the local link state of the node
        HashSet<Pair<Pair<Integer, Integer>, Double>> linkStates = createLinkStates();

        // broadcast the link state to all nodes in the network.
        initBroadcast(linkStates);

        // wait until link states from all other n - 1 nodes have been received
        try {
            listenerSignal.acquire();
            // System.out.println("node number " + nodeID + " has received " + (this.numOfNodes - 1) + " messages");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // the ClientHandlers update the node's routing table whenever they receive a message,
        // and then they release the 'listenerSignal' semaphore to signal it to the node.
        // Thus, after acquiring the lock we know that the node's adjacency matrix is completely up-to-date,
        // so we can finish here.
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
