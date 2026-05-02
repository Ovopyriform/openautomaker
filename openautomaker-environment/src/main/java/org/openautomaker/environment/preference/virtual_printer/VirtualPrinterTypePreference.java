package org.openautomaker.environment.preference.virtual_printer;

import java.util.List;
import java.util.prefs.Preferences;

import org.openautomaker.environment.PrinterType;
import org.openautomaker.environment.preference.APreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class VirtualPrinterTypePreference extends APreference<PrinterType> {

	@Inject
	protected VirtualPrinterTypePreference() {
		super();
	}

	@Override
	public List<PrinterType> validValues() {
		return List.of(PrinterType.values());
	}

	@Override
	public PrinterType getValue() {
		return PrinterType.valueOf(getUserNode().get(getKey(), PrinterType.ROBOX_PRO.name()));
	}

	@Override
	public void setValue(PrinterType printerType) {
		getUserNode().put(getKey(), printerType.name());
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}

}
