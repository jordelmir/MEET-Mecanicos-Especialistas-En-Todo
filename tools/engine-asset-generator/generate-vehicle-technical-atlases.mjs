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

function includesAny(name, terms) {
  return terms.some((term) => name.includes(term));
}

function panelReference(state, key, scale, material, firewall = false) {
  // Bake the upright X/Y/Z proportions into geometry instead of relying on
  // node rotations. Android GLB loaders can flatten child transforms while
  // isolating a node, but baked dimensions retain the intended front view.
  roundedBox(state, key, "stamped_sheet", [scale * 1.95, scale * 1.32, scale * 0.10], scale * 0.045, material);
  roundedBox(state, key, "upper_flange", [scale * 2.05, scale * 0.16, scale * 0.18], scale * 0.035, materials.machined, [0, -scale * 0.61, 0.08]);
  roundedBox(state, key, "lower_flange", [scale * 2.00, scale * 0.13, scale * 0.16], scale * 0.03, materials.darkSteel, [0, scale * 0.62, 0.06]);
  roundedBox(state, key, "left_reinforcement", [scale * 0.16, scale * 1.08, scale * 0.16], scale * 0.035, materials.darkSteel, [-scale * 0.82, 0, 0.07]);
  roundedBox(state, key, "right_reinforcement", [scale * 0.16, scale * 1.08, scale * 0.16], scale * 0.035, materials.darkSteel, [scale * 0.82, 0, 0.07]);
  if (firewall) {
    roundedBox(state, key, "transmission_tunnel_relief", [scale * 0.52, scale * 0.66, scale * 0.30], scale * 0.12, material, [0, scale * 0.31, 0.13]);
    torus(state, key, "steering_column_passage", scale * 0.13, scale * 0.025, materials.machined, [-scale * 0.45, -scale * 0.12, scale * 0.11]);
    torus(state, key, "harness_passage", scale * 0.10, scale * 0.022, materials.gasket, [scale * 0.48, -scale * 0.20, scale * 0.11]);
    torus(state, key, "hvac_passage", scale * 0.08, scale * 0.018, materials.aluminum, [scale * 0.20, -scale * 0.38, scale * 0.11]);
  }
}

function buildBodyReference(state, element, scale, material) {
  const key = element.visual.nodeKey;
  const name = element.nameOriginal.toLowerCase();
  if (includesAny(name, ["cortafuego", "firewall"])) {
    panelReference(state, key, scale, material, true);
    return true;
  }
  if (includesAny(name, ["panel", "piso", "techo", "capó", "capo", "guardafango", "guardabarro", "larguero", "refuerzo"])) {
    panelReference(state, key, scale, material, false);
    return true;
  }
  if (includesAny(name, ["puerta", "compuerta"])) {
    roundedBox(state, key, "door_shell", [scale * 1.45, scale * 1.18, scale * 0.12], scale * 0.07, material);
    roundedBox(state, key, "window_frame_top", [scale * 1.20, scale * 0.08, scale * 0.08], scale * 0.025, materials.darkSteel, [0, -scale * 0.74, 0]);
    roundedBox(state, key, "window_frame_left", [scale * 0.07, scale * 0.58, scale * 0.08], scale * 0.02, materials.darkSteel, [-scale * 0.58, -scale * 0.48, 0]);
    roundedBox(state, key, "window_frame_right", [scale * 0.07, scale * 0.58, scale * 0.08], scale * 0.02, materials.darkSteel, [scale * 0.58, -scale * 0.48, 0]);
    cylinder(state, key, "hinge_axis", scale * 0.045, scale * 0.76, materials.machined, [-scale * 0.73, 0, 0], [0, 0, 0], 16);
    return true;
  }
  if (includesAny(name, ["bumper", "parachoque", "paragolpe"])) {
    roundedBox(state, key, "impact_beam", [scale * 1.95, scale * 0.30, scale * 0.34], scale * 0.13, material);
    roundedBox(state, key, "left_return", [scale * 0.42, scale * 0.38, scale * 0.42], scale * 0.12, material, [-scale * 0.90, 0, -scale * 0.12], [0, 0.30, 0]);
    roundedBox(state, key, "right_return", [scale * 0.42, scale * 0.38, scale * 0.42], scale * 0.12, material, [scale * 0.90, 0, -scale * 0.12], [0, -0.30, 0]);
    return true;
  }
  if (includesAny(name, ["asiento", "seat"])) {
    roundedBox(state, key, "seat_cushion", [scale * 1.05, scale * 0.72, scale * 0.30], scale * 0.12, materials.polymer, [0, 0, -scale * 0.30], [-0.10, 0, 0]);
    roundedBox(state, key, "seat_back", [scale * 1.00, scale * 0.30, scale * 1.25], scale * 0.12, materials.polymer, [0, scale * 0.18, scale * 0.48], [-0.15, 0, 0]);
    cylinder(state, key, "left_rail", scale * 0.045, scale * 1.0, materials.steel, [-scale * 0.34, -scale * 0.30, -scale * 0.47], [0, 0, Math.PI / 2], 12);
    cylinder(state, key, "right_rail", scale * 0.045, scale * 1.0, materials.steel, [scale * 0.34, -scale * 0.30, -scale * 0.47], [0, 0, Math.PI / 2], 12);
    return true;
  }
  if (includesAny(name, ["espejo", "mirror"])) {
    roundedBox(state, key, "mirror_glass", [scale * 1.05, scale * 0.16, scale * 0.62], scale * 0.18, materials.aluminum);
    cylinder(state, key, "mounting_stem", scale * 0.09, scale * 0.55, materials.darkSteel, [0, -scale * 0.32, -scale * 0.18], [0, 0, Math.PI / 2], 16);
    roundedBox(state, key, "mounting_base", [scale * 0.42, scale * 0.18, scale * 0.36], scale * 0.08, materials.polymer, [0, -scale * 0.60, -scale * 0.18]);
    return true;
  }
  return false;
}

