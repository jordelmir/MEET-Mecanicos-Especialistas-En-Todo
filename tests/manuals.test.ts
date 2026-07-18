import { describe, it, expect, vi, beforeEach } from 'vitest';
import { tokenize, LocalFtsIndex } from '../services/automotiveKnowledgeEngine';
import type { KnowledgeChunk } from '../types';

// Mock storage module - no localStorage in Node
vi.mock('../services/storage', () => ({
  saveState: vi.fn(),
  loadState: vi.fn(() => []),
}));

// Mock ids module
vi.mock('../services/ids', () => ({
  createId: vi.fn(() => `mock_id_${Date.now()}_${Math.random().toString(36).slice(2)}`),
}));

// ─── TOKENIZER ───────────────────────────────────────────

describe('tokenize', () => {
  it('lowercases and splits input into words', () => {
    const tokens = tokenize('Hello World');
    expect(tokens).toEqual(['hello', 'world']);
  });

  it('removes Spanish accents (NFD normalization)', () => {
    const tokens = tokenize('Diagnóstico de válvula EGR');
    expect(tokens).toContain('diagnostico');
    expect(tokens).toContain('valvula');
    expect(tokens).toContain('egr');
  });

  it('removes punctuation and keeps alphanumeric', () => {
    const tokens = tokenize('P0230: Circuito de bomba (combustible)');
    expect(tokens).toContain('p0230');
    expect(tokens).toContain('circuito');
    expect(tokens).toContain('bomba');
    expect(tokens).toContain('combustible');
    expect(tokens.every(t => /^[a-z0-9]+$/.test(t))).toBe(true);
  });

  it('filters out single-character tokens', () => {
    const tokens = tokenize('a b cd efg');
    expect(tokens).toEqual(['cd', 'efg']);
  });

  it('returns empty array for empty input', () => {
    expect(tokenize('')).toEqual([]);
  });

  it('handles DTC codes correctly', () => {
    const tokens = tokenize('Error P0171 mezcla pobre');
    expect(tokens).toContain('p0171');
    expect(tokens).toContain('mezcla');
    expect(tokens).toContain('pobre');
  });
});

// ─── FTS INDEX ───────────────────────────────────────────

describe('LocalFtsIndex', () => {
  let index: LocalFtsIndex;

  const makeChunk = (id: string, text: string, docId: string = 'doc_1'): KnowledgeChunk => ({
    id,
    document_id: docId,
    vehicle_id_nullable: null,
    section_title_nullable: null,
    page_start_nullable: 1,
    page_end_nullable: 1,
    chunk_index: 0,
    text,
    token_count: text.split(/\s+/).length,
    embedding_vector_nullable: null,
    content_hash: `hash_${id}`,
    created_at: new Date().toISOString(),
  });

  beforeEach(() => {
    index = new LocalFtsIndex();
  });

  it('returns results for matching queries', () => {
    index.addChunk(makeChunk('chk_1', 'Bomba de combustible P0230 circuito primario'));
    index.addChunk(makeChunk('chk_2', 'Sensor de oxígeno banco uno'));
    const results = index.search('bomba combustible');
    expect(results.length).toBeGreaterThan(0);
    expect(results[0].chunk.id).toBe('chk_1');
  });

  it('returns empty array for no matches', () => {
    index.addChunk(makeChunk('chk_1', 'Bomba de combustible'));
    const results = index.search('transmision automatica');
    expect(results).toEqual([]);
  });

  it('ranks DTC code matches higher due to tokenWeight', () => {
    index.addChunk(makeChunk('chk_general', 'El circuito del relé de bomba tiene un problema general'));
    index.addChunk(makeChunk('chk_dtc', 'P0230 Circuito del relé de bomba de combustible'));
    const results = index.search('P0230');
    expect(results.length).toBeGreaterThan(0);
    expect(results[0].chunk.id).toBe('chk_dtc');
  });

  it('supports filterDocId to restrict results', () => {
    index.addChunk(makeChunk('chk_a', 'Bomba de combustible manual Toyota', 'doc_toyota'));
    index.addChunk(makeChunk('chk_b', 'Bomba de combustible manual Hyundai', 'doc_hyundai'));
    const results = index.search('bomba combustible', 'doc_toyota');
    expect(results.length).toBe(1);
    expect(results[0].chunk.id).toBe('chk_a');
  });

  it('clears all data when clear() is called', () => {
    index.addChunk(makeChunk('chk_1', 'Algo de texto'));
    expect(index.search('algo').length).toBeGreaterThan(0);
    index.clear();
    expect(index.search('algo')).toEqual([]);
  });

  it('handles accent-insensitive search', () => {
    index.addChunk(makeChunk('chk_1', 'Diagnóstico de válvula'));
    const results = index.search('diagnostico valvula');
    expect(results.length).toBeGreaterThan(0);
  });
});

