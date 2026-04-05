package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;

import jakarta.inject.Inject;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 *
 * @author Ian
 */
public class PasswordPreference implements PreferencesInnerPanelController.Preference {

	private final TextField control;
	private final StringProperty stringProperty;
	private final String caption;

	@Inject
	private I18N i18n;

	public PasswordPreference(StringProperty stringProperty, String caption) {

		//TODO: Seems incorrect to use GuiceContext here.  Look into
		GuiceContext.get().injectMembers(this);

		this.stringProperty = stringProperty;
		this.caption = caption;

		control = new PasswordField();
		control.setPrefWidth(250);
		control.setMinWidth(control.getPrefWidth());
		control.setMaxWidth(control.getPrefWidth());
		control.textProperty().addListener(
				(ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
					updateValueFromControl();
				});
		stringProperty.addListener(
				(ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
					control.setText(newValue);
				});
	}

	@Override
	public void updateValueFromControl() {
		stringProperty.set(control.getText());

		// User Preferences controls whether the property can be set - read back just in case our selection was overridden
		control.setText(stringProperty.get());
	}

	@Override
	public void populateControlWithCurrentValue() {
		control.setText(stringProperty.get());
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
