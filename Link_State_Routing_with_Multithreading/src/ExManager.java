import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class ExManager {
    private final String path;
    private int numOfNodes;
    private HashMap<Integer, Node> nodes;
    public int roundNumber;

    /**
     * The class constructor only saves the path to the input file.
     * The function read_txt actually reads the input and creates the graph.
     * @param path - the path to the file from which to read the input
     */
    public ExManager(String path) {

        this.path = path;
        this.roundNumber = 0;
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

            //stop reading when seeing "stop" string
            if(line.contains("stop")) { break; }

            String[] node_parameters = line.split(" ");
            int nodeId = Integer.parseInt(node_parameters[0]);
            HashMap<Integer, HashMap<String, Number>> NeighborsTable = new HashMap<>();
            for (int i = 1; i < node_parameters.length; i += 4) {

                // parse the file string info of this label
                int neighborId = Integer.parseInt(node_parameters[i]);
                double linkWeight = Double.parseDouble(node_parameters[i + 1]);
                int sendPort = Integer.parseInt(node_parameters[i + 2]);
                int listenPort = Integer.parseInt(node_parameters[i + 3]);

                // add it to the node's NeighborsTable
                HashMap<String, Number> nodeAttributes = new HashMap<>();
                nodeAttributes.put("weight", linkWeight);
                nodeAttributes.put("send port", sendPort);
                nodeAttributes.put("listen port", listenPort);
                NeighborsTable.put(neighborId, nodeAttributes);
            }
            this.nodes.put(nodeId, new Node(nodeId, this.numOfNodes, NeighborsTable));
        }
    }

    /**
     * prepares to start the link-state routing algorithm round.
     */
    private CountDownLatch prepareToStartRound() {
        // create a semaphore for the nodes to signal when they've finished the round
        // and instruct them to start listening to their neighbor nodes.
        CountDownLatch finishRoundSignal = new CountDownLatch(this.numOfNodes);
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.nodes.get(id).setFinishRoundSignal(finishRoundSignal);
            this.nodes.get(id).establishConnections();
        }

        return finishRoundSignal;
    }
    /**
     * the entire link-state routing algorithm.
     */
    public void start() {

        this.roundNumber += 1;

        CountDownLatch finishRoundSignal = prepareToStartRound();

        // start the round by creating thread instances of the nodes for the current round and calling "start".
        HashMap<Integer, Thread> nodeThreads = new HashMap<>();
        for (int id = 1; id <= this.numOfNodes; id++) {
            Thread nodeThread = new Thread(this.nodes.get(id));
            nodeThreads.put(id, nodeThread);
            nodeThread.start();
        }

        // wait until all nodes signal that they are finished running the link state round locally.
        try {
            finishRoundSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // wait until all node threads are finished.
        for (int id = 1; id <= this.numOfNodes; id++) {
            try {
                nodeThreads.get(id).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        prepareToFinishRound();

        System.out.println("==================== Finished Round " + roundNumber + " ====================");
    }

    /**
     * prepares to finish the link-state routing algorithm round.
     */
    private void prepareToFinishRound() {

        // once all node threads are finished,
        // we know that they all received the link states from all other nodes in the network,
        // so we can safely instruct them to stop listening to their neighbor nodes before exiting.
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.nodes.get(id).terminateConnections();
        }
    }


    public void terminate(){
        // your code here
    }

}
