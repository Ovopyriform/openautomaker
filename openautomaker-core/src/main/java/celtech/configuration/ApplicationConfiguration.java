package celtech.configuration;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import celtech.appManager.ProjectMode;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 *
 * @author Ian Hudson @ Liberty Systems Limited
 */
@Deprecated
// Find a better way to do this.
public class ApplicationConfiguration {

	private static final Logger LOGGER = LogManager.getLogger();

	public static final String resourcePath = "/celtech/resources/";

	public static final String modelResourcePath = resourcePath + "models/";

	public static final String imageResourcePath = resourcePath + "images/";

	public static final String fxmlResourcePath = resourcePath + "fxml/";

	public static final String fxmlPanelResourcePath = resourcePath + "fxml/panels/";

	public static final String fxmlDiagramsResourcePath = resourcePath + "fxml/diagrams/";

	public static final String fxmlButtonsResourcePath = resourcePath + "fxml/buttons/";

	public static final String fxmlTabsResourcePath = resourcePath + "fxml/tabs/";

	public static final String fxmlPrinterStatusResourcePath = resourcePath + "fxml/printerstatus/";

	public static final String fxmlUtilityPanelResourcePath = resourcePath + "fxml/utilityPanels/";

	public static final String fxmlPopupResourcePath = resourcePath + "fxml/popups/";

	public static final String fxmlBillingResourcePath = resourcePath + "fxml/billing/";

	public static final String fxmlLicensingResourcePath = resourcePath + "fxml/licensing/";

	public static final String fontResourcePath = "/org/openautomaker/ui/" + "fonts/";

	public static final String cssResourcePath = "/org/openautomaker/ui/" + "css/";

	private static final String mainCSSFile = cssResourcePath + "JMetroDarkTheme.css";

	private static final String dialogsCSSFile = cssResourcePath + "dialogsOverride.css";

	public static final double DEFAULT_WIDTH = 1440;

	public static final double DEFAULT_HEIGHT = 900;

	public static final double DESIRED_ASPECT_RATIO = DEFAULT_WIDTH / DEFAULT_HEIGHT;

	public static String projectGCodeDirectory = "GCode";

	public static final String demoPrintFilename = "demoPrint.gcode";
	private static final String supportedProjectFileExtension = org.openautomaker.ui.project.robox.RoboxFile.EXTENSION_WITHOUT_DOT;
	private static final String supportedRbxProjFileExtension = "rbxproj";

	public static final String[] supportedModelExtensions = {
			"stl",
			"obj",
			//"svg",
			"zip"
	};

	public static final String[] supportedProcessedModelExtensions = {
			//        "gcode"
	};

	public static final String printFileExtension = ".prt";

	private static String myMiniFactoryDownloadsDirectory = null;

	public static final float bedHotAboveDegrees = 60.0f;

	public static final String projectDataFilename = "projects.dat";

	public static final Color xAxisColour = Color.RED;

	public static final Color zAxisColour = Color.GREEN;

	public static final Duration notificationDisplayDelay = Duration.seconds(5);

	public static final Pos notificationPosition = Pos.BOTTOM_RIGHT;

	/**
	 * These variables are used to position the head correctly over the bed The actual travel of the mechanical system is not the same as the theoretical travel (to allow for door opening positions etc)
	 */
	public static final int xPrintOffset = 6;
	public static final int yPrintOffset = 6;

	public static final String timeOfLastNewsRetrievalItem = "LastSuccessfulNewsRetrieval";

	private static void addExtension(List<String> extensionList, String extension) {
		extensionList.add(extension);
	}

	public static ArrayList<String> getSupportedFileExtensionWildcards(ProjectMode projectMode) {
		ArrayList<String> returnVal = new ArrayList<>();

		switch (projectMode) {
			case NONE:
				for (String extension : supportedModelExtensions) {
					addExtension(returnVal, "*." + extension);
				}
				for (String extension : supportedProcessedModelExtensions) {
					addExtension(returnVal, "*." + extension);
				}
				break;
			case MESH:
				for (String extension : supportedModelExtensions) {
					addExtension(returnVal, "*." + extension);
				}
				break;
			default:
				break;
		}

		return returnVal;
	}

