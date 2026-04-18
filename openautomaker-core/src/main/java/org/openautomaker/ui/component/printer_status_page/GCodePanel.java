package org.openautomaker.ui.component.printer_status_page;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedVBox;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;

@Deprecated
public class GCodePanel extends GuicedVBox {

	private GCodePanelController controller = null;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	//TODO: So what's this about?  Seems an abstract for an fxml loader which is not needed.
	public GCodePanel() {
		super();
		init();
	}

	private void init() {
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("GCodePanel.fxml"));
		fxmlLoader.setRoot(this);
		try {
			fxmlLoader.load();
			controller = fxmlLoader.getController();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	public GCodePanelController getController() {
		return controller;
	}
}
