package celtech.coreUI.controllers.panels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.utils.RoboxProfileUtils;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;
import org.openautomaker.base.task_executor.Cancellable;
import org.openautomaker.base.task_executor.SimpleCancellable;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
import org.openautomaker.ui.ProjectAwareController;
import org.openautomaker.ui.state.SelectedPrinter;
import org.openautomaker.ui.state.SelectedProject;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.appManager.GCodeGeneratorManager;
import celtech.appManager.ModelContainerProject;
import celtech.appManager.Project;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

/**
 * FXML Controller class
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class TimeCostInsetPanelController implements ProjectAwareController {

	private static final Logger LOGGER = LogManager.getLogger();

	private final SlicerPreference slicerPreference;

	@FXML
	private HBox timeCostInsetRoot;

	@FXML
	private Label lblDraftTime;
	@FXML
	private Label lblNormalTime;
	@FXML
	private Label lblFineTime;
	@FXML
	private Label lblCustomTime;
	@FXML
	private Label lblDraftWeight;
	@FXML
	private Label lblNormalWeight;
	@FXML
	private Label lblFineWeight;
	@FXML
	private Label lblCustomWeight;
	@FXML
	private Label lblDraftCost;
	@FXML
	private Label lblNormalCost;
	@FXML
	private Label lblFineCost;
	@FXML
	private Label lblCustomCost;
	@FXML
	private RadioButton rbDraft;
	@FXML
	private RadioButton rbNormal;
	@FXML
	private RadioButton rbFine;
	@FXML
	private RadioButton rbCustom;
	@FXML
	private Label headAndSlicerType;

	private ToggleGroup qualityToggleGroup;

	private Project currentProject;
	private Printer currentPrinter;
	private String currentHeadType = "";

	private boolean settingPrintQuality = false;
	private boolean slicedAlready = false;

	private List<PrintQualityEnumeration> sliceOrder = new ArrayList<>(Arrays.asList(PrintQualityEnumeration.NORMAL,
			PrintQualityEnumeration.DRAFT,
			PrintQualityEnumeration.FINE,
			PrintQualityEnumeration.CUSTOM));

	private final TimeCostThreadManager timeCostThreadManager = TimeCostThreadManager.getInstance();
	//private final ExecutorService executorService = Executors.newFixedThreadPool(4);

	private final ChangeListener<Boolean> gCodePrepChangeListener;

	private final ChangeListener<ApplicationMode> applicationModeChangeListener;

	private final I18N i18n;
	private final TaskExecutor taskExecutor;
	private final ApplicationStatus applicationStatus;
	private final SelectedPrinter selectedPrinter;

	@Inject
	protected TimeCostInsetPanelController(
			I18N i18n,
			TaskExecutor taskExecutor,
			ApplicationStatus applicationStatus,
			SelectedPrinter selectedPrinter,
			SelectedProject selectedProject,
			SlicerPreference slicerPreference) {

		this.i18n = i18n;
		this.taskExecutor = taskExecutor;
		this.applicationStatus = applicationStatus;
		this.selectedPrinter = selectedPrinter;
		this.slicerPreference = slicerPreference;

		gCodePrepChangeListener = (ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			currentPrinter = selectedPrinter.get();
			updateHeadType(currentPrinter);
			updateFields(currentProject);
		};

		applicationModeChangeListener = (observable, oldValue, newValue) -> {
			if (newValue == ApplicationMode.SETTINGS) {
				timeCostInsetRoot.setVisible(true);
				timeCostInsetRoot.setMouseTransparent(false);
				if (selectedProject.get() == currentProject) {
					updateFields(currentProject);
				}
			}
			else {
				timeCostInsetRoot.setVisible(false);
				timeCostInsetRoot.setMouseTransparent(true);
				timeCostThreadManager.cancelRunningTimeCostTasks();
			}
		};
	}

	/**
	 * Initialises the controller class.
	 *
	 */
	public void initialize() {
		try {
			currentPrinter = selectedPrinter.get();
			updateHeadType(selectedPrinter.get());

			applicationStatus.modeProperty().addListener(applicationModeChangeListener);

			setupQualityRadioButtons();

			slicerPreference.addChangeListener(new PreferenceChangeListener() {
				@Override
				public void preferenceChange(PreferenceChangeEvent evt) {
					updateHeadAndSlicerType();
				}
			});
		}
		catch (Exception ex) {
			LOGGER.error("Exception when initializing TimeCostInsetPanel", ex);
		}
	}

	private void updateHeadType(Printer printer) {
		String headTypeBefore = currentHeadType;
		if (printer != null && printer.headProperty().get() != null) {
			currentHeadType = printer.headProperty().get().typeCodeProperty().get();
		}
		else {
			currentHeadType = HeadContainer.defaultHeadID;
		}
		if (!headTypeBefore.equals(currentHeadType)) {
			taskExecutor.runOnGUIThread(() -> {
				updateHeadAndSlicerType();
			});
		}
	}

	private void updateHeadAndSlicerType() {
		headAndSlicerType.setText(i18n.t("Estimates for head type: "
				+ currentHeadType
				+ "   -   "
				+ "Slicing with: "
				+ slicerPreference.getValue()));
	}

	private void setupQualityRadioButtons() {
		qualityToggleGroup = new ToggleGroup();
		rbDraft.setToggleGroup(qualityToggleGroup);
		rbDraft.setUserData(PrintQualityEnumeration.DRAFT);
		rbNormal.setToggleGroup(qualityToggleGroup);
		rbNormal.setUserData(PrintQualityEnumeration.NORMAL);
		rbFine.setToggleGroup(qualityToggleGroup);
		rbFine.setUserData(PrintQualityEnumeration.FINE);
		rbCustom.setToggleGroup(qualityToggleGroup);
		rbCustom.setUserData(PrintQualityEnumeration.CUSTOM);
		qualityToggleGroup.selectedToggleProperty().addListener(
				(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue) -> {
					settingPrintQuality = true;
					GCodeGeneratorManager gCodeGeneratorManager = currentProject.getGCodeGenManager();
					changeSlicingOrder((PrintQualityEnumeration) newValue.getUserData(), gCodeGeneratorManager);

					if (currentProject != null && currentProject instanceof ModelContainerProject) {
						gCodeGeneratorManager.setSuppressReaction(true);
					}

					currentProject.getPrinterSettings().setPrintQuality((PrintQualityEnumeration) newValue.getUserData());

					if (currentProject != null && currentProject instanceof ModelContainerProject) {
						gCodeGeneratorManager.setSuppressReaction(false);
					}

					settingPrintQuality = false;

					if (oldValue != null
							&& (PrintQualityEnumeration) oldValue.getUserData() == PrintQualityEnumeration.CUSTOM
							&& !slicedAlready) {
						updateFields(currentProject);
					}
				});
	}

	private void changeSlicingOrder(PrintQualityEnumeration firstToSlice, GCodeGeneratorManager gCodeGeneratorManager) {
		sliceOrder = new ArrayList<>(Arrays.asList(PrintQualityEnumeration.values()));
		sliceOrder.remove(firstToSlice);
		sliceOrder.add(0, firstToSlice);
		gCodeGeneratorManager.changeSlicingOrder(sliceOrder);
	}

	@Override
	public void setProject(Project project) {
		if (currentProject != null && currentProject instanceof ModelContainerProject)
			currentProject.getGCodeGenManager().getDataChangedProperty().removeListener(this.gCodePrepChangeListener);

		currentProject = project;
		if (currentProject != null) {
			selectPrintProfile(currentProject.getPrintQuality());
		}

		if (currentProject != null && currentProject instanceof ModelContainerProject)
			currentProject.getGCodeGenManager().getDataChangedProperty().addListener(this.gCodePrepChangeListener);
	}

	private void selectPrintProfile(PrintQualityEnumeration printQuality) {
		switch (printQuality) {
			case DRAFT:
				rbDraft.setSelected(true);
				break;
			case NORMAL:
				rbNormal.setSelected(true);
				break;
			case FINE:
				rbFine.setSelected(true);
				break;
			case CUSTOM:
				rbCustom.setSelected(true);
				break;
		}
	}

	/**
	 * Update the time, cost and weight fields. Long running calculations must be performed in a background thread. Run draft, normal and fine sequentially to avoid flooding the CPU(s).
	 */
	private void updateFields(Project project) {
		if (settingPrintQuality || applicationStatus.modeProperty().get() != ApplicationMode.SETTINGS) {
			return;
		}

		taskExecutor.runOnGUIThread(() -> {
			lblDraftTime.setText("...");
			lblNormalTime.setText("...");
			lblFineTime.setText("...");
			lblCustomTime.setText("...");
			lblDraftWeight.setText("...");
			lblNormalWeight.setText("...");
			lblFineWeight.setText("...");
			lblCustomWeight.setText("...");
			lblDraftCost.setText("...");
			lblNormalCost.setText("...");
			lblFineCost.setText("...");
			lblCustomCost.setText("...");
		});

		Cancellable cancellable = new SimpleCancellable();

		//TODO: Looks like this should be typed to PrintQualityEnumeration
		Runnable runUpdateFields = () -> {
			List<Future> futureList = new ArrayList<>();
			for (PrintQualityEnumeration printQuality : sliceOrder) {
				switch (printQuality) {
					case DRAFT:
						updateFieldsForQuality(project, PrintQualityEnumeration.DRAFT, lblDraftTime,
								lblDraftWeight,
								lblDraftCost, cancellable);
						break;
					case NORMAL:
						updateFieldsForQuality(project, PrintQualityEnumeration.NORMAL, lblNormalTime,
								lblNormalWeight,
								lblNormalCost, cancellable);
						break;
					case FINE:
						updateFieldsForQuality(project, PrintQualityEnumeration.FINE, lblFineTime,
								lblFineWeight,
								lblFineCost, cancellable);
						break;
					case CUSTOM:
						if (!currentProject.getPrinterSettings().getSettingsName().equals("")) {
							updateFieldsForQuality(project, PrintQualityEnumeration.CUSTOM, lblCustomTime,
									lblCustomWeight,
									lblCustomCost, cancellable);
						}
						break;
				}
				if (cancellable.cancelled().get())
					break;
			}

			try {
				for (Future f : futureList) {
					f.get();
				}
			}
			catch (InterruptedException | ExecutionException ex) {
			}

			if (cancellable.cancelled().get()) {
				futureList.forEach((f) -> {
					f.cancel(true);
				});
			}
		};

		slicedAlready = true;
		timeCostThreadManager.cancelRunningTimeCostTasksAndRun(runUpdateFields, cancellable);
	}

	/**
	 * Update the time, cost and weight fields for the given print quality and fields. Long running calculations must be performed in a background thread.
	 */
	private void updateFieldsForQuality(Project project, PrintQualityEnumeration printQuality,
			Label lblTime, Label lblWeight, Label lblCost, Cancellable cancellable) {
		if (!modelOutOfBounds(project, printQuality)) {
			if (project instanceof ModelContainerProject) {
				String working = i18n.t("timeCost.working");
				taskExecutor.runOnGUIThread(() -> {
					lblTime.setText(working);
					lblWeight.setText(working);
					lblCost.setText(working);
				});

				GetTimeWeightCost updateDetails = new GetTimeWeightCost((ModelContainerProject) project,
						lblTime, lblWeight,
						lblCost, cancellable);

				updateDetails.updateFromProject(printQuality);
			}
		}
	}

	private boolean modelOutOfBounds(Project project, PrintQualityEnumeration printQuality) {
		String headTypeToUse = HeadContainer.defaultHeadID;
		if (currentPrinter != null && currentPrinter.headProperty().get() != null) {
			headTypeToUse = currentPrinter.headProperty().get().typeCodeProperty().get();
		}

		RoboxProfile profileSettings = null;
		if (project != null && project.getNumberOfProjectifiableElements() > 0) {
			profileSettings = project.getPrinterSettings().getSettings(headTypeToUse, slicerPreference.getValue(), printQuality);
		}

		double zReduction = 0.0;
		if (currentPrinter != null && currentPrinter.headProperty().get() != null) {
			zReduction = currentPrinter.headProperty().get().getZReductionProperty().get();
		}

		double raftOffset = profileSettings == null ? 0.0 : RoboxProfileUtils.calculateRaftOffset(profileSettings, slicerPreference.getValue());

		boolean aModelIsOffTheBed = false;
		if (project != null && project.getTopLevelThings() != null) {
			for (ProjectifiableThing projectifiableThing : project.getTopLevelThings()) {
				if (projectifiableThing instanceof ModelContainer) {
					ModelContainer modelContainer = (ModelContainer) projectifiableThing;

					//TODO use settings derived offset values for spiral
					if (modelContainer.isOffBedProperty().get()
							|| (project.getPrinterSettings().getRaftOverride()
									&& modelContainer.isModelTooHighWithOffset(zReduction + raftOffset))
							|| (project.getPrinterSettings().getSpiralPrintOverride()
									&& modelContainer.isModelTooHighWithOffset(0.5))) {
						aModelIsOffTheBed = true;
						break;
					}
				}
			}
		}

		return aModelIsOffTheBed;
	}

	//TODO: Tidy up
	//	private void updatePrintQuality(PrinterSettingsOverrides printerSettings) {
	//		switch (printerSettings.getPrintQuality()) {
	//			case DRAFT:
	//				rbDraft.setSelected(true);
	//				break;
	//			case NORMAL:
	//				rbNormal.setSelected(true);
	//				break;
	//			case FINE:
	//				rbFine.setSelected(true);
	//				break;
	//			case CUSTOM:
	//				rbCustom.setSelected(true);
	//				break;
	//		}
	//	}

	@Override
	public void shutdownController() {

		if (currentProject != null && currentProject instanceof ModelContainerProject)
			currentProject.getGCodeGenManager().getDataChangedProperty().removeListener(this.gCodePrepChangeListener);
		currentProject = null;

		applicationStatus.modeProperty().removeListener(applicationModeChangeListener);
	}
}
