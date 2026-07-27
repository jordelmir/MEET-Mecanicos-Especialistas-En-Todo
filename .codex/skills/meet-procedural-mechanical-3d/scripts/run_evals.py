#!/usr/bin/env python3
"""Run deterministic v1 evaluations against MEET's proven G4ED assets."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
ASSET_ROOT = ROOT / "android/app/src/main/assets"
ATLAS = json.loads(
    (ASSET_ROOT / "knowledge/g4ed/g4ed_engine_atlas.json").read_text()
)
PACK_IDS = list(dict.fromkeys(
    element["visual"]["packId"] for element in ATLAS["elements"]
))
MANIFESTS = {
    pack_id: json.loads(
        (ASSET_ROOT / f"models/g4ed_atlas/{pack_id}/manifest.json").read_text()
    )
    for pack_id in PACK_IDS
}
BINDINGS = {
    binding["ordinal"]: binding
    for manifest in MANIFESTS.values()
    for binding in manifest["bindings"]
}


def glb_nodes(manifest: dict) -> set[str]:
    raw = (ASSET_ROOT / manifest["assetPath"]).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == manifest["sha256"]
    json_length = int.from_bytes(raw[12:16], "little")
    payload = json.loads(raw[20 : 20 + json_length].decode().strip())
    return {node["name"] for node in payload.get("nodes", []) if "name" in node}


def grade(text: str, passed: bool, evidence: str) -> dict:
    return {"text": text, "passed": passed, "evidence": evidence}


def main() -> None:
    grades = []
    binding1 = BINDINGS[1]
    manifest1 = MANIFESTS["g4ed_engine_structure"]
    nodes1 = glb_nodes(manifest1)
    grades += [
        grade("Ordinal 1 has group and mesh", binding1["groupNode"] in nodes1 and any(
            name.startswith(binding1["meshNodePrefix"]) for name in nodes1
        ), binding1["groupNode"]),
        grade("Ordinal 1 supports 360 and finite bounds", "ORBIT_360" in binding1[
            "interactionModes"
        ] and binding1["bounds"]["radius"] > 0, str(binding1["bounds"])),
        grade("Manifest forbids unsupported authority", not manifest1["oemClaim"] and not
              manifest1["vehicleSpecificClaim"], manifest1["dimensionalState"]),
    ]
    binding2 = BINDINGS[2]
    grades += [
        grade("Cylinder 1 is a schematic region", binding2["renderStrategy"] ==
              "SEMANTIC_REGION" and binding2["authority"] == "SCHEMATIC_REGION",
              binding2["authority"]),
        grade("Cylinder 1 redirects to block", binding2["parentCanonicalId"] ==
              ATLAS["elements"][0]["canonicalId"], binding2["parentCanonicalId"]),
        grade("Cylinder 1 is not directly sellable", not binding2["directlySellable"],
              str(binding2["directlySellable"])),
    ]
    binding7 = BINDINGS[7]
    grades += [
        grade("Oil gallery uses flow trace", binding7["animationMode"] == "FLOW_TRACE",
              binding7["animationMode"]),
        grade("Oil gallery has dedicated mesh", any(
            name.startswith(binding7["meshNodePrefix"]) for name in nodes1
        ), binding7["meshNodePrefix"]),
        grade("Manifest matches canonical atlas hash", manifest1["atlasContentSha256"] ==
              ATLAS["contentSha256"], manifest1["atlasContentSha256"]),
    ]
    crank_manifest = MANIFESTS["g4ed_crank_pistons_rods"]
    grades += [
        grade("Crank ordinals have unique nodes", len({
            BINDINGS[ordinal]["nodeKey"] for ordinal in range(27, 31)
        }) == 4, ", ".join(BINDINGS[ordinal]["nodeKey"] for ordinal in range(27, 31))),
        grade("Crank regions redirect to crankshaft", all(
            BINDINGS[ordinal]["parentCanonicalId"] == BINDINGS[27]["canonicalId"]
            for ordinal in range(28, 31)
        ), BINDINGS[27]["canonicalId"]),
        grade("Crank transforms are finite and resettable", all(
            binding["originalTransform"] == {
                "position": [0, 0, 0],
                "rotation": [0, 0, 0],
                "scale": [1, 1, 1],
            } and len(binding["explodeVector"]) == 3
            for binding in (BINDINGS[ordinal] for ordinal in range(27, 31))
        ), crank_manifest["sha256"]),
    ]
    all_nodes = {
        pack_id: glb_nodes(manifest)
        for pack_id, manifest in MANIFESTS.items()
    }
    all_bindings = [
        binding
        for manifest in MANIFESTS.values()
        for binding in manifest["bindings"]
    ]
    grades += [
        grade(
            "Exactly twenty complete system packs exist",
            len(MANIFESTS) == 20 and all(
                (ASSET_ROOT / manifest["assetPath"]).is_file()
                for manifest in MANIFESTS.values()
            ),
            f"{len(MANIFESTS)} manifests and GLBs",
        ),
        grade(
            "All 420 unique ordinals are bound",
            len(all_bindings) == 420
            and {binding["ordinal"] for binding in all_bindings} == set(range(1, 421)),
            f"{len(all_bindings)} bindings",
        ),
        grade(
            "Every pack matches atlas and GLB hashes",
            len(all_nodes) == 20 and all(
                manifest["atlasContentSha256"] == ATLAS["contentSha256"]
                for manifest in MANIFESTS.values()
            ),
            ATLAS["contentSha256"],
        ),
    ]
    pieces_ui = (
        ROOT
        / "android/app/src/main/kotlin/com/elysium369/meet/ui/screens/"
        "ProprietaryPartsBrowser.kt"
    ).read_text()
    motor_ui = (
        ROOT
        / "android/app/src/main/kotlin/com/elysium369/meet/ui/screens/"
        "ComponentLocatorScreen.kt"
    ).read_text()
    main_ui = (
        ROOT / "android/app/src/main/kotlin/com/elysium369/meet/MainActivity.kt"
    ).read_text()
    commerce_ui = (
        ROOT
        / "android/app/src/main/kotlin/com/elysium369/meet/ui/screens/"
        "PartRequestScreen.kt"
    ).read_text()
    grades += [
        grade(
            "Piezas and Motor 3D expose the G4ED atlas",
            "ATLAS G4ED · 420 EXPERIENCIAS 3D" in pieces_ui
            and "G4ED · 420 PIEZAS" in motor_ui,
            "Canonical navigation entry points found",
        ),
        grade(
            "AI receives canonical source and authority context",
            "FUENTE CANÓNICA MEET G4ED" in main_ui
            and "Autoridad visual" in main_ui,
            "Grounded G4ED AI context builder found",
        ),
        grade(
            "Commerce carries canonical reference without exact visual promotion",
            "canonicalReferenceId = atlasElement?.canonicalId" in commerce_ui
            and "La similitud visual no confirma compatibilidad exacta" in commerce_ui,
            "Canonical reference and compatibility disclaimer found",
        ),
    ]
    passed = sum(item["passed"] for item in grades)
    output = {
        "skill": "meet-procedural-mechanical-3d",
        "version": "2.0.0",
        "expectations": grades,
        "summary": {
            "passed": passed,
            "failed": len(grades) - passed,
            "total": len(grades),
            "pass_rate": passed / len(grades),
        },
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    if passed != len(grades):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
