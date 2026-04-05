package celtech.appManager;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.datafileaccessors.CameraProfileContainer;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterSettingsOverrides;
import org.openautomaker.base.configuration.fileRepresentation.SupportType;
import org.openautomaker.base.device.CameraManager;
import org.openautomaker.base.inject.configuration.file_representation.PrinterSettingsOverridesFactory;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Head.HeadType;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.utils.RectangularBounds;
import org.openautomaker.base.utils.Math.packing.core.Bin;
import org.openautomaker.base.utils.Math.packing.core.BinPacking;
import org.openautomaker.base.utils.Math.packing.primitives.MArea;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.Slicer;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.inject.model.ModelGroupFactory;
import org.openautomaker.ui.state.SelectedPrinter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import celtech.configuration.ApplicationConfiguration;
import celtech.configuration.fileRepresentation.ModelContainerProjectFile;
import celtech.configuration.fileRepresentation.ProjectFile;
import celtech.modelcontrol.Groupable;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ModelGroup;
import celtech.modelcontrol.ProjectifiableThing;
import celtech.modelcontrol.RotatableThreeD;
import celtech.modelcontrol.RotatableTwoD;
import celtech.utils.threed.MeshUtils;
import jakarta.inject.Inject;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

// TODO: Stores the state of the project in *ProjectFile and *ProjectContainer.  Needs to be simplified 
public class ModelContainerProject extends Project {

	private int version = -1;

	private Filament DEFAULT_FILAMENT;

	private static final String ASSOCIATE_WITH_EXTRUDER_NUMBER = "associateWithExtruderNumber";

	private static final Logger LOGGER = LogManager.getLogger();

	private ObjectProperty<Filament> extruder0Filament;
	private ObjectProperty<Filament> extruder1Filament;
	private BooleanProperty modelColourChanged;
	private BooleanBinding hasInvalidMeshes;

	//Changed to make this list always include both extruders
	private ObservableList<Boolean> lastCalculatedUsedExtruders;

	// Injected params.  Don't persist.
	private transient final SelectedPrinter selectedPrinter;
	private transient final FilamentContainer filamentContainer;
	private transient final ModelGroupFactory modelGroupFactory;
	private transient final BinPacking binPacking;
	private transient final SlicerPreference slicerPreference;
	private transient final PrinterContainer printerContainer;

	@Inject
	protected ModelContainerProject(
			ProjectsPathPreference projectsPathPreference,
			SlicerPreference slicerPreference,
			I18N i18n,
			CameraManager cameraManager,
			GCodeGeneratorManager gCodeGeneratorManager,
			SelectedPrinter selectedPrinter,
			FilamentContainer filamentContainer,
			ModelGroupFactory modelGroupFactory,
			CameraProfileContainer cameraProfileContainer,
			BinPacking binPacking,
			PrinterSettingsOverridesFactory printerSettingsOverridesFactory,
			PrinterContainer printerContainer) {

		super(projectsPathPreference, slicerPreference, i18n, cameraManager, gCodeGeneratorManager, modelGroupFactory, cameraProfileContainer, printerSettingsOverridesFactory);

		this.selectedPrinter = selectedPrinter;
		this.filamentContainer = filamentContainer;
		this.modelGroupFactory = modelGroupFactory;
		this.binPacking = binPacking;
		this.slicerPreference = slicerPreference;
		this.printerContainer = printerContainer;

		//Perhaps jsut move this into the constructor
		initialise();
	}

	@Override
	protected void initialise() {
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
		//filamentContainer = filamentContainer;

		DEFAULT_FILAMENT = filamentContainer.getFilamentByID("RBX-ABS-GR499");

		initialiseExtruderFilaments();
	}

	public Set<ModelContainer> getModelContainersWithInvalidMesh() {
		Set<ModelContainer> invalidModelContainers = new HashSet<>();
		getAllModels().stream()
				.map(ModelContainer.class::cast)
				.filter((modelContainer) -> (modelContainer.isInvalidMesh()))
				.forEach((modelContainer) -> {
					invalidModelContainers.add(modelContainer);
				});

		return invalidModelContainers;
	}

	public BooleanBinding hasInvalidMeshes() {
		return hasInvalidMeshes;
	}

