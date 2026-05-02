package org.openautomaker.base.comms.print_server.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CameraTag {

	private final String cameraProfileName;
	private final String cameraName;

	public CameraTag() {
		this.cameraProfileName = "";
		this.cameraName = "";
	}

	@JsonCreator
	public CameraTag(
			@JsonProperty("cameraProfileName") String cameraProfileName,
			@JsonProperty("cameraName") String cameraName) {
		this.cameraProfileName = cameraProfileName;
		this.cameraName = cameraName;
	}

	public String getCameraProfileName() {
		return cameraProfileName;
	}

	public String getCameraName() {
		return cameraName;
	}
}
