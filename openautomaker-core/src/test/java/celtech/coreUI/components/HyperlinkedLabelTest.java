package celtech.coreUI.components;

import static javafx.scene.paint.Color.BLACK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openautomaker.test_library.GuiceExtension;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.utils.FXUtils;

import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

@ExtendWith({ GuiceExtension.class, ApplicationExtension.class })
public class HyperlinkedLabelTest {

	private HyperlinkedLabel hyperLinkedLabel;

	@Start
	public void start(Stage stage) {
		hyperLinkedLabel = new HyperlinkedLabel();

		stage.setScene(new Scene(new StackPane(hyperLinkedLabel), 500, 500, BLACK));
		stage.setMaximized(true);

		stage.show();
	}

	@Test
	public void testReplaceText_plaintextOnly() throws Exception {
		String newText = "Some plain text";

		FXUtils.runAndWait(() -> {
			hyperLinkedLabel.replaceText(newText);
		});

		assertEquals(1, hyperLinkedLabel.getChildren().size());
		assertTrue(hyperLinkedLabel.getChildren().get(0) instanceof Text);
		assertEquals(((Text) hyperLinkedLabel.getChildren().get(0)).getText(), newText);
	}

	@Test
	public void testReplaceText_plaintextAndHyperlink() throws Exception {
		String newText = "Robox firmware update <a href=\"https://robox.freshdesk.com/solution/categories/1000090870/folders/1000214277/articles/1000180224-the-filament-isn-t-moving-as-expected\">Other article</a>";
		String expectedTextContent = "Robox firmware update ";
		String expectedHyperlinkContent = "Other article";

		FXUtils.runAndWait(() -> {
			hyperLinkedLabel.replaceText(newText);
		});

		assertEquals(2, hyperLinkedLabel.getChildren().size());
		assertTrue(hyperLinkedLabel.getChildren().get(0) instanceof Text);
		assertTrue(hyperLinkedLabel.getChildren().get(1) instanceof Hyperlink);
		assertEquals(((Text) hyperLinkedLabel.getChildren().get(0)).getText(), expectedTextContent);
		assertEquals(((Hyperlink) hyperLinkedLabel.getChildren().get(1)).getText(), expectedHyperlinkContent);
	}

	@Test
	public void testReplaceText_plaintextAndTwoHyperlinks() throws Exception {
		String newText = "Robox firmware update <a href=\"https://robox.freshdesk.com/support/home\">Robox solutions</a>more text<a href=\"https://robox.freshdesk.com/solution/categories/1000090870/folders/1000214277/articles/1000180224-the-filament-isn-t-moving-as-expected\">Other article</a>";

		String expectedTextContent1 = "Robox firmware update ";
		String expectedTextContent2 = "more text";
		String expectedHyperlinkContent1 = "Robox solutions";
		String expectedHyperlinkContent2 = "Other article";

		FXUtils.runAndWait(() -> {
			hyperLinkedLabel.replaceText(newText);
		});

		assertEquals(4, hyperLinkedLabel.getChildren().size());
		assertTrue(hyperLinkedLabel.getChildren().get(0) instanceof Text);
		assertTrue(hyperLinkedLabel.getChildren().get(1) instanceof Hyperlink);
		assertTrue(hyperLinkedLabel.getChildren().get(2) instanceof Text);
		assertTrue(hyperLinkedLabel.getChildren().get(3) instanceof Hyperlink);
		assertEquals(((Text) hyperLinkedLabel.getChildren().get(0)).getText(), expectedTextContent1);
		assertEquals(((Hyperlink) hyperLinkedLabel.getChildren().get(1)).getText(), expectedHyperlinkContent1);
		assertEquals(((Text) hyperLinkedLabel.getChildren().get(2)).getText(), expectedTextContent2);
		assertEquals(((Hyperlink) hyperLinkedLabel.getChildren().get(3)).getText(), expectedHyperlinkContent2);
	}

	@Test
	public void testReplaceText_HyperlinkOnly() throws Exception {
		String newText = "<a href=\"https://robox.freshdesk.com/solution/categories/1000090870/folders/1000214277/articles/1000180224-the-filament-isn-t-moving-as-expected\">Other article</a>";
		String expectedHyperlinkContent = "Other article";

		FXUtils.runAndWait(() -> {
			hyperLinkedLabel.replaceText(newText);
		});

		assertEquals(1, hyperLinkedLabel.getChildren().size());
		assertTrue(hyperLinkedLabel.getChildren().get(0) instanceof Hyperlink);
		assertEquals(((Hyperlink) hyperLinkedLabel.getChildren().get(0)).getText(), expectedHyperlinkContent);
	}

	@Test
	public void testReplaceText_HyperlinkInMiddleOfText() throws Exception {
		final String NEW_TEXT = "PRECEDING TEXT <a href=\"https://example.web.page/home\">LINK</a> FOLLOWING TEXT";

		final String EXPECTED_TEXT_CONTENT_1 = "PRECEDING TEXT ";
		final String EXPECTED_TEXT_CONTENT_2 = " FOLLOWING TEXT";
		final String EXPECTED_HYPERLINK_CONTENT = "LINK";

		FXUtils.runAndWait(() -> {
			hyperLinkedLabel.replaceText(NEW_TEXT);
		});

		assertEquals(3, hyperLinkedLabel.getChildren().size());
		assertTrue(hyperLinkedLabel.getChildren().get(0) instanceof Text, "The first element of the TextFlow should be plain text");
		assertTrue(hyperLinkedLabel.getChildren().get(1) instanceof Hyperlink, "The second element of the TextFlow should be a hyperlink");
		assertTrue(hyperLinkedLabel.getChildren().get(2) instanceof Text, "The third element of the TextFlow should be plain text");

		assertEquals(((Text) hyperLinkedLabel.getChildren().get(0)).getText(), EXPECTED_TEXT_CONTENT_1);
		assertEquals(((Hyperlink) hyperLinkedLabel.getChildren().get(1)).getText(), EXPECTED_HYPERLINK_CONTENT);
		assertEquals(((Text) hyperLinkedLabel.getChildren().get(2)).getText(), EXPECTED_TEXT_CONTENT_2);
	}
}
