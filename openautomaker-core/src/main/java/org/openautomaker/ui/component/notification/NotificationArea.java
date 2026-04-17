package org.openautomaker.ui.component.notification;

import jakarta.inject.Inject;
import javafx.scene.layout.VBox;

public class NotificationArea extends VBox {

	@Inject
	public NotificationArea(NotificationDisplay notificationDisplay, ProgressDisplay progressDisplay) {
		this.getChildren().add(notificationDisplay);
		this.getChildren().add(progressDisplay);
		setPickOnBounds(false);
	}
}
