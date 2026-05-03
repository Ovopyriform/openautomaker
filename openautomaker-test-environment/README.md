# openautomaker-test-environment

Provides OS-specific runtime resources needed to execute tests in other modules. Contains no source code.

## Responsibilities

- Copies OS-appropriate binaries and architecture-specific resources to `env/` during the build
- Provides `openautomaker.properties` and supporting resource directories for test execution
- OS detection via Maven profiles (macOS, Windows, Linux) and architecture detection (x64, arm64)

## Usage

Declare as a `test` scope dependency in any module that requires a runtime environment during testing:

```xml
<dependency>
    <groupId>org.openautomaker</groupId>
    <artifactId>openautomaker-test-environment</artifactId>
    <scope>test</scope>
</dependency>
```

## Dependencies

- Log4j2 (test scope)
