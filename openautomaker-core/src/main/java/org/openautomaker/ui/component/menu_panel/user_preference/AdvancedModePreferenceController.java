package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController.Preference;

import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;

public class AdvancedModePreferenceController implements Preference {

	private final CheckBox fControl;

	private final I18N i18n;

	@Inject
	protected AdvancedModePreferenceController(
			I18N i18n,
			AdvancedModePreference advancedModePreference,
			SystemNotificationManager systemNotificationManager) {

		this.i18n = i18n;

		fControl = new CheckBox();
		fControl.setPrefWidth(150);
		fControl.setMinWidth(fControl.getPrefWidth());

		BooleanProperty booleanProperty = fControl.selectedProperty();
		booleanProperty.setValue(advancedModePreference.getValue());

		// Confirm if they user wants to go to advanced mode.
		booleanProperty.addListener((observable, oldValue, newValue) -> {
			if (!newValue) {
				advancedModePreference.setValue(newValue);
				return;
			}

			// Ask the user whether they really want to do this..
			boolean confirmAdvancedMode = systemNotificationManager.confirmAdvancedMode();

			// If we're switching, set the preference
			if (confirmAdvancedMode)
				advancedModePreference.setValue(confirmAdvancedMode);

			// If we're cancelling, set the control back to false
			if (!confirmAdvancedMode)
				booleanProperty.setValue(confirmAdvancedMode);
		});
	}

	@Override
	public void updateValueFromControl() {
	}

	@Override
	public void populateControlWithCurrentValue() {
	}

	@Override
	public Control getControl() {
		return fControl;
	}

	@Override
	public String getDescription() {
		return i18n.t("preferences.advancedMode");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		fControl.disableProperty().unbind();
		fControl.disableProperty().bind(disableProperty);
	}

	public BooleanProperty getSelectedProperty() {
		return fControl.selectedProperty();
	}
}
