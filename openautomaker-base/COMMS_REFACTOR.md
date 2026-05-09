# Comms Package Refactor Notes

Target: `celtech.roboxbase.comms` (and subpackages). All changes maintain backwards compatibility unless noted.

## Architecture Overview

```
RoboxCommsManager (poll thread, 500ms)
  ├─ SerialDeviceDetector → HardwareCommandInterface → AsyncWriteThread → SerialPortManager (JSerialComm)
  └─ RemotePrinterDetector → RoboxRemoteCommandInterface → AsyncWriteThread → RemoteClient (HTTP)
```

`CommandInterface` (abstract Thread) runs the FSM:
`FOUND → CHECKING_FIRMWARE → CHECKING_ID → RESETTING_ID → DETERMINING_PRINTER_STATUS → CONNECTED → DISCONNECTED`

---

## Issues and Suggestions

### 1. `CommandInterface extends Thread` — entanglement
**Status:** DONE

FSM (`run()`, ~270 lines of switch/case) and thread lifecycle are the same object. Untestable without real I/O.

**Fix:** Extract FSM into a `ConnectionStateMachine` that takes state-transition callbacks. `CommandInterface` becomes a plain `Runnable` wrapper. No public API changes.

---

### 2. `instanceof` in base class — code smell
**Status:** DONE

`CommandInterface.run()`:
```java
// line 314
if (!(this instanceof RoboxRemoteCommandInterface))
    printerToUse.setAmbientLEDColour(...)

// line 406
if (this instanceof RoboxRemoteCommandInterface)
    writeToPrinter(READ_PRINTER_ID)
```
Base class knows about subclasses. Adding a third transport breaks this silently.

**Fix:** Two protected hook methods with defaults:
```java
protected boolean requiresPeriodicIdRefresh() { return false; }
protected boolean appliesAmbientColourOnConnect() { return true; }
```
`RoboxRemoteCommandInterface` overrides both. Zero public API change.

---

### 3. `AsyncWriteThread` — slot pool complexity for no gain
**Status:** DONE

50 `BlockingQueue` slots + `boolean[] queueInUse` manual tracking. But `sendCommand` is `synchronized` — only one caller in-flight at a time anyway. The pool buys nothing. Retry mechanism is disabled (`MAX_COMMAND_RETRIES = 1`).

**Fix:** Replace with a single producer-consumer using `CompletableFuture`:
```java
// Producer (sendCommand)
CompletableFuture<RoboxRxPacket> future = new CompletableFuture<>();
inboundQueue.add(new CommandHolder(command, future));
return future.get(POLL_TIMEOUT, MILLISECONDS);

// Consumer (run loop)
CommandHolder holder = inboundQueue.take();
RoboxRxPacket result = commandInterface.writeToPrinterImpl(...);
holder.future().complete(result);
```
Eliminates: 50-slot array, manual index tracking, `queueInUse`, null-packet sentinel, complex finally block. Same external interface on `AsyncWriteThread`.

---

### 4. `writeAndWaitForData` busy-wait
**Status:** DONE

`SerialPortManager.writeAndWaitForData()` spins in 100µs increments waiting for bytes (up to 5000 iterations = 500ms CPU burn per command). Port is already configured `TIMEOUT_READ_SEMI_BLOCKING` — the blocking read in `readSerialPort()` will block until data arrives naturally.

**Fix:** Remove wait loop. Write, then let `readSerialPort()` block on the first byte. `READ_TIMEOUT = 5000` already handles no-response case. Also eliminates `CommsSuppressedException` path and `suspendComms` field on `SerialPortManager`.

---

### 5. Dead `SerialPortDataListener` implementation
**Status:** DONE

`SerialPortManager implements SerialPortDataListener` but `callback()` (which registers the listener) is never called. `serialEvent()` reads bytes and discards them.

**Fix:** Remove `implements SerialPortDataListener` and three dead methods: `callback`, `serialEvent`, `getListeningEvents`.

---

### 6. Static singleton in `RoboxCommsManager`
**Status:** TODO

