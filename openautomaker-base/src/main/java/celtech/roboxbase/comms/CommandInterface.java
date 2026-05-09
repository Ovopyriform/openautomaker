package celtech.roboxbase.comms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.services.firmware.FirmwareLoadResult;
import org.openautomaker.base.services.firmware.FirmwareLoadService;
import org.openautomaker.environment.preference.printer.LastFirmwareVersionPreference;
import org.openautomaker.environment.preference.printer.LastSerialNumberPreference;
import org.openautomaker.environment.preference.root.FirmwarePathPreference;

import com.vdurmont.semver4j.Semver;

import celtech.roboxbase.comms.async.AsyncWriteThread;
import celtech.roboxbase.comms.async.CommandPacket;
import celtech.roboxbase.comms.exceptions.PortNotFoundException;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.FirmwareError;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.tx.RoboxTxPacket;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;

public abstract class CommandInterface extends Thread implements ConnectionStateCallbacks {

	protected static final Logger LOGGER = LogManager.getLogger();

	protected Semver fFirmwareVersionInUse;

	protected PrinterStatusConsumer controlInterface = null;
	protected DetectedDevice printerHandle = null;
	protected Printer printerToUse = null;

	protected final FirmwareLoadService firmwareLoadService;

	protected boolean suppressPrinterIDChecks = false;
	protected int sleepBetweenStatusChecks = 1000;

	protected boolean suspendStatusChecks = false;
	private final boolean localPrinter;

	private boolean isConnected = false;

	private final AsyncWriteThread asyncWriteThread;

	private final SystemNotificationManager systemNotificationManager;

	private final FirmwarePathPreference firmwarePathPreference;
	private final LastSerialNumberPreference lastSerialNumberPreference;
	private final LastFirmwareVersionPreference lastFirmwareVersionPreference;

	private PrinterContainer printerContainer;

	private final Semver requiredFirmwareVersion;

	private final ConnectionStateMachine stateMachine;

