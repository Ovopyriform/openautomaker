package org.openautomaker.ui.component.printer_id_dialog;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.StageManager;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

//TODO: Perhaps could be regualar injectable
public class PrinterIDDialog {

	private static final Logger LOGGER = LogManager.getLogger();

	private Stage dialogStage = null;

	private PrinterIDDialogController dialogController = null;

	@Inject
	private I18N i18n;

	@Inject
	private FXMLLoader fxmlloader;

	@Inject
	private StageManager stageManager;


	public PrinterIDDialog() {
		GuiceContext.inject(this);

		dialogStage = new Stage(StageStyle.TRANSPARENT);

		URL dialogFXMLURL = getClass().getResource("PrinterIDDialog.fxml");
		fxmlloader.setLocation(dialogFXMLURL);

		try {
			Parent dialogBoxScreen = (Parent) fxmlloader.load();
			dialogController = (PrinterIDDialogController) fxmlloader.getController();

			Scene dialogScene = new Scene(dialogBoxScreen, Color.TRANSPARENT);
			dialogScene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
			dialogStage.setScene(dialogScene);
			dialogStage.initOwner(stageManager.getMainStage());
			dialogStage.initModality(Modality.APPLICATION_MODAL);
			dialogController.configure(dialogStage);
		}
		catch (IOException ex) {
			LOGGER.error("Couldn't load printer ID dialog box FXML");
		}
	}

	/**
	 *
	 * @return
	 */
	public boolean show() {
		dialogStage.showAndWait();
		return dialogController.okPressed();
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
	 * @return
	 */
	public String getChosenPrinterName() {
		return dialogController.getChosenPrinterName();
	}

	/**
	 *
	 * @param printerToUse
	 */
	public void setPrinterToUse(Printer printerToUse) {
		dialogController.setPrinterToUse(printerToUse);
	}

	/**
	 *
	 * @param colour
	 */
	public void setChosenDisplayColour(Color colour) {
		dialogController.setChosenColour(colour);
	}

	/**
	 *
	 * @return
	 */
	public Color getChosenDisplayColour() {
		return dialogController.getChosenDisplayColour();
	}

	/**
	 *
	 * @param printerName
	 */
	public void setChosenPrinterName(String printerName) {
		dialogController.setChosenPrinterName(printerName);
	}
}
