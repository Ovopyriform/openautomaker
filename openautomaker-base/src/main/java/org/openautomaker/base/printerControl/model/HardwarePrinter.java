package org.openautomaker.base.printerControl.model;

import static com.sun.javafx.scene.control.skin.Utils.formatHexString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.MaterialType;
import org.openautomaker.base.PrinterColourMap;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.Macro;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterEdition;
import org.openautomaker.base.inject.printer_control.CalibrationXAndYActionsFactory;
import org.openautomaker.base.inject.printer_control.NozzleHeightStateTransitionManagerFactory;
import org.openautomaker.base.inject.printer_control.NozzleOpeningStateTransitionManagerFactory;
import org.openautomaker.base.inject.printer_control.PrintEngineFactory;
import org.openautomaker.base.inject.printer_control.PurgeActionsFactory;
import org.openautomaker.base.inject.printer_control.SingleNozzleHeightStateTransitionManagerFactory;
import org.openautomaker.base.inject.printer_control.XAndYStateTransitionManagerFactory;
import org.openautomaker.base.inject.printer_control.model.HeadFactory;
import org.openautomaker.base.inject.printer_control.model.ReelFactory;
import org.openautomaker.base.inject.printing.PrintJobFactory;
import org.openautomaker.base.inject.state_transition.CalibrationNozzleHeightActionsFactory;
import org.openautomaker.base.inject.state_transition.CalibrationNozzleOpeningActionsFactory;
import org.openautomaker.base.inject.state_transition.PurgeStateTransitionManagerFactory;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.postprocessor.PrintJobStatistics;
import org.openautomaker.base.printerControl.PrintActionUnavailableException;
import org.openautomaker.base.printerControl.PrintJob;
import org.openautomaker.base.printerControl.PrintJobRejectedException;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.PurgeRequiredException;
import org.openautomaker.base.printerControl.comms.commands.GCodeConstants;
import org.openautomaker.base.printerControl.comms.commands.MacroPrintException;
import org.openautomaker.base.printerControl.model.Head.HeadType;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionActions;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationNozzleHeightActions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationNozzleHeightTransitions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationNozzleOpeningActions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationNozzleOpeningTransitions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationSingleNozzleHeightActions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationSingleNozzleHeightTransitions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationXAndYActions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.CalibrationXAndYTransitions;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.NozzleHeightStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.NozzleOpeningStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.SingleNozzleHeightStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.XAndYStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.purge.PurgeActions;
import org.openautomaker.base.printerControl.model.statetransitions.purge.PurgeStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.purge.PurgeTransitions;
import org.openautomaker.base.services.gcodegenerator.GCodeGeneratorResult;
import org.openautomaker.base.services.printing.DatafileSendAlreadyInProgress;
import org.openautomaker.base.services.printing.DatafileSendNotInitialised;
import org.openautomaker.base.task_executor.Cancellable;
import org.openautomaker.base.task_executor.SimpleCancellable;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.base.task_executor.TaskResponder;
import org.openautomaker.base.task_executor.TaskResponse;
import org.openautomaker.base.utils.AxisSpecifier;
import org.openautomaker.base.utils.PrinterUtils;
import org.openautomaker.base.utils.RectangularBounds;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.base.utils.Math.MathUtils;
import org.openautomaker.base.utils.models.PrintableProject;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.PrinterType;
import org.openautomaker.environment.preference.root.PrintJobsPathPreference;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import celtech.roboxbase.comms.CommandInterface;
import celtech.roboxbase.comms.PrinterStatusConsumer;
import celtech.roboxbase.comms.events.ErrorConsumer;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.BusyStatus;
import celtech.roboxbase.comms.remote.EEPROMState;
import celtech.roboxbase.comms.remote.PauseStatus;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import celtech.roboxbase.comms.remote.clear.SuitablePrintJob;
import celtech.roboxbase.comms.rx.AckResponse;
import celtech.roboxbase.comms.rx.DebugDataResponse;
import celtech.roboxbase.comms.rx.FirmwareError;
import celtech.roboxbase.comms.rx.FirmwareResponse;
import celtech.roboxbase.comms.rx.GCodeDataResponse;
import celtech.roboxbase.comms.rx.HeadEEPROMDataResponse;
import celtech.roboxbase.comms.rx.HoursCounterResponse;
import celtech.roboxbase.comms.rx.ListFilesResponse;
import celtech.roboxbase.comms.rx.PrinterIDResponse;
import celtech.roboxbase.comms.rx.ReelEEPROMDataResponse;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.rx.SendFile;
import celtech.roboxbase.comms.rx.StatusResponse;
import celtech.roboxbase.comms.tx.FormatHeadEEPROM;
import celtech.roboxbase.comms.tx.ListFiles;
import celtech.roboxbase.comms.tx.PausePrint;
import celtech.roboxbase.comms.tx.QueryFirmwareVersion;
import celtech.roboxbase.comms.tx.ReadHeadEEPROM;
import celtech.roboxbase.comms.tx.ReadPrinterID;
import celtech.roboxbase.comms.tx.ReadSendFileReport;
import celtech.roboxbase.comms.tx.RoboxTxPacket;
import celtech.roboxbase.comms.tx.RoboxTxPacketFactory;
import celtech.roboxbase.comms.tx.SetAmbientLEDColour;
import celtech.roboxbase.comms.tx.SetDFeedRateMultiplier;
import celtech.roboxbase.comms.tx.SetDFilamentInfo;
import celtech.roboxbase.comms.tx.SetEFeedRateMultiplier;
import celtech.roboxbase.comms.tx.SetEFilamentInfo;
import celtech.roboxbase.comms.tx.SetReelLEDColour;
import celtech.roboxbase.comms.tx.SetTemperatures;
import celtech.roboxbase.comms.tx.StatusRequest;
import celtech.roboxbase.comms.tx.TxPacketTypeEnum;
import celtech.roboxbase.comms.tx.WriteHeadEEPROM;
import celtech.roboxbase.comms.tx.WritePrinterID;
import celtech.roboxbase.comms.tx.WriteReel0EEPROM;
import celtech.roboxbase.comms.tx.WriteReel1EEPROM;
import jakarta.annotation.Nullable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Point3D;
import javafx.scene.paint.Color;

/**
 *
 * @author Ian
 */
public final class HardwarePrinter implements Printer, ErrorConsumer {
	private static final String ROOT_APPLICATION_SHORT_NAME = "Root";
	private static final int MAX_RETAINED_PRINT_JOBS = 32;

	private static final Logger LOGGER = LogManager.getLogger();

	protected final ObjectProperty<PrinterStatus> printerStatus = new SimpleObjectProperty<>(PrinterStatus.IDLE);
	protected BooleanProperty macroIsInterruptible = new SimpleBooleanProperty(false);

	protected PrinterStatusConsumer printerStatusConsumer;
	protected CommandInterface commandInterface;

	private NumberFormat threeDPformatter;

	/*
	 * State machine data
	 */
	private final BooleanProperty canRemoveHead = new SimpleBooleanProperty(false);
	private final BooleanProperty canPurgeHead = new SimpleBooleanProperty(false);
	private final BooleanProperty mustPurgeHead = new SimpleBooleanProperty(false);
	private final BooleanProperty canInitiateNewState = new SimpleBooleanProperty(false);
	private final BooleanProperty canPrint = new SimpleBooleanProperty(false);
	private final BooleanProperty canOpenCloseNozzle = new SimpleBooleanProperty(false);
	private final BooleanProperty canPause = new SimpleBooleanProperty(false);
	private final BooleanProperty canResume = new SimpleBooleanProperty(false);
	private final BooleanProperty canRunMacro = new SimpleBooleanProperty(false);
	private final BooleanProperty canCancel = new SimpleBooleanProperty(false);
	private final BooleanProperty canOpenDoor = new SimpleBooleanProperty(false);
	private final BooleanProperty canCalibrateHead = new SimpleBooleanProperty(false);
	private final BooleanProperty canCalibrateNozzleHeight = new SimpleBooleanProperty(false);
	private final BooleanProperty canCalibrateXYAlignment = new SimpleBooleanProperty(false);
	private final BooleanProperty canCalibrateNozzleOpening = new SimpleBooleanProperty(false);
	private final BooleanProperty usedExtrudersLoaded = new SimpleBooleanProperty(false);

	private boolean headIntegrityChecksInhibited = false;

	/*
	 * Physical model Not sure how discovery will work, but we'll assume the following: - one identity - two reel controllers - one head controller - two extruder controllers We'll use EEPROM status to drive whether they're enabled or not
	 */
	private final PrinterIdentity printerIdentity = new PrinterIdentity();
	private final PrinterAncillarySystems printerAncillarySystems = new PrinterAncillarySystems();
	private final ObjectProperty<PrinterDefinitionFile> printerConfiguration = new SimpleObjectProperty<>(null);
	private final ObjectProperty<PrinterEdition> printerEdition = new SimpleObjectProperty<>(null);
	private final ObjectProperty<Head> head = new SimpleObjectProperty<>(null);
	private final ObservableMap<Integer, Reel> reels = FXCollections.observableHashMap();
	private final ObservableMap<Integer, Filament> effectiveFilaments = FXCollections.observableHashMap();
	private final ObservableList<Extruder> extruders = FXCollections.observableArrayList();

	private final ObjectProperty<PrinterConnection> printerConnection = new SimpleObjectProperty<>();

	private ObjectProperty<EEPROMState> lastHeadEEPROMState = new SimpleObjectProperty<>(EEPROMState.NOT_PRESENT);
	private final int maxNumberOfReels = 2;
	private final boolean[] reelEEPROMCheck = new boolean[maxNumberOfReels];
	private ObservableList<EEPROMState> lastReelEEPROMState = FXCollections.observableArrayList(EEPROMState.NOT_PRESENT, EEPROMState.NOT_PRESENT);

	/*
	 * Temperature-related data
	 */
	//TODO: Remove
	//private long lastTimestamp = System.currentTimeMillis();

	private final ObservableList<String> gcodeTranscript = FXCollections.observableArrayList();

	/*
	 * Data used for data chunk management
	 */
	private int dataFileSequenceNumber = 0;
	private int dataFileSequenceNumberStartPoint = 0;
	private static final int bufferSize = 512;
	private final StringBuffer outputBuffer = new StringBuffer(bufferSize);
	private boolean printInitiated = false;

	protected final ObjectProperty<PauseStatus> pauseStatus = new SimpleObjectProperty<>(PauseStatus.NOT_PAUSED);
	protected final ObjectProperty<BusyStatus> busyStatus = new SimpleObjectProperty<>(BusyStatus.NOT_BUSY);
	protected final IntegerProperty printJobLineNumber = new SimpleIntegerProperty(0);
	protected final StringProperty printJobID = new SimpleStringProperty("");

	private PrintEngine printEngine;

	private final String firstExtruderLetter = "E";
	private final int firstExtruderNumber = 0;
	private final String secondExtruderLetter = "D";
	private final int secondExtruderNumber = 1;

	/*
	 * Error handling
	 */
	private final Map<ErrorConsumer, List<FirmwareError>> errorConsumers = new WeakHashMap<>();
	private boolean processErrors = false;
	private final FilamentLoadedGetter filamentLoadedGetter;
	private Set<FirmwareError> suppressedFirmwareErrors = new HashSet<>();
	private boolean repairCorruptEEPROMData = true;
	private boolean doNotCheckForPresenceOfHead = false;

	/**
	 * Calibration state managers
	 */
	private NozzleHeightStateTransitionManager calibrationHeightManager;
	private SingleNozzleHeightStateTransitionManager calibrationSingleNozzleHeightManager;
	private NozzleOpeningStateTransitionManager calibrationOpeningManager;
	private XAndYStateTransitionManager calibrationAlignmentManager;

	private BooleanProperty headPowerOnFlag = new SimpleBooleanProperty(false);

	private boolean inCommissioningMode = false;

	/*
	 * Error handling Some errors should be retained until cleared by the user Keep an observable list...
	 */
	private final ObservableList<FirmwareError> activeErrors = FXCollections.observableArrayList();
	/*
	 * The Root interface needs to know about all the current errors (except maybe the suppressed ones). The active error list only contains errors for which "isRequireUserToClear()" returns true, although it isn't obvious why some are set this way and
	 * others are not. To avoid disturbing code that was not well understood, a second list "currentErrors" was created which contains all the uncleared errors, except for the suppressed ones. This is the list used by the Root interface.
	 */
	private final ObservableList<FirmwareError> currentErrors = FXCollections.observableArrayList();

	private StatusResponse latestStatusResponse = null;
	private AckResponse latestErrorResponse = null;

	@Override
	public CommandInterface getCommandInterface() {
		return commandInterface;
	}

	private int initalStatusCount = 0;

	/**
	 * A FilamentLoadedGetter can be provided to the HardwarePriner to provide a way to override the detection of whether a filament is loaded or not on a given extruder.
	 */
	public interface FilamentLoadedGetter {

		public boolean getFilamentLoaded(StatusResponse statusResponse, int extruderNumber);
	}

	private final FilamentContainer.FilamentDatabaseChangesListener filamentDatabaseChangesListener;

	//Dependencies
	private final I18N i18n;
	private final TaskExecutor taskExecutor;
	private final FilamentContainer filamentContainer;
	private final SystemNotificationManager systemNotificationManager;
	private final PrinterUtils printerUtils;
	private final PurgeActionsFactory purgeActionsFactory;
	private final PrinterColourMap printerColourMap;
	private final PrintJobCleaner printJobCleaner;
	private final CalibrationNozzleHeightActionsFactory calibrationNozzleHeightActionsFactory;
	private final PurgeStateTransitionManagerFactory purgeStateTransitionManagerFactory;
	private final CalibrationNozzleOpeningActionsFactory calibrationNozzleOpeningActionsFactory;
	private final CalibrationXAndYActionsFactory calibrationXAndYActionsFactory;
	private final XAndYStateTransitionManagerFactory xAndYStateTransitionManagerFactory;
	private final NozzleHeightStateTransitionManagerFactory nozzleHeightStateTransitionManagerFactory;
	private final SingleNozzleHeightStateTransitionManagerFactory singleNozzleHeightStateTransitionManagerFactory;
	private final NozzleOpeningStateTransitionManagerFactory nozzleOpeningStateTransitionManagerFactory;
	private final PrintJobsPathPreference printJobsPathPreference;
	private final PrintJobFactory printJobFactory;
	private final HeadContainer headContainer;
	private final HeadFactory headFactory;
	private final ReelFactory reelFactory;

	@AssistedInject
	public HardwarePrinter(
			I18N i18n,
			TaskExecutor taskExecutor,
			FilamentContainer filamentContainer,
			SystemNotificationManager systemNotificationManager,
			PrinterUtils printerUtils,
			PurgeActionsFactory purgeActionsFactory,
			PrinterColourMap printerColourMap,
			PrintEngineFactory printEngineFactory,
			PrintJobCleaner printJobCleaner,
			CalibrationNozzleHeightActionsFactory calibrationNozzleHeightActionsFactory,
			PurgeStateTransitionManagerFactory purgeStateTransitionManagerFactory,
			CalibrationNozzleOpeningActionsFactory calibrationNozzleOpeningActionsFactory,
			CalibrationXAndYActionsFactory calibrationXAndYActionsFactory,
			XAndYStateTransitionManagerFactory xAndYStateTransitionManagerFactory,
			NozzleHeightStateTransitionManagerFactory nozzleHeightStateTransitionManagerFactory,
			SingleNozzleHeightStateTransitionManagerFactory singleNozzleHeightStateTransitionManagerFactory,
			NozzleOpeningStateTransitionManagerFactory nozzleOpeningStateTransitionManagerFactory,
			PrintJobsPathPreference printJobsPathPreference,
			PrintJobFactory printJobFactory,
			HeadContainer headContainer,
			HeadFactory headFactory,
			ReelFactory reelFactory,
			@Nullable @Assisted PrinterStatusConsumer printerStatusConsumer,
			@Assisted CommandInterface commandInterface) {

		// The default FilamentLoadedGetter just checks the data in the statusResponse
		this(
				i18n,
				taskExecutor,
				filamentContainer,
				systemNotificationManager,
				printerUtils,
				purgeActionsFactory,
				printerColourMap,
				printEngineFactory,
				printJobCleaner,
				calibrationNozzleHeightActionsFactory,
				purgeStateTransitionManagerFactory,
				calibrationNozzleOpeningActionsFactory,
				calibrationXAndYActionsFactory,
				xAndYStateTransitionManagerFactory,
				nozzleHeightStateTransitionManagerFactory,
				singleNozzleHeightStateTransitionManagerFactory,
				nozzleOpeningStateTransitionManagerFactory,
				printJobsPathPreference,
				printJobFactory,
				headContainer,
				headFactory,
				reelFactory,
				printerStatusConsumer,
				commandInterface,
				(StatusResponse statusResponse, int extruderNumber) -> {
					if (extruderNumber == 1)
						return statusResponse.isFilament1SwitchStatus();

					return statusResponse.isFilament2SwitchStatus();

				},
				false);
	}

