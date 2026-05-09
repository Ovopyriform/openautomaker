package org.openautomaker.root.comms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;

import celtech.roboxbase.comms.DetectedDevice;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class CameraCommsManager extends Thread {

	private static final Logger LOGGER = LogManager.getLogger();

	private final CameraDeviceDetector cameraDeviceDetector;

	private Map<DetectedDevice, CameraInfo> activeCameras;

	private boolean keepRunning = true;

	@Inject
	public CameraCommsManager(CameraDeviceDetector cameraDeviceDetector) {
		this.cameraDeviceDetector = cameraDeviceDetector;
		activeCameras = new HashMap<>();
	}

	@Override
	public void run() {
		LOGGER.debug("Camera comms manager started");

		while (keepRunning) {
			long startOfRunTime = System.currentTimeMillis();

			List<DetectedDevice> attachedCameras = cameraDeviceDetector.searchForDevices();

			Map<DetectedDevice, CameraInfo> newActiveCameras = new HashMap<>();
			Set<DetectedDevice> currentActiveCameras = activeCameras.keySet();

			attachedCameras.forEach(connectedCam -> {
				if (!currentActiveCameras.contains(connectedCam)) {
					if (connectedCam instanceof DetectedCamera) {
						LOGGER.debug("We have found a new camera " + connectedCam);
						CameraInfo cameraInfo = assessCamera((DetectedCamera) connectedCam);
						if (cameraInfo != null) {
							if (!cameraInfo.getCameraName().matches("bcm\\d+.*"))
								newActiveCameras.put(connectedCam, cameraInfo);
							else
								LOGGER.debug("Ignored Broadcom VideoCore device " + cameraInfo.getCameraName());
						} else {
							LOGGER.debug("No information returned for detected camera " + ((DetectedCamera) connectedCam).getConnectionHandle());
						}
					} else {
						LOGGER.debug("We have found a device that is not a camera with handle " + connectedCam);
					}
				} else {
					newActiveCameras.put(connectedCam, activeCameras.get(connectedCam));
				}
			});

			activeCameras = newActiveCameras;

			long endOfRunTime = System.currentTimeMillis();
			long sleepTime = 500 - (endOfRunTime - startOfRunTime);

			if (sleepTime > 0) {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException ex) {
					LOGGER.debug("Camera comms manager was interrupted during sleep");
				}
			}
		}
	}

	private CameraInfo assessCamera(DetectedCamera detectedCamera) {
		return cameraDeviceDetector.findCameraInformation(detectedCamera.getConnectionHandle());
	}

	public void shutdown() {
		keepRunning = false;
		LOGGER.debug("Camera comms manager shutdown");
	}

	public List<CameraInfo> getAllCameraInfo() {
		return new ArrayList<>(activeCameras.values());
	}
}
