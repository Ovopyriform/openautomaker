package org.openautomaker.base.configuration.datafileaccessors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openautomaker.base.MaterialType;
import org.openautomaker.base.comms.print_server.PrintServerConnection;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.FilamentFileFilter;
import org.openautomaker.base.utils.DeDuplicator;
import org.openautomaker.base.utils.FileUtilities;
import org.openautomaker.environment.preference.ConnectedServersPreference;
import org.openautomaker.environment.preference.printer.FilamentsPathPreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;

@Singleton
public class FilamentContainer {

	private static final Logger LOGGER = LogManager.getLogger();

	private final ObservableList<Filament> appFilamentList = FXCollections.observableArrayList();
	private final ObservableList<Filament> userFilamentList = FXCollections.observableArrayList();
	private final ObservableList<Filament> completeFilamentList = FXCollections.observableArrayList();
	//private final ObservableList<Filament> completeFilamentListNoDuplicates = FXCollections.observableArrayList();
	private final ObservableMap<String, Filament> completeFilamentMapByID = FXCollections.observableHashMap();
	private final ObservableMap<String, String> completeFilamentNameByID = FXCollections.observableHashMap();

	public static final String CUSTOM_BRAND = "Custom";
	public static final String CUSTOM_CATEGORY = "";

	public final Filament createNewFilament = new Filament(null, null, null, null, null,
			0, 0, 0, 0, 0, 0, 0, 0, Color.ALICEBLUE,
			0, 0, false, false);
	public static final Filament UNKNOWN_FILAMENT = new Filament("Unknown",
			null,
			"",
			"",
			"",
			1.75f,
			1,
			1,
			0,
			0,
			0,
			0,
			0,
			Color.ALICEBLUE,
			0,
			0,
			false,
			false);

	private static final String NAME = "name";
	private static final String MATERIAL = "material";
	private static final String REEL_ID = "reelID";
	private static final String BRAND = "brand";
	private static final String CATEGORY = "category";
	private static final String DIAMETER = "diameter_mm";
	private static final String COST_GBP_PER_KG = "cost_gbp_per_kg";
	private static final String FILAMENT_MULTIPLIER = "filament_multiplier";
	private static final String FEED_RATE_MULTIPLIER = "feed_rate_multiplier";
	private static final String AMBIENT_TEMPERATURE_C = "ambient_temperature_C";
	private static final String FIRST_LAYER_BED_TEMPERATURE_C = "first_layer_bed_temperature_C";
	private static final String BED_TEMPERATURE_C = "bed_temperature_C";
	private static final String FIRST_LAYER_NOZZLE_TERMERATURE_C = "first_layer_nozzle_temperature_C";
	private static final String NOZZLE_REMPERATURE_C = "nozzle_temperature_C";
	private static final String DISPLAY_COLOUR = "display_colour";
	private static final String DEFAULT_LENGTH_M = "default_length_m";
	private static final String FILLED = "filled";

	public interface FilamentDatabaseChangesListener {

		public void whenFilamentChanges(String filamentId);
	}

	private final List<FilamentDatabaseChangesListener> filamentDatabaseChangesListeners = new ArrayList<>();

	private final FilamentsPathPreference filamentsPathPreference;
	private final ConnectedServersPreference connectedServersPreference;

	@Inject
	protected FilamentContainer(
			FilamentsPathPreference filamentsPathPreference,
			ConnectedServersPreference connectedServersPreference) {

		this.filamentsPathPreference = filamentsPathPreference;
		this.connectedServersPreference = connectedServersPreference;

		loadFilamentData();
	}

	public void addFilamentDatabaseChangesListener(FilamentDatabaseChangesListener listener) {
		filamentDatabaseChangesListeners.add(listener);
	}

	public void removeFilamentDatabaseChangesListener(FilamentDatabaseChangesListener listener) {
		filamentDatabaseChangesListeners.remove(listener);
	}

