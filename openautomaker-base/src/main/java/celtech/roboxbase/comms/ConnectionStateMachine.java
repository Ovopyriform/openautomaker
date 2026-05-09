package celtech.roboxbase.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterEdition;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.utils.PrinterUtils;

import com.vdurmont.semver4j.Semver;

import celtech.roboxbase.comms.exceptions.PortNotFoundException;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.FirmwareResponse;
import celtech.roboxbase.comms.rx.PrinterIDResponse;
import celtech.roboxbase.comms.rx.StatusResponse;
import celtech.roboxbase.comms.tx.RoboxTxPacketFactory;
import celtech.roboxbase.comms.tx.StatusRequest;
import celtech.roboxbase.comms.tx.TxPacketTypeEnum;
import javafx.scene.paint.Color;

/**
 * The printer connection FSM, decoupled from thread lifecycle. Transitions:
 * FOUND → CHECKING_FIRMWARE → CHECKING_ID → RESETTING_ID →
 * DETERMINING_PRINTER_STATUS → CONNECTED → DISCONNECTED
 */
class ConnectionStateMachine {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MAX_STATUS_RETRIES = 3;

	private final ConnectionStateCallbacks ctx;

	// FSM-internal state
	private volatile RoboxCommsState state = RoboxCommsState.FOUND;
	private volatile boolean keepRunning = true;
	private boolean isConnected = false;
	private boolean loadingFirmware = false;
	private int statusRequestCount = 0;
	private PrinterIDResponse lastPrinterIDResponse = null;
	private String printerName = null;
	private Semver firmwareVersionInUse = null;

	ConnectionStateMachine(ConnectionStateCallbacks ctx) {
		this.ctx = ctx;
	}

	boolean isConnected() {
		return isConnected;
	}

	RoboxCommsState getState() {
		return state;
	}

	void shutdown() {
		keepRunning = false;
		setState(RoboxCommsState.SHUTTING_DOWN);
	}

	private void setState(RoboxCommsState next) {
		LOGGER.debug("{} -> {}", state, next);
		state = next;
	}

	void run() {
		while (keepRunning) {
			switch (state) {
			case FOUND -> runFound();
			case CHECKING_FIRMWARE -> runCheckingFirmware();
			case CHECKING_ID -> runCheckingId();
			case RESETTING_ID -> runResettingId();
			case DETERMINING_PRINTER_STATUS -> runDeterminingPrinterStatus();
			case CONNECTED -> runConnected();
			case DISCONNECTED -> LOGGER.debug("state is disconnected");
			default -> {
			}
			}
		}

		LOGGER.info("Handler for {} beginning exit routine - state was {}", ctx.getPrinterHandle().getConnectionHandle(), state);
		runFinalShutdown();
		LOGGER.info("Handler for {} exited", ctx.getPrinterHandle().getConnectionHandle());
	}

	private void runFound() {
		LOGGER.debug("Trying to connect to printer in {}", ctx.getPrinterHandle());
		try {
			if (ctx.connectToPrinter()) {
				LOGGER.debug("Connected to Robox on {}", ctx.getPrinterHandle());
				isConnected = true;
				ctx.setConnected(true);
				statusRequestCount = 0;
				setState(RoboxCommsState.CHECKING_FIRMWARE);
			}
			else {
				LOGGER.error("Failed to connect to Robox on {}", ctx.getPrinterHandle());
				shutdown();
			}
		}
		catch (PortNotFoundException ex) {
			LOGGER.info("Port not ready for comms - windows needs time to settle...  If you're not running on windows this... is... bad...");
			shutdown();
		}
	}

