
package org.openautomaker.ui;

/**
 *
 * @author Ian
 */
public enum AmbientLEDState {

	/**
	 *
	 */
	OFF,

	/**
	 *
	 */
	WHITE,

	/**
	 *
	 */
	COLOUR;

	/**
	 *
	 * @return
	 */
	public AmbientLEDState getNextState() {
		AmbientLEDState returnState = null;

		AmbientLEDState[] values = AmbientLEDState.values();

		for (int i = 0; i < values.length; i++) {
			if (values[i] == this) {
				if (i < values.length - 1) {
					returnState = values[i + 1];
				}
				else {
					returnState = values[0];
				}
			}
		}

		return returnState;
	}
}
