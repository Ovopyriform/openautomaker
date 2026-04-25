package celtech.appManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.stream.Collectors;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterSettingsOverrides;
import org.openautomaker.base.configuration.fileRepresentation.SupportType;
import org.openautomaker.base.inject.configuration.file_representation.PrinterSettingsOverridesFactory;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Head.HeadType;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.Slicer;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
import org.openautomaker.ui.inject.model.ModelGroupFactory;
import org.openautomaker.ui.project.ProjectLayoutService;
import org.openautomaker.ui.project.robox.RoboxFile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;
import celtech.modelcontrol.ProjectifiableThing;
import celtech.modelcontrol.ResizeableThreeD;
import celtech.modelcontrol.ResizeableTwoD;
import celtech.modelcontrol.RotatableThreeD;
import celtech.modelcontrol.RotatableTwoD;
import celtech.modelcontrol.ScaleableThreeD;
import celtech.modelcontrol.ScaleableTwoD;
import celtech.modelcontrol.Translateable;
import celtech.modelcontrol.TranslateableTwoD;
import jakarta.inject.Inject;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.shape.MeshView;

public class Project {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final String ASSOCIATE_WITH_EXTRUDER_NUMBER = "associateWithExtruderNumber";

	public static class ProjectLoadException extends Exception {

		private static final long serialVersionUID = -8924678649000819002L;

		public ProjectLoadException(String message) {
			super(message);
		}
	}

	protected int version = -1;

	protected Set<ProjectChangesListener> projectChangesListeners;

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

	private transient Path projectFilePath;

	// Injected, don't persist
	protected transient final ProjectsPathPreference projectsPathPreference;

	private Filament DEFAULT_FILAMENT;
	private ObjectProperty<Filament> extruder0Filament;
	private ObjectProperty<Filament> extruder1Filament;
	private javafx.beans.property.BooleanProperty modelColourChanged;
	private BooleanBinding hasInvalidMeshes;
	private ObservableList<Boolean> lastCalculatedUsedExtruders;

	private transient final FilamentContainer filamentContainer;
	private transient final ModelGroupFactory modelGroupFactory;
	private transient final SlicerPreference slicerPreference;
	private transient final ProjectLayoutService layoutService;

	private Map<ModelContainer, ChangeListener<Number>> modelExtruderNumberListener = new HashMap<>();

	@Inject
	public Project(ProjectsPathPreference projectsPathPreference,
				   SlicerPreference slicerPreference,
				   I18N i18n,
				   FilamentContainer filamentContainer,
				   ModelGroupFactory modelGroupFactory,
				   PrinterSettingsOverridesFactory printerSettingsOverridesFactory,
				   ProjectLayoutService layoutService) {

		this.projectsPathPreference = projectsPathPreference;
		this.filamentContainer = filamentContainer;
		this.modelGroupFactory = modelGroupFactory;
		this.slicerPreference = slicerPreference;
		this.layoutService = layoutService;

		topLevelThings = FXCollections.observableArrayList();
		lastModifiedDate = new SimpleObjectProperty<>();
		projectChangesListeners = new HashSet<>();

		printerSettings = printerSettingsOverridesFactory.create();
		Date now = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("-hhmmss-ddMMYY");
		projectNameProperty = new SimpleStringProperty(i18n.t("projectLoader.untitled") + formatter.format(now));
		lastModifiedDate.set(now);

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

		// Initialise content state
		lastCalculatedUsedExtruders = FXCollections.observableArrayList();
		lastCalculatedUsedExtruders.add(0, false);
		lastCalculatedUsedExtruders.add(1, false);

		hasInvalidMeshes = new BooleanBinding() {
			{
				super.bind(topLevelThings);
			}

			@Override
			protected boolean computeValue() {
				return !getModelContainersWithInvalidMesh().isEmpty();
			}
		};

		extruder0Filament = new SimpleObjectProperty<>();
		extruder1Filament = new SimpleObjectProperty<>();
		modelColourChanged = new SimpleBooleanProperty();

		DEFAULT_FILAMENT = filamentContainer.getFilamentByID("RBX-ABS-GR499");
		// Filament init requires a Printer — call initialiseExtruderFilaments(printer)
		// externally after creation.
		extruder0Filament.set(DEFAULT_FILAMENT);
		extruder1Filament.set(DEFAULT_FILAMENT);
	}