	public static ArrayList<String> getSupportedFileExtensions(ProjectMode projectMode) {
		ArrayList<String> returnVal = new ArrayList<>();

		switch (projectMode) {
			case NONE:
				for (String extension : supportedModelExtensions) {
					addExtension(returnVal, extension);
				}
				returnVal.add(supportedProjectFileExtension);
				returnVal.add(supportedRbxProjFileExtension);
				break;
			case MESH:
				for (String extension : supportedModelExtensions) {
					addExtension(returnVal, extension);
				}
				break;
			default:
				break;
		}

		return returnVal;
	}

	//	private static Preference<Path> getPathPreference(DirectoryMemoryProperty whichProperty) {
	//		Preference<Path> pathPref = null;
	//		
	//		switch(whichProperty) {
	//			case LAST_FIRMWARE_DIRECTORY:
	//				pathPref = new FirmwarePathPreference();
	//				break;
	//			case LAST_GCODE_DIRECTORY:
	//				pathPref = new GCodePathPreference();
	//				break;
	//			case LAST_MACRO_DIRECTORY:
	//				pathPref = new MacroPathPreference();
	//				break;
	//			case LAST_MODEL_DIRECTORY:
	//				pathPref = new ModelsPathPreference();
	//				break;
	//			default:
	//				break;
	//		}
	//
	//		return pathPref;
	//	}

	//TODO: Shouldn't use this method.  Should be direct access to preferences.
	//	public static String resetLastDirectoryToDefaults(DirectoryMemoryProperty whichProperty) {
	//
	//		Path path = null;
	//
	//		Preference<Path> pathPref = getPathPreference(whichProperty);
	//
	//		pathPref.remove();
	//		return pathPref.getValue().toString();
	//
	//		//String defaultDirectory = OpenAutomakerEnv.get().getUserPath().toString();
	//		//setLastDirectory(whichProperty, defaultDirectory);
	//		//return defaultDirectory;
	//	}

	//	private static String getLastDirectory(DirectoryMemoryProperty memoryProperty) {
	//		Preference<Path> pathPref = getPathPreference(memoryProperty);
	//		return pathPref.getValue().toString();
	//	}

	//	public static File getLastDirectoryFile(DirectoryMemoryProperty memoryProperty) {
	//		String directory = getLastDirectory(memoryProperty);
	//
	//		File modelDirectory = new File(directory);
	//		if (!modelDirectory.exists()) {
	//			directory = ApplicationConfiguration.resetLastDirectoryToDefaults(memoryProperty);
	//			modelDirectory = new File(directory);
	//		}
	//		return modelDirectory;
	//	}

	//	public static void setLastDirectory(DirectoryMemoryProperty memoryProperty, String value) {
	//		getPathPreference(memoryProperty).setValue(Path.of(value));
	//	}

	/**
	 * TODO: WTF is this nonsense
	 * 
	 * This method supplies the application-specific download directory component for updates It is a hack and should be removed...
	 *
	 * @param applicationName
	 * @return
	 */
	public static String getDownloadModifier(String applicationName) {
		if (applicationName.equals("CEL Robox")) {
			return "0abc523fc24";
		}
		else if (applicationName.equals("CEL ReelProgrammer")) {
			return "14f690bc22c";
		}
		else if (applicationName.equals("CEL Commissionator")) {
			return "1532f2c4ab";
		}
		else {
			return null;
		}
	}

	public static String getMainCSSFile() {
		return ApplicationConfiguration.class.getResource(mainCSSFile).toExternalForm();
	}

	public static String getDialogsCSSFile() {
		return ApplicationConfiguration.class.getResource(dialogsCSSFile).toExternalForm();
	}
}
