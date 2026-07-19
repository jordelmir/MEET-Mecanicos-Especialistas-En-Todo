import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as THREE from "three";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

class NodeFileReader {
  readAsArrayBuffer(blob) { blob.arrayBuffer().then((value) => this.finish(value)).catch((error) => this.fail(error)); }
  readAsDataURL(blob) {
    blob.arrayBuffer().then((value) => this.finish(`data:${blob.type};base64,${Buffer.from(value).toString("base64")}`)).catch((error) => this.fail(error));
  }
  finish(value) { this.result = value; this.onload?.({ target: this }); this.onloadend?.({ target: this }); }
  fail(error) { this.error = error; this.onerror?.({ target: this }); this.onloadend?.({ target: this }); }
}
globalThis.FileReader ??= NodeFileReader;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "../..");
const modelRoot = path.join(repoRoot, "android/app/src/main/assets/models/vehicle_systems");
const kotlinOutput = path.join(repoRoot, "android/app/src/main/kotlin/com/elysium369/meet/visual3d/domain/GenericExtendedVehicleSystemsContract.generated.kt");
const authority = "L2_GENERIC_CUTAWAY";
const detailLevel = "D3_RECOGNIZABLE_INTERNALS";

const alias = (literalName, systemId) => ({ literalName, systemId });
const part = (key, archetype, literalNames, stage = 3, options = {}) => ({
  key,
  archetype,
  aliases: literalNames.map((name) => typeof name === "string" ? alias(name) : name),
  stage,
  ...options
});

