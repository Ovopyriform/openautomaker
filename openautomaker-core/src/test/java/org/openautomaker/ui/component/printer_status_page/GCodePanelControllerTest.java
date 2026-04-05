package org.openautomaker.ui.component.printer_status_page;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.PrinterType;
import org.openautomaker.test_library.GuiceExtension;
import org.openautomaker.ui.state.SelectedPrinter;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

@ExtendWith({ GuiceExtension.class, ApplicationExtension.class })
public class GCodePanelControllerTest {

	GCodePanelController controller;

	@Inject
	private SelectedPrinter selectedPrinter;

	@Inject
	FXMLLoader fxmlLoader;

	Printer mockPrinter;

	@Start
	public void start(Stage stage) throws IOException {
		mockPrinter = mock(Printer.class);
		when(mockPrinter.findPrinterType()).thenReturn(PrinterType.ROBOX_PRO);

		//Mock Head property to avoid NullPointerException when controller tries to access it
		Head mockHead = mock(Head.class);
		when(mockHead.typeCodeProperty()).thenReturn(new SimpleStringProperty("N1"));
		when(mockPrinter.headProperty()).thenReturn(new SimpleObjectProperty<Head>(mockHead));
		when(mockPrinter.gcodeTranscriptProperty()).thenReturn(FXCollections.observableArrayList());

		selectedPrinter.set(mockPrinter);

		fxmlLoader.setLocation(getClass().getResource("GCodePanel.fxml"));
		VBox gCodePanel = fxmlLoader.load();
		controller = fxmlLoader.getController();

		stage.setScene(new Scene(new StackPane(gCodePanel), 500, 500, Color.DARKGRAY));
		stage.setMaximized(true);

		stage.show();
	}

	private Optional<String> convertBackslashes(Optional<String> path) {
		// On Windows, paths can have a mixture of forward slashes and
		// backslashes. Convert them all to forward slashes.
		if (path.isPresent())
			return Optional.of(path.get().replace("\\", "/"));
		else
			return path;
	}

	@ParameterizedTest
	@MethodSource("provideGetGCodeFileToUseData")
	public void getGCodeFileToUse_test(String key, String expected) throws Exception {
		Optional<String> fileToUse = convertBackslashes(controller.getGCodeFileToUse(key));
		assertTrue(fileToUse.get().endsWith(expected));
	}

	private static Stream<Arguments> provideGetGCodeFileToUseData() {
		return Stream.of(
				Arguments.of("!!Home_all", "macros/Home_all.gcode"),
				Arguments.of("!!Remove_Head#N1", "macros/RBX10/Remove_Head.gcode"),
				Arguments.of("!!Short_Purge#N0", "macros/RBX10/Short_Purge#N0.gcode"),
				Arguments.of("!!Short_Purge#N1", "macros/RBX10/Short_Purge#N1.gcode"),
				Arguments.of("!Short_Purge#N1", "macros/Short_Purge#N1.gcode"),
				Arguments.of("!RBX10/Short_Purge#N1", "macros/RBX10/Short_Purge#N1.gcode"),
				Arguments.of("!PurgeMaterial#RBX01-DM#NB", "macros/PurgeMaterial#RBX01-DM#NB.gcode"),
				Arguments.of("!PurgeMaterial", "macros/PurgeMaterial.gcode"));
	}
}
