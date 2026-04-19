package celtech.appManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.project.RbxProjFile;
import org.openautomaker.project.RbxProjWriter;
import org.openautomaker.ui.inject.project.ModelContainerProjectFactory;
import org.openautomaker.ui.inject.project.ShapeContainerProjectFactory;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import celtech.appManager.project.ModelContainerProjectAdapter;
import celtech.configuration.ApplicationConfiguration;
import celtech.configuration.fileRepresentation.ModelContainerProjectFile;
import celtech.configuration.fileRepresentation.ProjectFile;
import celtech.configuration.fileRepresentation.ProjectFileDeserialiser;
import celtech.configuration.fileRepresentation.ShapeContainerProjectFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProjectPersistance {
	private Logger LOGGER = LogManager.getLogger();

	private final ProjectsPathPreference projectsPathPreference;
	private final ModelContainerProjectFactory modelContainerProjectFactory;
	private final ShapeContainerProjectFactory shapeContainerProjectFactory;
	private final RbxProjWriter rbxProjWriter;

	@Inject
	protected ProjectPersistance(
			ProjectsPathPreference projectsPathPreference,
			ModelContainerProjectFactory modelContainerProjectFactory,
			ShapeContainerProjectFactory shapeContainerProjectFactory,
			RbxProjWriter rbxProjWriter) {

		this.projectsPathPreference = projectsPathPreference;
		this.modelContainerProjectFactory = modelContainerProjectFactory;
		this.shapeContainerProjectFactory = shapeContainerProjectFactory;
		this.rbxProjWriter = rbxProjWriter;
	}

	public final void saveProject(Project project) {
		if (project == null)
			return;

		if (project instanceof ModelContainerProject mcp) {
			saveAsRbxProj(mcp);
			return;
		}

		Path basePath = projectsPathPreference.getValue().resolve(project.getProjectName());

		File dirHandle = basePath.toFile();
		if (!dirHandle.exists()) {
			dirHandle.mkdirs();
		}

		project.save(basePath);
		project.setProjectSaved(true);
	}

	private void saveAsRbxProj(ModelContainerProject project) {
		Path targetPath = projectsPathPreference.getValue()
				.resolve(project.getProjectName() + RbxProjFile.EXTENSION);
		try {
			rbxProjWriter.write(new ModelContainerProjectAdapter(project), targetPath);
			project.setProjectSaved(true);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to save project as .rbxproj: {}", targetPath, ex);
		}
	}

	public final Project loadProject(Path filePath) {
		Project project = null;

		//Legacy check to see if we've been given a path with an extension
		if (!filePath.toString().endsWith(ApplicationConfiguration.projectFileExtension))
			filePath = filePath.resolveSibling(filePath.getFileName() + ApplicationConfiguration.projectFileExtension);

		File file = filePath.toFile();

		try {
			ProjectFileDeserialiser deserializer = new ProjectFileDeserialiser();
			SimpleModule module = new SimpleModule("LegacyProjectFileDeserialiserModule", new Version(1, 0, 0, null));
			module.addDeserializer(ProjectFile.class, deserializer);

			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(module);
			ProjectFile projectFile = mapper.readValue(file, ProjectFile.class);

			if (projectFile instanceof ModelContainerProjectFile) {
				project = modelContainerProjectFactory.create();
				project.load(projectFile, filePath);
			}
			else if (projectFile instanceof ShapeContainerProjectFile) {
				project = shapeContainerProjectFactory.create();
				project.load(projectFile, filePath);
			}
		}
		catch (Exception ex) {
			//TODO: Issue loading the test data here which is ignored as it's caught and no failures.  Odd.  Look into the persistence of the project
			LOGGER.error("Unable to load project file at " + file.toString(), ex);
		}
		return project;
	}
}
