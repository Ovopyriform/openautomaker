package org.openautomaker.ui.component.graphic_button;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedButton;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;

public class GraphicButton extends GuicedButton {

	private final StringProperty fxmlFileNameProp = new SimpleStringProperty("");
	private final StringProperty styleClassOverride = new SimpleStringProperty("");

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	public GraphicButton() {
		init();
	}

	public GraphicButton(String fxmlFileName) {
		fxmlFileNameProp.set(fxmlFileName);
		init();
	}

	private void init() {
		loadFXML();
		getStyleClass().add("graphic-button");
		setPickOnBounds(false);
	}

	public String getFxmlFileName() {
		return fxmlFileNameProp.get();
	}

	public void setFxmlFileName(String fxmlFileName) {
		this.fxmlFileNameProp.set(fxmlFileName);
		loadFXML();
	}

	public StringProperty getFxmlFileNameProperty() {
		return fxmlFileNameProp;
	}

	// Attribute styleClassOverride
	public String getStyleClassOverride() {
		return styleClassOverride.get();
	}

	public void setStyleClassOverride(String styleClassOverride) {
		this.styleClassOverride.set(styleClassOverride);
		getStyleClass().clear();
		getStyleClass().add(styleClassOverride);
	}

	public StringProperty getStyleClassOverrideProperty() {
		return styleClassOverride;
	}

	private void loadFXML() throws RuntimeException {
		String fxmlFileName = fxmlFileNameProp.get();
		if ("".equals(fxmlFileName))
			return;

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource(fxmlFileName + ".fxml"));
		fxmlLoader.setController(this);
		fxmlLoader.setRoot(this);

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
}
