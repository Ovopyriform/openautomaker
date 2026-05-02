package xyz.openautomaker.root;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.codahale.metrics.annotation.Timed;

import celtech.roboxbase.comms.remote.Configuration;
import xyz.openautomaker.base.printerControl.model.PrinterException;

/**
 *
 * @author Ian
 */
@RolesAllowed("root")
@Path(Configuration.adminAPIService)
@Produces(MediaType.APPLICATION_JSON)
public class RootWebPageResource
{

	private static final Logger LOGGER = LogManager.getLogger();

	private final Utils utils = new Utils();

	public RootWebPageResource()
	{
	}

	@POST
	@Timed
	@Path(Configuration.shutdown)
	public void shutdown()
	{
		new Runnable()
		{
			@Override
			public void run()
			{
				LOGGER.info("Running shutdown thread");
				Root.getInstance().stop();
				LOGGER.info("Shutdown thread finished");
			}
		}.run();
	}

	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/setServerName")
	public Response setServerName(String serverName)
	{
		PrinterRegistry.getInstance().setServerName(serverName);
		// If this server is connected to just one RBX10 printer, then
		// assume it is a RoboxPro. Name the printer to be the same as the
		// server.
		if (PrinterRegistry.getInstance().getRemotePrinters().size() == 1)
		{
			PrinterRegistry.getInstance().getRemotePrinters().forEach((k,v) ->
			{
				try
				{
					if(v.printerConfigurationProperty().get().typeCode.equals("RBX10"))
					{
						v.updatePrinterName(serverName);
					}
				}
				catch (PrinterException ex)
				{
					LOGGER.error("Failed to set associated Robox Pro name.");
				}
			});
		}

		return Response.ok().build();
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("updateSystem")
	public Response updateSystem(
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException
	{
		File uploadedFile = new File(System.getProperty("java.io.tmpdir") + fileDetail.getFileName());
		LOGGER.info("Upgrade file " + uploadedFile.toString() + " has been uploaded");
		// save it
		utils.writeToFile(uploadedInputStream, uploadedFile.toPath());
		Root.getInstance().stop();
		return Response.ok().build();
	}
}
