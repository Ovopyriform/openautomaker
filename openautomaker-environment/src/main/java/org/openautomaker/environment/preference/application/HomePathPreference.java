package org.openautomaker.environment.preference.application;

import java.nio.file.Path;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openautomaker.environment.preference.APairedPathPreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Provides the application and user home paths
 */
@Singleton
public class HomePathPreference extends APairedPathPreference {

	// Test environment path elements
	protected static final String OPENAUTOMAKER_TEST_ENVIRONMENT = "openautomaker-test-environment";
	protected static final String ENV = "env";
	private static final String USR = "usr";
	private static final String APP = "app";

	@Inject
	protected HomePathPreference() {
		super();
	}

	private Path resolveTestEnvPath() {
		// We have two options, working directory is either in the project root (running from IDE) or the workspace root (parent of project root).  Check for the project root first, then the workspace root.
		Path projectRoot = Path.of(System.getProperty("user.dir")).getParent().resolve(OPENAUTOMAKER_TEST_ENVIRONMENT);
		if (projectRoot.toFile().exists())
			return projectRoot;

		// Assume it's the workspace root
		return Path.of(System.getProperty("user.dir")).resolve(OPENAUTOMAKER_TEST_ENVIRONMENT);
	}

	@Override
	public Path getAppValue() {
		// If it's not packaged, use the test environment
		if (!isPackaged())
			return resolveTestEnvPath().resolve(ENV).resolve(APP);

		// Evaluate the packaged path.
		String osName = System.getProperty("os.name");
		Path packagedPath = Path.of(System.getProperty("jpackage.app-path"));

		// If it's a mac the package path is the executable in the app.  Go back 2 folders to the root of the app
		//TODO: Only for Mac at the moment.  Inevitably something to do for Windows and Linux also
		if (osName.matches("^Mac.*"))
			packagedPath = packagedPath.getParent().getParent();

		return packagedPath;
	}

	@Override
	public Path getUserValue() {
		if (!isPackaged())
			return resolveTestEnvPath().resolve(ENV).resolve(USR).resolve(OPENAUTOMAKER);

		Path userPath = Path.of(System.getProperty("user.home"), OPENAUTOMAKER);

		return ensurePath(userPath) ? userPath : null;
	}

	@Override
	public Path getValue() {
		throw new UnsupportedOperationException("getValue not implemented for preference: " + getClass().getSimpleName());
	}

	@Override
	public void addChangeListener(PreferenceChangeListener listener) {
		throw new UnsupportedOperationException("addChangeListener not implemented for preference: " + getClass().getSimpleName());
	}

	@Override
	protected Preferences getNode() {
		throw new UnsupportedOperationException("getNode not implemented for preference: " + getClass().getSimpleName());
	}

	@Override
	public void setValue(Path value) {
		throw new UnsupportedOperationException("setValue not implemented for preference: " + getClass().getSimpleName());
	}

}
