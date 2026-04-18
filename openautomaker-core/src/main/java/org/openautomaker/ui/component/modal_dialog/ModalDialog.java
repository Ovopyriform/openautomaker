/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openautomaker.ui.component.modal_dialog;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.StageManager;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 *
 * @author ianhudson
 */
//TODO: Modal Dialog looks like the correct way to structure JavaFX components with guice.  Spit the 'tag' and the controller.
public class ModalDialog extends AnchorPane {

	private static final Logger LOGGER = LogManager.getLogger();
	private Stage dialogStage = null;
	private Scene dialogScene = null;
	private ModalDialogController dialogController = null;

	private String windowTitle = null;

	@Inject
	private StageManager stageManager;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	public ModalDialog(String windowTitle) {
		GuiceContext.inject(this);

		initialise(windowTitle);
	}

	public void initialise(String windowTitle) {
		// TODO: Well this seems odd.  Perhaps two different dialogs or parmeterised?
		if (windowTitle != null) {
			dialogStage = new Stage(StageStyle.UTILITY);
			dialogStage.setTitle(windowTitle);
		}
		else {
			dialogStage = new Stage(StageStyle.TRANSPARENT);
		}

		dialogStage.setResizable(false);

		URL fxml = getClass().getResource("ModalDialog.fxml");

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(fxml);
		try {
			Parent dialogBoxScreen = (Parent) fxmlLoader.load();
			dialogController = (ModalDialogController) fxmlLoader.getController();

			dialogScene = new Scene(dialogBoxScreen, Color.TRANSPARENT);
			dialogScene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
			dialogStage.setScene(dialogScene);
			dialogStage.initOwner(stageManager.getMainStage());
			dialogStage.initModality(Modality.APPLICATION_MODAL);
			dialogController.configure(dialogStage);
		}
		catch (IOException ex) {
			LOGGER.error("Couldn't load dialog box FXML");
		}
	}

	/**
	 *
	 * @param title
	 */
	public void setTitle(String title) {
		dialogController.setDialogTitle(title);
	}

	/**
	 *
	 * @param message
	 */
	public void setMessage(String message) {
		dialogController.setDialogMessage(message);
	}

	/**
	 *
	 * @param text
	 * @return
	 */
	public int addButton(String text) {
		int retVal = dialogController.addButton(text);
		dialogScene.getWindow().sizeToScene();
		return retVal;
	}

	/**
	 *
	 * @param text
	 * @param disabler
	 * @return
	 */
	public int addButton(String text, ReadOnlyBooleanProperty disabler) {
		int retVal = dialogController.addButton(text, disabler);
		dialogScene.getWindow().sizeToScene();
		return retVal;
	}

	/**
	 *
	 * @return
	 */
	public int show() {
		dialogStage.showAndWait();

		return dialogController.getButtonValue();
	}

	/**
	 *
	 */
	public void close() {
		dialogStage.hide();
	}

	/**
	 *
	 * @return
	 */
	public boolean isShowing() {
		return dialogStage.isShowing();
	}

	/**
	 *
	 * @param content
	 */
	public void setContent(Node content) {
		dialogController.setContent(content);
	}
}
