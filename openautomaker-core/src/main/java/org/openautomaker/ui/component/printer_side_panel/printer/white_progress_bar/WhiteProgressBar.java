
package org.openautomaker.ui.component.printer_side_panel.printer.white_progress_bar;

import java.io.IOException;
import java.net.URL;

import org.openautomaker.guice.FXMLLoaderFactory;
import org.openautomaker.guice.components.GuicedPane;

import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

public class WhiteProgressBar extends GuicedPane {

	@FXML
	private Polygon solidBar;

	@FXML
	private Polygon clearBar;

	private double width;
	private double height;
	private double progress;

	@Inject
	private FXMLLoaderFactory fxmlLoaderFactory;

	public WhiteProgressBar() {
		super();
		URL fxml = getClass().getResource("WhiteProgressBar.fxml");
		FXMLLoader fxmlLoader = fxmlLoaderFactory.create(fxml);
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);

		try {
			fxmlLoader.load();
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}

		solidBar.setFill(Color.WHITE);
		clearBar.setFill(Color.WHITE);
		clearBar.setOpacity(0.5);

		width = 50;
		height = 10;
		redraw();
	}

	/**
	 * Sets the progress of the bar.
	 * 
	 * @param progress - Double between 0 and 1
	 */
	public void setProgress(double progress) {
		if (progress != this.progress) {
			this.progress = progress;
			redraw();
		}
	}

	public void setControlWidth(double width) {
		this.width = width;
		redraw();
	}

	public void setControlHeight(double height) {
		this.height = height;
		redraw();
	}

	private void redraw() {
		double barWidth = width * progress;
		solidBar.getPoints().clear();
		solidBar.getPoints().addAll(new Double[] {
				0.0, 0.0,
				barWidth, 0.0,
				barWidth, height,
				0.0, height,
		});
		clearBar.getPoints().clear();
		clearBar.getPoints().addAll(new Double[] {
				barWidth, height,
				width, height,
				width, 0.0,
				barWidth, 0.0,
		});
	}
}
