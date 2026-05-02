package org.openautomaker.mock.printer_control.model;

import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.configuration.fileRepresentation.NozzleHeaterData;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.NozzleHeater;
import org.openautomaker.environment.I18N;

import com.google.inject.assistedinject.Assisted;

import jakarta.inject.Inject;
import javafx.beans.property.FloatProperty;

public class MockHead extends Head {

	@Inject
	public MockHead(
			I18N i18n,
			HeadContainer headContainer,
			@Assisted HeadFile headFile) {

		super(i18n, headContainer, headFile);
	}

	@Override
	protected NozzleHeater makeNozzleHeater(NozzleHeaterData nozzleHeaterData) {
		return new TestNozzleHeater(nozzleHeaterData.maximum_temperature_C,
				nozzleHeaterData.beta,
				nozzleHeaterData.tcal,
				0, 0, 0, 0, "");
	}

	public class TestNozzleHeater extends NozzleHeater {

		public TestNozzleHeater(float maximumTemperature,
				float beta,
				float tcal,
				float lastFilamentTemperature,
				int nozzleTemperature,
				int nozzleFirstLayerTargetTemperature,
				int nozzleTargetTemperature,
				String filamentID) {
			super(maximumTemperature, beta, tcal, lastFilamentTemperature, nozzleTemperature,
					nozzleFirstLayerTargetTemperature, nozzleTargetTemperature, filamentID);
		}

		@Override
		public FloatProperty lastFilamentTemperatureProperty() {
			return lastFilamentTemperature;
		}

	}

}
