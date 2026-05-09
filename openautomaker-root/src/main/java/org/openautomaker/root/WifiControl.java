package org.openautomaker.root;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.MachineType;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.preference.application.HomePathPreference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import celtech.roboxbase.comms.remote.clear.WifiStatusResponse;

@Singleton
public class WifiControl {

	private static final Logger LOGGER = LogManager.getLogger();

	private final OpenAutomakerEnv env;
	private final HomePathPreference homePathPreference;

	@Inject
	public WifiControl(OpenAutomakerEnv env, HomePathPreference homePathPreference) {
		this.env = env;
		this.homePathPreference = homePathPreference;
	}

	private String runScript(String scriptName, String... parameters) {
		List<String> command = new ArrayList<>();
		command.add(homePathPreference.getAppValue() + scriptName);

		for (String param : parameters)
			command.add(param);

		ProcessBuilder builder = new ProcessBuilder(command);
		String scriptOutput = null;

		try {
			Process wifiSetupProcess = builder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(wifiSetupProcess.getInputStream()));
			StringBuilder stringBuilder = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (stringBuilder.length() > 0)
					stringBuilder.append(System.getProperty("line.separator"));
				stringBuilder.append(line);
			}
			scriptOutput = stringBuilder.toString();
		} catch (IOException ex) {
			LOGGER.error("Error " + ex);
		}

		return scriptOutput;
	}

	public boolean enableWifi(boolean enableWifi) {
		String wifiControl = enableWifi ? "on" : "off";
		boolean result = false;

		if (env.getMachineType() != MachineType.WINDOWS) {
			String output = runScript("enableDisableWifi.sh", wifiControl);
			LOGGER.info(output);
			result = (output != null);
		}
		return result;
	}

	public WifiStatusResponse getCurrentWifiState() {
		final String SCRIPT_BASE = "getCurrentWifiState";
		String scriptOutput;

		if (env.getMachineType() == MachineType.WINDOWS)
			scriptOutput = runScript(SCRIPT_BASE + ".bat");
		else
			scriptOutput = runScript(SCRIPT_BASE + ".sh");

		WifiStatusResponse response = null;

		ObjectMapper mapper = new ObjectMapper();
		try {
			response = mapper.readValue(scriptOutput, WifiStatusResponse.class);
		} catch (IOException ex) {
			LOGGER.error("Unable to decipher wifi status response", ex);
		}

		return response;
	}

	public boolean setupWiFiCredentials(String ssidAndPassword) {
		boolean result = false;
		if (env.getMachineType() != MachineType.WINDOWS) {
			String output = runScript("setupWifi.sh", ssidAndPassword);
			LOGGER.info(output);
			result = (output != null);
		}
		return result;
	}
}
