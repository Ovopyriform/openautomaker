# openautomaker-core

Main JavaFX UI layer for OpenAutoMaker. Provides the application screens, 3D model visualization, project management, and slicer configuration interface.

## Responsibilities

- Application screen and panel management
- 3D model rendering and manipulation (transform, placement, collision)
- Project lifecycle: load, save, undo/redo
- G-Code loading, parsing, and preview visualization
- Slicer profile configuration UI
- Printer state display and control panels
- Embedded web server integration

## Key Packages

| Package | Purpose |
|---|---|
| `celtech.appManager` | `ProjectManager` — project load/save, RBXPROJ/ROBOX file I/O, undo/redo |
| `celtech.coreUI.visualisation` | 3D rendering engine, model display, printer bed, collision detection |
| `celtech.coreUI.visualisation.modelDisplay` | 3D model scene graph management |
| `celtech.coreUI.visualisation.metaparts` | Printer bed, nozzle, and camera 3D representations |
| `celtech.modelcontrol` | Model transform and placement logic |
| `celtech.services.modelLoader` | Background 3D model loading |
| `celtech.services.gcodepreview` | Real-time G-Code path visualization |
| `celtech.utils.gcode` | G-Code parsing and representation |
| `celtech.utils.settingsgeneration` | Slicer profile generation |
| `celtech.configuration` | `ApplicationConfiguration`, unit conversion |
| `org.openautomaker.ui` | `StageManager`, `ProjectGUIState`, `ProjectGUIRules`, `StandardColours` |
| `org.openautomaker.ui.component.*` | Migrated UI components (inset panels, controls, menus) |

## Key Classes

| Class | Purpose |
|---|---|
| `ProjectManager` | Central project state — owns the loaded models and print settings |
| `StageManager` | JavaFX window and scene lifecycle |
| `ProjectGUIState` | UI state machine driving panel visibility |
| `ApplicationConfiguration` | Global configuration paths and resource locations |

## Dependencies

- openautomaker-base
- openautomaker-project
- openautomaker-environment
- openautomaker-javafx
- openautomaker-guice
- JavaFX 26 (controls, FXML, web)
- ControlsFX, JFXtras, JMetro
- Apache Batik (SVG rendering)
- poly2tri-core (polygon triangulation)
- Apache Commons (math3, compress, text)
- Jackson (XML config)
- Woodstox (XML parsing)
- junrar (RAR archive support)
- Log4j2