	//TODO: State of the project held in two places, the projectFile and the container.  Simplify?
	@Override
	protected void load(ProjectFile projectFile, Path filePath) throws ProjectLoadException {

		if (!(projectFile instanceof ModelContainerProjectFile))
			throw new ProjectLoadException("Incorrect file type provided");

		ModelContainerProjectFile mcProjectFile = (ModelContainerProjectFile) projectFile;

		suppressProjectChanged = true;

		try {
			version = projectFile.getVersion();

			projectNameProperty.set(projectFile.getProjectName());
			lastModifiedDate.set(projectFile.getLastModifiedDate());
			lastPrintJobID = projectFile.getLastPrintJobID();
			projectNameModified = projectFile.isProjectNameModified();

			String filamentID0 = mcProjectFile.getExtruder0FilamentID();
			String filamentID1 = mcProjectFile.getExtruder1FilamentID();
			if (!filamentID0.equals("NULL")) {
				Filament filament0 = filamentContainer.getFilamentByID(filamentID0);
				if (filament0 != null) {
					extruder0Filament.set(filament0);
				}
			}
			if (!filamentID1.equals("NULL")) {
				Filament filament1 = filamentContainer.getFilamentByID(filamentID1);
				if (filament1 != null) {
					extruder1Filament.set(filament1);
				}
			}

			printerSettings.setSettingsName(mcProjectFile.getSettingsName());
			printerSettings.setPrintQuality(mcProjectFile.getPrintQuality());
			printerSettings.setBrimOverride(mcProjectFile.getBrimOverride());
			printerSettings.setFillDensityOverride(mcProjectFile.getFillDensityOverride());
			printerSettings.setFillDensityChangedByUser(mcProjectFile.isFillDensityOverridenByUser());
			printerSettings.setPrintSupportOverride(mcProjectFile.getPrintSupportOverride());
			printerSettings.setPrintSupportTypeOverride(mcProjectFile.getPrintSupportTypeOverride());
			printerSettings.setRaftOverride(mcProjectFile.getPrintRaft());
			printerSettings.setSpiralPrintOverride(mcProjectFile.getSpiralPrint());

			loadTimelapseSettings(mcProjectFile);

			loadModels(filePath);

			recreateGroups(mcProjectFile.getGroupStructure(), mcProjectFile.getGroupState());

		}
		catch (IOException ex) {
			LOGGER.error("Failed to load project " + filePath, ex);
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error("Failed to load project " + filePath, ex);
		}
		finally {
			suppressProjectChanged = false;
		}
	}

	private void loadModels(Path filePath) throws IOException, ClassNotFoundException {

		// Legacy in case we've been passed a path without the project file name
		if (!filePath.toString().endsWith(ApplicationConfiguration.projectFileExtension))
			filePath = filePath.resolveSibling(filePath.getFileName().toString() + ApplicationConfiguration.projectFileExtension);

		//Change the type of the file to the model type
		filePath = filePath.resolveSibling(
				filePath.getFileName().toString()
						.replace(ApplicationConfiguration.projectFileExtension, ApplicationConfiguration.projectModelsFileExtension));

		FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
		BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
		ObjectInputStream modelsInput = new ObjectInputStream(bufferedInputStream);
		int numModels = modelsInput.readInt();

		for (int i = 0; i < numModels; i++) {
			ModelContainer modelContainer = (ModelContainer) modelsInput.readObject();
			GuiceContext.get().injectMembers(modelContainer);
			Optional<MeshUtils.MeshError> error = MeshUtils.validate((TriangleMesh) modelContainer.getMeshView().getMesh());

			if (error.isPresent()) {
				modelContainer.setIsInvalidMesh(true);
				LOGGER.debug("Model load - " + error.get().name());
			}
			addModel(modelContainer);
		}

		modelsInput.close();
		bufferedInputStream.close();
		fileInputStream.close();

	}

	//TODO: Also odd.  Put in ProjectPersistance?
	public void saveProject(ModelContainerProject project) {
		Path projectPath = projectsPathPreference.getValue().resolve(project.getProjectName());
		project.save(projectPath);
	}

	public Path getProjectLocation() {
		return projectsPathPreference.getValue().resolve(projectNameProperty.get());
	}

	private void saveModels(Path path) throws IOException {

		try (ObjectOutputStream modelsOutput = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
			Set<ModelContainer> modelsHoldingMeshViews = getModelsHoldingMeshViews();

			modelsOutput.writeInt(modelsHoldingMeshViews.size());
			for (ModelContainer modelsHoldingMeshView : modelsHoldingMeshViews) {
				modelsOutput.writeObject(modelsHoldingMeshView);
			}
		}
		catch (FileNotFoundException e) {
			LOGGER.warn("Cannot create " + path.toString());
		}

	}

