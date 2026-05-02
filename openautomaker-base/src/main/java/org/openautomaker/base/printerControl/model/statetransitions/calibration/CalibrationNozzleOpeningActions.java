
package org.openautomaker.base.printerControl.model.statetransitions.calibration;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.Macro;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.inject.printer_control.CalibrationPrinterErrorHandlerFactory;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.comms.commands.GCodeMacros;
import org.openautomaker.base.printerControl.comms.commands.MacroLoadException;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.NozzleHeater;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionActions;
import org.openautomaker.base.task_executor.Cancellable;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.base.utils.PrinterUtils;

import com.google.inject.assistedinject.Assisted;

import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.HeadEEPROMDataResponse;
import jakarta.inject.Inject;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.ReadOnlyFloatProperty;
import javafx.beans.property.SimpleFloatProperty;

/**
 *
 * @author tony
 */
public class CalibrationNozzleOpeningActions extends StateTransitionActions {

	private static final Logger LOGGER = LogManager.getLogger();

	private final Printer printer;
	private HeadEEPROMDataResponse savedHeadData;

	private final float bOffsetStartingValue = 0.75f;
	private final float nozzleTolerance = 0.05f;
	private float nozzle0BOffset = 0;
	private float nozzle1BOffset = 0;
	private final FloatProperty nozzlePosition = new SimpleFloatProperty();
	private final FloatProperty bPositionGUIT = new SimpleFloatProperty();

	private final CalibrationPrinterErrorHandler printerErrorHandler;

	private boolean failedActionPerformed = false;

	private final boolean safetyFeaturesRequired;

	//Dependencies
	private final TaskExecutor taskExecutor;
	private final PrinterUtils printerUtils;
	private final GCodeMacros gCodeMacros;
	private final HeadContainer headContainer;

	@Inject
	protected CalibrationNozzleOpeningActions(
			TaskExecutor taskExecutor,
			PrinterUtils printerUtils,
			GCodeMacros gCodeMacros,
			CalibrationPrinterErrorHandlerFactory calibrationPrinterErrorHandlerFactory,
			CalibrationUtils calibrationUtils,
			HeadContainer headContainer,
			@Assisted Printer printer,
			@Assisted("userCancellable") Cancellable userCancellable,
			@Assisted("errorCancellable") Cancellable errorCancellable,
			@Assisted boolean safetyFeaturesRequired) {

		super(userCancellable, errorCancellable);

		this.taskExecutor = taskExecutor;
		this.printerUtils = printerUtils;
		this.gCodeMacros = gCodeMacros;
		this.headContainer = headContainer;

		this.safetyFeaturesRequired = safetyFeaturesRequired;
		this.printer = printer;

		nozzlePosition.addListener((observable, oldValue, newValue) -> {
			taskExecutor.runOnGUIThread(() -> {
				// bPositionGUIT mirrors nozzlePosition but is only changed on the GUI Thread
				LOGGER.debug("set bPositionGUIT to " + nozzlePosition.get());
				bPositionGUIT.set(nozzlePosition.get());
			});
		});

		printerErrorHandler = calibrationPrinterErrorHandlerFactory.create(printer, errorCancellable);
		printerErrorHandler.registerForPrinterErrors();
		calibrationUtils.setCancelledIfPrinterDisconnected(printer, errorCancellable);
	}

	@Override
	public void initialise() {
		nozzle0BOffset = 0;
		nozzle1BOffset = 0;
	}

