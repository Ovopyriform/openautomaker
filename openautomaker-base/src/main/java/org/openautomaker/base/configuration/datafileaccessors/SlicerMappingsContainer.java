package org.openautomaker.base.configuration.datafileaccessors;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.fileRepresentation.SlicerMappings;
import org.openautomaker.environment.preference.printer.PrintProfilesPathPreference;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 *
 * @author ianhudson
 */
//TODO: Look at putting all slicer stuff into one project.
@Singleton
public final class SlicerMappingsContainer {

	private static final Logger LOGGER = LogManager.getLogger();

	private static SlicerMappingsContainer instance = null;
	private static SlicerMappings slicerMappingsFile = null;
	private static final ObjectMapper mapper = new ObjectMapper();

	public static final String defaultSlicerMappingsFileName = "slicermapping.dat";

	private final PrintProfilesPathPreference printProfilesPathPreference;

	@Inject
	protected SlicerMappingsContainer(
			PrintProfilesPathPreference printProfilesPathPreference) {

		this.printProfilesPathPreference = printProfilesPathPreference;

		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
		loadSlicerMappingsFile();

		instance = this;
	}

	public void loadSlicerMappingsFile() {
		File slicerMappingsInputFile = printProfilesPathPreference.getAppValue().resolve(defaultSlicerMappingsFileName).toFile();

		if (!slicerMappingsInputFile.exists()) {
			slicerMappingsFile = new SlicerMappings(null);
			try {
				mapper.writeValue(slicerMappingsInputFile, slicerMappingsFile);
			}
			catch (IOException ex) {
				LOGGER.error("Error trying to load slicer mapping file");
			}

			return;
		}

		try {
			slicerMappingsFile = mapper.readValue(slicerMappingsInputFile, SlicerMappings.class);

		}
		catch (IOException ex) {
			LOGGER.error("Error loading slicer mapping file " + slicerMappingsInputFile.getAbsolutePath(), ex);
		}
	}

	/**
	 *
	 * @return
	 */
	@Deprecated
	public static SlicerMappingsContainer getInstance() {
		return instance;
	}

	/**
	 *
	 * @return
	 */
	public SlicerMappings getSlicerMappings() {
		return slicerMappingsFile;
	}
}