const systems = [
  {
    id: "lighting", systemIds: ["lighting"], coverage: ["Exterior", "Interior"], parts: [
      part("matrix_led_module", "lamp", ["Módulo Matrix LED"], 4),
      part("brake_lamps", "lamp", ["Luz de freno", "Tercera luz de freno"], 4),
      part("headlamp_level_sensor", "sensor", ["Sensor de altura para faros"], 5),
      part("afs_module", "module", ["Módulo AFS"], 3),
      part("headlamp_harness", "harness", ["Arnés de faros"], 2),
      part("bulb_connectors", "connector", ["Conectores de bombillo"], 5),
      part("rear_lamps", "lamp", ["Lámparas traseras"], 4),
      part("lamp_seals", "seal", ["Sellos de lámparas"], 6),
      part("door_lamps", "lamp", ["Luz de puertas"], 4),
      part("interior_light_module", "module", ["Módulo de iluminación interior"], 3)
    ]
  },
  {
    id: "hvac", systemIds: ["hvac"], coverage: ["Aire acondicionado", "Calefacción y ventilación"], parts: [
      part("ac_compressor", "compressor", ["Compresor A/C"], 2),
      part("compressor_clutch", "clutch", ["Clutch del compresor"], 4),
      part("compressor_pulley", "pulley", ["Polea del compresor"], 4),
      part("compressor_control_valve", "valve", ["Válvula de control del compresor"], 5),
      part("expansion_valve", "valve", ["Válvula de expansión"], 5),
      part("receiver_drier", "canister", ["Filtro deshidratador"], 3),
      part("refrigerant_lines", "pipes", ["Líneas de alta presión", "Líneas de baja presión"], 2),
      part("ac_pressure_sensor", "sensor", ["Sensor de presión A/C"], 5),
      part("heater_core", "radiator", ["Radiador de calefacción"], 3),
      part("heater_hoses", "pipes", ["Mangueras de calefacción"], 2),
      part("hvac_box", "housing", ["Caja HVAC"], 1),
      part("blower_motor", "fan", ["Motor blower"], 4),
      part("blower_pwm", "module", ["Módulo PWM blower"], 5),
      part("cabin_filter", "filter", ["Filtro de cabina"], 6),
      part("air_doors", "door", ["Actuador blend door", "Actuador mode door", "Actuador recirculation door", "Compuertas internas"], 5),
      part("air_ducts", "duct", ["Ductos de aire"], 2)
    ]
  },
  {
    id: "passive_safety", systemIds: ["passive_safety"], coverage: ["Airbags", "Sensores y control", "Retención"], parts: [
      part("driver_airbag", "airbag", ["Brazos limpiaparabrisas Airbag conductor"], 6),
      part("passenger_airbag", "airbag", ["Airbag pasajero"], 6),
      part("side_airbags", "airbag", ["Airbags laterales"], 6),
      part("curtain_airbags", "airbag", ["Airbags de cortina"], 6),
      part("knee_airbag", "airbag", ["Airbag de rodilla"], 6),
      part("srs_module", "module", ["Módulo SRS"], 3),
      part("impact_sensors", "sensor", ["Sensor impacto frontal", "Sensor impacto lateral"], 4),
      part("safing_sensor", "sensor", ["Sensor safing"], 4),
      part("srs_harness", "harness", ["Arnés amarillo SRS"], 2),
      part("srs_connectors", "connector", ["Conectores SRS"], 5),
      part("seatbelt_pretensioner", "pretensioner", ["Pretensor cinturón delantero"], 5)
    ]
  },
  {
    id: "adas", systemIds: ["adas"], coverage: ["Cámaras", "Parking", "Conducción asistida", "Percepción"], parts: [
      part("reverse_camera", "camera", ["Cámara de reversa"], 4),
      part("front_adas_camera", "camera", ["Cámara frontal ADAS"], 4),
      part("surround_cameras", "camera", ["Cámara 360 delantera", "Cámara 360 trasera", "Cámaras laterales"], 4),
      part("parking_sensors", "sensor", ["Sensor parking delantero", "Sensor parking trasero"], 5),
      part("parking_module", "module", ["Módulo parking assist"], 3),
      part("lane_keep_module", "module", ["Módulo lane keep assist"], 3),
      part("adaptive_cruise_module", "module", ["Módulo adaptive cruise"], 3),
      part("aeb_module", "module", ["Módulo AEB"], 3),
      part("blind_spot_module", "radar", ["Módulo blind spot"], 4),
      part("front_radar", "radar", [], 4),
      part("ultrasonic_array", "sensor", ["Sensores ultrasónicos"], 5)
    ]
  },
  {
    id: "body", systemIds: ["body"], coverage: ["Paneles", "Puertas", "Vidrios", "Espejos"], parts: [
      part("front_doors", "door", ["Puerta delantera izquierda", "Puerta delantera derecha"], 2),
      part("rear_doors", "door", ["Puerta trasera izquierda", "Puerta trasera derecha"], 2),
      part("tailgate", "panel", ["Compuerta trasera"], 2),
      part("side_panels", "panel", ["Paneles laterales"], 1),
      part("rocker_panels", "panel", ["Rocker panels"], 1),
      part("engine_splash_shields", "panel", ["Guardapolvos de motor"], 3),
      part("door_checks", "hinge", ["Limitador de puerta"], 5),
      part("door_latches", "lock", ["Cerradura de puerta"], 5),
      part("exterior_handles", "handle", ["Manija exterior"], 5),
      part("window_motors", "motor", ["Motor de ventana"], 4),
      part("door_glass", "glass", ["Vidrio de puerta"], 3),
      part("rear_glass", "glass", ["Vidrio trasero"], 3),
      part("side_mirrors", "mirror", ["Espejo lateral izquierdo", "Espejo lateral derecho"], 3),
      part("mirror_motors", "motor", ["Motor ajuste espejo", "Motor plegado espejo"], 5),
      part("mirror_heaters", "heater", ["Calefactor espejo"], 5)
    ]
  },
  {
    id: "wipers", systemIds: ["wipers"], coverage: ["Barrido delantero", "Barrido trasero", "Lavado y control"], parts: [
      part("front_wiper_motor", "motor", ["Motor limpiaparabrisas delantero"], 3),
      part("wiper_linkage", "linkage", ["Varillaje limpiaparabrisas"], 4),
      part("rear_wiper_motor", "motor", ["Motor limpiaparabrisas trasero"], 3),
      part("rear_wiper_arm", "wiper", ["Brazo trasero"], 5),
      part("washer_reservoir", "tank", ["Depósito lavaparabrisas"], 1),
      part("washer_pumps", "pump", ["Bomba lavaparabrisas delantera", "Bomba lavaparabrisas trasera"], 4),
      part("washer_hoses", "pipes", ["Mangueras lavaparabrisas"], 2),
      part("washer_level_sensor", "sensor", ["Sensor nivel líquido limpiaparabrisas"], 5),
      part("wiper_relay", "relay", ["Relé wiper"], 3),
      part("multifunction_switch", "switch", ["Switch multifunción"], 4),
      part("rain_sensor", "sensor", ["Sensor lluvia"], 5)
    ]
  },
  {
    id: "interior", systemIds: ["interior"], coverage: ["Tablero y controles", "Asientos", "Acabados"], parts: [
      part("hvac_panel", "control_panel", ["Panel HVAC"], 3),
      part("armrest", "trim", ["Apoyabrazos"], 2),
      part("hazard_switch", "switch", ["Switch hazard"], 5),
      part("defog_switch", "switch", ["Switch desempañador"], 5),
      part("light_switch", "switch", ["Switch luces"], 5),
      part("parking_brake", "lever", ["Freno de mano"], 4),
      part("front_seats", "seat", ["Asiento conductor", "Asiento pasajero"], 2),
      part("rear_seats", "seat", ["Asientos traseros"], 2),
      part("seat_rails", "rails", ["Rieles de asiento"], 4),
      part("seat_motors", "motor", ["Motor ajuste longitudinal", "Motor ajuste altura", "Motor ajuste respaldo", "Motor lumbar"], 5),
      part("seat_memory", "module", ["Módulo memoria"], 5),
      part("occupant_sensor", "sensor", ["Sensor ocupante"], 5),
      part("door_trims", "trim", ["Paneles de puerta", "Manijas interiores", "Espejos de parasol"], 3)
    ]
  },
  {
    id: "infotainment", systemIds: ["infotainment"], coverage: ["Comunicación", "Integración móvil", "Controles y captura"], parts: [
      part("bluetooth_module", "module", ["Módulo Bluetooth"], 3),
      part("wifi_module", "module", ["Módulo WiFi"], 3),
      part("cellular_module", "module", ["Módulo celular"], 3),
      part("steering_controls", "control_panel", ["Mandos al volante"], 4),
      part("phone_projection_module", "screen", ["Módulo CarPlay/Android Auto"], 3),
      part("oem_dashcam", "camera", ["Cámara dashcam OEM"], 4),
      part("antenna_network", "antenna", [], 4),
      part("audio_bus", "harness", [], 2)
    ]
  },
  {
    id: "access", systemIds: ["access"], coverage: ["Encendido", "Inmovilizador", "Cierre y sensores"], parts: [
      part("ignition_switch", "switch", ["Switch de ignición"], 4),
      part("immobilizer_module", "module", ["Módulo inmovilizador"], 3),
      part("central_lock_actuators", "lock", ["Actuadores cierre central"], 5),
      part("trunk_actuator", "lock", ["Actuador baúl"], 5),
      part("hood_sensor", "sensor", ["Sensor capó"], 5),
      part("trunk_sensor", "sensor", ["Sensor baúl"], 5),
      part("ultrasonic_security_sensor", "sensor", ["Sensor ultrasonido"], 5),
      part("tilt_sensor", "sensor", ["Sensor inclinación"], 5),
      part("motion_sensor", "sensor", ["Sensor movimiento"], 5)
    ]
  },
  {
    id: "hybrid_ev", systemIds: ["hybrid_ev"], coverage: ["Alta tensión", "Tracción eléctrica"], parts: [
      part("hv_battery", "battery", ["Batería HV"], 1),
      part("battery_modules", "battery_modules", ["Módulos de batería"], 3),
      part("hv_fuse", "fuse", ["Fusible HV"], 5),
      part("cell_voltage_sensors", "sensor", ["Sensores de voltaje por celda"], 5),
      part("battery_temperature_sensors", "sensor", ["Sensores de temperatura batería"], 5),
      part("current_sensors", "sensor", ["Sensores de corriente"], 5),
      part("hv_junction_box", "module", ["Caja de unión HV"], 3),
      part("orange_hv_cables", "hv_harness", ["Cables naranja HV"], 2),
      part("hv_connectors", "connector", ["Conectores HV"], 5),
      part("charge_port", "charge_port", ["Tapa puerto carga"], 4),
      part("front_traction_motor", "motor", ["Motor eléctrico delantero"], 2),
      part("rear_traction_motor", "motor", ["Motor eléctrico trasero"], 2),
      part("rotor_position_sensor", "sensor", ["Sensor posición rotor"], 5),
      part("reduction_gear", "gearbox", ["Reductor"], 4),
      part("traction_inverters", "inverter", ["Inverter motor delantero", "Inverter motor trasero"], 3),
      part("e_axle", "gearbox", ["Unidad e-axle"], 3),
      part("hybrid_control_module", "module", ["Módulo control híbrido"], 3),
      part("regenerative_brake", "disc", ["Freno regenerativo"], 4)
    ]
  },
  {
    id: "fluids", systemIds: ["fluids"], coverage: ["Fluidos", "Filtros", "Desgaste"], parts: [
      part("engine_oil", "fluid", ["Aceite de motor"], 2),
      part("brake_fluid", "fluid", ["Líquido de frenos"], 2),
      part("ac_oil", "fluid", ["Aceite compresor A/C"], 2),
      part("bearing_grease", "fluid", ["Grasa rodamientos"], 2),
      part("oil_filter", "filter", ["Filtro aceite"], 4),
      part("air_filter", "filter", ["Filtro aire"], 4),
      part("fuel_filter", "filter", ["Filtro combustible"], 4),
      part("cabin_filter", "filter", ["Filtro cabina"], 4),
      part("transmission_filter", "filter", ["Filtro transmisión"], 4),
      part("brake_pads", "pad", ["Pastillas freno"], 5),
      part("brake_shoes", "shoe", ["Zapatas freno"], 5),
      part("brake_discs", "disc", ["Discos freno"], 5)
    ]
  },
  {
    id: "hardware", systemIds: ["hardware"], coverage: ["Fasteners", "Sellos", "Sujeción"], parts: [
      part("head_bolts", "bolt", ["Pernos de culata torque-to-yield"], 6),
      part("rod_bolts", "bolt", ["Pernos de biela"], 6),
      part("flywheel_bolts", "bolt", ["Pernos de volante"], 6),
      part("converter_bolts", "bolt", ["Pernos de convertidor"], 6),
      part("caliper_bolts", "bolt", ["Pernos de caliper"], 6),
      part("engine_mount_bolts", "bolt", ["Pernos de soporte motor"], 6),
      part("alignment_bolts", "bolt", ["Pernos excéntricos alineación"], 6),
      part("axle_nuts", "nut", ["Tuercas de eje"], 6),
      part("flat_gaskets", "gasket", ["Juntas planas"], 5),
      part("mls_gaskets", "gasket", ["Juntas metálicas MLS"], 5),
      part("bushings", "bushing", ["Bujes"], 5),
      part("metal_clamps", "clamp", ["Abrazaderas metálicas"], 5)
    ]
  },
  {
    id: "functional_overview", systemIds: ["overview"], coverage: ["Mapa funcional", "Elementos críticos olvidados", "Reglas de evidencia"], parts: [
      part("sensor_domain", "network", ["│    ├── Sensors"], 1),
      part("engine_grounds", "ground", ["Tierras del motor"], 2),
      part("main_fusible_link", "fuse", ["Fusible link principal"], 2),
      part("battery_ibs", "sensor", ["Sensor IBS de batería"], 3),
      part("main_relay", "relay", ["Relé principal"], 3),
      part("prndl_sensor", "sensor", ["Sensor de rango PRNDL"], 3),
      part("neutral_safety_switch", "switch", ["Neutral safety switch"], 3),
      part("transmission_solenoids", "valve", ["Solenoides internos de transmisión"], 4),
      part("transmission_harness", "harness", ["Arnés interno de transmisión"], 4),
      part("transmission_bulkhead", "connector", ["Conector pasamuros de transmisión"], 4),
      part("evap_pressure_sensor", "sensor", ["Sensor de presión EVAP"], 5),
      part("evap_valves", "valve", ["Válvula vent EVAP", "Válvula purge EVAP"], 5)
    ]
  }
];

