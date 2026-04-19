# Robox Serial Protocol

Extracted from `openautomaker-base/src/main/java/celtech/roboxbase/comms/`.

Firmware-version-aware: packet sizes vary by firmware version (v699, v701, v724, v740, v741, v768).

---

## Transport Layer

| Parameter | Value |
|---|---|
| Interface | USB CDC (virtual serial) via JSerialComm |
| Baud rate | 115200 |
| Data bits | 8 |
| Parity | None |
| Stop bits | 1 |
| Flow control | Disabled |
| Read timeout | 5000 ms (`TIMEOUT_READ_SEMI_BLOCKING`) |
| Charset | US-ASCII |
| Wire CRC | **None** — `CRC16` class exists but is unused; relies on USB integrity |

---

## Frame Structure

### TX Frame (host → printer)

```
[1 byte cmd] [8-hex seq# if DATA_FILE_CHUNK or END_OF_DATA_FILE] [4-hex len if EXECUTE_GCODE or END_OF_DATA_FILE] [ASCII payload...]
```

- **cmd**: 1 byte, MSB = 0 (cmd < 0x80)
- **seq#**: 8-char upper-case ASCII hex, present on `DATA_FILE_CHUNK` and `END_OF_DATA_FILE` only
- **len**: 4-char upper-case ASCII hex byte count of payload, present on `EXECUTE_GCODE` and `END_OF_DATA_FILE` only
- **payload**: ASCII; floats encoded via `FixedDecimalFloatFormat` (8 chars, space-padded left, UK locale, `.` decimal)

`WriteHeadEEPROM` (0xA0) and `WriteReel0/1EEPROM` (0xA2/0xA4) prefix their EEPROM blob with `"00C0"` (192 as ASCII hex) before the payload.

### RX Frame (printer → host)

```
[1 byte cmd, MSB=1] [optional ASCII-hex length field] [payload...]
```

Host reads:
1. Read 1 byte (cmd).
2. If packet type has a length field: read ASCII-hex length (4 bytes for `GCODE_RESPONSE`, 2 bytes for `LIST_FILES_RESPONSE`), then read that many payload bytes.
3. Else: read fixed payload length declared by the RX class (firmware-dependent).

**Validation**: `RoboxRxPacketFactory` asserts `(cmd & 0x80) != 0`, else `InvalidCommandByteException`. Unknown cmd → `UnknownPacketTypeException`.

---

## TX Commands (host → printer)

All cmd bytes have MSB=0.

