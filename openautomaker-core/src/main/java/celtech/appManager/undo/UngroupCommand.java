/*
 * Copyright 2015 CEL UK
 */
package celtech.appManager.undo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import celtech.appManager.Project;
import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;

/**
 *
 * @author tony
 */
public class UngroupCommand implements Command {

	private static final Logger LOGGER = LogManager.getLogger();

	Project project;
	Map<Integer, Set<Groupable>> groupIds;
	private Set<ItemState> originalStates;
	private Set<ItemState> newStates;
	private Set<ModelContainer> containersToRecentre = new HashSet<>();

	public UngroupCommand(Project project, Set<ModelContainer> modelContainers) {
		this.project = project;
		groupIds = new HashMap<>();
		for (ModelContainer modelContainer : modelContainers) {
			if (modelContainer instanceof ModelGroup) {
				containersToRecentre.addAll(modelContainer.getChildModelContainers());
				groupIds.put(modelContainer.getModelId(), (Set) ((ModelGroup) modelContainer).getChildModelContainers());
			}
		}
	}

	@Override
	public void do_() {
		redo();
	}

	@Override
	public void undo() {
		for (int groupId : groupIds.keySet()) {
			project.group(groupIds.get(groupId), groupId);
		}
		project.setModelStates(originalStates);
	}

	@Override
	public void redo() {
		originalStates = project.getModelStates();
		try {
			try {
				project.ungroup(project.getModelContainersOfIds(groupIds.keySet()));
			}
			catch (Project.ProjectLoadException ex) {
				LOGGER.error("Could not ungroup", ex);
			}
			newStates = project.getModelStates();
		}
		catch (Exception ex) {
			LOGGER.error("Failed running command ", ex);
		}
	}

	@Override
	public boolean canMergeWith(Command command) {
		return false;
	}

	@Override
	public void merge(Command command) {
		throw new UnsupportedOperationException("Should never be called");
	}

}
