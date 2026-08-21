---
name: vehicle-health
description: Holistic vehicle health evaluation correlating engine, fuel, cooling, electrical, and emissions subsystems.
---

# Vehicle Health Assessment Skill

1. Query `vehicle_get_health_summary()` to retrieve individual subsystem scores and holistic score / 100.
2. Query `telemetry_detect_anomalies()` to check multi-sensor isolation forest and digital twin deviations.
3. Query `diagnostics_compare_baseline()` to assess long-term baseline drift.
4. Synthesize an explainable health breakdown:
   - Identify degraded subsystems.
   - List active risks (e.g. rising coolant trend, LTFT positive drift).
   - Recommend preventative inspections before component breakdown occurs.
