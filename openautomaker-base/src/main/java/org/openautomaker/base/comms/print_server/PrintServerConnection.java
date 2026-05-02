package org.openautomaker.base.comms.print_server;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.camera.CameraInfo;
import org.openautomaker.base.comms.print_server.data.CameraTag;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.fileRepresentation.CameraSettings;
import org.openautomaker.base.inject.comms.PrintServerConnectionFactory;
import org.openautomaker.base.inject.printing.SFTPUtilsFactory;
import org.openautomaker.base.services.printing.SFTPUtils;
import org.openautomaker.base.utils.PercentProgressReceiver;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.base.utils.net.MultipartUtility;
import org.openautomaker.environment.preference.application.NamePreference;
import org.openautomaker.environment.preference.application.VersionPreference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.jcraft.jsch.SftpProgressMonitor;
import com.vdurmont.semver4j.Semver;

import celtech.roboxbase.comms.DetectedDevice;
import celtech.roboxbase.comms.DeviceDetector;
import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.RoboxCommsManager;
import celtech.roboxbase.comms.DeviceDetector.DeviceConnectionType;
import celtech.roboxbase.comms.remote.Configuration;
import celtech.roboxbase.comms.remote.StringToBase64Encoder;
import celtech.roboxbase.comms.remote.clear.ListCamerasResponse;
import celtech.roboxbase.comms.remote.clear.ListPrintersResponse;
import celtech.roboxbase.comms.remote.clear.WhoAreYouResponse;
import celtech.roboxbase.comms.remote.types.SerializableFilament;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

/**
 *
 */
//TODO: Per connection service to monitor connected servers?
public final class PrintServerConnection {

	private static final class COMMAND {
		static final String LIST_PRINTERS = "/api/discovery/listPrinters";

		static final String LIST_CAMERAS = "/api/discovery/listCameras";

		static final String UPDATE_SYSTEM = "/api/admin/updateSystem";

		static final String SHUTDOWN_SYSTEM = "/api/admin/shutdown";

		static final String SAVE_FILAMENT = "/api/admin/saveFilament";

		static final String DELETE_FILAMENT = "/api/admin/deleteFilament";

		static final String SET_UPGRADE = "/api/admin/setUpgradeState";

		static final String CAMERA_CONTROL = "/api/cameraControl";

		static final String TAKE_SNAPSHOT = "/snapshot";
	}

	private static final Logger LOGGER = LogManager.getLogger();

	private InetAddress fAddress;

	private final StringProperty fName = new SimpleStringProperty("");

	private final StringProperty serverAddressProperty = new SimpleStringProperty("");

	private final StringProperty fPin = new SimpleStringProperty("1111");

	private final BooleanProperty discoveredConnection = new SimpleBooleanProperty(true);

	private ListProperty<String> fColours = new SimpleListProperty<>();

	private final StringProperty fRootUUID = new SimpleStringProperty("");

	private final BooleanProperty fCameraDetected = new SimpleBooleanProperty(false);

	private final ObjectProperty<CameraTag> fCameraTag = new SimpleObjectProperty<>(new CameraTag());

	private Semver fVersion;

	private List<DetectedDevice> fDetectedDevices = new ArrayList<>();

	private List<CameraInfo> fAttachedCameras = new ArrayList<>();

	private int fPollCount = 0;

	//private final BooleanProperty fDataChanged = new SimpleBooleanProperty(false);

	private static final ObjectMapper fMapper = new ObjectMapper();

	private final ObjectProperty<ServerStatus> fServerStatus = new SimpleObjectProperty<>(ServerStatus.NOT_CONNECTED);

	public static final String DEFAULT_USER = "root";

	public static final int READ_TIMEOUT_SHORT = 1500;

	public static final int CONNECT_TIMEOUT_SHORT = 300;

	public static final int READ_TIMEOUT_LONG = 15000;

	public static final int CONNECT_TIMEOUT_LONG = 2000;

