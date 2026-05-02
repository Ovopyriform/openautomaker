package org.openautomaker.base.configuration.fileRepresentation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.openautomaker.base.camera.CameraInfo;
import org.parboiled.common.FileUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CameraSettings {

	public final CameraProfile profile;
	public final CameraInfo camera;

	@JsonCreator
	public CameraSettings(@JsonProperty("profile") CameraProfile profile, @JsonProperty("camera") CameraInfo camera) {
		this.profile = profile;
		this.camera = camera;
	}

	@JsonIgnore
	public void writeToFile(Path fileLocation) throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		File file = fileLocation.toFile();
		FileUtils.ensureParentDir(file);
		mapper.writeValue(file, this);
	}

	@JsonIgnore
	public static CameraSettings readFromFile(Path fileLocation) throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		File file = fileLocation.toFile();
		return mapper.readValue(file, CameraSettings.class);
	}

	@JsonIgnore
	private void appendParametersForRootScript(List<String> parameters) {
		parameters.add(camera.getUdevName());
		parameters.add(String.format("%dx%d", profile.captureWidth, profile.captureHeight));
		profile.controlSettings.forEach((k, v) -> {
			if (k.startsWith("--")) {
				parameters.add(k);
				parameters.add(v);
			}
			else {
				parameters.add("-s");
				parameters.add(String.format("%s=%s", k, v));
			}
		});
	}

	@JsonIgnore
	public List<String> encodeSettingsForRootScript() {
		List<String> parameters = new ArrayList<>();
		appendParametersForRootScript(parameters);
		return parameters;
	}

	@JsonIgnore
	public List<String> encodeSettingsForRootScript(String printerName, String jobID) {
		List<String> parameters = new ArrayList<>();
		parameters.add(printerName);
		parameters.add(jobID);
		appendParametersForRootScript(parameters);
		return parameters;
	}
}
