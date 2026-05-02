package celtech.roboxbase.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterEdition;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.services.firmware.FirmwareLoadResult;
import org.openautomaker.base.services.firmware.FirmwareLoadService;
import org.openautomaker.base.utils.PrinterUtils;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.preference.application.RequiredFirmwareVersionPreference;
import org.openautomaker.environment.preference.printer.LastFirmwareVersionPreference;
import org.openautomaker.environment.preference.printer.LastSerialNumberPreference;
import org.openautomaker.environment.preference.root.FirmwarePathPreference;

import com.vdurmont.semver4j.Semver;

import celtech.roboxbase.comms.async.AsyncWriteThread;
import celtech.roboxbase.comms.async.CommandPacket;
import celtech.roboxbase.comms.exceptions.PortNotFoundException;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import celtech.roboxbase.comms.rx.FirmwareError;
import celtech.roboxbase.comms.rx.FirmwareResponse;
import celtech.roboxbase.comms.rx.PrinterIDResponse;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.rx.StatusResponse;
import celtech.roboxbase.comms.tx.RoboxTxPacket;
import celtech.roboxbase.comms.tx.RoboxTxPacketFactory;
import celtech.roboxbase.comms.tx.StatusRequest;
import celtech.roboxbase.comms.tx.TxPacketTypeEnum;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.paint.Color;

public abstract class CommandInterface extends Thread {

	protected boolean keepRunning = true;

	protected final Logger LOGGER = LogManager.getLogger();

	private final Semver requiredFirmwareVersion;
	protected Semver fFirmwareVersionInUse;

	protected PrinterStatusConsumer controlInterface = null;
	protected DetectedDevice printerHandle = null;
	protected Printer printerToUse = null;
	protected String printerFriendlyName = "Robox";
	protected RoboxCommsState commsState = RoboxCommsState.FOUND;
	protected PrinterID printerID = new PrinterID();

	protected final FirmwareLoadService firmwareLoadService;

	//protected String requiredFirmwareVersionString = "";
	//protected float requiredFirmwareVersion = 0;
	//protected float firmwareVersionInUse = 0;

	protected boolean suppressPrinterIDChecks = false;
	protected int sleepBetweenStatusChecks = 1000;
	private boolean loadingFirmware = false;

	protected boolean suspendStatusChecks = false;
	private final boolean localPrinter;

	private String printerName = null;

	private PrinterIDResponse lastPrinterIDResponse = null;

	private boolean isConnected = false;
	private int statusRequestCount = 0;
	private static final int maxAllowedStatusRequestCount = 3;

	private final AsyncWriteThread asyncWriteThread;

	private final SystemNotificationManager systemNotificationManager;

	private final FirmwarePathPreference firmwarePathPreference;
	private final LastSerialNumberPreference lastSerialNumberPreference;
	private final LastFirmwareVersionPreference lastFirmwareVersionPreference;

	private PrinterContainer printerContainer;

	private final OpenAutomakerEnv environment;

