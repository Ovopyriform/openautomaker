package org.openautomaker.project.rbxproj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openautomaker.environment.preference.root.TempPathPreference;
import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectFactory;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectReader;
import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.rbxproj.data.GcodeSettingsData;
import org.openautomaker.project.rbxproj.data.ModelTransformData;
import org.openautomaker.project.rbxproj.data.PrintSettingsData;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RbxProjWriterReaderTest {

	@TempDir
	Path tempDir;

	// -- writer-side mocks --
	@Mock IProject project;
	@Mock IProjectSettings settings;
	@Mock IProjectModel model;

	// -- reader-side mocks --
	@Mock IProjectFactory projectFactory;
	@Mock IModelLoader stlLoader;
	@Mock IProject loadedProject;
	@Mock TempPathPreference tempPathPreference;

	private RbxprojWriter writer;
	private IProjectReader reader;

	@BeforeEach
	void setUp() {
		writer = new RbxprojWriter();
		when(tempPathPreference.getValue()).thenReturn(tempDir);
		reader = new RbxprojReader(projectFactory, List.of(stlLoader), tempPathPreference);

		// Common writer stubs
		when(project.getProjectName()).thenReturn("TestProject");
		when(project.getSettings()).thenReturn(settings);
		when(project.getTopLevelModels()).thenReturn(List.of());
		when(project.getGroupStructure()).thenReturn(Map.of());
		when(project.getGroupTransforms()).thenReturn(Map.of());
		stubSettings(settings);

		// Common reader stubs
		when(projectFactory.create()).thenReturn(loadedProject);
		when(stlLoader.supports("stl")).thenReturn(true);
	}

	// -----------------------------------------------------------------------
	// Archive structure
	// -----------------------------------------------------------------------

	@Test
	void write_createsArchiveWithRequiredEntries() throws IOException {
		Path archive = tempDir.resolve("test.rbxproj");

		writer.write(project, archive);

		assertThat(archive).exists();
		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			assertThat(Files.exists(zip.getPath(RbxprojFile.PROJECT_JSON_ENTRY))).isTrue();
			assertThat(Files.exists(zip.getPath(RbxprojFile.PLACEMENTS_JSON_ENTRY))).isTrue();
			assertThat(Files.exists(zip.getPath(RbxprojFile.MODELS_MANIFEST_ENTRY))).isTrue();
		}
	}

	@Test
	void write_updatesMetadataOnSubsequentSave() throws IOException {
		Path archive = tempDir.resolve("test.rbxproj");
		writer.write(project, archive);

		IProject project2 = mock(IProject.class);
		when(project2.getProjectName()).thenReturn("UpdatedProject");
		when(project2.getSettings()).thenReturn(settings);
		when(project2.getTopLevelModels()).thenReturn(List.of());
		when(project2.getGroupStructure()).thenReturn(Map.of());
		when(project2.getGroupTransforms()).thenReturn(Map.of());

		writer.write(project2, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String json = new String(Files.readAllBytes(zip.getPath(RbxprojFile.PROJECT_JSON_ENTRY)));
			assertThat(json).contains("UpdatedProject");
		}
	}

	@Test
	void write_preservesExistingModelFilesOnSubsequentSave() throws IOException {
		Path archive = tempDir.resolve("incremental.rbxproj");
		setupSingleModel();
		writer.write(project, archive);

		long countAfterFirst;
		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			countAfterFirst = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
		}

		// Second save with no models — model file must remain in archive
		when(project.getTopLevelModels()).thenReturn(List.of());
		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			long countAfterSecond = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
			assertThat(countAfterSecond).isEqualTo(countAfterFirst);
		}
	}

	@Test
	void write_doesNotRecopyModelWithSameContent() throws IOException {
		Path archive = tempDir.resolve("no-recopy.rbxproj");
		setupSingleModel();
		writer.write(project, archive);

		long countAfterFirst;
		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			countAfterFirst = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
		}

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			long countAfterSecond = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
			assertThat(countAfterSecond).isEqualTo(countAfterFirst);
		}
	}

	// -----------------------------------------------------------------------
	// Project metadata
	// -----------------------------------------------------------------------

	@Test
	void write_projectJsonContainsProjectName() throws IOException {
		Path archive = tempDir.resolve("name.rbxproj");
		when(project.getProjectName()).thenReturn("MyAwesomeProject");

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String json = new String(Files.readAllBytes(zip.getPath(RbxprojFile.PROJECT_JSON_ENTRY)));
			assertThat(json).contains("MyAwesomeProject");
		}
	}

	@Test
	void write_projectJsonContainsPrintSettings() throws IOException {
		Path archive = tempDir.resolve("settings.rbxproj");
		when(settings.getPrintQuality()).thenReturn("FINE");
		when(settings.getBrimOverride()).thenReturn(4);
		when(settings.isPrintSupportOverride()).thenReturn(true);
		when(settings.isPrintRaft()).thenReturn(true);

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String json = new String(Files.readAllBytes(zip.getPath(RbxprojFile.PROJECT_JSON_ENTRY)));
			assertThat(json).contains("FINE");
			assertThat(json).contains("\"brimOverride\" : 4");
			assertThat(json).contains("\"printSupportOverride\" : true");
			assertThat(json).contains("\"printRaft\" : true");
		}
	}

	// -----------------------------------------------------------------------
	// Reader — project metadata
	// -----------------------------------------------------------------------

	@Test
	void read_returnsProjectFromFactory() throws IOException {
		Path archive = archiveWithNoModels();

		IProject result = reader.read(archive);

		assertThat(result).isSameAs(loadedProject);
	}

	@Test
	void read_setsProjectPath() throws IOException {
		Path archive = archiveWithNoModels();

		reader.read(archive);

		verify(loadedProject).setProjectPath(archive);
	}

	@Test
	void read_setsProjectName() throws IOException {
		Path archive = archiveWithNoModels();

		reader.read(archive);

		verify(loadedProject).setProjectName("TestProject");
	}

	@Test
	void read_returnsNullForMissingFile() {
		IProject result = reader.read(tempDir.resolve("nonexistent.rbxproj"));
		assertThat(result).isNull();
	}

	// -----------------------------------------------------------------------
	// Model storage and retrieval
	// -----------------------------------------------------------------------

	@Test
	void write_copiesModelFileIntoArchive() throws IOException {
		Path archive = tempDir.resolve("model.rbxproj");
		setupSingleModel();

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			long stlCount = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
			assertThat(stlCount).isEqualTo(1);
		}
	}

	@Test
	void write_placementsJsonReferencesStoredModel() throws IOException {
		Path archive = tempDir.resolve("placements.rbxproj");
		setupSingleModel();

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String placements = new String(Files.readAllBytes(zip.getPath(RbxprojFile.PLACEMENTS_JSON_ENTRY)));
			assertThat(placements).contains("models/");
			assertThat(placements).contains("part.stl");
		}
	}

	@Test
	void read_callsLoaderWithExtractedFile() throws IOException {
		Path archive = archiveWithOneModel("part.stl");
		IProjectModel returnedModel = mock(IProjectModel.class);
		when(stlLoader.load(any(Path.class))).thenReturn(returnedModel);
		when(returnedModel.getModelName()).thenReturn("part.stl");

		reader.read(archive);

		verify(stlLoader).load(any(Path.class));
	}

	@Test
	void read_appliesTransformToLoadedModel() throws IOException {
		Path archive = archiveWithOneModel("part.stl");
		IProjectModel returnedModel = mock(IProjectModel.class);
		when(stlLoader.load(any(Path.class))).thenReturn(returnedModel);
		when(returnedModel.getModelName()).thenReturn("part");

		reader.read(archive);

		verify(returnedModel).applyTransform(any(ModelTransformData.class));
	}

	@Test
	void read_setsExtruderOnLoadedModel() throws IOException {
		Path archive = archiveWithOneModel("part.stl");
		IProjectModel returnedModel = mock(IProjectModel.class);
		when(stlLoader.load(any(Path.class))).thenReturn(returnedModel);
		when(returnedModel.getModelName()).thenReturn("part");

		reader.read(archive);

		verify(returnedModel).setExtruder(0);
	}

	@Test
	void read_addsLoadedModelToProject() throws IOException {
		Path archive = archiveWithOneModel("part.stl");
		IProjectModel returnedModel = mock(IProjectModel.class);
		when(stlLoader.load(any(Path.class))).thenReturn(returnedModel);
		when(returnedModel.getModelName()).thenReturn("part");

		reader.read(archive);

		verify(loadedProject).addModel(returnedModel);
	}

	@Test
	void read_skipsModelWhenNoLoaderForExtension() throws IOException {
		Path archive = archiveWithOneModel("part.stl");
		when(stlLoader.supports("stl")).thenReturn(false);

		reader.read(archive);

		verify(loadedProject, never()).addModel(any());
	}

	// -----------------------------------------------------------------------
	// Deduplication
	// -----------------------------------------------------------------------

	@Test
	void write_deduplicatesIdenticalModelFiles() throws IOException {
		Path archive = tempDir.resolve("dedup.rbxproj");
		Path sharedFile = createStlFile("shared.stl");
		IProjectModel model1 = modelMock(1, sharedFile);
		IProjectModel model2 = modelMock(2, sharedFile);
		when(project.getTopLevelModels()).thenReturn(List.of(model1, model2));

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			long stlCount = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
			assertThat(stlCount).isEqualTo(1);
		}
	}

	@Test
	void write_storesBothFilesWhenContentDiffers() throws IOException {
		Path archive = tempDir.resolve("distinct.rbxproj");
		Path fileA = writeFile("a.stl", "solid a\nendsolid a");
		Path fileB = writeFile("b.stl", "solid b\nendsolid b");
		IProjectModel model1 = modelMock(1, fileA);
		IProjectModel model2 = modelMock(2, fileB);
		when(project.getTopLevelModels()).thenReturn(List.of(model1, model2));

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			long stlCount = Files.walk(zip.getPath("models"))
					.filter(p -> p.toString().endsWith(".stl"))
					.count();
			assertThat(stlCount).isEqualTo(2);
		}
	}

	// -----------------------------------------------------------------------
	// Manifest
	// -----------------------------------------------------------------------

	@Test
	void write_manifestContainsOriginalFilenameAndHash() throws IOException {
		Path archive = tempDir.resolve("manifest.rbxproj");
		setupSingleModel();

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String manifest = new String(Files.readAllBytes(zip.getPath(RbxprojFile.MODELS_MANIFEST_ENTRY)));
			assertThat(manifest).contains("part.stl");
			assertThat(manifest).contains("contentHash");
			// SHA-256 hex = 64 chars
			assertThat(manifest).matches("(?s).*\"contentHash\"\\s*:\\s*\"[0-9a-f]{64}\".*");
		}
	}

	// -----------------------------------------------------------------------
	// GCode write
	// -----------------------------------------------------------------------

	@Test
	void updateGcode_addsGcodeEntryToArchive() throws IOException {
		Path archive = archiveWithNoModels();
		Path gcodeFile = writeFile("out.gcode", "; layer 1\nG1 X10");
		writer.updateGcode(archive, "RBX01", gcodeFile, gcodeSettings("RBX01"));

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			assertThat(Files.exists(zip.getPath(RbxprojFile.gcodeEntry("RBX01")))).isTrue();
		}
	}

	@Test
	void updateGcode_writesSettingsJson() throws IOException {
		Path archive = archiveWithNoModels();
		writer.updateGcode(archive, "RBX01", writeFile("out.gcode", "; g"), gcodeSettings("RBX01"));

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			assertThat(Files.exists(zip.getPath(RbxprojFile.gcodeSettingsEntry("RBX01")))).isTrue();
			String json = new String(Files.readAllBytes(zip.getPath(RbxprojFile.gcodeSettingsEntry("RBX01"))));
			assertThat(json).contains("RBX01").contains("SINGLE_MATERIAL_HEAD");
		}
	}

	@Test
	void updateGcode_replacesExistingGcode() throws IOException {
		Path archive = archiveWithNoModels();
		writer.updateGcode(archive, "RBX01", writeFile("first.gcode", "; first"), gcodeSettings("RBX01"));
		writer.updateGcode(archive, "RBX01", writeFile("second.gcode", "; second"), gcodeSettings("RBX01"));

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String content = new String(Files.readAllBytes(zip.getPath(RbxprojFile.gcodeEntry("RBX01"))));
			assertThat(content).isEqualTo("; second");
		}
	}

	@Test
	void updateGcode_multiplePrinterTypesCoexist() throws IOException {
		Path archive = archiveWithNoModels();
		writer.updateGcode(archive, "RBX01", writeFile("a.gcode", "; rbx01"), gcodeSettings("RBX01"));
		writer.updateGcode(archive, "RBX02", writeFile("b.gcode", "; rbx02"), gcodeSettings("RBX02"));

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			assertThat(Files.exists(zip.getPath(RbxprojFile.gcodeEntry("RBX01")))).isTrue();
			assertThat(Files.exists(zip.getPath(RbxprojFile.gcodeEntry("RBX02")))).isTrue();
		}
	}

	// -----------------------------------------------------------------------
	// Reader — GCode
	// -----------------------------------------------------------------------

	@Test
	void readGcode_extractsCorrectContent() throws IOException {
		Path archive = archiveWithNoModels();
		writer.updateGcode(archive, "RBX01", writeFile("out.gcode", "; layer 1"), gcodeSettings("RBX01"));

		Optional<Path> result = reader.readGcode(archive, "RBX01");

		assertThat(result).isPresent();
		assertThat(Files.readString(result.get())).isEqualTo("; layer 1");
	}

	@Test
	void readGcode_returnsEmptyForMissingPrinterType() throws IOException {
		Path archive = archiveWithNoModels();

		assertThat(reader.readGcode(archive, "RBX10")).isEmpty();
	}

	@Test
	void readGcodeSettings_returnsStoredSnapshot() throws IOException {
		Path archive = archiveWithNoModels();
		PrintSettingsData ps = new PrintSettingsData(null, null, null, "FINE", 3, 0.4f, false, false, null, false, false);
		writer.updateGcode(archive, "RBX01", writeFile("out.gcode", "; g"),
				new GcodeSettingsData("RBX01", "DUAL_MATERIAL_HEAD", ps));

		Optional<GcodeSettingsData> result = reader.readGcodeSettings(archive, "RBX01");

		assertThat(result).isPresent();
		assertThat(result.get().printerTypeCode).isEqualTo("RBX01");
		assertThat(result.get().headType).isEqualTo("DUAL_MATERIAL_HEAD");
		assertThat(result.get().printSettings.printQuality).isEqualTo("FINE");
		assertThat(result.get().printSettings.brimOverride).isEqualTo(3);
		assertThat(result.get().printSettings.fillDensityOverride).isEqualTo(0.4f);
	}

	@Test
	void readGcodeSettings_returnsEmptyForMissingPrinterType() throws IOException {
		Path archive = archiveWithNoModels();

		assertThat(reader.readGcodeSettings(archive, "RBX99")).isEmpty();
	}

	@Test
	void listGcodeTargets_returnsAllPrinterTypeCodes() throws IOException {
		Path archive = archiveWithNoModels();
		writer.updateGcode(archive, "RBX01", writeFile("a.gcode", "; a"), gcodeSettings("RBX01"));
		writer.updateGcode(archive, "RBX02", writeFile("b.gcode", "; b"), gcodeSettings("RBX02"));

		assertThat(reader.listGcodeTargets(archive)).containsExactlyInAnyOrder("RBX01", "RBX02");
	}

	@Test
	void listGcodeTargets_returnsEmptyWhenNoGcodeStored() throws IOException {
		Path archive = archiveWithNoModels();

		assertThat(reader.listGcodeTargets(archive)).isEmpty();
	}

	// -----------------------------------------------------------------------
	// Groups
	// -----------------------------------------------------------------------

	@Test
	void write_preservesGroupStructureInPlacements() throws IOException {
		Path archive = tempDir.resolve("groups.rbxproj");
		ModelTransformData groupTransform = new ModelTransformData(5, 0, 5, 1, 1, 1, 0, 0, 0);
		when(project.getTopLevelModels()).thenReturn(List.of());
		when(project.getGroupStructure()).thenReturn(Map.of(10, Set.of(1, 2)));
		when(project.getGroupTransforms()).thenReturn(Map.of(10, groupTransform));

		writer.write(project, archive);

		try (FileSystem zip = FileSystems.newFileSystem(archive)) {
			String placements = new String(Files.readAllBytes(zip.getPath(RbxprojFile.PLACEMENTS_JSON_ENTRY)));
			assertThat(placements).contains("\"10\"");
		}
	}

	@Test
	void read_recreatesGroupsWhenPresent() throws Exception {
		Path archive = tempDir.resolve("groups.rbxproj");
		ModelTransformData groupTransform = new ModelTransformData(0, 0, 0, 1, 1, 1, 0, 0, 0);
		when(project.getTopLevelModels()).thenReturn(List.of());
		when(project.getGroupStructure()).thenReturn(Map.of(10, Set.of(1, 2)));
		when(project.getGroupTransforms()).thenReturn(Map.of(10, groupTransform));
		writer.write(project, archive);

		reader.read(archive);

		verify(loadedProject).recreateGroups(any(), any());
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private void setupSingleModel() throws IOException {
		Path stlPath = createStlFile("part.stl");
		when(model.getModelId()).thenReturn(1);
		when(model.getSourcePath()).thenReturn(stlPath);
		when(model.getLeafModels()).thenReturn(Set.of(model));
		when(model.getModelName()).thenReturn("part");
		when(model.getExtruder()).thenReturn(0);
		when(model.getTransform()).thenReturn(new ModelTransformData(0, 0, 0, 1, 1, 1, 0, 0, 0));
		when(project.getTopLevelModels()).thenReturn(List.of(model));
	}

	private IProjectModel modelMock(int id, Path sourcePath) {
		IProjectModel m = mock(IProjectModel.class);
		when(m.getModelId()).thenReturn(id);
		when(m.getSourcePath()).thenReturn(sourcePath);
		when(m.getLeafModels()).thenReturn(Set.of(m));
		when(m.getModelName()).thenReturn(sourcePath.getFileName().toString());
		when(m.getExtruder()).thenReturn(0);
		when(m.getTransform()).thenReturn(new ModelTransformData(0, 0, 0, 1, 1, 1, 0, 0, 0));
		return m;
	}

	private Path archiveWithNoModels() throws IOException {
		Path archive = tempDir.resolve(System.nanoTime() + ".rbxproj");
		writer.write(project, archive);
		return archive;
	}

	private Path archiveWithOneModel(String filename) throws IOException {
		Path stlPath = createStlFile(filename);
		IProjectModel m = modelMock(1, stlPath);
		when(project.getTopLevelModels()).thenReturn(List.of(m));
		Path archive = tempDir.resolve(System.nanoTime() + ".rbxproj");
		writer.write(project, archive);
		when(project.getTopLevelModels()).thenReturn(List.of()); // reset
		return archive;
	}

	private Path createStlFile(String name) throws IOException {
		return writeFile(name, "solid " + name + "\nendsolid " + name);
	}

	private Path writeFile(String name, String content) throws IOException {
		Path file = tempDir.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private GcodeSettingsData gcodeSettings(String printerTypeCode) {
		PrintSettingsData ps = new PrintSettingsData(null, null, null, "DRAFT", 0, 0f, false, false, null, false, false);
		return new GcodeSettingsData(printerTypeCode, "SINGLE_MATERIAL_HEAD", ps);
	}

	private void stubSettings(IProjectSettings s) {
		when(s.getExtruder0FilamentID()).thenReturn("NULL");
		when(s.getExtruder1FilamentID()).thenReturn("NULL");
		when(s.getSettingsName()).thenReturn("Draft");
		when(s.getPrintQuality()).thenReturn("DRAFT");
		when(s.getBrimOverride()).thenReturn(0);
		when(s.getFillDensityOverride()).thenReturn(0.15f);
		when(s.isFillDensityOverridenByUser()).thenReturn(false);
		when(s.isPrintSupportOverride()).thenReturn(false);
		when(s.getPrintSupportTypeOverride()).thenReturn("AS_PROFILE");
		when(s.isPrintRaft()).thenReturn(false);
		when(s.isSpiralPrint()).thenReturn(false);
	}
}
