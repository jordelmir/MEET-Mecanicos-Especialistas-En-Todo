import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as THREE from "three";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

class NodeFileReader {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((value) => this.finish(value)).catch((error) => this.fail(error));
  }
  readAsDataURL(blob) {
    blob.arrayBuffer()
      .then((value) => this.finish(`data:${blob.type};base64,${Buffer.from(value).toString("base64")}`))
      .catch((error) => this.fail(error));
  }
  finish(value) {
    this.result = value;
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
const modelRoot = path.join(repoRoot, "android/app/src/main/assets/models/vehicle_systems");
const authority = "L2_GENERIC_CUTAWAY";

function materials() {
  const material = (name, color, roughness, metalness) =>
    new THREE.MeshStandardMaterial({ name, color, roughness, metalness });
  return {
    cast: material("cast_iron", 0x29343d, 0.48, 0.82),
    steel: material("machined_steel", 0xbcc8d1, 0.22, 0.94),
    darkSteel: material("dark_steel", 0x3d4851, 0.31, 0.88),
    aluminum: material("cast_aluminum", 0x95a4af, 0.39, 0.76),
    copper: material("electrical_copper", 0xc77835, 0.24, 0.78),
    brass: material("brass", 0xb58b3d, 0.28, 0.72),
    black: material("technical_polymer", 0x151b20, 0.64, 0.06),
    rubber: material("automotive_rubber", 0x090d10, 0.86, 0.01),
    red: material("service_red", 0xa91f2c, 0.34, 0.55),
    blue: material("signal_blue", 0x176b9d, 0.31, 0.5),
    cyan: material("diagnostic_cyan", 0x10a7b5, 0.27, 0.48),
    amber: material("high_current_amber", 0xd88a19, 0.32, 0.42),
    green: material("control_green", 0x2f8a67, 0.37, 0.38),
    white: material("ceramic", 0xdce3e6, 0.52, 0.08)
  };
}

class AssemblyBuilder {
  constructor(assetId) {
    this.scene = new THREE.Scene();
    this.scene.name = `MEET_${assetId}_D3`;
    this.root = new THREE.Group();
    this.root.name = `system_root__${assetId}_d3`;
    this.root.userData = { authority, dimensional: false, purpose: "service inspection atlas" };
    this.scene.add(this.root);
    this.materials = materials();
    this.groups = new Map();
    this.partKeys = new Set();
    this.meshCount = 0;
    this.triangleCount = 0;
  }

  group(key) {
    if (!this.groups.has(key)) {
      const group = new THREE.Group();
      group.name = `system_part__${key}`;
      group.userData = { partKey: key, authority, dimensional: false };
      this.root.add(group);
      this.groups.set(key, group);
      this.partKeys.add(key);
    }
    return this.groups.get(key);
  }

  mesh(key, detail, geometry, material, position = [0, 0, 0], rotation = [0, 0, 0], scale = [1, 1, 1]) {
    geometry.computeVertexNormals();
    const mesh = new THREE.Mesh(geometry, material);
    mesh.name = `system_mesh__${key}__${detail}`;
    mesh.position.set(...position);
    mesh.rotation.set(...rotation);
    mesh.scale.set(...scale);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    mesh.userData = { partKey: key, authority, dimensional: false };
    this.group(key).add(mesh);
    const positionAttribute = geometry.getAttribute("position");
    this.triangleCount += geometry.index ? geometry.index.count / 3 : positionAttribute.count / 3;
    this.meshCount += 1;
    return mesh;
  }

  box(key, detail, size, position, material = this.materials.black, radius = 0.08, rotation = [0, 0, 0]) {
    return this.mesh(key, detail, new RoundedBoxGeometry(...size, 3, radius), material, position, rotation);
  }

  cylinder(key, detail, radius, length, position, material = this.materials.steel, rotation = [0, 0, 0], segments = 24) {
    return this.mesh(
      key,
      detail,
      new THREE.CylinderGeometry(radius, radius, length, segments, 1, false),
      material,
      position,
      rotation
    );
  }

  torus(key, detail, radius, tube, position, material = this.materials.steel, rotation = [0, 0, 0]) {
    return this.mesh(key, detail, new THREE.TorusGeometry(radius, tube, 10, 30), material, position, rotation);
  }

  sphere(key, detail, radius, position, material = this.materials.steel, scale = [1, 1, 1]) {
    return this.mesh(key, detail, new THREE.SphereGeometry(radius, 20, 14), material, position, [0, 0, 0], scale);
  }

  tube(key, detail, points, radius, material = this.materials.rubber, segments = 30) {
    const curve = new THREE.CatmullRomCurve3(points.map(([x, y, z]) => new THREE.Vector3(x, y, z)));
    return this.mesh(key, detail, new THREE.TubeGeometry(curve, segments, radius, 8, false), material);
  }

  spring(key, detail, position, radius, length, material = this.materials.darkSteel) {
    const points = [];
    const segments = 72;
    for (let index = 0; index <= segments; index += 1) {
      const t = index / segments;
      const angle = t * Math.PI * 12;
      points.push([
        position[0] + Math.cos(angle) * radius,
        position[1] - length / 2 + t * length,
        position[2] + Math.sin(angle) * radius
      ]);
    }
    return this.tube(key, detail, points, radius * 0.115, material, 72);
  }

  gear(key, detail, teeth, rootRadius, tipRadius, thickness, position, material = this.materials.steel, rotation = [0, Math.PI / 2, 0]) {
    const shape = new THREE.Shape();
    const points = teeth * 4;
    for (let index = 0; index < points; index += 1) {
      const radius = index % 4 === 1 || index % 4 === 2 ? tipRadius : rootRadius;
      const angle = index / points * Math.PI * 2;
      const x = Math.cos(angle) * radius;
      const y = Math.sin(angle) * radius;
      if (index === 0) shape.moveTo(x, y); else shape.lineTo(x, y);
    }
    shape.closePath();
    const geometry = new THREE.ExtrudeGeometry(shape, {
      depth: thickness,
      bevelEnabled: true,
      bevelSegments: 1,
      bevelSize: 0.018,
      bevelThickness: 0.018,
      curveSegments: 2
    }).center();
    return this.mesh(key, detail, geometry, material, position, rotation);
  }

  sensor(key, position, material = this.materials.cyan, rotation = [0, 0, Math.PI / 2]) {
    this.cylinder(key, "threaded_probe", 0.065, 0.32, position, this.materials.steel, rotation, 18);
    this.box(key, "connector", [0.22, 0.16, 0.18], [position[0], position[1] + 0.18, position[2]], material, 0.035);
    this.cylinder(key, "terminal", 0.025, 0.13, [position[0], position[1] + 0.30, position[2]], this.materials.copper, [0, 0, 0], 12);
  }

  solenoid(key, position, material = this.materials.black, rotation = [Math.PI / 2, 0, 0]) {
    this.cylinder(key, "coil_body", 0.13, 0.42, position, material, rotation, 22);
    this.cylinder(key, "valve_spool", 0.055, 0.57, position, this.materials.steel, rotation, 16);
    this.box(key, "connector", [0.18, 0.16, 0.16], [position[0] + 0.15, position[1] + 0.08, position[2]], this.materials.blue, 0.035);
  }

  module(key, position, size = [0.74, 0.18, 0.48], material = this.materials.black) {
    this.box(key, "sealed_case", size, position, material, 0.07);
    const fins = 6;
    for (let index = 0; index < fins; index += 1) {
      const x = position[0] - size[0] * 0.34 + index * size[0] * 0.135;
      this.box(key, `cooling_fin_${index + 1}`, [0.025, 0.12, size[2] * 0.82], [x, position[1] + size[1] * 0.58, position[2]], this.materials.aluminum, 0.008);
    }
    this.box(key, "multipin_socket", [0.24, 0.20, 0.24], [position[0] + size[0] * 0.54, position[1], position[2]], this.materials.blue, 0.035);
  }

  motor(key, position, length = 0.65, radius = 0.24) {
    this.cylinder(key, "motor_case", radius, length, position, this.materials.darkSteel, [0, 0, Math.PI / 2], 28);
    this.cylinder(key, "shaft", radius * 0.28, length + 0.28, position, this.materials.steel, [0, 0, Math.PI / 2], 18);
    this.box(key, "connector", [0.22, 0.18, 0.18], [position[0], position[1] + radius, position[2]], this.materials.black, 0.04);
  }

  bolt(key, detail, position, radius = 0.045, length = 0.16, rotation = [0, 0, 0]) {
    this.cylinder(key, `${detail}_shaft`, radius * 0.52, length, position, this.materials.steel, rotation, 12);
    this.cylinder(key, `${detail}_hex_head`, radius, length * 0.28, [position[0], position[1] + length * 0.55, position[2]], this.materials.darkSteel, rotation, 6);
  }

  bearing(key, detail, position, radius = 0.22, rotation = [0, Math.PI / 2, 0]) {
    this.torus(key, `${detail}_outer_race`, radius, radius * 0.12, position, this.materials.darkSteel, rotation);
    this.torus(key, `${detail}_inner_race`, radius * 0.58, radius * 0.10, position, this.materials.steel, rotation);
    for (let index = 0; index < 8; index += 1) {
      const angle = index / 8 * Math.PI * 2;
      this.sphere(
        key,
        `${detail}_ball_${index + 1}`,
        radius * 0.105,
        [position[0], position[1] + Math.sin(angle) * radius * 0.78, position[2] + Math.cos(angle) * radius * 0.78],
        this.materials.steel
      );
    }
  }

  hoseClamp(key, detail, position, radius, rotation = [0, Math.PI / 2, 0]) {
    this.torus(key, `${detail}_band`, radius, 0.025, position, this.materials.steel, rotation);
    this.box(key, `${detail}_screw_housing`, [0.14, 0.08, 0.07], [position[0], position[1] + radius, position[2]], this.materials.darkSteel, 0.018);
  }

  connectorPins(key, detail, position, columns = 4, rows = 2, spacing = 0.055) {
    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        this.cylinder(
          key,
          `${detail}_pin_${row + 1}_${column + 1}`,
          0.009,
          0.10,
          [position[0] + (column - (columns - 1) / 2) * spacing, position[1], position[2] + (row - (rows - 1) / 2) * spacing],
          this.materials.copper,
          [Math.PI / 2, 0, 0],
          8
        );
      }
    }
  }
}

