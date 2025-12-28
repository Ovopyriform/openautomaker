package org.openautomaker.ui.component.printer_status_page;

import javafx.beans.property.BooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


public class OuterPanelController {

	@FXML
	private VBox rootPane;

	@FXML
	private Label title;

	@FXML
	private Pane crossButton;
	private BooleanProperty visibilityProperty;

	public void setPreferredVisibility(BooleanProperty visibilityProperty) {
		if (visibilityProperty == null) {
			crossButton.setVisible(false);
		}
		else {
			this.visibilityProperty = visibilityProperty;
			crossButton.setOnMouseClicked((MouseEvent event) -> {
				this.visibilityProperty.set(false);
			});
		}
	}

	public void setInnerPanel(Node insetPanel) {
		rootPane.getChildren().add(insetPanel);
		VBox.setVgrow(insetPanel, Priority.ALWAYS);
	}

	public void setTitle(String title) {
		this.title.setText(title);
	}

}
