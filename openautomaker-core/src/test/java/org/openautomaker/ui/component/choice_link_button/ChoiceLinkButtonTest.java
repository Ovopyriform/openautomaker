package org.openautomaker.ui.component.choice_link_button;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openautomaker.test_library.GuiceExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.utils.FXUtils;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

//TODO: Write this test
@ExtendWith({ GuiceExtension.class, ApplicationExtension.class })
public class ChoiceLinkButtonTest {

	private static final String TEST_TEXT_0 = "test text 0";
	private static final String TEST_TEXT_1 = "test text 1";

	private ChoiceLinkButton choiceLinkButton;

	@Start
	public void start(Stage stage) {

		choiceLinkButton = new ChoiceLinkButton();
		//controller = gCodePanel.getController();
		stage.setScene(new Scene(new StackPane(choiceLinkButton), 500, 500, Color.DARKGRAY));
		//stage.setMaximized(true);

		stage.show();
	}

	//	@Test
	//	public void checkLoad() throws InterruptedException {
	//		Thread.sleep(1000);
	//	}

	@Test
	public void setTitleMessage_test(FxRobot robot) throws Exception {
		FXUtils.runAndWait(() -> {
			choiceLinkButton.setTitle(TEST_TEXT_0);
			choiceLinkButton.setMessage(TEST_TEXT_0);
		});

		Thread.sleep(2000);

		FXUtils.runAndWait(() -> {
			choiceLinkButton.setTitle(TEST_TEXT_1);
			choiceLinkButton.setMessage(TEST_TEXT_1);
		});

		//Thread.sleep(1000);
	}
}
