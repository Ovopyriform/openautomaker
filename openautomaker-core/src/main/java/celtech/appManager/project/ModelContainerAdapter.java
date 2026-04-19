package celtech.appManager.project;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.data.ModelTransformData;

import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ThreeDItemState;

class ModelContainerAdapter implements IProjectModel {

	private final ModelContainer mc;

	ModelContainerAdapter(ModelContainer mc) {
		this.mc = mc;
	}

	@Override
	public int getModelId() {
		return mc.getModelId();
	}

	@Override
	public File getSourceFile() {
		return mc.getModelFile();
	}

	@Override
	public String getModelName() {
		return mc.getModelName();
	}

	@Override
	public void setModelName(String name) {
		mc.setModelName(name);
	}

	@Override
	public int getExtruder() {
		return mc.getAssociateWithExtruderNumberProperty().get();
	}

	@Override
	public void setExtruder(int extruder) {
		mc.setUseExtruder0(extruder == 0);
	}

	@Override
	public Set<IProjectModel> getLeafModels() {
		return mc.getModelsHoldingMeshViews().stream()
				.map(ModelContainerAdapter::new)
				.collect(Collectors.toSet());
	}

	@Override
	public ModelTransformData getTransform() {
		ThreeDItemState s = mc.getState();
		return new ModelTransformData(
				s.x, s.y, s.z,
				s.preferredXScale, s.preferredYScale, s.preferredZScale,
				s.preferredRotationTurn, s.preferredRotationLean, s.preferredRotationTwist);
	}

	@Override
	public void applyTransform(ModelTransformData t) {
		mc.setState(new ThreeDItemState(
				mc.getModelId(),
				t.x, t.y, t.z,
				t.xScale, t.yScale, t.zScale,
				t.rotationTwist, t.rotationTurn, t.rotationLean));
	}
}
