package org.openautomaker.ui.inject.project;

import celtech.appManager.Project;
import org.openautomaker.ui.ProjectGUIState;

public interface ProjectGUIStateFactory {

	public ProjectGUIState create(Project project);

}
