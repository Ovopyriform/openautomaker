package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.environment.I18N;
import org.openautomaker.environment.PrinterType;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterTypePreference;
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
 */
public class CustomPrinterTypePreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<PrinterType> control;

	/**
	 * Custom ListCell implementation to display the correct text
	 */
	private class VirtualPrinterTypeListCell extends ListCell<PrinterType> {
		@Override
		protected void updateItem(PrinterType item, boolean empty) {
			super.updateItem(item, empty);
			if (!empty)
				setText(item.getDisplayName());
		}

	}

	private final I18N i18n;

	@Inject
	public CustomPrinterTypePreferenceController(
			I18N i18n,
			VirtualPrinterTypePreference virtualPrinterTypePreference) {

		this.i18n = i18n;

		control = new ComboBox<>();
		control.getStyleClass().add("cmbCleanCombo");
		control.setMinWidth(200);
		control.autosize();

		control.setItems(FXCollections.observableList(virtualPrinterTypePreference.values()));

		// Setup display
		Callback<ListView<PrinterType>, ListCell<PrinterType>> cellFactory = (listView) -> new VirtualPrinterTypeListCell();
		control.setButtonCell(cellFactory.call(null));
		control.setCellFactory(cellFactory);

		SelectionModel<PrinterType> selectionModel = control.getSelectionModel();

		// Set initial value
		selectionModel.select(virtualPrinterTypePreference.getValue());

		// Listen for changes
		selectionModel.selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> {
					virtualPrinterTypePreference.setValue(newValue);
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
		return i18n.t("preferences.printerType");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}

}
