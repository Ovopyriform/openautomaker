package org.openautomaker.root;

import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import org.openautomaker.base.postprocessor.PrintJobStatistics;
import org.openautomaker.environment.preference.root.PrintJobsPathPreference;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;

import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.remote.Configuration;
import celtech.roboxbase.comms.rx.AckResponse;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.rx.RoboxRxPacketFactory;
import celtech.roboxbase.comms.rx.RxPacketTypeEnum;
import celtech.roboxbase.comms.tx.ReadSendFileReport;
import celtech.roboxbase.comms.tx.ReportErrors;
import celtech.roboxbase.comms.tx.RoboxTxPacket;
import celtech.roboxbase.comms.tx.SendDataFileChunk;
import celtech.roboxbase.comms.tx.SendDataFileEnd;
import celtech.roboxbase.comms.tx.SendDataFileStart;
import celtech.roboxbase.comms.tx.SendPrintFileStart;
import celtech.roboxbase.comms.tx.StatusRequest;
import celtech.roboxbase.comms.tx.WritePrinterID;

/**
 *
 * @author Ian
 */
@RolesAllowed("root")
@Path("/{printerID}" + Configuration.lowLevelAPIService)
@Produces(MediaType.APPLICATION_JSON)
public class LowLevelAPI
{

	private static final Logger LOGGER = LogManager.getLogger();

	private final PrinterRegistry printerRegistry;
	private final PrintJobPersister printJobPersister;
	private final PrintJobsPathPreference printJobsPathPreference;
	private final FilamentContainer filamentContainer;

	@Inject
	public LowLevelAPI(PrinterRegistry printerRegistry, PrintJobPersister printJobPersister,
			PrintJobsPathPreference printJobsPathPreference, FilamentContainer filamentContainer) {
		this.printerRegistry = printerRegistry;
		this.printJobPersister = printJobPersister;
		this.printJobsPathPreference = printJobsPathPreference;
		this.filamentContainer = filamentContainer;
	}

	@POST
	@Timed
	@Path(Configuration.connectService)
	public Response connect(@PathParam("printerID") String printerID)
	{
		if (Root.isResponding()) {
			LOGGER.info("Was asked to connect to " + printerID);
			return Response.ok().build();
		}
		else
			return Response.serverError().status(503).build();
	}

	@POST
	@Timed
	@Path(Configuration.disconnectService)
	public Response disconnect(@PathParam("printerID") String printerID)
	{
		if (Root.isResponding()) {
			LOGGER.info("Was asked to disconnect from " + printerID);
			return Response.ok().build();
		}
		else
			return Response.serverError().status(503).build();
	}

