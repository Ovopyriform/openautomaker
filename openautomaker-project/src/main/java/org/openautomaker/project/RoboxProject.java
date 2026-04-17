package org.openautomaker.project;

import java.nio.file.Path;

public class RoboxProject {
	public static final String fileExtension = "roboxproj";

	private Path projectPath;

	public RoboxProject(Path projectPath) {
		this.projectPath = projectPath;
	}

	public Path getProjectPath() {
		return projectPath;
	}
}
