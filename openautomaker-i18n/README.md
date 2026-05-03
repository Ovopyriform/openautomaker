# openautomaker-i18n

Internationalization framework for OpenAutoMaker. Consolidates multiple `ResourceBundle` sources into a single lookup point and manages the active locale.

## Responsibilities

- Multi-bundle resource consolidation
- Locale-aware message retrieval
- Injectable locale management

## Key Classes

| Class | Purpose |
|---|---|
| `I18N` | Singleton — registers bundles, resolves keys against the current locale |
| `LocaleProvider` | Injectable provider for the active `Locale` |

## Usage

```java
@Inject
private I18N i18n;

String label = i18n.t("some.message.key");
```

Modules register their own `ResourceBundle` with `I18N` during startup. All lookups go through the single `I18N` instance.

## Dependencies

- Google Guice 7
- Log4j2
