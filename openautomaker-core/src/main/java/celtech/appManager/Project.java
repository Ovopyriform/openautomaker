package celtech.appManager;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.base.configuration.datafileaccessors.CameraProfileContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterSettingsOverrides;
import org.openautomaker.base.device.CameraManager;
import org.openautomaker.base.inject.configuration.file_representation.PrinterSettingsOverridesFactory;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
import org.openautomaker.ui.inject.model.ModelGroupFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import celtech.configuration.ApplicationConfiguration;
import celtech.configuration.fileRepresentation.ProjectFile;
import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;
import celtech.modelcontrol.ProjectifiableThing;
import celtech.modelcontrol.ResizeableThreeD;
import celtech.modelcontrol.ResizeableTwoD;
import celtech.modelcontrol.ScaleableThreeD;
import celtech.modelcontrol.ScaleableTwoD;
import celtech.modelcontrol.Translateable;
import celtech.modelcontrol.TranslateableTwoD;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
//TODO: Look into the raw types in this class.
public abstract class Project {

	private static final Logger LOGGER = LogManager.getLogger();

	public static class ProjectLoadException extends Exception {

		private static final long serialVersionUID = -8924678649000819002L;

		public ProjectLoadException(String message) {
			super(message);
		}
	}

	private int version = -1;



	protected Set<ProjectChangesListener> projectChangesListeners;

	protected BooleanProperty canPrint;
	protected BooleanProperty customSettingsNotChosen;

	protected final PrinterSettingsOverrides printerSettings;

	protected final TimelapseSettingsData timelapseSettings;

	protected final StringProperty projectNameProperty;
	protected ObjectProperty<Date> lastModifiedDate;

	protected boolean suppressProjectChanged = false;
	protected boolean projectSaved = true;

	protected ObjectProperty<ProjectMode> mode = new SimpleObjectProperty<>(ProjectMode.NONE);

	protected ObservableList<ProjectifiableThing> topLevelThings;

	protected String lastPrintJobID = "";

	protected boolean projectNameModified = false;

	private final GCodeGeneratorManager gCodeGenManager;

	//Injected, don't persiste
	private transient final CameraManager cameraManager;
	private transient final ModelGroupFactory modelGroupFactory;
	protected transient final ProjectsPathPreference projectsPathPreference;
	private transient final PrinterSettingsOverridesFactory printerSettingsOverridesFactory;

	private final CameraProfileContainer cameraProfileContainer;

