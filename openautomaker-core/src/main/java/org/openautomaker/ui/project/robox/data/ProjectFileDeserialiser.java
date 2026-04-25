package org.openautomaker.ui.project.robox.data;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Coerces legacy project files (pre-v4, no {@code projectType} field) into {@link ProjectFile}.
 * Modern files are deserialized directly.
 */
public class ProjectFileDeserialiser extends StdDeserializer<ProjectFile> {

	public ProjectFileDeserialiser() {
		this(null);
	}

	public ProjectFileDeserialiser(Class<?> vc) {
		super(vc);
	}

	private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper();

	@Override
	public ProjectFile deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
		ObjectMapper mapper = (ObjectMapper) jp.getCodec();
		ObjectNode root = (ObjectNode) mapper.readTree(jp);

		JsonNode versionNode = root.get("version");
		if (versionNode != null && versionNode.asInt() > 0 && versionNode.asInt() < 4) {
			// Legacy file with no projectType — was always a model project
			return PLAIN_MAPPER.treeToValue(root, ProjectFile.class);
		}

		JsonNode typeNode = root.get("projectType");
		if (typeNode == null) {
			return null;
		}

		return PLAIN_MAPPER.treeToValue(root, ProjectFile.class);
	}
}
