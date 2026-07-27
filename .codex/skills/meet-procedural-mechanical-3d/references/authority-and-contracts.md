# Authority and contract reference

## Authority ladder

| Authority | Meaning | Allowed claims |
|---|---|---|
| `SCHEMATIC_REGION` | Functional/location overlay on a parent | Region identity and teaching purpose |
| `REFERENCE_RECONSTRUCTION` | Original procedural approximation | Recognizable reference and interaction |
| `L2_GENERIC_ASSEMBLY` | Generic system-level assembly | System relationships, not vehicle fit |
| `OEM_VERIFIED` | Licensed, traceable OEM geometry | Only after evidence review |

Do not infer a higher level from realism, polygon count, a marketplace photo or
an online model. Geometry authority and compatibility authority are separate.

## Compatibility ceiling

Visual comparison may support inspection but cannot establish `EXACT`.
Compatibility needs one of:

- VIN plus OEM evidence;
- closed tuple of brand, model, year, engine and OEM;
- explicit physical confirmation with photo, connector and measurements.

For the G4ED atlas, keep `REQUIRES_VERIFICATION` until one of those gates passes.

## Required manifest relationship

```
knowledge element
  canonicalId ─────┐
  ordinal ─────────┼──> 3D binding
  visual.nodeKey ──┘      groupNode + meshNodePrefix
                          bounds + animation + transform
                          source/atlas/GLB hashes
```

Seller listings reference the canonical ID; they never overwrite canonical
names, authority, geometry or applicability.

## Region commerce rule

An integrated feature or reference mark:

- uses `SEMANTIC_REGION`;
- has a known parent;
- is not directly sellable;
- redirects to its parent part or a service such as inspection or machining.

