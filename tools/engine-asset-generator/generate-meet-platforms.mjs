import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as THREE from "three";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

class NodeFileReader {
  readAsArrayBuffer(blob) { blob.arrayBuffer().then((v) => this.finish(v)).catch((e) => this.fail(e)); }
  readAsDataURL(blob) { blob.arrayBuffer().then((v) => this.finish(`data:${blob.type};base64,${Buffer.from(v).toString("base64")}`)).catch((e) => this.fail(e)); }
  finish(value) { this.result = value; this.onload?.({ target: this }); this.onloadend?.({ target: this }); }
  fail(error) { this.error = error; this.onerror?.({ target: this }); this.onloadend?.({ target: this }); }
}
globalThis.FileReader ??= NodeFileReader;

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const outputRoot = path.join(root, "android/app/src/main/assets/models/meet_platforms");
const material = (name, color, metalness = 0.55, roughness = 0.36) =>
  new THREE.MeshStandardMaterial({ name, color, metalness, roughness });
const m = {
  body: material("meet_body_red", 0x9d1424, 0.72, 0.24),
  dark: material("meet_graphite", 0x121a20, 0.78, 0.30),
  steel: material("meet_steel", 0x8ea0aa, 0.88, 0.24),
  glass: material("meet_glass", 0x164a61, 0.18, 0.12),
  rubber: material("meet_rubber", 0x080b0d, 0.02, 0.86),
  cyan: material("meet_energy_cyan", 0x00c7d7, 0.52, 0.22),
  amber: material("meet_industrial_amber", 0xd97808, 0.42, 0.38),
  white: material("meet_aero_white", 0xd7e1e5, 0.64, 0.25),
  blue: material("meet_abyss_blue", 0x123e69, 0.68, 0.26)
};

class PlatformBuilder {
  constructor(id) {
    this.scene = new THREE.Scene();
    this.root = new THREE.Group();
    this.root.name = `meet_platform_root__${id}`;
    this.root.userData = { owner: "MEET", dimensional: false, certification: "REQUIRES_PHYSICAL_VALIDATION" };
    this.scene.add(this.root);
    this.parts = new Set();
    this.meshCount = 0;
  }
  group(part) {
    const group = new THREE.Group();
    group.name = `meet_part__${part}`;
    group.userData = { partId: part, owner: "MEET", dimensional: false };
    this.root.add(group);
    this.parts.add(part);
    return group;
  }
  mesh(part, detail, geometry, mat, position, rotation = [0, 0, 0]) {
    let group = this.root.getObjectByName(`meet_part__${part}`);
    if (!group) group = this.group(part);
    const mesh = new THREE.Mesh(geometry, mat);
    mesh.name = `meet_mesh__${part}__${detail}`;
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    this.meshCount += 1;
    return mesh;
  }
  box(part, detail, size, position, mat = m.body, rotation = [0, 0, 0], radius = 0.08) {
    return this.mesh(part, detail, new RoundedBoxGeometry(...size, 3, Math.min(radius, ...size.map((v) => v / 3))), mat, position, rotation);
  }
  cylinder(part, detail, radius, length, position, mat = m.steel, rotation = [Math.PI / 2, 0, 0], segments = 24) {
    return this.mesh(part, detail, new THREE.CylinderGeometry(radius, radius, length, segments), mat, position, rotation);
  }
  wheel(part, x, z, radius = 0.62, width = 0.34) {
    this.cylinder(part, "tire", radius, width, [x, radius, z], m.rubber, [0, 0, Math.PI / 2], 32);
    this.cylinder(part, "rim", radius * 0.58, width + 0.02, [x, radius, z], m.steel, [0, 0, Math.PI / 2], 20);
    this.cylinder(part, "hub", radius * 0.16, width + 0.08, [x, radius, z], m.cyan, [0, 0, Math.PI / 2], 16);
  }
}

