package org.openautomaker.ui.component.controls;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.MachineType;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.application.HostServices;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.scene.control.Hyperlink;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

public class HyperlinkedLabel extends TextFlow {

	@Inject
	private OpenAutomakerEnv environment;

	//@Inject
	//private NativeProperties nativeProperties;

	@Inject
	HostServices hostServices;

	private static final Logger LOGGER = LogManager.getLogger();

	private StringProperty text = new SimpleStringProperty("");
	private static final Pattern hyperlinkPattern = Pattern.compile("\\<a href=\"([^\"]+)\">([^<]+)</a>");
	private Map<String, URI> hyperlinkMap = new HashMap<>();

	public HyperlinkedLabel() {
		super();
		GuiceContext.get().injectMembers(this);
	}

	public void replaceText(String newText) {
		getChildren().clear();
		hyperlinkMap.clear();
		text.set("");
		setTextAlignment(TextAlignment.CENTER);

		Matcher matcher = hyperlinkPattern.matcher(newText);
		int matches = 0;
		int currentIndex = 0;

		//TODO: Revisit this
		while (matcher.find(currentIndex)) {
			matches++;
			if (matcher.start() > 0) {
				String textPortion = newText.substring(currentIndex, matcher.start());
				addPlainText(textPortion);
				currentIndex = matcher.end();
			}
			if (matcher.groupCount() == 2) {
				String linkURLString = matcher.group(1);
				String linkText = matcher.group(2);
				try {
					MachineType machineType = environment.getMachineType();

					URI linkURI = new URI(linkURLString);
					hyperlinkMap.put(linkText, linkURI);
					Hyperlink hyperlink = new Hyperlink();
					hyperlink.setOnAction((ActionEvent event) -> {
						Hyperlink newhyperlink = (Hyperlink) event.getSource();

						final String clickedLinkText = newhyperlink == null ? "" : newhyperlink.getText();
						if (hyperlinkMap.containsKey(clickedLinkText)) {
							hostServices.showDocument(hyperlinkMap.get(clickedLinkText).toString());
						}

					});
					hyperlink.setText(linkText);
					getChildren().add(hyperlink);
					currentIndex = matcher.end();
				}
				catch (URISyntaxException ex) {
					System.err.println("Error attempting to create UI hyperlink from "
							+ linkURLString);
				}
			}
			else {
				System.err.println("Error rendering dialog text: " + newText);
			}
		}

		if (matches == 0) {
			//We didn't have any hyperlinks here
			currentIndex = newText.length();
			addPlainText(newText);
		}

		if (currentIndex < newText.length()) {
			// Add any final text after the hyperlinks
			String textPortion = newText.substring(currentIndex);
			addPlainText(textPortion);
		}
	}

	private void addPlainText(String textPortion) {
		Text plainText = new Text(textPortion);
		plainText.getStyleClass().add("hyperlink-plaintext");
		getChildren().add(plainText);
		text.set(text.get() + textPortion);
	}

	public String getText() {
		return text.get();
	}

	public void setText(String text) {
		this.text.set(text);
	}

	public StringProperty textProperty() {
		return text;
	}
}
