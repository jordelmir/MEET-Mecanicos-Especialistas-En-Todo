import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as THREE from "three";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

class NodeFileReader {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((result) => this.finish(result)).catch((error) => this.fail(error));
  }

  readAsDataURL(blob) {
    blob.arrayBuffer()
      .then((result) => this.finish(`data:${blob.type};base64,${Buffer.from(result).toString("base64")}`))
      .catch((error) => this.fail(error));
  }

  finish(result) {
    this.result = result;
    const event = { target: this };
    this.onload?.(event);
    this.onloadend?.(event);
  }

  fail(error) {
    this.error = error;
    const event = { target: this };
    this.onerror?.(event);
    this.onloadend?.(event);
  }
}

globalThis.FileReader ??= NodeFileReader;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "../..");
const assetDir = path.join(repoRoot, "android/app/src/main/assets/models/engine_inline4_generic");
const outputPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(assetDir, "generic_inline4_engine.glb");
const manifestPath = path.join(path.dirname(outputPath), "manifest.json");

const scene = new THREE.Scene();
scene.name = "MEET_Generic_Inline4_L2";
const root = new THREE.Group();
root.name = "asset_root__generic_inline4_l2";
root.userData = {
  authority: "L2_GENERIC_ASSEMBLY",
  dimensional: false,
  purpose: "recognizable service and diagnostic teaching assembly"
};
scene.add(root);

const materials = {
  castIron: new THREE.MeshStandardMaterial({ name: "cast_iron", color: 0x26323b, roughness: 0.5, metalness: 0.82 }),
  darkSteel: new THREE.MeshStandardMaterial({ name: "dark_steel", color: 0x37434e, roughness: 0.28, metalness: 0.92 }),
  steel: new THREE.MeshStandardMaterial({ name: "machined_steel", color: 0xb7c4ce, roughness: 0.2, metalness: 0.96 }),
  aluminum: new THREE.MeshStandardMaterial({ name: "cast_aluminum", color: 0x8d9ca8, roughness: 0.42, metalness: 0.78 }),
  machinedAluminum: new THREE.MeshStandardMaterial({ name: "machined_aluminum", color: 0xd2dbe2, roughness: 0.2, metalness: 0.88 }),
  bronze: new THREE.MeshStandardMaterial({ name: "bronze", color: 0xa9652c, roughness: 0.3, metalness: 0.75 }),
  polymer: new THREE.MeshStandardMaterial({ name: "technical_polymer", color: 0x151b20, roughness: 0.66, metalness: 0.04 }),
  gasket: new THREE.MeshStandardMaterial({ name: "gasket", color: 0x151719, roughness: 0.82, metalness: 0.02 }),
  intake: new THREE.MeshStandardMaterial({ name: "intake_alloy", color: 0x526b75, roughness: 0.36, metalness: 0.66 }),
  exhaust: new THREE.MeshStandardMaterial({ name: "heat_aged_steel", color: 0x6b4b3a, roughness: 0.5, metalness: 0.7 })
};

const groups = new Map();
const partKeys = new Set();
let triangleCount = 0;
let meshCount = 0;

function groupFor(partKey) {
  if (!groups.has(partKey)) {
    const group = new THREE.Group();
    group.name = `asset_part__${partKey}`;
    group.userData = { partKey, authority: "L2_GENERIC_ASSEMBLY", dimensional: false };
    root.add(group);
    groups.set(partKey, group);
    partKeys.add(partKey);
  }
  return groups.get(partKey);
}

function addMesh(partKey, detail, geometry, material, position = [0, 0, 0], rotation = [0, 0, 0], scale = [1, 1, 1]) {
  geometry.computeVertexNormals();
  const mesh = new THREE.Mesh(geometry, material);
  mesh.name = `asset_mesh__${partKey}__${detail}`;
  mesh.position.set(...position);
  mesh.rotation.set(...rotation);
  mesh.scale.set(...scale);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  mesh.userData = { partKey, authority: "L2_GENERIC_ASSEMBLY", dimensional: false };
  groupFor(partKey).add(mesh);
  const positionAttribute = geometry.getAttribute("position");
  triangleCount += geometry.index ? geometry.index.count / 3 : positionAttribute.count / 3;
  meshCount += 1;
  return mesh;
}

