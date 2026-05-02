package org.openautomaker.base.configuration.fileRepresentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NozzleData {

	public final float diameter;
	public final float defaultXOffset;
	public final float minXOffset;
	public final float maxXOffset;
	public final float defaultYOffset;
	public final float minYOffset;
	public final float maxYOffset;
	public final float defaultZOffset;
	public final float minZOffset;
	public final float maxZOffset;
	public final float defaultBOffset;
	public final float minBOffset;
	public final float maxBOffset;
	public final float defaultExtrusionWidth;
	public final float minExtrusionWidth;
	public final float maxExtrusionWidth;
	public final String associatedExtruder;

	@JsonCreator
	public NozzleData(
			@JsonProperty("diameter") float diameter,
			@JsonProperty("defaultXOffset") float defaultXOffset,
			@JsonProperty("minXOffset") float minXOffset,
			@JsonProperty("maxXOffset") float maxXOffset,
			@JsonProperty("defaultYOffset") float defaultYOffset,
			@JsonProperty("minYOffset") float minYOffset,
			@JsonProperty("maxYOffset") float maxYOffset,
			@JsonProperty("defaultZOffset") float defaultZOffset,
			@JsonProperty("minZOffset") float minZOffset,
			@JsonProperty("maxZOffset") float maxZOffset,
			@JsonProperty("defaultBOffset") float defaultBOffset,
			@JsonProperty("minBOffset") float minBOffset,
			@JsonProperty("maxBOffset") float maxBOffset,
			@JsonProperty("defaultExtrusionWidth") float defaultExtrusionWidth,
			@JsonProperty("minExtrusionWidth") float minExtrusionWidth,
			@JsonProperty("maxExtrusionWidth") float maxExtrusionWidth,
			@JsonProperty("associatedExtruder") String associatedExtruder) {
		this.diameter = diameter;
		this.defaultXOffset = defaultXOffset;
		this.minXOffset = minXOffset;
		this.maxXOffset = maxXOffset;
		this.defaultYOffset = defaultYOffset;
		this.minYOffset = minYOffset;
		this.maxYOffset = maxYOffset;
		this.defaultZOffset = defaultZOffset;
		this.minZOffset = minZOffset;
		this.maxZOffset = maxZOffset;
		this.defaultBOffset = defaultBOffset;
		this.minBOffset = minBOffset;
		this.maxBOffset = maxBOffset;
		this.defaultExtrusionWidth = defaultExtrusionWidth;
		this.minExtrusionWidth = minExtrusionWidth;
		this.maxExtrusionWidth = maxExtrusionWidth;
		this.associatedExtruder = associatedExtruder;
	}
}
