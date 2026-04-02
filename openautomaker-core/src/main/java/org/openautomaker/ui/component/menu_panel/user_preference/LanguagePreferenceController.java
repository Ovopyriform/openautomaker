package org.openautomaker.ui.component.menu_panel.user_preference;

import java.util.Locale;

import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.l10n.LocalePreference;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;

import jakarta.inject.Inject;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.util.Callback;

/**
 *
 * @author Ian
 */
public class LanguagePreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<Locale> control;



	/**
	 * Control how locals are displayed
	 */
	private class LocaleListCell extends ListCell<Locale> {
		@Override
		protected void updateItem(Locale item, boolean empty) {
			super.updateItem(item, empty);
			if (!empty)
				setText(item.getDisplayName(localePreference.getValue()));
		}
	}

	private final I18N i18n;
	private final LocalePreference localePreference;

	@Inject
	public LanguagePreferenceController(I18N i18n, LocalePreference localePreference) {

		this.i18n = i18n;
		this.localePreference = localePreference;

		control = new ComboBox<>();
		control.getStyleClass().add("cmbCleanCombo");
		control.setMinWidth(200);
		control.autosize();

		control.setItems(FXCollections.observableList(localePreference.values()));

		// Set up display
		Callback<ListView<Locale>, ListCell<Locale>> cellFactory = (listView) -> new LocaleListCell();
		control.setButtonCell(cellFactory.call(null));
		control.setCellFactory(cellFactory);

		SelectionModel<Locale> selectionModel = control.getSelectionModel();

		// Set initial value
		selectionModel.select(localePreference.getValue());

		// Listen for changes
		selectionModel.selectedItemProperty()
				.addListener((ObservableValue<? extends Locale> observable, Locale oldValue, Locale newValue) -> {
					localePreference.setValue(newValue);
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
		return i18n.t("preferences.language");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}
}