	/**
	 *
	 * @param controlInterface
	 * @param printerHandle
	 * @param suppressPrinterIDChecks
	 * @param sleepBetweenStatusChecks
	 */
	protected CommandInterface(
			OpenAutomakerEnv environment,
			SystemNotificationManager systemNotificationManager,
			RequiredFirmwareVersionPreference requiredFirmwareVersionPreference,
			FirmwarePathPreference firmwarePathPreference,
			LastSerialNumberPreference lastSerialNumberPreference,
			LastFirmwareVersionPreference lastFirmwareVersionPreference,
			FirmwareLoadService firmwareLoadService,
			PrinterContainer printerContainer,
			PrinterStatusConsumer controlInterface,
			DetectedDevice printerHandle,
			boolean suppressPrinterIDChecks,
			int sleepBetweenStatusChecks,
			boolean localPrinter) {

		this.environment = environment;
		this.systemNotificationManager = systemNotificationManager;
		this.firmwarePathPreference = firmwarePathPreference;
		this.lastSerialNumberPreference = lastSerialNumberPreference;
		this.lastFirmwareVersionPreference = lastFirmwareVersionPreference;
		this.firmwareLoadService = firmwareLoadService;
		this.printerContainer = printerContainer;

		this.controlInterface = controlInterface;
		this.printerHandle = printerHandle;
		this.suppressPrinterIDChecks = suppressPrinterIDChecks;
		this.sleepBetweenStatusChecks = sleepBetweenStatusChecks;
		this.localPrinter = localPrinter;

		this.setDaemon(true);
		this.setName("CommandInterface|" + printerHandle.getConnectionHandle());
		this.setPriority(8);

		asyncWriteThread = new AsyncWriteThread(this, printerHandle.getConnectionHandle());

		requiredFirmwareVersion = requiredFirmwareVersionPreference.getValue();

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Firmware property value:" + requiredFirmwareVersion.getValue());

		firmwareLoadService.setOnSucceeded((WorkerStateEvent t) -> {
			FirmwareLoadResult result = (FirmwareLoadResult) t.getSource().getValue();
			boolean firmwareUpdatedOK = false;
			if (result.getStatus() == FirmwareLoadResult.SUCCESS) {
				systemNotificationManager.showFirmwareUpgradeStatusNotification(result);
				//                try
				//                {
				//                    printerToUse.transmitUpdateFirmware(result.getFirmwareID());
				shutdown();
				firmwareUpdatedOK = true;
				//                } catch (PrinterException ex)
				//                {
				//                }
			}

			if (!firmwareUpdatedOK) {
				systemNotificationManager.showFirmwareUpgradeStatusNotification(result);
			}
		});

		firmwareLoadService.setOnFailed((WorkerStateEvent t) -> {
			FirmwareLoadResult result = (FirmwareLoadResult) t.getSource().getValue();
			systemNotificationManager.showFirmwareUpgradeStatusNotification(result);
		});

		systemNotificationManager.configureFirmwareProgressDialog(firmwareLoadService);
	}

