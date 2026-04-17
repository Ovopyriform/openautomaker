package celtech.appManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.environment.preference.project.OpenProjectsPreference;
import org.openautomaker.ui.state.ProjectGUIStates;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProjectManager implements Serializable {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final long serialVersionUID = 4714858633610290041L;

	private static List<Project> openProjects = new ArrayList<>();

	private final ProjectsPathPreference projectsPathPreference;
	private final ProjectPersistance projectPersistance;
	private final ProjectGUIStates projectGUIStates;

	private final OpenProjectsPreference openProjectsPreference;

	@Inject
	protected ProjectManager(
			ProjectsPathPreference projectsPathPreference,
			OpenProjectsPreference openProjectsPreference,
			ProjectPersistance projectPersistance,
			ProjectGUIStates projectGUIStates) {

		this.projectsPathPreference = projectsPathPreference;
		this.openProjectsPreference = openProjectsPreference;
		this.projectPersistance = projectPersistance;
		this.projectGUIStates = projectGUIStates;

		loadOpenProjects();
	}

	// The open projects preference is used to store the paths of the open projects, so we need to load those projects on startup.
	private void loadOpenProjects() {
		for (Path projectPath : openProjectsPreference.getPaths()) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Loading project from path: " + projectPath);
			}

			Project project = loadProject(projectPath);
			if (project != null) {
				projectOpened(project);
			}
		}
	}

	/**
	 * This method should be called to save the paths of the open projects to the open projects preference. This should be called before the application exits to ensure that the open projects are remembered for next time.
	 */
	public void rememberOpenProjects() {
		for (Project project : openProjects) {
			if (project.getNumberOfProjectifiableElements() > 0 && !openProjectsPreference.getPaths().contains(project.getAbsolutePath())) {
				openProjectsPreference.add(project.getAbsolutePath());
			}
		}
	}

	public Project loadProject(Path projectPath) {
		return projectPersistance.loadProject(projectPath);
	}

	//TODO: These are odd.  Seems like the project manager isn't actuallly in charge of opening and closing projects.  It should be.
	public void projectOpened(Project project) {
		if (!openProjects.contains(project)) {
			openProjects.add(project);
			rememberOpenProjects();
		}
	}

	public void projectClosed(Project project) {
		project.close();
		if (openProjects.remove(project))
			rememberOpenProjects();
		
		// This simply removes this projects project GUI state
		//TODO:  This shouldn't be here.  GUI State should be handled outside of the project manager.
		projectGUIStates.remove(project);
	}

	public List<Project> getOpenProjects() {
		return openProjects;
	}

	private Set<String> getAvailableProjectNames() {
		Set<String> availableProjectNames = new HashSet<>();

		File projectDir = projectsPathPreference.getValue().toFile();

		File[] projectFiles = projectDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(ApplicationConfiguration.projectFileExtension);
			}
		});

		for (File file : projectFiles) {
			String fileName = file.getName();
			String projectName = fileName.replace(ApplicationConfiguration.projectFileExtension, "");
			availableProjectNames.add(projectName);
		}
		return availableProjectNames;
	}

	//TODO: This is kind of a sucky way to store projects.  You should be able to store a project anywhere.
	public Set<String> getOpenAndAvailableProjectNames() {
		Set<String> openAndAvailableProjectNames = new HashSet<>();
		for (Project project : openProjects) {
			openAndAvailableProjectNames.add(project.getProjectName());
		}
		openAndAvailableProjectNames.addAll(getAvailableProjectNames());
		return openAndAvailableProjectNames;
	}

	public Optional<Project> getProjectIfOpen(String projectName) {
		return openProjects.stream().filter((p) -> {
			return p.getProjectName().equals(projectName);
		}).findAny();
	}
}
