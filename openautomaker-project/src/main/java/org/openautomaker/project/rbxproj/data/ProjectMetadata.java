package org.openautomaker.project.rbxproj.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectMetadata {

	public final int version;
	public final String projectName;
	public final String lastModified;
	public final PrintSettingsData printSettings;

	@JsonCreator
	public ProjectMetadata(
			@JsonProperty("version") int version,
			@JsonProperty("projectName") String projectName,
			@JsonProperty("lastModified") String lastModified,
			@JsonProperty("printSettings") PrintSettingsData printSettings) {
		this.version = version;
		this.projectName = projectName;
		this.lastModified = lastModified;
		this.printSettings = printSettings;
	}
}
