package org.openautomaker.ui.component.menu_panel.extras;

import java.io.File;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.environment.preference.paths.GCodePathPreference;
import org.openautomaker.environment.preference.root.FirmwarePathPreference;
import org.openautomaker.environment.preference.slicer.SafetyFeaturesPreference;
import org.openautomaker.javafx.FXProperty;
import org.openautomaker.ui.StageManager;
import org.openautomaker.ui.component.menu_panel.MenuInnerPanel;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.coreUI.DisplayManager;
import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

/**
 * FXML Controller class
 *
 * @author Ian
 */
public class MaintenanceInsetPanelController implements MenuInnerPanel {

	private static final Logger LOGGER = LogManager.getLogger();

	private Printer connectedPrinter;

	private final FileChooser firmwareFileChooser = new FileChooser();

	private final FileChooser gcodeFileChooser = new FileChooser();

	private final BooleanProperty printingDisabled = new SimpleBooleanProperty(false);
	private final BooleanProperty noHead = new SimpleBooleanProperty(false);
	private final BooleanProperty dualHead = new SimpleBooleanProperty(false);
	private final BooleanProperty singleHead = new SimpleBooleanProperty(false);
	private final BooleanProperty noValveHead = new SimpleBooleanProperty(false);
	private final BooleanProperty noFilamentE = new SimpleBooleanProperty(false);
	private final BooleanProperty noFilamentD = new SimpleBooleanProperty(false);
	private final BooleanProperty noFilamentEOrD = new SimpleBooleanProperty(false);

	@FXML
	private VBox container;

	@FXML
	private Button YTestButton;

	@FXML
	private Button PurgeMaterialButton;

	@FXML
	private Button loadFirmwareButton;

	@FXML
	private Button T1CleanButton;

	@FXML
	private Button EjectStuckMaterialButton1;

	@FXML
	private Button EjectStuckMaterialButton2;

	@FXML
	private Button SpeedTestButton;

	@FXML
	private Button XTestButton;

	@FXML
	private Button T0CleanButton;

	@FXML
	private Label currentFirmwareField;

	@FXML
	private Button LevelGantryButton;

	@FXML
	private Button sendGCodeSDButton;

	@FXML
	private Button ZTestButton;

	private final I18N i18n;
	private final SafetyFeaturesPreference safetyFeaturesPreference;
	private final DisplayManager displayManager;
	private final StageManager stageManager;
	private final SelectedPrinter selectedPrinter;
	private final AdvancedModePreference advancedModePreference;
	private final FirmwarePathPreference firmwarePathPreference;
	private final GCodePathPreference gCodePathPreference;

	@Inject
	protected MaintenanceInsetPanelController(
			I18N i18n,
			SafetyFeaturesPreference safetyFeaturesPreference,
			DisplayManager displayManager,
			StageManager stageManager,
			SelectedPrinter selectedPrinter,
			AdvancedModePreference advancedModePreference,
			FirmwarePathPreference firmwarePathPreference,
			GCodePathPreference gCodePathPreference) {

		this.i18n = i18n;
		this.safetyFeaturesPreference = safetyFeaturesPreference;
		this.displayManager = displayManager;
		this.stageManager = stageManager;
		this.selectedPrinter = selectedPrinter;
		this.advancedModePreference = advancedModePreference;
		this.firmwarePathPreference = firmwarePathPreference;
		this.gCodePathPreference = gCodePathPreference;
	}

	@FXML
	void ejectStuckMaterial1(ActionEvent event) {
		if (connectedPrinter != null && connectedPrinter.headProperty().get() != null) {
			try {
				int nozzleNumber = 0;
				if (connectedPrinter.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD)
					nozzleNumber = 1;

				connectedPrinter.ejectStuckMaterial(nozzleNumber, false, null, safetyFeaturesPreference.getValue());
			}
			catch (PrinterException ex) {
				LOGGER.info("Error attempting to run eject stuck material E");
			}
		}
	}

	@FXML
	void ejectStuckMaterial2(ActionEvent event) {
		if (connectedPrinter != null &&
				connectedPrinter.headProperty().get() != null &&
				connectedPrinter.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			try {
				connectedPrinter.ejectStuckMaterial(0, false, null, safetyFeaturesPreference.getValue());
			}
			catch (PrinterException ex) {
				LOGGER.info("Error attempting to run eject stuck material D");
			}
		}
	}

