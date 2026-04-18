package org.openautomaker.ui.component.graphic_tab;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.control.Tab;

public class GraphicTab extends Tab {
	private static final Logger LOGGER = LogManager.getLogger();

	private final StringProperty fxmlIconName = new SimpleStringProperty("");
	private final StringProperty fxmlSelectedIconName = new SimpleStringProperty("");

	private final ChangeListener<Boolean> selectedTabChangeListener = (observable, oldValue, selected) -> {
		if (selected) {
			loadFXMLIcon(fxmlSelectedIconName.get());
		}
		else {
			loadFXMLIcon(fxmlIconName.get());
		}
	};

	@Inject
	FXMLLoaderFactory fxmlLoaderFactory;

	public GraphicTab() {
		GuiceContext.inject(this);
		selectedProperty().addListener(selectedTabChangeListener);
	}

	//TODO: Consider blank constructor for builder
	//	public GraphicTab() {
	//		selectedProperty().addListener(selectedTabChangeListener);
	//		fxmlLoaderFactory = null;
	//	}

	public GraphicTab(String fxmlIconName) {
		this();
		setFxmlIconName(fxmlIconName);
	}

	public String getFxmlIconName() {
		return this.fxmlIconName.get();
	}

	public void setFxmlIconName(String fxmlFileName) {
		this.fxmlIconName.set(fxmlFileName);
		loadFXMLIcon(this.fxmlIconName.get());
	}

	public String getFxmlSelectedIconName() {
		return this.fxmlSelectedIconName.get();
	}

	public void setFxmlSelectedIconName(String fxmlSelectedFileName) {
		this.fxmlSelectedIconName.set(fxmlSelectedFileName);
	}

	//TODO, this seems like a silly way to do this.  Just have an icon component which takes a name as a param and do it all in markup.
	private void loadFXMLIcon(String fxmlIconName) {
		if (fxmlIconName.equalsIgnoreCase(""))
			return;

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("tabs/" + fxmlIconName + ".fxml"));

		try {
			Group graphicGroup = fxmlLoader.load();
			setGraphic(graphicGroup);
		}
		catch (IOException ex) {
			LOGGER.error("Could not load FXML from file: " + fxmlIconName + ".fxml", ex);
		}
	}
}
