package org.openautomaker.environment.preference.virtual_printer;

import java.util.List;
import java.util.prefs.Preferences;

import org.openautomaker.environment.preference.ASimpleStringPreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class VirtualPrinterHeadPreference extends ASimpleStringPreference {

	@Inject
	protected VirtualPrinterHeadPreference() {

	}

	/**
	 * TODO: This should evaluate to the list of heads loaded from the config files.
	 * 
	 * Loading of heads should probably be a separate module.
	 */
	@Override
	public List<String> validValues() {
		return List.of();
	}

	@Override
	public String getDefault() {
		return "RBX01-SM";
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}

}
