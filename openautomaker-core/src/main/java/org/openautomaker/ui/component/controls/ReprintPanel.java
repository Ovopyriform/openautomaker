package org.openautomaker.ui.component.controls;

import java.util.List;

import org.controlsfx.control.MasterDetailPane;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.components.GuicedVBox;
import org.openautomaker.ui.StageManager;

import celtech.configuration.ApplicationConfiguration;
import celtech.roboxbase.comms.remote.clear.SuitablePrintJob;
import jakarta.inject.Inject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ReprintPanel extends GuicedVBox {

	private Stage dialogStage = new Stage(StageStyle.UNDECORATED);
	private final MasterDetailPane masterDetailsPane = new MasterDetailPane(Side.RIGHT);
	private final TableView<SuitablePrintJob> tableView = new TableView<>();
	private final GridPane detailsPanel = new GridPane();
	private final Text jobDurationLabel = new Text();
	private final Text printProfileLabel = new Text();
	private final Text eLengthMLabel = new Text();
	private final Text dLengthMLabel = new Text();
	private final Text jobDuration = new Text();
	private final Text printProfile = new Text();
	private final Text eLengthM = new Text();
	private final Text dLengthM = new Text();
	private final Text creationDateLabel = new Text();
	private final Text creationDate = new Text();
	private static final double VOLUME_TO_LENGTH_1_75MM = 0.000415751688076788;

	private final Button closeButton = new Button();
	private final Button printButton = new Button();

	private Printer printerToUse = null;

	@Inject
	private StageManager stageManager;

	@Inject
	private I18N i18n;

	public ReprintPanel() {

		masterDetailsPane.setMasterNode(tableView);
		masterDetailsPane.setDetailNode(detailsPanel);
		masterDetailsPane.setDividerPosition(0.4);

		detailsPanel.setPadding(new Insets(10, 10, 10, 10));

		closeButton.setOnAction(event -> {
			close();
		});

		printButton.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());
		printButton.setOnAction(event -> {
			printerToUse.printJob(tableView.getSelectionModel().getSelectedItem().getPrintJobID());
			close();
		});

		dialogStage.initModality(Modality.WINDOW_MODAL);

		setAlignment(Pos.CENTER);
		setPrefWidth(500);
		setPrefHeight(415);

		Text reprintTitle = new Text(i18n.t("reprintPanel.title"));
		reprintTitle.getStyleClass().add("reprint-title");

		Text reprintSubtitle = new Text(i18n.t("reprintPanel.subtitle"));
		reprintSubtitle.getStyleClass().add("reprint-subtitle");

		TableColumn<SuitablePrintJob, String> nameColumn = new TableColumn<>();
		nameColumn.setCellValueFactory(new PropertyValueFactory<SuitablePrintJob, String>("printJobName"));
		nameColumn.setText(i18n.t("rootScanner.name"));
		nameColumn.setPrefWidth(200);
		nameColumn.setResizable(false);
		nameColumn.setStyle("-fx-alignment: CENTER_LEFT;");

		tableView.getColumns().add(nameColumn);
		tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

		tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			displayDetails(newValue);
		});

		tableView.setPlaceholder(new Text(i18n.t("reprintPanel.noFilesToReprint")));

		detailsPanel.setHgap(10);

		jobDurationLabel.setText(i18n.t("reprintPanel.duration"));
		printProfileLabel.setText(i18n.t("reprintPanel.profile"));
		eLengthMLabel.setText(i18n.t("reprintPanel.requiredFilament1"));
		dLengthMLabel.setText(i18n.t("reprintPanel.requiredFilament2"));
		creationDateLabel.setText(i18n.t("reprintPanel.creationDate"));

		detailsPanel.add(creationDateLabel, 0, 0);
		detailsPanel.add(creationDate, 1, 0);
		detailsPanel.add(jobDurationLabel, 0, 1);
		detailsPanel.add(jobDuration, 1, 1);
		detailsPanel.add(printProfileLabel, 0, 2);
		detailsPanel.add(printProfile, 1, 2);
		detailsPanel.add(eLengthMLabel, 0, 3);
		detailsPanel.add(eLengthM, 1, 3);
		detailsPanel.add(dLengthMLabel, 0, 4);
		detailsPanel.add(dLengthM, 1, 4);

		closeButton.setText(i18n.t("buttonText.close"));

		printButton.setText(i18n.t("buttonText.make"));

		HBox buttonContainer = new HBox(10);
		buttonContainer.setAlignment(Pos.CENTER);
		buttonContainer.getChildren().addAll(closeButton, printButton);

		this.getChildren().addAll(reprintTitle, reprintSubtitle, masterDetailsPane, buttonContainer);

		Scene dialogScene = new Scene(this, Color.TRANSPARENT);
		dialogScene.getStylesheets().add(ApplicationConfiguration.getMainCSSFile());
		dialogStage.setScene(dialogScene);
		dialogStage.initOwner(stageManager.getMainStage());
	}

	public void show(Printer printer) {
		printerToUse = printer;
		List<SuitablePrintJob> suitablePrintJobs = printer.listJobsReprintableByMe();
		ObservableList<SuitablePrintJob> observablePrintJobs = FXCollections.observableArrayList(suitablePrintJobs);
		tableView.setItems(observablePrintJobs);

		if (observablePrintJobs.isEmpty()) {
			displayDetails(null);
		}
		else {
			tableView.getSelectionModel().selectFirst();
		}

		dialogStage.showAndWait();
	}

	public void close() {
		dialogStage.close();
		printerToUse = null;
	}

	private String convertToHoursMinutes(int seconds) {
		int minutes = seconds / 60;
		int hours = minutes / 60;
		minutes = minutes - (60 * hours);
		return String.format("%02d:%02d", hours, minutes);
	}

	private void displayDetails(SuitablePrintJob printJob) {
		if (printJob == null) {
			detailsPanel.setVisible(false);
			tableView.getSelectionModel().clearSelection();
			return;
		}

		creationDate.setText(printJob.getCreationDate());
		printProfile.setText(printJob.getPrintProfileName());
		double eLength = printJob.geteVolume() * VOLUME_TO_LENGTH_1_75MM;
		eLengthM.setText(String.format("%.2fm", eLength));
		double dLength = printJob.getdVolume() * VOLUME_TO_LENGTH_1_75MM;
		dLengthM.setText(String.format("%.2fm", dLength));
		jobDuration.setText(convertToHoursMinutes((int) printJob.getDurationInSeconds()));

		detailsPanel.setVisible(true);
	}
}
