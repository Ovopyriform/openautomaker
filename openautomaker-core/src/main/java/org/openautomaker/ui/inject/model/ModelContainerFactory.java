package org.openautomaker.ui.inject.model;

import java.nio.file.Path;

import celtech.modelcontrol.ModelContainer;
import javafx.scene.shape.MeshView;

public interface ModelContainerFactory {

	public ModelContainer create();

	public ModelContainer create(
			Path modelFile,
			MeshView meshView);

	public ModelContainer create(
			Path modelFile,
			MeshView meshView,
			int extruderAssociation);
}
