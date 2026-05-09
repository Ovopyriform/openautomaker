package org.openautomaker.root.rootDataStructures;

import java.nio.file.Path;

/**
 *
 * @author George Salter
 */
public class UsbPrintData
{

	private String printJobID;
	private Path printJobPath;

	public UsbPrintData()
	{
		// Jackson deserialization
	}

	public String getPrintJobID()
	{
		return printJobID;
	}

	public void setPrintJobID(String printJobID)
	{
		this.printJobID = printJobID;
	}

	public Path getPrintJobPath()
	{
		return printJobPath;
	}

	public void setPrintJobPath(Path printJobPath)
	{
		this.printJobPath = printJobPath;
	}

	@Override
	public String toString()
	{
		String usbPrintDataString = "USBPrintData:\n"
				+ "Print Job ID: " + printJobID + "\n"
				+ "Print Job path: " + printJobPath;
		return usbPrintDataString;
	}
}