| Cmd | Name | Payload | Notes |
|---|---|---|---|
| 0xB0 | STATUS_REQUEST | 0 | Polled every ~1000 ms |
| 0x95 | EXECUTE_GCODE | variable | 4-hex len prefix |
| 0x90 | START_OF_DATA_FILE | variable | job ID + filename |
| 0x97 | SEND_PRINT_FILE_START | 16 | job ID |
| 0x91 | DATA_FILE_CHUNK | fixed + seq# | 8-hex seq# prefix |
| 0x92 | END_OF_DATA_FILE | variable + seq# + len | both seq# and len fields |
| 0x94 | INITIATE_PRINT | 16 | job ID |
| 0xFF | ABORT_PRINT | 0 | |
| 0x98 | PAUSE_RESUME_PRINT | 1 | `"1"` = pause, `"0"` = resume |
| 0xB3 | REPORT_ERRORS | 0 | polled alongside STATUS_REQUEST |
| 0xC0 | RESET_ERRORS | 0 | |
| 0xB4 | QUERY_FIRMWARE_VERSION | 0 | |
| 0x8F | UPDATE_FIRMWARE | variable | |
| 0xC1 | WRITE_PRINTER_ID | 256 | see `PrinterIDDataStructure` |
| 0xB2 | READ_PRINTER_ID | 0 | |
| 0xA0 | WRITE_HEAD_EEPROM | 192 | prefixed `"00C0"` |
| 0xA1 | READ_HEAD_EEPROM | 0 | |
| 0xA2 | WRITE_REEL_0_EEPROM | 192 | prefixed `"00C0"` |
| 0xA3 | READ_REEL_0_EEPROM | 0 | |
| 0xA4 | WRITE_REEL_1_EEPROM | 192 | prefixed `"00C0"` |
| 0xA5 | READ_REEL_1_EEPROM | 0 | |
| 0xF8 | FORMAT_HEAD_EEPROM | 0 | |
| 0xF9 | FORMAT_REEL_0_EEPROM | 0 | |
| 0xFA | FORMAT_REEL_1_EEPROM | 0 | |
| 0xC2 | SET_AMBIENT_LED_COLOUR | 6 | RRGGBB ASCII hex |
| 0xC5 | SET_REEL_LED_COLOUR | 6 | RRGGBB ASCII hex |
| 0xC3 | SET_TEMPERATURES | 56 | 7 × 8-char floats: nozzle0Tgt, nozzle0FL, nozzle1Tgt, nozzle1FL, bedTgt, bedFL, ambientTgt |
| 0xC7 | SET_E_FEED_RATE_MULTIPLIER | 8 | 1 float |
| 0xC4 | SET_D_FEED_RATE_MULTIPLIER | 8 | 1 float |
| 0xC8 | SET_E_FILAMENT_INFO | 16 | diameter + multiplier |
| 0xC9 | SET_D_FILAMENT_INFO | 16 | diameter + multiplier |
| 0x96 | LIST_FILES | 0 | |
| 0x93 | READ_SEND_FILE_REPORT | 0 | |
| 0xB6 | READ_HOURS_COUNTER | 0 | |
| 0xFC | READ_DEBUG_DATA | 0 | |

---

## RX Responses (printer → host)

All cmd bytes have MSB=1.

| Cmd | Name | Size (bytes) | Notes |
|---|---|---|---|
| 0xE1 | STATUS_RESPONSE | 211–222 | firmware-dependent (see below) |
| 0xE4 | FIRMWARE_RESPONSE | 9 | 1 cmd + 8 ASCII; first char stripped, remainder parsed as float |
| 0xE3 | ACK_WITH_ERRORS | 34 or 66 | error bitmap; 33 B (<fw741) or 65 B (≥fw741) |
| 0xE5 | PRINTER_ID_RESPONSE | 257 | `PrinterIDDataStructure` |
| 0xE2 | HEAD_EEPROM_DATA | 193 | |
| 0xE6 | REEL_0_EEPROM_DATA | 193 | |
| 0xE8 | REEL_1_EEPROM_DATA | 193 | |
| 0xE7 | GCODE_RESPONSE | variable | 4-hex len prefix |
| 0xE0 | LIST_FILES_RESPONSE | variable | 2-hex count + 16 bytes per entry |
| 0xE9 | SEND_FILE | 25 | 1 cmd + 16-byte file ID + 8-hex expected seq# |
| 0xEA | HOURS_COUNTER | 9 | |
| 0xEF | DEBUG_DATA | 257 | bitmap |

---

## Payload Layouts

### STATUS_RESPONSE (0xE1)

Size varies by firmware: 211 (v699), 212 (v701), 213 (v724), 221 (v740), 222 (v768+).

Fields (v740, offsets after cmd byte):

```
runningPrintJobID           [16]  ASCII
printJobLineNumberHex        [8]  ASCII hex
pauseStatus                  [1]  enum (see Enums)
busyStatus                   [1]  enum
xSwitchState                 [1]
ySwitchState                 [1]
zSwitchState                 [1]
filamentESwitchState         [1]
filamentDSwitchState         [1]
nozzleSwitchState            [1]
lidSwitchState               [1]
ejectSwitchState             [1]
eIndexWheelState             [1]
dIndexWheelState             [1]
topZSwitchState              [1]
extruderEPresent             [1]
extruderDPresent             [1]
nozzleHeater0Mode            [1]
nozzleHeater1Mode            [1]
bedHeaterMode                [1]
nozzle0Temperature           [8]  float
nozzle0TargetTemp            [8]  float
nozzle0FirstLayerTargetTemp  [8]  float
nozzle1Temperature           [8]  float
nozzle1TargetTemp            [8]  float
nozzle1FirstLayerTargetTemp  [8]  float
bedTemperature               [8]  float
bedTargetTemperature         [8]  float
bedFirstLayerTargetTemp      [8]  float
ambientTemperature           [8]  float
ambientTargetTemperature     [8]  float
headFanOn                    [1]
... (axis positions, filament state, whyWaiting, SD-present, headPowerOn)
```

