import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ExManager {
    private final String path;
    private int num_of_nodes;
    private Node[] nodes;  // is this all I need here???

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
    public Node getNode(int id) {
        return nodes[id + 1];
    }

    /**
     * Returns the number of nodes in the network.
     * @return - num_of_nodes
     */
    public int getNum_of_nodes() {
        return this.num_of_nodes;
    }

    /**
     * update the weight of the link between the nodes with the given id's ON BOTH NODES.
     * @param id1 - first node id
     * @param id2 - second node id
     * @param weight - new weight of link between first and second nodes
     */
    public void update_edge(int id1, int id2, double weight) {
        //your code here
    }

    /**
     * read text from given path and create the network
     */
    public void read_txt() throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(path));
        // get number of nodes on first line:
        this.num_of_nodes = Integer.parseInt(scanner.nextLine());
        // scan nodes information on the rest of the input.
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.contains("stop")) { break; }
            String[] node_parameters = line.split(" ");
            int nodeId = Integer.parseInt(node_parameters[0]);
            for (int i = 1; i < node_parameters.length; i += 4) {
                int neighborId = Integer.parseInt(node_parameters[i]);
                int link_weight = Integer.parseInt(node_parameters[i + 1]);
                int sendPort = Integer.parseInt(node_parameters[i + 2]);
                int listenPort = Integer.parseInt(node_parameters[i + 3]);
            }

        }
    }

    /**
     * runs the link-state routing algorithm.
     */
    public void start() {
        // your code here
    }
}
