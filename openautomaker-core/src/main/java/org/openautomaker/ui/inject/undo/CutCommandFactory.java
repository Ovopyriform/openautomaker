package org.openautomaker.ui.inject.undo;

import java.util.Set;

import celtech.appManager.Project;
import celtech.appManager.undo.CutCommand;
import celtech.modelcontrol.ModelContainer;

public interface CutCommandFactory {

	public CutCommand create(
			Project project,
			Set<ModelContainer> modelContainers,
			float cutHeightValue);

}