### PRINTER_ID (0xE5, 257 bytes)

```
cmd                    [1]
model                  [5]
edition                [2]
weekOfManufacture      [2]
yearOfManufacture      [2]
poNumber               [7]
serialNumber           [4]
checkByte              [1]  UPS Mod-10 of concatenated ID fields
electronicsVersion     [1]
firstPad              [40]
printerFriendlyName  [100]  base64
secondPad             [86]
colour                 [6]  RRGGBB hex
```

### HEAD_EEPROM (0xE2, 193 bytes)

```
cmd                  [1]
headTypeCode        [16]
serial              [24]
maxTemp              [8]  float
thermistorBeta       [8]  float
thermistorTCal       [8]  float
nozzle0XOffset       [8]  float
nozzle0YOffset       [8]  float
nozzle0ZOffset       [8]  float
nozzle0BOffset       [8]  float
filament0ID          [8]
filament1ID          [8]
nozzle1XOffset       [8]  float
nozzle1YOffset       [8]  float
nozzle1ZOffset       [8]  float
nozzle1BOffset       [8]  float
spare               [24]
lastFilamentTemp1    [8]  float
lastFilamentTemp0    [8]  float
hourCounter          [8]  float
```

### REEL_EEPROM (0xE6/0xE8, 193 bytes)

```
filamentID          [16]
displayColour        [6]  RRGGBB hex
colourPad           [18]
firstLayerNozzleTemp [8]  float
nozzleTemp           [8]  float
firstLayerBedTemp    [8]  float
bedTemp              [8]  float
ambientTemp          [8]  float
filamentDiameter     [8]  float
filamentMultiplier   [8]  float
feedRateMultiplier   [8]  float
friendlyName        [40]  base64
materialType         [1]
...padding...
remainingFilament    [8]  float
```

### ACK_WITH_ERRORS (0xE3)

Payload = 33 bytes (firmware < v741) or 65 bytes (firmware ≥ v741).
Byte at position N set to `0x01` means `FirmwareError` with `bytePosition == N` is active.

| Pos | Error | Pos | Error |
|---|---|---|---|
| 0 | SD_CARD | 21 | NOZZLE_FLUSH_NEEDED |
| 1 | CHUNK_SEQUENCE | 22 | Z_TOP_SWITCH |
| 2 | FILE_TOO_LARGE | 23 | B_STUCK |
| 3 | GCODE_LINE_TOO_LONG | 24 | HEAD_POWER_EEPROM |
| 4 | USB_RX | 25 | HEAD_POWER_OVERTEMP |
| 5 | USB_TX | 26 | BED_THERMISTOR |
| 6 | BAD_COMMAND | 27 | NOZZLE0_THERMISTOR |
| 7 | HEAD_EEPROM | 28 | NOZZLE1_THERMISTOR |
| 8 | BAD_FIRMWARE_FILE | 29 | B_POSITION_LOST |
| 9 | FLASH_CHECKSUM | 30 | E_LOAD_ERROR / D_LOAD_ERROR |
| 10 | GCODE_BUFFER_OVERRUN | 31 | E_UNLOAD_ERROR / D_UNLOAD_ERROR |
| 11 | FILE_READ_CLOBBERED | 32 | POWEROFF_WHILST_HOT |
| 12 | MAX_GANTRY_ADJUSTMENT | 33 | E_NO_FILAMENT / D_NO_FILAMENT |
| 13 | REEL0_EEPROM | 34 | B_POSITION_WARNING |
| 14 | REEL1_EEPROM | 35 | HEAD_SHORTED |
| 15 | E_FILAMENT_SLIP | 36 | X_DRIVER |
| 16 | D_FILAMENT_SLIP | 37 | Y_DRIVER |
| — | — | 38 | ZA_DRIVER |
| — | — | 39 | ZB_DRIVER |
| — | — | 40 | E_DRIVER |
| — | — | 41 | D_DRIVER |

