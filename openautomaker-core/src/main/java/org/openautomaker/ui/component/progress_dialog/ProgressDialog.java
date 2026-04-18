package org.openautomaker.ui.component.progress_dialog;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.services.ControllableService;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.StageManager;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ProgressDialog {

	private static final Logger LOGGER = LogManager.getLogger();
	private Stage dialogStage = null;
	private ProgressDialogController dialogController = null;
	private StackPane dialogBoxContainer = null;

	private ControllableService controllableService;

	@Inject
	private FXMLLoader fxmlLoader;

	@Inject
	private StageManager stageManager;

	public ProgressDialog() {
		this(null);
	}

	public ProgressDialog(ControllableService service) {
		GuiceContext.inject(this);
		this.controllableService = service;

		dialogStage = new Stage(StageStyle.TRANSPARENT);
		URL dialogFXMLURL = getClass().getResource("ProgressDialog.fxml");

		fxmlLoader.setLocation(dialogFXMLURL);

		try {
			dialogBoxContainer = (StackPane) fxmlLoader.load();
			dialogController = (ProgressDialogController) fxmlLoader.getController();

			if (controllableService != null)
				dialogController.configure(controllableService, dialogStage);

			Scene dialogScene = new Scene(dialogBoxContainer, Color.TRANSPARENT);
			dialogScene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
			dialogStage.setScene(dialogScene);
			dialogStage.initOwner(stageManager.getMainStage());
			dialogStage.initModality(Modality.APPLICATION_MODAL);
			dialogStage.toFront();
		}
		catch (IOException ex) {
			LOGGER.error("Couldn't load dialog box FXML", ex);
		}
	}

	/**
	 *
	 * @param service
	 */
	public void associateControllableService(ControllableService service) {
		this.controllableService = service;
		dialogController.configure(controllableService, dialogStage);
	}

	/**
	 *
	 * @param eventType
	 * @param eventHandler
	 */
	public void addKeyHandler(EventType<KeyEvent> eventType, EventHandler<KeyEvent> eventHandler) {
		dialogBoxContainer.addEventHandler(eventType, eventHandler);
	}

	/**
	 *
	 * @param eventType
	 * @param eventHandler
	 */
	public void removeKeyHandler(EventType<KeyEvent> eventType, EventHandler<KeyEvent> eventHandler) {
		dialogBoxContainer.removeEventHandler(eventType, eventHandler);
	}
}