	public void doHeatingAction() throws RoboxCommsException, PrinterException, InterruptedException, CalibrationException {
		printerErrorHandler.registerForPrinterErrors();

		printer.inhibitHeadIntegrityChecks(true);
		printer.setPrinterStatus(PrinterStatus.CALIBRATING_NOZZLE_OPENING);

		savedHeadData = printer.readHeadEEPROM(true);

		HeadFile headReferenceData = headContainer.getHeadByID(savedHeadData.getHeadTypeCode());

		printer.homeAllAxes(true, userOrErrorCancellable);

		printer.goToTargetNozzleHeaterTemperature(
				0);
		if (printer.headProperty()
				.get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			printer.goToTargetNozzleHeaterTemperature(1);
		}

		waitOnNozzleTemperature(
				0);
		if (printerUtils.waitOnMacroFinished(printer, userOrErrorCancellable)) {
			return;
		}

		if (printer.headProperty()
				.get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			waitOnNozzleTemperature(1);
			if (printerUtils.waitOnMacroFinished(printer, userOrErrorCancellable)) {
				return;
			}
		}

		setMaximumOpen();

		try {
			gCodeMacros.sendMacroLineByLine(printer, Macro.BEFORE_NOZZLE_CALIBRATION, userOrErrorCancellable);
		}
		catch (IOException | MacroLoadException ex) {
			LOGGER.error("Failed to load pre-calibration macro");
			return;
		}

		if (headReferenceData != null) {
			setMinimumOpenZeroXYZ();
		}
		else {
			// We shouldn't ever get here, but just in case...
			LOGGER.debug("Setting B offsets to safe values ("
					+ headReferenceData.nozzles.get(0).defaultBOffset
					+ " - "
					+ headReferenceData.nozzles.get(1).defaultBOffset
					+ ")");
			printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
					savedHeadData.getUniqueID(),
					savedHeadData.getMaximumTemperature(),
					savedHeadData.getThermistorBeta(),
					savedHeadData.getThermistorTCal(),
					0,
					0,
					0,
					0.7f,
					savedHeadData.getFilamentID(0),
					savedHeadData.getFilamentID(1),
					0,
					0,
					0,
					-0.7f,
					savedHeadData.getLastFilamentTemperature(0),
					savedHeadData.getLastFilamentTemperature(1),
					savedHeadData.getHeadHours());
		}

		printer.readHeadEEPROM(false);

		printer.selectNozzle(1);
		printer.sendRawGCode("G0 X" + printer.getPrintVolumeCentre().getX(), false);
		printer.goToXYZPosition(printer.getPrintVolumeCentre().getX(),
				printer.getPrintVolumeCentre().getY(), 50);

