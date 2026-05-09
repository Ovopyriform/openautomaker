package org.openautomaker.root;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.inject.EnvironmentModule;
import org.openautomaker.root.custom_dropwizard.AuthenticatedAssetsBundle;
import org.openautomaker.root.custom_dropwizard.ExternalAuthenticatedAssetsBundle;
import org.openautomaker.root.inject.RootModule;
import org.openautomaker.root.security.RootAPIAuthFilter;
import org.openautomaker.root.security.RootAPIAuthenticator;
import org.openautomaker.root.security.User;

import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.forms.MultiPartBundle;
import ru.vyarus.dropwizard.guice.GuiceBundle;

public class Root extends Application<RoboxRemoteConfiguration> {

	private static final Logger LOGGER = LogManager.getLogger();

	private static Root instance = null;
	private boolean isStopping = false;
	private boolean isUpgrading = false;

	public static void main(String[] args) throws Exception {
		instance = new Root();
		instance.run(args);
	}

	public static Root getInstance() {
		return instance;
	}

	@Override
	public String getName() {
		return "printerControl";
	}

	@Override
	public void initialize(Bootstrap<RoboxRemoteConfiguration> bootstrap) {
		bootstrap.addBundle(GuiceBundle.builder()
				.modules(new EnvironmentModule(), new RootModule())
				.extensions(
						CoreManager.class,
						AdminAPI.class,
						CameraAPI.class,
						DiscoveryAPI.class,
						LowLevelAPI.class,
						PublicPrinterControlAPI.class,
						RootWebPageResource.class)
				.build());

		bootstrap.addBundle(new MultiPartBundle());

		String externalStaticDir = System.getProperty("root.static.dir");
		AuthenticatedAssetsBundle webControlAssetsBundle = null;

		if (externalStaticDir != null && !externalStaticDir.isEmpty()) {
			Path externalStaticDirPath = Paths.get(externalStaticDir);
			if (Files.isDirectory(externalStaticDirPath) && Files.isReadable(externalStaticDirPath)) {
				webControlAssetsBundle = new ExternalAuthenticatedAssetsBundle(externalStaticDirPath,
						"/assets", "/", new RootAPIAuthenticator());
			}
		}

		if (webControlAssetsBundle == null)
			webControlAssetsBundle = new AuthenticatedAssetsBundle("/assets", "/", new RootAPIAuthenticator());

		bootstrap.addBundle(webControlAssetsBundle);
	}

	@Override
	public void run(RoboxRemoteConfiguration configuration, Environment environment) {
		environment.lifecycle().addServerLifecycleListener((Server server) -> {
			server.setStopAtShutdown(true);
			server.setStopTimeout(500);
		});

		environment.jersey().setUrlPattern("/api/*");

		final AppSetupHealthCheck healthCheck = new AppSetupHealthCheck(configuration.getDefaultPIN());
		environment.healthChecks().register("template", healthCheck);
		environment.jersey().register(CORSFilter.class);

		environment.jersey().register(new AuthDynamicFeature(new RootAPIAuthFilter.Builder<User>()
				.setAuthenticator(new RootAPIAuthenticator())
				.setRealm("Robox Root API")
				.buildAuthFilter()));
		environment.admin().addTask(new AdminUpdateTask());

		String hostAddress = "";
		try {
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface ni = networkInterfaces.nextElement();
				Enumeration<InetAddress> nias = ni.getInetAddresses();
				while (nias.hasMoreElements()) {
					InetAddress ia = nias.nextElement();
					if (!ia.isLinkLocalAddress() && !ia.isLoopbackAddress() && ia instanceof Inet4Address) {
						hostAddress = ia.getHostAddress();
						break;
					}
				}
			}
		} catch (SocketException e) {
			LOGGER.error("unable to get current IP " + e.getMessage());
		}

		LOGGER.info("Root started up with IP " + hostAddress);
	}

	public void stop() {
		LOGGER.info("Stopping ...");
		isStopping = true;
		System.exit(0);
	}

	public void restart() {
		stop();
	}

	public void setApplicationPIN(String applicationPIN) {
		System.setProperty(OpenAutomakerEnv.ROOT_ACCESS_PIN, applicationPIN);
	}

	public String getApplicationPIN() {
		return System.getProperty(OpenAutomakerEnv.ROOT_ACCESS_PIN);
	}

	public void resetApplicationPINToDefault() {
		System.clearProperty(OpenAutomakerEnv.ROOT_ACCESS_PIN);
	}

	public boolean getIsStopping() {
		return isStopping;
	}

	public void setIsStopping(boolean isStopping) {
		this.isStopping = isStopping;
	}

	public boolean getIsUpgrading() {
		return isUpgrading;
	}

	public void setIsUpgrading(boolean isUpgrading) {
		this.isUpgrading = isUpgrading;
	}

	public static boolean isResponding() {
		return instance != null && !(instance.isStopping || instance.isUpgrading);
	}
}