const intakeKeys = [
  "air_filter_box", "air_filter", "air_box_lid", "intake_duct", "maf_sensor", "throttle_body",
  "tps_sensor", "throttle_actuator", "iac_valve", "map_sensor", "iat_sensor", "intake_manifold",
  "intake_manifold_gasket", "variable_intake_actuator", "variable_intake_solenoid", "vacuum_hoses",
  "turbocharger", "turbo_cold_housing", "turbo_hot_housing", "wastegate_actuator",
  "boost_control_solenoid", "turbo_oil_lines", "turbo_coolant_lines", "charge_hoses", "boost_sensor",
  "blow_off_valve", "bypass_valve", "supercharger", "supercharger_pulley", "supercharger_belt"
];

function buildIntake(b) {
  const m = b.materials;
  b.box("air_filter_box", "lower_shell", [1.45, 0.62, 1.05], [-2.65, 0.1, 0], m.black, 0.18);
  b.cylinder("air_filter", "pleated_core", 0.42, 0.72, [-2.65, 0.34, 0], m.white, [0, 0, Math.PI / 2], 36);
  for (let index = 0; index < 12; index += 1) b.torus("air_filter", `pleat_${index + 1}`, 0.42 - index * 0.008, 0.012, [-2.98 + index * 0.055, 0.34, 0], m.amber, [0, Math.PI / 2, 0]);
  b.box("air_box_lid", "upper_shell", [1.48, 0.16, 1.08], [-2.65, 0.55, 0], m.black, 0.12);
  b.tube("intake_duct", "main_duct", [[-1.95, 0.25, 0], [-1.25, 0.28, -0.18], [-0.62, 0.5, -0.25]], 0.27, m.rubber, 42);
  b.sensor("maf_sensor", [-1.26, 0.58, -0.1]);
  b.cylinder("throttle_body", "bore", 0.42, 0.52, [0.1, 0.65, -0.35], m.aluminum, [0, 0, Math.PI / 2], 36);
  b.cylinder("throttle_body", "butterfly_plate", 0.36, 0.035, [0.1, 0.65, -0.35], m.brass, [0, 0, Math.PI / 2], 30);
  b.sensor("tps_sensor", [0.08, 0.98, -0.35], m.blue);
  b.motor("throttle_actuator", [0.08, 0.25, -0.35], 0.42, 0.17);
  b.solenoid("iac_valve", [0.46, 0.94, 0.05], m.black);
  b.sensor("map_sensor", [1.0, 1.18, 0.1]);
  b.sensor("iat_sensor", [-0.64, 0.62, -0.22], m.green);
  b.box("intake_manifold", "plenum", [2.6, 0.58, 0.72], [1.2, 0.72, 0.15], m.aluminum, 0.19);
  for (const [index, z] of [-0.52, -0.18, 0.18, 0.52].entries()) {
    b.tube("intake_manifold", `runner_${index + 1}`, [[0.35, 0.62, z], [0.8, 0.3, z], [1.55, 0.18, z]], 0.13, m.aluminum, 28);
  }
  b.box("intake_manifold_gasket", "sealing_layer", [2.2, 0.055, 0.82], [1.5, 0.1, 0.15], m.rubber, 0.025);
  b.motor("variable_intake_actuator", [1.95, 1.15, 0.18], 0.42, 0.16);
  b.solenoid("variable_intake_solenoid", [2.55, 1.02, 0.25], m.blue);
  b.tube("vacuum_hoses", "vacuum_network", [[0.4, 1.02, 0.3], [1.1, 1.42, 0.65], [2.4, 1.28, 0.5]], 0.045, m.rubber, 34);

  b.torus("turbocharger", "center_scroll", 0.63, 0.20, [2.5, -0.95, 0], m.darkSteel, [0, Math.PI / 2, 0]);
  b.cylinder("turbocharger", "center_housing", 0.22, 0.8, [2.5, -0.95, 0], m.steel, [0, 0, Math.PI / 2], 28);
  b.torus("turbo_cold_housing", "compressor_scroll", 0.58, 0.22, [2.15, -0.95, 0], m.aluminum, [0, Math.PI / 2, 0]);
  b.torus("turbo_hot_housing", "turbine_scroll", 0.58, 0.23, [2.85, -0.95, 0], m.cast, [0, Math.PI / 2, 0]);
  b.motor("wastegate_actuator", [3.35, -0.58, 0.38], 0.45, 0.14);
  b.solenoid("boost_control_solenoid", [3.3, 0.12, 0.25], m.blue);
  b.tube("turbo_oil_lines", "feed_and_return", [[2.5, -0.65, 0.2], [2.2, -1.7, 0.55], [1.6, -1.9, 0.4]], 0.045, m.brass, 28);
  b.tube("turbo_coolant_lines", "coolant_loop", [[2.6, -0.7, -0.18], [3.2, -1.45, -0.45], [3.5, -0.6, -0.5]], 0.055, m.blue, 28);
  b.tube("charge_hoses", "charge_path", [[2.0, -0.96, -0.45], [0.9, -1.45, -0.72], [-0.2, -0.65, -0.55], [0.1, 0.4, -0.35]], 0.19, m.black, 48);
  b.sensor("boost_sensor", [0.65, -1.35, -0.65], m.cyan);
  b.solenoid("blow_off_valve", [1.2, -1.42, -0.62], m.red);
  b.solenoid("bypass_valve", [1.72, -1.3, -0.5], m.green);
  b.cylinder("supercharger", "rotor_case", 0.48, 1.45, [-1.9, -1.15, 0], m.aluminum, [0, 0, Math.PI / 2], 36);
  for (const z of [-0.16, 0.16]) b.gear("supercharger", `helical_rotor_${z}`, 12, 0.21, 0.29, 1.2, [-1.9, -1.15, z], m.steel, [0, Math.PI / 2, 0]);
  b.gear("supercharger_pulley", "drive_pulley", 22, 0.33, 0.39, 0.18, [-2.7, -1.15, 0], m.darkSteel);
  b.torus("supercharger_belt", "drive_belt", 0.72, 0.055, [-2.82, -0.55, 0], m.rubber, [0, Math.PI / 2, 0]);
}