	@Override
	protected void save(Path filePath) {
		if (topLevelThings.size() == 0) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("No top level things.  Returning");

			return;
		}

		//Legacy incase we're not given the project file name
		if (!filePath.toString().endsWith(ApplicationConfiguration.projectFileExtension))
			filePath = filePath.resolveSibling(filePath.getFileName().toString() + ApplicationConfiguration.projectFileExtension);

		Path modelsPath = filePath.resolveSibling(filePath.getFileName().toString().replace(ApplicationConfiguration.projectFileExtension, ApplicationConfiguration.projectModelsFileExtension));

		LOGGER.info(filePath.toString());
		LOGGER.info(modelsPath.toString());

		try {
			ProjectFile projectFile = new ModelContainerProjectFile();
			projectFile.populateFromProject(this);

			File file = filePath.toFile();

			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			mapper.writeValue(file, projectFile);
			saveModels(modelsPath);
		}
		catch (FileNotFoundException ex) {
			LOGGER.error("Failed to save project state", ex);
		}
		catch (IOException ex) {
			LOGGER.error("Couldn't write project state to file for project " + projectNameProperty.get(), ex);
		}

	}

	public List<Boolean> getPrintingExtruders(Printer printer) {
		List<Boolean> localUsedExtruders = new ArrayList<>();
		localUsedExtruders.add(false);
		localUsedExtruders.add(false);

		for (ProjectifiableThing loadedModel : topLevelThings) {
			if (loadedModel instanceof ModelContainer) {
				getUsedExtruders((ModelContainer) loadedModel, localUsedExtruders, printer);
			}
		}

		return localUsedExtruders;
	}

	/**
	 * Return true if all objects are on the same extruder, else return false.
	 */
	public boolean allModelsOnSameExtruder(Printer printer) {
		List<Boolean> extruders = getPrintingExtruders(printer);
		return !(extruders.get(0) && extruders.get(1));
	}

	private void getUsedExtruders(ModelContainer modelContainer, List<Boolean> usedExtruders, Printer printer) {
		if (modelContainer instanceof ModelGroup) {
			for (ModelContainer subModel : ((ModelGroup) modelContainer).getChildModelContainers()) {
				getUsedExtruders(subModel, usedExtruders, printer);
			}
		}
		else {
			if (printer != null && printer.headProperty().get() != null) {
				//Single material heads can only use 1 material
				if (printer.headProperty().get().headTypeProperty().get() == HeadType.SINGLE_MATERIAL_HEAD) {
					usedExtruders.set(0, true);
				}
				else if (printer.headProperty().get().headTypeProperty().get() == HeadType.DUAL_MATERIAL_HEAD) {
					if (printer.extrudersProperty().get(0).isFittedProperty().get() && printer.extrudersProperty().get(1).isFittedProperty().get()) {
						usedExtruders.set(modelContainer.getAssociateWithExtruderNumberProperty().get(), true);
					}
					else {
						//Yikes - DM head with less than 2 extruders...
						if (printer.extrudersProperty().get(0).isFittedProperty().get()) {
							//We have to use extruder 0
							usedExtruders.set(0, true);
						}
						else {
							//We have to use extruder 1
							usedExtruders.set(1, true);
						}
					}
				}
			}
			else {
				usedExtruders.set(modelContainer.getAssociateWithExtruderNumberProperty().get(), true);
			}
		}
	}

	/**
	 * Return which extruders are used by the project, as a set of the extruder numbers.
	 *
	 * @param printer
	 * @return
	 */
	@Override
	public ObservableList<Boolean> getUsedExtruders(Printer printer) {
		List<Boolean> localUsedExtruders = getPrintingExtruders(printer);

		if (printerSettings.getPrintSupportOverride() || printerSettings.getRaftOverride() || printerSettings.getBrimOverride() > 0) {
			if (printerSettings.getPrintSupportTypeOverride() == SupportType.MATERIAL_1) {
				if (!localUsedExtruders.get(0)) {
					localUsedExtruders.set(0, true);
				}
			}
			else if (printerSettings.getPrintSupportTypeOverride() == SupportType.MATERIAL_2 && printer != null && printer.extrudersProperty().get(1).isFittedProperty().get()) {
				if (!localUsedExtruders.get(1)) {
					localUsedExtruders.set(1, true);
				}
			}
			else if (printerSettings.getPrintSupportTypeOverride() == SupportType.AS_PROFILE) {
				if (printer != null && printer.headProperty().get() != null) {

					Head head = printer.headProperty().get();

					if (head.headTypeProperty().get().equals(HeadType.SINGLE_MATERIAL_HEAD)) {
						if (!localUsedExtruders.get(0)) {
							localUsedExtruders.set(0, true);
						}
					}
					else {
						// Here we must check the actual profile settings for the support nozzles and
						// determine manually which extruders are being used. It's not a neat solution.
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

	/**
	 * Return all ModelGroups and ModelContainers within the project.
	 *
	 * @return
	 */
	@Override
	public Set<ProjectifiableThing> getAllModels() {
		Set<ProjectifiableThing> allModelContainers = new HashSet<>();
		for (ProjectifiableThing loadedModel : topLevelThings) {
			if (loadedModel instanceof ModelContainer) {
				allModelContainers.add(loadedModel);
				allModelContainers.addAll(((ModelContainer) loadedModel).getDescendentModelContainers());
			}
		}
		return allModelContainers;
	}

	@Override
	public String toString() {
		return projectNameProperty.get();
	}

	@Override
	public void setLastPrintJobID(String printJobID) {
		lastPrintJobID = printJobID;
	}

	@Override
	public String getLastPrintJobID() {
		return lastPrintJobID;
	}

	public ReadOnlyBooleanProperty getModelColourChanged() {
		return modelColourChanged;
	}

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

	/**
	 * For new projects this should be called to initialise the extruder filaments according to the currently selected printer.
	 */
	private void initialiseExtruderFilaments() {
		// set defaults in case of no printer or reel
		extruder0Filament.set(DEFAULT_FILAMENT);
		extruder1Filament.set(DEFAULT_FILAMENT);

		Printer printer = selectedPrinter.get();
		if (printer != null) {
			if (printer.reelsProperty().containsKey(0)) {
				String filamentID = printer.reelsProperty().get(0).filamentIDProperty().get();
				extruder0Filament.set(filamentContainer.getFilamentByID(filamentID));
			}
			if (printer.reelsProperty().containsKey(1)) {
				String filamentID = printer.reelsProperty().get(1).filamentIDProperty().get();
				extruder1Filament.set(filamentContainer.getFilamentByID(filamentID));
			}
		}
	}

	@Override
	public void addModel(ProjectifiableThing projectifiableThing) {
		if (projectifiableThing instanceof ModelContainer) {
			ModelContainer modelContainer = (ModelContainer) projectifiableThing;
			topLevelThings.add(modelContainer);
			addModelListeners(modelContainer);
			for (ModelContainer childModelContainer : modelContainer.getChildModelContainers()) {
				addModelListeners(childModelContainer);
			}
			projectModified();
			fireWhenModelAdded(modelContainer);
		}
	}

	private void fireWhenModelAdded(ModelContainer modelContainer) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenModelAdded(modelContainer);
		}
	}

	@Override
	protected void fireWhenPrinterSettingsChanged(PrinterSettingsOverrides printerSettings) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenPrinterSettingsChanged(printerSettings);
		}
	}

	@Override
	protected void fireWhenTimelapseSettingsChanged(TimelapseSettingsData timelapseSettings) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenTimelapseSettingsChanged(timelapseSettings);
		}
	}

	@Override
	public void removeModels(Set<ProjectifiableThing> projectifiableThings) {
		Set<ModelContainer> modelContainers = (Set) projectifiableThings;

		for (ModelContainer modelContainer : modelContainers) {
			assert modelContainer != null;
		}

		topLevelThings.removeAll(modelContainers);

		for (ModelContainer modelContainer : modelContainers) {
			removeModelListeners(modelContainer);
		}
		projectModified();
		fireWhenModelsRemoved(projectifiableThings);
	}

	private void fireWhenModelsRemoved(Set<ProjectifiableThing> modelContainers) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenModelsRemoved(modelContainers);
		}
	}

	private Map<ModelContainer, ChangeListener<Number>> modelExtruderNumberListener = new HashMap<>();

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

	private Set<ModelContainer> getModelsHoldingMeshViews() {
		Set<ModelContainer> modelsHoldingMeshViews = new HashSet<>();
		for (ProjectifiableThing model : topLevelThings) {
			modelsHoldingMeshViews.addAll(((ModelContainer) model).getModelsHoldingMeshViews());
		}
		return modelsHoldingMeshViews;
	}

	private Set<ModelContainer> getModelsHoldingModels() {
		Set<ModelContainer> modelsHoldingMeshViews = new HashSet<>();
		for (ProjectifiableThing model : topLevelThings) {
			modelsHoldingMeshViews.addAll(((ModelContainer) model).getModelsHoldingModels());
		}
		return modelsHoldingMeshViews;
	}

	/**
	 * Return the set of those ModelContainers which are in any group.
	 */
	private Set<ModelContainer> getDescendentModelsInAllGroups() {
		Set<ModelContainer> modelsInGroups = new HashSet<>();
		for (ProjectifiableThing model : topLevelThings) {
			if (model instanceof ModelGroup) {
				modelsInGroups.addAll(getDescendentModelsInGroup((ModelGroup) model));
			}
		}
		return modelsInGroups;
	}

	/**
	 * Return the set of those ModelContainers which are in any group descending from the given group.
	 */
	private Set<ModelContainer> getDescendentModelsInGroup(ModelGroup modelGroup) {
		Set<ModelContainer> modelsInGroups = new HashSet<>();
		for (ModelContainer model : modelGroup.getChildModelContainers()) {
			if (model instanceof ModelGroup) {
				modelsInGroups.addAll(getDescendentModelsInGroup((ModelGroup) model));
			}
			else {
				modelsInGroups.add(model);
			}
		}
		return modelsInGroups;
	}

	/**
	 * Return a Map of child_model_id -> parent_model_id for all model:group and group:grou
	 *
	 * @return p relationships.
	 */
	public Map<Integer, Set<Integer>> getGroupStructure() {
		Map<Integer, Set<Integer>> groupStructure = new HashMap<>();
		for (ModelContainer modelContainer : getModelsHoldingModels()) {
			modelContainer.addGroupStructure(groupStructure);
		}
		return groupStructure;
	}

	/**
	 * Return a Map of model_id -> state for all models holding models (ie groups).
	 *
	 * @return
	 */
	public Map<Integer, ItemState> getGroupState() {
		Map<Integer, ItemState> groupState = new HashMap<>();
		for (ModelContainer modelContainer : getModelsHoldingModels()) {
			groupState.put(modelContainer.getModelId(), modelContainer.getState());
		}
		return groupState;
	}

	/**
	 * Using the group function, reapply the groupings as given by the groupStructure. The first groups to be created must be those containing only non-groups, and then each level of the group hierarchy.
	 * <p>
	 * First create new groups where all children are already instantiated. Then repeat until no new groups are created.
	 * </p>
	 *
	 * @param groupStructure
	 * @param groupStates
	 * @throws celtech.appManager.ModelContainerProject.ProjectLoadException
	 */
	public void recreateGroups(Map<Integer, Set<Integer>> groupStructure, Map<Integer, ItemState> groupStates) throws ProjectLoadException {
		int numNewGroups;
		do {
			numNewGroups = makeNewGroups(groupStructure, groupStates);
		} while (numNewGroups > 0);
	}

	/**
	 * Create groups where all the children are already instantiated, based on the structure and state given in the parameters.
	 *
	 * @return the number of groups created
	 */
	private int makeNewGroups(Map<Integer, Set<Integer>> groupStructure, Map<Integer, ItemState> groupStates) throws ProjectLoadException {
		int numGroups = 0;
		for (Map.Entry<Integer, Set<Integer>> entry : groupStructure.entrySet()) {
			if (allModelsInstantiated(entry.getValue())) {
				Set<Groupable> modelContainers = getModelContainersOfIds(entry.getValue()).stream().filter((model) -> (model instanceof Groupable)).collect(Collectors.toSet());
				int groupModelId = entry.getKey();
				ModelGroup group = group(modelContainers, groupModelId);
				recreateGroupState(group, groupStates);
				numGroups++;
			}
		}
		return numGroups;
	}

	/**
	 * Return true if loadedModels contains models for all the given modelIds, else return false.
	 */
	private boolean allModelsInstantiated(Set<Integer> modelIds) {
		for (int modelId : modelIds) {
			boolean modelFound = false;
			for (ProjectifiableThing modelContainer : topLevelThings) {
				if (modelContainer.getModelId() == modelId) {
					modelFound = true;
					break;
				}

			}
			if (!modelFound) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return the set of models for the given set of modelIds.
	 *
	 * @param modelIds
	 * @return
	 * @throws celtech.appManager.ModelContainerProject.ProjectLoadException
	 */
	public Set<ModelContainer> getModelContainersOfIds(Set<Integer> modelIds) throws ProjectLoadException {
		Set<ModelContainer> modelContainers = new HashSet<>();
		for (int modelId : modelIds) {
			Optional<ModelContainer> modelContainer = getModelContainerOfModelId(modelId);
			if (modelContainer.isPresent()) {
				modelContainers.add(modelContainer.get());
			}
			else {
				throw new ProjectLoadException("unexpected model id when recreating groups");
			}
		}
		return modelContainers;
	}

	private Optional<ModelContainer> getModelContainerOfModelId(int modelId) {
		for (ProjectifiableThing modelContainer : topLevelThings) {
			if (modelContainer.getModelId() == modelId) {
				return Optional.of((ModelContainer) modelContainer);
			}
		}
		return Optional.empty();

	}

	/**
	 * Update the transforms of the given group as indicated by groupState.
	 */
	private void recreateGroupState(ModelGroup group, Map<Integer, ItemState> groupStates) throws ProjectLoadException {
		group.setState(groupStates.get(group.getModelId()));
		group.checkOffBed();

	}

	@Override
	public void autoLayout() {
		autoLayout(topLevelThings);
	}

	@Override
	public void autoLayout(List<ProjectifiableThing> thingsToLayout) {
		double printVolumeWidth = 0;
		double printVolumeDepth = 0;

		Printer selPrinter = selectedPrinter.get();

		if (selPrinter != null && selPrinter.printerConfigurationProperty().get() != null) {
			printVolumeWidth = selPrinter.printerConfigurationProperty().get().getPrintVolumeWidth();
			printVolumeDepth = selPrinter.printerConfigurationProperty().get().getPrintVolumeDepth();
		}
		else {
			PrinterDefinitionFile defaultPrinterConfiguration = printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID);
			printVolumeWidth = defaultPrinterConfiguration.getPrintVolumeWidth();
			printVolumeDepth = defaultPrinterConfiguration.getPrintVolumeDepth();
		}

		Dimension binDimension = new Dimension((int) printVolumeWidth, (int) printVolumeDepth);
		Bin layoutBin = null;

		final double spacing = 2.5;
		final double halfSpacing = spacing / 2.0;

		Map<Integer, ProjectifiableThing> partMap = new HashMap<>();

		int numberOfPartsNotToLayout = topLevelThings.size() - thingsToLayout.size();

		if (numberOfPartsNotToLayout > 0) {
			MArea[] existingPieces = new MArea[numberOfPartsNotToLayout];
			int existingPartCounter = 0;
			for (ProjectifiableThing thingToConsider : topLevelThings) {
				if (!thingsToLayout.contains(thingToConsider)) {
					//We need to stop this part from being laid out
					RectangularBounds pieceBounds = thingToConsider.calculateBoundsInBedCoordinateSystem();
					Rectangle2D.Double rectangle = new Rectangle2D.Double(pieceBounds.getMinX() - halfSpacing, printVolumeDepth - pieceBounds.getMaxZ() - halfSpacing, pieceBounds.getWidth() + halfSpacing,
							pieceBounds.getDepth() + halfSpacing);
					MArea piece = new MArea(rectangle, existingPartCounter, ((ModelContainer) thingToConsider).getRotationTurn());
					existingPieces[existingPartCounter] = piece;
					partMap.put(existingPartCounter, thingToConsider);
					existingPartCounter++;
				}
			}
			layoutBin = new Bin(binDimension, existingPieces);
		}

		int startingIndexForPartsToLayout = (numberOfPartsNotToLayout == 0) ? 0 : numberOfPartsNotToLayout - 1;

		MArea[] partsToLayout = new MArea[thingsToLayout.size()];
		int partsToLayoutCounter = 0;
		for (ProjectifiableThing thingToLayout : thingsToLayout) {
			RectangularBounds pieceBounds = thingToLayout.calculateBoundsInBedCoordinateSystem();
			//Change the coords so that every part is in the bottom left corner
			Rectangle2D.Double rectangle = new Rectangle2D.Double(0, 0, pieceBounds.getWidth() + spacing, pieceBounds.getDepth() + spacing);
			//            Rectangle2D.Double rectangle = new Rectangle2D.Double(pieceBounds.getMinX() - halfSpacing,
			//                    printVolumeDepth - pieceBounds.getMaxZ() - halfSpacing,
			//                    pieceBounds.getWidth() + halfSpacing,
			//                    pieceBounds.getDepth() + halfSpacing);
			MArea piece = new MArea(rectangle, partsToLayoutCounter + startingIndexForPartsToLayout, 0);
			partsToLayout[partsToLayoutCounter] = piece;
			partMap.put(partsToLayoutCounter + startingIndexForPartsToLayout, thingToLayout);
			partsToLayoutCounter++;
		}
		//        LOGGER.info("started with");
		//        for (MArea area : partsToLayout)
		//        {
		//            LOGGER.info("Piece " + area.getID()
		//                    + " X" + area.getBoundingBox2D().getX()
		//                    + " Y" + area.getBoundingBox2D().getY()
		//                    + " W" + area.getBoundingBox2D().getWidth()
		//                    + " H" + area.getBoundingBox2D().getHeight()
		//                    + " R" + area.getRotation()
		//            );
		//
		//        }

		if (layoutBin != null) {
			MArea[] unplacedParts = null;
			unplacedParts = layoutBin.BBCompleteStrategy(partsToLayout);
			LOGGER.info("Unplaced = " + unplacedParts.length);
		}
		else {
			Bin[] bins = binPacking.BinPackingStrategy(partsToLayout, binDimension, binDimension);
			layoutBin = bins[0];
		}

		int numberOfPartsInTotal = layoutBin.getPlacedPieces().length;
		double newXPosition[] = new double[numberOfPartsInTotal];
		double newDepthPosition[] = new double[numberOfPartsInTotal];
		double newRotation[] = new double[numberOfPartsInTotal];
		double minLayoutX = 999, maxLayoutX = -999, minLayoutY = 999, maxLayoutY = -999;

		for (int pieceNumber = 0; pieceNumber < numberOfPartsInTotal; pieceNumber++) {
			MArea area = layoutBin.getPlacedPieces()[pieceNumber];

			//            LOGGER.info("Piece " + area.getID()
			//                    + " X" + area.getBoundingBox2D().getX()
			//                    + " Y" + area.getBoundingBox2D().getY()
			//                    + " W" + area.getBoundingBox2D().getWidth()
			//                    + " H" + area.getBoundingBox2D().getHeight()
			//                    + " R" + area.getRotation()
			//            );
			newRotation[pieceNumber] = area.getRotation();

			newDepthPosition[pieceNumber] = printVolumeDepth - area.getBoundingBox2D().getMaxY() + area.getBoundingBox2D().getHeight() / 2.0;
			newXPosition[pieceNumber] = area.getBoundingBox2D().getMinX() + (area.getBoundingBox2D().getWidth() / 2.0);

			maxLayoutX = Math.max(maxLayoutX, area.getBoundingBox2D().getMaxX());
			minLayoutX = Math.min(minLayoutX, area.getBoundingBox2D().getMinX());
			maxLayoutY = Math.max(maxLayoutY, area.getBoundingBox2D().getMaxY());
			minLayoutY = Math.min(minLayoutY, area.getBoundingBox2D().getMinY());
		}

		//        LOGGER.info("minx " + minLayoutX + " maxX " + maxLayoutX);
		//        LOGGER.info("miny " + minLayoutY + " maxY " + maxLayoutY);
		double xCentringOffset = (printVolumeWidth - (maxLayoutX - minLayoutX)) / 2.0;
		double yCentringOffset = (printVolumeDepth - (maxLayoutY - minLayoutY)) / 2.0;

		//        LOGGER.info("Centring offset x  " + xCentringOffset + " y " + yCentringOffset);
		for (int pieceNumber = 0; pieceNumber < layoutBin.getPlacedPieces().length; pieceNumber++) {
			MArea area = layoutBin.getPlacedPieces()[pieceNumber];
			ModelContainer container = (ModelContainer) partMap.get(area.getID());

			if (thingsToLayout.contains(container)) {
				double rotation = newRotation[pieceNumber] + container.getRotationTurn();
				if (rotation >= 360.0) {
					rotation -= 360.0;
				}
				container.setRotationTurn(rotation);

				//Only auto centre if we're laying out all of the parts
				if (numberOfPartsNotToLayout == 0) {
					container.translateTo(newXPosition[pieceNumber] + xCentringOffset, newDepthPosition[pieceNumber] + yCentringOffset);
				}
				else {
					container.translateTo(newXPosition[pieceNumber], newDepthPosition[pieceNumber]);
				}
				//                LOGGER.info("Thing " + area.getID() + " is at cX" + container.getTransformedCentreX() + " cY" + container.getTransformedCentreDepth() + " r" + container.getRotationTurn());
			}
		}

		projectModified();
		fireWhenAutoLaidOut();
	}

	private void fireWhenAutoLaidOut() {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenAutoLaidOut();
		}
	}

	public void rotateLeanModels(Set<RotatableThreeD> modelContainers, double rotation) {
		for (RotatableThreeD model : modelContainers) {
			model.setRotationLean(rotation);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void rotateTwistModels(Set<RotatableThreeD> modelContainers, double rotation) {
		for (RotatableThreeD model : modelContainers) {
			model.setRotationTwist(rotation);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void rotateTurnModels(Set<RotatableTwoD> modelContainers, double rotation) {
		for (RotatableTwoD model : modelContainers) {
			model.setRotationTurn(rotation);
		}
		projectModified();

		fireWhenModelsTransformed((Set) modelContainers);
	}

	public void dropToBed(Set<ModelContainer> modelContainers) {
		for (ModelContainer model : modelContainers) {
			{
				model.dropToBed();
				model.checkOffBed();
			}
		}
		projectModified();

		Set<ProjectifiableThing> projectifiableThings = (Set) modelContainers;
		fireWhenModelsTransformed(projectifiableThings);
	}

	public void snapToGround(ModelContainer modelContainer, MeshView pickedMesh, int faceNumber) {
		modelContainer.snapToGround(pickedMesh, faceNumber);
		projectModified();
		Set<ModelContainer> modelContainers = new HashSet<>();
		modelContainers.add(modelContainer);

		Set<ProjectifiableThing> projectifiableThings = (Set) modelContainers;
		fireWhenModelsTransformed(projectifiableThings);
	}

	@Override
	protected void fireWhenModelsTransformed(Set<ProjectifiableThing> modelContainers) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenModelsTransformed(modelContainers);
		}
	}

	private void fireWhenModelChanged(ModelContainer modelContainer, String propertyName) {
		for (ProjectChangesListener projectChangesListener : projectChangesListeners) {
			projectChangesListener.whenModelChanged(modelContainer, propertyName);
		}
	}

	public void setAssociatedExtruder(Set<ModelContainer> modelContainers, boolean useExtruder0) {
		for (ModelContainer modelContainer : modelContainers) {
			modelContainer.setUseExtruder0(useExtruder0);
		}

		boolean usingDifferentExtruders = false;
		int lastExtruder = -1;
		for (ProjectifiableThing projectifiableThing : getAllModels()) {
			ModelContainer model = (ModelContainer) projectifiableThing;
			int thisExtruder = model.getAssociateWithExtruderNumberProperty().get();
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

	@Override
	protected void checkNotAlreadyInGroup(Set<Groupable> modelContainers) {
		Set<ModelContainer> modelsAlreadyInGroups = getDescendentModelsInAllGroups();
		for (Groupable model : modelContainers) {
			if (modelsAlreadyInGroups.contains(model)) {
				throw new RuntimeException("Model " + model + " is already in a group");
			}
		}
	}

	/**
	 * Create a new group from models that are not yet in the project, and add model listeners to all descendent children.
	 *
	 * @param modelContainers
	 * @return
	 */
	@Override
	public ModelGroup createNewGroupAndAddModelListeners(Set<Groupable> modelContainers) {
		checkNotAlreadyInGroup(modelContainers);
		ModelGroup modelGroup = modelGroupFactory.create((Set) modelContainers);
		addModelListeners(modelGroup);
		for (ModelContainer childModelContainer : modelGroup.getDescendentModelContainers()) {
			addModelListeners(childModelContainer);
		}
		modelGroup.checkOffBed();
		return modelGroup;
	}
}
