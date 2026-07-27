import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { loadAtlas, outputRoot, parseRange } from "./g4ed-atlas-common.mjs";

function glbJson(pathname) {
  const raw = fs.readFileSync(pathname);
  if (raw.readUInt32LE(0) !== 0x46546c67 || raw.readUInt32LE(4) !== 2) {
    throw new Error(`Invalid GLB header: ${pathname}`);
  }
  const jsonLength = raw.readUInt32LE(12);
  const jsonType = raw.readUInt32LE(16);
  if (jsonType !== 0x4e4f534a) throw new Error(`Missing GLB JSON chunk: ${pathname}`);
  return { raw, json: JSON.parse(raw.subarray(20, 20 + jsonLength).toString("utf8").trim()) };
}

const atlas = loadAtlas();
const { start, end } = parseRange(process.argv.slice(2), atlas.elements.length);
const selected = atlas.elements.filter((element) => element.ordinal >= start && element.ordinal <= end);
const byPack = Map.groupBy(selected, (element) => element.visual.packId);
const results = [];

for (const [packId, elements] of byPack) {
  const directory = path.join(outputRoot, packId);
  const manifestPath = path.join(directory, "manifest.json");
  if (!fs.existsSync(manifestPath)) throw new Error(`Missing manifest for ${packId}`);
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  const assetPath = path.join(directory, manifest.assetFile);
  const { raw, json } = glbJson(assetPath);
  const sha256 = crypto.createHash("sha256").update(raw).digest("hex");
  if (sha256 !== manifest.sha256) throw new Error(`SHA-256 mismatch for ${packId}`);
  if (manifest.atlasContentSha256 !== atlas.contentSha256) {
    throw new Error(`Atlas hash mismatch for ${packId}`);
  }
  if (manifest.oemClaim || manifest.vehicleSpecificClaim) {
    throw new Error(`Unsupported geometry authority in ${packId}`);
  }

  const bindings = new Map(manifest.bindings.map((binding) => [binding.ordinal, binding]));
  const nodeNames = new Set((json.nodes ?? []).map((node) => node.name).filter(Boolean));
  for (const element of elements) {
    const binding = bindings.get(element.ordinal);
    if (!binding) throw new Error(`Missing binding for ordinal ${element.ordinal}`);
    if (binding.canonicalId !== element.canonicalId || binding.nodeKey !== element.visual.nodeKey) {
      throw new Error(`Wrong binding for ordinal ${element.ordinal}`);
    }
    if (!nodeNames.has(binding.groupNode)) {
      throw new Error(`Missing group node ${binding.groupNode}`);
    }
    if (![...nodeNames].some((name) => name.startsWith(binding.meshNodePrefix))) {
      throw new Error(`Missing mesh node for ordinal ${element.ordinal}`);
    }
    if (binding.oemClaim || binding.dimensional) {
      throw new Error(`Invalid claim for ordinal ${element.ordinal}`);
    }
    if (element.visual.renderStrategy === "SEMANTIC_REGION" && binding.directlySellable) {
      throw new Error(`Semantic region is directly sellable: ${element.ordinal}`);
    }
    if (!Number.isFinite(binding.bounds.radius) || binding.bounds.radius <= 0) {
      throw new Error(`Invalid bounds for ordinal ${element.ordinal}`);
    }
  }
  results.push({
    packId,
    verifiedElements: elements.length,
    sha256,
    bytes: raw.length,
    glbNodes: nodeNames.size
  });
}

console.log(JSON.stringify({ range: [start, end], verified: results }, null, 2));