function roadCore(b, { length, width, height, wheelbase, rear = wheelbase, wheels = 4 }) {
  b.box("chassis", "lower_frame", [width * 0.78, 0.24, length * 0.78], [0, 0.58, 0], m.dark);
  b.box("energy_structure", "central_spine", [0.34, 0.28, length * 0.62], [0, 0.78, 0], m.cyan);
  const zPositions = wheels === 6 ? [-wheelbase, 0.45, rear] : [-wheelbase, rear];
  for (const z of zPositions) {
    b.cylinder(`axle_${z}`, "axle", 0.14, width * 0.86, [0, 0.64, z], m.steel, [0, 0, Math.PI / 2]);
    b.wheel(`wheel_left_${z}`, -width / 2, z, height * 0.32);
    b.wheel(`wheel_right_${z}`, width / 2, z, height * 0.32);
  }
  b.box("floor", "structural_floor", [width * 0.72, 0.12, length * 0.58], [0, 0.93, 0], m.steel);
}

function buildTitan(b) {
  roadCore(b, { length: 7.2, width: 3.0, height: 2.5, wheelbase: 2.25, rear: 2.35, wheels: 6 });
  b.box("cab", "crew_cell", [2.55, 1.65, 2.6], [0, 1.75, -1.15], m.body);
  b.box("windshield", "armored_glass", [2.30, 0.78, 0.08], [0, 2.16, -2.46], m.glass, [0.18, 0, 0]);
  b.box("hood", "power_dome", [2.45, 0.72, 1.65], [0, 1.30, -3.03], m.body);
  b.box("cargo_bed", "modular_bed", [2.60, 0.78, 2.30], [0, 1.26, 2.06], m.dark);
  b.box("front_bumper", "recovery_bumper", [2.86, 0.48, 0.42], [0, 0.82, -3.72], m.steel);
  b.box("rear_bumper", "recovery_bumper", [2.86, 0.42, 0.38], [0, 0.78, 3.62], m.steel);
  b.box("left_door", "outer_panel", [0.12, 1.32, 1.20], [-1.29, 1.72, -1.05], m.body);
  b.box("right_door", "outer_panel", [0.12, 1.32, 1.20], [1.29, 1.72, -1.05], m.body);
  b.box("power_module", "original_v_powertrain", [1.42, 0.88, 1.20], [0, 1.17, -2.35], m.amber);
}

function buildBackhoe(b) {
  roadCore(b, { length: 5.2, width: 2.6, height: 2.7, wheelbase: 1.75, rear: 1.7 });
  b.box("operator_cab", "safety_cell", [1.85, 1.82, 1.62], [0, 1.85, 0.25], m.amber);
  b.box("cab_glass", "panoramic", [1.65, 0.82, 0.08], [0, 2.12, -0.58], m.glass);
  b.box("engine_bay", "service_hood", [1.92, 0.92, 1.42], [0, 1.33, -1.36], m.dark);
  b.box("loader_arms", "left_arm", [0.20, 0.25, 2.45], [-0.82, 1.35, -2.10], m.amber, [-0.30, 0, 0]);
  b.box("loader_arms", "right_arm", [0.20, 0.25, 2.45], [0.82, 1.35, -2.10], m.amber, [-0.30, 0, 0]);
  b.box("front_bucket", "bucket_shell", [2.55, 0.62, 0.88], [0, 0.55, -3.33], m.steel, [0.16, 0, 0]);
  b.box("rear_boom", "primary_boom", [0.34, 0.38, 2.75], [0, 2.02, 2.02], m.amber, [0.64, 0, 0]);
  b.box("rear_dipper", "dipper_arm", [0.28, 0.30, 2.15], [0, 1.08, 3.38], m.amber, [-0.58, 0, 0]);
  b.box("rear_bucket", "digging_bucket", [0.88, 0.62, 0.72], [0, 0.42, 4.26], m.steel);
  for (const x of [-1.48, 1.48]) b.box("stabilizers", `leg_${x}`, [0.24, 1.28, 0.28], [x, 0.72, 1.72], m.steel, [0, 0, x < 0 ? -0.42 : 0.42]);
  b.cylinder("hydraulics", "loader_cylinder", 0.10, 1.55, [0, 1.28, -2.15], m.cyan, [0.22, 0, 0]);
  b.cylinder("hydraulics", "boom_cylinder", 0.11, 1.62, [0, 2.34, 1.82], m.cyan, [0.68, 0, 0]);
}

