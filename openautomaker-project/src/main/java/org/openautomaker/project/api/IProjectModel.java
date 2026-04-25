package org.openautomaker.project.api;

import java.nio.file.Path;
import java.util.Set;

import org.openautomaker.project.rbxproj.data.ModelTransformData;

/**
 * A model that can be placed on the print bed and stored in a {@code .rbxproj} archive.
 * Abstracts over {@code ModelContainer} from openautomaker-core.
 */
public interface IProjectModel {

	int getModelId();

	/** Original source path from which this model was imported. May be {@code null}. */
	Path getSourcePath();

	/** Entry path within the {@code .rbxproj} archive this model was loaded from. {@code null} for freshly imported models. */
	String getRbxprojEntryPath();

	void setRbxprojEntryPath(String entryPath);

	/** SHA-256 hex hash of the model file content as stored in the {@code .rbxproj} archive. {@code null} for freshly imported models. */
	String getRbxprojContentHash();

	void setRbxprojContentHash(String hash);

	String getModelName();

	void setModelName(String name);

	/** Extruder assignment: 0 or 1. */
	int getExtruder();

	void setExtruder(int extruder);

	/**
	 * Returns all leaf models that actually hold mesh data.
	 * For a plain model this is a singleton set containing {@code this}.
	 * For a group this recurses through children.
	 */
	Set<IProjectModel> getLeafModels();

	/** Current position, rotation, and scale of this model on the bed. */
	ModelTransformData getTransform();

	/** Apply stored transform data back onto this model. */
	void applyTransform(ModelTransformData transform);
}
