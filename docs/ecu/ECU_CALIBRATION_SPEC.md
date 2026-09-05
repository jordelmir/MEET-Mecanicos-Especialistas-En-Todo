# ECU CALIBRATION & METADATA ARCHITECTURE (ASAM A2L & ODX)

**Doctrine:** A calibration modification is not an arbitrary binary patch. It is a typed, semantic changeset bound to an authoritative baseline.

---

## 1. The ASAM Calibration Pipeline

```
ASAM ODX (MCD-2 D) ──┐
                     ├──► Capability Compiler ──► Signed Capability Pack
ASAM A2L (MCD-2 MC) ─┘                                     │
                                                           ▼
Original Firmware (Readback) ──► Semantic Changeset ──► Validated Derived Binary ──► Checksum Engine ──► Flasher
```

---

## 2. Parameter, Curve & Map Representation

1. **Scalar Values (`VALUE`):**
   - Direct engineering representation (e.g. Idle Speed `N_IDLE` in RPM, Speed Limiter `VMAX` in km/h).
   - Enforces min/max boundaries declared in A2L.
2. **2D Lookup Curves (`CURVE`):**
   - 1 input axis (e.g. Coolant Temp `TCO`) mapping to 1 output array (e.g. Warm-up Enrichment factor).
   - Strictly validates monotonicity of input breakpoints.
3. **3D Lookup Tables (`MAP`):**
   - 2 input axes (e.g. Engine Speed `RPM` × Engine Load `LOAD`) mapping to a 2D matrix (e.g. Ignition Timing Angle `KFZW` in degrees CA).
   - Validates dimensions: $X \times Y$ values match axis point counts.

---

## 3. Semantic Changeset Lifecycle

1. **Baseline Hash Lock:**
   A `CalibrationChangeSet` explicitly declares `baselineArtifactHash`. If the physical ECU original binary hash does not match byte-for-byte, the changeset application is blocked.
2. **Human Review & AI Boundary:**
   - AI may provide explanatory text and highlight mechanical risks (e.g., "Increased boost pressure may exceed stock MAP sensor range").
   - AI cannot autonomously author, sign, or flash a calibration changeset. An authorized human technician must explicitly review and confirm the modifications.