	public Project(
			ProjectsPathPreference projectsPathPreference,
			SlicerPreference slicerPreference,
			I18N i18n,
			CameraManager cameraManager,
			GCodeGeneratorManager gCodeGeneratorManager,
			ModelGroupFactory modelGroupFactory,
			CameraProfileContainer cameraProfileContainer,
			PrinterSettingsOverridesFactory printerSettingsOverridesFactory) {

		this.cameraManager = cameraManager;
		this.modelGroupFactory = modelGroupFactory;
		this.cameraProfileContainer = cameraProfileContainer;
		this.printerSettingsOverridesFactory = printerSettingsOverridesFactory;

		topLevelThings = FXCollections.observableArrayList();

		canPrint = new SimpleBooleanProperty(true);
		customSettingsNotChosen = new SimpleBooleanProperty(true);
		lastModifiedDate = new SimpleObjectProperty<>();
		projectChangesListeners = new HashSet<>();

		this.projectsPathPreference = projectsPathPreference;

		printerSettings = printerSettingsOverridesFactory.create();
		Date now = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("-hhmmss-ddMMYY");
		projectNameProperty = new SimpleStringProperty(i18n.t("projectLoader.untitled") + formatter.format(now));
		lastModifiedDate.set(now);

		this.gCodeGenManager = gCodeGeneratorManager;
		gCodeGeneratorManager.setProject(this);

		customSettingsNotChosen.bind(printerSettings.printQualityProperty().isEqualTo(PrintQualityEnumeration.CUSTOM).and(printerSettings.getSettingsNameProperty().isEmpty()));
		// Cannot print if quality is CUSTOM and no custom settings have been chosen
		canPrint.bind(customSettingsNotChosen.not().and(gCodeGenManager.printOrSaveTaskRunningProperty().not()));

		printerSettings.getDataChanged().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			projectModified();
			fireWhenPrinterSettingsChanged(printerSettings);
		});

		timelapseSettings = new TimelapseSettingsData();
		timelapseSettings.getDataChanged().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			projectModified();
			fireWhenTimelapseSettingsChanged(timelapseSettings);
		});

		slicerPreference.addChangeListener(new PreferenceChangeListener() {
			@Override
			public void preferenceChange(PreferenceChangeEvent evt) {
				projectModified();
			}
		});
	}

	protected abstract void initialise();

	public final void setProjectName(String value) {
		projectNameProperty.set(value);
	}

	public final String getProjectName() {
		return projectNameProperty.get();
	}

	public final StringProperty projectNameProperty() {
		return projectNameProperty;
	}

	public final Path getAbsolutePath() {
		//TODO: It looks like this should just enumerate all the .robox files in the project directory and load those.  Not sure of the point of the open projects data.
		//return AutoMakerEnvironment.get().getUserPath(PROJECTS).resolve(getProjectName()).resolve(projectNameProperty.get() + ApplicationConfiguration.projectFileExtension);
		return projectsPathPreference.getValue().resolve(getProjectName()).resolve(getProjectName() + ApplicationConfiguration.projectFileExtension);
	}

	protected abstract void load(ProjectFile projectFile, Path filePath) throws ProjectLoadException;

	//	public static final Project loadProject(String basePath) {
	//		Project project = null;
	//		File file = new File(basePath + ApplicationConfiguration.projectFileExtension);
	//
	//		try {
	//			ProjectFileDeserialiser deserializer = new ProjectFileDeserialiser();
	//			SimpleModule module = new SimpleModule("LegacyProjectFileDeserialiserModule", new Version(1, 0, 0, null));
	//			module.addDeserializer(ProjectFile.class, deserializer);
	//
	//			ObjectMapper mapper = new ObjectMapper();
	//			mapper.registerModule(module);
	//			ProjectFile projectFile = mapper.readValue(file, ProjectFile.class);
	//
	//			if (projectFile instanceof ModelContainerProjectFile) {
	//				project = new ModelContainerProject();
	//				project.load(projectFile, basePath);
	//			}
	//			else if (projectFile instanceof ShapeContainerProjectFile) {
	//				project = new ShapeContainerProject();
	//				project.load(projectFile, basePath);
	//			}
	//		}
	//		catch (Exception ex) {
	//			LOGGER.error("Unable to load project file at " + file.toString(), ex);
	//		}
	//		return project;
	//	}

	public void save() {
		Path basePath = projectsPathPreference.getValue().resolve(getProjectName());

		File dirHandle = basePath.toFile();
		if (dirHandle.exists())
			dirHandle.mkdirs();

		save(basePath);
		setProjectSaved(true);
	}

	protected abstract void save(Path basePath);

	@Override
	public String toString() {
		return projectNameProperty.get();
	}

	public final PrintQualityEnumeration getPrintQuality() {
		return printerSettings.getPrintQuality();
	}

	public final void setPrintQuality(PrintQualityEnumeration printQuality) {
		if (printerSettings.getPrintQuality() != printQuality) {
			projectModified();
			printerSettings.setPrintQuality(printQuality);
		}
	}

	public final PrinterSettingsOverrides getPrinterSettings() {
		return printerSettings;
	}

	public final TimelapseSettingsData getTimelapseSettings() {
		return timelapseSettings;
	}

	public abstract void addModel(ProjectifiableThing projectifiableThing);

	public abstract void removeModels(Set<ProjectifiableThing> projectifiableThings);

	public final void addProjectChangesListener(ProjectChangesListener projectChangesListener) {
		projectChangesListeners.add(projectChangesListener);
	}

	public final void removeProjectChangesListener(ProjectChangesListener projectChangesListener) {
		projectChangesListeners.remove(projectChangesListener);
	}

	public final ObjectProperty<Date> getLastModifiedDate() {
		return lastModifiedDate;
	}

	public final BooleanProperty canPrintProperty() {
		return canPrint;
	}

	public final BooleanProperty customSettingsNotChosenProperty() {
		return customSettingsNotChosen;
	}

	public ObservableList<Boolean> getUsedExtruders(Printer printer) {
		List<Boolean> localUsedExtruders = new ArrayList<>();
		localUsedExtruders.add(false);
		localUsedExtruders.add(false);

		return FXCollections.observableArrayList(localUsedExtruders);
	}

	protected void loadTimelapseSettings(ProjectFile pFile) {
		timelapseSettings.setTimelapseTriggerEnabled(pFile.isTimelapseTriggerEnabled());
		String profileName = pFile.getTimelapseProfileName();
		if (profileName.isBlank())
			timelapseSettings.setTimelapseProfile(Optional.empty());
		else {
			timelapseSettings.setTimelapseProfile(Optional.ofNullable(cameraProfileContainer.getProfileByName(profileName)));
		}
		String cameraID = pFile.getTimelapseCameraID();
		Optional<CameraInfo> camera = Optional.empty();
		if (!cameraID.isBlank()) {
			String[] fields = cameraID.split(":");
			if (fields.length == 2) {
				String cameraName = fields[0];
				try {
					int cameraNumber = Integer.parseInt(fields[1]);
					camera = cameraManager.getConnectedCameras().stream().filter(c -> c.getCameraName().equals(cameraName) && c.getCameraNumber() == cameraNumber).findFirst();
				}
				catch (NumberFormatException ex) {
				}
			}
		}
		timelapseSettings.setTimelapseCamera(camera);
	}

	/**
	 * ProjectChangesListener allows other objects to observe when models are added or removed etc to the project.
	 */
	public interface ProjectChangesListener {

		/**
		 * This should be fired when a model is added to the project.
		 *
		 * @param projectifiableThing
		 */
		void whenModelAdded(ProjectifiableThing projectifiableThing);

		/**
		 * This should be fired when a model is removed from the project.
		 *
		 * @param projectifiableThing
		 */
		void whenModelsRemoved(Set<ProjectifiableThing> projectifiableThing);

		/**
		 * This should be fired when the project is auto laid out.
		 */
		void whenAutoLaidOut();

		/**
		 * This should be fired when one or more models have been moved, rotated or scaled etc. If possible try to fire just once for any given group change.
		 *
		 * @param projectifiableThing
		 */
		void whenModelsTransformed(Set<ProjectifiableThing> projectifiableThing);

		/**
		 * This should be fired when certain details of the model change. Currently this is only: - associatedExtruder
		 *
		 * @param modelContainer
		 * @param propertyName
		 */
		void whenModelChanged(ProjectifiableThing modelContainer, String propertyName);

		/**
		 * This should be fired whenever the PrinterSettings of the project changes.
		 *
		 * @param printerSettings
		 */
		void whenPrinterSettingsChanged(PrinterSettingsOverrides printerSettings);

		/**
		 * This should be fired whenever the TimelapseSettings of the project changes.
		 *
		 * @param timelapseSettings
		 */
		void whenTimelapseSettingsChanged(TimelapseSettingsData timelapseSettings);
	}

	public abstract void autoLayout();

	//This carries out the same function but leaves the existing things in place
	public abstract void autoLayout(List<ProjectifiableThing> thingsToLayout);

	/**
	 * Scale X, Y and Z by the given factor, apply the given ratio to the given scale. I.e. the ratio is not an absolute figure to be applied to the models but a ratio to be applied to the current scale.
	 *
	 * @param projectifiableThings
	 * @param ratio
	 */
	public final void scaleXYZRatioSelection(Set<ScaleableThreeD> projectifiableThings, double ratio) {
		for (ScaleableThreeD projectifiableThing : projectifiableThings) {
			projectifiableThing.setXScale(projectifiableThing.getXScale() * ratio, true);
			projectifiableThing.setYScale(projectifiableThing.getYScale() * ratio, true);
			projectifiableThing.setZScale(projectifiableThing.getZScale() * ratio, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) projectifiableThings);
	}

	/**
	 * Scale X, Y and Z by the given factor, apply the given ratio to the given scale. I.e. the ratio is not an absolute figure to be applied to the models but a ratio to be applied to the current scale.
	 *
	 * @param projectifiableThings
	 * @param ratio
	 */
	public final void scaleXYRatioSelection(Set<ScaleableTwoD> projectifiableThings, double ratio) {
		for (ScaleableTwoD projectifiableThing : projectifiableThings) {
			projectifiableThing.setXScale(projectifiableThing.getXScale() * ratio, true);
			projectifiableThing.setYScale(projectifiableThing.getYScale() * ratio, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) projectifiableThings);
	}

	public final void scaleXModels(Set<ScaleableTwoD> projectifiableThings, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			// this only happens for non-multiselect
			assert (projectifiableThings.size() == 1);
			ScaleableTwoD projectifiableThing = projectifiableThings.iterator().next();
			double ratio = newScale / projectifiableThing.getXScale();
			if (projectifiableThing instanceof ScaleableThreeD) {
				scaleXYZRatioSelection((Set) projectifiableThings, ratio);
			}
			else {
				scaleXYRatioSelection(projectifiableThings, ratio);
			}
		}
		else {
			for (ScaleableTwoD projectifiableThing : projectifiableThings) {
				{
					projectifiableThing.setXScale(newScale, true);
				}
			}
		}
		projectModified();
		fireWhenModelsTransformed((Set) projectifiableThings);
	}

	public final void scaleYModels(Set<ScaleableTwoD> projectifiableThings, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			// this only happens for non-multiselect
			assert (projectifiableThings.size() == 1);
			ScaleableTwoD projectifiableThing = projectifiableThings.iterator().next();
			double ratio = newScale / projectifiableThing.getYScale();

			if (projectifiableThing instanceof ScaleableThreeD) {
				scaleXYZRatioSelection((Set) projectifiableThings, ratio);
			}
			else {
				scaleXYRatioSelection(projectifiableThings, ratio);
			}
		}
		else {
			for (ScaleableTwoD projectifiableThing : projectifiableThings) {
				{
					projectifiableThing.setYScale(newScale, true);
				}
			}
		}
		projectModified();
		fireWhenModelsTransformed((Set) projectifiableThings);
	}

	public final void scaleZModels(Set<ScaleableThreeD> projectifiableThings, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			// this only happens for non-multiselect
			assert (projectifiableThings.size() == 1);
			ScaleableThreeD projectifiableThing = projectifiableThings.iterator().next();
			double ratio = newScale / projectifiableThing.getZScale();
			scaleXYZRatioSelection(projectifiableThings, ratio);
		}
		else {
			for (ScaleableThreeD projectifiableThing : projectifiableThings) {
				{
					projectifiableThing.setZScale(newScale, true);
				}
			}
		}
		projectModified();
		fireWhenModelsTransformed((Set) projectifiableThings);
	}

	public void translateModelsBy(Set<TranslateableTwoD> modelContainers, double x, double y) {
		for (TranslateableTwoD model : modelContainers) {
			model.translateBy(x, y);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsTo(Set<TranslateableTwoD> modelContainers, double x, double y) {
		for (TranslateableTwoD model : modelContainers) {
			model.translateTo(x, y);
		}
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsXTo(Set<TranslateableTwoD> modelContainers, double x) {
		for (TranslateableTwoD model : modelContainers) {
			model.translateXTo(x);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsDepthPositionTo(Set<Translateable> modelContainers, double z) {
		for (Translateable model : modelContainers) {
			model.translateDepthPositionTo(z);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsDepth(Set<ResizeableThreeD> modelContainers, double depth) {
		for (ResizeableThreeD model : modelContainers) {
			model.resizeDepth(depth);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsHeight(Set<ResizeableTwoD> modelContainers, double height) {
		for (ResizeableTwoD model : modelContainers) {
			model.resizeHeight(height);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsWidth(Set<ResizeableTwoD> modelContainers, double width) {
		for (ResizeableTwoD model : modelContainers) {
			model.resizeWidth(width);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public abstract Set<ProjectifiableThing> getAllModels();

	public final Set<ItemState> getModelStates() {
		Set<ItemState> states = new HashSet<>();
		for (ProjectifiableThing model : getAllModels()) {
			states.add(model.getState());
		}
		return states;
	}

	public final void setModelStates(Set<ItemState> modelStates) {
		Set<ProjectifiableThing> modelContainers = new HashSet<>();
		for (ItemState modelState : modelStates) {
			for (ProjectifiableThing model : getAllModels()) {
				if (model.getModelId() == modelState.modelId) {
					model.setState(modelState);
					model.updateOriginalModelBounds();
					modelContainers.add(model);
				}
			}
		}
		projectModified();
		fireWhenModelsTransformed(modelContainers);
	}

	public final ReadOnlyObjectProperty<ProjectMode> getModeProperty() {
		return mode;
	}

	public ProjectMode getMode() {
		return mode.get();
	}

	public final void setMode(ProjectMode mode) {
		this.mode.set(mode);
	}

	protected final void projectModified() {
		if (!suppressProjectChanged) {
			projectSaved = false;
			lastPrintJobID = "";
			lastModifiedDate.set(new Date());
		}
	}

	abstract protected void fireWhenModelsTransformed(Set<ProjectifiableThing> projectifiableThings);

	abstract protected void fireWhenPrinterSettingsChanged(PrinterSettingsOverrides printerSettings);

	abstract protected void fireWhenTimelapseSettingsChanged(TimelapseSettingsData timelapseSettings);

	public int getNumberOfProjectifiableElements() {
		return getAllModels().size();
	}

	public ObservableList<ProjectifiableThing> getTopLevelThings() {
		return topLevelThings;
	}

	public void setLastPrintJobID(String lastPrintJobID) {
		this.lastPrintJobID = lastPrintJobID;
	}

	public String getLastPrintJobID() {
		return lastPrintJobID;
	}

	public boolean isProjectNameModified() {
		return projectNameModified;
	}

	public void setProjectNameModified(boolean projectNameModified) {
		this.projectNameModified = projectNameModified;
	}

	public ModelGroup group(Set<Groupable> modelContainers) {
		Set<ProjectifiableThing> projectifiableThings = (Set) modelContainers;

		removeModels(projectifiableThings);
		ModelGroup modelGroup = createNewGroup(modelContainers);
		addModel(modelGroup);
		return modelGroup;
	}

	public ModelGroup group(Set<Groupable> modelContainers, int groupModelId) {
		Set<ProjectifiableThing> projectifiableThings = (Set) modelContainers;

		removeModels(projectifiableThings);
		ModelGroup modelGroup = createNewGroup(modelContainers, groupModelId);
		addModel(modelGroup);
		return modelGroup;
	}

	/**
	 * Create a new group from models that are not yet in the project.
	 *
	 * @param modelContainers
	 * @param groupModelId
	 * @return
	 */
	public ModelGroup createNewGroup(Set<Groupable> modelContainers, int groupModelId) {
		checkNotAlreadyInGroup(modelContainers);
		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers, groupModelId);
		modelGroup.checkOffBed();
		modelGroup.notifyScreenExtentsChange();
		return modelGroup;
	}

	/**
	 * Create a new group from models that are not yet in the project.
	 *
	 * @param modelContainers
	 * @return
	 */
	public ModelGroup createNewGroup(Set<Groupable> modelContainers) {
		checkNotAlreadyInGroup(modelContainers);

		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers);
		modelGroup.checkOffBed();
		modelGroup.notifyScreenExtentsChange();
		return modelGroup;
	}

	public void ungroup(Set<? extends ModelContainer> modelContainers) {
		List<ProjectifiableThing> ungroupedModels = new ArrayList<>();

		for (ModelContainer modelContainer : modelContainers) {
			if (modelContainer instanceof ModelGroup) {
				ModelGroup modelGroup = (ModelGroup) modelContainer;
				Set<ProjectifiableThing> modelGroups = new HashSet<>();
				modelGroups.add(modelGroup);
				removeModels(modelGroups);
				for (ModelContainer childModelContainer : modelGroup.getChildModelContainers()) {
					addModel(childModelContainer);
					childModelContainer.setBedCentreOffsetTransform();
					childModelContainer.applyGroupTransformToThis(modelGroup);
					childModelContainer.updateLastTransformedBoundsInParent();
					ungroupedModels.add(childModelContainer);
				}
				Set<ProjectifiableThing> changedModels = new HashSet<>(modelGroup.getChildModelContainers());
				fireWhenModelsTransformed(changedModels);
			}
		}
	}

	protected abstract void checkNotAlreadyInGroup(Set<Groupable> modelContainers);

	/**
	 * Create a new group from models that are not yet in the project, and add model listeners to all descendent children.
	 *
	 * @param modelContainers
	 * @return
	 */
	public abstract ModelGroup createNewGroupAndAddModelListeners(Set<Groupable> modelContainers);

	@JsonIgnore
	public void invalidate() {
		projectModified();
	}

	public GCodeGeneratorManager getGCodeGenManager() {
		return gCodeGenManager;
	}

	public void close() {
		gCodeGenManager.shutdown();
	}

	public boolean isProjectSaved() {
		return projectSaved;
	}

	public void setProjectSaved(boolean projectSaved) {
		this.projectSaved = projectSaved;
	}
}
