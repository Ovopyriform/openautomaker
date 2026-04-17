package org.openautomaker.ui.component.reset_head_dialog;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.inject.printer_control.model.HeadFactory;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.environment.I18N;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.configuration.ApplicationConfiguration;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.EEPROMState;
import celtech.roboxbase.comms.rx.AckResponse;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

/**
 *
 * @author Ian
 */
public class ResetHeadController {

	private static final Logger LOGGER = LogManager.getLogger(ResetHeadController.class.getName());

	@FXML
	private FlowPane headHolder;

	@FXML
	private ScrollPane scroller;

	@FXML
	private void cancel() {
		systemNotificationManager.hideProgramInvalidHeadDialog();
	}

	private final SystemNotificationManager systemNotificationManager;
	private final SelectedPrinter selectedPrinter;
	private final HeadContainer headContainer;

	private final HeadFactory headFactory;
	private final I18N i18n;

	protected ResetHeadController(
			I18N i18n,
			SystemNotificationManager systemNotificationManager,
			SelectedPrinter selectedPrinter,
			HeadContainer headContainer,
			HeadFactory headFactory) {

		this.i18n = i18n;
		this.systemNotificationManager = systemNotificationManager;
		this.selectedPrinter = selectedPrinter;
		this.headContainer = headContainer;
		this.headFactory = headFactory;

	}

	public void initialize() {
		List<HeadFile> headFiles = new ArrayList<>(headContainer.getCompleteHeadList());

		headFiles.sort((HeadFile o1, HeadFile o2) -> o2.getTypeCode().compareTo(o1.getTypeCode()));

		for (HeadFile headFile : headFiles) {
			URL headImageURL = getClass().getResource("/org/openautomaker/ui/images/heads/" + headFile.getTypeCode() + "-front.png");
			if (headImageURL == null) {
				headImageURL = getClass().getResource("/org/openautomaker/ui/images/heads/" + headFile.getTypeCode() + "-side.png");
			}
			if (headImageURL == null) {
				headImageURL = getClass().getResource("/org/openautomaker/ui/images/heads/unknown.png");
			}

			ImageView image = new ImageView(headImageURL.toExternalForm());
			image.setFitHeight(300);
			image.setFitWidth(300);
			String headNamePrefix = "headPanel." + headFile.getTypeCode();
			String headNameBold = headNamePrefix + ".titleBold";
			String headNameLight = headNamePrefix + ".titleLight";
			String buttonText = "Unknown";
			if (i18n.t(headNameBold) != null && i18n.t(headNameLight) != null) {
				buttonText = i18n.t(headNameBold) + i18n.t(headNameLight);
			}
			Button imageButton = new Button(buttonText, image);
			imageButton.setPrefWidth(350);
			imageButton.setPrefHeight(350);
			imageButton.setContentDisplay(ContentDisplay.TOP);
			imageButton.setOnAction((ActionEvent t) -> {
				Printer currentPrinter = selectedPrinter.get();
				Head head = headFactory.create(headFile);

				//Retain the last filament temperature and hours if they are available
				if (currentPrinter.getHeadEEPROMStateProperty().get() == EEPROMState.PROGRAMMED) {
					if (currentPrinter.headProperty().get() != null) {
						head.headHoursProperty().set(currentPrinter.headProperty().get().headHoursProperty().get());

						for (int nozzleHeaterCounter = 0; nozzleHeaterCounter < currentPrinter.headProperty().get().getNozzleHeaters().size(); nozzleHeaterCounter++) {
							if (head.getNozzleHeaters().size() > nozzleHeaterCounter) {
								head.getNozzleHeaters().get(nozzleHeaterCounter)
										.lastFilamentTemperatureProperty().set(currentPrinter.headProperty().get()
												.getNozzleHeaters().get(nozzleHeaterCounter).lastFilamentTemperatureProperty().get());
							}
						}

						if (currentPrinter.headProperty().get().typeCodeProperty().get().equals(head.typeCodeProperty().get())
								&& currentPrinter.headProperty().get().getChecksum() != null
								&& !currentPrinter.headProperty().get().getChecksum().equals("")) {
							head.setUniqueID(currentPrinter.headProperty().get().typeCodeProperty().get(),
									currentPrinter.headProperty().get().getWeekNumber(),
									currentPrinter.headProperty().get().getYearNumber(),
									currentPrinter.headProperty().get().getPONumber(),
									currentPrinter.headProperty().get().getSerialNumber(),
									currentPrinter.headProperty().get().getChecksum());
						}
					}
				}

				try {
					AckResponse formatResponse = currentPrinter.formatHeadEEPROM();
					if (!formatResponse.isError()) {
						currentPrinter.writeHeadEEPROM(head);
					}
				}
				catch (PrinterException | RoboxCommsException ex) {
					LOGGER.error("Couldn't format and write head data", ex);
				}

				systemNotificationManager.hideProgramInvalidHeadDialog();
			});
			headHolder.getChildren().add(imageButton);
		}
	}

}
