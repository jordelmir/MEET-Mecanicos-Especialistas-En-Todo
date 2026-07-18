import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  literalContextForEntity,
  PROPRIETARY_VEHICLE_LABEL,
  ProprietaryCatalogManifest,
  ProprietaryEntityIndex,
  ProprietarySectionShard,
  searchProprietaryEntities,
} from '../services/proprietaryPartsCatalog';

const publicAsset = <T>(path: string): T => JSON.parse(readFileSync(resolve('public', path), 'utf8')) as T;
const manifest = publicAsset<ProprietaryCatalogManifest>('knowledge/proprietary/manifest.json');
const index = publicAsset<ProprietaryEntityIndex>('knowledge/proprietary/entity_index.json');

describe('proprietary literal parts catalog', () => {
  it('ships the approved profile and full source coverage', () => {
    expect(PROPRIETARY_VEHICLE_LABEL).toBe('Hyundai Accent/Verna 2005 · caja automática · motor 1600 cc');
    expect(manifest.vehicleLabel).toBe(PROPRIETARY_VEHICLE_LABEL);
    expect(manifest.statistics.blockCount).toBe(74_648);
    expect(manifest.statistics.entityCount).toBeGreaterThan(4_500);
    expect(manifest.systems.length).toBeGreaterThanOrEqual(25);
  });

  it('binds every indexed entity to the universal procedural 3D scene', () => {
    expect(index.entities).toHaveLength(manifest.statistics.entityCount + manifest.statistics.realCaseCount);
    for (const entity of index.entities) {
      expect(entity.threeDimensionalBinding.nodeId).toBe(entity.id);
      expect(entity.threeDimensionalBinding.sceneId).toBe(entity.systemId);
      expect(entity.threeDimensionalBinding.visualAuthority).toBe('PROCEDURAL_SCHEMATIC');
      expect(entity.threeDimensionalBinding.isDimensionalModel).toBe(false);
    }
  });

  it('finds literal accents and keeps the related source context', () => {
    const results = searchProprietaryEntities(index.entities, 'Sensor CKP', 'sensors', 20);
    expect(results.length).toBeGreaterThan(0);
    const entity = results[0];
    const shard = publicAsset<ProprietarySectionShard>(entity.shardPath.replace('knowledge/proprietary/', 'knowledge/proprietary/'));
    const context = literalContextForEntity(shard, entity);
    expect(context[0].blockId).toBe(entity.sourceBlockId);
    expect(context[0].text).toBe(entity.nameOriginal);
  });
});
