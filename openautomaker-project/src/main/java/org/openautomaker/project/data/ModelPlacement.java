package org.openautomaker.project.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelPlacement {

	public final int modelId;
	/** Path within the ZIP archive, e.g. "models/abc123-part.stl" */
	public final String modelFile;
	public final String modelName;
	public final int extruder;
	public final ModelTransformData transform;

	@JsonCreator
	public ModelPlacement(
			@JsonProperty("modelId") int modelId,
			@JsonProperty("modelFile") String modelFile,
			@JsonProperty("modelName") String modelName,
			@JsonProperty("extruder") int extruder,
			@JsonProperty("transform") ModelTransformData transform) {
		this.modelId = modelId;
		this.modelFile = modelFile;
		this.modelName = modelName;
		this.extruder = extruder;
		this.transform = transform;
	}
}
