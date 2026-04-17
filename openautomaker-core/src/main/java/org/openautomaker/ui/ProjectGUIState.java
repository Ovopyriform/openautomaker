/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui;

import org.openautomaker.ui.inject.project.ProjectGUIRulesFactory;
import org.openautomaker.ui.inject.project.ProjectSelectionFactory;

import com.google.inject.assistedinject.Assisted;

import celtech.appManager.Project;
import celtech.coreUI.LayoutSubmode;
import celtech.appManager.undo.CommandStack;
import celtech.coreUI.visualisation.ProjectSelection;
import celtech.modelcontrol.ModelContainer;
import jakarta.inject.Inject;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

/**
 * The ProjectGUIState class contains GUI information for a project such as the selected models. It is put here to keep the Project class clean of GUI data.
 */
public class ProjectGUIState {
	private final ProjectSelection projectSelection;

	private final ObjectProperty<LayoutSubmode> layoutSubmode;

	private final CommandStack commandStack;

	private final ObservableSet<ModelContainer> excludedFromSelection = FXCollections.observableSet();

	private final ProjectGUIRules projectGUIRules;

	@Inject
	protected ProjectGUIState(
			ProjectSelectionFactory projectSelectionFactory,
			ProjectGUIRulesFactory projectGUIRulesFactory,
			@Assisted Project project) {

		projectSelection = projectSelectionFactory.create(project);

		layoutSubmode = new SimpleObjectProperty<>(LayoutSubmode.SELECT);
		commandStack = new CommandStack();
		projectGUIRules = projectGUIRulesFactory.create(projectSelection, excludedFromSelection);
	}

	public CommandStack getCommandStack() {
		return commandStack;
	}

	public ProjectGUIRules getProjectGUIRules() {
		return projectGUIRules;
	}

	public ObservableSet<ModelContainer> getExcludedFromSelection() {
		return excludedFromSelection;
	}

	public ProjectSelection getProjectSelection() {
		return projectSelection;
	}

	public ObjectProperty<LayoutSubmode> getLayoutSubmodeProperty() {
		return layoutSubmode;
	}
}
