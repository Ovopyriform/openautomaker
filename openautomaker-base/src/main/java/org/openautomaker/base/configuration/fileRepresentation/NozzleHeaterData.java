package org.openautomaker.base.configuration.fileRepresentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NozzleHeaterData {

	public final int maximum_temperature_C;
	public final float beta;
	public final float tcal;

	@JsonCreator
	public NozzleHeaterData(
			@JsonProperty("maximum_temperature_C") int maximum_temperature_C,
			@JsonProperty("beta") float beta,
			@JsonProperty("tcal") float tcal) {
		this.maximum_temperature_C = maximum_temperature_C;
		this.beta = beta;
		this.tcal = tcal;
	}
}
