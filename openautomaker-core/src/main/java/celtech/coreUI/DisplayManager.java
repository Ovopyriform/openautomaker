package celtech.coreUI;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterIdentity;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.environment.preference.advanced.ShowGCodeConsolePreference;
import org.openautomaker.environment.preference.application.FirstUsePreference;
import org.openautomaker.environment.preference.application.VersionPreference;
import org.openautomaker.environment.preference.modeling.ModelsPathPreference;
import org.openautomaker.environment.preference.modeling.ProjectsPathPreference;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.ui.StageManager;
import org.openautomaker.ui.component.info_screen_indicator.InfoScreenIndicatorController;
import org.openautomaker.ui.component.layout_status_menu_strip.LayoutStatusMenuStrip;
import org.openautomaker.ui.component.menu_panel.library.LibraryMenuPanelController;
import org.openautomaker.ui.component.printer_side_panel.PrinterSidePanel;
import org.openautomaker.ui.component.printer_status_page.PrinterStatusPageController;
import org.openautomaker.ui.component.progress_dialog.ProgressDialog;
import org.openautomaker.ui.component.purge_panel.PurgeInsetPanelController;
import org.openautomaker.ui.inject.project.ModelContainerProjectFactory;
import org.openautomaker.ui.inject.undo.UndoableProjectFactory;
import org.openautomaker.ui.state.ProjectGUIStates;
import org.openautomaker.ui.state.SelectedPrinter;
import org.openautomaker.ui.state.SelectedProject;
import org.openautomaker.ui.state.SelectedSpinnerControl;

