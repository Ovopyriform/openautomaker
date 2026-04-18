/*
 * Copyright 2015 CEL UK
 */
package org.openautomaker.ui.component.printer_side_panel;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.PrinterColourMap;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterConnection;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.base.printerControl.model.PrinterIdentity;
import org.openautomaker.base.printerControl.model.PrinterListChangesListener;
import org.openautomaker.base.printerControl.model.Reel;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.component.printer_id_dialog.PrinterIDDialog;
import org.openautomaker.ui.component.printer_side_panel.printer.PrinterComponent;
import org.openautomaker.ui.state.SelectedPrinter;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.ObservableList;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;

public class PrinterGrid extends FlowPane implements PrinterListChangesListener, ComponentIsolationInterface {
	private static final Logger LOGGER = LogManager.getLogger();

	private ObservableList<Printer> connectedPrinters;

	private final Map<Printer, PrinterComponent> printerComponentsByPrinter = new HashMap<>();

	@Inject
	private SelectedPrinter selectedPrinter;

	@Inject
	private PrinterColourMap printerColourMap;

	@Inject
	PrinterManager printerManager;

	public PrinterGrid() {

		GuiceContext.inject(this);

		final int width = 260;
		this.setPrefWidth(width);
		this.setMinWidth(width);
		this.setMaxWidth(width);
		this.setPrefHeight(120);
		this.setMaxHeight(260);
		this.setPrefWrapLength(261);

		try {
			connectedPrinters = printerManager.getConnectedPrinters();
			printerManager.getPrinterChangeNotifier().addListener(this);
		}
		catch (NoClassDefFoundError error) {
			// this should only happen in SceneBuilder
			connectedPrinters = new SimpleListProperty<>();
		}
		clearAndAddAllPrintersToGrid();
	}

	/**
	 * Add the given printer component to the given grid coordinates.
	 */
	private void addPrinterComponentToGrid(PrinterComponent printerComponent) {
		PrinterComponent.Size size;
		if (connectedPrinters.size() > 4) {
			size = PrinterComponent.Size.SIZE_SMALL;
		}
		else if (connectedPrinters.size() > 1) {
			size = PrinterComponent.Size.SIZE_MEDIUM;
		}
		else {
			size = PrinterComponent.Size.SIZE_LARGE;
		}

		printerComponent.setSize(size);
		this.setHgap(size.getSpacing());
		this.setVgap(size.getSpacing());
		this.getChildren().add(printerComponent);
	}

	private void removeAllPrintersFromGrid() {
		printerComponentsByPrinter.clear();
		this.getChildren().clear();
	}

	/**
	 * Remove the given printer from the display. Update the selected printer to one of the remaining printers.
	 */
	public void removePrinter(Printer printer) {
		//TODO: PrinterComponent factory
		PrinterComponent printerComponent = printerComponentsByPrinter.get(printer);
		this.getChildren().remove(printerComponent);
		printerComponentsByPrinter.remove(printer);
		actOnComponentInterruptible();
	}

	public final void clearAndAddAllPrintersToGrid() {
		removeAllPrintersFromGrid();

		if (connectedPrinters.size() > 0) {
			for (Printer printer : connectedPrinters) {
				PrinterComponent printerComponent = createPrinterComponentForPrinter(printer);
				addPrinterComponentToGrid(printerComponent);
			}

			actOnComponentInterruptible();
		}
		else {
			PrinterComponent printerComponent = createPrinterComponentForPrinter(null);
			addPrinterComponentToGrid(printerComponent);
		}
	}

	/**
	 * Create the PrinterComponent for the given printer and set up any listeners on component events.
	 */
	private PrinterComponent createPrinterComponentForPrinter(Printer printer) {
		PrinterComponent printerComponent = new PrinterComponent(printer, this);
		printerComponent.setOnMouseClicked((MouseEvent event) -> {
			handlePrinterClicked(event, printer);
		});
		printerComponentsByPrinter.put(printer, printerComponent);
		return printerComponent;
	}

