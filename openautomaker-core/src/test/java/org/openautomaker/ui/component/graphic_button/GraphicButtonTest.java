package org.openautomaker.ui.component.graphic_button;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.openautomaker.test_library.GuiceExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.utils.FXUtils;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

@ExtendWith({ GuiceExtension.class, ApplicationExtension.class })
public class GraphicButtonTest {

	protected static final String ABOUT_BUTTON = "aboutButton";
	protected static final String PREFERENCES_BUTTON = "preferencesButton";

	private GridPane gridPane = null;

	@Start
	void start(Stage stage) {
		gridPane = new GridPane();

		stage.setScene(new Scene(gridPane, 300, 300));
		stage.setMaximized(true);

		stage.show();
	}

	@Test
	void getSetFxmlFileName_test(FxRobot robot) throws Exception {
		GraphicButton graphicButton = new GraphicButton(ABOUT_BUTTON);
		graphicButton.setFxmlFileName(ABOUT_BUTTON);

		FXUtils.runAndWait(() -> {
			gridPane.getChildren().add(graphicButton);
		});

		assertEquals(ABOUT_BUTTON, graphicButton.getFxmlFileName());
		assertEquals(ABOUT_BUTTON, graphicButton.getFxmlFileNameProperty().get());

		FXUtils.runAndWait(() -> {
			graphicButton.setFxmlFileName(PREFERENCES_BUTTON);
		});

		assertEquals(PREFERENCES_BUTTON, graphicButton.getFxmlFileName());
		assertEquals(PREFERENCES_BUTTON, graphicButton.getFxmlFileNameProperty().get());

	}

	@Test
	void graphicButtonLoad_test(FxRobot robot) throws Exception {
		FXUtils.runAndWait(() -> {

			List<Node> children = gridPane.getChildren();

			provideFXMLNames().forEach((arguments) -> {
				String fxmlFileName = (String) arguments.get()[0];
				GraphicButton graphicButton = new GraphicButton(fxmlFileName);
				children.add(graphicButton);
			});

		});

		//TODO: Work out a nice assert for this.  This doesn't work.
		//assertThat(robot.lookup(".graphic").queryAs(SVGPath.class)).isNotNull();
	}

	private static Stream<Arguments> provideFXMLNames() {
		return Stream.of(
				Arguments.of(ABOUT_BUTTON),
				Arguments.of("acceptTick"),
				Arguments.of("addCloudModelToProjectButton"),
				Arguments.of("addToProjectButton"),
				Arguments.of("backwardButton"),
				Arguments.of("calibrateButton"),
				Arguments.of("cancelButton"),
				Arguments.of("cancelCross"),
				Arguments.of("closeNozzleButton"),
				Arguments.of("copyButton"),
				Arguments.of("copyModelButton"),
				Arguments.of("copyTextButton"),
				Arguments.of("cutButton"),
				Arguments.of("deleteButton"),
				Arguments.of("deleteModelButton"),
				Arguments.of("ejectFilamentButton"),
				Arguments.of("fillNozzleButton"),
				Arguments.of("fineNozzleButton"),
				Arguments.of("forwardButton"),
				//Arguments.of("graphicButtonWithLabel"), // Looks like an unused fxml
				Arguments.of("groupButton"),
				Arguments.of("helpButton"),
				Arguments.of("homeAxesButton"),
				Arguments.of("layoutModelButton"),
				Arguments.of("libraryButton"),
				Arguments.of("lightsButton"),
				Arguments.of("loadModelButton"),
				Arguments.of("newButton"),
				Arguments.of("newsButton"),
				Arguments.of("nextButton"),
				Arguments.of("openNozzleButton"),
				Arguments.of("pauseButton"),
				Arguments.of("preferencesButton"),
				Arguments.of("previewButton"),
				Arguments.of("previewLoadingButton"),
				Arguments.of("printButton"),
				Arguments.of("purgeButton"),
				Arguments.of("redoButton"),
				Arguments.of("removeHeadButton"),
				Arguments.of("reprintButton"),
				Arguments.of("retryButton"),
				Arguments.of("saveAsButton"),
				Arguments.of("saveButton"),
				Arguments.of("startButton"),
				Arguments.of("undoButton"),
				Arguments.of("unlockDoorButton"),
				Arguments.of("writeToReel1Button"),
				Arguments.of("writeToReel2Button")
		);
	}
}
