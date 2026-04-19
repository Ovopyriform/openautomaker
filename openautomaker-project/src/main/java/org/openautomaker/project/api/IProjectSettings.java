package org.openautomaker.project.api;

/**
 * Print settings associated with a project.
 * Abstracts over {@code PrinterSettingsOverrides} + filament data from openautomaker-base.
 */
public interface IProjectSettings {

	String getExtruder0FilamentID();

	String getExtruder1FilamentID();

	String getSettingsName();

	/** Print quality name, e.g. {@code "DRAFT"}, {@code "NORMAL"}, {@code "FINE"}. */
	String getPrintQuality();

	int getBrimOverride();

	float getFillDensityOverride();

	boolean isFillDensityOverridenByUser();

	boolean isPrintSupportOverride();

	/** Support type name, e.g. {@code "MATERIAL_1"}, {@code "MATERIAL_2"}. */
	String getPrintSupportTypeOverride();

	boolean isPrintRaft();

	boolean isSpiralPrint();
}