const materialSet = () => {
  const material = (name, color, roughness, metalness, emissive = 0x000000) => new THREE.MeshStandardMaterial({ name, color, roughness, metalness, emissive, emissiveIntensity: emissive ? 0.32 : 0 });
  return {
    cast: material("cast", 0x303b43, 0.48, 0.78), steel: material("steel", 0xb7c3ca, 0.24, 0.91),
    aluminum: material("aluminum", 0x8799a5, 0.34, 0.72), black: material("polymer", 0x12181d, 0.70, 0.04),
    rubber: material("rubber", 0x090c0e, 0.88, 0.01), copper: material("copper", 0xc47732, 0.28, 0.76),
    red: material("safety_red", 0xb32332, 0.38, 0.35, 0x260006), cyan: material("signal_cyan", 0x16a4b4, 0.31, 0.42, 0x00262a),
    amber: material("power_amber", 0xe19522, 0.34, 0.35, 0x291300), green: material("pcb_green", 0x28775c, 0.45, 0.32),
    orange: material("high_voltage_orange", 0xef6c18, 0.35, 0.34, 0x2c0b00), white: material("light_white", 0xe6edf0, 0.28, 0.18, 0x222a2d),
    glass: material("technical_glass", 0x3e7f93, 0.10, 0.18, 0x001820), blue: material("service_blue", 0x246eaa, 0.38, 0.34)
  };
};

