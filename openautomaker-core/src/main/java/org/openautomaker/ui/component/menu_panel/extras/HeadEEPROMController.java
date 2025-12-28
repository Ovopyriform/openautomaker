package org.openautomaker.ui.component.menu_panel.extras;

import static celtech.coreUI.controllers.panels.FXMLUtilities.addColonsToLabels;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.validation.ValidationSupport;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.printerControl.model.PrinterListChangesListener;
import org.openautomaker.base.printerControl.model.Reel;
import org.openautomaker.base.utils.PrinterUtils;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.javafx.FXProperty;
import org.openautomaker.ui.component.menu_panel.MenuInnerPanel;
import org.openautomaker.ui.component.modal_dialog.ModalDialog;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.components.RestrictedTextField;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.EEPROMState;
import celtech.roboxbase.comms.rx.HeadEEPROMDataResponse;
import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 *
 * @author Ian
 */
public class HeadEEPROMController implements PrinterListChangesListener, MenuInnerPanel {

	private static final Logger LOGGER = LogManager.getLogger();

	@FXML
	private RestrictedTextField rightNozzleZOverrun;

	@FXML
	private RestrictedTextField headThermistorTCal;

	@FXML
	private RestrictedTextField headThermistorBeta;

	@FXML
	private RestrictedTextField lastFilamentTemperature0;

	@FXML
	private RestrictedTextField lastFilamentTemperature1;

	@FXML
	private RestrictedTextField leftNozzleZOverrun;

	@FXML
	private RestrictedTextField headMaxTemperature;

	@FXML
	private RestrictedTextField rightNozzleXOffset;

	@FXML
	private RestrictedTextField leftNozzleYOffset;

	@FXML
	private RestrictedTextField rightNozzleYOffset;

	@FXML
	private RestrictedTextField leftNozzleXOffset;

	@FXML
	private RestrictedTextField headHourCounter;

	@FXML
	private RestrictedTextField rightNozzleBOffset;

	@FXML
	private HBox enterSerialNumberHBox;

	@FXML
	private ImageView serialValidImage;

	@FXML
	private ImageView serialInvalidImage;

	@FXML
	private RestrictedTextField headTypeCodeEntry;

	@FXML
	private RestrictedTextField printerWeek;

	@FXML
	private RestrictedTextField printerYear;

	@FXML
	private RestrictedTextField printerPONumber;

	@FXML
	private RestrictedTextField printerSerialNumber;

	@FXML
	private RestrictedTextField printerChecksum;

	@FXML
	private RestrictedTextField headTypeCode;

	@FXML
	private RestrictedTextField leftNozzleBOffset;

	@FXML
	private RestrictedTextField headType;

	@FXML
	private GridPane headEEPROMOffsets;

	@FXML
	private VBox headFullContainer;

	private ModalDialog eepromCommsError = null;

	private final BooleanProperty offsetFieldsDirty = new SimpleBooleanProperty();
	private final BooleanProperty canSave = new SimpleBooleanProperty();

	private final BooleanProperty canResetHeadProperty = new SimpleBooleanProperty(false);

	private ValidationSupport serialNumberValidation = new ValidationSupport();
	private BooleanProperty serialValidProperty = new SimpleBooleanProperty(false);
	private BooleanProperty ignoreSerialValidationProperty = new SimpleBooleanProperty(false);

	private final I18N i18n;
	private final SystemNotificationManager systemNotificationManager;
	private final PrinterManager printerManager;
	private final SelectedPrinter selectedPrinter;
	private final PrinterUtils printerUtils;
	private final AdvancedModePreference advancedModePreference;

	@Inject
	protected HeadEEPROMController(
			I18N i18n,
			SystemNotificationManager systemNotificationManager,
			SelectedPrinter selectedPrinter,
			PrinterManager printerManager,
			PrinterUtils printerUtils,
			AdvancedModePreference advancedModePreference) {

		this.i18n = i18n;
		this.systemNotificationManager = systemNotificationManager;

		this.selectedPrinter = selectedPrinter;
		this.printerManager = printerManager;
		this.printerUtils = printerUtils;
		this.advancedModePreference = advancedModePreference;
	}

