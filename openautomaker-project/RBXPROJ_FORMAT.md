# `.rbxproj` — OpenAutoMaker Project File Format

## Purpose

The `.rbxproj` format is the portable, self-contained project file type for OpenAutoMaker. It replaces (and coexists alongside) the older two-file `.robox` + `.models` approach.

**Goals:**

- Single file — easy to share, copy, and archive
- Original model files preserved verbatim inside the archive — no lossy re-serialisation
- Human-readable JSON metadata — project settings and placement data are inspectable without tooling
- Version-tolerant — all JSON structures carry an explicit `version` field for future migration
- Format-agnostic model storage — STL, OBJ, and future formats all stored as-is

---

## Archive Structure

A `.rbxproj` file is a standard ZIP archive with the following layout:

```
MyProject.rbxproj   (ZIP)
├── project.json
├── placements.json
├── models/
│   ├── manifest.json
│   ├── a1b2c3d4-part-a.stl
│   ├── e5f6g7h8-bracket.obj
│   └── i9j0k1l2-base.stl
└── gcode/
    ├── RBX01/
    │   ├── output.gcode
    │   └── settings.json
    └── RBX02/
        ├── output.gcode
        └── settings.json
```

Model filenames inside `models/` are prefixed with an 8-character hex UUID fragment to avoid
collisions when multiple imported files share the same name (e.g. two separate `part.stl` files).
`models/manifest.json` maps each stored filename back to the original and records the content hash
used for deduplication.

Each printer that has sliced GCode for this project gets its own subfolder under `gcode/`, named
by the printer's ID. A project may contain GCode for multiple printers simultaneously.

---

## `project.json`

Stores project identity and print settings.

### Schema

```json
{
  "version": 1,
  "projectName": "MyProject",
  "lastModified": "2026-04-19T10:00:00Z",
  "printSettings": {
    "extruder0FilamentID": "RBXFF-000A",
    "extruder1FilamentID": "RBXFF-001B",
    "settingsName": "Draft",
    "printQuality": "NORMAL",
    "brimOverride": 0,
    "fillDensityOverride": 0.2,
    "fillDensityOverridenByUser": false,
    "printSupportOverride": false,
    "printSupportTypeOverride": "MATERIAL_2",
    "printRaft": false,
    "spiralPrint": false
  }
}
```

### Fields

| Field | Type | Description |
|---|---|---|
| `version` | int | Format version. Current: `1` |
| `projectName` | string | Display name of the project |
| `lastModified` | string | ISO-8601 UTC timestamp of last save |
| `printSettings.extruder0FilamentID` | string | Filament ID for extruder 0 |
| `printSettings.extruder1FilamentID` | string | Filament ID for extruder 1 |
| `printSettings.settingsName` | string | Name of the active print profile |
| `printSettings.printQuality` | string | `DRAFT`, `NORMAL`, `FINE`, or `CUSTOM` |
| `printSettings.brimOverride` | int | Brim line count (0 = off) |
| `printSettings.fillDensityOverride` | float | Fill density 0.0–1.0 |
| `printSettings.fillDensityOverridenByUser` | bool | Whether user explicitly set fill density |
| `printSettings.printSupportOverride` | bool | Enable support structures |
| `printSettings.printSupportTypeOverride` | string | Support material: `MATERIAL_1` or `MATERIAL_2` |
| `printSettings.printRaft` | bool | Enable raft |
| `printSettings.spiralPrint` | bool | Enable spiral/vase mode |

---

## `placements.json`

Describes where each model sits on the print bed and how models are grouped.

### Schema

```json
{
  "version": 1,
  "placements": [
    {
      "modelId": 1,
      "modelFile": "models/a1b2c3d4-part-a.stl",
      "modelName": "Part A",
      "extruder": 0,
      "transform": {
        "x": 50.0,
        "y": 0.0,
        "z": 30.0,
        "xScale": 1.0,
        "yScale": 1.0,
        "zScale": 1.0,
        "rotationTurn": 0.0,
        "rotationLean": 0.0,
        "rotationTwist": 0.0
      }
    }
  ],
  "groups": {
    "5": [1, 2]
  },
  "groupTransforms": {
    "5": {
      "x": 0.0,
      "y": 0.0,
      "z": 0.0,
      "xScale": 1.0,
      "yScale": 1.0,
      "zScale": 1.0,
      "rotationTurn": 0.0,
      "rotationLean": 0.0,
      "rotationTwist": 0.0
    }
  }
}
```

