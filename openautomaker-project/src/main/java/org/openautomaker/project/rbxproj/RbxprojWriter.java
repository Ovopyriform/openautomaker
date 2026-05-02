package org.openautomaker.project.rbxproj;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.rbxproj.data.GcodeSettingsData;
import org.openautomaker.project.rbxproj.data.ModelFileEntry;
import org.openautomaker.project.rbxproj.data.ModelPlacement;
import org.openautomaker.project.rbxproj.data.ModelTransformData;
import org.openautomaker.project.rbxproj.data.ModelsManifest;
import org.openautomaker.project.rbxproj.data.PlacementsData;
import org.openautomaker.project.rbxproj.data.PrintSettingsData;
import org.openautomaker.project.rbxproj.data.ProjectMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Saves an {@link IProject} to a {@code .rbxproj} ZIP archive.
 *
 * <p>Identical model files (same SHA-256 content hash) are stored only once. Files already
 * present in the archive are never re-copied; only new models are added. Metadata and
 * placements are always overwritten. The archive is created on first save and updated
 * incrementally on subsequent saves.
 *
 * <p>Archive layout:
 * <pre>
 *   project.json          — metadata and print settings
 *   placements.json       — per-model transforms and group hierarchy
 *   models/
 *     manifest.json       — original filename → stored filename mapping + content hashes
 *     &lt;uuid&gt;-name.stl    — deduplicated model files
 *   gcode/
 *     &lt;printerId&gt;/
 *       output.gcode      — added post-slice via {@link #updateGcode}
 * </pre>
 */
@Singleton
public class RbxprojWriter {

	private static final Logger LOGGER = LogManager.getLogger();

	private final ObjectMapper mapper;

	@Inject
	public RbxprojWriter() {
		mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	}

	/**
	 * Write {@code project} to a {@code .rbxproj} archive at {@code targetPath}.
	 * Creates the archive if absent. Model files already present (matched by SHA-256) are
	 * reused; only new models are added. Metadata and placements are always overwritten.
	 *
	 * @param project    the project to save
	 * @param targetPath path on the base filesystem (should end with {@link RbxprojFile#EXTENSION})
	 */
	public void write(IProject project, Path targetPath) throws IOException {
		Files.createDirectories(targetPath.getParent());

		try (FileSystem zip = FileSystems.newFileSystem(targetPath, Map.of("create", "true"))) {
			Files.createDirectories(zip.getPath(RbxprojFile.MODELS_DIR));

			Map<Integer, String> modelIdToZipPath = new HashMap<>();
			ModelsManifest manifest = writeModels(project, zip, modelIdToZipPath);

			writeJson(zip, RbxprojFile.PROJECT_JSON_ENTRY, buildMetadata(project));
			writeJson(zip, RbxprojFile.PLACEMENTS_JSON_ENTRY, buildPlacementsData(project, modelIdToZipPath));
			writeJson(zip, RbxprojFile.MODELS_MANIFEST_ENTRY, manifest);
		}

		LOGGER.info("Saved project to {}", targetPath);
	}

	/**
	 * Add or replace GCode for a printer type inside an existing {@code .rbxproj} archive.
	 * Writes both {@code output.gcode} and a {@code settings.json} snapshot so callers can
	 * detect when the cached GCode is stale.
	 *
	 * @param rbxprojPath     path on the base filesystem to the existing {@code .rbxproj} file
	 * @param printerTypeCode printer type code used as the subfolder name (e.g. {@code "RBX01"})
	 * @param gcodeFile       path on the base filesystem to the generated {@code .gcode} file
	 * @param gcodeSettings   settings snapshot at time of slicing
	 */
	public void updateGcode(Path rbxprojPath, String printerTypeCode, Path gcodeFile,
			GcodeSettingsData gcodeSettings) throws IOException {
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			Path entry = zip.getPath(RbxprojFile.gcodeEntry(printerTypeCode));
			Files.createDirectories(entry.getParent());
			Files.copy(gcodeFile, entry, StandardCopyOption.REPLACE_EXISTING);
			writeJson(zip, RbxprojFile.gcodeSettingsEntry(printerTypeCode), gcodeSettings);
		}
		LOGGER.info("Updated GCode for printer type {} in {}", printerTypeCode, rbxprojPath);
	}

	/**
	 * Copy new model files into the archive, skipping any already present by SHA-256 hash.
	 * Models loaded from a {@code .rbxproj} are identified by their stored hash and entry path,
	 * avoiding any dependency on the original base-filesystem import location.
	 * Populates {@code modelIdToZipPath} as a side effect.
	 *
	 * @return updated manifest (existing entries preserved, new entries appended)
	 */
	private ModelsManifest writeModels(IProject project, FileSystem targetZip,
			Map<Integer, String> modelIdToZipPath) throws IOException {

		// Seed from existing manifest so already-stored files are never re-copied.
		LinkedHashMap<String, ModelFileEntry> entriesByPath = new LinkedHashMap<>();
		Map<String, String> hashToZipPath = new HashMap<>();
		loadExistingManifest(targetZip, entriesByPath, hashToZipPath);

		for (IProjectModel model : project.getTopLevelModels()) {
			for (IProjectModel leaf : model.getLeafModels()) {
				String hash = leaf.getRbxprojContentHash();

				if (hash != null && !hash.isBlank()) {
					// Model was loaded from a .rbxproj — use stored hash, no base-filesystem access needed
					String existingZipPath = hashToZipPath.get(hash);
					if (existingZipPath != null) {
						modelIdToZipPath.put(leaf.getModelId(), existingZipPath);
						LOGGER.debug("Model {} already in archive as {}", leaf.getModelId(), existingZipPath);
						continue;
					}
				}

				// No existing hash, or hash mapped to a path — must be a new model not loaded from a .rbxproj, so hash the source file
				
				// Freshly imported model — hash the source file on the base filesystem
				Path sourceFile = leaf.getSourcePath();
				if (sourceFile == null || !Files.exists(sourceFile)) {
					LOGGER.warn("Model {} has no accessible source file — skipping", leaf.getModelId());
					continue;
				}

				hash = sha256(sourceFile);
				String existingZipPath = hashToZipPath.get(hash);

				// If a file with the same content hash is already in the archive, reuse it instead of copying again
				if (existingZipPath != null) {
					modelIdToZipPath.put(leaf.getModelId(), existingZipPath);
					LOGGER.debug("Model {} already stored as {}", leaf.getModelId(), existingZipPath);
					continue;
				}
				
				// New model file — copy into archive and record in manifest
				String originalName = sourceFile.getFileName().toString();
				String zipEntryPath = RbxprojFile.MODELS_DIR
						+ UUID.randomUUID().toString().replace("-", "").substring(0, 8)
						+ "-" + originalName;

				Files.copy(sourceFile, targetZip.getPath(zipEntryPath));

				ModelFileEntry entry = new ModelFileEntry(zipEntryPath, originalName, hash);
				entriesByPath.put(zipEntryPath, entry);
				hashToZipPath.put(hash, zipEntryPath);
				modelIdToZipPath.put(leaf.getModelId(), zipEntryPath);
			}
		}

		return new ModelsManifest(new ArrayList<>(entriesByPath.values()));
	}

	/**
	 * Load the existing models manifest from the ZIP filesystem.
	 *
	 * @param zip ZIP filesystem to read from
	 * @param entriesByPath mapping of ZIP entry paths to model file entries
	 * @param hashToZipPath mapping of SHA-256 hashes to ZIP entry paths
	 */
	private void loadExistingManifest(FileSystem zip, LinkedHashMap<String, ModelFileEntry> entriesByPath,
			Map<String, String> hashToZipPath) {
		Path manifestPath = zip.getPath(RbxprojFile.MODELS_MANIFEST_ENTRY);

		if (!Files.exists(manifestPath))
			return;

		try {
			ModelsManifest existing = mapper.readValue(Files.readAllBytes(manifestPath), ModelsManifest.class);
			existing.entries.forEach(e -> {
				entriesByPath.put(e.filePath, e);
				hashToZipPath.put(e.contentHash, e.filePath);
			});
		}
		catch (IOException ex) {
			LOGGER.warn("Could not read existing models manifest — treating as empty", ex);
		}
	}

	/**
	 * Write JSON data to a file in the ZIP filesystem.
	 *
	 * @param zip - ZIP filesystem to write to
	 * @param entryName - name of the entry to write
	 * @param value - object to serialize
	 * @throws IOException if writing fails
	 */
	private void writeJson(FileSystem zip, String entryName, Object value) throws IOException {
		Path target = zip.getPath(entryName);
		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(target, mapper.writeValueAsBytes(value));
	}

	/**
	 * Build the project metadata object from the given project.
	 *
	 * @param project the project to extract metadata from
	 * @return a ProjectMetadata instance populated with data from the project
	 */
	private ProjectMetadata buildMetadata(IProject project) {
		IProjectSettings s = project.getSettings();

		PrintSettingsData settings = new PrintSettingsData(
				s.getExtruder0FilamentID(),
				s.getExtruder1FilamentID(),
				s.getSettingsName(),
				s.getPrintQuality(),
				s.getBrimOverride(),
				s.getFillDensityOverride(),
				s.isFillDensityOverridenByUser(),
				s.isPrintSupportOverride(),
				s.getPrintSupportTypeOverride(),
				s.isPrintRaft(),
				s.isSpiralPrint());

		return new ProjectMetadata(
				RbxprojFile.CURRENT_VERSION,
				project.getProjectName(),
				Instant.now().toString(),
				settings);
	}

	/**
	 * Build the placements data object from the given project.
	 *
	 * @param project the project to extract placements from
	 * @param modelIdToZipPath mapping of model IDs to ZIP paths
	 * @return a PlacementsData instance populated with data from the project
	 */
	private PlacementsData buildPlacementsData(IProject project, Map<Integer, String> modelIdToZipPath) {
		List<ModelPlacement> placements = new ArrayList<>();
		for (IProjectModel model : project.getTopLevelModels()) {
			for (IProjectModel leaf : model.getLeafModels()) {
				String zipPath = modelIdToZipPath.get(leaf.getModelId());
				if (zipPath == null) continue;

				placements.add(new ModelPlacement(
						leaf.getModelId(),
						zipPath,
						leaf.getModelName(),
						leaf.getExtruder(),
						leaf.getTransform()));
			}
		}

		Map<Integer, Set<Integer>> groupStructure = project.getGroupStructure();
		Map<Integer, ModelTransformData> groupTransforms = project.getGroupTransforms();
		return new PlacementsData(RbxprojFile.CURRENT_VERSION, placements, groupStructure, groupTransforms);
	}

	private static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream is = Files.newInputStream(file)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = is.read(buffer)) != -1) {
					digest.update(buffer, 0, read);
				}
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 unavailable", e);
		}
	}
}
