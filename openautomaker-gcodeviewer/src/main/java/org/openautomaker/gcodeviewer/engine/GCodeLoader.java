package org.openautomaker.gcodeviewer.engine;

import static org.lwjgl.glfw.GLFW.glfwPostEmptyEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Tony
 */
public class GCodeLoader extends Thread {
	private static final Logger LOGGER = LogManager.getLogger();

	GCodeProcessor processor;
	GCodeLineProcessor lineProcessor;

	private boolean loadOK = false;
	private boolean loadDone = false;
	private String gCodeFile;

	public GCodeLoader(String gCodeFile, RenderParameters renderParameters, GCodeViewerConfiguration configuration)
	{
		this.setName("GCodeLoader");
		this.gCodeFile = gCodeFile;
		this.processor = new GCodeProcessor();
		this.lineProcessor = new GCodeLineProcessor(renderParameters, configuration, this.processor.getSettings());
	}

	@Override
	public void run()
	{
		LOGGER.debug("Loading GCode file");
		loadOK = processor.processFile(gCodeFile, lineProcessor);
		glfwPostEmptyEvent(); // Wake up main thread.
		loadDone = true;
	}

	public GCodeProcessor getProcessor() {
		return processor;
	}

	public GCodeLineProcessor getLineProcessor() {
		return lineProcessor;
	}

	public boolean loadSuccess() {
		return loadOK;
	}

	public boolean loadFinished() {
		return loadDone;
	}

	public String getFilePath() {
		return gCodeFile;
	}
}
