# openautomaker

Application entry point for OpenAutoMaker. Wires all modules together, manages the application lifecycle, and handles platform-specific integration.

## Responsibilities

- JavaFX `Application` subclass and preloader (splash screen)
- Guice module composition for the full application
- Startup sequence: Guice wiring → `RoboxCommsManager` → `LocalWebInterface` → `TaskExecutor` → `PrinterManager` → `DisplayManager`
- Inter-application command listener on `localhost:4444`
- macOS native menu integration (NSMenuFX)
- Platform-specific packaging (macOS `.dmg`, Windows installer, Linux packages)

## Key Classes

| Class | Purpose |
|---|---|
| `Main` | Thin launcher stub required for JavaFX 11+ module system |
| `OpenAutomaker` | Main `Application` subclass — wires Guice and starts all subsystems |
| `OpenAutomakerPreloader` | Splash screen displayed during startup |
| `OpenAutomakerModule` | Guice bindings: `SystemNotificationManager`, `InterAppRequest` |
| `InterAppRequest` | Protocol for inter-application commands on port 4444 |

## Building

```bash
cd openautomaker-parent
mvn clean install        # Full build with platform installer
mvn javafx:run           # Run directly (development)
mvn javafx:run@debug     # Run with remote debugger on port 8001
```

## Dependencies

- openautomaker-core
- openautomaker-base
- openautomaker-environment
- openautomaker-javafx
- openautomaker-i18n
- NSMenuFX 3.1.0 (macOS native menu bar)
- ControlsFX
- JavaFX 26 (controls, FXML)
- Log4j2