function buildLoader(b) {
  roadCore(b, { length: 5.8, width: 2.8, height: 2.8, wheelbase: 1.82, rear: 1.78 });
  b.box("articulated_frame", "front_half", [2.15, 0.56, 2.35], [0, 0.98, -1.45], m.dark);
  b.box("articulated_frame", "rear_half", [2.20, 0.62, 2.18], [0, 1.02, 1.40], m.dark);
  b.cylinder("articulation_joint", "vertical_pin", 0.18, 0.72, [0, 1.05, 0], m.cyan, [0, 0, 0]);
  b.box("operator_cab", "rollover_cell", [1.82, 1.85, 1.58], [0, 1.92, 0.78], m.amber);
  b.box("engine_module", "rear_power_pack", [1.92, 1.05, 1.52], [0, 1.42, 2.23], m.body);
  for (const x of [-0.92, 0.92]) b.box("lift_arms", `arm_${x}`, [0.22, 0.34, 2.52], [x, 1.42, -2.20], m.amber, [-0.34, 0, 0]);
  b.box("material_bucket", "wide_bucket", [2.92, 0.78, 1.05], [0, 0.62, -3.60], m.steel, [0.12, 0, 0]);
  b.cylinder("lift_hydraulics", "left_cylinder", 0.12, 1.58, [-0.58, 1.24, -1.92], m.cyan, [0.36, 0, 0]);
  b.cylinder("lift_hydraulics", "right_cylinder", 0.12, 1.58, [0.58, 1.24, -1.92], m.cyan, [0.36, 0, 0]);
}

function buildRoadConcept(b, mode) {
  const supercar = mode === "apex";
  const ev = mode === "ion";
  roadCore(b, { length: supercar ? 4.8 : 5.2, width: 2.35, height: supercar ? 1.25 : 1.55, wheelbase: 1.55, rear: 1.45 });
  b.box("monocoque", "central_cell", [1.85, 0.62, 2.25], [0, 1.05, 0], supercar ? m.body : m.dark, [0, 0, 0], 0.25);
  b.box("front_aero", "nose", [2.12, 0.36, 1.30], [0, 0.88, -2.18], ev ? m.cyan : m.body, [-0.08, 0, 0], 0.22);
  b.box("rear_aero", "tail", [2.05, 0.42, 1.18], [0, 0.91, 2.08], supercar ? m.dark : m.body, [0.10, 0, 0], 0.22);
  b.box("canopy", "transparent_cell", [1.52, 0.58, 1.52], [0, 1.42, -0.05], m.glass, [0, 0, 0], 0.30);
  b.box("left_door", "scissor_shell", [0.10, 0.66, 1.48], [-0.97, 1.13, 0.02], m.body);
  b.box("right_door", "scissor_shell", [0.10, 0.66, 1.48], [0.97, 1.13, 0.02], m.body);
  if (ev) {
    b.box("battery_pack", "structural_cells", [1.58, 0.20, 2.62], [0, 0.72, 0.12], m.cyan);
    b.cylinder("front_motor", "axial_flux", 0.36, 0.54, [0, 0.80, -1.52], m.steel, [0, 0, Math.PI / 2]);
    b.cylinder("rear_motor", "axial_flux", 0.36, 0.54, [0, 0.80, 1.48], m.steel, [0, 0, Math.PI / 2]);
  } else {
    b.box("powertrain", "original_power_core", [1.20, 0.68, 0.92], [0, 0.94, 1.40], m.amber);
    b.box("active_diffuser", "venturi", [1.78, 0.16, 1.18], [0, 0.58, 2.18], m.cyan);
  }
  if (mode === "flux") {
    b.box("adaptive_spine", "energy_arch", [0.24, 0.20, 3.72], [0, 1.58, 0], m.cyan);
    b.box("left_vector_wing", "morphing_surface", [0.82, 0.10, 2.38], [-1.16, 1.02, 0], m.white, [0, 0, 0.08]);
    b.box("right_vector_wing", "morphing_surface", [0.82, 0.10, 2.38], [1.16, 1.02, 0], m.white, [0, 0, -0.08]);
  }
}

