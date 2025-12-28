package org.openautomaker.ui.component.menu_panel.extras;

import org.openautomaker.ui.component.menu_panel.MenuPanelController;

import celtech.coreUI.controllers.panels.PreferencesInnerPanelController;
import celtech.coreUI.controllers.panels.userpreferences.Preferences;
import jakarta.inject.Inject;

//TODO: Look at this binding of FXML to classes
public class ExtrasMenuPanelController extends MenuPanelController {

	//	public ExtrasMenuPanelController() {
	//		paneli18Name = "extrasMenu.title";
	//	}

	private final HeadEEPROMController headEEPROMController;
	private final RootScannerPanelController rootScannerPanelController;

	private final Preferences preferences;

	@Inject
	protected ExtrasMenuPanelController(
			HeadEEPROMController headEEPROMController,
			RootScannerPanelController rootScannerPanelController,
			Preferences preferences) {

		super();

		this.preferences = preferences;

		paneli18Name = "extrasMenu.title";
		this.headEEPROMController = headEEPROMController;
		this.rootScannerPanelController = rootScannerPanelController;
	}

	/**
	 * Define the inner panels to be offered in the main menu. For the future this is configuration information that could be e.g. stored in XML or in a plugin.
	 */
	@Override
	protected void setupInnerPanels() {
		loadInnerPanel("HeadEEPROMPanel.fxml");

		//UserPreferences userPreferences = Lookup.getUserPreferences();
		loadInnerPanel("PreferencesPanel.fxml",
				// Looks like this needs a factory.
				new PreferencesInnerPanelController("preferences.environment", preferences.createEnvironmentPreferences()));
		loadInnerPanel("PreferencesPanel.fxml",
				new PreferencesInnerPanelController("preferences.printing",
						preferences.createPrintingPreferences()));

		// TODO: Check why this is commented
		//        loadInnerPanel(
		//                ApplicationConfiguration.fxmlPanelResourcePath + "preferencesPanel.fxml",
		//                new PreferencesInnerPanelController("preferences.timelapse",
		//                        Preferences.createTimelapsePreferences(userPreferences)));

		loadInnerPanel("RootScannerPanel.fxml");

		loadInnerPanel("MaintenanceInsetPanel.fxml");

		//TODO: These should all be injected
		loadInnerPanel("PreferencesPanel.fxml",
				new PreferencesInnerPanelController("preferences.advanced",
						preferences.createAdvancedPreferences()));
		loadInnerPanel("PreferencesPanel.fxml",
				new PreferencesInnerPanelController("preferences.customPrinter",
						preferences.createCustomPrinterPreferences()));
	}
}
