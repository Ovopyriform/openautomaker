
package org.openautomaker.base.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openautomaker.base.printerControl.model.Extruder;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterChangesListener;
import org.openautomaker.base.printerControl.model.PrinterChangesNotifier;
import org.openautomaker.base.printerControl.model.Reel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PrinterChangesNotifierTest {

	@Mock
	Printer printer;

	@Mock
	Head mockHead;

	@Mock
	Reel mockReel;

	SimpleObjectProperty<Head> headProperty = new SimpleObjectProperty<>();
	ObservableMap<Integer, Reel> reelsProperty = FXCollections.observableHashMap();
	ObservableList<Extruder> extrudersProperty = FXCollections.observableArrayList(new Extruder("E"), new Extruder("D"));
	BooleanProperty reelDataChangedToggle = new SimpleBooleanProperty(false);

	@BeforeEach
	void setup() {
		when(printer.headProperty()).thenReturn(headProperty);
		when(printer.reelsProperty()).thenReturn(reelsProperty);
		when(printer.extrudersProperty()).thenReturn(extrudersProperty);
	}

	@Test
	public void testWhenHeadAdded() {
		PrinterChangesNotifier notifier = new PrinterChangesNotifier(printer);
		TestPrinterChangesListener listener = new TestPrinterChangesListener();
		notifier.addListener(listener);

		headProperty.set(mockHead);

		assertTrue(listener.headAdded);
	}

	@Test
	public void testWhenHeadRemoved() {
		PrinterChangesNotifier notifier = new PrinterChangesNotifier(printer);
		TestPrinterChangesListener listener = new TestPrinterChangesListener();
		notifier.addListener(listener);

		headProperty.set(mockHead);
		headProperty.set(null);

		assertTrue(listener.headAdded);
	}

	@Test
	public void testWhenReelAdded() {
		when(mockReel.dataChangedToggleProperty()).thenReturn(reelDataChangedToggle);

		PrinterChangesNotifier notifier = new PrinterChangesNotifier(printer);
		TestPrinterChangesListener listener = new TestPrinterChangesListener();
		notifier.addListener(listener);

		reelsProperty.put(0, mockReel);

		assertTrue(listener.reel0Added);
	}

	@Test
	public void testWhenReelRemoved() {
		when(mockReel.dataChangedToggleProperty()).thenReturn(reelDataChangedToggle);

		PrinterChangesNotifier notifier = new PrinterChangesNotifier(printer);
		TestPrinterChangesListener listener = new TestPrinterChangesListener();
		notifier.addListener(listener);

		reelsProperty.put(0, mockReel);
		reelsProperty.remove(0);

		assertTrue(listener.reel0Removed);
	}

	@Test
	public void testWhenReelChanged() {
		when(mockReel.dataChangedToggleProperty()).thenReturn(reelDataChangedToggle);

		PrinterChangesNotifier notifier = new PrinterChangesNotifier(printer);
		TestPrinterChangesListener listener = new TestPrinterChangesListener();
		notifier.addListener(listener);

		reelsProperty.put(0, mockReel);
		reelDataChangedToggle.set(!reelDataChangedToggle.get());

		assertTrue(listener.reel0Changed);
	}

	private static class TestPrinterChangesListener implements PrinterChangesListener {

		public boolean headAdded = false;
		public boolean headRemoved = false;
		public boolean reel0Added = false;
		public boolean reel0Removed = false;
		public boolean reel0Changed = false;

		@Override
		public void whenHeadAdded() {
			headAdded = true;
		}

		@Override
		public void whenHeadRemoved(Head head) {
			headRemoved = true;
		}

		@Override
		public void whenReelAdded(int reelIndex, Reel reel) {
			reel0Added = true;
		}

		@Override
		public void whenReelRemoved(int reelIndex, Reel reel) {
			reel0Removed = true;
		}

		@Override
		public void whenReelChanged(Reel reel) {
			reel0Changed = true;
		}

		@Override
		public void whenExtruderAdded(int extruderIndex) {
		}

		@Override
		public void whenExtruderRemoved(int extruderIndex) {
		}

	}

}
