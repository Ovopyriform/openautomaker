/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui.component.calibration_panel;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.guice.FXMLLoaderFactory;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * DiagramHandler loads and automatically rescales an fxml diagram when it's container changes size.
 * 
 * @author tony
 */
public class DiagramHandler {

	private static final Logger LOGGER = LogManager.getLogger(
			DiagramHandler.class.getName());

	private Bounds diagramBounds;
	private Pane diagramNode;
	private final VBox diagramContainer;

	public DiagramHandler(VBox diagramContainer) {
		this.diagramContainer = diagramContainer;
	}

	private void addDiagramMoveScaleListeners() {

		diagramContainer.widthProperty().addListener(
				(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
					resizeDiagram();
				});

		diagramContainer.heightProperty().addListener(
				(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
					resizeDiagram();
				});

	}

	private Bounds getBoundsOfNotYetDisplayedNode(Pane loadedDiagramNode) {
		Group group = new Group(loadedDiagramNode);
		Scene scene = new Scene(group);
		scene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
		group.applyCss();
		group.layout();
		Bounds bounds = loadedDiagramNode.getLayoutBounds();
		return bounds;
	}

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	private void loadDiagram() {
		URL fxmlFileName = getClass().getResource("diagrams/purge/purge.fxml");
		try {
			FXMLLoader loader = fxmlLoaderFactory.create(fxmlFileName);
			diagramNode = loader.load();
			diagramBounds = getBoundsOfNotYetDisplayedNode(diagramNode);
			diagramContainer.getChildren().clear();
			diagramContainer.getChildren().add(diagramNode);

		}
		catch (IOException ex) {
			LOGGER.error("Could not load diagram: " + fxmlFileName, ex);
		}
	}

	private void resizeDiagram() {
		double diagramWidth = diagramBounds.getWidth();
		double diagramHeight = diagramBounds.getHeight();

		double availableWidth = diagramContainer.getWidth();
		double availableHeight = diagramContainer.getHeight();

		double requiredScaleHeight = availableHeight / diagramHeight * 0.95;
		double requiredScaleWidth = availableWidth / diagramWidth * 0.95;
		double requiredScale = Math.min(requiredScaleHeight, requiredScaleWidth);

		diagramNode.setScaleX(requiredScale);
		diagramNode.setScaleY(requiredScale);

		diagramNode.setPrefWidth(0);
		diagramNode.setPrefHeight(0);

		double xTranslate = 0;
		double yTranslate = 0;
		xTranslate += availableWidth / 2.0 - diagramWidth / 2.0;
		yTranslate -= availableHeight;

		diagramNode.setTranslateX(xTranslate);
		diagramNode.setTranslateY(yTranslate);

	}

	void initialise() {
		loadDiagram();
		resizeDiagram();
		addDiagramMoveScaleListeners();
	}

}
