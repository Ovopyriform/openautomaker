package org.openautomaker.ui.project.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.rbxproj.data.ModelTransformData;

import celtech.appManager.Project;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ThreeDItemState;

/**
 * Implements {@link IProject} over a real {@link Project} so that
 * {@link org.openautomaker.project.rbxproj.RbxprojReader} can populate a core project object.
 *
 * <p>Call {@link #getProject()} after reading to retrieve the populated project.
 */
public class ProjectBridge implements IProject {

	private final Project project;
	private Path projectPath;

	public ProjectBridge(Project project) {
		this.project = project;
	}

	public Project getProject() {
		return project;
	}

	@Override
	public Path getProjectPath() {
		return projectPath;
	}

	@Override
	public void setProjectPath(Path projectPath) {
		this.projectPath = projectPath;
	}

	@Override
	public String getProjectName() {
		return project.getProjectName();
	}

	@Override
	public void setProjectName(String name) {
		project.setProjectName(name);
	}

	@Override
	public IProjectSettings getSettings() {
		return new PrinterSettingsAdapter(project);
	}

	@Override
	public List<IProjectModel> getTopLevelModels() {
		return project.getTopLevelThings().stream()
				.filter(t -> t instanceof celtech.modelcontrol.ModelContainer)
				.map(t -> (IProjectModel) new ModelContainerAdapter((celtech.modelcontrol.ModelContainer) t))
				.collect(Collectors.toList());
	}

	@Override
	public Map<Integer, Set<Integer>> getGroupStructure() {
		return project.getGroupStructure();
	}

	@Override
	public Map<Integer, ModelTransformData> getGroupTransforms() {
		return project.getGroupState().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> toTransformData(e.getValue())));
	}

	@Override
	public void addModel(IProjectModel model) {
		if (model instanceof ModelContainerAdapter mca) {
			project.addModel(mca.getModelContainer());
		}
	}

	@Override
	public void recreateGroups(Map<Integer, Set<Integer>> structure,
			Map<Integer, ModelTransformData> transforms) throws Exception {
		Map<Integer, ItemState> itemStates = transforms.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						e -> toItemState(e.getKey(), e.getValue())));
		project.recreateGroups(structure, itemStates);
	}

	private static ModelTransformData toTransformData(ItemState state) {
		if (state instanceof ThreeDItemState s) {
			return new ModelTransformData(
					s.x, s.y, s.z,
					s.preferredXScale, s.preferredYScale, s.preferredZScale,
					s.preferredRotationTurn, s.preferredRotationLean, s.preferredRotationTwist);
		}
		return new ModelTransformData(state.x, state.y, 0, state.preferredXScale, state.preferredYScale, 1, state.preferredRotationTurn, 0, 0);
	}

	private static ThreeDItemState toItemState(int modelId, ModelTransformData t) {
		return new ThreeDItemState(modelId,
				t.x, t.y, t.z,
				t.xScale, t.yScale, t.zScale,
				t.rotationTwist, t.rotationTurn, t.rotationLean);
	}
}
