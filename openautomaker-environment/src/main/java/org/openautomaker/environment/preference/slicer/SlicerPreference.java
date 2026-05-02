package org.openautomaker.environment.preference.slicer;

import java.util.List;
import java.util.prefs.Preferences;

import org.openautomaker.environment.Slicer;
import org.openautomaker.environment.preference.APreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Preference for the selected Slicer
 */
@Singleton
public class SlicerPreference extends APreference<Slicer> {

	@Inject
	protected SlicerPreference() {

	}

	@Override
	public List<Slicer> validValues() {
		return List.of(Slicer.values());
	}

	@Override
	public Slicer getValue() {
		return Slicer.valueOf(getUserNode().get(getKey(), Slicer.CURA_5.name()));
	}

	@Override
	public void setValue(Slicer slicer) {
		getUserNode().put(getKey(), slicer.name());
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}
}
