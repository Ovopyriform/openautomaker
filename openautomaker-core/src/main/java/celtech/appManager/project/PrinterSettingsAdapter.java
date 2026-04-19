package celtech.appManager.project;

import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.fileRepresentation.PrinterSettingsOverrides;
import org.openautomaker.project.api.IProjectSettings;

import celtech.appManager.ModelContainerProject;

class PrinterSettingsAdapter implements IProjectSettings {

	private final ModelContainerProject project;
	private final PrinterSettingsOverrides settings;

	PrinterSettingsAdapter(ModelContainerProject project) {
		this.project = project;
		this.settings = project.getPrinterSettings();
	}

	@Override
	public String getExtruder0FilamentID() {
		Filament f = project.getExtruder0FilamentProperty().get();
		return f != null ? f.getFilamentID() : "NULL";
	}

	@Override
	public String getExtruder1FilamentID() {
		Filament f = project.getExtruder1FilamentProperty().get();
		return f != null ? f.getFilamentID() : "NULL";
	}

	@Override
	public String getSettingsName() {
		return settings.getSettingsName();
	}

	@Override
	public String getPrintQuality() {
		return settings.getPrintQuality().name();
	}

	@Override
	public int getBrimOverride() {
		return settings.getBrimOverride();
	}

	@Override
	public float getFillDensityOverride() {
		return settings.getFillDensityOverride();
	}

	@Override
	public boolean isFillDensityOverridenByUser() {
		return settings.isFillDensityChangedByUser();
	}

	@Override
	public boolean isPrintSupportOverride() {
		return settings.getPrintSupportOverride();
	}

	@Override
	public String getPrintSupportTypeOverride() {
		return settings.getPrintSupportTypeOverride().name();
	}

	@Override
	public boolean isPrintRaft() {
		return settings.getRaftOverride();
	}

	@Override
	public boolean isSpiralPrint() {
		return settings.getSpiralPrintOverride();
	}
}
