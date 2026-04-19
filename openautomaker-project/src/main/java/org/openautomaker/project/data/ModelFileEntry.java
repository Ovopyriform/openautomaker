package org.openautomaker.project.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry in the models manifest: maps a stored ZIP path back to its original filename
 * and records the content hash used for deduplication.
 */
public class ModelFileEntry {

	/** Path within the ZIP archive, e.g. {@code "models/a1b2c3d4-part.stl"} */
	public final String zipPath;

	/** Original filename before the UUID prefix was added, e.g. {@code "part.stl"} */
	public final String originalName;

	/** Hex-encoded SHA-256 of the file content. Used to detect duplicates at write time. */
	public final String contentHash;

	@JsonCreator
	public ModelFileEntry(
			@JsonProperty("zipPath") String zipPath,
			@JsonProperty("originalName") String originalName,
			@JsonProperty("contentHash") String contentHash) {
		this.zipPath = zipPath;
		this.originalName = originalName;
		this.contentHash = contentHash;
	}
}
