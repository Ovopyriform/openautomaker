package org.openautomaker.ui.component.material;

import static javafx.scene.layout.Region.USE_PREF_SIZE;

import org.openautomaker.base.configuration.Filament;

import javafx.scene.control.CustomMenuItem;

/**
 *
 * @author Ian
 */
public class FilamentOnReelMenuItem extends CustomMenuItem {
	public FilamentOnReelMenuItem(String title, Filament filament, double prefWidth) {
		super();
		FilamentOnReelDisplay filamentOnReelDisplay = new FilamentOnReelDisplay(title, filament);
		filamentOnReelDisplay.setPrefWidth(prefWidth);
		filamentOnReelDisplay.setMaxWidth(USE_PREF_SIZE);
		setContent(filamentOnReelDisplay);
		setHideOnClick(false);
	}
}
