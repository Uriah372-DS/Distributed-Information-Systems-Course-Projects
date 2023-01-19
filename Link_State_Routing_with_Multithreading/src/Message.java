import java.util.*;
import java.io.*;

enum TYPES {BROADCAST, ACKNOWLEDGEMENT, PRIVATE}

public class Message implements Serializable {

    private final Pair<TYPES, HashMap<String, Serializable>> info;

    public Message(TYPES type, HashMap<String, Serializable> content) {
        this.info = new Pair<>(type, content);
    }

    public TYPES getType() {
        return info.getKey();
    }

    /**
     * return the content of this completely safe and un-secure message. why, DO YOU HAVE ANYTHING TO HIDE?
     * @return - value of "content" attribute of this message instance.
     */
    public HashMap<String, Serializable> getContent() {
        if (info.getKey() == TYPES.BROADCAST || info.getKey() == TYPES.ACKNOWLEDGEMENT) return info.getValue();
        else return null;
    }

    public static Message copy(Message msg) {
        TYPES copyMsgType = msg.getType();
        if (copyMsgType == TYPES.BROADCAST || copyMsgType == TYPES.ACKNOWLEDGEMENT) {
            HashMap<String, Serializable> copyMsgContent = new HashMap<>();
            HashMap<String, Serializable> msgContent = msg.getContent();

            for (String key : msgContent.keySet()) {
                copyMsgContent.put(key, msgContent.get(key));
            }
            return new Message(copyMsgType, copyMsgContent);
        }
        return null;
    }

    /**
     * Returns the content of private messages only if the recipient node's address is provided.
     * if a wrong address is provided the message will SELF-DESTRUCT! ¯\_( ͡❛ ͜ʖ ͡❛)_/¯
     * @param nodeAddress - the recipient node address.
     * @return - the message content, hopefully...
     */
    @SuppressWarnings("unused")
    public HashMap<String, Serializable> getContent(int nodeAddress) {
        if (info.getKey() == TYPES.BROADCAST)
            return info.getValue();
        else
            try {
                if (info.getKey() == TYPES.PRIVATE
                        && info.getValue().get("Address").equals(nodeAddress)) {
                    return info.getValue();

                } else {
                    System.out.println("Message: type - " + info.getKey() + ", content: " + info.getValue() + ": " +
                            "I was given wrong address. I will self-destruct now :(");
                    info.setValue(null);
                    return null;
                }

            } catch (Exception ignored) {}  // covered by hasAddress function
        return null;
    }
}
