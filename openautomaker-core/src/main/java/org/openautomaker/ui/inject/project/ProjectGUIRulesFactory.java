package org.openautomaker.ui.inject.project;

import org.openautomaker.ui.ProjectGUIRules;
import celtech.coreUI.visualisation.ProjectSelection;
import celtech.modelcontrol.ModelContainer;
import javafx.collections.ObservableSet;

public interface ProjectGUIRulesFactory {

	public ProjectGUIRules create(
			ProjectSelection projectSelection,
			ObservableSet<ModelContainer> excludedFromSelection);

}
