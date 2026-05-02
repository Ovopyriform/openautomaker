package org.openautomaker.base.configuration.fileRepresentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TimelapseSettings {
	public final boolean triggerEnabled;
	public final String cameraProfileName;
	public final String cameraName;

	@JsonCreator
	public TimelapseSettings(
			@JsonProperty("triggerEnabled") boolean triggerEnabled,
			@JsonProperty("cameraProfileName") String cameraProfileName,
			@JsonProperty("cameraName") String cameraName) {
		this.triggerEnabled = triggerEnabled;
		this.cameraProfileName = cameraProfileName;
		this.cameraName = cameraName;
	}
}
