import java.net.*;
import java.util.*;
import java.io.*;


public class WeightedGraph {

    private Set<Vertex> vertices = new HashSet<>();

    public void addVertex(Vertex v) {
        vertices.add(v);
    }

    public static WeightedGraph calculateShortestPathFromSource(WeightedGraph graph, Vertex source) {
        source.setDistance(0);

        Set<Vertex> settledNodes = new HashSet<>();
        Set<Vertex> unsettledNodes = new HashSet<>();

        unsettledNodes.add(source);

        while (unsettledNodes.size() != 0) {
            Vertex currentNode = getLowestDistanceVertex(unsettledNodes);
            unsettledNodes.remove(currentNode);
            for (Map.Entry<Vertex, Integer> adjacencyPair : currentNode.getAdjacentVertices().entrySet()) {
                Vertex adjacentNode = adjacencyPair.getKey();
                Integer edgeWeight = adjacencyPair.getValue();
                if (!settledNodes.contains(adjacentNode)) {
                    calculateMinimumDistance(adjacentNode, edgeWeight, currentNode);
                    unsettledNodes.add(adjacentNode);
                }
            }
            settledNodes.add(currentNode);
        }
        return graph;
    }

    private static Vertex getLowestDistanceVertex(Set<Vertex> unsettledNodes) {
        Vertex lowestDistanceNode = null;
        int lowestDistance = Integer.MAX_VALUE;
        for (Vertex node: unsettledNodes) {
            int nodeDistance = node.getDistance();
            if (nodeDistance < lowestDistance) {
                lowestDistance = nodeDistance;
                lowestDistanceNode = node;
            }
        }
        return lowestDistanceNode;
    }

    private static void calculateMinimumDistance(Vertex evaluationNode,
                                                 Integer edgeWeigh, Vertex sourceNode) {
        Integer sourceDistance = sourceNode.getDistance();
        if (sourceDistance + edgeWeigh < evaluationNode.getDistance()) {
            evaluationNode.setDistance(sourceDistance + edgeWeigh);
            LinkedList<Vertex> shortestPath = new LinkedList<>(sourceNode.getShortestPath());
            shortestPath.add(sourceNode);
            evaluationNode.setShortestPath(shortestPath);
        }
    }

    public static void main(String[] args) {
        Vertex nodeA = new Vertex("A");
        Vertex nodeB = new Vertex("B");
        Vertex nodeC = new Vertex("C");
        Vertex nodeD = new Vertex("D");
        Vertex nodeE = new Vertex("E");
        Vertex nodeF = new Vertex("F");

        nodeA.addDestination(nodeB, 10);
        nodeA.addDestination(nodeC, 15);

        nodeB.addDestination(nodeD, 12);
        nodeB.addDestination(nodeF, 15);

        nodeC.addDestination(nodeE, 10);

        nodeD.addDestination(nodeE, 2);
        nodeD.addDestination(nodeF, 1);

        nodeF.addDestination(nodeE, 5);

        WeightedGraph graph = new WeightedGraph();

        graph.addVertex(nodeA);
        graph.addVertex(nodeB);
        graph.addVertex(nodeC);
        graph.addVertex(nodeD);
        graph.addVertex(nodeE);
        graph.addVertex(nodeF);

        graph = calculateShortestPathFromSource(graph, nodeA);
    }
}


class Vertex {

    private final String name;

    private List<Vertex> shortestPath = new LinkedList<>();

    private Integer distance = Integer.MAX_VALUE;

    Map<Vertex, Integer> adjacentVertices = new HashMap<>();

    public void addDestination(Vertex destination, int distance) {
        adjacentVertices.put(destination, distance);
    }

    public Vertex(String name) {
        this.name = name;
    }

    // getters and setters
    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public void setShortestPath(List<Vertex> shortestPath) {
        this.shortestPath = shortestPath;
    }

    public Integer getDistance() {
        return distance;
    }

    public List<Vertex> getShortestPath() {
        return shortestPath;
    }

    public Map<Vertex, Integer> getAdjacentVertices() {
        return adjacentVertices;
    }
}

