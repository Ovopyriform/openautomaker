package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.layout.VBox;

public abstract class GuicedVBox extends VBox {
	protected GuicedVBox() {
		GuiceContext.inject(this);
	}
}
