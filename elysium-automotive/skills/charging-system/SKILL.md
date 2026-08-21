---
name: charging-system
description: Diagnostic skill for battery condition, alternator output, and electrical voltage regulation.
---

# Charging System Diagnostic Skill

1. Read control module voltage via `telemetry_get_features(pid="0142", seconds=10)`.
2. Compare voltage against engine state:
   - Key On Engine Off (KOEO): Normal is $12.4\text{V} - 12.8\text{V}$. $< 12.0\text{V}$ indicates weak battery state of charge.
   - Key On Engine Running (KOER): Normal is $13.8\text{V} - 14.6\text{V}$. $< 13.2\text{V}$ indicates insufficient alternator output.
   - Load Test: Turn on headlights, AC, and rear defroster. Voltage should not drop below $13.5\text{V}$ at 2000 RPM.
