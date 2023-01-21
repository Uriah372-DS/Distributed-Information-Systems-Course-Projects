import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * The ExManager is used as a global Synchronizer of the entire network.
 * It is used to set up the network, start and stop communication between the nodes, and terminate the simulation.
 * Every round in the link state routing governed by the ExManager works in 3 phases:
 * 1. prepareToStart()
 * 2. run the round
 * 3. prepareToFinish()
 * In the 1st phase, all nodes are instructed by the ExManager to start "listening" for messages from their neighbors.
 * This part is executed sequentially to avoid any node trying to send messages to nodes that aren't listening yet,
 * which can cause missed messages and at worst case a runtime error.
 * In the 2nd phase, the nodes are instructed to start the actual algorithm. this implies in the case of link state
 * routing that the node will broadcast their link states to all nodes in the network.
 * The 3rd part will only be called inside the added terminate() method, for easier optimization,
 * and in it the ExManager will instruct the nodes to immediately stop listening to for messages.
 * The synchronization between the phases in done by using a CountDownLatch. The ExManager sends a latch to the nodes
 * in the first phase, and they use it to signal the ExManager that they have completed the 2nd phase
 * and are ready to finish the round.
 * The ExManager will block until all the nodes signal that they are finished,
 * and they can print the correct output.
 * This doesn't imply that the nodes have any information on the entire network,
 * but because the algorithm is supposed to be synchronized then a synchronizer is mandatory to ensure it.
 */
public class ExManager {
    private final String path;
    private int numOfNodes;
    private HashMap<Integer, Node> nodes;
    public int roundNumber;

    /**
     * The class constructor only saves the path to the input file.
     * The function read_txt actually reads the input and creates the graph.
     */
    public ExManager(String path) {
        this.path = path;
        this.roundNumber = 0;
    }

    /** returns the node with this id. */
    public Node getNode(int id) { return nodes.get(id); }

    /** Returns the number of nodes in the network. */
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

    /** read text from given path and create the nodes in the network. */
    public void read_txt() throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(path));
        this.numOfNodes = Integer.parseInt(scanner.nextLine());
        this.nodes = new HashMap<>();
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.contains("stop")) { break; }

            String[] node_parameters = line.split(" ");
            int nodeId = Integer.parseInt(node_parameters[0]);
            HashMap<Integer, HashMap<String, Number>> neighborsInfo = new HashMap<>();
            for (int i = 1; i < node_parameters.length; i += 4) {

                int neighborId = Integer.parseInt(node_parameters[i]);
                double weight = Double.parseDouble(node_parameters[i + 1]);
                int sendPort = Integer.parseInt(node_parameters[i + 2]);
                int listenPort = Integer.parseInt(node_parameters[i + 3]);

                HashMap<String, Number> nodeAttributes = new HashMap<>();
                nodeAttributes.put("weight", weight);
                nodeAttributes.put("send port", sendPort);
                nodeAttributes.put("listen port", listenPort);
                neighborsInfo.put(neighborId, nodeAttributes);
            }
            this.nodes.put(nodeId, new Node(nodeId, this.numOfNodes, neighborsInfo));
        }
    }

    /** 1st phase - prepares to start the link-state routing algorithm round. */
    private CountDownLatch prepareToStart() {
        CountDownLatch finishRoundSignal = new CountDownLatch(this.numOfNodes);
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.nodes.get(id).setFinishRoundSignal(finishRoundSignal);
            this.nodes.get(id).startListening();
        }
        return finishRoundSignal;
    }

    /** runs a single round of the routing algorithm. */
    public void start() {
        this.roundNumber += 1;
        CountDownLatch finishRoundSignal = prepareToStart();

        // 2nd phase - start the round by creating thread instances of the nodes for the current round
        // and calling "start".
        HashMap<Integer, Thread> nodeThreads = new HashMap<>();
        for (int id = 1; id <= this.numOfNodes; id++) {
            Thread nodeThread = new Thread(this.nodes.get(id));
            nodeThreads.put(id, nodeThread);
            nodeThread.start();
        }

        // wait until all nodes signal that they are finished running the link state round locally.
        try {
            finishRoundSignal.await();
        }
        catch (InterruptedException ignored) {}

        // wait until all node threads are finished - this can take a moment because of OS thread priorities.
        for (int id = 1; id <= this.numOfNodes; id++) {
            try {
                nodeThreads.get(id).join();
            } catch (InterruptedException ignored) {}
        }
    }

    /** closes all sockets and servers and such. */
    private void prepareToFinish() {
        CountDownLatch signal = new CountDownLatch(numOfNodes);
        for (int id = 1; id <= this.numOfNodes; id++) {
            this.nodes.get(id).stopListening(signal);
        }
        try {
            signal.await();
        } catch (InterruptedException ignored) {}
    }

    public void terminate() {
        prepareToFinish();
    }
}
