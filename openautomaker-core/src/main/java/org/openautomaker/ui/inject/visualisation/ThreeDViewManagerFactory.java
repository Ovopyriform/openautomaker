package org.openautomaker.ui.inject.visualisation;

import com.google.inject.assistedinject.Assisted;

import celtech.appManager.Project;
import celtech.coreUI.visualisation.ThreeDViewManager;
import javafx.beans.property.ReadOnlyDoubleProperty;

public interface ThreeDViewManagerFactory {

	public ThreeDViewManager create(
			Project project,
			@Assisted("widthProperty") ReadOnlyDoubleProperty widthProperty,
			@Assisted("heightProperty") ReadOnlyDoubleProperty heightProperty);
}
