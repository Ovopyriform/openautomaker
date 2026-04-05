package org.openautomaker.environment;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.openautomaker.environment.preference.application.HomePathPreference;
import org.openautomaker.environment.preference.application.LogLevelPreference;
import org.openautomaker.environment.preference_factory.FilePreferencesFactory;
import org.openautomaker.environment.properties.ApplicationProperties;
import org.openautomaker.environment.properties.NativeProperties;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;

/**
 * Environment object for AutoMaker.
 * 
 * @author chris_henson
 *
 */
@Singleton
public class OpenAutomakerEnv {

	/*-
	 * Original Structure for Automaker working directory
	 * 
	 * CEL
	 *  +- AutoMaker
	 *  |	+- application.properties
	 *  |	+- AutoMaker [app]
	 *  |	+- AutoMaker.configFile.xml
	 *  |	+- AutoMaker.jar
	 *  |	+- AutoMaker.png
	 *  |	+- Java (JVM)
	 *  |	+- Key
	 *  |	|	+- automaker-root.ssh
	 *  |	+ Language
	 *  |	|	+- Language*.properties
	 *  |	+- lib (java)
	 *  |	+- librxtxSerial.jnilib // This needs to change to JSerialCom  RXTX is about 10 years out of date.
	 *  |	+- README
	 *  |		+- colorFabSplash.jpg
	 *  |		+- READEME_AutoMaker.html
	 *  +- Common
	 *  	+- bin
	 *  	|	+- KillCuraEngine.mac.sh [OS Specific Scripts]
	 *  	|	+- RoboxDetector.mac.sh [OS Specific Scripts]
	 *  	+- CameraProfiles
	 *  	|	+- Logitech C920.cameraprofile
	 *  	+- Cura
	 *  	|	+- CuraEngine
	 *  	+- Cura4
	 *  	|	+- CuraEngine
	 *  	+- Filaments
	 *  	|	+- *.roboxfilament
	 *  	+- GCodeViewer [app]
	 *  	+- Heads
	 *  	|	+- *.roboxhead
	 *  	+- Language
	 *  	|	+- UI_LanguageData*.properties
	 *  	|	+- NoUI_LanguageData*.properties
	 *  	+- Macros
	 *  	|	+- *.gcode
	 *  	+- Models
	 *  	|	+- *.gcode [models and demo code]
	 *  	+- Printers
	 *  	|	+- RBX01.roboxprinter
	 *  	|	+- RBX02.roboxprinter
	 *  	|	+- RBX10.roboxprinter
	 * 		+- PrintProfiles
	 * 		|	+- Cura [print profiles for heads]
	 * 		|	+- Cura4 [print profiles for heads]
	 * 		|	+- slicermapping.dat
	 * 		+- robox_r776.bin
	 *  	+- robox_r780.bin
	 *   	+- robox_r781.bin
	 */

	/*-
	 * New folder structure
	 * ${jpackage.app-path}  
	 *  +- openautomaker.properties [contains original properties and configuration file info] --> Moving to system preferences
	 *  +- openautomaker
	 *  	+- script [Contents of Common/bin]
	 *  	|	+- KillCuraEngine.*.sh
	 *  	|	+- RoboxDetector.*.sh
	 *  	+- cura-engine - Should this just be bundled in the class path?
	 *  	|	+- cura
	 *  	|	+- cura4 (do I need Cura any more.  Why choose the older one)
	 *  	|	+- serial ??  [What is the name of the lib]
	 *  	+- language - these can be bundled.  There's no reason to have them separately.
	 *  	|	+- all language resources [current splitting of language resources is a bit mental and prevents use of vanilla PropertiesResourceBundle, requires custom code]
	 *  	+- key
	 *  	|	+- ssh key
	 *  	+- camera-profiles
	 *  	+- filaments
	 *  	+- heads
	 *  	+- macros
	 *		+- models
	 *		+- printers
	 *		+- print-profiles
	 *		+- firmware
	 *		+- cura-engine
	 *
	 * ${user.home}
	 *	+- openautomaker
	 *		+- automaker.properties [user overrides of base properties] --> Moving to user preferences
	 *		+- camera-profiles
	 *		+- filaments
	 *		+- heads
	 *		+- macros
	 *		+- printers
	 *		+- print-profiles
	 *		+- print-jobs
	 *		+- projects
	 *		+- timelapse
	 */

	public static final String OPENAUTOMAKER = "openautomaker";