function buildElectricalReference(state, element, scale, material) {
  const key = element.visual.nodeKey;
  const name = element.nameOriginal.toLowerCase();
  if (includesAny(name, ["arnés", "arnes", "cable", "wire", "línea", "linea"])) {
    tube(state, key, "insulated_harness", [[-scale, 0, 0], [-scale * 0.42, scale * 0.22, 0.12], [scale * 0.30, -scale * 0.10, -0.10], [scale, scale * 0.16, 0]], scale * 0.055, materials.polymer, 42);
    cylinder(state, key, "left_terminal", scale * 0.10, scale * 0.24, materials.machined, [-scale, 0, 0], [0, 0, Math.PI / 2], 12);
    cylinder(state, key, "right_terminal", scale * 0.10, scale * 0.24, materials.machined, [scale, scale * 0.16, 0], [0, 0, Math.PI / 2], 12);
    return true;
  }
  if (includesAny(name, ["sensor", "switch", "interruptor"])) {
    cylinder(state, key, "sensor_probe", scale * 0.13, scale * 0.78, materials.machined, [0, -scale * 0.20, 0], [0, 0, 0], 20);
    roundedBox(state, key, "sensor_body", [scale * 0.58, scale * 0.44, scale * 0.52], scale * 0.10, material, [0, scale * 0.28, 0]);
    roundedBox(state, key, "connector", [scale * 0.42, scale * 0.36, scale * 0.38], scale * 0.07, materials.polymer, [scale * 0.43, scale * 0.30, 0]);
    return true;
  }
  if (includesAny(name, ["módulo", "modulo", "ecu", "ecm", "tcm", "controlador", "computadora"])) {
    roundedBox(state, key, "controller_case", [scale * 1.48, scale * 0.36, scale], scale * 0.10, materials.aluminum);
    roundedBox(state, key, "connector_bank", [scale * 0.96, scale * 0.30, scale * 0.28], scale * 0.06, materials.polymer, [0, -scale * 0.32, -scale * 0.38]);
    for (let index = -3; index <= 3; index += 1) {
      cylinder(state, key, `pin_${index + 4}`, scale * 0.025, scale * 0.20, materials.machined, [index * scale * 0.12, -scale * 0.52, -scale * 0.38], [0, 0, Math.PI / 2], 8);
    }
    return true;
  }
  if (includesAny(name, ["relé", "rele", "fusible", "relay"])) {
    roundedBox(state, key, "relay_or_fuse_body", [scale * 0.72, scale * 0.84, scale * 0.62], scale * 0.09, material);
    for (let index = -1; index <= 1; index += 1) {
      roundedBox(state, key, `blade_${index + 2}`, [scale * 0.08, scale * 0.30, scale * 0.18], scale * 0.015, materials.machined, [index * scale * 0.20, -scale * 0.55, 0]);
    }
    return true;
  }
  if (includesAny(name, ["motor", "alternador", "actuador", "solenoide"])) {
    cylinder(state, key, "electromagnetic_housing", scale * 0.42, scale * 0.82, materials.aluminum, [0, 0, 0], [0, 0, Math.PI / 2], 28);
    cylinder(state, key, "output_shaft", scale * 0.12, scale * 1.18, materials.machined, [0, 0, 0], [0, 0, Math.PI / 2], 18);
    roundedBox(state, key, "electrical_connector", [scale * 0.40, scale * 0.34, scale * 0.34], scale * 0.07, materials.polymer, [0, scale * 0.48, scale * 0.25]);
    return true;
  }
  return false;
}