class Builder {
  constructor(id) {
    this.scene = new THREE.Scene(); this.root = new THREE.Group(); this.root.name = `system_root__${id}_d3`;
    this.root.userData = { authority, dimensional: false, purpose: "extended service inspection atlas" };
    this.scene.add(this.root); this.m = materialSet(); this.groups = new Map(); this.keys = new Set(); this.meshCount = 0; this.triangleCount = 0;
  }
  group(key) { if (!this.groups.has(key)) { const g = new THREE.Group(); g.name = `system_part__${key}`; g.userData = { partKey: key, authority, dimensional: false }; this.groups.set(key, g); this.keys.add(key); this.root.add(g); } return this.groups.get(key); }
  mesh(key, detail, geometry, material, position, rotation = [0, 0, 0], scale = [1, 1, 1]) {
    geometry.computeVertexNormals(); const mesh = new THREE.Mesh(geometry, material); mesh.name = `system_mesh__${key}__${detail}`;
    mesh.position.set(...position); mesh.rotation.set(...rotation); mesh.scale.set(...scale); mesh.castShadow = true; mesh.receiveShadow = true;
    mesh.userData = { partKey: key, authority, dimensional: false }; this.group(key).add(mesh);
    this.triangleCount += geometry.index ? geometry.index.count / 3 : geometry.getAttribute("position").count / 3; this.meshCount += 1; return mesh;
  }
  box(key, detail, size, p, mat = this.m.black, radius = 0.06, r = [0, 0, 0]) { return this.mesh(key, detail, new RoundedBoxGeometry(...size, 2, radius), mat, p, r); }
  cyl(key, detail, radius, length, p, mat = this.m.steel, r = [0, 0, Math.PI / 2], segments = 20) { return this.mesh(key, detail, new THREE.CylinderGeometry(radius, radius, length, segments), mat, p, r); }
  sphere(key, detail, radius, p, mat = this.m.white, scale = [1, 1, 1]) { return this.mesh(key, detail, new THREE.SphereGeometry(radius, 18, 12), mat, p, [0, 0, 0], scale); }
  torus(key, detail, radius, tube, p, mat = this.m.steel, r = [0, Math.PI / 2, 0]) { return this.mesh(key, detail, new THREE.TorusGeometry(radius, tube, 8, 24), mat, p, r); }
  tube(key, detail, points, radius, mat = this.m.rubber) { const curve = new THREE.CatmullRomCurve3(points.map((p) => new THREE.Vector3(...p))); return this.mesh(key, detail, new THREE.TubeGeometry(curve, 24, radius, 7, false), mat, [0, 0, 0]); }
  bolt(key, detail, p, mat = this.m.steel) { this.cyl(key, `${detail}_shaft`, 0.045, 0.28, p, mat); this.cyl(key, `${detail}_head`, 0.085, 0.07, [p[0] + 0.15, p[1], p[2]], mat, [0, 0, Math.PI / 2], 6); }
}

