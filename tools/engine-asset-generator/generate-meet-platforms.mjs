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
const material = (name, color, metalness = 0.55, roughness = 0.36, extra = {}) =>
  new THREE.MeshPhysicalMaterial({ name, color, metalness, roughness, clearcoat: 0.35, clearcoatRoughness: 0.2, ...extra });
const m = {
  body: material("meet_body_red", 0x9d1424, 0.72, 0.24),
  dark: material("meet_graphite", 0x121a20, 0.78, 0.30),
  steel: material("meet_steel", 0x8ea0aa, 0.88, 0.24),
  glass: material("meet_glass", 0x0b3448, 0.12, 0.08, { transparent: true, opacity: 0.72, transmission: 0.12, depthWrite: true }),
  rubber: material("meet_rubber", 0x080b0d, 0.02, 0.86),
  cyan: material("meet_energy_cyan", 0x00c7d7, 0.52, 0.22),
  amber: material("meet_industrial_amber", 0xd97808, 0.42, 0.38),
  white: material("meet_aero_white", 0xd7e1e5, 0.64, 0.25),
  blue: material("meet_abyss_blue", 0x123e69, 0.68, 0.26),
  light: material("meet_lamp", 0xe9fbff, 0.18, 0.12, { emissive: 0x45dfff, emissiveIntensity: 2.2 }),
  redLight: material("meet_tail_lamp", 0xff1738, 0.18, 0.14, { emissive: 0xff0828, emissiveIntensity: 2.0 }),
  brake: material("meet_brake", 0x8d101b, 0.64, 0.28)
};

function loftGeometry(sections, radialSegments = 24) {
  const vertices = [];
  const indices = [];
  for (const section of sections) {
    for (let i = 0; i < radialSegments; i += 1) {
      const angle = i / radialSegments * Math.PI * 2;
      const side = Math.cos(angle);
      const vertical = Math.sin(angle);
      const lowerScale = vertical < 0 ? (section.lowerScale ?? 0.72) : 1;
      vertices.push(
        side * section.width * lowerScale,
        section.y + vertical * section.height,
        section.z
      );
    }
  }
  for (let s = 0; s < sections.length - 1; s += 1) {
    for (let i = 0; i < radialSegments; i += 1) {
      const next = (i + 1) % radialSegments;
      const a = s * radialSegments + i;
      const b = s * radialSegments + next;
      const c = (s + 1) * radialSegments + next;
      const d = (s + 1) * radialSegments + i;
      indices.push(a, b, d, b, c, d);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.Float32BufferAttribute(vertices, 3));
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  return geometry;
}

function prismGeometry(points, thickness = 0.08) {
  const shape = new THREE.Shape();
  points.forEach(([x, y], index) => index === 0 ? shape.moveTo(x, y) : shape.lineTo(x, y));
  shape.closePath();
  const geometry = new THREE.ExtrudeGeometry(shape, { depth: thickness, bevelEnabled: true, bevelSize: 0.025, bevelThickness: 0.025, bevelSegments: 2 });
  geometry.center();
  geometry.computeVertexNormals();
  return geometry;
}

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
  loft(part, detail, sections, mat = m.body, radialSegments = 24) {
    return this.mesh(part, detail, loftGeometry(sections, radialSegments), mat, [0, 0, 0]);
  }
  prism(part, detail, points, thickness, position, mat = m.body, rotation = [0, 0, 0]) {
    return this.mesh(part, detail, prismGeometry(points, thickness), mat, position, rotation);
  }
  wheel(part, x, z, radius = 0.62, width = 0.34) {
    const side = Math.sign(x) || 1;
    this.mesh(part, "tire", new THREE.TorusGeometry(radius * 0.78, radius * 0.22, 12, 36), m.rubber, [x, radius, z], [0, Math.PI / 2, 0]);
    this.cylinder(part, "brake_disc", radius * 0.48, width * 0.20, [x + side * width * 0.18, radius, z], m.steel, [0, 0, Math.PI / 2], 36);
    this.cylinder(part, "hub", radius * 0.13, width * 0.62, [x + side * width * 0.22, radius, z], m.dark, [0, 0, Math.PI / 2], 24);
    for (let i = 0; i < 10; i += 1) {
      const a = i / 10 * Math.PI * 2;
      this.box(part, `rim_spoke_${i + 1}`, [width * 0.14, radius * 0.10, radius * 0.72], [x + side * width * 0.42, radius, z], m.steel, [a, 0, 0], 0.025);
    }
    this.box(part, "brake_caliper", [width * 0.20, radius * 0.28, radius * 0.16], [x + side * width * 0.30, radius + radius * 0.30, z], m.brake, [0, 0, 0], 0.04);
  }
}

