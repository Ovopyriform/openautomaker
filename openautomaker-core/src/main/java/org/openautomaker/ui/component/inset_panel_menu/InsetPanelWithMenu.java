package org.openautomaker.ui.component.inset_panel_menu;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedHBox;
import javafx.scene.layout.HBox;

import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

/**
 *
 * @author Ian
 */
public class InsetPanelWithMenu extends GuicedHBox {

	@FXML
	private InsetPanelMenu menu;

	@FXML
	private HBox contentContainer;

	@Inject
	public FXMLLoaderFactory fxmlLoaderFactory;

	public InsetPanelWithMenu() {

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("insetPanelWithMenu.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		fxmlLoader.setClassLoader(this.getClass().getClassLoader());

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		getStyleClass().add("inset-panel-with-menu");

		contentContainer.getStyleClass().add("blue-inset-panel");
	}

	public void setMenuTitle(String title) {
		menu.setTitle(title);
	}

	public void addMenuItem(InsetPanelMenuItem menuItem, Node content) {
		menu.addMenuItem(menuItem);
	}
}