function cylinder(partKey, detail, radius, length, material, position, rotation = [0, 0, 0], radialSegments = 32) {
  return addMesh(
    partKey,
    detail,
    new THREE.CylinderGeometry(radius, radius, length, radialSegments, 1, false),
    material,
    position,
    rotation
  );
}

function torus(partKey, detail, majorRadius, tubeRadius, material, position, rotation = [0, 0, 0]) {
  return addMesh(
    partKey,
    detail,
    new THREE.TorusGeometry(majorRadius, tubeRadius, 12, 36),
    material,
    position,
    rotation
  );
}

function roundedBox(partKey, detail, width, height, depth, radius, material, position, rotation = [0, 0, 0], scale = [1, 1, 1]) {
  return addMesh(
    partKey,
    detail,
    new RoundedBoxGeometry(width, height, depth, 4, radius),
    material,
    position,
    rotation,
    scale
  );
}

function tube(partKey, detail, points, radius, material, tubularSegments = 32) {
  const curve = new THREE.CatmullRomCurve3(points.map(([x, y, z]) => new THREE.Vector3(x, y, z)));
  return addMesh(partKey, detail, new THREE.TubeGeometry(curve, tubularSegments, radius, 10, false), material);
}

function gearGeometry(teeth, rootRadius, tipRadius, thickness) {
  const shape = new THREE.Shape();
  const pointCount = teeth * 4;
  for (let index = 0; index < pointCount; index += 1) {
    const phase = index % 4;
    const radius = phase === 1 || phase === 2 ? tipRadius : rootRadius;
    const angle = (index / pointCount) * Math.PI * 2;
    const x = Math.cos(angle) * radius;
    const y = Math.sin(angle) * radius;
    if (index === 0) shape.moveTo(x, y);
    else shape.lineTo(x, y);
  }
  shape.closePath();
  return new THREE.ExtrudeGeometry(shape, {
    depth: thickness,
    bevelEnabled: true,
    bevelSegments: 2,
    bevelSize: 0.025,
    bevelThickness: 0.025,
    curveSegments: 2
  }).center();
}

function gear(partKey, detail, teeth, rootRadius, tipRadius, thickness, material, position) {
  return addMesh(partKey, detail, gearGeometry(teeth, rootRadius, tipRadius, thickness), material, position, [0, Math.PI / 2, 0]);
}

function spring(partKey, detail, x, y, z, material) {
  const points = [];
  const turns = 6;
  const segments = 60;
  for (let index = 0; index <= segments; index += 1) {
    const t = index / segments;
    const angle = t * turns * Math.PI * 2;
    points.push(new THREE.Vector3(
      x + Math.cos(angle) * 0.105,
      y + t * 0.58,
      z + Math.sin(angle) * 0.105
    ));
  }
  const curve = new THREE.CatmullRomCurve3(points);
  return addMesh(partKey, detail, new THREE.TubeGeometry(curve, 60, 0.022, 8, false), material);
}

function connectingRod(detail, x, pistonY, crankY, crankZ) {
  const start = new THREE.Vector3(x, crankY, crankZ);
  const end = new THREE.Vector3(x, pistonY - 0.37, 0);
  const midpoint = start.clone().add(end).multiplyScalar(0.5);
  const direction = end.clone().sub(start);
  const length = direction.length();
  const geometry = new RoundedBoxGeometry(0.16, length, 0.11, 3, 0.045);
  const mesh = addMesh("connecting_rods", detail, geometry, materials.darkSteel, midpoint.toArray());
  mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.normalize());
  torus("connecting_rods", `${detail}_big_end`, 0.16, 0.05, materials.steel, start.toArray(), [Math.PI / 2, 0, 0]);
  torus("connecting_rods", `${detail}_small_end`, 0.09, 0.035, materials.steel, end.toArray(), [Math.PI / 2, 0, 0]);
}

