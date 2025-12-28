
package org.openautomaker.ui.component.printer_side_panel.printer;

import static celtech.utils.StringMetrics.getWidthOfString;
import static com.sun.javafx.scene.control.skin.Utils.formatHexString;

import java.io.IOException;
import java.net.URL;
import java.util.stream.Collectors;

import org.openautomaker.base.PrinterColourMap;
import org.openautomaker.base.printerControl.PrinterStatus;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.base.task_executor.TaskExecutor;
import org.openautomaker.environment.I18N;
import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.GuiceContext;
import org.openautomaker.ui.component.printer_side_panel.ComponentIsolationInterface;
import org.openautomaker.ui.component.printer_side_panel.printer.svg.PrinterSVG;
import org.openautomaker.ui.component.printer_side_panel.printer.white_progress_bar.WhiteProgressBar;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

import celtech.coreUI.StandardColours;
import celtech.coreUI.components.HideableTooltip;
import celtech.roboxbase.comms.RemoteDetectedPrinter;
import celtech.roboxbase.comms.remote.PauseStatus;
import celtech.roboxbase.comms.remote.RoboxRemoteCommandInterface;
import celtech.roboxbase.comms.rx.FirmwareError;
import jakarta.inject.Inject;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class PrinterComponent extends Pane {

	private boolean selected = false;
	private Size currentSize;
	private boolean inInterruptibleState;
	private boolean showProgress;

	private final HideableTooltip errorTooltip = new HideableTooltip();

	public enum Size {

		SIZE_SMALL(80, 10, 9, 65, 6, 17),
		SIZE_MEDIUM(120, 20, 14, 100, 9, 26),
		SIZE_LARGE(260, 0, 30, 220, 20, 55);

		private final int size;
		private final int spacing;
		private final int fontSize;
		private final int progressBarWidth;
		private final int progressBarHeight;
		private final double progressBarYOffset;

		private Size(int size, int spacing, int fontSize, int progressBarWidth, int progressBarHeight, double progressBarYOffset) {
			this.size = size;
			this.spacing = spacing;
			this.fontSize = fontSize;
			this.progressBarWidth = progressBarWidth;
			this.progressBarHeight = progressBarHeight;
			this.progressBarYOffset = progressBarYOffset;
		}

		public int getSize() {
			return size;
		}

		public int getSpacing() {
			return spacing;
		}

		public int getFontSize() {
			return fontSize;
		}

		public int getProgressBarWidth() {
			return progressBarWidth;
		}

		public int getProgressBarHeight() {
			return progressBarHeight;
		}

		public double getProgressBarYOffset() {
			return progressBarYOffset;
		}
	}

	public enum Status {

		NO_INDICATOR(""),
		READY("printerStatus.idle"),
		PRINTING("printerStatus.printing"),
		PAUSED("printerStatus.paused"),
		NOTIFICATION("");

		private final String key;

		private Status(String key) {
			this.key = key;
		}

		/**
		 *
		 * @return
		 */
		public String getKey() {
			return key;
		}
	}

	@FXML
	private Text name;

	@FXML
	private Text rootName;

	@FXML
	private HBox rootNameBox;

	@FXML
	private Pane innerPane;

	@FXML
	private WhiteProgressBar progressBar;

	@FXML
	private PrinterSVG printerSVG;

	@FXML
	private ImageView printerImage;

	private double imageAspectRatio;

	private final Printer printer;
	private final ComponentIsolationInterface isolationInterface;

	private String styleClassForText = "regularText";

	private ChangeListener<String> nameListener = (ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
		setName(newValue);
	};
	private ChangeListener<Color> colorListener = (ObservableValue<? extends Color> observable, Color oldValue, Color newValue) -> {
		setColour(newValue);
	};
	private ChangeListener<Number> progressListener = (ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
		setProgress((double) newValue);
	};

	@Inject
	private I18N i18n;

	@Inject
	private TaskExecutor taskExecutor;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	@Inject
	PrinterColourMap printerColourMap;

	public PrinterComponent(Printer printer, ComponentIsolationInterface isolationInterface) {

		GuiceContext.get().injectMembers(this);

		this.printer = printer;
		this.isolationInterface = isolationInterface;

		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(getClass().getResource("PrinterComponent.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		initialise();
	}

	public void setStatus(Status status) {
		printerSVG.setStatus(status);
		progressBar.setVisible(showProgress);
	}

	public void setName(String newName) {
		newName = fitNameToWidth(newName);
		nameTextProperty().set(newName);
	}

	public StringProperty nameTextProperty() {
		return name.textProperty();
	}

	/**
	 * Initialise the component
	 */
	private void initialise() {
		setStyle("-fx-background-color: white;");

		name.getStyleClass().add(styleClassForText);
		name.setFill(Color.WHITE);

		rootName.getStyleClass().add(styleClassForText);
		rootName.setFill(Color.WHITE);

		String nameText;

		if (printer != null) {
			printerSVG.setPrinterIcon(printer.printerConfigurationProperty().get().getTypeCode());
			nameText = printer.getPrinterIdentity().printerFriendlyNameProperty().get();
			if (!printer.printerConfigurationProperty().get().getTypeCode().equalsIgnoreCase("RBX10") && printer.getCommandInterface() instanceof RoboxRemoteCommandInterface) {
				rootName.textProperty().unbind();
				rootName.textProperty().bind(((RemoteDetectedPrinter) printer.getCommandInterface().getPrinterHandle()).getServerPrinterIsAttachedTo().nameProperty());
				rootName.setVisible(true);
				printerSVG.setIsRoot(true);
			}
			else {
				rootName.setVisible(false);
				printerSVG.setIsRoot(false);
			}

			setColour(printer.getPrinterIdentity().printerColourProperty().get());
			printer.getPrinterIdentity().printerFriendlyNameProperty().addListener(nameListener);
			printer.getPrinterIdentity().printerColourProperty().addListener(colorListener);

			if (printer.getPrintEngine() != null)
				printer.getPrintEngine().progressProperty().addListener(progressListener);

			printer.printerStatusProperty().addListener(
					(ObservableValue<? extends PrinterStatus> observable, PrinterStatus oldValue, PrinterStatus newValue) -> {
						updateStatus(newValue, printer.pauseStatusProperty().get());
					});

			printer.pauseStatusProperty().addListener(
					(ObservableValue<? extends PauseStatus> observable, PauseStatus oldValue, PauseStatus newValue) -> {
						updateStatus(printer.printerStatusProperty().get(), newValue);
					});

			if (printer.getPrintEngine() != null)
				printer.getPrintEngine().highIntensityCommsInProgressProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
					updateStatus(printer.printerStatusProperty().get(), printer.pauseStatusProperty().get());
				});

			updateStatus(printer.printerStatusProperty().get(), printer.pauseStatusProperty().get());

			printer.getCurrentErrors().addListener((ListChangeListener.Change<? extends FirmwareError> c) -> {
				taskExecutor.runOnGUIThread(() -> {
					dealWithErrorVisibility();
				});
			});

			dealWithErrorVisibility();

			//TODO: There don't appear to be any images for these only a Frankenstein's Monster image.
			URL printerImageURL = getClass().getResource(printer.printerConfigurationProperty().get().getTypeCode() + ".png");
			if (printerImageURL != null) {
				Image newImage = new Image(printerImageURL.toExternalForm());
				imageAspectRatio = newImage.getWidth() / newImage.getHeight();
				printerImage.setImage(newImage);
			}
			else {
				printerImage.setVisible(false);
			}
		}
		else {
			nameText = i18n.t("sidePanel_printerStatus.notConnected");
			String style = "-fx-background-color: " + formatHexString(StandardColours.LIGHT_GREY) + ";";
			innerPane.setStyle(style);
			setStatus(Status.NO_INDICATOR);
		}

		nameText = fitNameToWidth(nameText);
		name.setText(nameText);

		setSize(Size.SIZE_LARGE);

		this.disabledProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
			if (newValue) {
				this.setOpacity(0.1);
			}
			else {
				this.setOpacity(1);
			}
		});
	}

	private void dealWithErrorVisibility() {

		if (printer.getCurrentErrors().isEmpty()) {
			errorTooltip.setText("");
			Tooltip.uninstall(this, errorTooltip);
			printerSVG.showErrorIndicator(false);
		}
		else {
			String errorTooltipString = printer.getCurrentErrors().stream()
					.map(error -> i18n.t("misc.error") + ": " + i18n.t(error.getErrorTitleKey()) + "\n")
					.collect(Collectors.joining());
			errorTooltip.setText(errorTooltipString);
			Tooltip.install(this, errorTooltip);
			printerSVG.showErrorIndicator(true);
		}
	}

	public void setProgress(double progress) {
		progressBar.setProgress(progress);
	}

	public void setColour(Color color) {
		Color displayColour = printerColourMap.printerToDisplayColour(color);
		String colourHexString = formatHexString(displayColour);
		String style = "-fx-background-color: " + colourHexString + ";";
		innerPane.setStyle(style);
	}

	public void setSelected(boolean select) {
		if (selected != select) {
			selected = select;
			redraw();
		}
	}

	public void setSize(Size size) {
		if (size != currentSize) {
			currentSize = size;
			redraw();
		}
	}

	private void updateStatus(PrinterStatus printerStatus, PauseStatus pauseStatus) {
		Status status;

		switch (printerStatus) {
			case IDLE:
			case REMOVING_HEAD:
			case OPENING_DOOR:
				status = Status.READY;
				inInterruptibleState = true;
				showProgress = false;
				break;
			case RUNNING_MACRO_FILE:
			case PRINTING_PROJECT:
			case CALIBRATING_NOZZLE_ALIGNMENT:
			case CALIBRATING_NOZZLE_OPENING:
			case CALIBRATING_NOZZLE_HEIGHT:
			case PURGING_HEAD:
				status = Status.PRINTING;
				inInterruptibleState = true;
				showProgress = true;
				break;
			default:
				status = Status.READY;
				inInterruptibleState = false;
				showProgress = false;
				break;
		}

		if (pauseStatus == PauseStatus.PAUSED
				|| pauseStatus == PauseStatus.SELFIE_PAUSE
				|| pauseStatus == PauseStatus.PAUSE_PENDING) {
			status = Status.PAUSED;
		}

		if (printer.getPrintEngine() != null && printer.getPrintEngine().highIntensityCommsInProgressProperty().get()) {
			inInterruptibleState = false;
		}

		setStatus(status);

		isolationInterface.interruptibilityUpdated(this);
	}

	/**
	 * Redraw the component. Reposition child nodes according to selection state and size.
	 */
	private void redraw() {
		int size = currentSize.getSize();
		int fontSize = currentSize.getFontSize();
		int progressBarWidth = currentSize.getProgressBarWidth();
		int progressBarHeight = currentSize.getProgressBarHeight();
		double progressBarYOffset = currentSize.getProgressBarYOffset();
		double nameLayoutY;

		int borderWidth = selected ? 3 : 0;

		setPrefWidth(size);
		setMinWidth(size);
		setMaxWidth(size);
		setMinHeight(size);
		setMaxHeight(size);
		setPrefHeight(size);

		double progressBarX = (size - progressBarWidth) / 2.0;
		double progressBarY = size - progressBarYOffset - progressBarHeight;

		innerPane.setMinWidth(size - borderWidth * 2);
		innerPane.setMaxWidth(size - borderWidth * 2);
		innerPane.setMinHeight(size - borderWidth * 2);
		innerPane.setMaxHeight(size - borderWidth * 2);
		innerPane.setTranslateX(borderWidth);
		innerPane.setTranslateY(borderWidth);

		rootNameBox.setPrefWidth(size - borderWidth * 2);
		rootNameBox.setPrefHeight(size - borderWidth * 2);
		rootNameBox.setTranslateX(borderWidth);
		rootNameBox.setTranslateY(borderWidth);

		printerSVG.setSize(currentSize);
		progressBar.setLayoutX(progressBarX);
		progressBar.setLayoutY(progressBarY);
		progressBar.setControlWidth(progressBarWidth);
		progressBar.setControlHeight(progressBarHeight);

		if (printerImage.isVisible()) {
			double fitHeight = size / 2;
			double fitWidth = (size / 2) * imageAspectRatio;
			printerImage.setFitHeight(fitHeight);
			printerImage.setFitWidth(fitWidth);
			printerImage.setLayoutX(size - fitWidth);
			printerImage.setLayoutY(size - fitHeight);
		}

		for (Node child : innerPane.getChildren()) {
			child.setTranslateX(-borderWidth);
			child.setTranslateY(-borderWidth);
		}

		name.setStyle("-fx-font-size: " + fontSize
				+ "px;");
		name.setLayoutX(progressBarX);

		rootName.setStyle("-fx-font-size: " + fontSize
				+ "px;");
		rootName.setLayoutX(progressBarX);

		Font font = name.getFont();
		Font actualFont = new Font(font.getName(), fontSize);
		FontMetrics fontMetrics = Toolkit.getToolkit().getFontLoader().getFontMetrics(actualFont);

		nameLayoutY = size - (progressBarYOffset / 2) + fontMetrics.getDescent();
		name.setLayoutY(nameLayoutY);

		updateBounds();

		setPrefSize(size, size);

	}

	@Override
	public double computeMinHeight(double width) {
		return currentSize.getSize();
	}

	@Override
	public double computeMinWidth(double height) {
		return currentSize.getSize();
	}

	@Override
	public double computeMaxHeight(double width) {
		return currentSize.getSize();
	}

	@Override
	public double computeMaxWidth(double height) {
		return currentSize.getSize();
	}

	@Override
	public double computePrefHeight(double width) {
		return currentSize.getSize();
	}

	@Override
	public double computePrefWidth(double height) {
		return currentSize.getSize();
	}

	/**
	 * Fit the printer name to the available space.
	 */
	public String fitNameToWidth(String name) {

		int FONT_SIZE = 14;
		int AVAILABLE_WIDTH = 125;
		double stringWidth = getWidthOfString(name, styleClassForText, FONT_SIZE);
		int i = 0;
		while (stringWidth > AVAILABLE_WIDTH) {
			name = name.substring(0, name.length() - 1);
			stringWidth = getWidthOfString(name, styleClassForText, FONT_SIZE);
			if (i > 100) {
				break;
			}
			i++;
		}
		return name;
	}

	public boolean isInterruptible() {
		return inInterruptibleState;
	}
}
