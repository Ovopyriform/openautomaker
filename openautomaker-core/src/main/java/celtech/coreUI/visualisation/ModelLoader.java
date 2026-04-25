/*
 * Copyright 2015 CEL UK
 */
package celtech.coreUI.visualisation;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.environment.I18N;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.utils.RectangularBounds;
import org.openautomaker.environment.preference.modeling.SplitLoosePartsOnLoadPreference;
import org.openautomaker.ui.inject.model.ModelGroupFactory;

import org.openautomaker.ui.inject.undo.UndoableProjectFactory;
import org.openautomaker.ui.state.SelectedPrinter;

import org.openautomaker.ui.inject.project.ProjectFactory;
import celtech.appManager.Project;
import celtech.appManager.ProjectCallback;
import celtech.appManager.ProjectMode;
import celtech.appManager.undo.UndoableProject;
import celtech.coreUI.visualisation.metaparts.ModelLoadResult;
import celtech.coreUI.visualisation.metaparts.ModelLoadResultType;
import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import celtech.services.modelLoader.ModelLoadResults;
import celtech.services.modelLoader.ModelLoaderService;
import celtech.utils.threed.MeshUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.shape.TriangleMesh;

/**
 * ModelLoader contains methods for loading models from a file.
 *
 * @author tony
 */
@Singleton
public class ModelLoader {
	//TODO: Look into model loading.  Perhaps a service?  Should this be a singleton?

	private static final Logger LOGGER = LogManager.getLogger();
	/*
	 * Mesh Model loading
	 */
	private final ModelLoaderService modelLoaderService;

	private final SystemNotificationManager systemNotificationManager;
	private final SelectedPrinter selectedPrinter;
	private final ProjectFactory projectFactory;
	private final UndoableProjectFactory undoableProjectFactory;
	private final ModelGroupFactory modelGroupFactory;
	private final SplitLoosePartsOnLoadPreference splitLoosePartsOnLoadPreference;
	private final I18N i18n;

	@Inject
	protected ModelLoader(
			SystemNotificationManager systemNotificationManager,
			ModelLoaderService modelLoaderService,
			SelectedPrinter selectedPrinter,
			ProjectFactory projectFactory,
			UndoableProjectFactory undoableProjectFactory,
			ModelGroupFactory modelGroupFactory,
			SplitLoosePartsOnLoadPreference splitLoosePartsOnLoadPreference,
			I18N i18n) {

		this.systemNotificationManager = systemNotificationManager;
		this.modelLoaderService = modelLoaderService;
		this.selectedPrinter = selectedPrinter;
		this.projectFactory = projectFactory;
		this.undoableProjectFactory = undoableProjectFactory;
		this.modelGroupFactory = modelGroupFactory;
		this.splitLoosePartsOnLoadPreference = splitLoosePartsOnLoadPreference;
		this.i18n = i18n;
	}

	public ModelLoaderService getModelLoaderService() {
		return modelLoaderService;
	}

	private void offerShrinkAndAddToProject(Project project, boolean relayout, ProjectCallback callMeBack,
			boolean dontGroupModelsOverride,
			Printer printer) {
		ModelLoadResults loadResults = modelLoaderService.getValue();
		if (loadResults.getResults().isEmpty()) {
			systemNotificationManager.showErrorNotification(
					i18n.t("dialogs.loadModelFailedTitle"),
					i18n.t("dialogs.loadModelFailedMessage"));
			return;
		}

		if (loadResults.getType() == ModelLoadResultType.Mesh) {
			project.setMode(ProjectMode.MESH);
			// validate incoming meshes
			// Associate the loaded meshes with extruders in turn, respecting the original groups / model files
			int numExtruders = 1;

			Printer selPrinter = selectedPrinter.get();
			if (selectedPrinter != null && selectedPrinter.getValue() != null) {
				numExtruders = selPrinter.extrudersProperty().size();
			}

			int currentExtruder = 0;

			for (ModelLoadResult loadResult : loadResults.getResults()) {
				Set<ModelContainer> modelContainers = (Set) loadResult.getProjectifiableThings();
				Set<String> invalidModelNames = new HashSet<>();
				for (ModelContainer modelContainer : modelContainers) {
					Optional<MeshUtils.MeshError> error = MeshUtils.validate((TriangleMesh) modelContainer.getMeshView().getMesh());
					if (error.isPresent()) {
						invalidModelNames.add(modelContainer.getModelName());
						modelContainer.setIsInvalidMesh(true);
						LOGGER.debug("Model load - " + error.get().name());
					}

					//Assign the models incrementally to the extruders
					modelContainer.getAssociateWithExtruderNumberProperty().set(currentExtruder);
				}

				if (currentExtruder < numExtruders - 1) {
					currentExtruder++;
				}
				else {
					currentExtruder = 0;
				}

				if (!invalidModelNames.isEmpty()) {
					boolean load = systemNotificationManager.showModelIsInvalidDialog(invalidModelNames);
					if (!load) {
						return;
					}
				}
			}

			boolean projectIsEmpty = project.getNumberOfProjectifiableElements() == 0;
			Set<ModelContainer> allModelContainers = new HashSet<>();
			boolean shouldCentre = loadResults.isShouldCentre();

			for (ModelLoadResult loadResult : loadResults.getResults()) {
				if (loadResult == null) {
					LOGGER.error("Error whilst attempting to load model");
					continue;
				}

				Set<ModelContainer> modelContainersToOperateOn = (Set) loadResult.getProjectifiableThings();
				if (splitLoosePartsOnLoadPreference.getValue()) {
					allModelContainers.add(makeGroup(modelContainersToOperateOn));
					continue;
				}

				allModelContainers.addAll(modelContainersToOperateOn);
			}

			Set<ProjectifiableThing> allProjectifiableThings = (Set) allModelContainers;

			addToProject(project, allProjectifiableThings, shouldCentre, dontGroupModelsOverride, printer);
			if (relayout && projectIsEmpty && loadResults.getResults().size() > 1) {
				//            project.autoLayout();
			}
		}

		if (project != null
				&& callMeBack != null) {
			callMeBack.modelAddedToProject(project);
		}
	}