	private void runCheckingFirmware() {
		LOGGER.debug("Check firmware {}", ctx.getPrinterHandle());

		if (loadingFirmware) {
			try {
				Thread.sleep(200);
			}
			catch (InterruptedException ex) {
				LOGGER.error("Interrupted while waiting for firmware to be loaded {}", ex.toString());
			}
			return;
		}

		try {
			Printer printer = ctx.getPrinter();
			FirmwareResponse firmwareResponse = printer.readFirmwareVersion();
			firmwareVersionInUse = firmwareResponse.getFirmwareVersion();
			ctx.setFirmwareVersionInUse(firmwareVersionInUse);

			Semver requiredVersion = ctx.getRequiredFirmwareVersion();
			boolean loadRequiredFirmware = false;

			if (!firmwareVersionInUse.isEquivalentTo(requiredVersion)) {
				LOGGER.warn(
						String.format("Firmware version is %.0f and should be %.0f.", firmwareVersionInUse.getValue(), requiredVersion.getValue()));

				lastPrinterIDResponse = printer.readPrinterID();
				if (!lastPrinterIDResponse.isValid()) {
					LOGGER.warn("Printer does not have a valid ID!");
					setState(RoboxCommsState.RESETTING_ID);
					return;
				}

				if (firmwareVersionInUse.getMajor() >= 691) {
					if (!checkSdCardPresent(printer))
						return;
				}

				SystemNotificationManager snm = ctx.getSystemNotificationManager();
				if (firmwareVersionInUse.isLowerThan(requiredVersion))
					loadRequiredFirmware = snm.askUserToUpdateFirmware(printer);
				else
					loadRequiredFirmware = snm.showDowngradeFirmwareDialog(printer);
			}

			if (loadRequiredFirmware) {
				loadingFirmware = true;
				ctx.loadFirmware(ctx.requiredFirmwareFilePath());
			}
			else {
				moveOnFromFirmwareCheck();
			}
		}
		catch (PrinterException ex) {
			LOGGER.debug("Exception whilst checking firmware version: {}", ex.toString());
			shutdown();
		}
	}

	private boolean checkSdCardPresent(Printer printer) {
		try {
			StatusRequest request = (StatusRequest) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST);
			StatusResponse response = (StatusResponse) ctx.writeThroughQueue(request, true);
			if (!response.issdCardPresent()) {
				LOGGER.warn("SD Card not present");
				ctx.getSystemNotificationManager().processErrorPacketFromPrinter(celtech.roboxbase.comms.rx.FirmwareError.SD_CARD, printer);
				shutdown();
				return false;
			}
			else {
				ctx.getSystemNotificationManager().clearAllDialogsOnDisconnect();
				return true;
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Failure during printer status request. {}", ex.toString());
			return false;
		}
	}

	private void moveOnFromFirmwareCheck() {
		if (ctx.isSuppressingPrinterIDChecks())
			setState(RoboxCommsState.DETERMINING_PRINTER_STATUS);
		else
			setState(RoboxCommsState.CHECKING_ID);
		loadingFirmware = false;
	}

	private void runCheckingId() {
		LOGGER.debug("Check id {}", ctx.getPrinterHandle());
		try {
			Printer printer = ctx.getPrinter();
			lastPrinterIDResponse = printer.readPrinterID();
			if (!lastPrinterIDResponse.isValid()) {
				LOGGER.warn("Printer does not have a valid ID: {}", lastPrinterIDResponse.toString());
				setState(RoboxCommsState.RESETTING_ID);
				return;
			}

			printerName = lastPrinterIDResponse.getPrinterFriendlyName();
			PrinterContainer printerContainer = ctx.getPrinterContainer();
			SystemNotificationManager snm = ctx.getSystemNotificationManager();

			if (printerName == null || (printerName.length() > 0 && printerName.charAt(0) == '\0')) {
				LOGGER.debug("Connected to unknown printer - setting to RBX01");
				snm.showNoPrinterIDDialog(printer);
				printerName = PrinterContainer.defaultPrinterID;
			}
			else {
				LOGGER.debug("Connected to printer {}", printerName);
			}

			PrinterDefinitionFile printerConfigFile = null;
			if (lastPrinterIDResponse.getModel() != null)
				printerConfigFile = printerContainer.getPrinterByID(lastPrinterIDResponse.getModel());
			if (printerConfigFile == null)
				printerConfigFile = printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID);

			printer.setPrinterConfiguration(printerConfigFile);
			for (PrinterEdition edition : printerConfigFile.editions) {
				if (edition.typeCode.equalsIgnoreCase(lastPrinterIDResponse.getEdition())) {
					printer.setPrinterEdition(edition);
					break;
				}
			}

			if (ctx.appliesAmbientColourOnConnect())
				printer.setAmbientLEDColour(Color.web(lastPrinterIDResponse.getPrinterColour()));
		}
		catch (PrinterException ex) {
			LOGGER.error("Error whilst checking printer ID");
		}

