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

