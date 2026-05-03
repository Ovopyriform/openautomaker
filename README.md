# OpenAutoMaker

Java/JavaFX desktop application for controlling Robox 3D printers. A modernized rebuild of the legacy AutoMaker software targeting current Java/Maven standards.

## Features

- Serial and network communication with Robox printers and Root servers
- 3D model loading, visualization, and placement (STL, OBJ, SVG)
- Slicer integration: Cura 5
- Project file management (RBXPROJ archive format)
- G-Code preview and post-processing
- Multi-platform: macOS, Windows, Linux

## Prerequisites

- JDK 25
- Apache Maven 3.9.0+

## Building

All builds run from `openautomaker-parent/`:

```bash
git clone https://github.com/ovopyriform/OpenAutoMaker.git
cd OpenAutoMaker/openautomaker-parent

mvn clean install       # Full build (produces platform installer)
mvn clean compile       # Compile only
mvn test                # Run all tests
mvn javafx:run          # Run the application
mvn javafx:run@debug    # Run with remote debugger on port 8001
```

On macOS the build produces a `.dmg` installer. Windows and Linux builds produce platform-appropriate packages.

## Module Structure

| Module | Purpose |
|---|---|
| `openautomaker-parent` | Maven parent POM: dependency versions, build plugins, OS profiles |
| `openautomaker-guice` | Guice DI integration with JavaFX FXML loaders |
| `openautomaker-i18n` | Internationalization and locale management |
| `openautomaker-environment` | Application preferences, properties, and OS environment |
| `openautomaker-javafx` | JavaFX utilities and preference↔property bindings |
| `openautomaker-test-library` | Shared JUnit 5 + Guice test utilities |
| `openautomaker-test-environment` | OS-specific runtime resources for tests |
| `openautomaker-project` | Project file format (RBXPROJ) and 3D model importers |
| `openautomaker-base` | Core domain: printer comms, serial I/O, configuration, slicing |
| `openautomaker-core` | JavaFX UI components, 3D visualization, project management |
| `openautomaker-discovery` | Hardware scanner and device enumeration |
| `openautomaker` | Application entry point, window management, platform integration |

## Architecture

```
openautomaker (entry point)
  ├── openautomaker-core     (UI + 3D + project state)
  ├── openautomaker-base     (printer comms + domain logic)
  ├── openautomaker-project  (file format + model import)
  └── openautomaker-discovery
        └── openautomaker-environment
              └── openautomaker-javafx
                    └── openautomaker-guice
                          └── openautomaker-i18n
```

## Key Technologies

| Concern | Library |
|---|---|
| UI | JavaFX 26, ControlsFX, JMetro, JFXtras |
| DI | Google Guice 7 |
| Serial comms | JSerialComm 2.4 |
| JSON/XML | Jackson 2.21 |
| Logging | Log4j2 2.25 |
| SVG | Apache Batik 1.16 |
| Testing | JUnit 5, Mockito 5, AssertJ, TestFX 4 |

## License

See [LICENSE](LICENSE).
