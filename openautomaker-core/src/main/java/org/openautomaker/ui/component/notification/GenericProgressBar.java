
package org.openautomaker.ui.component.notification;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;

public class GenericProgressBar extends AppearingProgressBar {

	private final ChangeListener<Boolean> displayBarChangeListener = (observable, newValue, oldValue) -> {
		displayBar(newValue);
	};

	private void displayBar(boolean displayBar) {
		if (displayBar) {
			startSlidingInToView();
		}
		else {
			startSlidingOutOfView();
		}
	}

	public GenericProgressBar(String title, ReadOnlyBooleanProperty displayProgressBar, ReadOnlyDoubleProperty progressProperty) {
		super();

		displayBar(displayProgressBar.get());
		displayProgressBar.addListener(displayBarChangeListener);

		progressRequired(true);
		targetLegendRequired(false);
		targetValueRequired(false);
		currentValueRequired(false);
		layerDataRequired(false);

		largeProgressDescription.setText(title);
		progressBar.progressProperty().bind(progressProperty);
	}
}
