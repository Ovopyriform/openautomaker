package org.openautomaker.root.rootDataStructures;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.I18N;
import org.openautomaker.root.PrinterRegistry;
import org.openautomaker.root.RootServices;

import com.fasterxml.jackson.annotation.JsonProperty;

import celtech.roboxbase.comms.rx.FirmwareError;

/**
 *
 * @author taldhous
 */
public class ActiveErrorStatusData
{

	private static final Logger LOGGER = LogManager.getLogger();

	private String printerID;
	//Errors
	private ArrayList<ErrorDetails> currentErrors;

	public ActiveErrorStatusData()
	{
		// Jackson deserialization
	}

	public void updateFromPrinterData(String printerID)
	{
		I18N i18n = RootServices.getI18N();

		try
		{
			this.printerID = printerID;
			Printer printer = PrinterRegistry.getInstance().getRemotePrinters().get(printerID);

			// The activeErrors list only contains those uncleared errors for which isRequireUserToClear() returns true.
			// The Root interface needs all the current errors, so the currentError list is used, as it contains
			// all the uncleared errors that have not been suppressed.
			if (printer != null && !printer.getCurrentErrors().isEmpty())
			{
				currentErrors = new ArrayList<>();
				for (FirmwareError currentError : printer.getCurrentErrors()) {
					if (currentError == FirmwareError.NOZZLE_FLUSH_NEEDED &&
							printer.printerStatusProperty().get() == PrinterStatus.IDLE)
					{
						//Suppress NOZZLE_FLUSH if the printer is idle.
					}
					else
					{
						String errorMessage = i18n.t(currentError.getErrorMessageKey());
						currentErrors.add(new ErrorDetails(currentError.getBytePosition(),
								i18n.t(currentError.getErrorTitleKey()),
								errorMessage,
								currentError.isRequireUserToClear(),
								currentError.getOptions()
								.stream()
								.mapToInt((o) -> o.getFlag())
								.reduce(0, (a, b) -> a | b)));
					}
				}
				if (currentErrors.isEmpty())
					currentErrors = null;
			}
		}
		catch (Exception ex)
		{
			LOGGER.error("ActiveErrorStatusData.updateFromPrinterData threw exception", ex);
			currentErrors = null;
		}
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