// Lower block and rotating assembly.
roundedBox("engine_block", "outer_casting", 3.72, 1.82, 1.82, 0.18, materials.castIron, [0, 0.15, 0]);
roundedBox("engine_block", "deck", 3.9, 0.18, 1.96, 0.07, materials.machinedAluminum, [0, 1.02, 0]);
for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
  cylinder("engine_block", `cylinder_liner_${index + 1}`, 0.29, 1.55, materials.darkSteel, [x, 0.45, 0], [0, 0, 0], 40);
  cylinder("main_bearing_caps", `cap_${index + 1}`, 0.31, 0.22, materials.darkSteel, [x, -0.83, 0], [Math.PI / 2, 0, 0], 32);
}
roundedBox("oil_pan", "sump", 3.2, 0.66, 1.52, 0.16, materials.darkSteel, [0, -1.02, 0], [0, 0, 0], [1, 0.78, 1]);
roundedBox("oil_pan_gasket", "perimeter", 3.5, 0.06, 1.7, 0.05, materials.gasket, [0, -0.72, 0]);

cylinder("crankshaft", "main_axis", 0.14, 4.35, materials.steel, [0, -0.48, 0], [0, 0, Math.PI / 2], 40);
for (const [index, x] of [-1.5, -0.75, 0, 0.75, 1.5].entries()) {
  cylinder("crankshaft", `main_journal_${index + 1}`, 0.24, 0.32, materials.machinedAluminum, [x, -0.48, 0], [0, 0, Math.PI / 2], 40);
}
const pistonHeights = [0.58, 0.15, 0.15, 0.58];
for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
  const throwZ = index === 0 || index === 3 ? 0.25 : -0.25;
  cylinder("crankshaft", `rod_journal_${index + 1}`, 0.18, 0.3, materials.steel, [x, -0.48, throwZ], [0, 0, Math.PI / 2], 36);
  addMesh("crankshaft", `counterweight_${index + 1}`, new THREE.SphereGeometry(0.38, 24, 16), materials.darkSteel, [x, -0.48, -throwZ], [0, 0, 0], [0.32, 0.9, 1]);
  const pistonY = pistonHeights[index];
  cylinder("pistons", `crown_${index + 1}`, 0.265, 0.42, materials.machinedAluminum, [x, pistonY, 0], [0, 0, 0], 40);
  roundedBox("pistons", `skirt_${index + 1}`, 0.47, 0.38, 0.42, 0.09, materials.aluminum, [x, pistonY - 0.18, 0]);
  cylinder("piston_pins", `pin_${index + 1}`, 0.07, 0.58, materials.steel, [x, pistonY - 0.04, 0], [Math.PI / 2, 0, 0], 28);
  for (let ring = 0; ring < 3; ring += 1) {
    torus("piston_rings", `piston_${index + 1}_ring_${ring + 1}`, 0.267, 0.012, materials.darkSteel, [x, pistonY + 0.11 - ring * 0.055, 0], [Math.PI / 2, 0, 0]);
  }
  connectingRod(`rod_${index + 1}`, x, pistonY, -0.48, throwZ);
}

cylinder("flywheel", "disc", 0.78, 0.2, materials.darkSteel, [-2.28, -0.48, 0], [0, 0, Math.PI / 2], 56);
torus("flywheel", "ring_gear", 0.7, 0.08, materials.steel, [-2.4, -0.48, 0], [0, Math.PI / 2, 0]);
gear("crank_pulley", "damper", 28, 0.49, 0.56, 0.2, materials.darkSteel, [2.28, -0.48, 0]);
gear("crank_sprocket", "timing", 18, 0.27, 0.33, 0.16, materials.steel, [2.06, -0.38, 0]);

