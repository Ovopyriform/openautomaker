package org.openautomaker.project.rbxproj.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelTransformData {

	public final double x;
	public final double y;
	public final double z;
	public final double xScale;
	public final double yScale;
	public final double zScale;
	public final double rotationTurn;
	public final double rotationLean;
	public final double rotationTwist;

	@JsonCreator
	public ModelTransformData(
			@JsonProperty("x") double x,
			@JsonProperty("y") double y,
			@JsonProperty("z") double z,
			@JsonProperty("xScale") double xScale,
			@JsonProperty("yScale") double yScale,
			@JsonProperty("zScale") double zScale,
			@JsonProperty("rotationTurn") double rotationTurn,
			@JsonProperty("rotationLean") double rotationLean,
			@JsonProperty("rotationTwist") double rotationTwist) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.xScale = xScale;
		this.yScale = yScale;
		this.zScale = zScale;
		this.rotationTurn = rotationTurn;
		this.rotationLean = rotationLean;
		this.rotationTwist = rotationTwist;
	}
}
