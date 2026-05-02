package org.openautomaker.base.comms.print_server;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.comms.print_server.data.PrintServerConnectionData;
import org.openautomaker.base.inject.comms.PrintServerConnectionFactory;
import org.openautomaker.environment.preference.APreference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.InetAddressSerializer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ConnectedServersPreference extends APreference<List<PrintServerConnection>> {

	private static final Logger LOGGER = LogManager.getLogger();

	private final List<PrintServerConnection> connectedServers = new CopyOnWriteArrayList<>();

	private final ObjectMapper objectMapper;
	private final PrintServerConnectionFactory printServerFactory;

	@Inject
	protected ConnectedServersPreference(PrintServerConnectionFactory printServerFactory) {
		super();

		this.printServerFactory = printServerFactory;

		objectMapper = new ObjectMapper();
		SimpleModule module = new SimpleModule("PrintServerConnectionModule", new Version(1, 0, 0, null, null, null));
		module.addSerializer(InetAddress.class, new InetAddressSerializer(true));
		objectMapper.registerModule(module);

		readConnectedServers();
	}

	private void readConnectedServers() {
		String jsonStr = getNode().get(getKey(), "");
		if (jsonStr.isEmpty())
			return;

		try {
			List<PrintServerConnectionData> saved = objectMapper.readValue(jsonStr, new TypeReference<List<PrintServerConnectionData>>() {});
			for (PrintServerConnectionData data : saved) {
				connectedServers.add(data.toConnection(printServerFactory));
			}
		}
		catch (IOException ex) {
			LOGGER.error("Unable to read Connected Servers. Removing preference data");
			remove();
		}
	}

	private void writeConnectedServers() {
		try {
			List<PrintServerConnectionData> dataList = connectedServers.stream()
					.map(PrintServerConnectionData::from)
					.toList();
			getNode().put(getKey(), objectMapper.writeValueAsString(dataList));
		}
		catch (JsonProcessingException e) {
			LOGGER.error("Unable to write Connected Servers to preference");
		}
	}

	public void put(PrintServerConnection server) {
		if (server == null || server.getAddress() == null)
			throw new IllegalArgumentException("Server cannot be null");

		if (!connectedServers.contains(server))
			connectedServers.add(server);
		writeConnectedServers();
	}

	public void remove(PrintServerConnection server) {
		if (server == null || server.getAddress() == null)
			throw new IllegalArgumentException("Server cannot be null");

		connectedServers.remove(server);
		writeConnectedServers();
	}

	public boolean contains(PrintServerConnection server) {
		if (server == null || server.getAddress() == null)
			throw new IllegalArgumentException("Server cannot be null");

		return connectedServers.contains(server);
	}

	public boolean contains(InetAddress address) {
		if (address == null)
			throw new IllegalArgumentException("Address cannot be null");

		return connectedServers.stream().anyMatch(s -> address.equals(s.getAddress()));
	}

	public PrintServerConnection get(InetAddress address) {
		if (address == null)
			throw new IllegalArgumentException("Address cannot be null");

		return connectedServers.stream()
				.filter(s -> address.equals(s.getAddress()))
				.findFirst()
				.orElse(null);
	}

	public PrintServerConnection get(PrintServerConnection server) {
		if (server == null || server.getAddress() == null)
			throw new IllegalArgumentException("Server cannot be null");

		return get(server.getAddress());
	}

	@Override
	public List<PrintServerConnection> getValue() {
		return Collections.unmodifiableList(connectedServers);
	}

	@Override
	public void setValue(List<PrintServerConnection> value) {
		connectedServers.clear();
		if (value != null)
			connectedServers.addAll(value);
		writeConnectedServers();
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}
}
