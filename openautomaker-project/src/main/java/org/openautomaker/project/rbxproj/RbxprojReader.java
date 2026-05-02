package org.openautomaker.project.rbxproj;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.root.TempPathPreference;
import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectFactory;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectReader;
import org.openautomaker.project.rbxproj.data.GcodeSettingsData;
import org.openautomaker.project.rbxproj.data.ModelFileEntry;
import org.openautomaker.project.rbxproj.data.ModelPlacement;
import org.openautomaker.project.rbxproj.data.ModelsManifest;
import org.openautomaker.project.rbxproj.data.PlacementsData;
import org.openautomaker.project.rbxproj.data.ProjectMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Loads a {@code .rbxproj} ZIP archive into an {@link IProject}.
 *
 * <p>Call {@link #read(Path)} from a background thread — model import is not JavaFX-thread-safe.
 */
@Singleton
public class RbxprojReader implements IProjectReader {

	private static final Logger LOGGER = LogManager.getLogger();

	private final IProjectFactory projectFactory;
	private final List<IModelLoader> modelLoaders;
	private final TempPathPreference tempPathPreference;
	private final ObjectMapper mapper;

	@Inject
	public RbxprojReader(
			IProjectFactory projectFactory,
			List<IModelLoader> modelLoaders,
			TempPathPreference tempPathPreference) {
		this.projectFactory = projectFactory;
		this.modelLoaders = modelLoaders;
		this.tempPathPreference = tempPathPreference;
		this.mapper = new ObjectMapper();
	}

	/**
	 * Load a {@code .rbxproj} archive.
	 *
	 * @param rbxprojPath path on the base filesystem to the {@code .rbxproj} file
	 * @return populated project, or {@code null} if loading failed
	 */
	@Override
	public IProject read(Path rbxprojPath) {
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			ProjectMetadata metadata = readEntry(zip, RbxprojFile.PROJECT_JSON_ENTRY, ProjectMetadata.class);
			PlacementsData placements = readEntry(zip, RbxprojFile.PLACEMENTS_JSON_ENTRY, PlacementsData.class);

			Map<String, ModelFileEntry> manifest = loadManifest(zip);

			IProject project = projectFactory.create();
			project.setProjectPath(rbxprojPath);
			if (metadata.projectName != null) {
				project.setProjectName(metadata.projectName);
			}

			for (ModelPlacement placement : placements.placements) {
				IProjectModel model = loadModel(zip, placement, manifest);
				if (model == null) continue;

				model.applyTransform(placement.transform);
				model.setExtruder(placement.extruder);
				model.setModelName(placement.modelName);
				project.addModel(model);
			}

			if (!placements.groups.isEmpty()) {
				project.recreateGroups(placements.groups, placements.groupTransforms);
			}

			return project;
		}
		catch (Exception ex) {
			LOGGER.error("Failed to load .rbxproj from {}", rbxprojPath, ex);
			return null;
		}
	}

	/**
	 * Extract the GCode for a printer type to a temp file on the base filesystem.
	 *
	 * @param rbxprojPath     path on the base filesystem to the {@code .rbxproj} file
	 * @param printerTypeCode printer type code (e.g. {@code "RBX01"})
	 * @return path to the extracted GCode file, or empty if no GCode exists for this printer type
	 */
	@Override
	public Optional<Path> readGcode(Path rbxprojPath, String printerTypeCode) {
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			Path entry = zip.getPath(RbxprojFile.gcodeEntry(printerTypeCode));
			if (!Files.exists(entry)) return Optional.empty();

			Path outFile = tempPathPreference.getValue().resolve(
					RbxprojFile.sanitisePrinterTypeCode(printerTypeCode) + "-" + RbxprojFile.GCODE_FILENAME);
			Files.copy(entry, outFile, StandardCopyOption.REPLACE_EXISTING);
			return Optional.of(outFile);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to read GCode for printer type {} from {}", printerTypeCode, rbxprojPath, ex);
			return Optional.empty();
		}
	}

	/**
	 * Read the settings snapshot stored alongside GCode for a specific printer type.
	 * Compare against current settings to determine if the cached GCode is stale.
	 *
	 * @param rbxprojPath     path on the base filesystem to the {@code .rbxproj} file
	 * @param printerTypeCode printer type code (e.g. {@code "RBX01"})
	 * @return the settings used when the GCode was generated, or empty if none stored
	 */
	@Override
	public Optional<GcodeSettingsData> readGcodeSettings(Path rbxprojPath, String printerTypeCode) {
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			Path entry = zip.getPath(RbxprojFile.gcodeSettingsEntry(printerTypeCode));
			if (!Files.exists(entry)) return Optional.empty();
			return Optional.of(mapper.readValue(Files.readAllBytes(entry), GcodeSettingsData.class));
		}
		catch (IOException ex) {
			LOGGER.error("Failed to read GCode settings for printer type {} from {}", printerTypeCode, rbxprojPath, ex);
			return Optional.empty();
		}
	}

	/**
	 * List all printer type codes that have GCode stored in the archive.
	 *
	 * @param rbxprojPath path on the base filesystem to the {@code .rbxproj} file
	 * @return printer type codes, never {@code null}
	 */
	@Override
	public List<String> listGcodeTargets(Path rbxprojPath) {
		List<String> types = new ArrayList<>();
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			Path gcodeDir = zip.getPath(RbxprojFile.GCODE_DIR);
			if (!Files.exists(gcodeDir)) return types;

			try (Stream<Path> entries = Files.walk(gcodeDir, 2)) {
				entries.filter(p -> RbxprojFile.GCODE_FILENAME.equals(p.getFileName().toString()))
						.map(p -> p.getParent().getFileName().toString())
						.forEach(types::add);
			}
		}
		catch (IOException ex) {
			LOGGER.error("Failed to list GCode targets in {}", rbxprojPath, ex);
		}
		return types;
	}

	private Map<String, ModelFileEntry> loadManifest(FileSystem zip) {
		Map<String, ModelFileEntry> result = new HashMap<>();
		Path manifestPath = zip.getPath(RbxprojFile.MODELS_MANIFEST_ENTRY);
		if (!Files.exists(manifestPath)) return result;
		try {
			ModelsManifest manifest = readEntry(zip, RbxprojFile.MODELS_MANIFEST_ENTRY, ModelsManifest.class);
			manifest.entries.forEach(e -> result.put(e.filePath, e));
		}
		catch (IOException ex) {
			LOGGER.warn("Could not read models manifest — falling back to filename heuristic", ex);
		}
		return result;
	}

	private <T> T readEntry(FileSystem zip, String entryName, Class<T> type) throws IOException {
		return mapper.readValue(Files.readAllBytes(zip.getPath(entryName)), type);
	}

	private IProjectModel loadModel(FileSystem zip, ModelPlacement placement,
			Map<String, ModelFileEntry> manifest) throws IOException {
		Path entry = zip.getPath(placement.modelFile);
		if (!Files.exists(entry)) {
			LOGGER.warn("Model file not found in archive: {}", placement.modelFile);
			return null;
		}

		ModelFileEntry manifestEntry = manifest.get(placement.modelFile);
		String originalName;
		if (manifestEntry != null) {
			originalName = manifestEntry.originalName;
		}
		else {
			// Fallback for archives written before the manifest was introduced
			originalName = Path.of(placement.modelFile).getFileName().toString();
			if (originalName.length() > 9 && originalName.charAt(8) == '-') {
				originalName = originalName.substring(9);
			}
		}

		String ext = extension(originalName).toLowerCase();
		IModelLoader loader = modelLoaders.stream()
				.filter(l -> l.supports(ext))
				.findFirst()
				.orElse(null);

		if (loader == null) {
			LOGGER.warn("No loader registered for format: {}", originalName);
			return null;
		}

		IProjectModel model = loader.load(entry);
		if (model != null) {
			model.setRbxprojEntryPath(placement.modelFile);
			if (manifestEntry != null) {
				model.setRbxprojContentHash(manifestEntry.contentHash);
			}
		}
		return model;
	}

	private static String extension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(dot + 1) : "";
	}
}
