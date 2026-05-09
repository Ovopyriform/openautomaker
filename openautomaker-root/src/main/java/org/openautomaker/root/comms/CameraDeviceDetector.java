package org.openautomaker.root.comms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.environment.MachineType;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.root.utils.NetworkUtils;

import celtech.roboxbase.comms.DetectedDevice;
import celtech.roboxbase.comms.DeviceDetector;

import com.google.inject.Inject;

public class CameraDeviceDetector extends DeviceDetector {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final String CAM_DETECTOR_COMMAND = "/home/pi/ARM-32bit/Root/cameraDetector.sh";
	private static final String CAM_FIND_INFO_COMMAND = "/home/pi/ARM-32bit/Root/findCameraInfo.sh";

	private final OpenAutomakerEnv env;

	@Inject
	public CameraDeviceDetector(OpenAutomakerEnv env) {
		this.env = env;
	}

	@Override
	public List<DetectedDevice> searchForDevices() {
		List<DetectedDevice> detectedCameras = new ArrayList<>();

		if (env.getMachineType() == MachineType.LINUX) {
			StringBuilder outputBuffer = new StringBuilder();

			ProcessBuilder builder = new ProcessBuilder(CAM_DETECTOR_COMMAND);
			Process process;

			try {
				process = builder.start();
				InputStream is = process.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				while ((line = br.readLine()) != null) {
					if (!line.equalsIgnoreCase(NOT_CONNECTED_STRING))
						outputBuffer.append(line);
				}
			} catch (IOException ex) {
				LOGGER.error("Error " + ex);
			}

			if (outputBuffer.length() > 0) {
				for (String handle : outputBuffer.toString().split(" "))
					detectedCameras.add(new DetectedCamera(DeviceConnectionType.USB, handle));
			}
		}

		return detectedCameras;
	}

	public CameraInfo findCameraInformation(String detectedCameraHandle) {
		CameraInfo cameraInfo = null;
		List<String> cameraInformation = new ArrayList<>();

		if (env.getMachineType() == MachineType.LINUX) {
			ProcessBuilder builder = new ProcessBuilder(CAM_FIND_INFO_COMMAND, detectedCameraHandle);
			Process process;
			try {
				process = builder.start();
				InputStream is = process.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				while ((line = br.readLine()) != null)
					cameraInformation.add(line);
			} catch (IOException ex) {
				LOGGER.error("Error " + ex);
			}

			if (cameraInformation.size() > 1) {
				String cameraName = cameraInformation.get(0);
				String cameraNumber = cameraInformation.get(1);

				String serverIP = "";
				try {
					serverIP = NetworkUtils.determineIPAddress();
				} catch (SocketException e) {
					LOGGER.error("Error when determining our IP address. " + e.getMessage());
				}

				cameraInfo = new CameraInfo();
				cameraInfo.setUdevName(detectedCameraHandle);
				cameraInfo.setCameraName(cameraName);
				cameraInfo.setCameraNumber(Integer.parseInt(cameraNumber));
				cameraInfo.setServerIP(serverIP);
			}
		}

		return cameraInfo;
	}
}
