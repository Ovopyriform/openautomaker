package org.openautomaker.project.api;

import java.nio.file.Path;

/**
 * Provides a writable temporary directory for staging extracted model files during load.
 * Implemented in the main application module, wrapping {@code TempPathPreference}.
 */
public interface ITempDirectory {

	Path getPath();
}
