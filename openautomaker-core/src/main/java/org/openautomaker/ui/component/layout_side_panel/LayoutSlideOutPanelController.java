
package org.openautomaker.ui.component.layout_side_panel;

import java.net.URL;
import java.util.ResourceBundle;

import org.openautomaker.base.printerControl.PrintJob;

import celtech.appManager.ModelContainerProject;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 *
 * @author Ian
 */
public class LayoutSlideOutPanelController implements Initializable {

	private ModelContainerProject currentProject = null;

	@FXML
	private Label lastModifiedDate;

	@FXML
	private ListView printHistory;

	private final ListChangeListener<PrintJob> printJobChangeListener = new ListChangeListener<>() {
		@Override
		public void onChanged(ListChangeListener.Change<? extends PrintJob> c) {
		}
	};

	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}

	/**
	 *
	 * @param currentProject
	 */
	public void bindLoadedModels(final ModelContainerProject currentProject) {
		this.currentProject = currentProject;
		lastModifiedDate.textProperty().unbind();
		lastModifiedDate.textProperty().bind(currentProject.getLastModifiedDate().asString());

		printHistory.getItems().clear();
	}
}