function roadCore(b, { length, width, height, wheelbase, rear = wheelbase, wheels = 4, wheelPositions = null, wheelRadius = null }) {
  b.box("chassis", "lower_frame", [width * 0.78, 0.24, length * 0.78], [0, 0.58, 0], m.dark);
  b.box("energy_structure", "central_spine", [0.34, 0.28, length * 0.62], [0, 0.78, 0], m.cyan);
  const zPositions = wheelPositions ?? (wheels === 6 ? [-wheelbase, 0.45, rear] : [-wheelbase, rear]);
  for (const z of zPositions) {
    b.cylinder(`axle_${z}`, "axle", 0.14, width * 0.86, [0, 0.64, z], m.steel, [0, 0, Math.PI / 2]);
    b.wheel(`wheel_left_${z}`, -width / 2, z, wheelRadius ?? height * 0.32);
    b.wheel(`wheel_right_${z}`, width / 2, z, wheelRadius ?? height * 0.32);
  }
  b.box("floor", "structural_floor", [width * 0.72, 0.12, length * 0.58], [0, 0.93, 0], m.steel);
}

function buildTitan(b) {
  roadCore(b, {
    length: 8.0, width: 2.72, height: 2.25, wheelbase: 2.55, rear: 2.55, wheels: 6,
    wheelPositions: [-2.55, 1.62, 2.62], wheelRadius: 0.67
  });
  b.loft("hood", "sculpted_power_hood", [
    { z: -3.88, y: 1.03, width: 0.52, height: 0.20 }, { z: -3.58, y: 1.14, width: 1.20, height: 0.34 },
    { z: -2.20, y: 1.22, width: 1.24, height: 0.42 }, { z: -1.72, y: 1.28, width: 1.20, height: 0.38 }
  ], m.body, 30);
  b.loft("cab", "four_door_safety_cell", [
    { z: -1.72, y: 1.48, width: 1.20, height: 0.62 }, { z: -1.35, y: 1.72, width: 1.22, height: 0.92 },
    { z: -0.75, y: 1.82, width: 1.18, height: 1.08 }, { z: 0.48, y: 1.80, width: 1.18, height: 1.05 },
    { z: 0.88, y: 1.54, width: 1.20, height: 0.72 }
  ], m.body, 30);
  b.loft("glasshouse", "panoramic_safety_glass", [
    { z: -1.48, y: 2.16, width: 1.08, height: 0.30 }, { z: -1.12, y: 2.34, width: 1.06, height: 0.52 },
    { z: 0.25, y: 2.34, width: 1.04, height: 0.50 }, { z: 0.62, y: 2.16, width: 1.04, height: 0.28 }
  ], m.glass, 28);
  b.box("cargo_floor", "reinforced_bed_floor", [2.35, 0.16, 2.72], [0, 1.02, 2.15], m.dark, [0, 0, 0], 0.04);
  for (const x of [-1.20, 1.20]) b.box("cargo_bed", `bed_side_${x}`, [0.14, 0.68, 2.72], [x, 1.34, 2.15], m.body, [0, 0, 0], 0.04);
  b.box("cargo_bed", "tailgate", [2.46, 0.68, 0.14], [0, 1.34, 3.48], m.body, [0, 0, 0], 0.04);
  b.box("front_bumper", "recovery_bumper", [2.62, 0.34, 0.34], [0, 0.72, -3.92], m.steel);
  b.box("rear_bumper", "recovery_bumper", [2.62, 0.30, 0.32], [0, 0.70, 3.68], m.steel);
  for (const x of [-1.225, 1.225]) {
    b.box("front_doors", `front_${x}`, [0.08, 0.98, 0.92], [x, 1.66, -0.82], m.body, [0, 0, 0], 0.035);
    b.box("rear_doors", `rear_${x}`, [0.08, 0.98, 0.82], [x, 1.66, 0.18], m.body, [0, 0, 0], 0.035);
    b.box("side_glass", `front_window_${x}`, [0.055, 0.48, 0.68], [x * 1.01, 2.15, -0.82], m.glass, [0, 0, 0], 0.035);
    b.box("side_glass", `rear_window_${x}`, [0.055, 0.48, 0.58], [x * 1.01, 2.15, 0.16], m.glass, [0, 0, 0], 0.035);
    b.box("door_hardware", `front_handle_${x}`, [0.045, 0.055, 0.24], [x * 1.035, 1.83, -0.62], m.steel, [0, 0, 0], 0.015);
    b.box("door_hardware", `rear_handle_${x}`, [0.045, 0.055, 0.22], [x * 1.035, 1.83, 0.34], m.steel, [0, 0, 0], 0.015);
    b.box("mirrors", `mirror_${x}`, [0.22, 0.16, 0.30], [x * 1.12, 2.14, -1.22], m.dark, [0, 0, 0], 0.05);
  }
  b.box("power_module", "original_v_powertrain", [1.42, 0.88, 1.20], [0, 1.17, -2.35], m.amber);
  for (const x of [-0.82, 0.82]) b.box("headlamps", `matrix_${x}`, [0.46, 0.16, 0.08], [x, 1.22, -3.70], m.light, [0, 0, x * 0.08], 0.03);
  for (let i = -3; i <= 3; i += 1) b.box("front_grille", `vertical_bar_${i + 4}`, [0.06, 0.46, 0.08], [i * 0.18, 0.98, -3.78], m.dark, [0, 0, 0], 0.015);
  for (const x of [-0.96, 0.96]) b.box("tail_lamps", `vertical_${x}`, [0.18, 0.48, 0.08], [x, 1.40, 3.56], m.redLight, [0, 0, 0], 0.03);
  for (const x of [-1.30, 1.30]) b.cylinder("rock_sliders", `slider_${x}`, 0.065, 3.50, [x, 0.72, -0.10], m.steel, [Math.PI / 2, 0, 0], 20);
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
  for (const x of [-0.64, 0.64]) {
    b.box("cab_glass", `side_${x}`, [0.07, 0.92, 1.06], [x, 2.05, 0.24], m.glass, [0, 0, 0], 0.05);
    b.cylinder("loader_linkage", `pivot_${x}`, 0.10, 0.34, [x, 1.18, -2.72], m.steel, [0, 0, Math.PI / 2], 24);
  }
  for (let i = 0; i < 7; i += 1) b.box("front_bucket", `tooth_${i + 1}`, [0.18, 0.12, 0.38], [-1.05 + i * 0.35, 0.30, -3.78], m.steel, [-0.18, 0, 0], 0.02);
  for (let i = 0; i < 4; i += 1) b.box("rear_bucket", `tooth_${i + 1}`, [0.14, 0.12, 0.30], [-0.30 + i * 0.20, 0.18, 4.54], m.steel, [-0.24, 0, 0], 0.02);
  b.cylinder("hydraulics", "boom_hose_left", 0.025, 2.15, [-0.16, 2.42, 2.20], m.rubber, [0.70, 0, 0], 12);
  b.cylinder("hydraulics", "boom_hose_right", 0.025, 2.15, [0.16, 2.42, 2.20], m.rubber, [0.70, 0, 0], 12);
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
  b.box("cab_glass", "front_screen", [1.58, 0.92, 0.07], [0, 2.18, -0.03], m.glass, [0.10, 0, 0], 0.04);
  for (const x of [-0.68, 0.68]) b.box("cab_glass", `side_${x}`, [0.07, 0.94, 1.02], [x, 2.15, 0.80], m.glass, [0, 0, 0], 0.04);
  for (let i = 0; i < 8; i += 1) b.box("material_bucket", `cutting_tooth_${i + 1}`, [0.20, 0.12, 0.42], [-1.25 + i * 0.36, 0.28, -4.04], m.steel, [-0.20, 0, 0], 0.02);
  b.cylinder("steering_hydraulics", "left_ram", 0.08, 0.92, [-0.58, 0.92, 0], m.cyan, [0, 0, Math.PI / 2], 18);
  b.cylinder("steering_hydraulics", "right_ram", 0.08, 0.92, [0.58, 0.92, 0], m.cyan, [0, 0, Math.PI / 2], 18);
}

