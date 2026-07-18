import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  Box,
  Database,
  FileCheck2,
  Layers3,
  Loader2,
  Orbit,
  ScanLine,
  Search,
  Sparkles,
} from 'lucide-react';
import {
  literalContextForEntity,
  loadProprietaryCatalog,
  loadProprietarySection,
  PROPRIETARY_VEHICLE_LABEL,
  ProprietaryCatalogEntity,
  ProprietaryCatalogManifest,
  ProprietaryEntityIndex,
  ProprietarySourceBlock,
  searchProprietaryEntities,
} from '../services/proprietaryPartsCatalog';

interface ProprietaryPartsExplorerProps {
  onOpenIn3D?: (partId: string, nodeId: string) => void;
  onOpenGuidedPilot: () => void;
}

export function ProprietaryPartsExplorer({ onOpenIn3D, onOpenGuidedPilot }: ProprietaryPartsExplorerProps) {
  const [manifest, setManifest] = useState<ProprietaryCatalogManifest | null>(null);
  const [index, setIndex] = useState<ProprietaryEntityIndex | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [systemId, setSystemId] = useState<string | null>(null);
  const [selected, setSelected] = useState<ProprietaryCatalogEntity | null>(null);
  const [literalBlocks, setLiteralBlocks] = useState<ProprietarySourceBlock[]>([]);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const explorerTopRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    loadProprietaryCatalog()
      .then(catalog => {
        if (!active) return;
        setManifest(catalog.manifest);
        setIndex(catalog.index);
      })
      .catch(cause => active && setError(cause instanceof Error ? cause.message : String(cause)));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selected) {
      setLiteralBlocks([]);
      return;
    }
    let active = true;
    setLoadingDetail(true);
    loadProprietarySection(selected.shardPath)
      .then(shard => active && setLiteralBlocks(literalContextForEntity(shard, selected)))
      .catch(cause => active && setError(cause instanceof Error ? cause.message : String(cause)))
      .finally(() => active && setLoadingDetail(false));
    return () => { active = false; };
  }, [selected]);

  useEffect(() => {
    explorerTopRef.current?.scrollIntoView({ block: 'start', behavior: 'auto' });
  }, [selected]);

  const results = useMemo(
    () => searchProprietaryEntities(index?.entities ?? [], query, systemId),
    [index, query, systemId],
  );
  const selectedSystem = manifest?.systems.find(system => system.id === selected?.systemId);

  if (error) {
    return (
      <div className="min-h-[560px] bg-slate-950 p-8 text-rose-300">
        <FileCheck2 className="mb-3" />
        <h2 className="text-lg font-bold text-white">El corpus propietario no superó la validación</h2>
        <p className="mt-2 text-sm">{error}</p>
      </div>
    );
  }

  if (!manifest || !index) {
    return (
      <div className="flex min-h-[560px] items-center justify-center bg-slate-950 text-cyan-300">
        <Loader2 className="mr-3 animate-spin" />
        <span className="font-mono text-xs uppercase">Validando 74.648 bloques propietarios</span>
      </div>
    );
  }

  if (selected) {
    return (
      <div ref={explorerTopRef} className="relative min-h-[680px] overflow-hidden bg-[#03070b] text-white">
        <div className="pointer-events-none absolute inset-0 opacity-30 [background-image:linear-gradient(rgba(34,211,238,.08)_1px,transparent_1px),linear-gradient(90deg,rgba(163,230,53,.06)_1px,transparent_1px)] [background-size:32px_32px]" />
        <div className="relative border-b border-cyan-400/20 px-4 py-4 md:px-7">
          <button onClick={() => setSelected(null)} className="inline-flex items-center gap-2 text-xs font-bold text-cyan-300 hover:text-white">
            <ArrowLeft size={16} /> Volver a piezas
          </button>
          <div className="mt-4 flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
            <div>
              <div className="mb-2 flex flex-wrap items-center gap-2 text-[10px] font-bold uppercase text-lime-300">
                <span>{selectedSystem?.title}</span><span>•</span><span>{selected.recordRole === 'REAL_CASE' ? 'Caso real' : 'Pieza'}</span>
              </div>
              <h2 className="max-w-4xl text-xl font-black leading-tight md:text-2xl">{selected.nameOriginal}</h2>
              <p className="mt-2 text-xs text-slate-400">{selected.vehicleScope}</p>
            </div>
            <button
              onClick={() => onOpenIn3D?.(selected.id, selected.threeDimensionalBinding.nodeId)}
              className="inline-flex h-11 items-center justify-center gap-2 border border-lime-300/60 bg-lime-300/10 px-4 text-xs font-black uppercase text-lime-200 shadow-[0_0_24px_rgba(163,230,53,.2)] hover:bg-lime-300/20"
            >
              <Orbit size={17} /> Abrir en Motor 3D
            </button>
          </div>
        </div>

        <div className="relative grid gap-0 lg:grid-cols-[minmax(320px,42%)_1fr]">
          <div className="relative min-h-[330px] overflow-hidden border-b border-cyan-400/20 bg-black/30 lg:border-b-0 lg:border-r">
            <HolographicPartScene entity={selected} color={selectedSystem?.color ?? '#22D3EE'} />
          </div>
          <div className="max-h-[600px] overflow-y-auto px-4 py-5 md:px-7">
            <div className="mb-4 flex items-center justify-between border-b border-white/10 pb-3">
              <div className="flex items-center gap-2 text-xs font-black uppercase text-cyan-300"><Database size={15} /> Información literal</div>
              <span className="font-mono text-[9px] text-slate-500">{selected.sourceFileName} · orden {selected.sourceOrder}</span>
            </div>
            {loadingDetail ? (
              <Loader2 className="animate-spin text-cyan-300" />
            ) : (
              <div className="space-y-3">
                {literalBlocks.map(block => (
                  <div key={block.blockId} className="border-l-2 border-cyan-400/30 bg-white/[0.025] px-3 py-2.5">
                    <div className="mb-1 font-mono text-[8px] uppercase text-slate-600">{block.recordRole} · #{block.order} · SHA {block.textHash.slice(0, 12)}</div>
                    <p className="whitespace-pre-wrap text-[12px] leading-relaxed text-slate-200">{block.text}</p>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-5 border-t border-white/10 pt-3 font-mono text-[9px] text-slate-500">
              Fuente propietaria del usuario · SHA-256 documento {selected.sourceDocumentSha256}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div ref={explorerTopRef} className="relative min-h-[680px] overflow-hidden bg-[#03070b] text-white">
      <div className="pointer-events-none absolute inset-0 opacity-40 [background-image:linear-gradient(rgba(34,211,238,.06)_1px,transparent_1px),linear-gradient(90deg,rgba(250,204,21,.04)_1px,transparent_1px)] [background-size:36px_36px]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px animate-pulse bg-cyan-300 shadow-[0_0_26px_6px_rgba(34,211,238,.35)]" />
      <header className="relative border-b border-cyan-400/20 px-4 py-5 md:px-7">
        <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-end">
          <div>
            <div className="flex items-center gap-2 text-[10px] font-black uppercase text-lime-300"><Sparkles size={14} /> Base propietaria completa</div>
            <h1 className="mt-2 text-2xl font-black md:text-3xl">Piezas · {PROPRIETARY_VEHICLE_LABEL}</h1>
            <p className="mt-2 font-mono text-[10px] text-slate-400">{manifest.statistics.entityCount.toLocaleString('es')} piezas · {manifest.statistics.realCaseCount} casos reales · {manifest.statistics.blockCount.toLocaleString('es')} bloques literales</p>
          </div>
          <button onClick={onOpenGuidedPilot} className="inline-flex h-10 items-center justify-center gap-2 border border-amber-300/30 bg-amber-300/5 px-3 text-[10px] font-bold uppercase text-amber-200 hover:bg-amber-300/10">
            <Layers3 size={15} /> Taller guiado
          </button>
        </div>
        <div className="relative mt-5 max-w-3xl">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-cyan-300" size={17} />
          <input
            value={query}
            onChange={event => setQuery(event.target.value)}
            placeholder="Buscar pieza, sensor, módulo, sistema o caso real"
            className="h-12 w-full border border-cyan-400/30 bg-black/50 pl-10 pr-4 text-sm text-white outline-none shadow-[inset_0_0_20px_rgba(34,211,238,.04)] focus:border-lime-300/60"
          />
          <ScanLine className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 animate-pulse text-lime-300" size={17} />
        </div>
      </header>

      <div className="relative flex gap-2 overflow-x-auto border-b border-white/10 px-4 py-3 md:px-7">
        <button onClick={() => setSystemId(null)} className={`shrink-0 border px-3 py-2 text-[10px] font-black uppercase ${systemId === null ? 'border-white/50 bg-white/10 text-white' : 'border-white/10 text-slate-500'}`}>Todos</button>
        {manifest.systems.map(system => (
          <button
            key={system.id}
            onClick={() => setSystemId(system.id)}
            className="shrink-0 border px-3 py-2 text-[10px] font-black uppercase transition-transform hover:-translate-y-0.5"
            style={{ borderColor: `${system.color}66`, color: systemId === system.id ? '#020617' : system.color, backgroundColor: systemId === system.id ? system.color : `${system.color}10`, boxShadow: systemId === system.id ? `0 0 20px ${system.color}44` : undefined }}
          >
            {system.title} · {system.entityCount}
          </button>
        ))}
      </div>

      <main className="relative px-4 py-4 md:px-7">
        <div className="mb-3 flex items-center justify-between text-[10px] font-mono text-slate-500">
          <span>{results.length} resultados visibles</span>
          {results.length === 400 && <span>Escribe más para afinar</span>}
        </div>
        <div className="divide-y divide-white/5 border-y border-white/10">
          {results.map((entity, position) => {
            const system = manifest.systems.find(item => item.id === entity.systemId);
            return (
              <button
                key={entity.id}
                onClick={() => setSelected(entity)}
                className="group grid w-full grid-cols-[34px_minmax(0,1fr)_auto] items-center gap-3 bg-black/10 px-2 py-3 text-left transition-all hover:bg-white/[0.04]"
              >
                <div className="relative flex h-8 w-8 items-center justify-center border" style={{ borderColor: `${system?.color ?? '#22D3EE'}66`, color: system?.color }}>
                  {entity.recordRole === 'REAL_CASE' ? <FileCheck2 size={15} /> : <Box size={15} />}
                  <span className="absolute inset-0 animate-ping border opacity-0 group-hover:opacity-30" style={{ borderColor: system?.color }} />
                </div>
                <div className="min-w-0">
                  <div className="line-clamp-2 text-xs font-bold text-slate-100 group-hover:text-white">{entity.nameOriginal}</div>
                  <div className="mt-1 font-mono text-[8px] uppercase text-slate-600">{system?.title} · {entity.sourceFileName} #{entity.sourceOrder}</div>
                </div>
                <div className="font-mono text-[9px] text-slate-600">{String(position + 1).padStart(3, '0')}</div>
              </button>
            );
          })}
        </div>
      </main>
    </div>
  );
}

function HolographicPartScene({ entity, color }: { entity: ProprietaryCatalogEntity; color: string }) {
  const seed = entity.threeDimensionalBinding.seed;
  const bars = Array.from({ length: 8 }, (_, index) => ({
    rotate: ((seed >> (index % 12)) + index * 37) % 180,
    width: 56 + ((seed + index * 17) % 110),
    delay: `${(index * 0.13).toFixed(2)}s`,
  }));
  return (
    <div className="absolute inset-0 flex items-center justify-center [perspective:700px]">
      <div className="absolute inset-x-[12%] bottom-[14%] h-[22%] rounded-[50%] border opacity-30 [transform:rotateX(68deg)]" style={{ borderColor: color, boxShadow: `0 0 50px ${color}55` }} />
      <div className="relative h-48 w-48 animate-[spin_18s_linear_infinite] [transform-style:preserve-3d]">
        {bars.map((bar, index) => (
          <div
            key={index}
            className="absolute left-1/2 top-1/2 h-3 -translate-x-1/2 -translate-y-1/2 border bg-black/50 shadow-lg animate-pulse"
            style={{ width: bar.width, borderColor: color, boxShadow: `0 0 18px ${color}88`, transform: `translate(-50%,-50%) rotateY(${bar.rotate}deg) rotateX(${index * 21}deg) translateZ(${28 + index * 4}px)`, animationDelay: bar.delay }}
          />
        ))}
      </div>
      <div className="absolute bottom-5 left-5 right-5 flex items-center justify-between font-mono text-[9px] uppercase" style={{ color }}>
        <span>Esquema procedural</span><span>Seed {seed}</span>
      </div>
    </div>
  );
}
