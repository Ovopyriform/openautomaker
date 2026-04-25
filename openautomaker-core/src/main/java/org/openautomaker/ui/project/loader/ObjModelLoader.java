package org.openautomaker.ui.project.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.importer.ObjImporter;
import org.openautomaker.project.importer.RawMeshData;
import org.openautomaker.ui.inject.model.ModelContainerFactory;
import org.openautomaker.ui.project.adapter.ModelContainerAdapter;

import celtech.modelcontrol.ModelContainer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ObjModelLoader implements IModelLoader {

	private final ModelContainerFactory modelContainerFactory;
	private final ObjImporter objImporter;

	@Inject
	public ObjModelLoader(
		ModelContainerFactory modelContainerFactory,
		ObjImporter objImporter) {

		this.modelContainerFactory = modelContainerFactory;
		this.objImporter = objImporter;
	}

	@Override
	public boolean supports(String extension) {
		return "obj".equalsIgnoreCase(extension);
	}

	@Override
	public IProjectModel load(Path path) throws IOException {
		List<RawMeshData> meshes = objImporter.load(path);
		if (meshes.isEmpty()) {
			throw new IOException("OBJ import returned no models for: " + path);
		}
		ModelContainer mc = modelContainerFactory.create(path,
				StlModelLoader.buildMeshView(meshes.get(0), path.getFileName().toString()));
		return new ModelContainerAdapter(mc);
	}
}