const transmissionKeys = [
  "torque_converter", "transmission_oil_pump", "valve_body", "shift_solenoids", "tcc_solenoid",
  "pressure_solenoid", "input_shaft", "output_shaft", "planetary_gearset", "clutch_packs",
  "transmission_filter", "transmission_pan", "atf_cooler_lines", "atf_temperature_sensor",
  "input_speed_sensor", "output_speed_sensor", "range_sensor", "tcm", "internal_harness",
  "bulkhead_connector", "differential", "left_axle", "right_axle", "outer_cv_joints",
  "inner_cv_joints", "cv_boots", "axle_seals"
];

function buildTransmission(b) {
  const m = b.materials;
  b.cylinder("torque_converter", "impeller_shell", 0.88, 0.48, [-2.15, 0.25, 0], m.darkSteel, [0, 0, Math.PI / 2], 48);
  b.torus("torque_converter", "welded_seam", 0.72, 0.08, [-2.4, 0.25, 0], m.steel, [0, Math.PI / 2, 0]);
  for (let index = 0; index < 12; index += 1) b.box("torque_converter", `turbine_vane_${index + 1}`, [0.06, 0.42, 0.16], [-2.14, 0.25 + Math.sin(index / 12 * Math.PI * 2) * 0.45, Math.cos(index / 12 * Math.PI * 2) * 0.45], m.aluminum, 0.018, [index / 12 * Math.PI * 2, 0, 0]);
  b.gear("transmission_oil_pump", "outer_gear", 18, 0.36, 0.43, 0.16, [-1.5, 0.25, 0], m.steel);
  b.gear("transmission_oil_pump", "inner_gear", 12, 0.20, 0.29, 0.19, [-1.5, 0.25, 0], m.brass);
  b.box("valve_body", "hydraulic_plate", [2.4, 0.22, 1.25], [0.15, -1.0, 0], m.aluminum, 0.08);
  for (let index = 0; index < 7; index += 1) b.tube("valve_body", `fluid_channel_${index + 1}`, [[-0.9 + index * 0.3, -0.86, -0.45], [-0.75 + index * 0.25, -0.82, 0], [-0.85 + index * 0.3, -0.86, 0.46]], 0.025, m.brass, 16);
  for (const x of [-0.7, -0.25, 0.2, 0.65]) b.solenoid("shift_solenoids", [x, -1.32, -0.35], m.blue);
  b.solenoid("tcc_solenoid", [0.9, -1.32, 0.35], m.red);
  b.solenoid("pressure_solenoid", [-0.95, -1.32, 0.35], m.green);
  b.cylinder("input_shaft", "splined_shaft", 0.11, 3.7, [0, 0.3, 0], m.steel, [0, 0, Math.PI / 2], 28);
  b.cylinder("output_shaft", "output_shaft", 0.14, 2.2, [0.95, 0.55, 0], m.steel, [0, 0, Math.PI / 2], 28);
  b.gear("planetary_gearset", "sun_gear", 18, 0.25, 0.34, 0.25, [0.1, 0.3, 0], m.brass);
  for (let index = 0; index < 4; index += 1) {
    const angle = index / 4 * Math.PI * 2;
    b.gear("planetary_gearset", `planet_${index + 1}`, 14, 0.18, 0.25, 0.22, [0.1, 0.3 + Math.cos(angle) * 0.55, Math.sin(angle) * 0.55], m.steel);
  }
  b.torus("planetary_gearset", "ring_gear", 0.82, 0.10, [0.1, 0.3, 0], m.darkSteel, [0, Math.PI / 2, 0]);
  for (let index = 0; index < 7; index += 1) b.cylinder("clutch_packs", `friction_disc_${index + 1}`, 0.66, 0.045, [0.85 + index * 0.07, 0.28, 0], index % 2 ? m.brass : m.darkSteel, [0, 0, Math.PI / 2], 38);
  b.box("transmission_filter", "filter_body", [1.1, 0.16, 0.62], [0.1, -1.48, 0], m.black, 0.08);
  b.box("transmission_pan", "service_pan", [2.7, 0.38, 1.55], [0.1, -1.82, 0], m.darkSteel, 0.14);
  b.tube("atf_cooler_lines", "supply", [[0.8, -1.2, 0.55], [1.8, -0.8, 1.0], [2.5, -0.2, 1.1]], 0.045, m.brass, 32);
  b.tube("atf_cooler_lines", "return", [[0.65, -1.25, 0.42], [1.7, -0.95, 0.75], [2.5, -0.35, 0.82]], 0.045, m.brass, 32);
  b.sensor("atf_temperature_sensor", [-0.55, -1.3, 0.48], m.amber);
  b.sensor("input_speed_sensor", [-0.85, 0.92, -0.48]);
  b.sensor("output_speed_sensor", [0.95, 0.96, -0.48]);
  b.sensor("range_sensor", [-1.05, 0.85, 0.48], m.green);
  b.module("tcm", [-1.7, 1.55, 0.65], [0.9, 0.16, 0.55], m.black);
  b.tube("internal_harness", "branch_network", [[-1.5, 1.25, 0.55], [-0.6, 0.5, 0.75], [0.2, -0.9, 0.58], [1.0, -1.25, 0.4]], 0.055, m.black, 40);
  b.box("bulkhead_connector", "sealed_connector", [0.32, 0.25, 0.38], [-1.38, -0.55, 0.73], m.blue, 0.05);
  b.gear("differential", "ring_gear", 34, 0.66, 0.78, 0.22, [1.65, 0.25, 0], m.darkSteel);
  b.gear("differential", "side_gear", 18, 0.27, 0.35, 0.28, [1.65, 0.25, 0], m.steel);
  b.cylinder("left_axle", "shaft", 0.09, 2.2, [2.75, 0.25, 0], m.steel, [0, 0, Math.PI / 2], 24);
  b.cylinder("right_axle", "shaft", 0.09, 2.2, [-3.25, 0.25, 0], m.steel, [0, 0, Math.PI / 2], 24);
  for (const [key, x] of [["outer_cv_joints", -4.25], ["outer_cv_joints", 3.85], ["inner_cv_joints", -2.2], ["inner_cv_joints", 2.05]]) b.sphere(key, `joint_${x}`, 0.30, [x, 0.25, 0], m.darkSteel, [1.15, 1, 1]);
  for (const [index, x] of [-3.9, -2.45, 2.25, 3.5].entries()) {
    for (let ring = 0; ring < 4; ring += 1) b.torus("cv_boots", `boot_${index + 1}_rib_${ring + 1}`, 0.22 - ring * 0.025, 0.035, [x + ring * 0.10 * Math.sign(x), 0.25, 0], m.rubber, [0, Math.PI / 2, 0]);
  }
  b.torus("axle_seals", "left_seal", 0.15, 0.035, [-2.02, 0.25, 0], m.red, [0, Math.PI / 2, 0]);
  b.torus("axle_seals", "right_seal", 0.15, 0.035, [2.02, 0.25, 0], m.red, [0, Math.PI / 2, 0]);
}

const suspensionKeys = [
  "front_struts", "front_coil_springs", "upper_strut_mounts", "strut_bearings", "bump_stops",
  "strut_dust_boots", "lower_control_arms", "control_arm_bushings", "lower_ball_joints",
  "front_stabilizer_bar", "stabilizer_bushings", "stabilizer_links", "rear_shocks", "rear_springs",
  "torsion_beam", "trailing_arms", "lateral_arms", "longitudinal_arms", "rear_stabilizer_links",
  "rear_hubs", "rear_bearings"
];

function arm(b, key, detail, points, width = 0.10) {
  b.tube(key, detail, points, width, b.materials.darkSteel, 24);
}

