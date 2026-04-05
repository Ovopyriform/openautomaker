package org.openautomaker.environment.preference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract Path based application preference
 */
public abstract class APathPreference extends APreference<Path> {

	// Name of the openautomaker folder
	protected static final String OPENAUTOMAKER = "openautomaker";

	/**
	 * Used in path preferences to determine if this instance of the application is packed using jpackage
	 * 
	 * @return true if it's packed using jpackage
	 */
	protected static boolean isPackaged() {
		String appHome = System.getProperty("jpackage.app-path");
		return appHome != null;
	}

	/**
	 * Checks if the provided path exists
	 * 
	 * @param path - The path to check
	 * @return boolean - true if the path exists
	 */
	protected static boolean pathExists(Path path) {
		return path.toFile().exists();
	}

	/**
	 * Checks that the provided path exists and attemps to create it if it doesn't.
	 * 
	 * @param path - The path to check/create
	 * @return boolean - true if the path is available
	 */
	protected static boolean ensurePath(Path path) {
		if (pathExists(path))
			return true;

		try {
			Files.createDirectories(path);
		}
		catch (IOException e) {
			return false;
		}

		return true;
	}

	/**
	 * Default implementation for getValue.
	 * 
	 * @return The path for this preference
	 */
	protected Path getPath() {
		Path defaultPath = getDefault();

		String pathStr = getNode().get(getKey(), null);
		if (pathStr == null) {
			remove();
			return defaultPath;
		}

		Path path = Path.of(pathStr);

		if (!pathExists(path)) {
			remove();
			return defaultPath;
		}

		return path;
	}

	@Override
	public Path getValue() {
		return getPath();
	}

	@Override
	public void setValue(Path path) {
		getNode().put(getKey(), path.toString());
	}
}
