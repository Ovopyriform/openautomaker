package org.openautomaker.base.comms.print_server;

import java.util.ArrayList;
import java.util.List;

import celtech.roboxbase.comms.DetectedDevice;
import celtech.roboxbase.comms.DeviceDetector;
import jakarta.inject.Inject;

/**
 *
 * @author Ian
 */
public class RemotePrinterDetector extends DeviceDetector {

	//private static final Logger LOGGER = LogManager.getLogger();

	private final ConnectedServersPreference connectedServersPreference;

	@Inject
	protected RemotePrinterDetector(
			ConnectedServersPreference connectedServersPreference) {

		super();

		this.connectedServersPreference = connectedServersPreference;
	}

	//TODO: Look at this.  The whole 'newlyDetectedDevices seems odd
	@Override
	public List<DetectedDevice> searchForDevices() {
		List<DetectedDevice> newlyDetectedPrinters = new ArrayList<>();

		List<PrintServerConnection> activeRoboxRoots = connectedServersPreference.getValue();

		// Search the roots that have been registered in core memory
		for (PrintServerConnection server : activeRoboxRoots) {
			if (server.getServerStatus() == PrintServerConnection.ServerStatus.CONNECTED) {
				List<DetectedDevice> attachedPrinters = server.listAttachedPrinters();
				newlyDetectedPrinters.addAll(attachedPrinters);
			}
		}

		return newlyDetectedPrinters;
	}
}
