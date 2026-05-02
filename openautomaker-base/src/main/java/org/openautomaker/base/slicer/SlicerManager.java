package org.openautomaker.base.slicer;

import java.nio.file.Path;
import java.text.MessageFormat;

import org.openautomaker.base.configuration.datafileaccessors.SlicerMappingsContainer;
import org.openautomaker.base.configuration.fileRepresentation.SlicerMappingData;
import org.openautomaker.base.inject.slicer.SlicerConfigWriterFactory;
import org.openautomaker.environment.Slicer;
import org.openautomaker.environment.preference.application.HomePathPreference;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
import org.openautomaker.environment.properties.NativeProperties;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * SlicerManager class. Deals with all the features around the current slicer. Single point for slicer info.
 */
@Singleton
public class SlicerManager {

	// These two need to be in a constants file somewhere.
	private static final String OPENAUTOMAKER = "openautomaker";
	private static final String NATIVE = "native";

	private static final String SLICER_EXECUTABLE = "openautomaker.native.slicer.executable";
	private static final String SLICER_PARAMS = "openautomaker.native.slicer.params";
	private static final String SLICER_PARAMS_MESH = "openautomaker.native.slicer.params.mesh";

	//TODO: Slicer Shell configuration.  At the moment, only used for Windows
	private static final String SLICER_SHELL = "openautomaker.native.slicer.shell.executable";
	private static final String SLICER_SHELL_PARAMS = "openautomaker.native.slicer.shell.params";

	private final SlicerPreference slicerPreference;
	private final HomePathPreference homePathPreference;
	private final NativeProperties nativeProperties;
	private final SlicerConfigWriterFactory slicerConfigWriterFactory;
	private final SlicerMappingsContainer slicerMappingsContainer;

	@Inject
	protected SlicerManager(
			SlicerPreference slicerPreference,
			HomePathPreference homePathPreference,
			NativeProperties nativeProperties,
			SlicerConfigWriterFactory slicerConfigWriterFactory,
			SlicerMappingsContainer slicerMappingsContainer) {
		
		this.slicerPreference = slicerPreference;
		this.homePathPreference = homePathPreference;
		this.nativeProperties = nativeProperties;
		this.slicerConfigWriterFactory = slicerConfigWriterFactory;
		this.slicerMappingsContainer = slicerMappingsContainer;

	}

	public Slicer getSlicer() {
		return slicerPreference.getValue();
	}

	public void setSlicer(Slicer slicer) {
		if (slicer != null)
			slicerPreference.setValue(slicer);
	}

	/**
	 * Gets the Path to the shell executable if required for this platform
	 * 
	 * @return Path to the shell executable or null if not required
	 */
	public Path getExecutable() {
		return homePathPreference.getAppValue()
				.resolve(OPENAUTOMAKER)
				.resolve(NATIVE)
				.resolve(getSlicer().getPathModifier())
				.resolve(nativeProperties.get(SLICER_EXECUTABLE));
	}

	//TODO: These look like they should be called getXParams rather than format...
	public String[] formatParams(Path jsonSettings, Path tempGCodeFile) {
		return MessageFormat.format(nativeProperties.get(SLICER_PARAMS).replaceAll(" ", "|"), jsonSettings, tempGCodeFile, getExecutable().toString()).split("\\|");
	}

	public String[] formatMeshParams(Integer extruderNumber, Path meshFile) {
		return MessageFormat.format(nativeProperties.get(SLICER_PARAMS_MESH).replaceAll(" ", "|"), extruderNumber, meshFile).split("\\|");
	}

	public Path getShell() {
		String slicerShell = nativeProperties.get(SLICER_SHELL);

		if (slicerShell == null)
			return null;

		// Take the shell path as a full path to the shell
		return Path.of(slicerShell);
	}

	/**
	 * Gets an array of the parsed parameters for the shell command
	 * 
	 * @return String[] of the parsed parameters
	 */
	public String[] getShellParams() {
		String slicerShellParams = nativeProperties.get(SLICER_SHELL_PARAMS);

		if (slicerShellParams == null)
			return null;

		return slicerShellParams.split("\\ ");
	}

	/**
	 * Returns the config writer for the chosen slicer
	 * 
	 * @return SlicerConfigWriter implementation
	 */
	//TODO: This should no longer be needed as there's only one config writer
	public SlicerConfigWriter getConfigWriter() {
		return slicerConfigWriterFactory.create();
	}

	public SlicerMappingData getMappings() {
		return slicerMappingsContainer.getSlicerMappings().mappings.get(getSlicer());
	}
}
