import fs from "node:fs";
import path from "node:path";
import {
  bolt,
  createPackScene,
  cylinder,
  exportPack,
  materials,
  repoRoot,
  roundedBox,
  torus,
  tube
} from "./g4ed-atlas-common.mjs";

const domainIndex = process.argv.indexOf("--atlas");
const selectedDomain = domainIndex >= 0 ? process.argv[domainIndex + 1] : "all";
const knownDomains = ["transmission_hydraulics", "electrical", "body", "remaining_systems"];
if (selectedDomain !== "all" && !knownDomains.includes(selectedDomain)) {
  throw new Error(`Unknown --atlas ${selectedDomain}; expected ${knownDomains.join(", ")} or all`);
}

const atlasRoot = path.join(
  repoRoot,
  "android/app/src/main/assets/knowledge/vehicle_technical_atlases"
);
const modelRoot = path.join(
  repoRoot,
  "android/app/src/main/assets/models/vehicle_technical_atlases"
);

function materialFor(element, domainId) {
  if (element.visual.renderStrategy === "SEMANTIC_REGION") return materials.semantic;
  if (element.elementKind === "CONDITIONAL_VARIANT") return materials.accent;
  if (element.elementKind === "CONSUMABLE") return materials.gasket;
  if (element.elementKind === "SERVICE_HARDWARE") return materials.steel;
  if (domainId === "electrical") return element.ordinal % 2 ? materials.polymer : materials.aluminum;
  if (domainId === "body") return element.ordinal % 3 ? materials.darkSteel : materials.aluminum;
  return element.ordinal % 2 ? materials.castIron : materials.machined;
}

function buildReference(state, element, domainId) {
  const key = element.visual.nodeKey;
  const scale = 0.48 + (element.ordinal % 9) * 0.045;
  const material = materialFor(element, domainId);
  const animation = element.visual.animationMode;

  if (animation === "FLOW_TRACE" || animation === "CURRENT_TRACE") {
    tube(
      state,
      key,
      domainId === "electrical" ? "current_path" : "fluid_path",
      [[-scale, -0.14, 0], [-scale * 0.32, 0.24, 0.16], [scale * 0.36, -0.08, -0.12], [scale, 0.18, 0]],
      0.035 + (element.ordinal % 3) * 0.008,
      domainId === "electrical" ? materials.accent : materials.oilFlow,
      12
    );
    return;
  }
  if (element.elementKind === "SERVICE_HARDWARE") {
    bolt(state, key, "service_hardware", [0, -0.16, 0], scale * 1.15);
    return;
  }
  if (element.elementKind === "CONSUMABLE") {
    torus(state, key, "service_element", scale * 0.48, 0.055, material);
    return;
  }
  if (animation === "ROTATIONAL_FUNCTION") {
    cylinder(state, key, "rotor", scale * 0.42, scale * 0.52, material, [0, 0, 0], [0, 0, Math.PI / 2], 12);
    torus(state, key, "functional_ring", scale * 0.47, 0.055, materials.machined, [0, 0, 0], [0, Math.PI / 2, 0]);
    return;
  }

  const proportions = domainId === "body"
    ? [scale * 1.75, scale * 0.42, scale * 1.05]
    : domainId === "electrical"
      ? [scale * 1.28, scale * 0.72, scale * 0.92]
      : [scale * 1.45, scale * 0.88, scale];
  roundedBox(
    state,
    key,
    element.visual.renderStrategy === "SEMANTIC_REGION" ? "semantic_region" : "reference_body",
    proportions,
    Math.min(0.11, scale * 0.17),
    material
  );
  if (element.elementKind === "ASSEMBLY") {
    cylinder(state, key, "assembly_axis", scale * 0.11, scale * 1.55, materials.machined, [0, 0, 0], [0, 0, Math.PI / 2], 12);
  }
}

const domains = selectedDomain === "all" ? knownDomains : [selectedDomain];
const summaries = [];
for (const domainId of domains) {
  const atlas = JSON.parse(
    fs.readFileSync(path.join(atlasRoot, `${domainId}_atlas.json`), "utf8")
  );
  const byPack = Map.groupBy(atlas.elements, (element) => element.visual.packId);
  for (const [packId, elements] of byPack) {
    const state = createPackScene(packId);
    for (const element of elements) buildReference(state, element, domainId);
    summaries.push(await exportPack(state, packId, elements, atlas, {
      root: path.join(modelRoot, domainId),
      assetRoot: `models/vehicle_technical_atlases/${domainId}`,
      generatedBy: "tools/engine-asset-generator/generate-vehicle-technical-atlases.mjs",
      generatorVersion: "1.0.0"
    }));
  }
}

console.log(JSON.stringify({
  domains,
  packs: summaries.length,
  elements: summaries.reduce((sum, item) => sum + item.elementCount, 0),
  meshes: summaries.reduce((sum, item) => sum + item.meshCount, 0),
  triangles: summaries.reduce((sum, item) => sum + item.triangleCount, 0),
  bytes: summaries.reduce((sum, item) => sum + item.bytes, 0),
  summaries
}, null, 2));
