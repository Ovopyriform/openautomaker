# openautomaker-parent

Maven parent POM for all OpenAutoMaker modules. Contains no source code.

## Responsibilities

- Centralized dependency version management
- Build plugin configuration (compiler, resources, surefire, shade, javafx-maven-plugin)
- OS-specific Maven profiles for macOS, Windows, and Linux platform packaging
- Module list declaration

## Key Versions

| Dependency | Version |
|---|---|
| Java | 25 |
| JavaFX | 26 |
| Google Guice | 7.0.0 |
| Jackson | 2.21.2 |
| Log4j2 | 2.25.4 |
| JUnit 5 | 5.x |
| Mockito | 5.x |

## Usage

All builds for the project run from this directory:

```bash
cd openautomaker-parent
mvn clean install
```
