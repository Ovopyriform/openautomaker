package org.openautomaker.ui.project.adapter;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.rbxproj.data.ModelTransformData;

import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ThreeDItemState;

public class ModelContainerAdapter implements IProjectModel {

	private final ModelContainer mc;

	ModelContainer getModelContainer() { return mc; }

	public ModelContainerAdapter(ModelContainer mc) {
		this.mc = mc;
	}

	@Override
	public int getModelId() {
		return mc.getModelId();
	}

	@Override
	public Path getSourcePath() {
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
	public String getRbxprojEntryPath() {
		return mc.getRbxprojEntryPath();
	}

	@Override
	public void setRbxprojEntryPath(String entryPath) {
		mc.setRbxprojEntryPath(entryPath);
	}

	@Override
	public String getRbxprojContentHash() {
		return mc.getRbxprojContentHash();
	}

	@Override
	public void setRbxprojContentHash(String hash) {
		mc.setRbxprojContentHash(hash);
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
