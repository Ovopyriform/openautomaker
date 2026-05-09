package celtech.roboxbase.comms.interapp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class InterAppClient {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private InterAppClient() {
	}

	/**
	 * Send a request to an already-running application instance.
	 *
	 * @return true if the message was delivered successfully
	 */
	public static boolean send(AbstractInterAppRequest request) {
		try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), InterAppConfiguration.PORT)) {
			OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream(), InterAppConfiguration.charSetToUse);
			out.write(MAPPER.writeValueAsString(request));
			out.flush();
			LOGGER.debug("Told sibling about params");
			return true;
		}
		catch (IOException ex) {
			LOGGER.error("IOException when contacting sibling: {}", ex.getMessage());
			return false;
		}
	}
}