	@Override
	public void run() {
		//Semver firmwareVer = null;

		while (keepRunning) {
			switch (commsState) {
				case FOUND:
					LOGGER.debug("Trying to connect to printer in " + printerHandle);

					try {
						boolean printerCommsOpen = connectToPrinter();
						if (printerCommsOpen) {
							LOGGER.debug("Connected to Robox on " + printerHandle);
							statusRequestCount = 0;
							commsState = RoboxCommsState.CHECKING_FIRMWARE;
						}
						else {
							LOGGER.error("Failed to connect to Robox on " + printerHandle);
							shutdown();
						}
					}
					catch (PortNotFoundException ex) {
						LOGGER.info("Port not ready for comms - windows needs time to settle...  If you're not running on windoes this... is... bad...");
						shutdown();
					}
					break;

				case CHECKING_FIRMWARE:
					LOGGER.debug("Check firmware " + printerHandle);
					if (loadingFirmware) {
						try {
							Thread.sleep(200);
						}
						catch (InterruptedException ex) {
							LOGGER.error("Interrupted while waiting for firmware to be loaded " + ex);
						}
						break;
					}

					FirmwareResponse firmwareResponse = null;
					boolean loadRequiredFirmware = false;

					try {
						firmwareResponse = printerToUse.readFirmwareVersion();
						// Set the firmware version so that the system knows which response
						// packet length to use.
						fFirmwareVersionInUse = firmwareResponse.getFirmwareVersion();

						if (!fFirmwareVersionInUse.isEquivalentTo(requiredFirmwareVersion)) {

							// The firmware version is different to that associated with AutoMaker
							LOGGER.warn(String.format("Firmware version is %.0f and should be %.0f.", fFirmwareVersionInUse.getValue(), requiredFirmwareVersion.getValue()));

							// Check that the printer ID is valid, as updating the firmware with a corrupt printer ID
							// can cause serious problems.
							lastPrinterIDResponse = printerToUse.readPrinterID();
							if (!lastPrinterIDResponse.isValid()) {
								LOGGER.warn("Printer does not have a valid ID!");
								commsState = RoboxCommsState.RESETTING_ID;
								break;
							}

							if (fFirmwareVersionInUse.getMajor() >= 691) {
								// Is the SD card present?
								try {
									StatusRequest request = (StatusRequest) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST);
									StatusResponse response = (StatusResponse) writeToPrinter(request, true);
									if (!response.issdCardPresent()) {
										LOGGER.warn("SD Card not present");
										systemNotificationManager.processErrorPacketFromPrinter(FirmwareError.SD_CARD, printerToUse);
										shutdown();
										break;
									}
									else {
										systemNotificationManager.clearAllDialogsOnDisconnect();
									}
								}
								catch (RoboxCommsException ex) {
									LOGGER.error("Failure during printer status request. " + ex.toString());
									break;
								}
							}

							if (fFirmwareVersionInUse.isLowerThan(requiredFirmwareVersion)) {
								// Tell the user to update
								loadRequiredFirmware = systemNotificationManager
										.askUserToUpdateFirmware(printerToUse);
							}
							//TODO: This seems like it shouldn't happen.
							else {
								// If printer firmware is more than required, we ask the user if they are sure of the downgrade
								loadRequiredFirmware = systemNotificationManager
										.showDowngradeFirmwareDialog(printerToUse);
							}
						}

						if (loadRequiredFirmware) {
							loadingFirmware = true;
							loadFirmware(firmwarePathPreference.getValue().resolve("robox_r" + requiredFirmwareVersion.getMajor() + ".bin").toString());
						}
						else {
							moveOnFromFirmwareCheck(firmwareResponse);
						}
					}
					catch (PrinterException ex) {
						LOGGER.debug("Exception whilst checking firmware version: " + ex);
						shutdown();
					}
					break;

				case CHECKING_ID:
					LOGGER.debug("Check id " + printerHandle);
					try {
						// Printer ID may or may not have been read, so get it again.
						lastPrinterIDResponse = printerToUse.readPrinterID();
						if (!lastPrinterIDResponse.isValid()) {
							LOGGER.warn("Printer does not have a valid ID: " + lastPrinterIDResponse.toString());
							commsState = RoboxCommsState.RESETTING_ID;
							break;
						}

						printerName = lastPrinterIDResponse.getPrinterFriendlyName();

						if (printerName == null
								|| (printerName.length() > 0
										&& printerName.charAt(0) == '\0')) {
							LOGGER.debug("Connected to unknown printer - setting to RBX01");
							systemNotificationManager.showNoPrinterIDDialog(printerToUse);
							printerName = PrinterContainer.defaultPrinterID;
						}
						else {
							LOGGER.debug("Connected to printer " + printerName);
						}

						PrinterDefinitionFile printerConfigFile = null;

						if (lastPrinterIDResponse.getModel() != null) {
							printerConfigFile = printerContainer.getPrinterByID(lastPrinterIDResponse.getModel());
						}

						if (printerConfigFile == null) {
							printerConfigFile = printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID);
						}
						printerToUse.setPrinterConfiguration(printerConfigFile);
						for (PrinterEdition editionUnderExamination : printerConfigFile.editions) {
							if (editionUnderExamination.typeCode.equalsIgnoreCase(lastPrinterIDResponse.getEdition())) {
								printerToUse.setPrinterEdition(editionUnderExamination);
								break;
							}
						}
						if (!(this instanceof RoboxRemoteCommandInterface))
							printerToUse.setAmbientLEDColour(Color.web(lastPrinterIDResponse.getPrinterColour()));
					}
					catch (PrinterException ex) {
						LOGGER.error("Error whilst checking printer ID");
					}

					commsState = RoboxCommsState.DETERMINING_PRINTER_STATUS;
					break;

				case RESETTING_ID:
					LOGGER.debug("Resetting identity for " + printerHandle);
					RoboxResetIDResult resetResult = RoboxResetIDResult.RESET_NOT_DONE;

					resetResult = systemNotificationManager
							.askUserToResetPrinterID(printerToUse, lastPrinterIDResponse);

					switch (resetResult) {
						case RESET_SUCCESSFUL: // Reset of printer id successful
							LOGGER.debug("Reset ID of " + printerHandle);
							commsState = RoboxCommsState.CHECKING_ID;
							break;
						case RESET_TEMPORARY: // Temporary set of printer type successful.
							LOGGER.debug("Set temporary identity for " + printerHandle);
							commsState = RoboxCommsState.DETERMINING_PRINTER_STATUS;
							break;
						case RESET_NOT_DONE: // Not done - set a default.
							LOGGER.debug("Set default ID for " + printerHandle);
							commsState = RoboxCommsState.DETERMINING_PRINTER_STATUS;
							printerToUse.setPrinterConfiguration(printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID));
							break;
						case RESET_CANCELLED:
						case RESET_FAILED:
						default: // Cancelled or failed. Disconnect printer.
							LOGGER.debug("Failed to set printer ID for " + printerHandle);
							shutdown();
							break;
					}
					break;

