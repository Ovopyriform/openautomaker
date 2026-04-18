package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.layout.StackPane;

public abstract class GuicedStackPane extends StackPane {
	protected GuicedStackPane() {
		GuiceContext.inject(this);
	}
}
