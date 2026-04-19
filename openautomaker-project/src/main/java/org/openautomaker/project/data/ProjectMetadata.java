package org.openautomaker.project.data;

public class ProjectMetadata {

	private int version;
	private String projectName;
	private String lastModified;
	private PrintSettingsData printSettings;

	public ProjectMetadata() {
	}

	public int getVersion() { return version; }
	public void setVersion(int v) { this.version = v; }

	public String getProjectName() { return projectName; }
	public void setProjectName(String v) { this.projectName = v; }

	public String getLastModified() { return lastModified; }
	public void setLastModified(String v) { this.lastModified = v; }

	public PrintSettingsData getPrintSettings() { return printSettings; }
	public void setPrintSettings(PrintSettingsData v) { this.printSettings = v; }
}
