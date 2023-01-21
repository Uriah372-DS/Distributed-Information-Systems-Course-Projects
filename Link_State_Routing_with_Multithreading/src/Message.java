import java.util.*;
import java.io.*;

enum TYPES {BROADCAST, ACKNOWLEDGEMENT}

/**
 * This class was used to abstract the data types that are handled and sent through sockets, for simplicity.
 * Each message has a type and a content fields, which are internally stored as key and value in a Pair.
 * This is the only class that is sent or received through a socket.
 */
public class Message implements Serializable {

    private final Pair<TYPES, HashMap<String, Serializable>> info;

    public Message(TYPES type, HashMap<String, Serializable> content) {
        this.info = new Pair<>(type, content);
    }

    /** returns the contents of this message. */
    public HashMap<String, Serializable> getContent() {
        if (info.getKey() == TYPES.BROADCAST || info.getKey() == TYPES.ACKNOWLEDGEMENT)
            return info.getValue();
        return null;
    }
}
