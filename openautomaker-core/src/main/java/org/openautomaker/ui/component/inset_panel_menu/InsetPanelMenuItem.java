package org.openautomaker.ui.component.inset_panel_menu;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedToggleButton;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;

public class InsetPanelMenuItem extends GuicedToggleButton {

	@Inject
	public FXMLLoaderFactory fxmlLoaderFactory;

	public InsetPanelMenuItem() {


		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("insetPanelMenuItem.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		fxmlLoader.setClassLoader(this.getClass().getClassLoader());

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		getStyleClass().add("inset-panel-menu-item");
	}

	public void setTitle(String title) {
		setText(title);
	}
}
