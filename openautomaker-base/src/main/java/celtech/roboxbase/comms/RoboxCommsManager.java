package celtech.roboxbase.comms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.base.comms.print_server.RemotePrinterDetector;
import org.openautomaker.base.device.CameraManager;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.device.USBDirectoryManager;
import org.openautomaker.base.inject.comms.HardwareCommandInterfaceFactory;
import org.openautomaker.base.inject.comms.RoboxRemoteCommandInterfaceFactory;
import org.openautomaker.base.inject.comms.VirtualPrinterCommandInterfaceFactory;
import org.openautomaker.base.inject.printer_control.HardwarePrinterFactory;
import org.openautomaker.base.printerControl.model.HardwarePrinter;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterConnection;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.MachineType;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.preference.camera.SearchForRemoteCamerasPreference;
import org.openautomaker.environment.preference.printer.DetectLoadedFilamentPreference;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterEnabledPreference;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterHeadPreference;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterTypePreference;
import org.openautomaker.javafx.FXProperty;

import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import celtech.roboxbase.comms.rx.StatusResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
@Singleton
//TODO: revisit how this manages printers.  Should probably be a Service?
public class RoboxCommsManager extends Thread implements PrinterStatusConsumer {

	private static final Logger LOGGER = LogManager.getLogger();

	private UUID fVirtualPrinterHandle = null;

	//public static final String CUSTOM_CONNECTION_HANDLE = "OfflinePrinterConnection";

	public static final String MOUNTED_MEDIA_FILE_PATH = "/media";

	public static final int MAX_ACTIVE_PRINTERS = 9;

	private boolean keepRunning = true;

	private final ObservableMap<DetectedDevice, Printer> activePrinters = FXCollections.observableHashMap();
	private final ObservableList<CameraInfo> activeCameras = FXCollections.observableArrayList();
	private boolean suppressPrinterIDChecks = false;
	private int sleepBetweenStatusChecksMS = 1000;

	private final SerialDeviceDetector usbSerialDeviceDetector;
	private final RemotePrinterDetector remotePrinterDetector;

	// perhaps we want to move this (and asociated code) into CameraCommsManager
	// This also means we need to move the CameraCommsManager out of root
	// and into RoboxBase...
	private final RemoteCameraDetector remoteCameraDetector;

	private boolean doNotCheckForPresenceOfHead = false;
	private BooleanProperty detectLoadedFilamentOverride = new SimpleBooleanProperty(true);
	private BooleanProperty dearchForRemoteCamerasProperty = new SimpleBooleanProperty(false);

	// Set to true when an attempt is made to connect another printer when the maximum number
	// of printers have already been connected. It will be set to false again when a printer is
	// disconnected. This means listeners will be notifed when the first attempt to connect
	// too any printers is made, but will not be notified again until the number of connected
	// printers decreases.
	private BooleanProperty tooManyRoboxAttachedProperty = new SimpleBooleanProperty(true);

	//	private RoboxCommsManager(boolean suppressPrinterIDChecks, boolean doNotCheckForPresenceOfHead,
	//			BooleanProperty detectLoadedFilamentProperty, BooleanProperty searchForRemoteCamerasProperty) {
	//
	//		configureVirtualPrinterPreferenceListeners();
	//
	//		this.fSuppressPrinterIDChecks = suppressPrinterIDChecks;
	//		this.fDoNotCheckForPresenceOfHead = doNotCheckForPresenceOfHead;
	//		this.fDetectLoadedFilamentOverride = detectLoadedFilamentProperty;
	//		this.fDearchForRemoteCamerasProperty = searchForRemoteCamerasProperty;
	//
	//		this.setDaemon(true);
	//		this.setName("Robox Comms Manager");
	//		this.setPriority(6);
	//
	//		usbSerialDeviceDetector = new SerialDeviceDetector(roboxVendorID, roboxProductID, printerToSearchFor);
	//		remotePrinterDetector = new RemotePrinterDetector();
	//
	//		remoteCameraDetector = new RemoteCameraDetector();
	//
	//		tooManyRoboxAttachedProperty.set(false);
	//	}

	private final PrinterManager printerManager;
	private final CameraManager cameraManager;
	private final USBDirectoryManager usbDirectoryManager;

