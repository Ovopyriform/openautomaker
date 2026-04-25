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
		PrintSettingsData ps = new PrintSettingsData();
		ps.setExtruder0FilamentID("RBXFF-000A");
		ps.setExtruder1FilamentID("NULL");
		ps.setSettingsName("Draft");
		ps.setPrintQuality("FINE");
		ps.setBrimOverride(3);
		ps.setFillDensityOverride(0.35f);
		ps.setFillDensityOverridenByUser(true);
		ps.setPrintSupportOverride(true);
		ps.setPrintSupportTypeOverride("MATERIAL_2");
		ps.setPrintRaft(true);
		ps.setSpiralPrint(false);

		GcodeSettingsData original = new GcodeSettingsData("RBX02", "DUAL_MATERIAL_HEAD", ps);
		String json = mapper.writeValueAsString(original);
		GcodeSettingsData restored = mapper.readValue(json, GcodeSettingsData.class);

		assertThat(restored.printerTypeCode).isEqualTo("RBX02");
		assertThat(restored.headType).isEqualTo("DUAL_MATERIAL_HEAD");
		assertThat(restored.printSettings.getExtruder0FilamentID()).isEqualTo("RBXFF-000A");
		assertThat(restored.printSettings.getPrintQuality()).isEqualTo("FINE");
		assertThat(restored.printSettings.getBrimOverride()).isEqualTo(3);
		assertThat(restored.printSettings.getFillDensityOverride()).isEqualTo(0.35f);
		assertThat(restored.printSettings.isFillDensityOverridenByUser()).isTrue();
		assertThat(restored.printSettings.isPrintSupportOverride()).isTrue();
		assertThat(restored.printSettings.getPrintSupportTypeOverride()).isEqualTo("MATERIAL_2");
		assertThat(restored.printSettings.isPrintRaft()).isTrue();
		assertThat(restored.printSettings.isSpiralPrint()).isFalse();
	}

	@Test
	void serialise_includesExpectedTopLevelKeys() throws Exception {
		GcodeSettingsData settings = new GcodeSettingsData("RBX01", "SINGLE_MATERIAL_HEAD", new PrintSettingsData());
		String json = mapper.writeValueAsString(settings);

		assertThat(json).contains("printerTypeCode");
		assertThat(json).contains("headType");
		assertThat(json).contains("printSettings");
	}
}