function buildRoadConcept(b, mode) {
  const supercar = mode === "apex";
  const ev = mode === "ion";
  roadCore(b, { length: supercar ? 4.8 : 5.2, width: 2.35, height: supercar ? 1.25 : 1.55, wheelbase: 1.55, rear: 1.45 });
  const bodyMaterial = supercar ? m.body : (ev ? m.white : m.dark);
  b.loft("monocoque", "continuous_aero_shell", [
    { z: -2.58, y: 0.72, width: 0.12, height: 0.10 }, { z: -2.34, y: 0.78, width: 0.78, height: 0.20 },
    { z: -1.78, y: 0.88, width: 1.08, height: 0.34 }, { z: -0.92, y: 1.02, width: 1.12, height: 0.48 },
    { z: 0.15, y: 1.08, width: 1.10, height: 0.55 }, { z: 1.12, y: 0.98, width: 1.08, height: 0.42 },
    { z: 2.15, y: 0.84, width: 1.02, height: 0.28 }, { z: 2.54, y: 0.74, width: 0.52, height: 0.14 }
  ], bodyMaterial, 32);
  b.loft("canopy", "single_piece_glasshouse", [
    { z: -1.08, y: 1.32, width: 0.72, height: 0.18 }, { z: -0.56, y: 1.48, width: 0.82, height: 0.40 },
    { z: 0.40, y: 1.50, width: 0.80, height: 0.38 }, { z: 0.98, y: 1.32, width: 0.68, height: 0.16 }
  ], m.glass, 28);
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
  for (const x of [-0.68, 0.68]) {
    b.box("headlamps", `laser_blade_${x}`, [0.50, 0.055, 0.10], [x, 0.96, -2.26], m.light, [0.08, 0, x * 0.18], 0.02);
    b.box("tail_lamps", `light_bar_${x}`, [0.58, 0.06, 0.08], [x, 0.94, 2.20], m.redLight, [-0.05, 0, -x * 0.12], 0.02);
  }
  b.box("front_splitter", "aero_plane", [1.92, 0.06, 0.34], [0, 0.53, -2.37], m.dark, [0, 0, 0], 0.02);
  b.box("rear_diffuser", "venturi_plane", [1.84, 0.08, 0.52], [0, 0.55, 2.25], m.dark, [0.08, 0, 0], 0.02);
}

