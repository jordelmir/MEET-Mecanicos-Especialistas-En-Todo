# MEET performance lab

Performance is accepted from measurements, never from a successful compilation or a single manual launch.
The canonical scenarios and tier budgets live in `tools/vehicle-truth/performance-budgets.json`.

Each result must bind device model, Android/API, RAM, thermal state, app commit, build variant, iteration count,
startup mode, P50/P95/P99, frame jank and PSS. Results without these fields are exploratory only. Baseline profiles
may be generated only from representative release navigation and must be regenerated when startup/navigation
ownership changes. Current measurement state is intentionally pending until the lab is executed.
