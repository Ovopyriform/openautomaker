
package org.openautomaker.base;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.inject.Singleton;

import jakarta.inject.Inject;
import javafx.scene.paint.Color;

@Singleton
public class PrinterColourMap {

	private final Map<Color, Color> colourMap;
	private final List<Color> printerColours;
	private final List<Color> displayColours;

	@Inject
	protected PrinterColourMap() {
		colourMap = Map.ofEntries(
				Map.entry(Color.web("#7F6E32"), Color.web("#7F7F7F")),
				Map.entry(Color.web("#FFD764"), Color.web("#FFFFFF")),
				Map.entry(Color.web("#000000"), Color.web("#000000")),
				Map.entry(Color.web("#000064"), Color.web("#00007F")),
				Map.entry(Color.web("#006400"), Color.web("#007F00")),
				Map.entry(Color.web("#640000"), Color.web("#7F0000")),
				Map.entry(Color.web("#643214"), Color.web("#FF7F7F")),
				Map.entry(Color.web("#34FF10"), Color.web("#7FFF7F")),
				Map.entry(Color.web("#7F6EFF"), Color.web("#7F7FFF")),
				Map.entry(Color.web("#7FFF00"), Color.web("#7FFF00")),
				Map.entry(Color.web("#00FF00"), Color.web("#00FF00")),
				Map.entry(Color.web("#00FF28"), Color.web("#00FF96")),
				Map.entry(Color.web("#00FF78"), Color.web("#00FFFF")),
				Map.entry(Color.web("#0000FF"), Color.web("#0000FF")),
				Map.entry(Color.web("#BE00FF"), Color.web("#7F00FF")),
				Map.entry(Color.web("#FF0082"), Color.web("#FF00FF")),
				Map.entry(Color.web("#FF0000"), Color.web("#FF0000")),
				Map.entry(Color.web("#FF4600"), Color.web("#FF7F00")),
				Map.entry(Color.web("#FFC800"), Color.web("#FFFF00")),
				Map.entry(Color.web("#FFFFFF"), Color.web("#A3A3A3")));

		printerColours = List.copyOf(colourMap.keySet());
		displayColours = List.copyOf(colourMap.values());
	}

	/**
	 *
	 * @param colour
	 * @return
	 */
	public Color printerToDisplayColour(Color colour) {
		return colourMap.get(colour);
	}

	/**
	 *
	 * @param colour
	 * @return
	 */
	public Color displayToPrinterColour(Color colour) {
		Color printerColour = null;

		for (Entry<Color, Color> colourEntry : colourMap.entrySet()) {
			if (colourEntry.getValue().equals(colour)) {
				printerColour = colourEntry.getKey();
				break;
			}
		}
		return printerColour;
	}

	public List<Color> getPrinterColours() {
		return printerColours;
	}

	public List<Color> getDisplayColours() {
		return displayColours;
	}
}