				case DETERMINING_PRINTER_STATUS:
					LOGGER.debug("Determining printer status on port " + printerHandle);

					try {
						StatusResponse statusResponse = (StatusResponse) writeToPrinter(
								RoboxTxPacketFactory.createPacket(
										TxPacketTypeEnum.STATUS_REQUEST),
								true);

						determinePrinterStatus(statusResponse);

						LOGGER.debug("Printer connected");

						controlInterface.printerConnected(printerHandle);

						//Stash the connected printer info
						String printerIDToUse = null;
						if (lastPrinterIDResponse != null
								&& lastPrinterIDResponse.getAsFormattedString() != null) {
							printerIDToUse = lastPrinterIDResponse.getAsFormattedString();
						}

						lastSerialNumberPreference.setValue(printerIDToUse);
						lastFirmwareVersionPreference.setValue(fFirmwareVersionInUse);

						// Two more preferences
						//CoreMemory.getInstance().setLastPrinterSerial(printerIDToUse);
						//CoreMemory.getInstance().setLastPrinterFirmwareVersion(firmwareVer);

						commsState = RoboxCommsState.CONNECTED;
					}
					catch (RoboxCommsException ex) {
						if (printerFriendlyName != null) {
							LOGGER.error("Failed to determine printer status on "
									+ printerFriendlyName);
						}
						else {
							LOGGER.error("Failed to determine printer status on unknown printer");
						}
						shutdown();
					}

