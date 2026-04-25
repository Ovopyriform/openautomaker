package org.openautomaker.ui.project.robox;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.ui.project.robox.data.ProjectFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import celtech.appManager.Project;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RoboxWriter {

	private static final Logger LOGGER = LogManager.getLogger();

	@Inject
	public RoboxWriter() {
	}

	/**
	 * Write a project as a legacy .robox + .models file pair.
	 */
	public void write(Project project, Path filePath) throws IOException{
		if (project.getTopLevelThings().isEmpty()) {
			LOGGER.debug("No top level things. Returning");
			return;
		}

		if (!filePath.toString().endsWith(RoboxFile.EXTENSION))
			filePath = filePath.resolve(filePath.getFileName().toString() + RoboxFile.EXTENSION);

		Path modelsPath = filePath.resolveSibling(
				filePath.getFileName().toString().replace(RoboxFile.EXTENSION, RoboxFile.MODELS_EXTENSION));

		try {
			ProjectFile projectFile = new ProjectFile();
			projectFile.populateFromProject(project);

			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			mapper.writeValue(filePath.toFile(), projectFile);
			writeModels(project, modelsPath);
		}
		catch (FileNotFoundException ex) {
			LOGGER.error("Failed to save project state", ex);
		}
	}

	private void writeModels(Project project, Path path) throws IOException {
		Set<ModelContainer> modelsHoldingMeshViews = getModelsHoldingMeshViews(project);
		try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
			out.writeInt(modelsHoldingMeshViews.size());
			for (ModelContainer mc : modelsHoldingMeshViews) {
				out.writeObject(mc);
			}
		}
		catch (FileNotFoundException e) {
			LOGGER.warn("Cannot create {}", path);
		}
	}

	private Set<ModelContainer> getModelsHoldingMeshViews(Project project) {
		Set<ModelContainer> result = new HashSet<>();
		for (ProjectifiableThing t : project.getTopLevelThings()) {
			result.addAll(((ModelContainer) t).getModelsHoldingMeshViews());
		}
		return result;
	}
}
