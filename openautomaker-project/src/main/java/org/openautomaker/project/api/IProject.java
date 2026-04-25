package org.openautomaker.project.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openautomaker.project.rbxproj.data.ModelTransformData;

/**
 * A 3D print project that can be serialised to a {@code .rbxproj} archive.
 * Abstracts over {@code Project} from openautomaker-core.
 */
public interface IProject {

	String getProjectName();

	void setProjectName(String name);

	/** Path on the base filesystem to the {@code .rbxproj} archive this project was loaded from. {@code null} for unsaved/new projects. */
	Path getProjectPath();

	void setProjectPath(Path projectPath);

	IProjectSettings getSettings();

	/**
	 * Returns all top-level items placed directly on the print bed.
	 * May include groups (which themselves contain leaf models).
	 */
	List<IProjectModel> getTopLevelModels();

	/**
	 * Maps group model ID → set of direct child model IDs.
	 * Empty when no groups exist.
	 */
	Map<Integer, Set<Integer>> getGroupStructure();

	/**
	 * Maps group model ID → the transform applied to that group container.
	 * Empty when no groups exist.
	 */
	Map<Integer, ModelTransformData> getGroupTransforms();

	void addModel(IProjectModel model);

	/**
	 * Reconstruct the group hierarchy from the stored structure and transforms.
	 * Called during load after all leaf models have been added.
	 *
	 * @param structure  group ID → child model IDs
	 * @param transforms group ID → group container transform
	 */
	void recreateGroups(Map<Integer, Set<Integer>> structure, Map<Integer, ModelTransformData> transforms)
			throws Exception;
}
