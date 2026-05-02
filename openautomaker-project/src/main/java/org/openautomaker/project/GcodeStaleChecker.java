package org.openautomaker.project;

import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.rbxproj.data.GcodeSettingsData;
import org.openautomaker.project.rbxproj.data.PrintSettingsData;

/**
 * Determines whether cached GCode is stale relative to current project
 * settings.
 *
 * <p>
 * GCode is considered stale if the printer type, head type, or any print
 * setting that affects the slice output has changed since the GCode was
 * generated.
 */
public final class GcodeStaleChecker {

	private GcodeStaleChecker() {
	}

	/**
	 * Returns {@code true} if the cached GCode should be discarded and the project
	 * re-sliced.
	 *
	 * @param stored             settings snapshot stored alongside the cached GCode
	 * @param currentPrinterType type code of the printer that will be used (e.g.
	 *                           {@code "RBX01"})
	 * @param currentHeadType    head type that will be used for slicing
	 * @param currentSettings    current project print settings
	 */
	public static boolean isStale(GcodeSettingsData stored, String currentPrinterType, String currentHeadType, IProjectSettings currentSettings) {

		if (!stored.printerTypeCode.equals(currentPrinterType))
			return true;
		if (!stored.headType.equals(currentHeadType))
			return true;

		PrintSettingsData snap = stored.printSettings;
		if (!eq(snap.printQuality, currentSettings.getPrintQuality()))
			return true;
		if (!eq(snap.extruder0FilamentID, currentSettings.getExtruder0FilamentID()))
			return true;
		if (!eq(snap.extruder1FilamentID, currentSettings.getExtruder1FilamentID()))
			return true;
		if (!eq(snap.settingsName, currentSettings.getSettingsName()))
			return true;
		if (snap.brimOverride != currentSettings.getBrimOverride())
			return true;
		if (snap.fillDensityOverride != currentSettings.getFillDensityOverride())
			return true;
		if (snap.fillDensityOverridenByUser != currentSettings.isFillDensityOverridenByUser())
			return true;
		if (snap.printSupportOverride != currentSettings.isPrintSupportOverride())
			return true;
		if (!eq(snap.printSupportTypeOverride, currentSettings.getPrintSupportTypeOverride()))
			return true;
		if (snap.printRaft != currentSettings.isPrintRaft())
			return true;
		if (snap.spiralPrint != currentSettings.isSpiralPrint())
			return true;

		return false;
	}

	/**
	 * Builds a {@link GcodeSettingsData} snapshot from current settings for storage
	 * alongside GCode.
	 *
	 * @param printerTypeCode printer type code (e.g. {@code "RBX01"})
	 * @param headType        head type used for slicing
	 * @param settings        current project print settings
	 */
	public static GcodeSettingsData snapshot(String printerTypeCode, String headType, IProjectSettings settings) {
		PrintSettingsData ps = new PrintSettingsData(
				settings.getExtruder0FilamentID(),
				settings.getExtruder1FilamentID(),
				settings.getSettingsName(),
				settings.getPrintQuality(),
				settings.getBrimOverride(),
				settings.getFillDensityOverride(),
				settings.isFillDensityOverridenByUser(),
				settings.isPrintSupportOverride(),
				settings.getPrintSupportTypeOverride(),
				settings.isPrintRaft(),
				settings.isSpiralPrint());
		return new GcodeSettingsData(printerTypeCode, headType, ps);
	}

	private static boolean eq(String a, String b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		return a.equals(b);
	}
}
