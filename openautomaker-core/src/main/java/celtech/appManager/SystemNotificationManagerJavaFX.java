package celtech.appManager;

import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.SystemErrorHandlerOptions;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.notification_manager.NotificationType;
import org.openautomaker.base.notification_manager.PurgeResponse;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.services.firmware.FirmwareLoadResult;
import org.openautomaker.base.services.firmware.FirmwareLoadService;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.base.task_executor.TaskResponder;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.application.NamePreference;
import org.openautomaker.environment.preference.slicer.SafetyFeaturesPreference;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.ui.StageManager;
import org.openautomaker.ui.component.choice_link_button.ChoiceLinkButton;
import org.openautomaker.ui.component.choice_link_dialog_box.ChoiceLinkDialogBox;
import org.openautomaker.ui.component.choice_link_dialog_box.ChoiceLinkDialogBox.PrinterDisconnectedException;
import org.openautomaker.ui.component.printer_id_dialog.PrinterIDDialog;
import org.openautomaker.ui.component.progress_dialog.ProgressDialog;

import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.components.Notifications.NotificationDisplay;
import celtech.coreUI.controllers.popups.ResetPrinterIDController;
import celtech.roboxbase.comms.RoboxResetIDResult;
import celtech.roboxbase.comms.rx.FirmwareError;
import celtech.roboxbase.comms.rx.PrinterIDResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 *
 * @author Ian
 */
@Singleton
public class SystemNotificationManagerJavaFX implements SystemNotificationManager {
	private final int DROOP_ERROR_CLEAR_TIME = 30000; // Milliseconds (= 30 seconds)

	private static final Logger LOGGER = LogManager.getLogger();

	private HashMap<SystemErrorHandlerOptions, ChoiceLinkButton> errorToButtonMap = null;

	/*
	 * SD card dialog
	 */
	protected boolean sdDialogOnDisplay = false;

	private Stage programInvalidHeadStage = null;

	private boolean headNotRecognisedDialogOnDisplay = false;

	private boolean reelNotRecognisedDialogOnDisplay = false;

	private boolean clearBedDialogOnDisplay = false;

	private boolean calibrateDialogOnDisplay = false;

	/*
	 * Firmware upgrade progress
	 */
	protected ProgressDialog firmwareUpdateProgress = null;

	/*
	 * Printer ID Dialog
	 */
	protected PrinterIDDialog printerIDDialog = null;

	private ChoiceLinkDialogBox keepPushingFilamentDialogBox = null;

	private ChoiceLinkDialogBox failedTransferDialogBox = null;

	private ChoiceLinkDialogBox failedEjectDialogBox = null;

	private ChoiceLinkDialogBox filamentMotionCheckDialogBox = null;

	private ChoiceLinkDialogBox filamentStuckDialogBox = null;

	private ChoiceLinkDialogBox loadFilamentNowDialogBox = null;

	/*
	 * Error dialog
	 */
	private ChoiceLinkDialogBox errorChoiceBox = null;
	private ListChangeListener<? super FirmwareError> errorChangeListener = null;
	private Thread clearDroopThread = null;

	private final FXMLLoaderFactory fxmlLoaderFactory;
	private final I18N i18n;
	private final StageManager stageManager;
	private final ApplicationStatus applicationStatus;
	private final TaskExecutor taskExecutor;
	private final NotificationDisplay notificationDisplay;
	private final NamePreference namePreference;
	private final SafetyFeaturesPreference safetyFeaturesPreference;

	@Inject
	protected SystemNotificationManagerJavaFX(
			FXMLLoaderFactory fxmlLoaderFactory,
			I18N i18n,
			ApplicationStatus applicationStatus,
			StageManager stageManager,
			TaskExecutor taskExecutor,
			NotificationDisplay notificationDisplay,
			NamePreference namePreference,
			SafetyFeaturesPreference safetyFeaturesPreference) {

		this.fxmlLoaderFactory = fxmlLoaderFactory;
		this.i18n = i18n;
		this.stageManager = stageManager;
		this.applicationStatus = applicationStatus;
		this.taskExecutor = taskExecutor;
		this.notificationDisplay = notificationDisplay;
		this.namePreference = namePreference;
		this.safetyFeaturesPreference = safetyFeaturesPreference;
	}

	private void clearErrorChoiceDialog(Printer printer) {
		if (errorChangeListener != null) {
			printer.getCurrentErrors().removeListener(errorChangeListener);
			errorChangeListener = null;
		}
		if (errorChoiceBox != null) {
			if (errorChoiceBox.isShowing())
				errorChoiceBox.close();
			errorChoiceBox = null;
		}
	}

	@Override
	public void showErrorNotification(String title, String message) {
		taskExecutor.runOnGUIThread(() -> {
			notificationDisplay.displayTimedNotification(title, message, NotificationType.CAUTION);
		});
	}

	@Override
	public void showWarningNotification(String title, String message) {
		taskExecutor.runOnGUIThread(() -> {
			notificationDisplay.displayTimedNotification(title, message, NotificationType.WARNING);
		});
	}

	@Override
	public void showInformationNotification(String title, String message) {
		taskExecutor.runOnGUIThread(() -> {
			notificationDisplay.displayTimedNotification(title, message, NotificationType.NOTE);
		});
	}

