
package org.openautomaker.base.utils;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterListChangesListener;
import org.openautomaker.base.printerControl.model.PrinterListChangesNotifier;
import org.openautomaker.mock.printer_control.model.MockPrinterFactory;
import org.openautomaker.test_library.GuiceExtension;

import jakarta.inject.Inject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@ExtendWith({GuiceExtension.class, MockitoExtension.class})
public class PrinterListChangesNotifierTest {

	@Inject
	MockPrinterFactory testPrinterFactory;

	@Mock
	PrinterListChangesListener mockListener;

	@Mock
	Printer mockPrinter;


	ObservableList<Printer> printers;
	PrinterListChangesNotifier notifier;

	@BeforeEach
	public void beforeEach() {
		printers = FXCollections.observableArrayList();
		notifier = new PrinterListChangesNotifier(printers);
		notifier.addListener(mockListener);
	}

	@Test
	public void testWhenPrinterAdded() {
		printers.add(mockPrinter);
		verify(mockListener).whenPrinterAdded(mockPrinter);
	}

	@Test
	public void testWhenPrinterAddedAndRemoved() {

		printers.add(mockPrinter);
		printers.remove(mockPrinter);

		verify(mockListener).whenPrinterAdded(mockPrinter);
		verify(mockListener).whenPrinterRemoved(mockPrinter);
	}
	
	// @Test
	// public void testWhenPrinterAddedThenHeadRemoved() throws PrinterException {
	// 	printers.add(mockPrinter);
	// 	mockPrinter.removeHead(null, true);
		
	// 	verify(mockListener).whenPrinterAdded(mockPrinter);
	// 	verify(mockListener).whenHeadAdded(mockPrinter);
	// 	verify(mockListener).whenHeadRemoved(eq(mockPrinter), any(Head.class));
	// }

	// @Test
	// public void testWhenPrinterAddedThenHeadRemovedWithThreePrinters() throws PrinterException {
	// 	Printer mockPrinter2 = mock(Printer.class);
	// 	Printer mockPrinter3 = mock(Printer.class);
	// 	printers.add(mockPrinter);
	// 	printers.add(mockPrinter2);
	// 	printers.add(mockPrinter3);
	// 	mockPrinter2.removeHead(null, true);

	// 	verify(mockListener).whenPrinterAdded(mockPrinter);
	// 	verify(mockListener).whenPrinterAdded(mockPrinter2);
	// 	verify(mockListener).whenPrinterAdded(mockPrinter3);
	// 	verify(mockListener).whenHeadRemoved(eq(mockPrinter2), any(Head.class));
	// }

	// Again, ecapsulation of the listeners is incorrect.  Tese the reel changes notifier separately from the printer list changes notifier.

	// @Test
	// public void testWhenPrinterAddedThenReelAdded() {
	// 	ObservableList<Printer> printers = FXCollections.observableArrayList();
	// 	PrinterListChangesNotifier notifier = new PrinterListChangesNotifier(printers);
	// 	notifier.addListener(mockListener);

	// 	MockPrinter printer = testPrinterFactory.create();
	// 	printers.add(printer);
	// 	printer.addReel(0);

	// 	verify(mockListener).whenPrinterAdded(printer);
	// 	verify(mockListener).whenReelAdded(printer, 0);
	// }

	// @Test
	// public void testWhenPrinterAddedThenReelRemoved() {
	// 	ObservableList<Printer> printers = FXCollections.observableArrayList();
	// 	PrinterListChangesNotifier notifier = new PrinterListChangesNotifier(printers);
	// 	notifier.addListener(mockListener);

	// 	MockPrinter printer = testPrinterFactory.create();
	// 	printers.add(printer);
	// 	printer.addReel(0);
	// 	printer.removeReel(0);

	// 	verify(mockListener).whenPrinterAdded(printer);
	// 	verify(mockListener).whenReelAdded(printer, 0);
	// 	verify(mockListener).whenReelRemoved(eq(printer), any(Reel.class), eq(0));
	// }

}
