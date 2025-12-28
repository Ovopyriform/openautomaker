package org.openautomaker.ui.component.printer_status_page;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openautomaker.environment.I18N;
import org.openautomaker.mock.printer_control.model.MockPrinter;
import org.openautomaker.mock.printer_control.model.MockPrinterFactory;
import org.openautomaker.test_library.GuiceExtension;
import org.openautomaker.ui.state.SelectedPrinter;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

@ExtendWith({ GuiceExtension.class, ApplicationExtension.class })
public class GCodePanelTest {

	private static Logger LOGGER = LogManager.getLogger();

	GCodePanelController controller;

	@Inject
	private SelectedPrinter selectedPrinter;

	@Inject
	private MockPrinterFactory mockPrinterFactory;

	@Inject
	I18N i18n;

	@Inject
	FXMLLoader fxmlLoader;

	@Start
	public void start(Stage stage) throws IOException {
		MockPrinter printer = mockPrinterFactory.create();
		selectedPrinter.set(printer);

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
