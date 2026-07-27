# G4ED system pack matrix

| Pack | Scope |
|---|---|
| `g4ed_engine_structure` | Block, housings, covers and sump |
| `g4ed_crank_pistons_rods` | Rotating assembly, pistons and rods |
| `g4ed_cylinder_head_combustion` | Head and combustion features |
| `g4ed_dohc_valvetrain` | Camshafts, valves and actuation |
| `g4ed_timing` | Timing drive and covers |
| `g4ed_lubrication` | Oil pump, filter, galleries and service items |
| `g4ed_cooling` | Pump, thermostat, passages and hoses |
| `g4ed_air_intake` | Intake path, throttle and manifold |
| `g4ed_crankcase_ventilation` | PCV and crankcase breathing |
| `g4ed_fuel_injection` | Rail, injectors and fuel delivery |
| `g4ed_ignition` | Plugs, coils and ignition wiring |
| `g4ed_engine_sensors` | Engine sensor set and harness interfaces |
| `g4ed_exhaust_emissions` | Exhaust, catalyst and emissions |
| `g4ed_alternator` | Alternator assembly and internals |
| `g4ed_starter` | Starter assembly and internals |
| `g4ed_accessories` | Auxiliary drive and accessories |
| `g4ed_automatic_transmission_coupling` | Flexplate and automatic coupling |
| `g4ed_powertrain_mounts` | Engine/transmission mounts |
| `g4ed_electronic_control` | ECU, relays, wiring and grounds |
| `g4ed_gaskets_seals` | Gaskets, seals and service hardware |

## Routing invariant

The knowledge atlas owns `element.visual.packId`. Generators, Android manifest
loading and UI navigation must consume that value; do not duplicate a manual
ordinal-to-pack table in application code.

## Pack completion invariant

A release build contains:

- 20 manifests;
- 20 GLBs;
- exactly 420 unique bindings across them;
- the same atlas content SHA-256 in every manifest;
- no missing group or mesh prefix in the GLB JSON chunks.

