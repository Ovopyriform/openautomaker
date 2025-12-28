package org.openautomaker.ui.component.printer_status_page;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.postprocessor.PrintJobStatistics;
import org.openautomaker.base.printerControl.PrintJob;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.ui.StatusInsetController;
import org.openautomaker.ui.state.SelectedPrinter;

import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * FXML Controller class
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
public class ProjectPanelController implements StatusInsetController {

	private static final Logger LOGGER = LogManager.getLogger();

	@FXML
	private VBox projectPanel;

	@FXML
	private Label projectName;

	@FXML
	private Label profileName;

	@FXML
	private Label layerHeight;

	private Printer currentPrinter = null;
	private ChangeListener<PrintJob> printJobChangeListener = (ObservableValue<? extends PrintJob> ov, PrintJob t, PrintJob printJob) -> {
		updateDisplay(printJob);
	};

	private final SelectedPrinter selectedPrinter;

	@Inject
	protected ProjectPanelController(
			SelectedPrinter selectedPrinter) {

		this.selectedPrinter = selectedPrinter;

	}

	private void updateDisplay(PrintJob printJob) {
		if (printJob != null) {
			try {
				NumberFormat threeDPformatter = NumberFormat.getNumberInstance(Locale.UK);
				threeDPformatter.setMaximumFractionDigits(3);
				threeDPformatter.setGroupingUsed(false);

				PrintJobStatistics stats = printJob.getStatistics();
				projectName.setText(stats.getProjectName());
				profileName.setText(stats.getProfileName());
				layerHeight.setText(threeDPformatter.format(stats.getLayerHeight()));
				projectPanel.setVisible(true);
			}
			catch (IOException ex) {
				projectPanel.setVisible(false);
				projectName.setText("");
				profileName.setText("");
				layerHeight.setText("");
				LOGGER.debug("Unable to retrieve project name");
			}
		}
		else {
			projectPanel.setVisible(false);
			projectName.setText("");
			profileName.setText("");
			layerHeight.setText("");
		}
	}

	/**
	 * Initialises the controller class.
	 */
	public void initialize() {
		selectedPrinter.addListener((ObservableValue<? extends Printer> ov, Printer lastPrinter, Printer newPrinter) -> {
			if (currentPrinter != null) {
				currentPrinter.getPrintEngine().printJobProperty().removeListener(printJobChangeListener);
			}

			if (newPrinter == null) {
				projectPanel.setVisible(false);
				currentPrinter = null;
			}
			else {
				currentPrinter = newPrinter;
				newPrinter.getPrintEngine().printJobProperty().addListener(printJobChangeListener);
				updateDisplay(newPrinter.getPrintEngine().printJobProperty().get());
			}
		});

		projectPanel.setVisible(false);
	}
}