function buildAircraft(b) {
  b.cylinder("fuselage", "pressure_shell", 0.54, 6.8, [0, 1.25, 0], m.white, [Math.PI / 2, 0, 0], 36);
  b.box("cockpit", "canopy", [0.92, 0.48, 1.16], [0, 1.58, -2.24], m.glass, [-0.10, 0, 0], 0.28);
  b.box("left_wing", "lifting_surface", [4.20, 0.12, 1.36], [-2.02, 1.24, -0.10], m.white, [0, -0.12, 0.03], 0.14);
  b.box("right_wing", "lifting_surface", [4.20, 0.12, 1.36], [2.02, 1.24, -0.10], m.white, [0, 0.12, -0.03], 0.14);
  b.box("horizontal_tail", "stabilizer", [3.00, 0.09, 0.72], [0, 1.36, 2.76], m.dark);
  b.box("vertical_tail", "fin", [0.12, 1.34, 1.10], [0, 2.00, 2.70], m.body);
  for (const x of [-1.22, 1.22]) b.cylinder("propulsion", `electric_fan_${x}`, 0.42, 0.72, [x, 1.02, 0.42], m.dark, [Math.PI / 2, 0, 0]);
  for (const [x, z] of [[-0.72, -0.72], [0.72, -0.72], [0, 2.25]]) b.wheel(`landing_gear_${x}_${z}`, x, z, 0.22, 0.14);
  b.box("energy_pack", "distributed_battery", [1.02, 0.18, 2.62], [0, 1.10, 0.12], m.cyan);
}

function buildRocket(b) {
  b.cylinder("first_stage", "propellant_shell", 0.92, 4.20, [0, 2.50, 0], m.white, [0, 0, 0], 40);
  b.cylinder("second_stage", "upper_stage", 0.72, 2.25, [0, 5.70, 0], m.dark, [0, 0, 0], 40);
  b.mesh("nose", "fairing", new THREE.ConeGeometry(0.72, 1.80, 40), m.body, [0, 7.72, 0]);
  b.cylinder("interstage", "separation_ring", 0.98, 0.24, [0, 4.70, 0], m.cyan, [0, 0, 0], 40);
  for (let i = 0; i < 5; i += 1) {
    const a = i / 5 * Math.PI * 2;
    b.mesh("engine_cluster", `engine_${i + 1}`, new THREE.ConeGeometry(0.26, 0.72, 24, 1, true), m.steel, [Math.cos(a) * 0.48, 0.10, Math.sin(a) * 0.48], [Math.PI, 0, 0]);
  }
  for (let i = 0; i < 4; i += 1) {
    const a = i / 4 * Math.PI * 2;
    b.box("grid_fins", `fin_${i + 1}`, [0.08, 0.72, 0.82], [Math.cos(a) * 1.12, 1.05, Math.sin(a) * 1.12], m.dark, [0, -a, 0]);
  }
  b.box("avionics", "flight_computer", [0.62, 0.32, 0.62], [0, 5.00, 0], m.cyan);
  b.cylinder("fuel_tank", "main_tank", 0.72, 1.65, [0, 2.85, 0], m.amber, [0, 0, 0], 32);
  b.cylinder("oxidizer_tank", "upper_tank", 0.72, 1.45, [0, 1.25, 0], m.blue, [0, 0, 0], 32);
}

