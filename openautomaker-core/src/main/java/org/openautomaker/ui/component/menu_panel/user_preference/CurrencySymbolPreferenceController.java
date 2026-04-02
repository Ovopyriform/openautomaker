package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.environment.CurrencySymbol;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.l10n.CurrencySymbolPreference;
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
public class CurrencySymbolPreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<CurrencySymbol> control;
	private final I18N i18n;

	private class CurrencySymbolListCell extends ListCell<CurrencySymbol> {
		@Override
		protected void updateItem(CurrencySymbol symbol, boolean empty) {
			super.updateItem(symbol, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
			}
			else {
				setText(symbol.getDisplayString());
			}
		}
	}

	@Inject
	public CurrencySymbolPreferenceController(
			I18N i18n,
			CurrencySymbolPreference currencySymbolPreference) {

		this.i18n = i18n;

		control = new ComboBox<>();
		control.getStyleClass().add("cmbCleanCombo");
		control.setMinWidth(200);
		control.autosize();

		control.setItems(FXCollections.observableList(currencySymbolPreference.values()));

		// Setup display
		Callback<ListView<CurrencySymbol>, ListCell<CurrencySymbol>> cellFactory = (listView) -> new CurrencySymbolListCell();
		control.setCellFactory(cellFactory);
		control.setButtonCell(cellFactory.call(null));
		
		SelectionModel<CurrencySymbol> selectionModel = control.getSelectionModel();

		//Set initial value
		selectionModel.select(currencySymbolPreference.getValue());

		//Listen for changes
		selectionModel.selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			currencySymbolPreference.setValue(newValue);
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
		return i18n.t("preferences.currencySymbol");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}
}
