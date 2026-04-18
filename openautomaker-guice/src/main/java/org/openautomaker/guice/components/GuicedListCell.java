package org.openautomaker.guice.components;

import org.openautomaker.guice.GuiceContext;

import javafx.scene.control.ListCell;

public abstract class GuicedListCell<T> extends ListCell<T> {
	protected GuicedListCell() {
		GuiceContext.inject(this);
	}
}