		setState(RoboxCommsState.DETERMINING_PRINTER_STATUS);
	}

	private void runResettingId() {
		LOGGER.debug("Resetting identity for {}", ctx.getPrinterHandle());
		Printer printer = ctx.getPrinter();
		RoboxResetIDResult resetResult = ctx.getSystemNotificationManager().askUserToResetPrinterID(printer, lastPrinterIDResponse);

		switch (resetResult) {
		case RESET_SUCCESSFUL:
			LOGGER.debug("Reset ID of {}", ctx.getPrinterHandle());
			setState(RoboxCommsState.CHECKING_ID);
			break;
		case RESET_TEMPORARY:
			LOGGER.debug("Set temporary identity for {}", ctx.getPrinterHandle());
			setState(RoboxCommsState.DETERMINING_PRINTER_STATUS);
			break;
		case RESET_NOT_DONE:
			LOGGER.debug("Set default ID for {}", ctx.getPrinterHandle());
			setState(RoboxCommsState.DETERMINING_PRINTER_STATUS);
			printer.setPrinterConfiguration(ctx.getPrinterContainer().getPrinterByID(PrinterContainer.defaultPrinterID));
			break;
		case RESET_CANCELLED:
		case RESET_FAILED:
		default:
			LOGGER.debug("Failed to set printer ID for {}", ctx.getPrinterHandle());
			shutdown();
			break;
		}
	}

	private void runDeterminingPrinterStatus() {
		LOGGER.debug("Determining printer status on port {}", ctx.getPrinterHandle());
		try {
			StatusResponse statusResponse = (StatusResponse) ctx.writeThroughQueue(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST),
					true);

			determinePrinterStatus(statusResponse);

			LOGGER.debug("Printer connected");
			ctx.notifyPrinterConnected();

			String printerIDString = (lastPrinterIDResponse != null && lastPrinterIDResponse.getAsFormattedString() != null)
					? lastPrinterIDResponse.getAsFormattedString()
					: null;

			ctx.persistConnectionInfo(printerIDString, firmwareVersionInUse);

			setState(RoboxCommsState.CONNECTED);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Failed to determine printer status on {}", (printerName != null ? printerName : "unknown printer"));
			shutdown();
		}
	}

	private void determinePrinterStatus(StatusResponse statusResponse) {
		if (statusResponse == null)
			return;

		if (!PrinterUtils.printJobIDIndicatesPrinting(statusResponse.getRunningPrintJobID()))
			return;

		LOGGER.debug("{} is printing", (printerName != null ? printerName : "Robox"));
	}

	private void runConnected() {
		try {
			if (!ctx.isSuspendingStatusChecks() && isConnected && state == RoboxCommsState.CONNECTED) {
				try {
					ctx.writeThroughQueue(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST), false);
					ctx.writeThroughQueue(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.REPORT_ERRORS), false);

					if (ctx.requiresPeriodicIdRefresh())
						ctx.writeThroughQueue(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID), false);

					statusRequestCount = 0;
				}
				catch (RoboxCommsException ex) {
					if (isConnected) {
						++statusRequestCount;
						if (statusRequestCount > MAX_STATUS_RETRIES) {
							LOGGER.warn("Failure during printer status request: {}", ex.toString());
							shutdown();
						}
					}
				}
			}

			Thread.sleep(ctx.getSleepBetweenStatusChecks());
		}
		catch (InterruptedException ex) {
			LOGGER.debug("Comms interrupted");
		}
	}

	private void runFinalShutdown() {
		LOGGER.debug("Shutdown command interface...");
		keepRunning = false;
		setState(RoboxCommsState.SHUTTING_DOWN);
		ctx.disconnectPrinter();
		ctx.cancelFirmwareLoadIfRunning();
		LOGGER.debug("set state to disconnected");
		setState(RoboxCommsState.DISCONNECTED);
		isConnected = false;
		ctx.setConnected(false);
		ctx.shutdownWriteQueue();
		LOGGER.debug("Shutdown command interface for {} complete", ctx.getPrinterHandle().getConnectionHandle());
		ctx.notifyPrinterDisconnected();
	}
}
