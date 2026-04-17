package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.environment.I18N;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;

import com.google.inject.assistedinject.Assisted;

import org.openautomaker.ui.component.controls.RestrictedNumberField;
import jakarta.inject.Inject;
import javafx.beans.property.FloatProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;

/**
 *
 * @author Ian
 */
public class FloatingPointPreference implements PreferencesInnerPanelController.Preference {

	private final RestrictedNumberField control;
	private final FloatProperty floatProperty;
	private final String caption;

	private final I18N i18n;

	@Inject
	public FloatingPointPreference(
			I18N i18n,
			@Assisted FloatProperty floatProperty,
			@Assisted("decimalPlaces") int decimalPlaces,
			@Assisted("digits") int digits,
			@Assisted boolean negativeAllowed,
			@Assisted String caption) {

		this.i18n = i18n;

		this.floatProperty = floatProperty;
		this.caption = caption;

		control = new RestrictedNumberField();
		control.setPrefWidth(150);
		control.setMaxWidth(control.getPrefWidth());
		control.setMinWidth(control.getPrefWidth());
		control.setAllowedDecimalPlaces(decimalPlaces);
		control.setAllowNegative(negativeAllowed);
		control.setMaxLength(digits);
		control.valueChangedProperty().addListener((ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) -> {
			updateValueFromControl();
		});
	}

	@Override
	public void updateValueFromControl() {
		floatProperty.set(control.getAsFloat());

		// User Preferences controls whether the property can be set - read back just in case our selection was overridden
		control.setValue(floatProperty.get());
	}

	@Override
	public void populateControlWithCurrentValue() {
		control.setValue(floatProperty.get());
	}

	@Override
	public Control getControl() {
		return control;
	}

	@Override
	public String getDescription() {
		return i18n.t(caption);
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}
}
