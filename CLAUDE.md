# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

OpenAutoMaker is a Java/JavaFX desktop application for controlling Robox 3D printers. It is a modernized rebuild of the legacy AutoMaker software with current Java/Maven standards. The app handles serial communication with printers, GCode generation, 3D model visualization, and slicer integration (Cura 5).

## Build Commands

All builds are run from `openautomaker-parent/`:

```bash
cd openautomaker-parent

mvn clean install        # Full build
mvn clean compile        # Compile only
mvn test                 # Run all tests
mvn -pl openautomaker-base test -Dtest=SomeTest  # Run a single test class
mvn javafx:run           # Run the application
mvn javafx:run@debug     # Run with remote debug on port 8001
```

Requirements: Apache Maven 3.9.0+, JDK 25. No Maven wrapper exists.

Tests run sequentially (`forkCount=1`) with headless JavaFX (`prism.order=sw`, `glass.platform=headless`) to avoid threading conflicts. FXML resources are copied during `process-classes`.

MacOS profile builds a `.dmg` installer; Windows/Linux profiles handle platform-specific Cura slicer detection.

## Module Structure

The parent POM is `openautomaker-parent/pom.xml`. Modules in dependency order:

| Module | Purpose |
|---|---|
| `openautomaker-guice` | Guice DI base utilities |
| `openautomaker-i18n` | Internationalization framework |
| `openautomaker-environment` | App preferences, logging (Log4j2), OS environment |
| `openautomaker-javafx` | Shared JavaFX utilities and custom controls |
| `openautomaker-test-library` | Shared test fixtures and helpers |
| `openautomaker-test-environment` | OS-specific test runtime resources |
| `openautomaker-base` | Core domain: printer control, serial comms, slicing, model importers, crypto |
| `openautomaker-core` | JavaFX UI components, scene/state management, dialogs, visualisation |
| `openautomaker-discovery` | Hardware scanner / device enumeration |
| `openautomaker` | Entry point, application lifecycle, window management |

Separate standalone apps (`openautomaker-gcodeviewer`, `openautomaker-root`, `openautomaker-root-ui`) are commented out of the parent POM and not built by default.

## Architecture

**Dependency flow:**
```
openautomaker (main app)
  └─ openautomaker-core (UI + state)
  └─ openautomaker-base (domain logic)
  └─ openautomaker-discovery
      └─ openautomaker-environment, openautomaker-javafx, openautomaker-guice, openautomaker-i18n
```

**Entry point:** `openautomaker/src/main/java/org/openautomaker/Main.java` — a thin stub required for JavaFX 11+ module system. It sets `OpenAutomakerPreloader` and launches `OpenAutomaker extends Application`.

`OpenAutomaker.java` wires Guice modules, starts `RoboxCommsManager` (serial/device), `LocalWebInterface` (embedded web server), `TaskExecutor` (async tasks), `PrinterManager`, `DisplayManager`, and listens on `localhost:4444` for inter-app commands via the `InterAppCommsConsumer` protocol.

**Packages:**
- `org.openautomaker.ui.*` — JavaFX UI components, controllers, dialogs
- `org.openautomaker.base.*` — domain logic (printers, slicing, importers, cameras, crypto, filament)
- `celtech.*` — legacy inherited code from original AutoMaker (RoboxCommsManager, interapp comms, web server, 3D visualisation); being incrementally migrated to `org.openautomaker.*`

**DI:** Guice with `BaseModule`, `EnvironmentModule`, `JavaFXModule`, `UIModule`. Prefer constructor injection.

**Async:** Background work uses `TaskExecutor` / `TaskResponse`. Do not block the JavaFX Application Thread.

## Key Technology

- **Java 25**, **JavaFX 26** (openjfx)
- **Guice 7** — dependency injection
- **Jackson 2.21** — JSON/XML serialization
- **Log4j2 2.25** — logging
- **JSerialComm 2.4** — serial port (printer hardware)
- **ControlsFX**, **JMetro**, **JFXtras** — extended JavaFX controls/theme
- **Batik 1.16** — SVG rendering
- **JUnit 5 (Jupiter)**, **TestFX 4**, **Mockito 5**, **AssertJ** — testing
- **Lombok** — annotation processor (check for `@Data`, `@Builder`, etc.)

## Legacy Code Notes

The `celtech.*` package namespace is legacy. New code goes under `org.openautomaker.*`. When editing files in `celtech.*`, be aware they may have implicit dependencies on static state or singletons that predate Guice integration. The `Savable` interface was recently deleted (visible in current git status) as part of this migration.
