package celtech.WebEngineFix;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.http.HttpRequest;
import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.application.NamePreference;
import org.openautomaker.ui.component.menu_panel.extras.RootScannerPanelController;

import celtech.roboxbase.comms.remote.StringToBase64Encoder;
import sun.net.www.protocol.http.HttpURLConnection;

/**
 *
 * @author Ian
 */
//TODO: this has to be completely refactored ro remove the URL stuff which is no longer supported.
public class AMURLHandler extends URLStreamHandler {

	private static final Logger LOGGER = LogManager.getLogger();

	private final NamePreference namePreference;

	public AMURLHandler(NamePreference namePreference) {
		super();
		this.namePreference = namePreference;
	}

	@Override
	protected URLConnection openConnection(URL url) throws IOException {
		HttpURLConnection con = new HttpURLConnection(url, null);

		HttpRequest request = null;

		try {
			request = HttpRequest.newBuilder(url.toURI())
					.GET()
					.header("User-Agent", namePreference.getValue())
					.header("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + RootScannerPanelController.pinForCurrentServer))
					.timeout(Duration.of(10, SECONDS))
					.build();
		}
		catch (URISyntaxException e) {
			LOGGER.error("URL -> URI Parse Exception.  URL: " + url.toString());
		}

		con.setRequestProperty("User-Agent", namePreference.getValue());
		con.setRequestProperty("Authorization", "Basic " + StringToBase64Encoder.encode("root:" + RootScannerPanelController.pinForCurrentServer));
		return con;
	}

}
