package org.openautomaker.project.importer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * Imports STL files (binary and ASCII) as {@link RawMeshData}.
 * No JavaFX or Guice dependencies.
 */
@Singleton
public class StlImporter implements MeshImporter {

	private static final Logger LOGGER = LogManager.getLogger();

	@Inject
	public StlImporter() {
	}

	private record Vertex3f(float x, float y, float z) {}

	@Override
	public List<RawMeshData> load(Path path) throws IOException {
		boolean binary = isBinary(path);
		LOGGER.debug("Loading STL {} ({})", path.getFileName(), binary ? "binary" : "ascii");
		return List.of(binary ? loadBinary(path) : loadAscii(path));
	}

	private boolean isBinary(Path path) throws IOException {
		try (var in = Files.newInputStream(path)) {
			byte[] header = new byte[80];
			if (in.read(header) < 80) return false;
			byte[] countBytes = new byte[4];
			if (in.read(countBytes) < 4) return false;
			int facets = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			return Files.size(path) == (long) facets * 50 + 84;
		}
	}

	private RawMeshData loadBinary(Path path) throws IOException {
		Map<Vertex3f, Integer> dedup = new HashMap<>();
		List<Float> vertexList = new ArrayList<>();
		List<Integer> faceList = new ArrayList<>();

		try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
			byte[] header = new byte[80];
			in.readFully(header);

			byte[] countBytes = new byte[4];
			in.readFully(countBytes);
			int facetCount = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
			LOGGER.debug("Binary STL: {} facets", facetCount);

			byte[] facetData = new byte[50];
			for (int f = 0; f < facetCount; f++) {
				in.readFully(facetData);
				ByteBuffer buf = ByteBuffer.wrap(facetData).order(ByteOrder.LITTLE_ENDIAN);
				// skip normal
				buf.getFloat(); buf.getFloat(); buf.getFloat();

				for (int v = 0; v < 3; v++) {
					float x = buf.getFloat();
					float y = buf.getFloat();
					float z = buf.getFloat();
					// reorder to match old importer (y→-z, z→y)
					Vertex3f vertex = new Vertex3f(x, -z, y);
					Integer idx = dedup.get(vertex);
					if (idx == null) {
						idx = vertexList.size() / 3;
						dedup.put(vertex, idx);
						vertexList.add(vertex.x());
						vertexList.add(vertex.y());
						vertexList.add(vertex.z());
					}
					faceList.add(idx);
				}
				// skip attribute byte count
				buf.getShort();
			}
		}

		return buildMeshData(vertexList, faceList, path.getFileName().toString(), 0);
	}

	private RawMeshData loadAscii(Path path) throws IOException {
		Map<Vertex3f, Integer> dedup = new HashMap<>();
		List<Float> vertexList = new ArrayList<>();
		List<Integer> faceList = new ArrayList<>();

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			int vInFacet = 0;
			int[] faceBuf = new int[3];

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("vertex")) {
					String[] parts = line.split("[ ]+");
					float x = Float.parseFloat(parts[1]);
					float y = Float.parseFloat(parts[2]);
					float z = Float.parseFloat(parts[3]);
					Vertex3f vertex = new Vertex3f(x, -z, y);
					Integer idx = dedup.get(vertex);
					if (idx == null) {
						idx = vertexList.size() / 3;
						dedup.put(vertex, idx);
						vertexList.add(vertex.x());
						vertexList.add(vertex.y());
						vertexList.add(vertex.z());
					}
					faceBuf[vInFacet++] = idx;
					if (vInFacet == 3) {
						for (int i : faceBuf) faceList.add(i);
						vInFacet = 0;
					}
				}
			}
		}

		return buildMeshData(vertexList, faceList, path.getFileName().toString(), 0);
	}

	private RawMeshData buildMeshData(List<Float> vertices, List<Integer> faces, String name, int extruder) {
		float[] vArr = new float[vertices.size()];
		for (int i = 0; i < vArr.length; i++) vArr[i] = vertices.get(i);
		int[] fArr = new int[faces.size()];
		for (int i = 0; i < fArr.length; i++) fArr[i] = faces.get(i);
		return new RawMeshData(vArr, fArr, extruder, name);
	}
}
