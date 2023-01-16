import java.io.*;
import java.net.*;
import java.util.concurrent.*;

class PortListener extends Thread implements Closeable {
    public int port;
    private final Node node;
    private final ConcurrentLinkedDeque<ClientHandler> clientHandlers;
    private final CountDownLatch initiatedAndListening;
    private final CountDownLatch signalClosed;
    private ServerSocket serverSocket;

    PortListener(int port, Node node, CountDownLatch initiatedAndListening, CountDownLatch signalClosed) {
        this.port = port;
        this.node = node;
        this.clientHandlers = new ConcurrentLinkedDeque<>();
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
        for (ClientHandler clientHandler : clientHandlers) { clientHandler.close(); }
        //noinspection LoopConditionNotUpdatedInsideLoop,StatementWithEmptyBody
        while (!clientHandlers.isEmpty());
        signalClosed.countDown();
    }

    @Override
    public void run() {
        serverSocket = null;
        ClientHandler clientHandler = null;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(this.port));
            this.initiatedAndListening.countDown();

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSocket = serverSocket.accept();  // blocking

                clientHandler = new ClientHandler(clientSocket, this.node, clientHandlers);
                clientHandlers.add(clientHandler);
                clientHandler.start();
            }
        }
        catch (SocketException e) {
            System.out.println("ServerSocket threw SocketException at port "
                    + this.port + " in node " + node.nodeID);
        }
        catch (InterruptedIOException e) {
            System.out.println("ServerSocket.accept() threw InterruptedIOException at port "
                    + this.port + " in node " + this.node.nodeID);
        }
        catch (IOException e) {
            System.out.println("ServerSocket threw IOException at port "
                    + this.port + " in node " + this.node.nodeID);
            e.printStackTrace();
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
    final ConcurrentLinkedDeque<ClientHandler> clientHandlers;

    ClientHandler(Socket socket, Node node, ConcurrentLinkedDeque<ClientHandler> clientHandlers) {
        this.socket = socket;
        this.node = node;
        this.clientHandlers = clientHandlers;
    }

    public void run() {
        try {
            ObjectInputStream input = new ObjectInputStream(new DataInputStream(this.socket.getInputStream()));
            // creating an ObjectOutputStream is mandatory for ObjectInputStream validity check
            new ObjectOutputStream(new DataOutputStream(this.socket.getOutputStream()));
            Object msgObject = input.readObject();
            Message msg = (Message) msgObject;
            this.node.floodingWithSequenceNumbers(msg, socket.getPort());
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
        } finally {
            clientHandlers.remove(this);
        }
    }
}

class WTFException extends RuntimeException {
    public WTFException(String errorMessage) {
        super(errorMessage);
    }
}
