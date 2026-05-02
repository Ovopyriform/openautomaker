package org.openautomaker.base.configuration.datafileaccessors;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.printerControl.model.Head.HeadType;
import org.openautomaker.environment.preference.printer.HeadDefsPathPreference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

//TODO: Seems like an odd way to sort out the heads.  Fix.
@Singleton
public class HeadContainer {

	private static final Logger LOGGER = LogManager.getLogger();

	private static final ObservableList<HeadFile> completeHeadList = FXCollections.observableArrayList();
	private static final ObservableMap<String, HeadFile> completeHeadMap = FXCollections.observableHashMap();
	private static final ObjectMapper mapper = new ObjectMapper();
	public static final String defaultHeadID = "RBX01-SM";
	public static final HeadType defaultHeadType = HeadType.SINGLE_MATERIAL_HEAD;

	//private final HeadDefsPathPreference headDefsPathPreference;

	//TODO: This should check the application heads folder (default heads) and the user heads folder
	@Inject
	protected HeadContainer(HeadDefsPathPreference headDefsPathPreference) {

		File applicationHeadDirHandle = headDefsPathPreference.getAppValue().toFile();
		File[] applicationheads = applicationHeadDirHandle.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(BaseConfiguration.headFileExtension);

			}
		});

		//TODO: At this point get any user defined heads also.

		ArrayList<HeadFile> heads = ingestHeads(applicationheads);
		completeHeadList.addAll(heads);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
	}

	private ArrayList<HeadFile> ingestHeads(File[] headFiles) {
		ArrayList<HeadFile> headList = new ArrayList<>();

		for (File headFile : headFiles) {
			try {
				HeadFile headFileData = mapper.readValue(headFile, HeadFile.class);

				headList.add(headFileData);
				completeHeadMap.put(headFileData.typeCode, headFileData);

			}
			catch (IOException ex) {
				LOGGER.error("Error loading head " + headFile.getAbsolutePath());
			}
		}

		return headList;
	}

	public HeadFile getHeadByID(String headID) {
		HeadFile returnedHead = completeHeadMap.get(headID);
		return returnedHead;
	}

	public ObservableList<HeadFile> getCompleteHeadList() {
		return completeHeadList;
	}

	public boolean isTypeCodeInDatabase(String typeCode) {
		if (typeCode == null || typeCode.trim() == "")
			return false;

		return completeHeadMap.containsKey(typeCode);
	}
}
