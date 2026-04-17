
package org.openautomaker.ui.component.notification;

import org.openautomaker.base.printerControl.model.HeaterMode;
import org.openautomaker.base.printerControl.model.NozzleHeater;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;

public class MaterialHeatingStatusBar extends AppearingProgressBar {

	private static final double EJECT_TEMPERATURE = 140.0;

	private NozzleHeater heater = null;
	private final int materialNumber;
	private final boolean thisIsTheOnlyNozzle;
	private static final double showBarIfMoreThanXDegreesOut = 3;

	private ChangeListener<Number> numberChangeListener = (observable, oldValue, newValue) -> {
		reassessStatus();
	};

	private ChangeListener<HeaterMode> heaterModeChangeListener = (observable, oldValue, newValue) -> {
		reassessStatus();
	};

	@Inject
	private I18N i18n;

	public MaterialHeatingStatusBar(NozzleHeater heater, int materialNumber, boolean thisIsTheOnlyNozzle) {
		super();

		GuiceContext.get().injectMembers(this);

		this.heater = heater;
		this.materialNumber = materialNumber;
		this.thisIsTheOnlyNozzle = thisIsTheOnlyNozzle;

		heater.nozzleTemperatureProperty().addListener(numberChangeListener);
		heater.nozzleTargetTemperatureProperty().addListener(numberChangeListener);
		heater.nozzleFirstLayerTargetTemperatureProperty().addListener(numberChangeListener);
		heater.heaterModeProperty().addListener(heaterModeChangeListener);

		getStyleClass().add("secondaryStatusBar");

		setPickOnBounds(false);
		setMouseTransparent(true);

		reassessStatus();
	}

	@Override
	public void initialize() {
		super.initialize();
		targetLegendRequired(true);
		targetValueRequired(true);
		currentValueRequired(true);
		progressRequired(true);
		layerDataRequired(false);
	}

