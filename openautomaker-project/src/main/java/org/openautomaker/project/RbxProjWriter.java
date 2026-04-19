package org.openautomaker.project;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.data.GcodeSettingsData;
import org.openautomaker.project.data.ModelFileEntry;
import org.openautomaker.project.data.ModelPlacement;
import org.openautomaker.project.data.ModelTransformData;
import org.openautomaker.project.data.ModelsManifest;
import org.openautomaker.project.data.PlacementsData;
import org.openautomaker.project.data.PrintSettingsData;
import org.openautomaker.project.data.ProjectMetadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Saves an {@link IProject} to a {@code .rbxproj} ZIP archive.
 *
 * <p>Identical model files (same SHA-256 content hash) are stored only once regardless of
 * how many placements reference them. The mapping from original filenames to stored entries
 * is written to {@code models/manifest.json}.
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
public class RbxProjWriter {

	private static final Logger LOGGER = LogManager.getLogger();

	private final ObjectMapper mapper;

	@Inject
	public RbxProjWriter() {
		mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	}

	/**
	 * Write {@code project} to a {@code .rbxproj} file at {@code targetPath}.
	 * Any existing file at that path is replaced.
	 *
	 * @param project    the project to save
	 * @param targetPath destination path (should end with {@link RbxProjFile#EXTENSION})
	 */
	public void write(IProject project, Path targetPath) throws IOException {
		Files.createDirectories(targetPath.getParent());
		Files.deleteIfExists(targetPath);

		try (FileSystem zip = FileSystems.newFileSystem(targetPath, Map.of("create", "true"))) {
			Files.createDirectories(zip.getPath("models"));

			Map<Integer, String> modelIdToZipPath = new HashMap<>();
			ModelsManifest manifest = writeModels(project, zip, modelIdToZipPath);

			writeJson(zip, RbxProjFile.PROJECT_JSON_ENTRY, buildMetadata(project));
			writeJson(zip, RbxProjFile.PLACEMENTS_JSON_ENTRY, buildPlacementsData(project, modelIdToZipPath));
			writeJson(zip, RbxProjFile.MODELS_MANIFEST_ENTRY, manifest);
		}

		LOGGER.info("Saved project to {}", targetPath);
	}

	/**
	 * Add or replace GCode for a printer type inside an existing {@code .rbxproj} archive.
	 * Writes both {@code output.gcode} and a {@code settings.json} snapshot so callers can
	 * detect when the cached GCode is stale.
	 *
	 * @param rbxprojPath     path to the existing {@code .rbxproj} file
	 * @param printerTypeCode printer type code used as the subfolder name (e.g. {@code "RBX01"})
	 * @param gcodeFile       path to the generated {@code .gcode} file to embed
	 * @param gcodeSettings   settings snapshot at time of slicing
	 */
	public void updateGcode(Path rbxprojPath, String printerTypeCode, Path gcodeFile,
			GcodeSettingsData gcodeSettings) throws IOException {
		try (FileSystem zip = FileSystems.newFileSystem(rbxprojPath)) {
			Path entry = zip.getPath(RbxProjFile.gcodeEntry(printerTypeCode));
			Files.createDirectories(entry.getParent());
			Files.copy(gcodeFile, entry, StandardCopyOption.REPLACE_EXISTING);
			writeJson(zip, RbxProjFile.gcodeSettingsEntry(printerTypeCode), gcodeSettings);
		}
		LOGGER.info("Updated GCode for printer type {} in {}", printerTypeCode, rbxprojPath);
	}

	/**
	 * Write all model files into the archive, deduplicating by SHA-256 content hash.
	 * Populates {@code modelIdToZipPath} as a side effect.
	 *
	 * @return manifest describing every stored model file
	 */
	private ModelsManifest writeModels(IProject project, FileSystem zip,
			Map<Integer, String> modelIdToZipPath) throws IOException {

		Map<String, String> hashToZipPath = new HashMap<>();
		List<ModelFileEntry> entries = new ArrayList<>();

		for (IProjectModel model : project.getTopLevelModels()) {
			for (IProjectModel leaf : model.getLeafModels()) {
				if (leaf.getSourceFile() == null || !leaf.getSourceFile().exists()) {
					LOGGER.warn("Model {} has no accessible source file — skipping", leaf.getModelId());
					continue;
				}

				Path sourceFile = leaf.getSourceFile().toPath();
				String hash = sha256(sourceFile);
				String existing = hashToZipPath.get(hash);

				if (existing != null) {
					modelIdToZipPath.put(leaf.getModelId(), existing);
					LOGGER.debug("Deduplicating model {} → reusing {}", leaf.getModelId(), existing);
				}
				else {
					String originalName = leaf.getSourceFile().getName();
					String zipEntryPath = RbxProjFile.MODELS_DIR
							+ UUID.randomUUID().toString().replace("-", "").substring(0, 8)
							+ "-" + originalName;

					Files.copy(sourceFile, zip.getPath(zipEntryPath));

					hashToZipPath.put(hash, zipEntryPath);
					modelIdToZipPath.put(leaf.getModelId(), zipEntryPath);
					entries.add(new ModelFileEntry(zipEntryPath, originalName, hash));
				}
			}
		}

		return new ModelsManifest(entries);
	}

	private void writeJson(FileSystem zip, String entryName, Object value) throws IOException {
		Files.write(zip.getPath(entryName), mapper.writeValueAsBytes(value));
	}

	private ProjectMetadata buildMetadata(IProject project) {
		IProjectSettings s = project.getSettings();

		PrintSettingsData settings = new PrintSettingsData();
		settings.setExtruder0FilamentID(s.getExtruder0FilamentID());
		settings.setExtruder1FilamentID(s.getExtruder1FilamentID());
		settings.setSettingsName(s.getSettingsName());
		settings.setPrintQuality(s.getPrintQuality());
		settings.setBrimOverride(s.getBrimOverride());
		settings.setFillDensityOverride(s.getFillDensityOverride());
		settings.setFillDensityOverridenByUser(s.isFillDensityOverridenByUser());
		settings.setPrintSupportOverride(s.isPrintSupportOverride());
		settings.setPrintSupportTypeOverride(s.getPrintSupportTypeOverride());
		settings.setPrintRaft(s.isPrintRaft());
		settings.setSpiralPrint(s.isSpiralPrint());

		ProjectMetadata metadata = new ProjectMetadata();
		metadata.setVersion(RbxProjFile.CURRENT_VERSION);
		metadata.setProjectName(project.getProjectName());
		metadata.setLastModified(Instant.now().toString());
		metadata.setPrintSettings(settings);
		return metadata;
	}

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
		return new PlacementsData(RbxProjFile.CURRENT_VERSION, placements, groupStructure, groupTransforms);
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
