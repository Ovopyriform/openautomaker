package org.openautomaker.project.rbxproj.data;

public class PrintSettingsData {

	private String extruder0FilamentID;
	private String extruder1FilamentID;
	private String settingsName;
	private String printQuality;
	private int brimOverride;
	private float fillDensityOverride;
	private boolean fillDensityOverridenByUser;
	private boolean printSupportOverride;
	private String printSupportTypeOverride;
	private boolean printRaft;
	private boolean spiralPrint;

	public PrintSettingsData() {
	}

	public String getExtruder0FilamentID() {
		return extruder0FilamentID;
	}

	public void setExtruder0FilamentID(String v) {
		this.extruder0FilamentID = v;
	}

	public String getExtruder1FilamentID() {
		return extruder1FilamentID;
	}

	public void setExtruder1FilamentID(String v) {
		this.extruder1FilamentID = v;
	}

	public String getSettingsName() {
		return settingsName;
	}

	public void setSettingsName(String v) {
		this.settingsName = v;
	}

	public String getPrintQuality() {
		return printQuality;
	}

	public void setPrintQuality(String v) {
		this.printQuality = v;
	}

	public int getBrimOverride() {
		return brimOverride;
	}

	public void setBrimOverride(int v) {
		this.brimOverride = v;
	}

	public float getFillDensityOverride() {
		return fillDensityOverride;
	}

	public void setFillDensityOverride(float v) {
		this.fillDensityOverride = v;
	}

	public boolean isFillDensityOverridenByUser() {
		return fillDensityOverridenByUser;
	}

	public void setFillDensityOverridenByUser(boolean v) {
		this.fillDensityOverridenByUser = v;
	}

	public boolean isPrintSupportOverride() {
		return printSupportOverride;
	}

	public void setPrintSupportOverride(boolean v) {
		this.printSupportOverride = v;
	}

	public String getPrintSupportTypeOverride() {
		return printSupportTypeOverride;
	}

	public void setPrintSupportTypeOverride(String v) {
		this.printSupportTypeOverride = v;
	}

	public boolean isPrintRaft() {
		return printRaft;
	}

	public void setPrintRaft(boolean v) {
		this.printRaft = v;
	}

	public boolean isSpiralPrint() {
		return spiralPrint;
	}

	public void setSpiralPrint(boolean v) {
		this.spiralPrint = v;
	}
}
