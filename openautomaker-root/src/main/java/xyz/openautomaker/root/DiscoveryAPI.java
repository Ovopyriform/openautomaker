package xyz.openautomaker.root;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonIgnore;

import celtech.roboxbase.comms.remote.clear.ListCamerasResponse;
import celtech.roboxbase.comms.remote.clear.ListPrintersResponse;
import celtech.roboxbase.comms.remote.clear.WhoAreYouResponse;
import javafx.scene.paint.Color;
import xyz.openautomaker.base.configuration.BaseConfiguration;
import xyz.openautomaker.base.printerControl.model.Printer;
import xyz.openautomaker.root.comms.CameraCommsManager;
import xyz.openautomaker.root.utils.NetworkUtils;

/**
 *
 * @author Ian
 */
@Path("/discovery")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryAPI
{

	@JsonIgnore
	private static final Logger LOGGER = LogManager.getLogger();

	private final CameraCommsManager cameraCommsManager;

	public DiscoveryAPI(CameraCommsManager cameraCommsManager)
	{
		this.cameraCommsManager = cameraCommsManager;
	}

	@RolesAllowed("root")
	@GET
	@Timed
	@Path("/listPrinters")
	@Consumes(MediaType.APPLICATION_JSON)
	public ListPrintersResponse listPrinters()
	{
		if (Root.isResponding() && PrinterRegistry.getInstance() != null)
		{
			ListPrintersResponse response = new ListPrintersResponse(PrinterRegistry.getInstance().getRemotePrinterIDs());
			LOGGER.trace("Returning " + response.getPrinterIDs().size() + " printers");

			return response;
		} else
		{
			return null;
		}
	}

	@RolesAllowed("root")
	@GET
	@Timed
	@Path("/listCameras")
	@Consumes(MediaType.APPLICATION_JSON)
	public ListCamerasResponse listCameras(@Context HttpServletRequest request)
	{
		ListCamerasResponse response = null;
		try {
			response = new ListCamerasResponse(cameraCommsManager.getAllCameraInfo());
		} catch(Exception e) {
			LOGGER.error("Exception in list camera response", e);
		}
		return response;
	}

	@GET
	@Timed(name = "getFingerprint")
	@Path("/whoareyou")
	@Consumes(MediaType.APPLICATION_JSON)
	public WhoAreYouResponse getFingerprint(@Context HttpServletRequest request, @QueryParam("pc")String pc, @QueryParam("rid")String rid, @QueryParam("ru")String ru)
	{
		boolean reportUpgrading = (ru != null && ru.equalsIgnoreCase("yes"));
		Root r = Root.getInstance();
		if (!r.getIsStopping() &&
				(reportUpgrading || !r.getIsUpgrading()) &&
				PrinterRegistry.getInstance() != null)
		{
			String hostAddress = "Unknown";

			try
			{
				hostAddress = NetworkUtils.determineIPAddress();
			} catch (SocketException e)
			{
				LOGGER.error("/whoareyou(" + request.getRemoteAddr() + "): unable to get current IP " + e.getMessage());
			}

			List<String> printerColours = null;

			// If we have printer colours requested from AutoMaker we return them
			if(pc != null && pc.equalsIgnoreCase("yes"))
			{
				printerColours = new ArrayList<>();

				Map<String, Printer> remotePrinters = PrinterRegistry.getInstance().getRemotePrinters();
				if(remotePrinters != null && !remotePrinters.isEmpty())
				{
					for(Printer printer : remotePrinters.validValues())
					{
						Color printerColour = printer.getPrinterIdentity().printerColourProperty().get();
						printerColours.add(printerColour.toString());
					}
				}
			}

			String rootUUID = null;
			if (rid != null && rid.equalsIgnoreCase("yes"))
				rootUUID = RootUUID.get();

			String upgradeStatus = null;
			if (reportUpgrading)
				upgradeStatus = r.getIsUpgrading() ? "upgrading" : "";

			return new WhoAreYouResponse(PrinterRegistry.getInstance().getServerName(),
					BaseConfiguration.getApplicationVersion(),
					hostAddress,
					printerColours,
					rootUUID,
					upgradeStatus);
		} else
		{
			return null;
		}
	}
}