```java
private static RoboxCommsManager instance = null;
// ...
instance = this; // line 183
// ...
instance = this; // line 206 — DUPLICATE assignment
```

`@Singleton` from Guice already handles this. `@Deprecated getInstance()` still has callers.

**Fix:** Audit all `RoboxCommsManager.getInstance()` call sites, inject via Guice. Delete static field and deprecated methods.

---

### 7. `RoboxRemoteCommandInterface` unmodeled operations
**Status:** TODO

`cancelPrint`, `sendStatistics`, `printGCodeFile`, `overrideFilament`, `sendCameraData` exist only on the concrete class. Callers must cast `CommandInterface → RoboxRemoteCommandInterface`.

**Fix:** Extract `IRemotePrinterControl` interface for these operations, or (cleaner) move them to a `RemotePrinterOperations` service holding `RemoteClient` directly — bypassing `CommandInterface` for operations not part of core printer I/O.

---

### 8. `RemoteClient` — raw types and per-instance `ObjectMapper`
**Status:** DONE

```java
Map<Integer, String> filamentMap = new HashMap(); // raw type
private final ObjectMapper mapper = new ObjectMapper(); // expensive per-instance; ObjectMapper is thread-safe
```

**Fix:** `private static final ObjectMapper MAPPER = new ObjectMapper();`
Fix raw `HashMap` to `HashMap<Integer, String>`.

---

### 9. 13-parameter constructor telescope
**Status:** TODO

`CommandInterface` has 13-param constructor; every subclass passes all through. `HardwareCommandInterface.java:54` has `//TODO: OK, there has to be a nicer way to do this.`

**Fix:** Group the 8 injected (non-assisted) deps into a `CommandInterfaceDeps` record. Bind as `Provider<CommandInterfaceDeps>` in Guice. Subclass constructors collapse to ~5 params.

---

### 10. `InterAppCommsThread.letUsBegin()` — single method does 4 things
**Status:** TODO

1. Creates server socket
2. Starts background thread
3. Acts as client sending to already-running instance
4. Calls `Platform.exit()`

**Fix:** Extract `InterAppClient` with a `send(AbstractInterAppRequest)` method. `letUsBegin` only does socket setup + `start()`. Caller decides what to do on `ALREADY_RUNNING_CONTACT_MADE`.

---

### 11. State mutation scattered across `run()`
**Status:** DONE

`commsState = RoboxCommsState.X` appears 15+ times directly in `run()`. No validation of valid transitions.

**Fix:**
```java
private void setState(RoboxCommsState next) {
    LOGGER.debug("{} → {}", commsState, next);
    commsState = next;
}
```

---

### 12. `AsyncWriteThread` runs at `MAX_PRIORITY`
**Status:** DONE

`setPriority(Thread.MAX_PRIORITY)` starves GC threads on single-core hardware. Combined with `synchronized sendCommand`, the priority boost does nothing useful.

**Fix:** Drop to `Thread.NORM_PRIORITY + 2`.

---

## Priority Order

| # | Description | Impact | Effort | Status |
|---|-------------|--------|--------|--------|
| 3 | AsyncWriteThread simplification | High | Low | ✅ |
| 4 | Remove busy-wait in SerialPortManager | Medium | Low | ✅ |
| 5 | Dead SerialPortDataListener removal | Low | Trivial | ✅ |
| 2 | Remove instanceof in base class | High | Low | ✅ |
| 11 | Centralise state transitions | Medium | Low | ✅ |
| 12 | Fix AsyncWriteThread priority | Low | Trivial | ✅ |
| 8 | RemoteClient ObjectMapper + raw types | Low | Trivial | ✅ |
| 6 | Remove static singleton | Medium | Medium | ✅ |
| 7 | Model remote-only operations properly | Medium | Medium | ✅ |
| 9 | Constructor parameter object | Medium | Medium | ✅ |
| 1 | Extract FSM from CommandInterface | High | High | ✅ |
| 10 | Split InterAppCommsThread.letUsBegin() | Low | Low | ✅ |
