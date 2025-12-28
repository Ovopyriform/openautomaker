
package org.openautomaker.ui.component.calibration_panel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.NozzleHeightStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.NozzleOpeningStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.SingleNozzleHeightStateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.XAndYStateTransitionManager;

import jakarta.inject.Inject;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyFloatProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DiagramController {

	private static final Logger LOGGER = LogManager.getLogger();

	private StateTransitionManager stateTransitionManager;

	@FXML
	private TextField xOffsetA;

	@FXML
	private TextField yOffsetA;

	@FXML
	protected ComboBox<String> cmbYOffset;

	@FXML
	protected ComboBox<String> cmbXOffset;

	@FXML
	private TextField calibrationTextField;

	@FXML
	private TextField fineNozzleLbl;

	@FXML
	private TextField fillNozzleLbl;

	@FXML
	private TextField BPosition;

	@FXML
	private HBox xOffsetComboContainer;

	@FXML
	private VBox yOffsetComboContainer;

	@FXML
	private HBox yOffsetContainerB;

	@FXML
	private HBox yOffsetContainerC;

	@FXML
	private HBox xOffsetContainerB;

	@FXML
	private HBox xOffsetContainerC;

	@FXML
	private HBox perfectAlignmentContainer;

	@FXML
	private HBox incorrectAlignmentContainer;

	@FXML
	private Button buttonA;

	@Inject
	protected DiagramController() {

	}

	public void setStateTransitionManager(StateTransitionManager stateTransitionManager) {
		this.stateTransitionManager = stateTransitionManager;

		if (stateTransitionManager instanceof NozzleHeightStateTransitionManager) {
			ReadOnlyDoubleProperty zcoProperty = ((NozzleHeightStateTransitionManager) stateTransitionManager).getZcoProperty();
			if (calibrationTextField != null) {
				calibrationTextField.setText(String.format("%1.2f", zcoProperty.get()));
			}
			setupZCoListener(zcoProperty);
		}
		if (stateTransitionManager instanceof SingleNozzleHeightStateTransitionManager) {
			ReadOnlyDoubleProperty zcoProperty = ((SingleNozzleHeightStateTransitionManager) stateTransitionManager).getZcoProperty();
			if (calibrationTextField != null) {
				calibrationTextField.setText(String.format("%1.2f", zcoProperty.get()));
			}
			setupZCoListener(zcoProperty);
		}
		if (stateTransitionManager instanceof NozzleOpeningStateTransitionManager) {
			ReadOnlyFloatProperty bPositionProperty = ((NozzleOpeningStateTransitionManager) stateTransitionManager).getBPositionProperty();
			if (BPosition != null) {
				BPosition.setText(String.format("%1.2f", bPositionProperty.get()));
			}
			setupBPositionListener(bPositionProperty);
		}
	}

	@FXML
	void buttonAAction(ActionEvent event) {
		stateTransitionManager.followTransition(StateTransitionManager.GUIName.A_BUTTON);
	}

	@FXML
	void buttonBAction(ActionEvent event) {
		stateTransitionManager.followTransition(StateTransitionManager.GUIName.B_BUTTON);
	}

	@FXML
	void upButtonAction(ActionEvent event) {
		stateTransitionManager.followTransition(StateTransitionManager.GUIName.UP);
	}

	@FXML
	void downButtonAction(ActionEvent event) {
		stateTransitionManager.followTransition(StateTransitionManager.GUIName.DOWN);
	}

	ChangeListener<Number> zcoListener = (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
		LOGGER.debug("zco listener fired");
		if (calibrationTextField != null) {
			LOGGER.debug("set zco text to " + String.format("%1.2f", newValue));
			calibrationTextField.setText(String.format("%1.2f", newValue));
			if (newValue.floatValue() <= 0f) {
				buttonA.setDisable(true);
			}
			else {
				buttonA.setDisable(false);
			}
		}
	};

	ChangeListener<Number> bPositionListener = (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
		if (BPosition != null) {
			BPosition.setText(String.format("%1.2f", newValue));
		}
	};

	protected void setupZCoListener(ReadOnlyDoubleProperty zcoProperty) {
		zcoProperty.removeListener(zcoListener);
		if (calibrationTextField != null) {
			LOGGER.debug("add zco listener");
			calibrationTextField.setText(String.format("%1.2f", zcoProperty.get()));
			zcoProperty.addListener(zcoListener);
		}
	}

	protected void setupBPositionListener(ReadOnlyFloatProperty bPositionProperty) {
		bPositionProperty.removeListener(bPositionListener);
		if (BPosition != null) {
			LOGGER.debug("add zco listener");
			BPosition.setText(String.format("%1.2f", bPositionProperty.get()));
			bPositionProperty.addListener(bPositionListener);
		}
	}

	public void initialize() {
		setupOffsetCombos();
	}

	private void setupOffsetCombos() {
		if (cmbXOffset != null) {
			cmbXOffset.getItems().add("A");
			cmbXOffset.getItems().add("B");
			cmbXOffset.getItems().add("C");
			cmbXOffset.getItems().add("D");
			cmbXOffset.getItems().add("E");
			cmbXOffset.getItems().add("F");
			cmbXOffset.getItems().add("G");
			cmbXOffset.getItems().add("H");
			cmbXOffset.getItems().add("I");
			cmbXOffset.getItems().add("J");
			cmbXOffset.getItems().add("K");
			cmbYOffset.getItems().add("1");
			cmbYOffset.getItems().add("2");
			cmbYOffset.getItems().add("3");
			cmbYOffset.getItems().add("4");
			cmbYOffset.getItems().add("5");
			cmbYOffset.getItems().add("6");
			cmbYOffset.getItems().add("7");
			cmbYOffset.getItems().add("8");
			cmbYOffset.getItems().add("9");
			cmbYOffset.getItems().add("10");
			cmbYOffset.getItems().add("11");

			cmbXOffset.valueProperty().addListener(
					(ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
						if (stateTransitionManager != null) {
							((XAndYStateTransitionManager) stateTransitionManager).setXOffset(
									newValue.toString());
						}
					});

			cmbYOffset.valueProperty().addListener(
					(ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
						if (stateTransitionManager != null) {
							((XAndYStateTransitionManager) stateTransitionManager).setYOffset(
									Integer.parseInt(newValue.toString()));
						}
					});

			cmbXOffset.setValue("F");
			cmbYOffset.setValue("6");

			xOffsetA.setText(xOffsetA.getText() + ":");
			yOffsetA.setText(yOffsetA.getText() + ":");
		}
	}

	public void setScale(double requiredScale, Node rootNode) {
		rootNode.setScaleX(requiredScale);
		rootNode.setScaleY(requiredScale);

		double invertedScale = 1 / requiredScale;

		if (cmbXOffset != null) {
			xOffsetComboContainer.setScaleX(invertedScale);
			xOffsetComboContainer.setScaleY(invertedScale);
			yOffsetComboContainer.setScaleX(invertedScale);
			yOffsetComboContainer.setScaleY(invertedScale);
			xOffsetContainerB.setScaleX(invertedScale);
			xOffsetContainerB.setScaleY(invertedScale);
			xOffsetContainerC.setScaleX(invertedScale);
			xOffsetContainerC.setScaleY(invertedScale);
			yOffsetContainerB.setScaleX(invertedScale);
			yOffsetContainerB.setScaleY(invertedScale);
			yOffsetContainerC.setScaleX(invertedScale);
			yOffsetContainerC.setScaleY(invertedScale);

			incorrectAlignmentContainer.setScaleX(invertedScale);
			incorrectAlignmentContainer.setScaleY(invertedScale);
			perfectAlignmentContainer.setScaleX(invertedScale);
			perfectAlignmentContainer.setScaleY(invertedScale);
		}

		if (fineNozzleLbl != null) {

			fineNozzleLbl.setScaleX(invertedScale);
			fineNozzleLbl.setScaleY(invertedScale);
			fillNozzleLbl.setScaleX(invertedScale);
			fillNozzleLbl.setScaleY(invertedScale);
		}

		if (BPosition != null) {
			BPosition.setScaleX(invertedScale);
			BPosition.setScaleY(invertedScale);
		}
	}

}