function layout(index, count) {
  const columns = Math.min(5, Math.ceil(Math.sqrt(count * 1.45)));
  const rows = Math.ceil(count / columns);
  const column = index % columns; const row = Math.floor(index / columns);
  return [(column - (columns - 1) / 2) * 1.55, ((rows - 1) / 2 - row) * 1.42, (index % 2 ? 0.34 : -0.34)];
}

function addModuleInternals(b, key, p, color = b.m.black) {
  b.box(key, "housing", [1.05, 0.62, 0.48], p, color, 0.08);
  b.box(key, "pcb", [0.84, 0.035, 0.34], [p[0], p[1] + 0.12, p[2]], b.m.green, 0.015);
  for (let i = 0; i < 4; i += 1) b.box(key, `chip_${i + 1}`, [0.12, 0.055, 0.10], [p[0] - 0.27 + i * 0.18, p[1] + 0.17, p[2]], b.m.black, 0.012);
  for (let i = 0; i < 6; i += 1) b.cyl(key, `pin_${i + 1}`, 0.014, 0.18, [p[0] + 0.55, p[1] - 0.18 + i * 0.07, p[2]], b.m.copper);
}

function addArchetype(b, entry, index, count) {
  const { key, archetype } = entry; const p = layout(index, count); const m = b.m;
  const moduleTypes = new Set(["module", "relay", "inverter", "control_panel", "network"]);
  if (moduleTypes.has(archetype)) {
    addModuleInternals(b, key, p, archetype === "inverter" ? m.orange : m.black);
    if (archetype === "relay") b.cyl(key, "coil", 0.12, 0.34, [p[0], p[1] + 0.24, p[2]], m.copper, [Math.PI / 2, 0, 0]);
    if (archetype === "network") for (let i = 0; i < 5; i += 1) b.tube(key, `bus_${i + 1}`, [[p[0], p[1], p[2]], [p[0] + (i - 2) * 0.24, p[1] - 0.6, p[2] + 0.2]], 0.025, m.cyan);
    return;
  }
  switch (archetype) {
    case "lamp":
      b.box(key, "housing", [1.15, 0.62, 0.48], p, m.black, 0.16); b.sphere(key, "lens", 0.34, [p[0], p[1], p[2] - 0.24], m.white, [1.25, 0.62, 0.38]);
      b.torus(key, "reflector", 0.25, 0.045, [p[0], p[1], p[2] - 0.18], m.aluminum); for (let i = 0; i < 6; i += 1) b.sphere(key, `led_${i + 1}`, 0.035, [p[0] - 0.25 + i * 0.10, p[1], p[2] - 0.48], m.white); break;
    case "compressor": case "motor": case "pump":
      b.cyl(key, "stator", 0.38, 0.82, p, archetype === "motor" ? m.copper : m.cast); b.cyl(key, "rotor", 0.18, 1.05, p, m.steel); b.torus(key, "bearing_front", 0.18, 0.045, [p[0] - 0.42, p[1], p[2]], m.steel); b.torus(key, "bearing_rear", 0.18, 0.045, [p[0] + 0.42, p[1], p[2]], m.steel); break;
    case "pulley": case "clutch": case "disc":
      b.cyl(key, "hub", 0.38, 0.16, p, m.darkSteel ?? m.cast); b.torus(key, "friction_ring", 0.38, 0.10, p, archetype === "clutch" ? m.red : m.steel); for (let i = 0; i < 8; i += 1) { const a = i / 8 * Math.PI * 2; b.cyl(key, `vent_${i + 1}`, 0.025, 0.22, [p[0], p[1] + Math.sin(a) * 0.27, p[2] + Math.cos(a) * 0.27], m.black); } break;
    case "sensor": case "camera": case "radar":
      b.box(key, "sealed_body", [0.64, 0.48, 0.42], p, archetype === "radar" ? m.cyan : m.black, 0.09); b.cyl(key, "sensing_element", archetype === "camera" ? 0.16 : 0.10, 0.25, [p[0], p[1], p[2] - 0.28], archetype === "camera" ? m.glass : m.cyan, [Math.PI / 2, 0, 0]); b.box(key, "connector", [0.32, 0.22, 0.28], [p[0] + 0.43, p[1], p[2]], m.blue, 0.04); break;
    case "valve":
      b.cyl(key, "body", 0.22, 0.72, p, m.aluminum); b.cyl(key, "spool", 0.08, 0.92, p, m.steel); b.cyl(key, "coil", 0.28, 0.34, [p[0] - 0.25, p[1], p[2]], m.copper); break;
    case "canister": case "tank": case "fluid":
      b.cyl(key, "reservoir", archetype === "tank" ? 0.42 : 0.30, 0.84, p, archetype === "fluid" ? m.blue : m.aluminum, [0, 0, 0]); b.cyl(key, "cap", 0.16, 0.12, [p[0], p[1] + 0.48, p[2]], m.black, [0, 0, 0]); b.tube(key, "service_line", [[p[0], p[1] - 0.3, p[2]], [p[0] + 0.55, p[1] - 0.55, p[2]]], 0.045, archetype === "fluid" ? m.blue : m.rubber); break;
    case "radiator": case "filter":
      b.box(key, "frame", [1.05, 0.78, 0.18], p, m.aluminum, 0.05); for (let i = 0; i < 9; i += 1) b.box(key, `element_${i + 1}`, [0.035, 0.62, 0.08], [p[0] - 0.40 + i * 0.10, p[1], p[2]], archetype === "filter" ? m.white : m.copper, 0.008); break;
    case "fan":
      b.torus(key, "shroud", 0.43, 0.065, p, m.black); b.cyl(key, "hub", 0.14, 0.18, p, m.steel); for (let i = 0; i < 7; i += 1) { const a = i / 7 * Math.PI * 2; b.box(key, `blade_${i + 1}`, [0.36, 0.11, 0.035], [p[0], p[1] + Math.sin(a) * 0.22, p[2] + Math.cos(a) * 0.22], m.black, 0.03, [a, 0, 0]); } break;
    case "pipes": case "duct": case "harness": case "hv_harness":
      for (let i = 0; i < (archetype === "harness" || archetype === "hv_harness" ? 5 : 3); i += 1) b.tube(key, `route_${i + 1}`, [[p[0] - 0.55, p[1] + i * 0.08 - 0.16, p[2]], [p[0], p[1] + 0.28 - i * 0.07, p[2] + 0.20], [p[0] + 0.55, p[1] + i * 0.08 - 0.16, p[2]]], archetype === "duct" ? 0.10 : 0.035, archetype === "hv_harness" ? m.orange : archetype === "harness" ? m.black : m.aluminum); break;
    case "connector": case "charge_port":
      b.box(key, "shell", [0.70, 0.55, 0.42], p, archetype === "charge_port" ? m.orange : m.blue, 0.09); for (let row = 0; row < 3; row += 1) for (let col = 0; col < 4; col += 1) b.cyl(key, `terminal_${row}_${col}`, 0.025, 0.22, [p[0] - 0.18 + col * 0.12, p[1] - 0.12 + row * 0.12, p[2] - 0.28], m.copper, [Math.PI / 2, 0, 0], 10); break;
    case "airbag":
      b.box(key, "module_case", [0.92, 0.36, 0.44], p, m.cast, 0.10); for (let i = 0; i < 4; i += 1) b.sphere(key, `fold_${i + 1}`, 0.23, [p[0] - 0.27 + i * 0.18, p[1] + 0.18, p[2]], m.white, [1.0, 0.45, 0.72]); b.cyl(key, "inflator", 0.10, 0.58, [p[0], p[1] - 0.20, p[2]], m.red); break;
    case "pretensioner": case "linkage": case "wiper": case "lever": case "rails":
      b.cyl(key, "pivot", 0.11, 0.28, p, m.steel); b.box(key, "left_link", [0.72, 0.10, 0.10], [p[0] - 0.35, p[1] + 0.16, p[2]], m.darkSteel ?? m.cast, 0.03, [0, 0, 0.22]); b.box(key, "right_link", [0.72, 0.10, 0.10], [p[0] + 0.35, p[1] - 0.12, p[2]], m.darkSteel ?? m.cast, 0.03, [0, 0, -0.22]); break;
    case "door": case "panel": case "trim": case "glass":
      b.box(key, "outer_surface", [1.15, 0.82, 0.12], p, archetype === "glass" ? m.glass : archetype === "trim" ? m.black : m.aluminum, 0.10); b.box(key, "inner_reinforcement", [0.82, 0.12, 0.34], [p[0], p[1], p[2] + 0.12], m.cast, 0.04); for (let i = 0; i < 4; i += 1) b.bolt(key, `mount_${i + 1}`, [p[0] - 0.35 + i * 0.23, p[1] - 0.30, p[2] + 0.18]); break;
    case "hinge": case "lock": case "handle": case "switch":
      b.box(key, "body", [0.72, 0.42, 0.32], p, m.black, 0.08); b.cyl(key, "pivot", 0.09, 0.48, [p[0] - 0.32, p[1], p[2]], m.steel, [0, 0, 0]); b.box(key, "actuating_element", [0.52, 0.10, 0.12], [p[0] + 0.24, p[1] + 0.16, p[2]], archetype === "switch" ? m.red : m.steel, 0.03); break;
    case "mirror": case "screen":
      b.box(key, "frame", [1.08, 0.70, 0.16], p, m.black, 0.12); b.box(key, "surface", [0.90, 0.54, 0.035], [p[0], p[1], p[2] - 0.10], archetype === "screen" ? m.cyan : m.glass, 0.06); addModuleInternals(b, key, [p[0], p[1], p[2] + 0.24]); break;
    case "heater": case "gasket": case "seal": case "clamp": case "bushing": case "nut":
      b.torus(key, "service_profile", archetype === "gasket" ? 0.40 : 0.28, archetype === "seal" ? 0.045 : 0.08, p, archetype === "heater" ? m.copper : archetype === "seal" || archetype === "bushing" ? m.rubber : m.steel); if (archetype === "heater") for (let i = 0; i < 5; i += 1) b.tube(key, `element_${i + 1}`, [[p[0] - 0.42, p[1] - 0.20 + i * 0.10, p[2]], [p[0] + 0.42, p[1] - 0.20 + i * 0.10, p[2]]], 0.018, m.copper); break;
    case "seat":
      b.box(key, "cushion", [0.92, 0.28, 0.82], [p[0], p[1] - 0.18, p[2]], m.black, 0.13); b.box(key, "backrest", [0.92, 0.95, 0.25], [p[0], p[1] + 0.42, p[2] + 0.26], m.black, 0.13, [0.14, 0, 0]); b.box(key, "frame", [0.74, 0.10, 0.64], [p[0], p[1] - 0.40, p[2]], m.steel, 0.04); break;
    case "antenna":
      b.cyl(key, "mast", 0.035, 0.95, p, m.steel, [0, 0, 0]); for (let i = 0; i < 4; i += 1) b.torus(key, `signal_${i + 1}`, 0.15 + i * 0.09, 0.012, [p[0], p[1] + 0.40, p[2]], m.cyan, [Math.PI / 2, 0, 0]); break;
    case "battery": case "battery_modules":
      b.box(key, "pack_case", [1.20, 0.60, 0.72], p, m.cast, 0.10); for (let i = 0; i < 8; i += 1) b.box(key, `cell_${i + 1}`, [0.10, 0.42, 0.48], [p[0] - 0.42 + i * 0.12, p[1], p[2]], i % 2 ? m.copper : m.aluminum, 0.025); b.box(key, "service_disconnect", [0.24, 0.18, 0.20], [p[0] + 0.40, p[1] + 0.36, p[2]], m.orange, 0.04); break;
    case "fuse":
      b.box(key, "insulated_body", [0.72, 0.38, 0.34], p, m.orange, 0.08); b.box(key, "fusible_element", [0.42, 0.055, 0.10], [p[0], p[1], p[2] - 0.20], m.copper, 0.015); break;
    case "gearbox":
      b.box(key, "case", [1.08, 0.78, 0.72], p, m.cast, 0.16); for (let i = 0; i < 3; i += 1) { b.torus(key, `gear_${i + 1}`, 0.18 + i * 0.06, 0.055, [p[0] - 0.25 + i * 0.25, p[1], p[2]], m.steel); b.cyl(key, `shaft_${i + 1}`, 0.06, 0.82, [p[0] - 0.25 + i * 0.25, p[1], p[2]], m.steel); } break;
    case "pad": case "shoe":
      b.box(key, "backing_plate", [0.72, 0.55, 0.10], p, m.steel, 0.10); b.box(key, "friction_material", [0.64, 0.47, 0.12], [p[0], p[1], p[2] - 0.11], m.red, 0.09); break;
    case "bolt":
      for (let i = 0; i < 4; i += 1) b.bolt(key, `fastener_${i + 1}`, [p[0], p[1] - 0.30 + i * 0.20, p[2]]); break;
    case "ground":
      b.tube(key, "braided_strap", [[p[0] - 0.55, p[1], p[2]], [p[0], p[1] + 0.22, p[2]], [p[0] + 0.55, p[1], p[2]]], 0.07, m.copper); b.torus(key, "left_eyelet", 0.13, 0.045, [p[0] - 0.55, p[1], p[2]], m.steel); b.torus(key, "right_eyelet", 0.13, 0.045, [p[0] + 0.55, p[1], p[2]], m.steel); break;
    default:
      b.box(key, "recognizable_body", [0.92, 0.62, 0.48], p, m.aluminum, 0.10); b.cyl(key, "service_axis", 0.10, 1.10, p, m.steel);
  }
}

