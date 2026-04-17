package org.openautomaker.ui.component.printer_status_page;

import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.base.comms.print_server.PrintServerConnection.CameraTag;
import org.openautomaker.base.configuration.CoreMemory;
import org.openautomaker.base.configuration.datafileaccessors.CameraProfileContainer;
import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;
import org.openautomaker.base.device.CameraManager;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import org.openautomaker.ui.component.snapshot.SnapshotController;
import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * FXML Controller class
 *
 * @author Ian
 */
public class SnapshotPanelController extends SnapshotController {
	private Printer connectedPrinter = null;

	private final ChangeListener<Boolean> cameraDetectedChangeListener = (ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
		controlSnapshotTask();
	};

	private final ChangeListener<ApplicationMode> applicationModeChangeListener = (ObservableValue<? extends ApplicationMode> observable, ApplicationMode oldValue, ApplicationMode newValue) -> {
		controlSnapshotTask();
	};

	private final ChangeListener<CameraTag> cameraTagChangeListener = (ObservableValue<? extends CameraTag> observable, CameraTag oldValue, CameraTag newValue) -> {
		selectCameraAndProfile(newValue.getCameraProfileName(), newValue.getCameraName());
	};

	private final SelectedPrinter selectedPrinter;
	private final ApplicationStatus applicationStatus;
	private final CoreMemory coreMemory;

	@Inject
	protected SnapshotPanelController(
			CoreMemory coreMemory,
			CameraManager cameraManager,
			TaskExecutor taskExecutor,
			ApplicationStatus applicationStatus,
			SelectedPrinter selectedPrinter,
			CameraProfileContainer cameraProfileContainer) {

		super(cameraManager, taskExecutor, cameraProfileContainer);

		this.coreMemory = coreMemory;
		this.selectedPrinter = selectedPrinter;
		this.applicationStatus = applicationStatus;
	}

	/**
	 * Initializes the controller class.
	 *
	 * @param url
	 * @param rb
	 */
	@Override
	public void initialize() {
		super.initialize();
		viewWidthFixed = true;
		selectedPrinter.addListener((ObservableValue<? extends Printer> observable, Printer oldValue, Printer newValue) -> {
			if (connectedPrinter != null)
				unbindFromPrinter(connectedPrinter);

			if (newValue != null)
				bindToPrinter(newValue);
		});

		applicationStatus.modeProperty().addListener(applicationModeChangeListener);
	}

	private void unbindFromPrinter(Printer printer) {
		if (connectedPrinter != null) {
			connectedPrinter = null;
		}

		if (connectedServer != null) {
			connectedServer.cameraDetectedProperty().removeListener(cameraDetectedChangeListener);
			connectedServer.cameraTagProperty().removeListener(cameraTagChangeListener);
			connectedServer = null;
		}
		controlSnapshotTask();
	}

	private void bindToPrinter(Printer printer) {
		connectedPrinter = printer;
		if (connectedPrinter != null &&
				connectedPrinter.getCommandInterface() instanceof RoboxRemoteCommandInterface) {
			connectedServer = ((RemoteDetectedPrinter) connectedPrinter.getCommandInterface().getPrinterHandle()).getServerPrinterIsAttachedTo();
			String profileName = "";
			String cameraName = "";
			CameraTag tag = connectedServer.cameraTagProperty().get();
			if (tag != null) {
				profileName = tag.getCameraProfileName();
				cameraName = tag.getCameraName();
			}

			populateCameraProfileChooser();
			populateCameraChooser();

			if (!profileName.isBlank() && !cameraName.isBlank()) {
				selectCameraAndProfile(profileName, cameraName);
			}
			else if (selectedProfile != null && selectedCamera != null) {
				connectedServer.setCameraTag(selectedProfile.getProfileName(), selectedCamera.getCameraName());
			}
		}
		controlSnapshotTask();
	}

	private void controlSnapshotTask() {
		//TODO: Return early rather than if/else
		if (applicationStatus.getMode() == ApplicationMode.STATUS && connectedServer != null && connectedServer.getCameraDetected()) {
			repopulateCameraProfileChooser();
			repopulateCameraChooser();

			if (snapshotTask == null)
				takeSnapshot();

		}
		else {
			if (snapshotTask != null) {
				snapshotTask.cancel();
				snapshotTask = null;
			}
		}
	}

	@Override
	protected void selectProfile(CameraProfile profile) {
		super.selectProfile(profile);
		if (connectedServer != null && profile != null && selectedCamera != null) {
			connectedServer.setCameraTag(profile.getProfileName(), selectedCamera.getCameraName());
			coreMemory.updateRoboxRoot(connectedServer);
		}
	}

	@Override
	protected void selectCamera(CameraInfo camera) {
		super.selectCamera(camera);
		if (connectedServer != null && selectedProfile != null && camera != null) {
			connectedServer.setCameraTag(selectedProfile.getProfileName(), camera.getCameraName());
			coreMemory.updateRoboxRoot(connectedServer);
		}
	}
}
