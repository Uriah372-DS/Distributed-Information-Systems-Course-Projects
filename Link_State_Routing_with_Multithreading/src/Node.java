import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;  // still from java.util

public class Node extends Thread {

    private final int nodeID;
    private final int numOfNodes;
    private final HashMap<Integer, PortListener> portListeners;
    public HashMap<Integer, HashMap<String, Number>> RoutingTable;
    public HashMap<Integer, HashMap<String, Number>> NeighborsTable;
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

        this.finishRoundGlobal  = finishRoundGlobal;

        // initialize a port listener for every neighbor to establish communication
        portListeners = new HashMap<>();
        for (int neighborId : this.NeighborsTable.keySet()) {
            int neighborPort = (Integer)(this.NeighborsTable.get(neighborId).get("listen port"));
            this.portListeners.put(neighborId, new PortListener(neighborPort, this, finishRoundLocal));
        }

        // use a semaphore to make the node thread wait until it receives a message from all of its neighbors.
        this.finishRoundLocal = new Semaphore(this.numOfNodes - 1);

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
    public int getNodeID() {
        return nodeID;
    }

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

    @Override
    public void run() {
        // start the PortListener threads
        for (PortListener listener : portListeners.values()) {
            listener.start();
        }

        linkStateRound();
        // HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates = createLinkStates();
        // broadcast(link);
        // wait()  // until "notify" using an "Event"
        // Graph g = new Graph(this);
        // RoutingTable = g.shortestPaths()

        // stop the PortListener threads
        for (PortListener listener : portListeners.values()) {
            listener.stopListening();
        }
        this.finishRoundGlobal.release();
    }

    public void broadcast(HashSet<Pair<Pair<Integer, Integer>, Number>> linkStates) {
        // send link state packet to all neighbors
        for (Integer neighborId : NeighborsTable.keySet()) {
            int sendPort = (Integer)(NeighborsTable.get(neighborId).get("send port"));
            try {
                Socket socket = new Socket("localhost", sendPort);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                // should use the Message class here!!! easier parsing with it:)
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