	@FXML
	void levelGantry(ActionEvent event) {
		try {
			connectedPrinter.levelGantry(false, null);
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't level gantry");
		}
	}

	@FXML
	void testX(ActionEvent event) {
		try {
			connectedPrinter.testX(false, null);
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't test X");
		}
	}

	@FXML
	void testY(ActionEvent event) {
		try {
			connectedPrinter.testY(false, null);
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't test Y");
		}
	}

	@FXML
	void testZ(ActionEvent event) {
		try {
			connectedPrinter.testZ(false, null);
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't test Z");
		}
	}

	@FXML
	void cleanNozzleT0(ActionEvent event) {
		try {
			connectedPrinter.cleanNozzle(0, false, null, safetyFeaturesPreference.getValue());
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't clean left nozzle");
		}
	}

	@FXML
	void cleanNozzleT1(ActionEvent event) {
		try {
			connectedPrinter.cleanNozzle(1, false, null, safetyFeaturesPreference.getValue());
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't clean right nozzle");
		}
	}

	@FXML
	void speedTest(ActionEvent event) {
		try {
			connectedPrinter.speedTest(false, null);
		}
		catch (PrinterException ex) {
			LOGGER.error("Couldn't run speed test");
		}
	}

	@FXML
	void loadFirmware(ActionEvent event) {

		//TODO: Why does this have a field??  Shouldn't this just be a file chooser builder method?
		firmwareFileChooser.setInitialFileName("Untitled");
		firmwareFileChooser.setTitle(i18n.t("maintenancePanel.firmwareFileChooserTitle"));
		firmwareFileChooser.setSelectedExtensionFilter(new ExtensionFilter(i18n.t("maintenancePanel.firmwareFileDescription"), "*.bin"));
		firmwareFileChooser.setInitialDirectory(firmwarePathPreference.getValue().toFile());

		final File file = firmwareFileChooser.showOpenDialog(stageManager.getMainStage());

		if (file != null) {
			firmwarePathPreference.setValue(file.toPath().getParent());
			connectedPrinter.loadFirmware(file.getAbsolutePath());
		}
	}

	@FXML
	void sendGCodeSD(ActionEvent event) {

		gcodeFileChooser.setInitialFileName("Untitled");
		gcodeFileChooser.setTitle(i18n.t("maintenancePanel.gcodeFileChooserTitle"));
		gcodeFileChooser.setSelectedExtensionFilter(new ExtensionFilter(i18n.t("maintenancePanel.gcodeFileDescription"), "*.gcode"));
		gcodeFileChooser.setInitialDirectory(gCodePathPreference.getValue().toFile());

		final File file = gcodeFileChooser.showOpenDialog(container.getScene().getWindow());

		if (file != null) {
			gCodePathPreference.setValue(file.toPath().getParent());
			try {
				connectedPrinter.executeGCodeFile(file.toPath(), false);
			}
			catch (PrinterException ex) {
				LOGGER.error("Error sending SD job");
			}

		}
	}

	@FXML
	void purge(ActionEvent event) {
		displayManager.getPurgeInsetPanelController().purge(connectedPrinter);
	}