function buildSuspension(b) {
  const m = b.materials;
  for (const [side, x] of [["left", -1.55], ["right", 1.55]]) {
    b.cylinder("front_struts", `${side}_damper_body`, 0.15, 1.55, [x, 0.65, -1.1], m.darkSteel, [0, 0, 0], 28);
    b.cylinder("front_struts", `${side}_piston_rod`, 0.055, 1.85, [x, 1.05, -1.1], m.steel, [0, 0, 0], 20);
    b.spring("front_coil_springs", `${side}_spring`, [x, 1.05, -1.1], 0.34, 1.2);
    b.cylinder("upper_strut_mounts", `${side}_mount`, 0.38, 0.16, [x, 1.95, -1.1], m.black, [0, 0, 0], 32);
    b.torus("strut_bearings", `${side}_bearing`, 0.25, 0.06, [x, 1.82, -1.1], m.steel, [Math.PI / 2, 0, 0]);
    b.cylinder("bump_stops", `${side}_stop`, 0.12, 0.24, [x, 1.42, -1.1], m.amber, [0, 0, 0], 18);
    for (let ring = 0; ring < 5; ring += 1) b.torus("strut_dust_boots", `${side}_boot_${ring + 1}`, 0.16 - ring * 0.012, 0.035, [x, 1.15 - ring * 0.11, -1.1], m.rubber, [Math.PI / 2, 0, 0]);
    arm(b, "lower_control_arms", `${side}_front_leg`, [[x * 0.35, -0.25, -0.55], [x * 0.65, -0.55, -0.9], [x, -0.6, -1.12]], 0.13);
    arm(b, "lower_control_arms", `${side}_rear_leg`, [[x * 0.25, -0.25, -1.35], [x * 0.72, -0.55, -1.2], [x, -0.6, -1.12]], 0.13);
    for (const [index, z] of [-0.55, -1.35].entries()) b.torus("control_arm_bushings", `${side}_${index + 1}`, 0.16, 0.055, [x * 0.3, -0.25, z], m.rubber, [0, Math.PI / 2, 0]);
    b.sphere("lower_ball_joints", `${side}_joint`, 0.17, [x, -0.58, -1.12], m.steel);
    b.cylinder("stabilizer_links", `${side}_link`, 0.055, 0.92, [x * 0.92, 0.05, -0.45], m.steel);
    b.sphere("stabilizer_links", `${side}_upper_joint`, 0.11, [x * 0.92, 0.5, -0.45], m.darkSteel);
    b.sphere("stabilizer_links", `${side}_lower_joint`, 0.11, [x * 0.92, -0.4, -0.45], m.darkSteel);

    b.cylinder("rear_shocks", `${side}_shock`, 0.12, 1.25, [x, 0.45, 1.2], m.darkSteel);
    b.spring("rear_springs", `${side}_spring`, [x * 0.78, 0.35, 1.2], 0.30, 1.05);
    arm(b, "trailing_arms", `${side}_arm`, [[x * 0.75, -0.45, 0.1], [x, -0.55, 0.85], [x, -0.5, 1.45]], 0.16);
    arm(b, "lateral_arms", `${side}_arm`, [[0.1, -0.35, 1.1], [x * 0.6, -0.5, 1.35], [x, -0.5, 1.45]], 0.11);
    arm(b, "longitudinal_arms", `${side}_arm`, [[x * 0.85, -0.35, 0.45], [x, -0.5, 1.45]], 0.12);
    b.cylinder("rear_stabilizer_links", `${side}_link`, 0.05, 0.58, [x * 0.9, -0.1, 1.65], m.steel);
    b.cylinder("rear_hubs", `${side}_hub`, 0.28, 0.34, [x, -0.45, 1.55], m.darkSteel, [0, 0, Math.PI / 2], 30);
    b.torus("rear_bearings", `${side}_bearing`, 0.21, 0.06, [x, -0.45, 1.55], m.steel, [0, Math.PI / 2, 0]);
  }
  b.tube("front_stabilizer_bar", "u_bar", [[-1.65, -0.38, -0.4], [-0.8, -0.62, -0.15], [0, -0.65, -0.05], [0.8, -0.62, -0.15], [1.65, -0.38, -0.4]], 0.075, m.darkSteel, 40);
  for (const x of [-0.62, 0.62]) b.torus("stabilizer_bushings", `bushing_${x}`, 0.11, 0.045, [x, -0.64, -0.08], m.rubber, [Math.PI / 2, 0, 0]);
  b.cylinder("torsion_beam", "cross_beam", 0.18, 2.85, [0, -0.55, 1.35], m.darkSteel, [0, 0, Math.PI / 2], 28);
}

const steeringKeys = [
  "steering_wheel", "steering_column", "steering_u_joint", "steering_rack", "rack_pinion",
  "inner_tie_rods", "outer_tie_rods", "rack_boots", "power_steering_pump",
  "power_steering_reservoir", "steering_hoses", "brake_pedal", "brake_booster", "master_cylinder",
  "brake_fluid_reservoir", "brake_lines", "front_discs", "front_calipers", "brake_pads",
  "rear_drum_context", "abs_module", "abs_pump", "abs_solenoids", "wheels_tires",
  "wheel_air_valves", "tpms_sensors", "wheel_center_caps"
];

