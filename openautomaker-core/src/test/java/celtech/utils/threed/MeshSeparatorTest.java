package celtech.utils.threed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openautomaker.project.importer.RawMeshData;
import org.openautomaker.project.importer.StlImporter;
import org.openautomaker.ui.project.loader.StlModelLoader;

import javafx.scene.shape.TriangleMesh;

public class MeshSeparatorTest {

	private TriangleMesh loadMesh(String resource) throws IOException {
		URL stlURL = this.getClass().getResource(resource);
		File file = new File(stlURL.getFile());
		RawMeshData raw = new StlImporter().load(file.toPath()).get(0);
		return (TriangleMesh) StlModelLoader.buildMeshView(raw, file.getName()).getMesh();
	}

	@Test
	public void testMeshOfOneObject() throws IOException {
		TriangleMesh mesh = loadMesh("/pyramid1.stl");
		List<TriangleMesh> meshes = MeshSeparator.separate(mesh);
		assertEquals(1, meshes.size());
		assertSame(mesh, meshes.get(0));
	}

	@Test
	public void testMeshOfTwoObjects() throws IOException {
		TriangleMesh mesh = loadMesh("/twodiscs.stl");
		List<TriangleMesh> meshes = MeshSeparator.separate(mesh);
		assertEquals(2, meshes.size());
		// each sub mesh should have 176 faces
		assertEquals(176, meshes.get(0).getFaces().size() / 6);
		assertEquals(176, meshes.get(1).getFaces().size() / 6);
	}

}
