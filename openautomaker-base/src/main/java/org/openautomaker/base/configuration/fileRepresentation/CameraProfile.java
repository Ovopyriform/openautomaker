package org.openautomaker.base.configuration.fileRepresentation;

import java.util.HashMap;
import java.util.Map;

import org.openautomaker.base.configuration.BaseConfiguration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CameraProfile {
	public final String profileName;
	public final int captureHeight;
	public final int captureWidth;
	public final boolean headLightOff;
	public final boolean ambientLightOff;
	public final boolean moveBeforeCapture;
	public final int moveToX;
	public final int moveToY;
	public final String cameraName;
	public final Map<String, String> controlSettings;

	@JsonIgnore
	public boolean systemProfile = false;

	@JsonCreator
	public CameraProfile(
			@JsonProperty("profileName") String profileName,
			@JsonProperty("captureHeight") int captureHeight,
			@JsonProperty("captureWidth") int captureWidth,
			@JsonProperty("headLightOff") boolean headLightOff,
			@JsonProperty("ambientLightOff") boolean ambientLightOff,
			@JsonProperty("moveBeforeCapture") boolean moveBeforeCapture,
			@JsonProperty("moveToX") int moveToX,
			@JsonProperty("moveToY") int moveToY,
			@JsonProperty("cameraName") String cameraName,
			@JsonProperty("controlSettings") Map<String, String> controlSettings) {
		this.profileName = profileName != null ? profileName : BaseConfiguration.defaultCameraProfileName;
		this.captureHeight = captureHeight;
		this.captureWidth = captureWidth;
		this.headLightOff = headLightOff;
		this.ambientLightOff = ambientLightOff;
		this.moveBeforeCapture = moveBeforeCapture;
		this.moveToX = moveToX;
		this.moveToY = moveToY;
		this.cameraName = cameraName != null ? cameraName : "";
		this.controlSettings = controlSettings != null ? controlSettings : new HashMap<>();
	}

	public CameraProfile(CameraProfile profileToCopy) {
		this("",
				profileToCopy.captureHeight,
				profileToCopy.captureWidth,
				profileToCopy.headLightOff,
				profileToCopy.ambientLightOff,
				profileToCopy.moveBeforeCapture,
				profileToCopy.moveToX,
				profileToCopy.moveToY,
				profileToCopy.cameraName,
				new HashMap<>(profileToCopy.controlSettings));
	}
}
