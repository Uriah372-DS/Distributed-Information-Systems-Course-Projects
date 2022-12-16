import java.net.*;
import java.util.*;
import java.io.*;

public class Node extends Thread {

    private final int nodeId;
    private final int num_of_nodes;
    private int[][] adj;

    public Node(int nodeId, int num_of_nodes, int[][] adj) {
        this.nodeId = nodeId;
        this.num_of_nodes = num_of_nodes;
        this.adj = adj;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getNum_of_nodes() {
        return num_of_nodes;
    }

    public int[][] getAdj() {
        return adj;
    }

    public void setAdj(int[][] adj) {
        this.adj = adj;
    }

    public void print_graph() {}
}
