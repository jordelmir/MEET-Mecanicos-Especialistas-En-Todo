export const PROPRIETARY_VEHICLE_LABEL = 'Hyundai Accent/Verna 2005 · caja automática · motor 1600 cc';
export const PROPRIETARY_MANIFEST_PATH = '/knowledge/proprietary/manifest.json';
export const PROPRIETARY_ENTITY_INDEX_PATH = '/knowledge/proprietary/entity_index.json';

export interface ProprietaryCatalogSystem {
  id: string;
  title: string;
  color: string;
  sectionCount: number;
  blockCount: number;
  entityCount: number;
  realCaseCount: number;
}

export interface ProprietaryCatalogSection {
  id: string;
  systemId: string;
  titleOriginal: string;
  sourceDocumentId: string;
  sourceFileName: string;
  sourceDocumentSha256: string;
  sourceOrderStart: number;
  sourceOrderEnd: number;
  blockCount: number;
  entityCount: number;
  realCaseCount: number;
  shardPath: string;
  contentSha256: string;
}

export interface ProprietaryCatalogManifest {
  schemaVersion: number;
  corpusId: string;
  corpusVersion: string;
  title: string;
  vehicleLabel: string;
  provenanceLabel: string;
  visualAuthority: 'PROCEDURAL_SCHEMATIC';
  systems: ProprietaryCatalogSystem[];
  sections: ProprietaryCatalogSection[];
  entityIndexPath: string;
  statistics: {
    blockCount: number;
    entityCount: number;
    realCaseCount: number;
    sectionCount: number;
    shardCount: number;
    roleCounts: Record<string, number>;
  };
  contentSha256: string;
}

export interface ProprietaryCatalogEntity {
  id: string;
  nameOriginal: string;
  recordRole: 'COMPONENT' | 'REAL_CASE';
  systemId: string;
  sectionId: string;
  shardPath: string;
  sourceDocumentId: string;
  sourceFileName: string;
  sourceDocumentSha256: string;
  sourceBlockId: string;
  sourceTextHash: string;
  sourceOrder: number;
  vehicleScope: string;
  threeDimensionalBinding: {
    sceneId: string;
    nodeId: string;
    visualAuthority: 'PROCEDURAL_SCHEMATIC';
    isDimensionalModel: false;
    seed: number;
  };
}

export interface ProprietaryEntityIndex {
  schemaVersion: number;
  corpusId: string;
  corpusVersion: string;
  vehicleLabel: string;
  entities: ProprietaryCatalogEntity[];
  contentSha256: string;
}

export interface ProprietarySourceBlock {
  blockId: string;
  kind: 'paragraph' | 'table';
  order: number;
  recordRole: 'SECTION_TITLE' | 'COMPONENT' | 'REAL_CASE' | 'TABLE' | 'SOURCE_DETAIL';
  sectionPath: string[];
  styleId: string;
  text: string;
  textHash: string;
  entityId: string | null;
  parentEntityId: string | null;
  rows?: string[][];
}

export interface ProprietarySectionShard {
  schemaVersion: number;
  corpusId: string;
  sectionId: string;
  systemId: string;
  titleOriginal: string;
  vehicleLabel: string;
  sourceDocumentId: string;
  sourceFileName: string;
  sourceDocumentSha256: string;
  blocks: ProprietarySourceBlock[];
  contentSha256: string;
}

let catalogPromise: Promise<{ manifest: ProprietaryCatalogManifest; index: ProprietaryEntityIndex }> | null = null;
const sectionCache = new Map<string, Promise<ProprietarySectionShard>>();

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`No se pudo cargar ${path}: HTTP ${response.status}`);
  return response.json() as Promise<T>;
}

export function loadProprietaryCatalog(): Promise<{ manifest: ProprietaryCatalogManifest; index: ProprietaryEntityIndex }> {
  catalogPromise ??= Promise.all([
    fetchJson<ProprietaryCatalogManifest>(PROPRIETARY_MANIFEST_PATH),
    fetchJson<ProprietaryEntityIndex>(PROPRIETARY_ENTITY_INDEX_PATH),
  ]).then(([manifest, index]) => {
    if (manifest.schemaVersion !== 1 || index.schemaVersion !== 1) throw new Error('Versión de catálogo no compatible');
    if (manifest.vehicleLabel !== PROPRIETARY_VEHICLE_LABEL || index.vehicleLabel !== PROPRIETARY_VEHICLE_LABEL) {
      throw new Error('El perfil vehicular propietario no coincide');
    }
    if (manifest.statistics.blockCount !== 74_648 || manifest.statistics.entityCount < 4_500) {
      throw new Error('El corpus propietario está incompleto');
    }
    return { manifest, index };
  });
  return catalogPromise;
}

export function loadProprietarySection(path: string): Promise<ProprietarySectionShard> {
  const publicPath = path.startsWith('/') ? path : `/${path}`;
  let pending = sectionCache.get(publicPath);
  if (!pending) {
    pending = fetchJson<ProprietarySectionShard>(publicPath);
    sectionCache.set(publicPath, pending);
    if (sectionCache.size > 8) {
      const oldest = sectionCache.keys().next().value as string | undefined;
      if (oldest && oldest !== publicPath) sectionCache.delete(oldest);
    }
  }
  return pending;
}

function normalized(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

export function searchProprietaryEntities(
  entities: readonly ProprietaryCatalogEntity[],
  query: string,
  systemId: string | null,
  limit = 400,
): ProprietaryCatalogEntity[] {
  const needle = normalized(query);
  const result: ProprietaryCatalogEntity[] = [];
  for (const entity of entities) {
    if (systemId && entity.systemId !== systemId) continue;
    if (needle && !normalized(entity.nameOriginal).includes(needle)) continue;
    result.push(entity);
    if (result.length >= limit) break;
  }
  return result;
}

export function literalContextForEntity(
  shard: ProprietarySectionShard,
  entity: ProprietaryCatalogEntity,
  limit = 360,
): ProprietarySourceBlock[] {
  const sourceIndex = shard.blocks.findIndex(block => block.blockId === entity.sourceBlockId);
  if (sourceIndex < 0) return [];
  const result = [shard.blocks[sourceIndex]];
  for (let index = sourceIndex + 1; index < shard.blocks.length && result.length < limit; index += 1) {
    const block = shard.blocks[index];
    if (block.entityId && block.entityId !== entity.id) break;
    if (block.parentEntityId === entity.id || block.entityId === entity.id || entity.recordRole === 'REAL_CASE') {
      result.push(block);
    } else if (result.length > 1) {
      break;
    }
  }
  return result;
}
