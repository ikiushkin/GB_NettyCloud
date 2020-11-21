package cloudclient.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import cloudclient.app.controllers.MainController;
import cloudclient.app.settings.ClientSettings;
import cloudcommon.services.settings.Settings;
import cloudnetwork.resources.NetworkSettings;

public class Client extends Application {

    private static Settings settings;

    public static void main(String[] args) {
        launch(args);
    }

    public static Settings getSettings() {
        return settings;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        settings = new Settings("client.cfg", ClientSettings.getSettings());
        String stylePath = settings.get(NetworkSettings.STYLE);

        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/client.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        primaryStage.setTitle("GB Cloud");
        primaryStage.setScene(new Scene(root, 900, 700));
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(500);
        primaryStage.getScene().getStylesheets().add("css/base_style.css");
        primaryStage.getScene().getStylesheets().add("css/gradient_style.css");
        primaryStage.getScene().getStylesheets().add(stylePath);
        primaryStage.getIcons().add(new Image("img/icon.png"));
        primaryStage.setOnCloseRequest(e -> controller.exitApp());
        primaryStage.show();
    }
}
