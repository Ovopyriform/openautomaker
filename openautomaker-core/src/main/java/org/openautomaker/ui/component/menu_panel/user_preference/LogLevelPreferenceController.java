package org.openautomaker.ui.component.menu_panel.user_preference;

import org.apache.logging.log4j.Level;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.application.LogLevelPreference;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;

import jakarta.inject.Inject;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.SelectionModel;

/**
 *
 */
public class LogLevelPreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<Level> control;

	private final I18N i18n;

	@Inject
	public LogLevelPreferenceController(
			I18N i18n,
			LogLevelPreference logLevelPreference) {

		this.i18n = i18n;
		control = new ComboBox<>();
		control.getStyleClass().add("cmbCleanCombo");
		control.setMinWidth(200);
		control.autosize();
		control.setItems(FXCollections.observableList(logLevelPreference.values()));

		SelectionModel<Level> selectionModel = control.getSelectionModel();

		// Set initial value
		selectionModel.select(logLevelPreference.getValue());

		//Listen for changes
		selectionModel.selectedItemProperty()
				.addListener((ObservableValue<? extends Level> observable, Level oldValue, Level newValue) -> {
					logLevelPreference.setValue(newValue);
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
		return control;
	}

	@Override
	public String getDescription() {
		return i18n.t("preferences.logLevel");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}
}
