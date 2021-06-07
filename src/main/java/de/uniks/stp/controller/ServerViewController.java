package de.uniks.stp.controller;

import de.uniks.stp.AlternateUserListCellFactory;
import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.controller.subcontroller.ServerSettingsChannelController;
import de.uniks.stp.model.*;
import de.uniks.stp.net.RestClient;
import de.uniks.stp.net.WSCallback;
import de.uniks.stp.net.WebSocketClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import kong.unirest.JsonNode;
import org.json.JSONArray;
import org.json.JSONObject;
import util.JsonUtil;
import util.SortUser;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static util.Constants.*;

/**
 * The class ServerViewController is about showing the ServerView. It is used to update the builder.
 */
public class ServerViewController {

    private static ModelBuilder builder;
    private final RestClient restClient;
    private final Server server;
    private final Parent view;
    private ScrollPane scrollPaneUserBox;
    private MenuButton serverMenuButton;
    private static Label textChannelLabel;
    private static Label generalLabel;
    private static Label welcomeToAccord;
    private static Button sendMessageButton;
    private ListView<User> onlineUsersList;
    private ListView<User> offlineUsersList;
    private VBox currentUserBox;
    private WebSocketClient systemWebSocketClient;
    private WebSocketClient chatWebSocketClient;
    private VBox chatBox;
    private ChatViewController messageViewController;
    private MenuItem serverSettings;
    private MenuItem inviteUsers;
    private Map<Categories, CategorySubController> categorySubControllerList;
    private VBox categoryBox;
    private ScrollPane scrollPaneCategories;
    private HomeViewController homeViewController;

    /**
     * "ServerViewController takes Parent view, ModelBuilder modelBuilder, Server server.
     * It also creates a new restClient"
     */
    public ServerViewController(Parent view, ModelBuilder modelBuilder, Server server, HomeViewController homeViewController) {
        this.view = view;
        builder = modelBuilder;
        this.server = server;
        this.restClient = new RestClient();
        this.homeViewController = homeViewController;
    }

    public static ServerChannel getSelectedChat() {
        return builder.getCurrentServerChannel();
    }

    public static void setSelectedChat(ServerChannel Chat) {
        builder.setCurrentServerChannel(Chat);
    }


    /**
     * Callback, when all server information are loaded
     */
    public interface ServerReadyCallback {
        void onSuccess(String status);
    }

    /**
     * Initialise all view parameters
     */
    public void startController(ServerReadyCallback serverReadyCallback) {
        serverMenuButton = (MenuButton) view.lookup("#serverMenuButton");
        scrollPaneCategories = (ScrollPane) view.lookup("#scrollPaneCategories");
        categoryBox = (VBox) scrollPaneCategories.getContent().lookup("#categoryVbox");
        textChannelLabel = (Label) view.lookup("#textChannel");
        generalLabel = (Label) view.lookup("#general");
        welcomeToAccord = (Label) view.lookup("#welcomeToAccord");
        scrollPaneUserBox = (ScrollPane) view.lookup("#scrollPaneUserBox");
        currentUserBox = (VBox) scrollPaneUserBox.getContent().lookup("#currentUserBox");
        onlineUsersList = (ListView<User>) scrollPaneUserBox.getContent().lookup("#onlineUsers");
        onlineUsersList.setCellFactory(new AlternateUserListCellFactory());
        offlineUsersList = (ListView<User>) scrollPaneUserBox.getContent().lookup("#offlineUsers");
        offlineUsersList.setCellFactory(new AlternateUserListCellFactory());
        chatBox = (VBox) view.lookup("#chatBox");
        categorySubControllerList = new HashMap<>();

        loadServerInfos(new ServerInfoCallback() {
            @Override
            public void onSuccess(String status) {
                if (status.equals("success")) {
                    if (getThisServer().getCategories().size() == 0) {
                        loadCategories(serverReadyCallback);
                    }
                }
            }
        }); // members & (categories)
        buildSystemWebSocket();
        buildChatWebSocket();
    }

