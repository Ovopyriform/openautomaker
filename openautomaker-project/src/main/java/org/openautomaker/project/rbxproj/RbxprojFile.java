package org.openautomaker.project.rbxproj;

public final class RbxprojFile {

	public static final String EXTENSION = ".rbxproj";
	public static final String EXTENSION_WITHOUT_DOT = "rbxproj";
	public static final String PROJECT_JSON_ENTRY = "project.json";
	public static final String PLACEMENTS_JSON_ENTRY = "placements.json";
	public static final String MODELS_DIR = "models/";
	public static final String MODELS_MANIFEST_ENTRY = "models/manifest.json";
	public static final String GCODE_DIR = "gcode/";
	public static final String GCODE_FILENAME = "output.gcode";
	public static final String GCODE_SETTINGS_FILENAME = "settings.json";
	public static final int CURRENT_VERSION = 1;

	/**
	 * Returns the project-relative path for a printer type's GCode output.
	 * e.g. {@code "gcode/RBX01/output.gcode"}
	 *
	 * @param printerTypeCode printer type code, e.g. {@code "RBX01"}
	 */
	public static String gcodeEntry(String printerTypeCode) {
		return GCODE_DIR + sanitisePrinterTypeCode(printerTypeCode) + "/" + GCODE_FILENAME;
	}

	/**
	 * Returns the project-relative path for a printer type's settings snapshot.
	 * e.g. {@code "gcode/RBX01/settings.json"}
	 *
	 * @param printerTypeCode printer type code, e.g. {@code "RBX01"}
	 */
	public static String gcodeSettingsEntry(String printerTypeCode) {
		return GCODE_DIR + sanitisePrinterTypeCode(printerTypeCode) + "/" + GCODE_SETTINGS_FILENAME;
	}

	/**
	 * Returns the project-relative path for the printer type's GCode directory.
	 * e.g. {@code "gcode/RBX01/"}
	 */
	public static String gcodePrinterDir(String printerTypeCode) {
		return GCODE_DIR + sanitisePrinterTypeCode(printerTypeCode) + "/";
	}

	/** Strip characters that are invalid in directory name segments. */
	public static String sanitisePrinterTypeCode(String printerTypeCode) {
		return printerTypeCode.replaceAll("[^a-zA-Z0-9._\\-]", "_");
	}

	private RbxprojFile() {
	}
}
