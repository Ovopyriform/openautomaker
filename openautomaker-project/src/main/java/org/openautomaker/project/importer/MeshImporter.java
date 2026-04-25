package org.openautomaker.project.importer;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

/**
 * Common interface for all mesh importers.
 * Implementations are pure Java with no JavaFX or Guice dependencies.
 */
public interface MeshImporter {

	List<RawMeshData> load(Path path) throws IOException;

	default List<RawMeshData> load(URL url) throws IOException {
		throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support URL loading");
	}
}
