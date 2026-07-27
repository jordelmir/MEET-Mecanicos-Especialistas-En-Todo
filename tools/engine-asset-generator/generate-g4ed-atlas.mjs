import * as THREE from "three";
import {
  addMesh,
  bolt,
  createPackScene,
  cylinder,
  exportPack,
  loadAtlas,
  materials,
  parseRange,
  roundedBox,
  torus,
  tube
} from "./g4ed-atlas-common.mjs";

function buildCrankshaft(state, nodeKey, mode = "complete") {
  if (mode === "complete") {
    cylinder(state, nodeKey, "axis", 0.13, 4.25, materials.steel, [0, 0, 0], [0, 0, Math.PI / 2], 40);
  }
  for (const [index, x] of [-1.5, -0.75, 0, 0.75, 1.5].entries()) {
    if (mode === "complete" || mode === "main") {
      cylinder(state, nodeKey, `main_journal_${index + 1}`, 0.235, 0.31, materials.machined, [x, 0, 0], [0, 0, Math.PI / 2], 40);
    }
  }
  for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
    const throwZ = index === 0 || index === 3 ? 0.27 : -0.27;
    if (mode === "complete" || mode === "rod") {
      cylinder(state, nodeKey, `rod_journal_${index + 1}`, 0.18, 0.3, materials.steel, [x, 0, throwZ], [0, 0, Math.PI / 2], 36);
    }
    if (mode === "complete" || mode === "counterweight") {
      addMesh(
        state,
        nodeKey,
        `counterweight_${index + 1}`,
        new THREE.SphereGeometry(0.39, 28, 18),
        materials.darkSteel,
        [x, 0, -throwZ],
        [0, 0, 0],
        [0.34, 0.92, 1]
      );
    }
  }
}