function buildSteeringBrakes(b) {
  const m = b.materials;
  b.torus("steering_wheel", "rim", 0.52, 0.075, [0, 1.65, -1.4], m.black, [Math.PI / 2, 0, 0]);
  for (const angle of [0, 2.1, 4.2]) b.box("steering_wheel", `spoke_${angle}`, [0.42, 0.07, 0.09], [Math.cos(angle) * 0.2, 1.65 + Math.sin(angle) * 0.2, -1.4], m.darkSteel, 0.025, [0, 0, angle]);
  b.cylinder("steering_column", "column", 0.09, 1.65, [0, 0.9, -0.8], m.steel, [Math.PI / 3, 0, 0], 24);
  b.cylinder("steering_u_joint", "cross_x", 0.07, 0.42, [0, 0.18, -0.35], m.steel, [0, 0, Math.PI / 2], 18);
  b.cylinder("steering_u_joint", "cross_y", 0.07, 0.42, [0, 0.18, -0.35], m.steel, [Math.PI / 2, 0, 0], 18);
  b.cylinder("steering_rack", "rack_housing", 0.16, 2.65, [0, -0.25, -0.3], m.aluminum, [0, 0, Math.PI / 2], 30);
  b.gear("rack_pinion", "pinion", 14, 0.17, 0.24, 0.20, [0, -0.05, -0.3], m.steel, [Math.PI / 2, 0, 0]);
  for (const [side, sign] of [["left", -1], ["right", 1]]) {
    b.cylinder("inner_tie_rods", `${side}_rod`, 0.055, 1.15, [sign * 1.65, -0.25, -0.3], m.steel, [0, 0, Math.PI / 2], 18);
    b.cylinder("outer_tie_rods", `${side}_rod`, 0.06, 0.72, [sign * 2.55, -0.25, -0.3], m.steel, [0, 0, Math.PI / 2], 18);
    b.sphere("outer_tie_rods", `${side}_joint`, 0.13, [sign * 2.9, -0.25, -0.3], m.darkSteel);
    for (let ring = 0; ring < 5; ring += 1) b.torus("rack_boots", `${side}_rib_${ring + 1}`, 0.18 - ring * 0.012, 0.035, [sign * (1.25 + ring * 0.12), -0.25, -0.3], m.rubber, [0, Math.PI / 2, 0]);
  }
  b.motor("power_steering_pump", [-2.2, 1.25, 0.2], 0.58, 0.28);
  b.cylinder("power_steering_reservoir", "reservoir", 0.26, 0.58, [-2.55, 1.85, 0.25], m.black, [0, 0, 0], 28);
  b.tube("steering_hoses", "pressure", [[-2.2, 1.05, 0.25], [-1.4, 0.5, 0.4], [-0.7, -0.2, -0.2]], 0.045, m.red, 34);
  b.tube("steering_hoses", "return", [[-2.45, 1.55, 0.25], [-1.2, 0.25, 0.6], [0.6, -0.2, -0.1]], 0.05, m.black, 34);
  b.box("brake_pedal", "pedal_arm", [0.12, 0.82, 0.12], [0.65, 1.25, -1.1], m.darkSteel, 0.03, [0, 0, -0.25]);
  b.box("brake_pedal", "pedal_pad", [0.42, 0.12, 0.24], [0.78, 0.84, -1.1], m.rubber, 0.05);
  b.cylinder("brake_booster", "vacuum_shell", 0.58, 0.34, [0.72, 1.35, 0.55], m.darkSteel, [Math.PI / 2, 0, 0], 40);
  b.cylinder("master_cylinder", "hydraulic_bore", 0.14, 0.78, [0.72, 1.35, 0.95], m.aluminum, [Math.PI / 2, 0, 0], 28);
  b.box("brake_fluid_reservoir", "reservoir", [0.48, 0.34, 0.30], [0.72, 1.75, 0.98], m.white, 0.08);
  b.tube("brake_lines", "front_split", [[0.72, 1.2, 1.25], [0, 0.5, 1.1], [-2.7, -0.2, -0.75]], 0.028, m.copper, 40);
  b.tube("brake_lines", "rear_split", [[0.72, 1.15, 1.18], [0, 0.1, 1.4], [2.7, -0.2, 0.75]], 0.028, m.copper, 40);
  for (const [index, [x, z]] of [[-2.65, -0.85], [2.65, -0.85], [-2.65, 0.95], [2.65, 0.95]].entries()) {
    b.torus("wheels_tires", `tire_${index + 1}`, 0.62, 0.20, [x, -0.35, z], m.rubber, [0, Math.PI / 2, 0]);
    b.torus("wheels_tires", `rim_${index + 1}`, 0.40, 0.07, [x, -0.35, z], m.aluminum, [0, Math.PI / 2, 0]);
    for (let spoke = 0; spoke < 6; spoke += 1) {
      const angle = spoke / 6 * Math.PI * 2;
      b.box("wheels_tires", `wheel_${index + 1}_spoke_${spoke + 1}`, [0.06, 0.50, 0.05], [x, -0.35 + Math.sin(angle) * 0.16, z + Math.cos(angle) * 0.16], m.steel, 0.018, [angle, 0, Math.PI / 2]);
    }
    b.cylinder("wheel_air_valves", `valve_${index + 1}`, 0.025, 0.20, [x, 0.12, z + 0.28], m.brass, [0, 0, 0], 12);
    b.box("tpms_sensors", `sensor_${index + 1}`, [0.16, 0.08, 0.08], [x, 0.02, z + 0.28], m.cyan, 0.025);
    b.cylinder("wheel_center_caps", `cap_${index + 1}`, 0.12, 0.05, [x, -0.35, z], m.black, [0, 0, Math.PI / 2], 24);
  }
  for (const [side, x] of [["left", -2.65], ["right", 2.65]]) {
    b.cylinder("front_discs", `${side}_disc`, 0.42, 0.07, [x, -0.35, -0.85], m.steel, [0, 0, Math.PI / 2], 40);
    for (let hole = 0; hole < 12; hole += 1) {
      const angle = hole / 12 * Math.PI * 2;
      b.cylinder("front_discs", `${side}_vent_${hole + 1}`, 0.02, 0.10, [x, -0.35 + Math.sin(angle) * 0.30, -0.85 + Math.cos(angle) * 0.30], m.black, [0, 0, Math.PI / 2], 10);
    }
    b.box("front_calipers", `${side}_caliper`, [0.25, 0.52, 0.30], [x, -0.08, -0.85], m.red, 0.10);
    b.box("brake_pads", `${side}_inner_pad`, [0.08, 0.36, 0.18], [x - Math.sign(x) * 0.08, -0.08, -0.85], m.darkSteel, 0.035);
    b.box("brake_pads", `${side}_outer_pad`, [0.08, 0.36, 0.18], [x + Math.sign(x) * 0.08, -0.08, -0.85], m.darkSteel, 0.035);
  }
  for (const [side, x] of [["left", -2.65], ["right", 2.65]]) {
    b.cylinder("rear_drum_context", `${side}_drum`, 0.40, 0.28, [x, -0.35, 0.95], m.darkSteel, [0, 0, Math.PI / 2], 40);
  }
  b.module("abs_module", [1.8, 0.75, 1.1], [0.72, 0.22, 0.55], m.aluminum);
  b.motor("abs_pump", [2.35, 0.55, 1.1], 0.52, 0.20);
  for (let index = 0; index < 6; index += 1) b.solenoid("abs_solenoids", [1.55 + index * 0.14, 0.45, 1.25], m.blue, [0, 0, 0]);
}

const electricalKeys = [
  "battery", "positive_terminal", "negative_terminal", "main_positive_cable", "main_negative_cable",
  "engine_ground", "chassis_ground", "main_fuse", "fusible_link", "engine_fuse_box",
  "interior_fuse_box", "blade_fuses", "iso_relays", "main_relay", "ignition_relay", "starter_relay",
  "alternator", "starter_motor", "engine_harness", "injector_harness", "coil_harness",
  "transmission_harness", "abs_harness", "sensor_harness", "multipin_connectors", "ecm", "tcm",
  "abs_controller", "ckp_sensor", "cmp_sensor", "maf_sensor", "map_sensor", "ect_sensor",
  "oxygen_sensors", "knock_sensor", "injectors", "vvt_solenoid", "evap_purge_solenoid",
  "radiator_fan", "fuel_pump", "transmission_solenoids", "abs_pump"
];

