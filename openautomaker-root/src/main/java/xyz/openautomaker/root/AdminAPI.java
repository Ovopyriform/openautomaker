package xyz.openautomaker.root;

import static xyz.openautomaker.environment.OpenAutoMakerEnv.TEMP;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.codahale.metrics.annotation.Timed;

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
import xyz.openautomaker.base.BaseLookup;
import xyz.openautomaker.base.configuration.Filament;
import xyz.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import xyz.openautomaker.base.printerControl.model.Printer;
import xyz.openautomaker.environment.MachineType;
import xyz.openautomaker.environment.OpenAutoMakerEnv;

/**
 *
 * @author Ian
 */
@Path(Configuration.adminAPIService)
@Produces(MediaType.APPLICATION_JSON)
public class AdminAPI {
	private static final Logger LOGGER = LogManager.getLogger();
	private final Utils utils = new Utils();

	public AdminAPI() {
	}

	@RolesAllowed("root")
	@POST
	@Timed
	@Path(Configuration.shutdown)
	public Response shutdown() {
		LOGGER.info("Shutdown requested");
		if (!Root.getInstance().getIsStopping()) {
			Root.getInstance().setIsStopping(true);
			BaseLookup.getTaskExecutor().runDelayedOnBackgroundThread(() -> Root.getInstance().stop(), 10000);
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
			PrinterRegistry.getInstance().setServerName(Utils.cleanInboundJSONString(serverName));
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

				OpenAutoMakerEnv env = OpenAutoMakerEnv.get();

				//TODO: Does this need to have a check.  Always use the user automaker temp dir.
				if (env.getMachineType() != MachineType.WINDOWS) {
					uploadedFileLocation = Paths.get("/tmp/" + fileName);
				}
				else {
					uploadedFileLocation = env.getUserPath(TEMP).resolve(fileName);
				}

				// save it
				utils.writeToFile(uploadedInputStream, uploadedFileLocation);

				long t2 = System.currentTimeMillis();
				LOGGER.info("Upgrade file " + uploadedFileLocation + " has been uploaded in " + Long.toString(t2 - t1) + "ms");

				// Shut down - but delay by 10 seconds to allow the response to go back to the requester first.
				Root.getInstance().setIsStopping(true);
				BaseLookup.getTaskExecutor().runDelayedOnBackgroundThread(() -> Root.getInstance().restart(), 10000);
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
				for (Printer printer : PrinterRegistry.getInstance().getRemotePrinters().validValues()) {
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
			if (WifiControl.setupWiFiCredentials(Utils.cleanInboundJSONString(ssidAndPassword)))
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
			if (WifiControl.enableWifi(enableWifi))
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
			return WifiControl.getCurrentWifiState();
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
			FilamentContainer.getInstance().saveFilament(filament);
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
			FilamentContainer.getInstance().deleteFilament(filament);
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
