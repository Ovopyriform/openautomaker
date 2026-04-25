package org.openautomaker.project.api;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads a model file into an {@link IProjectModel}.
 *
 * <p>Implementations wrap the concrete importers (STLImporter, ObjImporter, etc.)
 * from openautomaker-core and are registered in the main application module.
 */
public interface IModelLoader {

	/**
	 * Returns {@code true} if this loader handles files with the given extension.
	 *
	 * @param extension lowercase extension without dot, e.g. {@code "stl"}, {@code "obj"}
	 */
	boolean supports(String extension);

	/**
	 * Load the model file and return the resulting project model.
	 *
	 * @param path the model file to import
	 * @return loaded model, never {@code null}
	 * @throws IOException if the file cannot be read or parsed
	 */
	IProjectModel load(Path path) throws IOException;
}
