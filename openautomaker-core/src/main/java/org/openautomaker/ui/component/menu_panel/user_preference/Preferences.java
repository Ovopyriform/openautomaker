/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui.component.menu_panel.user_preference;

import java.util.ArrayList;
import java.util.List;

import org.openautomaker.environment.preference.advanced.ShowAdjustmentsPreference;
import org.openautomaker.environment.preference.advanced.ShowDiagnosticsPreference;
import org.openautomaker.environment.preference.advanced.ShowGCodeConsolePreference;
import org.openautomaker.environment.preference.advanced.ShowSnapshotPreference;
import org.openautomaker.environment.preference.application.FirstUsePreference;
import org.openautomaker.environment.preference.application.ShowTooltipsPreference;
import org.openautomaker.environment.preference.camera.SearchForRemoteCamerasPreference;
import org.openautomaker.environment.preference.l10n.GBPToLocalMultiplierPreference;
import org.openautomaker.environment.preference.modeling.SplitLoosePartsOnLoadPreference;
import org.openautomaker.environment.preference.printer.DetectLoadedFilamentPreference;
import org.openautomaker.environment.preference.slicer.SafetyFeaturesPreference;
import org.openautomaker.environment.preference.slicer.ShowGCodePreviewPreference;
import org.openautomaker.environment.preference.virtual_printer.VirtualPrinterEnabledPreference;
import org.openautomaker.javafx.FXProperty;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController;
import org.openautomaker.ui.component.menu_panel.PreferencesInnerPanelController.Preference;
import org.openautomaker.ui.inject.controller.AdvancedModePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CurrencySymbolPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CustomPrinterHeadPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.CustomPrinterTypePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.FloatingPointPreferenceFactory;
import org.openautomaker.ui.inject.controller.LanguagePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.LogLevelPreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.SlicerTypePreferenceControllerFactory;
import org.openautomaker.ui.inject.controller.TickBoxPreferenceFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Preferences creates collections of the Preference class.
 *
 * @author tony
 */
@Singleton
public class Preferences {

	//Dependencies
	private final DetectLoadedFilamentPreference detectLoadedFilamentPreference;
	private final FirstUsePreference firstUsePreference;
	private final GBPToLocalMultiplierPreference gbpToLocalMultiplierPreference;
	private final SafetyFeaturesPreference safetyFeaturesPreference;
	private final SearchForRemoteCamerasPreference searchForRemoteCamerasPreference;
	private final ShowAdjustmentsPreference showAdjustmentsPreference;
	private final ShowDiagnosticsPreference showDiagnosticsPreference;
	private final ShowGCodeConsolePreference showGCodeConsolePreference;
	private final ShowGCodePreviewPreference showGCodePreviewPreference;
	private final ShowSnapshotPreference showSnapshotPreference;
	private final ShowTooltipsPreference showTooltipsPreference;
	private final SplitLoosePartsOnLoadPreference splitLoosePartsOnLoadPreference;
	private final VirtualPrinterEnabledPreference virtualPrinterEnabledPreference;

	private final AdvancedModePreferenceControllerFactory advancedModePreferenceControllerFactory;
	private final CurrencySymbolPreferenceControllerFactory currencySymbolPreferenceControllerFactory;
	private final CustomPrinterHeadPreferenceControllerFactory customPrinterHeadPreferenceControllerFactory;
	private final CustomPrinterTypePreferenceControllerFactory customPrinterTypePreferenceControllerFactory;
	private final FloatingPointPreferenceFactory floatingPointPreferenceFactory;
	private final LanguagePreferenceControllerFactory languagePreferenceControllerFactory;
	private final LogLevelPreferenceControllerFactory logLevelPreferenceControllerFactory;
	private final SlicerTypePreferenceControllerFactory slicerTypePreferenceControllerFactory;
	private final TickBoxPreferenceFactory tickBoxPreferenceFactory;