	private final VirtualPrinterHeadPreference virtualPrinterHeadPreference;
	private final VirtualPrinterTypePreference virtualPrinterTypePreference;
	private final VirtualPrinterCommandInterfaceFactory virtualPrinterCommandInterfaceFactory;

	private final HardwareCommandInterfaceFactory hardwareCommandInterfaceFactory;
	private final RoboxRemoteCommandInterfaceFactory roboxRemoteCommandInterfaceFactory;
	private final HardwarePrinterFactory hardwarePrinterFactory;

	private final I18N i18n;

	private final OpenAutomakerEnv environment;

	@Inject
	protected RoboxCommsManager(
			OpenAutomakerEnv environment,
			VirtualPrinterEnabledPreference virtualPrinterEnabledPreference,
			VirtualPrinterHeadPreference virtualPrinterHeadPreference,
			VirtualPrinterTypePreference virtualPrinterTypePreference,
			VirtualPrinterCommandInterfaceFactory virtualPrinterCommandInterfaceFactory,
			HardwareCommandInterfaceFactory hardwareCommandInterfaceFactory,
			RoboxRemoteCommandInterfaceFactory roboxRemoteCommandInterfaceFactory,
			DetectLoadedFilamentPreference detectLoadedFilamentPreference,
			SearchForRemoteCamerasPreference searchForRemoteCamerasPreference,
			SerialDeviceDetector serialDeviceDetector,
			RemotePrinterDetector remotePrinterDetector,
			RemoteCameraDetector remoteCameraDetector,
			PrinterManager printerManager,
			CameraManager cameraManager,
			USBDirectoryManager usbDirectoryManager,
			HardwarePrinterFactory hardwarePrinterFactory,
			I18N i18n) {

		suppressPrinterIDChecks = false;
		doNotCheckForPresenceOfHead = false;

		this.environment = environment;
		this.virtualPrinterHeadPreference = virtualPrinterHeadPreference;
		this.virtualPrinterTypePreference = virtualPrinterTypePreference;
		this.virtualPrinterCommandInterfaceFactory = virtualPrinterCommandInterfaceFactory;

		this.hardwareCommandInterfaceFactory = hardwareCommandInterfaceFactory;
		this.roboxRemoteCommandInterfaceFactory = roboxRemoteCommandInterfaceFactory;

		this.hardwarePrinterFactory = hardwarePrinterFactory;

		this.i18n = i18n;

		//These being BooleanProperties is a little odd
		this.detectLoadedFilamentOverride = FXProperty.bind(detectLoadedFilamentPreference);
		this.dearchForRemoteCamerasProperty = FXProperty.bind(searchForRemoteCamerasPreference);

		this.setDaemon(true);
		this.setName("Robox Comms Manager");
		this.setPriority(6);

		this.usbSerialDeviceDetector = serialDeviceDetector;
		this.remotePrinterDetector = remotePrinterDetector;
		this.remoteCameraDetector = remoteCameraDetector;

		this.printerManager = printerManager;
		this.cameraManager = cameraManager;

		this.usbDirectoryManager = usbDirectoryManager;

		tooManyRoboxAttachedProperty.set(false);

		// Setup the virtual printer
		virtualPrinterTypePreference.addChangeListener((evt) -> {
			refreshVirtualPrinter();
		});

		virtualPrinterHeadPreference.addChangeListener((evt) -> {
			refreshVirtualPrinter();
		});

		if (virtualPrinterEnabledPreference.getValue())
			addVirtualPrinter(true);

		virtualPrinterEnabledPreference.addChangeListener((evt) -> {
			if (virtualPrinterEnabledPreference.getValue()) {
				addVirtualPrinter(true);
				return;
			}

			removeAllDummyPrinters();
		});

	}

	/**
	 * Refresh the virtual printer. Used if the printer type or head changes.
	 */
	private void refreshVirtualPrinter() {
		Optional<DetectedDevice> virtualPrinterHandle = getVirtualPrinter();
		if (virtualPrinterHandle.isPresent())
			removeDummyPrinter(virtualPrinterHandle.get());

		addVirtualPrinter(true);
	}

