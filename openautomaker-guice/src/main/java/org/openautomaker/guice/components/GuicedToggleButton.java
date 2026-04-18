package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.control.ToggleButton;

public abstract class GuicedToggleButton extends ToggleButton {
	protected GuicedToggleButton() {
		GuiceContext.inject(this);
	}
}