	@Inject
	protected Preferences(
			DetectLoadedFilamentPreference detectLoadedFilamentPreference,
			FirstUsePreference firstUsePreference,
			GBPToLocalMultiplierPreference gbpToLocalMultiplierPreference,
			SafetyFeaturesPreference safetyFeaturesPreference,
			SearchForRemoteCamerasPreference searchForRemoteCamerasPreference,
			ShowAdjustmentsPreference showAdjustmentsPreference,
			ShowDiagnosticsPreference showDiagnosticsPreference,
			ShowGCodeConsolePreference showGCodeConsolePreference,
			ShowGCodePreviewPreference showGCodePreviewPreference,
			ShowSnapshotPreference showSnapshotPreference,
			ShowTooltipsPreference showTooltipsPreference,
			SplitLoosePartsOnLoadPreference splitLoosePartsOnLoadPreference,
			VirtualPrinterEnabledPreference virtualPrinterEnabledPreference,
			AdvancedModePreferenceControllerFactory advancedModePreferenceControllerFactory,
			CurrencySymbolPreferenceControllerFactory currencySymbolPreferenceControllerFactory,
			CustomPrinterHeadPreferenceControllerFactory customPrinterHeadPreferenceControllerFactory,
			CustomPrinterTypePreferenceControllerFactory customPrinterTypePreferenceControllerFactory,
			FloatingPointPreferenceFactory floatingPointPreferenceFactory,
			LanguagePreferenceControllerFactory languagePreferenceControllerFactory,
			LogLevelPreferenceControllerFactory logLevelPreferenceControllerFactory,
			SlicerTypePreferenceControllerFactory slicerTypePreferenceControllerFactory,
			TickBoxPreferenceFactory tickBoxPreferenceFactory) {

		this.detectLoadedFilamentPreference = detectLoadedFilamentPreference;
		this.firstUsePreference = firstUsePreference;
		this.gbpToLocalMultiplierPreference = gbpToLocalMultiplierPreference;
		this.safetyFeaturesPreference = safetyFeaturesPreference;
		this.searchForRemoteCamerasPreference = searchForRemoteCamerasPreference;
		this.showAdjustmentsPreference = showAdjustmentsPreference;
		this.showDiagnosticsPreference = showDiagnosticsPreference;
		this.showGCodeConsolePreference = showGCodeConsolePreference;
		this.showGCodePreviewPreference = showGCodePreviewPreference;
		this.showSnapshotPreference = showSnapshotPreference;
		this.showTooltipsPreference = showTooltipsPreference;
		this.splitLoosePartsOnLoadPreference = splitLoosePartsOnLoadPreference;
		this.virtualPrinterEnabledPreference = virtualPrinterEnabledPreference;

		this.advancedModePreferenceControllerFactory = advancedModePreferenceControllerFactory;
		this.currencySymbolPreferenceControllerFactory = currencySymbolPreferenceControllerFactory;
		this.customPrinterHeadPreferenceControllerFactory = customPrinterHeadPreferenceControllerFactory;
		this.customPrinterTypePreferenceControllerFactory = customPrinterTypePreferenceControllerFactory;
		this.floatingPointPreferenceFactory = floatingPointPreferenceFactory;
		this.languagePreferenceControllerFactory = languagePreferenceControllerFactory;
		this.logLevelPreferenceControllerFactory = logLevelPreferenceControllerFactory;
		this.slicerTypePreferenceControllerFactory = slicerTypePreferenceControllerFactory;
		this.tickBoxPreferenceFactory = tickBoxPreferenceFactory;

	}

	public List<PreferencesInnerPanelController.Preference> createPrintingPreferences() {
		List<PreferencesInnerPanelController.Preference> preferences = new ArrayList<>();

		Preference slicerTypePref = slicerTypePreferenceControllerFactory.create();

		Preference safetyFeaturesOnPref = tickBoxPreferenceFactory.create(FXProperty.bind(safetyFeaturesPreference), "preferences.safetyFeaturesOn");

		Preference detectFilamentLoadedPref = tickBoxPreferenceFactory.create(FXProperty.bind(detectLoadedFilamentPreference), "preferences.detectLoadedFilament");

		preferences.add(slicerTypePref);
		preferences.add(safetyFeaturesOnPref);
		preferences.add(detectFilamentLoadedPref);

		return preferences;
	}

