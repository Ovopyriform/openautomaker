package org.openautomaker.gcodeviewer.inject;

import org.openautomaker.gcodeviewer.engine.GCodeViewerConfiguration;

import com.google.inject.AbstractModule;

public class GCodeViewerModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(GCodeViewerConfiguration.class).to(GCodeViewerConfiguration.class);
	}
}
