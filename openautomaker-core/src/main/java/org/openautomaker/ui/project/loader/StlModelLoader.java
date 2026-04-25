package org.openautomaker.ui.project.loader;

import java.io.IOException;
import java.nio.file.Path;

import org.openautomaker.project.api.IModelLoader;
import org.openautomaker.project.api.IProjectModel;
import org.openautomaker.project.importer.RawMeshData;
import org.openautomaker.project.importer.StlImporter;
import org.openautomaker.ui.inject.model.ModelContainerFactory;
import org.openautomaker.ui.project.adapter.ModelContainerAdapter;

import celtech.modelcontrol.ModelContainer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

@Singleton
public class StlModelLoader implements IModelLoader {

	private final ModelContainerFactory modelContainerFactory;
	private final StlImporter stlImporter;

	@Inject
	public StlModelLoader(
		ModelContainerFactory modelContainerFactory,
		StlImporter stlImporter) {

		this.modelContainerFactory = modelContainerFactory;
		this.stlImporter = stlImporter;
	}

	@Override
	public boolean supports(String extension) {
		return "stl".equalsIgnoreCase(extension);
	}

	@Override
	public IProjectModel load(Path path) throws IOException {
		ModelContainer mc = loadModelContainer(path);
		return new ModelContainerAdapter(mc);
	}

	ModelContainer loadModelContainer(Path path) throws IOException {
		RawMeshData raw = stlImporter.load(path).get(0);
		return modelContainerFactory.create(path, buildMeshView(raw, path.getFileName().toString()));
	}

	public static MeshView buildMeshView(RawMeshData raw, String id) {
		TriangleMesh mesh = new TriangleMesh();
		mesh.getPoints().setAll(raw.vertices());
		mesh.getTexCoords().setAll(0f, 0f);

		// JavaFX TriangleMesh requires interleaved [v,t, v,t, v,t] per triangle
		int[] rawFaces = raw.faces();
		int[] jfxFaces = new int[rawFaces.length * 2];
		for (int i = 0; i < rawFaces.length; i++) {
			jfxFaces[i * 2] = rawFaces[i];
			// jfxFaces[i * 2 + 1] = 0 (texcoord index) — default
		}
		mesh.getFaces().setAll(jfxFaces);

		int[] smoothing = new int[rawFaces.length / 3];
		mesh.getFaceSmoothingGroups().setAll(smoothing);

		MeshView view = new MeshView(mesh);
		view.setId(id + "_mesh");
		return view;
	}
}
