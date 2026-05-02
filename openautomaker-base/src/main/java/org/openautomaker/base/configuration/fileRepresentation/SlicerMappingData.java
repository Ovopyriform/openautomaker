package org.openautomaker.base.configuration.fileRepresentation;

import java.util.ArrayList;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SlicerMappingData {

	public final ArrayList<String> defaults;
	public final HashMap<String, String> mappingData;

	@JsonCreator
	public SlicerMappingData(
			@JsonProperty("defaults") ArrayList<String> defaults,
			@JsonProperty("mappingData") HashMap<String, String> mappingData) {
		this.defaults = defaults;
		this.mappingData = mappingData;
	}
}
