import java.net.*;
import java.util.*;
import java.io.*;

public class Node extends Thread implements Serializable {

    private final int address;
    private final int num_of_nodes;
    private final int port;
    private HashMap<Integer, Pair<String, Number>> RoutingTable;
    public HashMap<Integer, ? extends Serializable> neighbors;

    /**
     * Construct a single Node in the network.
     * @param address - a unique ID number for this node, ranging between 1 and the number of nodes in the network.
     * @param num_of_nodes - the number of nodes in the network (this implementation is non-uniform)
     * @param port - the node port address.
     * @param neighbors - the initial routing table of this node.
     */
    public Node(int address, int num_of_nodes, int port, HashMap<Integer, Pair<String, ?>[]> neighbors) {
        this.address = address;
        this.num_of_nodes = num_of_nodes;
        this.port = port;
        this.neighbors = neighbors;
    }

    /**
     *
     * @return - the node's unique ID number, between 1 and num_of_nodes.
     */
    public int getAddress() {
        return address;
    }

    /**
     *
     * @return - the total number of nodes in the network.
     */
    public int getNum_of_nodes() {
        return num_of_nodes;
    }

    /**
     *
     * @return - the node's port address.
     */
    public int getPort() {
        return port;
    }

    @Override
    public void run() {
        // for (u : neighbors) {
        //      updateEdge(u);
        // }
        // Pair<Pair<Integer, Integer>, Double> link = new Pair<>();
        // for (u : neighbors) {
        //      link.add(new Pair<Pair<Integer, Integer>, Double>(new Pair<Integer, Integer>(u, address), u["weight"]));
        // }
        // broadcast(link);
        // wait()  // until "notify" using an "Event"
        // Graph g = new Graph(this);
        // RoutingTable = g.shortestPaths()
    }
    /**
     * Print the current routing table of this node.
     */
    public void print_graph() {}
}