	private void notifyFilamentDatabaseChangesListeners(String filamentId) {
		for (FilamentDatabaseChangesListener listener : filamentDatabaseChangesListeners) {
			listener.whenFilamentChanges(filamentId);
		}
	}

	public Path constructFilePath(Filament filament) {
		String fileName = FileUtilities.cleanFileName(filament.getFriendlyFilamentName() + "-" + filament.getMaterial().getFriendlyName()) + BaseConfiguration.filamentFileExtension;

		return filamentsPathPreference.getUserValue().resolve(fileName);
	}

	private void loadFilamentData() {
		completeFilamentMapByID.clear();
		completeFilamentNameByID.clear();
		completeFilamentList.clear();
		appFilamentList.clear();
		userFilamentList.clear();

		List<Filament> filaments = null;

		File applicationFilamentDirHandle = filamentsPathPreference.getAppValue().toFile();
		File[] applicationfilaments = applicationFilamentDirHandle.listFiles(new FilamentFileFilter());

		if (applicationfilaments != null) {
			filaments = ingestFilaments(applicationfilaments, false);
			filaments.sort(Filament.BY_MATERIAL.thenComparing(Filament::compareByFilamentID));
			appFilamentList.addAll(filaments);
			completeFilamentList.addAll(filaments);
		}
		else {
			LOGGER.error("No application filaments found: " + applicationFilamentDirHandle.getAbsolutePath());
		}

		File userFilamentDirHandle = filamentsPathPreference.getUserValue().toFile();
		File[] userfilaments = userFilamentDirHandle.listFiles(new FilamentFileFilter());
		if (userfilaments != null) {
			filaments = ingestFilaments(userfilaments, true);
			filaments.sort(Filament.BY_MATERIAL.thenComparing(Filament::compareByFilamentID));
			for (Filament filament : filaments) {
				filament.setBrand(CUSTOM_BRAND);
				filament.setCategory(CUSTOM_CATEGORY);
			}
			completeFilamentList.addAll(filaments);
			userFilamentList.addAll(filaments);
		}
		else {
			LOGGER.info("No user filaments found: " + userFilamentDirHandle.getAbsolutePath());
		}
	}

