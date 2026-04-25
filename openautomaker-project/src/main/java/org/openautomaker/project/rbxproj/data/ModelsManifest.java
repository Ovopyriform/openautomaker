package org.openautomaker.project.rbxproj.data;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Index of all model files stored in the archive.
 * Written to {@code models/manifest.json}.
 *
 * <p>Maps original filenames to their stored (UUID-prefixed) names and tracks content
 * hashes so identical files are stored only once.
 */
public class ModelsManifest {

	public final List<ModelFileEntry> entries;

	@JsonCreator
	public ModelsManifest(@JsonProperty("entries") List<ModelFileEntry> entries) {
		this.entries = entries != null ? entries : new ArrayList<>();
	}
}