import com.google.inject.Injector;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.appManager.Project;
import celtech.appManager.ProjectCallback;
import celtech.appManager.ProjectManager;
import celtech.appManager.undo.CommandStack;
import celtech.appManager.undo.UndoableProject;
import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.components.ProjectTab;
import celtech.coreUI.components.Spinner;
import celtech.coreUI.components.TopMenuStrip;
import celtech.coreUI.components.Notifications.NotificationArea;
import celtech.coreUI.controllers.panels.PreviewManagerController;
import celtech.coreUI.keycommands.HiddenKey;
import celtech.coreUI.keycommands.KeyCommandListener;
import celtech.coreUI.keycommands.UnhandledKeyListener;
import celtech.coreUI.visualisation.ModelLoader;
import celtech.coreUI.visualisation.ProjectSelection;
import celtech.modelcontrol.ProjectifiableThing;
import celtech.roboxbase.comms.RoboxCommsManager;
import celtech.roboxbase.comms.VirtualPrinterCommandInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.effect.Glow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
@Singleton
public class DisplayManager implements EventHandler<KeyEvent>, KeyCommandListener, UnhandledKeyListener, SpinnerControl,
		ProjectCallback {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final int START_SCALING_WINDOW_HEIGHT = 700;
	private static final double MINIMUM_SCALE_FACTOR = 0.7;

	private static DisplayManager instance;
	//private static Stage mainStage;
	private static Scene scene;

	private HBox mainHolder;
	private StackPane sidePanelContainer;
	private final HashMap<ApplicationMode, Pane> insetPanels;
	private final AnchorPane rhPanel;
	private final VBox projectTabPaneHolder;
	private final HashMap<ApplicationMode, Object> insetPanelControllers;
	private VBox sidePanel;

	private static AnchorPane interchangeablePanelAreaWithNotificationArea;
	private static TabPane tabDisplay;
	private static SingleSelectionModel<Tab> tabDisplaySelectionModel;
	private static Tab printerStatusTab;
	private static Tab addPageTab;
	private Tab lastLayoutTab;

	/*
	 * Project loading
	 */
	private ProgressDialog modelLoadDialog = null;

	private InfoScreenIndicatorController infoScreenIndicatorController = null;

	private static final String addDummyPrinterCommand = "AddDummy";
	private static final String dummyCommandPrefix = "dummy:";

	private StackPane rootStackPane;
	private AnchorPane rootAnchorPane;
	private Pane spinnerContainer;
	private Spinner spinner;

	private final NotificationArea notificationArea;

	//Display scaling
	private BooleanProperty nodesMayHaveMoved;

	private final BooleanProperty libraryModeEntered = new SimpleBooleanProperty(false);

	public enum DisplayScalingMode {

		NORMAL,
		SHORT,
		VERY_SHORT
	}

	private ObjectProperty<DisplayScalingMode> displayScalingModeProperty = new SimpleObjectProperty<>(DisplayScalingMode.NORMAL);
	private final int SHORT_SCALE_BELOW_HEIGHT = 890;
	private final int VERY_SHORT_SCALE_BELOW_HEIGHT = 700;

	// This is here solely so it shutdown can be called on it when the application closes.
	// If other things need to be added, it should be changed to a more generic callback mechanism.
	private PreviewManagerController previewManager = null;

	//Remove this 
	//	private DisplayManager() {
	//		fAdvancedModePreference = new AdvancedModePreference();
	//		fShowGCodeConsolePreference = new ShowGCodeConsolePreference();
	//
	//		this.rootStackPane = new StackPane();
	//		this.nodesMayHaveMoved = new SimpleBooleanProperty(false);
	//		this.insetPanelControllers = new HashMap<>();
	//		this.insetPanels = new HashMap<>();
	//		//applicationStatus = applicationStatus;
	//		projectManager = ProjectManager.getInstance();
	//		this.projectTabPaneHolder = new VBox();
	//		AnchorPane.setBottomAnchor(projectTabPaneHolder, 0.0);
	//		AnchorPane.setTopAnchor(projectTabPaneHolder, 0.0);
	//		AnchorPane.setLeftAnchor(projectTabPaneHolder, 0.0);
	//		AnchorPane.setRightAnchor(projectTabPaneHolder, 0.0);
	//		this.rhPanel = new AnchorPane();
	//		//		 LOGGER.debug("Starting AutoMaker - initialising display manager...");
	//		//		 LOGGER.debug("Starting AutoMaker - machine type is " + BaseConfiguration.getMachineType());
	//	}

	private final Injector injector;
	private final AdvancedModePreference advancedModePreference;
	private final ShowGCodeConsolePreference showGCodeConsolePreference;
	private final ModelsPathPreference modelsPathPreference;
	private final VersionPreference versionPreference;
	private final FirstUsePreference firstUsePreference;
	private final I18N i18n;
	private final FXMLLoaderFactory fxmlLoaderFactory;
	private final TaskExecutor taskExecutor;
	private final SelectedPrinter selectedPrinter;
	private final SelectedProject selectedProject;
	private final ApplicationStatus applicationStatus;
	private final ProjectManager projectManager;
	private final UndoableProjectFactory undoableProjectFactory;
	private final ProjectGUIStates projectGUIStates;

	private final RoboxCommsManager roboxCommsManager;

	private final SelectedSpinnerControl selectedSpinnerControl;

	private final ModelContainerProjectFactory modelContainerProjectFactory;
	private final ModelLoader modelLoader;
	private final StageManager stageManager;
	private final ProjectsPathPreference projectsPathPreference;

	@Inject
	protected DisplayManager(
			Injector injector,
			AdvancedModePreference advancedModePreference,
			ShowGCodeConsolePreference showGCodeConsolePreference,
			ModelsPathPreference modelsPathPreference,
			VersionPreference versionPreference,
			FirstUsePreference firstUsePreference,
			I18N i18n,
			ApplicationStatus applicationStatus,
			FXMLLoaderFactory fxmlLoaderFactory,
			TaskExecutor taskExecutor,
			SelectedPrinter selectedPrinter,
			SelectedProject selectedProject,
			ProjectManager projectManager,
			UndoableProjectFactory undoableProjectFactory,
			ProjectGUIStates projectGUIStates,
			RoboxCommsManager roboxCommsManager,
			SelectedSpinnerControl selectedSpinnerControl,
			ModelContainerProjectFactory modelContainerProjectFactory,
			ModelLoader modelLoader,
			NotificationArea notificationArea,
			StageManager stageManager,
			ProjectsPathPreference projectsPathPreference) {

		this.injector = injector;
		
		this.advancedModePreference = advancedModePreference;
		this.showGCodeConsolePreference = showGCodeConsolePreference;
		this.modelsPathPreference = modelsPathPreference;
		this.versionPreference = versionPreference;
		this.firstUsePreference = firstUsePreference;

		this.i18n = i18n;

		this.fxmlLoaderFactory = fxmlLoaderFactory;
		this.taskExecutor = taskExecutor;
		this.applicationStatus = applicationStatus;
		this.selectedPrinter = selectedPrinter;
		this.selectedProject = selectedProject;
		this.projectManager = projectManager;
		this.undoableProjectFactory = undoableProjectFactory;
		this.projectGUIStates = projectGUIStates;
		this.roboxCommsManager = roboxCommsManager;
		this.selectedSpinnerControl = selectedSpinnerControl;
		this.modelContainerProjectFactory = modelContainerProjectFactory;
		this.modelLoader = modelLoader;
		this.notificationArea = notificationArea;
		this.stageManager = stageManager;
		this.projectsPathPreference = projectsPathPreference;

		this.rootStackPane = new StackPane();
		this.nodesMayHaveMoved = new SimpleBooleanProperty(false);
		this.insetPanelControllers = new HashMap<>();
		this.insetPanels = new HashMap<>();

		this.projectTabPaneHolder = new VBox();
		AnchorPane.setBottomAnchor(projectTabPaneHolder, 0.0);
		AnchorPane.setTopAnchor(projectTabPaneHolder, 0.0);
		AnchorPane.setLeftAnchor(projectTabPaneHolder, 0.0);
		AnchorPane.setRightAnchor(projectTabPaneHolder, 0.0);
		this.rhPanel = new AnchorPane();

		instance = this;
	}

	// This is here solely so shutdown can be called on it when the application closes.
	public void setPreviewManager(PreviewManagerController previewManager) {
		this.previewManager = previewManager;
	}

	private void loadProjectsAtStartup() {
		LOGGER.debug("start load projects");

		List<Project> preloadedProjects = projectManager.getOpenProjects();

		for (int projectNumber = preloadedProjects.size() - 1; projectNumber >= 0; projectNumber--) {
			Project project = preloadedProjects.get(projectNumber);
			ProjectTab newProjectTab = new ProjectTab(project, tabDisplay.widthProperty(), tabDisplay.heightProperty(), true);
			tabDisplay.getTabs().add(1, newProjectTab);
		}

		LOGGER.debug("end load projects");
	}

	public void showAndSelectPrintProfile(RoboxProfile roboxProfile) {
		applicationStatus.setMode(ApplicationMode.LIBRARY);
		Object initializable = insetPanelControllers.get(ApplicationMode.LIBRARY);
		LibraryMenuPanelController controller = (LibraryMenuPanelController) initializable;
		controller.showAndSelectPrintProfile(roboxProfile);
	}

	public void showAndSelectCameraProfile(CameraProfile profile) {
		applicationStatus.setMode(ApplicationMode.LIBRARY);
		Object initializable = insetPanelControllers.get(ApplicationMode.LIBRARY);
		LibraryMenuPanelController controller = (LibraryMenuPanelController) initializable;
		controller.showAndSelectCameraProfile(profile);
	}

	private void switchPagesForMode(ApplicationMode oldMode, ApplicationMode newMode) {
		libraryModeEntered.set(false);
		infoScreenIndicatorController.setSelected(newMode == ApplicationMode.STATUS);

		// Remove the existing side panel
		if (oldMode != null) {
			Pane lastInsetPanel = insetPanels.get(oldMode);
			if (lastInsetPanel != null) {
				interchangeablePanelAreaWithNotificationArea.getChildren().remove(lastInsetPanel);
			}
			else {
				if (interchangeablePanelAreaWithNotificationArea.getChildren().contains(projectTabPaneHolder)) {
					interchangeablePanelAreaWithNotificationArea.getChildren().remove(projectTabPaneHolder);
				}
			}
		}

		// Now add the relevant new one...
		Pane newInsetPanel = insetPanels.get(newMode);
		if (newInsetPanel != null) {
			AnchorPane.setBottomAnchor(newInsetPanel, 0.0);
			AnchorPane.setTopAnchor(newInsetPanel, 0.0);
			AnchorPane.setLeftAnchor(newInsetPanel, 0.0);
			AnchorPane.setRightAnchor(newInsetPanel, 0.0);
			interchangeablePanelAreaWithNotificationArea.getChildren().add(0, newInsetPanel);
		}

		if (null != newMode)
			switch (newMode) {
				case LAYOUT:
					interchangeablePanelAreaWithNotificationArea.getChildren().add(0, projectTabPaneHolder);
					//Switch tabs if necessary
					if (!(tabDisplaySelectionModel.getSelectedItem() instanceof ProjectTab)) {
						if (lastLayoutTab != null
								&& tabDisplay.getTabs().contains(lastLayoutTab)) {
							//Select the last project tab
							tabDisplaySelectionModel.select(lastLayoutTab);
						}
						else {
							//Select either the first tab or the the + tab (so that a new project is added)
							tabDisplaySelectionModel.select(1);
						}
					}
					break;
				case SETTINGS:
					interchangeablePanelAreaWithNotificationArea.getChildren().add(0, projectTabPaneHolder);
					break;
				case STATUS:
					interchangeablePanelAreaWithNotificationArea.getChildren().add(0, projectTabPaneHolder);
					tabDisplaySelectionModel.select(0);
					break;
				case LIBRARY:
					libraryModeEntered.set(true);
					break;
				default:
					break;
			}
	}

	/**
	 * TODO: Deprecated. Only left for compatibility for now. Remove
	 * 
	 * @return Instance of the display manager created by Guice
	 */
	@Deprecated
	public static DisplayManager getInstance() {
		return instance;
	}

	/**
	 * Show the spinner, and keep it centred on the given region.
	 */
	@Override
	public void startSpinning(Region centreRegion) {
		spinner.setVisible(true);
		spinner.startSpinning();
		spinner.setCentreNode(centreRegion);
	}

	/**
	 * Stop and hide the spinner.
	 */
	@Override
	public void stopSpinning() {
		spinner.setVisible(false);
		spinner.stopSpinning();
	}

	//TODO: Seems most of this config can be done without a stage.  Why wait until the stage is ready.  Can do this in the init.
	public void configureDisplayManager(Stage mainStage, String applicationName,
			String modelsToLoadAtStartup_projectName,
			List<String> modelsToLoadAtStartup,
			boolean dontGroupStartupModels) {

		LOGGER.debug("start configure display manager");

		stageManager.setMainStage(mainStage);

		mainStage.setTitle(applicationName + " - " + versionPreference.getValue().getValue());

		rootAnchorPane = new AnchorPane();

		rootStackPane.getChildren().add(rootAnchorPane);

		// This seems odd
		spinnerContainer = new Pane();
		spinnerContainer.setMouseTransparent(true);
		spinnerContainer.setPickOnBounds(false);
		spinner = new Spinner();
		spinner.setVisible(false);
		spinnerContainer.getChildren().add(spinner);
		selectedSpinnerControl.set(this);

		AnchorPane.setBottomAnchor(rootAnchorPane, 0.0);
		AnchorPane.setLeftAnchor(rootAnchorPane, 0.0);
		AnchorPane.setRightAnchor(rootAnchorPane, 0.0);
		AnchorPane.setTopAnchor(rootAnchorPane, 0.0);

		mainHolder = new HBox();
		mainHolder.setPrefSize(-1, -1);

		AnchorPane.setBottomAnchor(mainHolder, 0.0);
		AnchorPane.setLeftAnchor(mainHolder, 0.0);
		AnchorPane.setRightAnchor(mainHolder, 0.0);
		AnchorPane.setTopAnchor(mainHolder, 0.0);

		rootAnchorPane.getChildren().add(mainHolder);
		rootAnchorPane.getChildren().add(spinnerContainer);

		// Load in all of the side panels
		LOGGER.debug("setup panels for mode");
		for (ApplicationMode mode : ApplicationMode.values()) {
			setupPanelsForMode(mode);
		}

		// Create a place to hang the side panels from
		sidePanelContainer = new StackPane();
		HBox.setHgrow(sidePanelContainer, Priority.NEVER);

		//		try {
		sidePanel = new PrinterSidePanel();
		//		}
		//		catch (Exception ex) {
		//			LOGGER.error("Couldn't load side panel", ex);
		//		}

		sidePanelContainer.getChildren().add(sidePanel);

		mainHolder.getChildren().add(sidePanelContainer);

		projectTabPaneHolder.getStyleClass().add("master-details-pane");
		HBox.setHgrow(projectTabPaneHolder, Priority.ALWAYS);

		HBox.setHgrow(rhPanel, Priority.ALWAYS);

		interchangeablePanelAreaWithNotificationArea = new AnchorPane();
		AnchorPane.setBottomAnchor(interchangeablePanelAreaWithNotificationArea, 0.0);
		AnchorPane.setTopAnchor(interchangeablePanelAreaWithNotificationArea, 0.0);
		AnchorPane.setLeftAnchor(interchangeablePanelAreaWithNotificationArea, 0.0);
		AnchorPane.setRightAnchor(interchangeablePanelAreaWithNotificationArea, 0.0);
		rhPanel.getChildren().add(interchangeablePanelAreaWithNotificationArea);

		HBox topMenuStrip = new TopMenuStrip();
		AnchorPane.setTopAnchor(topMenuStrip, 0.0);
		AnchorPane.setLeftAnchor(topMenuStrip, 0.0);
		AnchorPane.setRightAnchor(topMenuStrip, 0.0);
		rhPanel.getChildren().add(topMenuStrip);

		mainHolder.getChildren().add(rhPanel);

		// Configure the main display tab pane - just the printer status page to start with
		tabDisplay = new TabPane();
		tabDisplay.setPickOnBounds(false);
		tabDisplay.setOnKeyPressed(this);
		tabDisplay.setTabMinHeight(56);
		tabDisplay.setTabMaxHeight(56);
		tabDisplaySelectionModel = tabDisplay.getSelectionModel();
		tabDisplay.getStyleClass().add("main-project-tabPane");
		configureProjectDragNDrop(tabDisplay);

		VBox.setVgrow(tabDisplay, Priority.ALWAYS);
		AnchorPane.setBottomAnchor(tabDisplay, 0.0);
		AnchorPane.setTopAnchor(tabDisplay, 0.0);
		AnchorPane.setLeftAnchor(tabDisplay, 0.0);
		AnchorPane.setRightAnchor(tabDisplay, 0.0);

		// The printer status tab will always be visible - the page is static
		try {

			FXMLLoader printerStatusPageLoader = fxmlLoaderFactory.create(getClass().getResource("/org/openautomaker/ui/component/printer_status_page/PrinterStatusPage.fxml"));
			AnchorPane printerStatusPage = printerStatusPageLoader.load();
			PrinterStatusPageController printerStatusPageController = printerStatusPageLoader.getController();
			printerStatusPageController.configure(projectTabPaneHolder);

			printerStatusTab = new Tab();

			FXMLLoader printerStatusPageLabelLoader = fxmlLoaderFactory.create(getClass().getResource("/org/openautomaker/ui/component/info_screen_indicator/InfoScreenIndicator.fxml"));
			VBox printerStatusLabelGroup = printerStatusPageLabelLoader.load();
			infoScreenIndicatorController = printerStatusPageLabelLoader.getController();
			printerStatusTab.setGraphic(printerStatusLabelGroup);
			printerStatusTab.setClosable(false);
			printerStatusTab.setContent(printerStatusPage);
			tabDisplay.getTabs().add(printerStatusTab);

			tabDisplaySelectionModel.selectedItemProperty().addListener(
					(ObservableValue<? extends Tab> ov, Tab lastTab, Tab newTab) -> {
						if (newTab == addPageTab) {
							createAndAddNewProjectTab();

							if (applicationStatus.getMode() != ApplicationMode.LAYOUT) {
								applicationStatus.setMode(ApplicationMode.LAYOUT);
							}
						}
						else if (newTab instanceof ProjectTab) {
							if (applicationStatus.getMode() != ApplicationMode.LAYOUT) {
								applicationStatus.setMode(ApplicationMode.LAYOUT);
							}

							if (lastTab != newTab) {
								ProjectTab projectTab = (ProjectTab) tabDisplaySelectionModel.getSelectedItem();
								projectTab.fireProjectSelected();
							}
						}
						else {
							//Going to status
							if (lastTab instanceof ProjectTab) {
								lastLayoutTab = lastTab;
							}

							if (applicationStatus.getMode() != ApplicationMode.STATUS) {
								applicationStatus.setMode(ApplicationMode.STATUS);
							}
						}

						if (lastTab instanceof ProjectTab) {
							ProjectTab lastProjectTab = (ProjectTab) lastTab;
							lastProjectTab.fireProjectDeselected();
						}
					});

			AnchorPane.setBottomAnchor(notificationArea, 90.0);
			AnchorPane.setLeftAnchor(notificationArea, 0.0);
			AnchorPane.setRightAnchor(notificationArea, 0.0);
			interchangeablePanelAreaWithNotificationArea.getChildren().add(notificationArea);
			projectTabPaneHolder.getChildren().add(tabDisplay);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to load printer status page", ex);
		}

		applicationStatus.modeProperty().addListener(
				(ObservableValue<? extends ApplicationMode> ov, ApplicationMode oldMode, ApplicationMode newMode) -> {
					switchPagesForMode(oldMode, newMode);
				});

		applicationStatus.setMode(ApplicationMode.STATUS);

		try {
			LayoutStatusMenuStrip layoutStatusMenuStrip = new LayoutStatusMenuStrip();
			layoutStatusMenuStrip.prefWidthProperty().bind(projectTabPaneHolder.widthProperty());
			projectTabPaneHolder.getChildren().add(layoutStatusMenuStrip);

			//			//TODO:  Make this a componen
			//			URL menuStripURL = getClass().getResource(ApplicationConfiguration.fxmlPanelResourcePath
			//					+ "LayoutStatusMenuStrip.fxml");
			//			FXMLLoader menuStripLoader = fxmlLoaderFactory.create(menuStripURL);
			//			VBox menuStripControls = (VBox) menuStripLoader.load();
			//			menuStripControls.prefWidthProperty().bind(projectTabPaneHolder.widthProperty());
			//			projectTabPaneHolder.getChildren().add(menuStripControls);
		}
		catch (Exception ex) {
			LOGGER.error("Failed to load menu strip controls", ex);
		}

		modelLoadDialog = new ProgressDialog(modelLoader.getModelLoaderService());

		scene = new Scene(rootStackPane, ApplicationConfiguration.DEFAULT_WIDTH,
				ApplicationConfiguration.DEFAULT_HEIGHT);

		scene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());

		scene.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(
					ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				whenWindowChangesSize();
			}
		});

		scene.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(
					ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				whenWindowChangesSize();
			}
		});

		projectTabPaneHolder.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(
					ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				whenWindowChangesSize();
			}
		});

		mainStage.maximizedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(
					ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				whenWindowChangesSize();
			}
		});

		mainStage.fullScreenProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(
					ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				LOGGER.debug("Stage fullscreen = " + newValue.booleanValue());
				whenWindowChangesSize();
			}
		});

		//TODO: Look at this.  It's a bit odd.
		HiddenKey hiddenKeyThing = new HiddenKey(selectedPrinter);
		hiddenKeyThing.addCommandSequence(addDummyPrinterCommand);
		hiddenKeyThing.addCommandWithParameterSequence(dummyCommandPrefix);
		hiddenKeyThing.addKeyCommandListener(this);
		hiddenKeyThing.addUnhandledKeyListener(this);
		hiddenKeyThing.captureHiddenKeys(scene);

		// Camera required to allow 2D shapes to be rotated in 3D in the '2D' UI
		PerspectiveCamera controlOverlaycamera = new PerspectiveCamera(false);

		scene.setCamera(controlOverlaycamera);

		mainStage.setScene(scene);

		addPageTab = new Tab();
		addPageTab.setText("+");
		addPageTab.setClosable(false);
		tabDisplay.getTabs().add(addPageTab);

		// Create ContextMenu for addPageTab.
		ContextMenu contextMenu = new ContextMenu();
		MenuItem item1 = new MenuItem("Open Project ...");
		item1.setOnAction((ActionEvent event) -> {
			openProject();
		});
		// Add MenuItem to ContextMenu
		contextMenu.getItems().add(item1);
		addPageTab.contextMenuProperty().set(contextMenu);

		LOGGER.debug("load projects");
		loadProjectsAtStartup();
		loadModelsIntoNewProject(modelsToLoadAtStartup_projectName,
				modelsToLoadAtStartup,
				dontGroupStartupModels);

		rootAnchorPane.layout();

		LOGGER.debug("end configure display manager");
	}

	// TODO: This should be in the file menu
	private void openProject() {
		FileChooser projectChooser = new FileChooser();
		projectChooser.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Robox Project", "*.robox"));
		projectChooser.setInitialDirectory(projectsPathPreference.getValue().toFile());
		List<File> files = projectChooser.showOpenMultipleDialog(stageManager.getMainStage());
		if (files != null && !files.isEmpty()) {
			files.forEach(projectFile -> {
				loadProject(projectFile);
			});
		}
	}

	private void setupPanelsForMode(ApplicationMode mode) {

		URL fxmlFileName = getClass().getResource(mode.getInsetPanelFXMLName());
		if (fxmlFileName == null)
			return;

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("About to load inset panel fxml: " + fxmlFileName.toString());

		try {
			FXMLLoader insetPanelLoader = fxmlLoaderFactory.create(fxmlFileName);

			//TODO: nope.  These all need a common interface.  Create a factory for mode Controllers
			//TODO: Double Nope.,  Could just load the files and get the controller from the FXML loader.
			insetPanelLoader.setController(injector.getInstance(mode.getControllerClass()));

			Pane insetPanel = (Pane) insetPanelLoader.load();
			Object insetPanelController = insetPanelLoader.getController();
			insetPanel.setId(mode.name());
			insetPanels.put(mode, insetPanel);
			insetPanelControllers.put(mode, insetPanelController);
		}
		catch (Exception ex) {
			insetPanels.put(mode, null);
			insetPanelControllers.put(mode, null);
			LOGGER.error("Couldn't load inset panel for mode:" + mode, ex);
		}
	}

	private ProjectTab createAndAddNewProjectTab() {
		ProjectTab projectTab = new ProjectTab(tabDisplay.widthProperty(), tabDisplay.heightProperty());
		tabDisplay.getTabs().add(tabDisplay.getTabs().size() - 1, projectTab);
		tabDisplaySelectionModel.select(projectTab);
		selectedProject.set(null);
		return projectTab;
	}

	public Rectangle2D getNormalisedPreviewRectangle() {
		Rectangle2D nRectangle = null;
		Tab currentTab = tabDisplaySelectionModel.getSelectedItem();

		if (currentTab instanceof ProjectTab) {
			ProjectTab currentProjectTab = (ProjectTab) currentTab;
			Rectangle2D previewBounds = currentProjectTab.getPreviewRectangle();
			Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
			nRectangle = new Rectangle2D(previewBounds.getMinX() / primaryScreenBounds.getWidth(),
					previewBounds.getMinY() / primaryScreenBounds.getHeight(),
					previewBounds.getWidth() / primaryScreenBounds.getWidth(),
					previewBounds.getHeight() / primaryScreenBounds.getHeight());
		}
		return nRectangle;
	}

	//	public Stage getMainStage() {
	//		return mainStage;
	//	}

	public ProjectTab getTabForProject(Project project) {
		ProjectTab pTab = null;
		if (tabDisplay != null) {
			pTab = tabDisplay.getTabs()
					.filtered((t) -> ((t instanceof ProjectTab) && ((ProjectTab) t).getProject() == project))
					.stream().map((t) -> ((ProjectTab) t)).findAny().orElse(null);
		}
		return pTab;
	}

	public void shutdown() {
		// This is here solely so it shutdown can be called on it when the application closes.
		if (previewManager != null) {
			previewManager.shutdown();
		}

		if (projectManager != null) {
			projectManager.saveState();
		}

		if (tabDisplay != null) {
			tabDisplay.getTabs().stream().filter((tab) -> (tab instanceof ProjectTab)).forEach(
					(tab) -> {
						((ProjectTab) tab).saveAndCloseProject();
					});
		}
	}

	/**
	 * Key handler for whole application Delete - deletes selected model
	 *
	 * @param event
	 */
	@Override
	public void handle(KeyEvent event) {
		if (applicationStatus.getMode() == ApplicationMode.LAYOUT) {
			Tab currentTab = tabDisplaySelectionModel.getSelectedItem();
			if (currentTab instanceof ProjectTab) {
				Project project = selectedProject.get();
				UndoableProject undoableProject = undoableProjectFactory.create(project);
				switch (event.getCode()) {
					case DELETE:
					case BACK_SPACE:
						if (projectGUIStates.get(project) != null) {
							if (projectGUIStates.get(project).getProjectGUIRules().canRemoveOrDuplicateSelection().get()) {
								deleteSelectedModels(project, undoableProject);
							}
						}
						event.consume();
						break;
					case A:
						if (event.isShortcutDown()) {
							selectAllModels(project);
							event.consume();
						}
						break;
					case Z:
						if (event.isShortcutDown() && (!event.isShiftDown())) {
							undoCommand(project);
							event.consume();
						}
						else if (event.isShortcutDown() && event.isShiftDown()) {
							redoCommand(project);
							event.consume();
						}
						break;
					case Y:
						if (event.isShortcutDown()) {
							redoCommand(project);
							event.consume();
						}
						break;
					default:
						break;
				}
			}
		}
		else if (applicationStatus.getMode() == ApplicationMode.STATUS && advancedModePreference.getValue()) {
			switch (event.getCode()) {
				case G:
					showGCodeConsolePreference.setValue(true);
					break;
				default:
					break;
			}
		}
	}

	private void selectAllModels(Project project) {
		ProjectSelection projectSelection = projectGUIStates.get(project).getProjectSelection();
		for (ProjectifiableThing modelContainer : project.getTopLevelThings()) {
			projectSelection.addSelectedItem(modelContainer);
		}
	}

	private void deleteSelectedModels(Project project, UndoableProject undoableProject) {
		Set<ProjectifiableThing> selectedModels = projectGUIStates.get(project).getProjectSelection().getSelectedModelsSnapshot();
		undoableProject.deleteModels(selectedModels);
	}

	private void undoCommand(Project project) {
		CommandStack commandStack = projectGUIStates.get(project).getCommandStack();
		if (commandStack.getCanUndo().get()) {
			try {
				commandStack.undo();
			}
			catch (CommandStack.UndoException ex) {
				LOGGER.debug("cannot undo " + ex);
			}
		}
	}

	private void redoCommand(Project project) {
		CommandStack commandStack = projectGUIStates.get(project).getCommandStack();
		if (commandStack.getCanRedo().get()) {
			try {
				commandStack.redo();
			}
			catch (CommandStack.UndoException ex) {
				LOGGER.debug("cannot undo " + ex);
			}
		}
	}

	public PurgeInsetPanelController getPurgeInsetPanelController() {
		return (PurgeInsetPanelController) insetPanelControllers.get(ApplicationMode.PURGE);
	}

	/**
	 * This is fired when the main window or one of the internal windows may have changed size.
	 */
	private void whenWindowChangesSize() {
		nodesMayHaveMoved.set(!nodesMayHaveMoved.get());

		//        LOGGER.info("Window size change: " + scene.getWidth() + " : " + scene.getHeight());
		if (scene.getHeight() < VERY_SHORT_SCALE_BELOW_HEIGHT) {
			if (displayScalingModeProperty.get() != DisplayScalingMode.VERY_SHORT) {
				displayScalingModeProperty.set(DisplayScalingMode.VERY_SHORT);
			}
		}
		else if (scene.getHeight() < SHORT_SCALE_BELOW_HEIGHT) {
			if (displayScalingModeProperty.get() != DisplayScalingMode.SHORT) {
				displayScalingModeProperty.set(DisplayScalingMode.SHORT);
			}
		}
		else {
			if (displayScalingModeProperty.get() != DisplayScalingMode.NORMAL) {
				displayScalingModeProperty.set(DisplayScalingMode.NORMAL);
			}
		}

		double scaleFactor = 1.0;
		if (scene.getHeight() < START_SCALING_WINDOW_HEIGHT) {
			scaleFactor = scene.getHeight() / START_SCALING_WINDOW_HEIGHT;
			if (scaleFactor < MINIMUM_SCALE_FACTOR) {
				scaleFactor = MINIMUM_SCALE_FACTOR;
			}
		}

		rootAnchorPane.setScaleX(scaleFactor);
		rootAnchorPane.setScaleY(scaleFactor);
		rootAnchorPane.setScaleZ(scaleFactor);

		rootAnchorPane.setPrefWidth(scene.getWidth() / scaleFactor);
		rootAnchorPane.setMinWidth(scene.getWidth() / scaleFactor);
		rootAnchorPane.setPrefHeight(scene.getHeight() / scaleFactor);
		rootAnchorPane.setMinHeight(scene.getHeight() / scaleFactor);
	}

	public ReadOnlyBooleanProperty nodesMayHaveMovedProperty() {
		return nodesMayHaveMoved;
	}

	@Override
	public boolean trigger(String commandSequence, String capturedParameter) {
		boolean handled = false;

		switch (commandSequence) {
			case addDummyPrinterCommand:
				//TODO: When is this called?  Seems dummy printers are added all over the shop
				roboxCommsManager.addVirtualPrinter(false);
				handled = true;
				break;
			case dummyCommandPrefix:
				Printer currentPrinter = selectedPrinter.get();
				PrinterIdentity pid = currentPrinter.getPrinterIdentity();
				if (pid.printeryearOfManufactureProperty().get().equals(
						VirtualPrinterCommandInterface.dummyYear)) {
					currentPrinter.sendRawGCode(
							capturedParameter.replaceAll("/", " ").trim().toUpperCase(), true);
					handled = true;
				}
				break;
		}

		return handled;
	}

	@Override
	public void unhandledKeyEvent(KeyEvent keyEvent) {
		//Try sending the keyEvent to the in-focus project
		handle(keyEvent);
	}

	private void loadProject(File projectFile) {
		try {
			Project p = projectManager.getProjectIfOpen(FilenameUtils.getBaseName(projectFile.getName()))
					.orElseGet(() -> {
						Project newProject = projectManager.loadProject(projectFile.toPath());
						if (newProject != null) {
							ProjectTab newProjectTab = new ProjectTab(newProject, tabDisplay.widthProperty(), tabDisplay.heightProperty(), false);
							tabDisplay.getTabs().add(tabDisplay.getTabs().size() - 1, newProjectTab);
						}
						return newProject;
					});
			if (p != null) {
				ProjectTab pt = getTabForProject(p);
				tabDisplaySelectionModel.select(pt);
				if (applicationStatus.getMode() != ApplicationMode.LAYOUT) {
					applicationStatus.setMode(ApplicationMode.LAYOUT);
				}
			}
		}
		catch (Exception ex) {
			LOGGER.error("Failed to open project", ex);
		}
	}

	private void configureProjectDragNDrop(Node basePane) {
		basePane.setOnDragOver((DragEvent event) -> {
			if (event.getGestureSource() != basePane) {
				Dragboard dragboard = event.getDragboard();
				if (dragboard.hasFiles()) {
					List<File> fileList = dragboard.getFiles();
					boolean accept = true;
					for (File file : fileList) {
						boolean extensionFound = false;

						if (file.getName().toUpperCase().endsWith(
								ApplicationConfiguration.projectFileExtension
										.toUpperCase())) {
							extensionFound = true;
							break;
						}

						if (!extensionFound) {
							accept = false;
							break;
						}
					}

					if (accept) {
						event.acceptTransferModes(TransferMode.COPY);
					}
				}
			}

			event.consume();
		});

		basePane.setOnDragEntered((DragEvent event) -> {
			/* the drag-and-drop gesture entered the target */
			/* show to the user that it is an actual gesture target */
			if (applicationStatus.modeProperty().getValue() == ApplicationMode.LAYOUT) {
				if (event.getGestureSource() != basePane) {
					Dragboard dragboard = event.getDragboard();
					if (dragboard.hasFiles()) {
						List<File> fileList = dragboard.getFiles();
						boolean accept = true;
						for (File file : fileList) {
							boolean extensionFound = false;
							if (file.getName().toUpperCase().endsWith(
									ApplicationConfiguration.projectFileExtension
											.toUpperCase())) {
								extensionFound = true;
								break;
							}

							if (!extensionFound) {
								accept = false;
								break;
							}
						}

						if (accept) {
							basePane.setEffect(new Glow());
						}
					}
				}
			}
			event.consume();
		});

		basePane.setOnDragExited((DragEvent event) -> {
			/* mouse moved away, remove the graphical cues */
			basePane.setEffect(null);

			event.consume();
		});

		basePane.setOnDragDropped((DragEvent event) -> {
			/* data dropped */
			LOGGER.debug("onDragDropped");
			/* if there is a string data on dragboard, read it and use it */
			Dragboard db = event.getDragboard();
			boolean success = false;
			if (db.hasFiles()) {
				db.getFiles().forEach(file -> {
					loadProject(file);
				});

			}
			else {
				LOGGER.error("No files in dragboard");
			}
			/*
			 * let the source know whether the string was successfully transferred and used
			 */
			event.setDropCompleted(success);

			event.consume();
		});
	}

	public ReadOnlyObjectProperty<DisplayScalingMode> getDisplayScalingModeProperty() {
		return displayScalingModeProperty;
	}

	public void loadModelsIntoNewProject(String projectName, List<String> modelsWithPaths, boolean dontGroupModels) {
		List<File> listOfFiles = new ArrayList<>();

		if (modelsWithPaths != null
				&& modelsWithPaths.size() > 0) {
			modelsWithPaths.forEach(modelRef -> {
				File fileRef = new File(modelRef);

				if (fileRef.exists()) {
					listOfFiles.add(fileRef);
				}
			});
		}

		Runnable loaderRunnable = () -> {
			Project newProject = modelContainerProjectFactory.create();
			newProject.setProjectName(projectName);

			modelLoader.loadExternalModels(newProject, listOfFiles, false, null, false);

			ProjectTab projectTab = new ProjectTab(newProject, tabDisplay.widthProperty(), tabDisplay.heightProperty(), false);

			tabDisplay.getTabs().add(tabDisplay.getTabs().size() - 1, projectTab);
			tabDisplay.getSelectionModel().select(projectTab);
		};

		if (firstUsePreference.getValue()) {
			File firstUsePrintFile = modelsPathPreference.getAppValue().resolve("RBX_ROBOT_MM.stl").toFile();

			Project newProject = modelContainerProjectFactory.create();
			newProject.setProjectName(i18n.t("myFirstPrintTitle"));

			List<File> fileToLoad = new ArrayList<>();
			fileToLoad.add(firstUsePrintFile);

			if (listOfFiles.size() > 0) {
				ChangeListener<Boolean> firstUseModelLoadListener = new ChangeListener<>() {
					@Override
					public void changed(ObservableValue<? extends Boolean> ov, Boolean wasRunning, Boolean isRunning) {
						if (wasRunning && !isRunning) {
							taskExecutor.runOnGUIThread(loaderRunnable);
							modelLoader.modelLoadingProperty().removeListener(this);
						}
					}
				};

				modelLoader.modelLoadingProperty().addListener(firstUseModelLoadListener);
			}
			modelLoader.loadExternalModels(newProject, fileToLoad, false, null, false);

			ProjectTab projectTab = new ProjectTab(newProject, tabDisplay.widthProperty(), tabDisplay.heightProperty(), false);

			tabDisplay.getTabs().add(1, projectTab);

			firstUsePreference.setValue(false);
		}
		else if (listOfFiles.size() > 0) {
			taskExecutor.runOnGUIThread(loaderRunnable);
		}
	}

	@Override
	public void modelAddedToProject(Project project) {

		if (tabDisplay.getSelectionModel().getSelectedItem() instanceof ProjectTab)
			((ProjectTab) tabDisplay.getSelectionModel().getSelectedItem()).modelAddedToProject(project);

		Project selProj = selectedProject.get();
		if (selProj == null || selProj != project)
			selectedProject.set(project);

	}

	public void initialiseBlank3DProject() {
		((ProjectTab) tabDisplay.getSelectionModel().getSelectedItem()).initialiseBlank3DProject();
	}

	public void initialiseBlank2DProject() {
		((ProjectTab) tabDisplay.getSelectionModel().getSelectedItem()).initialiseBlank2DProject();
	}

	public BooleanProperty libraryModeEnteredProperty() {
		return libraryModeEntered;
	}
}
