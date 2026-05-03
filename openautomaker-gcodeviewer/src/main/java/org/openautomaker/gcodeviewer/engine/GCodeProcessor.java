package org.openautomaker.gcodeviewer.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.gcodeviewer.entities.Entity;
import org.openautomaker.gcodeviewer.gcode.GCodeLine;
import org.openautomaker.gcodeviewer.gcode.GCodeLineParser;
import org.parboiled.Parboiled;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

/**
 * Process a GCode file line by line.
 *
 * @author Tony Aldhous
 */
public class GCodeProcessor {

	private static final Logger LOGGER = LogManager.getLogger();

	int numberOfBottomLayer = Entity.NULL_LAYER;
	int numberOfTopLayer = Entity.NULL_LAYER;

	List<String> lines = new ArrayList<>();
	// Settings can be included in the GCode comments, and can be used to "tweak" the generated view.
	// For example, the infill width and thickness are used to correct the appearance of thick
	// infill layers.
	Map<String, Double> settingsMap = new HashMap<>(); // Settings read from the GCode comments.

	/**
	 * Read G-Code file from the given file path passing each line to the consumer.
	 *
	 * @param filePath path to the file to be processed
	 * @return boolean indicating success or failure.
	 */
	public boolean processFile(String filePath, GCodeConsumer consumer) {
		boolean success = false;
		try {
			FileReader fileReader = new FileReader(new File(filePath));
			BufferedReader bufferedReader = new BufferedReader(fileReader);

			GCodeLineParser gCodeParser = Parboiled.createParser(GCodeLineParser.class);
			gCodeParser.setSettingsMap(settingsMap);

			ReportingParseRunner runner = new ReportingParseRunner<>(gCodeParser.Line());
			consumer.reset();
			int lineNumber = -1;
			for (String lineRead = bufferedReader.readLine(); lineRead != null; lineRead = bufferedReader.readLine()) {
				++lineNumber;
				lineRead = lineRead.trim();
				lines.add(lineRead);
				if (!lineRead.isEmpty())
				{
					gCodeParser.resetLine();
					ParsingResult result = runner.run(lineRead);
					if (result.hasErrors() || !result.matched) {
						String errorReport = "Parsing failure on line " + lineNumber + ": ";
						if (result.hasErrors())
							errorReport += ErrorUtils.printParseErrors(result);
						else
							errorReport += "no match.";
						LOGGER.error(errorReport);
						throw new RuntimeException(errorReport);
					} else {
						GCodeLine line = gCodeParser.getLine();
						line.lineNumber = lineNumber;
						if (line.layerNumber > Entity.NULL_LAYER) {
							if (numberOfTopLayer == Entity.NULL_LAYER || numberOfTopLayer < line.layerNumber)
								numberOfTopLayer = line.layerNumber;
							if (numberOfBottomLayer == Entity.NULL_LAYER || numberOfBottomLayer > line.layerNumber)
								numberOfBottomLayer = line.layerNumber;
						}
						consumer.processLine(line);
						//LOGGER.info("Read line" + Integer.toString(line.lineNumber));
					}
				}
			}
			consumer.complete();
			LOGGER.info("Parsed " + Integer.toString(lineNumber) + " lines.");
			success = true;
		} catch (IOException ex) {
			LOGGER.error("IO exception when attempting to parse file: " + filePath);
			LOGGER.error(ex.toString());
		}

		return success;
	}

	/**
	 * Read G-Code file from the given file path, returning it a list of GCodeLines.
	 *
	 * @param filePath path to the file to be loaded
	 * @return boolean indicating success or failure.
	 */
	public List<GCodeLine> loadFile(String filePath) {
		boolean success = false;
		List<GCodeLine> lines = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(new File(filePath));
			BufferedReader bufferedReader = new BufferedReader(fileReader);

			GCodeLineParser gCodeParser = Parboiled.createParser(GCodeLineParser.class);
			ReportingParseRunner runner = new ReportingParseRunner<>(gCodeParser.Line());
			int lineNumber = -1;
			for (String lineRead = bufferedReader.readLine(); lineRead != null; lineRead = bufferedReader.readLine()) {
				++lineNumber;
				lineRead = lineRead.trim();
				if (!lineRead.isEmpty())
				{
					gCodeParser.resetLine();
					ParsingResult result = runner.run(lineRead);
					if (result.hasErrors() || !result.matched) {
						String errorReport = "Parsing failure on line " + lineNumber + ": ";
						if (result.hasErrors())
							errorReport += ErrorUtils.printParseErrors(result);
						else
							errorReport += "no match.";
						LOGGER.error(errorReport);
						throw new RuntimeException(errorReport);
					} else {
						GCodeLine line = gCodeParser.getLine();
						line.lineNumber = lineNumber;
						if (lines.isEmpty() ||numberOfTopLayer < line.layerNumber)
							numberOfTopLayer = line.layerNumber;
						if (lines.isEmpty() || numberOfBottomLayer > line.layerNumber)
							numberOfBottomLayer = line.layerNumber;
						lines.add(line);
						//LOGGER.info("Read line" + Integer.toString(line.lineNumber));
					}
				}
			}
			LOGGER.info("Parsed " + Integer.toString(lineNumber) + " lines.");
			success = true;
		} catch (IOException ex) {
			LOGGER.error("IO exception when attempting to parse file: " + filePath);
			LOGGER.error(ex.toString());
		}

		if (!success)
			lines.clear();

		return lines;
	}

	public int getNumberOfTopLayer()
	{
		return numberOfTopLayer;
	}

	public int getNumberOfBottomLayer()
	{
		return numberOfBottomLayer;
	}

	public List<String> getLines()
	{
		return lines;
	}

	public Map<String, Double> getSettings()
	{
		return settingsMap;
	}
}