	public List<PreferencesInnerPanelController.Preference> createEnvironmentPreferences() {
		List<PreferencesInnerPanelController.Preference> preferences = new ArrayList<>();

		Preference languagePref = languagePreferenceControllerFactory.create();
		Preference showTooltipsPref = tickBoxPreferenceFactory.create(FXProperty.bind(showTooltipsPreference), "preferences.showTooltips");
		Preference logLevelPref = logLevelPreferenceControllerFactory.create();
		Preference firstUsePref = tickBoxPreferenceFactory.create(FXProperty.bind(firstUsePreference), "preferences.firstUse");

		Preference currencySymbolPref = currencySymbolPreferenceControllerFactory.create();
		Preference currencyGBPToLocalMultiplierPref = floatingPointPreferenceFactory.create(FXProperty.bind(gbpToLocalMultiplierPreference),
				2, 7, false, "preferences.currencyGBPToLocalMultiplier");

		Preference loosePartSplitPref = tickBoxPreferenceFactory.create(FXProperty.bind(splitLoosePartsOnLoadPreference), "preferences.loosePartSplit");

		Preference autoGCodePreviewPref = tickBoxPreferenceFactory.create(FXProperty.bind(showGCodePreviewPreference), "preferences.autoGCodePreview");

		Preference searchForRemoteCamerasPref = tickBoxPreferenceFactory.create(FXProperty.bind(searchForRemoteCamerasPreference), "preferences.searchForRemoteCameras");

		preferences.add(firstUsePref);
		preferences.add(languagePref);
		preferences.add(showTooltipsPref); //Added in.  Seemed to be missed
		preferences.add(logLevelPref);
		preferences.add(currencySymbolPref);
		preferences.add(currencyGBPToLocalMultiplierPref);
		preferences.add(loosePartSplitPref);
		preferences.add(autoGCodePreviewPref);
		preferences.add(searchForRemoteCamerasPref);

		return preferences;
	}

	public List<PreferencesInnerPanelController.Preference> createAdvancedPreferences() {
		List<PreferencesInnerPanelController.Preference> preferences = new ArrayList<>();

		AdvancedModePreferenceController advancedModePref = advancedModePreferenceControllerFactory.create();

		TickBoxPreference showDiagnosticsPref = tickBoxPreferenceFactory.create(FXProperty.bind(showDiagnosticsPreference), "preferences.showDiagnostics");
		showDiagnosticsPref.disableProperty(advancedModePref.getSelectedProperty().not());

		TickBoxPreference showGCodePref = tickBoxPreferenceFactory.create(FXProperty.bind(showGCodeConsolePreference), "preferences.showGCode");
		showGCodePref.disableProperty(advancedModePref.getSelectedProperty().not());

		TickBoxPreference showAdjustmentsPref = tickBoxPreferenceFactory.create(FXProperty.bind(showAdjustmentsPreference), "preferences.showAdjustments");
		showAdjustmentsPref.disableProperty(advancedModePref.getSelectedProperty().not());

		TickBoxPreference showSnapshotPref = tickBoxPreferenceFactory.create(FXProperty.bind(showSnapshotPreference), "preferences.showSnapshot");
		showSnapshotPref.disableProperty(advancedModePref.getSelectedProperty().not());

		preferences.add(advancedModePref);
		preferences.add(showDiagnosticsPref);
		preferences.add(showGCodePref);
		preferences.add(showAdjustmentsPref);
		preferences.add(showSnapshotPref);

		return preferences;
	}

	public List<PreferencesInnerPanelController.Preference> createCustomPrinterPreferences() {
		List<PreferencesInnerPanelController.Preference> preferences = new ArrayList<>();

		Preference enableCustomPrinterPref = tickBoxPreferenceFactory.create(FXProperty.bind(virtualPrinterEnabledPreference), "preferences.customPrinterEnabled");
		Preference customPrinterTypePref = customPrinterTypePreferenceControllerFactory.create();
		Preference customPrinterHeadPref = customPrinterHeadPreferenceControllerFactory.create();

		//BooleanProperty windows32Bit = new SimpleBooleanProperty(BaseConfiguration.isWindows32Bit());
		//enableCustomPrinterPref.disableProperty(windows32Bit);

		preferences.add(enableCustomPrinterPref);
		preferences.add(customPrinterTypePref);
		preferences.add(customPrinterHeadPref);

		return preferences;
	}

	//TODO: Root Only
	public static List<PreferencesInnerPanelController.Preference> createRootPreferences() {
		List<PreferencesInnerPanelController.Preference> preferences = new ArrayList<>();

		//        Preference
		//        Preference slicerTypePref = new SlicerTypePreference(userPreferences);
		//
		//        Preference safetyFeaturesOnPref = new TickBoxPreference(userPreferences.
		//                safetyFeaturesOnProperty(), "preferences.safetyFeaturesOn");
		//
		//        Preference detectFilamentLoadedPref = new TickBoxPreference(userPreferences.
		//                detectLoadedFilamentProperty(), "preferences.detectLoadedFilament");
		//
		//        preferences.add(slicerTypePref);
		//        preferences.add(safetyFeaturesOnPref);
		//        preferences.add(detectFilamentLoadedPref);

		return preferences;
	}
}
