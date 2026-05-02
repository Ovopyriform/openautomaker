package celtech.modelcontrol;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.configuration.fileRepresentation.PrinterDefinitionFile;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.utils.RectangularBounds;
import org.openautomaker.ui.state.SelectedPrinter;

import celtech.coreUI.visualisation.ScreenExtents;
import celtech.coreUI.visualisation.ScreenExtentsProvider;
import celtech.coreUI.visualisation.ScreenExtentsProviderTwoD;
import celtech.coreUI.visualisation.ShapeProviderTwoD;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

public abstract class ProjectifiableThing extends Group implements ScreenExtentsProviderTwoD, ShapeProviderTwoD {

	private Path modelFile;
	private String rbxprojEntryPath;
	private String rbxprojContentHash;
	protected boolean isCollided = false;
	protected BooleanProperty isSelected = new SimpleBooleanProperty(false);
	protected BooleanProperty isOffBed = new SimpleBooleanProperty(false);
	protected ScreenExtents extents = null;
	private List<ShapeProviderTwoD.ShapeChangeListener> shapeChangeListeners = new ArrayList<>();
	private List<ScreenExtentsProviderTwoD.ScreenExtentsListener> screenExtentsChangeListeners = new ArrayList<>();
	protected double printVolumeWidth = 0;
	protected double printVolumeDepth = 0;
	protected double printVolumeHeight = 0;
	protected Group bed;

	/**
	 * The modelId is only guaranteed unique at the project level because it could be reloaded with duplicate values from saved models into other projects.
	 */
	protected int modelId;
	private SimpleStringProperty modelName;

	protected Translate transformBedCentre = new Translate(0, 0, 0);

	protected Scale transformScalePreferred = new Scale(1, 1, 1);

	protected Rotate transformRotateTurnPreferred = new Rotate(0, 0, 0, 0, Z_AXIS);

	protected Translate transformMoveToPreferred = new Translate(0, 0, 0);

	protected List<Transform> rotationTransforms = new ArrayList<>();;
	protected RectangularBounds lastTransformedBoundsInParent;
	protected RectangularBounds originalModelBounds;

	protected static final Point3D Y_AXIS = new Point3D(0, 1, 0);
	protected static final Point3D Z_AXIS = new Point3D(0, 0, 1);
	protected static final Point3D X_AXIS = new Point3D(1, 0, 0);

	protected DoubleProperty preferredXScale = new SimpleDoubleProperty(1);
	protected DoubleProperty preferredYScale = new SimpleDoubleProperty(1);
	protected DoubleProperty preferredZScale = new SimpleDoubleProperty(1);

	protected DoubleProperty preferredRotationLean = new SimpleDoubleProperty(0);
	protected DoubleProperty preferredRotationTwist = new SimpleDoubleProperty(0);
	protected DoubleProperty preferredRotationTurn = new SimpleDoubleProperty(0);

	protected double bedCentreOffsetX;
	protected double bedCentreOffsetY;
	protected double bedCentreOffsetZ;

	@Inject
	protected transient SelectedPrinter selectedPrinter;

	@Inject
	protected transient PrinterContainer printerContainer;

	public ProjectifiableThing() {
		isSelected = new SimpleBooleanProperty(false);
		isOffBed = new SimpleBooleanProperty(false);
		shapeChangeListeners = new ArrayList<>();
		screenExtentsChangeListeners = new ArrayList<>();
	}

	public ProjectifiableThing(Path modelFile) {
		this();
		this.modelFile = modelFile;
	}

	@PostConstruct
	protected void postConstruct() {
		selectedPrinter.addListener((observable, oldValue, newValue) -> {
			updatePrintVolumeBounds(newValue);
		});

		updatePrintVolumeBounds(selectedPrinter.get());
	}

	public int getModelId() {
		return modelId;
	}

	public abstract ItemState getState();

	public abstract void setState(ItemState state);

	/**
	 * Make a copy of this ModelContainer and return it.
	 *
	 * @return
	 */
	public abstract ProjectifiableThing makeCopy();

	public abstract void clearElements();

	public void setModelFile(Path modelFile) {
		this.modelFile = modelFile;
	}

	public Path getModelFile() {
		return modelFile;
	}

	public String getRbxprojEntryPath() {
		return rbxprojEntryPath;
	}

	public void setRbxprojEntryPath(String rbxprojEntryPath) {
		this.rbxprojEntryPath = rbxprojEntryPath;
	}

	public String getRbxprojContentHash() {
		return rbxprojContentHash;
	}

	public void setRbxprojContentHash(String rbxprojContentHash) {
		this.rbxprojContentHash = rbxprojContentHash;
	}

	public final void addChildNodes(ObservableList<Node> nodes) {
		getChildren().addAll(nodes);
	}

	public final void addChildNode(Node node) {
		getChildren().add(node);
	}

	public final ObservableList<Node> getChildNodes() {
		return getChildren();
	}

	public void setSelected(boolean selected) {
		isSelected.set(selected);
		selectedAction();
	}

	public final boolean isSelected() {
		return isSelected.get();
	}

	public abstract void selectedAction();

	public final void setModelName(String modelName) {
		if (this.modelName == null) {
			this.modelName = new SimpleStringProperty();
		}
		this.modelName.set(modelName);
	}

	public final String getModelName() {
		return modelName.get();
	}