	@Override
	public void processErrorPacketFromPrinter(FirmwareError error, Printer printer) {
		taskExecutor.runOnGUIThread(() -> {
			switch (error) {
				case B_POSITION_LOST:
					LOGGER.warn("B Position Lost error detected");
					printer.clearError(error);
					break;

				case B_POSITION_WARNING:
					LOGGER.warn("B Position Warning error detected");
					printer.clearError(error);
					break;

				case ERROR_BED_TEMPERATURE_DROOP:
					if (clearDroopThread == null) {
						LOGGER.warn("Bed Temperature Droop Warning error detected");
						clearDroopThread = new Thread(() -> {
							try {
								// Clear the error after 30 seconds.
								// It is shown as a tooltip on the printerComponent.
								Thread.sleep(DROOP_ERROR_CLEAR_TIME);
								printer.clearError(error);
							}
							catch (Exception ex) {
								// May get some errors if printer has been disconnected.
							}
							finally {
								clearDroopThread = null;
							}
						});
						clearDroopThread.setDaemon(true);
						clearDroopThread.start();
					}
					break;

				default:
					if (errorChoiceBox == null) {
						setupErrorOptions();
						errorChoiceBox = new ChoiceLinkDialogBox(true);
						String printerName = printer.getPrinterIdentity().printerFriendlyNameProperty().get();
						if (printerName != null) {
							errorChoiceBox.setTitle(printerName + ": " + i18n.t(error.getErrorTitleKey()));
						}
						else {
							errorChoiceBox.setTitle(i18n.t(error.getErrorTitleKey()));
						}
						errorChoiceBox.setMessage(i18n.t(error.getErrorMessageKey()));
						error.getOptions()
								.stream()
								.forEach(option -> errorChoiceBox.addChoiceLink(errorToButtonMap.get(option)));
						errorChangeListener = (ListChangeListener.Change<? extends FirmwareError> c) -> {
							if (!printer.getCurrentErrors().contains(error)) {
								errorChoiceBox.closeDueToPrinterDisconnect();
								clearErrorChoiceDialog(printer);
							}
						};
						printer.getCurrentErrors().addListener(errorChangeListener);

						Optional<ChoiceLinkButton> buttonPressed = Optional.empty();
						try {
							// This shows the dialog and waits for user input.
							buttonPressed = errorChoiceBox.getUserInput();
						}
						catch (PrinterDisconnectedException ex) {
							buttonPressed = Optional.empty();
						}

						if (buttonPressed.isPresent()) {
							for (Entry<SystemErrorHandlerOptions, ChoiceLinkButton> mapEntry : errorToButtonMap.entrySet()) {
								if (buttonPressed.get() == mapEntry.getValue()) {
									switch (mapEntry.getKey()) {
										case ABORT:
										case OK_ABORT:
											try {
												if (printer.canPauseProperty().get()) {
													printer.pause();
												}
												if (printer.canCancelProperty().get()) {
													printer.cancel(null, safetyFeaturesPreference.getValue());
												}
											}
											catch (PrinterException ex) {
												LOGGER.error(
														"Error whilst cancelling print from error dialog");
											}
											break;

										case CLEAR_CONTINUE:
											try {
												if (printer.canResumeProperty().get()) {
													printer.resume();
												}
											}
											catch (PrinterException ex) {
												LOGGER.error(
														"Error whilst resuming print from error dialog");
											}
											break;

										default:
											break;
									}

									break;
								}
							}
						}
						printer.clearError(error);
						clearErrorChoiceDialog(printer);
					}
					break;
			}
		});
	}

	private void setupErrorOptions() {
		// This can't be called from the constructor because the instance is constructed before
		// i18n has been initialised.
		if (errorToButtonMap == null) {
			errorToButtonMap = new HashMap<>();
			for (SystemErrorHandlerOptions option : SystemErrorHandlerOptions.values()) {
				ChoiceLinkButton buttonToAdd = new ChoiceLinkButton();
				buttonToAdd.setTitle(i18n.t(option.getErrorTitleKey()));
				buttonToAdd.setMessage(i18n.t(option.getErrorMessageKey()));
				errorToButtonMap.put(option, buttonToAdd);
			}
		}
	}

	@Override
	public void showCalibrationDialogue() {
		if (!calibrateDialogOnDisplay) {
			calibrateDialogOnDisplay = true;
			taskExecutor.runOnGUIThread(() -> {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.headUpdateCalibrationRequiredTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.headUpdateCalibrationRequiredInstruction"));
				ChoiceLinkButton okCalibrateChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.headUpdateCalibrationYes"));
				//ChoiceLinkButton dontCalibrateChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.headUpdateCalibrationNo"));

				Optional<ChoiceLinkButton> calibrationResponse;
				try {
					calibrationResponse = choiceLinkDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					return;
				}
				finally {
					calibrateDialogOnDisplay = false;
				}

				if (calibrationResponse.isPresent()) {
					if (calibrationResponse.get() == okCalibrateChoice) {
						applicationStatus.setMode(ApplicationMode.CALIBRATION_CHOICE);
					}
				}
			});
		}
	}

	@Override
	public void showHeadUpdatedNotification() {
		taskExecutor.runOnGUIThread(() -> {
			showInformationNotification(
					i18n.t("notification.headSettingsUpdatedTitle"),
					i18n.t("notification.noActionRequired"));
		});
	}

	@Override
	public void showSDCardNotification() {
		if (!sdDialogOnDisplay) {
			taskExecutor.runOnGUIThread(() -> {
				sdDialogOnDisplay = true;
				showErrorNotification(i18n.t("dialogs.noSDCardTitle"),
						i18n.t("dialogs.noSDCardMessage"));
				sdDialogOnDisplay = false;
			});
		}
	}