    /**
     * Initialise all view parameters
     */
    public void startShowServer() throws InterruptedException {
        System.out.println(this.server.getName());
        serverMenuButton.setText(this.server.getName());
        serverSettings = serverMenuButton.getItems().get(0);
        serverSettings.setOnAction(this::onServerSettingsClicked);
        if (serverMenuButton.getItems().size() > 1) {
            inviteUsers = serverMenuButton.getItems().get(1);
            inviteUsers.setOnAction(this::onInviteUsersClicked);
        }
        builder.setServerChatWebSocketClient(this.chatWebSocketClient); // TODO because of message view

        showCurrentUser();
        showOnlineOfflineUsers();

        Platform.runLater(this::generateCategoriesChannelViews);
        if (builder.getCurrentServerChannel() != null) {
            showMessageView();
        }
    }

    /**
     * WebSocket for system messages.
     */
    private void buildSystemWebSocket() {
        try {
            systemWebSocketClient = new WebSocketClient(builder, URI.
                    create(WS_SERVER_URL + WEBSOCKET_PATH + SERVER_SYSTEM_WEBSOCKET_PATH + this.server.getId()),
                    new WSCallback() {

                        @Override
                        public void handleMessage(JsonStructure msg) {
                            System.out.println("msg: " + msg);
                            JsonObject jsonMsg = JsonUtil.parse(msg.toString());
                            String userAction = jsonMsg.getString("action");
                            JsonObject jsonData = jsonMsg.getJsonObject("data");
                            String userName = jsonData.getString("name");
                            String userId = jsonData.getString("id");

                            if (userAction.equals("categoryCreated")) {
                                createCategory(jsonData);
                            }
                            if (userAction.equals("categoryDeleted")) {
                                deleteCategory(jsonData);
                            }
                            if (userAction.equals("categoryUpdated")) {
                                updateCategory(jsonData);
                            }

                            if (userAction.equals("channelCreated")) {
                                createChannel(jsonData);
                            }
                            if (userAction.equals("channelDeleted")) {
                                deleteChannel(jsonData);
                            }
                            if (userAction.equals("channelUpdated")) {
                                updateChannel(jsonData);
                            }

                            if (userAction.equals("userArrived")) {
                                userArrived(jsonData);
                            }
                            if (userAction.equals("userExited")) {
                                userExited(jsonData);
                            }

                            if (userAction.equals("userJoined")) {
                                buildServerUser(userName, userId, true);
                            }
                            if (userAction.equals("userLeft")) {
                                if (userName.equals(builder.getPersonalUser().getName()) && builder.getCurrentServer() == getThisServer()) {
                                    Platform.runLater(StageManager::showLoginScreen);
                                }
                                buildServerUser(userName, userId, false);
                            }

                            if (userAction.equals("serverDeleted")) {
                                deleteServer();
                            }
                            if (userAction.equals("serverUpdated")) {
                                updateServer(userName);
                            }

                            if (builder.getCurrentServer() == getThisServer()) {
                                showOnlineOfflineUsers();
                            }
                        }

                        @Override
                        public void onClose(Session session, CloseReason closeReason) {
                            System.out.println(closeReason.getCloseCode().toString());
                            if (!closeReason.getCloseCode().toString().equals("NORMAL_CLOSURE")) {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR, StageManager.getLangBundle().getString("error.user_cannot_be_displayed"), ButtonType.OK);
                                    alert.setTitle(StageManager.getLangBundle().getString("error.dialog"));
                                    alert.setHeaderText(StageManager.getLangBundle().getString("error.no_connection"));
                                    Optional<ButtonType> result = alert.showAndWait();
                                    if (result.isPresent() && result.get() == ButtonType.OK) {
                                        buildSystemWebSocket();
                                    }
                                });
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the instance of this server.
     */
    private Server getThisServer() {
        return this.server;
    }

    /**
     * Build a serverUser with this instance of server.
     */
    private User buildServerUser(String userName, String userId, boolean online) {
        return builder.buildServerUser(this.server, userName, userId, online);
    }

    /**
     * WebSocket for chat messages.
     */
    private void buildChatWebSocket() {
        chatWebSocketClient = new WebSocketClient(builder, URI.
                create(WS_SERVER_URL + WEBSOCKET_PATH + CHAT_WEBSOCKET_PATH + builder.
                        getPersonalUser().getName().replace(" ", "+") + SERVER_WEBSOCKET_PATH + this.server.getId()),
                new WSCallback() {
                    /**
                     * handles server response
                     *
                     * @param msg is the response from the server as a JsonStructure
                     */
                    @Override
                    public void handleMessage(JsonStructure msg) {
                        JsonObject jsonObject = JsonUtil.parse(msg.toString());
                        System.out.println("serverChatWebSocketClient");
                        System.out.println(msg);

                        if (jsonObject.containsKey("channel")) {
                            Message message = null;
                            String id = jsonObject.getString("id");
                            String channelId = jsonObject.getString("channel");
                            int timestamp = jsonObject.getInt("timestamp");
                            String from = jsonObject.getString("from");
                            String text = jsonObject.getString("text");

                            // currentUser send
                            if (from.equals(builder.getPersonalUser().getName())) {
                                message = new Message().setMessage(text).
                                        setFrom(from).
                                        setTimestamp(timestamp).
                                        setServerChannel(builder.getCurrentServerChannel());
                                if (messageViewController != null && builder.getCurrentServerChannel().getId().equals(channelId)) {
                                    Platform.runLater(() -> messageViewController.clearMessageField());
                                }
                            }
                            // currentUser received
                            else if (!from.equals(builder.getPersonalUser().getName())) {
                                message = new Message().setMessage(text).
                                        setFrom(from).
                                        setTimestamp(timestamp).
                                        setServerChannel(builder.getCurrentServerChannel());
                                if (messageViewController != null && builder.getCurrentServerChannel().getId().equals(channelId)) {
                                    Platform.runLater(() -> messageViewController.clearMessageField());
                                }

                                for (Categories categories : server.getCategories()) {
                                    for (ServerChannel channel : categories.getChannel()) {
                                        if (channel.getId().equals(channelId)) {
                                            channel.withMessage(message);
                                            if (builder.getCurrentServerChannel() == null || channel != builder.getCurrentServerChannel()) {
                                                channel.setUnreadMessagesCounter(channel.getUnreadMessagesCounter() + 1);
                                            }
                                            if (builder.getCurrentServer() == getThisServer()) {
                                                categorySubControllerList.get(categories).refreshChannelList();
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            if (messageViewController != null && builder.getCurrentServerChannel().getId().equals(channelId)) {
                                assert message != null;
                                builder.getCurrentServerChannel().withMessage(message);
                                ChatViewController.printMessage(message);
                            }
                        }
                        if (jsonObject.containsKey("action") && jsonObject.getString("action").equals("info")) {
                            String errorTitle;
                            String serverMessage = jsonObject.getJsonObject("data").getString("message");
                            if (serverMessage.equals("This is not your username.")) {
                                errorTitle = "Username Error";
                            } else {
                                errorTitle = "Chat Error";
                            }
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
                                alert.setTitle(errorTitle);
                                alert.setHeaderText(serverMessage);
                                Optional<ButtonType> result = alert.showAndWait();
                                if (result.isPresent() && result.get() == ButtonType.OK) {
                                    buildSystemWebSocket();
                                }
                            });
                        }
                    }

                    @Override
                    public void onClose(Session session, CloseReason closeReason) {
                        System.out.println(closeReason.getCloseCode().toString());
                        if (!closeReason.getCloseCode().toString().equals("NORMAL_CLOSURE")) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
                                alert.setTitle("No Connection Error");
                                alert.setHeaderText("No Connection - Please check and try again later");
                                Optional<ButtonType> result = alert.showAndWait();
                                if (result.isPresent() && result.get() == ButtonType.OK) {
                                    buildSystemWebSocket();
                                }
                            });
                        }
                    }
                });
    }

    /**
     * Method for changing the current serverName.
     */
    private void changeServerName() {
        serverMenuButton.setText(server.getName());
    }

    /**
     * Initial Chat View and load chat history which is saved in list
     */
    public void showMessageView() {
        try {
            Parent root = FXMLLoader.load(StageManager.class.getResource("ChatView.fxml"), StageManager.getLangBundle());
            this.messageViewController = new ChatViewController(root, builder);
            this.chatBox.getChildren().clear();
            this.messageViewController.init();
            this.chatBox.getChildren().add(root);

            if (builder.getCurrentServer() != null && builder.getCurrentServerChannel() != null) {
                for (Message msg : builder.getCurrentServerChannel().getMessage()) {
                    // Display each Message which are saved
                    ChatViewController.printMessage(msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            this.currentUserBox.getChildren().clear();
            this.currentUserBox.getChildren().add(root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the builder and get the ServerUser as well as the categories. Also sets their online and offline Status.
     */
    public interface ServerInfoCallback {
        void onSuccess(String status);
    }

    /**
     * Method to get Server information
     */
    public void loadServerInfos(ServerInfoCallback serverInfoCallback) {
        restClient.getServerUsers(this.server.getId(), builder.getPersonalUser().getUserKey(), response -> {
            JsonNode body = response.getBody();
            String status = body.getObject().getString("status");
            System.out.println(status);
            this.server.setOwner(body.getObject().getJSONObject("data").getString("owner"));
            if (status.equals("success")) {
                JSONArray members = body.getObject().getJSONObject("data").getJSONArray("members");
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.getJSONObject(i);
                    String id = member.getString("id");
                    String name = member.getString("name");
                    boolean online = member.getBoolean("online");
                    builder.buildServerUser(this.server, name, id, online);
                }
                serverInfoCallback.onSuccess(status);
            } else if (status.equals("failure")) {
                System.out.println(body.getObject().getString("message"));
            }
        });
    }

    /**
     * update server
     */
    private void updateServer(String serverName) {
        this.server.setName(serverName);
        if (builder.getCurrentServer() == this.server) {
            Platform.runLater(this::changeServerName);
        }
        homeViewController.showServerUpdate();
    }

    /**
     * deletes server
     */
    private void deleteServer() {
        Platform.runLater(() -> {
            if (builder.getCurrentServer() == this.server) {
                builder.getPersonalUser().withoutServer(this.server);
                builder.setCurrentServer(null);
                this.homeViewController.serverDeleted();
            } else {
                builder.getPersonalUser().withoutServer(this.server);
                builder.setCurrentServer(null);
                homeViewController.refreshServerList();
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
            alert.setTitle("Server deleted!");
            alert.setHeaderText("Server " + this.server.getName() + " was deleted!");
            Optional<ButtonType> result = alert.showAndWait();
        });

        homeViewController.stopServer(this.server);
    }

    /**
     * adds a new Controller for a new Category with new view
     */
    private void createCategory(JsonObject jsonData) {
        String serverId = jsonData.getString("server");
        String categoryId = jsonData.getString("id");
        String name = jsonData.getString("name");
        for (Server server : builder.getPersonalUser().getServer()) {
            if (server.getId().equals(serverId)) {
                boolean found = false;
                for (Categories categories : server.getCategories()) {
                    if (categories.getId().equals(categoryId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Categories category = new Categories().setName(name).setId(categoryId);
                    server.withCategories(category);
                    if (builder.getCurrentServer() == this.server) {
                        generateCategoryChannelView(category);
                    }
                }
            }
        }
    }

    /**
     * deletes a category with controller and view
     */
    private void deleteCategory(JsonObject jsonData) {
        String serverId = jsonData.getString("server");
        String categoryId = jsonData.getString("id");

        for (Server server : builder.getPersonalUser().getServer()) {
            if (server.getId().equals(serverId)) {
                for (Categories categories : server.getCategories()) {
                    if (categories.getId().equals(categoryId)) {
                        server.withoutCategories(categories);
                        if (builder.getCurrentServer() == this.server) {
                            for (Node view : categoryBox.getChildren()) {
                                if (view.getId().equals(categories.getId())) {
                                    Platform.runLater(() -> this.categoryBox.getChildren().remove(view));
                                    categorySubControllerList.get(categories).stop();
                                    categorySubControllerList.remove(categories);

                                    if (categories.getChannel().contains(builder.getCurrentServerChannel()) || this.server.getCategories().size() == 0) {
                                        throwOutUserFromChatView();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * rename a Category and update it on the view
     */
    private void updateCategory(JsonObject jsonData) {
        String serverId = jsonData.getString("server");
        String categoryId = jsonData.getString("id");
        String name = jsonData.getString("name");

        for (Server server : builder.getPersonalUser().getServer()) {
            if (server.getId().equals(serverId)) {
                for (Categories categories : server.getCategories()) {
                    if (categories.getId().equals(categoryId) && !categories.getName().equals(name)) {
                        categories.setName(name);
                        break;
                    }
                }
            }
        }
    }

    /**
     * adds the new channel to category for the user
     *
     * @param jsonData the message data
     */
    private void createChannel(JsonObject jsonData) {
        String channelId = jsonData.getString("id");
        String channelName = jsonData.getString("name");
        String channelType = jsonData.getString("type");
        boolean channelPrivileged = jsonData.getBoolean("privileged");
        String categoryId = jsonData.getString("category");

        for (Server server : builder.getPersonalUser().getServer()) {
            for (Categories cat : server.getCategories()) {
                if (cat.getId().equals(categoryId)) {
                    ServerChannel newChannel = new ServerChannel().setId(channelId).setType(channelType).setName(channelName).setPrivilege(channelPrivileged);
                    cat.withChannel(newChannel);
                    if (builder.getCurrentServer() == this.server) {
                        Platform.runLater(() -> ServerSettingsChannelController.loadChannels(ServerSettingsChannelController.getSelectedChannel()));
                    }
                    break;
                }
            }
        }
    }

    /**
     * deletes channel from category for the user and eventually
     * get thrown out when users selected chat is the channel which will be deleted
     *
     * @param jsonData the message data
     */
    private void deleteChannel(JsonObject jsonData) {
        String channelId = jsonData.getString("id");
        String categoryId = jsonData.getString("category");

        for (Server server : builder.getPersonalUser().getServer()) {
            for (Categories cat : server.getCategories()) {
                if (cat.getId().equals(categoryId)) {
                    for (ServerChannel channel : cat.getChannel()) {
                        if (channel.getId().equals(channelId)) {
                            cat.withoutChannel(channel);
                            if (builder.getCurrentServer() == this.server) {
                                Platform.runLater(() -> ServerSettingsChannelController.loadChannels(null));
                                if (builder.getCurrentServerChannel().equals(channel)) {
                                    throwOutUserFromChatView();
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * update userList when a user joins the server
     */
    private void userArrived(JsonObject jsonData) {
        String id = jsonData.getString("id");
        String name = jsonData.getString("name");
        boolean status = jsonData.getBoolean("online");

        this.server.withUser(buildServerUser(name, id, status));
        if (builder.getCurrentServer() == this.server) {
            showOnlineOfflineUsers();
        }
    }

    /**
     * update userList when a user exits the server
     */
    private void userExited(JsonObject jsonData) {
        String id = jsonData.getString("id");
        String name = jsonData.getString("name");
        this.server.withoutUser(buildServerUser(name, id, true));
        if (builder.getCurrentServer() == this.server) {
            showOnlineOfflineUsers();
        }
        if (name.equals(builder.getPersonalUser().getName())) {
            Platform.runLater(() -> {
                builder.getPersonalUser().withoutServer(this.server);
                builder.setCurrentServer(null);
                this.homeViewController.serverDeleted();

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
                alert.setTitle("Server leaved!");
                alert.setHeaderText("Server " + this.server.getName() + " was leaved!");
                Optional<ButtonType> result = alert.showAndWait();
            });
            homeViewController.stopServer(this.server);
        }
    }

    /**
     * updates the channel name by change and the privileged with the privileged users from a channel by change
     */
    public void updateChannel(JsonObject jsonData) {
        String categoryId = jsonData.getString("category");
        String channelId = jsonData.getString("id");
        String channelName = jsonData.getString("name");
        String channelType = jsonData.getString("type");
        boolean channelPrivileged = jsonData.getBoolean("privileged");
        JsonArray jsonArray = jsonData.getJsonArray("members");
        String memberId = "";
        boolean flag = false;
        ArrayList<User> member = new ArrayList<>();
        for (int j = 0; j < jsonArray.size(); j++) {
            memberId = jsonArray.getString(j);
            for (User user : this.server.getUser()) {
                if (user.getId().equals(memberId)) {
                    member.add(user);
                }
            }
        }
        for (Categories category : this.server.getCategories()) {
            if (category.getId().equals(categoryId)) {
                for (ServerChannel channel : category.getChannel()) {
                    if (channel.getId().equals(channelId)) {
                        flag = true;
                        category.withoutChannel(channel);
                        channel.setName(channelName);
                        channel.setPrivilege(channelPrivileged);
                        ArrayList<User> privileged = new ArrayList<>(channel.getPrivilegedUsers());
                        channel.withoutPrivilegedUsers(privileged);
                        channel.withPrivilegedUsers(member);
                        category.withChannel(channel);
                        break;
                    }
                }
                if (!flag) {
                    ServerChannel newChannel = new ServerChannel().setId(channelId).setType(channelType).setName(channelName)
                            .setPrivilege(channelPrivileged).withPrivilegedUsers(member);
                    category.withChannel(newChannel);
                    if (builder.getCurrentServer() == this.server) {
                        Platform.runLater(() -> ServerSettingsChannelController.loadChannels(ServerSettingsChannelController.getSelectedChannel()));
                    }
                }
            }
        }
    }

    /**
     * Split Users into offline and online users then update the list
     */
    public void showOnlineOfflineUsers() {
        ArrayList<User> onlineUsers = new ArrayList<>();
        ArrayList<User> offlineUsers = new ArrayList<>();
        for (User user : this.server.getUser()) {
            if (user.isStatus()) {
                if (user.getName().equals(builder.getPersonalUser().getName())) {
                    Platform.runLater(() -> checkForOwnership(user.getId()));
                }
                if (!this.server.getCurrentUser().getName().equals(user.getName())) {
                    onlineUsers.add(user);
                }
            } else {
                if (!this.server.getCurrentUser().getName().equals(user.getName())) {
                    offlineUsers.add(user);
                }
            }
        }
        Platform.runLater(() -> {
            onlineUsersList.prefHeightProperty().bind(onlineUsersList.fixedCellSizeProperty().multiply(onlineUsers.size()));
            offlineUsersList.prefHeightProperty().bind(offlineUsersList.fixedCellSizeProperty().multiply(offlineUsers.size()));
            onlineUsersList.setItems(FXCollections.observableList(onlineUsers).sorted(new SortUser()));
            offlineUsersList.setItems(FXCollections.observableList(offlineUsers).sorted(new SortUser()));
        });
    }

    /**
     * Gets categories from server and adds in list
     */
    public void loadCategories(ServerReadyCallback serverReadyCallback) {
        restClient.getServerCategories(this.server.getId(), builder.getPersonalUser().getUserKey(), response -> {
            JsonNode body = response.getBody();
            String status = body.getObject().getString("status");
            if (status.equals("success")) {
                JSONArray data = body.getObject().getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject categoryInfo = data.getJSONObject(i);
                    Categories categories = new Categories();
                    categories.setId(categoryInfo.getString("id"));
                    categories.setName(categoryInfo.getString("name"));
                    this.server.withCategories(categories);
                    loadChannels(categories, serverReadyCallback);
                }
            }
        });
    }

    /**
     * Gets all channels for a category and adds in list
     *
     * @param cat the category to load the channels from it
     */
    public void loadChannels(Categories cat, ServerReadyCallback serverReadyCallback) {
        restClient.getCategoryChannels(this.server.getId(), cat.getId(), builder.getPersonalUser().getUserKey(), response -> {
            JsonNode body = response.getBody();
            String status = body.getObject().getString("status");
            if (status.equals("success")) {
                JSONArray data = body.getObject().getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject channelInfo = data.getJSONObject(i);
                    ServerChannel channel = new ServerChannel();
                    channel.setCurrentUser(builder.getPersonalUser());
                    channel.setId(channelInfo.getString("id"));
                    channel.setName(channelInfo.getString("name"));
                    channel.setCategories(cat);
                    loadChannelMessages(channel, serverReadyCallback);
                    boolean boolPrivilege = channelInfo.getBoolean("privileged");
                    channel.setPrivilege(boolPrivilege);

                    JSONObject json = new JSONObject(channelInfo.toString());
                    JSONArray jsonArray = json.getJSONArray("members");
                    String memberId;

                    for (int j = 0; j < jsonArray.length(); j++) {
                        memberId = jsonArray.getString(j);
                        for (User user : this.server.getUser()) {
                            if (user.getId().equals(memberId)) {
                                channel.withPrivilegedUsers(user);
                            }
                        }
                    }
                }
            }
        });
    }

    private void loadChannelMessages(ServerChannel channel, ServerReadyCallback serverReadyCallback) {
        System.out.println(new Date().getTime());
        restClient.getChannelMessages(new Date().getTime(), this.server.getId(), channel.getCategories().getId(), channel.getId(), builder.getPersonalUser().getUserKey(), response -> {
            JsonNode body = response.getBody();
            String status = body.getObject().getString("status");
            if (status.equals("success")) {
                JSONArray data = body.getObject().getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject jsonData = data.getJSONObject(i);
                    String from = jsonData.getString("from");
                    long timestamp = jsonData.getLong("timestamp");
                    String text = jsonData.getString("text");
                    Message message = new Message().setMessage(text).setFrom(from).setTimestamp(timestamp);
                    channel.withMessage(message);
                }
                serverReadyCallback.onSuccess(status);
            }
        });
    }

    public void stop() {
        try {
            if (this.systemWebSocketClient != null) {
                if (this.systemWebSocketClient.getSession() != null) {
                    this.systemWebSocketClient.stop();
                }
            }
            if (this.chatWebSocketClient != null) {
                if (this.chatWebSocketClient.getSession() != null) {
                    this.chatWebSocketClient.stop();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (this.serverSettings != null) {
            this.serverSettings.setOnAction(null);
        }
        if (serverMenuButton.getItems().size() > 1 && this.inviteUsers != null) {
            this.inviteUsers.setOnAction(null);
        }

        for (CategorySubController categorySubController : this.categorySubControllerList.values()) {
            categorySubController.stop();
        }
    }

    /**
     * when language changed reset labels and texts with correct language
     */
    public static void onLanguageChanged() {
        ResourceBundle lang = StageManager.getLangBundle();
        if (textChannelLabel != null)
            textChannelLabel.setText(lang.getString("label.textchannel"));

        if (generalLabel != null)
            generalLabel.setText(lang.getString("label.general"));

        if (welcomeToAccord != null)
            welcomeToAccord.setText(lang.getString("label.welcome_to_accord"));

        if (sendMessageButton != null)
            sendMessageButton.setText(lang.getString("button.send"));
    }

    private void onServerSettingsClicked(ActionEvent actionEvent) {
        StageManager.showServerSettingsScreen();
    }

    private void onInviteUsersClicked(ActionEvent actionEvent) {
        StageManager.showInviteUsersScreen();
    }


    /**
     * generates new views for all categories of the server
     */
    private void generateCategoriesChannelViews() {
        Platform.runLater(() -> this.categoryBox.getChildren().clear());
        for (Categories categories : builder.getCurrentServer().getCategories()) {
            generateCategoryChannelView(categories);
        }
    }

    /**
     * generates a new view for a category with a FIXED width for the scrollPane
     */
    private void generateCategoryChannelView(Categories categories) {
        try {
            Parent view = FXMLLoader.load(StageManager.class.getResource("CategorySubView.fxml"));
            view.setId(categories.getId());
            CategorySubController tempCategorySubController = new CategorySubController(view, this, categories);
            tempCategorySubController.init();
            categorySubControllerList.put(categories, tempCategorySubController);
            Platform.runLater(() -> this.categoryBox.getChildren().add(view));
        } catch (Exception e) {
            System.err.println("Error on showing Server Settings Field Screen");
            e.printStackTrace();
        }
    }

    private void checkForOwnership(String id) {
        if (!this.server.getOwner().equals(id)) {
            if (serverMenuButton.getItems().size() >= 2) {
                serverMenuButton.getItems().remove(1);
            }
        }
    }

    private void throwOutUserFromChatView() {
        builder.setCurrentServerChannel(null);
        setSelectedChat(null);
        this.messageViewController.stop();
        Platform.runLater(() -> this.chatBox.getChildren().clear());
    }
}
