package org.openautomaker.base.configuration.fileRepresentation;

import java.util.Map;

import org.openautomaker.environment.Slicer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SlicerMappings {

	public final Map<Slicer, SlicerMappingData> mappings;

	@JsonCreator
	public SlicerMappings(
			@JsonProperty("mappings") Map<Slicer, SlicerMappingData> mappings) {
		this.mappings = mappings;
	}

	public boolean isMapped(Slicer slicerType, String variable) {
		boolean isMapped = false;
		for (String formula : mappings.get(slicerType).mappingData.values()) {
			String[] elements = formula.split(":");
			if (elements.length == 0 && formula.equals(variable)) {
				isMapped = true;
				break;
			}
			else if (elements[0].equals(variable)) {
				isMapped = true;
				break;
			}
		}
		return isMapped;
	}
}
