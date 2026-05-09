package org.openautomaker.root.ui.inject;

import java.util.Locale;
import java.util.prefs.PreferenceChangeListener;

import org.openautomaker.environment.LocaleProvider;
import org.openautomaker.root.ui.GRootCommandLineArgs;
import org.openautomaker.root.ui.remote.RootServer;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import jakarta.inject.Singleton;

public class GRootModule extends AbstractModule {

	private final GRootCommandLineArgs args;

	public GRootModule(GRootCommandLineArgs args) {
		this.args = args;
	}

	@Override
	protected void configure() {
		Locale locale = Locale.forLanguageTag(args.languageTag);
		bind(LocaleProvider.class).toInstance(new LocaleProvider() {
			@Override
			public Locale getValue() {
				return locale;
			}

			@Override
			public void addChangeListener(PreferenceChangeListener pcl) {
			}
		});
	}

	@Provides
	@Singleton
	public RootServer provideRootServer() {
		String configDir = args.configurationDirectory.isBlank() ? args.installDirectory : args.configurationDirectory;
		RootServer server = new RootServer(args.hostName, args.portNumber, configDir);
		server.initPIN(args.pin);
		server.startUpdating(args.updateInterval);
		return server;
	}
}
