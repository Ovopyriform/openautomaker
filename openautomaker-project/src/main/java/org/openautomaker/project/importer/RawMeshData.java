package org.openautomaker.project.importer;

/**
 * Pure-Java geometry produced by the importers in this module.
 *
 * {@code vertices} — flat [x0,y0,z0, x1,y1,z1, ...]
 * {@code faces}    — flat triangle indices [v0, v1, v2, ...] — 3 ints per triangle
 * {@code extruder} — extruder assignment (0-based)
 * {@code name}     — mesh name (object name for OBJ, filename for STL)
 */
public record RawMeshData(float[] vertices, int[] faces, int extruder, String name) {

	/** Merges multiple meshes into one. Vertices are concatenated; face indices are offset accordingly. */
	public static RawMeshData merge(java.util.List<RawMeshData> meshes, String name) {
		if (meshes.isEmpty()) throw new IllegalArgumentException("Cannot merge empty list");
		if (meshes.size() == 1) return meshes.get(0);

		int totalVerts = meshes.stream().mapToInt(m -> m.vertices().length / 3).sum();
		int totalFaceInts = meshes.stream().mapToInt(m -> m.faces().length).sum();

		float[] vertices = new float[totalVerts * 3];
		int[] faces = new int[totalFaceInts];

		int vOffset = 0;
		int fOffset = 0;
		for (RawMeshData mesh : meshes) {
			System.arraycopy(mesh.vertices(), 0, vertices, vOffset * 3, mesh.vertices().length);
			for (int i = 0; i < mesh.faces().length; i++) {
				faces[fOffset + i] = mesh.faces()[i] + vOffset;
			}
			vOffset += mesh.vertices().length / 3;
			fOffset += mesh.faces().length;
		}

		return new RawMeshData(vertices, faces, 0, name);
	}
}