// Cylinder head and valve train.
roundedBox("head_gasket", "layer", 3.78, 0.055, 1.84, 0.045, materials.gasket, [0, 1.12, 0]);
roundedBox("cylinder_head", "casting", 3.86, 0.82, 1.86, 0.16, materials.aluminum, [0, 1.52, 0]);
for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
  cylinder("head_bolts", `bolt_left_${index + 1}`, 0.055, 0.92, materials.darkSteel, [x, 1.55, -0.72], [0, 0, 0], 18);
  cylinder("head_bolts", `bolt_right_${index + 1}`, 0.055, 0.92, materials.darkSteel, [x, 1.55, 0.72], [0, 0, 0], 18);
}
cylinder("camshafts_context", "intake_cam", 0.11, 3.65, materials.steel, [0, 2.03, -0.43], [0, 0, Math.PI / 2], 32);
cylinder("camshafts_context", "exhaust_cam", 0.11, 3.65, materials.steel, [0, 2.03, 0.43], [0, 0, Math.PI / 2], 32);
for (const [camIndex, z] of [-0.43, 0.43].entries()) {
  for (const [lobeIndex, x] of [-1.45, -1.05, -0.65, -0.25, 0.25, 0.65, 1.05, 1.45].entries()) {
    addMesh("camshafts_context", `cam_${camIndex + 1}_lobe_${lobeIndex + 1}`, new THREE.SphereGeometry(0.2, 20, 14), materials.darkSteel, [x, 2.03, z], [0, 0, 0], [0.46, 1, 0.72]);
  }
}
for (const [index, x] of [-1.45, -1.05, -0.65, -0.25, 0.25, 0.65, 1.05, 1.45].entries()) {
  const intakeZ = -0.53;
  const exhaustZ = 0.53;
  cylinder("intake_valves", `stem_${index + 1}`, 0.033, 0.74, materials.steel, [x, 1.48, intakeZ], [0, 0, 0], 16);
  cylinder("intake_valves", `head_${index + 1}`, 0.115, 0.055, materials.steel, [x, 1.1, intakeZ], [0, 0, 0], 24);
  cylinder("exhaust_valves", `stem_${index + 1}`, 0.033, 0.74, materials.steel, [x, 1.48, exhaustZ], [0, 0, 0], 16);
  cylinder("exhaust_valves", `head_${index + 1}`, 0.105, 0.055, materials.exhaust, [x, 1.1, exhaustZ], [0, 0, 0], 24);
  spring("valve_springs", `intake_${index + 1}`, x, 1.63, intakeZ, materials.darkSteel);
  spring("valve_springs", `exhaust_${index + 1}`, x, 1.63, exhaustZ, materials.darkSteel);
  cylinder("valve_retainers", `intake_${index + 1}`, 0.105, 0.055, materials.machinedAluminum, [x, 2.2, intakeZ], [0, 0, 0], 24);
  cylinder("valve_retainers", `exhaust_${index + 1}`, 0.105, 0.055, materials.machinedAluminum, [x, 2.2, exhaustZ], [0, 0, 0], 24);
}
for (const [index, x] of [-1.35, -0.45, 0.45, 1.35].entries()) {
  roundedBox("cam_caps", `cap_intake_${index + 1}`, 0.32, 0.18, 0.38, 0.06, materials.aluminum, [x, 2.12, -0.43]);
  roundedBox("cam_caps", `cap_exhaust_${index + 1}`, 0.32, 0.18, 0.38, 0.06, materials.aluminum, [x, 2.12, 0.43]);
}
roundedBox("valve_cover_gasket", "perimeter", 3.7, 0.06, 1.58, 0.055, materials.gasket, [0, 2.29, 0]);
roundedBox("valve_cover", "shell", 3.72, 0.56, 1.62, 0.22, materials.polymer, [0, 2.56, 0], [0, 0, 0], [1, 0.75, 1]);
cylinder("valve_cover", "oil_filler", 0.18, 0.12, materials.darkSteel, [-1.15, 2.87, -0.28], [0, 0, 0], 28);

// Timing drive with toothed wheels and individually visible links.
gear("cam_sprockets_context", "intake", 30, 0.42, 0.49, 0.16, materials.steel, [2.06, 2.0, -0.43]);
gear("cam_sprockets_context", "exhaust", 30, 0.42, 0.49, 0.16, materials.steel, [2.06, 2.0, 0.43]);
gear("timing_idler", "idler", 20, 0.25, 0.31, 0.13, materials.machinedAluminum, [2.12, 0.82, 0.68]);
gear("timing_tensioner", "tensioner", 18, 0.22, 0.29, 0.13, materials.machinedAluminum, [2.12, 0.62, -0.7]);
for (let index = 0; index < 42; index += 1) {
  const t = index / 42;
  const angle = t * Math.PI * 2;
  const y = 0.82 + Math.cos(angle) * 1.22;
  const z = Math.sin(angle) * 0.82;
  torus("timing_belt", `link_${index + 1}`, 0.075, 0.022, materials.bronze, [2.16, y, z], [0, Math.PI / 2, 0]);
}
roundedBox("timing_cover_lower", "lower", 0.18, 1.45, 1.78, 0.12, materials.polymer, [2.34, 0.25, 0]);
roundedBox("timing_cover_upper", "upper", 0.18, 1.25, 1.78, 0.12, materials.polymer, [2.34, 1.65, 0]);

