package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.layout.HBox;

public abstract class GuicedHBox extends HBox {
	protected GuicedHBox() {
		GuiceContext.inject(this);
	}
}
