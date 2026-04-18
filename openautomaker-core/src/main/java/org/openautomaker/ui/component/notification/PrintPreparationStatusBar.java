
package org.openautomaker.ui.component.notification;

import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.slicer.SafetyFeaturesPreference;
import celtech.appManager.GCodeGeneratorManager;
import celtech.appManager.ModelContainerProject;
import celtech.appManager.Project;
import jakarta.inject.Inject;
import javafx.beans.value.ChangeListener;

public class PrintPreparationStatusBar extends AppearingProgressBar {
	private Printer printer = null;
	private Project project;

	private GCodeGeneratorManager gCodeGenManager = null;

	@Inject
	private SafetyFeaturesPreference safetyFeaturesPreference;

	@Inject
	private I18N i18n;

	private final ChangeListener<Boolean> serviceStatusListener = (observable, newValue, oldValue) -> {
		reassessStatus();
	};

	private final ChangeListener<Number> serviceProgressListener = (observable, newValue, oldValue) -> {
		reassessStatus();
	};


	public PrintPreparationStatusBar() {
		getStyleClass().add("secondaryStatusBar");
	}


	public void bindToPrinter(Printer printer) {
		this.printer = printer;
		printer.getPrintEngine().transferGCodeToPrinterService.runningProperty().addListener(serviceStatusListener);
		printer.getPrintEngine().transferGCodeToPrinterService.progressProperty().addListener(serviceProgressListener);

		cancelButton.setOnAction((t) -> {
			try {
				if (gCodeGenManager != null)
					gCodeGenManager.cancelPrintOrSaveTask();
				if (printer.canCancelProperty().get())
					printer.cancel(null, safetyFeaturesPreference.getValue());
			}
			catch (PrinterException ex) {
				System.out.println("Couldn't resume print");
			}
		});

		if (project != null) {
			reassessStatus();
		}
	}

	public void bindToProject(Project project) {
		this.project = project;

		if (project instanceof ModelContainerProject) {
			gCodeGenManager = project.getGCodeGenManager();
			if (gCodeGenManager != null) {
				gCodeGenManager.selectedTaskRunningProperty().addListener(serviceStatusListener);
				gCodeGenManager.selectedTaskProgressProperty().addListener(serviceProgressListener);
			}
		}

		if (printer != null) {
			reassessStatus();
		}
	}

	@Override
	public void initialize() {
		super.initialize();
		targetLegendRequired(false);
		targetValueRequired(false);
		currentValueRequired(false);
		progressRequired(true);
		layerDataRequired(false);
	}

	private void reassessStatus() {
		boolean showBar = false;

		if (gCodeGenManager != null && gCodeGenManager.printOrSaveTaskRunningProperty().get() && gCodeGenManager.selectedTaskRunningProperty().get()) {
			largeProgressDescription.setText(gCodeGenManager.getSelectedTaskMessage());
			progressBar.setProgress(gCodeGenManager.selectedTaskProgressProperty().get());
			cancelButton.visibleProperty().set(true);
			showBar = true;
		}
		else {
			cancelButton.visibleProperty().set(true);
		}

		if (printer != null && printer.getPrintEngine().transferGCodeToPrinterService.runningProperty().get()) {
			largeProgressDescription.setText(i18n.t("printerStatus.sendingToPrinter"));
			progressBar.setProgress(printer.getPrintEngine().transferGCodeToPrinterService.getProgress());
			//Cancel is provided from the print bar in this mode
			cancelButton.visibleProperty().set(false);
			showBar = true;
		}

		if (showBar) {
			startSlidingInToView();
		}
		else {
			startSlidingOutOfView();
		}
	}

	public void unbindAll() {
		unbindFromProject();
		unbindFromPrinter();
		// Hide the bar if it is currently shown.
		startSlidingOutOfView();
	}

	public void unbindFromPrinter() {
		if (printer != null) {
			printer.getPrintEngine().transferGCodeToPrinterService.runningProperty().removeListener(serviceStatusListener);
			printer.getPrintEngine().transferGCodeToPrinterService.progressProperty().removeListener(serviceProgressListener);
			printer = null;
		}
	}

	public void unbindFromProject() {
		if (project != null) {
			gCodeGenManager = project.getGCodeGenManager();
			if (gCodeGenManager != null) {
				gCodeGenManager.selectedTaskRunningProperty().removeListener(serviceStatusListener);
				gCodeGenManager.selectedTaskProgressProperty().removeListener(serviceProgressListener);
			}
			project = null;
		}
	}
}
