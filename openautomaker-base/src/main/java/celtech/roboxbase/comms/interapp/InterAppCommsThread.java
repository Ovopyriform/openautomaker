package celtech.roboxbase.comms.interapp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 *
 * @author ianhudson
 */
@Singleton
public class InterAppCommsThread extends Thread {

	private static final Logger LOGGER = LogManager.getLogger();

	private boolean keepRunning = true;
	private ServerSocket initialServerSocket;
	private Socket serverSocket = null;
	private static final ObjectMapper mapper = new ObjectMapper();
	private InterAppCommsConsumer commsConsumer = null;

	@Inject
	public InterAppCommsThread() {
		this.setName("InterAppComms");
	}

	@Override
	public void run() {
		while (keepRunning) {
			try {
				serverSocket = initialServerSocket.accept();

				AbstractInterAppRequest interAppRequest = mapper.readValue(serverSocket.getInputStream(), AbstractInterAppRequest.class);
				if (interAppRequest != null) {
					LOGGER.info("Was InterApp data:" + interAppRequest.toString());

					if (commsConsumer != null) {
						commsConsumer.incomingComms(interAppRequest);
					}
				}

			}
			catch (IOException ex) {
				// Get a "SocketException - socket closed" when the thread is terminated.
				if (keepRunning)
					LOGGER.error("Error trying to listen for InterApp comms");
			}
		}
	}

	public InterAppStartupStatus letUsBegin(AbstractInterAppRequest interAppCommsRequest, InterAppCommsConsumer commsConsumer) {
		this.commsConsumer = commsConsumer;

		try {
			initialServerSocket = new ServerSocket(InterAppConfiguration.PORT, 0, InetAddress.getLoopbackAddress());
			this.start();
			return InterAppStartupStatus.STARTED_OK;
		}
		catch (BindException e) {
			LOGGER.info("AutoMaker asked to start but instance is already running.");
			boolean sent = InterAppClient.send(interAppCommsRequest);
			return sent ? InterAppStartupStatus.ALREADY_RUNNING_CONTACT_MADE : InterAppStartupStatus.ALREADY_RUNNING_COULDNT_CONTACT;
		}
		catch (IOException e) {
			LOGGER.error("Unexpected error whilst attempting to check if another app is running");
			return InterAppStartupStatus.OTHER_ERROR;
		}
	}

	public void shutdown() {
		keepRunning = false;
		try {
			initialServerSocket.close();
		}
		catch (IOException ex) {
			LOGGER.error("Error whilst closing inter app comms socket", ex);
		}
	}
}
