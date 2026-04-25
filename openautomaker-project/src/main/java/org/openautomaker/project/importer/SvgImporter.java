package org.openautomaker.project.importer;

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.CompositeGraphicsNode;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.ShapeNode;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.environment.preference.modeling.SvgExtrusionDepthPreference;
import org.w3c.dom.Document;
import org.w3c.dom.svg.SVGSVGElement;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Parses an SVG file and extrudes each closed path into a 3D prism, returning
 * one {@link RawMeshData} per contour in the same face format as {@link StlImporter}
 * and {@link ObjImporter}.
 *
 * <p>Coordinate system: SVG x/y → 3D x/y, extrusion along z. SVG y is flipped
 * using a global maxY computed across all shapes so relative positions are preserved.
 */
@Singleton
public class SvgImporter implements MeshImporter {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final double FLATNESS = 0.5;

	/** SVG CSS pixels to millimetres (96 DPI). */
	private static final float PX_TO_MM = (float) (25.4 / 96.0);

	private final SvgExtrusionDepthPreference extrusionDepthPreference;

	@Inject
	public SvgImporter(SvgExtrusionDepthPreference extrusionDepthPreference) {
		this.extrusionDepthPreference = extrusionDepthPreference;
	}

	@Override
	public List<RawMeshData> load(Path svgPath) throws IOException {
		return load(svgPath, extrusionDepthPreference.getValue(), 0, 0);
	}

	public List<RawMeshData> load(Path svgPath, float extrusionDepthMm) throws IOException {
		return load(svgPath, extrusionDepthMm, 0, 0);
	}

	public List<RawMeshData> load(Path svgPath, float extrusionDepthMm, float bedWidthMm, float bedDepthMm) throws IOException {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
		Document doc = factory.createDocument(svgPath.toUri().toString());

		SVGSVGElement svgRoot = ((SVGOMDocument) doc).getRootElement();
		LOGGER.warn("SVG attributes: width='{}' height='{}' viewBox='{}'",
				svgRoot.getAttribute("width"),
				svgRoot.getAttribute("height"),
				svgRoot.getAttribute("viewBox"));

		UserAgentAdapter userAgent = bedWidthMm > 0 && bedDepthMm > 0
				? new UserAgentAdapter() {
					@Override
					public java.awt.geom.Dimension2D getViewportSize() {
						return new Dimension(
								Math.round(bedWidthMm / PX_TO_MM),
								Math.round(bedDepthMm / PX_TO_MM));
					}
				}
				: new UserAgentAdapter();
		DocumentLoader loader = new DocumentLoader(userAgent);
		BridgeContext ctx = new BridgeContext(userAgent, loader);
		ctx.setDynamicState(BridgeContext.STATIC);

		GVTBuilder builder = new GVTBuilder();
		GraphicsNode root = builder.build(ctx, doc);

		float svgPhysWidthPx = (float) svgRoot.getWidth().getBaseVal().getValue();
		float svgPhysHeightPx = (float) svgRoot.getHeight().getBaseVal().getValue();
		LOGGER.warn("SVG resolved: {}x{} CSS px = {}x{} mm | root transform: {}",
				svgPhysWidthPx, svgPhysHeightPx,
				svgPhysWidthPx * PX_TO_MM, svgPhysHeightPx * PX_TO_MM,
				root.getTransform());

		List<float[]> contours = extractContours(root);
		LOGGER.debug("SVG {} → {} raw contours before extrusion", svgPath.getFileName(), contours.size());
		if (contours.isEmpty()) {
			LOGGER.warn("No closed paths found in {}", svgPath.getFileName());
			return List.of();
		}

		float globalMaxY = 0;
		float globalMaxX = 0;
		for (float[] contour : contours) {
			for (int i = 0; i < contour.length; i += 2) {
				if (contour[i] > globalMaxX) globalMaxX = contour[i];
				if (contour[i + 1] > globalMaxY) globalMaxY = contour[i + 1];
			}
		}
		LOGGER.warn("SVG raw coordinate bounds: maxX={} maxY={} (before px→mm)", globalMaxX, globalMaxY);

		String baseName = svgPath.getFileName().toString();
		List<RawMeshData> results = new ArrayList<>();
		for (int idx = 0; idx < contours.size(); idx++) {
			RawMeshData mesh = extrude(contours.get(idx), extrusionDepthMm, globalMaxY,
					baseName + "_contour_" + idx);
			if (mesh != null) {
				results.add(mesh);
			}
		}
		return results;
	}

