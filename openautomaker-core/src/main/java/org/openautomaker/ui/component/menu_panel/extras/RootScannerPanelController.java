package org.openautomaker.ui.component.menu_panel.extras;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.comms.print_server.PrintServerConnection.ServerStatus;
import org.openautomaker.base.comms.print_server.PrintServerConnectionManager;
import org.openautomaker.environment.I18N;
import org.openautomaker.ui.component.menu_panel.MenuInnerPanel;

import celtech.WebEngineFix.AMURLStreamHandlerFactory;
import celtech.coreUI.components.RootCameraTableCell;
import celtech.coreUI.components.RootConnectionButtonTableCell;
import celtech.coreUI.components.RootTableCell;
import jakarta.inject.Inject;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

/**
 * FXML Controller class
 *
 * @author Ian
 */
//TODO: refactor to get rid of unsupported URL stuff.
public class RootScannerPanelController implements MenuInnerPanel {

	private static final Logger LOGGER = LogManager.getLogger();

	public static String pinForCurrentServer = "";

	@FXML
	private TableView<PrintServerConnection> scannedRoots;

	private TableColumn colourColumn;
	private TableColumn nameColumn;
	private TableColumn ipAddressColumn;
	private TableColumn versionColumn;
	private TableColumn<PrintServerConnection, ServerStatus> statusColumn;
	private TableColumn<PrintServerConnection, PrintServerConnection> scannedRootButtonsColumn;
	private TableColumn cameraColumn;

	@FXML
	private TextField ipTextField;

	@FXML
	private Button addRootButton;

	@FXML
	private Button deleteRootButton;

	private static final String IPADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
			+ "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

	private final I18N i18n;
	private final RootConnectionButtonTableCell fRootConnectionButtonTableCell;
	private final AMURLStreamHandlerFactory amURLStreamHandlerFactory;
	private final PrintServerConnectionManager printServerConnectionManager;

	@Inject
	protected RootScannerPanelController(
			I18N i18n,
			RootConnectionButtonTableCell rootConnectionButtonTableCell,
			AMURLStreamHandlerFactory amURLStreamHandlerFactory,
			PrintServerConnectionManager printServerConnectionManager) {

		this.i18n = i18n;
		this.fRootConnectionButtonTableCell = rootConnectionButtonTableCell;
		this.amURLStreamHandlerFactory = amURLStreamHandlerFactory;
		this.printServerConnectionManager = printServerConnectionManager;
	}

	@FXML
	private void manuallyAddRoot(ActionEvent event) {
		String enteredIP = ipTextField.getText();
		try {
			InetAddress address = InetAddress.getByName(enteredIP);
			printServerConnectionManager.createManualConnection(address);
			ipTextField.setText("");
		}
		catch (UnknownHostException ex) {
			LOGGER.error("Bad IP address for manually added Root: " + enteredIP);
		}
	}

	@FXML
	private void manuallyDeleteRoot(ActionEvent event) {
		String enteredIP = ipTextField.getText();

		try {
			InetAddress address = InetAddress.getByName(enteredIP);
			printServerConnectionManager.removeManualConnection(address);
			ipTextField.setText("");
		}
		catch (UnknownHostException ex) {
			LOGGER.error("Bad IP address for manually removed Root: " + enteredIP);
		}
	}

