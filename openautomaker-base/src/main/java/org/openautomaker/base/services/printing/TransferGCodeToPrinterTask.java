package org.openautomaker.base.services.printing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import org.openautomaker.base.postprocessor.PrintJobStatistics;
import org.openautomaker.base.printerControl.comms.commands.GCodeMacros;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.environment.PrinterType;
import org.openautomaker.environment.preference.application.KeyPathPreference;

import com.google.inject.assistedinject.Assisted;
import com.jcraft.jsch.SftpProgressMonitor;

import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.IRemotePrinterControl;
import jakarta.inject.Inject;
import javafx.beans.property.IntegerProperty;
import javafx.concurrent.Task;

/**
 *
 * @author ianhudson
 */
public class TransferGCodeToPrinterTask extends Task<GCodePrintResult> {
	private static final Logger LOGGER = LogManager.getLogger();

	private class TransferProgressMonitor implements SftpProgressMonitor {
		long count = 0;
		long fileSize = 0;
		PrintServerConnection server;

		public TransferProgressMonitor(PrintServerConnection server) {
			this.server = server;
		}

		@Override
		public void init(int op, String src, String dest, long fileSize) {
			this.fileSize = fileSize;
			this.count = 0;
			if (server != null)
				server.resetPollCount();
			LOGGER.debug("Initialise file transfer: src = \"" + src + "\", dst = \"" + dest + "\", fileSize = " + Long.toString(fileSize));
		}

		@Override
		public boolean count(long increment) {
			count += increment;
			LOGGER.debug("Transfer progress: count = " + Long.toString(count) + " of " + Long.toString(fileSize));
			updateProgress((float) count, (float) fileSize);
			if (server != null)
				server.resetPollCount();
			return !isCancelled();
		}

		@Override
		public void end() {
		}
	}

	private Printer printerToUse = null;
	private String gcodeFileToPrint = null;
	private String printJobID = null;

	private IntegerProperty linesInFile = null;
	private boolean printUsingSDCard = true;
	private boolean dontInitiatePrint = false;
	private int startFromSequenceNumber = 0;
	private boolean thisJobCanBeReprinted = false;
	private int lineCounter = 0;
	private int numberOfLines = 0;
	private final PrintJobStatistics printJobStatistics;
	private final CameraSettings cameraData;

	//Dependencies
	private final GCodeMacros gCodeMacros;
	private final KeyPathPreference keyPathPreference;

	/**
	 *
	 * @param printerToUse
	 * @param modelFileToPrint
	 * @param printJobID
	 * @param linesInFile
	 * @param printUsingSDCard
	 * @param startFromSequenceNumber
	 * @param thisJobCanBeReprinted
	 * @param dontInitiatePrint
	 * @param printJobStatistics
	 * @param cameraData
	 */
	@Inject
	protected TransferGCodeToPrinterTask(
			GCodeMacros gCodeMacros,
			KeyPathPreference keyPathPreference,
			@Assisted Printer printerToUse,
			@Assisted("modelFileToPrint") String modelFileToPrint,
			@Assisted("printJobID") String printJobID,
			@Assisted IntegerProperty linesInFile,
			@Assisted("printUsingSDCard") boolean printUsingSDCard,
			@Assisted int startFromSequenceNumber,
			@Assisted("thisJobCanBeReprinted") boolean thisJobCanBeReprinted,
			@Assisted("dontInitiatePrint") boolean dontInitiatePrint,
			@Assisted PrintJobStatistics printJobStatistics,
			@Assisted CameraSettings cameraData) {

		this.gCodeMacros = gCodeMacros;
		this.keyPathPreference = keyPathPreference;

		this.printerToUse = printerToUse;
		this.gcodeFileToPrint = modelFileToPrint;
		this.printJobID = printJobID;
		this.linesInFile = linesInFile;
		this.printUsingSDCard = printUsingSDCard;
		this.startFromSequenceNumber = startFromSequenceNumber;
		this.thisJobCanBeReprinted = thisJobCanBeReprinted;
		this.dontInitiatePrint = dontInitiatePrint;
		this.printJobStatistics = printJobStatistics;
		this.cameraData = cameraData;
		updateProgress(0.0, 100.0);
	}

	@Override
	protected GCodePrintResult call() throws Exception {
		long startTime = System.currentTimeMillis();
		GCodePrintResult result = new GCodePrintResult();
		result.setPrintJobID(printJobID);

		boolean gotToEndOK = false;

		updateTitle("GCode Print ID:" + printJobID);
		File gcodeFile = new File(gcodeFileToPrint);
		Optional<PrinterType> printerType = Optional.of(printerToUse.findPrinterType());
		numberOfLines = gCodeMacros.countLinesInMacroFile(gcodeFile, ";", printerType);
		linesInFile.setValue(numberOfLines);

		LOGGER.debug("Beginning transfer of file " + gcodeFileToPrint + " to printer from line "
				+ startFromSequenceNumber);

		boolean errorTransferringStats = false;
		boolean printerIsRemote = (printerToUse.getCommandInterface() instanceof IRemotePrinterControl);

		if (printerIsRemote) {
			//We're talking to a remote printer
			//Send the statistics and camera data if they exist
			if (!gcodeFile.getParent().endsWith("Macros") && printJobStatistics != null) {
				try {
					((IRemotePrinterControl) printerToUse.getCommandInterface()).sendStatistics(printJobStatistics);
					if (cameraData != null)
						((IRemotePrinterControl) printerToUse.getCommandInterface()).sendCameraData(printJobID, cameraData);

				}
				catch (RoboxCommsException ex) {
					errorTransferringStats = true;
				}
			}
		}

		if (!errorTransferringStats) {
			updateMessage("Transferring GCode");

			if (printerIsRemote &&
					!gcodeFile.getParent().endsWith("Macros") &&
					printUsingSDCard &&
					startFromSequenceNumber == 0)
				gotToEndOK = transferToRemotePrinter(gcodeFile);
			else
				gotToEndOK = transferToPrinter(gcodeFile);
		}

		long endTime = System.currentTimeMillis();
		LOGGER.info("Print transfer took " + Long.toString(endTime - startTime) + "ms");

		result.setSuccess(gotToEndOK);
		return result;
	}

