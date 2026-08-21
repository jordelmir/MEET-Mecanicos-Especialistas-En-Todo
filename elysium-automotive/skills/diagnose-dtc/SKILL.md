---
name: diagnose-dtc
description: Evidence-driven diagnostic reasoning for automotive trouble codes (DTCs) using EVAIR MCP tools.
---

# Diagnose DTC Skill

When diagnosing an automotive Trouble Code (DTC):

1. **Identify Vehicle**: Call `vehicle_get_identity()` to get VIN, make, model, year, and engine specifications.
2. **Retrieve Context**: Call `obd_read_dtcs()` to get confirmed and pending codes.
3. **Retrieve Freeze Frame**: Call `obd_read_freeze_frame()` to inspect engine speed, load, coolant temp, and fuel trims captured when the DTC triggered.
4. **Inspect Live Features**: Call `telemetry_get_features(pid=..., seconds=15)` for key PIDs (RPM, Coolant, STFT, LTFT, MAP, Voltage) to avoid raw token flooding.
5. **Compare Vehicle Baseline**: Call `diagnostics_compare_baseline(pid=...)` to determine if observed parameters deviate from THIS SPECIFIC VEHICLE's historical baseline.
6. **Formulate Hypotheses**: Build ranked hypotheses differentiating FACTS from INFERENCES.
7. **Discriminating Tests**: Propose the next lowest-risk, highest-information-gain test (e.g. coil swap, smoke test) without requesting destructive or moving-actuator commands.
8. **Safety Mandate**: NEVER clear DTCs without explicit user confirmation. All safety-critical actuator operations are denied by policy.