	/**
	 * Initialises the controller class.
	 *
	 * @param url
	 * @param rb
	 */
	public void initialize() {
		try {
			YTestButton.disableProperty().bind(printingDisabled);
			PurgeMaterialButton.disableProperty().bind(
					noFilamentEOrD.or(noHead).or(printingDisabled));

			T0CleanButton.disableProperty().bind(
					noHead
							.or(printingDisabled)
							.or(dualHead.and(noFilamentD))
							.or(singleHead.and(noFilamentEOrD))
							.or(noValveHead));
			T1CleanButton.disableProperty().bind(
					noHead
							.or(printingDisabled)
							.or(dualHead.and(noFilamentE))
							.or(singleHead.and(noFilamentEOrD))
							.or(noValveHead));

			EjectStuckMaterialButton1.disableProperty().bind(printingDisabled.or(noFilamentE));
			EjectStuckMaterialButton2.disableProperty().bind(printingDisabled.or(noFilamentD).or(singleHead));

			SpeedTestButton.disableProperty().bind(printingDisabled);
			XTestButton.disableProperty().bind(printingDisabled);

			LevelGantryButton.disableProperty().bind(printingDisabled);
			ZTestButton.disableProperty().bind(printingDisabled);
			
			BooleanProperty advancedmodeProperty = FXProperty.bind(advancedModePreference);
			
			sendGCodeSDButton.disableProperty().bind(printingDisabled.or(advancedmodeProperty).not());

			currentFirmwareField.setStyle("-fx-font-weight: bold;");

			//			gcodeFileChooser.setTitle(i18n.tInst("maintenancePanel.gcodeFileChooserTitle"));
			//			gcodeFileChooser.getExtensionFilters()
			//					.addAll(
			//							new FileChooser.ExtensionFilter(i18n.tInst(
			//									"maintenancePanel.gcodeFileDescription"),
			//									"*.gcode"));

			//			firmwareFileChooser.setTitle(i18n.tInst("maintenancePanel.firmwareFileChooserTitle"));
			//			firmwareFileChooser.getExtensionFilters()
			//					.addAll(
			//							new FileChooser.ExtensionFilter(i18n.tInst(
			//									"maintenancePanel.firmwareFileDescription"), "*.bin"));

			selectedPrinter.addListener((observable, oldValue, newValue) -> {
						if (connectedPrinter != null) {
							currentFirmwareField.textProperty().unbind();
							sendGCodeSDButton.disableProperty().unbind();
							loadFirmwareButton.disableProperty().unbind();

							printingDisabled.unbind();
							printingDisabled.set(true);
							noHead.unbind();
							noHead.set(true);
							noFilamentE.unbind();
							noFilamentE.set(true);
							noFilamentD.unbind();
							noFilamentD.set(true);
							noFilamentEOrD.unbind();
							noFilamentEOrD.set(true);

							dualHead.unbind();
							dualHead.set(false);
							singleHead.unbind();
							singleHead.set(false);
							noValveHead.unbind();
							noValveHead.set(false);
						}

						connectedPrinter = newValue;

						if (connectedPrinter != null) {
							currentFirmwareField.textProperty().bind(connectedPrinter.getPrinterIdentity().firmwareVersionProperty());
							loadFirmwareButton.disableProperty()
									.bind(printingDisabled.or(advancedmodeProperty
											.not()
											.or(connectedPrinter.getPrinterIdentity()
													.validIDProperty()
													.not())));

							printingDisabled.bind(connectedPrinter.printerStatusProperty().isNotEqualTo(
									PrinterStatus.IDLE));

							noHead.bind(connectedPrinter.headProperty().isNull());

							//if (noHead.not().get())
							//{
							//    dualHead.bind(Bindings.size(
							//                    connectedPrinter.headProperty().get().getNozzleHeaters()).isEqualTo(
							//                    2));
							//    singleHead.bind(Bindings.size(
							//                    connectedPrinter.headProperty().get().getNozzleHeaters()).isEqualTo(
							//                    1));
							//    noValveHead.bind(connectedPrinter.headProperty().get().valveTypeProperty().isEqualTo(Head.ValveType.NOT_FITTED));
							//}
							dualHead.bind(Bindings.createBooleanBinding(() -> connectedPrinter.headProperty().get() != null &&
									(connectedPrinter.headProperty().get().getNozzleHeaters().size() == 2),
									connectedPrinter.headProperty()));

							singleHead.bind(Bindings.createBooleanBinding(() -> connectedPrinter.headProperty().get() != null &&
									(connectedPrinter.headProperty().get().getNozzleHeaters().size() == 1),
									connectedPrinter.headProperty()));

							noValveHead.bind(Bindings.createBooleanBinding(() -> connectedPrinter.headProperty().get() != null &&
									(connectedPrinter.headProperty().get().valveTypeProperty().get() == Head.ValveType.NOT_FITTED),
									connectedPrinter.headProperty()));

							noFilamentE.bind(
									connectedPrinter.extrudersProperty().get(0).filamentLoadedProperty().not());
							noFilamentD.bind(
									connectedPrinter.extrudersProperty().get(1).filamentLoadedProperty().not());

							noFilamentEOrD.bind(
									connectedPrinter.extrudersProperty().get(0).filamentLoadedProperty().not()
											.and(
													connectedPrinter.extrudersProperty().get(1).filamentLoadedProperty().not()));
						}
						else {
							loadFirmwareButton.disableProperty().unbind();
							loadFirmwareButton.disableProperty().set(true);
							currentFirmwareField.setText("-");
						}
					});
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public String getMenuTitle() {
		return "maintenancePanel.title";
	}

	@Override
	public List<OperationButton> getOperationButtons() {
		return null;
	}

	@Override
	public void panelSelected() {
	}
}
