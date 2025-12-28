package org.openautomaker.ui.component.printer_status_page;

import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.PrinterColourMap;
import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterConnection;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.printerControl.model.PrinterListChangesListener;
import org.openautomaker.base.printerControl.model.Reel;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.environment.preference.advanced.ShowAdjustmentsPreference;
import org.openautomaker.environment.preference.advanced.ShowDiagnosticsPreference;
import org.openautomaker.environment.preference.advanced.ShowGCodeConsolePreference;
import org.openautomaker.environment.preference.advanced.ShowSnapshotPreference;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.javafx.FXProperty;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.DisplayManager;
import celtech.coreUI.components.HyperlinkedLabel;
import celtech.coreUI.components.JogButton;
import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.remote.PauseStatus;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class PrinterStatusPageController implements Initializable, PrinterListChangesListener {

	private static final Logger LOGGER = LogManager.getLogger();

	private final I18N i18n;

	private final AdvancedModePreference advancedModePreference;
	private final ShowDiagnosticsPreference showDiagnosticsPreference;
	private final ShowGCodeConsolePreference showGCodeConsolePreference;
	private final ShowAdjustmentsPreference showAdjustmentsPreference;
	private final ShowSnapshotPreference showSnapshotPreference;
	private final DisplayManager displayManager;

	private Printer printerToUse = null;
	private PrintServerConnection serverToUse = null;
	private ChangeListener<Color> printerColourChangeListener = null;
	private ChangeListener<PrinterStatus> printerStatusChangeListener = null;
	private ChangeListener<PauseStatus> pauseStatusChangeListener = null;

	private String transferringDataString = null;

	private final PrinterColourMap colourMap;

	private NumberFormat threeDPformatter;
	private NumberFormat fiveDPformatter;
	private boolean initialised = false;

	@FXML
	private AnchorPane container;

	@FXML
	private StackPane statusPane;

	@FXML
	private StackPane rbx01Stack; // Default printer stack, that holds the images of the printer shown on the status page.
	private HashMap<String, StackPane> printerStackMap = null;

	private ImageView baseNoReels;

	private ImageView baseReel2;

	private ImageView baseReel1;

	private ImageView reel1Background;
	private ColorAdjust reel1BackgroundColourEffect = new ColorAdjust();

	private ImageView reel2Background;
	private ColorAdjust reel2BackgroundColourEffect = new ColorAdjust();

	private ImageView baseReelBoth;

	private ImageView doorClosed;

	private ImageView doorOpen;

	private ImageView singleMaterialHead;

	private ImageView dualMaterialHead;

	private ImageView singleNozzleHead;

	private ImageView ambientLight;
	private ColorAdjust ambientColourEffect = new ColorAdjust();

	private ImageView bed;

	private ImageView temperatureWarning;

	private VBox extruder1Controls;

	private VBox extruder2Controls;

	private HBox xAxisControls;

	private VBox yAxisControls;

	private VBox zAxisControls;

	@FXML
	private VBox disconnectedText;

	@FXML
	private HyperlinkedLabel disconnectedLinkedText;

	private VBox vBoxLeft = new VBox();
	private VBox vBoxRight = new VBox();
	private VBox gcodePanel = null;
	private VBox diagnosticPanel = null;
	private VBox projectPanel = null;
	private VBox printAdjustmentsPanel = null;
	private VBox snapshotPanel = null;
	private VBox parentPanel = null;

	private BooleanProperty selectedPrinterIsPrinting = new SimpleBooleanProperty(false);
	private BooleanProperty selectedPrinterHasCamera = new SimpleBooleanProperty(false);
	private BooleanProperty projectPanelShouldBeVisible = new SimpleBooleanProperty(true);
	private BooleanProperty projectPanelVisibility = new SimpleBooleanProperty(false);

	private final BooleanProperty printerConnectionOffline = new SimpleBooleanProperty(false);

	private final PrinterManager printerManager;
	private final SelectedPrinter selectedPrinter;
	private final TaskExecutor taskExecutor;
	private final FXMLLoaderFactory fxmlLoaderFactory;
	@Inject
	protected PrinterStatusPageController(
			I18N i18n,
			AdvancedModePreference advancedModePreference,
			ShowDiagnosticsPreference showDiagnosticsPreference,
			ShowGCodeConsolePreference showGCodeConsolePreference,
			ShowAdjustmentsPreference showAdjustmentsPreference,
			ShowSnapshotPreference showSnapshotPreference,
			FXMLLoaderFactory fxmlLoaderFactory,
			DisplayManager displayManager,
			PrinterColourMap printerColourMap,
			PrinterManager printerManager,
			SelectedPrinter selectedPrinter,
			TaskExecutor taskExecutor) {

		this.i18n = i18n;
		this.colourMap = printerColourMap;
		this.printerManager = printerManager;
		this.selectedPrinter = selectedPrinter;
		this.taskExecutor = taskExecutor;

		this.advancedModePreference = advancedModePreference;
		this.showDiagnosticsPreference = showDiagnosticsPreference;
		this.showGCodeConsolePreference = showGCodeConsolePreference;
		this.showAdjustmentsPreference = showAdjustmentsPreference;
		this.showSnapshotPreference = showSnapshotPreference;
		this.fxmlLoaderFactory = fxmlLoaderFactory;
		this.displayManager = displayManager;
	}

	private final MapChangeListener<Integer, Filament> effectiveFilamentListener = (MapChangeListener.Change<? extends Integer, ? extends Filament> change) -> {
		setupBaseDisplay();
	};

	private final ChangeListener<Boolean> filamentLoadedListener = (ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
		setupBaseDisplay();
	};

	@FXML
	void jogButton(ActionEvent event) {
		JogButton button = (JogButton) event.getSource();

		try {
			printerToUse.jogAxis(button.getAxis(), button.getDistance(), button.getFeedRate(),
					button.getUseG1());
		}
		catch (PrinterException ex) {
			LOGGER.error("Failed to jog printer - " + ex.getMessage());
		}
	}

	private void displayScaleChanged(DisplayManager.DisplayScalingMode scalingMode) {
		switch (scalingMode) {
			case VERY_SHORT:
			case SHORT:
				projectPanelShouldBeVisible.set(false);
				break;
			case NORMAL:
				projectPanelShouldBeVisible.set(true);
				break;
		}
		resizePrinterDisplay(parentPanel);
	}

	/**
	 * Initializes the controller class.
	 *
	 * @param url
	 * @param rb
	 */
	@Override
	public void initialize(URL url, ResourceBundle rb) {
		// Should only run once, but gets called every time a printer stack is loaded.
		// initialize is called every time a controller is created
		if (initialised)
			return;

		initialised = true;

		printerManager.getPrinterChangeNotifier().addListener(this);

		advancedModePreference.addChangeListener(new PreferenceChangeListener() {
			@Override
			public void preferenceChange(PreferenceChangeEvent evt) {
				setAdvancedControlsVisibility();
			}

		});

		printerStackMap = new HashMap<>();
		printerStackMap.put("RBX01", rbx01Stack);
		printerStackMap.put("RBX02", rbx01Stack);
		setupPrinterType("RBX01");

		threeDPformatter = NumberFormat.getNumberInstance(Locale.UK);
		threeDPformatter.setMaximumFractionDigits(3);
		threeDPformatter.setGroupingUsed(false);

		fiveDPformatter = NumberFormat.getNumberInstance(Locale.UK);
		fiveDPformatter.setMaximumFractionDigits(5);
		fiveDPformatter.setGroupingUsed(false);

		transferringDataString = i18n.t("PrintQueue.SendingToPrinter");

		printerColourChangeListener = (ObservableValue<? extends Color> observable, Color oldValue, Color newValue) -> {
			setupAmbientLight();
		};

		printerStatusChangeListener = (ObservableValue<? extends PrinterStatus> observable, PrinterStatus oldValue, PrinterStatus newValue) -> {
			setAdvancedControlsVisibility();
		};

		pauseStatusChangeListener = (ObservableValue<? extends PauseStatus> observable, PauseStatus oldValue, PauseStatus newValue) -> {
			setAdvancedControlsVisibility();
		};

		doorClosed.setVisible(false);
		doorOpen.setVisible(false);
		temperatureWarning.setVisible(false);

		setupBaseDisplay();
		setupAmbientLight();
		setupHead();

		AnchorPane.setTopAnchor(vBoxLeft, 30.0);
		AnchorPane.setBottomAnchor(vBoxLeft, 30.0);
		loadInsetPanels();

		selectedPrinter.addListener(
				new ChangeListener<Printer>() {
					@Override
					public void changed(ObservableValue<? extends Printer> ov,
							Printer t, Printer selectedPrinter) {
						unbindFromSelectedPrinter();
						printerToUse = selectedPrinter;
						printerConnectionOffline.set(printerToUse != null && printerToUse.printerConnectionProperty().get().equals(PrinterConnection.OFFLINE));
						setupBaseDisplay();
						setupAmbientLight();

						if (selectedPrinter == null) {
							temperatureWarning.setVisible(false);
						}
						else {
							selectedPrinter.getPrinterIdentity().printerColourProperty().addListener(
									printerColourChangeListener);

							temperatureWarning.visibleProperty().bind(
									selectedPrinter.getPrinterAncillarySystems().bedTemperatureProperty()
											.greaterThan(ApplicationConfiguration.bedHotAboveDegrees));

							selectedPrinter.printerStatusProperty().addListener(
									printerStatusChangeListener);
							selectedPrinter.pauseStatusProperty().addListener(
									pauseStatusChangeListener);
							doorOpen.visibleProperty().bind(selectedPrinter.getPrinterAncillarySystems().doorOpenProperty());
							doorClosed.visibleProperty().bind(selectedPrinter.getPrinterAncillarySystems().doorOpenProperty().not());

							selectedPrinter.effectiveFilamentsProperty().addListener(effectiveFilamentListener);

							selectedPrinter.extrudersProperty().forEach(extruder -> {
								extruder.filamentLoadedProperty().addListener(filamentLoadedListener);
							});
						}
					}
				});

		disconnectedLinkedText.replaceText(i18n.t("printerStatus.noPrinterAttached"));

		projectPanelVisibility.bind(projectPanelShouldBeVisible.and(selectedPrinterIsPrinting));

		displayManager.getDisplayScalingModeProperty().addListener(new ChangeListener<DisplayManager.DisplayScalingMode>() {

			@Override
			public void changed(ObservableValue<? extends DisplayManager.DisplayScalingMode> ov, DisplayManager.DisplayScalingMode t, DisplayManager.DisplayScalingMode t1) {
				displayScaleChanged(t1);
			}
		});

		displayScaleChanged(displayManager.getDisplayScalingModeProperty().get());
	}

	private StackPane loadPrinterStack(String printerTypeCode) {
		StackPane printerStack = printerStackMap.getOrDefault(printerTypeCode, null);
		if (printerStack == null) {
			String printerStackFXMLName = printerTypeCode.toLowerCase() + "Stack.fxml";
			URL printerStackURL = getClass().getResource(ApplicationConfiguration.fxmlPrinterStatusResourcePath + printerStackFXMLName);
			try {
				FXMLLoader loader = fxmlLoaderFactory.create(printerStackURL);
				loader.setController(this);

				printerStack = loader.load();
				printerStackMap.put(printerTypeCode, printerStack);

				// Add the printer stack to the status page.
				statusPane.getChildren().add(1, printerStack);
				printerStack.setVisible(false);
			}
			catch (Exception ex) {
				LOGGER.info("Couldn't load printer stack for printer type \"" + printerTypeCode + "\" - defaulting to RBX01");
				if (printerStackURL != null)
					LOGGER.debug("printerStackURL = " + printerStackURL.toString());
				else
					LOGGER.debug("printerStackURL = null!");

				printerStack = null;
			}

			if (printerStack == null) {
				printerStack = rbx01Stack;
				// So it doesn't try to load it again.
				printerStackMap.put(printerTypeCode, printerStack);
			}
		}

		return printerStack;
	}

	private void setupPrinterType(String printerTypeCode) {
		// Clear visible property bindings from hidden elements.
		if (temperatureWarning != null) {
			temperatureWarning.visibleProperty().unbind();
		}
		if (doorOpen != null) {
			doorOpen.visibleProperty().unbind();
		}
		if (doorClosed != null) {
			doorClosed.visibleProperty().unbind();
		}

		// Hide all the printer stacks.
		printerStackMap.forEach((k, v) -> v.setVisible(false));
		if (printerTypeCode != null && !printerTypeCode.isEmpty()) {
			StackPane printerStack = loadPrinterStack(printerTypeCode);
			if (printerStack == null) {
				throw new RuntimeException("No printer stacks found in printer status fxml.");
			}

			printerStack.setVisible(true);
			for (Node pgNode : printerStack.getChildren()) {
				String pgNodeName = pgNode.getId();
				switch (pgNodeName) {
					case "baseNoReels":
						baseNoReels = (ImageView) pgNode;
						break;
					case "baseReel1":
						baseReel1 = (ImageView) pgNode;
						break;
					case "baseReel2":
						baseReel2 = (ImageView) pgNode;
						break;
					case "reel1Background":
						reel1Background = (ImageView) pgNode;
						reel1Background.setEffect(reel1BackgroundColourEffect);
						break;
					case "reel2Background":
						reel2Background = (ImageView) pgNode;
						reel2Background.setEffect(reel2BackgroundColourEffect);
						break;
					case "baseReelBoth":
						baseReelBoth = (ImageView) pgNode;
						break;
					case "doorClosed":
						doorClosed = (ImageView) pgNode;
						break;
					case "doorOpen":
						doorOpen = (ImageView) pgNode;
						break;
					case "singleMaterialHead":
						singleMaterialHead = (ImageView) pgNode;
						break;
					case "dualMaterialHead":
						dualMaterialHead = (ImageView) pgNode;
						break;
					case "singleNozzleHead":
						singleNozzleHead = (ImageView) pgNode;
						break;
					case "ambientLight":
						ambientLight = (ImageView) pgNode;
						ambientLight.setEffect(ambientColourEffect);
						break;
					case "bed":
						bed = (ImageView) pgNode;
						break;
					case "temperatureWarning":
						temperatureWarning = (ImageView) pgNode;
						break;

					case "extruder1Controls":
						extruder1Controls = (VBox) pgNode;
						break;

					case "extruder2Controls":
						extruder2Controls = (VBox) pgNode;
						break;

					case "xAxisControls":
						xAxisControls = (HBox) pgNode;
						break;

					case "yAxisControls":
						yAxisControls = (VBox) pgNode;
						break;

					case "zAxisControls":
						zAxisControls = (VBox) pgNode;
						break;

					default:
						break;
				}
			}
		}
	}

	private void setupBaseDisplay() {
		if (printerToUse == null) {
			setupPrinterType(null);

			baseNoReels.setVisible(false);
			baseReel1.setVisible(false);
			baseReel2.setVisible(false);
			baseReelBoth.setVisible(false);
			bed.setVisible(false);
			vBoxLeft.setVisible(false);
			vBoxRight.setVisible(false);
			disconnectedText.setVisible(true);
		}
		else {
			setupPrinterType(printerToUse.printerConfigurationProperty().get().getTypeCode());
			vBoxLeft.setVisible(true);
			vBoxRight.setVisible(true);
			disconnectedText.setVisible(false);
			setupHead();

			doorOpen.visibleProperty().bind(printerToUse.getPrinterAncillarySystems().doorOpenProperty());
			doorClosed.visibleProperty().bind(printerToUse.getPrinterAncillarySystems().doorOpenProperty().not());

			if (((printerToUse.extrudersProperty().get(0).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(0).isFittedProperty().get())
					|| (printerToUse.effectiveFilamentsProperty().containsKey(0) && printerToUse.effectiveFilamentsProperty().get(0) != FilamentContainer.UNKNOWN_FILAMENT))
					&& ((printerToUse.extrudersProperty().get(1).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(1).isFittedProperty().get())
							|| (printerToUse.effectiveFilamentsProperty().containsKey(1) && printerToUse.effectiveFilamentsProperty().get(1) != FilamentContainer.UNKNOWN_FILAMENT))) {
				baseNoReels.setVisible(false);
				baseReel1.setVisible(false);
				baseReel2.setVisible(false);
				baseReelBoth.setVisible(true);
				bed.setVisible(true);
			}
			else if (((printerToUse.extrudersProperty().get(0).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(0).isFittedProperty().get())
					|| (printerToUse.effectiveFilamentsProperty().containsKey(0) && printerToUse.effectiveFilamentsProperty().get(0) != FilamentContainer.UNKNOWN_FILAMENT))
					&& (!(printerToUse.extrudersProperty().get(1).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(1).isFittedProperty().get())
							|| (!printerToUse.effectiveFilamentsProperty().containsKey(1) && printerToUse.effectiveFilamentsProperty().get(1) != FilamentContainer.UNKNOWN_FILAMENT))) {
				baseNoReels.setVisible(false);
				baseReel1.setVisible(true);
				baseReel2.setVisible(false);
				baseReelBoth.setVisible(false);
				bed.setVisible(true);
			}
			else if (((printerToUse.extrudersProperty().get(1).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(1).isFittedProperty().get())
					|| (printerToUse.effectiveFilamentsProperty().containsKey(1) && printerToUse.effectiveFilamentsProperty().get(1) != FilamentContainer.UNKNOWN_FILAMENT))
					&& (!(printerToUse.extrudersProperty().get(0).filamentLoadedProperty().get() && printerToUse.extrudersProperty().get(0).isFittedProperty().get())
							|| (!printerToUse.effectiveFilamentsProperty().containsKey(0) && printerToUse.effectiveFilamentsProperty().get(0) != FilamentContainer.UNKNOWN_FILAMENT))) {
				baseNoReels.setVisible(false);
				baseReel1.setVisible(false);
				baseReel2.setVisible(true);
				baseReelBoth.setVisible(false);
				bed.setVisible(true);
			}
			else {
				baseNoReels.setVisible(true);
				baseReel1.setVisible(false);
				baseReel2.setVisible(false);
				baseReelBoth.setVisible(false);
				bed.setVisible(true);
			}
		}

		setupReel1Colour();
		setupReel2Colour();

		setAdvancedControlsVisibility();

		resizePrinterDisplay(parentPanel);
	}

	private void setColorAdjustFromDesiredColour(ColorAdjust effect, Color desiredColor) {
		if (desiredColor != null) {
			effect.setHue(hueConverter(desiredColor.getHue()));
			effect.setBrightness(desiredColor.getBrightness() - 1);
			effect.setSaturation(desiredColor.getSaturation());
		}
		//        LOGGER.info("Colour - h=" + hueConverter(desiredColor.getHue()) + " s=" + desiredColor.getSaturation() + " b" + desiredColor.getBrightness());
	}

	private void setupReel1Colour() {
		if (printerToUse == null
				|| !printerToUse.effectiveFilamentsProperty().containsKey(0)
				|| printerToUse.effectiveFilamentsProperty().get(0) == null
				|| printerToUse.effectiveFilamentsProperty().get(0) == FilamentContainer.UNKNOWN_FILAMENT) {
			reel1Background.setVisible(false);
		}
		else {
			Color reel1Colour = printerToUse.effectiveFilamentsProperty().get(0).getDisplayColour();
			setColorAdjustFromDesiredColour(reel1BackgroundColourEffect, reel1Colour);
			reel1Background.setVisible(true);
		}
	}

	private void setupReel2Colour() {
		if (printerToUse == null
				|| !printerToUse.effectiveFilamentsProperty().containsKey(1)
				|| printerToUse.effectiveFilamentsProperty().get(1) == null
				|| printerToUse.effectiveFilamentsProperty().get(1) == FilamentContainer.UNKNOWN_FILAMENT) {
			reel2Background.setVisible(false);
		}
		else {
			Color reel2Colour = printerToUse.effectiveFilamentsProperty().get(1).getDisplayColour();
			setColorAdjustFromDesiredColour(reel2BackgroundColourEffect, reel2Colour);
			reel2Background.setVisible(true);
		}
	}

	private double hueConverter(double hueCyl) {
		double returnedHue = 0;
		if (hueCyl <= 180) {
			returnedHue = hueCyl / 180.0;
		}
		else {
			returnedHue = -(360 - hueCyl) / 180.0;
		}
		return returnedHue;
	}

	private void setupAmbientLight() {
		if (printerToUse == null) {
			ambientLight.setVisible(false);
		}
		else {
			Color ambientColour = colourMap.printerToDisplayColour(printerToUse.getPrinterIdentity().printerColourProperty().get());
			setColorAdjustFromDesiredColour(ambientColourEffect, ambientColour);
			ambientLight.setVisible(true);
		}
	}

	private void setupHead() {
		boolean singleMaterialHeadVisible = false;
		boolean dualMaterialHeadVisible = false;
		boolean singleNozzleHeadVisible = false;

		if (printerToUse != null) {
			Head printerHead = printerToUse.headProperty().get();
			if (printerHead != null) {
				if (printerHead.headTypeProperty().get() == Head.HeadType.SINGLE_MATERIAL_HEAD) {
					if (printerHead.getNozzles().size() == 1) {
						singleNozzleHeadVisible = true;
					}
					else {
						singleMaterialHeadVisible = true;
					}
				}
				else if (printerHead.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
					dualMaterialHeadVisible = true;
				}
			}
		}

		singleMaterialHead.setVisible(singleMaterialHeadVisible);
		dualMaterialHead.setVisible(dualMaterialHeadVisible);
		singleNozzleHead.setVisible(singleNozzleHeadVisible);
	}

	private void setAdvancedControlsVisibility() {
		boolean visible = false;

		if (printerToUse != null
				&& advancedModePreference.getValue()
				&& !printerConnectionOffline.get()) {
			switch (printerToUse.printerStatusProperty().get()) {
				case IDLE:
					visible = true;
					selectedPrinterIsPrinting.set(false);
					break;
				case PRINTING_PROJECT:
					selectedPrinterIsPrinting.set(true);
					break;
				default:
					selectedPrinterIsPrinting.set(false);
					break;
			}

			switch (printerToUse.pauseStatusProperty().get()) {
				case PAUSED:
				case SELFIE_PAUSE:
					visible = true;
					break;
				case PAUSE_PENDING:
				case RESUME_PENDING:
					visible = false;
					break;
				default:
					break;
			}
			if (printerToUse.getCommandInterface() instanceof RoboxRemoteCommandInterface) {
				PrintServerConnection connectedServer = ((RemoteDetectedPrinter) printerToUse.getCommandInterface().getPrinterHandle()).getServerPrinterIsAttachedTo();
				if (serverToUse != null && serverToUse != connectedServer) {
					selectedPrinterHasCamera.unbind();
					selectedPrinterHasCamera.set(false);
					serverToUse = null;
				}
				if (serverToUse == null) {
					serverToUse = connectedServer;
					selectedPrinterHasCamera.bind(serverToUse.cameraDetectedProperty());
				}
			}
		}
		else {
			selectedPrinterIsPrinting.set(false);
			selectedPrinterHasCamera.unbind();
			selectedPrinterHasCamera.set(false);
		}

		xAxisControls.setVisible(visible);
		yAxisControls.setVisible(visible);
		zAxisControls.setVisible(visible);

		extruder1Controls.setVisible(advancedModePreference.getValue()
				&& visible
				&& printerToUse.extrudersProperty().get(0).filamentLoadedProperty().get()
				&& printerToUse.extrudersProperty().get(0).isFittedProperty().get());
		extruder2Controls.setVisible(advancedModePreference.getValue()
				&& visible
				&& printerToUse.extrudersProperty().get(1).filamentLoadedProperty().get()
				&& printerToUse.extrudersProperty().get(1).isFittedProperty().get());
		//        xAxisControls.setVisible(true);
		//        yAxisControls.setVisible(true);
		//        zAxisControls.setVisible(true);
		//        extruder1Controls.setVisible(true);
		//        extruder2Controls.setVisible(true);
	}

	/**
	 *
	 * @param parent
	 */
	public void configure(VBox parent) {
		parentPanel = parent;

		parent.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				resizePrinterDisplay(parentPanel);
			}
		});
		parent.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				resizePrinterDisplay(parentPanel);
			}
		});
	}

	private void resizePrinterDisplay(VBox parent) {
		if (parent != null) {
			final double beginWidth = 1500;
			final double beginHeight = 1106;
			final double aspect = beginWidth / beginHeight;
			boolean lhPanelVisible = gcodePanel.isVisible() || diagnosticPanel.isVisible();
			boolean rhPanelVisible = projectPanel.isVisible() || printAdjustmentsPanel.isVisible() || snapshotPanel.isVisible();
			double fudgeFactor = (baseReel2.isVisible() || baseReelBoth.isVisible()) ? 600 : 300;
			double lefthandPanelWidthToSubtract = (lhPanelVisible || rhPanelVisible) ? fudgeFactor : 0.0;
			double parentWidth = parent.getWidth() - lefthandPanelWidthToSubtract;
			double parentHeight = parent.getHeight();
			double displayAspect = parentWidth / parentHeight;

			double newWidth = 0;
			double newHeight = 0;

			if (displayAspect >= aspect) {
				// Drive from height
				newWidth = parentHeight * aspect;
				newHeight = parentHeight;
			}
			else {
				//Drive from width
				newHeight = parentWidth / aspect;
				newWidth = parentWidth;
			}

			double xScale = Double.max((newWidth / beginWidth), 0.4);
			double yScale = Double.max((newHeight / beginHeight), 0.4);

			statusPane.setScaleX(xScale);
			statusPane.setScaleY(yScale);
		}
	}

	private void unbindFromSelectedPrinter() {
		if (printerToUse != null) {
			printerToUse.getPrinterIdentity().printerColourProperty().removeListener(
					printerColourChangeListener);
			printerToUse.printerStatusProperty().removeListener(printerStatusChangeListener);
			printerToUse.pauseStatusProperty().removeListener(pauseStatusChangeListener);
			printerToUse.effectiveFilamentsProperty().removeListener(effectiveFilamentListener);
			printerToUse.extrudersProperty().forEach(extruder -> {
				extruder.filamentLoadedProperty().removeListener(filamentLoadedListener);
			});

			selectedPrinterHasCamera.unbind();
			selectedPrinterHasCamera.set(false);
			serverToUse = null;
			printerToUse = null;
		}

		temperatureWarning.visibleProperty().unbind();
		temperatureWarning.setVisible(false);

		doorOpen.visibleProperty().unbind();
		doorOpen.setVisible(false);
		doorClosed.visibleProperty().unbind();
		doorClosed.setVisible(false);
	}

	private VBox loadInsetPanel(String innerPanelFXMLName, String title,
			BooleanProperty visibleProperty,
			ObservableValue<Boolean> appearanceConditions,
			VBox parentPanel,
			int position) {

		//This needs to take into account panels which have been made into components now.
		FXMLLoader loader = null;

		// Removing this for now.  Seems odd.
		//if (!innerPanelFXMLName.equals("GCodePanel.fxml")) {
			URL insetPanelURL = getClass().getResource(innerPanelFXMLName);
			loader = fxmlLoaderFactory.create(insetPanelURL);
			//}
		VBox wrappedPanel = null;

		try {
			VBox insetPanel = loader.load();
			//VBox insetPanel = innerPanelFXMLName.equals("GCodePanel.fxml") ? new GCodePanel() : loader.load();
			if (title != null) {
				wrappedPanel = wrapPanelInOuterPanel(insetPanel, title, visibleProperty);
				if (appearanceConditions != null) {
					wrappedPanel.visibleProperty().bind(appearanceConditions);
				}
				wrappedPanel.managedProperty().bind(wrappedPanel.visibleProperty());
				if (position <= parentPanel.getChildren().size())
					parentPanel.getChildren().add(position, wrappedPanel);
				else
					parentPanel.getChildren().add(wrappedPanel);

				//TODO: So what's this now?
				//final VBox panelToChangeHeightOf = wrappedPanel;
				//panelVisibilityAction((visibleProperty != null) ? visibleProperty.getValue() : false, panelToChangeHeightOf, parentPanel, position);
				//wrappedPanel.visibleProperty().addListener(new ChangeListener<Boolean>()
				//{
				//    @Override
				//    public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean visible)
				//    {
				//        panelVisibilityAction(visible, panelToChangeHeightOf, parentPanel, position);
				//    }
				//});
			}
			else {
				wrappedPanel = insetPanel;
			}
		}
		catch (IOException ex) {
			LOGGER.error("Unable to load inset panel: " + innerPanelFXMLName, ex);
		}
		return wrappedPanel;
	}

	private void panelVisibilityAction(boolean visible, VBox panel, VBox parentPanel, int position) {
		taskExecutor.runOnGUIThread(() -> {
			if (visible) {
				if (!parentPanel.getChildren().contains(panel)) {
					if (position <= parentPanel.getChildren().size()) {
						parentPanel.getChildren().add(position, panel);
					}
					else {
						parentPanel.getChildren().add(panel);
					}
				}
			}
			else {
				parentPanel.getChildren().remove(panel);
			}
			panel.setManaged(visible);
		});
	}

	private VBox wrapPanelInOuterPanel(Node insetPanel, String title,
			BooleanProperty visibleProperty) {
		FXMLLoader loader = fxmlLoaderFactory.create(getClass().getResource("OuterPanel.fxml"));
		VBox outerPanel = null;

		try {
			outerPanel = loader.load();
			OuterPanelController outerPanelController = loader.getController();
			outerPanelController.setInnerPanel(insetPanel);
			outerPanelController.setTitle(i18n.t(title));
			outerPanelController.setPreferredVisibility(visibleProperty);
		}
		catch (IOException ex) {
			LOGGER.error("Unable to load outer panel", ex);
		}
		return outerPanel;
	}

	private void loadInsetPanels() {
		vBoxLeft.setSpacing(20);

		BooleanProperty advancedMode = FXProperty.bind(advancedModePreference);

		//TODO: Check all the appearance conditions on here to make sure it checks advanced mode.
		BooleanProperty showDiagnostics = FXProperty.bind(showDiagnosticsPreference);
		diagnosticPanel = loadInsetPanel("DiagnosticPanel.fxml", "diagnosticPanel.title",
				showDiagnostics, showDiagnostics.and(printerConnectionOffline.not()),
				vBoxLeft,
				0);

		BooleanProperty showGCodeConsole = FXProperty.bind(showGCodeConsolePreference);
		gcodePanel = loadInsetPanel("GCodePanel.fxml", "gcodeEntry.title",
				showGCodeConsole,
				showGCodeConsole
						.and(advancedMode)
						.and(printerConnectionOffline.not()),
				vBoxLeft,
				1);
		gcodePanel.visibleProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			resizePrinterDisplay(parentPanel);
		});

		vBoxRight.setSpacing(20);
		projectPanel = loadInsetPanel("ProjectPanel.fxml", "projectPanel.title", null, projectPanelVisibility, vBoxRight, 0);
		projectPanel.visibleProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			resizePrinterDisplay(parentPanel);
		});

		BooleanProperty showAdjustments = FXProperty.bind(showAdjustmentsPreference);
		printAdjustmentsPanel = loadInsetPanel("TweakPanel.fxml", "printAdjustmentsPanel.title",
				showAdjustments,
				showAdjustments.and(selectedPrinterIsPrinting), vBoxRight, 1);
		printAdjustmentsPanel.visibleProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			resizePrinterDisplay(parentPanel);
		});

		BooleanProperty showSnapshot = FXProperty.bind(showSnapshotPreference);
		snapshotPanel = loadInsetPanel("SnapshotPanel.fxml", "snapshotPanel.title",
				showSnapshot,
				showSnapshot.and(selectedPrinterHasCamera), vBoxRight, 2);
		snapshotPanel.visibleProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			resizePrinterDisplay(parentPanel);
		});

		container.getChildren().add(vBoxLeft);
		AnchorPane.setTopAnchor(vBoxLeft, 30.0);
		AnchorPane.setLeftAnchor(vBoxLeft, 30.0);
		AnchorPane.setBottomAnchor(vBoxLeft, 90.0);
		container.getChildren().add(vBoxRight);
		AnchorPane.setTopAnchor(vBoxRight, 30.0);
		AnchorPane.setRightAnchor(vBoxRight, 30.0);

	}

	@Override
	public void whenPrinterAdded(Printer printer) {
	}

	@Override
	public void whenPrinterRemoved(Printer printer) {
	}

	@Override
	public void whenHeadAdded(Printer printer) {
		setupHead();
	}

	@Override
	public void whenHeadRemoved(Printer printer, Head head) {
		setupHead();
	}

	@Override
	public void whenReelAdded(Printer printer, int reelIndex) {
		setupBaseDisplay();
	}

	@Override
	public void whenReelRemoved(Printer printer, Reel reel, int reelIndex) {
		setupBaseDisplay();
	}

	@Override
	public void whenReelChanged(Printer printer, Reel reel) {
		setupBaseDisplay();
	}

	@Override
	public void whenExtruderAdded(Printer printer, int extruderIndex) {
	}

	@Override
	public void whenExtruderRemoved(Printer printer, int extruderIndex) {
	}

}
