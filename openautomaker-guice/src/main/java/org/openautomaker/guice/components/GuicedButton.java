package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.control.Button;

public abstract class GuicedButton extends Button {
	protected GuicedButton() {
		GuiceContext.inject(this);
	}
}