	// This needs to be synchronized because it is called from both the RoboxCommsManager thread and
	// the main JavaFX thread (when a dummy printer is added). Without the synchronization, the activePrinters
	// list can be updated simultaneously by the two threads, occasionally causing corruption.
	private synchronized void assessCandidatePrinter(DetectedDevice detectedPrinter) {
		if (detectedPrinter != null
				&& !activePrinters.keySet().contains(detectedPrinter)) {
			if (activePrinters.size() >= MAX_ACTIVE_PRINTERS) {
				LOGGER.info("Max number of printers already connected - not connecteding to new printer on " + detectedPrinter.getConnectionHandle());
				tooManyRoboxAttachedProperty.set(true);
			}
			else {
				// We need to connect!
				LOGGER.info("Adding new printer on " + detectedPrinter.getConnectionHandle());
				Printer newPrinter = makePrinter(detectedPrinter);
				activePrinters.put(detectedPrinter, newPrinter);
				newPrinter.startComms();
			}
		}
	}

	private Printer makePrinter(DetectedDevice detectedPrinter) {
		HardwarePrinter.FilamentLoadedGetter filamentLoadedGetter = (StatusResponse statusResponse, int extruderNumber) -> {
			if (!detectLoadedFilamentOverride.get()) {
				// if this preference has been deselected then always say that the filament
				// has been detected as loaded.
				return true;
			}
			else {
				if (extruderNumber == 1) {
					return statusResponse.isFilament1SwitchStatus();
				}
				else {
					return statusResponse.isFilament2SwitchStatus();
				}
			}
		};
		Printer newPrinter = null;

		switch (detectedPrinter.getConnectionType()) {
			case SERIAL:
				newPrinter = hardwarePrinterFactory.create(this, hardwareCommandInterfaceFactory.create(
						this, detectedPrinter, suppressPrinterIDChecks,
						sleepBetweenStatusChecksMS), filamentLoadedGetter,
						doNotCheckForPresenceOfHead);
				newPrinter.setPrinterConnection(PrinterConnection.LOCAL);
				break;
			case ROBOX_REMOTE:
				RoboxRemoteCommandInterface commandInterface = roboxRemoteCommandInterfaceFactory.create(
						this, (RemoteDetectedPrinter) detectedPrinter, suppressPrinterIDChecks,
						sleepBetweenStatusChecksMS);

				newPrinter = hardwarePrinterFactory.create(this, commandInterface, filamentLoadedGetter,
						doNotCheckForPresenceOfHead);
				newPrinter.setPrinterConnection(PrinterConnection.REMOTE);
				break;
			case DUMMY:
				//TODO: If we keep a reference to the dummy printer interface surely we can change things on the fly?
				VirtualPrinterCommandInterface dummyCommandInterface = virtualPrinterCommandInterfaceFactory.create(this,
						detectedPrinter,
						suppressPrinterIDChecks,
						sleepBetweenStatusChecksMS,
						i18n.t("preferences.customPrinter"),
						virtualPrinterTypePreference.getValue().getTypeCode());

				dummyCommandInterface.setupHead(virtualPrinterHeadPreference.getValue());
				newPrinter = hardwarePrinterFactory.create(this, dummyCommandInterface, filamentLoadedGetter,
						doNotCheckForPresenceOfHead);
				//                
				//                if(detectedPrinter.getConnectionHandle().equals(CUSTOM_CONNECTION_HANDLE)) 
				//                {
				newPrinter.setPrinterConnection(PrinterConnection.OFFLINE);
				//                } else 
				//                {
				//                    newPrinter.setPrinterConnection(PrinterConnection.DUMMY);
				//                }
				break;
			default:
				LOGGER.error("Don't know how to handle connected printer: " + detectedPrinter);
				break;
		}
		return newPrinter;
	}

	private void removeMissingCameras(List<CameraInfo> remotelyAttachedCameras) {
		List<CameraInfo> missingCameras = new ArrayList<>();

		// Remove cameras that are no longer detected.
		activeCameras.forEach((c) -> {
			if (!remotelyAttachedCameras.contains(c))
				missingCameras.add(c);
		});
		missingCameras.forEach((c) -> {
			cameraManager.cameraDisconnected(c);
			activeCameras.remove(c);
		});
	}

