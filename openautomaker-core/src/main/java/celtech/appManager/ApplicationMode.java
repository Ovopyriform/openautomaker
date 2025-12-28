package celtech.appManager;

import org.openautomaker.ui.component.about_panel.AboutPanelController;
import org.openautomaker.ui.component.calibration_panel.CalibrationInsetPanelController;
import org.openautomaker.ui.component.load_model_panel.LoadModelInsetPanelController;
import org.openautomaker.ui.component.menu_panel.extras.ExtrasMenuPanelController;
import org.openautomaker.ui.component.menu_panel.extras.MaintenanceInsetPanelController;
import org.openautomaker.ui.component.menu_panel.library.LibraryMenuPanelController;
import org.openautomaker.ui.component.purge_panel.PurgeInsetPanelController;
import org.openautomaker.ui.component.registration_panel.RegistrationInsetPanelController;
import org.openautomaker.ui.component.welcome_panel.WelcomeInsetPanelController;

import celtech.configuration.ApplicationConfiguration;

/**
 *
 * @author ianhudson
 */
//TODO: Refactor this.  Hold the FXML file names here and use the file to load the controller
public enum ApplicationMode {

	WELCOME("Welcome", WelcomeInsetPanelController.class),
	CALIBRATION_CHOICE("Calibration", CalibrationInsetPanelController.class),
	REGISTRATION("registration", RegistrationInsetPanelController.class),
	PURGE("purge", PurgeInsetPanelController.class),
	MAINTENANCE("Maintenance", MaintenanceInsetPanelController.class),
	ABOUT("about", AboutPanelController.class),
	SYSTEM_INFORMATION("systemInformation", null),
	EXTRAS_MENU("extrasMenu", ExtrasMenuPanelController.class),
	//TODO printer status has to be last otherwise the temperature graph doesn't work!! Fix in DisplayManager
	STATUS(null, null),
	/**
	 *
	 */
	LAYOUT(null, null),
	ADD_MODEL("loadModel", LoadModelInsetPanelController.class),
	//MY_MINI_FACTORY("myMiniFactoryLoader", MyMiniFactoryLoaderController.class),
	/**
	 *
	 */
	SETTINGS(null, null),
	LIBRARY("extrasMenu", LibraryMenuPanelController.class);

	//    NEWS("news", NewsController.class);

	private final String insetPanelFXMLPrefix;
	private final Class controllerClass;

	private ApplicationMode(String insetPanelFXMLPrefix, Class<?> controllerClass) {
		this.insetPanelFXMLPrefix = insetPanelFXMLPrefix;
		this.controllerClass = controllerClass;
	}

	/**
	 *
	 * @return
	 */
	// This doesn't make any sense.  Store the filei nthe enum and put the controller in the file.
	public String getInsetPanelFXMLName() {
		return ApplicationConfiguration.fxmlPanelResourcePath + insetPanelFXMLPrefix + "InsetPanel" + ".fxml";
	}

	public Class getControllerClass() {
		return controllerClass;
	}
}
