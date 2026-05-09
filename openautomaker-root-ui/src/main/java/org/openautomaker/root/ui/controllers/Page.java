
package org.openautomaker.root.ui.controllers;

import org.openautomaker.root.ui.GRootServices;

import javafx.scene.control.Labeled;

import org.openautomaker.root.ui.remote.PrinterStatusResponse;
import org.openautomaker.root.ui.remote.RootPrinter;

/**
 *
 * @author Tony
 */
public interface Page {

	public void setRootStackController(RootStackController rootController);
	public void startUpdates();
	public void stopUpdates();
	public void displayPage(RootPrinter printer);
	public void hidePage();
	public boolean isVisible();

	default String secondsToHMS(int secondsInput) {
		int minutes = (int)Math.floor(secondsInput / 60);
		int seconds = (int)Math.floor(secondsInput - (minutes * 60));
		int hours = (int)Math.floor(minutes / 60);
		minutes = minutes - (60 * hours);

		String hms = String.format("%d:%02d:%02d", hours, minutes, seconds);
		return hms;
	}

	default void translateLabels(Labeled ... labels) {
		for (Labeled l : labels)
			l.setText(GRootServices.getI18N().t(l.getText()));
	}

	default String getStatusIcon(RootPrinter p, MachineDetails.OPACITY iconOpacity) {
		String typeCode = "";
		String printerColour = "White";
		if (p != null) {
			PrinterStatusResponse s = p.getCurrentStatusProperty().get();
			if (s != null) {
				typeCode = s.getPrinterTypeCode();
				printerColour = s.getPrinterWebColourString();
			}
		}
		return MachineDetails.getDetails(typeCode).getStatusIcon(printerColour, iconOpacity);
	}
}