	/**
	 * Initialises the controller class.
	 *
	 * @param url
	 * @param rb
	 */
	public void initialize() {
		URL.setURLStreamHandlerFactory(amURLStreamHandlerFactory);

		colourColumn = new TableColumn<>();
		colourColumn.setPrefWidth(20);
		colourColumn.setResizable(false);
		colourColumn.setCellValueFactory(new PropertyValueFactory<>("colours"));
		colourColumn.setCellFactory(column -> {
			return new TableCell<PrintServerConnection, List<String>>() {

				@Override
				protected void updateItem(List<String> colours, boolean empty) {
					super.updateItem(colours, empty); //This is mandatory

					setText(null);

					if (colours == null || empty || colours.isEmpty()) { //If the cell is empty
						setStyle("");
					}
					else { //If the cell is not empty

						// For now let's get the first colour
						Color printerColour = Color.valueOf(colours.get(0));
						String printerColourFormatted = formatColor(printerColour);
						setStyle("-fx-background-color: " + printerColourFormatted);
					}
				}
			};
		});

		nameColumn = new TableColumn<>();
		nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		nameColumn.setText(i18n.t("rootScanner.name"));
		nameColumn.setPrefWidth(160);
		nameColumn.setResizable(false);
		nameColumn.setStyle("-fx-alignment: CENTER_LEFT;");

		ipAddressColumn = new TableColumn<>();
		ipAddressColumn.setCellValueFactory(new PropertyValueFactory<>("serverIP"));
		ipAddressColumn.setText(i18n.t("rootScanner.ipAddress"));
		ipAddressColumn.setPrefWidth(100);
		ipAddressColumn.setResizable(false);
		ipAddressColumn.setStyle("-fx-alignment: CENTER;");

		versionColumn = new TableColumn<>();
		versionColumn.setCellValueFactory(new PropertyValueFactory<>("version"));
		versionColumn.setText(i18n.t("rootScanner.version"));
		versionColumn.setPrefWidth(100);
		versionColumn.setResizable(false);
		versionColumn.setStyle("-fx-alignment: CENTER;");

		statusColumn = new TableColumn<>();
		statusColumn.setCellFactory(statusCell -> new RootTableCell());
		statusColumn.setCellValueFactory(new PropertyValueFactory<>("serverStatus"));
		statusColumn.setPrefWidth(40);
		statusColumn.setResizable(false);

		scannedRootButtonsColumn = new TableColumn<>();
		scannedRootButtonsColumn.setCellFactory(buttonCell -> fRootConnectionButtonTableCell);
		scannedRootButtonsColumn.setCellValueFactory((CellDataFeatures<PrintServerConnection, PrintServerConnection> p) -> new SimpleObjectProperty<>(p.getValue()));
		scannedRootButtonsColumn.setMinWidth(350);
		scannedRootButtonsColumn.setMaxWidth(Integer.MAX_VALUE);
		scannedRootButtonsColumn.setResizable(false);

		cameraColumn = new TableColumn<>();
		cameraColumn.setCellFactory(cameraCell -> new RootCameraTableCell());
		cameraColumn.setCellValueFactory(new PropertyValueFactory<>("cameraDetected"));
		cameraColumn.setText(i18n.t("rootScanner.camera"));
		cameraColumn.setPrefWidth(60);
		cameraColumn.setResizable(false);

		scannedRoots.getColumns().add(colourColumn);
		scannedRoots.getColumns().add(nameColumn);
		scannedRoots.getColumns().add(ipAddressColumn);
		scannedRoots.getColumns().add(versionColumn);
		scannedRoots.getColumns().add(statusColumn);
		scannedRoots.getColumns().add(scannedRootButtonsColumn);
		scannedRoots.getColumns().add(cameraColumn);
		scannedRoots.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		HBox.setHgrow(scannedRoots, Priority.ALWAYS);

		scannedRoots.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		scannedRoots.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<PrintServerConnection>() {
			@Override
			public void changed(ObservableValue<? extends PrintServerConnection> observable, PrintServerConnection oldValue, PrintServerConnection newValue) {
				if (newValue == null
						|| newValue.isDiscoveredConnection()) {
					ipTextField.setText("");
				}
				else {
					ipTextField.setText(newValue.getServerIP());
				}
			}
		});

		scannedRoots.setPlaceholder(new Text(i18n.t("rootScanner.noRemoteServersFound")));

		// Add list items
		ObservableList<PrintServerConnection> knownServers = printServerConnectionManager.getKnownServers();
		scannedRoots.setItems(knownServers);

		knownServers.addListener(new ListChangeListener<PrintServerConnection>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends PrintServerConnection> change) {
				if (knownServers.size() > 0) {
					scannedRoots.getSelectionModel().selectFirst();
					return;
				}

				scannedRoots.getSelectionModel().clearSelection();
			}
		});

		ipTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			String enteredIP = ipTextField.getText();

			try {
				PrintServerConnection matchingServer = printServerConnectionManager.findKnownServerConnection(InetAddress.getByName(enteredIP));
				addRootButton.setDisable(matchingServer != null); // Can't add existing server.
				deleteRootButton.setDisable(matchingServer == null || matchingServer.isDiscoveredConnection());
			}
			catch (UnknownHostException e) {
				LOGGER.error("Entered IP Address not valid:" + enteredIP);
				addRootButton.setDisable(true);
				deleteRootButton.setDisable(true);
			}
		});

		addRootButton.setDisable(true);
		deleteRootButton.setDisable(true);
	}

	@Override
	public String getMenuTitle() {
		return "preferences.root";
	}

	@Override
	public List<OperationButton> getOperationButtons() {
		return null;
	}

	// Format color as string for CSS (#rrggbb format, values in hex).
	private String formatColor(Color c) {
		int r = (int) (255 * c.getRed());
		int g = (int) (255 * c.getGreen());
		int b = (int) (255 * c.getBlue());
		return String.format("#%02x%02x%02x", r, g, b);
	}

	@Override
	public void panelSelected() {
	}
}