// Lubrication, cooling, intake, and exhaust context.
gear("oil_pump", "gerotor_outer", 16, 0.27, 0.34, 0.14, materials.steel, [1.82, -0.72, -0.48]);
gear("oil_pump", "gerotor_inner", 11, 0.15, 0.22, 0.17, materials.bronze, [1.82, -0.72, -0.48]);
cylinder("oil_filter", "canister", 0.28, 0.7, materials.darkSteel, [-1.25, -0.18, -1.05], [Math.PI / 2, 0, 0], 36);
cylinder("water_pump", "housing", 0.38, 0.25, materials.aluminum, [1.72, 0.18, 0.88], [0, 0, Math.PI / 2], 36);
gear("water_pump", "impeller", 12, 0.2, 0.31, 0.08, materials.steel, [1.88, 0.18, 0.88]);
roundedBox("thermostat_housing", "housing", 0.58, 0.42, 0.46, 0.12, materials.aluminum, [-1.58, 1.42, 0.92]);
cylinder("thermostat", "capsule", 0.12, 0.34, materials.bronze, [-1.58, 1.42, 0.92], [Math.PI / 2, 0, 0], 24);

roundedBox("intake_manifold", "plenum", 3.25, 0.58, 0.64, 0.19, materials.intake, [0, 1.25, -1.55]);
for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
  tube("intake_manifold", `runner_${index + 1}`, [[x, 1.5, -0.78], [x, 1.6, -1.05], [x, 1.35, -1.28]], 0.13, materials.intake, 26);
}
cylinder("throttle_body", "bore", 0.35, 0.48, materials.aluminum, [1.78, 1.25, -1.55], [0, 0, Math.PI / 2], 36);
cylinder("throttle_body", "plate", 0.31, 0.035, materials.bronze, [1.79, 1.25, -1.55], [0, 0, Math.PI / 2], 30);

for (const [index, x] of [-1.2, -0.4, 0.4, 1.2].entries()) {
  tube("exhaust_manifold", `primary_${index + 1}`, [[x, 1.45, 0.84], [x, 1.2, 1.28], [0.7 - index * 0.25, 0.65, 1.55]], 0.12, materials.exhaust, 28);
}
tube("exhaust_manifold", "collector", [[0.35, 0.7, 1.55], [0.6, 0.25, 1.6], [0.9, -0.15, 1.6]], 0.22, materials.exhaust, 30);

const exporter = new GLTFExporter();
const arrayBuffer = await new Promise((resolve, reject) => {
  exporter.parse(scene, resolve, reject, {
    binary: true,
    trs: true,
    onlyVisible: true,
    includeCustomExtensions: false,
    maxTextureSize: 1024
  });
});

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
const glb = Buffer.from(arrayBuffer);
fs.writeFileSync(outputPath, glb);
const sha256 = crypto.createHash("sha256").update(glb).digest("hex");
const manifest = {
  schemaVersion: 1,
  assetId: "meet.generic.inline4.engine.l2",
  assetFile: path.basename(outputPath),
  displayName: "Motor L4 genérico MEET",
  geometryAuthority: "L2_GENERIC_ASSEMBLY",
  dimensionalState: "ILLUSTRATIVE_PROPORTIONS_ONLY",
  oemClaim: false,
  vehicleSpecificClaim: false,
  generatedBy: "tools/engine-asset-generator/generate-inline4-engine.mjs",
  generatorVersion: "1.0.0",
  threeVersion: THREE.REVISION,
  meshNodePrefix: "asset_mesh__",
  meshCount,
  triangleCount: Math.round(triangleCount),
  partKeys: [...partKeys].sort(),
  sha256,
  license: "Original procedural asset generated for MEET; project-owned source generator",
  warning: "No dimensional/OEM evidence. Physical confirmation is required."
};
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(JSON.stringify({ outputPath, manifestPath, bytes: glb.length, meshCount, triangleCount: manifest.triangleCount, sha256 }, null, 2));
