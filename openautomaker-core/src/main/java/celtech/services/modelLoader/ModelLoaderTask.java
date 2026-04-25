package celtech.services.modelLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.utils.FileUtilities;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.root.TempPathPreference;
import org.openautomaker.project.importer.ObjImporter;
import org.openautomaker.project.importer.RawMeshData;
import org.openautomaker.project.importer.StlImporter;

import com.google.inject.assistedinject.Assisted;

import celtech.coreUI.visualisation.ApplicationMaterials;
import celtech.coreUI.visualisation.metaparts.ModelLoadResult;
import celtech.coreUI.visualisation.metaparts.ModelLoadResultType;
import celtech.modelcontrol.ModelContainer;
import celtech.modelcontrol.ProjectifiableThing;
import jakarta.inject.Inject;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;

import org.openautomaker.ui.inject.model.ModelContainerFactory;
import org.openautomaker.ui.project.loader.StlModelLoader;
import org.openautomaker.ui.project.loader.SvgModelLoader;

//TODO: Revisit all services
public class ModelLoaderTask extends Task<ModelLoadResults> {

	private static final Logger LOGGER = LogManager.getLogger();

	private final List<File> modelFilesToLoad;
	private final DoubleProperty percentProgress = new SimpleDoubleProperty();

	private final ModelContainerFactory modelContainerFactory;
	private final StlImporter stlImporter;
	private final ObjImporter objImporter;
	private final SvgModelLoader svgModelLoader;
	private final TempPathPreference tempPathPreference;
	private final I18N i18n;

	@Inject
	protected ModelLoaderTask(
			ModelContainerFactory modelContainerFactory,
			StlImporter stlImporter,
			ObjImporter objImporter,
			SvgModelLoader svgModelLoader,
			TempPathPreference tempPathPreference,
			I18N i18n,
			@Assisted List<File> modelFilesToLoad) {

		this.modelContainerFactory = modelContainerFactory;
		this.stlImporter = stlImporter;
		this.objImporter = objImporter;
		this.svgModelLoader = svgModelLoader;
		this.tempPathPreference = tempPathPreference;
		this.i18n = i18n;
		this.modelFilesToLoad = modelFilesToLoad;

		percentProgress.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
				updateProgress(t1.doubleValue(), 100.0);
			}
		});
	}

	@Override
	protected ModelLoadResults call() throws Exception {
		List<ModelLoadResult> modelLoadResultList = new ArrayList<>();

		updateTitle(i18n.t("dialogs.loadModelTitle"));

		for (File modelFileToLoad : modelFilesToLoad) {
			LOGGER.info("Model file load started:" + modelFileToLoad.getName());

			Path modelPath = modelFileToLoad.toPath();
			updateMessage(i18n.t("dialogs.gcodeLoadMessagePrefix") + modelFileToLoad.getName());
			updateProgress(0, 100);

			final List<Path> pathsToLoad = new ArrayList<>();

			String upperName = modelFileToLoad.getName().toUpperCase();
			if (upperName.endsWith("ZIP")) {
				ZipFile zipFile = new ZipFile(modelFileToLoad);
				try {
					final Enumeration<? extends ZipEntry> entries = zipFile.entries();
					while (entries.hasMoreElements()) {
						final ZipEntry entry = entries.nextElement();
						Path tempTarget = tempPathPreference.getValue().resolve(entry.getName());
						FileUtilities.writeStreamToFile(zipFile.getInputStream(entry), tempTarget.toString());
						pathsToLoad.add(tempTarget);
					}
				}
				catch (IOException ex) {
					LOGGER.error("Error unwrapping zip - " + ex.getMessage());
				}
				finally {
					zipFile.close();
				}
			}
			else {
				pathsToLoad.add(modelPath);
			}

			for (Path pathToLoad : pathsToLoad) {
				ModelLoadResult loadResult = loadTheFile(pathToLoad);
				if (loadResult != null) {
					modelLoadResultList.add(loadResult);
				}
				else {
					LOGGER.warn("Failed to load model: " + pathToLoad);
				}
			}
		}

		ModelLoadResultType type = null;
		if (!modelLoadResultList.isEmpty()) {
			type = modelLoadResultList.get(0).getType();
		}
		return new ModelLoadResults(type, modelLoadResultList);
	}

	private ModelLoadResult loadTheFile(Path path) {
		String upper = path.getFileName().toString().toUpperCase();
		String name = path.getFileName().toString();

		try {
			if (upper.endsWith("OBJ")) {
				List<RawMeshData> meshes = objImporter.load(path);
				return buildMultiMeshResult(path, meshes);
			}
			else if (upper.endsWith("STL")) {
				RawMeshData raw = stlImporter.load(path).get(0);
				MeshView view = StlModelLoader.buildMeshView(raw, name);
				applyDefaults(view, name);
				ModelContainer mc = modelContainerFactory.create(path, view);
				Set<ProjectifiableThing> things = new HashSet<>();
				things.add(mc);
				return new ModelLoadResult(ModelLoadResultType.Mesh, path.toString(), name, things);
			}
			else if (upper.endsWith("SVG")) {
				return svgModelLoader.loadAsResult(path);
			}
		}
		catch (Exception ex) {
			LOGGER.error("Failed to load {}: {}", path, ex.getMessage(), ex);
		}
		return null;
	}

	private ModelLoadResult buildMultiMeshResult(Path path, List<RawMeshData> meshes) {
		Set<ProjectifiableThing> things = new HashSet<>();
		for (RawMeshData raw : meshes) {
			MeshView view = StlModelLoader.buildMeshView(raw, raw.name());
			applyDefaults(view, raw.name());
			ModelContainer mc = modelContainerFactory.create(path, view, raw.extruder());
			things.add(mc);
		}
		return things.isEmpty() ? null
				: new ModelLoadResult(ModelLoadResultType.Mesh, path.toString(), path.getFileName().toString(), things);
	}

	private void applyDefaults(MeshView view, String id) {
		view.setMaterial(ApplicationMaterials.getDefaultModelMaterial());
		view.setCullFace(CullFace.BACK);
		view.setId(id + "_mesh");
	}

	public void updateMessageText(String message) {
		updateMessage(message);
	}
}
