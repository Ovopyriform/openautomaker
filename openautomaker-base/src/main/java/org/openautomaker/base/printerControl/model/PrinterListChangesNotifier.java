package org.openautomaker.base.printerControl.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 * PrinterListChangesNotifier listens to a list of printers and notifies registered listeners about the following events: - Printer added - Printer removed - Head added to printer - Head removed from printer - Reel added to printer (with reel index)
 * - Reel removed from printer (with reel index) - Printer Identity changed To be done: - Filament detected on extruder (with extruder index) - Filament removed from extruder (with extruder index)
 *
 * @author tony
 */
public class PrinterListChangesNotifier {

	private final List<PrinterListChangesListener> listeners = new ArrayList<>();
	private final Map<Printer, PrinterChangesListener> printerListeners = new HashMap<>();
	private final Map<Printer, PrinterChangesNotifier> printerNotifiers = new HashMap<>();

	public PrinterListChangesNotifier(ObservableList<Printer> printers) {

		printers.addListener((ListChangeListener.Change<? extends Printer> change) -> {
			while (change.next()) {
				if (change.wasAdded()) {
					for (Printer printer : change.getAddedSubList()) {

						fireWhenPrinterAdded(printer);
						setupPrinterChangesNotifier(printer);
					}
					continue;
				}

				if (change.wasRemoved()) {
					for (Printer printer : change.getRemoved()) {
						fireWhenPrinterRemoved(printer);
						removePrinterChangesNotifier(printer);
					}
					continue;
				}

				if (change.wasReplaced())
					continue;

				if (change.wasUpdated())
					continue;
			}
		});
	}

	private void fireWhenPrinterRemoved(Printer printer) {
		for (PrinterListChangesListener listener : new ArrayList<>(listeners)) {

			ObservableMap<Integer, Reel> reels = printer.reelsProperty();
			if (reels != null) {
				for (Entry<Integer, Reel> reel : reels.entrySet()) {
					listener.whenReelRemoved(printer, reel.getValue(), reel.getKey());
				}
			}

			ObservableObjectValue<Head> head = printer.headProperty();
			if (head != null && head.get() != null) {
				listener.whenHeadRemoved(printer, head.get());
			}

			ObservableList<Extruder> extruders = printer.extrudersProperty();
			if (extruders != null) {
				for (Extruder extruder : extruders) {
					if (extruder.isFittedProperty().get()) {
						int extruderIndex = extruders.indexOf(extruder);
						listener.whenExtruderRemoved(printer, extruderIndex);
					}
				}
			}

			listener.whenPrinterRemoved(printer);
		}
	}

	private void setupPrinterChangesNotifier(Printer printer) {
		PrinterChangesNotifier printerChangesNotifier = new PrinterChangesNotifier(printer);
		PrinterChangesListener printerChangesListener = new PrinterChangesListener() {

			@Override
			public void whenHeadAdded() {
				fireWhenHeadAdded(printer);
			}

			@Override
			public void whenHeadRemoved(Head head) {
				fireWhenHeadRemoved(printer, head);
			}

			@Override
			public void whenReelAdded(int reelIndex, Reel reel) {
				fireWhenReelAdded(printer, reelIndex);
			}

			@Override
			public void whenReelRemoved(int reelIndex, Reel reel) {
				fireWhenReelRemoved(printer, reel, reelIndex);
			}

			@Override
			public void whenReelChanged(Reel reel) {
				fireWhenReelChanged(printer, reel);
			}

			@Override
			public void whenExtruderAdded(int extruderIndex) {
				fireWhenExtruderAdded(printer, extruderIndex);
			}

			@Override
			public void whenExtruderRemoved(int extruderIndex) {
				fireWhenExtruderRemoved(printer, extruderIndex);
			}

		};
		printerListeners.put(printer, printerChangesListener);
		printerNotifiers.put(printer, printerChangesNotifier);
		printerChangesNotifier.addListener(printerChangesListener);
	}

	private void removePrinterChangesNotifier(Printer printer) {
		printerNotifiers.get(printer).removeListener(printerListeners.get(printer));
	}

	private void fireWhenPrinterAdded(Printer printer) {
		List<PrinterListChangesListener> listenerList = new ArrayList<>();
		for (PrinterListChangesListener listener : listeners) {
			listenerList.add(listener);
		}

		for (PrinterListChangesListener listener : listenerList) {
			listener.whenPrinterAdded(printer);
			if (printer.headProperty().get() != null) {
				listener.whenHeadAdded(printer);
			}

			printer.reelsProperty().entrySet().stream().forEach((mappedReel) -> {
				listener.whenReelAdded(printer, mappedReel.getKey());
			});

			for (int extruderIndex = 0; extruderIndex < printer.extrudersProperty().size(); extruderIndex++) {
				if (printer.extrudersProperty().get(extruderIndex).isFittedProperty().get()) {
					listener.whenExtruderAdded(printer, extruderIndex);
				}
			}
		}
	}

	public void addListener(PrinterListChangesListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(PrinterListChangesListener listener) {
		this.listeners.remove(listener);
	}

	private void fireWhenHeadAdded(Printer printer) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenHeadAdded(printer);
		}
	}

	private void fireWhenHeadRemoved(Printer printer, Head head) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenHeadRemoved(printer, head);
		}
	}

	private void fireWhenReelAdded(Printer printer, int reelIndex) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenReelAdded(printer, reelIndex);
		}
	}

	private void fireWhenReelRemoved(Printer printer, Reel reel, int reelIndex) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenReelRemoved(printer, reel, reelIndex);
		}
	}

	private void fireWhenReelChanged(Printer printer, Reel reel) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenReelChanged(printer, reel);
		}
	}

	private void fireWhenExtruderAdded(Printer printer, int extruderIndex) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenExtruderAdded(printer, extruderIndex);
		}
	}

	private void fireWhenExtruderRemoved(Printer printer, int extruderIndex) {
		List<PrinterListChangesListener> listToIterateThrough = listeners.stream().collect(Collectors.toList());
		for (PrinterListChangesListener listener : listToIterateThrough) {
			listener.whenExtruderRemoved(printer, extruderIndex);
		}
	}
}
