package celtech.appManager;

import java.net.URL;

public enum ApplicationMode {

	WELCOME("/org/openautomaker/ui/component/welcome_panel/WelcomeInsetPanel.fxml"),
	CALIBRATION_CHOICE("/org/openautomaker/ui/component/calibration_panel/CalibrationInsetPanel.fxml"),
	REGISTRATION("/org/openautomaker/ui/component/registration_panel/registrationInsetPanel.fxml"),
	PURGE("/org/openautomaker/ui/component/purge_panel/purgeInsetPanel.fxml"),
	ABOUT("/org/openautomaker/ui/component/about_panel/aboutInsetPanel.fxml"),
	SYSTEM_INFORMATION(null),
	EXTRAS_MENU("/org/openautomaker/ui/component/menu_panel/extras/extrasMenuInsetPanel.fxml"),
	//TODO printer status has to be last otherwise the temperature graph doesn't work!! Fix in DisplayManager
	STATUS(null),
	LAYOUT(null),
	ADD_MODEL("/org/openautomaker/ui/component/load_model_panel/loadModelInsetPanel.fxml"),
	SETTINGS(null),
	LIBRARY("/org/openautomaker/ui/component/menu_panel/library/libraryMenuInsetPanel.fxml");

	private final String fxmlPath;

	private ApplicationMode(String fxmlPath) {
		this.fxmlPath = fxmlPath;
	}

	public URL getInsetPanelFXMLURL() {
		if (fxmlPath == null) return null;
		return ApplicationMode.class.getResource(fxmlPath);
	}
}
