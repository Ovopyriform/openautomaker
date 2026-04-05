package org.openautomaker.environment.preference_factory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FilePreferencesFactory implements PreferencesFactory {
	private static final String BACK = "..";
	private static final String OPENAUTOMAKER_TEST_ENVIRONMENT = "openautomaker-test-environment";
	private static final String ENV = "env";

	private static final Logger log = LogManager.getLogger();

	Preferences userRootPreferences;

	@Override
	public Preferences systemRoot() {
		return userRoot();
	}

	@Override
	public Preferences userRoot() {
		if (userRootPreferences == null) {
			log.debug("Instantiating user root preferences");

			userRootPreferences = new FilePreferences(null, "");
		}
		return userRootPreferences;
	}

	private static File preferencesFile;

	private static Path resolveTestEnvPath() {
		// We have two options, working directory is either in the project root (running from IDE) or the workspace root (parent of project root).  Check for the project root first, then the workspace root.
		Path projectRoot = Path.of(System.getProperty("user.dir")).getParent().resolve(OPENAUTOMAKER_TEST_ENVIRONMENT);
		if (projectRoot.toFile().exists())
			return projectRoot;

		// Assume it's the workspace root
		return Path.of(System.getProperty("user.dir")).resolve(OPENAUTOMAKER_TEST_ENVIRONMENT);
	}

	public static File getPreferencesFile() {
		if (preferencesFile != null)
			return preferencesFile;

		Path testEnvPath = resolveTestEnvPath().resolve(ENV).resolve("testenv.prefs");
		preferencesFile = testEnvPath.toFile().getAbsoluteFile();
		log.info("Preferences file is " + preferencesFile);
		
		return preferencesFile;
	}
}
