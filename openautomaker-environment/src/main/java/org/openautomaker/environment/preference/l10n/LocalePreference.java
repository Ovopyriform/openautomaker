package org.openautomaker.environment.preference.l10n;

import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import org.openautomaker.environment.LocaleProvider;
import org.openautomaker.environment.preference.APreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Preference representing the users choice of locale
 */
@Singleton
public class LocalePreference extends APreference<Locale> implements LocaleProvider {

	private Locale fDefaultLocale;

	// Supported languages
	private static final List<Locale> VALUES = List.of(
			Locale.UK,
			Locale.US,
			Locale.GERMANY,
			Locale.FRANCE,
			Locale.JAPAN,
			Locale.KOREA,
			Locale.CHINA,
			Locale.TAIWAN,
			Locale.forLanguageTag("tr-TR"), //Turkey
			Locale.forLanguageTag("es-ES"), //Spain
			Locale.forLanguageTag("fi-FI"), //Finland
			Locale.forLanguageTag("ru-RU"), //Russia
			Locale.forLanguageTag("sv-SE"), //Sweden
			Locale.forLanguageTag("zh-HK"), //Hong Kong
			Locale.forLanguageTag("zh-SG") // Singapore
	);

	@Inject
	public LocalePreference() {
		super();
		// Check system Locale.  If it's not in the list default to en-gb
		fDefaultLocale = Locale.getDefault();
		if (!VALUES.contains(fDefaultLocale))
			fDefaultLocale = Locale.UK;

	}

	@Override
	public Locale getValue() {
		return Locale.forLanguageTag(getUserNode().get(getKey(), fDefaultLocale.toLanguageTag()));
	}

	@Override
	public void setValue(Locale locale) {
		getUserNode().put(getKey(), locale.toLanguageTag());
	}

	@Override
	public List<Locale> validValues() {
		return VALUES;
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}
}
