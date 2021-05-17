package de.uniks.stp.controller;

import de.uniks.stp.AlternateUserListCellFactory;
import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.model.CurrentUser;
import de.uniks.stp.model.Server;
import de.uniks.stp.model.User;
import de.uniks.stp.net.RestClient;
import de.uniks.stp.net.WSCallback;
import de.uniks.stp.net.WebSocketClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import kong.unirest.JsonNode;
import org.json.JSONArray;
import org.json.JSONObject;
import util.JsonUtil;

import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The class ServerViewController is about showing the ServerView. It is used to update the builder.
 */
public class ServerViewController {

    private final RestClient restClient;
    private final Server server;
    private final Parent view;
    private HBox root;
    private ScrollPane scrollPaneUserBox;
    private ModelBuilder builder;
    private VBox channelBox;
    private VBox textChannelBox;
    private Label serverNameText;
    private TextField sendTextField;
    private Button sendMessageButton;
    private ListView<User> onlineUsersList;
    private VBox userBox;
    private VBox currentUserBox;
    private WebSocketClient SERVER_USER;

    /**
     * "ServerViewController takes Parent view, ModelBuilder modelBuilder, Server server.
     * It also creates a new restClient"
     */
    public ServerViewController(Parent view, ModelBuilder modelBuilder, Server server) {
        this.view = view;
        this.builder = modelBuilder;
        this.server = server;
        restClient = new RestClient();
    }

    /**
     * Initialise all view parameters
     */
    public void init() {
        root = (HBox) view.lookup("#root");
        channelBox = (VBox) view.lookup("#channelBox");
        serverNameText = (Label) view.lookup("#serverName");
        serverNameText.setText(server.getName());
        textChannelBox = (VBox) view.lookup("#textChannelBox");
        sendTextField = (TextField) view.lookup("#sendTextField");
        sendMessageButton = (Button) view.lookup("#sendMessageButton");
        sendMessageButton.setOnAction(this::onSendMessage);
        scrollPaneUserBox = (ScrollPane) view.lookup("#scrollPaneUserBox");
        currentUserBox = (VBox) scrollPaneUserBox.getContent().lookup("#currentUserBox");
        userBox = (VBox) scrollPaneUserBox.getContent().lookup("#userBox");
        onlineUsersList = (ListView<User>) scrollPaneUserBox.getContent().lookup("#onlineUsers");
        onlineUsersList.setCellFactory(new AlternateUserListCellFactory());
        showCurrentUser();
        showOnlineUsers();
        showServerUsers();
    }

    /**
     * Display Current User
     */
    private void showCurrentUser() {
        try {
            Parent root = FXMLLoader.load(StageManager.class.getResource("UserProfileView.fxml"));
            UserProfileController userProfileController = new UserProfileController(root, builder);
            userProfileController.init();
            CurrentUser currentUser = builder.getPersonalUser();
            userProfileController.setUserName(currentUser.getName());
            userProfileController.setOnline();
            this.currentUserBox.getChildren().add(root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the builder and get the ServerUser. Also sets their online and offline Status.
     */
    public void showOnlineUsers() {
        restClient.getServerUsers(server.getId(), builder.getPersonalUser().getUserKey(), response -> {
            JsonNode body = response.getBody();
            String status = body.getObject().getString("status");
            if (status.equals("success")) {
                JSONArray members = body.getObject().getJSONObject("data").getJSONArray("members");
                builder.getCurrentServer().getUser().clear();
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.getJSONObject(i);
                    String id = member.getString("id");
                    String name = member.getString("name");
                    boolean online = member.getBoolean("online");
                    builder.buildServerUser(name, id, online);
                }
            } else if (status.equals("failure")) {
                System.out.println(body.getObject().getString("message"));
            }
        });
    }

    /**
     * Get Server Users and set them in Online User List
     */
    private void showServerUsers() {
        try {
            SERVER_USER = new WebSocketClient(builder, new URI("wss://ac.uniks.de/ws/system?serverId=" + builder.getCurrentServer().getId()), new WSCallback() {

                @Override
                public void handleMessage(JsonStructure msg) {
                    System.out.println("msg: " + msg);
                    JsonObject jsonMsg = JsonUtil.parse(msg.toString());
                    String userAction = jsonMsg.getString("action");
                    JsonObject jsonData = jsonMsg.getJsonObject("data");
                    String userName = jsonData.getString("name");
                    String userId = jsonData.getString("id");

                    if (userAction.equals("userJoined")) {
                        builder.buildServerUser(userName, userId, true);
                    }
                    if (userAction.equals("userLeft")) {
                        builder.buildServerUser(userName, userId, false);
                    }
                    Platform.runLater(() -> onlineUsersList.setItems(FXCollections.observableList(builder.getCurrentServer().getUser())));
                }

                public void onClose(Session session, CloseReason closeReason) {
                    System.out.println(closeReason.getCloseCode().toString());
                    if (!closeReason.getCloseCode().toString().equals("NORMAL_CLOSURE")) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Users cannot be displayed. No connection to server.", ButtonType.OK);
                            alert.setTitle("Error Dialog");
                            alert.setHeaderText("No Connection");
                            Optional<ButtonType> result = alert.showAndWait();
                            if (result.isPresent() && result.get() == ButtonType.OK) {
                                showServerUsers();
                            }
                        });
                    }
                }
            });
            builder.setSERVER_USER(SERVER_USER);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        restClient.getUsers(builder.getPersonalUser().getUserKey(), response -> {
            Platform.runLater(() -> onlineUsersList.setItems(FXCollections.observableList(builder.getCurrentServer().getUser())));
        });
    }

    public void showText() {

    }

    public void onSendMessage(ActionEvent event) {

    }

    public void showChannels() {

    }

}
