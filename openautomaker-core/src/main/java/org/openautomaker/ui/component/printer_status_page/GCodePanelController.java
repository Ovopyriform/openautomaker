package org.openautomaker.ui.component.printer_status_page;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.comms.commands.GCodeMacros;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterException;
import org.openautomaker.environment.preference.advanced.AdvancedModePreference;
import org.openautomaker.environment.preference.slicer.MacroPathPreference;
import org.openautomaker.javafx.FXProperty;
import org.openautomaker.ui.StatusInsetController;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.coreUI.components.RestrictedTextField;
import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class GCodePanelController implements StatusInsetController {

	private static final Logger LOGGER = LogManager.getLogger();

	private ListChangeListener<String> gcodeTranscriptListener = null;
	private Printer currentPrinter = null;

	@FXML
	private VBox gcodeEditParent;

	@FXML
	private RestrictedTextField gcodeEntryField;

	@FXML
	private ListView<String> gcodeTranscript;

	@FXML
	private Button sendGCodeButton;

	@FXML
	private HBox gcodePanel;

	@FXML
	void sendGCodeM(MouseEvent event) {
		fireGCodeAtPrinter();
	}

	@FXML
	void sendGCodeA(ActionEvent event) {
		fireGCodeAtPrinter();
	}

	private final AdvancedModePreference advancedModePreference;
	private final SelectedPrinter selectedPrinter;
	private final GCodeMacros gCodeMacros;
	private final MacroPathPreference macroPathPreference;

	@Inject
	protected GCodePanelController(
			AdvancedModePreference advancedModePreference,
			GCodeMacros gCodeMacros,
			SelectedPrinter selectedPrinter,
			MacroPathPreference macroPathPreference) {

		this.advancedModePreference = advancedModePreference;
		this.selectedPrinter = selectedPrinter;
		this.gCodeMacros = gCodeMacros;
		this.macroPathPreference = macroPathPreference;

	}

	//TODO: Convert to Optional<Path>
	Optional<String> getGCodeFileToUse(String text) {
		String macroFilename;
		Path gcodeFileWithPathApp;

		if (text.startsWith("!!")) {
			// with !! use scoring technique. Use current printer type
			// and head type. Optionally add #N0 or #N1 to macro name to specify a nozzle
			macroFilename = text.substring(2);

			GCodeMacros.NozzleUseIndicator nozzleUse = GCodeMacros.NozzleUseIndicator.DONT_CARE;

			int hashIx = macroFilename.indexOf('#');
			if (hashIx != -1) {
				String nozzleSelect = macroFilename.substring(hashIx + 2);
				if ("0".equals(nozzleSelect)) {
					nozzleUse = GCodeMacros.NozzleUseIndicator.NOZZLE_0;
				}
				else if ("1".equals(nozzleSelect)) {
					nozzleUse = GCodeMacros.NozzleUseIndicator.NOZZLE_1;
				}
				macroFilename = macroFilename.substring(0, hashIx);
			}

			try {
				gcodeFileWithPathApp = gCodeMacros.getFilename(macroFilename,
						Optional.of(currentPrinter.findPrinterType()),
						currentPrinter.headProperty().get().typeCodeProperty().get(),
						nozzleUse,
						GCodeMacros.SafetyIndicator.DONT_CARE);
			}
			catch (FileNotFoundException ex) {
				gcodeFileWithPathApp = null;
			}
		}
		else {
			macroFilename = text.substring(1);
			gcodeFileWithPathApp = macroPathPreference.getAppValue().resolve(macroFilename + ".gcode");
		}

		Path gcodeFileWithPathUser = macroPathPreference.getUserValue().resolve(macroFilename + ".gcode");

		if (gcodeFileWithPathUser.toFile().exists())
			return Optional.of(gcodeFileWithPathUser.toString());

		if (gcodeFileWithPathApp != null && gcodeFileWithPathApp.toFile().exists())
			return Optional.of(gcodeFileWithPathApp.toString());

		LOGGER.error("Failed to find macro: " + macroFilename);
		return Optional.empty();
	}

	protected void fireGCodeAtPrinter() {
		gcodeEntryField.selectAll();
		String text = gcodeEntryField.getText();

		if (text.startsWith("!")) {
			Optional<String> gcodeFileToUse = getGCodeFileToUse(text);

			//See if we can run a macro
			if (currentPrinter != null && gcodeFileToUse.isPresent()) {
				try {
					currentPrinter.executeGCodeFile(Paths.get(gcodeFileToUse.get()), false);
					currentPrinter.gcodeTranscriptProperty().add(text);
				}
				catch (PrinterException ex) {
					LOGGER.error("Failed to run macro: " + text, ex);
				}
			}
			else {
				LOGGER.error("Can't run requested macro: " + text);
			}
		}
		else if (!text.equals("")) {
			selectedPrinter.get().sendRawGCode(text.toUpperCase(), true);
		}
	}

	private void selectLastItemInTranscript() {
		gcodeTranscript.getSelectionModel().selectLast();
		gcodeTranscript.scrollTo(gcodeTranscript.getSelectionModel().getSelectedIndex());
	}

	private boolean suppressReactionToGCodeEntryChange = false;

	//TODO: This should probably be in the constructor.
	public void initialize() {
		BooleanProperty advancedModeProperty = FXProperty.bind(advancedModePreference);

		gcodeEntryField.disableProperty().bind(advancedModeProperty.not());
		sendGCodeButton.disableProperty().bind(advancedModeProperty.not());

		gcodeTranscriptListener = (ListChangeListener.Change<? extends String> change) -> {
			while (change.next()) {
			}

			suppressReactionToGCodeEntryChange = true;
			selectLastItemInTranscript();
			suppressReactionToGCodeEntryChange = false;
		};

		gcodeTranscript.selectionModelProperty().get().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				if (!suppressReactionToGCodeEntryChange) {
					gcodeEntryField.setText(gcodeTranscript.getSelectionModel().getSelectedItem());
				}
			}
		});

		// Set up printer and detect changes
		currentPrinter = selectedPrinter.get();
		selectedPrinter.addListener(
				(ObservableValue<? extends Printer> ov, Printer oldValue, Printer newValue) -> {
					if (currentPrinter != null) {
						currentPrinter.gcodeTranscriptProperty().removeListener(gcodeTranscriptListener);
					}

					if (newValue != null) {
						gcodeTranscript.setItems(newValue.gcodeTranscriptProperty());
						newValue.gcodeTranscriptProperty().addListener(gcodeTranscriptListener);
					}
					else {
						gcodeTranscript.setItems(null);
					}
					currentPrinter = newValue;
				});

		gcodeEditParent.visibleProperty().bind(selectedPrinter.isNotNull());

		gcodeEntryField.addEventHandler(KeyEvent.KEY_PRESSED, (KeyEvent t) -> {
			if (t.getCode() == KeyCode.UP) {
				gcodeTranscript.getSelectionModel().selectPrevious();
				t.consume();
			}
			else if (t.getCode() == KeyCode.DOWN) {
				gcodeTranscript.getSelectionModel().selectNext();
				t.consume();
			}
		});

		gcodeTranscript.addEventHandler(KeyEvent.KEY_PRESSED, (KeyEvent t) -> {
			if (t.getCode() == KeyCode.ENTER) {
				fireGCodeAtPrinter();
			}
		});

		gcodeTranscript.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() > 1) {
					fireGCodeAtPrinter();
				}
			}
		});
	}
}
