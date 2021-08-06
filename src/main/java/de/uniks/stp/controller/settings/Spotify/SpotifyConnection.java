package de.uniks.stp.controller.settings.Spotify;

import com.sun.net.httpserver.HttpServer;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.Image;
import com.wrapper.spotify.model_objects.specification.Track;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERefreshRequest;
import com.wrapper.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import com.wrapper.spotify.requests.data.albums.GetAlbumRequest;
import com.wrapper.spotify.requests.data.player.GetInformationAboutUsersCurrentPlaybackRequest;
import com.wrapper.spotify.requests.data.tracks.GetTrackRequest;
import de.uniks.stp.StageManager;
import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.controller.settings.ConnectionController;
import de.uniks.stp.controller.titlebar.TitleBarController;
import de.uniks.stp.util.ResizeHelper;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SpotifyConnection {
    private final ModelBuilder builder;
    private String clientID = "f2557b7362074d3b93537b2803ef48b1";
    private String codeVerifier = "";
    private String codeChallenge = "";
    private String code = "";
    private WebView webView;
    private Stage loginStage;
    private HttpServer server;
    private SpotifyApi spotifyApi;
    private AuthorizationCodePKCERequest authorizationCodePKCERequest;
    private AuthorizationCodeCredentials authorizationCodeCredentials;
    private AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest;

    private GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest;
    private GetAlbumRequest getAlbumRequest;

    private ConnectionController connectionController;
    private CurrentlyPlayingContext currentSong;
    private com.wrapper.spotify.model_objects.specification.Image artwork;

    private Label bandAndSong;
    private ImageView spotifyArtwork;
    private Label timePlayed;
    private Label timeTotal;
    private ProgressBar progressBar;

    private String artist;
    private Boolean isPersonalUser;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> handle;
    private ScheduledExecutorService schedulerDescription;
    private GetTrackRequest getTrackRequest;
    Runnable updatePersonalUserViewRunnable = new Runnable() {
        public void run() {
            currentSong = getCurrentlyPlayingSong();
            String albumID = builder.getSpotifyConnection().getCurrentlyPlayingSongAlbumID();
            artwork = builder.getSpotifyConnection().getCurrentlyPlayingSongArtwork(albumID);
            builder.getPersonalUser().setDescription("#" + artist + " - " + currentSong.getItem().getName() + "#" + artwork.getUrl());
            Platform.runLater(() -> updatePersonalUserView(currentSong.getProgress_ms(), currentSong.getItem().getDurationMs()));
        }
    };
    private Parent spotifyLoginView;
    private TitleBarController titleBarController;

    public SpotifyConnection(ModelBuilder builder) {
        this.builder = builder;
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientID)
                .setRedirectUri(URI.create("http://localhost:8888/callback/"))
                .setAccessToken(builder.getSpotifyToken())
                .setRefreshToken(builder.getSpotifyRefresh())
                .build();
        builder.setSpotifyConnection(this);
    }

    public void setSpotifyApi(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public void setGetInformationAboutUsersCurrentPlaybackRequest(GetInformationAboutUsersCurrentPlaybackRequest getInformationAboutUsersCurrentPlaybackRequest) {
        this.getInformationAboutUsersCurrentPlaybackRequest = getInformationAboutUsersCurrentPlaybackRequest;
    }

    public void init(ConnectionController connectionController) {
        this.connectionController = connectionController;

        try {
            spotifyLoginView = FXMLLoader.load(Objects.requireNonNull(StageManager.class.getResource("controller/LoginWebView.fxml")), StageManager.getLangBundle());
        } catch (IOException e) {
            e.printStackTrace();
        }

        loginStage = new Stage();
        loginStage.initStyle(StageStyle.TRANSPARENT);
        Scene scene = new Scene(Objects.requireNonNull(spotifyLoginView));

        webView = (WebView) spotifyLoginView.lookup("#loginWebView");

        // create titleBar
        HBox titleBarBox = (HBox) spotifyLoginView.lookup("#titleBarBox");
        Parent titleBarView = null;
        try {
            titleBarView = FXMLLoader.load(Objects.requireNonNull(StageManager.class.getResource("controller/titlebar/TitleBarView.fxml")), StageManager.getLangBundle());
        } catch (IOException e) {
            e.printStackTrace();
        }
        titleBarBox.getChildren().add(titleBarView);
        titleBarController = new TitleBarController(loginStage, titleBarView, builder);
        titleBarController.init();
        titleBarController.setTheme();
        titleBarController.setMaximizable(true);
        titleBarController.setTitle("Spotify Login");

        createHttpServer();
        createCodeVerifier();
        createCodeChallenge();
        webView.getEngine().load(createSpotifyAuthenticationURLPKCE());
        webView.getEngine().getLoadWorker().stateProperty().addListener(this::getSpotifyCode);

        webView.prefHeightProperty().bind(loginStage.heightProperty());
        webView.prefWidthProperty().bind(loginStage.widthProperty());

        loginStage.setScene(scene);
        loginStage.setResizable(true);
        loginStage.setMinWidth(660);
        loginStage.setMinHeight(710);
        loginStage.show();
        ResizeHelper.addResizeListener(loginStage);
    }

    private void createHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(8888), 0);
            server.createContext("/");
            server.setExecutor(null); // creates a default executor
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createCodeVerifier() {
        codeVerifier = RandomStringUtils.random(80, 0, 0, true, true, null, new SecureRandom());
    }

    private void createCodeChallenge() {
        byte[] codeChallengeHash = DigestUtils.sha256(codeVerifier);
        Base64.Encoder encoder = Base64.getUrlEncoder();
        String codeChallengeBase64 = encoder.encodeToString(codeChallengeHash);
        codeChallenge = codeChallengeBase64.substring(0, codeChallengeBase64.length() - 1);
    }

    private String createSpotifyAuthenticationURLPKCE() {
        String url = "https://accounts.spotify.com/authorize";
        url += "?client_id=" + clientID;
        url += "&response_type=code";
        url += "&redirect_uri=http://localhost:8888/callback/";
        url += "&code_challenge_method=S256";
        url += "&code_challenge=" + codeChallenge;
        url += "&scope=user-read-currently-playing user-read-playback-position user-read-playback-state";
        return url;
    }

    public void setAuthorizationCodePKCERequest(AuthorizationCodePKCERequest authorizationCodePKCERequest) {
        this.authorizationCodePKCERequest = authorizationCodePKCERequest;
    }

    public void getAuthenticationToken() {
        try {

            if (authorizationCodePKCERequest == null) {
                authorizationCodePKCERequest = spotifyApi.authorizationCodePKCE(code, codeVerifier).build();
            }
            authorizationCodeCredentials = authorizationCodePKCERequest.execute();
            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
            builder.setSpotifyToken(spotifyApi.getAccessToken());
            builder.setSpotifyRefresh(spotifyApi.getRefreshToken());
            builder.saveSettings();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            e.printStackTrace();
        }
    }

    private void getSpotifyCode(Observable observable) {
        if (webView.getEngine().getLocation().contains("code=")) {
            String[] link = webView.getEngine().getLocation().split("code=");
            code = link[1];
            getAuthenticationToken();
            Platform.runLater(this::stop);
            connectionController.init();
            updateUserDescriptionScheduler();
        }
    }

    public void setAuthorizationCodePKCERefreshRequest(AuthorizationCodePKCERefreshRequest authorizationCodePKCERefreshRequest) {
        this.authorizationCodePKCERefreshRequest = authorizationCodePKCERefreshRequest;
    }

    public void refreshToken() {
        if (builder.getSpotifyRefresh() != null) {
            try {
                if (!Objects.equals(spotifyApi.getClientId(), "default")) {
                    authorizationCodePKCERefreshRequest = spotifyApi.authorizationCodePKCERefresh().build();
                }
                authorizationCodeCredentials = authorizationCodePKCERefreshRequest.execute();
                spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
                spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
                builder.setSpotifyToken(spotifyApi.getAccessToken());
                builder.setSpotifyRefresh(spotifyApi.getRefreshToken());
                builder.saveSettings();
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public CurrentlyPlayingContext getCurrentlyPlayingSong() {
        try {
            if (!Objects.equals(spotifyApi.getClientId(), "default")) {
                getInformationAboutUsersCurrentPlaybackRequest = spotifyApi.getInformationAboutUsersCurrentPlayback().build();
            }
            return getInformationAboutUsersCurrentPlaybackRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setGetTrackRequest(GetTrackRequest getTrackRequest) {
        this.getTrackRequest = getTrackRequest;
    }

    public String getCurrentlyPlayingSongAlbumID() {
        try {
            CurrentlyPlayingContext currentlyPlayingContext = getInformationAboutUsersCurrentPlaybackRequest.execute();
            String idS = currentlyPlayingContext.getItem().getId();
            if (!Objects.equals(spotifyApi.getClientId(), "default")) {
                getTrackRequest = spotifyApi.getTrack(idS).build();
            }
            Track track = getTrackRequest.execute();
            if (track != null) {
                return track.getAlbum().getId();
            }
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setGetAlbumRequest(GetAlbumRequest getAlbumRequest) {
        this.getAlbumRequest = getAlbumRequest;
    }

    public Image getCurrentlyPlayingSongArtwork(String albumID) {
        if (albumID != null) {
            try {
                if (!Objects.equals(spotifyApi.getClientId(), "default")) {
                    getAlbumRequest = spotifyApi.getAlbum(albumID).build();
                }
                Album album = getAlbumRequest.execute();
                album.getArtists();
                artist = album.getArtists()[0].getName();
                Image[] images = album.getImages();
                return images[2];
            } catch (IOException | SpotifyWebApiException | ParseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void updateValuesUser(String userDescription) {
        //if contains spotify url
        if (userDescription.contains("i.scdn.co") && !isPersonalUser) {
            String[] userDescriptionSplit = userDescription.split("#");
            bandAndSong.setText(userDescriptionSplit[1]);
            spotifyArtwork.setImage(new javafx.scene.image.Image(userDescriptionSplit[2]));
            timeTotal.setVisible(false);
            timePlayed.setVisible(false);
            progressBar.setVisible(false);
        }
    }

    public void showSpotifyPopupView(HBox cell, Boolean isPersonalUser, String userDescription) {
        this.isPersonalUser = isPersonalUser;
        Parent root;
        try {
            root = FXMLLoader.load(Objects.requireNonNull(StageManager.class.getResource("controller/SpotifyView.fxml")), StageManager.getLangBundle());
            Label spotifyHeaderLabel = (Label) root.lookup("#spotifyHeaderLabel");
            spotifyArtwork = (ImageView) root.lookup("#spotifyArtwork");
            bandAndSong = (Label) root.lookup("#bandAndSong");
            timePlayed = (Label) root.lookup("#timePlayed");
            timeTotal = (Label) root.lookup("#timeTotal");
            progressBar = (ProgressBar) root.lookup("#progressBar");

            ResourceBundle lang = StageManager.getLangBundle();
            spotifyHeaderLabel.setText(lang.getString("label.spotifyHeaderLabel"));
            bandAndSong.setText(lang.getString("label.no_song_playing"));

            if (isPersonalUser) {
                builder.getSpotifyConnection().personalUserListener(bandAndSong, spotifyArtwork, timePlayed, timeTotal, progressBar);
            } else if (userDescription != null) {
                updateValuesUser(userDescription);
            }

            cell.setStyle("-fx-background-color: #1db954; -fx-background-radius: 0 10 10 0; -fx-padding: 5 5 5 5;");
            Bounds bounds = (cell.localToScreen(cell.getBoundsInLocal()));
            double x = bounds.getMinX() - 200;
            double y = bounds.getMinY();

            final Stage dialog = new Stage();
            dialog.initOwner(cell.getScene().getWindow());
            dialog.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    dialog.close();
                    cell.setStyle("-fx-background-color: transparent; -fx-background-radius: 10 10 10 10; -fx-padding: 5 5 5 5;");
                    builder.getSpotifyConnection().stopPersonalScheduler();
                }
            });
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            dialog.initStyle(StageStyle.TRANSPARENT);
            dialog.setX(x);
            dialog.setY(y);
            dialog.setScene(scene);
            dialog.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUserDescriptionScheduler() {
        currentSong = getCurrentlyPlayingSong();
        if (currentSong != null) {
            String albumID = getCurrentlyPlayingSongAlbumID();
            artwork = builder.getSpotifyConnection().getCurrentlyPlayingSongArtwork(albumID);
            builder.getPersonalUser().setDescription("#" + artist + " - " + currentSong.getItem().getName() + "#" + artwork.getUrl());
            schedulerDescription = Executors.newScheduledThreadPool(1);
            schedulerDescription.scheduleAtFixedRate(() -> {
                currentSong = getCurrentlyPlayingSong();
                String albumID1 = builder.getSpotifyConnection().getCurrentlyPlayingSongAlbumID();
                artwork = builder.getSpotifyConnection().getCurrentlyPlayingSongArtwork(albumID1);
                if (builder.isSpotifyShow()) {
                    builder.getPersonalUser().setDescription("#" + artist + " - " + currentSong.getItem().getName() + "#" + artwork.getUrl());
                    System.out.println(builder.getPersonalUser().getDescription());
                }
            }, 0, 15, TimeUnit.SECONDS);
        }
    }

    public void personalUserListener(Label bandAndSong, ImageView spotifyArtwork, Label timePlayed, Label timeTotal, ProgressBar progessBar) {
        CurrentlyPlayingContext currentlyPlayingContext = getCurrentlyPlayingSong();
        if (currentlyPlayingContext != null) {
            this.bandAndSong = bandAndSong;
            this.spotifyArtwork = spotifyArtwork;
            this.timeTotal = timeTotal;
            this.timePlayed = timePlayed;
            this.progressBar = progessBar;
            int timeToPlayLeft = currentlyPlayingContext.getItem().getDurationMs() - currentlyPlayingContext.getProgress_ms();
            if (currentlyPlayingContext.getIs_playing() && isPersonalUser) {
                scheduler = Executors.newScheduledThreadPool(1);
                handle = scheduler.scheduleAtFixedRate(updatePersonalUserViewRunnable, 0, 1, TimeUnit.SECONDS);
                scheduler.schedule(() -> {
                    handle.cancel(true);
                    scheduler.shutdown();
                    personalUserListener(bandAndSong, spotifyArtwork, timePlayed, timeTotal, progessBar);
                }, timeToPlayLeft, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void updatePersonalUserView(double elapsed, double duration) {
        bandAndSong.setText(builder.getPersonalUser().getDescription().split("#")[1]);
        javafx.scene.image.Image image = new javafx.scene.image.Image(artwork.getUrl());
        spotifyArtwork.setImage(image);
        formatTime((int) elapsed, (int) duration);
        double progressbarValue = (elapsed / duration);
        progressBar.setProgress(progressbarValue + 0.03);
    }

    private void formatTime(int elapsed, int duration) {
        int intElapsed = (int) Math.floor(elapsed / 1000.0);
        int elapsedHours = intElapsed / (60 * 60);
        if (elapsedHours > 0) {
            intElapsed -= elapsedHours * 60 * 60;
        }
        int elapsedMinutes = intElapsed / 60;
        int elapsedSeconds = intElapsed - elapsedHours * 60 * 60 - elapsedMinutes * 60;

        if (duration > 0) {
            int intDuration = (int) Math.floor(duration / 1000.0);
            int durationHours = intDuration / (60 * 60);
            if (durationHours > 0) {
                intDuration -= durationHours * 60 * 60;
            }
            int durationMinutes = intDuration / 60;
            int durationSeconds = intDuration - durationHours * 60 * 60 -
                    durationMinutes * 60;
            timePlayed.setText(String.format("%02d:%02d", elapsedMinutes, elapsedSeconds));
            timeTotal.setText(String.format("%02d:%02d", durationMinutes, durationSeconds));
        }
    }

    public void stop() {
        if (webView != null) {
            webView.getEngine().locationProperty().removeListener(this::getSpotifyCode);
            webView.getEngine().load(null);
        }
        if (server != null) {
            server.stop(0);
        }
        server = null;
        if (loginStage != null) {
            loginStage.close();
        }
    }

    public void stopPersonalScheduler() {
        if (handle != null) {
            handle.cancel(true);
            scheduler.shutdownNow();
        }
    }

    public void stopDescriptionScheduler() {
        if (schedulerDescription != null) {
            schedulerDescription.shutdownNow();
        }
    }

    public void setTheme() {
        if (builder.getTheme().equals("Bright")) {
            setWhiteMode();
        } else {
            setDarkMode();
        }
    }

    private void setWhiteMode() {
        if (spotifyLoginView != null) {
            spotifyLoginView.getStylesheets().clear();
            spotifyLoginView.getStylesheets().add(Objects.requireNonNull(StageManager.class.getResource("styles/themes/bright/LoginWebView.css")).toExternalForm());
        }
        if (titleBarController != null) {
            titleBarController.setTheme();
        }
    }

    private void setDarkMode() {
        if (spotifyLoginView != null) {
            spotifyLoginView.getStylesheets().clear();
            spotifyLoginView.getStylesheets().add(Objects.requireNonNull(StageManager.class.getResource("styles/themes/dark/LoginWebView.css")).toExternalForm());
        }
        if (titleBarController != null) {
            titleBarController.setTheme();
        }
    }
}