	private void assessCandidateCamera(CameraInfo candidateCamera) {
		if (!activeCameras.contains(candidateCamera)) {
			activeCameras.add(candidateCamera);
			cameraManager.cameraConnected(candidateCamera);
		}
	}

	@Override
	public void start() {
		LOGGER.debug("Starting comms manager");
		super.start();
	}

	@Override
	public void run() {
		while (keepRunning) {
			long startOfRunTime = System.currentTimeMillis();

			// Search
			List<DetectedDevice> directlyAttachedDevices = usbSerialDeviceDetector.searchForDevices();
			List<DetectedDevice> remotelyAttachedDevices = remotePrinterDetector.searchForDevices();

			if (dearchForRemoteCamerasProperty.get()) {
				// Cache camera info
				List<CameraInfo> remotelyAttachedCameras = remoteCameraDetector.searchForDevices();
				removeMissingCameras(remotelyAttachedCameras);
				remotelyAttachedCameras.forEach(this::assessCandidateCamera);
			}
			else if (!activeCameras.isEmpty()) {
				// searchForRemoteCamerasProperty has been set to false, so clear camera cache.
				activeCameras.forEach((c) -> {
					// The camera detected flag on the server is reset for each camera
					// on the server. Although slightly inefficent, this doesn't matter
					// as all cameras will be removed.
					c.getServer().setCameraDetected(false);
					cameraManager.cameraDisconnected(c);
				});
				activeCameras.clear();
			}

			// Now new connections
			List<DetectedDevice> printersToConnect = new ArrayList<>();
			directlyAttachedDevices.forEach(newPrinter -> {
				if (!activePrinters.keySet().contains(newPrinter)) {
					printersToConnect.add(newPrinter);
				}
			});
			remotelyAttachedDevices.forEach(newPrinter -> {
				if (!activePrinters.keySet().contains(newPrinter)) {
					printersToConnect.add(newPrinter);
				}
			});

			for (DetectedDevice printerToConnect : printersToConnect) {
				LOGGER.debug("We have found a new printer " + printerToConnect);
				assessCandidatePrinter(printerToConnect);
			}

			if (environment.getMachineType() == MachineType.LINUX) {
				// check if there are any usb drives mounted
				File mediaDir = new File(MOUNTED_MEDIA_FILE_PATH);
				usbDirectoryManager.addAllUSBDirectories(mediaDir.listFiles());
			}

			long endOfRunTime = System.currentTimeMillis();
			long runTime = endOfRunTime - startOfRunTime;
			long sleepTime = 500 - runTime;

			if (sleepTime > 0) {
				try {
					Thread.sleep(sleepTime);
				}
				catch (InterruptedException ex) {
					LOGGER.info("Comms manager was interrupted during sleep");
				}
			}
		}
	}

	/**
	 *
	 */
	public void shutdown() {
		keepRunning = false;

		List<Printer> printersToShutdown = new ArrayList<>();
		printerManager.getConnectedPrinters().forEach(printer -> {
			printersToShutdown.add(printer);
		});

		for (Printer printer : printersToShutdown) {
			LOGGER.debug("Shutdown printer " + printer);
			try {
				printer.getCommandInterface().shutdown();
				printer.shutdown();
			}
			catch (Exception ex) {
				LOGGER.error("Error shutting down printer");
			}
		}
	}

	/**
	 *
	 * @param detectedPrinter
	 */
	@Override
	public void printerConnected(DetectedDevice detectedPrinter) {
		Printer printerHusk = activePrinters.get(detectedPrinter);
		printerHusk.connectionEstablished();

		printerManager.printerConnected(printerHusk);
	}

