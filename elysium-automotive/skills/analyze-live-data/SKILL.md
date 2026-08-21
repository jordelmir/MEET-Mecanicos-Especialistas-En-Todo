---
name: analyze-live-data
description: Extracts and correlates statistical signal features (mean, stdDev, slope, p05, p95) across multi-sensor streams.
---

# Analyze Live Data Skill

When inspecting vehicle live data:
1. Identify target PIDs for analysis (e.g. RPM `010C`, MAP `010B`, Coolant `0105`, STFT `0106`).
2. Call `telemetry_get_features(pid=..., seconds=15)` for each PID to obtain compact statistical moments without token explosion.
3. Correlate variations:
   - Check if RPM dips coincide with MAP pressure spikes.
   - Check if fuel trim positive drift coincides with throttle movements.