function escapeKotlin(value) { return value.replaceAll("\\", "\\\\").replaceAll("\"", "\\\"").replaceAll("$", "\\$"); }
function floatLiteral(value) { return Number.isInteger(value) ? `${value}.0f` : `${value.toFixed(2)}f`; }

function writeKotlinContract() {
  const definitions = systems.map((system) => {
    const bindingLines = system.parts.map((entry, index) => {
      const p = layout(index, system.parts.length); const sign = index % 2 ? 1 : -1;
      const offset = [p[0] * 0.72, p[1] * 0.72 + sign * 0.35, sign * (0.8 + (index % 3) * 0.18)];
      const aliases = entry.aliases.map((item) => {
        const systemId = item.systemId ?? system.systemIds[0];
        return `GenericSystemSourceAlias(\"${escapeKotlin(systemId)}\", \"${escapeKotlin(item.literalName)}\")`;
      }).join(", ");
      const aliasArguments = aliases ? `, ${aliases}` : "";
      return `            binding(\"${entry.key}\", ${entry.stage}, ${offset.map(floatLiteral).join(", ")}${aliasArguments})`;
    }).join(",\n");
    const property = system.id.replace(/_([a-z])/g, (_, char) => char.toUpperCase());
    return `    val ${property} = asset(\n        id = \"${system.id}\",\n        supportedSystemIds = setOf(${system.systemIds.map((id) => `\"${id}\"`).join(", ")}),\n        bindings = listOf(\n${bindingLines}\n        )\n    )`;
  }).join("\n\n");
  const properties = systems.map((system) => system.id.replace(/_([a-z])/g, (_, char) => char.toUpperCase())).join(",\n        ");
  const output = `// Generated by tools/engine-asset-generator/generate-extended-vehicle-systems.mjs. Do not edit manually.\npackage com.elysium369.meet.visual3d.domain\n\nobject GenericExtendedVehicleSystemsContract {\n${definitions}\n\n    val assets: List<GenericSystemAssetDefinition> = listOf(\n        ${properties}\n    )\n\n    private fun asset(\n        id: String,\n        supportedSystemIds: Set<String>,\n        bindings: List<GenericSystemAssetBinding>\n    ) = GenericSystemAssetDefinition(\n        id = id,\n        assetPath = \"models/vehicle_systems/$id/generic_$id.glb\",\n        manifestPath = \"models/vehicle_systems/$id/manifest.json\",\n        supportedSystemIds = supportedSystemIds,\n        bindings = bindings\n    )\n\n    private fun binding(\n        meshKey: String,\n        serviceStage: Int,\n        offsetX: Float,\n        offsetY: Float,\n        offsetZ: Float,\n        vararg aliases: GenericSystemSourceAlias\n    ) = GenericSystemAssetBinding(\n        meshKey = meshKey,\n        sourceAliases = aliases.toSet(),\n        serviceStage = serviceStage,\n        explodedOffset = CatalogServiceOffset(offsetX, offsetY, offsetZ)\n    )\n}\n`;
  fs.writeFileSync(kotlinOutput, output);
}

