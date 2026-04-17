package org.openautomaker.ui.component.root_connection_button_table_cell;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.comms.print_server.PrintServerConnection.ServerStatus;
import org.openautomaker.base.comms.print_server.PrintServerConnectionManager;
import org.openautomaker.base.notification_manager.NotificationType;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.base.utils.SystemUtils;
import org.openautomaker.environment.I18N;
import org.openautomaker.environment.preference.application.VersionPreference;
import org.openautomaker.environment.preference.root.TempPathPreference;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.GuiceContext;
import com.vdurmont.semver4j.Semver;

import org.openautomaker.ui.component.notification.GenericProgressBar;
import org.openautomaker.ui.component.notification.ProgressDisplay;
import celtech.roboxbase.comms.remote.Configuration;
import celtech.utils.TaskWithProgessCallback;
import celtech.utils.WebUtil;
import jakarta.inject.Inject;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public class RootConnectionButtonTableCell extends TableCell<PrintServerConnection, PrintServerConnection> {

	private static final String ROOT_UPGRADE_FILE_PREFIX = "RootARM-32bit-";

	private static FutureTask<Optional<File>> rootDownloadFuture = null;
	private static ExecutorService rootDownloadExecutor = Executors.newFixedThreadPool(1);
	private static GenericProgressBar rootSoftwareDownloadProgress;

	private GenericProgressBar rootSoftwareUploadProgress;

	private BooleanProperty inhibitUpdate = new SimpleBooleanProperty(false);

	private boolean userEnteredPin = false;

	@FXML
	private HBox connectedBox;

	@FXML
	private HBox disconnectedBox;

	@FXML
	private TextField pinEntryField;

	@FXML
	private Button updateButton;

	@FXML
	private Button downgradeButton;

	@FXML
	void connectToServer(ActionEvent event) {
		if (associatedServer != null) {
			userEnteredPin = true;
			associatedServer.setPin(pinEntryField.getText());
			printServerConnectionManager.connect(associatedServer);
		}
	}

	@FXML
	void disconnectFromServer(ActionEvent event) {
		if (associatedServer != null) {
			associatedServer.disconnect();
		}
	}

	@FXML
	void deleteServer(ActionEvent event) {
		if (associatedServer != null) {
			associatedServer.disconnect();
		}
	}

	@FXML
	void onPinKeyPressed(KeyEvent event) {
		if (event.getCode().equals(KeyCode.ENTER)) {
			connectToServer(null);
		}
	}

	@Inject
	private VersionPreference versionPreference;

	@Inject
	private SystemNotificationManager systemNotificationManager;

	@Inject
	private I18N i18n;

	@Inject
	private ProgressDisplay progressDisplay;

	@Inject
	private TaskExecutor taskExecutor;

	@Inject
	private TempPathPreference tempPathPreference;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	@Inject
	private PrintServerConnectionManager printServerConnectionManager;

	@Inject
	private WebUtil webUtil;

	public RootConnectionButtonTableCell() {
		super();
		GuiceContext.get().injectMembers(this);

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("RootConnectionButtonTableCell.fxml"));
		fxmlLoader.setController(this);
		fxmlLoader.setClassLoader(getClass().getClassLoader());

		try {
			buttonHolder = fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	public void initialize() {
		updateButton.disableProperty().bind(inhibitUpdate);
		downgradeButton.disableProperty().bind(inhibitUpdate);
	}

	private static void tidyRootFiles(Path path, Path filename) {
		try {
			Files.newDirectoryStream(path, (p) -> {
				String f = p.getFileName().toString();
				return Files.isRegularFile(p) && !f.equals(filename.getFileName().toString()) && f.startsWith(ROOT_UPGRADE_FILE_PREFIX);
			})
					.forEach(rootARMPath -> {
						try {
							Files.deleteIfExists(rootARMPath);
						}
						catch (IOException ex) {
						}
					});
		}
		catch (IOException ex) {
		}
	}

	private void upgradeRootWithFile(Path path, Path filename) {
		if (associatedServer != null) {
			TaskWithProgessCallback<Boolean> rootUploader = new TaskWithProgessCallback<>() {
				@Override
				protected Boolean call() throws Exception {
					inhibitUpdate.set(true);
					Optional<File> rootFileOptional = getRootDownloadFuture(path, filename).get();
					if (rootFileOptional.isPresent()) {
						taskExecutor.runOnGUIThread(() -> {
							rootSoftwareUploadProgress = progressDisplay.addGenericProgressBarToDisplay(i18n.t("rootScanner.rootUploadTitle"),
									runningProperty(),
									progressProperty());
						});

						return associatedServer.upgradeRootSoftware(path, filename, this);
					}
					else {
						inhibitUpdate.set(false);
						return false;
					}
				}

				@Override
				public void updateProgressPercent(double percentProgress) {
					updateProgress(percentProgress, 100.0);
				}
			};

			rootUploader.setOnScheduled((event) -> {
				inhibitUpdate.set(true);
			});

			rootUploader.setOnFailed((event) -> {
				systemNotificationManager.showErrorNotification(i18n.t("rootScanner.rootUploadTitle"), i18n.t("rootScanner.failedUploadMessage"));
				progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareUploadProgress);
				rootSoftwareUploadProgress = null;
				inhibitUpdate.set(false);
			});

			rootUploader.setOnSucceeded((event) -> {
				if ((boolean) event.getSource().getValue()) {
					systemNotificationManager.showDismissableNotification(i18n.t("rootScanner.successfulUploadMessage"), i18n.t("dialogs.OK"), NotificationType.NOTE);
				}
				else {
					systemNotificationManager.showErrorNotification(i18n.t("rootScanner.rootUploadTitle"), i18n.t("rootScanner.failedUploadMessage"));
				}
				progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareUploadProgress);
				rootSoftwareUploadProgress = null;
				inhibitUpdate.set(false);
			});

			if (rootSoftwareUploadProgress != null) {
				progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareUploadProgress);
				rootSoftwareUploadProgress = null;
			}

			Thread rootUploaderThread = new Thread(rootUploader);
			rootUploaderThread.setName("Root uploader");
			rootUploaderThread.setDaemon(true);
			rootUploaderThread.start();
		}
	}

	private synchronized Future<Optional<File>> getRootDownloadFuture(Path rootFileDirectory,
			Path rootFileName) {
		Path rootFilePath = rootFileDirectory.resolve(rootFileName);
		try {
			if (!Files.exists(rootFilePath, LinkOption.NOFOLLOW_LINKS)) {
				rootDownloadFuture = null;
			}
			else if (Files.size(rootFilePath) < 10000000) {
				// Sanity check - less than 10 Mb seems to be too small for a Root install file.
				Files.delete(rootFilePath);
				rootDownloadFuture = null;
			}
		}
		catch (IOException ex) {
			rootDownloadFuture = null;
		}

		// It is static synchronized, so all other instances are blocked.
		if (rootDownloadFuture == null) {
			TaskWithProgessCallback<Optional<File>> rootDownloader = new TaskWithProgessCallback<>() {
				@Override
				protected Optional<File> call() throws Exception {
					taskExecutor.runOnGUIThread(() -> {
						if (rootSoftwareDownloadProgress != null) {
							progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareDownloadProgress);
							rootSoftwareDownloadProgress = null;
						}
					});

					if (Files.exists(rootFilePath, LinkOption.NOFOLLOW_LINKS)) {
						return Optional.of(rootFilePath.toFile());
					}
					else {
						taskExecutor.runOnGUIThread(() -> {
							rootSoftwareDownloadProgress = progressDisplay.addGenericProgressBarToDisplay(i18n.t("rootScanner.rootDownloadTitle"),
									runningProperty(),
									progressProperty());

						});

						// Download the file from the web server.
						URL rootDownloadURL = new URL("https://downloads.cel-uk.com/software/root/" + rootFileName);
						if (SystemUtils.downloadFromUrl(rootDownloadURL, rootFilePath.toString(), this))
							return Optional.of(rootFilePath.toFile());
					}
					return Optional.empty();
				}

				@Override
				public void updateProgressPercent(double percentProgress) {
					updateProgress(percentProgress, 100.0);
				}
			};

			rootDownloader.setOnScheduled((result) -> {
			});

			rootDownloader.setOnSucceeded((result) -> {
				tidyRootFiles(rootFileDirectory, rootFileName);
				if (rootSoftwareDownloadProgress != null) {
					systemNotificationManager.showInformationNotification(i18n.t("rootScanner.rootDownloadTitle"), i18n.t("rootScanner.successfulDownloadMessage"));
					progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareDownloadProgress);
					rootSoftwareDownloadProgress = null;
				}
			});

			rootDownloader.setOnFailed((result) -> {
				systemNotificationManager.showErrorNotification(i18n.t("rootScanner.rootDownloadTitle"), i18n.t("rootScanner.failedDownloadMessage"));
				progressDisplay.removeGenericProgressBarFromDisplay(rootSoftwareDownloadProgress);
				rootSoftwareDownloadProgress = null;
			});

			rootDownloadFuture = rootDownloader;
			rootDownloadExecutor.execute(rootDownloader);
		}
		return rootDownloadFuture;
	}

	@FXML
	void updateRoot(ActionEvent event) {
		Path pathToRootFile = tempPathPreference.getValue();
		Path rootFile = Paths.get(ROOT_UPGRADE_FILE_PREFIX + versionPreference.getValue().getValue() + ".zip");
		upgradeRootWithFile(pathToRootFile, rootFile);
	}

	@FXML
	void downgradeRoot(ActionEvent event) {
		boolean downgradeConfirmed = systemNotificationManager.showAreYouSureYouWantToDowngradeDialog();
		if (downgradeConfirmed) {
			updateRoot(event);
		}
	}

	@FXML
	private void launchRootManager(ActionEvent event) {
		String url = "http://" + associatedServer.getServerIP() + ":" + Configuration.remotePort + "/index.html";
		webUtil.launchURL(url);
	}

	private StackPane buttonHolder;
	private PrintServerConnection associatedServer = null;
	private ChangeListener<ServerStatus> serverStatusListener = new ChangeListener<>() {
		@Override
		public void changed(ObservableValue<? extends ServerStatus> observable, ServerStatus oldValue, ServerStatus newValue) {
			processServerStatus(newValue);
		}
	};


	@Override
	protected void updateItem(PrintServerConnection item, boolean empty) {
		super.updateItem(item, empty);

		if (item != associatedServer) {
			if (associatedServer != null) {
				associatedServer.serverStatusProperty().removeListener(serverStatusListener);
			}
			if (item != null) {
				item.serverStatusProperty().addListener(serverStatusListener);
			}
			associatedServer = item;
		}

		if (item != null && !empty) {
			setGraphic(buttonHolder);
			processServerStatus(item.getServerStatus());
		}
		else {
			setGraphic(null);
		}
	}

	private void processServerStatus(ServerStatus status) {
		switch (status) {
			case CONNECTED:
				connectedBox.setVisible(true);
				disconnectedBox.setVisible(false);
				updateButton.setVisible(false);
				downgradeButton.setVisible(false);
				userEnteredPin = false;
				break;
			case NOT_CONNECTED:
				disconnectedBox.setVisible(true);
				pinEntryField.clear();
				connectedBox.setVisible(false);
				updateButton.setVisible(false);
				downgradeButton.setVisible(false);
				userEnteredPin = false;
				break;
			case WRONG_VERSION:
				handleWrongVersionCase();
				break;
			case WRONG_PIN:
				if (userEnteredPin) {
					systemNotificationManager.showErrorNotification(i18n.t("rootScanner.PIN"), i18n.t("rootScanner.incorrectPIN"));
					userEnteredPin = false;
				}
				break;
			case UPGRADING:
			default:
				disconnectedBox.setVisible(false);
				connectedBox.setVisible(false);
				updateButton.setVisible(false);
				downgradeButton.setVisible(false);
				userEnteredPin = false;
				break;
		}
	}

	private void handleWrongVersionCase() {
		Semver localVersion = versionPreference.getValue();
		Semver serverVersion = associatedServer.getVersion();

		int comparison = localVersion.compareTo(serverVersion);

		if (comparison < 0) {
			// Local version is lower than server
			downgradeButton.setVisible(true);
			updateButton.setVisible(false);
		}
		else if (comparison > 0) {
			// Local version is higher than server
			updateButton.setVisible(true);
			downgradeButton.setVisible(false);
		}

		disconnectedBox.setVisible(false);
		connectedBox.setVisible(false);
		userEnteredPin = false;
	}
}
