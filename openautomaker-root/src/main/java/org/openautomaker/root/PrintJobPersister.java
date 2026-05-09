package org.openautomaker.root;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.printerControl.PrintJob;
import org.openautomaker.environment.preference.root.PrintJobsPathPreference;
import org.parboiled.common.FileUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PrintJobPersister {

	private static final Logger LOGGER = LogManager.getLogger();

	private final PrintJobsPathPreference printJobsPathPreference;

	private String printJobIDBeingPersisted = null;
	private BufferedWriter printJobWriter = null;

	@Inject
	public PrintJobPersister(PrintJobsPathPreference printJobsPathPreference) {
		this.printJobsPathPreference = printJobsPathPreference;
	}

	public void startFile(String printJobID) {
		try {
			if (printJobWriter != null) {
				printJobWriter.close();
				LOGGER.error("Found a partially persisted remote file for job " + printJobIDBeingPersisted);
			}

			printJobIDBeingPersisted = printJobID;
			PrintJob pj = new PrintJob(printJobIDBeingPersisted, printJobsPathPreference.getValue());

			FileUtils.forceMkdir(pj.getRoboxisedFileLocation().toFile().getParentFile());
			FileWriter fw = new FileWriter(pj.getRoboxisedFileLocation().toFile());
			printJobWriter = new BufferedWriter(fw);
		} catch (IOException ex) {
			LOGGER.error("Error when attempting to persist remote file " + printJobIDBeingPersisted, ex);
		}
	}

	public void writeSegment(String segment) {
		if (printJobWriter != null) {
			try {
				printJobWriter.write(segment);
			} catch (IOException ex) {
				LOGGER.error("Error when attempting to persist remote file " + printJobIDBeingPersisted, ex);
			}
		} else {
			LOGGER.error("Unable to process remote file segment - no local file open.");
		}
	}

	public void closeFile(String segment) {
		LOGGER.info("End of print job " + printJobIDBeingPersisted);

		if (printJobWriter != null) {
			try {
				printJobWriter.write(segment);
				printJobWriter.flush();
				printJobWriter.close();
				printJobWriter = null;
				printJobIDBeingPersisted = null;
			} catch (IOException ex) {
				LOGGER.error("Error when attempting to persist remote file " + printJobIDBeingPersisted, ex);
			}
		} else {
			LOGGER.error("Unable to process end of remote file - no local file open.");
		}
	}

	public String getPrintJobID() {
		return printJobIDBeingPersisted;
	}
}