function buildElectrical(b) {
  const m = b.materials;
  b.box("battery", "case", [1.25, 0.78, 0.72], [-2.7, 1.15, 0], m.black, 0.12);
  b.box("battery", "top", [1.28, 0.12, 0.74], [-2.7, 1.58, 0], m.darkSteel, 0.06);
  b.cylinder("positive_terminal", "positive_post", 0.09, 0.18, [-3.08, 1.72, -0.22], m.copper, [0, 0, 0], 18);
  b.cylinder("negative_terminal", "negative_post", 0.09, 0.18, [-2.32, 1.72, 0.22], m.steel, [0, 0, 0], 18);
  b.tube("main_positive_cable", "b_plus", [[-3.08, 1.78, -0.22], [-2.2, 2.15, -0.65], [-1.2, 1.7, -0.75]], 0.065, m.red, 36);
  b.tube("main_negative_cable", "negative", [[-2.32, 1.78, 0.22], [-1.7, 2.1, 0.68], [-0.8, 1.6, 0.8]], 0.065, m.black, 36);
  b.tube("engine_ground", "engine_strap", [[-0.8, 1.6, 0.8], [-0.25, 1.3, 0.62], [0.2, 1.45, 0.5]], 0.05, m.copper, 24);
  b.tube("chassis_ground", "chassis_strap", [[-1.5, 2.05, 0.7], [-1.0, 2.35, 1.0], [-0.2, 2.2, 1.05]], 0.05, m.copper, 24);
  b.box("main_fuse", "high_current_fuse", [0.42, 0.18, 0.24], [-1.55, 1.82, -0.75], m.amber, 0.04);
  b.box("fusible_link", "link", [0.5, 0.12, 0.16], [-1.05, 1.76, -0.75], m.red, 0.03);
  for (const [key, x] of [["engine_fuse_box", -0.35], ["interior_fuse_box", 0.85]]) {
    b.box(key, "junction_box", [1.0, 0.34, 0.82], [x, 1.55, -0.55], m.black, 0.10);
    b.box(key, "service_lid", [1.02, 0.08, 0.84], [x, 1.77, -0.55], m.darkSteel, 0.06);
  }
  for (let index = 0; index < 12; index += 1) b.box("blade_fuses", `fuse_${index + 1}`, [0.10, 0.22, 0.07], [-0.7 + index * 0.13, 1.82, -0.75 + (index % 2) * 0.38], index % 3 === 0 ? m.red : index % 3 === 1 ? m.blue : m.amber, 0.018);
  for (let index = 0; index < 5; index += 1) b.box("iso_relays", `relay_${index + 1}`, [0.22, 0.28, 0.22], [-0.55 + index * 0.34, 1.90, -0.45], m.black, 0.035);
  for (const [key, x, mat] of [["main_relay", 1.75, m.green], ["ignition_relay", 2.1, m.blue], ["starter_relay", 2.45, m.red]]) b.box(key, "relay_case", [0.26, 0.34, 0.26], [x, 1.55, -0.45], mat, 0.04);
  b.motor("alternator", [-2.2, -1.2, -0.2], 0.75, 0.36);
  b.torus("alternator", "stator_winding", 0.25, 0.06, [-2.2, -1.2, -0.2], m.copper, [0, Math.PI / 2, 0]);
  b.motor("starter_motor", [-1.15, -1.35, -0.2], 0.82, 0.28);
  b.cylinder("starter_motor", "solenoid", 0.14, 0.55, [-1.15, -1.08, -0.2], m.black, [0, 0, Math.PI / 2], 20);
  const harnesses = [
    ["engine_harness", m.black, [[-0.7, 0.6, 0.2], [0, 0.2, 0.7], [1.1, 0.5, 0.5], [2.4, 0.1, 0.7]]],
    ["injector_harness", m.red, [[-0.1, 0.2, 0.7], [0.5, -0.4, 0.8], [1.5, -0.45, 0.8]]],
    ["coil_harness", m.amber, [[0.2, 0.3, 0.55], [0.8, 0.8, 0.65], [1.8, 0.75, 0.65]]],
    ["transmission_harness", m.green, [[0, 0.15, 0.75], [0.6, -0.9, 1.1], [1.7, -1.25, 1.0]]],
    ["abs_harness", m.blue, [[-0.3, 0.35, 0.65], [1.2, 1.0, 1.15], [2.3, 0.8, 1.15]]],
    ["sensor_harness", m.cyan, [[0.1, 0.45, 0.5], [1.6, 1.35, 0.95], [3.0, 1.0, 0.8]]]
  ];
  for (const [key, mat, points] of harnesses) b.tube(key, "loom", points, 0.055, mat, 40);
  for (let index = 0; index < 4; index += 1) b.box("multipin_connectors", `connector_${index + 1}`, [0.28, 0.22, 0.32], [2.65 + index * 0.32, 0.7 - index * 0.25, 0.7], m.blue, 0.04);
  b.module("ecm", [-2.25, 0.1, -0.85], [0.92, 0.18, 0.62], m.aluminum);
  b.module("tcm", [-1.05, 0.1, -0.85], [0.82, 0.18, 0.55], m.black);
  b.module("abs_controller", [0.05, 0.1, -0.85], [0.82, 0.18, 0.55], m.aluminum);
  const sensors = [
    ["ckp_sensor", 0.95, m.cyan], ["cmp_sensor", 1.35, m.green], ["maf_sensor", 1.75, m.blue],
    ["map_sensor", 2.15, m.cyan], ["ect_sensor", 2.55, m.green], ["oxygen_sensors", 2.95, m.white],
    ["knock_sensor", 3.35, m.amber]
  ];
  for (const [key, x, mat] of sensors) b.sensor(key, [x, 0.15, -0.75], mat);
  for (let index = 0; index < 4; index += 1) {
    const x = 0.65 + index * 0.34;
    b.cylinder("injectors", `injector_${index + 1}`, 0.075, 0.50, [x, -1.0, -0.8], m.steel, [0, 0, 0], 18);
    b.box("injectors", `connector_${index + 1}`, [0.16, 0.15, 0.16], [x + 0.11, -0.85, -0.8], m.black, 0.03);
  }
  b.solenoid("vvt_solenoid", [2.15, -1.0, -0.8], m.green, [0, 0, 0]);
  b.solenoid("evap_purge_solenoid", [2.65, -1.0, -0.8], m.blue, [0, 0, 0]);
  b.cylinder("radiator_fan", "shroud", 0.62, 0.12, [3.15, -0.65, 0.05], m.black, [Math.PI / 2, 0, 0], 40);
  b.motor("radiator_fan", [3.15, -0.65, 0.05], 0.28, 0.16);
  for (let index = 0; index < 7; index += 1) {
    const angle = index / 7 * Math.PI * 2;
    b.box("radiator_fan", `blade_${index + 1}`, [0.08, 0.45, 0.05], [3.15 + Math.cos(angle) * 0.25, -0.65, 0.05 + Math.sin(angle) * 0.25], m.black, 0.025, [0, angle, Math.PI / 2]);
  }
  b.motor("fuel_pump", [3.25, -1.45, -0.7], 0.48, 0.18);
  for (let index = 0; index < 4; index += 1) b.solenoid("transmission_solenoids", [0.8 + index * 0.35, -1.8, 0.75], m.blue);
  b.motor("abs_pump", [2.7, -1.75, 0.75], 0.52, 0.20);
}

function enhanceIntake(b) {
  const m = b.materials;
  for (const [index, [x, z]] of [[-3.18, -0.43], [-2.12, -0.43], [-3.18, 0.43], [-2.12, 0.43]].entries()) {
    b.box("air_box_lid", `spring_clip_${index + 1}`, [0.10, 0.30, 0.07], [x, 0.48, z], m.steel, 0.018);
  }
  b.cylinder("throttle_body", "butterfly_shaft", 0.035, 0.86, [0.1, 0.65, -0.35], m.steel, [Math.PI / 2, 0, 0], 14);
  b.torus("throttle_body", "shaft_return_spring", 0.11, 0.018, [0.1, 0.65, 0.02], m.darkSteel, [Math.PI / 2, 0, 0]);
  b.hoseClamp("intake_duct", "airbox_clamp", [-1.94, 0.25, 0], 0.29);
  b.hoseClamp("intake_duct", "throttle_clamp", [-0.60, 0.50, -0.25], 0.29);
  b.hoseClamp("charge_hoses", "compressor_clamp", [1.98, -0.96, -0.45], 0.21);
  b.hoseClamp("charge_hoses", "throttle_clamp", [0.10, 0.39, -0.35], 0.21);
  b.cylinder("turbocharger", "common_shaft", 0.055, 1.12, [2.5, -0.95, 0], m.steel, [0, 0, Math.PI / 2], 18);
  b.bearing("turbocharger", "journal_bearing", [2.5, -0.95, 0], 0.18);
  for (const [key, x, material, reverse] of [
    ["turbo_cold_housing", 2.10, m.aluminum, 1],
    ["turbo_hot_housing", 2.90, m.darkSteel, -1]
  ]) {
    b.cylinder(key, "impeller_hub", 0.13, 0.18, [x, -0.95, 0], material, [0, 0, Math.PI / 2], 24);
    for (let index = 0; index < 11; index += 1) {
      const angle = index / 11 * Math.PI * 2;
      b.box(
        key,
        `curved_blade_${index + 1}`,
        [0.12, 0.34, 0.035],
        [x, -0.95 + Math.sin(angle) * 0.21, Math.cos(angle) * 0.21],
        material,
        0.015,
        [angle * reverse, 0, Math.PI / 2]
      );
    }
  }
  b.cylinder("wastegate_actuator", "diaphragm_can", 0.23, 0.18, [3.35, -0.58, 0.38], m.aluminum, [0, 0, Math.PI / 2], 28);
  b.cylinder("wastegate_actuator", "actuator_rod", 0.025, 0.72, [3.08, -0.82, 0.30], m.steel, [0, 0, Math.PI / 3], 12);
  for (const x of [-2.55, -1.90, -1.25]) b.bearing("supercharger", `rotor_support_${x}`, [x, -1.15, 0], 0.20);
}

function enhanceTransmission(b) {
  const m = b.materials;
  b.cylinder("torque_converter", "turbine_hub", 0.22, 0.72, [-2.15, 0.25, 0], m.steel, [0, 0, Math.PI / 2], 26);
  b.bearing("torque_converter", "stator_one_way_clutch", [-1.92, 0.25, 0], 0.30);
  for (const x of [-1.15, -0.85, 1.65]) b.gear("input_shaft", `spline_${x}`, 20, 0.11, 0.15, 0.16, [x, 0.3, 0], m.steel);
  for (const x of [0.45, 1.25, 1.95]) b.gear("output_shaft", `spline_${x}`, 22, 0.14, 0.18, 0.16, [x, 0.55, 0], m.steel);
  for (let row = 0; row < 3; row += 1) {
    for (let column = 0; column < 7; column += 1) {
      b.bolt("valve_body", `separator_bolt_${row + 1}_${column + 1}`, [-0.75 + column * 0.26, -0.80, -0.42 + row * 0.42], 0.035, 0.11);
    }
  }
  for (let index = 0; index < 8; index += 1) {
    const angle = index / 8 * Math.PI * 2;
    b.box("clutch_packs", `friction_tab_${index + 1}`, [0.10, 0.12, 0.08], [1.05, 0.28 + Math.sin(angle) * 0.68, Math.cos(angle) * 0.68], m.brass, 0.018);
  }
  b.gear("differential", "left_spider_gear", 12, 0.17, 0.24, 0.20, [1.65, 0.55, 0], m.steel, [Math.PI / 2, 0, 0]);
  b.gear("differential", "right_spider_gear", 12, 0.17, 0.24, 0.20, [1.65, -0.05, 0], m.steel, [Math.PI / 2, 0, 0]);
  for (const [joint, x] of [["outer_left", -4.25], ["inner_left", -2.2], ["inner_right", 2.05], ["outer_right", 3.85]]) {
    const key = joint.startsWith("outer") ? "outer_cv_joints" : "inner_cv_joints";
    for (let index = 0; index < 6; index += 1) {
      const angle = index / 6 * Math.PI * 2;
      b.sphere(key, `${joint}_ball_${index + 1}`, 0.055, [x, 0.25 + Math.sin(angle) * 0.22, Math.cos(angle) * 0.22], m.steel);
    }
  }
  for (let index = 0; index < 14; index += 1) {
    const angle = index / 14 * Math.PI * 2;
    b.bolt("transmission_pan", `pan_bolt_${index + 1}`, [0.1 + Math.cos(angle) * 1.22, -1.58, Math.sin(angle) * 0.64], 0.038, 0.13);
  }
  b.connectorPins("bulkhead_connector", "sealed_terminals", [-1.38, -0.39, 0.73], 4, 3);
}

