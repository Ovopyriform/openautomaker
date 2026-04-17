package org.openautomaker.ui.component.notification;

import org.openautomaker.base.notification_manager.NotificationType;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public class ConditionalNotificationBar extends AppearingNotificationBar {

	private ObservableValue<Boolean> appearanceCondition;

	private final ChangeListener<Boolean> conditionChangeListener = new ChangeListener<>() {
		@Override
		public void changed(
				ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			if (Platform.isFxApplicationThread()) {
				calculateVisibility();
			}
			else {
				Platform.runLater(() -> {
					calculateVisibility();
				});
			}
		}
	};

	@Inject
	private I18N i18n;

	@Inject
	private NotificationDisplay notificationDisplay;

	public ConditionalNotificationBar(String message, NotificationType notificationType) {

		GuiceContext.get().injectMembers(this);

		notificationDescription.replaceText(i18n.t(message));
		setType(notificationType);
	}

	public void clearAppearanceCondition() {
		if (appearanceCondition != null) {
			appearanceCondition.removeListener(conditionChangeListener);
		}
		appearanceCondition = null;
		startSlidingOutOfView();
	}

	public ObservableValue<Boolean> getAppearanceCondition() {
		return appearanceCondition;
	}

	public void setAppearanceCondition(BooleanBinding appearanceCondition) {
		if (this.appearanceCondition != null) {
			this.appearanceCondition.removeListener(conditionChangeListener);
		}
		this.appearanceCondition = appearanceCondition;
		this.appearanceCondition.addListener(conditionChangeListener);
		calculateVisibility();
	}

	private void calculateVisibility() {
		if (appearanceCondition.getValue()) {
			show();
		}
		else {
			startSlidingOutOfView();
		}
	}

	@Override
	public void show() {
		notificationDisplay.addStepCountedNotificationBar(this);
		startSlidingInToView();
	}

	@Override
	public void finishedSlidingIntoView() {
	}

	@Override
	public void finishedSlidingOutOfView() {
		notificationDisplay.removeStepCountedNotificationBar(this);
	}

	@Override
	public boolean isSameAs(AppearingNotificationBar bar) {
		boolean theSame = false;
		if (this.getType() == bar.getType()
				&& this.notificationDescription.getText().equals(bar.notificationDescription.getText())) {
			theSame = true;
		}

		return theSame;
	}

	@Override
	public void destroyBar() {
		clearAppearanceCondition();
		finishedSlidingOutOfView();
	}
}