function buildSubmarine(b) {
  b.cylinder("pressure_hull", "central_hull", 1.05, 6.4, [0, 1.35, 0], m.blue, [Math.PI / 2, 0, 0], 40);
  b.mesh("bow", "hydrodynamic_nose", new THREE.SphereGeometry(1.04, 32, 18), m.blue, [0, 1.35, -3.14]);
  b.mesh("stern", "propulsion_fairing", new THREE.ConeGeometry(1.02, 1.82, 36), m.dark, [0, 1.35, 3.95], [Math.PI / 2, 0, 0]);
  b.box("sail", "command_sail", [0.62, 1.10, 1.18], [0, 2.42, -0.25], m.dark, [0, 0, 0], 0.25);
  b.cylinder("periscope", "sensor_mast", 0.10, 1.42, [0, 3.47, -0.28], m.steel, [0, 0, 0], 18);
  b.box("left_dive_plane", "control_surface", [2.22, 0.10, 0.72], [-1.46, 1.42, -1.34], m.steel, [0, 0, 0.05]);
  b.box("right_dive_plane", "control_surface", [2.22, 0.10, 0.72], [1.46, 1.42, -1.34], m.steel, [0, 0, -0.05]);
  b.box("tail_planes", "horizontal_control", [3.10, 0.12, 0.78], [0, 1.40, 3.14], m.steel);
  b.cylinder("propulsor", "rim_drive", 0.70, 0.32, [0, 1.35, 4.88], m.cyan, [Math.PI / 2, 0, 0], 32);
  b.box("battery_modules", "lower_energy_bank", [1.20, 0.42, 3.22], [0, 0.88, -0.20], m.cyan);
  b.box("ballast_left", "variable_tank", [0.42, 0.72, 3.72], [-0.72, 1.05, 0], m.steel);
  b.box("ballast_right", "variable_tank", [0.42, 0.72, 3.72], [0.72, 1.05, 0], m.steel);
}

const platforms = [
  ["titan_forge", "MEET Titan Forge", buildTitan],
  ["backhoe_hx", "MEET Backhoe HX", buildBackhoe],
  ["terra_loader", "MEET Terra Loader", buildLoader],
  ["chronos_flux", "MEET Chronos Flux", (b) => buildRoadConcept(b, "flux")],
  ["ion_vector", "MEET Ion Vector", (b) => buildRoadConcept(b, "ion")],
  ["apex_r", "MEET Apex R", (b) => buildRoadConcept(b, "apex")],
  ["aero_v1", "MEET Aero V1", buildAircraft],
  ["asterion", "MEET Asterion", buildRocket],
  ["abyss_one", "MEET Abyss One", buildSubmarine]
];

fs.mkdirSync(outputRoot, { recursive: true });
const manifest = [];
for (const [id, displayName, build] of platforms) {
  const builder = new PlatformBuilder(id);
  build(builder);
  const exporter = new GLTFExporter();
  const raw = await new Promise((resolve, reject) => exporter.parse(builder.scene, resolve, reject, {
    binary: true, trs: true, onlyVisible: true, includeCustomExtensions: false
  }));
  const glb = Buffer.from(raw);
  const file = `${id}.glb`;
  fs.writeFileSync(path.join(outputRoot, file), glb);
  manifest.push({
    id, displayName, assetFile: file, owner: "MEET", originalProceduralDesign: true,
    dimensional: false, oemClaim: false, manufacturingCertified: false,
    partCount: builder.parts.size, meshCount: builder.meshCount,
    sha256: crypto.createHash("sha256").update(glb).digest("hex")
  });
}
fs.writeFileSync(path.join(outputRoot, "manifest.json"), `${JSON.stringify({
  schemaVersion: 1,
  generator: "tools/engine-asset-generator/generate-meet-platforms.mjs",
  warning: "Original MEET procedural concepts. Dimensions, materials, physics and manufacturing require engineering validation.",
  platforms: manifest
}, null, 2)}\n`);
console.log(JSON.stringify(manifest, null, 2));