	protected CommandInterface(
			CommandInterfaceDeps deps,
			PrinterStatusConsumer controlInterface,
			DetectedDevice printerHandle,
			boolean suppressPrinterIDChecks,
			int sleepBetweenStatusChecks,
			boolean localPrinter) {

		this.systemNotificationManager = deps.systemNotificationManager();
		this.firmwarePathPreference = deps.firmwarePathPreference();
		this.lastSerialNumberPreference = deps.lastSerialNumberPreference();
		this.lastFirmwareVersionPreference = deps.lastFirmwareVersionPreference();
		this.firmwareLoadService = deps.firmwareLoadService();
		this.printerContainer = deps.printerContainer();
		this.requiredFirmwareVersion = deps.requiredFirmwareVersionPreference().getValue();

		this.controlInterface = controlInterface;
		this.printerHandle = printerHandle;
		this.suppressPrinterIDChecks = suppressPrinterIDChecks;
		this.sleepBetweenStatusChecks = sleepBetweenStatusChecks;
		this.localPrinter = localPrinter;

		this.setDaemon(true);
		this.setName("CommandInterface|" + printerHandle.getConnectionHandle());
		this.setPriority(8);

		asyncWriteThread = new AsyncWriteThread(this, printerHandle.getConnectionHandle());
		stateMachine = new ConnectionStateMachine(this);

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Firmware property value:" + requiredFirmwareVersion.getValue());

		firmwareLoadService.setOnSucceeded((WorkerStateEvent t) -> {
			FirmwareLoadResult result = (FirmwareLoadResult) t.getSource().getValue();
			boolean firmwareUpdatedOK = false;
			if (result.getStatus() == FirmwareLoadResult.SUCCESS) {
				systemNotificationManager.showFirmwareUpgradeStatusNotification(result);
				shutdown();
				firmwareUpdatedOK = true;
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
		stateMachine.run();
	}

	public void shutdown() {
		stateMachine.shutdown();
	}

	// ---- ConnectionStateCallbacks implementation ----

	@Override
	public boolean connectToPrinter() throws PortNotFoundException {
		isConnected = connectToPrinterImpl();
		return isConnected;
	}

	@Override
	public void disconnectPrinter() {
		disconnectPrinterImpl();
	}

	@Override
	public RoboxRxPacket writeThroughQueue(RoboxTxPacket packet, boolean dontPublish) throws RoboxCommsException {
		return asyncWriteThread.sendCommand(new CommandPacket(packet, dontPublish));
	}

	@Override
	public void shutdownWriteQueue() {
		asyncWriteThread.shutdown();
	}

	@Override
	public Printer getPrinter() {
		return printerToUse;
	}

	@Override
	public DetectedDevice getPrinterHandle() {
		return printerHandle;
	}

	@Override
	public boolean isSuppressingPrinterIDChecks() {
		return suppressPrinterIDChecks;
	}

	@Override
	public int getSleepBetweenStatusChecks() {
		return sleepBetweenStatusChecks;
	}

	@Override
	public boolean isSuspendingStatusChecks() {
		return suspendStatusChecks;
	}

	@Override
	public Semver getRequiredFirmwareVersion() {
		return requiredFirmwareVersion;
	}

	@Override
	public PrinterContainer getPrinterContainer() {
		return printerContainer;
	}

	@Override
	public SystemNotificationManager getSystemNotificationManager() {
		return systemNotificationManager;
	}

	@Override
	public String requiredFirmwareFilePath() {
		return firmwarePathPreference.getValue()
				.resolve("robox_r" + requiredFirmwareVersion.getMajor() + ".bin")
				.toString();
	}

	@Override
	public void setConnected(boolean connected) {
		this.isConnected = connected;
	}

	@Override
	public void setFirmwareVersionInUse(Semver version) {
		this.fFirmwareVersionInUse = version;
	}

	@Override
	public void persistConnectionInfo(String printerIDString, Semver firmwareVersion) {
		lastSerialNumberPreference.setValue(printerIDString);
		lastFirmwareVersionPreference.setValue(firmwareVersion);
	}

	@Override
	public void notifyPrinterConnected() {
		controlInterface.printerConnected(printerHandle);
	}

	@Override
	public void notifyPrinterDisconnected() {
		controlInterface.disconnected(printerHandle);
	}

	@Override
	public void loadFirmware(String firmwareFilePath) {
		LOGGER.debug("Being asked to load firmware - status is " + stateMachine.getState() + " thread " + this.getName());
		suspendStatusChecks = true;
		firmwareLoadService.reset();
		firmwareLoadService.setPrinterToUse(printerToUse);
		firmwareLoadService.setFirmwareFileToLoad(firmwareFilePath);
		firmwareLoadService.start();
	}

	@Override
	public void cancelFirmwareLoadIfRunning() {
		Platform.runLater(() -> {
			if (firmwareLoadService.isRunning()) {
				LOGGER.info("Shutdown command interface firmware service...");
				firmwareLoadService.cancel();
			}
		});
	}

	// ---- Public API (unchanged) ----

	/**
	 * @deprecated Use {@link #writeThroughQueue} for internal FSM writes.
	 *             External callers should prefer the Printer API.
	 */
	public final RoboxRxPacket writeToPrinter(RoboxTxPacket messageToWrite) throws RoboxCommsException {
		if (isConnected) {
			return writeToPrinter(messageToWrite, false);
		}
		else {
			return null;
		}
	}

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

	public void setPrinter(Printer printer) {
		this.printerToUse = printer;
		asyncWriteThread.start();
	}

	public final boolean connectToPrinterImpl_wrapper() throws PortNotFoundException {
		return connectToPrinterImpl();
	}

	protected abstract boolean connectToPrinterImpl() throws PortNotFoundException;

	protected abstract void disconnectPrinterImpl();

	public void operateRemotely(boolean enableRemoteOperation) {
		this.suspendStatusChecks = enableRemoteOperation;
	}

	public boolean isLocalPrinter() {
		return localPrinter;
	}

	protected final RoboxCommsState getCommsState() {
		return stateMachine.getState();
	}

	public void clearAllErrors() {
		// Nothing to do by default.
	}

	public void clearError(FirmwareError error) {
		// Nothing to do by default.
	}

	// Subclass hook overrides — defaults
	@Override
	public boolean requiresPeriodicIdRefresh() {
		return false;
	}

	@Override
	public boolean appliesAmbientColourOnConnect() {
		return true;
	}

	protected abstract void setSleepBetweenStatusChecks(int sleepMillis);
}