					break;
				case CONNECTED:
					try {
						if (!suspendStatusChecks && isConnected && commsState == RoboxCommsState.CONNECTED) {
							try {
								writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST));

								writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.REPORT_ERRORS));

								// If we're talking to a remote printer we need to keep checking data that may have changed without us knowing
								if (this instanceof RoboxRemoteCommandInterface) {
									writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
								}
								statusRequestCount = 0;
							}
							catch (RoboxCommsException ex) {
								if (isConnected) {
									// Disconnect printer after max allowed number of failed attempts.
									++statusRequestCount;
									if (statusRequestCount > maxAllowedStatusRequestCount) {
										LOGGER.warn("Failure during printer status request: " + ex);
										// LOGGER.debug("Status request count for " + printerHandle.getConnectionHandle() + " (" + Integer.toString(statusRequestCount) + ") exceeds maxAllowedStatusRequestCount(" + Integer.toString(maxAllowedStatusRequestCount));
										shutdown();
									}
								}
							}
						}

						Thread.sleep(sleepBetweenStatusChecks);
					}
					catch (InterruptedException ex) {
						LOGGER.debug("Comms interrupted");
					}

					break;

				case DISCONNECTED:
					LOGGER.debug("state is disconnected");
					break;
				default:
					break;
			}
		}
		LOGGER.info("Handler for " + printerHandle.getConnectionHandle() + " beginning exit routine - state was " + commsState);
		finalShutdown();
		LOGGER.info("Handler for " + printerHandle.getConnectionHandle() + " exited");
	}

	private void moveOnFromFirmwareCheck(FirmwareResponse firmwareResponse) {
		if (suppressPrinterIDChecks == false) {
			commsState = RoboxCommsState.CHECKING_ID;
		}
		else {
			commsState = RoboxCommsState.DETERMINING_PRINTER_STATUS;
		}
		loadingFirmware = false;
	}

	private void suspendStatusChecks(boolean suspendStatusChecks) {
		this.suspendStatusChecks = suspendStatusChecks;
	}

	public void loadFirmware(String firmwareFilePath) {
		LOGGER.debug("Being asked to load firmware - status is " + commsState + " thread " + this.getName());
		suspendStatusChecks(true);
		//        this.interrupt();
		firmwareLoadService.reset();
		firmwareLoadService.setPrinterToUse(printerToUse);
		firmwareLoadService.setFirmwareFileToLoad(firmwareFilePath);
		firmwareLoadService.start();
	}

	public void shutdown() {
		keepRunning = false;
		commsState = RoboxCommsState.SHUTTING_DOWN;
	}

	private void finalShutdown() {
		LOGGER.debug("Shutdown command interface...");
		keepRunning = false;
		commsState = RoboxCommsState.SHUTTING_DOWN;
		suspendStatusChecks(true);
		disconnectPrinterImpl();
		Platform.runLater(() -> {
			if (firmwareLoadService.isRunning()) {
				LOGGER.info("Shutdown command interface firmware service...");
				firmwareLoadService.cancel();
			}
		});
		LOGGER.debug("set state to disconnected");
		commsState = RoboxCommsState.DISCONNECTED;
		isConnected = false;
		asyncWriteThread.shutdown();
		LOGGER.debug("Shutdown command interface for " + printerHandle.getConnectionHandle() + " complete");
		controlInterface.disconnected(printerHandle);
	}

	/**
	 *
	 * @param sleepMillis
	 */
	protected abstract void setSleepBetweenStatusChecks(int sleepMillis);

	/**
	 *
	 * @param messageToWrite
	 * @return
	 * @throws RoboxCommsException
	 */
	public final RoboxRxPacket writeToPrinter(RoboxTxPacket messageToWrite) throws RoboxCommsException {
		if (isConnected) {
			return writeToPrinter(messageToWrite, false);
		}
		else {
			return null;
		}
	}

	/**
	 *
	 * @param messageToWrite
	 * @param dontPublishResult
	 * @return
	 * @throws RoboxCommsException
	 */
	public final RoboxRxPacket writeToPrinter(RoboxTxPacket messageToWrite, boolean dontPublishResult) throws RoboxCommsException {
		if (isConnected) {
			return asyncWriteThread.sendCommand(new CommandPacket(messageToWrite, dontPublishResult));
		}
		else {
			return null;
		}
	}

	public abstract RoboxRxPacket writeToPrinterImpl(RoboxTxPacket messageToWrite,
			boolean dontPublishResult) throws RoboxCommsException;

	/**
	 *
	 * @param printer
	 */
	public void setPrinter(Printer printer) {
		// The asyncWriterThread is started here because it is potentially risky
		// starting a thread in the constructor, as the command interface is not
		// properly initialised. As not much can happen until a printer is set,
		// it is started here. 
		this.printerToUse = printer;
		State s = asyncWriteThread.getState();

		asyncWriteThread.start();
	}

	/**
	 *
	 * @return
	 */
	public final boolean connectToPrinter() throws PortNotFoundException {
		isConnected = connectToPrinterImpl();
		return isConnected;
	}

	/**
	 *
	 * @return @throws celtech.roboxbase.comms.exceptions.PortNotFoundException
	 */
	protected abstract boolean connectToPrinterImpl() throws PortNotFoundException;

	/**
	 *
	 */
	protected abstract void disconnectPrinterImpl();

	private void determinePrinterStatus(StatusResponse statusResponse) {
		if (statusResponse == null)
			return;

		if (!PrinterUtils.printJobIDIndicatesPrinting(statusResponse.getRunningPrintJobID()))
			return;

		if (printerFriendlyName == null) {
			LOGGER.error("Connected to an unknown printer that is printing");
			return;
		}

		if (LOGGER.isDebugEnabled())
			LOGGER.debug(printerFriendlyName + " is printing");
	}

	public void operateRemotely(boolean enableRemoteOperation) {
		suspendStatusChecks(enableRemoteOperation);
	}

	public boolean isLocalPrinter() {
		return localPrinter;
	}

	public DetectedDevice getPrinterHandle() {
		return printerHandle;
	}

	public void clearAllErrors() {
		// Nothing to do by default.
	}

	public void clearError(FirmwareError error) {
		// Nothing to do by default.
	}
}
