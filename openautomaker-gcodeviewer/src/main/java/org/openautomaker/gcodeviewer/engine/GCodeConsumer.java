
package org.openautomaker.gcodeviewer.engine;

import org.openautomaker.gcodeviewer.gcode.GCodeLine;

/**
 *
 * @author Tony
 */
public interface GCodeConsumer {
	public void reset();
	public void processLine(GCodeLine line);
	public void complete();
}
