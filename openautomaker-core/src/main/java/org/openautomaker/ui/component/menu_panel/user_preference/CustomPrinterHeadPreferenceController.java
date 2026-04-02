package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterHeadPreference;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import jakarta.inject.Inject;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionModel;
import javafx.util.Callback;

/**
 *
 * @author George Salter
 */
public class CustomPrinterHeadPreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<String> control;


	private final BiMap<String, String> fHeadDisplayNameMap;

	/**
	 * Implementation of ListCell to control displayed text
	 */
	private class CustomerPrinterHeadListCell extends ListCell<String> {
		@Override
		protected void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			if (!empty)
				if (fHeadDisplayNameMap.containsKey(item))
					setText(fHeadDisplayNameMap.get(item));
				else
					setText(item);
		}
	}

	private final I18N i18n;

	@Inject
	public CustomPrinterHeadPreferenceController(
			I18N i18n,
			VirtualPrinterHeadPreference virtualPrinterHeadPreference,
			HeadContainer headContainer) {

		this.i18n = i18n;

		//TODO: These shouldn't be defined in the combo box.  Should be a separate head loader module which sort out all this stuff.
		//TODO: Known heads.  Shouldn't be defined here
		fHeadDisplayNameMap = HashBiMap.create();
		fHeadDisplayNameMap.put("RBX01-SM", "QuickFill\u2122");
		fHeadDisplayNameMap.put("RBX01-S2", "QuickFill\u2122 v2");
		fHeadDisplayNameMap.put("RBX01-DM", "DualMaterial\u2122");
		fHeadDisplayNameMap.put("RBXDV-S1", "SingleX\u2122 Experimental\u2122");
		fHeadDisplayNameMap.put("RBXDV-S3", "SingleLite\u2122");

		control = new ComboBox<>();
		control.getStyleClass().add("cmbCleanCombo");
		control.setMinWidth(200);
		control.autosize();

		headContainer.getCompleteHeadList().forEach(headFile -> control.getItems().add(headFile.getTypeCode()));

		// Setup display
		Callback<ListView<String>, ListCell<String>> cellFactory = (listView) -> new CustomerPrinterHeadListCell();
		control.setButtonCell(cellFactory.call(null));
		control.setCellFactory(cellFactory);

		SelectionModel<String> selectionModel = control.getSelectionModel();

		// Set up initial value
		selectionModel.select(virtualPrinterHeadPreference.getValue());

		// Listen for changes
		selectionModel.selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> {
					virtualPrinterHeadPreference.setValue(newValue);
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
		return i18n.t("preferences.printerHead");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		control.disableProperty().unbind();
		control.disableProperty().bind(disableProperty);
	}

}
