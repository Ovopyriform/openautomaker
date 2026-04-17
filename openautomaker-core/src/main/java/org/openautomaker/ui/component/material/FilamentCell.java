/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui.component.material;

import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;

/**
 *
 * @author tony
 */
public class FilamentCell extends ListCell<Filament> {

	private static int SWATCH_SQUARE_SIZE = 16;

	HBox cellContainer;
	Rectangle rectangle = new Rectangle();
	Label label;

	@Inject
	public I18N i18n;

	public FilamentCell() {
		GuiceContext.get().injectMembers(this);

		cellContainer = new HBox();
		cellContainer.setAlignment(Pos.CENTER_LEFT);
		rectangle = new Rectangle(SWATCH_SQUARE_SIZE, SWATCH_SQUARE_SIZE);
		label = new Label();
		label.setId("materialComponentComboLabel");
		label.getStyleClass().add("filamentSwatchPadding");
		cellContainer.getChildren().addAll(rectangle, label);
	}

	@Override
	protected void updateItem(Filament item, boolean empty) {
		super.updateItem(item, empty);
		if (item != null && !empty
				&& item != FilamentContainer.UNKNOWN_FILAMENT) {
			Filament filament = item;
			setGraphic(cellContainer);
			rectangle.setVisible(true);
			rectangle.setFill(filament.getDisplayColour());

			label.setText(filament.getLongFriendlyName());
		}
		else if (item == FilamentContainer.UNKNOWN_FILAMENT) {
			Filament filament = item;
			setGraphic(cellContainer);
			rectangle.setVisible(false);

			label.setText(filament.getLongFriendlyName());
		}
		else {
			setGraphic(null);
			label.setText(i18n.t("materialComponent.unknown"));
		}
	}
}
