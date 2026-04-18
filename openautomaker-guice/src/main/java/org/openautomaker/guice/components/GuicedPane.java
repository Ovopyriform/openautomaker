package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.layout.Pane;

public abstract class GuicedPane extends Pane {
	protected GuicedPane() {
		GuiceContext.inject(this);
	}
}