### Top-level fields

| Field | Type | Description |
|---|---|---|
| `version` | int | Format version. Current: `1` |
| `placements` | array | One entry per leaf model (mesh-holding containers only) |
| `groups` | object | Maps group `modelId` → array of child `modelId`s |
| `groupTransforms` | object | Maps group `modelId` → transform for that group container |

### Placement fields

| Field | Type | Description |
|---|---|---|
| `modelId` | int | Unique integer ID for this model within the project |
| `modelFile` | string | Path within the ZIP to the model file, e.g. `models/a1b2c3d4-part.stl` |
| `modelName` | string | Display name shown in the UI |
| `extruder` | int | Extruder assignment: `0` or `1` |
| `transform` | object | See transform fields below |

### Transform fields

All transforms correspond directly to the internal `ThreeDItemState` of a `ModelContainer`.
Positions are in bed coordinates (millimetres). Rotations are in degrees.

| Field | Type | Description |
|---|---|---|
| `x` | double | Position along the X axis (bed width) |
| `y` | double | Drop-to-bed Y adjustment |
| `z` | double | Position along the Z axis (bed depth) |
| `xScale` | double | Scale factor on X axis (1.0 = original size) |
| `yScale` | double | Scale factor on Y axis |
| `zScale` | double | Scale factor on Z axis |
| `rotationTurn` | double | Rotation around the Y axis ("turn"), degrees |
| `rotationLean` | double | Rotation around the X axis ("lean"), degrees |
| `rotationTwist` | double | Rotation around the Y axis post-lean ("twist"), degrees |

### Groups

Groups are stored as a flat map rather than a nested tree.

- `groups` maps each group's `modelId` to the set of its direct children's `modelIds`
- `groupTransforms` stores the transform for the group container itself (not its children)
- On load, groups are reconstructed bottom-up: leaf-only groups first, then groups of groups

---

## Model Storage

Each model file referenced in `placements.json` is stored verbatim in the `models/` directory of the ZIP. The original file format is preserved — STL files remain STL, OBJ files remain OBJ.

Filenames follow the pattern `<8-char-hex>-<original-filename>` where the hex prefix is derived
from a random UUID, ensuring uniqueness even when multiple models share the same original filename.

### Deduplication

Identical model files (same content) are stored only once regardless of how many placements
reference them. Equality is determined by SHA-256 content hash, not filename. This avoids bloating
the archive when the user adds the same STL multiple times or duplicates a model in the UI.

### `models/manifest.json`

Every stored model file has an entry in `models/manifest.json`:

```json
{
  "entries": [
    {
      "zipPath": "models/a1b2c3d4-part.stl",
      "originalName": "part.stl",
      "contentHash": "e3b0c44298fc1c149afb..."
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `zipPath` | string | Full path of the stored file within the ZIP |
| `originalName` | string | Filename as it appeared on disk before import |
| `contentHash` | string | Hex-encoded SHA-256 of the file content |

The manifest is the authoritative source of original filenames on load. Archives written before
the manifest was introduced are still supported — the reader falls back to stripping the UUID
prefix from the stored filename.

### Supported formats

| Extension | Importer |
|---|---|
| `.stl` | `STLImporter` (binary and ASCII) |
| `.obj` | `ObjImporter` (with material groups) |

---

## GCode Storage

Sliced GCode is stored inside the archive under `gcode/<printerId>/output.gcode`. Storing GCode
in the project file keeps the print-ready output together with its source data and settings.

### Directory structure

```
gcode/
  <printerTypeCode>/
    output.gcode
    settings.json