function buildTransmissionReference(state, element, scale, material) {
  const key = element.visual.nodeKey;
  const name = element.nameOriginal.toLowerCase();
  if (includesAny(name, ["engranaje", "planetario", "piñón", "pinon", "corona", "sprocket"])) {
    cylinder(state, key, "gear_core", scale * 0.46, scale * 0.22, material, [0, 0, 0], [Math.PI / 2, 0, 0], 24);
    torus(state, key, "gear_teeth_reference", scale * 0.48, scale * 0.10, materials.machined, [0, 0, 0], [Math.PI / 2, 0, 0]);
    cylinder(state, key, "gear_hub", scale * 0.16, scale * 0.38, materials.darkSteel, [0, 0, 0], [Math.PI / 2, 0, 0], 18);
    return true;
  }
  if (includesAny(name, ["eje", "shaft", "semieje", "árbol", "arbol"])) {
    cylinder(state, key, "shaft", scale * 0.14, scale * 1.75, materials.machined, [0, 0, 0], [0, 0, Math.PI / 2], 24);
    torus(state, key, "left_bearing", scale * 0.22, scale * 0.055, materials.steel, [-scale * 0.62, 0, 0], [0, Math.PI / 2, 0]);
    torus(state, key, "right_bearing", scale * 0.22, scale * 0.055, materials.steel, [scale * 0.62, 0, 0], [0, Math.PI / 2, 0]);
    return true;
  }
  if (includesAny(name, ["disco", "embrague", "clutch", "convertidor", "tambor", "pack"])) {
    cylinder(state, key, "friction_body", scale * 0.52, scale * 0.22, material, [0, 0, 0], [Math.PI / 2, 0, 0], 32);
    torus(state, key, "friction_ring", scale * 0.40, scale * 0.08, materials.gasket, [0, 0, 0], [Math.PI / 2, 0, 0]);
    cylinder(state, key, "splined_hub", scale * 0.16, scale * 0.34, materials.machined, [0, 0, 0], [Math.PI / 2, 0, 0], 18);
    return true;
  }
  if (includesAny(name, ["solenoide", "válvula", "valvula"])) {
    cylinder(state, key, "valve_body", scale * 0.20, scale * 0.82, material, [0, 0, 0], [0, 0, Math.PI / 2], 24);
    cylinder(state, key, "valve_spool", scale * 0.09, scale * 1.02, materials.machined, [0, 0, 0], [0, 0, Math.PI / 2], 16);
    roundedBox(state, key, "connector", [scale * 0.36, scale * 0.32, scale * 0.34], scale * 0.06, materials.polymer, [-scale * 0.52, 0, 0]);
    return true;
  }
  return false;
}