// ─── RAG ENGINE ──────────────────────────────────────────

describe('AutomotiveKnowledgeRagEngine', () => {
  let engine: any;

  beforeEach(async () => {
    vi.resetModules();
    vi.doMock('../services/storage', () => ({
      saveState: vi.fn(),
      loadState: vi.fn(() => []),
    }));
    vi.doMock('../services/ids', () => ({
      createId: vi.fn(() => `mock_id_${Date.now()}_${Math.random().toString(36).slice(2)}`),
    }));
    const mod = await import('../services/automotiveKnowledgeEngine');
    engine = new mod.AutomotiveKnowledgeRagEngine();
  });

  it('seed database populates documents and chunks', () => {
    expect(engine.getDocuments().length).toBeGreaterThan(0);
    expect(engine.getChunks().length).toBeGreaterThan(0);
  });

  it('seed database populates torque spec cards', () => {
    const torques = engine.getTorqueCards();
    expect(torques.length).toBeGreaterThan(0);
    torques.forEach((t: any) => {
      expect(t.component).toBeTruthy();
      expect(t.torque_value).toBeTruthy();
      expect(t.unit).toBeTruthy();
    });
  });

  it('seed database populates fluid spec cards', () => {
    const fluids = engine.getFluidCards();
    expect(fluids.length).toBeGreaterThan(0);
    fluids.forEach((f: any) => {
      expect(f.system).toBeTruthy();
      expect(f.fluid_type).toBeTruthy();
      expect(f.capacity).toBeGreaterThan(0);
    });
  });

  it('seed database populates diagnostic procedure cards', () => {
    const procs = engine.getProcedureCards();
    expect(procs.length).toBeGreaterThan(0);
    procs.forEach((p: any) => {
      expect(p.title).toBeTruthy();
      expect(p.steps.length).toBeGreaterThan(0);
      expect(p.tools_required.length).toBeGreaterThan(0);
    });
  });

  it('searchManuals returns results for known DTC codes', () => {
    const results = engine.searchManuals('P0230');
    expect(results.length).toBeGreaterThan(0);
  });

  it('searchManuals returns empty for unknown content', () => {
    const results = engine.searchManuals('xyznonexistentthing12345');
    expect(results).toEqual([]);
  });

  it('answerTechnicalQuestion returns UNSOURCED for unknown queries', () => {
    const result = engine.answerTechnicalQuestion(
      'warp propulsion quantum-drive',
      null,
      null
    );
    expect(result.quality).toBe('UNSOURCED');
    expect(result.confidence).toBe('LOW');
    expect(result.answer).toContain('No tengo una fuente local confiable');
  });

  it('answerTechnicalQuestion returns procedure for known DTC', () => {
    const result = engine.answerTechnicalQuestion(
      'diagnóstico P0230',
      null,
      'P0230'
    );
    expect(result.answer).toContain('Circuito Primario');
    expect(result.answer).toContain('P0230');
  });

  it('answerTechnicalQuestion returns fluid specs when asked', () => {
    const result = engine.answerTechnicalQuestion(
      'aceite de motor capacidad',
      { make: 'Hyundai', model: 'Accent', year: 2005 } as any,
      null
    );
    expect(
      result.answer.includes('Fluido') || result.answer.includes('Aceite') || result.answer.includes('aceite')
    ).toBe(true);
  });

  it('getGuidedProcedureForDtc returns steps for known DTC', () => {
    const steps = engine.getGuidedProcedureForDtc('P0230');
    expect(steps.length).toBeGreaterThan(0);
    steps.forEach((s: any) => {
      expect(s.instruction).toBeTruthy();
      expect(s.order).toBeGreaterThan(0);
    });
  });

  it('getGuidedProcedureForDtc returns empty array for unknown DTC', () => {
    const steps = engine.getGuidedProcedureForDtc('P9999');
    expect(steps).toEqual([]);
  });

  it('never invents data - strict hallucination guard', () => {
    const queries = [
      'como reparar la computadora de un auto',
      'donde comprar repuestos baratos',
      'manual de Ford F-150 2024',
    ];
    queries.forEach(q => {
      const result = engine.answerTechnicalQuestion(q, null, null);
      if (result.quality === 'UNSOURCED') {
        expect(result.answer).toContain('No tengo una fuente local confiable');
      }
      if (result.citations.length === 0) {
        expect(result.confidence).not.toBe('HIGH');
      }
    });
  });

  it('deleteDocument removes doc and rebuilds index', () => {
    const docsBefore = engine.getDocuments().length;
    const firstDocId = engine.getDocuments()[0].id;
    engine.deleteDocument(firstDocId);
    expect(engine.getDocuments().length).toBe(docsBefore - 1);
    expect(engine.getDocuments().find((d: any) => d.id === firstDocId)).toBeUndefined();
  });
});
