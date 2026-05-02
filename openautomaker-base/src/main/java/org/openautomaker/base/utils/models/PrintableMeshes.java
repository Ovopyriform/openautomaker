package org.openautomaker.base.utils.models;

import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.openautomaker.base.configuration.RoboxProfile;
import org.openautomaker.base.configuration.datafileaccessors.HeadContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.configuration.fileRepresentation.PrinterSettingsOverrides;
import org.openautomaker.base.services.camera.CameraTriggerData;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;
import org.openautomaker.environment.Slicer;

import com.google.inject.assistedinject.Assisted;

import jakarta.inject.Inject;

public class PrintableMeshes {

	private final List<MeshForProcessing> meshesForProcessing;
	private final List<Boolean> usedExtruders;
	private final List<Integer> extruderForModel;
	private final String projectName;
	private final String requiredPrintJobID;
	private final RoboxProfile settings;
	private final PrinterSettingsOverrides printOverrides;
	private final PrintQualityEnumeration printQuality;
	private final Slicer defaultSlicerType;
	private final Vector3D centreOfPrintedObject;
	private final boolean safetyFeaturesRequired;
	private final boolean cameraEnabled;
	private final CameraTriggerData cameraTriggerData;

	private final HeadContainer headContainer;

	@Inject
	protected PrintableMeshes(
			HeadContainer headContainer,
			@Assisted List<MeshForProcessing> meshesForProcessing,
			@Assisted List<Boolean> usedExtruders,
			@Assisted List<Integer> extruderForModel,
			@Assisted("projectName") String projectName,
			@Assisted("requiredPrintJobID") String requiredPrintJobID,
			@Assisted RoboxProfile settings,
			@Assisted PrinterSettingsOverrides printOverrides,
			@Assisted PrintQualityEnumeration printQuality,
			@Assisted Slicer defaultSlicerType,
			@Assisted Vector3D centreOfPrintedObject,
			@Assisted("safetyFeaturesRequired") boolean safetyFeaturesRequired,
			@Assisted("cameraEnabled") boolean cameraEnabled,
			@Assisted CameraTriggerData cameraTriggerData) {

		this.headContainer = headContainer;

		this.meshesForProcessing = meshesForProcessing;
		this.usedExtruders = usedExtruders;
		this.extruderForModel = extruderForModel;
		this.projectName = projectName;
		this.requiredPrintJobID = requiredPrintJobID;
		this.settings = settings;
		this.printOverrides = printOverrides;
		this.printQuality = printQuality;
		this.defaultSlicerType = defaultSlicerType;
		this.centreOfPrintedObject = centreOfPrintedObject;
		this.safetyFeaturesRequired = safetyFeaturesRequired;
		this.cameraEnabled = cameraEnabled;
		this.cameraTriggerData = cameraTriggerData;
	}

	public List<MeshForProcessing> getMeshesForProcessing() {
		return meshesForProcessing;
	}

	public List<Boolean> getUsedExtruders() {
		return usedExtruders;
	}

	public List<Integer> getExtruderForModel() {
		return extruderForModel;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getRequiredPrintJobID() {
		return requiredPrintJobID;
	}

	public RoboxProfile getSettings() {
		return settings;
	}

	public PrinterSettingsOverrides getPrintOverrides() {
		return printOverrides;
	}

	public PrintQualityEnumeration getPrintQuality() {
		return printQuality;
	}

	public Slicer getDefaultSlicerType() {
		return defaultSlicerType;
	}

	public Vector3D getCentreOfPrintedObject() {
		return centreOfPrintedObject;
	}

	public boolean isSafetyFeaturesRequired() {
		return safetyFeaturesRequired;
	}

	public boolean isCameraEnabled() {
		return cameraEnabled;
	}

	public CameraTriggerData getCameraTriggerData() {
		return cameraTriggerData;
	}

	public int getNumberOfNozzles() {
		int nNozzles = 0;
		HeadFile printerHead = headContainer.getHeadByID(settings.getHeadType());
		if (printerHead != null)
			nNozzles = printerHead.nozzles.size();
		return nNozzles;
	}
}
