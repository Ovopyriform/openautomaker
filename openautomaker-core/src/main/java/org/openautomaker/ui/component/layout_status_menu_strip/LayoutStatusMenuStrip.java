package org.openautomaker.ui.component.layout_status_menu_strip;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedVBox;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;

public class LayoutStatusMenuStrip extends GuicedVBox {

	private LayoutStatusMenuStripController controller = null;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	public LayoutStatusMenuStrip() {
		super();
		init();
	}

	private void init() {
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("LayoutStatusMenuStrip.fxml"));
		fxmlLoader.setRoot(this);
		try {
			fxmlLoader.load();
			controller = fxmlLoader.getController();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
}
