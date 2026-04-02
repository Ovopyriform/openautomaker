package org.openautomaker.ui.inject.controller;

import org.openautomaker.ui.component.menu_panel.user_preference.FloatingPointPreference;

import com.google.inject.assistedinject.Assisted;

import javafx.beans.property.FloatProperty;

public interface FloatingPointPreferenceFactory {

	public FloatingPointPreference create(
			FloatProperty floatProperty,
			@Assisted("decimalPlaces") int decimalPlaces,
			@Assisted("digits") int digits,
			boolean negativeAllowed,
			String caption);

}
