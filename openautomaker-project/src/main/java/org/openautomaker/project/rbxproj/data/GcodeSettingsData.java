package org.openautomaker.project.rbxproj.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapshot of the settings used to generate a GCode file.
 * Written to {@code gcode/<printerTypeCode>/settings.json} alongside {@code output.gcode}.
 *
 * <p>Compare a saved snapshot against current project settings to determine whether the cached
 * GCode is stale and needs re-slicing.
 */
public class GcodeSettingsData {

	/** Printer type code this GCode targets, e.g. {@code "RBX01"}, {@code "RBX02"}. */
	public final String printerTypeCode;

	/** Head type identifier used for the slice profile lookup, e.g. {@code "SINGLE_MATERIAL_HEAD"}. */
	public final String headType;

	/** Full print settings snapshot at time of slicing. */
	public final PrintSettingsData printSettings;

	@JsonCreator
	public GcodeSettingsData(
			@JsonProperty("printerTypeCode") String printerTypeCode,
			@JsonProperty("headType") String headType,
			@JsonProperty("printSettings") PrintSettingsData printSettings) {
		this.printerTypeCode = printerTypeCode;
		this.headType = headType;
		this.printSettings = printSettings;
	}
}
