package org.openautomaker.environment.preference;

import java.util.List;

/**
 * Abstract boolean application preference
 */
public abstract class ASimpleBooleanPreference extends APreference<Boolean> {

	@Override
	public List<Boolean> validValues() {
		return List.of(Boolean.TRUE, Boolean.FALSE);
	}

	@Override
	public Boolean getValue() {
		return Boolean.valueOf(getUserNode().getBoolean(getKey(), getDefault()));
	}

	@Override
	public void setValue(Boolean value) {
		getUserNode().putBoolean(getKey(), value);
	}

	@Override
	public Boolean getDefault() {
		return Boolean.TRUE;
	}
}