function enhanceSuspension(b) {
  const m = b.materials;
  for (const [side, x] of [["left", -1.55], ["right", 1.55]]) {
    b.torus("front_struts", `${side}_lower_spring_seat`, 0.36, 0.055, [x, 0.55, -1.1], m.darkSteel, [Math.PI / 2, 0, 0]);
    b.torus("upper_strut_mounts", `${side}_rubber_isolator`, 0.29, 0.075, [x, 1.91, -1.1], m.rubber, [Math.PI / 2, 0, 0]);
    b.bolt("upper_strut_mounts", `${side}_shaft_nut`, [x, 2.08, -1.1], 0.075, 0.18);
    b.box("front_struts", `${side}_knuckle_bracket`, [0.42, 0.52, 0.16], [x, -0.05, -1.1], m.darkSteel, 0.055);
    for (const y of [-0.18, 0.10]) b.bolt("front_struts", `${side}_knuckle_bolt_${y}`, [x, y, -1.22], 0.055, 0.24, [Math.PI / 2, 0, 0]);
    b.box("lower_control_arms", `${side}_stamped_reinforcement`, [0.58, 0.08, 0.44], [x * 0.58, -0.53, -1.02], m.cast, 0.08, [0, side === "left" ? -0.25 : 0.25, 0]);
    b.cylinder("control_arm_bushings", `${side}_front_inner_sleeve`, 0.075, 0.32, [x * 0.3, -0.25, -0.55], m.steel, [0, 0, Math.PI / 2], 18);
    b.cylinder("control_arm_bushings", `${side}_rear_inner_sleeve`, 0.075, 0.32, [x * 0.3, -0.25, -1.35], m.steel, [0, 0, Math.PI / 2], 18);
    b.cylinder("lower_ball_joints", `${side}_tapered_stud`, 0.065, 0.42, [x, -0.33, -1.12], m.steel, [0, 0, 0], 16);
    b.torus("lower_ball_joints", `${side}_dust_boot`, 0.12, 0.045, [x, -0.48, -1.12], m.rubber, [Math.PI / 2, 0, 0]);
    b.torus("rear_hubs", `${side}_flange`, 0.31, 0.055, [x, -0.45, 1.55], m.darkSteel, [0, Math.PI / 2, 0]);
    b.bearing("rear_bearings", `${side}_double_row`, [x, -0.45, 1.55], 0.19);
    for (let index = 0; index < 4; index += 1) {
      const angle = index / 4 * Math.PI * 2;
      b.bolt("rear_hubs", `${side}_wheel_stud_${index + 1}`, [x, -0.45 + Math.sin(angle) * 0.19, 1.55 + Math.cos(angle) * 0.19], 0.035, 0.22, [0, 0, Math.PI / 2]);
    }
  }
  for (const x of [-0.62, 0.62]) b.box("stabilizer_bushings", `retainer_${x}`, [0.34, 0.08, 0.24], [x, -0.59, -0.08], m.steel, 0.035);
}

function enhanceSteeringBrakes(b) {
  const m = b.materials;
  b.cylinder("steering_wheel", "center_hub", 0.18, 0.18, [0, 1.65, -1.4], m.darkSteel, [Math.PI / 2, 0, 0], 24);
  b.gear("steering_column", "column_spline", 20, 0.08, 0.11, 0.22, [0, 0.25, -0.36], m.steel, [Math.PI / 3, 0, 0]);
  for (let index = 0; index < 18; index += 1) b.box("rack_pinion", `rack_tooth_${index + 1}`, [0.08, 0.08, 0.18], [-0.78 + index * 0.09, -0.08, -0.30], m.steel, 0.012, [0, 0, Math.PI / 4]);
  for (const [side, x] of [["left", -2.65], ["right", 2.65]]) {
    b.cylinder("front_discs", `${side}_hub_hat`, 0.20, 0.11, [x, -0.35, -0.85], m.darkSteel, [0, 0, Math.PI / 2], 30);
    for (let index = 0; index < 10; index += 1) {
      const angle = index / 10 * Math.PI * 2;
      b.box("front_discs", `${side}_internal_vane_${index + 1}`, [0.06, 0.32, 0.035], [x, -0.35 + Math.sin(angle) * 0.28, -0.85 + Math.cos(angle) * 0.28], m.darkSteel, 0.012, [angle, 0, Math.PI / 2]);
    }
    b.cylinder("front_calipers", `${side}_hydraulic_piston`, 0.15, 0.12, [x - Math.sign(x) * 0.11, -0.08, -0.85], m.steel, [0, 0, Math.PI / 2], 28);
    b.cylinder("front_calipers", `${side}_bleeder_screw`, 0.025, 0.22, [x, 0.23, -0.70], m.brass, [0, 0, 0], 10);
    b.box("brake_pads", `${side}_inner_friction`, [0.045, 0.31, 0.15], [x - Math.sign(x) * 0.045, -0.08, -0.85], m.cast, 0.025);
    b.box("brake_pads", `${side}_outer_friction`, [0.045, 0.31, 0.15], [x + Math.sign(x) * 0.045, -0.08, -0.85], m.cast, 0.025);
  }
  for (const [side, x] of [["left", -2.65], ["right", 2.65]]) {
    b.torus("rear_drum_context", `${side}_primary_shoe`, 0.28, 0.045, [x, -0.35, 0.95], m.cast, [0, Math.PI / 2, 0]);
    b.spring("rear_drum_context", `${side}_return_spring`, [x, -0.05, 0.95], 0.07, 0.38, m.red);
  }
  for (let index = 0; index < 6; index += 1) {
    b.cylinder("abs_module", `hydraulic_port_${index + 1}`, 0.035, 0.18, [1.55 + index * 0.10, 0.91, 1.28], m.brass, [0, 0, 0], 12);
  }
  for (const [wheel, [x, z]] of [[1, [-2.65, -0.85]], [2, [2.65, -0.85]], [3, [-2.65, 0.95]], [4, [2.65, 0.95]]]) {
    for (let index = 0; index < 16; index += 1) {
      const angle = index / 16 * Math.PI * 2;
      b.box("wheels_tires", `tire_${wheel}_tread_${index + 1}`, [0.24, 0.10, 0.08], [x, -0.35 + Math.sin(angle) * 0.70, z + Math.cos(angle) * 0.70], m.rubber, 0.018, [angle, 0, Math.PI / 2]);
    }
  }
}

