package celtech.coreUI.controllers.panels;

import java.net.URL;
import java.util.ResourceBundle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.ui.SlidablePanel;

import celtech.coreUI.controllers.SlideOutHandleController;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
@Deprecated
public class PrinterStatusSlideOutPanelController implements Initializable, SlidablePanel {

	private static final Logger LOGGER = LogManager.getLogger(
			PrinterStatusSlideOutPanelController.class.getName());

	@FXML
	private SlideOutHandleController SlideOutHandleController;

	@Override
	public void initialize(URL url, ResourceBundle rb) {
	}

	@Override
	public void slideIn() {
		SlideOutHandleController.slideIn();
	}

}
