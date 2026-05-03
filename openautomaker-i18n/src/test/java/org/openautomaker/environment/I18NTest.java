package org.openautomaker.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import jakarta.inject.Inject;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class I18NTest {

	//@formatter:off
	
	// List of keys to test
	private static final List<String> TRANSLATION_KEYS = List.of(
			"buttonText.addModel",
			"buttonText.addModelFromCloud",
			"buttonText.addToProject",
			"buttonText.ambientLights",
			"buttonText.autoLayout"
			);
	
	
	// English Translations
	private static final Map<String, String> ENGLISH = Map.of(
			"buttonText.addModel", "Add Model",
			"buttonText.addModelFromCloud", "From Cloud",
			"buttonText.addToProject", "Add To Project",
			"buttonText.ambientLights", "Ambient",
			"buttonText.autoLayout", "Auto Layout"
		);
	
	//French Translations
	private static final Map<String, String> FRENCH = Map.of(
			"buttonText.addModel","Ajouter un modèle",
			"buttonText.addModelFromCloud","Depuis le cloud",
			"buttonText.addToProject","Ajouter au projet",
			"buttonText.ambientLights","Ambiante",
			"buttonText.autoLayout","Mise en page automatique"
		);
	
	// German Translations
	private static final Map<String, String> GERMAN = Map.of(
			"buttonText.addModel","Modell hinzufügen",
			"buttonText.addModelFromCloud","Vom Cloud-Speicher",
			"buttonText.addToProject","Zum Projekt hinzufügen",
			"buttonText.ambientLights","Umgebung",
			"buttonText.autoLayout","Auto Layout"
		);
	
	// Spanish Translations
	public static final Map<String, String> SPANISH = Map.of(
			"buttonText.addModel","Añadir Modelo",
			"buttonText.addModelFromCloud","De La Nube",
			"buttonText.addToProject","Añadir Al Proyecto",
			"buttonText.ambientLights","Ambiente",
			"buttonText.autoLayout","Acomodo Automático"
		);

	
	// Expected template replacements (english only)
	private static final Map<String, String> EXPECTED_TEMPLATES = Map.of(
			"*T01","Disconnect your machine from USB and AC power. Check your USB cable is connected correctly.",
			"*T02","If this error persists then revert to older version of firmware / AutoMaker.",
			"*T03","Disconnect your machine from USB and AC power. Check that your SD card is present and seated correctly."
		);
	//@formatter:on

	// Configure injection for test
	private Injector injector;

	private class I18NTestModule extends AbstractModule {

		@Override
		public void configure() {
			LocaleProvider mockLocaleProvider = Mockito.mock(LocaleProvider.class);
			Mockito.when(mockLocaleProvider.getValue()).thenReturn(Locale.UK);
			bind(LocaleProvider.class).toInstance(mockLocaleProvider);
		}
	}

	@BeforeAll
	public void beforeAll() {
		injector = Guice.createInjector(new I18NTestModule());
		injector.injectMembers(this);
	}

	@Inject
	I18N i18n;

	@Test
	void testInjection() {
		assertNotNull(i18n, "Didn't get I18N with locale");
	}

	@ParameterizedTest(name = "Testing Locale {0}")
	@MethodSource("t_definedLocale_testCases")
	void t_definedLocale_test(Locale locale, Map<String, String> expected) {
			i18n.changeLocale(locale);

		for (String translationKey : expected.keySet()) {
			assertEquals(expected.get(translationKey), i18n.t(translationKey));
			}

			i18n.changeLocale(Locale.getDefault());
	}

	private static Stream<Arguments> t_definedLocale_testCases() {
		return Stream.of(
				Arguments.of(Locale.ENGLISH, ENGLISH),
				Arguments.of(Locale.FRENCH, FRENCH),
				Arguments.of(Locale.GERMAN, GERMAN),
				Arguments.of(Locale.forLanguageTag("es"), SPANISH));
	}

	@Test
	void substituteTemplates_test() {
		i18n.changeLocale(Locale.ENGLISH);

		for (String templateKey : EXPECTED_TEMPLATES.keySet()) {
			assertEquals(EXPECTED_TEMPLATES.get(templateKey), i18n.substituteTemplates(templateKey));
		}

		i18n.changeLocale(Locale.getDefault());
	}
}
