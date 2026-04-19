package org.openautomaker.project.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PlacementsData {

	public final int version;
	public final List<ModelPlacement> placements;
	/** Maps group modelId → set of child modelIds */
	public final Map<Integer, Set<Integer>> groups;
	/** Maps group modelId → transform for that group container */
	public final Map<Integer, ModelTransformData> groupTransforms;

	@JsonCreator
	public PlacementsData(
			@JsonProperty("version") int version,
			@JsonProperty("placements") List<ModelPlacement> placements,
			@JsonProperty("groups") Map<Integer, Set<Integer>> groups,
			@JsonProperty("groupTransforms") Map<Integer, ModelTransformData> groupTransforms) {
		this.version = version;
		this.placements = placements != null ? placements : new ArrayList<>();
		this.groups = groups != null ? groups : new HashMap<>();
		this.groupTransforms = groupTransforms != null ? groupTransforms : new HashMap<>();
	}
}