	public static final int MAX_ALLOWED_POLL_COUNT = 8;



	//	@JsonIgnore
	//	// A server for any given address is created once, on the first create request, and placed on the
	//	// known server list. Subsequent create requests get the server from the known server list.
	//	private static final List<DetectedServer> knownServerList = new ArrayList<>();

	public enum ServerStatus {

		NOT_CONNECTED,
		CONNECTED,
		WRONG_VERSION,
		WRONG_PIN,
		UPGRADING;
	}

	private final PrintServerConnectionFactory detectedServerFactory;

	private final VersionPreference versionPreference;

	private final NamePreference namePreference;

	private final SFTPUtilsFactory sFTPUtilsFactory;

	private final RoboxCommsManager roboxCommsManager;

	@AssistedInject
	protected PrintServerConnection(
			NamePreference namePreference,
			VersionPreference versionPreference,
			RoboxCommsManager roboxCommsManager,
			PrintServerConnectionFactory detectedServerFactory,
			SFTPUtilsFactory sFTPUtilsFactory) {

		this.namePreference = namePreference;
		this.versionPreference = versionPreference;
		this.roboxCommsManager = roboxCommsManager;
		this.detectedServerFactory = detectedServerFactory;
		this.sFTPUtilsFactory = sFTPUtilsFactory;
	}

	@AssistedInject
	protected PrintServerConnection(
			NamePreference namePreference,
			VersionPreference versionPreference,
			RoboxCommsManager roboxCommsManager,
			PrintServerConnectionFactory detectedServerFactory,
			SFTPUtilsFactory sFTPUtilsFactory,
			@Assisted InetAddress address) {

		this.namePreference = namePreference;
		this.versionPreference = versionPreference;
		this.roboxCommsManager = roboxCommsManager;
		this.detectedServerFactory = detectedServerFactory;
		this.sFTPUtilsFactory = sFTPUtilsFactory;

		fAddress = address;
	}

	public void setServerAddress(String address) {
		serverAddressProperty.set(address);
	}

	public String getServerAddress() {
		return serverAddressProperty.get();
	}

	//	public synchronized DetectedServer createDetectedServer(InetAddress address) {
	//		// This is the only public way to create a DetectedServer. It is synchronised so that
	//		// it can be called by multiple threads.
	//		return knownServerList.stream()
	//				.filter(s -> s.getAddress().equals(address))
	//				.findAny()
	//				.orElseGet(() -> {
	//					DetectedServer ds = detectedServerFactory.create(address);
	//					knownServerList.add(ds);
	//					return ds;
	//				});
	//	}

	public int getPollCount() {
		return fPollCount;
	}

	public void resetPollCount() {
		fPollCount = 0;
	}

	public boolean maxPollCountExceeded() {
		if (fPollCount > MAX_ALLOWED_POLL_COUNT) {
			LOGGER.warn("Maximum poll count of \"" + getDisplayName() + "\" exceeded! Count = " + Integer.toString(fPollCount));
			return true;
		}
		else
			return false;
		//return (pollCount > maxAllowedPollCount);
	}

	public boolean incrementPollCount() {
		fPollCount++;
		return !maxPollCountExceeded();
	}

	public InetAddress getAddress() {
		return fAddress;
	}

	public void setAddress(InetAddress address) {
		this.fAddress = address;
	}

	public String getName() {
		return fName.get();
	}

	public String getDisplayName() {
		return getName() + "@" + getServerIP();
	}

	public void setName(String name) {
		this.fName.set(name);
	}

	public StringProperty nameProperty() {
		return fName;
	}

	public String getServerIP() {
		return fAddress.getHostAddress();
	}

	public Semver getVersion() {
		return fVersion;
	}

	public void setVersion(Semver version) {
		fVersion = version;
	}

	public List<String> getColours() {
		return fColours.get();
	}

	public void setColours(List<String> colours) {
		this.fColours.setAll(colours);
	}

