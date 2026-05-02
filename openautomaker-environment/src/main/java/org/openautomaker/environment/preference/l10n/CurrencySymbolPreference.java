package org.openautomaker.environment.preference.l10n;

import java.util.List;
import java.util.prefs.Preferences;

import org.openautomaker.environment.CurrencySymbol;
import org.openautomaker.environment.preference.APreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CurrencySymbolPreference extends APreference<CurrencySymbol> {

	@Inject
	protected CurrencySymbolPreference() {

	}

	@Override
	public List<CurrencySymbol> validValues() {
		return List.of(CurrencySymbol.values());
	}

	@Override
	public CurrencySymbol getValue() {
		return CurrencySymbol.valueOf(getNode().get(getKey(), CurrencySymbol.POUND.name()));
	}

	@Override
	public void setValue(CurrencySymbol value) {
		getNode().put(getKey(), value.name());
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}

}
