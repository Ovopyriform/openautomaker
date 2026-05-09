package org.openautomaker.environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Language features
 * 
 */
@Singleton
public final class I18N {

	/**
	 * ResourceBundle implementation used to consolidate multiple resource bundles
	 */
	private class MultiResourceBundle extends ResourceBundle {

		private final List<ResourceBundle> delegates;

		/**
		 * Build a resource bundle from multiple resource bundles
		 * 
		 * @param resourceBundles - ResourceBundles to consolidate
		 */
		public MultiResourceBundle(ResourceBundle... resourceBundles) {
			delegates = resourceBundles == null ? new ArrayList<>() : List.of(resourceBundles);
		}

		@Override
		protected Object handleGetObject(String key) {
			Optional<Object> firstPropertyValue = this.delegates.stream()
					.filter(delegate -> delegate != null && delegate.containsKey(key))
					.map(delegate -> delegate.getObject(key))
					.findFirst();

			return firstPropertyValue.isPresent() ? firstPropertyValue.get() : null;
		}

		@Override
		public Enumeration<String> getKeys() {
			List<String> keys = this.delegates.stream()
					.filter(delegate -> delegate != null)
					.flatMap(delegate -> Collections.list(delegate.getKeys()).stream())
					.collect(Collectors.toList());

			return Collections.enumeration(keys);
		}
	}

	private static final Pattern TEMPLATE_PATTERN = Pattern.compile(".*\\*T(\\d\\d).*");

	protected ResourceBundle fldLanguage = null;

	/**
	 * Injectable I18N constructor
	 * 
	 * @param localPreference - LocalePreference (injected by Guice)
	 */
	@Inject
	public I18N(LocaleProvider localeProvider) {
		refreshResourceBundle(localeProvider.getValue());

		// As we're following the applications locale, add a listener to replace the INSTANCE if it changes
		localeProvider.addChangeListener((evt) -> {
			refreshResourceBundle(localeProvider.getValue());
		});
	}

	/**
	 * Returns the appropriate translation based on the key provided
	 * 
	 * Instance version for injection
	 * 
	 * @param key - The message key to translate
	 * @return Translated key
	 */
	public String t(String key) {
		if (key == null)
			return "";

		String translatedResource = null;

		// Manage non-existing keys, just return the string passed to us.
		try {
			translatedResource = fldLanguage.getString(key);
		}
		catch (MissingResourceException ex) {
			return key;
		}

		return substituteTemplates(translatedResource);
	}

	/**
	 * Strings containing templates (eg *T14) should be substituted with the correct text.
	 * 
	 * Instance version for Injection
	 *
	 * @param langString
	 * @return
	 */
	public String substituteTemplates(String langString) {
		while (true) {
			Matcher matcher = TEMPLATE_PATTERN.matcher(langString);
			if (!matcher.find())
				break;

			String template = "*T" + matcher.group(1);
			String templatePattern = "\\*T" + matcher.group(1);
			langString = langString.replaceAll(templatePattern, t(template));
		}

		return langString;
	}

	/**
	 * Returns the resource bundle being used for translation
	 * 
	 * @return The resources currently in use.
	 */
	public ResourceBundle getResourceBundle() {
		return fldLanguage;
	}

	/**
	 * Protected method only used for testing. Changes the I18N object locale without updating the preference
	 * 
	 * @param locale - The local to change to
	 */
	protected void changeLocale(Locale locale) {
		refreshResourceBundle(locale);
	}

	// TODO: Optimisation.  Resource Bundles are cached.  Combining the resources would mean there's no need for this multi-resource bundle
	private void refreshResourceBundle(Locale locale) {
		String packageName = getClass().getPackageName();
		List<String> resourceNames = List.of(
				packageName + ".LanguageData",
				packageName + ".UI_LanguageData",
				packageName + ".NoUI_LanguageData");

		fldLanguage = new MultiResourceBundle(resourceNames.stream()
				.map((resourceName) -> {
					return ResourceBundle.getBundle(resourceName, locale);
				})
				.toArray(ResourceBundle[]::new));
	}
}
