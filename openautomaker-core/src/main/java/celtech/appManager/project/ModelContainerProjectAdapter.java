package celtech.appManager.project;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openautomaker.project.api.IProject;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.api.IProjectSettings;
import org.openautomaker.project.data.ModelTransformData;

import celtech.appManager.ModelContainerProject;
import celtech.modelcontrol.ItemState;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ThreeDItemState;

/**
 * Bridges {@link ModelContainerProject} to the {@link IProject} interface expected by
 * {@link org.openautomaker.project.RbxProjWriter}.
 */
public class ModelContainerProjectAdapter implements IProject {

	private final ModelContainerProject project;

	public ModelContainerProjectAdapter(ModelContainerProject project) {
		this.project = project;
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
				.filter(t -> t instanceof ModelContainer)
				.map(t -> (IProjectModel) new ModelContainerAdapter((ModelContainer) t))
				.collect(Collectors.toList());
	}

	@Override
	public Map<Integer, Set<Integer>> getGroupStructure() {
		return project.getGroupStructure();
	}

	@Override
	public Map<Integer, ModelTransformData> getGroupTransforms() {
		return project.getGroupState().entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> toTransformData(e.getValue())));
	}

	@Override
	public void addModel(IProjectModel model) {
		throw new UnsupportedOperationException("Use ModelContainerProject.addModel() directly");
	}

	@Override
	public void recreateGroups(Map<Integer, Set<Integer>> structure,
			Map<Integer, ModelTransformData> transforms) throws Exception {
		throw new UnsupportedOperationException("Use ModelContainerProject.recreateGroups() directly");
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
}
