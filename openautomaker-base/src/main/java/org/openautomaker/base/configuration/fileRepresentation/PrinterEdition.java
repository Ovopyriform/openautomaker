package org.openautomaker.base.configuration.fileRepresentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PrinterEdition {

	public final String typeCode;
	public final String friendlyName;

	@JsonCreator
	public PrinterEdition(
			@JsonProperty("typeCode") String typeCode,
			@JsonProperty("friendlyName") String friendlyName) {
		this.typeCode = typeCode;
		this.friendlyName = friendlyName;
	}

	@Override
	public String toString() {
		if (friendlyName != null && friendlyName.length() > 0)
			return friendlyName;
		else
			return super.toString();
	}
}