---

## Request / Response Flow

- Strict 1:1 request-response, synchronous per command
- `AsyncWriteThread` queue: 50 slots max, 30 s poll timeout, 1 retry max
- Shutdown via `poisonedPill` sentinel
- Every non-data command returns typed response OR `ACK_WITH_ERRORS` (0xE3) if firmware flagged errors
- Poll loop: STATUS_REQUEST (0xB0) then REPORT_ERRORS (0xB3) each tick while `CONNECTED`

---

## Connection State Machine

```
FOUND
  └─► CHECKING_FIRMWARE    (QUERY_FIRMWARE_VERSION 0xB4)
        └─► CHECKING_ID    (READ_PRINTER_ID 0xB2)
              ├─► RESETTING_ID    (WRITE_PRINTER_ID 0xC1, if ID invalid)
              └─► DETERMINING_PRINTER_STATUS
                    └─► CONNECTED    (poll STATUS_REQUEST + REPORT_ERRORS)
                          └─► SHUTTING_DOWN    (3 consecutive poll failures)
                                └─► DISCONNECTED
```

Source: `CommandInterface.run()`, `RoboxCommsState.java`.

---

## Error Handling

| Level | Condition | Response |
|---|---|---|
| Transport | Read timeout (5 s) or `SerialPortException` | Disconnect |
| Frame | RX byte with MSB clear | `InvalidCommandByteException` |
| Frame | Unknown cmd byte | `UnknownPacketTypeException` |
| Application | `ACK_WITH_ERRORS` received | Parse bitmap, call `RESET_ERRORS` (0xC0) after handling |
| Application | 3 consecutive STATUS_REQUEST failures | `SHUTTING_DOWN` → `DISCONNECTED` |
| Application | Printer ID integrity | UPS Mod-10 `checkByte` validated at app layer |

---

## Status Enums (single-byte fields in STATUS_RESPONSE)

| Enum | Values |
|---|---|
| `PauseStatus` | 0=NOT_PAUSED, 1=PAUSE_PENDING, 2=PAUSED, 3=RESUME_PENDING, 4=SELFIE_PAUSE |
| `BusyStatus` | 0=NOT_BUSY, 1–5=various filament load/unload ops |
| `EEPROMState` | 0=NOT_PRESENT, 1=NOT_PROGRAMMED, 2=PROGRAMMED |
| `WhyAreWeWaitingState` | 0=NOT_WAITING, 1=COOLING, 2=BED_HEATING, 3=NOZZLE_HEATING |

---

## Key Source Files

```
openautomaker-base/src/main/java/celtech/roboxbase/comms/
├── SerialPortManager.java              transport
├── HardwareCommandInterface.java       read/write loop
├── CommandInterface.java               state machine
├── async/AsyncWriteThread.java         command queue
├── RoboxTxPacket.java                  TX base class
├── RoboxRxPacket.java                  RX base class
├── RoboxRxPacketFactory.java           RX dispatch
├── TxPacketTypeEnum.java               TX cmd definitions
├── RxPacketTypeEnum.java               RX cmd definitions
├── RoboxCommsState.java                connection states
├── remote/FixedDecimalFloatFormat.java float encoding
├── remote/PrinterIDDataStructure.java  printer ID layout
├── rx/StatusResponse.java              status packet
├── rx/HeadEEPROMDataResponse.java
├── rx/ReelEEPROMDataResponse.java
├── rx/PrinterIDResponse.java
├── rx/GCodeDataResponse.java
├── rx/ListFilesResponseImpl.java
├── rx/SendFile.java
├── rx/HoursCounterResponse.java
├── rx/DebugDataResponse.java
├── rx/FirmwareResponse.java
├── rx/AckResponse.java
└── rx/FirmwareError.java               error bitmap enum
```
