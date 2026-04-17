package org.openautomaker.ui.component.root_table_cell;

import org.openautomaker.base.comms.print_server.PrintServerConnection;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 *
 * @author George Salter
 */
public class RootCameraTableCell extends TableCell<PrintServerConnection, Boolean> {
	private final ImageView imageContainer;
	private final Image connectedImage;
	private final Image disconnectedImage;

	public RootCameraTableCell() {
		imageContainer = new ImageView();
		connectedImage = new Image(getClass().getResourceAsStream("/org/openautomaker/ui/images/webcam.png"));
		disconnectedImage = new Image(getClass().getResourceAsStream("/org/openautomaker/ui/images/webcam_disconnected.png"));
		setAlignment(Pos.CENTER);
	}

	@Override
	protected void updateItem(Boolean cameraDetected, boolean empty) {
		super.updateItem(cameraDetected, empty);
		if (cameraDetected != null && !empty) {
			setGraphic(imageContainer);
			if (cameraDetected) {
				imageContainer.setImage(connectedImage);
			}
			else {
				imageContainer.setImage(disconnectedImage);
			}
		}
		else {
			setGraphic(null);
		}
	}
}
