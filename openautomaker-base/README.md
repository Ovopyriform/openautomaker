# openautomaker-base

Core domain library for OpenAutoMaker. Handles all communication with Robox printers and Root servers, device discovery, configuration persistence, and slicer integration.

## Responsibilities

- Serial and network communication with Robox printers
- Remote Root server and camera detection
- Device state management and event publishing
- Printer and filament configuration (load, save, validate)
- Slicer management (CuraEngine invocation, profile generation)
- G-Code post-processing
- Cryptographic utilities

## Key Packages

| Package | Purpose |
|---|---|
| `celtech.roboxbase.comms` | `RoboxCommsManager` — central comms coordinator; serial/network protocol |
| `celtech.roboxbase.comms.async` | Asynchronous command dispatch |
| `celtech.roboxbase.comms.events` | Printer state change events |
| `celtech.roboxbase.comms.remote` | Remote Root server and camera communication |
| `celtech.roboxbase.comms.rx` / `tx` | Low-level receive/transmit packet handling |
| `org.openautomaker.base` | Configuration, device types, material types, `BaseLookup` |
| `org.openautomaker.base.camera` | Camera device control |
| `org.openautomaker.base.crypto` | Encryption utilities |
| `org.openautomaker.base.importers` | Model import pipeline |
| `org.openautomaker.base.inject` | Guice bindings for comms and base services |

## Key Classes

| Class | Purpose |
|---|---|
| `RoboxCommsManager` | Starts and coordinates all printer communication |
| `DeviceDetector` / `SerialDeviceDetector` | Detects locally attached Robox printers |
| `RemoteServerDetector` / `RemotePrinterHost` | Discovers Root servers on the network |
| `CommandInterface` / `HardwareCommandInterface` | Protocol abstraction over printer commands |
| `BaseLookup` | Minimal singleton for shared base-layer state |

## Dependencies

- openautomaker-environment
- openautomaker-javafx
- JSerialComm 2.4 (serial port)
- Jackson 2.21 (configuration JSON/XML)
- Apache Batik (SVG)
- Apache Commons (lang3, math3, io, codec, collections)
- JSch (SSH for remote Root access)
- Google Guice 7
- Log4j2
