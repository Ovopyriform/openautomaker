package org.openautomaker.base.comms.print_server;

import static org.openautomaker.base.comms.print_server.PrintServerConnection.ServerStatus.CONNECTED;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.inject.comms.PrintServerConnectionFactory;

import celtech.roboxbase.comms.RemoteServerDetector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

@Singleton
public class PrintServerConnectionManager {

	private class PrintServerScannerService extends Service<Void> {

		// Proxies to the containing class
		private final RemoteServerDetector remoteServerDetector = PrintServerConnectionManager.this.remoteServerDetector;
		private final List<PrintServerConnection> knownServers = PrintServerConnectionManager.this.knownServers;

		public PrintServerScannerService() {
			super();
		}

		/**
		 * Proxy to the container class
		 * 
		 * @param printServerConnection
		 */
		private boolean isConnected(PrintServerConnection printServerConnection) {
			return PrintServerConnectionManager.this.isConnected(printServerConnection);
		}

		/**
		 * Proxy to the container class
		 * 
		 * @param printServerConnection
		 */
		private void disconnect(PrintServerConnection printServerConnection) {
			PrintServerConnectionManager.this.disconnect(printServerConnection);
		}

		@Override
		protected Task<Void> createTask() {
			return new Task<>() {

				@Override
				protected Void call() throws Exception {
					while (!isCancelled()) {
						List<PrintServerConnection> discoveredServers = remoteServerDetector.searchForServers();

						//Tidy up.  Spin through the known servers, remove if not detected.
						//Seems like this should be a separate service per server?
						knownServers.forEach((printServerConnection) -> {

							//No need to do anything we know about it and it's still there.
							if (discoveredServers.contains(printServerConnection)) {
								discoveredServers.remove(printServerConnection);
								return;
							}

							// If it was manually added, ignore this one.
							if (!printServerConnection.isDiscoveredConnection())
								return;

							// Haven't timed out the polling yet, wait until polling is exhausted
							if (printServerConnection.incrementPollCount())
								return;

							// Remove from known servers and disconnect if connected.
							knownServers.remove(printServerConnection);
							//Discovery and connection management should be different things.
							//Connected servers should have their own service managing them.
							if (isConnected(printServerConnection))
								disconnect(printServerConnection);
						});

						//At this point detectedServers only contains newly discovered servers
						knownServers.addAll(discoveredServers);

						// Sleep for 1 second.  Perhaps make this increment over time?
						// TODO: Look at starting the service when the UI is visible and stopping when it's not.  Otherwise just noise.
						// Connection threads/services would help with this being the persistent connections to the servers.
						try {
							Thread.sleep(1000);
						}
						catch (InterruptedException ex) {
							LOGGER.debug("RootScanner Thread interrupted");
						}
					}

					return null;
				}
			};
		}
	}

	private static final Logger LOGGER = LogManager.getLogger();

	private final ObservableList<PrintServerConnection> knownServers;
	private final ObservableMap<InetAddress, PrintServerConnection> connectedServers;

	private final ConnectedServersPreference connectedServersPreference;
	private final PrintServerConnectionFactory printServerConnectionFactory;

	//private final ObservableMap<InetAddress, PrintServerConnection> currentServers = FXCollections.observableHashMap();

	private final RemoteServerDetector remoteServerDetector;

	private final PrintServerScannerService printServerScannerService;

	@Inject
	public PrintServerConnectionManager(
			ConnectedServersPreference connectedServersPreference,
			PrintServerConnectionFactory printServerConnectionFactory,
			RemoteServerDetector remoteServerDetector) {

		this.connectedServersPreference = connectedServersPreference;
		this.printServerConnectionFactory = printServerConnectionFactory;
		this.remoteServerDetector = remoteServerDetector;

		//Wrapping a Concurrent Hash Map as these are modified on the detector thread.
		// Probably don't need to do this but belt/braces.
		this.connectedServers = FXCollections.observableMap(new ConcurrentHashMap<>());
		this.knownServers = FXCollections.observableList(new CopyOnWriteArrayList<>());

		//Try to reconnect all the saved printers or remove
		connectedServersPreference.getValue().forEach((printServerConnection) -> {

			//This should be handed off to a connection management service.
			if (!printServerConnection.whoAreYou() && printServerConnection.maxPollCountExceeded()) {
				disconnect(printServerConnection);
				return;
			}

			connect(printServerConnection);
		});

		connectedServersPreference.setValue(new ArrayList<>(connectedServers.values()));
		
		//Create and start
		printServerScannerService = new PrintServerScannerService();
		startScannerService();
	}

	public void startScannerService() {
		printServerScannerService.start();
	}

	public void restartScannerService() {
		printServerScannerService.restart();
	}

	public void stopScannerService() {
		printServerScannerService.cancel();
	}

	public Service<Void> getScannerService() {
		return printServerScannerService;
	}

	public PrintServerConnection findKnownServerConnection(InetAddress inetAddress) {
		return knownServers.stream()
				.filter(printServerConnection -> printServerConnection.getAddress().equals(inetAddress))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Manually create a connection to a print server and, check it exists and add it to the known servers
	 * 
	 * @param inetAddress - Address of the new print server
	 * @return An existing or new connection,<br/>
	 *         null if no connection can be made.
	 * @throws UnknownHostException if the host is not found
	 */
	public PrintServerConnection createManualConnection(InetAddress inetAddress) throws UnknownHostException {

		PrintServerConnection knownServer = findKnownServerConnection(inetAddress);

		if (knownServer != null)
			return knownServer;

		knownServer = printServerConnectionFactory.create(inetAddress);

		if (!knownServer.whoAreYou())
			throw new UnknownHostException();

		knownServer.setDiscoveredConnection(false);
		connect(knownServer);
		return knownServer;
	}

	/**
	 * Remove a manually created server connection
	 * 
	 * @param inetAddress - Address of the server
	 */
	public void removeManualConnection(InetAddress inetAddress) throws UnknownHostException {

		PrintServerConnection knownServer = findKnownServerConnection(inetAddress);

		//Exit if it doesn't exist or it's a discovered connection
		if (knownServer == null || knownServer.isDiscoveredConnection())
			throw new UnknownHostException();

		knownServers.remove(knownServer);
	}

	public ObservableList<PrintServerConnection> getKnownServers() {
		return knownServers;
	}

	public ObservableMap<InetAddress, PrintServerConnection> getConnectedServers() {
		return connectedServers;
	}

	public boolean isConnected(PrintServerConnection printServer) {
		return connectedServersPreference.contains(printServer) && CONNECTED.equals(printServer.getServerStatus());
	}

	public void connect(PrintServerConnection printServer) {
		// Already connected
		if (isConnected(printServer))
			return;

		try {
			printServer.connect();

			if (findKnownServerConnection(printServer.getAddress()) == null)
				knownServers.add(printServer);
			
			connectedServersPreference.put(printServer);
		}
		catch (IOException e) {
			LOGGER.error(e.toString(), e);
		}
	}

	public void disconnect(PrintServerConnection printServer) {
		connectedServersPreference.remove(printServer);
		printServer.disconnect();
	}
}
