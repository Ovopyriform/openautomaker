package org.openautomaker.project.api;

import java.io.File;
import java.util.Set;

import org.openautomaker.project.data.ModelTransformData;

/**
 * A model that can be placed on the print bed and stored in a {@code .rbxproj} archive.
 * Abstracts over {@code ModelContainer} from openautomaker-core.
 */
public interface IProjectModel {

	int getModelId();

	/** Original source file from which this model was imported. May be {@code null}. */
	File getSourceFile();

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
