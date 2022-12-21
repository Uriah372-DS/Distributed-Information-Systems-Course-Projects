import java.net.*;
import java.util.*;
import java.io.*;

enum TYPES {BROADCAST, ROUTING, PRIVATE, ACKNOWLEDGEMENT}

public class Message<K, V> extends Pair<K, V> implements Serializable, Runnable {

    public K type;
    private V content;

    public Message(K type, V content) {
        super(type, content);
        this.type = type;
        this.content = content;
    }

    /**
     * return the content of this completely safe and un-secure message. why, DO YOU HAVE ANYTHING TO HIDE?
     * @return - value of "content" attribute of this message instance.
     */
    public V getContent() {
        if (type == TYPES.BROADCAST || type == TYPES.ROUTING || type == TYPES.ACKNOWLEDGEMENT) return content;
        else return null;
    }

    /**
     *
     * @return - True if content contains an "address" field, False otherwise.
     */
    private Boolean hasAddress() {
        return Arrays.stream(content.getClass().getFields()).anyMatch(f -> f.getName().equals("address"));
    }

    /**
     * Returns the content of private messages only if the recipient node's address is provided.
     * if a wrong address is provided the message will SELF-DESTRUCT! ¯\_( ͡❛ ͜ʖ ͡❛)_/¯
     * @param nodeAddress - the recipient node address.
     * @return -
     */
    public V getContent(int nodeAddress) {
        if ((type == TYPES.BROADCAST))
            return content;
        else
            try {
                if (type == TYPES.PRIVATE
                        && hasAddress()
                        && content.getClass().getField("address").get(content).equals(nodeAddress)) {
                    return content;

            } else {
                    System.out.println("Message: type: " + type + ", content: " + content +": " +
                            "I was given wrong address. I will self-destruct now :(");
                    type = null;
                    content = null;
                    return null;
                }

        } catch (Exception ignored) {}  // covered by hasAddress function
        return null;
    }

    public void setContent(V content) {
        this.content = content;
    }

    public void run() {

    }
}