	private ArrayList<Filament> ingestFilaments(File[] filamentFiles, boolean filamentsAreMutable) {
		ArrayList<Filament> filamentList = new ArrayList<>();

		int filamentCounter = 0;

		for (File filamentFile : filamentFiles) {
			filamentCounter++;
			if (LOGGER.isTraceEnabled())
				LOGGER.trace("Loading filament " + filamentFile.getAbsolutePath());

			try {
				Properties filamentProperties = new Properties();
				try (FileInputStream fileInputStream = new FileInputStream(filamentFile)) {
					filamentProperties.load(fileInputStream);

					String name = filamentProperties.getProperty(NAME).trim();

					String filamentID = filamentProperties.getProperty(REEL_ID).trim();
					String brand = filamentProperties.getProperty(BRAND);
					if (brand != null) {
						brand = brand.trim();
					}
					else {
						brand = "";
					}
					String category = filamentProperties.getProperty(CATEGORY);
					if (category != null) {
						category = category.trim();
					}
					else {
						category = "";
					}
					String material = filamentProperties.getProperty(MATERIAL).trim();
					String diameterString = filamentProperties.getProperty(DIAMETER).trim();
					String filamentMultiplierString = filamentProperties.getProperty(
							FILAMENT_MULTIPLIER).trim();
					String feedRateMultiplierString = filamentProperties.getProperty(
							FEED_RATE_MULTIPLIER).trim();
					String ambientTempString = filamentProperties.getProperty(AMBIENT_TEMPERATURE_C).trim();
					String firstLayerBedTempString = filamentProperties.getProperty(
							FIRST_LAYER_BED_TEMPERATURE_C).trim();
					String bedTempString = filamentProperties.getProperty(BED_TEMPERATURE_C).trim();
					String firstLayerNozzleTempString = filamentProperties.getProperty(
							FIRST_LAYER_NOZZLE_TERMERATURE_C).trim();
					String nozzleTempString = filamentProperties.getProperty(NOZZLE_REMPERATURE_C).trim();
					String displayColourString = filamentProperties.getProperty(
							DISPLAY_COLOUR).trim();
					// introduced in 1.01.05
					String costGBPPerKGString = "40";
					try {
						costGBPPerKGString = filamentProperties.getProperty(COST_GBP_PER_KG).trim();
					}
					catch (Exception ex) {
						LOGGER.debug("No cost per GBP found in filament file " + filamentFile.getAbsolutePath());
					}

					// introduced in 2.01.03
					String defaultLengthString = "240";
					try {
						defaultLengthString = filamentProperties.getProperty(DEFAULT_LENGTH_M).trim();
					}
					catch (Exception ex) {
						LOGGER.debug("No default length found in filament file " + filamentFile.getAbsolutePath());
					}

					String filledString = "No";
					try {
						filledString = filamentProperties.getProperty(FILLED).trim();
					}
					catch (Exception ex) {
						LOGGER.debug("No 'filled' property found in filament file");
					}

					if (name != null
							&& material != null
							&& filamentID != null
							&& diameterString != null
							&& feedRateMultiplierString != null
							&& filamentMultiplierString != null
							&& ambientTempString != null
							&& firstLayerBedTempString != null
							&& bedTempString != null
							&& firstLayerNozzleTempString != null
							&& nozzleTempString != null
							&& displayColourString != null) {
						MaterialType selectedMaterial;
						try {
							selectedMaterial = MaterialType.valueOf(material);
						}
						catch (IllegalArgumentException ex) {
							// Default material to 'Special'.
							selectedMaterial = MaterialType.SPC;
							LOGGER.warn("Using material SPC as material type "
									+ material
									+ " not recognised in filament file "
									+ filamentFile.getAbsolutePath());
						}

						try {
							float diameter = Float.valueOf(diameterString);
							float filamentMultiplier = Float.valueOf(filamentMultiplierString);
							float feedRateMultiplier = Float.valueOf(feedRateMultiplierString);
							int ambientTemp = Integer.valueOf(ambientTempString);
							int firstLayerBedTemp = Integer.valueOf(firstLayerBedTempString);
							int bedTemp = Integer.valueOf(bedTempString);
							int firstLayerNozzleTemp = Integer.valueOf(firstLayerNozzleTempString);
							int nozzleTemp = Integer.valueOf(nozzleTempString);
							Color colour = Color.web(displayColourString);
							float costGBPPerKG = Float.valueOf(costGBPPerKGString);
							int defaultLength_m = Integer.valueOf(defaultLengthString);
							boolean filled = filledString.equalsIgnoreCase("yes");

							Filament newFilament = new Filament(
									name,
									selectedMaterial,
									filamentID,
									brand,
									category,
									diameter,
									filamentMultiplier,
									feedRateMultiplier,
									ambientTemp,
									firstLayerBedTemp,
									bedTemp,
									firstLayerNozzleTemp,
									nozzleTemp,
									colour,
									costGBPPerKG,
									defaultLength_m,
									filled,
									filamentsAreMutable);

							filamentList.add(newFilament);

							completeFilamentMapByID.put(filamentID, newFilament);
							completeFilamentNameByID.put(filamentID, name);

						}
						catch (IllegalArgumentException ex) {
							LOGGER.error("Failed to parse filament file "
									+ filamentFile.getAbsolutePath());
						}
					}
				}
			}
			catch (Exception ex) {
				ex.printStackTrace();
				LOGGER.error("Error loading filament " + filamentFile.getAbsolutePath() + " " + ex);
			}
		}

		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Loaded " + filamentCounter + " filaments.");

		return filamentList;
	}