		if (printerUtils.waitOnBusy(printer, userOrErrorCancellable)) {
			return;
		}

	}

	private void waitOnNozzleTemperature(int nozzleNumber) throws InterruptedException {
		NozzleHeater nozzleHeater = printer.headProperty().get()
				.getNozzleHeaters().get(nozzleNumber);
		printerUtils.waitUntilTemperatureIsReached(
				nozzleHeater.nozzleTemperatureProperty(), null,
				nozzleHeater
						.nozzleTargetTemperatureProperty().get(),
				5, 300, userOrErrorCancellable);
	}

	public void doNoMaterialCheckAction() throws CalibrationException, InterruptedException, PrinterException {
		extrudeUntilStall(0);
		pressuriseSystem(0);
		if (printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			extrudeUntilStall(1);
			pressuriseSystem(1);
		}
		Thread.sleep(3000);
		printer.selectNozzle(1);
		// 
		nozzlePosition.set(0);
	}

	private void setMaximumOpen() {
		try {
			HeadFile headReferenceData = headContainer.getHeadByID(savedHeadData.getHeadTypeCode());
			printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
					savedHeadData.getUniqueID(),
					savedHeadData.getMaximumTemperature(),
					savedHeadData.getThermistorBeta(),
					savedHeadData.getThermistorTCal(),
					savedHeadData.getNozzle1XOffset(),
					savedHeadData.getNozzle1YOffset(),
					savedHeadData.getNozzle1ZOffset(),
					headReferenceData.nozzles.get(0).maxBOffset,
					savedHeadData.getFilamentID(0),
					savedHeadData.getFilamentID(1),
					savedHeadData.getNozzle2XOffset(),
					savedHeadData.getNozzle2YOffset(),
					savedHeadData.getNozzle2ZOffset(),
					headReferenceData.nozzles.get(1).minBOffset,
					savedHeadData.getLastFilamentTemperature(0),
					savedHeadData.getLastFilamentTemperature(1),
					savedHeadData.getHeadHours());
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Unable to set max opening values in head");
		}
	}

	private void setMaximumOpenZeroXYZ() {
		try {
			HeadFile headReferenceData = headContainer.getHeadByID(savedHeadData.getHeadTypeCode());
			printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
					savedHeadData.getUniqueID(),
					savedHeadData.getMaximumTemperature(),
					savedHeadData.getThermistorBeta(),
					savedHeadData.getThermistorTCal(),
					0,
					0,
					0,
					headReferenceData.nozzles.get(0).maxBOffset,
					savedHeadData.getFilamentID(0),
					savedHeadData.getFilamentID(1),
					0,
					0,
					0,
					headReferenceData.nozzles.get(1).minBOffset,
					savedHeadData.getLastFilamentTemperature(0),
					savedHeadData.getLastFilamentTemperature(1),
					savedHeadData.getHeadHours());
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Unable to set max opening values in head");
		}
	}

	private void setMinimumOpenZeroXYZ() {
		try {
			HeadFile headReferenceData = headContainer.getHeadByID(savedHeadData.getHeadTypeCode());
			printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
					savedHeadData.getUniqueID(),
					savedHeadData.getMaximumTemperature(),
					savedHeadData.getThermistorBeta(),
					savedHeadData.getThermistorTCal(),
					0,
					0,
					0,
					headReferenceData.nozzles.get(0).minBOffset,
					savedHeadData.getFilamentID(0),
					savedHeadData.getFilamentID(1),
					0,
					0,
					0,
					headReferenceData.nozzles.get(1).maxBOffset,
					savedHeadData.getLastFilamentTemperature(0),
					savedHeadData.getLastFilamentTemperature(1),
					savedHeadData.getHeadHours());
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Unable to set max opening values in head");
		}
	}

	public void doT0Extrusion() throws PrinterException, CalibrationException {
		setMaximumOpenZeroXYZ();
		printer.selectNozzle(0);
		printer.openNozzleFully();
		if (printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			printer.sendRawGCode("G1 D15 F300", false);
		}
		else {
			printer.sendRawGCode("G1 E15 F300", false);
		}
		printerUtils.waitOnBusy(printer, userOrErrorCancellable);
		printer.closeNozzleFully();
	}

	public void doT1Extrusion() throws PrinterException, CalibrationException {
		printer.selectNozzle(1);
		printer.openNozzleFully();
		if (printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			printer.sendRawGCode("G1 E15 F300", false);
		}
		else {
			printer.sendRawGCode("G1 E45 F600", false);
		}
		printerUtils.waitOnBusy(printer, userOrErrorCancellable);
		setMinimumOpenZeroXYZ();
		printer.selectNozzle(0);
		printer.selectNozzle(1);
		printer.closeNozzleFully();
	}

	public void doPreCalibrationPrimingFine() throws RoboxCommsException, CalibrationException, PrinterException {
		nozzlePosition.set(0);

		LOGGER.debug("Setting B offsets to calibration values ("
				+ bOffsetStartingValue
				+ " - "
				+ -bOffsetStartingValue
				+ ")");

		printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
				savedHeadData.getUniqueID(),
				savedHeadData.getMaximumTemperature(),
				savedHeadData.getThermistorBeta(),
				savedHeadData.getThermistorTCal(),
				0,
				0,
				0,
				bOffsetStartingValue,
				savedHeadData.getFilamentID(0),
				savedHeadData.getFilamentID(1),
				0,
				0,
				0,
				-bOffsetStartingValue,
				savedHeadData.getLastFilamentTemperature(0),
				savedHeadData.getLastFilamentTemperature(1),
				savedHeadData.getHeadHours());
		extrudeUntilStall(0);
		pressuriseSystem(0);
	}

	public void doCalibrateFineNozzle() throws CalibrationException {
		printer.gotoNozzlePosition(nozzlePosition.get());
	}

	public void doIncrementFineNozzlePosition() throws CalibrationException, InterruptedException {
		nozzlePosition.set(nozzlePosition.get() + nozzleTolerance);
		LOGGER.debug("(FINE) nozzle position set to " + nozzlePosition.get());
		if (nozzlePosition.get() > 1f + nozzleTolerance) {
			throw new CalibrationException("Nozzle position beyond limit");
		}
		printer.gotoNozzlePosition(nozzlePosition.get());
		Thread.sleep(1000);
	}

	public void doIncrementFillNozzlePosition() throws CalibrationException, InterruptedException {
		nozzlePosition.set(nozzlePosition.get() + nozzleTolerance);
		LOGGER.debug("(FILL) nozzle position set to " + nozzlePosition);
		if (nozzlePosition.get() > 1 + nozzleTolerance) {
			throw new CalibrationException("Nozzle position beyond limit");
		}
		printer.gotoNozzlePosition(nozzlePosition.get());
		Thread.sleep(1000);
	}

	public void doPreCalibrationPrimingFill() throws RoboxCommsException, CalibrationException {
		nozzlePosition.set(0);
		extrudeUntilStall(1);
		pressuriseSystem(1);
	}

	public void doCalibrateFillNozzle() throws CalibrationException {
		printer.gotoNozzlePosition(nozzlePosition.get());
	}

	public void doFinaliseCalibrateFineNozzle() throws PrinterException, CalibrationException {
		printer.closeNozzleFully();
		// calculate offset
		LOGGER.debug("(FINE) finalise nozzle position set at " + nozzlePosition.get());
		nozzle0BOffset = bOffsetStartingValue - nozzleTolerance + nozzlePosition.get();
	}

	public void doFinaliseCalibrateFillNozzle() throws PrinterException, CalibrationException {
		printer.closeNozzleFully();
		// calculate offset
		LOGGER.debug("(FILL) finalise nozzle position set at " + nozzlePosition);
		nozzle1BOffset = -bOffsetStartingValue + nozzleTolerance - nozzlePosition.get();
	}

	public void doConfirmNoMaterialAction() throws PrinterException, RoboxCommsException, InterruptedException, CalibrationException {
		// set to just about to be open
		saveSettings();
		printer.closeNozzleFully();

		printer.selectNozzle(0);
		extrudeUntilStall(0);
		pressuriseSystem(0);

		Thread.sleep(3000);

		printer.selectNozzle(1);
		extrudeUntilStall(1);
		pressuriseSystem(1);
	}

	public void doConfirmMaterialExtrudingAction() throws PrinterException, CalibrationException {
		printer.selectNozzle(0);
		extrudeUntilStall(0);
		printer.openNozzleFully();

		if (printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			printer.sendRawGCode("G1 D10 F50", false);
		}
		else {
			printer.sendRawGCode("G1 E10 F50", false);
		}

		if (printerUtils.waitOnBusy(printer, userOrErrorCancellable)) {
			return;
		}

		printer.selectNozzle(1);
		extrudeUntilStall(1);
		printer.openNozzleFully();
		printer.sendRawGCode("G1 E10 F50", false);
		if (printerUtils.waitOnBusy(printer, userOrErrorCancellable)) {
			return;
		}
	}

	private void saveSettings() throws RoboxCommsException {
		LOGGER.debug("save new head data");
		printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
				savedHeadData.getUniqueID(),
				savedHeadData.getMaximumTemperature(),
				savedHeadData.getThermistorBeta(),
				savedHeadData.getThermistorTCal(),
				savedHeadData.getNozzle1XOffset(),
				savedHeadData.getNozzle1YOffset(),
				savedHeadData.getNozzle1ZOffset(),
				nozzle0BOffset,
				savedHeadData.getFilamentID(0),
				savedHeadData.getFilamentID(1),
				savedHeadData.getNozzle2XOffset(),
				savedHeadData.getNozzle2YOffset(),
				savedHeadData.getNozzle2ZOffset(),
				nozzle1BOffset,
				savedHeadData.getLastFilamentTemperature(0),
				savedHeadData.getLastFilamentTemperature(1),
				savedHeadData.getHeadHours());

	}

	private void restoreHeadData() {
		if (savedHeadData != null) {
			try {
				LOGGER.debug("Restore head data");
				printer.transmitWriteHeadEEPROM(savedHeadData.getHeadTypeCode(),
						savedHeadData.getUniqueID(),
						savedHeadData.getMaximumTemperature(),
						savedHeadData.getThermistorBeta(),
						savedHeadData.getThermistorTCal(),
						savedHeadData.getNozzle1XOffset(),
						savedHeadData.getNozzle1YOffset(),
						savedHeadData.getNozzle1ZOffset(),
						savedHeadData.getNozzle1BOffset(),
						savedHeadData.getFilamentID(0),
						savedHeadData.getFilamentID(1),
						savedHeadData.getNozzle2XOffset(),
						savedHeadData.getNozzle2YOffset(),
						savedHeadData.getNozzle2ZOffset(),
						savedHeadData.getNozzle2BOffset(),
						savedHeadData.getLastFilamentTemperature(0),
						savedHeadData.getLastFilamentTemperature(1),
						savedHeadData.getHeadHours());
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Unable to restore head! " + ex);
			}
		}
	}

	private void extrudeUntilStall(int nozzleNumber) {
		try {
			// select nozzle
			printer.selectNozzle(nozzleNumber);
			// G36 = extrude until stall E700 = top extruder F2000 = feed rate mm/min (?)
			// extrude either requested volume or until filament slips
			if (nozzleNumber == 0
					&& printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
				printer.sendRawGCode("G36 D100 F800", false);
			}
			else {
				printer.sendRawGCode("G36 E100 F800", false);
			}
			printerUtils.waitOnBusy(printer, userOrErrorCancellable);

		}
		catch (PrinterException ex) {
			LOGGER.error("Error in needle valve priming");
		}
	}

	public ReadOnlyFloatProperty getBPositionGUITProperty() {
		return bPositionGUIT;
	}

	private void pressuriseSystem(int nozzleNumber) {
		if (nozzleNumber == 0
				&& printer.headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			printer.sendRawGCode("G1 D4 F400", false);
		}
		else {
			printer.sendRawGCode("G1 E4 F400", false);
		}
		printerUtils.waitOnBusy(printer, userOrErrorCancellable);
	}

	public void doFinishedAction() {
		try {
			printerErrorHandler.deregisterForPrinterErrors();
			saveSettings();
			resetPrinter();
		}
		catch (CalibrationException | RoboxCommsException | PrinterException ex) {
			LOGGER.error("Error in finished action: " + ex);
		}
		printer.setPrinterStatus(PrinterStatus.IDLE);
	}

	public void doFailedAction() {
		// this can be called twice if an error occurs
		if (failedActionPerformed) {
			return;
		}

		failedActionPerformed = true;
		try {
			LOGGER.info("failed action");
			printerErrorHandler.deregisterForPrinterErrors();
			restoreHeadData();
			resetPrinter();
			abortAnyOngoingPrint();
			printer.setPrinterStatus(PrinterStatus.IDLE);
		}
		catch (CalibrationException | PrinterException ex) {
			LOGGER.error("Error in failed action: " + ex);
		}
	}

	@Override
	public void whenUserCancelDetected() {
		LOGGER.info("user cancel action");
		restoreHeadData();
		abortAnyOngoingPrint();
	}

	@Override
	public void whenErrorDetected() {
		LOGGER.info("error cancel action");
		printerErrorHandler.deregisterForPrinterErrors();
		restoreHeadData();
		abortAnyOngoingPrint();
	}

	@Override
	public void resetAfterCancelOrError() {
		try {
			doFailedAction();
		}
		catch (Exception ex) {
			ex.printStackTrace();
			LOGGER.error("error resetting printer " + ex);
		}
	}

	private void resetPrinter() throws PrinterException, CalibrationException {
		printerErrorHandler.deregisterForPrinterErrors();
		printer.closeNozzleFully();
		LOGGER.debug("Switch heaters and lights off");
		switchHeatersAndHeadLightOff();
		LOGGER.debug("bring bed to front");
		doBringBedToFrontAndRaiseHead();
		printer.inhibitHeadIntegrityChecks(false);
		printerUtils.waitOnBusy(printer, (Cancellable) null);
	}

	public void doBringBedToFrontAndRaiseHead() throws PrinterException, CalibrationException {
		printer.switchToAbsoluteMoveMode();
		printer.goToXYZPosition(105, 150, 25);
		printerUtils.waitOnBusy(printer, userOrErrorCancellable);
	}

	private void switchHeatersAndHeadLightOff() throws PrinterException {
		printer.switchAllNozzleHeatersOff();
		printer.switchOffHeadLEDs();
	}

	private void abortAnyOngoingPrint() {
		try {
			if (printer.canCancelProperty().get()) {
				LOGGER.debug("Cancel ongoing job");
				printer.cancel(null, safetyFeaturesRequired);
			}
			else {
				LOGGER.debug("Nothing ongoing to cancel");
			}
		}
		catch (PrinterException ex) {
			LOGGER.error("Failed to abort print - " + ex.getMessage());
		}
	}
}