	@POST
	@Timed
	@Path(Configuration.writeDataService)
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public RoboxRxPacket writeToPrinter(@PathParam("printerID") String printerID,
			RoboxTxPacket remoteTx)
	{
		if (Root.isResponding()) {
			RoboxRxPacket rxPacket = null;
			long t1 = System.currentTimeMillis();
			//        LOGGER.info("Request to write to printer with ID " + printerID + " and packet type " + remoteTx.getPacketType());
			//        String messagePayload = remoteTx.getMessagePayload();//
			//        if (messagePayload != null)
			//             LOGGER.info("    Payload length " + Integer.toString(messagePayload.length()));
			//        if (remoteTx.getIncludeSequenceNumber())
			//             LOGGER.info("    Sequence number = " + Integer.toString(remoteTx.getSequenceNumber()));
			try
			{
				if (printerRegistry != null
						&& !printerRegistry.getRemotePrinterIDs().contains(printerID))
				{
					rxPacket = RoboxRxPacketFactory.createPacket(RxPacketTypeEnum.PRINTER_NOT_FOUND);
				} else
				{
					if (remoteTx instanceof StatusRequest)
					{
						rxPacket = printerRegistry.getRemotePrinters().get(printerID).getLastStatusResponse();
					} else if (remoteTx instanceof ReportErrors)
					{
						AckResponse ackResponse = printerRegistry.getRemotePrinters().get(printerID).getLastErrorResponse();
						// The firmware errors in the last response will be empty because it has already been processed by Root.
						// So replace it with the list of active errors.
						//            ackResponse.setFirmwareErrors(printerRegistry.getRemotePrinters().get(printerID).getActiveErrors());
						ackResponse.setFirmwareErrors(printerRegistry.getRemotePrinters().get(printerID).getCurrentErrors());
						rxPacket = ackResponse;
					} else if (remoteTx instanceof ReadSendFileReport
							&& printerRegistry.getRemotePrinters().get(printerID).getPrintEngine().highIntensityCommsInProgressProperty().get())
					{
						rxPacket = RoboxRxPacketFactory.createPacket(RxPacketTypeEnum.SEND_FILE);
					}
					else
					{
						try
						{
							rxPacket = printerRegistry.getRemotePrinters().get(printerID).getCommandInterface().writeToPrinter(remoteTx, false);
						} catch (RoboxCommsException ex)
						{
							LOGGER.error("Failed whilst writing to local printer with ID" + printerID);
						}

						if (remoteTx instanceof SendPrintFileStart
								|| remoteTx instanceof SendDataFileStart)
						{
							printJobPersister.startFile(remoteTx.getMessagePayload());
							printerRegistry.getRemotePrinters().get(printerID).getPrintEngine().takingItThroughTheBackDoor(true);
						} else if (remoteTx instanceof SendDataFileChunk)
						{
							String payload = remoteTx.getMessagePayload();
							printJobPersister.writeSegment(payload);
						} else if (remoteTx instanceof SendDataFileEnd)
						{
							printJobPersister.closeFile(remoteTx.getMessagePayload());
							printerRegistry.getRemotePrinters().get(printerID).getPrintEngine().takingItThroughTheBackDoor(false);
						}
						else if (remoteTx instanceof WritePrinterID &&
								printerRegistry.getRemotePrinters().size() == 1 &&
								printerRegistry.getRemotePrinters().get(printerID).printerConfigurationProperty().get().typeCode.equals("RBX10"))
						{
							// If only one printer is connected and it is an RBX10, then this is a Robox Pro printer.
							// Keep the server name the same as the printer name.
							WritePrinterID wpid = ((WritePrinterID) remoteTx);
							printerRegistry.setServerName(wpid.getPrinterFriendlyName());
						}
					}
				}

			}
			catch (Exception ex)
			{
				long t2 = System.currentTimeMillis();
				LOGGER.error("LowLevelAPI.writeToPrinter() after " + (t2 - t1) + "ms caught exception " + ex.getClass().getCanonicalName() + " with message " + ex.getMessage());
				throw ex;
			}

			long t2 = System.currentTimeMillis();
			//if (rxPacket == null)
			//    LOGGER.info("Returning null packet after " + (t2 - t1) + "ms");
			//else
			//    LOGGER.info("Returning packet " + rxPacket.getPacketType() + " after " + (t2 - t1) + "ms");
			return rxPacket;
		}
		else
			return null;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path(Configuration.sendStatisticsService)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response provideStatistics(@PathParam("printerID") String printerID,
			PrintJobStatistics statistics)
	{
		Response response = null;
		if (Root.isResponding()) {
			java.nio.file.Path statsFileLocation = printJobsPathPreference.getValue().resolve(statistics.getPrintJobID()).resolve(statistics.getPrintJobID() + BaseConfiguration.statisticsFileExtension);
			try
			{
				LOGGER.info("Writing statistics to file \"" + statsFileLocation + "\" ...");
				statistics.writeStatisticsToFile(statsFileLocation);
				LOGGER.info("... done");
				response = Response.ok().build();
			} catch (IOException ex)
			{
				response = Response.serverError().build();
			}
		}
		else
			response = Response.serverError().status(503).build();

		return response;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path(Configuration.retrieveStatisticsService)
	@Produces(MediaType.APPLICATION_JSON)
	public PrintJobStatistics retrieveStatistics(@PathParam("printerID") String printerID,
			String printJobID)
	{
		PrintJobStatistics statistics = null;
		if (Root.isResponding()) {
			try
			{
				statistics = printerRegistry.getRemotePrinters().get(printerID).getPrintEngine().printJobProperty().get().getStatistics();
			} catch (IOException ex)
			{

			}
		}
		return statistics;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path("/{printJobID}" + Configuration.sendCameraDataService)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response provideCameraData(@PathParam("printerID") String printerID,
			@PathParam("printJobID") String printJobID,
			CameraSettings cameraData)
	{
		Response response = null;
		if (Root.isResponding()) {
			java.nio.file.Path dataFileLocation = printJobsPathPreference.getValue().resolve(printJobID).resolve(printJobID + BaseConfiguration.cameraDataFileExtension);
			try
			{
				LOGGER.info("Writing camera data to file \"" + dataFileLocation + "\" ...");
				cameraData.writeToFile(dataFileLocation);
				LOGGER.info("... done");
				response = Response.ok().build();
			}
			catch (IOException ex) {
				response = Response.serverError().build();
			}
		}
		else
			response = Response.serverError().status(503).build();

		return response;
	}

	@RolesAllowed("root")
	@GET
	@Timed
	@Path("/{printJobID}" + Configuration.retrieveCameraDataService)
	@Produces(MediaType.APPLICATION_JSON)
	public CameraSettings retrieveCameraData(@PathParam("printerID") String printerID,
			@PathParam("printJobID") String printJobID)
	{
		CameraSettings cameraData = null;
		if (Root.isResponding()) {
			java.nio.file.Path dataFileLocation = printJobsPathPreference.getValue().resolve(printJobID).resolve(printJobID + BaseConfiguration.cameraDataFileExtension);
			try
			{
				//LOGGER.info("Reading camera data from file \"" + dataFileLocation + "\" ...");
				cameraData= CameraSettings.readFromFile(dataFileLocation);
				//LOGGER.info("... done");
			}
			catch (IOException ex) {
			}
		}
		return cameraData;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path(Configuration.overrideFilamentService)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response overrideFilament(@PathParam("printerID") String printerID,
			Map<Integer, String> filamentMap)
	{
		Response response = null;
		if (Root.isResponding()) {
			Entry<Integer, String> filamentEntry = filamentMap.entrySet().iterator().next();
			Filament chosenFilament = filamentContainer.getFilamentByID(filamentEntry.getValue());
			if (chosenFilament != null)
			{
				printerRegistry.getRemotePrinters().get(printerID).overrideFilament(filamentEntry.getKey(), chosenFilament);
				response = Response.ok().build();
			} else
			{
				response = Response.serverError().build();
			}
		}
		else
			response = Response.serverError().status(503).build();
		return response;
	}
}
