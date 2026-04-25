package org.openautomaker.project.rbxproj;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RbxProjFileTest {

	@Test
	void gcodeEntry_returnsExpectedPath() {
		assertThat(RbxprojFile.gcodeEntry("RBX01"))
				.isEqualTo("gcode/RBX01/output.gcode");
	}

	@Test
	void gcodeSettingsEntry_returnsExpectedPath() {
		assertThat(RbxprojFile.gcodeSettingsEntry("RBX02"))
				.isEqualTo("gcode/RBX02/settings.json");
	}

	@Test
	void gcodePrinterDir_returnsExpectedPath() {
		assertThat(RbxprojFile.gcodePrinterDir("RBX10"))
				.isEqualTo("gcode/RBX10/");
	}

	@Test
	void sanitisePrinterTypeCode_preservesAlphanumericAndAllowedChars() {
		assertThat(RbxprojFile.sanitisePrinterTypeCode("RBX01")).isEqualTo("RBX01");
		assertThat(RbxprojFile.sanitisePrinterTypeCode("my-printer.v2")).isEqualTo("my-printer.v2");
	}

	@Test
	void sanitisePrinterTypeCode_replacesInvalidChars() {
		assertThat(RbxprojFile.sanitisePrinterTypeCode("RBX01 #special/path"))
				.isEqualTo("RBX01__special_path");
	}

	@Test
	void constants_haveExpectedValues() {
		assertThat(RbxprojFile.EXTENSION).isEqualTo(".rbxproj");
		assertThat(RbxprojFile.EXTENSION_WITHOUT_DOT).isEqualTo("rbxproj");
		assertThat(RbxprojFile.MODELS_DIR).isEqualTo("models/");
		assertThat(RbxprojFile.MODELS_MANIFEST_ENTRY).isEqualTo("models/manifest.json");
		assertThat(RbxprojFile.GCODE_FILENAME).isEqualTo("output.gcode");
		assertThat(RbxprojFile.GCODE_SETTINGS_FILENAME).isEqualTo("settings.json");
		assertThat(RbxprojFile.CURRENT_VERSION).isEqualTo(1);
	}
}