	/**
	 * This is called when the user clicks on the printer component for the given printer, and handles click (select printer) and double-click (go to edit printer details).
	 *
	 * @param event
	 */
	private void handlePrinterClicked(MouseEvent event, Printer printer) {
		if (event.getClickCount() == 1) {
			selectPrinter(printer);
		}
		if (event.getClickCount() > 1) {
			showEditPrinterDetails(printer);
		}
	}

	private void selectPrinter(Printer printer) {
		Printer selPrinter = selectedPrinter.get();
		if (selPrinter != null) {
			PrinterComponent printerComponent = printerComponentsByPrinter.get(selPrinter);
			if (printerComponent != null) {
				printerComponent.setSelected(false);
			}
		}
		if (printer != null) {
			PrinterComponent printerComponent = printerComponentsByPrinter.get(printer);
			printerComponent.setSelected(true);
		}

		selectedPrinter.set(printer);
		actOnComponentInterruptible();
	}

	/**
	 * Show the printerIDDialog for the given printer.
	 */
	private void showEditPrinterDetails(Printer printer) {

		if (printer != null && printer.printerConnectionProperty().isNotEqualTo(PrinterConnection.OFFLINE).get()) {
			PrinterIDDialog printerIDDialog = new PrinterIDDialog();
			printerIDDialog.setPrinterToUse(printer);
			PrinterIdentity printerIdentity = printer.getPrinterIdentity();
			printerIDDialog.setChosenDisplayColour(printerColourMap.printerToDisplayColour(
					printerIdentity.printerColourProperty().get()));
			printerIDDialog.setChosenPrinterName(printerIdentity.printerFriendlyNameProperty().get());

			boolean okPressed = printerIDDialog.show();

			if (okPressed) {
				try {
					PrinterIdentity clonedID = printer.getPrinterIdentity().clone();
					clonedID.printerFriendlyNameProperty().set(printerIDDialog.getChosenPrinterName());
					clonedID.printerColourProperty().set(printerColourMap.displayToPrinterColour(
							printerIDDialog.getChosenDisplayColour()));
					printer.updatePrinterIdentity(clonedID);
				}
				catch (PrinterException ex) {
					LOGGER.error("Error writing printer ID");
				}
			}
		}
	}

	/**
	 * Select any one of the active printers. If there are no printers left then select 'null'
	 */
	private void selectOnePrinter() {
		if (connectedPrinters.size() > 0) {
			selectPrinter(connectedPrinters.get(0));
		}
		else {
			selectPrinter(null);
		}
	}

	@Override
	public void whenPrinterAdded(Printer printer) {
		clearAndAddAllPrintersToGrid();
		selectPrinter(printer);
	}

	@Override
	public void whenPrinterRemoved(Printer printer) {
		removePrinter(printer);
		clearAndAddAllPrintersToGrid();
		selectOnePrinter();
	}

	@Override
	public void whenHeadAdded(Printer printer) {
	}

	@Override
	public void whenHeadRemoved(Printer printer, Head head) {
	}

	@Override
	public void whenReelAdded(Printer printer, int reelIndex) {
	}

	@Override
	public void whenReelRemoved(Printer printer, Reel reel, int reelIndex) {
	}

	@Override
	public void whenReelChanged(Printer printer, Reel reel) {
	}

	@Override
	public void whenExtruderAdded(Printer printer, int extruderIndex) {
	}

	@Override
	public void whenExtruderRemoved(Printer printer, int extruderIndex) {
	}

	@Override
	public void interruptibilityUpdated(PrinterComponent component) {
		actOnComponentInterruptible();
	}

	private void actOnComponentInterruptible() {
		Printer selPrinter = selectedPrinter.get();
		if (selPrinter != null) {
			for (Entry<Printer, PrinterComponent> componentEntry : printerComponentsByPrinter.entrySet()) {
				if (componentEntry.getKey() == selPrinter) {
					componentEntry.getValue().setDisable(false);
				}
				else {
					PrinterComponent componentToExamine = printerComponentsByPrinter.get(selPrinter);
					if (componentToExamine != null && !componentToExamine.isInterruptible()) {
						componentEntry.getValue().setDisable(true);
					}
					else {
						componentEntry.getValue().setDisable(false);
					}
				}
			}
		}
	}
}
