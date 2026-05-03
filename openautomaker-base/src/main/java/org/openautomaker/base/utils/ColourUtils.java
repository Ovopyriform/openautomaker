package org.openautomaker.base.utils;

import javafx.scene.paint.Color;

public final class ColourUtils {

	private ColourUtils() {
	}

	/**
	 * Formats a {@link Color} as a CSS hex string, e.g. {@code #FF8000}.
	 */
	public static String formatHexString(Color color) {
		return String.format("#%02X%02X%02X",
				Math.round(color.getRed() * 255),
				Math.round(color.getGreen() * 255),
				Math.round(color.getBlue() * 255));
	}
}
