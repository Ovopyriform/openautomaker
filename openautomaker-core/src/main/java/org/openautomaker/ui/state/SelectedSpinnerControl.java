package org.openautomaker.ui.state;

import com.google.inject.Singleton;

import org.openautomaker.ui.SpinnerControl;
import jakarta.inject.Inject;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Simple wrapper object for injection
 */
@Singleton
public class SelectedSpinnerControl extends SimpleObjectProperty<SpinnerControl> {

	@Inject
	protected SelectedSpinnerControl() {
		super();
	}
}
