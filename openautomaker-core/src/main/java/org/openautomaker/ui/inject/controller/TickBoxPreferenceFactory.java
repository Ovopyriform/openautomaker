package org.openautomaker.ui.inject.controller;

import org.openautomaker.ui.component.menu_panel.user_preference.TickBoxPreference;

import javafx.beans.property.BooleanProperty;

public interface TickBoxPreferenceFactory {

	public TickBoxPreference create(
			BooleanProperty booleanProperty,
			String caption);
}
