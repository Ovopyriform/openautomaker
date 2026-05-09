package celtech.roboxbase.comms;

import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;

import com.vdurmont.semver4j.Semver;

import celtech.roboxbase.comms.exceptions.PortNotFoundException;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.tx.RoboxTxPacket;

/**
 * Callbacks used by {@link ConnectionStateMachine} to interact with its owning
 * {@link CommandInterface} without depending on the concrete class.
 */
interface ConnectionStateCallbacks {
	// ---- Transport layer ----

	boolean connectToPrinter() throws PortNotFoundException;

	void disconnectPrinter();

	/**
	 * Send a packet directly through the async write queue (no connected-guard).
	 */
	RoboxRxPacket writeThroughQueue(RoboxTxPacket packet, boolean dontPublish) throws RoboxCommsException;

	void shutdownWriteQueue();

	// ---- Per-transport behaviour hooks ----

	boolean requiresPeriodicIdRefresh();

	boolean appliesAmbientColourOnConnect();

	// ---- Data reads ----

	Printer getPrinter();

	DetectedDevice getPrinterHandle();

	boolean isSuppressingPrinterIDChecks();

	int getSleepBetweenStatusChecks();

	boolean isSuspendingStatusChecks();

	Semver getRequiredFirmwareVersion();

	PrinterContainer getPrinterContainer();

	SystemNotificationManager getSystemNotificationManager();

	/** Computes the local filesystem path to the required firmware binary. */
	String requiredFirmwareFilePath();

	// ---- Callbacks ----

	void setConnected(boolean connected);

	void setFirmwareVersionInUse(Semver version);

	void persistConnectionInfo(String printerIDString, Semver firmwareVersion);

	void notifyPrinterConnected();

	void notifyPrinterDisconnected();

	void loadFirmware(String firmwareFilePath);

	void cancelFirmwareLoadIfRunning();
}
