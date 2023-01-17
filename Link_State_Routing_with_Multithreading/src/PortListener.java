import java.io.*;
import java.net.*;
import java.util.Objects;
import java.util.concurrent.*;

class PortListener extends Thread implements Closeable {
    public int port;
    private final Node node;
    private final CountDownLatch initiatedAndListening;
    private final CountDownLatch signalClosed;
    private ServerSocket serverSocket;

    PortListener(int port, Node node, CountDownLatch initiatedAndListening, CountDownLatch signalClosed) {
        this.port = port;
        this.node = node;
//        this.clientHandlers = new ConcurrentLinkedDeque<>();
        this.initiatedAndListening = initiatedAndListening;
        this.signalClosed = signalClosed;
    }

    public void close(){

        this.interrupt();  // always permitted
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException ignore) {
            System.out.println("IOException while closing server socket in port listener "
                    + port + " in node " + node.nodeID);
        }

        signalClosed.countDown();
    }

    @Override
    public void run() {
//        Thread.currentThread().setPriority(7);
        serverSocket = null;
        ClientHandler clientHandler = null;
        try {
            serverSocket = new ServerSocket();
        } catch (IOException e) {
            System.out.println("Random IOException while creating serverSocket at port "
                    + port + " in node " + node.nodeID);
            close();
        }
        try {
            serverSocket.setReuseAddress(true);
        } catch (SocketException e) {
            System.out.println("SocketException while calling serverSocket.setReuseAddress(true) at port "
                    + port + " in node " + node.nodeID);
        }
        try {
            serverSocket.bind(new InetSocketAddress(this.port));
        } catch (IOException e) {
            System.out.println("IOException while calling serverSocket.bind(new InetSocketAddress(this.port)) at port "
                    + port + " in node " + node.nodeID);
            e.printStackTrace();
        }
        //noinspection StatementWithEmptyBody
        while (serverSocket.isClosed()) {}
        initiatedAndListening.countDown();  // signal the node.
        try {
            while (!Thread.currentThread().isInterrupted() &&
                    !serverSocket.isClosed() &&
                    serverSocket.isBound()) {

                Socket clientSocket = serverSocket.accept();  // blocking

                clientHandler = new ClientHandler(clientSocket, this.node);
//                clientHandlers.add(clientHandler);
                clientHandler.start();
            }
        }
        catch (IOException e) {

            if (!Objects.equals(e.getMessage(), "Socket closed"))
                System.out.println("ServerSocket.accept() threw " + e.getClass().getName() + " at port "
                        + this.port + " in node " + this.node.nodeID + " - Exception Message: " + e.getMessage());
        }
        finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed())
                    serverSocket.close();
                if (clientHandler != null && clientHandler.isAlive())
                    clientHandler.interrupt();
            }
            catch (IOException e) {
                System.out.println("IOException while trying to close serverSocket at port "
                        + this.port + " in node " + this.node.nodeID);
                e.printStackTrace();
            }
        }
    }
}

class ClientHandler extends Thread {
    final Socket socket;
    final Node node;
//    final ConcurrentLinkedDeque<ClientHandler> clientHandlers;

    ClientHandler(Socket socket, Node node) {
        this.socket = socket;
        this.node = node;
//        this.clientHandlers = clientHandlers;
    }

    public void run() {
        ObjectInputStream input;
        try {
            input = new ObjectInputStream(this.socket.getInputStream());
            // creating an ObjectOutputStream is mandatory for ObjectInputStream validity check
            new ObjectOutputStream(this.socket.getOutputStream());
            // process the message
            Message msg = (Message) input.readObject();
            if (msg.getType() == TYPES.BROADCAST) {
                this.node.floodingWithSequenceNumbers(msg, socket.getPort());
            }
        } catch (NumberFormatException | EOFException | SocketException ignore) {

        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public void close() {
        this.interrupt();
        try {
            this.socket.close();
        } catch (IOException e) {
            System.out.println("IOException while trying to close socket in ClientHandler");
            e.printStackTrace();
        }
    }
}