	private void reassessStatus() {
		boolean showHeaterBar = false;

		switch (heater.heaterModeProperty().get()) {
			case OFF:
				break;
			case FIRST_LAYER:
				if (Math.abs(heater.nozzleTemperatureProperty().get() - heater.nozzleFirstLayerTargetTemperatureProperty().get()) > showBarIfMoreThanXDegreesOut) {
					if (heater.nozzleFirstLayerTargetTemperatureProperty().get() > heater.nozzleTemperatureProperty().get()) {
						if (thisIsTheOnlyNozzle) {
							largeProgressDescription.setText(i18n.t("printerStatus.heatingNozzle"));
						}
						else {
							switch (materialNumber) {
								case 1:
									largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material1Label"));
									break;
								case 2:
									largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material2Label"));
									break;
							}
						}
					}
					else {
						if (thisIsTheOnlyNozzle) {
							largeProgressDescription.setText(i18n.t("printerStatus.coolingNozzle"));
						}
						else {
							switch (materialNumber) {
								case 1:
									largeProgressDescription.setText(i18n.t("printerStatus.cooling") + " " + i18n.t("printerStatus.Material1Label"));
									break;
								case 2:
									largeProgressDescription.setText(i18n.t("printerStatus.cooling") + " " + i18n.t("printerStatus.Material2Label"));
									break;
							}
						}
					}

					largeTargetLegend.textProperty().set(i18n.t("progressBar.targetTemperature"));
					largeTargetValue.textProperty().set(heater.nozzleFirstLayerTargetTemperatureProperty().asString("%d").get()
							.concat(i18n.t("misc.degreesC")));
					currentValue.textProperty().set(heater.nozzleTemperatureProperty().asString("%d").get()
							.concat(i18n.t("misc.degreesC")));

					if (heater.nozzleFirstLayerTargetTemperatureProperty().doubleValue() > 0) {
						double normalisedProgress = 0;
						normalisedProgress = heater.nozzleTemperatureProperty().doubleValue() / heater.nozzleFirstLayerTargetTemperatureProperty().doubleValue();
						normalisedProgress = Math.max(0, normalisedProgress);
						normalisedProgress = Math.min(1, normalisedProgress);

						progressBar.setProgress(normalisedProgress);
					}
					else {
						progressBar.setProgress(0);
					}
					showHeaterBar = true;
				}
				break;

			case NORMAL:
				if (Math.abs(heater.nozzleTemperatureProperty().get() - heater.nozzleTargetTemperatureProperty().get()) > showBarIfMoreThanXDegreesOut) {
					if (heater.nozzleTargetTemperatureProperty().get() > heater.nozzleTemperatureProperty().get()) {
						if (thisIsTheOnlyNozzle) {
							largeProgressDescription.setText(i18n.t("printerStatus.heatingNozzle"));
						}
						else {
							switch (materialNumber) {
								case 1:
									largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material1Label"));
									break;
								case 2:
									largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material2Label"));
									break;
							}
						}
					}
					else {
						if (thisIsTheOnlyNozzle) {
							largeProgressDescription.setText(i18n.t("printerStatus.coolingNozzle"));
						}
						else {
							switch (materialNumber) {
								case 1:
									largeProgressDescription.setText(i18n.t("printerStatus.cooling") + " " + i18n.t("printerStatus.Material1Label"));
									break;
								case 2:
									largeProgressDescription.setText(i18n.t("printerStatus.cooling") + " " + i18n.t("printerStatus.Material2Label"));
									break;
							}
						}
					}

					largeTargetLegend.textProperty().set(i18n.t("progressBar.targetTemperature"));
					largeTargetValue.textProperty().set(heater.nozzleTargetTemperatureProperty().asString("%d").get()
							.concat(i18n.t("misc.degreesC")));
					currentValue.textProperty().set(heater.nozzleTemperatureProperty().asString("%d").get()
							.concat(i18n.t("misc.degreesC")));

					if (heater.nozzleFirstLayerTargetTemperatureProperty().doubleValue() > 0) {
						double normalisedProgress = 0;
						normalisedProgress = heater.nozzleTemperatureProperty().doubleValue() / heater.nozzleTargetTemperatureProperty().doubleValue();
						normalisedProgress = Math.max(0, normalisedProgress);
						normalisedProgress = Math.min(1, normalisedProgress);

						progressBar.setProgress(normalisedProgress);
					}
					else {
						progressBar.setProgress(0);
					}
					showHeaterBar = true;
				}
				break;
			case FILAMENT_EJECT:
				if (Math.abs(heater.nozzleTemperatureProperty().get() - EJECT_TEMPERATURE) > showBarIfMoreThanXDegreesOut) {
					if (thisIsTheOnlyNozzle) {
						largeProgressDescription.setText(i18n.t("printerStatus.heatingNozzle"));
					}
					else {
						switch (materialNumber) {
							case 1:
								largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material1Label"));
								break;
							case 2:
								largeProgressDescription.setText(i18n.t("printerStatus.heating") + " " + i18n.t("printerStatus.Material2Label"));
								break;
						}
					}

					largeTargetLegend.textProperty().set(i18n.t("progressBar.targetTemperature"));
					largeTargetValue.textProperty().set(String.format("%.0f", EJECT_TEMPERATURE)
							+ i18n.t("misc.degreesC"));
					currentValue.textProperty().set(heater.nozzleTemperatureProperty().asString("%d").get()
							.concat(i18n.t("misc.degreesC")));

					double normalisedProgress = 0;
					normalisedProgress = heater.nozzleTemperatureProperty().doubleValue() / EJECT_TEMPERATURE;
					normalisedProgress = Math.max(0, normalisedProgress);
					normalisedProgress = Math.min(1, normalisedProgress);

					progressBar.setProgress(normalisedProgress);

					showHeaterBar = true;
				}
				break;
			default:
				break;
		}

		if (showHeaterBar) {
			startSlidingInToView();
		}
		else {
			startSlidingOutOfView();
		}
	}

	public void unbindAll() {
		if (heater != null) {
			heater.nozzleTemperatureProperty().removeListener(numberChangeListener);
			heater.nozzleTargetTemperatureProperty().removeListener(numberChangeListener);
			heater.nozzleFirstLayerTargetTemperatureProperty().removeListener(numberChangeListener);
			heater.heaterModeProperty().removeListener(heaterModeChangeListener);
			heater = null;
		}
		// Hide the bar if it is currently shown.
		startSlidingOutOfView();
	}
}
