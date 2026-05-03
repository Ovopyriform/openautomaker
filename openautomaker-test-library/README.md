# openautomaker-test-library

Shared JUnit 5 test utilities for OpenAutoMaker modules. Enables `@Inject` field injection in test classes via a Guice JUnit 5 extension.

## Responsibilities

- JUnit 5 `Extension` that performs Guice field injection on test instances
- Default Guice module configuration for tests

## Key Classes

| Class | Purpose |
|---|---|
| `GuiceExtension` | JUnit 5 extension — injects `@Inject`-annotated fields before each test |
| `TestProperties` | Provides the Guice module(s) to use for a test class |

## Usage

```java
@ExtendWith(GuiceExtension.class)
@TestProperties(modules = MyTestModule.class)
class MyServiceTest {

    @Inject
    private MyService service;

    @Test
    void testSomething() { ... }
}
```

## Dependencies

- openautomaker-guice
- JUnit 5 (Jupiter, params, launcher)
- Mockito 5
- Google Guice 7

> Packaged as a regular JAR (not `test` scope) so it can be declared as a `test` dependency by other modules.