function buildRemainingReference(state, element, scale, material) {
  const key = element.visual.nodeKey;
  const name = element.nameOriginal.toLowerCase();
  if (includesAny(name, ["resorte", "muelle", "spring"])) {
    const points = Array.from({ length: 45 }, (_, index) => {
      const angle = index * Math.PI * 0.42;
      return [Math.cos(angle) * scale * 0.34, (index / 44 - 0.5) * scale * 1.45, Math.sin(angle) * scale * 0.34];
    });
    tube(state, key, "coil_spring", points, scale * 0.055, materials.steel, 88);
    return true;
  }
  if (includesAny(name, ["amortiguador", "strut", "shock"])) {
    cylinder(state, key, "damper_body", scale * 0.24, scale * 1.05, material, [0, -scale * 0.18, 0], [0, 0, 0], 24);
    cylinder(state, key, "piston_rod", scale * 0.09, scale * 1.55, materials.machined, [0, scale * 0.34, 0], [0, 0, 0], 18);
    torus(state, key, "lower_eye", scale * 0.22, scale * 0.06, materials.darkSteel, [0, -scale * 0.78, 0], [Math.PI / 2, 0, 0]);
    return true;
  }
  if (includesAny(name, ["disco de freno", "rotor", "tambor de freno"])) {
    cylinder(state, key, "brake_rotor", scale * 0.56, scale * 0.16, materials.castIron, [0, 0, 0], [Math.PI / 2, 0, 0], 36);
    cylinder(state, key, "rotor_hat", scale * 0.24, scale * 0.30, materials.darkSteel, [0, 0, 0], [Math.PI / 2, 0, 0], 24);
    return true;
  }
  if (includesAny(name, ["caliper", "cáliper", "mordaza"])) {
    roundedBox(state, key, "caliper_bridge", [scale * 0.92, scale * 0.42, scale * 0.62], scale * 0.18, material);
    cylinder(state, key, "hydraulic_piston", scale * 0.22, scale * 0.46, materials.machined, [0, 0, scale * 0.22], [Math.PI / 2, 0, 0], 24);
    return true;
  }
  if (includesAny(name, ["rueda", "neumático", "neumatico", "llanta", "tire"])) {
    torus(state, key, "tire", scale * 0.48, scale * 0.16, materials.polymer, [0, 0, 0], [Math.PI / 2, 0, 0]);
    cylinder(state, key, "wheel", scale * 0.34, scale * 0.16, materials.aluminum, [0, 0, 0], [Math.PI / 2, 0, 0], 28);
    return true;
  }
  if (includesAny(name, ["manguera", "tubería", "tuberia", "línea", "linea"])) {
    tube(state, key, "service_line", [[-scale, 0, 0], [-scale * 0.4, scale * 0.3, 0.18], [scale * 0.35, -scale * 0.18, -0.14], [scale, scale * 0.14, 0]], scale * 0.07, material, 42);
    return true;
  }
  if (includesAny(name, ["radiador", "condensador", "evaporador", "enfriador"])) {
    roundedBox(state, key, "heat_exchanger_core", [scale * 1.55, scale * 0.22, scale], scale * 0.06, materials.aluminum);
    for (let index = -4; index <= 4; index += 1) {
      roundedBox(state, key, `fin_${index + 5}`, [scale * 1.35, scale * 0.04, scale * 0.035], scale * 0.01, materials.machined, [0, scale * 0.13, index * scale * 0.10]);
    }
    cylinder(state, key, "left_tank", scale * 0.13, scale * 1.02, materials.polymer, [-scale * 0.78, 0, 0], [0, 0, 0], 18);
    cylinder(state, key, "right_tank", scale * 0.13, scale * 1.02, materials.polymer, [scale * 0.78, 0, 0], [0, 0, 0], 18);
    return true;
  }
  return false;
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
  if (domainId === "body" && buildBodyReference(state, element, scale, material)) return;
  if (domainId === "electrical" && buildElectricalReference(state, element, scale, material)) return;
  if (domainId === "transmission_hydraulics" && buildTransmissionReference(state, element, scale, material)) return;
  if (domainId === "remaining_systems" && buildRemainingReference(state, element, scale, material)) return;

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
      generatorVersion: "1.1.0"
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
