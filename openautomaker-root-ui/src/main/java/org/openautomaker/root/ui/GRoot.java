package org.openautomaker.root.ui;

import java.util.List;

import org.openautomaker.guice.GuiceContext;
import org.openautomaker.root.ui.controllers.RootStackController;
import org.openautomaker.root.ui.inject.GRootModule;
import org.openautomaker.root.ui.remote.RootServer;

import com.beust.jcommander.JCommander;

import jakarta.inject.Inject;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class GRoot extends Application {

	private static final GRootCommandLineArgs COMMAND_LINE_ARGS = new GRootCommandLineArgs();

	@Inject
	private RootServer server;

	@Inject
	private RootStackController rootController;

	@Inject
	private GRootServices gRootServices;

	public GRoot() {
		GuiceContext guiceContext = new GuiceContext(this, () -> List.of(new GRootModule(COMMAND_LINE_ARGS)));
		guiceContext.init();
	}

	@Override
	public void start(Stage stage) throws Exception {
		FXMLLoader rootLoader = new FXMLLoader(getClass().getResource("/fxml/RootStack.fxml"), null);
		rootLoader.setController(rootController);
		Parent root = rootLoader.load();

		Scene scene = new Scene(root);
		scene.getStylesheets().add("/styles/Styles.css");

		stage.setTitle("GRoot");
		stage.setScene(scene);
		stage.getIcons().addAll(
				new Image(getClass().getResourceAsStream("/icon/GRootIcon_256x256.png")),
				new Image(getClass().getResourceAsStream("/icon/GRootIcon_64x64.png")),
				new Image(getClass().getResourceAsStream("/icon/GRootIcon_32x32.png")));
		stage.show();
	}

	@Override
	public void stop() {
		rootController.stop();
	}

	public static void main(String[] args) {
		new JCommander(COMMAND_LINE_ARGS).parse(args);

		if (COMMAND_LINE_ARGS.showSplashScreen)
			System.setProperty("javafx.preloader", GRootPreloader.class.getName());
		launch(args);
	}
}
