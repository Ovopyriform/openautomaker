package org.openautomaker;

import static org.openautomaker.OpenAutomaker.AUTOMAKER_ICON_256;
import static org.openautomaker.OpenAutomaker.AUTOMAKER_ICON_32;
import static org.openautomaker.OpenAutomaker.AUTOMAKER_ICON_64;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.inject.EnvironmentModule;
import org.openautomaker.environment.preference.application.VersionPreference;

import com.gluonhq.ignite.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Preloader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class OpenAutomakerPreloader extends Preloader {

	private Stage preloaderStage;
	private Pane splashLayout;
	private double splashWidth;
	private double splashHeight;

	private static final Logger LOGGER = LogManager.getLogger();

	@Inject
	private VersionPreference fVersionPreference;

	public OpenAutomakerPreloader() {
		GuiceContext fGuiceContext = new GuiceContext(this, () -> List.of(new EnvironmentModule()));
		fGuiceContext.init();
	}

	@Override
	public void start(Stage stage) throws Exception {

		this.preloaderStage = stage;
		LOGGER.debug("show splash - start");
		preloaderStage.toFront();
		preloaderStage.getIcons().addAll(
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_256)),
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_64)),
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_32)));

		//String splashImageName = "Splash_AutoMakerPro.png";
		Image splashImage = new Image(getClass().getResourceAsStream("/org/openautomaker/ui/images/Splash_AutoMakerPro.png"));

		ImageView splash = new ImageView(splashImage);

		splashWidth = splashImage.getWidth();
		splashHeight = splashImage.getHeight();
		splashLayout = new AnchorPane();

		Text versionLabel = new Text("Version " + fVersionPreference.getValue().getValue());
		versionLabel.getStyleClass().add("splashVersion");
		AnchorPane.setBottomAnchor(versionLabel, 20.0);
		AnchorPane.setLeftAnchor(versionLabel, 21.0);

		splashLayout.setStyle("-fx-background-color: rgba(255, 0, 0, 0);");
		splashLayout.getChildren().addAll(splash, versionLabel);

		Scene splashScene = new Scene(splashLayout, Color.TRANSPARENT);
		splashScene.getStylesheets().add(getClass().getResource("/org/openautomaker/ui/css/JMetroDarkTheme.css").toExternalForm());

		preloaderStage.initStyle(StageStyle.TRANSPARENT);

		final Rectangle2D bounds = Screen.getPrimary().getBounds();
		preloaderStage.setScene(splashScene);
		preloaderStage.setX(bounds.getMinX() + bounds.getWidth() / 2 - splashWidth / 2);
		preloaderStage.setY(bounds.getMinY() + bounds.getHeight() / 2 - splashHeight / 2);

		LOGGER.debug("show splash");
		preloaderStage.show();
	}

	@Override
	public void handleStateChangeNotification(StateChangeNotification scn) {

		if (scn.getType() == StateChangeNotification.Type.BEFORE_START) {
			LOGGER.debug("Hide Splash");

			//preloaderStage.hide();

			// Below code is a hack it seems as it's needed to patch the time between splash hide and main show
			// Application initialisation is out of whack.  Head/Filament/Printer loading is not done in init, but after start.
			PauseTransition pauseForABit = new PauseTransition(Duration.millis(2000));
			FadeTransition fadeSplash = new FadeTransition(Duration.seconds(2), splashLayout);
			fadeSplash.setFromValue(1.0);
			fadeSplash.setToValue(0.0);
			fadeSplash.setOnFinished(actionEvent -> {
				preloaderStage.hide();
				preloaderStage.setAlwaysOnTop(false);
			});

			SequentialTransition splashSequence = new SequentialTransition(pauseForABit, fadeSplash);
			splashSequence.play();
		}
	}
}
