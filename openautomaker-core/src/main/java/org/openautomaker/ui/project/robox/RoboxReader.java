package org.openautomaker.ui.project.robox;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.CameraProfileContainer;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.device.CameraManager;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.inject.project.ProjectFactory;
import org.openautomaker.ui.project.robox.data.ProjectFile;
import org.openautomaker.ui.project.robox.data.ProjectFileDeserialiser;
import org.openautomaker.ui.state.SelectedPrinter;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import celtech.appManager.Project;
import celtech.modelcontrol.ModelContainer;
import celtech.utils.threed.MeshUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.shape.TriangleMesh;

@Singleton
public class RoboxReader {

	private static final Logger LOGGER = LogManager.getLogger();

	private final ProjectFactory projectFactory;
	private final SelectedPrinter selectedPrinter;
	private final CameraManager cameraManager;
	private final CameraProfileContainer cameraProfileContainer;
	private final FilamentContainer filamentContainer;

	@Inject
	public RoboxReader(
			ProjectFactory projectFactory,
			SelectedPrinter selectedPrinter,
			CameraManager cameraManager,
			CameraProfileContainer cameraProfileContainer,
			FilamentContainer filamentContainer) {

		this.projectFactory = projectFactory;
		this.selectedPrinter = selectedPrinter;
		this.cameraManager = cameraManager;
		this.cameraProfileContainer = cameraProfileContainer;
		this.filamentContainer = filamentContainer;
	}

	/**
	 * Read a legacy .robox project from disk. Returns null if the file cannot be parsed.
	 */
	public Project read(Path filePath) {
		if (!filePath.toString().endsWith(RoboxFile.EXTENSION))
			filePath = filePath.resolveSibling(filePath.getFileName() + RoboxFile.EXTENSION);

		try {
			ProjectFileDeserialiser deserializer = new ProjectFileDeserialiser();
			SimpleModule module = new SimpleModule("LegacyProjectFileDeserialiserModule", new Version(1, 0, 0, null));
			module.addDeserializer(ProjectFile.class, deserializer);

			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(module);
			ProjectFile projectFile = mapper.readValue(filePath.toFile(), ProjectFile.class);

			if (projectFile != null) {
				Project project = projectFactory.create();
				project.initialiseExtruderFilaments(selectedPrinter.get());
				populateProject(project, projectFile, filePath);
				project.setProjectFilePath(filePath);
				return project;
			}
		}
		catch (Exception ex) {
			LOGGER.error("Unable to load project file at {}", filePath, ex);
		}
		return null;
	}

	private void populateProject(Project project, ProjectFile projectFile, Path filePath) {
		project.setSuppressProjectChanged(true);
		try {
			project.setVersion(projectFile.getVersion());
			project.setProjectName(projectFile.getProjectName());
			project.getLastModifiedDate().set(projectFile.getLastModifiedDate());
			project.setLastPrintJobID(projectFile.getLastPrintJobID());
			project.setProjectNameModified(projectFile.isProjectNameModified());

			String filamentID0 = projectFile.getExtruder0FilamentID();
			String filamentID1 = projectFile.getExtruder1FilamentID();
			if (filamentID0 != null && !filamentID0.equals("NULL")) {
				Filament f = filamentContainer.getFilamentByID(filamentID0);
				if (f != null) project.getExtruder0FilamentProperty().set(f);
			}
			if (filamentID1 != null && !filamentID1.equals("NULL")) {
				Filament f = filamentContainer.getFilamentByID(filamentID1);
				if (f != null) project.getExtruder1FilamentProperty().set(f);
			}

			project.getPrinterSettings().setSettingsName(projectFile.getSettingsName());
			project.getPrinterSettings().setPrintQuality(projectFile.getPrintQuality());
			project.getPrinterSettings().setBrimOverride(projectFile.getBrimOverride());
			project.getPrinterSettings().setFillDensityOverride(projectFile.getFillDensityOverride());
			project.getPrinterSettings().setFillDensityChangedByUser(projectFile.isFillDensityOverridenByUser());
			project.getPrinterSettings().setPrintSupportOverride(projectFile.getPrintSupportOverride());
			project.getPrinterSettings().setPrintSupportTypeOverride(projectFile.getPrintSupportTypeOverride());
			project.getPrinterSettings().setRaftOverride(projectFile.getPrintRaft());
			project.getPrinterSettings().setSpiralPrintOverride(projectFile.getSpiralPrint());

			loadTimelapseSettings(project, projectFile);
			loadModels(project, filePath);
			project.recreateGroups(projectFile.getGroupStructure(), projectFile.getGroupState());
		}
		catch (IOException | ClassNotFoundException | Project.ProjectLoadException ex) {
			LOGGER.error("Failed to load project {}", filePath, ex);
		}
		finally {
			project.setSuppressProjectChanged(false);
		}
	}

	private void loadModels(Project project, Path filePath) throws IOException, ClassNotFoundException {
		if (!filePath.toString().endsWith(RoboxFile.EXTENSION))
			filePath = filePath.resolve(filePath.getFileName().toString() + RoboxFile.EXTENSION);

		filePath = filePath.resolveSibling(
				filePath.getFileName().toString().replace(RoboxFile.EXTENSION, RoboxFile.MODELS_EXTENSION));

		try (FileInputStream fis = new FileInputStream(filePath.toFile());
				BufferedInputStream bis = new BufferedInputStream(fis);
				ObjectInputStream ois = new ObjectInputStream(bis)) {

			int numModels = ois.readInt();
			for (int i = 0; i < numModels; i++) {
				ModelContainer mc = (ModelContainer) ois.readObject();
				GuiceContext.get().injectMembers(mc);
				Optional<MeshUtils.MeshError> error = MeshUtils.validate((TriangleMesh) mc.getMeshView().getMesh());
				if (error.isPresent()) {
					mc.setIsInvalidMesh(true);
					LOGGER.debug("Model load - {}", error.get().name());
				}
				project.addModel(mc);
			}
		}
	}

	private void loadTimelapseSettings(Project project, ProjectFile pFile) {
		project.getTimelapseSettings().setTimelapseTriggerEnabled(pFile.isTimelapseTriggerEnabled());
		String profileName = pFile.getTimelapseProfileName();
		if (profileName.isBlank()) {
			project.getTimelapseSettings().setTimelapseProfile(Optional.empty());
		}
		else {
			project.getTimelapseSettings().setTimelapseProfile(
					Optional.ofNullable(cameraProfileContainer.getProfileByName(profileName)));
		}
		String cameraID = pFile.getTimelapseCameraID();
		Optional<CameraInfo> camera = Optional.empty();
		if (!cameraID.isBlank()) {
			String[] fields = cameraID.split(":");
			if (fields.length == 2) {
				try {
					int cameraNumber = Integer.parseInt(fields[1]);
					camera = cameraManager.getConnectedCameras().stream()
							.filter(c -> c.getCameraName().equals(fields[0]) && c.getCameraNumber() == cameraNumber)
							.findFirst();
				}
				catch (NumberFormatException ex) {
				}
			}
		}
		project.getTimelapseSettings().setTimelapseCamera(camera);
	}
}
