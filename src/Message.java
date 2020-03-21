import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {
    private Integer intendedGroupID;
    private Integer senderID; // So we can make it blue for you to indicate that you sent it.
    private String message;
    private List<byte[]> images;

    @Override
    public String toString(){
        return message.trim().length() > 0 ? message : "[Image]";
    }

    public Integer getIntendedGroupID(){
        return intendedGroupID;
    }

    public Integer getSenderID() {
        return senderID;
    }

    public String getMessage(){
        return message;
    }

    public List<byte[]> getImages(){
        return images;
    }

    public Message(Integer intendedGroupID, Integer senderID, String message, List<byte[]> images) {
        this.intendedGroupID = intendedGroupID;
        this.senderID = senderID;
        this.message = message;
        this.images = images;
    }
}