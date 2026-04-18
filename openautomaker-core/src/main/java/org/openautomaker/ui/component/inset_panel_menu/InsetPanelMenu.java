package org.openautomaker.ui.component.inset_panel_menu;

import java.io.IOException;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedHBox;

import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 *
 * @author Ian
 */
public class InsetPanelMenu extends GuicedHBox {

	@FXML
	private Text menuTitle;

	@FXML
	private VBox menuItemContainer;

	private ToggleGroup buttonGroup = new ToggleGroup();

	@Inject
	public FXMLLoaderFactory fxmlLoaderFactory;

	public InsetPanelMenu() {
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("insetPanelMenu.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		fxmlLoader.setClassLoader(this.getClass().getClassLoader());

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	public void setTitle(String title) {
		menuTitle.setText(title);
	}

	public void addMenuItem(InsetPanelMenuItem menuItem) {
		menuItemContainer.getChildren().add(menuItem);

		menuItem.setToggleGroup(buttonGroup);
	}
}
