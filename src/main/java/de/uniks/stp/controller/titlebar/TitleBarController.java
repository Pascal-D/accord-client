package de.uniks.stp.controller.titlebar;

import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Objects;

public class TitleBarController {
    private final Stage stage;
    private final Parent titleBarView;
    private final ModelBuilder builder;
    private HBox titleBarSpaceBox;
    private HBox logoAndLabelBox;
    private HBox buttonsBox;
    private Button minButton;
    private Button maxButton;
    private Button closeButton;

    private double x, y;

    public TitleBarController(Stage stage, Parent titleBarView, ModelBuilder builder) {
        this.stage = stage;
        this.titleBarView = titleBarView;
        this.builder = builder;
    }


    public void init() {
        titleBarSpaceBox = (HBox) titleBarView.lookup("#titleBarSpace");
        logoAndLabelBox = (HBox) titleBarView.lookup("#titleLogoAndLabel");
        buttonsBox = (HBox) titleBarView.lookup("#titleButtons");
        minButton = (Button) titleBarView.lookup("#Button_minTitleBar");
        maxButton = (Button) titleBarView.lookup("#Button_maxTitleBar");
        closeButton = (Button) titleBarView.lookup("#Button_closeTitleBar");

        titleBarSpaceBox.prefWidthProperty().bind(stage.widthProperty().subtract(logoAndLabelBox.getPrefWidth() + buttonsBox.getPrefWidth())); // 78 + 109 = 187

        setOnListener();
    }

    private void setOnListener() {
        minButton.setOnAction(event -> {
            Stage thisStage = (Stage) minButton.getScene().getWindow();
            thisStage.setIconified(true);
        });
        maxButton.setOnAction(event -> {
            Stage thisStage = (Stage) maxButton.getScene().getWindow();
            thisStage.setMaximized(!thisStage.isMaximized());
        });
        closeButton.setOnAction(event -> {
            Stage thisStage = (Stage) closeButton.getScene().getWindow();
            thisStage.fireEvent(new WindowEvent(thisStage, WindowEvent.WINDOW_CLOSE_REQUEST));
        });

        titleBarSpaceBox.setOnMouseDragged(this::setStagePos);
        titleBarSpaceBox.setOnMousePressed(this::getScenePos);

        logoAndLabelBox.setOnMouseDragged(this::setStagePos);
        logoAndLabelBox.setOnMousePressed(this::getScenePos);
    }

    private void setStagePos(MouseEvent event) {
        Stage thisStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        thisStage.setX(event.getScreenX() - x);
        thisStage.setY(event.getScreenY() - y);
    }

    private void getScenePos(MouseEvent event) {
        x = event.getSceneX();
        y = event.getSceneY();
    }

    public void setTheme() {
        if (builder.getTheme().equals("Bright")) {
            setWhiteMode();
        } else {
            setDarkMode();
        }
    }

    private void setWhiteMode() {
        titleBarView.getStylesheets().clear();
        titleBarView.getStylesheets().add(Objects.requireNonNull(StageManager.class.getResource("styles/themes/bright/TitleBar.css")).toExternalForm());
    }

    private void setDarkMode() {
        titleBarView.getStylesheets().clear();
        titleBarView.getStylesheets().add(Objects.requireNonNull(StageManager.class.getResource("styles/themes/dark/TitleBar.css")).toExternalForm());
    }

    public void setMaximizable(boolean state) {
        maxButton.setDisable(!state);
    }
}