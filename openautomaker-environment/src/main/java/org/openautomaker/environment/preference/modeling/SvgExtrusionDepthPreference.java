package org.openautomaker.environment.preference.modeling;

import java.util.prefs.Preferences;

import org.openautomaker.environment.preference.ASimpleFloatPreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SvgExtrusionDepthPreference extends ASimpleFloatPreference {

	@Inject
	protected SvgExtrusionDepthPreference() {
	}

	@Override
	public Float getDefault() {
		return 3.0f;
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}
}
