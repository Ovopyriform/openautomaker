package org.openautomaker.ui.component.about_panel;

import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterIdentity;
import org.openautomaker.environment.preference.application.VersionPreference;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.coreUI.DisplayManager;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 *
 * @author Ian
 */
public class AboutPanelController {

	private final Clipboard clipboard = Clipboard.getSystemClipboard();
	private final ClipboardContent content = new ClipboardContent();

	@FXML
	private Label roboxSerialNumber;

	@FXML
	private Label roboxElectronicsVersion;

	@FXML
	private Label headSerialNumber;

	@FXML
	private Label version;

	@FXML
	private Label infoLabel;

	@FXML
	private Text bdLabel;

	@FXML
	private Text bdNames;

	@FXML
	private Text hwengLabel;

	@FXML
	private Text hwengNames;

	@FXML
	private Text swengLabel;

	@FXML
	private Text swengNames;

	@FXML
	private Text amTitleText1;

	@FXML
	private Text amTitleText2;

	@FXML
	private Text amTitleText3;

	@FXML
	private VBox logoBox;

	private Printer currentPrinter = null;


	private final VersionPreference fVersionPreference;
	private final DisplayManager fDisplayManager;
	private final ApplicationStatus applicationStatus;
	private final SelectedPrinter selectedPrinter;

	@Inject
	protected AboutPanelController(
			VersionPreference versionPreference,
			DisplayManager displayManager,
			ApplicationStatus applicationStatus,
			SelectedPrinter selectedPrinter) {

		this.fVersionPreference = versionPreference;
		this.fDisplayManager = displayManager;
		this.applicationStatus = applicationStatus;
		this.selectedPrinter = selectedPrinter;
	}

	@FXML
	private void viewREADME(ActionEvent event) {
		applicationStatus.setMode(ApplicationMode.WELCOME);
	}

	@FXML
	private void okPressed(ActionEvent event) {
		applicationStatus.returnToLastMode();
	}

	@FXML
	private void copyPrinterSerialNumber(ActionEvent event) {
		content.putString(roboxSerialNumber.getText());
		clipboard.setContent(content);
	}

	@FXML
	private void copyHeadSerialNumber(ActionEvent event) {
		content.putString(headSerialNumber.getText());
		clipboard.setContent(content);
	}

	@FXML
	private void systemInformationPressed(ActionEvent event) {
		applicationStatus.setMode(ApplicationMode.SYSTEM_INFORMATION);
	}

	public void initialize() {
		version.setText(fVersionPreference.getValue().getValue());

		fDisplayManager.getDisplayScalingModeProperty().addListener(new ChangeListener<DisplayManager.DisplayScalingMode>() {
			@Override
			public void changed(ObservableValue<? extends DisplayManager.DisplayScalingMode> ov, DisplayManager.DisplayScalingMode t, DisplayManager.DisplayScalingMode scalingMode) {
				switch (scalingMode) {
					case NORMAL:
						infoLabel.setStyle("-fx-font-size: 21px");
						hwengLabel.setStyle("-fx-font-size: 21px");
						hwengNames.setStyle("-fx-font-size: 21px");
						swengLabel.setStyle("-fx-font-size: 21px");
						swengNames.setStyle("-fx-font-size: 21px");
						bdLabel.setStyle("-fx-font-size: 21px");
						bdNames.setStyle("-fx-font-size: 21px");
						amTitleText1.setStyle("-fx-font-size: 100px");
						amTitleText2.setStyle("-fx-font-size: 100px");
						amTitleText3.setStyle("-fx-font-size: 14px");
						logoBox.setScaleX(1);
						logoBox.setScaleY(1);
						break;
					default:
						infoLabel.setStyle("-fx-font-size: 14px");
						hwengLabel.setStyle("-fx-font-size: 14px");
						hwengNames.setStyle("-fx-font-size: 14px");
						swengLabel.setStyle("-fx-font-size: 14px");
						swengNames.setStyle("-fx-font-size: 14px");
						bdLabel.setStyle("-fx-font-size: 14px");
						bdNames.setStyle("-fx-font-size: 14px");
						amTitleText1.setStyle("-fx-font-size: 70px");
						amTitleText2.setStyle("-fx-font-size: 70px");
						amTitleText3.setStyle("-fx-font-size: 10px");
						logoBox.setScaleX(0.8);
						logoBox.setScaleY(0.8);
						break;

				}
			}
		});

		selectedPrinter.addListener((ObservableValue<? extends Printer> observable, Printer oldValue, Printer newValue) -> {
			bindToPrinter(newValue);
		});
		bindToPrinter(selectedPrinter.get());
	}

	private void updateHeadData(Head head) {
		if (head != null) {
			headSerialNumber.setText(head.getFormattedSerial());
		}
		else {
			headSerialNumber.setText("");
		}
	}

	private void updateIDData(PrinterIdentity identity) {
		if (identity != null) {
			roboxSerialNumber.setText(identity.toString());
			if (!identity.printerelectronicsVersionProperty().get().isEmpty())
				roboxElectronicsVersion.setText("E" + identity.printerelectronicsVersionProperty().get());
			else
				roboxElectronicsVersion.setText("");
		}
		else {
			roboxSerialNumber.setText("");
			roboxElectronicsVersion.setText("");
		}
	}

	private ChangeListener<Head> headChangeListener = (observable, oldValue, newValue) -> {
			updateHeadData(newValue);
	};

	private void bindToPrinter(Printer printer) {
		if (currentPrinter != null) {
			currentPrinter.headProperty().removeListener(headChangeListener);
		}

		if (printer != null) {
			printer.headProperty().addListener(headChangeListener);
			updateHeadData(printer.headProperty().get());
			updateIDData(printer.getPrinterIdentity());
		}
		else {
			updateHeadData(null);
			updateIDData(null);
		}

		currentPrinter = printer;
	}
}
