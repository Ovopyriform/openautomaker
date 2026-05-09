package org.openautomaker.root.inject;

import org.openautomaker.base.inject.BaseModule;
import org.openautomaker.base.task_executor.HeadlessTaskExecutor;
import org.openautomaker.base.task_executor.TaskExecutor;

import com.google.inject.AbstractModule;

public class RootModule extends AbstractModule {

	@Override
	protected void configure() {
		install(new BaseModule() {
			@Override
			protected void overrideBindings() {
				bind(TaskExecutor.class).to(HeadlessTaskExecutor.class);
			}
		});
	}
}
