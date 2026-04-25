package org.openautomaker.ui.project.adapter;

import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectFactory;
import org.openautomaker.ui.inject.project.ProjectFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProjectBridgeFactory implements IProjectFactory {

	private final ProjectFactory delegate;

	@Inject
	public ProjectBridgeFactory(ProjectFactory delegate) {
		this.delegate = delegate;
	}

	@Override
	public IProject create() {
		return new ProjectBridge(delegate.create());
	}
}