function buildAircraft(b) {
  b.loft("fuselage", "pressure_shell", [
    { z: -3.72, y: 1.26, width: 0.05, height: 0.05 }, { z: -3.24, y: 1.28, width: 0.42, height: 0.38 },
    { z: -2.35, y: 1.30, width: 0.56, height: 0.54 }, { z: 0.65, y: 1.28, width: 0.58, height: 0.56 },
    { z: 2.55, y: 1.31, width: 0.42, height: 0.40 }, { z: 3.50, y: 1.34, width: 0.08, height: 0.08 }
  ], m.white, 32);
  b.box("cockpit", "canopy", [0.92, 0.48, 1.16], [0, 1.58, -2.24], m.glass, [-0.10, 0, 0], 0.28);
  b.box("left_wing", "lifting_surface", [4.20, 0.12, 1.36], [-2.02, 1.24, -0.10], m.white, [0, -0.12, 0.03], 0.14);
  b.box("right_wing", "lifting_surface", [4.20, 0.12, 1.36], [2.02, 1.24, -0.10], m.white, [0, 0.12, -0.03], 0.14);
  b.box("horizontal_tail", "stabilizer", [3.00, 0.09, 0.72], [0, 1.36, 2.76], m.dark);
  b.box("vertical_tail", "fin", [0.12, 1.34, 1.10], [0, 2.00, 2.70], m.body);
  for (const x of [-1.22, 1.22]) b.cylinder("propulsion", `electric_fan_${x}`, 0.42, 0.72, [x, 1.02, 0.42], m.dark, [Math.PI / 2, 0, 0]);
  for (const [x, z] of [[-0.72, -0.72], [0.72, -0.72], [0, 2.25]]) b.wheel(`landing_gear_${x}_${z}`, x, z, 0.22, 0.14);
  b.box("energy_pack", "distributed_battery", [1.02, 0.18, 2.62], [0, 1.10, 0.12], m.cyan);
  for (const x of [-1.22, 1.22]) {
    b.cylinder("propulsion", `fan_inlet_${x}`, 0.31, 0.76, [x, 1.02, 0.42], m.steel, [Math.PI / 2, 0, 0], 28);
    for (let i = 0; i < 8; i += 1) b.box("propulsion", `fan_${x}_blade_${i}`, [0.04, 0.34, 0.08], [x, 1.02, 0.02], m.dark, [0, i / 8 * Math.PI * 2, 0], 0.01);
  }
  for (let i = 0; i < 5; i += 1) b.box("cockpit", `window_${i + 1}`, [0.22, 0.18, 0.04], [-0.42 + i * 0.21, 1.58, -2.66 + Math.abs(i - 2) * 0.08], m.glass, [-0.08, 0, 0], 0.02);
  b.box("navigation_lights", "left_red", [0.12, 0.08, 0.16], [-4.08, 1.26, -0.18], m.redLight, [0, 0, 0], 0.02);
  b.box("navigation_lights", "right_white", [0.12, 0.08, 0.16], [4.08, 1.26, -0.18], m.light, [0, 0, 0], 0.02);
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
  for (const y of [0.55, 1.35, 2.15, 3.05, 3.85, 5.15, 6.25]) b.cylinder("stage_structure", `ring_${y}`, y > 4.8 ? 0.75 : 0.95, 0.055, [0, y, 0], m.steel, [0, 0, 0], 40);
  for (let i = 0; i < 4; i += 1) {
    const a = i / 4 * Math.PI * 2;
    b.cylinder("rcs", `thruster_${i + 1}`, 0.07, 0.28, [Math.cos(a) * 0.76, 6.32, Math.sin(a) * 0.76], m.dark, [Math.PI / 2, 0, -a], 16);
  }
  b.cylinder("engine_cluster", "central_engine", 0.30, 0.78, [0, 0.05, 0], m.steel, [0, 0, 0], 28);
}

