package org.openautomaker.project.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.openautomaker.project.rbxproj.data.GcodeSettingsData;

public interface IProjectReader {

	/**
	 * Load a {@code .rbxproj} archive.
	 *
	 * @param rbxprojPath path on the base filesystem to the {@code .rbxproj} file
	 * @return populated project, or {@code null} if loading failed
	 */
	IProject read(Path rbxprojPath);

	/**
	 * Extract the GCode for a printer type to a temp file on the base filesystem.
	 *
	 * @param rbxprojPath     path on the base filesystem to the {@code .rbxproj} file
	 * @param printerTypeCode printer type code (e.g. {@code "RBX01"})
	 * @return path to the extracted GCode file, or empty if no GCode exists for this printer type
	 */
	Optional<Path> readGcode(Path rbxprojPath, String printerTypeCode);

	/**
	 * Read the settings snapshot stored alongside GCode for a specific printer type.
	 * Compare against current settings to determine if the cached GCode is stale.
	 *
	 * @param rbxprojPath     path on the base filesystem to the {@code .rbxproj} file
	 * @param printerTypeCode printer type code (e.g. {@code "RBX01"})
	 * @return the settings used when the GCode was generated, or empty if none stored
	 */
	Optional<GcodeSettingsData> readGcodeSettings(Path rbxprojPath, String printerTypeCode);

	/**
	 * List all printer type codes that have GCode stored in the archive.
	 *
	 * @param rbxprojPath path on the base filesystem to the {@code .rbxproj} file
	 * @return printer type codes, never {@code null}
	 */
	List<String> listGcodeTargets(Path rbxprojPath);

}