```

`<printerTypeCode>` is the printer model code (e.g. `RBX01`, `RBX02`, `RBX10`) — not a specific
printer's serial number. GCode is keyed by type because the same model file sliced for the same
printer type with the same settings produces identical output regardless of which individual unit
will print it. Characters outside `[a-zA-Z0-9._-]` are replaced with `_` to ensure a valid ZIP
entry path.

### `settings.json`

Stored alongside each `output.gcode`, this snapshot records the full settings used to generate the
GCode. It allows callers to detect stale GCode without re-slicing unnecessarily.

```json
{
  "printerTypeCode": "RBX01",
  "headType": "SINGLE_MATERIAL_HEAD",
  "printSettings": {
    "extruder0FilamentID": "RBXFF-000A",
    "extruder1FilamentID": "NULL",
    "settingsName": "Draft",
    "printQuality": "DRAFT",
    "brimOverride": 0,
    "fillDensityOverride": 0.15,
    "fillDensityOverridenByUser": false,
    "printSupportOverride": false,
    "printSupportTypeOverride": "AS_PROFILE",
    "printRaft": false,
    "spiralPrint": false
  }
}
```

| Field | Type | Description |
|---|---|---|
| `printerTypeCode` | string | Printer model code, e.g. `RBX01` |
| `headType` | string | Head type used for the slice profile |
| `printSettings` | object | Full print settings snapshot (same schema as `project.json` `printSettings`) |

### GCode lifecycle

GCode is **not** written during the initial project save — it is generated separately after slicing
and added to an existing archive via `RbxProjWriter.updateGcode()`. If GCode already exists for
that printer type it is replaced along with its settings snapshot. The rest of the archive is
preserved unchanged.

On load, callers should:
1. Call `RbxProjReader.listGcodeTargets()` to discover which printer types have cached GCode
2. Call `RbxProjReader.readGcodeSettings()` to load the settings snapshot
3. Compare the snapshot against the current project settings to decide whether to re-slice

---

## Java API

The module exposes three entry points:

### `RbxProjFile`

Constants and helpers for the format: file extension, ZIP entry names, version number, and
`gcodeEntry(printerId)` / `gcodePrinterDir(printerId)` for building printer-specific paths.

### `RbxProjWriter`

```java
@Inject RbxProjWriter writer;

// Initial save — writes project.json, placements.json, models/*
writer.write(project, Path.of("MyProject.rbxproj"));

// After slicing — embed GCode + settings snapshot for a printer type
GcodeSettingsData snapshot = new GcodeSettingsData("RBX01", "SINGLE_MATERIAL_HEAD", printSettings);
writer.updateGcode(Path.of("MyProject.rbxproj"), "RBX01", Path.of("output.gcode"), snapshot);
```

`write()` creates the archive from scratch. `updateGcode()` rewrites the archive in-place,
replacing any prior GCode for that printer while preserving all other entries.

### `RbxProjReader`

```java
@Inject RbxProjReader reader;

// Load project
IProject project = reader.read(Path.of("MyProject.rbxproj"));

// Discover which printer types have cached GCode
List<String> targets = reader.listGcodeTargets(Path.of("MyProject.rbxproj"));

// Check if cached GCode is stale before re-slicing
Optional<GcodeSettingsData> snapshot = reader.readGcodeSettings(Path.of("MyProject.rbxproj"), "RBX01");

// Extract GCode for a printer type to a temp file
Optional<Path> gcode = reader.readGcode(Path.of("MyProject.rbxproj"), "RBX01");
```

`read()` extracts model files to temp, imports them, applies transforms, reconstructs groups.
`readGcode()` extracts GCode for a printer type to a temp file and returns its path.
`readGcodeSettings()` returns the settings snapshot without extracting GCode — use to detect stale caches.
`listGcodeTargets()` returns printer type codes for all stored GCode without extracting anything.
All methods must be called from a background thread.

---

## Integration with the Existing Project System

The older `.robox` / `.models` format remains fully supported. `ProjectPersistance` (in
`openautomaker-core`) handles `.robox` files unchanged.

To add `.rbxproj` load support in the application, detect the file extension before delegating to
`ProjectPersistance`:

```java
// In ProjectManager or the file-open handler (openautomaker module):
if (filePath.toString().endsWith(RbxProjFile.EXTENSION)) {
    return rbxProjReader.read(filePath);
} else {
    return projectPersistance.loadProject(filePath);
}
```

This wiring belongs in `openautomaker` (the main app module) which depends on both
`openautomaker-core` and `openautomaker-project`, avoiding a circular dependency.
