package org.openautomaker.ui.component.menu_panel.user_preference;

import org.openautomaker.environment.I18N;
import org.openautomaker.environment.Slicer;
import org.openautomaker.environment.preference.slicer.SlicerPreference;
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
public class SlicerTypePreferenceController implements PreferencesInnerPanelController.Preference {

	private final ComboBox<Slicer> comboBox;


	/**
	 * Custom display for combo box cell
	 */
	private class SlicerListCell extends ListCell<Slicer> {
		@Override
		protected void updateItem(Slicer item, boolean empty) {
			super.updateItem(item, empty);
			if (!empty)
				setText(item.getFriendlyName());
		}
	}

	private final I18N i18n;
	private final SlicerPreference slicerPreference;

	@Inject
	public SlicerTypePreferenceController(
			I18N i18n,
			SlicerPreference slicerPreference) {

		this.i18n = i18n;
		this.slicerPreference = slicerPreference;

		comboBox = new ComboBox<>();
		comboBox.getStyleClass().add("cmbCleanCombo");
		comboBox.setMinWidth(200);
		comboBox.autosize();

		comboBox.setItems(FXCollections.observableList(slicerPreference.values()));

		Callback<ListView<Slicer>, ListCell<Slicer>> cellFactory = (listView) -> new SlicerListCell();
		comboBox.setButtonCell(cellFactory.call(null));
		comboBox.setCellFactory(cellFactory);

		SelectionModel<Slicer> selectionModel = comboBox.getSelectionModel();

		// Set initial value
		selectionModel.select(slicerPreference.getValue());

		//Listen for Changes.
		selectionModel.selectedItemProperty()
				.addListener((ObservableValue<? extends Slicer> observable, Slicer oldValue, Slicer newValue) -> {
					slicerPreference.setValue(newValue);
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
		return comboBox;
	}

	@Override
	public String getDescription() {
		return i18n.t("preferences.slicerType");
	}

	@Override
	public void disableProperty(ObservableValue<Boolean> disableProperty) {
		comboBox.disableProperty().unbind();
		comboBox.disableProperty().bind(disableProperty);
	}
}
