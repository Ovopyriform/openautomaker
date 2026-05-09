package org.openautomaker.root;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.device.PrinterManager;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterListChangesListener;
import org.openautomaker.base.printerControl.model.PrinterListChangesNotifier;
import org.openautomaker.base.printerControl.model.Reel;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.environment.OpenAutomakerEnv;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PrinterRegistry implements PrinterListChangesListener {

	private static final Logger LOGGER = LogManager.getLogger();

	private static volatile PrinterRegistry instance;

	private final Map<String, Printer> remotePrinters = new ConcurrentHashMap<>();
	private final List<String> remotePrinterIDs = new ArrayList<>();

	@Inject
	public PrinterRegistry(PrinterManager printerManager) {
		instance = this;
		PrinterListChangesNotifier notifier = printerManager.getPrinterChangeNotifier();
		if (notifier != null)
			notifier.addListener(this);
	}

	public static PrinterRegistry getInstance() {
		return instance;
	}

	public Map<String, Printer> getRemotePrinters() {
		return remotePrinters;
	}

	public List<String> getRemotePrinterIDs() {
		return remotePrinterIDs;
	}

	@Override
	public void whenPrinterAdded(Printer printer) {
		if (!remotePrinters.containsValue(printer)) {
			String printerID = null;
			do {
				printerID = SystemUtils.generate16DigitID();
			} while (remotePrinters.containsKey(printerID));

			LOGGER.info("New printer detected - id is " + printerID);
			synchronized (this) {
				remotePrinters.put(printerID, printer);
				remotePrinterIDs.add(printerID);
			}
		}
	}

	@Override
	public void whenPrinterRemoved(Printer printer) {
		for (Entry<String, Printer> printerEntry : remotePrinters.entrySet()) {
			if (printerEntry.getValue() == printer) {
				remotePrinters.remove(printerEntry.getKey());
				remotePrinterIDs.remove(printerEntry.getKey());
				LOGGER.info("Printer with id " + printerEntry.getKey() + " removed");
				break;
			}
		}
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

	public String getServerName() {
		return System.getProperty(OpenAutomakerEnv.ROOT_SERVER_NAME);
	}

	public void setServerName(String serverName) {
		System.setProperty(OpenAutomakerEnv.ROOT_SERVER_NAME, serverName);
	}
}
