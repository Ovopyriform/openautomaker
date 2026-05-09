package org.openautomaker.root;

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
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.preference.application.VersionPreference;
import org.openautomaker.root.comms.CameraCommsManager;
import org.openautomaker.root.utils.NetworkUtils;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.inject.Inject;

import celtech.roboxbase.comms.remote.clear.ListCamerasResponse;
import celtech.roboxbase.comms.remote.clear.ListPrintersResponse;
import celtech.roboxbase.comms.remote.clear.WhoAreYouResponse;
import javafx.scene.paint.Color;

@Path("/discovery")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryAPI {

	@JsonIgnore
	private static final Logger LOGGER = LogManager.getLogger();

	private final CameraCommsManager cameraCommsManager;
	private final PrinterRegistry printerRegistry;
	private final VersionPreference versionPreference;

	@Inject
	public DiscoveryAPI(CameraCommsManager cameraCommsManager, PrinterRegistry printerRegistry, VersionPreference versionPreference) {
		this.cameraCommsManager = cameraCommsManager;
		this.printerRegistry = printerRegistry;
		this.versionPreference = versionPreference;
	}

	@RolesAllowed("root")
	@GET
	@Timed
	@Path("/listPrinters")
	@Consumes(MediaType.APPLICATION_JSON)
	public ListPrintersResponse listPrinters()
	{
		if (Root.isResponding() && printerRegistry != null)
		{
			ListPrintersResponse response = new ListPrintersResponse(printerRegistry.getRemotePrinterIDs());
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
				printerRegistry != null)
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

				Map<String, Printer> remotePrinters = printerRegistry.getRemotePrinters();
				if(remotePrinters != null && !remotePrinters.isEmpty())
				{
					for(Printer printer : remotePrinters.values())
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

			return new WhoAreYouResponse(printerRegistry.getServerName(),
					versionPreference.getValue().getValue(),
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
