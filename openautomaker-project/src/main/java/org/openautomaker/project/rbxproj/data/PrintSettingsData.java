package org.openautomaker.project.rbxproj.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PrintSettingsData {

	public final String extruder0FilamentID;
	public final String extruder1FilamentID;
	public final String settingsName;
	public final String printQuality;
	public final int brimOverride;
	public final float fillDensityOverride;
	public final boolean fillDensityOverridenByUser;
	public final boolean printSupportOverride;
	public final String printSupportTypeOverride;
	public final boolean printRaft;
	public final boolean spiralPrint;

	@JsonCreator
	public PrintSettingsData(
			@JsonProperty("extruder0FilamentID") String extruder0FilamentID,
			@JsonProperty("extruder1FilamentID") String extruder1FilamentID,
			@JsonProperty("settingsName") String settingsName,
			@JsonProperty("printQuality") String printQuality,
			@JsonProperty("brimOverride") int brimOverride,
			@JsonProperty("fillDensityOverride") float fillDensityOverride,
			@JsonProperty("fillDensityOverridenByUser") boolean fillDensityOverridenByUser,
			@JsonProperty("printSupportOverride") boolean printSupportOverride,
			@JsonProperty("printSupportTypeOverride") String printSupportTypeOverride,
			@JsonProperty("printRaft") boolean printRaft,
			@JsonProperty("spiralPrint") boolean spiralPrint) {
		this.extruder0FilamentID = extruder0FilamentID;
		this.extruder1FilamentID = extruder1FilamentID;
		this.settingsName = settingsName;
		this.printQuality = printQuality;
		this.brimOverride = brimOverride;
		this.fillDensityOverride = fillDensityOverride;
		this.fillDensityOverridenByUser = fillDensityOverridenByUser;
		this.printSupportOverride = printSupportOverride;
		this.printSupportTypeOverride = printSupportTypeOverride;
		this.printRaft = printRaft;
		this.spiralPrint = spiralPrint;
	}
}