	/**
	 * Suggest a safe name for a new filament name based on the proposed name.
	 */
	public String suggestNonDuplicateName(String proposedName) {
		List<String> currentFilamentNames = new ArrayList<>();
		completeFilamentList.stream().forEach((filament) -> {
			currentFilamentNames.add(filament.getFriendlyFilamentName());
		});
		return DeDuplicator.suggestNonDuplicateNameCopy(proposedName, currentFilamentNames);
	}

	/**
	 * Save the given filament to file, using the friendly name and material type as file name. If a filament already exists of the same filamentID but different file name then delete that file.
	 */
	public void saveFilament(Filament filament) {
		Map<InetAddress, PrintServerConnection> serversToPushTo = connectedServersPreference.getValue();

		for (PrintServerConnection server : serversToPushTo.values()) {
			server.saveFilament(filament);
		}

		if (!completeFilamentMapByID.containsKey(filament.getFilamentID())) {
			addNewFilament(filament);
		}
		else {
			Filament currentFilamentOfThisID = getFilamentByID(filament.getFilamentID());
			String originalFriendlyNameForFilament = completeFilamentNameByID.get(filament.getFilamentID());
			if (!originalFriendlyNameForFilament.equals(filament.getFriendlyFilamentName())) {
				deleteFilamentUsingOldName(currentFilamentOfThisID);
				addNewFilament(filament);
			}
			else {
				saveEditedUserFilament(filament);
			}
		}
		notifyFilamentDatabaseChangesListeners(filament.getFilamentID());
	}

	private void addNewFilament(Filament filament) {
		saveEditedUserFilament(filament);
		userFilamentList.add(filament);
		completeFilamentList.add(filament);
		completeFilamentMapByID.put(filament.getFilamentID(), filament);
		completeFilamentNameByID.put(filament.getFilamentID(), filament.getFriendlyFilamentName());
	}

	private void saveEditedUserFilament(Filament filament) {
		NumberFormat floatConverter = DecimalFormat.getNumberInstance(Locale.UK);
		floatConverter.setMinimumFractionDigits(3);
		floatConverter.setGroupingUsed(false);

		try {
			Properties filamentProperties = new Properties();

			filamentProperties.setProperty(NAME, filament.getFriendlyFilamentName());
			filamentProperties.setProperty(MATERIAL, filament.getMaterial().name());
			filamentProperties.setProperty(REEL_ID, filament.getFilamentID());
			filamentProperties.setProperty(BRAND, filament.getBrand());
			filamentProperties.setProperty(CATEGORY, filament.getCategory());
			filamentProperties.setProperty(COST_GBP_PER_KG, floatConverter.format(
					filament.getCostGBPPerKG()));
			filamentProperties.setProperty(DIAMETER, floatConverter.format(
					filament.getDiameter()));
			filamentProperties.setProperty(FILAMENT_MULTIPLIER, floatConverter.format(
					filament.getFilamentMultiplier()));
			filamentProperties.setProperty(FEED_RATE_MULTIPLIER, floatConverter.format(
					filament.getFeedRateMultiplier()));
			filamentProperties.setProperty(AMBIENT_TEMPERATURE_C, String.valueOf(
					filament.getAmbientTemperature()));
			filamentProperties.setProperty(FIRST_LAYER_BED_TEMPERATURE_C, String.valueOf(
					filament.getFirstLayerBedTemperature()));
			filamentProperties.setProperty(BED_TEMPERATURE_C, String.valueOf(
					filament.getBedTemperature()));
			filamentProperties.setProperty(FIRST_LAYER_NOZZLE_TERMERATURE_C, String.valueOf(
					filament.getFirstLayerNozzleTemperature()));
			filamentProperties.setProperty(NOZZLE_REMPERATURE_C, String.valueOf(
					filament.getNozzleTemperature()));
			filamentProperties.setProperty(FILLED, (filament.isFilled() ? "Yes" : "No"));

			String webColour = String.format("#%02X%02X%02X",
					(int) (filament.getDisplayColour().getRed() * 255),
					(int) (filament.getDisplayColour().getGreen() * 255),
					(int) (filament.getDisplayColour().getBlue() * 255));
			filamentProperties.setProperty(DISPLAY_COLOUR, webColour);

			File filamentFile = constructFilePath(filament).toFile();
			try (FileOutputStream fileOutputStream = new FileOutputStream(filamentFile)) {
				filamentProperties.store(fileOutputStream, "Robox data");
			}

		}
		catch (IOException ex) {
			LOGGER.error("Error whilst storing filament file " + filament.getFileName() + " " + ex);
		}
	}

