# openautomaker-guice

Bridges Google Guice dependency injection with JavaFX FXML loading, enabling constructor injection in FXML controllers.

## Responsibilities

- FXML loading with Guice-managed controller instantiation
- Global `Injector` lifecycle management via `GuiceContext`
- Base classes for Guice-injected JavaFX components
- `@PostConstruct` lifecycle support in Guice modules

## Key Classes

| Class | Purpose |
|---|---|
| `GuiceContext` | Holds and provides the global Guice `Injector` |
| `FXMLGuicer` | Loads FXML files using Guice-backed `FXMLLoader` |
| `FXMLLoaderFactory` / `FXMLLoaderFactoryImpl` | Injectable factory for Guice-enabled loaders |
| `PostConstructModule` | Guice module wiring `@PostConstruct` support |
| `GuicedPane`, `GuicedVBox`, `GuicedHBox`, etc. | JavaFX layout containers with Guice injection |
| `GuicedButton`, `GuicedToggleButton` | JavaFX controls with Guice injection |
| `GuicedListCell` | `ListCell` subclass with Guice injection |

## Usage

Declare controllers via `fx:controller` in FXML. Do not call `setController()` — let Guice manage instantiation via `FXMLGuicer` or `FXMLLoaderFactory`.

## Dependencies

- Google Guice 7
- JavaFX (controls, FXML)
- Log4j2
