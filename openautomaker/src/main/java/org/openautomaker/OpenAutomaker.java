package org.openautomaker;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.BaseLookup;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.inject.BaseModule;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.base.task_executor.TaskResponse;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.inject.EnvironmentModule;
import org.openautomaker.environment.preference.slicer.SafetyFeaturesPreference;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.inject.OpenAutomakerModule;
import org.openautomaker.javafx.inject.JavaFXModule;
import org.openautomaker.ui.component.choice_link_dialog_box.ChoiceLinkDialogBox;
import org.openautomaker.ui.component.choice_link_dialog_box.ChoiceLinkDialogBox.PrinterDisconnectedException;
import org.openautomaker.ui.inject.UIModule;

import celtech.coreUI.DisplayManager;
import celtech.roboxbase.comms.RoboxCommsManager;
import celtech.roboxbase.comms.interapp.AbstractInterAppRequest;
import celtech.roboxbase.comms.interapp.InterAppCommsConsumer;
import celtech.roboxbase.comms.interapp.InterAppCommsThread;
import celtech.roboxbase.comms.interapp.InterAppStartupStatus;
import celtech.webserver.LocalWebInterface;
import jakarta.inject.Inject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class OpenAutomaker extends Application implements /* AutoUpdateCompletionListener, */ InterAppCommsConsumer {

	public static final String AUTOMAKER_ICON_256 = "/org/openautomaker/images/AutoMakerIcon_256x256.png";
	public static final String AUTOMAKER_ICON_64 = "/org/openautomaker/images/AutoMakerIcon_64x64.png";
	public static final String AUTOMAKER_ICON_32 = "/org/openautomaker/images/AutoMakerIcon_32x32.png";

	private static final Logger LOGGER = LogManager.getLogger();

	@Inject
	private OpenAutomakerEnv environment;

	@Inject
	private SafetyFeaturesPreference fSafetyFeaturesPreference;

	@Inject
	private I18N i18n;

	// Unless they're used here, these shouldn't need to be injected here.  Initialised as needed.

	@Inject
	private DisplayManager displayManager;

	@Inject
	private RoboxCommsManager commsManager;

	@Inject
	private BaseLookup baseLookup;


	@Inject
	private TaskExecutor taskExecutor;

	@Inject
	private SystemNotificationManager systemNotificationHandler;

	@Inject
	PrinterManager printerManager;

	@Inject
	WelcomeToApplicationManager welcomeToApplicationManager;

	//private AutoUpdate autoUpdater = null;
	private List<Printer> waitingForCancelFrom = new ArrayList<>();
	private Stage mainStage;
	private LocalWebInterface localWebInterface = null;

	@Inject
	private InterAppCommsThread interAppCommsListener;

	private final List<String> modelsToLoadAtStartup = new ArrayList<>();
	private String modelsToLoadAtStartup_projectName = "Import";
	private boolean modelsToLoadAtStartup_dontgroup = false;

	// Supporting deep linking??
	private final String uriScheme = "automaker:";
	private final String paramDivider = "\\?";

	private final GuiceContext guiceContext;

	public OpenAutomaker() {

		// Guice this up.  Get rid of static singletons
		guiceContext = new GuiceContext(this, () -> List.of(
				new EnvironmentModule(),
				new OpenAutomakerModule(),
				new BaseModule(),
				new UIModule(),
				new JavaFXModule()));

		guiceContext.init();
	}

	@Override
	public void init() throws Exception {
		InterAppRequestCommand interAppCommand = InterAppRequestCommand.NONE;
		List<InterAppParameter> interAppParameters = new ArrayList<>();

		//TODO: Shouldn't this only allow loading of a project so you can associate the file type with the program?
		if (getParameters().getUnnamed().size() == 1) {
			String potentialParam = getParameters().getUnnamed().get(0);
			if (potentialParam.startsWith(uriScheme)) {
				//We've been started through a URI scheme
				potentialParam = potentialParam.replaceAll(uriScheme, "");

				String[] paramParts = potentialParam.split(paramDivider);
				if (paramParts.length == 2) {
					//                    LOGGER.info("Viable param:" + potentialParam + "->" + paramParts[0] + " -------- " + paramParts[1]);
					// Got a viable param
					switch (paramParts[0]) {
						case "loadModel":
							String[] subParams = paramParts[1].split("&");

							for (String subParam : subParams) {
								InterAppParameter parameter = InterAppParameter.fromParts(subParam);

								if (parameter != null)
									interAppParameters.add(parameter);
							}

							if (interAppParameters.size() > 0)
								interAppCommand = InterAppRequestCommand.LOAD_MESH_INTO_LAYOUT_VIEW;

							break;
						default:
							break;
					}
				}
			}
		}

		InterAppRequest interAppCommsRequest = new InterAppRequest();
		interAppCommsRequest.setCommand(interAppCommand);
		interAppCommsRequest.setUrlEncodedParameters(interAppParameters);

		InterAppStartupStatus startupStatus = interAppCommsListener.letUsBegin(interAppCommsRequest, this);

		if (startupStatus != InterAppStartupStatus.STARTED_OK) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Error initialising InterAppRequest: " + startupStatus.name());

			return;
		}

		//TODO: This seems like it shouldn't need be here.
		//BaseConfiguration.initialise(OpenAutomaker.class);
		//Lookup.setupDefaultValuesFX();

		//BaseLookup.setupDefaultValues();

		//Now Injected
		//BaseLookup.setTaskExecutor(fGuiceContext.getInstance(LiveTaskExecutor.class));
		//BaseLookup.setSystemNotificationHandler(fGuiceContext.getInstance(SystemNotificationManager.class));
		//BaseLookup.setSlicerMappings(slicerMappingsContainer.getSlicerMappings());

		//This seems odd, perhaps just inject this or create a factory using assisted inject?
		//BaseLookup.setPostProcessorOutputWriterFactory(LiveGCodeOutputWriter::new);

		//Now Injected
		//		Lookup.setNotificationDisplay(new NotificationDisplay());
		//		Lookup.setProgressDisplay(new ProgressDisplay());

		//ApplicationUtils.outputApplicationStartupBanner();

		//		commsManager = RoboxCommsManager.getInstance(
		//				false,
		//				FXProperty.bind(fDetectLoadedFilamentPreference),
		//				FXProperty.bind(fSearchForRemoteCamerasPreference));

		switch (interAppCommand) {
			case LOAD_MESH_INTO_LAYOUT_VIEW:

				interAppCommsRequest.getUnencodedParameters().forEach(
						param -> {
							if (param.getType() == InterAppParameterType.MODEL_NAME) {
								modelsToLoadAtStartup.add(param.getUnencodedParameter());
							}
							else if (param.getType() == InterAppParameterType.PROJECT_NAME) {
								modelsToLoadAtStartup_projectName = param.getUnencodedParameter();
							}
							else if (param.getType() == InterAppParameterType.DONT_GROUP_MODELS) {
								switch (param.getUnencodedParameter()) {
									case "true":
										modelsToLoadAtStartup_dontgroup = true;
										break;
									default:
										break;
								}
							}
						});
				break;
			default:
				break;
		}

		// Some FXML load seems to go here in startup

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Startup status was: " + startupStatus.name());
	}

	//TODO: Finalise this for the mac, windows and linux.
	/*
	 * private void attachMenus(Stage stage) { MenuToolkit tk = MenuToolkit.toolkit(new LocalePreference().getValue());
	 * 
	 * AboutStageBuilder aboutStageBuilder = AboutStageBuilder.start("About OpenAutomaker") .withAppName("OpenAutomaker") .withCloseOnFocusLoss().withText("Line 1\nLine2") .withVersionString("Version " + new VersionPreference().getValue().getValue())
	 * .withCopyright("Copyright \u00A9 " + Calendar .getInstance().get(Calendar.YEAR));
	 * 
	 * try { //AutoMakerEnvironment.get().getApplicationPath().resolve("AutoMaker.icns").toFile(); IcnsParser parser = IcnsParser.forFile(new ApplicationPathPreference().getValue().resolve("Resources").resolve("OpenAutomaker.icns").toFile());
	 * aboutStageBuilder = aboutStageBuilder.withImage(new Image(parser.getIconStream(IcnsType.ICN))); } catch (NullPointerException e) { LOGGER.error("NPE from image parser ICNS"); } catch (IOException e) { LOGGER.error("Could not load ICNS"); }
	 * 
	 * Menu applicationMenu = tk.createDefaultApplicationMenu("OpenAutomaker", aboutStageBuilder.build());
	 * 
	 * //TODO: Create the full menu bar. Only really needed once I have the jar based project structure MenuBar bar = new MenuBar(); bar.useSystemMenuBarProperty().set(true); bar.getMenus().add(applicationMenu); tk.setMenuBar(bar);
	 * 
	 * // Menu menu = new Menu("test"); // MenuItem myItem = new MenuItem("Hallo welt"); // menu.getItems().add(myItem); // tk.setDockIconMenu(menu); }
	 */

	@Override
	public void start(Stage stage) throws Exception {
		// Not sure why it wasn't using the primary stage.  Have changed that for the moment
		//mainStage = new Stage();
		mainStage = stage;

		try {

			String applicationName = i18n.t("application.title");

			// TODO: The configDisplayManager seems to do a lot of stuff that doesn't need to be done once the stage exists.
			displayManager.configureDisplayManager(mainStage, applicationName, modelsToLoadAtStartup_projectName, modelsToLoadAtStartup, modelsToLoadAtStartup_dontgroup);

			attachIcons(mainStage);

			//if (OpenAutomakerEnv.get().getMachineType() == MachineType.MAC)
			//attachMenus(mainStage);

			mainStage.setOnCloseRequest((WindowEvent event) -> {
				boolean transferringDataToPrinter = false;
				boolean willShutDown = true;

				ObservableList<Printer> connectedPrinters = printerManager.getConnectedPrinters();

				for (Printer printer : connectedPrinters) {
					transferringDataToPrinter = transferringDataToPrinter | printer.getPrintEngine().transferGCodeToPrinterService.isRunning();
				}

				if (transferringDataToPrinter) {
					boolean shutDownAnyway = systemNotificationHandler.showJobsTransferringShutdownDialog();

					if (shutDownAnyway) {
						for (Printer printer : connectedPrinters) {
							waitingForCancelFrom.add(printer);

							try {
								printer.cancel((TaskResponse<Object> taskResponse) -> {
									waitingForCancelFrom.remove(printer);
								}, fSafetyFeaturesPreference.getValue());
							}
							catch (PrinterException ex) {
								LOGGER.error("Error cancelling print on printer " + printer.getPrinterIdentity().printerFriendlyNameProperty().get() + " - " + ex.getMessage());
							}
						}
					}
					else {
						event.consume();
						willShutDown = false;
					}
				}

				if (willShutDown) {
					//ApplicationUtils.outputApplicationShutdownBanner();
					Platform.exit();
				}
				else {
					LOGGER.info("Shutdown aborted - transfers to printer were in progress");
				}
			});
		}
		catch (Throwable ex) {
			ex.printStackTrace();
			Platform.exit();
		}

		showMainStage();
	}

	private void attachIcons(Stage stage) {
		stage.getIcons().addAll(
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_256)),
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_64)),
				new Image(getClass().getResourceAsStream(AUTOMAKER_ICON_32)));
	}

	//	@Override
	//	public void autoUpdateComplete(boolean requiresShutdown) {
	//		if (requiresShutdown) {
	//			Platform.exit();
	//		}
	//	}

	public static void main(String[] args) {
		System.setProperty("javafx.preloader", OpenAutomakerPreloader.class.getName());
		launch(args);
		// Sometimes a thread stops the application from terminating. The
		// problem is difficult to reproduce, and so far it has not been
		// possible to identify which thread is causing the problem. Calling
		// System.exit(0) should not be necessary and is not good practice, but
		// is a feeble attempt to force all threads to terminate.
		System.exit(0);
	}

	@Override
	public void stop() throws Exception {
		interAppCommsListener.shutdown();

		if (localWebInterface != null)
			localWebInterface.stop();

		int timeoutStrikes = 3;
		while (waitingForCancelFrom.size() > 0 && timeoutStrikes > 0) {
			Thread.sleep(1000);
			timeoutStrikes--;
		}

		if (commsManager != null)
			commsManager.shutdown();

		//		if (autoUpdater != null) {
		//			autoUpdater.shutdown();
		//		}

		if (displayManager != null)
			displayManager.shutdown();

		//Does nothing ,commented
		//baseConfiguration.shutdown();

		if (LOGGER.isDebugEnabled())
			outputRunningThreads();

		//TODO: This thread sleep looks like a hack
		Thread.sleep(5000);

		baseLookup.setShuttingDown(true);
	}

	//    private void setAppUserIDForWindows()
	//    {
	//        if (getMachineType() == MachineType.WINDOWS)
	//        {
	//            setCurrentProcessExplicitAppUserModelID("CelTech.AutoMaker");
	//        }
	//    }
	//
	//    public static void setCurrentProcessExplicitAppUserModelID(final String appID)
	//    {
	//        if (SetCurrentProcessExplicitAppUserModelID(new WString(appID)).longValue() != 0)
	//        {
	//            throw new RuntimeException(
	//                "unable to set current process explicit AppUserModelID to: " + appID);
	//        }
	//    }
	//
	//    private static native NativeLong SetCurrentProcessExplicitAppUserModelID(WString appID);
	//
	//    static
	//    {
	//        if (getMachineType() == MachineType.WINDOWS)
	//        {
	//            Native.register("shell32");
	//        }
	//    }
	/**
	 * Indicates whether any threads are believed to be running
	 *
	 * @return
	 */
	//	private boolean areThreadsStillRunning() {
	//		ThreadGroup rootThreadGroup = getRootThreadGroup();
	//		int numberOfThreads = rootThreadGroup.activeCount();
	//		return numberOfThreads > 0;
	//	}

	/**
	 * Outputs running thread names if there are any Returns true if running threads were found
	 *
	 * @return
	 */
	private boolean outputRunningThreads() {
		ThreadGroup rootThreadGroup = getRootThreadGroup();
		int numberOfThreads = rootThreadGroup.activeCount();
		Thread[] threadList = new Thread[numberOfThreads];
		rootThreadGroup.enumerate(threadList, true);

		if (numberOfThreads > 0) {
			LOGGER.info("There are " + numberOfThreads + " threads running:");
			for (Thread th : threadList) {
				LOGGER.info("---------------------------------------------------");
				LOGGER.info("THREAD DUMP:" + th.getName() + " isDaemon=" + th.isDaemon() + " isAlive=" + th.isAlive());
				for (StackTraceElement element : th.getStackTrace()) {
					LOGGER.info(">>>" + element.toString());
				}
				LOGGER.info("---------------------------------------------------");
			}
		}

		return numberOfThreads > 0;
	}

	private void showMainStage() {
		//final AutoUpdateCompletionListener completeListener = this;

		mainStage.setOnShown((WindowEvent event) -> {
			//autoUpdater = new AutoUpdate(BaseConfiguration.getApplicationShortName(), ApplicationConfiguration.getDownloadModifier(BaseConfiguration.getApplicationName()), completeListener);
			//autoUpdater.start();

			// TODO: Possibly test this dialog first?  Just force this to false.
			if (environment.has3DSupport()) {
				welcomeToApplicationManager.displayWelcomeIfRequired();
				commsManager.start();
			}
			else {
				taskExecutor.runOnGUIThread(() -> {
					ChoiceLinkDialogBox threeDProblemBox = new ChoiceLinkDialogBox(false);
					threeDProblemBox.setTitle(i18n.t("dialogs.fatalErrorNo3DSupport"));
					threeDProblemBox.setMessage(i18n.t("dialogs.automakerErrorNo3DSupport"));
					threeDProblemBox.addChoiceLink(i18n.t("dialogs.error.okAbortJob"));
					try {
						threeDProblemBox.getUserInput();
					}
					catch (PrinterDisconnectedException ex) {
					}
					LOGGER.error("Closing down due to lack of required 3D support.");
					Platform.exit();
				});
			}

			// Virtual printer check
			//if (fUseVirtualPrinterPreference.get())
			//	RoboxCommsManager.getInstance().addVirtualPrinter(true);

		});
		mainStage.setAlwaysOnTop(false);

		//set Stage boundaries to visible bounds of the main screen
		Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
		mainStage.setX(primaryScreenBounds.getMinX());
		mainStage.setY(primaryScreenBounds.getMinY());
		mainStage.setWidth(primaryScreenBounds.getWidth());
		mainStage.setHeight(primaryScreenBounds.getHeight());

		//mainStage.initModality(Modality.WINDOW_MODAL);

		mainStage.show();
	}

	@Override
	public void incomingComms(AbstractInterAppRequest interAppRequest) {
		LOGGER.info("Received an InterApp comms request: " + interAppRequest.toString());

		if (interAppRequest instanceof InterAppRequest) {
			InterAppRequest amRequest = (InterAppRequest) interAppRequest;
			switch (amRequest.getCommand()) {
				case LOAD_MESH_INTO_LAYOUT_VIEW:
					String projectName = "Import";
					List<String> modelsToLoad = new ArrayList<>();
					boolean dontGroupModels = false;

					for (InterAppParameter interAppParam : amRequest.getUnencodedParameters()) {
						if (interAppParam.getType() == InterAppParameterType.MODEL_NAME) {
							modelsToLoad.add(interAppParam.getUnencodedParameter());
						}
						else if (interAppParam.getType() == InterAppParameterType.PROJECT_NAME) {
							projectName = interAppParam.getUnencodedParameter();
						}
						else if (interAppParam.getType() == InterAppParameterType.DONT_GROUP_MODELS) {
							switch (interAppParam.getUnencodedParameter()) {
								case "true":
									dontGroupModels = true;
									break;
								default:
									break;
							}
						}
					}
					displayManager.loadModelsIntoNewProject(projectName, modelsToLoad, dontGroupModels);
					break;
				default:
					break;
			}

		}
	}

	private static ThreadGroup getRootThreadGroup() {
		ThreadGroup root = Thread.currentThread().getThreadGroup();
		ThreadGroup parent = root.getParent();
		while (parent != null) {
			root = parent;
			parent = parent.getParent();
		}
		return root;
	}
}
