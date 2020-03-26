import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Group implements Serializable {
    private Integer groupID;
    public List<Message> messages;
    private List<Integer> clientIDs; // Each group needs to have its own client list in order for clients to know to
    // add/remove this group from their list. Group A has client list B and each client in B has group A in their group list.
    private String groupName;

    public Group(List<Integer> clientIDs, String groupName){
        groupID = -1; // All groups initialized with ID of -1. The server then sees this and allocates the next available ID.
        messages = new ArrayList<Message>();
        this.clientIDs = clientIDs;
        this.groupName = groupName;
    }

    public List<Integer> getClientIDs(){
        return clientIDs;
    }
    public void setClientIDs(List<Integer> clientIDs) {
        this.clientIDs = clientIDs;
    }

    public Integer getGroupID() {
        return groupID;
    }
    public void setGroupID(Integer groupID) {
        this.groupID = groupID;
    }

    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<Message> getMessages() {
        return messages;
    }
    public void addMessage(Message message){
        messages.add(message);
    }

    @Override
    public String toString(){
        return groupName + "(" + groupID + ") has " + clientIDs.toString();
    }
}
