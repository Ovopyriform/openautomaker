package org.openautomaker.base.inject;

import org.openautomaker.base.notification_manager.DevNullNotificationManager;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.task_executor.DevNullTaskExecutor;
import org.openautomaker.base.task_executor.TaskExecutor;

public class BaseTestModule extends BaseModule {

	@Override
	protected void overrideBindings() {
		bind(TaskExecutor.class).to(DevNullTaskExecutor.class);
		bind(SystemNotificationManager.class).to(DevNullNotificationManager.class);
	}
}
