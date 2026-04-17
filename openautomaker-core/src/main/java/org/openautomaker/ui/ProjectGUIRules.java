/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui;

import com.google.inject.assistedinject.Assisted;

import celtech.coreUI.visualisation.ProjectSelection;
import celtech.modelcontrol.ModelContainer;
import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableSet;

/**
 * ProjectGUIRules indicates eg if the project selection can be translated, removed, scaled etc.
 */
public class ProjectGUIRules {

	private final ObservableSet<ModelContainer> excludedFromSelection;

	private final ProjectSelection projectSelection;

	@Inject
	public ProjectGUIRules(
			@Assisted ProjectSelection projectSelection,
			@Assisted ObservableSet<ModelContainer> excludedFromSelection) {

		this.projectSelection = projectSelection;
		this.excludedFromSelection = excludedFromSelection;
	}

	public BooleanBinding canSnapToGroundSelection() {
		return projectSelection.getSelectionHasChildOfGroup().not();
	}

	public BooleanBinding canRemoveOrDuplicateSelection() {
		return Bindings.isEmpty(excludedFromSelection);
	}

	public BooleanBinding canAddModel() {
		return Bindings.isEmpty(excludedFromSelection);
	}

	public BooleanBinding canCutModel() {
		return projectSelection.getNumModelsSelectedProperty().isEqualTo(1).and(Bindings.isEmpty(excludedFromSelection));
	}

	public BooleanBinding canGroupSelection() {
		return projectSelection.getNumModelsSelectedProperty().greaterThan(1).and(Bindings.isEmpty(excludedFromSelection));
	}

	public BooleanBinding canUngroupSelection() {
		return projectSelection.getNumGroupsSelectedProperty().isEqualTo(1).and(Bindings.isEmpty(excludedFromSelection));
	}
}
