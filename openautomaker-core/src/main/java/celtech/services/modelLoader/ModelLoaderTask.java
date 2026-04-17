package celtech.services.modelLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.utils.FileUtilities;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.root.TempPathPreference;
import org.openautomaker.ui.inject.importer.OBJImporterFactory;
import org.openautomaker.ui.inject.importer.STLImporterFactory;
import org.openautomaker.ui.inject.importer.SVGImporterFactory;

import com.google.inject.assistedinject.Assisted;

import celtech.coreUI.visualisation.metaparts.ModelLoadResult;
import celtech.coreUI.visualisation.metaparts.ModelLoadResultType;
import celtech.utils.threed.importers.obj.ObjImporter;
import celtech.utils.threed.importers.stl.STLImporter;
import celtech.utils.threed.importers.svg.SVGImporter;
import jakarta.inject.Inject;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;

/**
 *
 * @author ianhudson
 */
//TODO: This should be refactored to be a service that can be called by the task, and then the task just handles the progress and message updates.  This would make it easier to test the loading code without needing to run a task.
public class ModelLoaderTask extends Task<ModelLoadResults> {

	private static final Logger LOGGER = LogManager.getLogger();

	private final List<File> modelFilesToLoad;
	private final DoubleProperty percentProgress = new SimpleDoubleProperty();

	private final OBJImporterFactory objImporterFactory;
	private final STLImporterFactory stlImporterFactory;
	private final SVGImporterFactory svgImporterFactory;
	private final TempPathPreference tempPathPreference;
	private final I18N i18n;

	@Inject
	protected ModelLoaderTask(
			OBJImporterFactory objImporterFactory,
			STLImporterFactory stlImporterFactory,
			SVGImporterFactory svgImporterFactory,
			TempPathPreference tempPathPreference,
			I18N i18n,
			@Assisted List<File> modelFilesToLoad) {

		this.objImporterFactory = objImporterFactory;
		this.stlImporterFactory = stlImporterFactory;
		this.svgImporterFactory = svgImporterFactory;
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

			String modelFilePath = modelFileToLoad.getAbsolutePath();
			updateMessage(i18n.t("dialogs.gcodeLoadMessagePrefix")
					+ modelFileToLoad.getName());
			updateProgress(0, 100);

			final List<String> fileNamesToLoad = new ArrayList<>();

			if (modelFilePath.toUpperCase().endsWith("ZIP")) {
				//                modelLoadResults.setShouldCentre(false);
				ZipFile zipFile = new ZipFile(modelFilePath);

				try {
					final Enumeration<? extends ZipEntry> entries = zipFile.entries();
					while (entries.hasMoreElements()) {
						final ZipEntry entry = entries.nextElement();
						final String tempTargetname = tempPathPreference.getValue() + entry.getName();
						FileUtilities.writeStreamToFile(zipFile.getInputStream(entry), tempTargetname);
						fileNamesToLoad.add(tempTargetname);
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
				fileNamesToLoad.add(modelFilePath);
			}

			for (String filenameToLoad : fileNamesToLoad) {
				ModelLoadResult loadResult = loadTheFile(filenameToLoad);
				if (loadResult != null) {
					modelLoadResultList.add(loadResult);
				}
				else {
					LOGGER.warn("Failed to load model: " + filenameToLoad);
				}
			}
		}

		ModelLoadResultType type = null;
		if (!modelLoadResultList.isEmpty()) {
			type = modelLoadResultList.get(0).getType();
		}
		return new ModelLoadResults(type, modelLoadResultList);
	}

	private ModelLoadResult loadTheFile(String modelFileToLoad) {
		ModelLoadResult modelLoadResult = null;

		if (modelFileToLoad.toUpperCase().endsWith("OBJ")) {
			ObjImporter reader = objImporterFactory.create();
			modelLoadResult = reader.loadFile(this, modelFileToLoad, percentProgress, false);
		}
		else if (modelFileToLoad.toUpperCase().endsWith("STL")) {
			STLImporter reader = stlImporterFactory.create();
			modelLoadResult = reader.loadFile(this, new File(modelFileToLoad),
					percentProgress);
		}
		else if (modelFileToLoad.toUpperCase().endsWith("SVG")) {
			SVGImporter reader = svgImporterFactory.create();
			modelLoadResult = reader.loadFile(this, new File(modelFileToLoad),
					percentProgress);
		}

		return modelLoadResult;
	}

	/**
	 *
	 * @param message
	 */
	public void updateMessageText(String message) {
		updateMessage(message);
	}
}
