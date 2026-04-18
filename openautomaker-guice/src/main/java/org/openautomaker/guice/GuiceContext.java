package org.openautomaker.guice;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Supplier;

//import org.openautomaker.environment.I18N;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

public class GuiceContext {

	private final Object contextRoot;
	protected Injector injector;

	private final Supplier<Collection<Module>> modules;

	private static GuiceContext guiceContext;

	//Used to grab the context for classes which can't be injected.
	public static GuiceContext get() {
		return guiceContext;
	}

	/**
	 * Create the Guice context
	 * 
	 * @param contextRoot root object to inject
	 * @param modules     custom Guice modules
	 */
	public GuiceContext(Object contextRoot, Supplier<Collection<Module>> modules) {
		this.contextRoot = Objects.requireNonNull(contextRoot);
		this.modules = Objects.requireNonNull(modules);
		guiceContext = this;
	}

	/**
	 * Injects members into given instance
	 * 
	 * @param instance instance to inject members into
	 */
	public void injectMembers(Object obj) {
		injector.injectMembers(obj);
	}

	public static void inject(Object obj) {
		get().injectMembers(obj);
	}

	/**
	 * Create instance of given class
	 * 
	 * @param cls type
	 * @param <T> class type
	 * @return resulting instance
	 */
	public <T> T getInstance(Class<T> cls) {
		return injector.getInstance(cls);
	}

	/**
	 * Context initialisation
	 */
	public final void init() {
		Collection<Module> uniqueModules = new HashSet<>(this.modules.get());
		uniqueModules.add(new GuiceContextModule());
		uniqueModules.add(new FXModule());
		uniqueModules.add(new PostConstructModule());
		injector = Guice.createInjector(uniqueModules.toArray(new Module[0]));
		injectMembers(contextRoot);
	}

	/**
	 * Context disposal
	 */
	public final void dispose() {

	}

	//Allows injection of this GuiceContext
	private class GuiceContextModule extends AbstractModule {

		@Provides
		GuiceContext provideGuiceContext() {
			return GuiceContext.this;
		}
	}

	private class FXModule extends AbstractModule {

		@Provides
		HostServices provideHostServices() {

			if (contextRoot instanceof Application)
				return ((Application) contextRoot).getHostServices();

			return (new Application() {

				@Override
				public void start(Stage primaryStage) throws Exception {
					// TODO Auto-generated method stub
				}

			}).getHostServices();
		}

		@Override
		protected void configure() {
			bind(FXMLLoader.class).toProvider(FXMLLoaderProvider.class);
			bind(FXMLLoaderFactory.class).to(FXMLLoaderFactoryImpl.class);
		}
	}

}
