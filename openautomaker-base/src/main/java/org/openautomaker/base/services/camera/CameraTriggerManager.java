package org.openautomaker.base.services.camera;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import org.openautomaker.base.postprocessor.nouveau.nodes.CommentNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.LayerChangeDirectiveNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.MCodeNode;
import org.openautomaker.base.postprocessor.nouveau.nodes.TravelNode;
import org.openautomaker.base.printerControl.PrintJob;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.utils.ScriptUtils;
import org.openautomaker.environment.preference.application.HomePathPreference;

import com.google.inject.assistedinject.Assisted;

import celtech.roboxbase.comms.remote.PauseStatus;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.scene.paint.Color;

/**
 *
 * @author Ian
 */
public class CameraTriggerManager {
	private static final Logger LOGGER = LogManager.getLogger();

	private static final String APP_SHORT_NAME_ROOT = "Root";
	private static final int AMBIENT_LIGHT_OFF_DELAY = 2000;
	private static final int SCRIPT_TIMEOUT = 15;

	private Printer associatedPrinter = null;
	private static final int MOVE_FEED_RATE_MM_PER_MIN = 12000;
	private CameraTriggerData triggerData;

	private final ChangeListener<? super PauseStatus> pauseStatusListener = (observable, oldPauseStatus, newPauseStatus) -> {
		if (newPauseStatus == PauseStatus.SELFIE_PAUSE) {
			try {
				if (triggerUSBCamera()) {
					associatedPrinter.resume();
				}
			}
			catch (PrinterException ex) {
				LOGGER.error("Exception whilst resuming", ex);
			}
		}
	};

	private final HomePathPreference homePathPreference;

	@Inject
	protected CameraTriggerManager(
			HomePathPreference homePathPreference,
			@Nullable @Assisted Printer printer) {

		this.homePathPreference = homePathPreference;

		associatedPrinter = printer;

		if (associatedPrinter != null) {
			// In case the printer has been seen before, we only want to have one pauseStatusListener
			associatedPrinter.pauseStatusProperty().removeListener(pauseStatusListener);
			associatedPrinter.pauseStatusProperty().addListener(pauseStatusListener);
		}
	}

	public void appendLayerEndTriggerCode(LayerChangeDirectiveNode layerChangeNode) {
		CommentNode beginComment = new CommentNode("Start of camera trigger");
		CommentNode endComment = new CommentNode("End of camera trigger");

		TravelNode moveBedForward = new TravelNode();

		boolean outputMoveCommand = triggerData.isMoveBeforeCapture();
		boolean turnOffHeadLights = triggerData.isTurnOffHeadLights();

		if (outputMoveCommand) {
			int xMoveInt = triggerData.getyMoveBeforeCapture();
			moveBedForward.getMovement().setX(xMoveInt);

			int yMoveInt = triggerData.getyMoveBeforeCapture();
			moveBedForward.getMovement().setY(yMoveInt);

			moveBedForward.getFeedrate().setFeedRate_mmPerMin(MOVE_FEED_RATE_MM_PER_MIN);
		}

		MCodeNode selfiePauseNode = new MCodeNode(1);
		selfiePauseNode.setCPresent(true);
		MCodeNode turnHeadLightsOff = new MCodeNode(128);
		MCodeNode turnHeadLightsOn = new MCodeNode(129);

		TravelNode returnToPreviousPosition = new TravelNode();
		returnToPreviousPosition.getMovement().setX(layerChangeNode.getMovement().getX());
		returnToPreviousPosition.getMovement().setY(layerChangeNode.getMovement().getY());
		returnToPreviousPosition.getFeedrate().setFeedRate_mmPerMin(MOVE_FEED_RATE_MM_PER_MIN);

		layerChangeNode.addSiblingAfter(endComment);

		if (turnOffHeadLights)
			layerChangeNode.addSiblingAfter(turnHeadLightsOn);

		layerChangeNode.addSiblingAfter(returnToPreviousPosition);
		layerChangeNode.addSiblingAfter(selfiePauseNode);

		if (outputMoveCommand)
			layerChangeNode.addSiblingAfter(moveBedForward);

		if (turnOffHeadLights)
			layerChangeNode.addSiblingAfter(turnHeadLightsOff);

		layerChangeNode.addSiblingAfter(beginComment);
	}

	public void setTriggerData(CameraTriggerData triggerData) {
		this.triggerData = triggerData;
	}

	private boolean triggerUSBCamera() {
		boolean resumePrinter;

		// If we are talking to a remote printer, return false, root will handle the resume.
		if (associatedPrinter.getCommandInterface() instanceof RoboxRemoteCommandInterface) {
			resumePrinter = false;
		}
		else {
			LOGGER.info("Triggering USB camera");
			try {
				// TODO: Find a better way to do this other than on short name.  Needs to be an environment enumeration perhaps.  Why does it even need it.  Only part of root?
				String applicationShortName = "OpenAutomaker"; //new ShortNamePreference().getValue();

				if (applicationShortName.equals(APP_SHORT_NAME_ROOT)) {
					// If we are on a root device we can attempt to take a snapshot from the camera before resuming the print.
					PrintJob job = associatedPrinter.getPrintEngine().printJobProperty().get();
					if (job != null) {
						String jobID = job.getJobUUID();
						CameraSettings cameraData = job.getCameraData();
						if (cameraData != null) {
							String printerName = associatedPrinter.getPrinterIdentity().printerFriendlyNameProperty().get();
							List<String> parameters = cameraData.encodeSettingsForRootScript(printerName, jobID);
							// Synchronized access with CameraAPI::takeSnapshot, so both are not trying to access the
							// camera at the same time. Synchronize on the CameraSettings class object as it
							// is easily accessable to both methods.
							if (cameraData.profile.ambientLightOff) {
								try {
									associatedPrinter.setAmbientLEDColour(Color.BLACK);
									// Apparently have to wait a couple of seconds for the light to turn off.
									Thread.sleep(AMBIENT_LIGHT_OFF_DELAY);
								}
								catch (PrinterException ex) {
									LOGGER.error("Failed to switch off ambient light", ex);
								}
								catch (InterruptedException ex) {
								}
							}
							synchronized (CameraSettings.class) {
								//TODO: Where does takePhoto.sh come from?  Haven't been able to find it.
								ScriptUtils.runScript(homePathPreference.getAppValue().resolve("takePhoto.sh").toString(),
										SCRIPT_TIMEOUT,
										parameters.toArray(new String[0]));
							}
							if (!cameraData.profile.ambientLightOff) {
								try {
									associatedPrinter.setAmbientLEDColour(associatedPrinter.getPrinterIdentity()
											.printerColourProperty()
											.get());
								}
								catch (PrinterException ex) {
									LOGGER.error("Failed to switch on ambient light", ex);
								}
							}
						}
					}
				}

				resumePrinter = true;
			}
			catch (Exception ex) {
				LOGGER.error("Exception during selfie", ex);
			}
			finally {
				resumePrinter = true;
			}
		}

		return resumePrinter;
	}
}
