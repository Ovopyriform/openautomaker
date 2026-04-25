package org.openautomaker.project.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Imports OBJ files as a list of {@link RawMeshData} (one per object/group).
 * No JavaFX or Guice dependencies.
 */
@Singleton
public class ObjImporter implements MeshImporter {

	private static final Logger LOGGER = LogManager.getLogger();

	private static float scale = 1f;

	@Inject
	public ObjImporter() {
	}

	@Override
	public List<RawMeshData> load(Path path) throws IOException {
		try (InputStream stream = Files.newInputStream(path)) {
			Path parent = path.getParent();
			return parse(stream, parent);
		}
	}

	@Override
	public List<RawMeshData> load(URL url) throws IOException {
		try (InputStream stream = url.openStream()) {
			String rawPath = url.getPath();
			int lastSlash = rawPath.lastIndexOf('/');
			String dirStr = lastSlash > 0 ? rawPath.substring(0, lastSlash) : "";
			Path dir = dirStr.isEmpty() ? null : Path.of(dirStr);
			return parse(stream, dir);
		}
	}

	private List<RawMeshData> parse(InputStream stream, Path dir) throws IOException {
		List<RawMeshData> results = new ArrayList<>();
		Map<String, Integer> materialIndex = new HashMap<>();

		List<Float> fileVertices = new ArrayList<>();
		List<Integer> fileFaces = new ArrayList<>();

		String currentKey = "default";
		boolean pendingObject = false;
		int facesStart = 0;
		int currentMaterial = -1;
		Map<String, Integer> keyToMaterial = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				try {
					if (line.startsWith("v ")) {
						String[] p = line.substring(2).trim().split(" +");
						float x = Float.parseFloat(p[0]) * scale;
						float y = Float.parseFloat(p[1]) * scale;
						float z = Float.parseFloat(p[2]) * scale;
						fileVertices.add(x);
						fileVertices.add(-z);
						fileVertices.add(y);
					}
					else if (line.startsWith("f ")) {
						pendingObject = true;
						String[] parts = line.substring(2).trim().split(" +");
						int v1 = parseVertexIndex(parts[0], fileVertices.size() / 3);
						for (int i = 1; i < parts.length - 1; i++) {
							int v2 = parseVertexIndex(parts[i], fileVertices.size() / 3);
							int v3 = parseVertexIndex(parts[i + 1], fileVertices.size() / 3);
							fileFaces.add(v1);
							fileFaces.add(v2);
							fileFaces.add(v3);
						}
					}
					else if (line.startsWith("mtllib ")) {
						String mtlFile = line.substring("mtllib ".length()).trim();
						materialIndex = loadMaterials(mtlFile, dir);
					}
					else if (line.startsWith("usemtl ")) {
						String name = line.substring("usemtl ".length()).trim();
						currentMaterial = materialIndex.getOrDefault(name, 0);
					}
					else if (line.startsWith("o ") || line.startsWith("g ")) {
						if (pendingObject) {
							RawMeshData mesh = buildMesh(currentKey, fileVertices, fileFaces, facesStart,
									keyToMaterial.getOrDefault(currentKey, 0));
							if (mesh != null) results.add(mesh);
							facesStart = fileFaces.size();
							pendingObject = false;
						}
						currentKey = line.substring(1).trim();
						if (currentMaterial >= 0) keyToMaterial.put(currentKey, currentMaterial);
					}
				}
				catch (Exception ex) {
					LOGGER.debug("Skipped OBJ line: {}", line);
				}
			}
		}

		if (pendingObject) {
			RawMeshData mesh = buildMesh(currentKey, fileVertices, fileFaces, facesStart,
					keyToMaterial.getOrDefault(currentKey, 0));
			if (mesh != null) results.add(mesh);
		}

		return results;
	}

	private int parseVertexIndex(String token, int totalVerts) {
		int raw = Integer.parseInt(token.split("/")[0]);
		return raw < 0 ? raw + totalVerts : raw - 1;
	}

	private RawMeshData buildMesh(String name, List<Float> fileVertices, List<Integer> fileFaces,
			int facesStart, int extruder) {
		Map<Integer, Integer> vertexMap = new HashMap<>();
		List<Float> newVerts = new ArrayList<>();
		List<Integer> newFaces = new ArrayList<>();

		for (int i = facesStart; i < fileFaces.size(); i++) {
			int vi = fileFaces.get(i);
			Integer nvi = vertexMap.get(vi);
			if (nvi == null) {
				nvi = newVerts.size() / 3;
				vertexMap.put(vi, nvi);
				newVerts.add(fileVertices.get(vi * 3));
				newVerts.add(fileVertices.get(vi * 3 + 1));
				newVerts.add(fileVertices.get(vi * 3 + 2));
			}
			newFaces.add(nvi);
		}

		if (newFaces.isEmpty()) return null;

		float[] vArr = new float[newVerts.size()];
		for (int i = 0; i < vArr.length; i++) vArr[i] = newVerts.get(i);
		int[] fArr = new int[newFaces.size()];
		for (int i = 0; i < fArr.length; i++) fArr[i] = newFaces.get(i);

		return new RawMeshData(vArr, fArr, extruder, name);
	}

	/** Reads an MTL file and returns material-name → sequential-index. No JavaFX needed. */
	private Map<String, Integer> loadMaterials(String filename, Path dir) {
		Map<String, Integer> index = new HashMap<>();
		if (dir == null) return index;

		Path mtlPath = dir.resolve(filename);
		int nextId = 0;
		try (BufferedReader reader = Files.newBufferedReader(mtlPath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("newmtl ")) {
					String name = line.substring("newmtl ".length()).trim();
					if (!index.containsKey(name)) {
						index.put(name, nextId++);
					}
				}
			}
		}
		catch (Exception ex) {
			LOGGER.debug("Could not load MTL file {}: {}", mtlPath, ex.getMessage());
		}
		return index;
	}

	public static void setScale(float s) { scale = s; }
}
