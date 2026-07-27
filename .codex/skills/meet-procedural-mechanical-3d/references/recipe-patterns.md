# Procedural recipe patterns

## Component

Build a recognizable primary silhouette, then add service-facing cues such as
flanges, bores, connector blocks, fastener positions or sealing faces. Use
separate detail mesh names while keeping one stable element group.

## Semantic region

Represent inseparable geometry with a localized overlay or dedicated submesh.
Use translucent emissive material, macro camera bounds and `REGION_PULSE` or
the relevant functional animation. Point commerce to the parent.

## Fluid path

Use a Catmull-Rom curve and tube geometry. Keep start/end relationships
plausible but label them schematic unless routing evidence exists. Use distinct
oil, coolant, air, fuel and exhaust materials; do not animate a live value when
OBD data is absent.

## Electrical path

Use a dedicated emissive conductor or harness trace and `CURRENT_TRACE`.
Distinguish source, load, control and ground conceptually without inventing pin
numbers or voltages. Commerce comparison must include connector, pin count,
keying and voltage drop under load.

## Body and interior

Prioritize silhouette, mounting edges, clip/weld patterns, side and body-style
identity. Do not imply collision dimensions, structural repair authorization or
sedan/hatchback interchangeability from a procedural panel.

## Suspension, steering and brakes

Show the joint topology and service interfaces needed to identify the part.
Keep alignment, torque, preload and press-fit values out of geometry unless
supported by the service source. Never collapse a documented mult-link layout
into a visually simpler axle architecture.

## SRS and safety equipment

Use inert reference geometry only. Do not animate deployment, pyrotechnic
activation or bypass procedures. Conditional equipment must stay pending
physical confirmation.

## Rotational function

Provide an explicit rotation axis and visually separate journals, teeth, rotor
or pulley faces when they matter to inspection. Store the neutral transform and
derive motion from elapsed time, never by accumulating transforms frame to
frame.

## Reciprocating function

Drive motion from a normalized phase and restore from the neutral transform.
For multi-cylinder engines, encode the teaching firing relationship separately
from exact timing claims.

## Hardware and consumables

Model the class of interface—bolt, clip, seal, gasket, filter, belt—without
inventing dimensions, pitch, material grade or tooth count. Those become
comparison checkpoints in commerce until measured.

## Deterministic fallback

When a dedicated recipe is not yet available, select a stable recipe from
element kind and animation mode. Seed variations from the canonical ordinal.
Fallback coverage must remain labeled `REFERENCE_RECONSTRUCTION`; prioritize
high-value or safety-critical elements for dedicated refinement.
