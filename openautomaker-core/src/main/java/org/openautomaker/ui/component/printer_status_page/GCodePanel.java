package org.openautomaker.ui.component.printer_status_page;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

@Deprecated
public class GCodePanel extends VBox {

	private GCodePanelController controller = null;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	//TODO: So what's this about?  Seems an abstract for an fxml loader which is not needed.
	public GCodePanel() {
		super();
		GuiceContext.get().injectMembers(this);
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
