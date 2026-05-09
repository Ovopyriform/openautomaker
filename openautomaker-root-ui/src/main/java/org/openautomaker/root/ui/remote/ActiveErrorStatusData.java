package org.openautomaker.root.ui.remote;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author taldhous
 */
public class ActiveErrorStatusData
{
	private String printerID;

	private ArrayList<ErrorDetails> currentErrors;

	public ActiveErrorStatusData()
	{
		// Jackson deserialization
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
	public ArrayList<ErrorDetails> getActiveErrors()
	{
		return currentErrors;
	}

	@JsonProperty
	public void setActiveErrors(ArrayList<ErrorDetails> activeErrors)
	{
		this.currentErrors = activeErrors;
	}
}
