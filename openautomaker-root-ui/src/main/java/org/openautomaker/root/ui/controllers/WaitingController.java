package org.openautomaker.root.ui.controllers;

import org.openautomaker.root.ui.GRootServices;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import org.openautomaker.root.ui.remote.RootPrinter;
import org.openautomaker.root.ui.remote.ServerStatusResponse;

public class WaitingController implements Initializable, Page {

	@FXML
	private StackPane waitingPane;

	@FXML
	private Label statusLabel;

	private final String connectingMessage = GRootServices.getI18N().t("waiting.connecting");
	private final String upgradingMessage = GRootServices.getI18N().t("waiting.upgrading");

	private ChangeListener<ServerStatusResponse> serverStatusListener = (ob, ov, nv) -> {
		//System.out.println("WaitingController::serverStatusListener()");
		if (nv != null &&
				(nv.getUpgradeStatus() == null ||
				!nv.getUpgradeStatus().equalsIgnoreCase("upgrading")))
			acknowledgeConnected();
	};

	private RootStackController rootController = null;

	@Override
	public void setRootStackController(RootStackController rootController) {
		this.rootController = rootController;
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {
		waitingPane.setVisible(false);
		statusLabel.setText(GRootServices.getI18N().t(connectingMessage));
	}

	@Override
	public void startUpdates() {
		//System.out.println("WaitingController::startUpdates()");
		rootController.getRootServer().getCurrentStatusProperty().addListener(serverStatusListener);
	}

	@Override
	public void stopUpdates() {
		//System.out.println("WaitingController::stopUpdates()");
		rootController.getRootServer().getCurrentStatusProperty().removeListener(serverStatusListener);
	}

	@Override
	public void displayPage(RootPrinter printer) {
		//System.out.println("WaitingController::displayPage()");
		if (!waitingPane.isVisible()) {
			startUpdates();
			waitingPane.setVisible(true);
		}
	}

	@Override
	public void hidePage() {
		//System.out.println("WaitingController::hidePage()");
		stopUpdates();
		waitingPane.setVisible(false);
	}

	@Override
	public boolean isVisible() {
		return waitingPane.isVisible();
	}

	public void acknowledgeConnected() {
		rootController.showPrinterSelectPage();
	}

	public void setAsConnecting() {
		statusLabel.setText(connectingMessage);
	}

	public void setAsUpgrading() {
		statusLabel.setText(upgradingMessage);
	}
}
