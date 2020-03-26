import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerMain extends Observable {
    private List<Integer> registeredUsers; // Clients are gonna be on different computers, so we need to store registered user somewhere, so store it on Server as a list.
    private Integer currentClientNumber, currentGroupNumber;
    private Map<Integer, ClientConnection> clientData;

    public static void main(String[] args){
        new ServerMain();
    }

    public ServerMain() {
        registeredUsers = new ArrayList<Integer>();
        currentClientNumber = currentGroupNumber = 0;
        clientData = new HashMap<Integer, ClientConnection>();
        try {
            ServerView serverView = new ServerView();
            serverView.init();
            new JFXPanel(); // Initialize the JavaFX environment. I don't call launch() since that creates a new
            // instance of View using the default constructor, but each View needs a client, which is passed in the
            // constructor. The bypass: do everything that launch() does, but manually, to keep this instead.
            Platform.runLater(() -> new ServerView().start(new Stage()));
            // Create the ServerSocket.
            ServerSocket server = new ServerSocket(8000);
            while (true) {
                // Take in connection
                Socket connectionToClient = server.accept();

                // Create input and output streams.
                OutputStream outputStream = connectionToClient.getOutputStream();
                InputStream inputStream = connectionToClient.getInputStream();

                // Create ClientObserver to output to client from server when server changes.
                ClientObserver clientObserver = new ClientObserver(outputStream);
                this.addObserver(clientObserver);

                // Create input stream thread to listen to inputs from the client coming in through the socket. NOTE: Creation of ObjectOutputStream MUST precede creation of ObjectInputStream.
                new Thread(new ClientHandler(inputStream)).start();

                // Give the new client his custom ID.
                this.setChanged();
                this.notifyObservers(currentClientNumber);
                clientData.put(currentClientNumber, new ClientConnection(clientObserver, inputStream, outputStream));
                currentClientNumber++;
            }
        } catch (IOException e) {
            System.out.println("Could not intialize server");
        } catch(Exception e){
            System.out.println("Could not initialize server view.");
        }
    }

    // Observes changes to server, and relays data to clients.
    class ClientObserver implements Observer {
        // The Observer will observe the server, and when server changes, relay that data to its client via writing to an output stream. So, we need an output stream.

        ObjectOutputStream outputToClient;

        // Initializes output stream to client.
        public ClientObserver(OutputStream outputToClient){
            try {
                this.outputToClient = new ObjectOutputStream(outputToClient);
            } catch (IOException e) {
                System.out.println("Could not create ObjectOutputStream in ClientObserver");
            }
        }

        // Writes to client.
        @Override
        public void update(Observable o, Object arg) {
            try { // Write to client once observer observes changes to the server.
                System.out.println("Writing " + arg);
                outputToClient.writeObject(arg);
                outputToClient.flush(); // Code works without this, but just to force bytes to be written to stream.
                outputToClient.reset(); // Otherwise, Client 1 gets first element, Client 2 gets first 2, ... Client n gets first n. We want all clients to get all n elements.
            } catch (IOException e) {
                System.out.println("Could not write object to client in ClientObserver");
                e.printStackTrace();
            }
        }
    }

    private void removeClient(int clientID){
        try {
            registeredUsers.remove(new Integer(clientID)); // Remove user if he sends himself when he is already on registered users list.
            deleteObserver(clientData.get(clientID).clientObserver);
            clientData.get(clientID).inputStream.close();
            clientData.get(clientID).outputStream.close();
        }catch(IOException ioe){
            System.out.println("Could not remove client " + clientID);
        }
    }

    private void clear(){
        for(Integer clientID : clientData.keySet()){
            removeClient(clientID);
        }
        clientData.clear();
    }

    // Observes input from a client. Sets server as changed. ClientObserver sees change in server and relays data to clients.
    class ClientHandler implements Runnable {
        // This thread will listen for data coming in to the server over the socket and notify observers of it. Therefore, we need an InputStream to listen from.

        ObjectInputStream inputFromClient;

        // Initializes input stream from client.
        public ClientHandler(InputStream inputFromClient) {
            try {
                this.inputFromClient = new ObjectInputStream(inputFromClient);
            } catch (IOException e) {
                System.out.println("Could not create ObjectInputStream from ClientHandler");
            }
        }

        // Observes input from a client. Sets server as changed. ClientObserver sees change in server and relays data to other clients.
        @Override
        public void run() {
            // When we read from the input stream, broadcast it to all client observers to relay to the clients via the respective socket.
            try {
                Object dataFromClient = inputFromClient.readObject();
                while (dataFromClient != null) {
                    setChanged();
                    if(dataFromClient instanceof Integer){
                        Integer clientID = (Integer) dataFromClient;
                        // This client sent its client id to be added to or removed from registered users. So, send the
                        // updated list to all observers to push to ALL clients.
                        if(registeredUsers.contains(clientID)){
                            removeClient(clientID);
                            clientData.remove(clientID);
                        }else {
                            registeredUsers.add((Integer)dataFromClient); // Add the new client's future ID to the list of registered users. ID sent
                        }
                        System.out.println("Server sending " + registeredUsers);
                        notifyObservers(registeredUsers);
                    }else if (dataFromClient instanceof Group){
                        System.out.println("Server received " + dataFromClient);
                        Group group = (Group)dataFromClient;
                        if(group.getGroupID() == -1){ // Indicates group is asking for proper id.
                            group.setGroupID(currentGroupNumber);
                            System.out.println("Here ya go, number " + currentGroupNumber);
                            currentGroupNumber++;
                        }
                        notifyObservers(group);
                    }else{
                        notifyObservers(dataFromClient);
                    }
                    System.out.println("Server sent " + dataFromClient);
                    dataFromClient = inputFromClient.readObject();
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not read data from client");
            }
        }
    }

    class ServerView extends Application{
        @Override
        public void start(Stage primaryStage){
            try {
                StackPane stackPane = new StackPane();
                // Sometimes we have multiple possible IP addresses. Display all options.
                List<String> possibleAddresses = new ArrayList<String>();
                Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();
                while(interfaceEnumeration.hasMoreElements()){
                    NetworkInterface networkInterface = interfaceEnumeration.nextElement();
                    if(networkInterface.isLoopback() || !networkInterface.isUp()){
                        continue;
                    }
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while(addresses.hasMoreElements()){
                        InetAddress addr = addresses.nextElement();
                        if(addr instanceof Inet4Address){
                            possibleAddresses.add(addr.toString().substring(1)); // Address is displayed as /123.456.78.9. Eliminate the /.
                        }
                    }
                }
                String text;
                if(possibleAddresses.size() == 0){
                    text = "Could not find any networks. Open your WiFi network preferences to find the IP address.\nIf multiple options are available, pick IPv4.";
                }else if (possibleAddresses.size() == 1){
                    text = "Connect to " + possibleAddresses.get(0);
                }else{
                    text = "Multiple network options are available. Try:\n";
                    for(String possibleAddress : possibleAddresses){
                        text = text.concat(possibleAddress + "\n");
                    }
                }
                primaryStage.setOnCloseRequest(event -> {
                    System.out.println("Closing server");
                    clear();
                    System.exit(0);
                });
                Label label = new Label(text);
                label.setFont(Font.font(50));
                stackPane.getChildren().add(label);
                primaryStage.setScene(new Scene(stackPane));
                primaryStage.show();
            }catch (Exception e){}
            clear(); // If server crashed instead of closing naturally, sever all connections.
        }
    }

    class ClientConnection{
        private ClientObserver clientObserver;
        private InputStream inputStream;
        private OutputStream outputStream;
        public ClientConnection(ClientObserver clientObserver, InputStream inputStream, OutputStream outputStream){
            this.clientObserver = clientObserver;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }
    }
}
