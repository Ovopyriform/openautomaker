/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui.component.material;

import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.components.GuicedListCell;

import jakarta.inject.Inject;

/**
 *
 * @author tony
 */
public class FilamentCellLong extends FilamentCell {

	@Inject
	private I18N i18n;

	public FilamentCellLong() {
		super();

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

			if (filament.getMaterial() != null) {
				label.setText(filament.getLongFriendlyName() + " "
						+ filament.getMaterial().getFriendlyName());
			}
			else {
				label.setText(filament.getLongFriendlyName());
			}
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
