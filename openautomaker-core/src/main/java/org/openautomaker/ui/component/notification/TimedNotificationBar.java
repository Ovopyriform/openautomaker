package org.openautomaker.ui.component.notification;

import java.util.Timer;
import java.util.TimerTask;

import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.guice.GuiceContext;

import jakarta.inject.Inject;

public class TimedNotificationBar extends AppearingNotificationBar {

	private final int displayFor_ms = 4000;
	private final int selfDestructIn_ms = 6000;
	private Timer selfDestructTimer = null;

	private final NotificationDisplay notificationDisplay;

	@Inject
	private TaskExecutor taskExecutor;

	protected TimedNotificationBar(NotificationDisplay notificationDisplay) {

		GuiceContext.get().injectMembers(this);

		this.notificationDisplay = notificationDisplay;

	}

	@Override
	public void show() {
		notificationDisplay.addNotificationBar(this);
		selfDestructTimer = new Timer("TimedNotificationSelfDestruct", true);
		startSlidingInToView();
		selfDestructTimer.schedule(new SelfDestructTask(), selfDestructIn_ms);
	}

	@Override
	public void finishedSlidingIntoView() {
		Timer putItAwayTimer = new Timer("TimedNotificationDisposer", true);
		putItAwayTimer.schedule(new SlideAwayTask(), displayFor_ms);
	}

	@Override
	public void finishedSlidingOutOfView() {
		notificationDisplay.removeNotificationBar(this);
		if (selfDestructTimer != null) {
			selfDestructTimer.cancel();
			selfDestructTimer = null;
		}
	}

	private class SlideAwayTask extends TimerTask {

		@Override
		public void run() {
			startSlidingOutOfView();
		}
	}

	private class SelfDestructTask extends TimerTask {

		@Override
		public void run() {
			taskExecutor.runOnGUIThread(() -> {
				finishedSlidingOutOfView();
			});
		}
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
		finishedSlidingOutOfView();
	}

}
