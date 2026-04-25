package org.openautomaker.project.rbxproj.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in the models manifest: maps a stored project-relative path back to its original
 * filename and records the content hash used for deduplication.
 */
public class ModelFileEntry {

	/** Project-relative path, e.g. {@code "models/a1b2c3d4-part.stl"} */
	public final String filePath;

	/** Original filename before the UUID prefix was added, e.g. {@code "part.stl"} */
	public final String originalName;

	/** Hex-encoded SHA-256 of the file content. Used to detect duplicates at write time. */
	public final String contentHash;

	@JsonCreator
	public ModelFileEntry(
			@JsonProperty("filePath") String filePath,
			@JsonProperty("originalName") String originalName,
			@JsonProperty("contentHash") String contentHash) {
		this.filePath = filePath;
		this.originalName = originalName;
		this.contentHash = contentHash;
	}
}
