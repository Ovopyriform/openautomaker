package celtech.roboxbase.comms.remote;

import java.nio.file.Path;

import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import org.openautomaker.base.postprocessor.PrintJobStatistics;

import celtech.roboxbase.comms.exceptions.RoboxCommsException;

/**
 * Operations available only on a remote (network-connected) printer. Callers
 * check {@code instanceof IRemotePrinterControl} instead of the concrete class.
 */
public interface IRemotePrinterControl {
	boolean cancelPrint(boolean safetyOn);

	void sendStatistics(PrintJobStatistics printJobStatistics) throws RoboxCommsException;

	PrintJobStatistics retrieveStatistics() throws RoboxCommsException;

	void sendCameraData(String printJobID, CameraSettings cameraData) throws RoboxCommsException;

	CameraSettings retrieveCameraData(String printJobID) throws RoboxCommsException;

	void overrideFilament(int reelNumber, Filament filament) throws RoboxCommsException;

	void startPrintJob(String printJobID) throws RoboxCommsException;

	void printGCodeFile(Path remoteFileName) throws RoboxCommsException;
}
