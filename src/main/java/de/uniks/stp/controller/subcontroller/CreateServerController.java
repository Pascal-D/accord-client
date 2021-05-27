package de.uniks.stp.controller.subcontroller;

import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.model.CurrentUser;
import de.uniks.stp.model.User;
import de.uniks.stp.net.RestClient;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import kong.unirest.JsonNode;

import java.util.ResourceBundle;

/**
 * The class CreateServerController is about showing the createServerView. After a server is
 * created it is closed in HoweViewController. It is called after the + Button is clicked.
 */
public class CreateServerController {

    private final RestClient restClient;
    private final ModelBuilder builder;
    private Parent view;
    private VBox createServerBox;
    private static TextField serverName;
    private Button createServer;
    private CurrentUser personalUser;
    private Runnable change;
    private static Label errorLabel;
    private static String error;

    /**
     * "The class CreateServerController takes the parameters Parent view, ModelBuilder builder.
     * It also creates a new restClient"
     */
    public CreateServerController(Parent view, ModelBuilder builder) {
        this.builder = builder;
        this.view = view;
        restClient = new RestClient();
    }

    /**
     * Initialise all view parameters
     */
    public void init() {
        // Load all view references
        createServerBox = (VBox) view.lookup("#createServerBox");
        serverName = (TextField) view.lookup("#serverName");
        errorLabel = (Label) view.lookup("#errorLabel");
        createServer = (Button) view.lookup(("#createServer"));
        createServer.setOnAction(this::onCreateServerClicked);
    }

    /**
     * Set the Runnable parameter that is called after the Ok button is clicked
     *
     * @param change the userKey off the personalUser
     */
    public void showCreateServerView(Runnable change) {
        this.change = change;
    }

    /**
     * Create the server and change the currentView to the ServerView with the newly created server.
     *
     * @param event is called when the Ok button is clicked
     */
    public void onCreateServerClicked(ActionEvent event) {
        try {
            this.personalUser = builder.getPersonalUser();
            String name = serverName.getText();
            if (name != null && !name.isEmpty()) {
                JsonNode response = restClient.postServer(personalUser.getUserKey(), name);
                String status = response.getObject().getString("status");
                if (status.equals("success")) {
                    String serverId = response.getObject().getJSONObject("data").getString("id");
                    String serverName = response.getObject().getJSONObject("data").getString("name");
                    builder.setCurrentServer(builder.buildServer(serverName, serverId));
                    change.run();
                } else if (status.equals(("failure"))) {
                    setError("error.create_server_failure");
                }
            } else {
                setError("error.server_name_field_empty");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getMessage().equals("java.net.NoRouteToHostException: No route to host: connect")) {
                setError("error.create_server_no_connection");
            }
        }
    }

    /**
     * when language changed reset labels and texts with correct language
     */
    public static void onLanguageChanged() {
        ResourceBundle lang = StageManager.getLangBundle();
        if (serverName != null)
            serverName.setText(lang.getString("textfield.server_name"));

        if (error != null && !error.equals("")) {
            errorLabel.setText(lang.getString(error));
        }
    }

    /**
     * set the error text in label placeholder
     *
     * @param errorMsg the error text
     */
    private void setError(String errorMsg) {
        ResourceBundle lang = StageManager.getLangBundle();
        error = errorMsg;
        errorLabel.setText(lang.getString(error));
    }
}