	void whenResetToDefaultsPressed() {
		systemNotificationManager.showProgramInvalidHeadDialog(null);
	}

	/**
	 * Write the values from the text fields onto the actual head. If the unique id is already stored on the head then do not overwrite it.
	 */
	private float getFloatValueOrZero(RestrictedTextField field) {
		float returnValue = 0;
		try {
			returnValue = field.getFloatValue();
		}
		catch (ParseException ex) {
		}
		return returnValue;
	}

	void whenSavePressed() {
		try {
			String headTypeCodeText = headTypeCode.getText();
			float headMaxTemperatureVal = getFloatValueOrZero(headMaxTemperature);
			float headThermistorBetaVal = getFloatValueOrZero(headThermistorBeta);
			float headThermistorTCalVal = getFloatValueOrZero(headThermistorTCal);
			float leftNozzleXOffsetVal = (leftNozzleXOffset.isVisible() ? leftNozzleXOffset.getFloatValue() : rightNozzleXOffset.getFloatValue());
			float leftNozzleYOffsetVal = (leftNozzleYOffset.isVisible() ? leftNozzleYOffset.getFloatValue() : rightNozzleYOffset.getFloatValue());
			float leftNozzleZOverrunVal = (leftNozzleZOverrun.isVisible() ? leftNozzleZOverrun.getFloatValue() : rightNozzleZOverrun.getFloatValue());
			float leftNozzleBOffsetVal = (leftNozzleBOffset.isVisible() ? leftNozzleBOffset.getFloatValue() : (rightNozzleBOffset.isVisible() ? rightNozzleBOffset.getFloatValue() : 0.0F));
			float rightNozzleXOffsetVal = (leftNozzleXOffset.isVisible() ? rightNozzleXOffset.getFloatValue() : 0.0F);
			float rightNozzleYOffsetVal = (leftNozzleYOffset.isVisible() ? rightNozzleYOffset.getFloatValue() : 0.0F);
			float rightNozzleBOffsetVal = (rightNozzleBOffset.isVisible() ? rightNozzleBOffset.getFloatValue() : 0.0F);
			float rightNozzleZOverrunVal = rightNozzleZOverrun.getFloatValue();
			float lastFilamentTemperatureVal0 = getFloatValueOrZero(lastFilamentTemperature0);
			float lastFilamentTemperatureVal1 = 0;
			if (lastFilamentTemperature1.isVisible()) {
				lastFilamentTemperatureVal1 = getFloatValueOrZero(lastFilamentTemperature1);
			}
			float headHourCounterVal = headHourCounter.getFloatValue();

			float nozzle1ZOffsetCalculated = printerUtils.deriveNozzle1ZOffsetsFromOverrun(leftNozzleZOverrunVal, rightNozzleZOverrunVal);
			float nozzle2ZOffsetCalculated = printerUtils.deriveNozzle2ZOffsetsFromOverrun(leftNozzleZOverrunVal, rightNozzleZOverrunVal);

			Printer selPrinter = selectedPrinter.get();
			// N.B. this call must come after reading the data in the fields because
			// reading the head eeprom results in the fields being updated with current head
			// data (i.e. fields will lose edited values)
			HeadEEPROMDataResponse headDataResponse = selPrinter.readHeadEEPROM(true);

			String idToCreate = headTypeCodeEntry.getText()
					+ printerWeek.getText()
					+ printerYear.getText()
					+ printerPONumber.getText()
					+ printerSerialNumber.getText()
					+ printerChecksum.getText();

			selPrinter.transmitWriteHeadEEPROM(
					headTypeCodeText, idToCreate, headMaxTemperatureVal, headThermistorBetaVal,
					headThermistorTCalVal, leftNozzleXOffsetVal, leftNozzleYOffsetVal,
					nozzle1ZOffsetCalculated, leftNozzleBOffsetVal,
					"", "",
					rightNozzleXOffsetVal, rightNozzleYOffsetVal,
					nozzle2ZOffsetCalculated, rightNozzleBOffsetVal,
					lastFilamentTemperatureVal0, lastFilamentTemperatureVal1, headHourCounterVal);

			offsetFieldsDirty.set(false);

			enterSerialNumberHBox.setDisable(true);
			ignoreSerialValidationProperty.set(true);

			try {
				selPrinter.readHeadEEPROM(false);
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Error reading head EEPROM");
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error writing head EEPROM");
			eepromCommsError.setMessage(i18n.t("eeprom.headWriteError"));
			eepromCommsError.show();
		}
		catch (ParseException ex) {
			LOGGER.error("Parse error getting head data");
		}
	}

	void readPrinterID(ActionEvent event) {
		try {
			selectedPrinter.get().readPrinterID();
		}
		catch (PrinterException ex) {
			LOGGER.error("Error reading printer ID");
		}
	}

	/**
	 * Initialises the controller class.
	 */
	public void initialize() {
		try {
			eepromCommsError = new ModalDialog(i18n.t("eeprom.error"));
			//eepromCommsError.setTitle(i18n.t("eeprom.error"));
			eepromCommsError.addButton(i18n.t("dialogs.OK"));

			ChangeListener<String> offsetsChangedListener = (observable, oldValue, newValue) -> {
				offsetFieldsDirty.set(true);
			};

			leftNozzleBOffset.textProperty().addListener(offsetsChangedListener);
			leftNozzleXOffset.textProperty().addListener(offsetsChangedListener);
			leftNozzleYOffset.textProperty().addListener(offsetsChangedListener);
			leftNozzleZOverrun.textProperty().addListener(offsetsChangedListener);
			rightNozzleBOffset.textProperty().addListener(offsetsChangedListener);
			rightNozzleXOffset.textProperty().addListener(offsetsChangedListener);
			rightNozzleYOffset.textProperty().addListener(offsetsChangedListener);
			rightNozzleZOverrun.textProperty().addListener(offsetsChangedListener);

			serialInvalidImage.setImage(new Image(getClass().getResource(
					ApplicationConfiguration.imageResourcePath + "CrossIcon.png").toExternalForm()));
			serialValidImage.setImage(new Image(getClass().getResource(
					ApplicationConfiguration.imageResourcePath + "TickIcon.png").toExternalForm()));

			serialValidProperty.addListener((observable, oldValue, newValue) -> {
				serialValidImage.setVisible(newValue);
				serialInvalidImage.setVisible(!newValue);
			});

			ChangeListener<String> serialPartChangeListener = (observable, oldValue, newValue) -> {
				validateHeadSerial();
				offsetFieldsDirty.set(true);
			};

			headTypeCodeEntry.textProperty().addListener(serialPartChangeListener);
			printerWeek.textProperty().addListener(serialPartChangeListener);
			printerYear.textProperty().addListener(serialPartChangeListener);
			printerPONumber.textProperty().addListener(serialPartChangeListener);
			printerSerialNumber.textProperty().addListener(serialPartChangeListener);
			printerChecksum.textProperty().addListener(serialPartChangeListener);
			validateHeadSerial();

			canSave.bind(offsetFieldsDirty.and(ignoreSerialValidationProperty.or(serialValidProperty)));

			selectedPrinter.addListener((observable, oldValue, newValue) -> {
				if (newValue != oldValue)
					selectedPrinterChanged(oldValue, newValue); // This is probably not the right name.
			});

			printerManager.getPrinterChangeNotifier().addListener(this);

			if (selectedPrinter.get() != null)
				selectedPrinterChanged(null, selectedPrinter.get());

			addColonsToLabels(headFullContainer);

		}
		catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private void validateHeadSerial() {
		boolean serialValid = Head.validateSerial(headTypeCodeEntry.getText(),
				printerWeek.getText(),
				printerYear.getText(),
				printerPONumber.getText(),
				printerSerialNumber.getText(),
				printerChecksum.getText());
		serialValidProperty.set(serialValid);
	}

	private void updateFieldsFromAttachedHead(Head head) {
		Head.ValveType valveType;
		headTypeCode.setText(head.typeCodeProperty().get().trim());
		headType.setText(head.nameProperty().get().trim());

		valveType = head.valveTypeProperty().get();

		headTypeCodeEntry.setText(head.typeCodeProperty().get().trim());
		printerWeek.setText(head.getWeekNumber());
		printerYear.setText(head.getYearNumber());
		printerPONumber.setText(head.getPONumber());
		printerSerialNumber.setText(head.getSerialNumber());
		printerChecksum.setText(head.getChecksum());

		if (head.uniqueIDProperty().get().length() > 8) {
			enterSerialNumberHBox.setDisable(true);
			ignoreSerialValidationProperty.set(true);
		}
		else {
			enterSerialNumberHBox.setDisable(false);
			ignoreSerialValidationProperty.set(false);
		}

		if (head.getNozzleHeaters().size() > 0) {
			lastFilamentTemperature0.setText(String.format("%.0f",
					head.getNozzleHeaters().get(0).lastFilamentTemperatureProperty().get()));
			headMaxTemperature.setText(String.format("%.0f",
					head.getNozzleHeaters().get(0).maximumTemperatureProperty().get()));
			headThermistorBeta.setText(String.format("%.2f",
					head.getNozzleHeaters().get(0).betaProperty().get()));
			headThermistorTCal.setText(String.format("%.2f",
					head.getNozzleHeaters().get(0).tCalProperty().get()));
		}

		if (head.getNozzleHeaters().size() > 1) {
			lastFilamentTemperature1.setText(String.format("%.0f",
					head.getNozzleHeaters().get(1).lastFilamentTemperatureProperty().get()));
			lastFilamentTemperature1.setVisible(true);
		}
		else {
			lastFilamentTemperature1.setVisible(false);
		}

		headHourCounter.setText(String.format("%.2f", head.headHoursProperty().get()));

		if (head.getNozzles().size() > 1) {
			if (valveType == Head.ValveType.FITTED) {
				leftNozzleBOffset.setVisible(true);
				leftNozzleBOffset.setText(String.format("%.2f",
						head.getNozzles().get(0).bOffsetProperty().get()));
				rightNozzleBOffset.setVisible(true);
				rightNozzleBOffset.setText(String.format("%.2f",
						head.getNozzles().get(1).bOffsetProperty().get()));
			}
			else {
				leftNozzleBOffset.setVisible(false);
				leftNozzleBOffset.setText("");
				rightNozzleBOffset.setVisible(false);
				rightNozzleBOffset.setText("");
			}

			leftNozzleXOffset.setVisible(true);
			leftNozzleXOffset.setText(String.format("%.2f",
					head.getNozzles().get(0).xOffsetProperty().get()));
			leftNozzleYOffset.setVisible(true);
			leftNozzleYOffset.setText(String.format("%.2f",
					head.getNozzles().get(0).yOffsetProperty().get()));

			rightNozzleXOffset.setText(String.format("%.2f",
					head.getNozzles().get(1).xOffsetProperty().get()));
			rightNozzleYOffset.setText(String.format("%.2f",
					head.getNozzles().get(1).yOffsetProperty().get()));

			float leftNozzleZOffset = head.getNozzles().get(0).zOffsetProperty().get();
			float rightNozzleZOffset = head.getNozzles().get(1).zOffsetProperty().get();
			float leftNozzleZOverrunValue = printerUtils.deriveNozzle1OverrunFromOffsets(leftNozzleZOffset,
					rightNozzleZOffset);
			leftNozzleZOverrun.setVisible(true);
			leftNozzleZOverrun.setText(String.format("%.2f", leftNozzleZOverrunValue));

			float rightNozzleZOverrunValue = printerUtils.deriveNozzle2OverrunFromOffsets(leftNozzleZOffset,
					rightNozzleZOffset);
			rightNozzleZOverrun.setVisible(true);
			rightNozzleZOverrun.setText(String.format("%.2f", rightNozzleZOverrunValue));
		}
		else if (head.getNozzles().size() > 0) {
			if (valveType == Head.ValveType.FITTED) {
				rightNozzleBOffset.setVisible(true);
				rightNozzleBOffset.setText(String.format("%.2f",
						head.getNozzles().get(0).bOffsetProperty().get()));
			}
			else {
				rightNozzleBOffset.setVisible(false);
				rightNozzleBOffset.setText("");
			}

			rightNozzleXOffset.setText(String.format("%.2f",
					head.getNozzles().get(0).xOffsetProperty().get()));
			rightNozzleYOffset.setText(String.format("%.2f",
					head.getNozzles().get(0).yOffsetProperty().get()));
			float rightNozzleZOffset = head.getNozzles().get(0).zOffsetProperty().get();
			float rightNozzleZOverrunValue = printerUtils.deriveNozzle1OverrunFromOffsets(rightNozzleZOffset,
					rightNozzleZOffset);
			rightNozzleZOverrun.setVisible(true);
			rightNozzleZOverrun.setText(String.format("%.2f", rightNozzleZOverrunValue));

			leftNozzleBOffset.setVisible(false);
			leftNozzleBOffset.setText("");
			leftNozzleXOffset.setVisible(false);
			leftNozzleXOffset.setText("");
			leftNozzleYOffset.setVisible(false);
			leftNozzleZOverrun.setVisible(false);
			leftNozzleZOverrun.setText("");
			leftNozzleBOffset.setText("");
			leftNozzleBOffset.setVisible(false);
		}

		offsetFieldsDirty.set(false);
	}

	private void updateFieldsForNoHead() {
		headTypeCode.setText("");
		headType.setText("");

		headTypeCodeEntry.setText("");
		printerWeek.setText("");
		printerYear.setText("");
		printerPONumber.setText("");
		printerSerialNumber.setText("");
		printerChecksum.setText("");

		enterSerialNumberHBox.setDisable(true);

		lastFilamentTemperature0.setText("");
		lastFilamentTemperature1.setText("");
		headHourCounter.setText("");
		headMaxTemperature.setText("");
		headThermistorBeta.setText("");
		headThermistorTCal.setText("");
		leftNozzleXOffset.setText("");
		leftNozzleYOffset.setText("");
		leftNozzleZOverrun.setText("");
		leftNozzleBOffset.setText("");
		rightNozzleXOffset.setText("");
		rightNozzleYOffset.setText("");
		rightNozzleZOverrun.setText("");
		rightNozzleBOffset.setText("");

		offsetFieldsDirty.set(false);
	}

	private void selectedPrinterChanged(Printer oldValue, Printer newValue) {
		updateFieldsForNoHead();

		if (oldValue != null && oldValue.headProperty().get() != null) {
			removeHeadChangeListeners(oldValue.headProperty().get());
			oldValue.getHeadEEPROMStateProperty().removeListener(headEEPROMStateChangeListener);
		}

		if (newValue != null) {
			newValue.getHeadEEPROMStateProperty().addListener(headEEPROMStateChangeListener);
		}

		//TODO:  Probably worth changing to a return early
		if (newValue != null && newValue.headProperty().get() != null) {
			Head head = newValue.headProperty().get();
			updateFieldsFromAttachedHead(head);
			listenForHeadChanges(head);
			canResetHeadProperty.set(true);
		}
		else if (newValue != null && newValue.getHeadEEPROMStateProperty().get() == EEPROMState.NOT_PROGRAMMED) {
			canResetHeadProperty.set(true);
		}
		else {
			canResetHeadProperty.set(false);
		}
	}

	@Override
	public void whenPrinterAdded(Printer printer) {
		headEEPROMOffsets.disableProperty().bind(
				FXProperty.bind(advancedModePreference).not());
	}

	@Override
	public void whenPrinterRemoved(Printer printer) {
		headEEPROMOffsets.disableProperty().unbind();
	}

	@Override
	public void whenHeadAdded(Printer printer) {
		if (printer == selectedPrinter.get()) {
			Head head = printer.headProperty().get();
			updateFieldsFromAttachedHead(head);
			listenForHeadChanges(head);
			canResetHeadProperty.set(true);
		}
	}

	@Override
	public void whenHeadRemoved(Printer printer, Head head) {
		if (printer == selectedPrinter.get()) {
			updateFieldsForNoHead();
			removeHeadChangeListeners(head);
			canResetHeadProperty.set(false);
			ignoreSerialValidationProperty.set(false);
		}
	}

	@Override
	public void whenReelAdded(Printer printer, int reelIndex) {
	}

	@Override
	public void whenReelRemoved(Printer printer, Reel reel, int reelIndex) {
	}

	@Override
	public void whenReelChanged(Printer printer, Reel reel) {
	}

	@Override
	public void whenExtruderAdded(Printer printer, int extruderIndex) {
	}

	@Override
	public void whenExtruderRemoved(Printer printer, int extruderIndex) {
	}

	private final ChangeListener<EEPROMState> headEEPROMStateChangeListener = new ChangeListener<>() {
		@Override
		public void changed(ObservableValue<? extends EEPROMState> ov, EEPROMState t, EEPROMState t1) {
			if (t1 == EEPROMState.NOT_PROGRAMMED) {
				canResetHeadProperty.set(true);
			}
		}
	};

	private ChangeListener<Object> headChangeListener;

	private void listenForHeadChanges(Head head) {
		headChangeListener = (ObservableValue<? extends Object> observable, Object oldValue, Object newValue) -> {
			updateFieldsFromAttachedHead(head);
		};

		head.getNozzles().get(0).xOffsetProperty().addListener(headChangeListener);
		head.getNozzles().get(0).yOffsetProperty().addListener(headChangeListener);
		head.getNozzles().get(0).zOffsetProperty().addListener(headChangeListener);
		head.getNozzles().get(0).bOffsetProperty().addListener(headChangeListener);

		if (head.getNozzleHeaters().size() > 0) {
			head.getNozzleHeaters().get(0).lastFilamentTemperatureProperty().addListener(
					headChangeListener);
		}

		if (head.getNozzles().size() > 1) {
			head.getNozzles().get(1).xOffsetProperty().addListener(headChangeListener);
			head.getNozzles().get(1).yOffsetProperty().addListener(headChangeListener);
			head.getNozzles().get(1).zOffsetProperty().addListener(headChangeListener);
			head.getNozzles().get(1).bOffsetProperty().addListener(headChangeListener);
		}

		if (head.getNozzleHeaters().size() > 1) {
			head.getNozzleHeaters().get(1).lastFilamentTemperatureProperty().addListener(
					headChangeListener);
		}
	}

	private void removeHeadChangeListeners(Head head) {
		if (headChangeListener != null) {
			head.getNozzles().get(0).xOffsetProperty().removeListener(headChangeListener);
			head.getNozzles().get(0).yOffsetProperty().removeListener(headChangeListener);
			head.getNozzles().get(0).zOffsetProperty().removeListener(headChangeListener);
			head.getNozzles().get(0).bOffsetProperty().removeListener(headChangeListener);

			if (head.getNozzles().size() > 1) {
				head.getNozzles().get(1).xOffsetProperty().removeListener(headChangeListener);
				head.getNozzles().get(1).yOffsetProperty().removeListener(headChangeListener);
				head.getNozzles().get(1).zOffsetProperty().removeListener(headChangeListener);
				head.getNozzles().get(1).bOffsetProperty().removeListener(headChangeListener);
			}
		}
	}

	@Override
	public String getMenuTitle() {
		return "extrasMenu.headEEPROM";
	}

	@Override
	public List<OperationButton> getOperationButtons() {
		List<MenuInnerPanel.OperationButton> operationButtons = new ArrayList<>();
		MenuInnerPanel.OperationButton saveButton = new MenuInnerPanel.OperationButton() {
			@Override
			public String getTextId() {
				return "genericFirstLetterCapitalised.Save";
			}

			@Override
			public String getFXMLName() {
				return "saveButton";
			}

			@Override
			public String getTooltipTextId() {
				return "genericFirstLetterCapitalised.Save";
			}

			@Override
			public void whenClicked() {
				whenSavePressed();
			}

			@Override
			public BooleanProperty whenEnabled() {
				return canSave;
			}

		};
		operationButtons.add(saveButton);

		MenuInnerPanel.OperationButton resetToDefaultsButton = new MenuInnerPanel.OperationButton() {
			@Override
			public String getTextId() {
				return "headPanel.resetToDefaults";
			}

			@Override
			public String getFXMLName() {
				return "saveButton";
			}

			@Override
			public String getTooltipTextId() {
				return "headPanel.resetToDefaults";
			}

			@Override
			public void whenClicked() {
				whenResetToDefaultsPressed();
			}

			@Override
			public ObservableBooleanValue whenEnabled() {
				return canResetHeadProperty;
			}

		};
		operationButtons.add(resetToDefaultsButton);
		return operationButtons;
	}

	@Override
	public void panelSelected() {
	}
}