	// ── Identity ────────────────────────────────────────────────────────────────

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
		if (projectFilePath != null)
			return projectFilePath;
		return projectsPathPreference.getValue().resolve(getProjectName()).resolve(getProjectName() + RoboxFile.EXTENSION);
	}

	public final void setProjectFilePath(java.nio.file.Path path) {
		this.projectFilePath = path;
	}

	public final java.nio.file.Path getProjectFilePath() {
		return projectFilePath;
	}

	public boolean isProjectNameModified() {
		return projectNameModified;
	}

	public void setProjectNameModified(boolean projectNameModified) {
		this.projectNameModified = projectNameModified;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public void setSuppressProjectChanged(boolean suppress) {
		this.suppressProjectChanged = suppress;
	}

	public boolean isProjectSaved() {
		return projectSaved;
	}

	public void setProjectSaved(boolean projectSaved) {
		this.projectSaved = projectSaved;
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

	public final ObjectProperty<Date> getLastModifiedDate() {
		return lastModifiedDate;
	}

	public void setLastPrintJobID(String lastPrintJobID) {
		this.lastPrintJobID = lastPrintJobID;
	}

	public String getLastPrintJobID() {
		return lastPrintJobID;
	}

	// ── Settings ─────────────────────────────────────────────────────────────────

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

	// ── Filament ─────────────────────────────────────────────────────────────────

	public void setExtruder0Filament(Filament filament) {
		extruder0Filament.set(filament);
	}

	public void setExtruder1Filament(Filament filament) {
		extruder1Filament.set(filament);
	}

	public ObjectProperty<Filament> getExtruder0FilamentProperty() {
		return extruder0Filament;
	}

	public ObjectProperty<Filament> getExtruder1FilamentProperty() {
		return extruder1Filament;
	}

	public ReadOnlyBooleanProperty getModelColourChanged() {
		return modelColourChanged;
	}

	public void initialiseExtruderFilaments(Printer printer) {
		extruder0Filament.set(DEFAULT_FILAMENT);
		extruder1Filament.set(DEFAULT_FILAMENT);
		if (printer != null) {
			if (printer.reelsProperty().containsKey(0)) {
				extruder0Filament.set(filamentContainer.getFilamentByID(printer.reelsProperty().get(0).filamentIDProperty().get()));
			}
			if (printer.reelsProperty().containsKey(1)) {
				extruder1Filament.set(filamentContainer.getFilamentByID(printer.reelsProperty().get(1).filamentIDProperty().get()));
			}
		}
	}

	// ── Content ──────────────────────────────────────────────────────────────────

	public ObservableList<ProjectifiableThing> getTopLevelThings() {
		return topLevelThings;
	}

	public Set<ProjectifiableThing> getAllModels() {
		Set<ProjectifiableThing> allModelContainers = new HashSet<>();
		for (ProjectifiableThing loadedModel : topLevelThings) {
			if (loadedModel instanceof ModelContainer mc) {
				allModelContainers.add(mc);
				allModelContainers.addAll(mc.getDescendentModelContainers());
			}
		}
		return allModelContainers;
	}

	public void addModel(ProjectifiableThing projectifiableThing) {
		if (projectifiableThing instanceof ModelContainer modelContainer) {
			topLevelThings.add(modelContainer);
			addModelListeners(modelContainer);
			for (ModelContainer child : modelContainer.getChildModelContainers()) {
				addModelListeners(child);
			}
			projectModified();
			fireWhenModelAdded(modelContainer);
		}
	}

	public void removeModels(Set<ProjectifiableThing> projectifiableThings) {
		Set<ModelContainer> modelContainers = (Set) projectifiableThings;
		for (ModelContainer mc : modelContainers) {
			assert mc != null;
		}
		topLevelThings.removeAll(modelContainers);
		for (ModelContainer mc : modelContainers) {
			removeModelListeners(mc);
		}
		projectModified();
		fireWhenModelsRemoved(projectifiableThings);
	}

	public int getNumberOfProjectifiableElements() {
		return getAllModels().size();
	}

	public Set<ModelContainer> getModelContainersWithInvalidMesh() {
		Set<ModelContainer> invalid = new HashSet<>();
		getAllModels().stream().map(ModelContainer.class::cast).filter(ModelContainer::isInvalidMesh).forEach(invalid::add);
		return invalid;
	}

	public BooleanBinding hasInvalidMeshes() {
		return hasInvalidMeshes;
	}

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

	// ── Extruder usage
	// ────────────────────────────────────────────────────────────

	public List<Boolean> getPrintingExtruders(Printer printer) {
		List<Boolean> localUsedExtruders = new ArrayList<>();
		localUsedExtruders.add(false);
		localUsedExtruders.add(false);
		for (ProjectifiableThing loadedModel : topLevelThings) {
			if (loadedModel instanceof ModelContainer mc) {
				getUsedExtrudersForContainer(mc, localUsedExtruders, printer);
			}
		}
		return localUsedExtruders;
	}

	public boolean allModelsOnSameExtruder(Printer printer) {
		List<Boolean> extruders = getPrintingExtruders(printer);
		return !(extruders.get(0) && extruders.get(1));
	}

	private void getUsedExtrudersForContainer(ModelContainer modelContainer, List<Boolean> usedExtruders, Printer printer) {
		if (modelContainer instanceof ModelGroup group) {
			for (ModelContainer sub : group.getChildModelContainers()) {
				getUsedExtrudersForContainer(sub, usedExtruders, printer);
			}
		}
		else {
			if (printer != null && printer.headProperty().get() != null) {
				if (printer.headProperty().get().headTypeProperty().get() == HeadType.SINGLE_MATERIAL_HEAD) {
					usedExtruders.set(0, true);
				}
				else if (printer.headProperty().get().headTypeProperty().get() == HeadType.DUAL_MATERIAL_HEAD) {
					if (printer.extrudersProperty().get(0).isFittedProperty().get() && printer.extrudersProperty().get(1).isFittedProperty().get()) {
						usedExtruders.set(modelContainer.getAssociateWithExtruderNumberProperty().get(), true);
					}
					else if (printer.extrudersProperty().get(0).isFittedProperty().get()) {
						usedExtruders.set(0, true);
					}
					else {
						usedExtruders.set(1, true);
					}
				}
			}
			else {
				usedExtruders.set(modelContainer.getAssociateWithExtruderNumberProperty().get(), true);
			}
		}
	}

	public ObservableList<Boolean> getUsedExtruders(Printer printer) {
		List<Boolean> localUsedExtruders = getPrintingExtruders(printer);

		if (printerSettings.getPrintSupportOverride() || printerSettings.getRaftOverride() || printerSettings.getBrimOverride() > 0) {
			if (printerSettings.getPrintSupportTypeOverride() == SupportType.MATERIAL_1) {
				localUsedExtruders.set(0, true);
			}
			else if (printerSettings.getPrintSupportTypeOverride() == SupportType.MATERIAL_2 && printer != null
					&& printer.extrudersProperty().get(1).isFittedProperty().get()) {
				localUsedExtruders.set(1, true);
			}
			else if (printerSettings.getPrintSupportTypeOverride() == SupportType.AS_PROFILE) {
				if (printer != null && printer.headProperty().get() != null) {
					Head head = printer.headProperty().get();
					if (head.headTypeProperty().get().equals(HeadType.SINGLE_MATERIAL_HEAD)) {
						localUsedExtruders.set(0, true);
					}
					else {
						RoboxProfile settings = printerSettings.getSettings(head.typeCodeProperty().get(), Slicer.CURA_4);
						if (printerSettings.getPrintSupportOverride()) {
							int supportNoz = settings.getSpecificIntSettingWithDefault("supportNozzle", 0);
							int supportInterfaceNoz = settings.getSpecificIntSettingWithDefault("supportInterfaceNozzle", 0);
							localUsedExtruders.set(1 - supportNoz, true);
							localUsedExtruders.set(1 - supportInterfaceNoz, true);
						}
						if (printerSettings.getRaftOverride() || printerSettings.getBrimOverride() > 0) {
							int raftBrimNoz = settings.getSpecificIntSettingWithDefault("raftBrimNozzle", 0);
							localUsedExtruders.set(1 - raftBrimNoz, true);
						}
					}
				}
			}
		}

		lastCalculatedUsedExtruders.setAll(localUsedExtruders);
		return lastCalculatedUsedExtruders;
	}

	public void setAssociatedExtruder(Set<ModelContainer> modelContainers, boolean useExtruder0) {
		for (ModelContainer mc : modelContainers) {
			mc.setUseExtruder0(useExtruder0);
		}

		boolean usingDifferentExtruders = false;
		int lastExtruder = -1;
		for (ProjectifiableThing thing : getAllModels()) {
			int thisExtruder = ((ModelContainer) thing).getAssociateWithExtruderNumberProperty().get();
			if (lastExtruder >= 0 && lastExtruder != thisExtruder) {
				usingDifferentExtruders = true;
				break;
			}
			lastExtruder = thisExtruder;
		}

		if (!usingDifferentExtruders) {
			if (slicerPreference.getValue() == Slicer.CURA_4) {
				printerSettings.getPrintSupportTypeOverrideProperty().set(SupportType.AS_PROFILE);
			}
			else {
				printerSettings.getPrintSupportTypeOverrideProperty().set(useExtruder0 ? SupportType.MATERIAL_1 : SupportType.MATERIAL_2);
			}
			fireWhenPrinterSettingsChanged(printerSettings);
		}
		projectModified();
	}

	// ── Grouping
	// ──────────────────────────────────────────────────────────────────

	public Map<Integer, Set<Integer>> getGroupStructure() {
		Map<Integer, Set<Integer>> groupStructure = new HashMap<>();
		for (ModelContainer mc : getModelsHoldingModels()) {
			mc.addGroupStructure(groupStructure);
		}
		return groupStructure;
	}

	public Map<Integer, ItemState> getGroupState() {
		Map<Integer, ItemState> groupState = new HashMap<>();
		for (ModelContainer mc : getModelsHoldingModels()) {
			groupState.put(mc.getModelId(), mc.getState());
		}
		return groupState;
	}

	public ModelGroup group(Set<Groupable> modelContainers) {
		Set<ProjectifiableThing> things = (Set) modelContainers;
		removeModels(things);
		ModelGroup modelGroup = createNewGroup(modelContainers);
		addModel(modelGroup);
		return modelGroup;
	}

	public ModelGroup group(Set<Groupable> modelContainers, int groupModelId) {
		Set<ProjectifiableThing> things = (Set) modelContainers;
		removeModels(things);
		ModelGroup modelGroup = createNewGroup(modelContainers, groupModelId);
		addModel(modelGroup);
		return modelGroup;
	}

	public ModelGroup createNewGroup(Set<Groupable> modelContainers) {
		checkNotAlreadyInGroup(modelContainers);
		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers);
		modelGroup.checkOffBed();
		modelGroup.notifyScreenExtentsChange();
		return modelGroup;
	}

	public ModelGroup createNewGroup(Set<Groupable> modelContainers, int groupModelId) {
		checkNotAlreadyInGroup(modelContainers);
		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers, groupModelId);
		modelGroup.checkOffBed();
		modelGroup.notifyScreenExtentsChange();
		return modelGroup;
	}

	public ModelGroup createNewGroupAndAddModelListeners(Set<Groupable> modelContainers) {
		checkNotAlreadyInGroup(modelContainers);
		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers);
		addModelListeners(modelGroup);
		for (ModelContainer child : modelGroup.getDescendentModelContainers()) {
			addModelListeners(child);
		}
		modelGroup.checkOffBed();
		return modelGroup;
	}

	public void ungroup(Set<? extends ModelContainer> modelContainers) {
		for (ModelContainer mc : modelContainers) {
			if (mc instanceof ModelGroup group) {
				Set<ProjectifiableThing> groupSet = new HashSet<>();
				groupSet.add(group);
				removeModels(groupSet);
				for (ModelContainer child : group.getChildModelContainers()) {
					addModel(child);
					child.setBedCentreOffsetTransform();
					child.applyGroupTransformToThis(group);
					child.updateLastTransformedBoundsInParent();
				}
				fireWhenModelsTransformed(new HashSet<>(group.getChildModelContainers()));
			}
		}
	}

	private void checkNotAlreadyInGroup(Set<Groupable> modelContainers) {
		Set<ModelContainer> inGroups = getDescendentModelsInAllGroups();
		for (Groupable model : modelContainers) {
			if (inGroups.contains(model)) {
				throw new RuntimeException("Model " + model + " is already in a group");
			}
		}
	}

	public void recreateGroups(Map<Integer, Set<Integer>> groupStructure, Map<Integer, ItemState> groupStates) throws ProjectLoadException {
		int numNewGroups;
		do {
			numNewGroups = makeNewGroups(groupStructure, groupStates);
		} while (numNewGroups > 0);
	}

	private int makeNewGroups(Map<Integer, Set<Integer>> groupStructure, Map<Integer, ItemState> groupStates) throws ProjectLoadException {
		int numGroups = 0;
		for (Map.Entry<Integer, Set<Integer>> entry : groupStructure.entrySet()) {
			if (allModelsInstantiated(entry.getValue())) {
				Set<Groupable> modelContainers = getModelContainersOfIds(entry.getValue()).stream().filter(m -> m instanceof Groupable)
						.collect(Collectors.toSet());
				ModelGroup group = group(modelContainers, entry.getKey());
				group.setState(groupStates.get(group.getModelId()));
				group.checkOffBed();
				numGroups++;
			}
		}
		return numGroups;
	}

	private boolean allModelsInstantiated(Set<Integer> modelIds) {
		for (int modelId : modelIds) {
			boolean found = false;
			for (ProjectifiableThing mc : topLevelThings) {
				if (mc.getModelId() == modelId) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	public Set<ModelContainer> getModelContainersOfIds(Set<Integer> modelIds) throws ProjectLoadException {
		Set<ModelContainer> result = new HashSet<>();
		for (int modelId : modelIds) {
			Optional<ModelContainer> mc = getModelContainerOfModelId(modelId);
			if (mc.isPresent()) {
				result.add(mc.get());
			}
			else {
				throw new ProjectLoadException("unexpected model id when recreating groups");
			}
		}
		return result;
	}

	private Optional<ModelContainer> getModelContainerOfModelId(int modelId) {
		for (ProjectifiableThing mc : topLevelThings) {
			if (mc.getModelId() == modelId)
				return Optional.of((ModelContainer) mc);
		}
		return Optional.empty();
	}

	public java.nio.file.Path getProjectLocation() {
		return projectsPathPreference.getValue().resolve(projectNameProperty.get());
	}

	// ── Listeners
	// ─────────────────────────────────────────────────────────────────

	public final void addProjectChangesListener(ProjectChangesListener listener) {
		projectChangesListeners.add(listener);
	}

	public final void removeProjectChangesListener(ProjectChangesListener listener) {
		projectChangesListeners.remove(listener);
	}

	private void addModelListeners(ModelContainer modelContainer) {
		if (!(modelContainer instanceof ModelGroup) && !modelExtruderNumberListener.containsKey(modelContainer)) {
			ChangeListener<Number> changeListener = (observable, oldValue, newValue) -> {
				fireWhenModelChanged(modelContainer, ASSOCIATE_WITH_EXTRUDER_NUMBER);
				modelColourChanged.set(!modelColourChanged.get());
			};
			modelExtruderNumberListener.put(modelContainer, changeListener);
			modelContainer.getAssociateWithExtruderNumberProperty().addListener(changeListener);
		}
	}

	public void removeModelListeners(ModelContainer modelContainer) {
		if (!(modelContainer instanceof ModelGroup)) {
			modelContainer.getAssociateWithExtruderNumberProperty().removeListener(modelExtruderNumberListener.get(modelContainer));
			modelExtruderNumberListener.remove(modelContainer);
		}
	}

	// ── Transforms
	// ────────────────────────────────────────────────────────────────

	public final void scaleXYZRatioSelection(Set<ScaleableThreeD> things, double ratio) {
		for (ScaleableThreeD t : things) {
			t.setXScale(t.getXScale() * ratio, true);
			t.setYScale(t.getYScale() * ratio, true);
			t.setZScale(t.getZScale() * ratio, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) things);
	}

	public final void scaleXYRatioSelection(Set<ScaleableTwoD> things, double ratio) {
		for (ScaleableTwoD t : things) {
			t.setXScale(t.getXScale() * ratio, true);
			t.setYScale(t.getYScale() * ratio, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) things);
	}

	public final void scaleXModels(Set<ScaleableTwoD> things, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			assert things.size() == 1;
			ScaleableTwoD t = things.iterator().next();
			double ratio = newScale / t.getXScale();
			if (t instanceof ScaleableThreeD)
				scaleXYZRatioSelection((Set) things, ratio);
			else
				scaleXYRatioSelection(things, ratio);
		}
		else {
			for (ScaleableTwoD t : things)
				t.setXScale(newScale, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) things);
	}

	public final void scaleYModels(Set<ScaleableTwoD> things, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			assert things.size() == 1;
			ScaleableTwoD t = things.iterator().next();
			double ratio = newScale / t.getYScale();
			if (t instanceof ScaleableThreeD)
				scaleXYZRatioSelection((Set) things, ratio);
			else
				scaleXYRatioSelection(things, ratio);
		}
		else {
			for (ScaleableTwoD t : things)
				t.setYScale(newScale, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) things);
	}

	public final void scaleZModels(Set<ScaleableThreeD> things, double newScale, boolean preserveAspectRatio) {
		if (preserveAspectRatio) {
			assert things.size() == 1;
			ScaleableThreeD t = things.iterator().next();
			double ratio = newScale / t.getZScale();
			scaleXYZRatioSelection(things, ratio);
		}
		else {
			for (ScaleableThreeD t : things)
				t.setZScale(newScale, true);
		}
		projectModified();
		fireWhenModelsTransformed((Set) things);
	}

	public void translateModelsBy(Set<TranslateableTwoD> modelContainers, double x, double y) {
		for (TranslateableTwoD m : modelContainers)
			m.translateBy(x, y);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsTo(Set<TranslateableTwoD> modelContainers, double x, double y) {
		for (TranslateableTwoD m : modelContainers)
			m.translateTo(x, y);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsXTo(Set<TranslateableTwoD> modelContainers, double x) {
		for (TranslateableTwoD m : modelContainers)
			m.translateXTo(x);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void translateModelsDepthPositionTo(Set<Translateable> modelContainers, double z) {
		for (Translateable m : modelContainers)
			m.translateDepthPositionTo(z);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsDepth(Set<ResizeableThreeD> modelContainers, double depth) {
		for (ResizeableThreeD m : modelContainers)
			m.resizeDepth(depth);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsHeight(Set<ResizeableTwoD> modelContainers, double height) {
		for (ResizeableTwoD m : modelContainers)
			m.resizeHeight(height);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void resizeModelsWidth(Set<ResizeableTwoD> modelContainers, double width) {
		for (ResizeableTwoD m : modelContainers)
			m.resizeWidth(width);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void rotateLeanModels(Set<RotatableThreeD> modelContainers, double rotation) {
		for (RotatableThreeD m : modelContainers)
			m.setRotationLean(rotation);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void rotateTwistModels(Set<RotatableThreeD> modelContainers, double rotation) {
		for (RotatableThreeD m : modelContainers)
			m.setRotationTwist(rotation);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void rotateTurnModels(Set<RotatableTwoD> modelContainers, double rotation) {
		for (RotatableTwoD m : modelContainers)
			m.setRotationTurn(rotation);
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void dropToBed(Set<ModelContainer> modelContainers) {
		for (ModelContainer m : modelContainers) {
			m.dropToBed();
			m.checkOffBed();
		}
		projectModified();
		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void snapToGround(ModelContainer modelContainer, MeshView pickedMesh, int faceNumber) {
		modelContainer.snapToGround(pickedMesh, faceNumber);
		projectModified();
		Set<ModelContainer> set = new HashSet<>();
		set.add(modelContainer);
		fireWhenModelsTransformed((Set) set);
	}

	// ── Layout
	// ────────────────────────────────────────────────────────────────────

	public void autoLayout() {
		layoutService.autoLayout(this);
		projectModified();
		fireWhenAutoLaidOut();
	}

	public void autoLayout(List<ProjectifiableThing> thingsToLayout) {
		layoutService.autoLayout(this, thingsToLayout);
		projectModified();
		fireWhenAutoLaidOut();
	}

	// ── Misc
	// ──────────────────────────────────────────────────────────────────────

	@JsonIgnore
	public void invalidate() {
		projectModified();
	}

	public void close() {
		// TODO: caller (ProjectTab) should shutdown GCodeGeneratorManager via
		// ProjectGUIState
	}

	@Override
	public String toString() {
		return projectNameProperty.get();
	}

	protected final void projectModified() {
		if (!suppressProjectChanged) {
			projectSaved = false;
			lastPrintJobID = "";
			lastModifiedDate.set(new Date());
		}
	}

	// Looks like this is no longer needed.  Keep for now.
	private Set<ModelContainer> getModelsHoldingMeshViews() {
		Set<ModelContainer> result = new HashSet<>();
		for (ProjectifiableThing t : topLevelThings) {
			result.addAll(((ModelContainer) t).getModelsHoldingMeshViews());
		}
		return result;
	}

	private Set<ModelContainer> getModelsHoldingModels() {
		Set<ModelContainer> result = new HashSet<>();
		for (ProjectifiableThing t : topLevelThings) {
			result.addAll(((ModelContainer) t).getModelsHoldingModels());
		}
		return result;
	}

	private Set<ModelContainer> getDescendentModelsInAllGroups() {
		Set<ModelContainer> result = new HashSet<>();
		for (ProjectifiableThing t : topLevelThings) {
			if (t instanceof ModelGroup group) {
				result.addAll(getDescendentModelsInGroup(group));
			}
		}
		return result;
	}

	private Set<ModelContainer> getDescendentModelsInGroup(ModelGroup group) {
		Set<ModelContainer> result = new HashSet<>();
		for (ModelContainer mc : group.getChildModelContainers()) {
			if (mc instanceof ModelGroup subGroup)
				result.addAll(getDescendentModelsInGroup(subGroup));
			else
				result.add(mc);
		}
		return result;
	}


	//TODO: This should be reimplemented to use PropertyChangeSupport and fire PropertyChangeEvents instead of having custom listeners and firing methods for each type of change, but this will do for now.
	// ── Fire events
	// ────────────────────────────────────────────────────────────────

	protected void fireWhenModelsTransformed(Set<ProjectifiableThing> things) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenModelsTransformed(things);
	}

	protected void fireWhenPrinterSettingsChanged(PrinterSettingsOverrides settings) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenPrinterSettingsChanged(settings);
	}

	protected void fireWhenTimelapseSettingsChanged(TimelapseSettingsData settings) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenTimelapseSettingsChanged(settings);
	}

	private void fireWhenModelAdded(ModelContainer mc) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenModelAdded(mc);
	}

	private void fireWhenModelsRemoved(Set<ProjectifiableThing> things) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenModelsRemoved(things);
	}

	private void fireWhenAutoLaidOut() {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenAutoLaidOut();
	}

	private void fireWhenModelChanged(ModelContainer mc, String propertyName) {
		for (ProjectChangesListener l : projectChangesListeners)
			l.whenModelChanged(mc, propertyName);
	}

	// ── ProjectChangesListener
	// ────────────────────────────────────────────────────

	public interface ProjectChangesListener {
		void whenModelAdded(ProjectifiableThing projectifiableThing);

		void whenModelsRemoved(Set<ProjectifiableThing> projectifiableThings);

		void whenAutoLaidOut();

		void whenModelsTransformed(Set<ProjectifiableThing> projectifiableThings);

		void whenModelChanged(ProjectifiableThing modelContainer, String propertyName);

		void whenPrinterSettingsChanged(PrinterSettingsOverrides printerSettings);

		void whenTimelapseSettingsChanged(TimelapseSettingsData timelapseSettings);
	}
}
