package org.openautomaker.ui.component.preview_panel;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.services.gcodegenerator.GCodeGeneratorResult;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.environment.preference.slicer.ShowGCodePreviewPreference;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.appManager.ModelContainerProject;
import celtech.appManager.Project;
import org.openautomaker.ui.StandardColours;
import celtech.services.gcodepreview.GCodePreviewExecutorService;
import celtech.services.gcodepreview.GCodePreviewTask;
import jakarta.inject.Inject;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;

/**
 * FXML Controller class
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class PreviewManagerController {

	private static final Logger LOGGER = LogManager.getLogger();

	public enum PreviewState {
		CLOSED,
		LOADING,
		OPEN,
		SLICE_UNAVAILABLE,
		NOT_SUPPORTED
	}

	private ObjectProperty<PreviewState> previewState = new SimpleObjectProperty<>(PreviewState.CLOSED);
	private Project currentProject = null;
	private GCodePreviewExecutorService updateExecutor = new GCodePreviewExecutorService();
	private GCodePreviewExecutorService previewExecutor = new GCodePreviewExecutorService();
	private GCodePreviewTask previewTask = null;

	private final ChangeListener<Boolean> previewRunningListener = (observable, wasRunning, isRunning) -> {
		if (wasRunning && !isRunning) {
			removePreview();
		}
	};

	private final ChangeListener<Boolean> gCodePrepChangeListener = (observable, oldValue, newValue) -> {
		//LOGGER.info("gCodePrepChangeListener");
		autoStartAndUpdatePreview();
	};

	private final ChangeListener<PrintQualityEnumeration> printQualityChangeListener = (observable, oldValue, newValue) -> {
		//LOGGER.info("printQualityChangeListener");
		autoStartAndUpdatePreview();
	};

	private final ChangeListener<ApplicationMode> applicationModeChangeListener = (observable, oldValue, newValue) -> {
		//LOGGER.info("printQualityChangeListener");
		if (newValue == ApplicationMode.SETTINGS) {
			autoStartAndUpdatePreview();
		}
	};

	private final ApplicationStatus applicationStatus;
	private final ShowGCodePreviewPreference showGCodePreviewPreference;
	private final SelectedPrinter selectedPrinter;
	private final ProjectsPathPreference projectsPathPreference;

	@Inject
	public PreviewManagerController(
			ApplicationStatus applicationStatus,
			ShowGCodePreviewPreference showGCodePreviewPreference,
			SelectedPrinter selectedPrinter,
			ProjectsPathPreference projectsPathPreference) {

		this.applicationStatus = applicationStatus;
		this.showGCodePreviewPreference = showGCodePreviewPreference;
		this.selectedPrinter = selectedPrinter;
		this.projectsPathPreference = projectsPathPreference;

		//		if(BaseConfiguration.isWindows32Bit())
		//		{
		//			//LOGGER.info("Setting previewState to NOT_SUPPORTED");
		//			previewState.set(PreviewState.NOT_SUPPORTED);
		//		}

		try {
			applicationStatus.modeProperty().addListener(applicationModeChangeListener);
		}
		catch (Exception ex) {
			LOGGER.error("Unexpected error in PreviewManager constructor", ex);
		}
	}

	public ReadOnlyObjectProperty<PreviewState> previewStateProperty() {
		return previewState;
	}

	public void previewAction(ActionEvent event) {
		//LOGGER.info("previewAction");
		if (previewState.get() != PreviewState.OPEN)
			updatePreview();
	}

	public void setProjectAndPrinter(Project project, Printer printer) {
		if (currentProject != project) {
			if (currentProject != null && currentProject instanceof ModelContainerProject) {
				((ModelContainerProject) currentProject).getGCodeGenManager().getDataChangedProperty().removeListener(this.gCodePrepChangeListener);
				((ModelContainerProject) currentProject).getGCodeGenManager().getPrintQualityProperty().removeListener(this.printQualityChangeListener);
			}

			currentProject = project;
			if (currentProject != null && currentProject instanceof ModelContainerProject) {
				((ModelContainerProject) currentProject).getGCodeGenManager().getDataChangedProperty().addListener(this.gCodePrepChangeListener);
				((ModelContainerProject) currentProject).getGCodeGenManager().getPrintQualityProperty().addListener(this.printQualityChangeListener);
				if (previewState.get() == PreviewState.OPEN ||
						previewState.get() == PreviewState.LOADING ||
						previewState.get() == PreviewState.SLICE_UNAVAILABLE) {
					updatePreview();
				}
				else if (previewState.get() != PreviewState.NOT_SUPPORTED) {
					//LOGGER.info("Setting previewState to CLOSED");
					previewState.set(PreviewState.CLOSED);
				}
			}
			else {
				clearPreview();
			}
		}
	}

	public void shutdown() {
		removePreview();

		if (currentProject != null && currentProject instanceof ModelContainerProject)
			((ModelContainerProject) currentProject).getGCodeGenManager().getDataChangedProperty().removeListener(this.gCodePrepChangeListener);
		currentProject = null;

		applicationStatus.modeProperty().removeListener(applicationModeChangeListener);
	}

	private boolean modelIsSuitable() {
		return (currentProject != null &&
				currentProject instanceof ModelContainerProject &&
				((ModelContainerProject) currentProject).getGCodeGenManager().modelIsSuitable());
	}

	private void clearPreview() {
		//LOGGER.info("clearPreview");
		if (previewTask != null) {
			//LOGGER.info("Clearing preview");
			previewTask.clearGCode();
		}
		//LOGGER.info("clearPreview done");
	}

	// Start and remove preview need to be synchronized so that
	// the previewTask is started/stopped and the variable updated
	// as a single transaction.
	private synchronized void removePreview() {
		//LOGGER.info("removingPreview");
		if (previewTask != null) {
			previewTask.runningProperty().removeListener(previewRunningListener);
			previewTask.terminatePreview();
			previewState.set(PreviewState.CLOSED);
		}
		previewTask = null;
		//LOGGER.info("removePreview done");
	}

	// There are some curious issues with starting the preview.
	// Originally the preview tried open in  specific position relative
	// to the AutoMaker window. To do this, it called displayManager.getNormalisedPreviewRectangle(),
	// which queries some JavaFX nodes. The calling thread was not necessarily the main JavaFX thread.
	// Most of the time this worked, but if the tabs and the forward button were clicked multiple times,
	// particularly during startup, it would sometimes corrupt the internal JavaFX data structures,
	// causing JavaFX to throw exceptions and enter an infinite loop, freezing the GUI.
	//
	// Making startPreview into a FutureTask, running it on the JavaFx thread and waiting for the result produced even
	// wierder symptoms. Clicking on the forward and tab buttons during starup would cause startPreview to be
	// called multiple times but not complete. A preview window would appear but not update. On closing the preview,
	// another would immediately appear. It seems these previews started by the calls to startPreview that did not complete.
	// If all these "phantom" previews were closed, so no more appeared, then opening a preview with the preview button worked.
	// The current code below, which creates the previewTask immediately, and places in a fixed position rather than calling
	// the displayManager, seems to work OK.
	private synchronized void startPreview() {
		//LOGGER.info("startPreview");
		if (previewTask == null) {
			String printerType = null;
			Printer printer = selectedPrinter.get();
			if (printer != null)
				printerType = printer.printerConfigurationProperty().get().getTypeCode();

			Path projectPath = projectsPathPreference.getValue().resolve(currentProject.getProjectName());

			Rectangle2D nRectangle = new Rectangle2D(0.25, 0.25, 0.5, 0.5);
			previewTask = new GCodePreviewTask(projectPath.toString(), printerType, nRectangle);
			previewTask.runningProperty().addListener(previewRunningListener);
			previewExecutor.runTask(previewTask);
		}
		//LOGGER.info("startPreview done");
	}

	private void autoStartAndUpdatePreview() {
		//LOGGER.info("autoStartAndUpdatePreview");
		if (previewState.get() == PreviewState.OPEN ||
				previewState.get() == PreviewState.LOADING ||
				previewState.get() == PreviewState.SLICE_UNAVAILABLE ||
				showGCodePreviewPreference.getValue()) {
			//LOGGER.info("autoStartAndUpdatePreview calling updatePreview");
			updatePreview();
		}
	}

	private void updatePreview() {
		boolean modelUnsuitable = !modelIsSuitable();
		if (modelUnsuitable) {
			//LOGGER.info("Model unsuitable: setting previewState to SLICE_UNAVAILABLE ...");
			previewState.set(PreviewState.SLICE_UNAVAILABLE);
			//LOGGER.info("... Model unsuitable: clearing preview ...");
			clearPreview();
			//LOGGER.info("... Model unsuitable done");
		}
		else {
			Runnable doUpdatePreview = () -> {
				// Showing preview preview button.
				//LOGGER.info("Setting previewState to LOADING");
				previewState.set(PreviewState.LOADING);

				//LOGGER.info("Preview is null");
				if (previewTask == null)
					startPreview();
				else
					clearPreview();
				ModelContainerProject mProject = (ModelContainerProject) currentProject;
				//LOGGER.info("Waiting for prep result");
				Optional<GCodeGeneratorResult> resultOpt = mProject.getGCodeGenManager().getPrepResult(currentProject.getPrintQuality());
				//LOGGER.info("Got prep result - ifPresent() = " + Boolean.toString(resultOpt.isPresent()));
				//LOGGER.info("                  isSuccess() = " + (resultOpt.isPresent() ? Boolean.toString(resultOpt.get().isSuccess()) : "---"));
				if (resultOpt.isPresent() && resultOpt.get().isSuccess()) {
					//LOGGER.info("GCodePrepResult = " + resultOpt.get().getPostProcOutputFileName());

					// Get tool colours.
					Color t0Colour = StandardColours.ROBOX_BLUE;
					Color t1Colour = StandardColours.HIGHLIGHT_ORANGE;
					String printerType = null;
					Printer printer = selectedPrinter.get();
					if (printer != null) {
						printerType = printer.printerConfigurationProperty().get().getTypeCode();

						Head head = printer.headProperty().get();
						if (head != null) {
							// Assume we have at least one extruder.
							Filament filamentInUse;
							filamentInUse = printer.effectiveFilamentsProperty().get(0);
							if (filamentInUse != null && filamentInUse != FilamentContainer.UNKNOWN_FILAMENT) {
								Color colour = filamentInUse.getDisplayColour();
								if (colour != null)
									t0Colour = colour;
							}
							if (head.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
								t1Colour = t0Colour;
								t0Colour = Color.ORANGE;
								filamentInUse = printer.effectiveFilamentsProperty().get(1);
								if (filamentInUse != null && filamentInUse != FilamentContainer.UNKNOWN_FILAMENT) {
									Color colour = filamentInUse.getDisplayColour();
									if (colour != null)
										t0Colour = colour;
								}
							}
							else
								t1Colour = t0Colour;
						}
					}

					//LOGGER.info("Preview is still null");
					if (previewTask == null)
						startPreview();
					else
						previewTask.setPrinterType(printerType);
					//LOGGER.info("Loading GCode file = " + resultOpt.get().getPostProcOutputFileName());
					previewTask.setToolColour(0, t0Colour);
					previewTask.setToolColour(1, t1Colour);
					previewTask.loadGCodeFile(resultOpt.get().getPostProcOutputFileName());
					if (showGCodePreviewPreference.getValue())
						previewTask.giveFocus();

					//LOGGER.info("Setting previewState to OPEN ...");
					previewState.set(PreviewState.OPEN);
					//LOGGER.info("... OPEN done");
				}
				else {
					// Failed.
					//LOGGER.info("Failed - Setting previewState to SLICE_UNAVAILABLE ...");
					previewState.set(PreviewState.SLICE_UNAVAILABLE);
					//LOGGER.info("... SLICE_UNAVAILABLE done");
				}
			};

			//LOGGER.info("Cancelling update tasks");
			updateExecutor.cancelTask();
			//LOGGER.info("Running update tasks");
			updateExecutor.runTask(doUpdatePreview);
			//LOGGER.info("done updates");
		}
		//LOGGER.info("Updating preview done");
	}
}
