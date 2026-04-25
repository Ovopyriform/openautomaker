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
		if (!eq(snap.getPrintQuality(), currentSettings.getPrintQuality()))
			return true;
		if (!eq(snap.getExtruder0FilamentID(), currentSettings.getExtruder0FilamentID()))
			return true;
		if (!eq(snap.getExtruder1FilamentID(), currentSettings.getExtruder1FilamentID()))
			return true;
		if (!eq(snap.getSettingsName(), currentSettings.getSettingsName()))
			return true;
		if (snap.getBrimOverride() != currentSettings.getBrimOverride())
			return true;
		if (snap.getFillDensityOverride() != currentSettings.getFillDensityOverride())
			return true;
		if (snap.isFillDensityOverridenByUser() != currentSettings.isFillDensityOverridenByUser())
			return true;
		if (snap.isPrintSupportOverride() != currentSettings.isPrintSupportOverride())
			return true;
		if (!eq(snap.getPrintSupportTypeOverride(), currentSettings.getPrintSupportTypeOverride()))
			return true;
		if (snap.isPrintRaft() != currentSettings.isPrintRaft())
			return true;
		if (snap.isSpiralPrint() != currentSettings.isSpiralPrint())
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
		PrintSettingsData ps = new PrintSettingsData();
		ps.setExtruder0FilamentID(settings.getExtruder0FilamentID());
		ps.setExtruder1FilamentID(settings.getExtruder1FilamentID());
		ps.setSettingsName(settings.getSettingsName());
		ps.setPrintQuality(settings.getPrintQuality());
		ps.setBrimOverride(settings.getBrimOverride());
		ps.setFillDensityOverride(settings.getFillDensityOverride());
		ps.setFillDensityOverridenByUser(settings.isFillDensityOverridenByUser());
		ps.setPrintSupportOverride(settings.isPrintSupportOverride());
		ps.setPrintSupportTypeOverride(settings.getPrintSupportTypeOverride());
		ps.setPrintRaft(settings.isPrintRaft());
		ps.setSpiralPrint(settings.isSpiralPrint());
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