async function exportAsset(config) {
  const builder = new Builder(config.id); config.parts.forEach((entry, index) => addArchetype(builder, entry, index, config.parts.length));
  const expected = config.parts.map((entry) => entry.key); const missing = expected.filter((key) => !builder.keys.has(key));
  if (missing.length) throw new Error(`${config.id} missing keys: ${missing.join(", ")}`);
  const exporter = new GLTFExporter();
  const arrayBuffer = await new Promise((resolve, reject) => exporter.parse(builder.scene, resolve, reject, { binary: true, trs: true, onlyVisible: true, maxTextureSize: 512 }));
  const outputDir = path.join(modelRoot, config.id); fs.mkdirSync(outputDir, { recursive: true });
  const file = `generic_${config.id}.glb`; const outputPath = path.join(outputDir, file); const glb = Buffer.from(arrayBuffer); fs.writeFileSync(outputPath, glb);
  const sha256 = crypto.createHash("sha256").update(glb).digest("hex");
  const manifest = {
    schemaVersion: 1, assetId: `meet.generic.${config.id}.d3`, assetFile: file, displayName: config.id.replaceAll("_", " "),
    geometryAuthority: authority, dimensionalState: "ILLUSTRATIVE_PROPORTIONS_ONLY", oemClaim: false, vehicleSpecificClaim: false,
    generatedBy: "tools/engine-asset-generator/generate-extended-vehicle-systems.mjs", detailLevel, generatorVersion: "1.0.0",
    threeVersion: THREE.REVISION, meshNodePrefix: "system_mesh__", meshCount: builder.meshCount, triangleCount: Math.round(builder.triangleCount),
    partKeys: [...builder.keys].sort(), supportedSystemIds: config.systemIds, subsystemCoverage: config.coverage, sha256,
    license: "Original procedural asset generated for MEET; project-owned source generator",
    warning: "No dimensional/OEM evidence. Installed applicability requires source evidence and physical confirmation."
  };
  fs.writeFileSync(path.join(outputDir, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  return { id: config.id, bytes: glb.length, meshCount: builder.meshCount, triangleCount: manifest.triangleCount, sha256 };
}

writeKotlinContract();
const requestedId = process.argv[2]; const selected = requestedId ? systems.filter((system) => system.id === requestedId) : systems;
if (!selected.length) throw new Error(`Unknown extended system asset: ${requestedId}`);
const results = []; for (const config of selected) results.push(await exportAsset(config));
console.log(JSON.stringify(results, null, 2));
