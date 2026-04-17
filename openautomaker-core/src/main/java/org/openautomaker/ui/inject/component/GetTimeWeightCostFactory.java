package org.openautomaker.ui.inject.component;

import org.openautomaker.base.task_executor.Cancellable;

import com.google.inject.assistedinject.Assisted;

import celtech.appManager.ModelContainerProject;
import org.openautomaker.ui.component.time_cost_inset_panel.GetTimeWeightCost;
import javafx.scene.control.Label;

public interface GetTimeWeightCostFactory {

	public GetTimeWeightCost create(
			@Assisted("project") ModelContainerProject project,
			@Assisted("lblTime") Label lblTime,
			@Assisted("lblWeight") Label lblWeight,
			@Assisted("lblCost") Label lblCost,
			@Assisted("cancellable") Cancellable cancellable);

}
