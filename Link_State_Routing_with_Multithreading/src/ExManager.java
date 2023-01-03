import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;  // still from java.util

public class ExManager {
    private final String path;
    private int numOfNodes;
    private HashMap<Integer, Node> nodes;
    private Semaphore finishRoundGlobal;

    /**
     * The class constructor only saves the path to the input file.
     * The function read_txt actually reads the input and creates the graph.
     * @param path - the path to the file from which to read the input
     */
    public ExManager(String path) {

        this.path = path;
    }

    public String getPath() {
        return path;
    }

    /**
     * returns the node with this id
     * @param id - Node id
     * @return - Node with this id
     */
    public Node getNode(int id) { return nodes.get(id); }

    /**
     * Returns the number of nodes in the network.
     * @return - num_of_nodes
     */
    public int getNum_of_nodes() {
        return this.numOfNodes;
    }

    /**
     * update the weight of the link between the nodes with the given id's ON BOTH NODES.
     * @param id1 - first node id
     * @param id2 - second node id
     * @param weight - new weight of link between first and second nodes
     */
    public void update_edge(int id1, int id2, double weight) {
        this.nodes.get(id1).updateWeight(id2, weight);
        this.nodes.get(id2).updateWeight(id1, weight);
    }

    /**
     * read text from given path and create the network
     */
    public void read_txt() throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(path));
        // get number of nodes on first line:
        this.numOfNodes = Integer.parseInt(scanner.nextLine());
        this.nodes = new HashMap<>();
        // scan nodes information on the rest of the input.
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.contains("stop")) { break; }
            String[] node_parameters = line.split(" ");
            int nodeId = Integer.parseInt(node_parameters[0]);
            HashMap<Integer, HashMap<String, Number>> NeighborsTable = new HashMap<>();
            for (int i = 1; i < node_parameters.length; i += 4) {
                int neighborId = Integer.parseInt(node_parameters[i]);
                int linkWeight = Integer.parseInt(node_parameters[i + 1]);
                int sendPort = Integer.parseInt(node_parameters[i + 2]);
                int listenPort = Integer.parseInt(node_parameters[i + 3]);
                HashMap<String, Number> nodeAttributes = new HashMap<>();
                nodeAttributes.put("weight", linkWeight);
                nodeAttributes.put("send port", sendPort);
                nodeAttributes.put("listen port", listenPort);
                NeighborsTable.put(neighborId, nodeAttributes);
            }
            this.nodes.put(nodeId, new Node(nodeId, this.numOfNodes, NeighborsTable, finishRoundGlobal));
        }

        // can now initialize the semaphore condition to finish the global round
        this.finishRoundGlobal = new Semaphore(this.numOfNodes);
    }

    /**
     * runs the link-state routing algorithm.
     */
    public void start() {
        try {
            for (int id = 1; id <= this.numOfNodes; id++) {
                this.nodes.get(id).start();
            }

            for (int id = 1; id <= this.numOfNodes; id++) {
                this.nodes.get(id).join();
            }
        } catch (InterruptedException ignored) {}
    }

    public void terminate(){
        // your code here
    }

}
