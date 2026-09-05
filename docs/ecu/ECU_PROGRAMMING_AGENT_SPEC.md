# ELYSIUM PROGRAMMING AGENT SPECIFICATION

**Role:** High-Performance, Out-of-Process Execution Engine for J2534, SocketCAN, and Low-Level Bench Recovery.

---

## 1. System Topology

```
┌────────────────────────────────────────────────────────┐
│               MEET Android / Desktop UI                │
│ (Compose, Diagnostics, Vehicle Truth, Session Manager)  │
└───────────────────────────┬────────────────────────────┘
                            │ Authenticated Local IPC (Unix Socket / Named Pipe)
                            │ Signed Token + Short-Lived Nonce
┌───────────────────────────▼────────────────────────────┐
│               Elysium Programming Agent                │
│    (Core Orchestrator, Preflight Guard, State Machine)  │
└───────┬───────────────────┬────────────────────┬───────┘
        │ Subprocess IPC    │ Native Socket      │ Native USB
┌───────▼────────┐  ┌───────▼────────┐  ┌────────▼───────┐
│ J2534 Bridge   │  │ Linux          │  │ FTDI / Native  │
│ Worker Process │  │ SocketCAN      │  │ Bench Dongle   │
│ (32/64-bit DLL)│  │ (can0 / vcan0) │  │ (K-Line / CAN) │
└───────┬────────┘  └───────┬────────┘  └────────┬───────┘
        │ Pass-Thru API     │ Socket API         │ Serial
┌───────▼───────────────────▼────────────────────▼───────┐
│                     Physical ECU                       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Fault Isolation & Stability Architecture

1. **Bitness & Crash Decoupling:**
   Vendor J2534 drivers (e.g. Tactrix `openport2.0.dll`, Bosch `MTS653x.dll`) are frequently 32-bit legacy C/C++ libraries. Running them inside a dedicated `j2534-bridge` process prevents memory leaks, blocking calls, and unhandled memory access violations from terminating the main application.
2. **Local Execution Doctrine:**
   The Programming Agent executes locally on the machine physically connected to the vehicle. The cloud is NEVER in the critical byte-streaming loop during a flash session.
3. **Emergency Recovery Channel:**
   If the J2534 bridge process exits unexpectedly during block transfer, the Programming Agent detects the broken pipe, marks the durable session as `RECOVERY_REQUIRED`, and exposes the recovery kernel without crashing the host OS.
