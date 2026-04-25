package org.openautomaker.ui.project.loader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.importer.RawMeshData;
import org.openautomaker.project.importer.SvgImporter;
import org.openautomaker.ui.inject.model.ModelContainerFactory;
import org.openautomaker.ui.project.adapter.ModelContainerAdapter;

import celtech.coreUI.visualisation.metaparts.ModelLoadResult;
import celtech.coreUI.visualisation.metaparts.ModelLoadResultType;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.shape.MeshView;

@Singleton
public class SvgModelLoader implements IModelLoader {

	private final ModelContainerFactory modelContainerFactory;
	private final SvgImporter svgImporter;

	@Inject
	public SvgModelLoader(ModelContainerFactory modelContainerFactory, SvgImporter svgImporter) {
		this.modelContainerFactory = modelContainerFactory;
		this.svgImporter = svgImporter;
	}

	@Override
	public boolean supports(String extension) {
		return "svg".equalsIgnoreCase(extension);
	}

	@Override
	public IProjectModel load(Path path) throws IOException {
		ModelContainer mc = loadModelContainer(path);
		return new ModelContainerAdapter(mc);
	}

	public ModelLoadResult loadAsResult(Path path) throws IOException {
		ModelContainer mc = loadModelContainer(path);
		Set<ProjectifiableThing> things = new HashSet<>();
		things.add(mc);
		return new ModelLoadResult(ModelLoadResultType.Mesh, path.toAbsolutePath().toString(),
				path.getFileName().toString(), things);
	}

	private ModelContainer loadModelContainer(Path path) throws IOException {
		List<RawMeshData> meshes = svgImporter.load(path);
		if (meshes.isEmpty()) {
			throw new IOException("No extrudable paths found in SVG: " + path.getFileName());
		}
		RawMeshData merged = RawMeshData.merge(meshes, path.getFileName().toString());
		MeshView meshView = StlModelLoader.buildMeshView(merged, path.getFileName().toString());
		return modelContainerFactory.create(path, meshView);
	}
}
