package org.openautomaker.ui.component.welcome_panel;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.application.HomePathPreference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.html.HTMLAnchorElement;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import jakarta.inject.Inject;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.web.WebView;

/**
 *
 * @author Ian
 */
public class WelcomeInsetPanelController {

	private static final Logger LOGGER = LogManager.getLogger();

	@FXML
	private WebView textContainer;

	private final ApplicationStatus applicationStatus;
	private final HomePathPreference homePathPreference;

	@Inject
	protected WelcomeInsetPanelController(
			ApplicationStatus applicationStatus,
			HomePathPreference homePathPreference) {

		this.applicationStatus = applicationStatus;
		this.homePathPreference = homePathPreference;

	}

	@FXML
	void backToStatusAction(ActionEvent event) {
		applicationStatus.setMode(ApplicationMode.STATUS);
	}

	public void initialize() {
		String protocol = "file:///";
		String basePath = homePathPreference.getAppValue().resolve("README").resolve("README_AutoMaker.html").toString();
		basePath = basePath.replace("\\", "/");
		String urlEncodedPath = "";

		try {
			urlEncodedPath = URLEncoder.encode(urlEncodedPath, "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			LOGGER.error("Error encoding readme URL", ex);
		}

		final String normalisedURL = protocol + basePath;

		textContainer.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != Worker.State.SUCCEEDED)
				return;

			NodeList nodeList = textContainer.getEngine().getDocument().getElementsByTagName("a");

			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				EventTarget eventTarget = (EventTarget) node;

				eventTarget.addEventListener("click", (evt) -> {
					EventTarget target = evt.getCurrentTarget();

					HTMLAnchorElement anchorElement = (HTMLAnchorElement) target;

					String href = anchorElement.getHref();

					//If we're going outside of the readme file then launch in the native browser
					String decodedHref = null;

					try {
						decodedHref = URLDecoder.decode(href, "UTF-8");
					}
					catch (UnsupportedEncodingException ex) {
						LOGGER.error("Failed to decode README href", ex);
					}

					if (decodedHref != null && decodedHref.startsWith(normalisedURL))
						return;

					evt.preventDefault();

					try {
						URI outboundURI = new URI(href);

						if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
							Desktop.getDesktop().browse(outboundURI);

					}
					catch (URISyntaxException | IOException ex) {
						LOGGER.error("Unable to generate URI from " + href);
					}

				}, false);

			}
		});

		textContainer.getEngine().load(normalisedURL);

	}
}
