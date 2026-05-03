package org.openautomaker.ui.component.printer_side_panel.printer;

import static javafx.scene.paint.Color.BLACK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openautomaker.ui.component.printer_side_panel.printer.svg.PrinterSVGTest.EXPECTED_STATUS_ICON_ID;
import static org.openautomaker.ui.component.printer_side_panel.printer.white_progress_bar.WhiteProgressBarTest.CLEAR_BAR_ID;
import static org.openautomaker.ui.component.printer_side_panel.printer.white_progress_bar.WhiteProgressBarTest.SOLID_BAR_ID;
import static org.testfx.assertions.api.Assertions.assertThat;
import static org.testfx.assertions.impl.Adapter.fromMatcher;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.printerControl.model.PrinterIdentity;
import org.openautomaker.test_library.GuiceExtension;
import org.openautomaker.ui.component.printer_side_panel.ComponentIsolationInterface;
import org.openautomaker.ui.component.printer_side_panel.printer.PrinterComponent.Size;
import org.openautomaker.ui.component.printer_side_panel.printer.PrinterComponent.Status;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.utils.FXUtils;
import org.testfx.matcher.base.NodeMatchers;

import celtech.roboxbase.comms.remote.PauseStatus;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;

@ExtendWith({ GuiceExtension.class, MockitoExtension.class, ApplicationExtension.class })
public class PrinterComponentTest {

	private static final String INNER_PANE_ID = "#innerPane";
	private static final String ROOT_NAME_BOX_ID = "#rootNameBox";
	private static final String PROGRESS_BAR_ID = "#progressBar";

	private static final int SELECTED_OFFSET = 6;

	private static Map<PrinterStatus, Status> EXPECTED_PRINTER_STATUS_COMPONENT_STATUS = Map.of(
			PrinterStatus.IDLE, Status.READY,
			PrinterStatus.REMOVING_HEAD, Status.READY,
			PrinterStatus.OPENING_DOOR, Status.READY,
			PrinterStatus.RUNNING_MACRO_FILE, Status.PRINTING,
			PrinterStatus.RUNNING_TEST, Status.READY,
			PrinterStatus.PRINTING_PROJECT, Status.PRINTING,
			PrinterStatus.CALIBRATING_NOZZLE_ALIGNMENT, Status.PRINTING,
			PrinterStatus.CALIBRATING_NOZZLE_OPENING, Status.PRINTING,
			PrinterStatus.CALIBRATING_NOZZLE_HEIGHT, Status.PRINTING,
			PrinterStatus.PURGING_HEAD, Status.PRINTING);

	private static Map<PauseStatus, Boolean> EXPECTED_PAUSED_STATUS_VISIBLE = Map.of(
			PauseStatus.NOT_PAUSED, Boolean.FALSE,
			PauseStatus.PAUSE_PENDING, Boolean.TRUE,
			PauseStatus.PAUSED, Boolean.TRUE,
			PauseStatus.RESUME_PENDING, Boolean.FALSE,
			PauseStatus.SELFIE_PAUSE, Boolean.TRUE);

	private ComponentIsolationInterface mockContainer = mock(ComponentIsolationInterface.class);

	@Mock
	private Printer mockPrinter;

	private SimpleObjectProperty<PrinterStatus> printerStatusProperty = new SimpleObjectProperty<>(PrinterStatus.IDLE);
	private SimpleObjectProperty<PauseStatus> pauseStatusProperty = new SimpleObjectProperty<>(PauseStatus.NOT_PAUSED);

	PrinterComponent printerComponent;

	@Start
	void start(Stage stage) {
		PrinterDefinitionFile config = new PrinterDefinitionFile(1, "RBX10", null, null, null, 0, 0, 0);
		PrinterIdentity identity = new PrinterIdentity();
		identity.printerColourProperty().set(Color.BLUE);
		identity.printerFriendlyNameProperty().set("Mock RBX10");

		when(mockPrinter.printerConfigurationProperty()).thenReturn(new SimpleObjectProperty<>(config));
		when(mockPrinter.getPrinterIdentity()).thenReturn(identity);
		when(mockPrinter.printerStatusProperty()).thenReturn(printerStatusProperty);
		when(mockPrinter.pauseStatusProperty()).thenReturn(pauseStatusProperty);
		when(mockPrinter.getCurrentErrors()).thenReturn(FXCollections.observableArrayList());

		printerComponent = new PrinterComponent(mockPrinter, mockContainer);

		stage.setScene(new Scene(new StackPane(printerComponent), 500, 500, BLACK));
		stage.setMaximized(true);

		stage.show();
	}

