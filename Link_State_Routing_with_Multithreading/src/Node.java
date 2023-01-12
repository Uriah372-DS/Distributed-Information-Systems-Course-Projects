import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;  // still from java.util

public class Node extends Thread {

    private final int nodeID;
    private final int numOfNodes;
    private boolean establishedConnections;
    private final HashMap<Integer, PortListener> portListeners;
    public HashMap<Integer, HashMap<String, Number>> RoutingTable;
    public HashMap<Integer, HashMap<String, Number>> NeighborsTable;
    public HashSet<Integer> updatedNodes;
    private Semaphore finishRoundGlobal;
    private Semaphore finishRoundLocal;

    /**
     * Construct a single Node in the network.
     * @param nodeID - a unique ID number for this node, ranging between 1 and the number of nodes in the network.
     * @param numOfNodes - the number of nodes in the network (this implementation is non-uniform)
     * @param NeighborsTable - the initial routing table of this node.
     */
    public Node(int nodeID,
                int numOfNodes,
                HashMap<Integer, HashMap<String, Number>> NeighborsTable,
                Semaphore finishRoundGlobal) {

        this.nodeID = nodeID;

        this.numOfNodes = numOfNodes;

        this.NeighborsTable = NeighborsTable;

        this.updatedNodes = new HashSet<>();

        this.establishedConnections = false;

        this.finishRoundGlobal  = finishRoundGlobal;

        // use a semaphore to make the node thread wait until it receives a message from all other nodes.
        this.finishRoundLocal = new Semaphore(this.numOfNodes - 1);

        // initialize a port listener for every neighbor to establish communication
        portListeners = new HashMap<>();
        for (int neighborId : this.NeighborsTable.keySet()) {
            int neighborPort = (Integer)(this.NeighborsTable.get(neighborId).get("listen port"));
            this.portListeners.put(neighborId, new PortListener(neighborPort, this, finishRoundLocal));
        }

        // initialize the fields of the routing table null, assume they will be filled later in the run.
        this.RoutingTable = new HashMap<>();
        for (int id = 1; id <= this.numOfNodes; id++) {
            HashMap<String, Number> nodeAttributes = new HashMap<>();
            if (NeighborsTable.containsKey(id)) {
                nodeAttributes.put("next", id);
                nodeAttributes.put("distance", NeighborsTable.get(id).get("weight"));
            }
            else {
                nodeAttributes.put("next", null);
                nodeAttributes.put("distance", null);
            }
            this.RoutingTable.put(id, nodeAttributes);
        }
    }

    public void updateWeight(int neighborId, Number newWeight) {
        NeighborsTable.get(neighborId).put("weight", newWeight);
        RoutingTable.get(neighborId).put("distance", newWeight);
    }

    /**
     *
     * @return - the node's unique ID number, between 1 and numOfNodes.
     */
    public int getNodeID() { return nodeID; }

    public HashSet<Pair<Pair<Integer, Integer>, Number>> createLinkStates() {

        // create new empty set of pairs
        HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates = new HashSet<>();

        // fill the set with edges and their weights
        for (int neighborId : this.NeighborsTable.keySet()) {
            linkStates.add(new Pair<Pair<Integer, Integer>, Number>(
                    new Pair<Integer, Integer>(nodeID, neighborId),
                    this.NeighborsTable.get(neighborId).get("weight")));
        }

        // return a set of the form {<(u, v), w(u, v)> | u in N(v)}
        return linkStates;
    }

    public void establishConnections() {
        for (PortListener listener : portListeners.values()) { listener.startListening(); }
        this.establishedConnections = true;
    }

    public void terminateConnections() {
        for (PortListener listener : portListeners.values()) { listener.stopListening(); }
        this.establishedConnections = true;
    }

    @Override
    public void run() {
        if (!this.establishedConnections) {
            System.out.println("node " + this.nodeID + " connections not established!");
            return;
        }

        linkStateRound();
        // HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates = createLinkStates();
        // broadcast(linkStates);
        // wait();
        // Graph g = new Graph(this);
        // RoutingTable = g.shortestPaths();

        // stop the PortListener threads
        terminateConnections();
        this.finishRoundGlobal.release();
    }

    private void broadcast(HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates) {

        // send link state packet to all neighbors
        for (Integer neighborId : NeighborsTable.keySet()) {
            // create a Message instance to broadcast to this node
            TYPES msgType = TYPES.BROADCAST;
            HashMap<String, Serializable> msgContent = new HashMap<>();
            msgContent.put("Origin", this.nodeID);
            msgContent.put("HopCounter", this.numOfNodes - 1);
            msgContent.put("LinkStates", linkStates);
            Message<TYPES, HashMap<String, Serializable>> msg = new Message<>(msgType, msgContent);

            // send link state message to this neighbor
            int sendPort = (Integer)(NeighborsTable.get(neighborId).get("send port"));
            try {
                Socket socket = new Socket("localhost", sendPort);
                ObjectOutputStream output = new ObjectOutputStream(new DataOutputStream(socket.getOutputStream()));
                output.writeObject(msg);
                output.flush();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void linkStateRound() {
        HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates = createLinkStates();
        broadcast(linkStates);

        // wait until link state packets from all other nodes have been received
        try {
            finishRoundLocal.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Print the current routing table of this node.
     */
    public void print_graph() {}
}
