# openautomaker-project

Project file format and 3D model import library for OpenAutoMaker. Defines the public project API and handles serialization to/from the RBXPROJ archive format.

## Responsibilities

- Public API for projects, models, and print settings
- RBXPROJ file format: ZIP-based archive containing model binaries and JSON metadata
- 3D model import: STL, OBJ, and SVG (extruded to 3D)
- G-Code staleness detection
- Recently-opened project preference tracking

## Key Packages

| Package | Purpose |
|---|---|
| `org.openautomaker.project.api` | Public interfaces: `IProject`, `IProjectModel`, `IProjectSettings`, `IProjectFactory`, `IProjectReader`, `IModelLoader` |
| `org.openautomaker.project.rbxproj` | RBXPROJ archive read/write: `RbxprojFile`, `RbxprojReader`, `RbxprojWriter` |
| `org.openautomaker.project.rbxproj.data` | Serialized data structures: `ProjectMetadata`, `ModelsManifest`, `ModelTransformData`, `PlacementsData`, `PrintSettingsData`, `GcodeSettingsData` |
| `org.openautomaker.project.importer` | Model importers: `StlImporter`, `ObjImporter`, `SvgImporter`; `RawMeshData` intermediate representation |

## RBXPROJ Format

RBXPROJ is a ZIP archive containing:
- `metadata.json` — project name, version, timestamp
- `models/` — raw model binary files
- `manifest.json` — model inventory and transform data
- `placements.json` — model positions on the print bed
- `settings.json` — print and G-Code settings

## Dependencies

- openautomaker-environment
- Jackson 2.21 (JSON serialization)
- Apache Batik (SVG parsing for `SvgImporter`)
- Google Guice 7
- Log4j2
