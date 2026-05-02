package org.openautomaker.ui.project;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.utils.RectangularBounds;
import org.openautomaker.base.utils.Math.packing.core.Bin;
import org.openautomaker.base.utils.Math.packing.core.BinPacking;
import org.openautomaker.base.utils.Math.packing.primitives.MArea;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.appManager.Project;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Service for automatically laying out parts in a project within the print volume of the selected printer.
 */
@Singleton
public class ProjectLayoutService {

	private final BinPacking binPacking;
	private final SelectedPrinter selectedPrinter;
	private final PrinterContainer printerContainer;

	@Inject
	public ProjectLayoutService(
			BinPacking binPacking,
			SelectedPrinter selectedPrinter,
			PrinterContainer printerContainer) {

		this.binPacking = binPacking;
		this.selectedPrinter = selectedPrinter;
		this.printerContainer = printerContainer;
	}

	/**
	 * Automatically lays out the top-level things in the project within the print volume of the selected printer.
	 *
	 * @param project The project to layout.
	 */
	public void autoLayout(Project project) {
		autoLayout(project, project.getTopLevelThings());
	}

	/**
	 * Automatically lays out the specified things in the project within the print volume of the selected printer.
	 *
	 * @param project The project to layout.
	 * @param thingsToLayout The list of things to layout. If null or empty, all top-level things will be laid out.
	 */
	public void autoLayout(Project project, List<ProjectifiableThing> thingsToLayout) {
		double printVolumeWidth = 0;
		double printVolumeDepth = 0;

		Printer selPrinter = selectedPrinter.get();
		if (selPrinter != null && selPrinter.printerConfigurationProperty().get() != null) {
			printVolumeWidth = selPrinter.printerConfigurationProperty().get().printVolumeWidth;
			printVolumeDepth = selPrinter.printerConfigurationProperty().get().printVolumeDepth;
		}
		else {
			PrinterDefinitionFile defaultPrinter = printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID);
			printVolumeWidth = defaultPrinter.printVolumeWidth;
			printVolumeDepth = defaultPrinter.printVolumeDepth;
		}

		Dimension binDimension = new Dimension((int) printVolumeWidth, (int) printVolumeDepth);
		Bin layoutBin = null;
		final double spacing = 2.5;
		final double halfSpacing = spacing / 2.0;

		List<ProjectifiableThing> topLevelThings = project.getTopLevelThings();
		Map<Integer, ProjectifiableThing> partMap = new HashMap<>();
		int numberOfPartsNotToLayout = topLevelThings.size() - thingsToLayout.size();

		if (numberOfPartsNotToLayout > 0) {
			MArea[] existingPieces = new MArea[numberOfPartsNotToLayout];
			int existingPartCounter = 0;
			for (ProjectifiableThing t : topLevelThings) {
				if (!thingsToLayout.contains(t)) {
					RectangularBounds bounds = t.calculateBoundsInBedCoordinateSystem();
					Rectangle2D.Double rect = new Rectangle2D.Double(
							bounds.getMinX() - halfSpacing, printVolumeDepth - bounds.getMaxZ() - halfSpacing,
							bounds.getWidth() + halfSpacing, bounds.getDepth() + halfSpacing);
					existingPieces[existingPartCounter] = new MArea(rect, existingPartCounter, ((ModelContainer) t).getRotationTurn());
					partMap.put(existingPartCounter, t);
					existingPartCounter++;
				}
			}
			layoutBin = new Bin(binDimension, existingPieces);
		}

		int startingIndex = (numberOfPartsNotToLayout == 0) ? 0 : numberOfPartsNotToLayout - 1;
		MArea[] partsToLayout = new MArea[thingsToLayout.size()];
		int counter = 0;
		for (ProjectifiableThing t : thingsToLayout) {
			RectangularBounds bounds = t.calculateBoundsInBedCoordinateSystem();
			Rectangle2D.Double rect = new Rectangle2D.Double(0, 0, bounds.getWidth() + spacing, bounds.getDepth() + spacing);
			partsToLayout[counter] = new MArea(rect, counter + startingIndex, 0);
			partMap.put(counter + startingIndex, t);
			counter++;
		}

		if (layoutBin != null) {
			layoutBin.BBCompleteStrategy(partsToLayout);
		}
		else {
			Bin[] bins = binPacking.BinPackingStrategy(partsToLayout, binDimension, binDimension);
			layoutBin = bins[0];
		}

		int total = layoutBin.getPlacedPieces().length;
		double[] newX = new double[total], newDepth = new double[total], newRot = new double[total];
		double minX = 999, maxX = -999, minY = 999, maxY = -999;

		for (int i = 0; i < total; i++) {
			MArea area = layoutBin.getPlacedPieces()[i];
			newRot[i] = area.getRotation();
			newDepth[i] = printVolumeDepth - area.getBoundingBox2D().getMaxY() + area.getBoundingBox2D().getHeight() / 2.0;
			newX[i] = area.getBoundingBox2D().getMinX() + area.getBoundingBox2D().getWidth() / 2.0;
			maxX = Math.max(maxX, area.getBoundingBox2D().getMaxX());
			minX = Math.min(minX, area.getBoundingBox2D().getMinX());
			maxY = Math.max(maxY, area.getBoundingBox2D().getMaxY());
			minY = Math.min(minY, area.getBoundingBox2D().getMinY());
		}

		double xOffset = (printVolumeWidth - (maxX - minX)) / 2.0;
		double yOffset = (printVolumeDepth - (maxY - minY)) / 2.0;

		for (int i = 0; i < layoutBin.getPlacedPieces().length; i++) {
			MArea area = layoutBin.getPlacedPieces()[i];
			ModelContainer container = (ModelContainer) partMap.get(area.getID());
			if (thingsToLayout.contains(container)) {
				double rotation = newRot[i] + container.getRotationTurn();
				if (rotation >= 360.0) rotation -= 360.0;
				container.setRotationTurn(rotation);
				if (numberOfPartsNotToLayout == 0) container.translateTo(newX[i] + xOffset, newDepth[i] + yOffset);
				else container.translateTo(newX[i], newDepth[i]);
			}
		}
	}
}
