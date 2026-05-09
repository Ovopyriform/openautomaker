package org.openautomaker.root;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.root.comms.CameraCommsManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import celtech.roboxbase.comms.RoboxCommsManager;
import io.dropwizard.lifecycle.Managed;

@Singleton
public class CoreManager implements Managed {

	private static final Logger LOGGER = LogManager.getLogger();

	private final RoboxCommsManager commsManager;
	private final CameraCommsManager cameraCommsManager;

	@Inject
	public CoreManager(RoboxCommsManager commsManager, CameraCommsManager cameraCommsManager,
			PrinterRegistry printerRegistry, MountableMediaRegistry mountableMediaRegistry,
			RootServices rootServices) {
		this.commsManager = commsManager;
		this.cameraCommsManager = cameraCommsManager;
	}

	@Override
	public void start() throws Exception {
		com.sun.javafx.application.PlatformImpl.startup(() -> {
		});
		commsManager.start();
		cameraCommsManager.start();
	}

	@Override
	public void stop() throws Exception {
		LOGGER.info("Asked to shutdown Root");
		commsManager.shutdown();
		cameraCommsManager.shutdown();
	}
}
