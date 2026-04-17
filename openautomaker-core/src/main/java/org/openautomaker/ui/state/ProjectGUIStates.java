package org.openautomaker.ui.state;

import java.util.HashMap;

import org.openautomaker.ui.inject.project.ProjectGUIStateFactory;

import celtech.appManager.Project;
import org.openautomaker.ui.ProjectGUIState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProjectGUIStates extends HashMap<Project, ProjectGUIState> {

	private static final long serialVersionUID = 8909454024892371529L;

	private final ProjectGUIStateFactory projectGUIStateFactory;

	@Inject
	protected ProjectGUIStates(
			ProjectGUIStateFactory projectGUIStateFactory) {

		super();

		this.projectGUIStateFactory = projectGUIStateFactory;
	}

	/**
	 * Gets the existing or create the ProjectGUIState object for the provided project.
	 * 
	 * @param project - The Project to create the GUI state for
	 * 
	 * @return the appropriate ProjectGUIState
	 */
	public ProjectGUIState get(Project project) {
		ProjectGUIState projectGUIState = super.get(project);

		if (projectGUIState != null)
			return projectGUIState;

		projectGUIState = projectGUIStateFactory.create(project);
		put(project, projectGUIState);

		return projectGUIState;
	}

}