	public void deleteFilament(Filament filament) {
		assert (filament.isMutable());

		Map<InetAddress, PrintServerConnection> serversToPushTo = connectedServersPreference.getValue();

		for (PrintServerConnection server : serversToPushTo.values()) {
			server.deleteFilament(filament);
		}

		try {
			Files.delete(constructFilePath(filament));
			userFilamentList.remove(filament);
			completeFilamentList.remove(filament);
			completeFilamentMapByID.remove(filament.getFilamentID());
			completeFilamentNameByID.remove(filament.getFilamentID());
		}
		catch (IOException ex) {
			LOGGER.error("Error deleting filament: " + constructFilePath(filament));
		}
		notifyFilamentDatabaseChangesListeners(filament.getFilamentID());
	}

	private void deleteFilamentUsingOldName(Filament filament) {
		assert (filament.isMutable());
		String oldName = completeFilamentNameByID.get(filament.getFilamentID()) + "-" + filament.getMaterial().getFriendlyName() + BaseConfiguration.filamentFileExtension;

		Path oldFilePath = filamentsPathPreference.getUserValue().resolve(oldName);

		try {
			Files.delete(oldFilePath);
			userFilamentList.remove(filament);
			completeFilamentList.remove(filament);
			completeFilamentMapByID.remove(filament.getFilamentID());
			completeFilamentNameByID.remove(filament.getFilamentID());
		}
		catch (IOException ex) {
			LOGGER.error("Error deleting filament: " + oldFilePath.toString());
		}
		notifyFilamentDatabaseChangesListeners(filament.getFilamentID());
	}

	public boolean isFilamentIDValid(String filamentID) {
		boolean filamentIDIsValid = false;

		if (filamentID != null
				&& (filamentID.matches("RBX-[0-9A-Z]{3}-.*")
						|| filamentID.matches("^U.*"))) {
			filamentIDIsValid = true;
		}

		return filamentIDIsValid;
	}

	public boolean isFilamentIDInDatabase(String filamentID) {
		boolean filamentIDIsInDatabase = false;

		if (filamentID != null
				&& getFilamentByID(filamentID) != null) {
			filamentIDIsInDatabase = true;
		}

		return filamentIDIsInDatabase;
	}

	public Filament getFilamentByID(String filamentID) {
		Filament returnedFilament = null;

		if (filamentID != null) {
			returnedFilament = completeFilamentMapByID.get(filamentID);
			if (returnedFilament == null) {
				//Try replacing dashes with underscores...
				returnedFilament = completeFilamentMapByID.get(filamentID.replaceAll("-", "_"));
			}
		}
		return returnedFilament;
	}

	/**
	 * Add the filament to the user filament list but do not save it to disk.
	 */
	public void addFilamentToUserFilamentList(Filament filament) {
		userFilamentList.add(filament);
		completeFilamentList.add(filament);
		completeFilamentMapByID.put(filament.getFilamentID(), filament);
		completeFilamentNameByID.put(filament.getFilamentID(), filament.getFriendlyFilamentName());
	}

	public ObservableList<Filament> getCompleteFilamentList() {
		return completeFilamentList;
	}

	public ObservableList<Filament> getUserFilamentList() {
		return userFilamentList;
	}

	public ObservableList<Filament> getAppFilamentList() {
		return appFilamentList;
	}

	/**
	 * For testing only.
	 */
	protected void reload() {
		loadFilamentData();
	}
}
