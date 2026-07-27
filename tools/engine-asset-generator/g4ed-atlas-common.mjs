import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as THREE from "three";
import { GLTFExporter } from "three/addons/exporters/GLTFExporter.js";
import { RoundedBoxGeometry } from "three/addons/geometries/RoundedBoxGeometry.js";

class NodeFileReader {
  readAsArrayBuffer(blob) {
    blob.arrayBuffer().then((value) => this.#finish(value)).catch((error) => this.#fail(error));
  }

  readAsDataURL(blob) {
    blob.arrayBuffer()
      .then((value) => this.#finish(`data:${blob.type};base64,${Buffer.from(value).toString("base64")}`))
      .catch((error) => this.#fail(error));
  }

  #finish(value) {
    this.result = value;
    const event = { target: this };
    this.onload?.(event);
    this.onloadend?.(event);
  }

  #fail(error) {
    this.error = error;
    const event = { target: this };
    this.onerror?.(event);
    this.onloadend?.(event);
  }
}

globalThis.FileReader ??= NodeFileReader;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
export const repoRoot = path.resolve(scriptDir, "../..");
export const atlasPath = path.join(
  repoRoot,
  "android/app/src/main/assets/knowledge/g4ed/g4ed_engine_atlas.json"
);
export const outputRoot = path.join(
  repoRoot,
  "android/app/src/main/assets/models/g4ed_atlas"
);

export const NODE_PREFIX = "asset_mesh__";
export const GROUP_PREFIX = "asset_part__";

export const materials = {
  castIron: new THREE.MeshStandardMaterial({
    name: "g4ed_cast_iron",
    color: 0x202b33,
    roughness: 0.48,
    metalness: 0.82
  }),
  darkSteel: new THREE.MeshStandardMaterial({
    name: "g4ed_dark_steel",
    color: 0x35434d,
    roughness: 0.3,
    metalness: 0.92
  }),
  steel: new THREE.MeshStandardMaterial({
    name: "g4ed_machined_steel",
    color: 0xb7c9d4,
    roughness: 0.18,
    metalness: 0.96
  }),
  aluminum: new THREE.MeshStandardMaterial({
    name: "g4ed_cast_aluminum",
    color: 0x899ba6,
    roughness: 0.4,
    metalness: 0.78
  }),
  machined: new THREE.MeshStandardMaterial({
    name: "g4ed_machined_surface",
    color: 0xd5e5ec,
    roughness: 0.16,
    metalness: 0.9
  }),
  polymer: new THREE.MeshStandardMaterial({
    name: "g4ed_technical_polymer",
    color: 0x101820,
    roughness: 0.63,
    metalness: 0.05
  }),
  gasket: new THREE.MeshStandardMaterial({
    name: "g4ed_gasket",
    color: 0x191d20,
    roughness: 0.86,
    metalness: 0.02
  }),
  oilFlow: new THREE.MeshStandardMaterial({
    name: "g4ed_oil_flow_region",
    color: 0xffb000,
    emissive: 0x5a2600,
    emissiveIntensity: 0.75,
    roughness: 0.28,
    metalness: 0.12,
    transparent: true,
    opacity: 0.88
  }),
  coolantFlow: new THREE.MeshStandardMaterial({
    name: "g4ed_coolant_flow_region",
    color: 0x00d9ff,
    emissive: 0x004d66,
    emissiveIntensity: 0.75,
    roughness: 0.24,
    metalness: 0.08,
    transparent: true,
    opacity: 0.82
  }),
  semantic: new THREE.MeshStandardMaterial({
    name: "g4ed_semantic_region",
    color: 0x00f5cf,
    emissive: 0x004b42,
    emissiveIntensity: 0.7,
    roughness: 0.22,
    metalness: 0.25,
    transparent: true,
    opacity: 0.72
  }),
  accent: new THREE.MeshStandardMaterial({
    name: "g4ed_service_accent",
    color: 0xcc22ff,
    emissive: 0x400050,
    emissiveIntensity: 0.65,
    roughness: 0.26,
    metalness: 0.48
  })
};

export function loadAtlas() {
  return JSON.parse(fs.readFileSync(atlasPath, "utf8"));
}

export function parseRange(argv, maximum = 420) {
  const rangeIndex = argv.indexOf("--range");
  const raw = rangeIndex >= 0 ? argv[rangeIndex + 1] : `1-${maximum}`;
  const match = /^(\d+)(?:-(\d+))?$/.exec(raw ?? "");
  if (!match) throw new Error(`Invalid --range value: ${raw}`);
  const start = Number(match[1]);
  const end = Number(match[2] ?? match[1]);
  if (start < 1 || end > maximum || start > end) {
    throw new Error(`Range must be within 1-${maximum}: ${raw}`);
  }
  return { start, end };
}

export function createPackScene(packId) {
  const scene = new THREE.Scene();
  scene.name = `MEET_${packId}`;
  const root = new THREE.Group();
  root.name = `asset_root__${packId}`;
  root.userData = {
    authority: "REFERENCE_RECONSTRUCTION",
    dimensional: false,
    oemClaim: false
  };
  scene.add(root);
  return { scene, root, meshCount: 0, triangleCount: 0 };
}

export function addMesh(
  state,
  nodeKey,
  detail,
  geometry,
  material,
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  scale = [1, 1, 1]
) {
  let group = state.root.getObjectByName(`${GROUP_PREFIX}${nodeKey}`);
  if (!group) {
    group = new THREE.Group();
    group.name = `${GROUP_PREFIX}${nodeKey}`;
    group.userData = { nodeKey, dimensional: false, oemClaim: false };
    state.root.add(group);
  }
  geometry.computeVertexNormals();
  const mesh = new THREE.Mesh(geometry, material);
  mesh.name = `${NODE_PREFIX}${nodeKey}__${detail}`;
  mesh.position.set(...position);
  mesh.rotation.set(...rotation);
  mesh.scale.set(...scale);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  mesh.userData = { nodeKey, dimensional: false, oemClaim: false };
  group.add(mesh);
  const vertices = geometry.getAttribute("position");
  state.triangleCount += geometry.index ? geometry.index.count / 3 : vertices.count / 3;
  state.meshCount += 1;
  return mesh;
}

export function roundedBox(
  state,
  nodeKey,
  detail,
  size,
  radius,
  material,
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  scale = [1, 1, 1]
) {
  return addMesh(
    state,
    nodeKey,
    detail,
    new RoundedBoxGeometry(size[0], size[1], size[2], 4, radius),
    material,
    position,
    rotation,
    scale
  );
}

export function cylinder(
  state,
  nodeKey,
  detail,
  radius,
  length,
  material,
  position = [0, 0, 0],
  rotation = [0, 0, 0],
  radialSegments = 32
) {
  return addMesh(
    state,
    nodeKey,
    detail,
    new THREE.CylinderGeometry(radius, radius, length, radialSegments, 1, false),
    material,
    position,
    rotation
  );
}

export function torus(
  state,
  nodeKey,
  detail,
  majorRadius,
  tubeRadius,
  material,
  position = [0, 0, 0],
  rotation = [0, 0, 0]
) {
  return addMesh(
    state,
    nodeKey,
    detail,
    new THREE.TorusGeometry(majorRadius, tubeRadius, 12, 36),
    material,
    position,
    rotation
  );
}

export function tube(
  state,
  nodeKey,
  detail,
  points,
  radius,
  material,
  tubularSegments = 36
) {
  const curve = new THREE.CatmullRomCurve3(
    points.map(([x, y, z]) => new THREE.Vector3(x, y, z))
  );
  return addMesh(
    state,
    nodeKey,
    detail,
    new THREE.TubeGeometry(curve, tubularSegments, radius, 10, false),
    material
  );
}

export function bolt(state, nodeKey, detail, position, scale = 1) {
  cylinder(state, nodeKey, `${detail}_shank`, 0.055 * scale, 0.48 * scale, materials.steel, position);
  cylinder(
    state,
    nodeKey,
    `${detail}_head`,
    0.105 * scale,
    0.09 * scale,
    materials.darkSteel,
    [position[0], position[1] + 0.285 * scale, position[2]],
    [0, 0, 0],
    6
  );
}

export function seededVector(ordinal) {
  const angle = ordinal * 2.399963229728653;
  return [
    Number((Math.cos(angle) * (0.85 + (ordinal % 5) * 0.09)).toFixed(4)),
    Number((((ordinal % 7) - 3) * 0.18).toFixed(4)),
    Number((Math.sin(angle) * (0.85 + (ordinal % 3) * 0.12)).toFixed(4))
  ];
}

export function elementBounds(group) {
  group.updateMatrixWorld(true);
  const bounds = new THREE.Box3().setFromObject(group);
  const center = bounds.getCenter(new THREE.Vector3());
  const sphere = bounds.getBoundingSphere(new THREE.Sphere());
  return {
    center: [center.x, center.y, center.z].map((value) => Number(value.toFixed(4))),
    radius: Number((Number.isFinite(sphere.radius) ? sphere.radius : 1).toFixed(4))
  };
}

export async function exportPack(state, packId, elements, atlas) {
  const exporter = new GLTFExporter();
  const arrayBuffer = await new Promise((resolve, reject) => {
    exporter.parse(state.scene, resolve, reject, {
      binary: true,
      trs: true,
      onlyVisible: true,
      includeCustomExtensions: false,
      maxTextureSize: 1024
    });
  });
  const directory = path.join(outputRoot, packId);
  const assetFile = `${packId}.glb`;
  fs.mkdirSync(directory, { recursive: true });
  const glb = Buffer.from(arrayBuffer);
  const sha256 = crypto.createHash("sha256").update(glb).digest("hex");
  fs.writeFileSync(path.join(directory, assetFile), glb);

  const bindings = elements.map((element) => {
    const group = state.root.getObjectByName(`${GROUP_PREFIX}${element.visual.nodeKey}`);
    if (!group) throw new Error(`Missing geometry group for ${element.visual.nodeKey}`);
    return {
      ordinal: element.ordinal,
      canonicalId: element.canonicalId,
      nodeKey: element.visual.nodeKey,
      groupNode: group.name,
      meshNodePrefix: `${NODE_PREFIX}${element.visual.nodeKey}__`,
      parentCanonicalId: element.parentCanonicalId,
      elementKind: element.elementKind,
      renderStrategy: element.visual.renderStrategy,
      authority: element.visual.authority,
      cameraPreset: element.visual.cameraPreset,
      interactionModes: element.visual.interactionModes,
      animationMode: element.visual.animationMode,
      originalTransform: {
        position: [group.position.x, group.position.y, group.position.z],
        rotation: [group.rotation.x, group.rotation.y, group.rotation.z],
        scale: [group.scale.x, group.scale.y, group.scale.z]
      },
      explodeVector: seededVector(element.ordinal),
      bounds: elementBounds(group),
      directlySellable: element.commerce.directlySellable,
      dimensional: false,
      oemClaim: false
    };
  });

  const manifest = {
    schemaVersion: 1,
    packId,
    atlasId: atlas.atlasId,
    atlasVersion: atlas.atlasVersion,
    atlasContentSha256: atlas.contentSha256,
    sourceSha256: atlas.source.sha256,
    assetFile,
    assetPath: `models/g4ed_atlas/${packId}/${assetFile}`,
    geometryAuthority: "MIXED_REFERENCE_AND_SEMANTIC",
    dimensionalState: "ILLUSTRATIVE_PROPORTIONS_ONLY",
    oemClaim: false,
    vehicleSpecificClaim: false,
    generatedBy: "tools/engine-asset-generator/generate-g4ed-atlas.mjs",
    generatorVersion: "1.0.0",
    threeVersion: THREE.REVISION,
    groupNodePrefix: GROUP_PREFIX,
    meshNodePrefix: NODE_PREFIX,
    elementCount: elements.length,
    ordinalRange: [elements[0].ordinal, elements.at(-1).ordinal],
    meshCount: state.meshCount,
    triangleCount: Math.round(state.triangleCount),
    sha256,
    license: "Original procedural reference reconstruction generated for MEET",
    warning: atlas.geometryPolicy.warning,
    bindings
  };
  fs.writeFileSync(path.join(directory, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`);
  return {
    packId,
    elementCount: elements.length,
    meshCount: state.meshCount,
    triangleCount: manifest.triangleCount,
    bytes: glb.length,
    sha256
  };
}