	public ReadOnlyBooleanProperty modelLoadingProperty() {
		return modelLoaderService.runningProperty();
	}

	/**
	 * Load each model in modelsToLoad, do not lay them out on the bed.
	 *
	 * @param project
	 * @param modelsToLoad
	 * @param callMeBack
	 */
	public void loadExternalModels(Project project, List<File> modelsToLoad, ProjectCallback callMeBack) {
		loadExternalModels(project, modelsToLoad, false, callMeBack, false);
	}

	/**
	 * Load each model in modelsToLoad and relayout if requested. If there are already models loaded in the project then do not relayout even if relayout=true;
	 *
	 * @param project
	 * @param modelsToLoad
	 * @param relayout
	 * @param callMeBack
	 */
	public void loadExternalModels(Project project, List<File> modelsToLoad, boolean relayout, ProjectCallback callMeBack,
			boolean dontGroupModelsOverride) {
		modelLoaderService.reset();
		modelLoaderService.setModelFilesToLoad(modelsToLoad);
		modelLoaderService.setOnFailed((WorkerStateEvent t) -> {
			LOGGER.error("Model loader service failed", t.getSource().getException());
			systemNotificationManager.showErrorNotification(
					i18n.t("dialogs.loadModelFailedTitle"),
					i18n.t("dialogs.loadModelFailedMessage"));
		});
		modelLoaderService.setOnSucceeded((WorkerStateEvent t) -> {
			Project projectToUse = null;

			if (project == null) {
				ModelLoadResults loadResults = modelLoaderService.getValue();
				if (!loadResults.getResults().isEmpty()) {
					switch (loadResults.getType()) {
						case Mesh:
							projectToUse = projectFactory.create();
							projectToUse.initialiseExtruderFilaments(selectedPrinter.get());
							break;
					}
				}
			}
			else {
				projectToUse = project;
			}
			offerShrinkAndAddToProject(projectToUse, relayout, callMeBack, dontGroupModelsOverride, selectedPrinter.get());
		});
		modelLoaderService.start();
	}

	/**
	 * Add the given ModelContainers to the project. Some may be ModelGroups. If there is more than one ModelContainer/Group then put them in one overarching group.
	 */
	private void addToProject(Project project, Set<ProjectifiableThing> modelContainers,
			boolean shouldCentre,
			boolean dontGroupModelsOverride,
			Printer printer) {
		UndoableProject undoableProject = undoableProjectFactory.create(project);

		ModelContainer modelContainer;

		if (modelContainers.size() == 1) {
			modelContainer = (ModelContainer) modelContainers.iterator().next();
			addModelSequence(undoableProject, modelContainer, shouldCentre, printer);
		}
		else if (!dontGroupModelsOverride) {
			Set<Groupable> thingsToGroup = (Set) modelContainers;
			modelContainer = project.createNewGroupAndAddModelListeners(thingsToGroup);
			addModelSequence(undoableProject, modelContainer, shouldCentre, printer);
		}
		else {
			modelContainers.iterator().forEachRemaining(mc -> {
				addModelSequence(undoableProject, mc, shouldCentre, printer);
			});
		}
	}

	private void addModelSequence(UndoableProject undoableProject,
			ProjectifiableThing projectifiableThing,
			boolean shouldCentre,
			Printer printer) {
		shrinkIfRequested(projectifiableThing, printer);
		if (shouldCentre) {
			projectifiableThing.moveToCentre();
			if (projectifiableThing instanceof ModelContainer) {
				((ModelContainer) projectifiableThing).dropToBed();
			}
		}
		projectifiableThing.checkOffBed();
		undoableProject.addModel(projectifiableThing);
	}

	private void shrinkIfRequested(ProjectifiableThing projectifiableThing, Printer printer) {
		boolean shrinkModel = false;
		RectangularBounds originalBounds = projectifiableThing.getOriginalModelBounds();

		if (printer != null) {
			boolean modelIsTooLarge = printer.isBiggerThanPrintVolume(originalBounds);
			if (modelIsTooLarge) {
				shrinkModel = systemNotificationManager.showModelTooBigDialog(projectifiableThing.getModelName());
			}
			if (shrinkModel) {
				projectifiableThing.shrinkToFitBed();
			}
		}
	}

	private ModelContainer makeGroup(Set<ModelContainer> modelContainers) {
		Set<ModelContainer> splitModelContainers = new HashSet<>();
		for (ModelContainer modelContainer : modelContainers) {
			try {
				ModelContainer splitContainerOrGroup = modelContainer.splitIntoParts();
				splitModelContainers.add(splitContainerOrGroup);
			}
			catch (StackOverflowError ex) {
				splitModelContainers.add(modelContainer);
			}
		}
		if (splitModelContainers.size() == 1) {
			return splitModelContainers.iterator().next();
		}
		else {
			return modelGroupFactory.create(splitModelContainers);
		}
	}

}