function buildFirstThirty(state, element) {
  const key = element.visual.nodeKey;
  const ordinal = element.ordinal;
  const cylinderXs = [-1.2, -0.4, 0.4, 1.2];
  switch (ordinal) {
    case 1:
      roundedBox(state, key, "outer_casting", [3.72, 1.82, 1.82], 0.18, materials.castIron);
      roundedBox(state, key, "machined_deck", [3.88, 0.16, 1.94], 0.06, materials.machined, [0, 0.96, 0]);
      for (const [index, x] of cylinderXs.entries()) {
        torus(state, key, `bore_lip_${index + 1}`, 0.29, 0.035, materials.machined, [x, 1.055, 0], [Math.PI / 2, 0, 0]);
      }
      break;
    case 2:
    case 3:
    case 4:
    case 5:
      cylinder(state, key, `cylinder_${ordinal - 1}`, 0.292, 1.55, materials.semantic, [cylinderXs[ordinal - 2], 0, 0], [0, 0, 0], 48);
      break;
    case 6:
      cylinderXs.forEach((x, index) =>
        cylinder(state, key, `integrated_liner_${index + 1}`, 0.31, 1.58, materials.darkSteel, [x, 0, 0], [0, 0, 0], 48)
      );
      break;
    case 7:
      tube(state, key, "main_oil_gallery", [[-1.7, 0, -0.62], [0, 0.08, -0.66], [1.7, 0, -0.62]], 0.075, materials.oilFlow, 56);
      cylinderXs.forEach((x, index) =>
        tube(state, key, `journal_feed_${index + 1}`, [[x, 0, -0.62], [x, -0.55, -0.1]], 0.045, materials.oilFlow, 22)
      );
      break;
    case 8:
      cylinderXs.forEach((x, index) =>
        torus(state, key, `coolant_jacket_${index + 1}`, 0.39, 0.065, materials.coolantFlow, [x, 0.25, 0], [Math.PI / 2, 0, 0])
      );
      tube(state, key, "coolant_spine", [[-1.65, 0.55, 0.55], [0, 0.62, 0.62], [1.65, 0.55, 0.55]], 0.08, materials.coolantFlow, 48);
      break;
    case 9:
      roundedBox(state, key, "deck_surface", [3.9, 0.11, 1.96], 0.045, materials.machined);
      cylinderXs.forEach((x, index) =>
        torus(state, key, `bore_edge_${index + 1}`, 0.3, 0.025, materials.semantic, [x, 0.07, 0], [Math.PI / 2, 0, 0])
      );
      break;
    case 10:
      [-1.5, -0.75, 0, 0.75, 1.5].forEach((x, index) =>
        torus(state, key, `main_saddle_${index + 1}`, 0.27, 0.085, materials.semantic, [x, 0, 0], [0, Math.PI / 2, 0])
      );
      break;
    case 11:
      [-1.5, -0.75, 0, 0.75, 1.5].forEach((x, index) => {
        roundedBox(state, key, `cap_${index + 1}`, [0.42, 0.25, 0.72], 0.07, materials.darkSteel, [x, 0, 0]);
        torus(state, key, `bearing_seat_${index + 1}`, 0.22, 0.055, materials.machined, [x, 0.11, 0], [0, Math.PI / 2, 0]);
      });
      break;
    case 12:
      [-1.5, -0.75, 0, 0.75, 1.5].forEach((x, index) => {
        bolt(state, key, `left_${index + 1}`, [x, 0, -0.3], 1.1);
        bolt(state, key, `right_${index + 1}`, [x, 0, 0.3], 1.1);
      });
      break;
    case 13:
      [-1.25, -0.4, 0.45, 1.3].forEach((x, index) =>
        cylinder(state, key, `expansion_plug_${index + 1}`, 0.18, 0.08, materials.accent, [x, 0, 0], [Math.PI / 2, 0, 0], 36)
      );
      break;
    case 14:
      [-0.65, 0, 0.65].forEach((x, index) =>
        cylinder(state, key, `oil_gallery_plug_${index + 1}`, 0.09, 0.12, materials.steel, [x, 0, 0], [Math.PI / 2, 0, 0], 8)
      );
      break;
    case 15:
      roundedBox(state, key, "front_housing", [0.24, 2.15, 1.82], 0.12, materials.aluminum);
      torus(state, key, "crank_opening", 0.39, 0.12, materials.machined, [0.14, -0.52, 0], [0, Math.PI / 2, 0]);
      break;
    case 16:
      roundedBox(state, key, "rear_seal_plate", [0.2, 1.26, 1.3], 0.12, materials.aluminum);
      torus(state, key, "seal_bore", 0.4, 0.14, materials.gasket, [0.12, 0, 0], [0, Math.PI / 2, 0]);
      break;
    case 17:
      roundedBox(state, key, "rear_engine_plate", [0.1, 2.0, 1.82], 0.08, materials.darkSteel);
      torus(state, key, "crank_clearance", 0.43, 0.08, materials.machined, [0.06, -0.35, 0], [0, Math.PI / 2, 0]);
      break;
    case 18:
      roundedBox(state, key, "cylinder_head_casting", [3.72, 0.92, 1.82], 0.16, materials.aluminum);
      cylinderXs.forEach((x, index) => {
        cylinder(state, key, `spark_well_${index + 1}`, 0.105, 0.78, materials.darkSteel, [x, 0.05, 0], [0, 0, 0], 32);
        torus(state, key, `combustion_chamber_${index + 1}`, 0.27, 0.06, materials.machined, [x, -0.5, 0], [Math.PI / 2, 0, 0]);
      });
      break;
    case 19:
      roundedBox(state, key, "valve_cover_shell", [3.72, 0.56, 1.62], 0.22, materials.polymer);
      cylinder(state, key, "oil_filler_neck", 0.18, 0.15, materials.darkSteel, [-1.12, 0.34, -0.28], [0, 0, 0], 30);
      break;
    case 20:
      roundedBox(state, key, "dohc_decorative_cover", [3.25, 0.22, 1.3], 0.16, materials.polymer);
      roundedBox(state, key, "dohc_badge", [1.25, 0.05, 0.34], 0.05, materials.accent, [0, 0.14, 0]);
      break;
    case 21:
      roundedBox(state, key, "upper_timing_cover", [0.22, 1.25, 1.7], 0.13, materials.polymer);
      break;
    case 22:
      roundedBox(state, key, "lower_timing_cover", [0.22, 1.5, 1.78], 0.13, materials.polymer);
      torus(state, key, "pulley_relief", 0.42, 0.1, materials.darkSteel, [0.13, -0.4, 0], [0, Math.PI / 2, 0]);
      break;
    case 23:
      roundedBox(state, key, "timing_backplate", [0.1, 2.55, 1.78], 0.07, materials.darkSteel);
      break;
    case 24:
      roundedBox(state, key, "oil_sump", [3.2, 0.68, 1.52], 0.17, materials.darkSteel, [0, -0.08, 0], [0, 0, 0], [1, 0.8, 1]);
      roundedBox(state, key, "mounting_flange", [3.48, 0.1, 1.7], 0.055, materials.machined, [0, 0.3, 0]);
      break;
    case 25:
      roundedBox(state, key, "windage_tray", [2.9, 0.08, 1.25], 0.05, materials.steel);
      cylinderXs.forEach((x, index) =>
        torus(state, key, `relief_${index + 1}`, 0.2, 0.035, materials.darkSteel, [x, 0.06, 0], [Math.PI / 2, 0, 0])
      );
      break;
    case 26:
      [-0.9, -0.3, 0.3, 0.9].forEach((x, index) =>
        roundedBox(state, key, `baffle_${index + 1}`, [0.08, 0.45, 1.05], 0.025, materials.steel, [x, 0, 0])
      );
      break;
    case 27:
      buildCrankshaft(state, key, "complete");
      break;
    case 28:
      buildCrankshaft(state, key, "main");
      break;
    case 29:
      buildCrankshaft(state, key, "rod");
      break;
    case 30:
      buildCrankshaft(state, key, "counterweight");
      break;
    default:
      throw new Error(`Missing high-detail recipe for ordinal ${ordinal}`);
  }
}

