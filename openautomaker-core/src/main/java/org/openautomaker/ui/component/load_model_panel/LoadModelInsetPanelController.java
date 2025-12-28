package org.openautomaker.ui.component.load_model_panel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.application.HomePathPreference;
import org.openautomaker.environment.preference.modeling.ModelsPathPreference;
import org.openautomaker.ui.StageManager;
import org.openautomaker.ui.state.SelectedProject;

import celtech.appManager.ApplicationMode;
import celtech.appManager.ApplicationStatus;
import celtech.appManager.ProjectMode;
import celtech.configuration.ApplicationConfiguration;
import celtech.coreUI.components.InsetPanelMenu;
import celtech.coreUI.components.InsetPanelMenuItem;
import celtech.coreUI.visualisation.ModelLoader;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;

/**
 *
 * @author Ian
 */
public class LoadModelInsetPanelController {

	private static final Logger LOGGER = LogManager.getLogger();

	private final FileChooser modelFileChooser = new FileChooser();
	//private final DisplayManager displayManager;


	@FXML
	private VBox container;

	@FXML
	private VBox webContentContainer;

	@FXML
	private InsetPanelMenu menu;

	@FXML
	void cancelPressed(ActionEvent event) {
		applicationStatus.returnToLastMode();
	}

	private final I18N i18n;
	private final StageManager stageManager;
	private final ApplicationStatus applicationStatus;
	private final ModelLoader modelLoader;
	private final SelectedProject selectedProject;
	private final HomePathPreference homePathPreference;
	private final ModelsPathPreference modelsPathPreference;

	@Inject
	protected LoadModelInsetPanelController(
			I18N i18n,
			StageManager stageManager,
			ApplicationStatus applicationStatus,
			ModelLoader modelLoader,
			SelectedProject selectedProject,
			HomePathPreference homePathPreference,
			ModelsPathPreference modelsPathPreference) {

		this.i18n = i18n;
		this.stageManager = stageManager;
		this.applicationStatus = applicationStatus;
		this.modelLoader = modelLoader;
		this.selectedProject = selectedProject;
		this.homePathPreference = homePathPreference;
		this.modelsPathPreference = modelsPathPreference;
	}

	@FXML
	void addToProjectPressed(ActionEvent event) {
		Platform.runLater(() -> {
			ListIterator iterator = modelFileChooser.getExtensionFilters().listIterator();

			while (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}

			String descriptionOfFile = i18n.t("dialogs.meshFileChooserDescription");

			modelFileChooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter(descriptionOfFile,
							ApplicationConfiguration.getSupportedFileExtensionWildcards(
									ProjectMode.MESH)));

			modelFileChooser.setInitialDirectory(modelsPathPreference.getValue().toFile());

			List<File> files;

			files = modelFileChooser.showOpenMultipleDialog(stageManager.getMainStage());

			if (files != null && !files.isEmpty()) {
				modelsPathPreference.setValue(files.get(0).getParentFile().toPath());
				modelLoader.loadExternalModels(selectedProject.get(), files,
						true, null, false);
			}
		});
	}

	public void initialize() {

		menu.setTitle(i18n.t("loadModel.menuTitle"));

		InsetPanelMenuItem myComputerItem = new InsetPanelMenuItem();
		myComputerItem.setTitle(i18n.t("loadModel.myComputer"));

		InsetPanelMenuItem myMiniFactoryItem = new InsetPanelMenuItem();
		myMiniFactoryItem.setTitle(i18n.t("loadModel.myMiniFactory"));

		menu.addMenuItem(myComputerItem);
		menu.addMenuItem(myMiniFactoryItem);

		modelFileChooser.setTitle(i18n.t("dialogs.modelFileChooser"));
		modelFileChooser.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter(i18n.t("dialogs.modelFileChooserDescription"),
						ApplicationConfiguration.getSupportedFileExtensionWildcards(
								ProjectMode.NONE)));

		applicationStatus.modeProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != ApplicationMode.ADD_MODEL || oldValue == newValue)
				return;

			webContentContainer.getChildren().clear();

			WebView webView = new WebView();
			VBox.setVgrow(webView, Priority.ALWAYS);

			final WebEngine webEngine = webView.getEngine();

			webEngine.getLoadWorker().stateProperty().addListener(
					new ChangeListener<State>() {
						@Override
						public void changed(ObservableValue<? extends State> ov,
								State oldState, State newState) {
							switch (newState) {
								case RUNNING:
									break;
								case SUCCEEDED:
									JSObject win = (JSObject) webEngine.executeScript("window");
									win.setMember("automaker", new WebCallback());
									break;
							}
						}
					});
			webContentContainer.getChildren().addAll(webView);
			//TODO: Don't go here any more.
			webEngine.load("http://cel-robox.myminifactory.com");
		});
	}

	public class WebCallback {

		public void downloadFile(String fileURL) {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Got download URL of " + fileURL);

			String tempID = SystemUtils.generate16DigitID();
			try {
				URL downloadURL = new URL(fileURL);

				String extension = FilenameUtils.getExtension(fileURL);
				final String tempFilename = homePathPreference.getAppValue().resolve(tempID + "." + extension).toString();

				URLConnection urlConn = downloadURL.openConnection();

				InputStream webInputStream = urlConn.getInputStream();

				if (extension.equalsIgnoreCase("stl")) {
					if (LOGGER.isDebugEnabled())
						LOGGER.debug("Got stl file from My Mini Factory");

					final String targetname = homePathPreference.getUserValue().resolve(FilenameUtils.getBaseName(fileURL)).toString();
					writeStreamToFile(webInputStream, targetname);
				}
				else if (extension.equalsIgnoreCase("zip")) {
					if (LOGGER.isDebugEnabled())
						LOGGER.debug("Got zip file from My Mini Factory");

					writeStreamToFile(webInputStream, tempFilename);
					ZipFile zipFile = new ZipFile(tempFilename);
					try {
						final Enumeration<? extends ZipEntry> entries = zipFile.entries();
						final List<File> filesToLoad = new ArrayList<>();
						while (entries.hasMoreElements()) {
							final ZipEntry entry = entries.nextElement();
							final String tempTargetname = homePathPreference.getUserValue().resolve(entry.getName()).toString();

							writeStreamToFile(zipFile.getInputStream(entry), tempTargetname);
							filesToLoad.add(new File(tempTargetname));
						}
						modelLoader.loadExternalModels(selectedProject.get(),
								filesToLoad, null);
					}
					finally {
						zipFile.close();
					}
				}
				else if (extension.equalsIgnoreCase("rar")) {
					LOGGER.debug("Got rar file from My Mini Factory");
				}

				webInputStream.close();

			}
			catch (IOException ex) {
				LOGGER.error("Failed to download My Mini Factory file :" + fileURL);
			}
		}
	}

	private void writeStreamToFile(InputStream is, String localFilename) throws IOException {
		FileOutputStream fos = null;

		try {
			fos = new FileOutputStream(localFilename); //open outputstream to local file

			byte[] buffer = new byte[4096]; //declare 4KB buffer
			int len;

			//while we have availble data, continue downloading and storing to local file
			while ((len = is.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
		}
		finally {
			try {
				if (is != null) {
					is.close();
				}
			}
			finally {
				if (fos != null) {
					fos.close();
				}
			}
		}
	}

}
