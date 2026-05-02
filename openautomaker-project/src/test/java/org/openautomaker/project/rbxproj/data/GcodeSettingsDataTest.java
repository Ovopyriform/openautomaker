package org.openautomaker.project.rbxproj.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class GcodeSettingsDataTest {

	private final ObjectMapper mapper = new ObjectMapper()
			.configure(SerializationFeature.INDENT_OUTPUT, true);

	@Test
	void roundTrip_preservesAllFields() throws Exception {
		PrintSettingsData ps = new PrintSettingsData(
				"RBXFF-000A", "NULL", "Draft", "FINE", 3, 0.35f, true, true, "MATERIAL_2", true, false);

		GcodeSettingsData original = new GcodeSettingsData("RBX02", "DUAL_MATERIAL_HEAD", ps);
		String json = mapper.writeValueAsString(original);
		GcodeSettingsData restored = mapper.readValue(json, GcodeSettingsData.class);

		assertThat(restored.printerTypeCode).isEqualTo("RBX02");
		assertThat(restored.headType).isEqualTo("DUAL_MATERIAL_HEAD");
		assertThat(restored.printSettings.extruder0FilamentID).isEqualTo("RBXFF-000A");
		assertThat(restored.printSettings.printQuality).isEqualTo("FINE");
		assertThat(restored.printSettings.brimOverride).isEqualTo(3);
		assertThat(restored.printSettings.fillDensityOverride).isEqualTo(0.35f);
		assertThat(restored.printSettings.fillDensityOverridenByUser).isTrue();
		assertThat(restored.printSettings.printSupportOverride).isTrue();
		assertThat(restored.printSettings.printSupportTypeOverride).isEqualTo("MATERIAL_2");
		assertThat(restored.printSettings.printRaft).isTrue();
		assertThat(restored.printSettings.spiralPrint).isFalse();
	}

	@Test
	void serialise_includesExpectedTopLevelKeys() throws Exception {
		GcodeSettingsData settings = new GcodeSettingsData("RBX01", "SINGLE_MATERIAL_HEAD",
				new PrintSettingsData(null, null, null, null, 0, 0f, false, false, null, false, false));
		String json = mapper.writeValueAsString(settings);

		assertThat(json).contains("printerTypeCode");
		assertThat(json).contains("headType");
		assertThat(json).contains("printSettings");
	}
}