	@AssistedInject
	public HardwarePrinter(
			I18N i18n,
			TaskExecutor taskExecutor,
			FilamentContainer filamentContainer,
			SystemNotificationManager systemNotificationManager,
			PrinterUtils printerUtils,
			PurgeActionsFactory purgeActionsFactory,
			PrinterColourMap printerColourMap,
			PrintEngineFactory printEngineFactory,
			PrintJobCleaner printJobCleaner,
			CalibrationNozzleHeightActionsFactory calibrationNozzleHeightActionsFactory,
			PurgeStateTransitionManagerFactory purgeStateTransitionManagerFactory,
			CalibrationNozzleOpeningActionsFactory calibrationNozzleOpeningActionsFactory,
			CalibrationXAndYActionsFactory calibrationXAndYActionsFactory,
			XAndYStateTransitionManagerFactory xAndYStateTransitionManagerFactory,
			NozzleHeightStateTransitionManagerFactory nozzleHeightStateTransitionManagerFactory,
			SingleNozzleHeightStateTransitionManagerFactory singleNozzleHeightStateTransitionManagerFactory,
			NozzleOpeningStateTransitionManagerFactory nozzleOpeningStateTransitionManagerFactory,
			PrintJobsPathPreference printJobsPathPreference,
			PrintJobFactory printJobFactory,
			HeadContainer headContainer,
			HeadFactory headFactory,
			ReelFactory reelFactory,
			@Nullable @Assisted PrinterStatusConsumer printerStatusConsumer,
			@Assisted CommandInterface commandInterface,
			@Assisted FilamentLoadedGetter filamentLoadedGetter,
			@Assisted boolean doNotCheckForPresenceOfHead) {

		this.i18n = i18n;
		this.taskExecutor = taskExecutor;
		this.filamentContainer = filamentContainer;
		this.systemNotificationManager = systemNotificationManager;
		this.printerUtils = printerUtils;
		this.purgeActionsFactory = purgeActionsFactory;
		this.printerColourMap = printerColourMap;
		this.printJobCleaner = printJobCleaner;
		this.calibrationNozzleHeightActionsFactory = calibrationNozzleHeightActionsFactory;
		this.purgeStateTransitionManagerFactory = purgeStateTransitionManagerFactory;
		this.calibrationNozzleOpeningActionsFactory = calibrationNozzleOpeningActionsFactory;
		this.calibrationXAndYActionsFactory = calibrationXAndYActionsFactory;
		this.xAndYStateTransitionManagerFactory = xAndYStateTransitionManagerFactory;
		this.nozzleHeightStateTransitionManagerFactory = nozzleHeightStateTransitionManagerFactory;
		this.singleNozzleHeightStateTransitionManagerFactory = singleNozzleHeightStateTransitionManagerFactory;
		this.nozzleOpeningStateTransitionManagerFactory = nozzleOpeningStateTransitionManagerFactory;
		this.printJobsPathPreference = printJobsPathPreference;
		this.printJobFactory = printJobFactory;
		this.headContainer = headContainer;
		this.headFactory = headFactory;
		this.reelFactory = reelFactory;

		this.printerStatusConsumer = printerStatusConsumer;
		this.commandInterface = commandInterface;
		this.filamentLoadedGetter = filamentLoadedGetter;
		this.doNotCheckForPresenceOfHead = doNotCheckForPresenceOfHead;

		printEngine = printEngineFactory.create(this);

		extruders.add(firstExtruderNumber, new Extruder(firstExtruderLetter));
		extruders.add(secondExtruderNumber, new Extruder(secondExtruderLetter));
		effectiveFilaments.put(0, FilamentContainer.UNKNOWN_FILAMENT);
		effectiveFilaments.put(1, FilamentContainer.UNKNOWN_FILAMENT);

		setupBindings();
		setupFilamentDatabaseChangeListeners();

		threeDPformatter = DecimalFormat.getNumberInstance(Locale.UK);
		threeDPformatter.setMaximumFractionDigits(3);
		threeDPformatter.setGroupingUsed(false);

		commandInterface.setPrinter(this);

		registerErrorConsumerAllErrors(this);

		head.addListener(new ChangeListener<Head>() {
			@Override
			public void changed(ObservableValue<? extends Head> ov, Head oldHeadValue, Head newHeadValue) {
				canCalibrateNozzleOpening.unbind();
				canCalibrateNozzleOpening.set(false);
				canCalibrateNozzleHeight.unbind();
				canCalibrateNozzleHeight.set(false);
				canCalibrateXYAlignment.unbind();
				canCalibrateXYAlignment.set(false);

				if (newHeadValue != null) {
					if (head.get().valveTypeProperty().get() == Head.ValveType.FITTED) {
						canOpenCloseNozzle.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).or(pauseStatus.isEqualTo(PauseStatus.PAUSED)));

						canCalibrateNozzleOpening.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(extrudersProperty().get(0).filamentLoadedProperty()).and(Bindings.valueAt(reels, 0).isNotNull())
								.and(head.get().headTypeProperty().isEqualTo(HeadType.SINGLE_MATERIAL_HEAD).or(extrudersProperty().get(1).filamentLoadedProperty().and(Bindings.valueAt(reels, 1).isNotNull()))));
					}
					canCalibrateXYAlignment
							.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(Bindings.size(head.get().getNozzles()).greaterThan(1)).and(extrudersProperty().get(0).filamentLoadedProperty()).and(Bindings.valueAt(reels, 0).isNotNull())
									.and(head.get().headTypeProperty().isEqualTo(HeadType.SINGLE_MATERIAL_HEAD).or(extrudersProperty().get(1).filamentLoadedProperty().and(Bindings.valueAt(reels, 1).isNotNull()))));
					Bindings.size(head.get().getNozzles()).greaterThan(1);
					canCalibrateNozzleHeight.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(extrudersProperty().get(0).filamentLoadedProperty()).and(Bindings.valueAt(reels, 0).isNotNull())
							.and(head.get().headTypeProperty().isEqualTo(HeadType.SINGLE_MATERIAL_HEAD).or(extrudersProperty().get(1).filamentLoadedProperty().and(Bindings.valueAt(reels, 1).isNotNull()))));
				}
			}
		});

		filamentDatabaseChangesListener = (String filamentId) -> {
			for (Map.Entry<Integer, Reel> posReel : reels.entrySet()) {
				if (posReel.getValue().filamentIDProperty().get().equals(filamentId)) {
					try {
						Filament changedFilament = filamentContainer.getFilamentByID(filamentId);
						if (changedFilament != null) {
							LOGGER.debug("Update reel with updated filament data");
							transmitWriteReelEEPROM(posReel.getKey(), changedFilament);
						}
					}
					catch (RoboxCommsException ex) {
						LOGGER.error("Unable to program reel with update filament of id: " + filamentId);
					}
				}
			}
		};
	}

	private void setupBindings() {
		extruders.stream().forEach(extruder -> extruder.canEject
				.bind((printerStatus.isEqualTo(PrinterStatus.IDLE).or(pauseStatus.isEqualTo(PauseStatus.PAUSED))).or(printerStatus.isEqualTo(PrinterStatus.REMOVING_HEAD)).and(extruder.isFitted).and(extruder.filamentLoaded)));

		// TODO canPrint ought to take account of lid and filament
		canPrint.bind(head.isNotNull().and(printerStatus.isEqualTo(PrinterStatus.IDLE)).and(busyStatus.isEqualTo(BusyStatus.NOT_BUSY)));

		canOpenCloseNozzle.set(false);
		canCalibrateNozzleOpening.set(false);
		canCalibrateNozzleHeight.set(false);
		canCalibrateXYAlignment.set(false);

		canInitiateNewState.bind(printerStatus.isEqualTo(PrinterStatus.IDLE));

		canCancel.bind(pauseStatus.isEqualTo(PauseStatus.PAUSED)
				// .or(printEngine.postProcessorService.runningProperty())
				// .or(printEngine.slicerService.runningProperty())
				.or(pauseStatus.isEqualTo(PauseStatus.SELFIE_PAUSE)).or(printEngine.transferGCodeToPrinterService.runningProperty()).or(printerStatus.isEqualTo(PrinterStatus.PURGING_HEAD))
				.or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_ALIGNMENT)).or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_HEIGHT)).or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_OPENING))
				.or(printerStatus.isEqualTo(PrinterStatus.RUNNING_MACRO_FILE).and(printEngine.macroBeingRun.isNotEqualTo(Macro.CANCEL_PRINT))));

		canRunMacro.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).or(pauseStatus.isEqualTo(PauseStatus.PAUSED)).or(pauseStatus.isEqualTo(PauseStatus.SELFIE_PAUSE))
				.or(printerStatus.isEqualTo(PrinterStatus.RUNNING_MACRO_FILE).and(printEngine.macroBeingRun.isEqualTo(Macro.CANCEL_PRINT))).or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_ALIGNMENT))
				.or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_HEIGHT)).or(printerStatus.isEqualTo(PrinterStatus.CALIBRATING_NOZZLE_OPENING)).or(printerStatus.isEqualTo(PrinterStatus.PURGING_HEAD)));

		canPause.bind(pauseStatus.isNotEqualTo(PauseStatus.PAUSED).and(pauseStatus.isNotEqualTo(PauseStatus.SELFIE_PAUSE)).and(pauseStatus.isNotEqualTo(PauseStatus.PAUSE_PENDING))
				.and(printerStatus.isEqualTo(PrinterStatus.PRINTING_PROJECT).or(pauseStatus.isEqualTo(PauseStatus.RESUME_PENDING))));

		canCalibrateHead.bind(head.isNotNull().and(printerStatus.isEqualTo(PrinterStatus.IDLE)).and(printEngine.highIntensityCommsInProgressProperty().not()));

		canRemoveHead.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(busyStatus.isEqualTo(BusyStatus.NOT_BUSY)).and(printEngine.highIntensityCommsInProgressProperty().not()));

		canPurgeHead.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(busyStatus.isEqualTo(BusyStatus.NOT_BUSY)).and(printEngine.highIntensityCommsInProgressProperty().not())
				.and(extruders.get(firstExtruderNumber).filamentLoaded.or(extruders.get(secondExtruderNumber).filamentLoaded)));

		canOpenDoor.bind(printerStatus.isEqualTo(PrinterStatus.IDLE).and(printEngine.highIntensityCommsInProgressProperty().not()).and(busyStatus.isEqualTo(BusyStatus.NOT_BUSY)));

		// This is rebound when a project is printed and depends on the extruders being used
		usedExtrudersLoaded.bind(extruders.get(0).filamentLoaded);

		canResume.bind((pauseStatus.isEqualTo(PauseStatus.PAUSED).or(pauseStatus.isEqualTo(PauseStatus.PAUSE_PENDING))).or(pauseStatus.isEqualTo(PauseStatus.SELFIE_PAUSE)).and(usedExtrudersLoaded));
	}

	/**
	 * If the filament details change for a filament currently on a reel, then the reel should be immediately updated with the new details.
	 */
	private void setupFilamentDatabaseChangeListeners() {
		filamentContainer.addFilamentDatabaseChangesListener(filamentDatabaseChangesListener);
	}

	@Override
	public void setPrinterStatus(PrinterStatus printerStatus) {
		taskExecutor.runOnGUIThread(() -> {
			boolean okToChangeState = true;
			LOGGER.debug("Status was " + this.printerStatus.get().name() + " and is going to " + printerStatus);
			switch (printerStatus) {
				case IDLE:
					break;
				default:
					; //Added to remove warning
			}

			if (okToChangeState) {
				this.printerStatus.set(printerStatus);
			}
			LOGGER.debug("Setting printer status to " + printerStatus);
		});
	}

	@Override
	public ReadOnlyObjectProperty<PrinterStatus> printerStatusProperty() {
		return printerStatus;
	}

	@Override
	public PrinterIdentity getPrinterIdentity() {
		return printerIdentity;
	}

	@Override
	public PrinterAncillarySystems getPrinterAncillarySystems() {
		return printerAncillarySystems;
	}

	@Override
	public ReadOnlyObjectProperty<PrinterDefinitionFile> printerConfigurationProperty() {
		return printerConfiguration;
	}

	@Override
	public PrinterType findPrinterType() {
		if (printerConfigurationProperty().get() == null) {
			return null;
		}

		return printerConfigurationProperty().get().getPrinterType();
	}

	@Override
	public void setPrinterConfiguration(PrinterDefinitionFile printerConfigurationFile) {
		this.printerConfiguration.set(printerConfigurationFile);
	}

	@Override
	public ReadOnlyObjectProperty<PrinterEdition> printerEditionProperty() {
		return printerEdition;
	}

	@Override
	public void setPrinterEdition(PrinterEdition printerEdition) {
		this.printerEdition.set(printerEdition);
	}

	@Override
	public ReadOnlyObjectProperty<PrinterConnection> printerConnectionProperty() {
		return printerConnection;
	}

	@Override
	public void setPrinterConnection(PrinterConnection printerConnection) {
		this.printerConnection.set(printerConnection);
	}

	@Override
	public Point3D getPrintVolumeCentre() {
		if (printerConfiguration.get() != null) {
			return new Point3D(printerConfiguration.get().getPrintVolumeWidth() / 2, printerConfiguration.get().getPrintVolumeDepth() / 2, printerConfiguration.get().getPrintVolumeHeight() / 2);
		}
		else {
			return Point3D.ZERO;
		}
	}

	@Override
	public boolean isBiggerThanPrintVolume(RectangularBounds bounds) {
		boolean biggerThanPrintArea = false;

		if (printerConfiguration.get() != null) {
			double xSize = bounds.getWidth();
			double ySize = bounds.getHeight();
			double zSize = bounds.getDepth();

			double epsilon = 0.001;

			if (MathUtils.compareDouble(xSize, printerConfiguration.get().getPrintVolumeWidth(), epsilon) == MathUtils.MORE_THAN
					|| MathUtils.compareDouble(ySize, printerConfiguration.get().getPrintVolumeHeight(), epsilon) == MathUtils.MORE_THAN
					|| MathUtils.compareDouble(zSize, printerConfiguration.get().getPrintVolumeDepth(), epsilon) == MathUtils.MORE_THAN) {
				biggerThanPrintArea = true;
			}
		}

		return biggerThanPrintArea;
	}

	@Override
	public ReadOnlyObjectProperty<Head> headProperty() {
		return head;
	}

	@Override
	public ObservableMap<Integer, Reel> reelsProperty() {
		return reels;
	}

	@Override
	public ObservableMap<Integer, Filament> effectiveFilamentsProperty() {
		return effectiveFilaments;
	}

	@Override
	public void overrideFilament(int reelNumber, Filament filament) {
		effectiveFilaments.put(reelNumber, filament);
		if (getCommandInterface() instanceof RoboxRemoteCommandInterface) {
			// We need to propogate this to the Root that is managing the printer...
			try {
				((RoboxRemoteCommandInterface) getCommandInterface()).overrideFilament(reelNumber, filament);
			}
			catch (RoboxCommsException ex) {
				LOGGER.warn("Comms exception whilst attempting to override filament on remote printer");
			}
		}
	}

	@Override
	public ObservableList<Extruder> extrudersProperty() {
		return extruders;
	}

	@Override
	public ReadOnlyIntegerProperty printJobLineNumberProperty() {
		return printJobLineNumber;
	}

	@Override
	public ReadOnlyStringProperty printJobIDProperty() {
		return printJobID;
	}

	@Override
	public ReadOnlyObjectProperty<PauseStatus> pauseStatusProperty() {
		return pauseStatus;
	}

	@Override
	public ReadOnlyObjectProperty<BusyStatus> busyStatusProperty() {
		return busyStatus;
	}

	@Override
	public ReadOnlyBooleanProperty headPowerOnFlagProperty() {
		return headPowerOnFlag;
	}

	@Override
	public PrintEngine getPrintEngine() {
		return printEngine;
	}

	/*
	 * Remove head
	 */
	@Override
	public final ReadOnlyBooleanProperty canRemoveHeadProperty() {
		return canRemoveHead;
	}

	@Override
	public void removeHead(TaskResponder responder, boolean safetyFeaturesRequired) throws PrinterException {
		if (!canRemoveHead.get()) {
			throw new PrinterException("Head remove not permitted");
		}

		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = doRemoveHeadActivity(cancellable, safetyFeaturesRequired);

			taskExecutor.respondOnGUIThread(responder, success, "Ready to remove head");

			setPrinterStatus(PrinterStatus.IDLE);

		}, "Removing head").start();
	}

	protected boolean doRemoveHeadActivity(Cancellable cancellable, boolean safetyFeaturesRequired) {
		boolean success = false;

		try {
			printEngine.runMacroPrintJob(Macro.REMOVE_HEAD, true, false, safetyFeaturesRequired);
			printerUtils.waitOnMacroFinished(this, cancellable);
			success = true;
		}
		catch (MacroPrintException ex) {
			LOGGER.error("Failed to run remove head macro: " + ex.getMessage());
		}

		return success;
	}

	/*
	 * Purge head
	 */
	@Override
	public final ReadOnlyBooleanProperty canPurgeHeadProperty() {
		return canPurgeHead;
	}

	protected final FloatProperty purgeTemperatureProperty = new SimpleFloatProperty(0);

	/**
	 * Reset the purge temperature for the given head, printer settings and nozzle heater number.
	 *
	 * @param headToWrite
	 * @param nozzleHeaterNumber
	 */
	@Override
	public void resetPurgeTemperatureForNozzleHeater(Head headToWrite, int nozzleHeaterNumber) {
		Filament settingsFilament = null;
		Head headToOutput = headToWrite.clone();

		if (headToOutput.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			if (nozzleHeaterNumber == 0) {
				settingsFilament = effectiveFilamentsProperty().get(1);
			}
			else if (nozzleHeaterNumber == 1) {
				settingsFilament = effectiveFilamentsProperty().get(0);
			}
			else {
				throw new RuntimeException("dont know which filament to use for nozzle heater  + " + nozzleHeaterNumber);
			}
		}
		else {
			// There's only one heater on a single material head
			if (nozzleHeaterNumber == 0) {
				settingsFilament = effectiveFilamentsProperty().get(0);
			}
		}

		if (settingsFilament != null) {
			float reelNozzleTemperature = settingsFilament.getNozzleTemperature();

			headToOutput.nozzleHeaters.get(nozzleHeaterNumber).lastFilamentTemperature.set(reelNozzleTemperature);

			try {
				writeHeadEEPROM(headToOutput);
				readHeadEEPROM(false);
			}
			catch (RoboxCommsException ex) {
				LOGGER.warn("Failed to write purge temperature");
			}
		}
	}

	/*
	 * Print
	 */
	@Override
	public final ReadOnlyBooleanProperty canPrintProperty() {
		return canPrint;
	}

	@Override
	public ReadOnlyBooleanProperty canOpenCloseNozzleProperty() {
		return canOpenCloseNozzle;
	}

	@Override
	public ReadOnlyBooleanProperty canCalibrateNozzleHeightProperty() {
		return canCalibrateNozzleHeight;
	}

	@Override
	public ReadOnlyBooleanProperty canCalibrateXYAlignmentProperty() {
		return canCalibrateXYAlignment;
	}

	@Override
	public ReadOnlyBooleanProperty canCalibrateNozzleOpeningProperty() {
		return canCalibrateNozzleOpening;
	}

	/**
	 * Calibrate head
	 */
	@Override
	public ReadOnlyBooleanProperty canCalibrateHeadProperty() {
		return canCalibrateHead;
	}

	/*
	 * Cancel
	 */
	@Override
	public final ReadOnlyBooleanProperty canCancelProperty() {
		return canCancel;
	}

	private void doCancel(TaskResponder responder, boolean safetyFeaturesRequired) throws PrinterException {
		if (!canCancel.get()) {
			throw new PrinterException("Cancel not permitted: printer status is " + printerStatus);
		}

		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = doAbortActivity(cancellable, safetyFeaturesRequired);

			if (responder != null) {
				taskExecutor.respondOnGUIThread(responder, success, "Abort complete");
			}

			setPrinterStatus(PrinterStatus.IDLE);

		}, "Aborting").start();
	}

	@Override
	public void cancel(TaskResponder responder, boolean safetyFeaturesRequired) throws PrinterException {
		doCancel(responder, safetyFeaturesRequired);
	}

	@Override
	public void forcedCancel(TaskResponder responder) throws PrinterException {
		doCancel(responder, false);
	}

	private boolean doAbortActivity(Cancellable cancellable, boolean safetyFeaturesRequired) {
		boolean success = false;

		printEngine.stopAllServices();
		if (getCommandInterface() instanceof RoboxRemoteCommandInterface) {
			((RoboxRemoteCommandInterface) getCommandInterface()).cancelPrint(safetyFeaturesRequired);
		}
		else {

			switchBedHeaterOff();
			switchAllNozzleHeatersOff();

			RoboxTxPacket abortPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.ABORT_PRINT);
			try {
				LOGGER.info("Sending abort packet");
				AckResponse response = (AckResponse) commandInterface.writeToPrinter(abortPacket);
				LOGGER.info("Response = " + response.toString());
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Couldn't send abort command to printer");
			}
			printerUtils.waitOnBusy(this, cancellable);

			try {
				printEngine.runMacroPrintJob(Macro.CANCEL_PRINT, true, false, safetyFeaturesRequired);
				printerUtils.waitOnMacroFinished(this, cancellable);
				success = true;
			}
			catch (MacroPrintException ex) {
				LOGGER.error("Failed to run abort macro: " + ex.getMessage());
			}
		}
		return success;
	}

	/**
	 *
	 * @return
	 */
	@Override
	public final ReadOnlyBooleanProperty canPauseProperty() {
		return canPause;
	}

	@Override
	public void pause() throws PrinterException {
		if (!canPause.get()) {
			throw new PrintActionUnavailableException("Cannot pause at this time - printer status is " + printerStatus.get().name());
		}

		LOGGER.debug("Printer model asked to pause");

		try {
			PausePrint gcodePacket = (PausePrint) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.PAUSE_RESUME_PRINT);
			gcodePacket.setPause();

			commandInterface.writeToPrinter(gcodePacket);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Robox comms exception when sending resume print command " + ex);
		}
	}

	/**
	 *
	 * @return
	 */
	@Override
	public final ReadOnlyBooleanProperty canResumeProperty() {
		return canResume;
	}

	/**
	 *
	 * @throws PrinterException
	 */
	@Override
	public void resume() throws PrinterException {
		if (!canResume.get()) {
			throw new PrintActionUnavailableException("Cannot resume at this time");
		}

		try {
			PausePrint gcodePacket = (PausePrint) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.PAUSE_RESUME_PRINT);
			gcodePacket.setResume();

			commandInterface.writeToPrinter(gcodePacket);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Robox comms exception when sending resume print command " + ex);
		}
	}

	private void forcedResume() throws PrinterException {
		try {
			PausePrint gcodePacket = (PausePrint) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.PAUSE_RESUME_PRINT);
			gcodePacket.setResume();

			commandInterface.writeToPrinter(gcodePacket);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Robox comms exception when sending resume print command " + ex);
		}
	}

	/**
	 *
	 * @return
	 */
	@Override
	public int getDataFileSequenceNumber() {
		return dataFileSequenceNumber;
	}

	/**
	 *
	 * @return
	 */
	@Override
	public boolean isPrintInitiated() {
		return printInitiated;
	}

	@Override
	public void transferGCodeFileToPrinterAndCallbackWhenDone(Path fileName, TaskResponder<?> taskResponder) {
		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = false;

			try {
				printEngine.printGCodeFile(null, fileName, true, true, true);
				printerUtils.waitOnMacroFinished(this, cancellable);
				success = true;
			}
			catch (MacroPrintException ex) {
				LOGGER.error("Error transferring " + fileName + " to printer");
			}

			taskExecutor.respondOnGUIThread(taskResponder, success, "Complete");

		}, "Transfer to printer").start();
	}

	@Override
	public void executeGCodeFile(Path fileName, boolean canDisconnectDuringPrint) throws PrinterException {
		executeGCodeFile(null, fileName, canDisconnectDuringPrint);
	}

	@Override
	public void executeGCodeFile(String printJobName, Path fileName, boolean canDisconnectDuringPrint) throws PrinterException {
		if (!canRunMacro.get()) {
			LOGGER.error("Printer state is " + printerStatus.getName() + " when execute GCode called");
			throw new PrintActionUnavailableException("Execute GCode not available");
		}

		if (mustPurgeHead.get()) {
			throw new PurgeRequiredException("Cannot execute GCode - purge required");
		}

		boolean jobAccepted = false;

		try {
			jobAccepted = printEngine.printGCodeFile(printJobName, fileName, true, canDisconnectDuringPrint);
		}
		catch (MacroPrintException ex) {
			LOGGER.error("Failed to print GCode file " + fileName.toString() + " : " + ex.getMessage());
		}

		if (!jobAccepted) {
			throw new PrintJobRejectedException("Could not run GCode " + fileName.toString() + " in mode " + printerStatus.get().name());
		}
	}

	private void executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro macro, boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(macro, blockUntilFinished, cancellable, false, false, false);
	}

	private void executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro macro, boolean blockUntilFinished, Cancellable cancellable, boolean requireNozzle0, boolean requireNozzle1, boolean requireSafetyFeatures) throws PrinterException {
		if (canRunMacro.get()) {
			if (blockUntilFinished) {
				executeMacroWithoutPurgeCheck(macro, requireNozzle0, requireNozzle1, requireSafetyFeatures);
				printerUtils.waitOnMacroFinished(this, cancellable);
			}
			else {
				new Thread(() -> {
					try {
						executeMacroWithoutPurgeCheck(macro, requireNozzle0, requireNozzle1, requireSafetyFeatures);
						printerUtils.waitOnMacroFinished(this, cancellable);
					}
					catch (PrinterException ex) {
						LOGGER.error("PrinterException whilst invoking macro: " + ex.getMessage());
					}
				}, "Executing Macro " + macro.name()).start();
			}
		}
		else {
			LOGGER.error("Printer state is " + printerStatus.get().name());
			throw new PrintActionUnavailableException("Macro " + macro.name() + " not available");
		}
	}

	@Override
	public void homeAllAxes(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.HOME_ALL, blockUntilFinished, cancellable);
	}

	@Override
	public void purgeMaterial(boolean requireNozzle0, boolean requireNozzle1, boolean safetyFeaturesRequired, boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		boolean nozzle0Required = false;
		boolean nozzle1Required = false;

		// Prevent trying to purge second material on a single material head, as it can damage it.
		Head head = headProperty().get();
		if (head != null) {
			nozzle0Required = requireNozzle0;
			if (head.getNozzles().size() > 1 && head.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
				nozzle1Required = requireNozzle1;
			}
		}

		if (nozzle0Required || nozzle1Required) {
			Macro macro = Macro.PURGE_MATERIAL;

			executeMacroWithoutPurgeCheckAndWaitIfRequired(macro, blockUntilFinished, cancellable, nozzle0Required, nozzle1Required, safetyFeaturesRequired);
		}
	}

	@Override
	public void miniPurge(boolean blockUntilFinished, Cancellable cancellable, int nozzleNumber, boolean safetyFeaturesRequired) throws PrinterException {
		boolean requireNozzle0 = false;
		boolean requireNozzle1 = false;

		// Prevent trying to purge second material on a single material head, as it can damage it.
		Head head = headProperty().get();
		if (head != null) {
			if (nozzleNumber == 0) {
				requireNozzle0 = true;
			}
			else if (nozzleNumber == 1 && head.getNozzles().size() > 1 && head.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
				requireNozzle1 = true;
			}
		}

		if (requireNozzle0 || requireNozzle1) {
			Macro macro = Macro.MINI_PURGE;

			executeMacroWithoutPurgeCheckAndWaitIfRequired(macro, blockUntilFinished, cancellable, requireNozzle0, requireNozzle1, safetyFeaturesRequired);
		}
	}

	@Override
	public void testX(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.TEST_X, blockUntilFinished, cancellable);
	}

	@Override
	public void testY(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.TEST_Y, blockUntilFinished, cancellable);
	}

	@Override
	public void testZ(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.TEST_Z, blockUntilFinished, cancellable);
	}

	@Override
	public void levelGantry(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.LEVEL_GANTRY, blockUntilFinished, cancellable);
	}

	@Override
	public void levelGantryTwoPoints(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.LEVEL_GANTRY_TWO_POINTS, blockUntilFinished, cancellable);
	}

	@Override
	public void levelY(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.LEVEL_Y, blockUntilFinished, cancellable);
	}

	@Override
	public void ejectStuckMaterial(int nozzleNumber, boolean blockUntilFinished, Cancellable cancellable, boolean safetyFeaturesRequired) throws PrinterException {
		boolean nozzle0Required = false;
		boolean nozzle1Required = false;

		// Prevent trying to eject second material on a single material head, as it can damage it.
		Head head = headProperty().get();
		if (head != null) {
			if (nozzleNumber == 0) {
				nozzle0Required = true;
			}
			else if (nozzleNumber == 1 && head.getNozzles().size() > 1 && head.headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
				nozzle1Required = true;
			}
		}

		if (nozzle0Required || nozzle1Required) {
			executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.EJECT_STUCK_MATERIAL, blockUntilFinished, cancellable, nozzle0Required, nozzle1Required, safetyFeaturesRequired);
		}
	}

	@Override
	public void cleanNozzle(int nozzleNumber, boolean blockUntilFinished, Cancellable cancellable, boolean safetyFeaturesRequired) throws PrinterException {
		boolean nozzle0Required = nozzleNumber == 0;
		boolean nozzle1Required = nozzleNumber == 1;

		// Do nothing if there is no head, or the head does not have valves, or does not have the specified nozzle.
		Head head = headProperty().get();
		if (head != null && head.valveTypeProperty().get() == Head.ValveType.FITTED) {
			if (nozzleNumber == 0) {
				nozzle0Required = true;
			}
			else if (nozzleNumber == 1 && head.getNozzles().size() > 1) {
				nozzle1Required = true;
			}
		}

		if (nozzle0Required || nozzle1Required) {
			executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.CLEAN_NOZZLE, blockUntilFinished, cancellable, nozzle0Required, nozzle1Required, safetyFeaturesRequired);
		}
	}

	@Override
	public void speedTest(boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(Macro.SPEED_TEST, blockUntilFinished, cancellable);
	}

	@Override
	public void runCommissioningTest(Macro macro, Cancellable cancellable) throws PrinterException {
		runCommissioningTest(macro, cancellable, false, false);
	}

	@Override
	public void runCommissioningTest(Macro macro, Cancellable cancellable, boolean requireNozzle0, boolean requireNozzle1) throws PrinterException {
		executeMacroWithoutPurgeCheckAndWaitIfRequired(macro, true, cancellable, requireNozzle0, requireNozzle1, false);
	}

	@Override
	public void executeMacroWithoutPurgeCheck(Macro macro) throws PrinterException {
		executeMacroWithoutPurgeCheck(macro, false, false, false);
	}

	/**
	 *
	 * @param macro
	 * @param requireNozzle0
	 * @param requireNozzle1
	 * @param requireSafetyFeatures
	 * @throws PrinterException
	 */
	@Override
	public void executeMacroWithoutPurgeCheck(Macro macro, boolean requireNozzle0, boolean requireNozzle1, boolean requireSafetyFeatures) throws PrinterException {
		boolean jobAccepted = false;

		try {
			jobAccepted = printEngine.runMacroPrintJob(macro, requireNozzle0, requireNozzle1, requireSafetyFeatures);
		}
		catch (MacroPrintException ex) {
			LOGGER.error("Failed to macro: " + macro.name() + " reason:" + ex.getMessage());
		}

		if (!jobAccepted) {
			throw new PrintJobRejectedException("Macro " + macro.name() + " could not be run");
		}
	}

	//TODO: Tidy up
	//	private void forceExecuteMacroAsStream(String macroName, boolean blockUntilFinished, Cancellable cancellable) throws PrinterException {
	//		if (blockUntilFinished) {
	//			sendMacroFileBitByBit(macroName, cancellable);
	//		} else {
	//			new Thread(() -> {
	//				try {
	//					sendMacroFileBitByBit(macroName, cancellable);
	//				} catch (PrinterException ex) {
	//					LOGGER.error("PrinterException whilst invoking macro: " + ex.getMessage());
	//				}
	//			}, "Executing Macro " + macroName).start();
	//		}
	//	}

	//TODO: tidy up
	//	private void sendMacroFileBitByBit(String macroName, Cancellable cancellable) throws PrinterException {
	//		if (headProperty().get() != null) {
	//			try {
	//				ArrayList<String> macroContents = GCodeMacros.getMacroContents(macroName, Optional.of(findPrinterType()), headProperty().get().typeCodeProperty().get(), false, false, false);
	//				macroContents.forEach(line -> {
	//					String lineToOutput = SystemUtils.cleanGCodeForTransmission(line);
	//					if (!line.equals("")) {
	//						sendRawGCode(lineToOutput, false);
	//						printerUtils.waitOnBusy(this, cancellable);
	//					}
	//				});
	//			} catch (IOException | MacroLoadException ex) {
	//				throw new PrinterException("Failed to open macro file for streaming " + macroName);
	//			}
	//		} else {
	//			throw new PrinterException("Failed to open macro file for streaming " + macroName);
	//		}
	//	}

	@Override
	public void callbackWhenNotBusy(TaskResponder responder) {
		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = false;

			printerUtils.waitOnBusy(this, cancellable);
			success = true;

			taskExecutor.respondOnGUIThread(responder, success, "Complete");

		}, "Waiting until not busy").start();
	}

	/*
	 * Data transmission commands
	 */
	/**
	 *
	 * @param gcodeToSend
	 * @param addToTranscript
	 * @return
	 * @throws RoboxCommsException
	 */
	private String transmitDirectGCode(final String gcodeToSend, boolean addToTranscript) throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.EXECUTE_GCODE);

		String gcodeToSendWithLF = SystemUtils.cleanGCodeForTransmission(gcodeToSend) + "\n";

		gcodePacket.setMessagePayload(gcodeToSendWithLF);

		RoboxRxPacket rawResponse = commandInterface.writeToPrinter(gcodePacket);
		GCodeDataResponse response = (rawResponse instanceof GCodeDataResponse ? (GCodeDataResponse) rawResponse : null);

		if (addToTranscript) {
			taskExecutor.runOnGUIThread(new Runnable() {

				@Override
				public void run() {
					addToGCodeTranscript(gcodeToSendWithLF);
					if (response == null) {
						addToGCodeTranscript(i18n.t("gcodeEntry.errorMessage"));
					}
					else if (!response.getGCodeResponse().trim().equals("")) {
						addToGCodeTranscript(response.getGCodeResponse());
					}
				}
			});
		}

		return (response != null) ? response.getGCodeResponse() : null;
	}

	private boolean transmitDataFileStart(final String fileID, boolean jobCanBeReprinted) throws RoboxCommsException {
		RoboxTxPacket gcodePacket = null;

		if (jobCanBeReprinted) {
			gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SEND_PRINT_FILE_START);
		}
		else {
			gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.START_OF_DATA_FILE);
		}

		gcodePacket.setMessagePayload(fileID);

		AckResponse response = (AckResponse) commandInterface.writeToPrinter(gcodePacket);
		boolean success = false;
		// Only check for SD card errors here...
		success = !response.getFirmwareErrors().contains(FirmwareError.SD_CARD);

		return success;
	}

	private AckResponse transmitDataFileChunk(final String payloadData, final int sequenceNumber) throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.DATA_FILE_CHUNK);
		gcodePacket.setMessagePayload(payloadData);
		gcodePacket.setSequenceNumber(sequenceNumber);

		AckResponse response = (AckResponse) commandInterface.writeToPrinter(gcodePacket);
		dataFileSequenceNumber++;

		return response;
	}

	private AckResponse transmitDataFileEnd(final String payloadData, final int sequenceNumber) throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.END_OF_DATA_FILE);
		gcodePacket.setMessagePayload(payloadData);
		gcodePacket.setSequenceNumber(sequenceNumber);

		return (AckResponse) commandInterface.writeToPrinter(gcodePacket);
	}

	/**
	 *
	 * @return @throws RoboxCommsException
	 */
	@Override
	public AckResponse transmitReportErrors() throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.REPORT_ERRORS);

		return (AckResponse) commandInterface.writeToPrinter(gcodePacket);
	}

	/**
	 *
	 * @throws RoboxCommsException
	 */
	@Override
	public void transmitResetErrors() throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.RESET_ERRORS);

		commandInterface.writeToPrinter(gcodePacket);
	}

	/**
	 *
	 * @param firmwareID
	 * @throws PrinterException
	 */
	@Override
	public void transmitUpdateFirmware(final String firmwareID) throws PrinterException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.UPDATE_FIRMWARE);
		gcodePacket.setMessagePayload(firmwareID);

		try {
			commandInterface.writeToPrinter(gcodePacket);
		}
		catch (RoboxCommsException ex) {
			// We expect to see an exception here as the printer disconnects after an update...
			LOGGER.info("Post firmware update disconnect");
		}
	}

	private void transmitInitiatePrint(final String printJobUUID) throws RoboxCommsException {
		RoboxTxPacket gcodePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.INITIATE_PRINT);
		gcodePacket.setMessagePayload(printJobUUID);

		LOGGER.debug("Initiate Print sent to " + printerIdentity.printerFriendlyName.get() + " - Print Job " + printJobUUID + " starting ----------------------------------->");
		commandInterface.writeToPrinter(gcodePacket);
	}

	/**
	 *
	 * @return @throws celtech.printerControl.model.PrinterException
	 */
	@Override
	public AckResponse formatHeadEEPROM() throws PrinterException {
		return formatHeadEEPROM(false);
	}

	@Override
	public AckResponse formatHeadEEPROM(boolean dontPublishResult) throws PrinterException {
		if (!dontPublishResult) {
			head.set(null);
		}

		FormatHeadEEPROM formatHead = (FormatHeadEEPROM) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.FORMAT_HEAD_EEPROM);
		AckResponse response = null;
		try {
			response = (AckResponse) commandInterface.writeToPrinter(formatHead, dontPublishResult);
			LOGGER.debug("Head formatted");
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending format head");
			throw new PrinterException("Error formatting head");
		}

		return response;
	}

	/**
	 *
	 * @param reelNumber
	 * @return @throws celtech.printerControl.model.PrinterException
	 */
	@Override
	public AckResponse formatReelEEPROM(final int reelNumber) throws PrinterException {
		RoboxTxPacket formatPacket = null;
		LOGGER.debug("Formatting reel " + reelNumber);
		switch (reelNumber) {
			case 0:
				formatPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.FORMAT_REEL_0_EEPROM);
				break;
			case 1:
				formatPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.FORMAT_REEL_1_EEPROM);
				break;
		}

		AckResponse response = null;
		try {
			response = (AckResponse) commandInterface.writeToPrinter(formatPacket);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending format reel");
			throw new PrinterException("Error formatting reel");
		}

		return response;
	}

	/**
	 *
	 * @param reelNumber
	 * @param dontPublishResponseEvent
	 * @return @throws RoboxCommsException
	 */
	@Override
	public ReelEEPROMDataResponse readReelEEPROM(int reelNumber, boolean dontPublishResponseEvent) throws RoboxCommsException {
		LOGGER.debug("Reading reel " + reelNumber + " EEPROM");

		RoboxTxPacket packet = null;

		switch (reelNumber) {
			case 0:
				packet = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_0_EEPROM);
				break;
			case 1:
				packet = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_1_EEPROM);
				break;
			default:
				LOGGER.warn("Using default reel - was asked to read reel number " + reelNumber);
				packet = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_0_EEPROM);
				break;
		}

		return (ReelEEPROMDataResponse) commandInterface.writeToPrinter(packet, dontPublishResponseEvent);
	}

	/**
	 *
	 * @param reelToWrite
	 * @throws RoboxCommsException
	 */
	private void writeReelEEPROM(int reelNumber, Reel reelToWrite, boolean dontPublishResult) throws RoboxCommsException {
		RoboxTxPacket readPacket = null;
		RoboxTxPacket writePacket = null;

		switch (reelNumber) {
			case 0:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_0_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_0_EEPROM);
				((WriteReel0EEPROM) writePacket).populateEEPROM(reelToWrite.filamentID.get(), reelToWrite.firstLayerNozzleTemperature.get(), reelToWrite.nozzleTemperature.get(), reelToWrite.firstLayerBedTemperature.get(),
						reelToWrite.bedTemperature.get(), reelToWrite.ambientTemperature.get(), reelToWrite.diameter.get(), reelToWrite.filamentMultiplier.get(), reelToWrite.feedRateMultiplier.get(), reelToWrite.remainingFilament.get(),
						reelToWrite.friendlyFilamentName.get(), reelToWrite.material.get(), formatHexString(reelToWrite.displayColour.get()).substring(1));
				break;
			case 1:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_1_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_1_EEPROM);
				((WriteReel1EEPROM) writePacket).populateEEPROM(reelToWrite.filamentID.get(), reelToWrite.firstLayerNozzleTemperature.get(), reelToWrite.nozzleTemperature.get(), reelToWrite.firstLayerBedTemperature.get(),
						reelToWrite.bedTemperature.get(), reelToWrite.ambientTemperature.get(), reelToWrite.diameter.get(), reelToWrite.filamentMultiplier.get(), reelToWrite.feedRateMultiplier.get(), reelToWrite.remainingFilament.get(),
						reelToWrite.friendlyFilamentName.get(), reelToWrite.material.get(), formatHexString(reelToWrite.displayColour.get()).substring(1));
				break;
		}

		AckResponse response = null;

		if (readPacket != null && writePacket != null) {
			response = (AckResponse) commandInterface.writeToPrinter(writePacket, dontPublishResult);
			//            commandInterface.writeToPrinter(readPacket);
		}
	}

	/**
	 *
	 * @param filament
	 * @return
	 * @throws RoboxCommsException
	 */
	@Override
	public AckResponse transmitWriteReelEEPROM(int reelNumber, Filament filament) throws RoboxCommsException {
		RoboxTxPacket readPacket = null;
		RoboxTxPacket writePacket = null;

		switch (reelNumber) {
			case 0:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_0_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_0_EEPROM);
				((WriteReel0EEPROM) writePacket).populateEEPROM(filament.getFilamentID(), filament.getFirstLayerNozzleTemperature(), filament.getNozzleTemperature(), filament.getFirstLayerBedTemperature(), filament.getBedTemperature(),
						filament.getAmbientTemperature(), filament.getDiameter(), filament.getFilamentMultiplier(), filament.getFeedRateMultiplier(), filament.getRemainingFilament(), filament.getFriendlyFilamentName(),
						filament.getMaterial(),
						formatHexString(filament.getDisplayColour()).substring(1));
				break;
			case 1:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_1_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_1_EEPROM);
				((WriteReel1EEPROM) writePacket).populateEEPROM(filament.getFilamentID(), filament.getFirstLayerNozzleTemperature(), filament.getNozzleTemperature(), filament.getFirstLayerBedTemperature(), filament.getBedTemperature(),
						filament.getAmbientTemperature(), filament.getDiameter(), filament.getFilamentMultiplier(), filament.getFeedRateMultiplier(), filament.getRemainingFilament(), filament.getFriendlyFilamentName(),
						filament.getMaterial(),
						formatHexString(filament.getDisplayColour()).substring(1));
				break;
			default:
				LOGGER.warn("Using default reel - was asked to read reel number " + reelNumber);
				break;
		}

		AckResponse response = null;

		if (readPacket != null && writePacket != null) {
			response = (AckResponse) commandInterface.writeToPrinter(writePacket);
			commandInterface.writeToPrinter(readPacket);
		}

		return response;
	}

	@Override
	public void transmitWriteReelEEPROM(int reelNumber, String filamentID, float reelFirstLayerNozzleTemperature, float reelNozzleTemperature, float reelFirstLayerBedTemperature, float reelBedTemperature, float reelAmbientTemperature,
			float reelFilamentDiameter, float reelFilamentMultiplier, float reelFeedRateMultiplier, float reelRemainingFilament, String friendlyName, MaterialType materialType, Color displayColour) throws RoboxCommsException {

		RoboxTxPacket readPacket = null;
		RoboxTxPacket writePacket = null;

		switch (reelNumber) {
			case 0:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_0_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_0_EEPROM);
				((WriteReel0EEPROM) writePacket).populateEEPROM(filamentID, reelFirstLayerNozzleTemperature, reelNozzleTemperature, reelFirstLayerBedTemperature, reelBedTemperature, reelAmbientTemperature, reelFilamentDiameter,
						reelFilamentMultiplier, reelFeedRateMultiplier, reelRemainingFilament, friendlyName, materialType, formatHexString(displayColour).substring(1));
				break;
			case 1:
				readPacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_REEL_1_EEPROM);
				writePacket = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_REEL_1_EEPROM);
				((WriteReel1EEPROM) writePacket).populateEEPROM(filamentID, reelFirstLayerNozzleTemperature, reelNozzleTemperature, reelFirstLayerBedTemperature, reelBedTemperature, reelAmbientTemperature, reelFilamentDiameter,
						reelFilamentMultiplier, reelFeedRateMultiplier, reelRemainingFilament, friendlyName, materialType, formatHexString(displayColour).substring(1));
				break;
			default:
				LOGGER.warn("Using default reel - was asked to read reel number " + reelNumber);
				break;
		}

		AckResponse response = null;

		if (readPacket != null && writePacket != null) {
			response = (AckResponse) commandInterface.writeToPrinter(writePacket);
			commandInterface.writeToPrinter(readPacket);
		}
	}

	@Override
	public AckResponse transmitWriteHeadEEPROM(String headTypeCode, String headUniqueID, float maximumTemperature, float thermistorBeta, float thermistorTCal, float nozzle1XOffset, float nozzle1YOffset, float nozzle1ZOffset,
			float nozzle1BOffset, String filament0ID, String filament1ID, float nozzle2XOffset, float nozzle2YOffset, float nozzle2ZOffset, float nozzle2BOffset, float lastFilamentTemperature0, float lastFilamentTemperature1,
			float hourCounter) throws RoboxCommsException {
		WriteHeadEEPROM writeHeadEEPROM = (WriteHeadEEPROM) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_HEAD_EEPROM);
		writeHeadEEPROM.populateEEPROM(headTypeCode, headUniqueID, maximumTemperature, thermistorBeta, thermistorTCal, nozzle1XOffset, nozzle1YOffset, nozzle1ZOffset, nozzle1BOffset, filament0ID, filament1ID, nozzle2XOffset, nozzle2YOffset,
				nozzle2ZOffset, nozzle2BOffset, lastFilamentTemperature0, lastFilamentTemperature1, hourCounter);
		AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeHeadEEPROM);
		commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_HEAD_EEPROM));
		return response;
	}

	/*
	 * Higher level controls
	 */
	@Override
	public void transmitSetTemperatures(double nozzle0FirstLayerTarget, double nozzle0Target, double nozzle1FirstLayerTarget, double nozzle1Target, double bedFirstLayerTarget, double bedTarget, double ambientTarget)
			throws RoboxCommsException {
		SetTemperatures setTemperatures = (SetTemperatures) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_TEMPERATURES);
		setTemperatures.setTemperatures(nozzle0FirstLayerTarget, nozzle0Target, nozzle1FirstLayerTarget, nozzle1Target, bedFirstLayerTarget, bedTarget, ambientTarget);
		commandInterface.writeToPrinter(setTemperatures);
	}

	@Override
	public ListFilesResponse transmitListFiles() throws RoboxCommsException {
		ListFiles listFiles = (ListFiles) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.LIST_FILES);

		// We don't want the result of this to be published
		return (ListFilesResponse) commandInterface.writeToPrinter(listFiles, true);
	}

	@Override
	public StatusResponse transmitStatusRequest() throws RoboxCommsException {
		StatusRequest statusRequest = (StatusRequest) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.STATUS_REQUEST);
		return (StatusResponse) commandInterface.writeToPrinter(statusRequest);
	}

	@Override
	public boolean initialiseDataFileSend(String fileID, boolean jobCanBeReprinted) throws DatafileSendAlreadyInProgress, RoboxCommsException {
		boolean success = false;
		success = transmitDataFileStart(fileID, jobCanBeReprinted);
		outputBuffer.delete(0, outputBuffer.length());
		dataFileSequenceNumber = 0;
		printInitiated = false;

		return success;
	}

	@Override
	public SendFile requestSendFileReport() throws RoboxCommsException {
		ReadSendFileReport sendFileReport = (ReadSendFileReport) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_SEND_FILE_REPORT);

		SendFile sendFileData = (SendFile) commandInterface.writeToPrinter(sendFileReport, true);

		return sendFileData;
	}

	/**
	 *
	 * @param jobUUID
	 * @throws RoboxCommsException
	 */
	@Override
	public void initiatePrint(String jobUUID) throws RoboxCommsException {
		transmitInitiatePrint(jobUUID);
		printInitiated = true;
	}

	@Override
	public void sendDataFileChunk(String hexDigits, boolean lastPacket, boolean appendCRLF) throws DatafileSendNotInitialised, RoboxCommsException {
		boolean dataIngested = false;

		if (appendCRLF) {
			hexDigits += "\r";
		}

		int remainingCharacters = hexDigits.length();

		while (remainingCharacters > 0) {
			/*
			 * Load the entire line if possible
			 */
			if ((outputBuffer.capacity() - outputBuffer.length()) >= remainingCharacters) {
				String stringToWrite = hexDigits.substring(hexDigits.length() - remainingCharacters);
				outputBuffer.append(stringToWrite);
				dataIngested = true;
				remainingCharacters -= stringToWrite.length();
			}
			else {
				/*
				 * put in what we can
				 */
				String stringToWrite = hexDigits.substring(hexDigits.length() - remainingCharacters, (hexDigits.length() - remainingCharacters) + (outputBuffer.capacity() - outputBuffer.length()));
				outputBuffer.append(stringToWrite);
				remainingCharacters -= stringToWrite.length();
			}

			/*
			 * If this is the last packet then send as an end...
			 */
			if (dataIngested && lastPacket) {
				LOGGER.trace("Final complete chunk seq:" + dataFileSequenceNumber + ":\n\"" + outputBuffer.toString() + "\"");
				AckResponse response = transmitDataFileEnd(outputBuffer.toString(), dataFileSequenceNumber);
				if (response.isError()) {
					LOGGER.error("Error sending final data file chunk - seq " + dataFileSequenceNumber);
				}
			}
			else if ((outputBuffer.capacity() - outputBuffer.length()) == 0) {
				/*
				 * Send when full
				 */

				if (dataFileSequenceNumber >= dataFileSequenceNumberStartPoint) {
					LOGGER.trace("Sending chunk seq:" + dataFileSequenceNumber);

					AckResponse response = transmitDataFileChunk(outputBuffer.toString(), dataFileSequenceNumber);
					if (response.isError() && response.getFirmwareErrors().contains(FirmwareError.USB_RX) && response.getFirmwareErrors().contains(FirmwareError.USB_TX) && response.getFirmwareErrors().contains(FirmwareError.BAD_COMMAND)
							&& response.getFirmwareErrors().contains(FirmwareError.CHUNK_SEQUENCE) && response.getFirmwareErrors().contains(FirmwareError.FILE_READ_CLOBBERED)
							&& response.getFirmwareErrors().contains(FirmwareError.GCODE_BUFFER_OVERRUN) && response.getFirmwareErrors().contains(FirmwareError.GCODE_LINE_TOO_LONG)) {
						LOGGER.error("Error sending data file chunk - seq " + dataFileSequenceNumber);
					}
				}
				else {
					dataFileSequenceNumber++;
				}
				outputBuffer.delete(0, bufferSize);
			}
		}
	}

	@Override
	public void printProject(PrintableProject printableProject, Optional<GCodeGeneratorResult> potentialGCodeGenResult, boolean safetyFeaturesRequired) throws PrinterException {
		Filament filament0 = effectiveFilamentsProperty().get(0);
		Filament filament1 = effectiveFilamentsProperty().get(1);

		double nozzle0FirstLayerTarget = 0;
		double nozzle1FirstLayerTarget = 0;
		double nozzle0Target = 0;
		double nozzle1Target = 0;
		double bedFirstLayerTarget = 0;
		double bedTarget = 0;
		double ambientTarget = 0;

		List<Boolean> usedExtruders = printableProject.getUsedExtruders();

		bindLoadedExtruders(usedExtruders);

		boolean needToOverrideTempsForReel0 = false;
		if (filament0 != FilamentContainer.UNKNOWN_FILAMENT) {
			if (usedExtruders.get(0) && !reels.containsKey(0) || (reels.containsKey(0) && !reels.get(0).isSameAs(filament0))) {
				needToOverrideTempsForReel0 = true;
			}
		}

		boolean needToOverrideTempsForReel1 = false;
		if (filament1 != FilamentContainer.UNKNOWN_FILAMENT) {
			// Never attempt to override for filament 1 if we aren't using a dual material head
			if (headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD && usedExtruders.get(1) && !reels.containsKey(1) || (reels.containsKey(1) && !reels.get(1).isSameAs(filament1))) {
				needToOverrideTempsForReel1 = true;
			}
		}

		// Set up bed and ambient targets
		if (usedExtruders.get(0) && !usedExtruders.get(1)) {
			bedFirstLayerTarget = filament0.getFirstLayerBedTemperature();
			bedTarget = filament0.getBedTemperature();
			ambientTarget = filament0.getAmbientTemperature();

			// Using extruder 0 for this print
			//
			// TODO This is a specific hack for issues with PLA softening in the bowden tube
			// If we don't need the loaded PLA then eject it
			if (extruders.get(1).filamentLoadedProperty().get() && filament1.getMaterial() == MaterialType.PLA) {
				systemNotificationManager.showInformationNotification("", i18n.t("notification.ejectingNotRequiredFilament"));
				ejectFilament(1, null);
			}
		}
		else if (!usedExtruders.get(0) && usedExtruders.get(1)) {
			bedFirstLayerTarget = filament1.getFirstLayerBedTemperature();
			bedTarget = filament1.getBedTemperature();
			ambientTarget = filament1.getAmbientTemperature();

			// Using extruder 1 for this print
			//
			// TODO This is a specific hack for issues with PLA softening in the bowden tube
			// If we don't need the loaded PLA then eject it
			if (extruders.get(0).filamentLoadedProperty().get() && filament0.getMaterial() == MaterialType.PLA) {
				systemNotificationManager.showInformationNotification("", i18n.t("notification.ejectingNotRequiredFilament"));
				ejectFilament(0, null);
			}
		}
		else {
			// Using both extruders for this print
			//
			// TODO This is a specific hack for issues with PLA softening in the bowden tube
			// Force the bed and ambient temperatures to PLA levels if it is on board
			// Remove when we deal with material-specific handling
			if (filament0.getMaterial() != filament1.getMaterial() && (filament0.getMaterial() == MaterialType.PLA || filament1.getMaterial() == MaterialType.PLA)) {
				// Tell the code below that we need to send new temperatures
				needToOverrideTempsForReel0 = true;

				if (filament0.getMaterial() == MaterialType.PLA) {
					bedFirstLayerTarget = filament0.getFirstLayerBedTemperature();
					bedTarget = filament0.getBedTemperature();
					ambientTarget = filament0.getAmbientTemperature();
				}
				else {
					bedFirstLayerTarget = filament1.getFirstLayerBedTemperature();
					bedTarget = filament1.getBedTemperature();
					ambientTarget = filament1.getAmbientTemperature();
				}
			}
			else {
				bedFirstLayerTarget = Math.max(filament0.getFirstLayerBedTemperature(), filament1.getFirstLayerBedTemperature());
				bedTarget = Math.max(filament0.getBedTemperature(), filament1.getBedTemperature());
				ambientTarget = Math.min(filament0.getAmbientTemperature(), filament1.getAmbientTemperature());
			}
		}

		// Set up nozzle targets and extrusion multipliers and diameters
		if (headProperty().get().headTypeProperty().get() == Head.HeadType.DUAL_MATERIAL_HEAD) {
			nozzle1FirstLayerTarget = filament0.getFirstLayerNozzleTemperature();
			nozzle1Target = filament0.getNozzleTemperature();
			nozzle0FirstLayerTarget = filament1.getFirstLayerNozzleTemperature();
			nozzle0Target = filament1.getNozzleTemperature();

			if (extruders.get(0).isFittedProperty().get()) {
				changeFilamentInfo("E", filament0.getDiameter(), filament0.getFilamentMultiplier());
				changeEFeedRateMultiplier(filament0.getFeedRateMultiplier());
				extruders.get(0).lastFeedrateMultiplierInUse.set(filament0.getFeedRateMultiplier());
			}

			if (extruders.get(1).isFittedProperty().get()) {
				changeFilamentInfo("D", filament1.getDiameter(), filament1.getFilamentMultiplier());
				changeDFeedRateMultiplier(filament1.getFeedRateMultiplier());
				extruders.get(1).lastFeedrateMultiplierInUse.set(filament1.getFeedRateMultiplier());
			}
		}
		else {
			nozzle0FirstLayerTarget = filament0.getFirstLayerNozzleTemperature();
			nozzle0Target = filament0.getNozzleTemperature();

			changeFilamentInfo("E", filament0.getDiameter(), filament0.getFilamentMultiplier());
			changeEFeedRateMultiplier(filament0.getFeedRateMultiplier());
			extruders.get(0).lastFeedrateMultiplierInUse.set(filament0.getFeedRateMultiplier());
		}

		if (needToOverrideTempsForReel0 || needToOverrideTempsForReel1) {
			try {
				transmitSetTemperatures(nozzle0FirstLayerTarget, nozzle0Target, nozzle1FirstLayerTarget, nozzle1Target, bedFirstLayerTarget, bedTarget, ambientTarget);
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Failure to set temperatures prior to print");
			}
		}

		try {
			transmitDirectGCode(GCodeConstants.goToTargetFirstLayerBedTemperature, false);
			boolean gCodeGenSuccessful = printEngine.printProject(printableProject, potentialGCodeGenResult, safetyFeaturesRequired);
			transmitDirectGCode(GCodeConstants.switchBedHeaterOff, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error whilst sending preheat commands");
		}
	}

	private void bindLoadedExtruders(List<Boolean> usedExtruders) {
		usedExtrudersLoaded.unbind();
		if (usedExtruders.get(0)) {
			usedExtrudersLoaded.bind(extruders.get(0).filamentLoaded);
		}
		if (usedExtruders.get(1)) {
			usedExtrudersLoaded.bind(extruders.get(1).filamentLoaded);
		}
	}

	@Override
	public void addToGCodeTranscript(String gcodeToSend) {
		gcodeTranscript.add(gcodeToSend);
	}

	@Override
	public ObservableList<String> gcodeTranscriptProperty() {
		return gcodeTranscript;
	}

	@Override
	public void processRoboxResponse(RoboxRxPacket rxPacket) {
		RoboxEventProcessor roboxEventProcessor = new RoboxEventProcessor(this, rxPacket);
		taskExecutor.runOnGUIThread(roboxEventProcessor);
	}

	/*
	 * Door open
	 */
	@Override
	public ReadOnlyBooleanProperty canOpenDoorProperty() {
		return canOpenDoor;
	}

	@Override
	public void goToOpenDoorPosition(TaskResponder responder, boolean safetyFeaturesRequired) throws PrinterException {
		if (!canOpenDoor.get()) {
			throw new PrintActionUnavailableException("Door open not available");
		}

		setPrinterStatus(PrinterStatus.OPENING_DOOR);

		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = doOpenDoorActivity(cancellable, safetyFeaturesRequired);

			if (responder != null) {
				taskExecutor.respondOnGUIThread(responder, success, "Door open");
			}

			setPrinterStatus(PrinterStatus.IDLE);

		}, "Opening door").start();
	}

	private boolean doOpenDoorActivity(Cancellable cancellable, boolean safetyFeaturesRequired) {
		boolean openTheDoorWithCooling = false;
		boolean success = false;

		if (printerAncillarySystems.bedTemperatureProperty().get() > 60) {
			if (!safetyFeaturesRequired) {
				try {
					transmitDirectGCode(GCodeConstants.goToOpenDoorPositionDontWait, false);
					printerUtils.waitOnBusy(this, cancellable);
					success = true;
				}
				catch (RoboxCommsException ex) {
					LOGGER.error("Error opening door " + ex.getMessage());
				}
			}
			else {
				openTheDoorWithCooling = systemNotificationManager.showOpenDoorDialog();
			}
		}
		else {
			openTheDoorWithCooling = true;
		}

		if (openTheDoorWithCooling) {
			try {
				transmitDirectGCode(GCodeConstants.goToOpenDoorPosition, false);
				printerUtils.waitOnBusy(this, cancellable);
				success = true;
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Error when moving sending open door command");
			}
		}

		return success;
	}

	@Override
	public void goToOpenDoorPositionDontWait(TaskResponder responder) throws PrinterException {
		if (!canOpenDoor.get()) {
			throw new PrintActionUnavailableException("Door open not available");
		}

		setPrinterStatus(PrinterStatus.OPENING_DOOR);

		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = doOpenDoorActivityDontWait(cancellable);

			if (responder != null) {
				taskExecutor.respondOnGUIThread(responder, success, "Door open don't wait");
			}

			setPrinterStatus(PrinterStatus.IDLE);

		}, "Opening door don't wait").start();
	}

	private boolean doOpenDoorActivityDontWait(Cancellable cancellable) {
		boolean success = false;
		try {
			transmitDirectGCode(GCodeConstants.goToOpenDoorPositionDontWait, false);
			printerUtils.waitOnBusy(this, cancellable);
			success = true;
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when moving sending open door command");
		}

		return success;
	}

	@Override
	public void updatePrinterName(String chosenPrinterName) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printerFriendlyName.set(chosenPrinterName);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());
		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer name " + ex.getMessage());
			throw new PrinterException("Failed to write name to printer");
		}
	}

	@Override
	public void updatePrinterDisplayColour(Color displayColour) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();

		newIdentity.printerColour.set(printerColourMap.displayToPrinterColour(displayColour));
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
			setAmbientLEDColour(Color.web(idResponse.getPrinterColour()));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer colour " + ex.getMessage());
			throw new PrinterException("Failed to write colour to printer");
		}
	}

	@Override
	public void updatePrinterModelAndEdition(PrinterDefinitionFile printerDefinition, PrinterEdition printerEdition) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printermodel.set(printerDefinition.getTypeCode());
		newIdentity.printeredition.set(printerEdition.getTypeCode());
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer model and edition " + ex.getMessage());
			throw new PrinterException("Failed to write model and edition to printer");
		}
	}

	@Override
	public void updatePrinterWeek(String weekIdentifier) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printerweekOfManufacture.set(weekIdentifier);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer week " + ex.getMessage());
			throw new PrinterException("Failed to write week to printer");
		}
	}

	@Override
	public void updatePrinterYear(String yearIdentifier) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printeryearOfManufacture.set(yearIdentifier);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer year " + ex.getMessage());
			throw new PrinterException("Failed to write year to printer");
		}
	}

	@Override
	public void updatePrinterPONumber(String poIdentifier) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printerpoNumber.set(poIdentifier);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer PO number " + ex.getMessage());
			throw new PrinterException("Failed to write PO number to printer");
		}
	}

	@Override
	public void updatePrinterSerialNumber(String serialIdentifier) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printerserialNumber.set(serialIdentifier);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer serial number " + ex.getMessage());
			throw new PrinterException("Failed to write serial number to printer");
		}
	}

	@Override
	public void updatePrinterIDChecksum(String checksum) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = printerIdentity.clone();
		newIdentity.printercheckByte.set(checksum);
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer id checksum " + ex.getMessage());
			throw new PrinterException("Failed to write identity to printer");
		}
	}

	@Override
	public void updatePrinterIdentity(PrinterIdentity identity) throws PrinterException {
		WritePrinterID writeIDCmd = (WritePrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_PRINTER_ID);

		PrinterIdentity newIdentity = identity.clone();
		writeIDCmd.populatePacket(newIdentity.printerUniqueID.get(), newIdentity.printermodel.get(), newIdentity.printeredition.get(), newIdentity.printerweekOfManufacture.get(), newIdentity.printeryearOfManufacture.get(),
				newIdentity.printerpoNumber.get(), newIdentity.printerserialNumber.get(), newIdentity.printercheckByte.get(), newIdentity.printerelectronicsVersion.get(), newIdentity.printerFriendlyName.get(),
				formatHexString(newIdentity.printerColour.get()).substring(1), newIdentity.firmwareVersion.get());

		try {
			AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeIDCmd);
			PrinterIDResponse idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID));
			setAmbientLEDColour(Color.web(idResponse.getPrinterColour()));
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception whilst writing printer id checksum " + ex.getMessage());
			throw new PrinterException("Failed to write checksum to printer");
		}
	}

	@Override
	public void goToTargetBedTemperature() {
		try {
			transmitDirectGCode(GCodeConstants.goToTargetBedTemperature, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending go to target bed temperature command");
		}
	}

	@Override
	public void switchBedHeaterOff() {
		try {
			transmitDirectGCode(GCodeConstants.switchBedHeaterOff, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending switch bed heater off command");
		}
	}

	@Override
	public void goToTargetNozzleHeaterTemperature(int nozzleHeaterNumber) {
		LOGGER.debug("Go to target nozzle heater temperature " + nozzleHeaterNumber);
		try {
			if (nozzleHeaterNumber == 0) {
				transmitDirectGCode(GCodeConstants.goToTargetNozzleHeaterTemperature0, false);
			}
			else if (nozzleHeaterNumber == 1) {
				transmitDirectGCode(GCodeConstants.goToTargetNozzleHeaterTemperature1, false);
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending go to target nozzle temperature command");
		}
	}

	@Override
	public void setNozzleHeaterTargetTemperature(int nozzleHeaterNumber, int targetTemperature) {
		LOGGER.debug("Set nozzle  target temp " + nozzleHeaterNumber);
		try {
			if (head.get() != null && head.get().nozzleHeaters.size() > nozzleHeaterNumber) {
				StringBuilder tempCommand = new StringBuilder();
				if (head.get().nozzleHeaters.get(nozzleHeaterNumber).heaterMode.get() == HeaterMode.FIRST_LAYER) {
					tempCommand.append("M103");
				}
				else {
					tempCommand.append("M104");
				}

				if (nozzleHeaterNumber == 0) {
					tempCommand.append(" S");
				}
				else if (nozzleHeaterNumber == 1) {
					tempCommand.append(" T");
				}
				tempCommand.append(targetTemperature);
				transmitDirectGCode(tempCommand.toString(), false);
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending set nozzle temperature command");
		}
	}

	@Override
	public void setAmbientTemperature(int targetTemperature) {
		try {
			transmitDirectGCode(GCodeConstants.setAmbientTemperature + targetTemperature, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending set ambient temperature command");
		}
	}

	@Override
	public void setBedFirstLayerTargetTemperature(int targetTemperature) {
		try {
			transmitDirectGCode(GCodeConstants.setFirstLayerBedTemperatureTarget + targetTemperature, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending set bed first layer target temperature command");
		}
	}

	@Override
	public void setBedTargetTemperature(int targetTemperature) {
		try {
			transmitDirectGCode(GCodeConstants.setBedTemperatureTarget + targetTemperature, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending set bed target temperature command");
		}
	}

	@Override
	public String sendRawGCode(String gCode, boolean addToTranscript) {
		String transcript = null;
		try {
			transcript = transmitDirectGCode(gCode, addToTranscript);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending raw gcode : " + gCode);
		}

		return transcript;
	}

	@Override
	public void gotoNozzlePosition(float position) {
		try {
			transmitDirectGCode("G0 B" + threeDPformatter.format(position), false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending close nozzle command");
		}
	}

	@Override
	public void switchOnHeadLEDs() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.switchOnHeadLEDs, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending switch head LED on command");
			throw new PrinterException("Error sending LED on command");
		}
	}

	@Override
	public void switchOffHeadLEDs() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.switchOffHeadLEDs, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending switch head LED off command");
			throw new PrinterException("Error sending LED off command");
		}
	}

	@Override
	public void switchAllNozzleHeatersOff() {
		switchNozzleHeaterOff(0);
		switchNozzleHeaterOff(1);
	}

	@Override
	public void switchNozzleHeaterOff(int heaterNumber) {
		try {
			if (heaterNumber == 0) {
				LOGGER.debug("Turn off nozzle heater 0");
				transmitDirectGCode(GCodeConstants.switchNozzleHeaterOff0, false);
			}
			else if (heaterNumber == 1) {
				LOGGER.debug("Turn off nozzle heater 1");
				transmitDirectGCode(GCodeConstants.switchNozzleHeaterOff1, false);
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending switch nozzle heater off command");
		}
	}

	@Override
	public void homeX() {
		try {
			transmitDirectGCode("G28 X", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending x home command");
		}
	}

	@Override
	public void homeY() {
		try {
			transmitDirectGCode("G28 Y", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending y home command");
		}
	}

	@Override
	public void homeZ() {
		try {
			transmitDirectGCode("G28 Z", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending z home command");
		}
	}

	@Override
	public void probeX() {
		try {
			transmitDirectGCode("G28 X?", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending probe X command");
		}
	}

	@Override
	public float getXDelta() throws PrinterException {
		float deltaValue = 0;
		String measurementString = null;

		try {
			String response = transmitDirectGCode("M111", false);
			LOGGER.debug("X delta response: " + response);
			measurementString = response.replaceFirst("Xdelta:", "").replaceFirst("\nok", "").trim();
			deltaValue = Float.valueOf(measurementString.trim());

		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending get X delta");
			throw new PrinterException("Error sending get X delta");
		}
		catch (NumberFormatException ex) {
			LOGGER.error("Couldn't parse measurement string for X delta: " + measurementString);
			throw new PrinterException("Measurement string for X delta: " + measurementString + " : could not be parsed");
		}

		return deltaValue;
	}

	@Override
	public void probeY() {
		try {
			transmitDirectGCode("G28 Y?", false);
			String result = transmitDirectGCode("M112", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending probe Y command");
		}
	}

	@Override
	public float getYDelta() throws PrinterException {
		float deltaValue = 0;
		String measurementString = null;

		try {
			String response = transmitDirectGCode("M112", false);
			LOGGER.debug("Y delta response: " + response);
			measurementString = response.replaceFirst("Ydelta:", "").replaceFirst("\nok", "").trim();
			deltaValue = Float.valueOf(measurementString.trim());

		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending get Y delta");
			throw new PrinterException("Error sending get Y delta");
		}
		catch (NumberFormatException ex) {
			LOGGER.error("Couldn't parse measurement string for Y delta: " + measurementString);
			throw new PrinterException("Measurement string for Y delta: " + measurementString + " : could not be parsed");
		}

		return deltaValue;
	}

	@Override
	public void probeZ() {
		try {
			transmitDirectGCode("G28 Z?", false);
			String result = transmitDirectGCode("M113", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending probe Z command");
		}
	}

	@Override
	public float getZDelta() throws PrinterException {
		float deltaValue = 0;
		String measurementString = null;

		try {
			String response = transmitDirectGCode("M113", false);
			LOGGER.debug("Z delta response: " + response);
			measurementString = response.replaceFirst("Zdelta:", "").replaceFirst("\nok", "").trim();
			deltaValue = Float.valueOf(measurementString.trim());

		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending get Z delta");
			throw new PrinterException("Error sending get Z delta");
		}
		catch (NumberFormatException ex) {
			LOGGER.error("Couldn't parse measurement string for Z delta: " + measurementString);
			throw new PrinterException("Measurement string for Z delta: " + measurementString + " : could not be parsed");
		}

		return deltaValue;
	}

	@Override
	public void levelGantryRaw() {
		try {
			transmitDirectGCode("G38", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending level gantry command");
		}
	}

	@Override
	public void goToZPosition(double position, int feedrate_mmPerMin) {
		try {
			transmitDirectGCode("G1 Z" + threeDPformatter.format(position) + " " + feedrate_mmPerMin, false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending z position command");
		}
	}

	@Override
	public void goToZPosition(double position) {
		try {
			transmitDirectGCode("G0 Z" + threeDPformatter.format(position), false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending z position command");
		}
	}

	@Override
	public void goToXYPosition(double xPosition, double yPosition) {
		try {
			transmitDirectGCode("G0 X" + threeDPformatter.format(xPosition) + " Y" + threeDPformatter.format(yPosition), false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending x y position command");
		}
	}

	@Override
	public void goToXYZPosition(double xPosition, double yPosition, double zPosition) {
		try {
			transmitDirectGCode("G0 X" + threeDPformatter.format(xPosition) + " Y" + threeDPformatter.format(yPosition) + " Z" + threeDPformatter.format(zPosition), false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending x y z position command");
		}
	}

	@Override
	public void switchToAbsoluteMoveMode() {
		try {
			transmitDirectGCode("G90", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending change to absolute move command");
		}
	}

	@Override
	public void switchToRelativeMoveMode() {
		try {
			transmitDirectGCode("G91", false);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending change to relative move command");
		}
	}

	@Override
	public void writeHeadEEPROM(Head headToWrite, boolean readback) throws RoboxCommsException {
		WriteHeadEEPROM writeHeadEEPROM = (WriteHeadEEPROM) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.WRITE_HEAD_EEPROM);

		String filamentID0 = "";
		float lastFilamentTemp0 = 0;
		float beta = 0;
		float tcal = 0;
		float maxHeadTemp = 0;

		if (headToWrite.getNozzleHeaters().size() > 0) {
			filamentID0 = headToWrite.getNozzleHeaters().get(0).filamentIDProperty().get();
			lastFilamentTemp0 = headToWrite.getNozzleHeaters().get(0).lastFilamentTemperatureProperty().get();
			beta = headToWrite.getNozzleHeaters().get(0).betaProperty().get();
			tcal = headToWrite.getNozzleHeaters().get(0).tCalProperty().get();
			maxHeadTemp = headToWrite.getNozzleHeaters().get(0).maximumTemperatureProperty().get();
		}

		String filamentID1 = "";
		float lastFilamentTemp1 = 0;

		if (headToWrite.getNozzleHeaters().size() > 1) {
			filamentID1 = headToWrite.getNozzleHeaters().get(1).filamentIDProperty().get();
			lastFilamentTemp1 = headToWrite.getNozzleHeaters().get(1).lastFilamentTemperatureProperty().get();
		}

		float nozzle0XOffset = 0;
		float nozzle0YOffset = 0;
		float nozzle0ZOffset = 0;
		float nozzle0BOffset = 0;

		if (headToWrite.getNozzles().size() > 0) {
			nozzle0XOffset = headToWrite.getNozzles().get(0).xOffsetProperty().get();
			nozzle0YOffset = headToWrite.getNozzles().get(0).yOffsetProperty().get();
			nozzle0ZOffset = headToWrite.getNozzles().get(0).zOffsetProperty().get();
			nozzle0BOffset = headToWrite.getNozzles().get(0).bOffsetProperty().get();
		}

		float nozzle1XOffset = 0;
		float nozzle1YOffset = 0;
		float nozzle1ZOffset = 0;
		float nozzle1BOffset = 0;

		if (headToWrite.getNozzles().size() > 1) {
			nozzle1XOffset = headToWrite.getNozzles().get(1).xOffsetProperty().get();
			nozzle1YOffset = headToWrite.getNozzles().get(1).yOffsetProperty().get();
			nozzle1ZOffset = headToWrite.getNozzles().get(1).zOffsetProperty().get();
			nozzle1BOffset = headToWrite.getNozzles().get(1).bOffsetProperty().get();
		}

		writeHeadEEPROM.populateEEPROM(headToWrite.typeCodeProperty().get(), headToWrite.uniqueIDProperty().get(), headToWrite.getNozzleHeaters().size(), maxHeadTemp, beta, tcal, lastFilamentTemp0, filamentID0, lastFilamentTemp1,
				filamentID1, headToWrite.headHoursProperty().get(), headToWrite.getNozzles().size(), nozzle0XOffset, nozzle0YOffset, nozzle0ZOffset, nozzle0BOffset, nozzle1XOffset, nozzle1YOffset, nozzle1ZOffset, nozzle1BOffset);

		AckResponse response = (AckResponse) commandInterface.writeToPrinter(writeHeadEEPROM);

		if (readback) {
			readHeadEEPROM(false);
		}
	}

	@Override
	public void writeHeadEEPROM(Head headToWrite) throws RoboxCommsException {
		writeHeadEEPROM(headToWrite, true);
	}

	@Override
	public HeadEEPROMDataResponse readHeadEEPROM(boolean dontPublishResponseEvent) throws RoboxCommsException {
		ReadHeadEEPROM readHead = (ReadHeadEEPROM) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_HEAD_EEPROM);
		HeadEEPROMDataResponse headEEPROMData = (HeadEEPROMDataResponse) commandInterface.writeToPrinter(readHead, dontPublishResponseEvent);
		return headEEPROMData;
	}

	@Override
	public void ejectFilament(int extruderNumber, TaskResponder responder) throws PrinterException {
		if (!extruders.get(extruderNumber).isFitted.get()) {
			throw new PrintActionUnavailableException("Extruder " + extruderNumber + " is not present");
		}

		if (!extruders.get(extruderNumber).canEject.get()) {
			throw new PrintActionUnavailableException("Eject is not available for extruder " + extruderNumber);
		}

		final Cancellable cancellable = new SimpleCancellable();

		new Thread(() -> {
			boolean success = doEjectFilamentActivity(extruderNumber, cancellable);

			taskExecutor.respondOnGUIThread(responder, success, "Filament ejected");

		}, "Ejecting filament").start();

	}

	private boolean doEjectFilamentActivity(int extruderNumber, Cancellable cancellable) {
		boolean success = false;
		try {
			transmitDirectGCode(GCodeConstants.ejectFilament + " " + extruders.get(extruderNumber).getExtruderAxisLetter(), false);
			printerUtils.waitOnBusy(this, cancellable);
			success = true;
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error whilst ejecting filament");
		}

		return success;
	}

	@Override
	public void jogAxis(AxisSpecifier axis, float distance, float feedrate, boolean use_G1) throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.carriageRelativeMoveMode, true);
			if (use_G1) {
				if (feedrate > 0) {
					transmitDirectGCode("G1 " + axis.name() + threeDPformatter.format(distance) + " F" + threeDPformatter.format(feedrate), true);
				}
				else {
					transmitDirectGCode("G1 " + axis.name() + threeDPformatter.format(distance), true);
				}
			}
			else {
				transmitDirectGCode("G0 " + axis.name() + threeDPformatter.format(distance), true);
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error jogging axis");
			throw new PrinterException("Comms error whilst jogging axis");
		}

	}

	@Override
	public void switchOffHeadFan() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.switchOffHeadFan, true);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending head fan off command");
			throw new PrinterException("Error whilst sending head fan off");
		}
	}

	@Override
	public void switchOnHeadFan() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.switchOnHeadFan, true);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending head fan on command");
			throw new PrinterException("Error whilst sending head fan on");
		}
	}

	@Override
	public void openNozzleFully() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.openNozzle, true);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending open nozzle command");
			throw new PrinterException("Error whilst sending nozzle open command");
		}
	}

	@Override
	public void closeNozzleFully() throws PrinterException {
		try {
			transmitDirectGCode(GCodeConstants.closeNozzle, true);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending nozzle close command");
			throw new PrinterException("Error whilst sending nozzle close command");
		}
	}

	@Override
	public void setAmbientLEDColour(Color colour) throws PrinterException {
		SetAmbientLEDColour ledColour = (SetAmbientLEDColour) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_AMBIENT_LED_COLOUR);

		ledColour.setLEDColour(formatHexString(colour).substring(1));

		try {
			commandInterface.writeToPrinter(ledColour, true);
		}
		catch (RoboxCommsException ex) {
			throw new PrinterException("Error sending ambient LED command");
		}
	}

	@Override
	public void setReelLEDColour(Color colour) throws PrinterException {
		SetReelLEDColour ledColour = (SetReelLEDColour) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_REEL_LED_COLOUR);
		ledColour.setLEDColour(formatHexString(colour).substring(1));
		try {
			commandInterface.writeToPrinter(ledColour);
		}
		catch (RoboxCommsException ex) {
			throw new PrinterException("Error sending reel LED command");
		}
	}

	@Override
	public PrinterIDResponse readPrinterID() throws PrinterException {
		PrinterIDResponse idResponse = null;

		ReadPrinterID readId = (ReadPrinterID) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_PRINTER_ID);
		try {
			idResponse = (PrinterIDResponse) commandInterface.writeToPrinter(readId);
		}
		catch (RoboxCommsException ex) {
			throw new PrinterException("Error sending read printer ID command");
		}

		return idResponse;
	}

	@Override
	public FirmwareResponse readFirmwareVersion() throws PrinterException {
		FirmwareResponse response = null;

		QueryFirmwareVersion readFirmware = (QueryFirmwareVersion) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.QUERY_FIRMWARE_VERSION);
		try {
			response = (FirmwareResponse) commandInterface.writeToPrinter(readFirmware);
		}
		catch (RoboxCommsException ex) {
			throw new PrinterException("Error sending read firmware command");
		}
		return response;
	}

	@Override
	public void selectNozzle(int nozzleNumber) throws PrinterException {
		if (nozzleNumber >= head.get().nozzles.size()) {
			throw new PrinterException("Nozzle number " + nozzleNumber + " does not exist");
		}

		try {
			transmitDirectGCode(GCodeConstants.selectNozzle + nozzleNumber, true);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error sending nozzle select command");
			throw new PrinterException("Error whilst sending nozzle select command");
		}
	}

	@Override
	public void shutdown() {
		filamentContainer.removeFilamentDatabaseChangesListener(filamentDatabaseChangesListener);
		LOGGER.debug("Shutdown print engine...");
		printEngine.shutdown();
	}

	@Override
	public XAndYStateTransitionManager startCalibrateXAndY(boolean safetyFeaturesRequired) throws PrinterException {
		if (!canCalibrateXYAlignment.get()) {
			throw new PrinterException("Calibrate not permitted");
		}

		StateTransitionManager.StateTransitionActionsFactory actionsFactory = (Cancellable userCancellable, Cancellable errorCancellable) -> calibrationXAndYActionsFactory.create(HardwarePrinter.this, userCancellable, errorCancellable,
				safetyFeaturesRequired);

		StateTransitionManager.TransitionsFactory transitionsFactory = (StateTransitionActions actions) -> new CalibrationXAndYTransitions((CalibrationXAndYActions) actions);

		calibrationAlignmentManager = xAndYStateTransitionManagerFactory.create(actionsFactory, transitionsFactory);
		return calibrationAlignmentManager;
	}

	@Override
	public NozzleHeightStateTransitionManager startCalibrateNozzleHeight(boolean safetyFeaturesRequired) throws PrinterException {
		if (!canCalibrateNozzleHeight.get()) {
			throw new PrinterException("Calibrate not permitted");
		}

		StateTransitionManager.StateTransitionActionsFactory actionsFactory = (Cancellable userCancellable, Cancellable errorCancellable) -> calibrationNozzleHeightActionsFactory.create(HardwarePrinter.this, userCancellable,
				errorCancellable,
				safetyFeaturesRequired);

		StateTransitionManager.TransitionsFactory transitionsFactory = (StateTransitionActions actions) -> new CalibrationNozzleHeightTransitions((CalibrationNozzleHeightActions) actions);

		calibrationHeightManager = nozzleHeightStateTransitionManagerFactory.create(actionsFactory, transitionsFactory);
		return calibrationHeightManager;
	}

	@Override
	public SingleNozzleHeightStateTransitionManager startCalibrateSingleNozzleHeight(boolean safetyFeaturesRequired) throws PrinterException {
		if (!canCalibrateNozzleHeight.get()) {
			throw new PrinterException("Calibrate not permitted");
		}

		StateTransitionManager.StateTransitionActionsFactory actionsFactory = (Cancellable userCancellable, Cancellable errorCancellable) -> calibrationNozzleHeightActionsFactory.create(HardwarePrinter.this, userCancellable,
				errorCancellable,
				safetyFeaturesRequired);

		StateTransitionManager.TransitionsFactory transitionsFactory = (StateTransitionActions actions) -> new CalibrationSingleNozzleHeightTransitions((CalibrationSingleNozzleHeightActions) actions);

		calibrationSingleNozzleHeightManager = singleNozzleHeightStateTransitionManagerFactory.create(actionsFactory, transitionsFactory);
		return calibrationSingleNozzleHeightManager;
	}

	@Override
	public PurgeStateTransitionManager startPurge(boolean requireSafetyFeatures) throws PrinterException {
		if (!canPurgeHead.get()) {
			throw new PrinterException("Purge not permitted");
		}

		/**
		 * The state transition mechanism requires 3 classes to be created:
		 * <p>
		 * + StateTransitionManager, the GUI deals solely with this small class
		 * </p>
		 * <p>
		 * + StateTransitionActions, the methods that are run on the business object
		 * </p>
		 * + Transitions, the set of valid transitions between states
		 */
		StateTransitionManager.StateTransitionActionsFactory actionsFactory = (Cancellable userCancellable, Cancellable errorCancellable) -> purgeActionsFactory.create(HardwarePrinter.this, userCancellable, errorCancellable,
				requireSafetyFeatures);

		StateTransitionManager.TransitionsFactory transitionsFactory = (StateTransitionActions actions) -> new PurgeTransitions((PurgeActions) actions);

		PurgeStateTransitionManager purgeManager = purgeStateTransitionManagerFactory.create(actionsFactory, transitionsFactory);
		return purgeManager;
	}

	@Override
	public NozzleOpeningStateTransitionManager startCalibrateNozzleOpening(boolean safetyFeaturesRequired) throws PrinterException {

		StateTransitionManager.StateTransitionActionsFactory actionsFactory = (Cancellable userCancellable, Cancellable errorCancellable) -> calibrationNozzleOpeningActionsFactory.create(HardwarePrinter.this, userCancellable,
				errorCancellable,
				safetyFeaturesRequired);

		StateTransitionManager.TransitionsFactory transitionsFactory = (StateTransitionActions actions) -> new CalibrationNozzleOpeningTransitions((CalibrationNozzleOpeningActions) actions);

		calibrationOpeningManager = nozzleOpeningStateTransitionManagerFactory.create(actionsFactory, transitionsFactory);
		return calibrationOpeningManager;
	}

	@Override
	public void registerErrorConsumer(ErrorConsumer errorConsumer, List<FirmwareError> errorsOfInterest) {
		LOGGER.debug("Registering printer error consumer - " + errorConsumer.toString() + " on printer " + printerIdentity.printerFriendlyName);
		errorConsumers.put(errorConsumer, errorsOfInterest);
	}

	@Override
	public void registerErrorConsumerAllErrors(ErrorConsumer errorConsumer) {
		LOGGER.debug("Registering printer error consumer for all errors - " + errorConsumer.toString() + " on printer " + printerIdentity.printerFriendlyName);
		ArrayList<FirmwareError> errorsOfInterest = new ArrayList<>();
		errorsOfInterest.add(FirmwareError.ALL_ERRORS);
		errorConsumers.put(errorConsumer, errorsOfInterest);
	}

	@Override
	public void deregisterErrorConsumer(ErrorConsumer errorConsumer) {
		LOGGER.debug("Deregistering printer error consumer for all errors - " + errorConsumer.toString() + " on printer " + printerIdentity.printerFriendlyName);
		errorConsumers.remove(errorConsumer);
	}

	/**
	 *
	 * * As of v741 firmware this functionality is now handled within Robox
	 *
	 *
	 * @param error
	 * @return True if the filament slip routine has been called the max number of times for this print
	 */
	//    @Override
	//    public boolean doFilamentSlipActionWhilePrinting(FirmwareError error)
	//    {
	//        boolean filamentSlipLimitReached = false;
	//
	//        if ((error == FirmwareError.E_FILAMENT_SLIP && filamentSlipEActionFired < 3)
	//                || (error == FirmwareError.D_FILAMENT_SLIP && filamentSlipDActionFired < 3))
	//        {
	//            LOGGER.debug("Need to run filament slip action");
	//            try
	//            {
	//                systemNotificationManager.showFilamentMotionCheckBanner();
	//                pause();
	//                printerUtils.waitOnBusy(this, (Cancellable) null);
	//                if (error == FirmwareError.E_FILAMENT_SLIP)
	//                {
	//                    forceExecuteMacroAsStream("filament_slip_action_E", true, null);
	//                } else if (error == FirmwareError.D_FILAMENT_SLIP)
	//                {
	//                    forceExecuteMacroAsStream("filament_slip_action_D", true, null);
	//                } else
	//                {
	//                    LOGGER.warn("Filament slip action called with invalid error: "
	//                            + error.
	//                            name());
	//                }
	//                AckResponse response = transmitReportErrors();
	//                if (response.isError())
	//                {
	//                    if (error == FirmwareError.E_FILAMENT_SLIP)
	//                    {
	//                        doAttemptEject('E');
	//                    } else
	//                    {
	//                        doAttemptEject('D');
	//                    }
	//                } else
	//                {
	//                    forcedResume();
	//                }
	//                systemNotificationManager.hideFilamentMotionCheckBanner();
	//            } catch (PrinterException | RoboxCommsException ex)
	//            {
	//                LOGGER.error("Error attempting automated filament slip action");
	//            } finally
	//            {
	//                if (error == FirmwareError.E_FILAMENT_SLIP)
	//                {
	//                    filamentSlipEActionFired++;
	//                } else
	//                {
	//                    filamentSlipDActionFired++;
	//                }
	//            }
	//        } else
	//        {
	//            filamentSlipLimitReached = true;
	//        }
	//
	//        return filamentSlipLimitReached;
	//    }
	//
	//    private void doAttemptEject(char extruderLetter) throws PrinterException
	//    {
	//        LOGGER.debug("Suspect that we're out of filament");
	//        sendRawGCode("M909 S60", false);
	//        printerUtils.waitOnBusy(this, (Cancellable) null);
	//        sendRawGCode("M121 " + extruderLetter, false);
	//        printerUtils.waitOnBusy(this, (Cancellable) null);
	//        try
	//        {
	//            AckResponse response = transmitReportErrors();
	//            systemNotificationManager.hideFilamentMotionCheckBanner();
	//            if (response.isError())
	//            {
	//                cancel(null);
	//                systemNotificationManager.showFilamentStuckMessage();
	//            } else
	//            {
	//                systemNotificationManager.showLoadFilamentNowMessage();
	//            }
	//        } catch (RoboxCommsException ex)
	//        {
	//            LOGGER.error("...");
	//        }
	//        sendRawGCode("M909 S10", false);
	//        printerUtils.waitOnBusy(this, (Cancellable) null);
	//    }
	@Override
	public void consumeError(FirmwareError error) {

		if (!inCommissioningMode) {
			switch (error) {
				case E_UNLOAD_ERROR:
					systemNotificationManager.showEjectFailedDialog(this, 1, error);
					break;

				case D_UNLOAD_ERROR:
					systemNotificationManager.showEjectFailedDialog(this, 0, error);
					break;

				case E_FILAMENT_SLIP:
				case D_FILAMENT_SLIP:
					try {
						if (pauseStatus.get() != PauseStatus.PAUSED && pauseStatus.get() != PauseStatus.PAUSE_PENDING) {
							pause();
						}
						systemNotificationManager.processErrorPacketFromPrinter(error, this);
					}
					catch (PrinterException ex) {
						LOGGER.error("Failed to pause print after filament slip detected " + error.name());
					}
					break;
				case ERROR_D_NO_FILAMENT:
				case ERROR_E_NO_FILAMENT:
					systemNotificationManager.processErrorPacketFromPrinter(error, this);
					break;
				default:
					// Back stop
					switch (printerStatus.get()) {
						// Ignore the error in these cases - they should be handled elsewhere
						case CALIBRATING_NOZZLE_ALIGNMENT:
						case CALIBRATING_NOZZLE_HEIGHT:
						case CALIBRATING_NOZZLE_OPENING:
						case PURGING_HEAD:
							break;
						default:
							if (busyStatus.get() != BusyStatus.UNLOADING_FILAMENT_E && busyStatus.get() != BusyStatus.UNLOADING_FILAMENT_D) {
								systemNotificationManager.processErrorPacketFromPrinter(error, this);
							}
							break;
					}
					break;
			}
		}
	}

	@Override
	public void connectionEstablished() {
		processErrors = true;
	}

	@Override
	public List<Integer> requestDebugData(boolean addToGCodeTranscript) {
		List<Integer> debugData = null;

		RoboxTxPacket debugRequest = RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.READ_DEBUG_DATA);

		try {
			DebugDataResponse response = (DebugDataResponse) commandInterface.writeToPrinter(debugRequest);

			if (response != null) {
				debugData = response.getDebugData();
			}

			if (addToGCodeTranscript) {
				taskExecutor.runOnGUIThread(new Runnable() {

					@Override
					public void run() {
						if (response == null) {
							addToGCodeTranscript("No data returned\n");
						}
						else {
							addToGCodeTranscript(response.getDebugData() + "\n");
						}
					}
				});
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error whilst requesting debug data: " + ex.getMessage());
		}

		return debugData;

	}

	@Override
	public void setDataFileSequenceNumberStartPoint(int startingSequenceNumber) {
		dataFileSequenceNumberStartPoint = startingSequenceNumber;
	}

	@Override
	public void resetDataFileSequenceNumber() {
		dataFileSequenceNumber = 0;
	}

	@Override
	public void extrudeUntilSlip(int extruderNumber, int extrusionVolume, int feedrate_mm_per_min) throws PrinterException {
		try {
			if (extrudersProperty().get(extruderNumber).isFitted.get()) {
				transmitDirectGCode("G36 " + extrudersProperty().get(extruderNumber).getExtruderAxisLetter() + extrusionVolume + " F" + feedrate_mm_per_min, false);
			}
			else {
				String errorText = "Attempt to extrude until slip on extruder " + extruderNumber + " which is not fitted";
				LOGGER.error(errorText);
				throw new PrinterException(errorText);
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when sending go to target bed temperature command");
			throw new PrinterException("Error when sending extrude until slip on extruder " + extruderNumber + ": " + ex.getMessage());
		}
	}

	@Override
	public void suppressFirmwareErrors(FirmwareError... firmwareErrors) {
		for (FirmwareError firmwareError : firmwareErrors) {
			suppressedFirmwareErrors.add(firmwareError);
		}
	}

	@Override
	public void cancelFirmwareErrorSuppression() {
		suppressedFirmwareErrors.clear();
	}

	@Override
	public void suppressEEPROMErrorCorrection(boolean suppress) {
		repairCorruptEEPROMData = !suppress;
	}

	@Override
	public TemperatureAndPWMData getTemperatureAndPWMData() throws PrinterException {
		TemperatureAndPWMData data = null;
		try {
			String response = transmitDirectGCode("M105", false);
			data = new TemperatureAndPWMData();
			data.populateFromPrinterData(response);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error when requesting temperature and PWM data");
			throw new PrinterException("Error when requesting temperature and PWM data");
		}

		return data;

	}

	/**
	 * Synchronised method to update a reels EEPROM check counter
	 * 
	 * @param reelNumber the reel to update
	 * @param count      the counter value
	 */
	private synchronized void setEEPROMCheckForReel(final int reelNumber, final boolean check) {
		reelEEPROMCheck[reelNumber] = check;
	}

	/**
	 * Synchronised method to get a reels EEPROM check counter
	 * 
	 * @param reelNumber the reel number to get the count for
	 * @return the count as an int
	 */
	private synchronized boolean getEEPROMCheckForReel(final int reelNumber) {
		return reelEEPROMCheck[reelNumber];
	}

	/**
	 * Check the application that we are in, if it's root return true
	 * 
	 * @return true if application's short name is root
	 */
	private boolean runningRoot() {
		//TODO: 'Running as root' needs to be checked somehow.  Returns false for the moment.  Needs a better way.
		return false;
		//return new ShortNamePreference().getValue().equals(ROOT_APPLICATION_SHORT_NAME);
	}

	class RoboxEventProcessor implements Runnable {
		// This boolean is mutable and possibly put here as a work around to not
		// being able to use mutable local variables in lambda expressions, idealy
		// we would not have mutable members in a runnable class.
		private boolean errorWasConsumed;

		private final Printer printer;
		private final RoboxRxPacket rxPacket;
		private final int REEL_EEPROM_COUNTS_BEFORE_CHECK = 400;

		public RoboxEventProcessor(Printer printer, RoboxRxPacket rxPacket) {
			this.printer = printer;
			this.rxPacket = rxPacket;
		}

		@Override
		public void run() {
			switch (rxPacket.getPacketType()) {
				case ACK_WITH_ERRORS:
					AckResponse ackResponse = (AckResponse) rxPacket;
					latestErrorResponse = ackResponse;

					LOGGER.trace(ackResponse.toString());

					if (ackResponse.isError()) {
						List<FirmwareError> errorsFound = new ArrayList<>(ackResponse.getFirmwareErrors());

						// Copy the error consumer list to stop concurrent modification exceptions if the consumer deregisters itself
						Map<ErrorConsumer, List<FirmwareError>> errorsToIterateThrough = new WeakHashMap<>(errorConsumers);

						if (commandInterface.isLocalPrinter()) {
							// Only clear the firmware errors on a local printer.
							// They will be cleared by the associated Root on a remote printer.
							try {
								transmitResetErrors();
							}
							catch (RoboxCommsException ex) {
								LOGGER.warn("Couldn't clear firmware error list");
							}
						}

						if (processErrors) {
							List<FirmwareError> newErrors = new ArrayList<>();

							StringBuilder errorOutput = new StringBuilder();
							ackResponse.getFirmwareErrors().forEach(error -> {
								errorOutput.append(i18n.t(error.getErrorTitleKey()));
								errorOutput.append('\n');
							});
							LOGGER.info(errorOutput.toString());

							errorsFound.stream().forEach(foundError -> {
								errorWasConsumed = false;
								if (foundError == FirmwareError.Z_TOP_SWITCH) {
									if (head.get() != null) {
										LOGGER.error("Z+ switch error occurred at " + head.get().headZPosition.get() + "mm");
									}
									else {
										LOGGER.error("Z+ switch occurred - no head so height could not be determined");
									}
								}

								if (foundError.isRequireUserToClear() && !newErrors.contains(foundError)) {
									newErrors.add(foundError);
								}

								if (foundError.isRequireUserToClear() && !activeErrors.contains(foundError)) {
									activeErrors.add(foundError);
								}

								if (suppressedFirmwareErrors.contains(foundError) || (foundError == FirmwareError.HEAD_POWER_EEPROM && doNotCheckForPresenceOfHead)
										|| ((foundError == FirmwareError.D_FILAMENT_SLIP || foundError == FirmwareError.E_FILAMENT_SLIP) && printerStatus.get() == PrinterStatus.IDLE && !inCommissioningMode)
										|| (foundError == FirmwareError.NOZZLE_FLUSH_NEEDED && (printerStatus.get() == PrinterStatus.IDLE || printerStatus.get() == PrinterStatus.PURGING_HEAD) && !inCommissioningMode)) {
									LOGGER.debug("Error:" + foundError.name() + " suppressed");
								}
								else {
									if (!currentErrors.contains(foundError))
										currentErrors.add(foundError);

									errorsToIterateThrough.forEach((consumer, errorList) -> {
										if (errorList.contains(foundError) || errorList.contains(FirmwareError.ALL_ERRORS)) {
											LOGGER.debug("Error:" + foundError.name() + " passed to " + consumer.toString());
											consumer.consumeError(foundError);
											errorWasConsumed = true;
										}
									});
									if (!errorWasConsumed) {
										LOGGER.info("Default action for error:" + foundError.name());
										systemNotificationManager.processErrorPacketFromPrinter(foundError, printer);
									}
								}
							});
							if (!commandInterface.isLocalPrinter()) {
								List<FirmwareError> lostErrors = new ArrayList<>();
								activeErrors.stream().forEach(activeError -> {
									if (!newErrors.contains(activeError)) {
										lostErrors.add(activeError);
									}
								});
								// Ideally, currentErrors should be a superset of activeErrors,
								// but in practise activeErrors can contain some suppressed errors.
								currentErrors.stream().forEach(currentError -> {
									if (!errorsFound.contains(currentError) && !lostErrors.contains(currentError)) {
										lostErrors.add(currentError);
									}
								});
								lostErrors.stream().forEach(lostError -> {
									LOGGER.info("Error no longer current:" + lostError.name());
									activeErrors.remove(lostError);
									currentErrors.remove(lostError);
								});
							}
							LOGGER.trace(ackResponse.toString());
						}
						else {
							errorsFound.stream().forEach(foundError -> {
								LOGGER.info("No action for error:" + foundError.name());
							});
						}
					}
					else {
						if (processErrors && !commandInterface.isLocalPrinter() && (!activeErrors.isEmpty() || !currentErrors.isEmpty())) {
							activeErrors.stream().forEach(activeError -> {
								LOGGER.info("Error no longer active:" + activeError.name());
							});
							activeErrors.clear();
							currentErrors.clear();
						}
					}
					break;

				case STATUS_RESPONSE:
					if (initalStatusCount < 2) {
						initalStatusCount++;
					}
					StatusResponse statusResponse = (StatusResponse) rxPacket;
					latestStatusResponse = statusResponse;
					LOGGER.trace(statusResponse.toString());

					/*
					 * Ancillary systems
					 */
					headPowerOnFlag.set(statusResponse.isHeadPowerOn());
					printerAncillarySystems.ambientTemperature.set(statusResponse.getAmbientTemperature());
					printerAncillarySystems.ambientTargetTemperature.set(statusResponse.getAmbientTargetTemperature());
					printerAncillarySystems.bedTemperature.set(statusResponse.getBedTemperature());
					printerAncillarySystems.bedTargetTemperature.set(statusResponse.getBedTargetTemperature());
					printerAncillarySystems.bedFirstLayerTargetTemperature.set(statusResponse.getBedFirstLayerTargetTemperature());
					printerAncillarySystems.ambientFanOn.set(statusResponse.isAmbientFanOn());
					printerAncillarySystems.bedHeaterMode.set(statusResponse.getBedHeaterMode());
					printerAncillarySystems.headFanOn.set(statusResponse.isHeadFanOn());
					printerAncillarySystems.XStopSwitch.set(statusResponse.isxSwitchStatus());
					printerAncillarySystems.YStopSwitch.set(statusResponse.isySwitchStatus());
					printerAncillarySystems.ZStopSwitch.set(statusResponse.iszSwitchStatus());
					printerAncillarySystems.ZTopStopSwitch.set(statusResponse.isTopZSwitchStatus());
					printerAncillarySystems.bAxisHome.set(statusResponse.isNozzleSwitchStatus());
					printerAncillarySystems.doorOpen.set(statusResponse.isDoorOpen());
					printerAncillarySystems.reelButton.set(statusResponse.isReelButtonPressed());
					printerAncillarySystems.feedRateEMultiplier.set(statusResponse.getFeedRateEMultiplier());
					printerAncillarySystems.feedRateDMultiplier.set(statusResponse.getFeedRateDMultiplier());
					printerAncillarySystems.whyAreWeWaitingProperty.set(statusResponse.getWhyAreWeWaitingState());
					printerAncillarySystems.updateGraphData();
					printerAncillarySystems.sdCardInserted.set(statusResponse.issdCardPresent());
					printerAncillarySystems.dualReelAdaptorPresent.set(statusResponse.isDualReelAdaptorPresent());

					if (!statusResponse.issdCardPresent() && !suppressedFirmwareErrors.contains(FirmwareError.SD_CARD)) {
						systemNotificationManager.showNoSDCardDialog();
					}

					/*
					 * Extruders
					 */
					boolean filament1Loaded = filamentLoadedGetter.getFilamentLoaded(statusResponse, 1);
					boolean filament2Loaded = filamentLoadedGetter.getFilamentLoaded(statusResponse, 2);

					extruders.get(firstExtruderNumber).filamentLoaded.set(filament1Loaded);
					extruders.get(firstExtruderNumber).indexWheelState.set(statusResponse.isEIndexStatus());
					extruders.get(firstExtruderNumber).isFitted.set(statusResponse.isExtruderEPresent());
					extruders.get(firstExtruderNumber).filamentDiameter.set(statusResponse.getEFilamentDiameter());
					extruders.get(firstExtruderNumber).extrusionMultiplier.set(statusResponse.getEFilamentMultiplier());

					extruders.get(secondExtruderNumber).filamentLoaded.set(filament2Loaded);
					extruders.get(secondExtruderNumber).indexWheelState.set(statusResponse.isDIndexStatus());
					extruders.get(secondExtruderNumber).isFitted.set(statusResponse.isExtruderDPresent());
					extruders.get(secondExtruderNumber).filamentDiameter.set(statusResponse.getDFilamentDiameter());
					extruders.get(secondExtruderNumber).extrusionMultiplier.set(statusResponse.getDFilamentMultiplier());

					pauseStatus.set(statusResponse.getPauseStatus());

					busyStatus.set(statusResponse.getBusyStatus());

					if (statusResponse.getBusyStatus() == BusyStatus.LOADING_FILAMENT_E && !filament1Loaded) {
						systemNotificationManager.showKeepPushingFilamentNotification();

					}
					else if (statusResponse.getBusyStatus() == BusyStatus.LOADING_FILAMENT_D && !filament2Loaded) {
						systemNotificationManager.showKeepPushingFilamentNotification();

					}
					else {
						systemNotificationManager.hideKeepPushingFilamentNotification();
					}

					printJobLineNumber.set(statusResponse.getPrintJobLineNumber());
					printJobID.set(statusResponse.getRunningPrintJobID());

					if (head.isNotNull().get()) {
						/*
						 * Heater
						 */
						if (head.get().nozzleHeaters.size() > 0) {
							NozzleHeater nozzleHeater0 = head.get().nozzleHeaters.get(0);
							nozzleHeater0.nozzleTemperature.set(statusResponse.getNozzle0Temperature());
							nozzleHeater0.nozzleFirstLayerTargetTemperature.set(statusResponse.getNozzle0FirstLayerTargetTemperature());
							nozzleHeater0.nozzleTargetTemperature.set(statusResponse.getNozzle0TargetTemperature());
							nozzleHeater0.heaterMode.set(statusResponse.getNozzle0HeaterMode());

							if (head.get().getNozzleHeaters().size() > 1) {
								NozzleHeater nozzleHeater1 = head.get().nozzleHeaters.get(1);
								nozzleHeater1.nozzleTemperature.set(statusResponse.getNozzle1Temperature());
								nozzleHeater1.nozzleFirstLayerTargetTemperature.set(statusResponse.getNozzle1FirstLayerTargetTemperature());
								nozzleHeater1.nozzleTargetTemperature.set(statusResponse.getNozzle1TargetTemperature());
								nozzleHeater1.heaterMode.set(statusResponse.getNozzle1HeaterMode());
							}
							head.get().nozzleHeaters.stream().forEach(heater -> heater.updateGraphData());
						}

						/*
						 * Nozzle data
						 */
						if (head.get().nozzles.size() > 0) {
							// TODO modify to work with multiple nozzles
							// This is only true for the current cam-based heads that only really have one B axis
							head.get().nozzles.stream().forEach(nozzle -> nozzle.BPosition.set(statusResponse.getBPosition()));
						}

						head.get().BPosition.set(statusResponse.getBPosition());
						head.get().headXPosition.set(statusResponse.getHeadXPosition());
						head.get().headYPosition.set(statusResponse.getHeadYPosition());
						head.get().headZPosition.set(statusResponse.getHeadZPosition());
						head.get().nozzleInUse.set(statusResponse.getNozzleInUse());
					}

					checkHeadEEPROM(statusResponse);

					checkReelEEPROMs(statusResponse);

					if (!filament1Loaded && !reels.containsKey(0) && effectiveFilaments.containsKey(0) && effectiveFilaments.get(0) != FilamentContainer.UNKNOWN_FILAMENT) {
						effectiveFilaments.put(0, FilamentContainer.UNKNOWN_FILAMENT);
					}

					if (!filament2Loaded && !reels.containsKey(1) && effectiveFilaments.containsKey(1) && effectiveFilaments.get(1) != FilamentContainer.UNKNOWN_FILAMENT) {
						effectiveFilaments.put(1, FilamentContainer.UNKNOWN_FILAMENT);
					}

					break;

				case FIRMWARE_RESPONSE:
					FirmwareResponse fwResponse = (FirmwareResponse) rxPacket;
					printerIdentity.firmwareVersion.set(fwResponse.getFirmwareRevision());
					break;

				case PRINTER_ID_RESPONSE:
					PrinterIDResponse idResponse = (PrinterIDResponse) rxPacket;
					printerIdentity.printermodel.set(idResponse.getModel());
					printerIdentity.printeredition.set(idResponse.getEdition());
					printerIdentity.printerweekOfManufacture.set(idResponse.getWeekOfManufacture());
					printerIdentity.printeryearOfManufacture.set(idResponse.getYearOfManufacture());
					printerIdentity.printerpoNumber.set(idResponse.getPoNumber());
					printerIdentity.printerserialNumber.set(idResponse.getSerialNumber());
					printerIdentity.printercheckByte.set(idResponse.getCheckByte());
					printerIdentity.printerelectronicsVersion.set(idResponse.getElectronicsVersion());
					printerIdentity.printerFriendlyName.set(idResponse.getPrinterFriendlyName());
					printerIdentity.printerColour.set(Color.web(idResponse.getPrinterColour()));
					break;

				case REEL_0_EEPROM_DATA:
				case REEL_1_EEPROM_DATA:
					ReelEEPROMDataResponse reelResponse = (ReelEEPROMDataResponse) rxPacket;
					processReelResponse(reelResponse);
					break;

				case HEAD_EEPROM_DATA:
					//                    LOGGER.info("Head EEPROM data received");

					HeadEEPROMDataResponse headResponse = (HeadEEPROMDataResponse) rxPacket;

					if (repairCorruptEEPROMData) {
						if (Head.isTypeCodeValid(headResponse.getHeadTypeCode())) {
							// Might be unrecognised but correct format for a Robox head type code

							if (headContainer.isTypeCodeInDatabase(headResponse.getHeadTypeCode())) {
								if (head.get() == null) {
									// No head attached to model

									Head newHead = null;

									HeadFile headData = headContainer.getHeadByID(headResponse.getHeadTypeCode());
									if (headData != null) {
										newHead = headFactory.create(headData);
										newHead.updateFromEEPROMData(headResponse);
										newHead.name.set(i18n.t("headPanel." + newHead.typeCodeProperty().get() + ".titleBold") + i18n.t("headPanel." + newHead.typeCodeProperty().get() + ".titleLight"));
									}
									else {
										LOGGER.error("Attempt to create head with invalid or absent type code");
									}

									head.set(newHead);
								}
								else {
									// Head already attached to model
									head.get().updateFromEEPROMData(headResponse);
								}

								// Check to see if the data is in bounds
								// Suppress the check if we are calibrating, since out of bounds data is used during this operation
								if (!headIntegrityChecksInhibited) {
									RepairResult result = head.get().bringDataInBounds();

									switch (result) {
										case REPAIRED_WRITE_ONLY:
											try {
												writeHeadEEPROM(head.get());
												LOGGER.info("Automatically updated head data - no calibration required");
												systemNotificationManager.showHeadUpdatedNotification();
											}
											catch (RoboxCommsException ex) {
												LOGGER.error("Error updating head after repair " + ex.getMessage());
											}
											break;
										case REPAIRED_WRITE_AND_RECALIBRATE:
											try {
												writeHeadEEPROM(head.get());
												systemNotificationManager.showCalibrationDialogue();
												LOGGER.info("Automatically updated head data - calibration suggested");
											}
											catch (RoboxCommsException ex) {
												LOGGER.error("Error updating head after repair " + ex.getMessage());
											}
											break;

										default:
											break;

									}
								}
							}
							else if (!headIntegrityChecksInhibited) {
								// We don't recognise the head but it seems to be valid
								systemNotificationManager.showHeadNotRecognisedDialog(printerIdentity.printerFriendlyName.get());
								LOGGER.error("Head with type code: " + headResponse.getHeadTypeCode() + " attached. Not in database so ignoring...");
							}
						}
						else {
							if (repairCorruptEEPROMData && !headIntegrityChecksInhibited) {
								// Either not set or type code doesn't match Robox head type code
								systemNotificationManager.showProgramInvalidHeadDialog((TaskResponse<HeadFile> taskResponse) -> {
									HeadFile chosenHeadFile = taskResponse.getReturnedObject();

									if (chosenHeadFile != null) {
										Head chosenHead = headFactory.create(chosenHeadFile);
										chosenHead.allocateRandomID();
										head.set(chosenHead);
										LOGGER.info("Reprogrammed head as " + chosenHeadFile.getTypeCode() + " with ID " + head.get().uniqueID.get());
										try {
											writeHeadEEPROM(head.get());
											systemNotificationManager.showCalibrationDialogue();
											LOGGER.info("Automatically updated head data - calibration suggested");
										}
										catch (RoboxCommsException ex) {
											LOGGER.error("Error updating head after repair " + ex.getMessage());
										}
									}
									else {
										// Force the head prompt - we must have been cancelled
										lastHeadEEPROMState.set(EEPROMState.NOT_PRESENT);
									}
								});
							}
						}
					}
					else {
						// We've been asked to suppress EEPROM error handling - load the data anyway
						if (head.get() == null) {
							// No head attached to model
							Head newHead = null;

							HeadFile headData = headContainer.getHeadByID(headResponse.getHeadTypeCode());
							if (headData != null) {
								newHead = headFactory.create(headData);
								newHead.updateFromEEPROMData(headResponse);
							}
							else {
								LOGGER.error("Attempt to create head with invalid or absent type code");
							}
							head.set(newHead);
						}
						else {
							// Head already attached to model
							head.get().updateFromEEPROMData(headResponse);
						}
					}
					break;

				case GCODE_RESPONSE:
					break;

				case HOURS_COUNTER:
					HoursCounterResponse hoursResponse = (HoursCounterResponse) rxPacket;
					printerAncillarySystems.hoursCounter.set(hoursResponse.getHoursCounter());
					break;

				case SEND_FILE:
					break;

				default:
					LOGGER.warn("Unknown packet type delivered to Printer Status: " + rxPacket.getPacketType().name());
					break;
			}
		}

		private void checkHeadEEPROM(StatusResponse statusResponse) {
			if (lastHeadEEPROMState.get() != statusResponse.getHeadEEPROMState()) {
				lastHeadEEPROMState.set(statusResponse.getHeadEEPROMState());
				switch (statusResponse.getHeadEEPROMState()) {
					case NOT_PRESENT:
						head.set(null);
						break;
					case NOT_PROGRAMMED:
						LOGGER.error("Unformatted head detected - no action taken");
						head.set(null);
						//                        try
						//                        {
						//                            formatHeadEEPROM();
						//                        } catch (PrinterException ex)
						//                        {
						//                            LOGGER.error("Error formatting head");
						//                        }
						break;
					case PROGRAMMED:
						try {
							LOGGER.debug("About to read head EEPROM");
							readHeadEEPROM(false);
						}
						catch (RoboxCommsException ex) {
							LOGGER.error("Error attempting to read head eeprom", ex);
						}
						break;
				}
			}
		}

		private void checkReelEEPROMs(StatusResponse statusResponse) {
			for (int reelNumber = 0; reelNumber < maxNumberOfReels; reelNumber++) {
				if ((lastReelEEPROMState.get(reelNumber) != statusResponse.getReelEEPROMState(reelNumber)) || getEEPROMCheckForReel(reelNumber)) {
					setEEPROMCheckForReel(reelNumber, false);
					lastReelEEPROMState.set(reelNumber, statusResponse.getReelEEPROMState(reelNumber));
					switch (statusResponse.getReelEEPROMState(reelNumber)) {
						case NOT_PRESENT:
							effectiveFilaments.put(reelNumber, FilamentContainer.UNKNOWN_FILAMENT);
							reels.remove(reelNumber);
							break;
						case NOT_PROGRAMMED:
							effectiveFilaments.put(reelNumber, FilamentContainer.UNKNOWN_FILAMENT);
							reels.remove(reelNumber);
							LOGGER.error("Unformatted reel detected - no action taken");
							//                            try
							//                            {
							//                                formatReelEEPROM(reelNumber);
							//                            } catch (PrinterException ex)
							//                            {
							//                                LOGGER.error("Error formatting reel " + reelNumber);
							//                            }
							break;
						case PROGRAMMED:
							try {
								readReelEEPROM(reelNumber, false);
							}
							catch (RoboxCommsException ex) {
								LOGGER.error("Error attempting to read reel " + reelNumber + " eeprom");
							}
							break;
					}
				}
			}
		}

		/**
		 * Process a {@link ReelEEPROMDataResponse}.
		 * 
		 * @param reelResponse
		 */
		private void processReelResponse(ReelEEPROMDataResponse reelResponse) {
			Reel reel;
			if (!reels.containsKey(reelResponse.getReelNumber())) {
				reel = reelFactory.create();
				reel.updateFromEEPROMData(reelResponse);
				reels.put(reelResponse.getReelNumber(), reel);
			}
			else {
				reel = reels.get(reelResponse.getReelNumber());
				reel.updateFromEEPROMData(reelResponse);
			}

			extruders.get(reelResponse.getReelNumber()).lastFeedrateMultiplierInUse.set(reelResponse.getFeedRateMultiplier());

			Filament filament = new Filament(reelResponse);

			if (runningRoot() && filament.isMutable()) {
				// We only wish to display the data from the EEPROM,
				// we don't want to have custom filament files saved on root
				effectiveFilaments.put(reelResponse.getReelNumber(), filament);
				LOGGER.debug("Custom reel loaded to " + reelResponse.getReelNumber() + ", displaying contents of EEPROM");
				return;
			}

			if (!filamentContainer.isFilamentIDInDatabase(reelResponse.getFilamentID())) {
				// unrecognised reel
				saveUnknownFilamentToDatabase(reelResponse);
			}

			if (filamentContainer.isFilamentIDInDatabase(reelResponse.getFilamentID())) {
				// Check to see if the data is in bounds
				RepairResult result = reels.get(reelResponse.getReelNumber()).bringDataInBounds(filamentContainer.getFilamentByID(reelResponse.getFilamentID()));

				switch (result) {
					case REPAIRED_WRITE_ONLY:
						try {
							writeReelEEPROM(reelResponse.getReelNumber(), reels.get(reelResponse.getReelNumber()), true);
							LOGGER.debug("Automatically updated reel data");
							systemNotificationManager.showReelUpdatedNotification();
							// Force the next iteration of status check to read the reel eeprom
							setEEPROMCheckForReel(reelResponse.getReelNumber(), true);
						}
						catch (RoboxCommsException ex) {
							LOGGER.error("Error updating reel after repair " + ex.getMessage());
						}
						break;
					default:
						// Update the effective filament if *and only if* we have this filament in our database
						// Should happen on the second time through after an auto-update
						filament = filamentContainer.getFilamentByID(reelResponse.getFilamentID());
						effectiveFilaments.put(reelResponse.getReelNumber(), filament);
						break;
				}
			}
		}

		/**
		 * Update the database with the filament details.
		 * 
		 * Before V3.01.00 the behaviour used to be as follows: "If the filament is not a Robox filament then update the database with the filament details. If it is an unknown Robox filament just add it to the database in memory but do not save it to disk."
		 * 
		 * The new behaviour means that missing Robox filaments are saved to the users filament directory.
		 *
		 * @param reelResponse
		 */
		private void saveUnknownFilamentToDatabase(ReelEEPROMDataResponse reelResponse) {
			Filament filament = new Filament(reelResponse);
			filamentContainer.saveFilament(filament);
			// if (filament.isMutable())
			// {
			// filamentContainer.saveFilament(filament);
			// } else
			// {
			// filamentContainer.addFilamentToUserFilamentList(filament);
			// }
		}
	};

	@Override
	public void resetHeadToDefaults() throws PrinterException {
		if (head.get() != null) {
			head.get().resetToDefaults();
			try {
				writeHeadEEPROM(head.get());
			}
			catch (RoboxCommsException ex) {
				LOGGER.error("Error whilst writing default head EEPROM data - " + ex.getMessage());
			}
		}
		else {
			throw new PrinterException("Asked to reset head to defaults when no head was attached");
		}
	}

	@Override
	public void inhibitHeadIntegrityChecks(boolean inhibit) {
		headIntegrityChecksInhibited = inhibit;
	}

	@Override
	public void changeEFeedRateMultiplier(double feedRate) throws PrinterException {
		LOGGER.debug("Firing change feed rate multiplier for E: " + feedRate);

		try {
			SetEFeedRateMultiplier setFeedRateMultiplier = (SetEFeedRateMultiplier) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_E_FEED_RATE_MULTIPLIER);
			setFeedRateMultiplier.setFeedRateMultiplier(feedRate);
			commandInterface.writeToPrinter(setFeedRateMultiplier);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception when settings feed rate");
			throw new PrinterException("Comms exception when settings feed rate");
		}
	}

	@Override
	public void changeDFeedRateMultiplier(double feedRate) throws PrinterException {
		LOGGER.debug("Firing change feed rate multiplier for D: " + feedRate);

		try {
			SetDFeedRateMultiplier setFeedRateMultiplier = (SetDFeedRateMultiplier) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_D_FEED_RATE_MULTIPLIER);
			setFeedRateMultiplier.setFeedRateMultiplier(feedRate);
			commandInterface.writeToPrinter(setFeedRateMultiplier);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception when settings feed rate");
			throw new PrinterException("Comms exception when settings feed rate");
		}
	}

	@Override
	public void changeFilamentInfo(String extruderLetter, double filamentDiameter, double extrusionMultiplier) throws PrinterException {
		Extruder selectedExtruder = null;

		for (Extruder extruder : extruders) {
			if (extruder.getExtruderAxisLetter().equalsIgnoreCase(extruderLetter) && extruder.isFittedProperty().get()) {
				selectedExtruder = extruder;
				break;
			}
		}

		if (selectedExtruder == null) {
			throw new PrinterException("Attempt to change filament info for non-existent extruder: " + extruderLetter);
		}

		LOGGER.debug("Firing change filament info:" + "Extruder " + extruderLetter + " Diameter " + filamentDiameter + " Multiplier " + extrusionMultiplier);

		try {
			switch (extruderLetter) {
				case "E":
					SetEFilamentInfo setEFilamentInfo = (SetEFilamentInfo) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_E_FILAMENT_INFO);
					setEFilamentInfo.setFilamentInfo(filamentDiameter, extrusionMultiplier);
					commandInterface.writeToPrinter(setEFilamentInfo);
					break;
				case "D":
					SetDFilamentInfo setDFilamentInfo = (SetDFilamentInfo) RoboxTxPacketFactory.createPacket(TxPacketTypeEnum.SET_D_FILAMENT_INFO);
					setDFilamentInfo.setFilamentInfo(filamentDiameter, extrusionMultiplier);
					commandInterface.writeToPrinter(setDFilamentInfo);
					break;
			}
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Comms exception when setting filament info for extruder " + extruderLetter);
			throw new PrinterException("Comms exception when setting filament info for extruder " + extruderLetter);
		}
	}

	@Override
	public NozzleHeightStateTransitionManager getNozzleHeightCalibrationStateManager() {
		return calibrationHeightManager;
	}

	public SingleNozzleHeightStateTransitionManager getSingleNozzleHeightCalibrationStateManager() {
		return calibrationSingleNozzleHeightManager;
	}

	@Override
	public NozzleOpeningStateTransitionManager getNozzleOpeningCalibrationStateManager() {
		return calibrationOpeningManager;
	}

	@Override
	public XAndYStateTransitionManager getNozzleAlignmentCalibrationStateManager() {
		return calibrationAlignmentManager;
	}

	@Override
	public void loadFirmware(String firmwareFilePath) {
		commandInterface.loadFirmware(firmwareFilePath);
	}

	/**
	 *
	 * @return
	 */
	@Override
	public ObservableList<EEPROMState> getReelEEPROMStateProperty() {
		return lastReelEEPROMState;
	}

	@Override
	public ReadOnlyObjectProperty<EEPROMState> getHeadEEPROMStateProperty() {
		return lastHeadEEPROMState;
	}

	@Override
	public void startComms() {
		commandInterface.start();
	}

	@Override
	public void stopComms() {
		commandInterface.shutdown();
	}

	@Override
	public String toString() {
		return printerIdentity.printerFriendlyName.get();
	}

	@Override
	public void setCommissioningTestMode(boolean inCommissioningMode) {
		this.inCommissioningMode = inCommissioningMode;
	}

	@Override
	public void clearError(FirmwareError error) {
		if (activeErrors.contains(error) || currentErrors.contains(error)) {
			commandInterface.clearError(error);
			activeErrors.remove(error);
			currentErrors.remove(error);
		}
	}

	@Override
	public void clearAllErrors() {
		commandInterface.clearAllErrors();
		activeErrors.clear();
		currentErrors.clear();
	}

	@Override
	public ObservableList<FirmwareError> getActiveErrors() {
		return activeErrors;
	}

	@Override
	public ObservableList<FirmwareError> getCurrentErrors() {
		return currentErrors;
	}

	@Override
	public List<PrintJobStatistics> listReprintableJobs() {
		List<PrintJobStatistics> orderedStats = new ArrayList<>();
		Path printJobsPath = printJobsPathPreference.getValue();

		try {
			LOGGER.debug("Getting reprintable print jobs");
			File printSpoolDir = printJobsPath.toFile();
			for (File printJobDir : printSpoolDir.listFiles()) {
				LOGGER.debug("Checking file: " + printJobDir.getName());
				if (printJobDir.isDirectory()) {
					PrintJob pj = printJobFactory.create(printJobDir.getName());
					File roboxisedGCode = pj.getRoboxisedFileLocation().toFile();
					File statistics = pj.getStatisticsFileLocation().toFile();

					if (roboxisedGCode.exists())
						LOGGER.debug("Has roboxisedGCode " + roboxisedGCode.getName());
					if (statistics.exists())
						LOGGER.debug("Has statistics " + statistics.getName());
					if (roboxisedGCode.exists() && statistics.exists()) {
						LOGGER.debug("Adding stats to list");

						// Valid files - does it work for us?
						try {
							PrintJobStatistics stats = pj.getStatistics();
							orderedStats.add(stats);
						}
						catch (IOException ex) {
							LOGGER.error("Failed to load stats from " + printJobDir.getName(), ex);
						}
					}
				}
			}
		}
		catch (Exception ex) {
			LOGGER.error("Failed to listFiles from " + printJobsPath.toString(), ex);
		}
		return orderedStats;
	}

	@Override
	public List<SuitablePrintJob> listJobsReprintableByMe() {
		return createSuitablePrintJobsFromStatistics(listReprintableJobs());
	}

	@Override
	public List<SuitablePrintJob> createSuitablePrintJobsFromStatistics(List<PrintJobStatistics> orderedStats) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss dd-MM-yyyy");

		List<SuitablePrintJob> suitablePrintJobs = new ArrayList<>();

		orderedStats.sort((PrintJobStatistics o1, PrintJobStatistics o2) -> o1.getCreationDate().compareTo(o2.getCreationDate()));
		// Make sure the newest are at the top
		Collections.reverse(orderedStats);

		for (PrintJobStatistics stats : orderedStats) {
			LOGGER.debug("Checking job " + stats.getPrintJobID());
			HeadType printedWithHeadType = null;
			try {
				printedWithHeadType = HeadType.valueOf(stats.getPrintedWithHeadType());
				LOGGER.debug("    job printed with head type" + printedWithHeadType.toString());
			}
			catch (IllegalArgumentException e) {
				LOGGER.debug("    HeadType is invalid");
			}
			if (headProperty().get() == null)
				LOGGER.debug("    head is null");
			if (headProperty().get() != null && headProperty().get().headTypeProperty().get() != printedWithHeadType)
				LOGGER.debug("    headTypeProperty ( " + headProperty().get().headTypeProperty().get().toString() + ") != printedWithHeadType");
			if (headProperty().get() != null && headProperty().get().headTypeProperty().get() == printedWithHeadType) {
				// The head type matches
				boolean material1RequirementsMet = true;
				if (stats.getRequiresMaterial1()) {
					if (!extruders.get(0).isFittedProperty().get() || !extruders.get(0).filamentLoadedProperty().get() || !reels.containsKey(0)) {
						material1RequirementsMet = false;
					}
				}
				if (material1RequirementsMet)
					LOGGER.debug("material1RequirementsMet = true");
				else
					LOGGER.debug("material1RequirementsMet = false");

				boolean material2RequirementsMet = true;
				if (stats.getRequiresMaterial2()) {
					if (!extruders.get(1).isFittedProperty().get() || !extruders.get(1).filamentLoadedProperty().get() || !reels.containsKey(1)) {
						material2RequirementsMet = false;
					}
				}

				if (material2RequirementsMet)
					LOGGER.debug("material2RequirementsMet = true");
				else
					LOGGER.debug("material2RequirementsMet = false");

				if (material1RequirementsMet && material2RequirementsMet) {
					if (suitablePrintJobs.size() < MAX_RETAINED_PRINT_JOBS) {
						LOGGER.debug("Adding to suitable print jobs.");
						// Yay - this one is suitable
						SuitablePrintJob suitablePrintJob = new SuitablePrintJob();
						suitablePrintJob.setPrintJobID(stats.getPrintJobID());
						suitablePrintJob.setPrintJobName(stats.getProjectName());
						suitablePrintJob.setPrintJobPath(stats.getProjectPath());
						suitablePrintJob.setPrintProfileName(stats.getProfileName());
						suitablePrintJob.setDurationInSeconds(stats.getPredictedDuration());
						suitablePrintJob.seteVolume(stats.geteVolumeUsed());
						suitablePrintJob.setdVolume(stats.getdVolumeUsed());
						suitablePrintJob.setCreationDate(dateFormat.format(stats.getCreationDate()));
						suitablePrintJobs.add(suitablePrintJob);
					}
					else {
						LOGGER.debug("Suitable job - but too many jobs.");
						break;
					}
				}
			}
		}

		return suitablePrintJobs;
	}

	@Override
	public void tidyPrintJobDirectories() {
		printJobCleaner.tidyPrintJobDirectories();
	}

	@Override
	public boolean printJob(String printJobID) {
		PrintJob printJob = printJobFactory.create(printJobID);
		return getPrintEngine().printFileFromDisk(printJob);
	}

	@Override
	public boolean printJobFromDirectory(String printJobName, Path directoryPath) {
		LOGGER.info("printJobFromDirectory(\"" + printJobName + "\", \"" + directoryPath);
		PrintJob printJob = new PrintJob(printJobName, directoryPath);
		return getPrintEngine().spoolAndPrintFileFromDisk(printJob);
	}

	@Override
	public AckResponse getLastErrorResponse() {
		return latestErrorResponse;
	}

	@Override
	public StatusResponse getLastStatusResponse() {
		return latestStatusResponse;
	}
}