	public ListProperty<String> coloursProperty() {
		return fColours;
	}

	public boolean getCameraDetected() {
		return fCameraDetected.get();
	}

	public void setCameraDetected(boolean cameraDetected) {
		this.fCameraDetected.set(cameraDetected);
		//TODO: If cameraDetected is cleared, then ensure that there are no attached cameras.
		if (!cameraDetected)
			fAttachedCameras.clear();
	}

	public BooleanProperty cameraDetectedProperty() {
		return fCameraDetected;
	}

	public ObjectProperty<CameraTag> cameraTagProperty() {
		return fCameraTag;
	}

	public void setCameraTag(String cameraProfileName, String cameraName) {
		CameraTag currentTag = fCameraTag.get();
		if (!currentTag.getCameraProfileName().equalsIgnoreCase(cameraProfileName) ||
				!currentTag.getCameraName().equalsIgnoreCase(cameraName)) {
			fCameraTag.set(new CameraTag(cameraProfileName, cameraName));
		}
	}

	public String getCameraProfileName() {
		return fCameraTag.get().getCameraProfileName();
	}

	public String getCameraName() {
		return fCameraTag.get().getCameraName();
	}

	public String getRootUUID() {
		return fRootUUID.get();
	}

	public void setRootUUID(String rootUUID) {
		this.fRootUUID.set(rootUUID);
	}

	public StringProperty rootUUIDProperty() {
		return fRootUUID;
	}

	public ServerStatus getServerStatus() {
		// LOGGER.info("ServerStatus of " + getName() + " == " + this.serverStatus.get().name());
		return fServerStatus.get();
	}

	private void setServerStatus(ServerStatus status) {
		fServerStatus.set(status);
	}

	public ObjectProperty<ServerStatus> serverStatusProperty() {
		return fServerStatus;
	}

	public String getPin() {
		return fPin.get();
	}

	public void setPin(String pin) {
		this.fPin.set(pin);
	}

	public StringProperty pinProperty() {
		return fPin;
	}

	public boolean isDiscoveredConnection() {
		return discoveredConnection.get();
	}

	public BooleanProperty getDiscoveredConnectionProperty() {
		return discoveredConnection;
	}

	public void setDiscoveredConnection(boolean value) {
		discoveredConnection.set(value);
	}

	//	public ReadOnlyBooleanProperty dataChangedProperty() {
	//		return fDataChanged;
	//	}

	//TODO: this should not return a boolean, just throw an exception if there is an error condition.  Only called from the print server connection manager
	public void connect() throws InvalidPinException, InvalidResponseException, InvalidVersionException, IOException {

		ServerStatus serverStatus = fServerStatus.get();
		// No need to do anything.  Just return
		if (serverStatus == ServerStatus.CONNECTED)
			return;

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Connecting " + fName.get() + " Current Status: " + fServerStatus.get());

		Semver appVersion = versionPreference.getValue();

		// Return early if we already know it's the wrong version
		if (serverStatus == ServerStatus.WRONG_VERSION)
			throw new InvalidVersionException("Host: " + getDisplayName() + " Version: " + fVersion.getValue() + " (Expected: " + appVersion.getValue() + ")");

		int response = 0;

		// Try to get the response from the data.  Return false on fail
		try {
			response = getData(COMMAND.LIST_PRINTERS);
		}
		catch (IOException ex) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Caught exception " + ex.toString() + "- setting status to NOT_CONNECTED");

			setServerStatus(ServerStatus.NOT_CONNECTED);
			throw ex;

			//coreMemory.deactivateRoboxRoot(this);
			//return false;
		}

		// Not authorised
		if (response == 401) {
			LOGGER.debug("Setting status to WRONG_PIN");
			setServerStatus(ServerStatus.WRONG_PIN);

			throw new InvalidPinException();

			//coreMemory.deactivateRoboxRoot(this); // Shouldn't manage this, this should be in the printer manager.
			//return false;
		}

