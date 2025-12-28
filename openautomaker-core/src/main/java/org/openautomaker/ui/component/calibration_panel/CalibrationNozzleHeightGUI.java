
package org.openautomaker.ui.component.calibration_panel;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransition;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionManager;
import org.openautomaker.base.printerControl.model.statetransitions.StateTransitionManager.GUIName;
import org.openautomaker.base.printerControl.model.statetransitions.calibration.NozzleHeightCalibrationState;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;

import celtech.configuration.ApplicationConfiguration;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;

/**
 *
 * @author tony
 */
public class CalibrationNozzleHeightGUI {

	private static final Logger LOGGER = LogManager.getLogger(
			CalibrationNozzleHeightGUI.class.getName());

	private CalibrationInsetPanelController controller;
	StateTransitionManager<NozzleHeightCalibrationState> stateManager;
	Map<GUIName, Region> namesToButtons = new HashMap<>();

	@Inject
	private I18N i18n;

	public CalibrationNozzleHeightGUI(CalibrationInsetPanelController controller,
			StateTransitionManager<NozzleHeightCalibrationState> stateManager) {

		GuiceContext.get().injectMembers(this);

		this.controller = controller;
		this.stateManager = stateManager;

		stateManager.stateGUITProperty().addListener(new ChangeListener() {
			@Override
			public void changed(ObservableValue observable, Object oldValue, Object newValue) {
				setState((NozzleHeightCalibrationState) newValue);
			}
		});
		populateNamesToButtons(controller);
	}

	private void showAppropriateButtons(NozzleHeightCalibrationState state) {
		controller.hideAllInputControlsExceptStepNumber();
		if (state.showCancelButton()) {
			controller.cancelCalibrationButton.setVisible(true);
		}
		for (StateTransition<NozzleHeightCalibrationState> allowedTransition : this.stateManager.getTransitions()) {
			if (namesToButtons.containsKey(allowedTransition.getGUIName())) {
				namesToButtons.get(allowedTransition.getGUIName()).setVisible(true);
			}
		}
	}

	public void setState(NozzleHeightCalibrationState state) {
		LOGGER.debug("GUI going to state " + state);
		controller.calibrationStatus.replaceText(i18n.t(state.getKey()));
		showAppropriateButtons(state);
		if (state.getDiagramName().isPresent()) {
			URL fxmlURL = getClass().getResource(
					ApplicationConfiguration.fxmlDiagramsResourcePath
							+ "nozzleheight" + "/" + state.getDiagramName().get());

			controller.showDiagram(fxmlURL);
		}
		int stepNo = 0;
		switch (state) {
			case IDLE:
				break;
			case INITIALISING:
				controller.calibrationMenu.disableNonSelectedItems();
				stepNo = 1;
				break;
			case HEATING:
				controller.showSpinner();
				stepNo = 2;
				break;
			case HEAD_CLEAN_CHECK:
				stepNo = 3;
				break;
			case MEASURE_Z_DIFFERENCE:
				stepNo = 4;
				break;
			case INSERT_PAPER:
				stepNo = 5;
				break;
			case PROBING:
				stepNo = 6;
				break;
			case BRING_BED_FORWARD:
				break;
			case REPLACE_PEI_BED:
				stepNo = 7;
				break;
			case DONE:
				break;
			case CANCELLED:
				controller.resetMenuAndGoToChoiceMode();
				break;
			case FINISHED:
				controller.calibrationMenu.reset();
				break;
			case FAILED:
				controller.calibrationMenu.enableNonSelectedItems();
				break;
		}
		if (stepNo != 0) {
			controller.stepNumber.setText(String.format(i18n.t("calibrationPanel.stepXOf7"), stepNo));
		}
	}

	private void populateNamesToButtons(CalibrationInsetPanelController controller) {
		namesToButtons.put(GUIName.YES, controller.buttonA);
		namesToButtons.put(GUIName.NO, controller.buttonB);
		namesToButtons.put(GUIName.NEXT, controller.nextButton);
		namesToButtons.put(GUIName.RETRY, controller.retryPrintButton);
		namesToButtons.put(GUIName.START, controller.startCalibrationButton);
		namesToButtons.put(GUIName.BACK, controller.backToStatus);
	}

}
