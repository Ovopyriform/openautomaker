package org.openautomaker.project;

import java.util.prefs.Preferences;

import org.openautomaker.environment.preference.ASimpleFloatPreference;

import jakarta.inject.Singleton;

@Singleton
public class SVGExtrusionDepthPreference extends ASimpleFloatPreference {

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}

	@Override
	public Float getDefault() {
		return Float.valueOf(3.0f);
	}
}
