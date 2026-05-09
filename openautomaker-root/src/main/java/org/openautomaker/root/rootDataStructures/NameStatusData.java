package org.openautomaker.root.rootDataStructures;

import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.root.PrinterRegistry;
import org.openautomaker.root.RootServices;

import javafx.scene.paint.Color;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author taldhous
 */
public class NameStatusData
{

	private String printerID;
	private String printerName;
	private String printerTypeCode;
	private String printerWebColourString;

	public NameStatusData()
	{
		// Jackson deserialization
	}

	public void updateFromPrinterData(String printerID)
	{
		this.printerID = printerID;
		Printer printer = PrinterRegistry.getInstance().getRemotePrinters().get(printerID);
		printerName = printer.getPrinterIdentity().printerFriendlyNameProperty().get();
		printerTypeCode = printer.printerConfigurationProperty().get().typeCode;
		Color displayColour = RootServices.getPrinterColourMap()
				.printerToDisplayColour(printer.getPrinterIdentity().printerColourProperty().get());
		printerWebColourString = displayColour != null ? String.format("#%02X%02X%02X",
				(int) (displayColour.getRed() * 255),
				(int) (displayColour.getGreen() * 255),
				(int) (displayColour.getBlue() * 255)) : "#000000";
	}

	@JsonProperty
	public String getPrinterID()
	{
		return printerID;
	}

	@JsonProperty
	public void setPrinterID(String printerID)
	{
		this.printerID = printerID;
	}

	@JsonProperty
	public String getPrinterName()
	{
		return printerName;
	}

	@JsonProperty
	public void setPrinterName(String printerName)
	{
		this.printerName = printerName;
	}

	@JsonProperty
	public String getPrinterTypeCode()
	{
		return printerTypeCode;
	}

	@JsonProperty
	public void setPrinterTypeCode(String printerTypeCode)
	{
		this.printerTypeCode = printerTypeCode;
	}

	@JsonProperty
	public String getPrinterWebColourString()
	{
		return printerWebColourString;
	}

	@JsonProperty
	public void setPrinterWebColourString(String printerWebColourString)
	{
		this.printerWebColourString = printerWebColourString;
	}
}
