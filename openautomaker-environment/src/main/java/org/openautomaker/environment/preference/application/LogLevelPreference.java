package org.openautomaker.environment.preference.application;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

import org.apache.logging.log4j.Level;
import org.openautomaker.environment.preference.APreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Represents the configured log level for the application.
 * 
 * Simple wrapper tying together the the Preferences API and log4j Level.
 *
 */
@Singleton
public class LogLevelPreference extends APreference<Level> {

	private static final String DEFAULT_VALUE = "INFO";

	/**
	 * Populates the level from the preference. Defaults to INFO
	 */
	@Inject
	protected LogLevelPreference() {
		super();
	}

	@Override
	public List<Level> validValues() {
		Level[] levels = Level.values();
		Arrays.sort(levels);
		return List.of(levels);
	}

	@Override
	public Level getValue() {
		return Level.getLevel(getUserNode().get(getKey(), DEFAULT_VALUE));
	}

	@Override
	public void setValue(Level level) {
		getUserNode().put(getKey(), level.name());
	}

	@Override
	protected Preferences getNode() {
		return getUserNode();
	}
}
