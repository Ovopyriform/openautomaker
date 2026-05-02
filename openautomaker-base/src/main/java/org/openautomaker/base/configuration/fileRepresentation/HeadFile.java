package org.openautomaker.base.configuration.fileRepresentation;

import java.util.List;
import java.util.Optional;

import org.openautomaker.base.printerControl.model.Extruder;
import org.openautomaker.base.printerControl.model.Head.HeadType;
import org.openautomaker.base.printerControl.model.Head.ValveType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HeadFile {

	public final int version;
	public final String typeCode;
	public final HeadType type;
	public final ValveType valves;
	public final float zReduction;
	public final List<NozzleHeaterData> nozzleHeaters;
	public final List<NozzleData> nozzles;

	@JsonCreator
	public HeadFile(
			@JsonProperty("version") int version,
			@JsonProperty("typeCode") String typeCode,
			@JsonProperty("type") HeadType type,
			@JsonProperty("valves") ValveType valves,
			@JsonProperty("zReduction") float zReduction,
			@JsonProperty("nozzleHeaters") List<NozzleHeaterData> nozzleHeaters,
			@JsonProperty("nozzles") List<NozzleData> nozzles) {
		this.version = version;
		this.typeCode = typeCode;
		this.type = type;
		this.valves = valves;
		this.zReduction = zReduction;
		this.nozzleHeaters = nozzleHeaters;
		this.nozzles = nozzles;
	}

	@Override
	public String toString() {
		return typeCode;
	}

	@JsonIgnore
	public Optional<Integer> getNozzleNumberForExtruderNumber(int extruderNumber) {
		Optional<Integer> returnVal = Optional.empty();

		for (int nozzleIndex = 0; nozzleIndex < nozzles.size(); nozzleIndex++) {
			if (nozzles.get(nozzleIndex).associatedExtruder.equalsIgnoreCase(Extruder.getExtruderLetterForNumber(extruderNumber))) {
				returnVal = Optional.of(nozzleIndex);
				break;
			}
		}
		return returnVal;
	}
}
