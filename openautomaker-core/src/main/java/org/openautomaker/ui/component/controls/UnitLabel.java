package org.openautomaker.ui.component.controls;

import org.openautomaker.environment.preference.l10n.ShowMetricUnitsPreference;
import org.openautomaker.guice.GuiceContext;

import celtech.configuration.units.UnitType;
import jakarta.inject.Inject;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;

public class UnitLabel extends Label {

	private final StringProperty unitType = new SimpleStringProperty(UnitType.NONE.name());
	private UnitType units = UnitType.NONE;

	@Inject
	private ShowMetricUnitsPreference showMetricUnitsPreference;

	public UnitLabel() {
		super();
		GuiceContext.get().injectMembers(this);
	}

	public UnitType getUnits() {
		return units;
	}

	public void setUnits(UnitType units) {
		this.units = units;
		unitType.set(units.name());
		updateDisplay();
	}

	public String getUnitType() {
		return units.name();
	}

	public void setUnitType(String value) {
		units = UnitType.valueOf(value);
		unitType.set(value);
		updateDisplay();
	}

	public ReadOnlyStringProperty unitTypeProperty() {
		return unitType;
	}

	private void updateDisplay() {
		if (!showMetricUnitsPreference.getValue()) {
			this.setText(units.getImperialSymbol());
			return;
		}

		this.setText(units.getMetricSymbol());
	}
}