		// Any other command apart from success
		if (response != 200) {
			LOGGER.debug("Response = " + Integer.toString(response) + "- setting status to NOT_CONNECTED");
			setServerStatus(ServerStatus.NOT_CONNECTED);

			throw new InvalidResponseException();

			//coreMemory.deactivateRoboxRoot(this);
			//return false;
		}

		// Includes debug hack to allow mismatching development versions to operate.
		// TODO: This should probably check for a specific dev suffix on either rather than both
		if (!fVersion.isEqualTo(appVersion) &&
				!(appVersion.getSuffixTokens()[0].equals("tadev") && fVersion.getSuffixTokens()[0].equals("tadev"))) {
			LOGGER.debug("Setting status to WRONG_VERSION");
			setServerStatus(ServerStatus.WRONG_VERSION);

			throw new InvalidVersionException("Host: " + getDisplayName() + " Version: " + fVersion.getValue() + " (Expected: " + appVersion.getValue() + ")");

			//			coreMemory.deactivateRoboxRoot(this);
			//			return false;
		}

		// If all the checked have padded, goto connected
		LOGGER.debug("Setting status to CONNECTED");
		setServerStatus(ServerStatus.CONNECTED);
		//coreMemory.activateRoboxRoot(this);

		//return true;

	}

	public void disconnect() {
		LOGGER.info("Disconnecting \"" + getDisplayName() + "\"");
		setCameraDetected(false);
		setServerStatus(ServerStatus.NOT_CONNECTED);
		//coreMemory.deactivateRoboxRoot(this);

		// Disconnect devices (printers)
		fDetectedDevices.forEach((device) -> {
			LOGGER.info("Disconnecting device " + device.toString());
			roboxCommsManager.disconnected(device);
		});
	}

	//TODO: This looks like it needs a connection checker service.  doing this here as a blocking check seems odd.
	public boolean whoAreYou() {
		boolean gotAResponse = false;
		WhoAreYouResponse response = null;

		String url = "http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + "/api/discovery/whoareyou?pc=yes&rid=yes";
		long t1 = System.currentTimeMillis();
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// optional default is GET
			con.setRequestMethod("GET");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			//+ BaseConfiguration.getApplicationVersion());

			con.setConnectTimeout(CONNECT_TIMEOUT_SHORT);
			con.setReadTimeout(READ_TIMEOUT_SHORT);

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				fPollCount = 0; // Contact! Zero the poll count;
				int availChars = con.getInputStream().available();
				byte[] inputData = new byte[availChars];
				con.getInputStream().read(inputData, 0, availChars);
				response = fMapper.readValue(inputData, WhoAreYouResponse.class);

				if (response != null) {
					gotAResponse = true;
					fName.set(response.getName());
					fVersion = new Semver(response.getServerVersion());
					serverAddressProperty.set(response.getServerIP());

					ObservableList<String> observableList = FXCollections.observableArrayList();
					List<String> printerColours = response.getPrinterColours();
					if (printerColours != null) {
						observableList = FXCollections.observableArrayList(printerColours);
					}
					fColours = new SimpleListProperty<>(observableList);

					String rid = response.getRootUUID();
					if (rid != null)
						fRootUUID.set(rid);
					//System.out.println("Host \"" + address.getHostAddress() + "\" name = \"" + response.getName() + "\" rootUUID = \"" + rid + "\"");
					//if (!version.getVersionString().equalsIgnoreCase(BaseConfiguration.getApplicationVersion()))
					//{
					//    setServerStatus(ServerStatus.WRONG_VERSION);
					//}
				}
				else {
					LOGGER.warn("Got an indecipherable response from " + fAddress.getHostAddress());
				}
			}
			else if (responseCode == 503) {
				if (fServerStatus.get() != ServerStatus.UPGRADING) {
					LOGGER.warn("503 response from @ " + fAddress.getHostAddress());
					disconnect();
				}
			}
			else {
				LOGGER.warn("No response to \"" + url + "\" from @" + fAddress.getHostAddress());
				//disconnect();
			}
		}
		catch (java.net.SocketTimeoutException stex) {
			long t2 = System.currentTimeMillis();
			LOGGER.warn("Timeout whilst asking who are you @ " + fAddress.getHostAddress() + " - time taken = " + Long.toString(t2 - t1));
			//disconnect();
		}
		catch (IOException ex) {
			LOGGER.error("Error whilst asking who are you @ " + fAddress.getHostAddress(), ex);
			disconnect();
		}
		return gotAResponse;
	}

	public List<DetectedDevice> listAttachedPrinters() {
		String url = "http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + COMMAND.LIST_PRINTERS;

		long t1 = System.currentTimeMillis();
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// optional default is GET
			con.setRequestMethod("GET");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			con.setConnectTimeout(CONNECT_TIMEOUT_SHORT);
			con.setReadTimeout(READ_TIMEOUT_SHORT);

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				int availChars = con.getInputStream().available();
				byte[] inputData = new byte[availChars];
				con.getInputStream().read(inputData, 0, availChars);
				ListPrintersResponse listPrintersResponse = fMapper.readValue(inputData, ListPrintersResponse.class);

				List<DetectedDevice> previousDetectedDevices = fDetectedDevices;
				fDetectedDevices = new ArrayList<>();
				// Move any existing devices from the current list to the new list.
				listPrintersResponse.getPrinterIDs().forEach((printerID) -> {
					fDetectedDevices.add(previousDetectedDevices.stream()
							.filter((d) -> d.getConnectionHandle().equals(printerID) && d.getConnectionType() == DeviceDetector.DeviceConnectionType.ROBOX_REMOTE)
							.findAny()
							.orElse(new RemoteDetectedPrinter(this, DeviceDetector.DeviceConnectionType.ROBOX_REMOTE, printerID)));
				});

				// Disconnect any devices that were previously found, but are not in the new list.
				previousDetectedDevices.forEach((device) -> {
					if (!fDetectedDevices.contains(device)) {
						LOGGER.info("Disconnecting missing device " + device.getConnectionHandle());
						roboxCommsManager.disconnected(device);
					}
				});
				fPollCount = 0; // Successful contact, so zero the poll count;
			}
			else {
				disconnect();
				LOGGER.warn("No response to \"" + url + "\" from @" + fAddress.getHostAddress());
			}
		}
		catch (java.net.SocketTimeoutException ex) {
			long t2 = System.currentTimeMillis();
			LOGGER.error("Timeout whilst polling for remote printers @ " + fAddress.getHostAddress() + " - time taken = " + Long.toString(t2 - t1));
			// But don't disconnect.
			//disconnect();
		}
		catch (IOException ex) {
			disconnect();
			LOGGER.warn("Error whilst polling for remote printers @ " + fAddress.getHostAddress(), ex);
		}
		return fDetectedDevices;
	}

	public List<CameraInfo> listAttachedCameras() {
		String url = "http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + COMMAND.LIST_CAMERAS;

		List<CameraInfo> detectedCameras = new ArrayList<>();

		long t1 = System.currentTimeMillis();
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// optional default is GET
			con.setRequestMethod("GET");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			con.setConnectTimeout(CONNECT_TIMEOUT_SHORT);
			con.setReadTimeout(READ_TIMEOUT_SHORT);

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				int availChars = con.getInputStream().available();
				byte[] inputData = new byte[availChars];
				con.getInputStream().read(inputData, 0, availChars);

				ListCamerasResponse listCamerasResponse = fMapper.readValue(inputData, ListCamerasResponse.class);

				detectedCameras = listCamerasResponse.getCameras();
				detectedCameras.forEach((dc) -> {
					dc.setServer(this);
					dc.setServerIP(fAddress.getHostAddress());
				});
				fAttachedCameras = detectedCameras;

				fPollCount = 0; // Successful contact, so zero the poll count;
			}
			else {
				LOGGER.warn("No response to \"" + url + "\"from @" + fAddress.getHostAddress());
			}
		}
		catch (java.net.SocketTimeoutException ex) {
			long t2 = System.currentTimeMillis();
			LOGGER.error("Timeout whilst polling for remote cameras @" + fAddress.getHostAddress() + " - time taken = " + Long.toString(t2 - t1));
			// On a timeout, use last know list of attached cameras, to avoid flickering of camera panels.
			detectedCameras = fAttachedCameras;
		}
		catch (IOException ex) {
			LOGGER.warn("Error whilst polling for remote cameras @" + fAddress.getHostAddress(), ex);
		}

		fCameraDetected.set(!detectedCameras.isEmpty());

		return detectedCameras;
	}

	public Image takeCameraSnapshot(CameraSettings settings) {
		String url = "http://"
				+ fAddress.getHostAddress()
				+ ":"
				+ Configuration.remotePort
				+ COMMAND.CAMERA_CONTROL
				+ "/"
				+ Integer.toString(settings.camera.getCameraNumber())
				+ COMMAND.TAKE_SNAPSHOT;
		Image snapshotImage = null;
		long t1 = System.currentTimeMillis();
		try {
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// optional default is GET
			con.setRequestMethod("POST");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			String jsonifiedData = SystemUtils.jsonEscape(fMapper.writeValueAsString(settings));
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type", "application/json");
			con.setRequestProperty("Content-Length", "" + jsonifiedData.length());
			con.getOutputStream().write(jsonifiedData.getBytes());

			con.setConnectTimeout(CONNECT_TIMEOUT_SHORT);
			con.setReadTimeout(READ_TIMEOUT_SHORT);

			int responseCode = con.getResponseCode();

			if (responseCode == 200) {
				fPollCount = 0; // Successful contact, so zero the poll count;
				snapshotImage = new Image(con.getInputStream());
				if (snapshotImage.isError()) {
					LOGGER.error("Error loading image.from \"" + url + "\"@" + fAddress.getHostAddress() + "\r\n" + snapshotImage.exceptionProperty().get().getMessage(), snapshotImage.exceptionProperty().get());
					snapshotImage = null;
				}
			}
			else {
				LOGGER.warn("No response to \"" + url + "\"@" + fAddress.getHostAddress());
			}
		}
		catch (java.net.SocketTimeoutException ex) {
			long t2 = System.currentTimeMillis();
			LOGGER.error("Timeout whilst polling for remote cameras @" + fAddress.getHostAddress() + " - time taken = " + Long.toString(t2 - t1));
		}
		catch (IOException ex) {
			LOGGER.error("Error whilst polling for remote cameras @" + fAddress.getHostAddress(), ex);
		}

		return snapshotImage;
	}

	public void postRoboxPacket(String urlString) throws IOException {
		postRoboxPacket(urlString, null, null);
	}

	public Object postRoboxPacket(String urlString, String content, Class<?> expectedResponseClass) throws IOException {
		Object returnvalue = null;

		URL obj = new URL("http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + urlString);
		try {
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			con.setRequestMethod("POST");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			con.setConnectTimeout(CONNECT_TIMEOUT_LONG);
			con.setReadTimeout(READ_TIMEOUT_LONG);

			if (content != null) {
				con.setDoOutput(true);
				con.setRequestProperty("Content-Type", "application/json");
				con.setRequestProperty("Content-Length", "" + content.length());
				con.getOutputStream().write(content.getBytes());
			}

			int responseCode = con.getResponseCode();
			fPollCount = 0; // Successful contact, so zero the poll count;

			if (responseCode >= 200
					&& responseCode < 300) {
				if (expectedResponseClass != null) {
					returnvalue = fMapper.readValue(con.getInputStream(), expectedResponseClass);
				}
			}
			else {
				//Raise an error but don't disconnect...
				LOGGER.error("Got " + responseCode + " when trying " + urlString);
			}
		}
		catch (java.net.SocketTimeoutException ex) {
			LOGGER.error("Timeout in postRoboxPacket @" + obj.toString() + ", exception message = " + ex.getMessage());
			throw ex;
		}
		return returnvalue;
	}

	public int postData(String urlString, String content) throws IOException {
		URL obj = new URL("http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + urlString);
		int rc = -1;

		try {
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			con.setRequestMethod("POST");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			con.setReadTimeout(READ_TIMEOUT_LONG);
			con.setConnectTimeout(CONNECT_TIMEOUT_LONG);

			if (content != null) {
				con.setDoOutput(true);
				con.setRequestProperty("Content-Type", "application/json");
				con.setRequestProperty("Content-Length", "" + content.length());
				con.getOutputStream().write(content.getBytes());
			}

			rc = con.getResponseCode();
			fPollCount = 0; // Successful contact, so zero the poll count;
		}
		catch (java.net.SocketTimeoutException ex) {
			LOGGER.error("Timeout in postData @" + obj.toString() + ", exception message = " + ex.getMessage());
			throw ex;
		}
		return rc;
	}

	public int getData(String urlString) throws IOException {
		URL obj = new URL("http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + urlString);

		int rc = -1;
		try {
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			con.setRequestMethod("GET");

			//add request header
			con.setRequestProperty("User-Agent", namePreference.getValue());
			con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + getPin()));

			con.setConnectTimeout(CONNECT_TIMEOUT_LONG);
			con.setReadTimeout(READ_TIMEOUT_LONG);

			rc = con.getResponseCode();
			fPollCount = 0; // Successful contact, so zero the poll count;
		}
		catch (java.net.SocketTimeoutException ex) {
			LOGGER.error("Timeout in getData @" + obj.toString() + ", exception message = " + ex.getMessage());
			throw ex;
		}
		return rc;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(21, 31)
				.append(fAddress)
				.toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof PrintServerConnection))
			return false;

		if (obj == this)
			return true;


		PrintServerConnection rhs = (PrintServerConnection) obj;
		// If both servers have rootUUIDs, compare them. Otherwise,
		// compare the IP addresses.
		if (!fRootUUID.get().trim().isEmpty() && !rhs.fRootUUID.get().trim().isEmpty())
			return fRootUUID.get().equals(rhs.fRootUUID.get());

		return fAddress.equals(rhs.getAddress());
	}

	private class TransferProgressMonitor implements SftpProgressMonitor {
		long count = 0;
		long fileSize = 0;
		PrintServerConnection server;
		PercentProgressReceiver progressReceiver;

		public TransferProgressMonitor(PrintServerConnection server, PercentProgressReceiver progressReceiver) {
			this.server = server;
			this.progressReceiver = progressReceiver;
		}

		@Override
		public void init(int op, String src, String dest, long fileSize) {
			this.fileSize = fileSize;
			this.count = 0;

			server.resetPollCount();
			LOGGER.debug("Initialise file transfer: src = \"" + src + "\", dst = \"" + dest + "\", fileSize = " + Long.toString(fileSize));
		}

		@Override
		public boolean count(long increment) {
			count += increment;
			float percentageDone = 50.0f;
			if (fileSize > 0)
				percentageDone = 25.0f + (75.0f * count / fileSize);
			progressReceiver.updateProgressPercent(percentageDone);

			server.resetPollCount();
			LOGGER.debug("Transfer progress: " + Float.toString(percentageDone) + "%");
			return true;
		}

		@Override
		public void end() {
		}
	}

	public boolean upgradeRootSoftware(Path path, Path filename, PercentProgressReceiver progressReceiver) {
		boolean success = true;

		progressReceiver.updateProgressPercent(0.0);

		try {
			if (postData(COMMAND.SET_UPGRADE, "true") == 503) { // 503 = server unavailable - implies it is probably upgrading.
				return false;
			}
		}
		catch (IOException ex) {
		}
		fServerStatus.set(ServerStatus.UPGRADING);

		// First try SFTP;
		TransferProgressMonitor monitor = new TransferProgressMonitor(this, progressReceiver);
		SFTPUtils sftpHelper = sFTPUtilsFactory.create(fAddress.getHostAddress());
		File localFile = path.resolve(filename).toFile();
		if (sftpHelper.transferToRemotePrinter(localFile, Paths.get("/tmp"), filename, monitor)) {
			try {
				int rc = postData(COMMAND.SHUTDOWN_SYSTEM, null);
				success = (rc == 200);
			}
			catch (java.net.SocketTimeoutException stex) {
				success = true;
			}
			catch (IOException ex) {
				LOGGER.debug("Exception in shutdown of remote server, server likely shutdown before response: " + ex.getMessage());
			}
		}

		if (!success) {

			// Try http POST
			String charset = "UTF-8";
			String requestURL = "http://" + fAddress.getHostAddress() + ":" + Configuration.remotePort + COMMAND.UPDATE_SYSTEM;

			try {
				progressReceiver.updateProgressPercent(0.0);
				long t1 = System.currentTimeMillis();
				MultipartUtility multipart = new MultipartUtility(requestURL, charset, StringToBase64Encoder.encode("root:" + getPin()));

				File rootSoftwareFile = path.resolve(filename).toFile();
				LOGGER.info("upgradeRootSoftware: uploading file " + path + filename);

				// Awkward lambda is to update the last response time whenever the progress bar is updated. This should prevent the server from
				// being removed.
				multipart.addFilePart("name", rootSoftwareFile,
						(double pp) -> {
							fPollCount = 0;
							progressReceiver.updateProgressPercent(pp);
						});
				long t2 = System.currentTimeMillis();
				LOGGER.debug("upgradeRootSoftware: time to do multipart.addFilePartLong() = " + Long.toString(t2 - t1));

				//List<String> response = 
				multipart.finish();

				long t3 = System.currentTimeMillis();
				LOGGER.debug("upgradeRootSoftware: time to do multipart.finish() = " + Long.toString(t3 - t2) + ", total time = " + Long.toString(t3 - t1));

				success = true;
			}
			catch (IOException ex) {
				LOGGER.error("Failure during write of root software: " + ex.getMessage());
			}
		}

		if (success) {
			// Disconnecting here does not clear the user interface, so set the poll count to force the user interface to disconnect.
			disconnect();
			fPollCount = MAX_ALLOWED_POLL_COUNT + 1;
		}
		else {
			fServerStatus.set(ServerStatus.CONNECTED);
			try {
				postData(COMMAND.SET_UPGRADE, "false");
			}
			catch (IOException ex) {
			}
		}

		return success;
	}

	public void saveFilament(Filament filament) {
		try {
			SerializableFilament serializableFilament = new SerializableFilament(filament);
			String jsonifiedData = SystemUtils.jsonEscape(fMapper.writeValueAsString(serializableFilament));
			postData(COMMAND.SAVE_FILAMENT, jsonifiedData);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to save filament to root " + getName(), ex);
		}
	}

	public void deleteFilament(Filament filament) {
		try {
			SerializableFilament serializableFilament = new SerializableFilament(filament);
			String jsonifiedData = SystemUtils.jsonEscape(fMapper.writeValueAsString(serializableFilament));
			postData(COMMAND.DELETE_FILAMENT, jsonifiedData);
		}
		catch (IOException ex) {
			LOGGER.error("Failed to delete filament from root " + getName(), ex);
		}
	}

	@Override
	public String toString() {
		return fName + "@" + fAddress.getHostAddress() + " v" + fVersion;
	}
}