	/**
	 *
	 */
	// This needs to be synchronized because it is called from both the RoboxCommsManager thread and
	// the main JavaFX thread (when a dummy printer is removed). Without the synchronization, the activePrinters
	// list can be updated simultaneously by the two threads, occasionally causing corruption.
	private synchronized void disconnectedSync(DetectedDevice printerHandle) {
		final Printer printerToRemove = activePrinters.get(printerHandle);
		if (printerToRemove != null) {
			printerToRemove.stopComms();
			printerToRemove.shutdown();
		}

		printerManager.printerDisconnected(printerToRemove);

		if (activePrinters.containsKey(printerHandle)) {
			if (activePrinters.get(printerHandle) != null
					&& activePrinters.get(printerHandle).getPrinterIdentity() != null
					&& activePrinters.get(printerHandle).getPrinterIdentity().printerFriendlyNameProperty().get() != null
					&& !activePrinters.get(printerHandle).getPrinterIdentity().printerFriendlyNameProperty().get().equals("")) {
				LOGGER.info("Disconnected from " + activePrinters.get(printerHandle).getPrinterIdentity().printerFriendlyNameProperty().get());
			}
			else {
				LOGGER.info("Disconnected");
			}
			activePrinters.remove(printerHandle);
			tooManyRoboxAttachedProperty.set(false);
		}
	}

	/**
	 *
	 */
	@Override
	public void disconnected(DetectedDevice printerHandle) {
		disconnectedSync(printerHandle);
	}

	public void addVirtualPrinter(boolean isCustomPrinter) {
		//Generate a UUID for the printer handle
		fVirtualPrinterHandle = UUID.randomUUID();
		DetectedDevice virtualPrinter = new DetectedDevice(DeviceDetector.DeviceConnectionType.DUMMY, fVirtualPrinterHandle.toString());
		assessCandidatePrinter(virtualPrinter);

		//        dummyPrinterCounter++;
		//        String actualPrinterPort = isCustomPrinter ? CUSTOM_CONNECTION_HANDLE : dummyPrinterPort + " " + dummyPrinterCounter;
		//        dummyPrinterName = isCustomPrinter ? i18n.tInst("preferences.customPrinter") : "DP " + dummyPrinterCounter;
		//        DetectedDevice printerHandle = new DetectedDevice(DeviceDetector.DeviceConnectionType.DUMMY,
		//                actualPrinterPort);
		//        assessCandidatePrinter(printerHandle);
	}

	public void removeDummyPrinter(DetectedDevice printerHandle) {
		disconnectedSync(printerHandle);
	}

	public void removeAllDummyPrinters() {
		getDummyPrinterHandles().forEach(this::removeDummyPrinter);
	}

	private Optional<DetectedDevice> getVirtualPrinter() {
		return getDummyPrinterHandles().stream()
				.filter(printerHandle -> printerHandle.getConnectionHandle().equals(fVirtualPrinterHandle.toString()))
				.findFirst();
	}

	public List<Printer> getDummyPrinters() {
		return activePrinters.entrySet()
				.stream()
				.filter(p -> p.getKey().getConnectionType() == DeviceDetector.DeviceConnectionType.DUMMY)
				.map(e -> e.getValue())
				.collect(Collectors.toList());
	}

	private List<DetectedDevice> getDummyPrinterHandles() {
		return activePrinters.keySet()
				.stream()
				.filter(printerHandle -> printerHandle.getConnectionType() == DeviceDetector.DeviceConnectionType.DUMMY)
				.collect(Collectors.toList());
	}

	/**
	 *
	 * @param milliseconds
	 */
	public void setSleepBetweenStatusChecks(int milliseconds) {
		sleepBetweenStatusChecksMS = milliseconds;
	}

	//	private void deviceNoLongerPresent(DetectedDevice detectedDevice) {
	//		Printer printerToDisconnect = activePrinters.get(detectedDevice);
	//		if (printerToDisconnect != null) {
	//			CommandInterface commandInterface = printerToDisconnect.getCommandInterface();
	//			if (commandInterface != null) {
	//				commandInterface.shutdown();
	//			}
	//			else {
	//				LOGGER.info("CI was null");
	//			}
	//		}
	//		else {
	//			LOGGER.info("not in active list");
	//		}
	//	}

	public BooleanProperty tooManyRoboxAttachedProperty() {
		return tooManyRoboxAttachedProperty;
	}

	//    public void setDummyPrinterHeadType(String dummyPrinterHeadType) {
	//        this.dummyPrinterHeadType = dummyPrinterHeadType;
	//    }
	//    
	//    public void setDummyPrinterType(PrinterType dummyPrinterType) {
	//        this.dummyPrinterType = dummyPrinterType;
	//    }
}
