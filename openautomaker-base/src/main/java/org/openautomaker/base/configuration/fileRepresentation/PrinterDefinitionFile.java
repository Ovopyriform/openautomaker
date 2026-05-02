package org.openautomaker.base.configuration.fileRepresentation;

import java.util.List;

import org.openautomaker.environment.PrinterType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PrinterDefinitionFile {

	public final int version;
	public final String typeCode;
	public final String friendlyName;
	public final List<PrinterEdition> editions;
	public final List<String> compatibleHeads;
	public final int printVolumeWidth;
	public final int printVolumeDepth;
	public final int printVolumeHeight;

	@JsonCreator
	public PrinterDefinitionFile(
			@JsonProperty("version") int version,
			@JsonProperty("typeCode") String typeCode,
			@JsonProperty("friendlyName") String friendlyName,
			@JsonProperty("editions") List<PrinterEdition> editions,
			@JsonProperty("compatibleHeads") List<String> compatibleHeads,
			@JsonProperty("printVolumeWidth") int printVolumeWidth,
			@JsonProperty("printVolumeDepth") int printVolumeDepth,
			@JsonProperty("printVolumeHeight") int printVolumeHeight) {
		this.version = version;
		this.typeCode = typeCode;
		this.friendlyName = friendlyName;
		this.editions = editions;
		this.compatibleHeads = compatibleHeads;
		this.printVolumeWidth = printVolumeWidth;
		this.printVolumeDepth = printVolumeDepth;
		this.printVolumeHeight = printVolumeHeight;
	}

	public PrinterType getPrinterType() {
		return PrinterType.getPrinterTypeForTypeCode(typeCode);
	}

	@Override
	public String toString() {
		if (friendlyName != null && friendlyName.length() > 0)
			return friendlyName;
		else
			return super.toString();
	}
}