function buildDeterministicReference(state, element) {
  const key = element.visual.nodeKey;
  const scale = 0.65 + (element.ordinal % 7) * 0.07;
  const material = element.visual.renderStrategy === "SEMANTIC_REGION"
    ? materials.semantic
    : element.elementKind === "CONSUMABLE"
      ? materials.gasket
      : element.elementKind === "SERVICE_HARDWARE"
        ? materials.steel
        : element.elementKind === "CONDITIONAL_VARIANT"
          ? materials.accent
          : element.ordinal % 3 === 0
            ? materials.aluminum
            : materials.darkSteel;

  if (element.visual.animationMode === "FLOW_TRACE") {
    tube(
      state,
      key,
      "reference_flow_path",
      [[-scale, -0.2, 0], [-scale * 0.25, 0.3, 0.2], [scale * 0.35, -0.1, -0.15], [scale, 0.22, 0]],
      0.06 + (element.ordinal % 3) * 0.015,
      material,
      40
    );
  } else if (element.elementKind === "SERVICE_HARDWARE") {
    bolt(state, key, "reference_hardware", [0, -0.2, 0], scale * 1.5);
  } else if (element.elementKind === "CONSUMABLE") {
    torus(state, key, "reference_seal", scale * 0.52, 0.08, material);
  } else if (element.visual.animationMode === "ROTATIONAL_FUNCTION") {
    cylinder(state, key, "reference_rotor", scale * 0.48, scale * 0.55, material, [0, 0, 0], [0, 0, Math.PI / 2], 40);
    torus(state, key, "reference_rim", scale * 0.52, 0.08, materials.machined, [0, 0, 0], [0, Math.PI / 2, 0]);
  } else {
    roundedBox(
      state,
      key,
      "reference_body",
      [scale * 1.5, scale * 0.82, scale],
      Math.min(0.18, scale * 0.2),
      material
    );
    if (element.elementKind === "ASSEMBLY" || element.visual.animationMode === "EXPLODE_REASSEMBLE") {
      cylinder(state, key, "reference_axis", scale * 0.16, scale * 1.85, materials.machined, [0, 0, 0], [0, 0, Math.PI / 2], 28);
    }
  }
}

const atlas = loadAtlas();
const { start, end } = parseRange(process.argv.slice(2), atlas.elements.length);
const selected = atlas.elements.filter((element) => element.ordinal >= start && element.ordinal <= end);
const byPack = Map.groupBy(selected, (element) => element.visual.packId);
const summaries = [];

for (const [packId, elements] of byPack) {
  const state = createPackScene(packId);
  for (const element of elements) {
    if (element.ordinal <= 30) buildFirstThirty(state, element);
    else buildDeterministicReference(state, element);
  }
  summaries.push(await exportPack(state, packId, elements, atlas));
}

console.log(JSON.stringify({ range: [start, end], packs: summaries }, null, 2));