	private static final String OPENAUTOMAKER_ROOT = OPENAUTOMAKER + ".root";
	public static final String OPENAUTOMAKER_ROOT_CONNECTED = OPENAUTOMAKER_ROOT + ".connected";

	//public static final String OPENAUTOMAKER_LEGACY = OPENAUTOMAKER + ".legacy";

	// Properties specific to ROOT
	// TODO: Consider separate ROOT environment
	private static final String ROOT = "root";
	public static final String ROOT_SERVER_NAME = ROOT + ".server.name";
	public static final String ROOT_ACCESS_PIN = ROOT + ".access.pin";

	private static final Logger LOGGER = LogManager.getLogger();

	private MachineType machineType = null;
	
	// Dependencies
	private final ApplicationProperties applicationProperties;
	private final NativeProperties nativeProperties;
	private final HomePathPreference homePathPreference;
	private final LogLevelPreference logLevelPreference;

	@Inject
	protected OpenAutomakerEnv(
			ApplicationProperties applicationProperties,
			NativeProperties nativeProperties,
			HomePathPreference homePathPreference,
			LogLevelPreference logLevelPreference) {

		this.applicationProperties = applicationProperties;
		this.nativeProperties = nativeProperties;
		this.homePathPreference = homePathPreference;
		this.logLevelPreference = logLevelPreference;

		configurePreferences();
		configureLogging();

		if (LOGGER.isDebugEnabled())
			dumpAppProps();

		if (LOGGER.isTraceEnabled())
			dumpSysProps();
	}

	private static boolean isPackaged() {
		return System.getProperty("jpackage.app-path") != null;
	}
	
	private void configurePreferences() {
		// If it's not packaged, use file preferences
		if (!isPackaged()) {
			LOGGER.info("Application is unpackaged.  Configuring for test environment");
			LOGGER.info("Using FilePreferecesFactory for test environment");
			System.setProperty("java.util.prefs.PreferencesFactory", FilePreferencesFactory.class.getName());
		}
		
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Application Home Path: " + homePathPreference.getAppValue().toString());
			LOGGER.debug("User Home Path: " + homePathPreference.getUserValue().toString());
		}
	}
	
	/**
	 * Check logging properties and set up logger appropriately
	 */
	private void configureLogging() {
		// If it's unpackaged, always use debug logging.  Ignore log level changes
		if (!isPackaged()) {
			Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);
			return;
		}

		LogLevelPreference logLevel = logLevelPreference;

		Configurator.setAllLevels(LogManager.getRootLogger().getName(), logLevel.getValue());
		
		logLevel.addChangeListener((evt) -> {
				Configurator.setAllLevels(LogManager.getRootLogger().getName(), logLevel.getValue());
		});
	}
	
	/**
	 * Dump all the properties to the log
	 */
	private void dumpAppProps() {
		LOGGER.debug("Application Properties ------------------------------------");
		applicationProperties.getPropertyNames().forEach((key) -> {
			LOGGER.debug(key + ": " + applicationProperties.get(key));
		});
		
		LOGGER.debug("Native Properties ------------------------------------------");
		nativeProperties.getPropertyNames().forEach((key) -> {
			LOGGER.debug(key + ": " + nativeProperties.get(key).toString());
		});
		
	}

	private void dumpSysProps() {
		LOGGER.debug("System Properties -----------------------------------------");
		System.getProperties().stringPropertyNames().forEach((key) -> {
			LOGGER.debug(key + ": " + System.getProperty(key));
		});
	}
	
	/**
	 * MachineType, used for UI oddities
	 * 
	 * @return Detected MachineType
	 * 
	 * This should not be needed if the build is created correctly.  Given the build is created for a specific system, configuration can be created at
	 * build-time and simply used at runtime.  The runtime system should not have to know what it's running on.
	 */
	public MachineType getMachineType() {
		if (machineType != null)
			return machineType;
		
		String osName = System.getProperty("os.name");
		for (MachineType mt : MachineType.values()) {
			if (osName.matches(mt.getRegex())) {
				machineType = mt;
				return machineType;
			}
		}
		
		// Case where there's an unknown OS.
		return null;
	}
	
	/**
	 * Checks for 3D support.
	 * 
	 * @return true if supported
	 */
	public boolean has3DSupport() {
		String forceGPU = System.getProperty("prism.forceGPU");
		if (forceGPU != null && forceGPU.equalsIgnoreCase("true"))
			return true;

		if (Platform.isSupported(ConditionalFeature.SCENE3D))
			return true;
		
		return false;
	}
}
