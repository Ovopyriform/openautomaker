# openautomaker-environment

Application preferences, runtime properties, and OS environment configuration for OpenAutoMaker.

## Responsibilities

- Load and expose `openautomaker.properties` at runtime
- Typed preference system organized by domain
- OS-specific logging configuration (macOS, Windows, Linux Maven profiles)
- Guice bindings for all environment-level services

## Preference Domains

| Package | Covers |
|---|---|
| `application` | App-level settings, home path |
| `advanced` | Advanced user settings |
| `camera` | Camera device configuration |
| `l10n` | Locale/language selection |
| `modeling` | Project file paths |
| `paths` | General filesystem paths |
| `printer` | Printer connection settings |
| `product` | Product-specific configuration |
| `project` | Per-project preferences |
| `root` | Root server settings |
| `slicer` | CuraEngine path and configuration |
| `virtual_printer` | Virtual printer settings |

## Key Classes

| Class | Purpose |
|---|---|
| `ApplicationProperties` | Loads `openautomaker.properties` from the runtime environment |
| `APreference` | Abstract base for all typed preferences |
| `ASimpleBooleanPreference` | Convenience base for boolean preferences |
| `EnvironmentModule` | Guice module wiring all preferences |

## Dependencies

- openautomaker-guice
- openautomaker-i18n
- Google Guice 7
- JavaFX
- Log4j2
- semver4j
