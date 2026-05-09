package org.openautomaker.root;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.MachineType;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.preference.root.TempPathPreference;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;

import celtech.roboxbase.comms.remote.Configuration;
import celtech.roboxbase.comms.remote.clear.WifiStatusResponse;
import celtech.roboxbase.comms.remote.types.SerializableFilament;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(Configuration.adminAPIService)
@Produces(MediaType.APPLICATION_JSON)
public class AdminAPI {

	private static final Logger LOGGER = LogManager.getLogger();

	private final Utils utils = new Utils();
	private final OpenAutomakerEnv env;
	private final TempPathPreference tempPathPreference;
	private final FilamentContainer filamentContainer;
	private final WifiControl wifiControl;
	private final PrinterRegistry printerRegistry;

	@Inject
	public AdminAPI(OpenAutomakerEnv env, TempPathPreference tempPathPreference,
			FilamentContainer filamentContainer, WifiControl wifiControl, PrinterRegistry printerRegistry) {
		this.env = env;
		this.tempPathPreference = tempPathPreference;
		this.filamentContainer = filamentContainer;
		this.wifiControl = wifiControl;
		this.printerRegistry = printerRegistry;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path(Configuration.shutdown)
	public Response shutdown() {
		LOGGER.info("Shutdown requested");
		if (!Root.getInstance().getIsStopping()) {
			Root.getInstance().setIsStopping(true);
			new Timer().schedule(new TimerTask() { public void run() { Root.getInstance().stop(); } }, 10000);
		}
		return Response.ok().build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/setServerName")
	public Response setServerName(String serverName) {
		if (Root.isResponding()) {
			printerRegistry.setServerName(Utils.cleanInboundJSONString(serverName));
			return Response.ok().build();
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("updateSystem")
	public Response updateSystem(@FormDataParam("name") InputStream uploadedInputStream, @FormDataParam("name") FormDataContentDisposition fileDetail) throws IOException {
		Response response = null;
		if (Root.isResponding()) {
			try {
				String fileName = fileDetail.getFileName();
				LOGGER.info("Asked to upgrade using file " + fileName);

				Root.getInstance().setIsUpgrading(true);
				long t1 = System.currentTimeMillis();
				java.nio.file.Path uploadedFileLocation;

				if (env.getMachineType() != MachineType.WINDOWS) {
					uploadedFileLocation = java.nio.file.Paths.get("/tmp/" + fileName);
				} else {
					java.nio.file.Path tempPath = tempPathPreference.getValue();
					uploadedFileLocation = tempPath != null ? tempPath.resolve(fileName) : java.nio.file.Paths.get("/tmp/" + fileName);
				}

				utils.writeToFile(uploadedInputStream, uploadedFileLocation);

				long t2 = System.currentTimeMillis();
				LOGGER.info("Upgrade file " + uploadedFileLocation + " has been uploaded in " + Long.toString(t2 - t1) + "ms");

				Root.getInstance().setIsStopping(true);
				new Timer().schedule(new TimerTask() { public void run() { Root.getInstance().restart(); } }, 10000);
				response = Response.ok().build();
			} catch (IOException ex) {
				Root.getInstance().setIsUpgrading(false);
				response = Response.serverError().build();
			}
		} else
			response = Response.serverError().status(503).build();

		return response;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/updatePIN")
	public Response updatePIN(String newPIN) {
		if (Root.isResponding()) {
			Root.getInstance().setApplicationPIN(Utils.cleanInboundJSONString(newPIN));
			return Response.ok().build();
		} else
			return Response.serverError().status(503).build();
	}

	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/resetPIN")
	public Response resetPIN(String printerSerial) {
		if (Root.isResponding()) {
			boolean serialMatches = false;

			String serialToUse = Utils.cleanInboundJSONString(printerSerial);
			if (serialToUse != null) {
				for (Printer printer : printerRegistry.getRemotePrinters().values()) {
					if (printer.getPrinterIdentity().printerUniqueIDProperty().get().toLowerCase().endsWith(serialToUse.toLowerCase())) {
						serialMatches = true;
						break;
					}
				}
			}

			if (serialMatches) {
				Root.getInstance().resetApplicationPINToDefault();
				return Response.ok().build();
			} else {
				return Response.serverError().build();
			}
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/setWiFiCredentials")
	public Response setWiFiCredentials(String ssidAndPassword) {
		if (Root.isResponding()) {
			LOGGER.info("Asked to change wifi creds to " + ssidAndPassword);
			if (wifiControl.setupWiFiCredentials(Utils.cleanInboundJSONString(ssidAndPassword)))
				return Response.ok().build();
			else
				return Response.serverError().build();
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/enableDisableWifi")
	public Response enableDisableWifi(boolean enableWifi) {
		if (Root.isResponding()) {
			if (wifiControl.enableWifi(enableWifi))
				return Response.ok().build();
			else
				return Response.serverError().build();
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path("/getCurrentWifiState")
	public WifiStatusResponse getCurrentWifiSSID() {
		if (Root.isResponding())
			return wifiControl.getCurrentWifiState();
		else
			return null;
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/saveFilament")
	public Response saveFilament(SerializableFilament serializableFilament) {
		if (Root.isResponding()) {
			Filament filament = serializableFilament.getFilament();
			filamentContainer.saveFilament(filament);
			return Response.ok().build();
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/deleteFilament")
	public Response deleteFilament(SerializableFilament serializableFilament) {
		if (Root.isResponding()) {
			Filament filament = serializableFilament.getFilament();
			filamentContainer.deleteFilament(filament);
			return Response.ok().build();
		} else
			return Response.serverError().status(503).build();
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/setUpgradeState")
	public Response setUpgradeState(boolean isUpgrading) {
		if (!Root.getInstance().getIsStopping()) {
			Root.getInstance().setIsUpgrading(isUpgrading);
			return Response.ok().build();
		} else
			return Response.serverError().status(503).build();
	}
}