function enhanceElectrical(b) {
  const m = b.materials;
  for (let index = 0; index < 6; index += 1) {
    const x = -3.12 + index * 0.17;
    b.box("battery", `cell_plate_${index + 1}`, [0.08, 0.52, 0.52], [x, 1.20, 0], index % 2 ? m.copper : m.darkSteel, 0.018);
    b.cylinder("battery", `vent_cap_${index + 1}`, 0.045, 0.06, [x, 1.68, 0], m.black, [0, 0, 0], 14);
  }
  for (let index = 0; index < 12; index += 1) {
    const x = -0.7 + index * 0.13;
    const z = -0.75 + (index % 2) * 0.38;
    b.box("blade_fuses", `fuse_${index + 1}_left_blade`, [0.018, 0.22, 0.045], [x - 0.026, 1.68, z], m.copper, 0.004);
    b.box("blade_fuses", `fuse_${index + 1}_right_blade`, [0.018, 0.22, 0.045], [x + 0.026, 1.68, z], m.copper, 0.004);
  }
  for (let index = 0; index < 5; index += 1) {
    const x = -0.55 + index * 0.34;
    b.cylinder("iso_relays", `relay_${index + 1}_coil`, 0.065, 0.16, [x, 1.90, -0.45], m.copper, [Math.PI / 2, 0, 0], 18);
    b.box("iso_relays", `relay_${index + 1}_contact`, [0.10, 0.025, 0.04], [x, 2.02, -0.45], m.steel, 0.006, [0, 0, 0.25]);
  }
  for (let index = 0; index < 12; index += 1) {
    const angle = index / 12 * Math.PI * 2;
    b.box("alternator", `vent_${index + 1}`, [0.07, 0.20, 0.035], [-2.2, -1.2 + Math.sin(angle) * 0.27, -0.2 + Math.cos(angle) * 0.27], m.aluminum, 0.012, [angle, 0, Math.PI / 2]);
  }
  b.gear("starter_motor", "starter_pinion", 10, 0.10, 0.15, 0.16, [-0.68, -1.35, -0.2], m.steel);
  const moduleDetails = [
    ["ecm", [-2.25, 0.20, -0.85], 0.72],
    ["tcm", [-1.05, 0.20, -0.85], 0.62],
    ["abs_controller", [0.05, 0.20, -0.85], 0.62]
  ];
  for (const [key, position, width] of moduleDetails) {
    b.box(key, "pcb", [width, 0.035, 0.42], position, m.green, 0.012);
    for (let index = 0; index < 4; index += 1) b.box(key, `processor_${index + 1}`, [0.12, 0.055, 0.10], [position[0] - width * 0.27 + index * width * 0.18, position[1] + 0.045, position[2]], m.black, 0.015);
    for (let index = 0; index < 8; index += 1) b.cylinder(key, `capacitor_${index + 1}`, 0.018, 0.07, [position[0] - width * 0.35 + index * width * 0.10, position[1] + 0.07, position[2] + 0.14], m.copper, [0, 0, 0], 10);
  }
  for (let index = 0; index < 4; index += 1) b.connectorPins("multipin_connectors", `connector_${index + 1}`, [2.65 + index * 0.32, 0.82 - index * 0.25, 0.7], 4, 2);
  const branchPoints = [
    ["engine_harness", [0.0, 0.2, 0.7], [0.2, 1.0, 1.0]],
    ["injector_harness", [0.5, -0.4, 0.8], [0.5, -0.85, 1.15]],
    ["coil_harness", [0.8, 0.8, 0.65], [0.8, 1.25, 1.0]],
    ["transmission_harness", [0.6, -0.9, 1.1], [0.6, -1.45, 1.35]],
    ["abs_harness", [1.2, 1.0, 1.15], [1.5, 1.45, 1.35]],
    ["sensor_harness", [1.6, 1.35, 0.95], [2.0, 1.75, 1.15]]
  ];
  for (const [key, start, end] of branchPoints) {
    b.tube(key, "service_branch", [start, [(start[0] + end[0]) / 2, (start[1] + end[1]) / 2, (start[2] + end[2]) / 2 + 0.15], end], 0.035, m.black, 22);
    b.box(key, "branch_connector", [0.18, 0.15, 0.18], end, m.blue, 0.03);
  }
  for (const [key, x] of [["ckp_sensor", 0.95], ["cmp_sensor", 1.35], ["maf_sensor", 1.75], ["map_sensor", 2.15], ["ect_sensor", 2.55], ["oxygen_sensors", 2.95], ["knock_sensor", 3.35]]) {
    b.torus(key, "sealing_o_ring", 0.07, 0.015, [x, 0.00, -0.75], m.rubber, [Math.PI / 2, 0, 0]);
  }
  for (let index = 0; index < 4; index += 1) b.cylinder("injectors", `injector_${index + 1}_nozzle`, 0.022, 0.16, [0.65 + index * 0.34, -1.29, -0.8], m.brass, [0, 0, 0], 10);
  for (const [key, x] of [["vvt_solenoid", 2.15], ["evap_purge_solenoid", 2.65]]) b.cylinder(key, "magnetic_core", 0.045, 0.52, [x, -1.0, -0.8], m.steel, [Math.PI / 2, 0, 0], 14);
}

const assets = [
  { id: "intake_boost", file: "generic_intake_boost.glb", keys: intakeKeys, build: (b) => { buildIntake(b); enhanceIntake(b); } },
  { id: "transmission_drivetrain", file: "generic_transmission_drivetrain.glb", keys: transmissionKeys, build: (b) => { buildTransmission(b); enhanceTransmission(b); } },
  { id: "suspension", file: "generic_suspension.glb", keys: suspensionKeys, build: (b) => { buildSuspension(b); enhanceSuspension(b); } },
  { id: "steering_brakes_wheels", file: "generic_steering_brakes_wheels.glb", keys: steeringKeys, build: (b) => { buildSteeringBrakes(b); enhanceSteeringBrakes(b); } },
  { id: "electrical_control", file: "generic_electrical_control.glb", keys: electricalKeys, build: (b) => { buildElectrical(b); enhanceElectrical(b); } }
];

async function exportAsset(config) {
  const builder = new AssemblyBuilder(config.id);
  config.build(builder);
  const missing = config.keys.filter((key) => !builder.partKeys.has(key));
  const unexpected = [...builder.partKeys].filter((key) => !config.keys.includes(key));
  if (missing.length || unexpected.length) {
    throw new Error(`${config.id} key mismatch: missing=${missing.join(",")} unexpected=${unexpected.join(",")}`);
  }
  const exporter = new GLTFExporter();
  const arrayBuffer = await new Promise((resolve, reject) => {
    exporter.parse(builder.scene, resolve, reject, {
      binary: true,
      trs: true,
      onlyVisible: true,
      includeCustomExtensions: false,
      maxTextureSize: 1024
    });
  });
  const outputDir = path.join(modelRoot, config.id);
  fs.mkdirSync(outputDir, { recursive: true });
  const outputPath = path.join(outputDir, config.file);
  const glb = Buffer.from(arrayBuffer);
  fs.writeFileSync(outputPath, glb);
  const sha256 = crypto.createHash("sha256").update(glb).digest("hex");
  const manifest = {
    schemaVersion: 1,
    assetId: `meet.generic.${config.id}.d3`,
    assetFile: config.file,
    displayName: config.id.replaceAll("_", " "),
    geometryAuthority: authority,
    dimensionalState: "ILLUSTRATIVE_PROPORTIONS_ONLY",
    oemClaim: false,
    vehicleSpecificClaim: false,
    generatedBy: "tools/engine-asset-generator/generate-vehicle-systems.mjs",
    detailLevel: "D3_RECOGNIZABLE_INTERNALS",
    generatorVersion: "2.0.0",
    threeVersion: THREE.REVISION,
    meshNodePrefix: "system_mesh__",
    meshCount: builder.meshCount,
    triangleCount: Math.round(builder.triangleCount),
    partKeys: [...builder.partKeys].sort(),
    sha256,
    license: "Original procedural asset generated for MEET; project-owned source generator",
    warning: "No dimensional/OEM evidence. Installed applicability requires source evidence and physical confirmation."
  };
  fs.writeFileSync(path.join(outputDir, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  return { id: config.id, outputPath, bytes: glb.length, meshCount: builder.meshCount, triangleCount: manifest.triangleCount, sha256 };
}

const requestedId = process.argv[2];
const selectedAssets = requestedId ? assets.filter((asset) => asset.id === requestedId) : assets;
if (selectedAssets.length === 0) throw new Error(`Unknown system asset: ${requestedId}`);
const results = [];
for (const config of selectedAssets) results.push(await exportAsset(config));
console.log(JSON.stringify(results, null, 2));
