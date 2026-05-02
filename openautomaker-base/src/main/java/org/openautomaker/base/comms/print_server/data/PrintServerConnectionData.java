package org.openautomaker.base.comms.print_server.data;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.inject.comms.PrintServerConnectionFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vdurmont.semver4j.Semver;

public class PrintServerConnectionData {

	private final String address;
	private final String name;
	private final String rootUUID;
	private final String pin;
	private final boolean wasAutomaticallyAdded;
	private final String version;
	private final CameraTag cameraTag;

	@JsonCreator
	public PrintServerConnectionData(
			@JsonProperty("address") String address,
			@JsonProperty("name") String name,
			@JsonProperty("rootUUID") String rootUUID,
			@JsonProperty("pin") String pin,
			@JsonProperty("wasAutomaticallyAdded") boolean wasAutomaticallyAdded,
			@JsonProperty("version") String version,
			@JsonProperty("cameraTag") CameraTag cameraTag) {
		this.address = address;
		this.name = name;
		this.rootUUID = rootUUID;
		this.pin = pin;
		this.wasAutomaticallyAdded = wasAutomaticallyAdded;
		this.version = version;
		this.cameraTag = cameraTag;
	}

	public static PrintServerConnectionData from(PrintServerConnection server) {
		CameraTag tag = server.cameraTagProperty().get();
		return new PrintServerConnectionData(
				server.getServerAddress(),
				server.getName(),
				server.getRootUUID(),
				server.getPin(),
				server.isDiscoveredConnection(),
				server.getVersion() != null ? server.getVersion().toString() : null,
				new CameraTag(tag.getCameraProfileName(), tag.getCameraName()));
	}

	public PrintServerConnection toConnection(PrintServerConnectionFactory factory) throws UnknownHostException {
		InetAddress inetAddress = InetAddress.getByName(address);
		PrintServerConnection server = factory.create(inetAddress);
		server.setServerAddress(address);
		server.setName(name != null ? name : "");
		if (rootUUID != null)
			server.setRootUUID(rootUUID);
		if (version != null)
			server.setVersion(new Semver(version));
		server.setPin(pin != null ? pin : "1111");
		server.setDiscoveredConnection(wasAutomaticallyAdded);
		if (cameraTag != null)
			server.setCameraTag(cameraTag.getCameraProfileName(), cameraTag.getCameraName());
		return server;
	}

	public String getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}

	public String getRootUUID() {
		return rootUUID;
	}

	public String getPin() {
		return pin;
	}

	public boolean isWasAutomaticallyAdded() {
		return wasAutomaticallyAdded;
	}

	public String getVersion() {
		return version;
	}

	public CameraTag getCameraTag() {
		return cameraTag;
	}
}