	@Override
	public void showSliceSuccessfulNotification() {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.sliceSuccessful"));
	}

	@Override
	public void showGCodePostProcessSuccessfulNotification() {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.gcodePostProcessSuccessful"));
	}

	@Override
	public void showPrintJobCancelledNotification() {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.printJobCancelled"));
	}

	@Override
	public void showPrintJobFailedNotification() {
		showErrorNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.printJobFailed"));
	}

	@Override
	public void showPrintTransferSuccessfulNotification(String printerName) {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.printTransferredSuccessfully")
				+ " "
				+ printerName + "\n"
				+ i18n.t("notification.printTransferredSuccessfullyEnd"));
	}

	/**
	 *
	 * @param printerName
	 */
	@Override
	public void showPrintTransferFailedNotification(String printerName) {
		if (failedTransferDialogBox == null) {
			taskExecutor.runOnGUIThread(() -> {
				if (failedTransferDialogBox == null) {
					failedTransferDialogBox = new ChoiceLinkDialogBox(false);
					failedTransferDialogBox.setTitle(i18n.t("notification.PrintQueueTitle"));
					failedTransferDialogBox.setMessage(i18n.t(
							"notification.printTransferFailed"));

					failedTransferDialogBox.addChoiceLink(i18n.t("misc.OK"));

					try {
						failedTransferDialogBox.getUserInput();
					}
					catch (PrinterDisconnectedException ex) {
						// this should never happen
						LOGGER.error("Print job transfer failed to printer " + printerName);
					}
					failedTransferDialogBox = null;
					LOGGER.error("Print job transfer failed to printer " + printerName);
				}
			});
		}
	}

	@Override
	public void removePrintTransferFailedNotification() {
		if (failedTransferDialogBox != null) {
			taskExecutor.runOnGUIThread(() -> {
				failedTransferDialogBox.close();
				failedTransferDialogBox = null;
			});
		}
	}

	@Override
	public void showPrintTransferInitiatedNotification() {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.printTransferInitiated"));
	}

	@Override
	public void showReprintStartedNotification() {
		showInformationNotification(i18n.t("notification.PrintQueueTitle"), i18n.t(
				"notification.reprintInitiated"));
	}

	@Override
	public void showFirmwareUpgradeStatusNotification(FirmwareLoadResult result) {
		if (result != null) {
			switch (result.getStatus()) {
				case FirmwareLoadResult.SDCARD_ERROR:
					showErrorNotification(i18n.t("dialogs.firmwareUpdateFailedTitle"),
							i18n.t("dialogs.sdCardError"));
					break;
				case FirmwareLoadResult.FILE_ERROR:
					showErrorNotification(i18n.t("dialogs.firmwareUpdateFailedTitle"),
							i18n.t("dialogs.firmwareFileError"));
					break;
				case FirmwareLoadResult.OTHER_ERROR:
					showErrorNotification(i18n.t("dialogs.firmwareUpdateFailedTitle"),
							i18n.t("dialogs.firmwareUpdateFailedMessage"));
					break;
				case FirmwareLoadResult.SUCCESS:
					showInformationNotification(i18n.t("dialogs.firmwareUpdateSuccessTitle"),
							i18n.t("dialogs.firmwareUpdateSuccessMessage"));
					break;
			}
		}
		else {
			showErrorNotification(i18n.t("dialogs.firmwareUpdateFailedTitle"),
					i18n.t("dialogs.firmwareUpdateFailedMessage"));
		}
	}

	/**
	 * Returns 0 for no upgrade and 1 for upgrade
	 *
	 * @param requiredFirmwareVersion
	 * @param actualFirmwareVersion
	 * @return True if the user has agreed to update, otherwise false
	 */
	@Override
	public boolean askUserToUpdateFirmware(Printer printerToUpdate) {
		Callable<Boolean> askUserToUpgradeDialog = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				String printerName = printerToUpdate.getPrinterIdentity().printerFriendlyNameProperty().get();
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.firmwareUpdateTitle") + printerName);
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.firmwareUpdateError"));
				ChoiceLinkButton updateFirmwareChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.firmwareUpdateOKTitle"),
						i18n.t("dialogs.firmwareUpdateOKMessage"));
				//ChoiceLinkButton dontUpdateFirmwareChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.firmwareUpdateNotOKTitle"),
						i18n.t("dialogs.firmwareUpdateNotOKMessage"));

				Optional<ChoiceLinkButton> firmwareUpgradeResponse = choiceLinkDialogBox.getUserInput();

				boolean updateConfirmed = false;

				if (firmwareUpgradeResponse.isPresent()) {
					updateConfirmed = firmwareUpgradeResponse.get() == updateFirmwareChoice;
				}

				return updateConfirmed;
			}
		};
		FutureTask<Boolean> askUserToUpgradeTask = new FutureTask<>(askUserToUpgradeDialog);
		taskExecutor.runOnGUIThread(askUserToUpgradeTask);
		try {
			return askUserToUpgradeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during firmware upgrade query");
			return false;
		}
	}

	@Override
	public boolean showDowngradeFirmwareDialog(Printer printerToUpdate) {
		Callable<Boolean> showDowngradeFirmwareDialog = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				String printerName = printerToUpdate.getPrinterIdentity().printerFriendlyNameProperty().get();
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.firmwareDowngradeTitle") + printerName);
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.firmwareDowngradeMessage"));
				ChoiceLinkButton downgradeFirmwareChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.firmwareDowngradeOKTitle"),
						i18n.t("dialogs.firmwareDowngradeOKMessage"));
				//ChoiceLinkButton dontDownGradeFirmwareChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.firmwareDowngradeNotOKTitle"),
						i18n.t("dialogs.firmwareDowngradeNotOKMessage"));

				Optional<ChoiceLinkButton> firmwareDowngradeResponse = choiceLinkDialogBox.getUserInput();

				boolean downgradeConfirmed = false;

				if (firmwareDowngradeResponse.isPresent()) {
					downgradeConfirmed = firmwareDowngradeResponse.get() == downgradeFirmwareChoice;
				}

				return downgradeConfirmed;
			}
		};
		FutureTask<Boolean> askUserToDowngradeTask = new FutureTask<>(showDowngradeFirmwareDialog);
		taskExecutor.runOnGUIThread(askUserToDowngradeTask);
		try {
			return askUserToDowngradeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during firmware downgrade query");
			return false;
		}
	}

	/**
	 * @param printerToUse
	 * @param printerID
	 * @return 0 for failure, 1 for reset, 2 for temporary set.
	 */
	@Override
	public RoboxResetIDResult askUserToResetPrinterID(Printer printerToUse, PrinterIDResponse printerID) {

		//TODO: Can this be defined using a lambda?
		Callable<RoboxResetIDResult> resetPrinterIDCallable = new Callable<>() {
			@Override
			public RoboxResetIDResult call() throws Exception {
				Stage resetPrinterIDStage = null;
				ResetPrinterIDController controller = null;
				try {
					URL fxmlFileName = getClass().getResource(ApplicationConfiguration.fxmlPopupResourcePath + "resetPrinterIDDialog.fxml");
					FXMLLoader resetDialogLoader = fxmlLoaderFactory.create(fxmlFileName);

					VBox resetVBox = (VBox) resetDialogLoader.load();
					controller = (ResetPrinterIDController) resetVBox.getUserData();
					resetPrinterIDStage = new Stage(StageStyle.UNDECORATED);
					resetPrinterIDStage.initModality(Modality.APPLICATION_MODAL);
					resetPrinterIDStage.setScene(new Scene(resetVBox));
					resetPrinterIDStage.initOwner(stageManager.getMainStage());
					controller.setPrinterToUse(printerToUse);
					controller.updateFieldsFromPrinterID(printerID);
					resetPrinterIDStage.showAndWait();
					return controller.getResetResult();
				}
				catch (Exception ex) {
					LOGGER.error("Couldn't load reset printer Id dialog", ex);
					return RoboxResetIDResult.RESET_FAILED;
				}
			}
		};

		FutureTask<RoboxResetIDResult> resetPrinterIDTask = new FutureTask<>(resetPrinterIDCallable);
		taskExecutor.runOnGUIThread(resetPrinterIDTask);
		try {
			return resetPrinterIDTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during printer id reset");
			return RoboxResetIDResult.RESET_FAILED;
		}
	}

	@Override
	public void configureFirmwareProgressDialog(FirmwareLoadService firmwareLoadService) {
		taskExecutor.runOnGUIThread(() -> {
			firmwareUpdateProgress = new ProgressDialog(firmwareLoadService);
		});
	}

	@Override
	public void showNoSDCardDialog() {
		if (!sdDialogOnDisplay) {
			sdDialogOnDisplay = true;
			taskExecutor.runOnGUIThread(() -> {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.noSDCardTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.noSDCardMessage"));
				//ChoiceLinkButton openTheLidChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("misc.OK"));

				try {
					choiceLinkDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("this should never happen");
				}

				sdDialogOnDisplay = false;
			});
		}
	}

	@Override
	public void showNoPrinterIDDialog(Printer printer) {
		if (!printerIDDialog.isShowing()) {
			printerIDDialog.setPrinterToUse(printer);

			taskExecutor.runOnGUIThread(() -> {
				if (printerIDDialog == null) {
					printerIDDialog = new PrinterIDDialog();
				}

				printerIDDialog.show();
			});
		}
	}

	@Override
	public boolean showOpenDoorDialog() {
		Callable<Boolean> askUserWhetherToOpenDoorDialog = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.openLidPrinterHotTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.openLidPrinterHotInfo"));
				ChoiceLinkButton openTheLidChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.openLidPrinterHotGoAheadHeading"),
						i18n.t("dialogs.openLidPrinterHotGoAheadInfo"));
				//ChoiceLinkButton dontOpenTheLidChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.openLidPrinterHotDontOpenHeading"));

				Optional<ChoiceLinkButton> doorOpenResponse = choiceLinkDialogBox.getUserInput();

				boolean openTheLid = false;

				if (doorOpenResponse.isPresent()) {
					openTheLid = doorOpenResponse.get() == openTheLidChoice;
				}

				return openTheLid;
			}
		};

		FutureTask<Boolean> askUserWhetherToOpenDoorTask = new FutureTask<>(
				askUserWhetherToOpenDoorDialog);
		taskExecutor.runOnGUIThread(askUserWhetherToOpenDoorTask);
		try {
			return askUserWhetherToOpenDoorTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during door open query");
			return false;
		}
	}

	/**
	 *
	 * @param modelFilename
	 * @return True if the user has opted to shrink the model
	 */
	@Override
	public boolean showModelTooBigDialog(String modelFilename) {
		Callable<Boolean> askUserWhetherToLoadModel = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.ModelTooLargeTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.ModelTooLargeDescription"));
				ChoiceLinkButton shrinkChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.ShrinkModelToFit"));
				//ChoiceLinkButton dontShrinkChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.dontShrink"));

				Optional<ChoiceLinkButton> shrinkResponse = choiceLinkDialogBox.getUserInput();

				boolean shrinkModel = false;

				if (shrinkResponse.isPresent()) {
					shrinkModel = shrinkResponse.get() == shrinkChoice;
				}

				return shrinkModel;
			}
		};

		FutureTask<Boolean> askUserWhetherToLoadModelTask = new FutureTask<>(
				askUserWhetherToLoadModel);
		taskExecutor.runOnGUIThread(askUserWhetherToLoadModelTask);
		try {
			return askUserWhetherToLoadModelTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during model too large query");
			return false;
		}
	}

	/**
	 *
	 * @param applicationName
	 * @return True if the user has elected to upgrade
	 */
	@Override
	public boolean showApplicationUpgradeDialog(String applicationName) {
		Callable<Boolean> askUserWhetherToUpgrade = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.updateApplicationTitle"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.updateApplicationMessagePart1")
						+ " "
						+ applicationName
						+ " "
						+ i18n.t("dialogs.updateApplicationMessagePart2"));
				ChoiceLinkButton upgradeChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("misc.Yes"),
						i18n.t("dialogs.updateExplanation"));
				//ChoiceLinkButton dontUpgradeChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("misc.No"),
						i18n.t("dialogs.updateContinueWithCurrent"));

				Optional<ChoiceLinkButton> upgradeResponse = choiceLinkDialogBox.getUserInput();

				boolean upgradeApplication = false;

				if (upgradeResponse.isPresent()) {
					upgradeApplication = upgradeResponse.get() == upgradeChoice;
				}

				return upgradeApplication;
			}
		};

		FutureTask<Boolean> askWhetherToUpgradeTask = new FutureTask<>(askUserWhetherToUpgrade);
		taskExecutor.runOnGUIThread(askWhetherToUpgradeTask);
		try {
			return askWhetherToUpgradeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during model too large query");
			return false;
		}
	}

	@Override
	public boolean showAreYouSureYouWantToDowngradeDialog() {
		Callable<Boolean> askUserWhetherToDowngrade = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.downgradeWarning"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.downgradeMessage"));
				ChoiceLinkButton proceedChoice = choiceLinkDialogBox.addChoiceLink(i18n.t("dialogs.downgradeContinue"));
				//ChoiceLinkButton dontDowngradeChoice =
				choiceLinkDialogBox.addChoiceLink(i18n.t("dialogs.Cancel"));

				Optional<ChoiceLinkButton> downgradeResponse = choiceLinkDialogBox.getUserInput();

				boolean downgradeApplication = false;

				if (downgradeResponse.isPresent()) {
					downgradeApplication = downgradeResponse.get() == proceedChoice;
				}

				return downgradeApplication;
			}
		};

		FutureTask<Boolean> askWhetherToDowngradeTask = new FutureTask<>(askUserWhetherToDowngrade);
		taskExecutor.runOnGUIThread(askWhetherToDowngradeTask);
		try {
			return askWhetherToDowngradeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error when asking user if they wish to contiunue with downgrade of root");
			return false;
		}
	}

	@Override
	public PurgeResponse showPurgeDialog() {
		return showPurgeDialog(true);
	}

	/**
	 * @return True if the user has elected to purge
	 */
	@Override
	public PurgeResponse showPurgeDialog(boolean allowAutoPrint) {
		Callable<PurgeResponse> askUserWhetherToPurge = new Callable<>() {
			@Override
			public PurgeResponse call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.purgeRequiredTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.purgeRequiredInstruction"));

				ChoiceLinkButton purge = null;
				if (allowAutoPrint) {
					purge = choiceLinkDialogBox.addChoiceLink(
							i18n.t("dialogs.goForPurgeTitle"),
							i18n.t("dialogs.goForPurgeInstruction"));
				}
				ChoiceLinkButton dontPurge = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.dontGoForPurgeTitle"),
						i18n.t("dialogs.dontGoForPurgeInstruction"));
				ChoiceLinkButton dontPrint = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.dontPrintTitle"),
						i18n.t("dialogs.dontPrintInstruction"));

				Optional<ChoiceLinkButton> purgeResponse = choiceLinkDialogBox.getUserInput();

				PurgeResponse response = null;

				if (purgeResponse.get() == purge) {
					response = PurgeResponse.PRINT_WITH_PURGE;
				}
				else if (purgeResponse.get() == dontPurge) {
					response = PurgeResponse.PRINT_WITHOUT_PURGE;
				}
				else if (purgeResponse.get() == dontPrint) {
					response = PurgeResponse.DONT_PRINT;
				}

				return response;
			}
		};

		FutureTask<PurgeResponse> askWhetherToPurgeTask = new FutureTask<>(askUserWhetherToPurge);
		taskExecutor.runOnGUIThread(askWhetherToPurgeTask);
		try {
			return askWhetherToPurgeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during purge query");
			return null;
		}
	}

	/**
	 * @return True if the user has elected to shutdown
	 */
	@Override
	public boolean showJobsTransferringShutdownDialog() {
		Callable<Boolean> askUserWhetherToShutdown = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t(
						"dialogs.printJobsAreStillTransferringTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.printJobsAreStillTransferringMessage"));
				ChoiceLinkButton shutdown = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.shutDownAndTerminateTitle"),
						i18n.t("dialogs.shutDownAndTerminateMessage"));
				//ChoiceLinkButton dontShutdown =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.dontShutDownTitle"),
						i18n.t("dialogs.dontShutDownMessage"));

				Optional<ChoiceLinkButton> shutdownResponse = choiceLinkDialogBox.getUserInput();

				return shutdownResponse.get() == shutdown;
			}
		};

		FutureTask<Boolean> askWhetherToShutdownTask = new FutureTask<>(askUserWhetherToShutdown);
		taskExecutor.runOnGUIThread(askWhetherToShutdownTask);
		try {
			return askWhetherToShutdownTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during shutdown whilst transferring query");
			return false;
		}
	}

	@Override
	public void showProgramInvalidHeadDialog(TaskResponder<HeadFile> responder) {
		if (programInvalidHeadStage == null) {
			taskExecutor.runOnGUIThread(() -> {
				try {
					URL fxmlFileName = getClass().getResource(ApplicationConfiguration.fxmlPopupResourcePath + "resetHeadDialog.fxml");
					FXMLLoader resetDialogLoader = fxmlLoaderFactory.create(fxmlFileName);

					VBox resetDialog = (VBox) resetDialogLoader.load();
					programInvalidHeadStage = new Stage(StageStyle.UNDECORATED);
					programInvalidHeadStage.initModality(Modality.APPLICATION_MODAL);
					programInvalidHeadStage.setScene(new Scene(resetDialog));
					programInvalidHeadStage.initOwner(stageManager.getMainStage());
					programInvalidHeadStage.show();
				}
				catch (Exception ex) {
					LOGGER.error("Couldn't load head reset dialog", ex);
				}
			});
		}
	}

	@Override
	public void hideProgramInvalidHeadDialog() {
		if (programInvalidHeadStage != null) {
			programInvalidHeadStage.close();
			programInvalidHeadStage = null;
		}
	}

	@Override
	public void showHeadNotRecognisedDialog(String printerName) {
		if (!headNotRecognisedDialogOnDisplay) {
			headNotRecognisedDialogOnDisplay = true;
			taskExecutor.runOnGUIThread(() -> {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t(
						"dialogs.headNotRecognisedTitle"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.headNotRecognisedMessage1")
						+ " "
						+ printerName
						+ " "
						+ i18n.t("dialogs.headNotRecognisedMessage2")
						+ " "
						+ namePreference.getValue());

				//ChoiceLinkButton openTheLidChoice =
				choiceLinkDialogBox.addChoiceLink(i18n.t("misc.OK"));

				try {
					choiceLinkDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("printer disconnected");
				}

				headNotRecognisedDialogOnDisplay = false;
			});
		}
	}

	@Override
	public Optional<PrinterErrorChoice> showPrinterErrorDialog(String title, String message,
			boolean showContinueOption, boolean showAbortOption, boolean showRetryOption,
			boolean showOKOption) {
		if (!showContinueOption && !showAbortOption && !showRetryOption && !showOKOption) {
			throw new RuntimeException("Must allow one option to be shown");
		}
		Callable<Optional<PrinterErrorChoice>> askUserToRespondToPrinterError = new Callable<>() {
			@Override
			public Optional<PrinterErrorChoice> call() throws Exception {
				ChoiceLinkDialogBox printerErrorDialogBox = new ChoiceLinkDialogBox(true);
				printerErrorDialogBox.setTitle(title);
				printerErrorDialogBox.setMessage(message);

				ChoiceLinkButton continueChoice = new ChoiceLinkButton();
				continueChoice.setTitle(i18n.t("dialogs.error.continue"));
				continueChoice.setMessage(i18n.t("dialogs.error.clearAndContinue"));

				ChoiceLinkButton abortChoice = new ChoiceLinkButton();
				abortChoice.setTitle(i18n.t("dialogs.error.abort"));
				abortChoice.setMessage(i18n.t("dialogs.error.abortProcess"));

				ChoiceLinkButton retryChoice = new ChoiceLinkButton();
				retryChoice.setTitle(i18n.t("dialogs.error.retry"));
				retryChoice.setMessage(i18n.t("dialogs.error.retryProcess"));

				ChoiceLinkButton okChoice = new ChoiceLinkButton();
				okChoice.setTitle(i18n.t("error.handler.OK.title"));

				if (showContinueOption) {
					printerErrorDialogBox.addChoiceLink(continueChoice);
				}

				if (showAbortOption) {
					printerErrorDialogBox.addChoiceLink(abortChoice);
				}

				if (showRetryOption) {
					printerErrorDialogBox.addChoiceLink(retryChoice);
				}

				if (showOKOption) {
					printerErrorDialogBox.addChoiceLink(okChoice);
				}

				Optional<ChoiceLinkButton> response = printerErrorDialogBox.getUserInput();

				Optional<PrinterErrorChoice> userResponse = Optional.empty();

				if (response.isPresent()) {
					if (response.get() == continueChoice) {
						userResponse = Optional.of(PrinterErrorChoice.CONTINUE);
					}
					else if (response.get() == abortChoice) {
						userResponse = Optional.of(PrinterErrorChoice.ABORT);
					}
					else if (response.get() == okChoice) {
						userResponse = Optional.of(PrinterErrorChoice.OK);
					}
					else if (response.get() == retryChoice) {
						userResponse = Optional.of(PrinterErrorChoice.RETRY);
					}
				}

				return userResponse;
			}
		};

		FutureTask<Optional<PrinterErrorChoice>> askContinueAbortTask = new FutureTask<>(
				askUserToRespondToPrinterError);
		taskExecutor.runOnGUIThread(askContinueAbortTask);
		try {
			return askContinueAbortTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during printer error query");
			return Optional.empty();
		}

	}

	@Override
	public void showReelUpdatedNotification() {
		taskExecutor.runOnGUIThread(() -> {
			showInformationNotification(i18n.t("notification.reelDataUpdatedTitle"),
					i18n.t("notification.noActionRequired"));
		});
	}

	@Override
	public void showReelNotRecognisedDialog(String printerName) {
		if (!reelNotRecognisedDialogOnDisplay) {
			reelNotRecognisedDialogOnDisplay = true;
			taskExecutor.runOnGUIThread(() -> {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(true);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.reelNotRecognisedTitle"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.reelNotRecognisedMessage1")
						+ " "
						+ printerName
						+ " "
						+ i18n.t("dialogs.reelNotRecognisedMessage2")
						+ " "
						+ namePreference.getValue());

				choiceLinkDialogBox.addChoiceLink(i18n.t("misc.OK"));

				try {
					choiceLinkDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("printer disconnected");
				}

				reelNotRecognisedDialogOnDisplay = false;
			});
		}
	}

	@Override
	public void askUserToClearBed() {
		if (!clearBedDialogOnDisplay) {
			clearBedDialogOnDisplay = true;
			taskExecutor.runOnGUIThread(() -> {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.clearBedTitle"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.clearBedInstruction"));

				choiceLinkDialogBox.addChoiceLink(i18n.t("misc.OK"));

				try {
					choiceLinkDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("this should never happen");
				}

				clearBedDialogOnDisplay = false;
			});
		}
	}

	/**
	 * Returns 0 for no downgrade and 1 for downgrade
	 *
	 * @return True if the user has decided to switch to Advanced Mode, otherwise false
	 */
	@Override
	public boolean confirmAdvancedMode() {
		Callable<Boolean> confirmAdvancedModeDialog = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.goToAdvancedModeTitle"));
				choiceLinkDialogBox.setMessage(i18n.t("dialogs.goToAdvancedModeMessage"));
				ChoiceLinkButton goToAdvancedModeChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.goToAdvancedModeYesTitle"),
						i18n.t("dialogs.goToAdvancedModeYesMessage"));
				//ChoiceLinkButton dontGoToAdvancedModeChoice =
				choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.goToAdvancedModeNoTitle"),
						i18n.t("dialogs.goToAdvancedModeNoMessage"));

				Optional<ChoiceLinkButton> goToAdvancedModeResponse = choiceLinkDialogBox.getUserInput();

				boolean goToAdvancedMode = false;

				if (goToAdvancedModeResponse.isPresent()) {
					goToAdvancedMode = goToAdvancedModeResponse.get() == goToAdvancedModeChoice;
				}

				return goToAdvancedMode;
			}
		};

		FutureTask<Boolean> confirmAdvancedModeTask = new FutureTask<>(confirmAdvancedModeDialog);
		taskExecutor.runOnGUIThread(confirmAdvancedModeTask);
		try {
			return confirmAdvancedModeTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during advanced mode query: " + ex);
			return false;
		}
	}

	/**
	 *
	 * @param printerName
	 */
	@Override
	public void showKeepPushingFilamentNotification() {
		if (keepPushingFilamentDialogBox == null) {
			Platform.runLater(() -> {
				if (keepPushingFilamentDialogBox == null) {
					keepPushingFilamentDialogBox = new ChoiceLinkDialogBox(true);
					keepPushingFilamentDialogBox.setTitle(i18n.t(
							"notification.keepPushingFilamentTitle"));
					keepPushingFilamentDialogBox.setMessage(i18n.t(
							"notification.keepPushingFilament"));
					try {
						keepPushingFilamentDialogBox.getUserInput();
					}
					catch (PrinterDisconnectedException ex) {
						LOGGER.error("printer disconnected");
					}
				}
			});
		}
	}

	@Override
	public void hideKeepPushingFilamentNotification() {
		if (keepPushingFilamentDialogBox != null) {
			Platform.runLater(() -> {
				if (keepPushingFilamentDialogBox != null) {
					keepPushingFilamentDialogBox.close();
					keepPushingFilamentDialogBox = null;
				}
			});
		}
	}

	/**
	 *
	 * @param printer
	 * @param nozzleNumber
	 * @param error
	 */
	@Override
	public void showEjectFailedDialog(Printer printer, int nozzleNumber, FirmwareError error) {
		if (failedEjectDialogBox == null) {
			taskExecutor.runOnGUIThread(() -> {
				failedEjectDialogBox = new ChoiceLinkDialogBox(true);
				failedEjectDialogBox.setTitle(i18n.t("error.ERROR_UNLOAD"));
				failedEjectDialogBox.setMessage(i18n.t(
						"error.ERROR_UNLOAD.message"));

				ChoiceLinkButton ejectStuckMaterial = failedEjectDialogBox.addChoiceLink(
						i18n.t("error.ERROR_UNLOAD.action.title"));
				failedEjectDialogBox.addChoiceLink(
						i18n.t("error.ERROR_UNLOAD.noaction.title"));

				boolean runEjectStuckMaterial = false;

				Optional<ChoiceLinkButton> choice;
				try {
					choice = failedEjectDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					return;
				}
				if (choice.isPresent()) {
					if (choice.get() == ejectStuckMaterial) {
						runEjectStuckMaterial = true;
					}
				}
				failedEjectDialogBox = null;

				if (runEjectStuckMaterial) {
					LOGGER.error("Eject failed - user chose to eject stuck material");
					try {
						printer.ejectStuckMaterial(nozzleNumber, false, null, safetyFeaturesPreference.getValue());
					}
					catch (PrinterException ex) {
						LOGGER.error("Error when automatically invoking eject stuck material");
					}
				}
				else {
					LOGGER.error("Eject failed - user chose not to run eject stuck material");
				}

				printer.clearError(error);
			});
		}
	}

	@Override
	public void showFilamentStuckMessage() {
		if (filamentStuckDialogBox == null) {
			taskExecutor.runOnGUIThread(() -> {
				filamentStuckDialogBox = new ChoiceLinkDialogBox(true);
				filamentStuckDialogBox.setTitle(i18n.t("dialogs.filamentStuck.title"));
				filamentStuckDialogBox.setMessage(
						i18n.t("dialogs.filamentStuck.message"));

				//ChoiceLinkButton ok =
				filamentStuckDialogBox.addChoiceLink(i18n.t("misc.OK"));

				try {
					//Optional<ChoiceLinkButton> choice =
					filamentStuckDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					return;
				}

				filamentStuckDialogBox.close();
				filamentStuckDialogBox = null;
			});
		}
	}

	@Override
	public void showLoadFilamentNowMessage() {
		if (loadFilamentNowDialogBox == null) {
			taskExecutor.runOnGUIThread(() -> {
				loadFilamentNowDialogBox = new ChoiceLinkDialogBox(true);
				loadFilamentNowDialogBox.setTitle(i18n.t("dialogs.loadFilamentNow.title"));
				loadFilamentNowDialogBox.setMessage(
						i18n.t("dialogs.loadFilamentNow.message"));

				//ChoiceLinkButton ok =
				loadFilamentNowDialogBox.addChoiceLink(i18n.t("misc.OK"));

				try {
					//Optional<ChoiceLinkButton> choice =
					loadFilamentNowDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("printer disconnected");
				}

				loadFilamentNowDialogBox.close();
				loadFilamentNowDialogBox = null;
			});
		}
	}

	@Override
	public void showFilamentMotionCheckBanner() {
		if (filamentMotionCheckDialogBox == null) {
			taskExecutor.runOnGUIThread(() -> {
				filamentMotionCheckDialogBox = new ChoiceLinkDialogBox(true);
				filamentMotionCheckDialogBox.setTitle(i18n.t("notification.printManagement.title"));
				filamentMotionCheckDialogBox.setMessage(
						i18n.t("notification.filamentMotionCheck"));

				try {
					filamentMotionCheckDialogBox.getUserInput();
				}
				catch (PrinterDisconnectedException ex) {
					LOGGER.error("printer disconnected");
				}
			});
		}
	}

	@Override
	public void hideFilamentMotionCheckBanner() {
		if (filamentMotionCheckDialogBox != null) {
			taskExecutor.runOnGUIThread(() -> {
				if (filamentMotionCheckDialogBox != null) {
					filamentMotionCheckDialogBox.close();
					filamentMotionCheckDialogBox = null;
				}
			});
		}
	}

	@Override
	public boolean showModelIsInvalidDialog(Set<String> modelNames) {
		Callable<Boolean> askUserWhetherToLoadModel = new Callable<>() {
			@Override
			public Boolean call() throws Exception {
				ChoiceLinkDialogBox choiceLinkDialogBox = new ChoiceLinkDialogBox(false);
				choiceLinkDialogBox.setTitle(i18n.t("dialogs.modelInvalidTitle"));
				choiceLinkDialogBox.setMessage(i18n.t(
						"dialogs.modelInvalidDescription"));

				ListView<String> problemModels = new ListView<>();
				problemModels.getItems().addAll(modelNames);
				choiceLinkDialogBox.addControl(problemModels);

				problemModels.setMaxHeight(200);

				ChoiceLinkButton loadChoice = choiceLinkDialogBox.addChoiceLink(
						i18n.t("dialogs.loadModel"));
				choiceLinkDialogBox.addChoiceLink(i18n.t("dialogs.dontLoadModel"));

				Optional<ChoiceLinkButton> loadResponse = choiceLinkDialogBox.getUserInput();

				boolean loadModel = false;

				if (loadResponse.isPresent()) {
					loadModel = loadResponse.get() == loadChoice;
				}

				return loadModel;
			}
		};

		FutureTask<Boolean> askInvalidModelTask = new FutureTask<>(askUserWhetherToLoadModel);
		taskExecutor.runOnGUIThread(askInvalidModelTask);
		try {
			return askInvalidModelTask.get();
		}
		catch (InterruptedException | ExecutionException ex) {
			LOGGER.error("Error during model invalid query");
			return false;
		}
	}

	@Override
	public void clearAllDialogsOnDisconnect() {
		if (errorChoiceBox != null) {
			taskExecutor.runOnGUIThread(() -> {
				errorChoiceBox.closeDueToPrinterDisconnect();
			});
		}
	}

	@Override
	public void showDismissableNotification(String message, String buttonText, NotificationType notificationType) {
		taskExecutor.runOnGUIThread(() -> {
			notificationDisplay.displayDismissableNotification(message, buttonText, notificationType);
		});
	}
}
