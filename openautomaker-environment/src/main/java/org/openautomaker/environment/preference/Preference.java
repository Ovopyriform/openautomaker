package org.openautomaker.environment.preference;

import java.util.List;
import java.util.prefs.PreferenceChangeListener;

/**
 * Interface which defines the public methods of an application preference
 * 
 * @param <T> The type of preference
 */
public interface Preference<T> {

	/**
	 * Adds a PreferenceChangeListener to preference
	 * 
	 * @param pcl - PreferenceChangeListener to handle the preference change.
	 */
	public void addChangeListener(PreferenceChangeListener pcl);

	/**
	 * Returns a list of values of type T valid for this type.
	 * 
	 * @return A list of elements of type T.
	 * 
	 * @exception UnsupportedOperationException from default implementation
	 */
	public default List<T> validValues() {
		throw new UnsupportedOperationException("values not implemented for preference: " + getClass().getSimpleName());
	};

	/**
	 * Gets the value of the preference in the defined type
	 * 
	 * @return The value of the preferences of type T
	 */
	public T getValue();

	/**
	 * Sets the value of the preference
	 * 
	 * @param value - The preference value of type T
	 */
	public void setValue(T value);
	
	/**
	 * Gets the default value for this application preference
	 * 
	 * @return the default value of preference of type T
	 * 
	 * @exception UnsupportedOperationException from default implementation
	 */
	public default T getDefault() {
		throw new UnsupportedOperationException("getDefault not implemented for preference: " + getClass().getSimpleName());
	};

	/**
	 * Removes the value associated to this preference
	 */
	public void remove();
}
