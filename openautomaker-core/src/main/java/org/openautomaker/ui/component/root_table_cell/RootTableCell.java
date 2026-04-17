package org.openautomaker.ui.component.root_table_cell;

import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.comms.print_server.PrintServerConnection.ServerStatus;

import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 *
 * @author Ian
 */
public class RootTableCell extends TableCell<PrintServerConnection, ServerStatus> {

	private ImageView imageContainer;
	private Image connectedImage;
	private Image disconnectedImage;

	public RootTableCell() {
		imageContainer = new ImageView();
		connectedImage = new Image(getClass().getResourceAsStream("/org/openautomaker/ui/images/plug_connected.png"));
		disconnectedImage = new Image(getClass().getResourceAsStream("/org/openautomaker/ui/images/plug_disconnected.png"));
	}

	@Override
	protected void updateItem(ServerStatus item, boolean empty) {
		super.updateItem(item, empty);
		if (item != null && !empty) {
			setGraphic(imageContainer);
			if (item == ServerStatus.CONNECTED || item == ServerStatus.UPGRADING) {
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
