package org.openautomaker.base.configuration.datafileaccessors;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.environment.preference.printer.PrinterDefsPathPreference;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

@Singleton
public class PrinterContainer {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final String DOT_ROBOX_PRINTER = ".roboxprinter";

	private ObservableList<PrinterDefinitionFile> completePrinterList;
	private ObservableMap<String, PrinterDefinitionFile> completePrinterMap;

	private final ObjectMapper mapper = new ObjectMapper();

	public static final String defaultPrinterID = "RBX01";

	//    private PrinterContainer()
	//    {
	//		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	//
	//        completePrinterList = FXCollections.observableArrayList();
	//        completePrinterMap = FXCollections.observableHashMap();
	//
	//		Path printerDefsPath = new PrinterDefsPathPreference().getValue();
	//
	//		File[] printerFiles = printerDefsPath.toFile().listFiles(new FileFilter() {
	//			@Override
	//			public boolean accept(File file) {
	//				return file.getPath().endsWith(DOT_ROBOX_PRINTER);
	//			}
	//		});
	//
	//		if (printerFiles == null)
	//			LOGGER.error("Could not load printer definitions from: " + printerDefsPath.toString());
	//
	//		List<PrinterDefinitionFile> printerDefs = ingestPrinters(printerFiles);
	//		completePrinterList = FXCollections.observableList(printerDefs);
	//    }

	@Inject
	protected PrinterContainer(
			PrinterDefsPathPreference printerDefsPathPreference) {

		completePrinterList = FXCollections.observableArrayList();
		completePrinterMap = FXCollections.observableHashMap();

		Path printerDefsPath = printerDefsPathPreference.getValue();

		File[] printerFiles = printerDefsPath.toFile().listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.getPath().endsWith(DOT_ROBOX_PRINTER);
			}
		});

		if (printerFiles == null)
			LOGGER.error("Could not load printer definitions from: " + printerDefsPath.toString());

		List<PrinterDefinitionFile> printerDefs = ingestPrinters(printerFiles);
		completePrinterList = FXCollections.observableList(printerDefs);
	}

	private List<PrinterDefinitionFile> ingestPrinters(File[] printerDefFiles) {
		List<PrinterDefinitionFile> printerList = new ArrayList<>();

		if (printerDefFiles == null)
			return printerList;

		for (File printerDefFile : printerDefFiles) {

			try {
				PrinterDefinitionFile printerData = mapper.readValue(printerDefFile, PrinterDefinitionFile.class);

				printerList.add(printerData);
				completePrinterMap.put(printerData.typeCode, printerData);
			}
			catch (IOException ex) {
				LOGGER.error("Error loading printer " + printerDefFile.getAbsolutePath());
			}
		}

		return printerList;
	}

	//	@Deprecated
	//	public static PrinterContainer getInstance() {
	//		return instance;
	//	}

	//TODO: Should not be static
	public PrinterDefinitionFile getPrinterByID(String printerID) {
		PrinterDefinitionFile returnedPrinter = completePrinterMap.get(printerID);
		return returnedPrinter;
	}

	//TODO: should not be static
	public ObservableList<PrinterDefinitionFile> getCompletePrinterList() {
		return completePrinterList;
	}
}
