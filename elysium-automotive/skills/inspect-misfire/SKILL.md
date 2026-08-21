---
name: inspect-misfire
description: Specialized misfire investigation for combustion engines (P0300-P0308) correlating Mode 06, RPM variance, and fuel trims.
---

# Inspect Misfire Skill

When analyzing engine misfires:

1. Retrieve Mode 06 misfire counts per cylinder via `obd_read_mode06()`.
2. Inspect RPM stability and variance in idle via `telemetry_get_features(pid="010C", seconds=15)`.
3. Check Short Term and Long Term Fuel Trim via `telemetry_get_features(pid="0106")` and `telemetry_get_features(pid="0107")`.
4. Differentiate ignition vs fuel vs mechanical causes:
   - Positive high STFT (> +15%) at idle that normalizes at 2500 RPM suggests intake vacuum leak.
   - Positive high STFT that worsens under load suggests fuel delivery (filter, pump, injector).
   - Random erratic misfire across all cylinders (P0300) suggests fuel pressure, MAF, or timing.
   - Single cylinder isolated misfire (e.g. P0301) suggests plug, wire, coil, injector, or compression on that specific cylinder.
5. Recommend discriminatory tests (e.g. swapping coil #1 to cylinder #2).
