package celtech.appManager;

import static org.openautomaker.project.DeDuplicator.deduplicate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.project.OpenProjectsPreference;
import org.openautomaker.project.api.IProject;
import org.openautomaker.project.rbxproj.RbxprojFile;
import org.openautomaker.project.rbxproj.RbxprojReader;
import org.openautomaker.project.rbxproj.RbxprojWriter;
import org.openautomaker.ui.project.adapter.ProjectBridge;
import org.openautomaker.ui.project.robox.RoboxFile;
import org.openautomaker.ui.project.robox.RoboxReader;
import org.openautomaker.ui.project.robox.RoboxWriter;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProjectManager {

	private static final Logger LOGGER = LogManager.getLogger();

	private static List<Project> openProjects = new ArrayList<>();

	private final ProjectsPathPreference projectsPathPreference;
	private final OpenProjectsPreference openProjectsPreference;
	private final RoboxReader roboxReader;
	private final RoboxWriter roboxWriter;
	private final RbxprojWriter rbxProjWriter;
	private final RbxprojReader rbxprojReader;

	@Inject
	protected ProjectManager(
			ProjectsPathPreference projectsPathPreference,
			OpenProjectsPreference openProjectsPreference,
			RoboxReader roboxReader,
			RoboxWriter roboxWriter,
			RbxprojWriter rbxProjWriter,
			RbxprojReader rbxprojReader) {

		this.projectsPathPreference = projectsPathPreference;
		this.openProjectsPreference = openProjectsPreference;
		this.roboxReader = roboxReader;
		this.roboxWriter = roboxWriter;
		this.rbxProjWriter = rbxProjWriter;
		this.rbxprojReader = rbxprojReader;

		loadOpenProjects();
	}

	private void loadOpenProjects() {
		for (Path projectPath : openProjectsPreference.getPaths()) {
			LOGGER.debug("Loading project from path: {}", projectPath);
			Project project = loadProject(projectPath);
			if (project != null) {
				projectOpened(project);
			}
		}
	}

	/**
	 * Saves the list of open projects to the OpenProjectsPreference.
	 */
	public void rememberOpenProjects() {
		for (Project project : openProjects) {
			Path filePath = project.getProjectFilePath();
			if (filePath != null && !openProjectsPreference.contains(filePath)) {
				openProjectsPreference.add(filePath);
			}
		}
	}

	public void projectOpened(Project project) {
		if (!openProjects.contains(project)) {
			openProjects.add(project);

			Path filePath = project.getProjectFilePath();
			if (filePath != null && !openProjectsPreference.contains(filePath)) {
				openProjectsPreference.add(filePath);
			}
		}
	}

	public void projectClosed(Project project) {
		project.close();
		openProjectsPreference.remove(project.getProjectFilePath());
	}

	public List<Project> getOpenProjects() {
		return openProjects;
	}

	public Optional<Project> getProjectIfOpen(String projectName) {
		return openProjects.stream()
				.filter(p -> p.getProjectName().equals(projectName))
				.findAny();
	}

	public Set<String> getOpenAndAvailableProjectNames() {
		Set<String> names = new HashSet<>();
		for (Project project : openProjects) {
			names.add(project.getProjectName());
		}
		names.addAll(getAvailableProjectNames());
		return names;
	}

	private Set<String> getAvailableProjectNames() {
		Set<String> names = new HashSet<>();
		File projectDir = projectsPathPreference.getValue().toFile();
		File[] projectFiles = projectDir.listFiles((dir, name) -> name.endsWith(RoboxFile.EXTENSION));
		if (projectFiles != null) {
			for (File file : projectFiles) {
				names.add(file.getName().replace(RoboxFile.EXTENSION, ""));
			}
		}
		return names;
	}

	/**
	 * Saves the given project.  If the project doesn't have a file path, it will be saved to the default projects directory with a generated name.  If the project was loaded from a .robox file, it will be saved as a .robox file to preserve all data.  If the project was loaded from a .rbxproj file, it will be saved as a .rbxproj file.
	 * 
	 * @param project - the project to save.  Must not be null.
	 */
	public final void saveProject(Project project) {
		// If the project is null we can't save it.
		if (project == null)
			return;

		Path projectFilePath = project.getProjectFilePath();
		//For the moment enumerate the name of the project file if there isn't one assigned.  In future this will be handled by a 'Save As' dialog.
		if (projectFilePath == null) {
			// Need to deduplicate this if it's a new project.
			Path newFilePath = deduplicate(projectsPathPreference.getValue().resolve(project.getProjectName() + RbxprojFile.EXTENSION));
			project.setProjectFilePath(newFilePath);
		}

		// If it was loaded as legacy, save as legacy.  No need to check model sources.
		if (project.getProjectFilePath() != null && project.getProjectFilePath().toString().endsWith(RoboxFile.EXTENSION)) {
			// If it's already a .robox file, save as .robox.  We can assume we already have everything.
			saveAsRoboxFile(project);
			return;
		}

		// Default to a pbxproj file.  Should only get here if it's a new project or it was loaded from a .rbxproj file.
		saveAsRbxprojFile(project);
	}

	/**
	 * Saves all open projects.
	 */
	public void saveAllProjects() {
		for (Project project : openProjects) {
			saveProject(project);
		}
	}

	public final Project loadProject(Path filePath) {
		if (filePath.toString().endsWith(RbxprojFile.EXTENSION)) {
			return loadRbxprojFile(filePath);
		}
		return loadRoboxFile(filePath);
	}

	private Project loadRbxprojFile(Path filePath) {
		IProject loaded = rbxprojReader.read(filePath);
		if (loaded instanceof ProjectBridge bridge) {
			Project project = bridge.getProject();
			project.setProjectFilePath(filePath);
			project.setProjectSaved(true);
			return project;
		}
		LOGGER.error("Unexpected IProject type returned from RbxProjReader for {}", filePath);
		return null;
	}

	private Project loadRoboxFile(Path filePath) {
		return roboxReader.read(filePath);
	}

	// Will be used for minration of legacy projects via 'Save As rbxproj' in future.  Don't delete
	private boolean allSourceFilesAccessible(Project project) {
		for (var thing : project.getAllModels()) {
			if (thing instanceof celtech.modelcontrol.ModelContainer mc) {
				Path src = mc.getModelFile();
				if (src == null || !Files.exists(src)) {
					LOGGER.warn("Source file inaccessible for model '{}' — skipping .rbxproj save", mc.getModelName());
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Saves the project as a .rbxproj file.
	 * 
	 * @param project - the project to save.  Must not be null.
	 */
	private void saveAsRbxprojFile(Project project) {
		Path targetPath = project.getProjectFilePath();

		try {
			rbxProjWriter.write(new ProjectBridge(project), targetPath);
			project.setProjectSaved(true);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to save project as .rbxproj: {}", targetPath, ex);
			//This should also fire a notification to the UI to let the user know the save failed, but that isn't implemented yet.
		}
	}

	/**
	 * Saves the project as a legacy .robox file.  This is used when saving projects that were loaded from .robox files and the original models aren't available, to preserve all data.
	 * 
	 * @param project - the project to save.  Must not be null.
	 */
	private void saveAsRoboxFile(Project project) {
		Path targetPath = project.getProjectFilePath();
		try {
			roboxWriter.write(project, targetPath);
		}
		catch (IOException e) {
			LOGGER.error("Failed to save project as .robox: {}", targetPath, e);
			// This should also fire a notification to the UI to let the user know the save failed, but that isn't implemented yet.
		}
		project.setProjectSaved(true);
	}
}
