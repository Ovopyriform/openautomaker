package org.openautomaker.ui.component.menu_panel;

import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.component.graphic_button.GraphicButtonWithLabel;
import org.openautomaker.ui.component.menu_panel.MenuInnerPanel;
import org.openautomaker.ui.component.menu_panel.MenuInnerPanel.OperationButton;

import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

/**
 * The ButtonBox class is a JavaFX HBox that displays a set of operation buttons as defined by a {@code List<OperationButton>}. This list can change according to the active panel that it is tied to.
 *
 * @author tony
 */
public class ButtonBox extends HBox {

	@Inject
	private I18N i18n;

	public ButtonBox(ReadOnlyObjectProperty<MenuInnerPanel> extrasMenuInnerPanelProperty) {
		GuiceContext.get().injectMembers(this);
		setAlignment(Pos.CENTER);
		extrasMenuInnerPanelProperty.addListener(
				(ObservableValue<? extends MenuInnerPanel> observable, MenuInnerPanel oldValue, MenuInnerPanel newValue) -> {
					setupButtonsForInnerPanel(newValue);
				});
		setupButtonsForInnerPanel(extrasMenuInnerPanelProperty.get());
	}

	public void setExtrasMenuInnerPanelProperty(ReadOnlyObjectProperty<MenuInnerPanel> extrasMenuInnerPanelProperty) {
		extrasMenuInnerPanelProperty.addListener(
				(observable, oldValue, newValue) -> {
					setupButtonsForInnerPanel(newValue);
				});

		setupButtonsForInnerPanel(extrasMenuInnerPanelProperty.get());
	}

	/**
	 * Set up the buttons according to the given panel.
	 */
	private void setupButtonsForInnerPanel(MenuInnerPanel innerPanel) {
		if (innerPanel == null)
			return;

		getChildren().clear();

		if (innerPanel.getOperationButtons() == null)
			return;

		for (OperationButton operationButton : innerPanel.getOperationButtons()) {
			GraphicButtonWithLabel button = new GraphicButtonWithLabel(operationButton.getFXMLName(), i18n.t(operationButton.getTextId()));

			button.setOnAction((ActionEvent event) -> {
				operationButton.whenClicked();
			});

			button.disableProperty().bind(Bindings.not(operationButton.whenEnabled()));

			getChildren().add(button);
		}
	}

}