	public final void setCollided(boolean collided) {
		this.isCollided = collided;
	}

	public final boolean isCollided() {
		return isCollided;
	}

	protected abstract boolean recalculateScreenExtents();

	@Override
	public final ScreenExtents getScreenExtents() {
		if (extents == null) {
			recalculateScreenExtents();
		}
		return extents;
	}

	@Override
	public final void addScreenExtentsChangeListener(ScreenExtentsProvider.ScreenExtentsListener listener) {
		recalculateScreenExtents();
		screenExtentsChangeListeners.add(listener);
	}

	@Override
	public final void removeScreenExtentsChangeListener(
			ScreenExtentsProvider.ScreenExtentsListener listener) {
		screenExtentsChangeListeners.remove(listener);
	}

	public final void notifyScreenExtentsChange() {
		if (recalculateScreenExtents()) {
			for (ScreenExtentsProvider.ScreenExtentsListener screenExtentsListener : screenExtentsChangeListeners) {
				screenExtentsListener.screenExtentsChanged(this);
			}
		}
	}

	@Override
	public final void addShapeChangeListener(ShapeProviderTwoD.ShapeChangeListener listener) {
		shapeChangeListeners.add(listener);
	}

	@Override
	public final void removeShapeChangeListener(ShapeProviderTwoD.ShapeChangeListener listener) {
		shapeChangeListeners.remove(listener);
	}

	/**
	 * This method must be called at the end of any operation that changes one or more of the transforms.
	 */
	public final void notifyShapeChange() {
		for (ShapeProviderTwoD.ShapeChangeListener shapeChangeListener : shapeChangeListeners) {
			shapeChangeListener.shapeChanged(this);
		}
	}

	private void updatePrintVolumeBounds(Printer printer) {
		if (printer != null
				&& printer.printerConfigurationProperty().get() != null) {
			printVolumeWidth = printer.printerConfigurationProperty().get().printVolumeWidth;
			printVolumeDepth = printer.printerConfigurationProperty().get().printVolumeDepth;
			printVolumeHeight = printer.printerConfigurationProperty().get().printVolumeHeight;
		}
		else {
			PrinterDefinitionFile defaultPrinterConfiguration = printerContainer.getPrinterByID(PrinterContainer.defaultPrinterID);
			printVolumeWidth = defaultPrinterConfiguration.printVolumeWidth;
			printVolumeDepth = defaultPrinterConfiguration.printVolumeDepth;
			printVolumeHeight = defaultPrinterConfiguration.printVolumeHeight;
		}
		printVolumeBoundsUpdated();
	}

	protected abstract void printVolumeBoundsUpdated();

	public abstract void checkOffBed();

	public abstract void moveToCentre();

	public void setBedReference(Group bed) {
		this.bed = bed;
	}

	public abstract void setBedCentreOffsetTransform();

	public RectangularBounds getOriginalModelBounds() {
		return originalModelBounds;
	}

	public abstract void shrinkToFitBed();

	protected abstract RectangularBounds calculateBoundsInLocal();

	public abstract RectangularBounds calculateBoundsInBedCoordinateSystem();

	protected final void setScalePivotToCentreOfModel() {
		transformScalePreferred.setPivotX(getBoundsInLocal().getMinX()
				+ getBoundsInLocal().getWidth() / 2.0);
		transformScalePreferred.setPivotY(getBoundsInLocal().getMinY()
				+ getBoundsInLocal().getHeight() / 2.0);

		if (this instanceof ScaleableThreeD) {
			transformScalePreferred.setPivotZ(getBoundsInLocal().getMinZ()
					+ getBoundsInLocal().getDepth() / 2.0);
		}
	}

	protected void setRotationPivotsToCentreOfModel() {
		transformRotateTurnPreferred.setPivotX(originalModelBounds.getCentreX());
		transformRotateTurnPreferred.setPivotY(originalModelBounds.getCentreY());

		if (this instanceof RotatableThreeD) {
			transformRotateTurnPreferred.setPivotZ(originalModelBounds.getCentreZ());
		}
	}

	public void updateOriginalModelBounds() {
		originalModelBounds = calculateBoundsInLocal();
		setScalePivotToCentreOfModel();
		setRotationPivotsToCentreOfModel();
	}

	public abstract RectangularBounds calculateBoundsInParentCoordinateSystem();

	protected abstract void updateScaleTransform(boolean dropToBed);

	public Scale getTransformScale() {
		return transformScalePreferred;
	}

	public double getXScale() {
		return preferredXScale.get();
	}

	public double getYScale() {
		return preferredYScale.get();
	}

	public void setXScale(double scaleFactor, boolean dropToBed) {
		preferredXScale.set(scaleFactor);
		transformScalePreferred.setX(scaleFactor);
		updateScaleTransform(dropToBed);
	}

	public void setYScale(double scaleFactor, boolean dropToBed) {
		preferredYScale.set(scaleFactor);
		transformScalePreferred.setY(scaleFactor);
		updateScaleTransform(dropToBed);
	}

	public double getTransformedCentreDepth() {
		if (this instanceof TranslateableThreeD) {
			return lastTransformedBoundsInParent.getCentreZ();
		}
		else {
			return lastTransformedBoundsInParent.getCentreY();
		}
	}

	public double getTransformedCentreX() {
		return lastTransformedBoundsInParent.getCentreX();
	}
}