	private List<float[]> extractContours(GraphicsNode root) {
		List<float[]> contours = new ArrayList<>();
		collectShapes(root, contours, new AffineTransform());
		return contours;
	}

	private void collectShapes(GraphicsNode node, List<float[]> contours, AffineTransform parentTransform) {
		AffineTransform nodeTransform = new AffineTransform(parentTransform);
		AffineTransform nt = node.getTransform();
		if (nt != null) {
			nodeTransform.concatenate(nt);
		}

		if (node instanceof ShapeNode shapeNode) {
			Shape shape = shapeNode.getShape();
			if (shape != null) {
				contours.addAll(flattenShape(shape, nodeTransform));
			}
		}
		if (node instanceof CompositeGraphicsNode composite) {
			for (Object child : composite.getChildren()) {
				collectShapes((GraphicsNode) child, contours, nodeTransform);
			}
		}
	}

	private List<float[]> flattenShape(Shape shape, AffineTransform nodeTransform) {
		List<float[]> contours = new ArrayList<>();
		PathIterator it = shape.getPathIterator(nodeTransform, FLATNESS);
		List<float[]> current = new ArrayList<>();
		float[] seg = new float[6];
		while (!it.isDone()) {
			int type = it.currentSegment(seg);
			switch (type) {
				case PathIterator.SEG_MOVETO -> {
					if (!current.isEmpty()) {
						contours.add(toFlatArray(current));
						current.clear();
					}
					current.add(new float[]{seg[0], seg[1]});
				}
				case PathIterator.SEG_LINETO -> current.add(new float[]{seg[0], seg[1]});
				case PathIterator.SEG_CLOSE -> {
					if (!current.isEmpty()) {
						contours.add(toFlatArray(current));
						current.clear();
					}
				}
				default -> LOGGER.warn("Unexpected segment type {} in path — curves may be missing", type);
			}
			it.next();
		}
		if (!current.isEmpty()) {
			contours.add(toFlatArray(current));
		}
		return contours;
	}

	private float[] toFlatArray(List<float[]> points) {
		float[] arr = new float[points.size() * 2];
		for (int i = 0; i < points.size(); i++) {
			arr[i * 2] = points.get(i)[0];
			arr[i * 2 + 1] = points.get(i)[1];
		}
		return arr;
	}

	RawMeshData extrude(float[] contour, float depth, float globalMaxY, String name) {
		int n = contour.length / 2;
		if (n < 3) {
			return null;
		}

		float[] vertices = new float[n * 2 * 3];
		for (int i = 0; i < n; i++) {
			float x = contour[i * 2] * PX_TO_MM;
			float y = (globalMaxY - contour[i * 2 + 1]) * PX_TO_MM;
			vertices[i * 3] = x;
			vertices[i * 3 + 1] = y;
			vertices[i * 3 + 2] = 0;
			vertices[(n + i) * 3] = x;
			vertices[(n + i) * 3 + 1] = y;
			vertices[(n + i) * 3 + 2] = depth;
		}

		int faceCount = (n - 2) * 2 + n * 2;
		int[] faces = new int[faceCount * 3];
		int fi = 0;

		// Bottom cap — fan from vertex 0 (CW when viewed from -z)
		for (int i = 1; i < n - 1; i++) {
			faces[fi++] = 0;
			faces[fi++] = i+1;
			faces[fi++] = i;
		}

		// Top cap — fan from vertex n (CCW when viewed from +z)
		for (int i = 1; i < n - 1; i++) {
			faces[fi++] = n;
			faces[fi++] = n+i;
			faces[fi++] = n+i+1;
		}

		// Side quads as two triangles each
		for (int i = 0; i < n; i++) {
			int next = (i + 1) % n;
			faces[fi++] = i;
			faces[fi++] = next;
			faces[fi++] = n+i;

			faces[fi++] = next;
			faces[fi++] = n+next;
			faces[fi++] = n+i;
		}

		return new RawMeshData(vertices, faces, 0, name);
	}
}
