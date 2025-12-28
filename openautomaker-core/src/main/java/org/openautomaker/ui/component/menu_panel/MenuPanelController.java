package org.openautomaker.ui.component.menu_panel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.FXMLLoaderFactory;

import celtech.appManager.ApplicationStatus;
import celtech.coreUI.components.VerticalMenu;
import celtech.coreUI.controllers.panels.ButtonBox;
import jakarta.inject.Inject;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

//TODO: Seems like this could be rolled into MenuInnerPanel with default implementations
public abstract class MenuPanelController {

	private static final Logger LOGGER = LogManager.getLogger();

	@Inject
	private I18N i18n;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	@Inject
	private ApplicationStatus applicationStatus;

	private ButtonBox buttonBox;


	protected MenuPanelController() {

	}

	//Leaving this here at the moment.  Seems it should only contain one ever?
	private List<FXMLLoader> panelLoaders = new ArrayList<>();

	@FXML
	protected VerticalMenu panelMenu;

	@FXML
	private VBox insetNodeContainer;

	@FXML
	private HBox buttonBoxContainer;

	protected String paneli18Name = "";

	private final ObjectProperty<MenuInnerPanel> innerPanelProperty = new SimpleObjectProperty<>(null);

	public void initialize() {
		buttonBox = new ButtonBox(innerPanelProperty);
		//buttonBox.setExtrasMenuInnerPanelProperty(innerPanelProperty);
		buttonBoxContainer.getChildren().add(buttonBox);

		setupInnerPanels();
		buildExtras();

		//        fDisplayManager.getDisplayScalingModeProperty().addListener(new ChangeListener<DisplayManager.DisplayScalingMode>()
		//        {
		//
		//            @Override
		//            public void changed(ObservableValue<? extends DisplayManager.DisplayScalingMode> ov, DisplayManager.DisplayScalingMode t, DisplayManager.DisplayScalingMode t1)
		//            {
		//                switch (t1)
		//                {
		//                    case SHORT:
		//                    case VERY_SHORT:
		//                        Insets shortInsets = new Insets(20, 0, 0, 0);
		//                        insetNodeContainer.setPadding(shortInsets);
		//                        break;
		//                    default:
		//                        Insets normalInsets = new Insets(95, 0, 0, 0);
		//                        insetNodeContainer.setPadding(normalInsets);
		//                        break;
		//                }
		//            }
		//        });
	}

	/**
	 * Define the inner panels to be offered in the main menu. For the future this is configuration information that could be e.g. stored in XML or in a plugin.
	 */
	protected abstract void setupInnerPanels();

	/**
	 * Load the given inner panel.
	 *
	 * @param fxmlLocation
	 * @param controller
	 * @return
	 */
	protected FXMLLoader loadInnerPanel(String fxmlLocation) {
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource(fxmlLocation));

		try {
			fxmlLoader.load();
			panelLoaders.add(fxmlLoader);
			return fxmlLoader;
		}
		catch (IOException ex) {
			LOGGER.error("Unable to load panel: " + fxmlLocation, ex);
		}
		return null;
	}

	protected FXMLLoader loadInnerPanel(String fxmlLocation, MenuInnerPanel controller) {
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource(fxmlLocation));
		fxmlLoader.setController(controller);

		try {
			fxmlLoader.load();
			panelLoaders.add(fxmlLoader);
			return fxmlLoader;
		}
		catch (IOException ex) {
			LOGGER.error("Unable to load panel: " + fxmlLocation, ex);
		}
		return null;
	}

	/**
	 * For each InnerPanel, create a menu item that will open it.
	 */
	private void buildExtras() {
		panelMenu.setTitle(i18n.t(paneli18Name));
		
		panelLoaders.forEach((panelFxmlLoader) -> {
			MenuInnerPanel controller = panelFxmlLoader.getController();
			panelMenu.addItem(controller.getMenuTitle(), () -> {
				openInnerPanel(panelFxmlLoader);
			}, null);
		});
	}

	/**
	 * Open the given inner panel.
	 */
	//TODO: Seems like the panel could be loaded at this point?
	private void openInnerPanel(FXMLLoader panelFxmlLoader) {
		MenuInnerPanel controller = panelFxmlLoader.getController();
		insetNodeContainer.getChildren().clear();
		insetNodeContainer.getChildren().add(panelFxmlLoader.getRoot());
		innerPanelProperty.set(controller);
		controller.panelSelected();
	}

	@FXML
	private void okPressed(ActionEvent event) {
		applicationStatus.returnToLastMode();
	}
}