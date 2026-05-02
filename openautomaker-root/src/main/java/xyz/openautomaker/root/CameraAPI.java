package xyz.openautomaker.root;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.annotation.Timed;

import celtech.roboxbase.comms.remote.Configuration;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import xyz.openautomaker.base.configuration.BaseConfiguration;
import xyz.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import xyz.openautomaker.base.utils.ScriptUtils;
import xyz.openautomaker.environment.MachineType;
import xyz.openautomaker.environment.OpenAutoMakerEnv;
import xyz.openautomaker.root.comms.CameraCommsManager;
/**
 *
 * @author Tony Aldhous
 */
@RolesAllowed("root")
@Path(Configuration.cameraAPIService+ "/{cameraNumber}")
@Produces(MediaType.APPLICATION_JSON)
public class CameraAPI
{

	private static final Logger LOGGER = LogManager.getLogger();

	private static final int BYTE_SCRIPT_TIMEOUT = 15;

	private final CameraCommsManager cameraCommsManager;

	public CameraAPI(CameraCommsManager cameraCommsManager)
	{
		this.cameraCommsManager = cameraCommsManager;
	}

	private byte[] takeSnapshot(CameraSettings settings) {
		LOGGER.debug("Taking snapshot for camera " + settings.camera.getCameraName());
		List<String> parameters = settings.encodeSettingsForRootScript();
		byte[] imageData = null;
		// Synchronized access with CameraTriggerManager::triggerUSBCamera, so both are not trying to access the
		// camera at the same time. Synchronize on the CameraSettings class object as it
		// is easily accessable to both methods.

		if (OpenAutoMakerEnv.get().getMachineType() == MachineType.LINUX) {
			synchronized(CameraSettings.class) {
				imageData = ScriptUtils.runByteScript(BaseConfiguration.getApplicationInstallDirectory(CameraAPI.class) + "takeSnapshot.sh",
						BYTE_SCRIPT_TIMEOUT,
						parameters.toArray(new String[0]));
			}
		}
		if (imageData == null)
			LOGGER.debug("ImageData = null");
		else
			LOGGER.debug("ImageData length = " + imageData.length);

		return imageData;
	}

	private Optional<byte[]> getCameraSnapshot(CameraSettings settings)
	{
		return cameraCommsManager.getAllCameraInfo()
				.stream()
				.filter(c -> c.getUdevName().equals(settings.camera.getUdevName()))
				.findAny()
				.map((c) -> takeSnapshot(settings));
	}

	@POST
	@Timed
	@Path("snapshot")
	@Produces("image/jpg")
	public Response getSnapshot(@PathParam("cameraNumber") String cameraNumber,
			CameraSettings settings)
	{
		// Camera number should match settings.
		int cameraNo = -1;
		try {
			cameraNo = Integer.parseInt(cameraNumber);
		}
		catch (NumberFormatException ex) {
			cameraNo = -2;
		}
		if (cameraNo != settings.camera.getCameraNumber())
			return Response.serverError().build();

		return getCameraSnapshot(settings).map((imageData) -> Response.ok(imageData).build())
				.orElseGet(() -> Response.serverError().build());
	}
}
