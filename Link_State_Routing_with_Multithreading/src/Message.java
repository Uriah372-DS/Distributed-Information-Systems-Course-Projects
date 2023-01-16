import java.util.*;
import java.io.*;

enum TYPES {BROADCAST, PRIVATE, ACKNOWLEDGEMENT}

public class Message implements Serializable {

    public TYPES type;
    private HashMap<String, Serializable> content;

    public Message(TYPES type, HashMap<String, Serializable> content) {
        this.type = type;
        this.content = content;
    }

    /**
     * return the content of this completely safe and un-secure message. why, DO YOU HAVE ANYTHING TO HIDE?
     * @return - value of "content" attribute of this message instance.
     */
    public HashMap<String, Serializable> getContent() {
        if (type == TYPES.BROADCAST || type == TYPES.ACKNOWLEDGEMENT) return content;
        else return null;
    }

    /**
     * @return - True if content contains an "address" field, False otherwise.
     */
    private Boolean hasAddress() {
        return Arrays.stream(content.getClass().getFields()).anyMatch(f -> f.getName().equals("address"));
    }

    /**
     * Returns the content of private messages only if the recipient node's address is provided.
     * if a wrong address is provided the message will SELF-DESTRUCT! ¯\_( ͡❛ ͜ʖ ͡❛)_/¯
     * @param nodeAddress - the recipient node address.
     * @return - the message content, hopefully...
     */
    @SuppressWarnings("unused")
    public HashMap<String, Serializable> getContent(int nodeAddress) {
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
}
