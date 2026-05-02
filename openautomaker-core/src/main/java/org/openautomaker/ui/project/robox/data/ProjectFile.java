package org.openautomaker.ui.project.robox.data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;
import org.openautomaker.base.configuration.fileRepresentation.SupportType;
import org.openautomaker.base.services.slicer.PrintQualityEnumeration;

import celtech.appManager.Project;
import celtech.modelcontrol.ItemState;

public class ProjectFile {

	private ProjectFileTypeEnum projectType = ProjectFileTypeEnum.MODEL;
	private int version = 5;
	private String projectName;
	private Date lastModifiedDate;
	private String lastPrintJobID = "";
	private boolean projectNameModified = false;
	private boolean timelapseTriggerEnabled = false;
	private String timelapseProfileName = "";
	private String timelapseCameraID = "";

	private int subVersion = 1;
	private int brimOverride = 0;
	private float fillDensityOverride = 0;
	private boolean fillDensityOverridenByUser = false;
	private boolean printSupportOverride = false;
	private SupportType printSupportTypeOverride = SupportType.MATERIAL_2;
	private boolean printRaft = false;
	private boolean spiralPrint = false;
	private String extruder0FilamentID;
	private String extruder1FilamentID;
	private String settingsName = BaseConfiguration.draftSettingsProfileName;
	private PrintQualityEnumeration printQuality = PrintQualityEnumeration.NORMAL;
	private Map<Integer, Set<Integer>> groupStructure = new HashMap<>();
	private Map<Integer, ItemState> groupState = new HashMap<>();

	public ProjectFileTypeEnum getProjectType() {
		return projectType;
	}

	public void setProjectType(ProjectFileTypeEnum projectType) {
		this.projectType = projectType;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public Date getLastModifiedDate() {
		return lastModifiedDate;
	}

	public void setLastModifiedDate(Date lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}

	public String getLastPrintJobID() {
		return lastPrintJobID;
	}

	public void setLastPrintJobID(String lastPrintJobID) {
		this.lastPrintJobID = lastPrintJobID;
	}

	public boolean isProjectNameModified() {
		return projectNameModified;
	}

	public void setProjectNameModified(boolean projectNameModified) {
		this.projectNameModified = projectNameModified;
	}

	public boolean isTimelapseTriggerEnabled() {
		return timelapseTriggerEnabled;
	}

	public void setTimelapseTriggerEnabled(boolean timelapseTriggerEnabled) {
		this.timelapseTriggerEnabled = timelapseTriggerEnabled;
	}

	public String getTimelapseProfileName() {
		return timelapseProfileName;
	}

	public void setTimelapseProfileName(String timelapseProfileName) {
		this.timelapseProfileName = timelapseProfileName;
	}

	public String getTimelapseCameraID() {
		return timelapseCameraID;
	}

	public void setTimelapseCameraID(String timelapseCameraID) {
		this.timelapseCameraID = timelapseCameraID;
	}

	public int getSubVersion() {
		return subVersion;
	}

	public void setSubVersion(int subVersion) {
		this.subVersion = subVersion;
	}

	public int getBrimOverride() {
		return brimOverride;
	}

	public void setBrimOverride(int brimOverride) {
		this.brimOverride = brimOverride;
	}

	public float getFillDensityOverride() {
		return fillDensityOverride;
	}

	public void setFillDensityOverride(float fillDensityOverride) {
		this.fillDensityOverride = fillDensityOverride;
	}

	public boolean isFillDensityOverridenByUser() {
		return fillDensityOverridenByUser;
	}

	public void setFillDensityOverridenByUser(boolean fillDensityOverridenByUser) {
		this.fillDensityOverridenByUser = fillDensityOverridenByUser;
	}

	public boolean getPrintSupportOverride() {
		return printSupportOverride;
	}

	public void setPrintSupportOverride(boolean printSupportOverride) {
		this.printSupportOverride = printSupportOverride;
	}

	public SupportType getPrintSupportTypeOverride() {
		return printSupportTypeOverride;
	}

	public void setPrintSupportTypeOverride(SupportType printSupportTypeOverride) {
		this.printSupportTypeOverride = printSupportTypeOverride;
	}

	public boolean getPrintRaft() {
		return printRaft;
	}

	public void setPrintRaft(boolean printRaft) {
		this.printRaft = printRaft;
	}

	public boolean getSpiralPrint() {
		return spiralPrint;
	}

	public void setSpiralPrint(boolean spiralPrint) {
		this.spiralPrint = spiralPrint;
	}

	public String getExtruder0FilamentID() {
		return extruder0FilamentID;
	}

	public void setExtruder0FilamentID(String extruder0FilamentID) {
		this.extruder0FilamentID = extruder0FilamentID;
	}

	public String getExtruder1FilamentID() {
		return extruder1FilamentID;
	}

	public void setExtruder1FilamentID(String extruder1FilamentID) {
		this.extruder1FilamentID = extruder1FilamentID;
	}

	public String getSettingsName() {
		return settingsName;
	}

	public void setSettingsName(String settingsName) {
		this.settingsName = settingsName;
	}

	public PrintQualityEnumeration getPrintQuality() {
		return printQuality;
	}

	public void setPrintQuality(PrintQualityEnumeration printQuality) {
		this.printQuality = printQuality;
	}

	public Map<Integer, Set<Integer>> getGroupStructure() {
		return groupStructure;
	}

	public void setGroupStructure(Map<Integer, Set<Integer>> groupStructure) {
		this.groupStructure = groupStructure;
	}

	public Map<Integer, ItemState> getGroupState() {
		return groupState;
	}

	public void setGroupState(Map<Integer, ItemState> groupState) {
		this.groupState = groupState;
	}

	public void populateFromProject(Project project) {
		projectName = project.getProjectName();
		lastModifiedDate = project.getLastModifiedDate().get();
		lastPrintJobID = project.getLastPrintJobID();
		projectNameModified = project.isProjectNameModified();
		timelapseTriggerEnabled = project.getTimelapseSettings().getTimelapseTriggerEnabled();
		timelapseProfileName = project.getTimelapseSettings().getTimelapseProfile().map(cp -> cp.profileName).orElse("");
		timelapseCameraID = project.getTimelapseSettings().getTimelapseCamera()
				.map(c -> String.format("%s:%02d", c.getCameraName(), c.getCameraNumber())).orElse("");

		extruder0FilamentID = project.getExtruder0FilamentProperty().get().getFilamentID();
		extruder1FilamentID = project.getExtruder1FilamentProperty().get().getFilamentID();
		settingsName = project.getPrinterSettings().getSettingsName();
		printQuality = project.getPrinterSettings().getPrintQuality();
		brimOverride = project.getPrinterSettings().getBrimOverride();
		fillDensityOverride = project.getPrinterSettings().getFillDensityOverride();
		fillDensityOverridenByUser = project.getPrinterSettings().isFillDensityChangedByUser();
		printSupportOverride = project.getPrinterSettings().getPrintSupportOverride();
		printSupportTypeOverride = project.getPrinterSettings().getPrintSupportTypeOverride();
		printRaft = project.getPrinterSettings().getRaftOverride();
		spiralPrint = project.getPrinterSettings().getSpiralPrintOverride();
		groupStructure = project.getGroupStructure();
		groupState = project.getGroupState();
	}
}
