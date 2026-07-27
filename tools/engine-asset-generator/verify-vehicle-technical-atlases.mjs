import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { repoRoot } from "./g4ed-atlas-common.mjs";

const domainIndex = process.argv.indexOf("--atlas");
const selectedDomain = domainIndex >= 0 ? process.argv[domainIndex + 1] : "all";
const knownDomains = ["transmission_hydraulics", "electrical", "body", "remaining_systems"];
if (selectedDomain !== "all" && !knownDomains.includes(selectedDomain)) {
  throw new Error(`Unknown --atlas ${selectedDomain}; expected ${knownDomains.join(", ")} or all`);
}

const atlasRoot = path.join(repoRoot, "android/app/src/main/assets/knowledge/vehicle_technical_atlases");
const modelRoot = path.join(repoRoot, "android/app/src/main/assets/models/vehicle_technical_atlases");

function decodeGlb(pathname) {
  const raw = fs.readFileSync(pathname);
  if (raw.readUInt32LE(0) !== 0x46546c67 || raw.readUInt32LE(4) !== 2) {
    throw new Error(`Invalid GLB header: ${pathname}`);
  }
  const jsonLength = raw.readUInt32LE(12);
  if (raw.readUInt32LE(16) !== 0x4e4f534a) throw new Error(`Missing GLB JSON: ${pathname}`);
  return { raw, json: JSON.parse(raw.subarray(20, 20 + jsonLength).toString("utf8").trim()) };
}

const domains = selectedDomain === "all" ? knownDomains : [selectedDomain];
const results = [];
for (const domainId of domains) {
  const atlas = JSON.parse(fs.readFileSync(path.join(atlasRoot, `${domainId}_atlas.json`), "utf8"));
  const byPack = Map.groupBy(atlas.elements, (element) => element.visual.packId);
  for (const [packId, elements] of byPack) {
    const directory = path.join(modelRoot, domainId, packId);
    const manifestPath = path.join(directory, "manifest.json");
    if (!fs.existsSync(manifestPath)) throw new Error(`Missing manifest: ${domainId}/${packId}`);
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    const { raw, json } = decodeGlb(path.join(directory, manifest.assetFile));
    const sha256 = crypto.createHash("sha256").update(raw).digest("hex");
    if (manifest.atlasId !== atlas.atlasId || manifest.atlasContentSha256 !== atlas.contentSha256) {
      throw new Error(`Atlas identity mismatch: ${domainId}/${packId}`);
    }
    if (sha256 !== manifest.sha256) throw new Error(`SHA-256 mismatch: ${domainId}/${packId}`);
    if (manifest.oemClaim || manifest.vehicleSpecificClaim) {
      throw new Error(`Unsupported geometry claim: ${domainId}/${packId}`);
    }
    if (manifest.bindings.length !== elements.length || manifest.elementCount !== elements.length) {
      throw new Error(`Element count mismatch: ${domainId}/${packId}`);
    }
    const nodes = new Set((json.nodes ?? []).map((node) => node.name).filter(Boolean));
    const bindings = new Map(manifest.bindings.map((binding) => [binding.canonicalId, binding]));
    for (const element of elements) {
      const binding = bindings.get(element.canonicalId);
      if (!binding || binding.nodeKey !== element.visual.nodeKey) {
        throw new Error(`Binding mismatch: ${element.canonicalId}`);
      }
      if (!nodes.has(binding.groupNode) || ![...nodes].some((node) => node.startsWith(binding.meshNodePrefix))) {
        throw new Error(`Missing GLB node: ${element.canonicalId}`);
      }
      if (binding.oemClaim || binding.dimensional || binding.authority !== element.visual.authority) {
        throw new Error(`Authority mismatch: ${element.canonicalId}`);
      }
      if (!Number.isFinite(binding.bounds.radius) || binding.bounds.radius <= 0) {
        throw new Error(`Invalid bounds: ${element.canonicalId}`);
      }
    }
    results.push({
      domainId,
      packId,
      elements: elements.length,
      meshes: manifest.meshCount,
      triangles: manifest.triangleCount,
      bytes: raw.length,
      sha256
    });
  }
}

console.log(JSON.stringify({
  domains,
  verifiedPacks: results.length,
  verifiedElements: results.reduce((sum, item) => sum + item.elements, 0),
  bytes: results.reduce((sum, item) => sum + item.bytes, 0),
  results
}, null, 2));
