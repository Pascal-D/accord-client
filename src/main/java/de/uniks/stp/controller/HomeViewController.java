package de.uniks.stp.controller;

import de.uniks.stp.AlternateServerListCellFactory;
import de.uniks.stp.AlternateUserListCellFactory;
import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.model.Server;
import de.uniks.stp.model.User;
import de.uniks.stp.net.RestClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;


import java.io.IOException;

public class HomeViewController {
    private BorderPane root;
    private ScrollPane scrollPaneUserBox;
    private ScrollPane scrollPaneServerBox;
    private ListView<User> onlineUsersList;
    private ObservableList<User> onlineUsers;
    private ListView<Server> serverList;
    private ObservableList<Server> onlineServers;

    private VBox userBox;
    private VBox currentUserBox;
    private VBox serverBox;
    private ModelBuilder builder;
    private Parent view;
    private Button settingsButton;
    private Button logoutButton;


    public HomeViewController(Parent view, ModelBuilder modelBuilder) {
        this.view = view;
        this.builder = modelBuilder;
    }

    public void init() {
        root = (BorderPane) view.lookup("#root");
        scrollPaneUserBox = (ScrollPane) view.lookup("#scrollPaneUserBox");
        currentUserBox = (VBox) scrollPaneUserBox.getContent().lookup("#currentUserBox");
        scrollPaneServerBox = (ScrollPane) view.lookup("#scrollPaneServerBox");
        serverBox = (VBox) scrollPaneServerBox.getContent().lookup("#serverBox");
        settingsButton = (Button) view.lookup("#settingsButton");
        logoutButton = (Button) view.lookup("#logoutButton");


        serverList = (ListView<Server>) scrollPaneServerBox.getContent().lookup("#serverList");
        serverList.setCellFactory(new AlternateServerListCellFactory());
        onlineServers = FXCollections.observableArrayList();
        this.serverList.setItems(onlineServers);

        onlineUsersList = (ListView<User>) scrollPaneUserBox.getContent().lookup("#onlineUsers");
        onlineUsersList.setCellFactory(new AlternateUserListCellFactory());
        onlineUsers = FXCollections.observableArrayList();
        this.onlineUsersList.setItems(onlineUsers);

        this.settingsButton.setOnAction(this::settingsButtonOnClicked);
        this.logoutButton.setOnAction(this::logoutButtonOnClicked);

        showServers();
        showCurrentUser();
        showUser();
    }

    private void showServers() {
        onlineServers.clear();
        if (!builder.getPersonalUser().getUserKey().equals("")) {
            JSONArray jsonResponse = RestClient.getServers(builder.getPersonalUser().getUserKey());
            for (int i = 0; i < jsonResponse.length(); i++) {
                String serverName = jsonResponse.getJSONObject(i).get("name").toString();
                String serverId = jsonResponse.getJSONObject(i).get("id").toString();
                if (!serverName.equals(builder.getPersonalUser().getName())) {
                    builder.buildServer(serverName, serverId);
                    onlineServers.add(new Server().setName(serverName).setId(serverId));
                }
            }
        }
    }

    private void showUser() {
        onlineUsers.clear();
        JSONArray jsonResponse = RestClient.getUsers(builder.getPersonalUser().getUserKey());
        for (int i = 0; i < jsonResponse.length(); i++) {
            String userName = jsonResponse.getJSONObject(i).get("name").toString();
            if (!userName.equals(builder.getPersonalUser().getName())) {
                builder.buildUser(userName);
                onlineUsers.add(new User().setName(userName).setStatus(true));
            }
        }
    }

    private void showCurrentUser() {
        try {
            Parent root = FXMLLoader.load(StageManager.class.getResource("UserProfileView.fxml"));
            UserProfileController userProfileController = new UserProfileController(root, builder);
            userProfileController.init();
            User currentUser = builder.getPersonalUser();
            userProfileController.setUserName(currentUser.getName());
            userProfileController.setOnline();
            this.currentUserBox.getChildren().add(root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        this.settingsButton.setOnAction(null);
        this.logoutButton.setOnAction(null);
    }

    public void setBuilder(ModelBuilder builder) {
        this.builder = builder;
    }

    private void settingsButtonOnClicked(ActionEvent actionEvent) {
        StageManager.showSettingsScreen();
    }

    private void logoutButtonOnClicked(ActionEvent actionEvent) {
        RestClient restclient = new RestClient();
        restclient.logout(builder.getPersonalUser().getUserKey(), response -> {
            JSONObject result = response.getBody().getObject();
            if(result.get("status").equals("success")) {
                System.out.println(result.get("message"));
                Platform.runLater(StageManager::showLoginScreen);
            }
        });
    }
}