	@Test
	// Checks the component responds correctly to printer status updates
	void printerStatusUpdate_test(FxRobot robot) throws Exception {
		FXUtils.runAndWait(() -> {
			printerComponent.setSize(Size.SIZE_LARGE);
		});

		List.of(PrinterStatus.values()).forEach((status) -> {
			try {
				FXUtils.runAndWait(() -> {
					printerStatusProperty.set(status);
				});

				String statusIcon = EXPECTED_STATUS_ICON_ID.get(EXPECTED_PRINTER_STATUS_COMPONENT_STATUS.get(status));
				assertThat(robot.lookup(statusIcon).queryAs(Pane.class)).isVisible();
			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

		// Check all paused statuses
		FXUtils.runAndWait(() -> {
			printerStatusProperty.set(PrinterStatus.PRINTING_PROJECT);
		});
		List.of(PauseStatus.values()).forEach((pauseStatus) -> {
			try {
				FXUtils.runAndWait(() -> {
					pauseStatusProperty.set(pauseStatus);
				});

				assertThat(robot.lookup(EXPECTED_STATUS_ICON_ID.get(Status.PAUSED)).queryAs(Pane.class))
						.is(fromMatcher(EXPECTED_PAUSED_STATUS_VISIBLE.get(pauseStatus).equals(Boolean.TRUE) ? NodeMatchers.isVisible() : NodeMatchers.isInvisible()));
			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}

	@Test
	void setProgress_test(FxRobot robot) throws Exception {
		FXUtils.runAndWait(() -> {
			printerStatusProperty.set(PrinterStatus.PRINTING_PROJECT);
			printerComponent.setSize(Size.SIZE_LARGE);
			printerComponent.setSelected(true);
			printerComponent.setProgress(0.5);
		});

		//Check the bar is displayed
		assertThat(robot.lookup(PROGRESS_BAR_ID).queryAs(Pane.class)).isVisible();

		// Check the bar width is correct
		Bounds solidBar = robot.lookup(SOLID_BAR_ID).queryAs(Polygon.class).getBoundsInLocal();
		Bounds clearBar = robot.lookup(CLEAR_BAR_ID).queryAs(Polygon.class).getBoundsInLocal();
		assertEquals(solidBar.getWidth(), clearBar.getWidth());

		//TODO: Enhance to check 0% and 100%?
	}

	//@Test
	void setSelected_test(FxRobot robot) {

		List.of(Size.values()).forEach((size) -> {
			try {
				FXUtils.runAndWait(() -> {
					printerComponent.setSize(size);
					printerComponent.setSelected(true); // Causes redraw
				});

				int expectedSize = size.getSize() - SELECTED_OFFSET;

				Pane innerPane = robot.lookup(INNER_PANE_ID).queryAs(Pane.class);
				assertEquals(expectedSize, innerPane.getMinWidth());
				assertEquals(expectedSize, innerPane.getMaxWidth());
				assertEquals(expectedSize, innerPane.getMinHeight());
				assertEquals(expectedSize, innerPane.getMaxHeight());

				Pane rootNameBox = robot.lookup(ROOT_NAME_BOX_ID).queryAs(Pane.class);
				assertEquals(expectedSize, rootNameBox.getPrefWidth());
				assertEquals(expectedSize, rootNameBox.getPrefHeight());
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	//@Test
	void setSize_test(FxRobot robot) throws Exception {

		List.of(Size.values()).forEach((size) -> {
			try {
				FXUtils.runAndWait(() -> {
					printerComponent.setSize(size);
				});


				int expectedSize = size.getSize();

				assertEquals(expectedSize, printerComponent.getMinWidth());
				assertEquals(expectedSize, printerComponent.getMaxWidth());
				assertEquals(expectedSize, printerComponent.getMinHeight());
				assertEquals(expectedSize, printerComponent.getMaxHeight());

				assertEquals(expectedSize, printerComponent.getPrefHeight());
				assertEquals(expectedSize, printerComponent.getPrefHeight());

				Pane innerPane = robot.lookup(INNER_PANE_ID).queryAs(Pane.class);
				assertEquals(expectedSize, innerPane.getMinWidth());
				assertEquals(expectedSize, innerPane.getMaxWidth());
				assertEquals(expectedSize, innerPane.getMinHeight());
				assertEquals(expectedSize, innerPane.getMaxHeight());

				Pane rootNameBox = robot.lookup(ROOT_NAME_BOX_ID).queryAs(Pane.class);
				assertEquals(expectedSize, rootNameBox.getPrefWidth());
				assertEquals(expectedSize, rootNameBox.getPrefHeight());
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}
