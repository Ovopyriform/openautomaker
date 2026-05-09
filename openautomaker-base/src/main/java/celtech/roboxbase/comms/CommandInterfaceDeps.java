package celtech.roboxbase.comms;

import org.openautomaker.base.configuration.datafileaccessors.PrinterContainer;
import org.openautomaker.base.notification_manager.SystemNotificationManager;
import org.openautomaker.base.services.firmware.FirmwareLoadService;
import org.openautomaker.environment.OpenAutomakerEnv;
import org.openautomaker.environment.preference.application.RequiredFirmwareVersionPreference;
import org.openautomaker.environment.preference.printer.LastFirmwareVersionPreference;
import org.openautomaker.environment.preference.printer.LastSerialNumberPreference;
import org.openautomaker.environment.preference.root.FirmwarePathPreference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Groups the 8 Guice-injected dependencies shared by all {@link CommandInterface} subclasses,
 * reducing constructor parameter count from 13 to 6.
 */
@Singleton
public record CommandInterfaceDeps(
		OpenAutomakerEnv environment,
		SystemNotificationManager systemNotificationManager,
		RequiredFirmwareVersionPreference requiredFirmwareVersionPreference,
		FirmwarePathPreference firmwarePathPreference,
		LastSerialNumberPreference lastSerialNumberPreference,
		LastFirmwareVersionPreference lastFirmwareVersionPreference,
		FirmwareLoadService firmwareLoadService,
		PrinterContainer printerContainer)
{
	@Inject
	public CommandInterfaceDeps {}
}
