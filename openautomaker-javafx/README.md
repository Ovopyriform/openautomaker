# openautomaker-javafx

JavaFX integration utilities for OpenAutoMaker. Primarily provides bidirectional binding between the preference system and JavaFX observable properties.

## Responsibilities

- Binding `APreference` values to JavaFX `BooleanProperty`, `FloatProperty`, etc.
- Guice module for JavaFX-specific bindings

## Key Classes

| Class | Purpose |
|---|---|
| `FXProperty` | Adapts typed preferences to JavaFX observable properties with bidirectional sync |
| `JavaFXModule` | Guice module exposing JavaFX-aware bindings |

## Dependencies

- openautomaker-environment
- JavaFX 26
- Google Guice 7 / Gluon Ignite
- semver4j
