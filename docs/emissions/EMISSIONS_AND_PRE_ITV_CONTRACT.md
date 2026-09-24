# MEET Emissions Lab & Pre-ITV Costa Rica — Architectural Contract

**Version:** 1.0.0  
**Effective Date:** 2026-09-24  
**Principle:** *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

---

## 1. Executive Summary

MEET Emissions Lab provides on-board combustion diagnostics, continuous oxygen sensor waveform analysis, and a guided Pre-ITV inspection protocol tailored to Costa Rican regulatory standards (COSEVI / MOPT).

### Key Differentiation
An OBD-II dongle (ELM327 / STN) **cannot physically measure** tailpipe gas concentrations because it does not have a chemical gas analyzer. The vehicle ECU only knows what its on-board sensors report.
Therefore, MEET enforces a strict four-layer metrological truth taxonomy:
- **`MEASURED`**: Physical direct measurement from an authorized physical sensor (ECU direct PID or external physical gas probe).
- **`PHYSICS_DERIVED`**: Computed through deterministic mass-conservation laws (e.g. stoichiometric carbon mass balance for $\text{CO}_2$ mass rate).
- **`MODEL_ESTIMATED`**: Inferred via statistical domain models fusing fuel trims, lambda, misfire count, and oxygen sensor switching patterns.
- **`UNKNOWN`**: Never coerced to 0, PASS, or NORMAL.

---

## 2. Hard Metrological Safety Invariants

1. **No Invented Measurements**: A model estimate must **never** be labeled `MEASURED`.
   ```kotlin
   require(truthClass != TruthClass.MEASURED || origin == EvidenceOrigin.ECU_DIRECT || origin == EvidenceOrigin.PHYSICAL_GAS_ANALYZER)
   ```
2. **Strict Mode $06 Evaluation**:
   - Unknown UASID evaluates to `DecodeStatus.UNKNOWN_UASID` and `Mode06Verdict.UNKNOWN`. It is never multiplied by 1.0 or marked PASS.
   - Missing limits (no minimum and no maximum) evaluate to `DecodeStatus.NO_LIMITS` and `Mode06Verdict.UNKNOWN`.
   - MIDs are never synthetically injected if ECU bitmap discovery returns empty.
3. **No Global Engine Displacement Assumption**:
   - Speed-density calculations strictly require a confirmed engine displacement (`displacementLiters != null`).
   - If displacement is unknown, speed-density returns `Estimate.Unavailable("ENGINE_DISPLACEMENT_REQUIRED")`.
4. **Anti-Circularity Guard**:
   - $\lambda$ cannot be derived from a fuel rate that was calculated assuming stoichiometric $\lambda = 1.0$.
5. **Regulatory Disclaimer & Naming**:
   - The product is named **"Pre-ITV Costa Rica" / "Emissions Lab"**.
   - It prominently displays: *"No afiliado ni certificado por DEKRA/MOPT. No sustituye una inspección oficial."*

---

## 3. Costa Rica Regulatory Engine (COSEVI / MOPT)

Regulated by the official *Manual de Procedimientos para la Revisión Técnica de Vehículos Automotores en las Estaciones de RTV* (COSEVI/MOPT):

### Gasoline 4-Stroke Limits

| Category | Idle CO | Idle HC | Idle $\text{CO}_2$ | 2500 RPM CO | 2500 RPM HC | 2500 RPM $\text{CO}_2$ | Lambda Criteria |
|---|---|---|---|---|---|---|---|
| **Pre-1995** | $\le 4.5\%$ | $\le 650\text{ ppm}$ | $\ge 10.0\%$ | $\le 4.0\%$ | $\le 600\text{ ppm}$ | $\ge 10.0\%$ | N/A |
| **1995–1998** | $\le 1.0\%$ | $\le 300\text{ ppm}$ | $\ge 10.0\%$ | $\le 0.8\%$ | $\le 250\text{ ppm}$ | $\ge 11.0\%$ | N/A |
| **$\ge 1999$** | $\le 0.50\%$ | $\le 125\text{ ppm}$ | $\ge 10.0\%$ | $\le 0.30\%$ | $\le 100\text{ ppm}$ | $\ge 12.0\%$ | $1.00 \pm 0.07$ (if entry $\ge 2012\text{-}10\text{-}26$) |

### Conservative Decision Rule
For maximum threshold $L$:
- `upper95 < L` $\to$ **`probable PASS`**
- `lower95 > L` $\to$ **`probable FAIL`**
- Otherwise $\to$ **`INCONCLUSIVE`**

---

## 4. Hardware Expansion: MEET Gas Probe Contract

To achieve physical measurement parity with official inspection stations:
- Stainless steel tailpipe probe (25 cm insertion).
- Condensate water trap & sub-micron filter.
- NDIR benches for $\text{CO}$ and $\text{CO}_2$.
- NDIR automotive bench for $\text{HC}$.
- Electrochemical cell for $\text{O}_2$.
- Micro-controller transmitting samples via BLE directly to MEET app.
- Upon connection, virtual estimates are promoted to `MEASURED`.

---

## 5. Persistence Specification (Room Schema 82 $\to$ 83)

- `emission_sessions`: Records session metadata, vehicle ID, rule set, and overall verdict.
- `emission_frames`: Time-series telemetry frames.
- `emission_phase_results`: Summary of Idle and 2500 RPM test phases.
- `emission_estimates`: Gas estimates with 95% confidence intervals and lineage.
- `gas_probe_calibrations`: Zero and span calibrations for physical probe hardware.
