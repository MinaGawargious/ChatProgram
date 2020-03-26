import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class View extends Application {
    private ClientMain client; // Each View also has a client to get data from (like group list, messages, etc.).
    // View x has client y and client y has View x.
    private ObjectOutputStream outputToServer;
    protected InputStream inputStream;
    protected OutputStream outputStream;
    private Group currentGroup;
    private List<File> selectedImages;

    private Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    private double screenWidth = screenSize.getWidth();
    private double screenHeight = screenSize.getHeight();
    private double sideViewWidth = 0.2 * screenWidth;
    private double verticalGap = 0.01 * screenHeight;
    private double horizontalGap = 0.01 * screenWidth; // Main view width is 0.78*width = width - sideViewWidth -horizontalGap
    private double smallItemHeight = 0.075 * screenHeight;
    private double smallButtonWidth = 0.075*screenWidth;

    private VBox groupListVBox;
    private VBox messagesVBox;
    private Label groupNameLabel;
    private Button editMembersButton;
    private TextField textField;
    private Button uploadButton;

    private AddUsersStage addUsersStage;

    public Group getCurrentGroup(){
        return currentGroup;
    }

    public AddUsersStage getAddUsersStage(){
        return addUsersStage;
    }

    public View(ClientMain client){
        this.client = client;
        selectedImages = new ArrayList<File>();
        try {
//            init();
            new JFXPanel(); // Initialize the JavaFX environment. I don't call launch() since that creates a new
            // instance of View using the default constructor, but each View needs a client, which is passed in the
            // constructor. The bypass: do everything that launch() does, but manually, to keep this instead.
            System.out.println("Inited");
            Semaphore semaphore = new Semaphore(1);
            Platform.runLater(() -> {
                start(new Stage());
                System.out.println("Called start");
                semaphore.release();
            });
            System.out.println("Created View 2");
            semaphore.acquire(); // Wait for view to show up.
        }catch (Exception e) {
            System.out.println("Could not initialize view for client " + client.clientID);
            e.printStackTrace();
        }
    }

    public void setConnections(ObjectOutputStream outputToServer, InputStream inputStream, OutputStream outputStream) {
        this.outputToServer = outputToServer;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    private double scale(Image image, double maxWidth, double maxHeight){
        return Math.min(maxWidth/image.getWidth(), maxHeight/image.getHeight());
        // We can scale the height up a certain amount and the width up a certain amount. To maintain aspect ratio, return the smallest of the 2.
    }

    @Override
    public void start(Stage primaryStage){
        System.out.println("In start");
        // Entire screen:
        HBox entireScreen = new HBox(horizontalGap);
        entireScreen.setStyle("-fx-background-color: #B0E0E6;");
        // Side view. Contains "new group" button and group previews. Separated by verticalGap:
        VBox sideView = new VBox(verticalGap);
        sideView.setPrefSize(sideViewWidth, screenHeight);
        // New Group image for new group button:
        Image groupImage = new Image(getClass().getResourceAsStream("group.png"));
        ImageView groupImageView = new ImageView(groupImage);
        double scale = scale(groupImage, sideViewWidth, smallItemHeight);
        groupImageView.setFitWidth(scale * groupImage.getWidth());
        groupImageView.setFitHeight(scale * groupImage.getHeight());
        // New group button:
        Button newGroupButton = new Button(null, groupImageView);
        newGroupButton.setPrefSize(sideViewWidth, smallItemHeight);
        newGroupButton.setPadding(Insets.EMPTY);
        newGroupButton.setOnAction(event -> {
            addUsersStage = new AddUsersStage(null);
        });

        // Group Preview VBox with Group PreviewScroll Pane:
        groupListVBox = new VBox(verticalGap);
        // Group Preview Scroll Pane:
        ScrollPane groupListScrollPane = new ScrollPane(groupListVBox);
        groupListScrollPane.setPrefSize(sideViewWidth, screenHeight - smallItemHeight - verticalGap);
        // Add to side view:
        sideView.getChildren().addAll(newGroupButton, groupListScrollPane);

        // Main view. Contains top bar, main message pane, selected images preview if we selected images, and bottom bar to type messages into:
        VBox mainView = new VBox(verticalGap);
        mainView.setPrefSize(screenWidth - sideViewWidth - horizontalGap, screenHeight);
        // Top Pane. Contains group name label and "edit members" button:
        HBox topPaneHBox = new HBox(horizontalGap);
        topPaneHBox.setPrefSize(screenWidth - sideViewWidth - horizontalGap, smallItemHeight);
        topPaneHBox.setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
        // group name label:
        groupNameLabel = new Label("No group selected");
        groupNameLabel.setPrefSize(screenWidth - sideViewWidth - 2*horizontalGap - smallButtonWidth, smallItemHeight);
        groupNameLabel.setAlignment(Pos.CENTER);
        groupNameLabel.setBackground(Background.EMPTY);

        // Edit members image for edit members button:
        Image editMembersImage = new Image(getClass().getResourceAsStream("group2.png"));
        ImageView editMembersImageView = new ImageView(editMembersImage);
        scale = scale(editMembersImage, smallButtonWidth, smallItemHeight); // Ensures image fits in button width.
        editMembersImageView.setFitWidth(scale * editMembersImage.getWidth());
        editMembersImageView.setFitHeight(scale * editMembersImage.getHeight());
        // Edit members button:
        editMembersButton = new Button(null, editMembersImageView);
        editMembersButton.setDisable(true);
        editMembersButton.setPrefSize(smallButtonWidth, smallItemHeight); // Positions button properly to the right. Same size so long as image is properly scaled, but setting button size positions it properly.
        editMembersButton.setPadding(Insets.EMPTY);
        editMembersButton.setBackground(Background.EMPTY);
        editMembersButton.setOnAction(event -> {
            addUsersStage = new AddUsersStage(currentGroup);
        });
        // Add to top pane:
        topPaneHBox.getChildren().addAll(groupNameLabel, editMembersButton);

        // Messages pane, containing scrollpane of messages for currently displayed group:
        messagesVBox = new VBox(verticalGap);
//                messagesVBox.setPrefSize(screenWidth - sideViewWidth - horizontalGap,screenHeight - 2*smallItemHeight - 2*verticalGap);
        // Messages scroll pane:
        ScrollPane messagesScrollPane = new ScrollPane(messagesVBox);
        messagesScrollPane.setPrefSize(screenWidth - sideViewWidth - horizontalGap, screenHeight - 2*smallItemHeight - 2*verticalGap);

        // Bottom pane containing image HBox (if images selected) and send pane (with text field and upload button):
        VBox bottomPaneVBox = new VBox(0);
        bottomPaneVBox.setPrefSize(screenWidth - sideViewWidth - horizontalGap, smallItemHeight);
        bottomPaneVBox.setBackground(new Background(new BackgroundFill(Color.BLUE, null, null)));
        bottomPaneVBox.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));

        //Images HBox for when user selects images, containing imageScrollPane:
        HBox imageHBox = new HBox(horizontalGap);
        imageHBox.setPadding(new Insets(0, 0, 0, horizontalGap));
//                    imageHBox.setPrefSize(screenWidth - sideViewWidth - horizontalGap, smallItemHeight);
        // Image Scroll Pane:
        ScrollPane imageScrollPane = new ScrollPane(imageHBox);
        imageScrollPane.setPrefSize(screenWidth - sideViewWidth - horizontalGap, smallItemHeight); // Change height as we select images.

        // Send pane with text field and upload button:
        HBox sendPaneHBox = new HBox(horizontalGap);
        sendPaneHBox.setPrefSize(screenWidth - sideViewWidth - horizontalGap, smallItemHeight);
        sendPaneHBox.setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));
        sendPaneHBox.setBorder(Border.EMPTY);

        // Text field:
        textField = new TextField();
        textField.setPromptText("Type message...");
        textField.setBorder(Border.EMPTY);
        textField.setBackground(Background.EMPTY);
        textField.setPrefSize(screenWidth - sideViewWidth - 2*horizontalGap - smallButtonWidth, smallItemHeight);
        textField.setDisable(true);
        textField.setOnAction(event -> {
            if(textField.getText().trim().length() > 0 || selectedImages.size() > 0) {
                ArrayList<byte[]> images = new ArrayList<byte[]>();
                for (File file : selectedImages) {
                    try {
                        BufferedImage bufferedImage = ImageIO.read(new FileInputStream(file));
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ImageIO.write(bufferedImage, file.getName().substring(file.getName().lastIndexOf(".") + 1), byteArrayOutputStream);
                        images.add(byteArrayOutputStream.toByteArray());
                    } catch (IOException ioe) {
                        System.out.println("Could not add buffered image from file " + file);
                    }
                }
                Message message = new Message(currentGroup.getGroupID(), client.clientID, textField.getText().trim(), images);
                send(message);
                System.out.println("Sent message to group " + currentGroup);
                textField.clear();
                if(selectedImages.size() > 0) {
                    imageHBox.getChildren().clear();
                    bottomPaneVBox.getChildren().remove(imageScrollPane);
                    messagesScrollPane.setPrefSize(screenWidth - sideViewWidth - horizontalGap, screenHeight - 2*smallItemHeight - 2*verticalGap);
                    selectedImages.clear();
                }
            }
        });

        // Upload image for upload button:
        Image uploadImage = new Image(getClass().getResourceAsStream("uploadIcon.png"));
        ImageView uploadImageView = new ImageView(uploadImage);
        scale = scale(uploadImage, smallButtonWidth, smallItemHeight);
        uploadImageView.setFitWidth(scale * uploadImage.getWidth());
        uploadImageView.setFitWidth(scale * uploadImage.getWidth());
        uploadImageView.setFitHeight(scale * uploadImage.getHeight());
        // Upload button:
        uploadButton = new Button(null, uploadImageView);
        uploadButton.setBackground(Background.EMPTY);
        uploadButton.setPrefSize(smallButtonWidth, smallItemHeight);
        uploadButton.setPadding(Insets.EMPTY);
        uploadButton.setDisable(true);
        uploadButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Select an Image", "*.png", "*.jpg", "*.gif"));
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(primaryStage);
            if(selectedFiles != null && selectedFiles.size() > 0){
                for(File selectedFile : selectedFiles){
                    if(selectedFile != null && !selectedImages.contains(selectedFile)){
                        selectedImages.add(selectedFile);
                        Image selectedImage = new Image(selectedFile.toURI().toString());
                        ImageView selectedImageView = new ImageView(selectedImage);
                        double imageScale = scale(selectedImage, screenWidth/10, 0.85*smallItemHeight);
                        selectedImageView.setFitWidth(imageScale * selectedImage.getWidth());
                        selectedImageView.setFitHeight(imageScale * selectedImage.getHeight());
                        imageHBox.getChildren().add(selectedImageView);
                    }
                }
            }
            if(selectedImages.size() > 0 && !bottomPaneVBox.getChildren().contains(imageScrollPane)){
                messagesScrollPane.setPrefSize(screenWidth - sideViewWidth - horizontalGap, screenHeight - 3*smallItemHeight - 2*verticalGap);
                bottomPaneVBox.getChildren().add(0, imageScrollPane);
            }
        });
        // Add to send pane:
        sendPaneHBox.getChildren().addAll(textField, uploadButton);
        // Add to bottom pane
        bottomPaneVBox.getChildren().addAll(sendPaneHBox);
        // Add to main view:
        mainView.getChildren().addAll(topPaneHBox, messagesScrollPane, bottomPaneVBox);
        // Add to entire screen:
        entireScreen.getChildren().addAll(sideView, mainView);

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Closing");
            send(client.clientID); // Send this client ID to remove yourself from the program.
            try {
                inputStream.close();
                outputStream.close();
            }catch(IOException ioe){
                System.out.println("Could not close streams");
            }
            System.exit(0);
        });
        primaryStage.setScene(new Scene(entireScreen));
        primaryStage.show();
    }

    private void send(Object object){
        try{
            outputToServer.writeObject(object);
            outputToServer.flush();
            outputToServer.reset(); // There was a bug where updating a Group object SENT the updated object but the server RECEIVED the old version. Reset "Reset will disregard the state of any objects already written to the stream" to fix this to ignore the fact that an old version of the same object is already in the stream.
            System.out.println("Sent " + object);
        }catch(IOException ioe){
            System.out.println("Could not send " + object + " in send method");
        }
    }

    public void updateSideView(){
        groupListVBox.getChildren().clear();
        for(Group group: client.getGroups()){
            Label groupLabel = new Label(group.getMessages().size() >= 1 ? group.getMessages().get(group.getMessages().size() - 1).toString() : "Click to start chatting"); // Set preview to last message if there is one.
            groupLabel.setOnMouseClicked(event -> {
                currentGroup = group;
                updateMessageView(group);
            });
            groupListVBox.getChildren().add(groupLabel);
        }
    }

    public void setGroupNameLabel(String name){
        groupNameLabel.setText(name);
    }

    public void updateMessageView(Group group){
        currentGroup = group;
        groupNameLabel.setDisable(group == null);
        editMembersButton.setDisable(group == null);
        textField.setDisable(group == null);
        uploadButton.setDisable(group == null);
        groupNameLabel.setText(group == null ? "No group selected" : group.getGroupName());
        messagesVBox.getChildren().clear();

        if(group != null){
            for(Message message : group.getMessages()){
                if(message.getImages() != null && message.getImages().size() > 0){
                    // Show images first.
                    for(byte[] byteArray : message.getImages()){
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
                        try{
                            BufferedImage bufferedImage = ImageIO.read(byteArrayInputStream);
                            Image image = SwingFXUtils.toFXImage(bufferedImage, null); // Create an FX image from a buffered image.
                            ImageView imageView = new ImageView(image);
                            double scale = scale(image, screenWidth / 3, screenHeight / 3);
                            imageView.setFitWidth(scale * image.getWidth());
                            imageView.setFitHeight(scale * image.getHeight());
                            messagesVBox.getChildren().add(imageView);
                        }catch (IOException e){
                            System.out.println("Could not display image.");
                        }
                    }
                }

                if(message.getMessage() != null && message.getMessage().length() > 0){
                    Label messageLabel = new Label(message.getMessage());
                    messageLabel.setTextFill(Paint.valueOf(message.getSenderID() == client.clientID ? "blue" : "black")); // If I sent this message, make it blue.
                    messagesVBox.getChildren().add(messageLabel);
                }
            }
        }
    }

    class AddUsersStage extends Stage{
        private Group group;
        private VBox membersVBox;
        private Button addUsersButton;
        private TextField groupNameTextField;
        public AddUsersStage(Group group){
            this.group = group;
            // AddUsersVBox, containing group name text field, members VBox, and add button:
            VBox addUsersVBox = new VBox(verticalGap);
            addUsersVBox.setPrefSize(sideViewWidth, screenHeight);
            //Add button:
            addUsersButton = new Button(group == null ? "Create group" : "Edit group");
            addUsersButton.setPrefSize(sideViewWidth, smallItemHeight);
            addUsersButton.setDisable(true);

            // Group Name Text Field:
            groupNameTextField = new TextField(group == null ? null : group.getGroupName());
            groupNameTextField.setPromptText("Group Name");
            groupNameTextField.setAlignment(Pos.CENTER);

            // Members VBox with members scrollPane inside.
            membersVBox = new VBox(0);
//                membersVBox.setPrefSize(sideViewWidth, screenHeight - 2*smallItemHeight - 2*verticalGap);
            for(Integer userID: client.registeredUsers) {
                CheckBox checkBox = new CheckBox(userID.toString()); // Default is unselected and enabled
                if(userID == client.clientID){
                    checkBox.setSelected(true);
                    checkBox.setDisable(group == null); // When creating a group, I must be in that group. So, disable selecting myself if this is a new group.
                }else if(group != null && group.getClientIDs().contains(userID)){ // This is someone else, and the group is not null so it has users and this user is one of them.
                    checkBox.setSelected(true);
                }
                checkBox.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                    Platform.runLater(() -> addUsersButton.setDisable(groupNameTextField.getText() == null || groupNameTextField.getText().trim().length() == 0 || getSelectedClients(membersVBox).size() < 2));
                });
                membersVBox.getChildren().add(checkBox);
            }
            // Members scrollPane:
            ScrollPane membersScrollPane = new ScrollPane(membersVBox);
            membersScrollPane.setPrefSize(sideViewWidth, screenHeight - 2 * smallItemHeight - 2 * verticalGap);

            // Add group name text field functionality:
            groupNameTextField.textProperty().addListener(((observable, oldValue, newValue) -> {
                Platform.runLater(() -> addUsersButton.setDisable(groupNameTextField.getText() == null || groupNameTextField.getText().trim().length() == 0 || getSelectedClients(membersVBox).size() < 2));
            }));

            // Add users button functionality:
            addUsersButton.setOnAction(event -> {
                if(group == null){
                    Group newGroup = new Group(getSelectedClients(membersVBox), groupNameTextField.getText().trim());
                    send(newGroup);
                }else{
                    System.out.println("EditING group " + group);
                    group.setClientIDs(getSelectedClients(membersVBox));
                    group.setGroupName(groupNameTextField.getText().trim());
                    System.out.println("EditED group " + group);
                    send(group);
                }
                this.close();
            });
            addUsersVBox.getChildren().addAll(groupNameTextField, membersScrollPane, addUsersButton);
            this.setScene(new Scene(addUsersVBox));
            this.initModality(Modality.APPLICATION_MODAL);
            this.show();
        }

        public void update(){ // Update the view to make it accurate with the list of registered users.
            List<Integer> selectedClients = getSelectedClients(membersVBox);
            Platform.runLater(() ->  membersVBox.getChildren().clear());// Clear old list. Update in new.
            List<CheckBox> checkboxes = new ArrayList<CheckBox>(); // Instead of a Platform.runLater() for each client, do it all at the very end. Doing it for each client means it takes longer to render, but it STARTS rendering immediately. This way takes shorter to render, but you see nothing until it's all done.
            for(Integer userID: client.registeredUsers){
                CheckBox checkBox = new CheckBox(userID.toString());
                checkBox.setSelected(selectedClients.contains(userID)); // If it was previously selected, restore that status. If you were mid-edit when someone left, you shouldn't have to restart.
                if(userID == client.clientID && group == null){ // Creating a new group still means you must be in that group. You can't force other people in a group
                    checkBox.setDisable(true);
                }
                checkBox.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                    Platform.runLater(() -> addUsersButton.setDisable(groupNameTextField.getText() == null || groupNameTextField.getText().trim().length() == 0 || getSelectedClients(membersVBox).size() < 2));
                });
                checkboxes.add(checkBox);
            }
            Platform.runLater(() -> {
                membersVBox.getChildren().addAll(checkboxes);
                addUsersButton.setDisable(getSelectedClients(membersVBox).size() < 2);
            }); // If someone left and that brought the selected users down below 2, disable the add button.
        }

        private List<Integer> getSelectedClients(Pane pane){
            List<Integer> selectedClients = new ArrayList<Integer>();
            for(Node node : pane.getChildren()){
                if(node instanceof CheckBox && ((CheckBox)(node)).isSelected()){
                    selectedClients.add(Integer.parseInt(((CheckBox)node).getText()));
                }
            }
            return selectedClients;
        }
    }
}