function buildSubmarine(b) {
  b.loft("pressure_hull", "hydrodynamic_shell", [
    { z: -4.05, y: 1.35, width: 0.05, height: 0.05 }, { z: -3.50, y: 1.35, width: 0.72, height: 0.70 },
    { z: -2.65, y: 1.35, width: 1.04, height: 1.00 }, { z: 1.75, y: 1.35, width: 1.06, height: 1.02 },
    { z: 3.25, y: 1.35, width: 0.72, height: 0.70 }, { z: 4.55, y: 1.35, width: 0.10, height: 0.10 }
  ], m.blue, 36);
  b.box("sail", "command_sail", [0.62, 1.10, 1.18], [0, 2.42, -0.25], m.dark, [0, 0, 0], 0.25);
  b.cylinder("periscope", "sensor_mast", 0.10, 1.42, [0, 3.47, -0.28], m.steel, [0, 0, 0], 18);
  b.box("left_dive_plane", "control_surface", [2.22, 0.10, 0.72], [-1.46, 1.42, -1.34], m.steel, [0, 0, 0.05]);
  b.box("right_dive_plane", "control_surface", [2.22, 0.10, 0.72], [1.46, 1.42, -1.34], m.steel, [0, 0, -0.05]);
  b.box("tail_planes", "horizontal_control", [3.10, 0.12, 0.78], [0, 1.40, 3.14], m.steel);
  b.cylinder("propulsor", "rim_drive", 0.70, 0.32, [0, 1.35, 4.88], m.cyan, [Math.PI / 2, 0, 0], 32);
  b.box("battery_modules", "lower_energy_bank", [1.20, 0.42, 3.22], [0, 0.88, -0.20], m.cyan);
  b.box("ballast_left", "variable_tank", [0.42, 0.72, 3.72], [-0.72, 1.05, 0], m.steel);
  b.box("ballast_right", "variable_tank", [0.42, 0.72, 3.72], [0.72, 1.05, 0], m.steel);
  for (let i = 0; i < 7; i += 1) {
    const a = i / 7 * Math.PI * 2;
    b.box("propulsor", `blade_${i + 1}`, [0.08, 0.56, 0.16], [0, 1.35, 4.92], m.steel, [0, 0, a], 0.02);
  }
  for (const z of [-2.20, -1.42, -0.64, 0.14, 0.92]) b.cylinder("hull_frames", `frame_${z}`, 1.08, 0.045, [0, 1.35, z], m.dark, [Math.PI / 2, 0, 0], 40);
  b.mesh("sonar", "bow_array", new THREE.SphereGeometry(0.52, 24, 16), m.cyan, [0, 1.35, -3.66]);
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
  schemaVersion: 2,
  generator: "tools/engine-asset-generator/generate-meet-platforms.mjs",
  warning: "Original MEET high-detail procedural concepts. Visual realism is illustrative; dimensions, materials, physics and manufacturing require engineering validation.",
  platforms: manifest
}, null, 2)}\n`);
console.log(JSON.stringify(manifest, null, 2));
