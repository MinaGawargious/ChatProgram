import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class ClientMain implements Serializable {
    protected List<Integer> registeredUsers; // Since clients will be on different computers, it's fastest to keep local copies of registered users.
    protected int clientID;
    private View view; // Each client has a view he can update as he gets new messages, added to/removed from groups, etc.
    private List<Group> groups; // Each client needs to have their own group list in order to display the groups on the side view.
    private Socket socket;

    public List<Group> getGroups() {
        return groups;
    }

    public static void main(String[] args){
        new ClientMain();
    }

    public ClientMain() {
        clientID = -1; // Initial value to indicate ID not yet set.
        registeredUsers = new ArrayList<Integer>();
        groups = new ArrayList<Group>();
        try {
            view = new View(this);
            setSocket();

            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            ObjectOutputStream outputToServer = new ObjectOutputStream(outputStream);
            view.setConnections(outputToServer, inputStream, outputStream);
            // Make a thread to listen to the updates from the server.
            new Thread(new ServerHandler(inputStream)).start();
            while(this.clientID == -1){
                // Client connects to server. Input and output streams are established on both sides, but client waits until he gets a valid ID before sending ID to server.
                // ClientID set in ServerHandler thread, so yield CPU to allow for it to change.
                Thread.yield();
            }
            outputToServer.writeObject(this.clientID);
            outputToServer.flush();

            System.out.println("Created View.");
        } catch (IOException e) {
            System.out.println("Could not create socket to server.");
        }
    }

    private Group getGroupWithID(int groupID){
        for (Group group: groups){
            if(group.getGroupID() == groupID){
                return group;
            }
        }
        return null;
    }

    private void updateGroupList(Group group){
        Group myCopy = getGroupWithID(group.getGroupID());
        if(myCopy != null && !group.getClientIDs().contains(clientID)){ // Previously in group. Not anymore. Kick me out.
            groups.remove(myCopy);
            System.out.println("Kicked out. " + groups);
        }else if(myCopy != null && group.getClientIDs().contains(clientID)){ // Previously in group. Still in group. Update name and members list.
            myCopy.setGroupName(group.getGroupName());
            myCopy.setClientIDs(group.getClientIDs());
            System.out.println("Updated. " + groups);
        }else if (myCopy == null && group.getClientIDs().contains(clientID)){ // Not already in group. Added in.
            groups.add(group);
            System.out.println("Added. " + groups);
        }
    }

    // Observes input from the server.
    class ServerHandler implements Runnable{
        // We will read updates from the server coming in to the input stream, so we need an input stream to read from.
        ObjectInputStream inputFromServer;

        // Initializes input stream from server.
        public ServerHandler(InputStream inputFromServer){
            try {
                this.inputFromServer = new ObjectInputStream(inputFromServer);
            } catch (IOException e) {
                System.out.println("Could not create ObjectInputStream From ServerHandler");
            }
        }

        // Observes input from the server.
        @Override
        public void run() {
            try {
                Object dataFromServer = inputFromServer.readObject();
                while(dataFromServer != null){
                    if (dataFromServer instanceof List){ // Received registered users list.
                        registeredUsers = (ArrayList)dataFromServer;
                        if(view != null && view.getAddUsersStage() != null && view.getAddUsersStage().isShowing()){ // If I am in the middle of creating/updating a group, keep me up to date on available people.
                            // Remove the people no longer in the group
                            System.out.println("UPDATING");
                            view.getAddUsersStage().update();
                        }
                        System.out.println(registeredUsers);
                    }else if (dataFromServer instanceof Integer && clientID == -1){ // Received our new client ID.
                        clientID = (Integer)dataFromServer;
                        System.out.println("NEW ID OF " + clientID);
                    }else if (dataFromServer instanceof Group){
                        Group group = (Group)dataFromServer;
                        updateGroupList(group);
                        boolean stillInGroup = getGroupWithID(group.getGroupID()) != null;
                        Platform.runLater(() -> {
                            view.updateSideView();
                            if(view.getCurrentGroup() != null && view.getCurrentGroup().getGroupID().equals(group.getGroupID())){ // We updated the group that's currently displayed.
                                if(stillInGroup){
                                    view.setGroupNameLabel(group.getGroupName());
                                }else{
                                    view.updateMessageView(null);
                                }
                            }
                        });
                    }else if (dataFromServer instanceof Message){
                        Message message = (Message)dataFromServer;
                        Group myCopy = getGroupWithID(message.getIntendedGroupID());
                        if(myCopy != null){ // I am part of the group this message is intended for.
                            myCopy.addMessage(message);
                            Platform.runLater(() -> {
                                view.updateSideView();
                                if(view.getCurrentGroup() != null && view.getCurrentGroup().getGroupID().equals(message.getIntendedGroupID())){ // The currently displayed group is the one we sent the message to. Update message view.
                                    view.updateMessageView(myCopy);
                                }
                            });
                        }
                    }
                    dataFromServer = inputFromServer.readObject();
                }
                System.err.println("RECEIVED NULL DATA!!!!!!!!!!!!!!!!");
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not read data from server");
            }

            System.out.println("Connection severed or server shut down.");
            try{
                view.inputStream.close();
                view.outputStream.close();
            }catch(IOException ioe){
                System.out.println("Could not close streams");
            }
            System.exit(0);
        }
    }

    private void setSocket(){
        Semaphore semaphore = new Semaphore(0);
        Platform.runLater(() -> {
            boolean found = false;
            do {
                try {
                    String IPAddress = getIPAddress();
                    if(IPAddress == null || IPAddress.trim().equals("")){
                        throw new Exception();
                    }
                    socket = new Socket(IPAddress, 8000);
                    System.out.println("Connected");
                    found = true;
                    semaphore.release();
                }catch(Exception ioe){
                    System.out.println("Failed to connect.");
                }
            }while(!found);
        });
        try {
            semaphore.acquire();
        }catch(InterruptedException ie){
            System.out.println("Interrupted.");
        }
    }

    public static String getIPAddress(){
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        VBox vBox = new VBox();
        TextField textField = new TextField();
        textField.setPromptText("IP Address to connect to...");
        Button connectButton = new Button("CONNECT");
        connectButton.setOnAction(event -> stage.close());
        vBox.getChildren().addAll(textField, connectButton);
        stage.setScene(new Scene(vBox));
        stage.showAndWait();
        return textField.getText();
    }
}
