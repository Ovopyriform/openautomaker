package org.openautomaker.ui.component.notification;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public class DismissableNotificationBar extends AppearingNotificationBar {

	private EventHandler<ActionEvent> dismissAction = new EventHandler<>() {
		@Override
		public void handle(ActionEvent t) {
			startSlidingOutOfView();
		}
	};

	private final NotificationDisplay notificationDisplay;

	public DismissableNotificationBar(NotificationDisplay notificationDisplay) {
		this.notificationDisplay = notificationDisplay;

		actionButton.setVisible(true);
		actionButton.setOnAction(dismissAction);
	}

	public DismissableNotificationBar(NotificationDisplay notificationDisplay, String buttonText) {
		this(notificationDisplay);

		actionButton.setText(buttonText);
	}

	@Override
	public void show() {
		notificationDisplay.addNotificationBar(this);
		startSlidingInToView();
	}

	@Override
	public void finishedSlidingIntoView() {
	}

	@Override
	public void finishedSlidingOutOfView() {
		notificationDisplay.removeNotificationBar(this);
	}

	@Override
	public boolean isSameAs(AppearingNotificationBar bar) {
		boolean theSame = false;
		if (this.getType() == bar.getType()
				&& this.notificationDescription.getText().equals(bar.notificationDescription.getText())
				&& this.notificationType == bar.notificationType
				&& actionButton.getText().equals(bar.actionButton.getText())) {
			theSame = true;
		}

		return theSame;
	}

	@Override
	public void destroyBar() {
		finishedSlidingOutOfView();
	}
}
