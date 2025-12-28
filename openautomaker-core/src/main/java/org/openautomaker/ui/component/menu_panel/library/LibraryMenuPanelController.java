package org.openautomaker.ui.component.menu_panel.library;

import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;
import org.openautomaker.environment.I18N;
import org.openautomaker.ui.component.menu_panel.MenuPanelController;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;

public class LibraryMenuPanelController extends MenuPanelController {

	private FXMLLoader cameraProfilePanelLoader = null;
	private FXMLLoader profileLibraryPanelLoader = null;

	private final I18N i18n;

	@Inject
	protected LibraryMenuPanelController(I18N i18n) {
		super();

		this.i18n = i18n;

		paneli18Name = "libraryMenu.title";
	}

	//TODO: Revisit this.  Could we just load the panels as they're needed?
	@Override
	protected void setupInnerPanels() {
		loadInnerPanel("FilamentLibraryPanel.fxml");
		profileLibraryPanelLoader = loadInnerPanel("ProfileLibraryPanel.fxml");
		cameraProfilePanelLoader = loadInnerPanel("CameraProfilesPanel.fxml");
	}

	public void showAndSelectPrintProfile(RoboxProfile roboxProfile) {
		ProfileLibraryPanelController controller = (ProfileLibraryPanelController) profileLibraryPanelLoader.getController();
		String profileMenuItemName = i18n.t(controller.getMenuTitle());
		panelMenu.selectItemOfName(profileMenuItemName);
		controller.setAndSelectPrintProfile(roboxProfile);
	}

	public void showAndSelectCameraProfile(CameraProfile profile) {
		CameraProfilesPanelController controller = (CameraProfilesPanelController) cameraProfilePanelLoader.getController();
		String cameraProfileMenuItemName = i18n.t(controller.getMenuTitle());
		panelMenu.selectItemOfName(cameraProfileMenuItemName);
		controller.setAndSelectCameraProfile(profile);
	}
}
