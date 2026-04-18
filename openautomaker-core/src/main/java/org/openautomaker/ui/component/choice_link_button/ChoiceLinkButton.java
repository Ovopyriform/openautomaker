package org.openautomaker.ui.component.choice_link_button;

import java.io.IOException;

import org.openautomaker.guice.components.GuicedButton;

import org.openautomaker.ui.component.controls.HyperlinkedLabel;
import jakarta.inject.Inject;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

public class ChoiceLinkButton extends GuicedButton {

	@FXML
	VBox labelGroup;

	@FXML
	HyperlinkedLabel title;

	@FXML
	HyperlinkedLabel message;

	@Inject
	private FXMLLoader fxmlLoader;

	public ChoiceLinkButton() {

		fxmlLoader.setLocation(getClass().getResource("ChoiceLinkButton.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		fxmlLoader.setClassLoader(this.getClass().getClassLoader());

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		this.getStyleClass().add("error-dialog-choice-button");
	}

	public void setTitle(String i18nTitle) {
		title.replaceText(i18nTitle);
	}

	public String getTitle() {
		return title.getText();
	}

	public StringProperty titleProperty() {
		return title.textProperty();
	}

	public void setMessage(String i18nMessage) {
		message.replaceText(i18nMessage);
	}

	public String getMessage() {
		return message.getText();
	}

	public StringProperty messageProperty() {
		return message.textProperty();
	}
}