	private boolean transferToPrinter(File gcodeFile) throws Exception {
		FileReader gcodeReader = null;
		Scanner scanner = null;
		boolean gotToEndOK = false;
		//Note that FileReader is used, not File, since File is not Closeable
		try {
			gcodeReader = new FileReader(gcodeFile);
			scanner = new Scanner(gcodeReader);

			if (printUsingSDCard && startFromSequenceNumber == 0) {
				printerToUse.initialiseDataFileSend(printJobID, thisJobCanBeReprinted);
			}

			printerToUse.resetDataFileSequenceNumber();
			printerToUse.setDataFileSequenceNumberStartPoint(startFromSequenceNumber);

			lineCounter = 0;

			while (scanner.hasNextLine() && !isCancelled()) {
				String line = scanner.nextLine();
				line = line.trim();

				if (gCodeMacros.isMacroExecutionDirective(line)) {
					//Put in contents of macro
					List<String> macroLines = gCodeMacros.getMacroContents(line,
							Optional.of(printerToUse.findPrinterType()),
							printerToUse.headProperty().get().typeCodeProperty().get(),
							false, false, false);
					for (String macroLine : macroLines) {
						outputLine(macroLine);
					}
				}
				else {
					outputLine(line);
				}

				if (lineCounter < numberOfLines) {
					updateProgress((float) lineCounter, (float) numberOfLines);
				}
			}
			gotToEndOK = true;
		}
		catch (FileNotFoundException ex) {
			LOGGER.error("Couldn't open gcode file " + gcodeFileToPrint + ": " + ex);
		}
		catch (RoboxCommsException ex) {
			LOGGER.error("Error during print operation - abandoning transfer of " + printJobID + " " + ex.getMessage());
			updateMessage("Printing error");
		}
		finally {
			if (scanner != null) {
				scanner.close();
			}

			if (gcodeReader != null) {
				gcodeReader.close();
			}
		}
		return gotToEndOK;
	}

	private void outputLine(String line) throws RoboxCommsException, DatafileSendNotInitialised {
		if (line.equals("") == false && line.startsWith(";") == false) {
			line = SystemUtils.cleanGCodeForTransmission(line);
			if (printUsingSDCard) {
				LOGGER.trace("Sending data line " + lineCounter + " to printer");
				printerToUse.sendDataFileChunk(line, lineCounter == numberOfLines - 1,
						true);
				if (startFromSequenceNumber == 0
						&& !dontInitiatePrint
						&& ((printerToUse.getDataFileSequenceNumber() > 1
								&& printerToUse.isPrintInitiated() == false)
								|| (lineCounter == numberOfLines - 1
										&& printerToUse.isPrintInitiated() == false))) {
					//Start printing!
					printerToUse.initiatePrint(printJobID);
				}
			}
			else {
				printerToUse.sendRawGCode(line, false);
			}
			lineCounter++;
		}
	}

	private boolean transferToRemotePrinter(File gcodeFile) {
		// Use sftp to transfer file to remote printer.
		// Note: this does NOT expand macros, which has to
		// be done on the remote printer.
		boolean transferredOK = false;
		IRemotePrinterControl remoteCI = (IRemotePrinterControl) printerToUse.getCommandInterface();
		RemoteDetectedPrinter remoteDevice = (RemoteDetectedPrinter) printerToUse.getCommandInterface().getPrinterHandle();
		String hostAddress = remoteDevice.getServerPrinterIsAttachedTo().getServerIP();

		SFTPUtils sftpHelper = new SFTPUtils(keyPathPreference, hostAddress);
		SftpProgressMonitor monitor = new TransferGCodeToPrinterTask.TransferProgressMonitor(remoteDevice.getServerPrinterIsAttachedTo());
		Path remoteDirectory = Paths.get(BaseConfiguration.getRemotePrintJobDirectory()).resolve(printJobID);
		if (sftpHelper.transferToRemotePrinter(gcodeFile, remoteDirectory, Paths.get(gcodeFile.getName()), monitor)) {
			try {
				LOGGER.info("Transferred GCode");
				if (printJobStatistics != null)
					remoteCI.startPrintJob(printJobID);
				else
					remoteCI.printGCodeFile(remoteDirectory.resolve(gcodeFile.getName()));
				transferredOK = true;
			}
			catch (RoboxCommsException ex) {
				if (thisJobCanBeReprinted && printJobStatistics != null)
					LOGGER.error("Failed to start remote print job\"" + printJobID + "\".", ex);
				else
					LOGGER.error("Failed to start remote print of file \"" + gcodeFile.getName() + "\".", ex);
			}
		}

		return transferredOK;
	}
}
