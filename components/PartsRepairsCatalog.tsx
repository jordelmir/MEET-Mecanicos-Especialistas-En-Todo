import React, { useState, useMemo, useCallback } from 'react';
import {
  DetailedPart,
  GuidedRepairProcedure,
  RepairStep3D,
  VehicleProfile
} from '../types';
import {
  SOURCE_BACKED_PARTS_CATALOG,
  SOURCE_BACKED_REPAIR_PROCEDURES,
  SOURCE_BACKED_PACK_META,
  canCompleteRepairStep,
  loadRepairProgress,
  saveRepairProgress
} from '../services/universalPartsCatalog';
import { AuthoringConsole } from './AuthoringConsole';
import { ProprietaryPartsExplorer } from './ProprietaryPartsExplorer';
import {
  Search,
  Wrench,
  ChevronRight,
  ChevronDown,
  Layers,
  Zap,
  AlertTriangle,
  Info,
  ShieldAlert,
  Clock,
  Check,
  Play,
  Eye,
  ArrowRight,
  Filter,
  Star,
  Box,
  Cpu,
  Activity,
  Target,
  X,
  ChevronLeft,
  Shield
} from 'lucide-react';

// ── INTERFACES ──────────────────────────────────────────────

interface PartsRepairsCatalogProps {
  vehicle: VehicleProfile | null;
  onOpenIn3D?: (partId: string, nodeId: string) => void;
  onStartRepair?: (procedureId: string) => void;
}

type CatalogView = 'BROWSE' | 'PART_DETAIL' | 'PROCEDURE_DETAIL';

interface SystemNode {
  label: string;
  icon: React.ReactNode;
  system: string;
  subsystems: { label: string; subsystem: string; count: number }[];
  color: string;
  glowColor: string;
}

// ── SYSTEM HIERARCHY ────────────────────────────────────────

const SYSTEM_HIERARCHY: SystemNode[] = [
  {
    label: 'Suspensión',
    icon: <Activity size={16} />,
    system: 'Suspensión',
    color: 'from-cyan-400 to-blue-500',
    glowColor: 'rgba(34,211,238,0.4)',
    subsystems: [
      { label: 'Suspensión Delantera', subsystem: 'Suspensión Delantera', count: 0 },
      { label: 'Suspensión Trasera', subsystem: 'Suspensión Trasera', count: 0 }
    ]
  },
  {
    label: 'Motor',
    icon: <Cpu size={16} />,
    system: 'Motor',
    color: 'from-amber-400 to-orange-500',
    glowColor: 'rgba(251,191,36,0.4)',
    subsystems: [
      { label: 'Bloque y Cigüeñal', subsystem: 'Bloque', count: 0 },
      { label: 'Culata y Válvulas', subsystem: 'Culata', count: 0 },
      { label: 'Distribución', subsystem: 'Distribución', count: 0 },
      { label: 'Admisión', subsystem: 'Admisión', count: 0 },
      { label: 'Escape y Emisiones', subsystem: 'Escape', count: 0 }
    ]
  },
  {
    label: 'Frenos',
    icon: <ShieldAlert size={16} />,
    system: 'Frenos',
    color: 'from-red-400 to-rose-500',
    glowColor: 'rgba(248,113,113,0.4)',
    subsystems: [
      { label: 'Frenos Delanteros', subsystem: 'Frenos Delanteros', count: 0 },
      { label: 'Frenos Traseros', subsystem: 'Frenos Traseros', count: 0 },
      { label: 'ABS / ESC', subsystem: 'ABS', count: 0 }
    ]
  },
  {
    label: 'Dirección',
    icon: <Target size={16} />,
    system: 'Dirección',
    color: 'from-violet-400 to-purple-500',
    glowColor: 'rgba(167,139,250,0.4)',
    subsystems: [
      { label: 'Cremallera y Terminales', subsystem: 'Cremallera', count: 0 },
      { label: 'Columna', subsystem: 'Columna', count: 0 }
    ]
  },
  {
    label: 'Electricidad',
    icon: <Zap size={16} />,
    system: 'Eléctrico',
    color: 'from-yellow-400 to-amber-500',
    glowColor: 'rgba(250,204,21,0.4)',
    subsystems: [
      { label: 'Carga y Arranque', subsystem: 'Carga', count: 0 },
      { label: 'Sensores', subsystem: 'Sensores', count: 0 },
      { label: 'Fusibles y Relés', subsystem: 'Fusibles', count: 0 }
    ]
  },
  {
    label: 'Transmisión',
    icon: <Box size={16} />,
    system: 'Transmisión',
    color: 'from-emerald-400 to-green-500',
    glowColor: 'rgba(52,211,153,0.4)',
    subsystems: [
      { label: 'Caja Automática', subsystem: 'ATF', count: 0 },
      { label: 'Embrague / Convertidor', subsystem: 'Embrague', count: 0 }
    ]
  },
  {
    label: 'Climatización',
    icon: <Activity size={16} />,
    system: 'Climatización',
    color: 'from-sky-400 to-indigo-500',
    glowColor: 'rgba(56,189,248,0.4)',
    subsystems: [
      { label: 'Compresor y Condensador', subsystem: 'Compresor', count: 0 },
      { label: 'Evaporador y Ventilación', subsystem: 'Evaporador', count: 0 }
    ]
  }
];

// ── DIFFICULTY AND SAFETY BADGES ────────────────────────────

function DifficultyBadge({ level }: { level: string }) {
  const colors: Record<string, string> = {
    EASY: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
    MEDIUM: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
    HARD: 'bg-red-500/20 text-red-300 border-red-500/30'
  };
  const labels: Record<string, string> = { EASY: 'Fácil', MEDIUM: 'Intermedio', HARD: 'Avanzado' };
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${colors[level] || colors.MEDIUM}`}>
      <Wrench size={10} />
      {labels[level] || level}
    </span>
  );
}

function SafetyBadge({ level }: { level: string }) {
  const colors: Record<string, string> = {
    SAFE: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
    CAUTION: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
    DANGER: 'bg-red-500/20 text-red-300 border-red-500/30'
  };
  const labels: Record<string, string> = { SAFE: 'Seguro', CAUTION: 'Precaución', DANGER: '⚠ Peligro' };
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${colors[level] || colors.CAUTION}`}>
      <ShieldAlert size={10} />
      {labels[level] || level}
    </span>
  );
}

function ConfidenceBadge({ level }: { level: string }) {
  const colors: Record<string, string> = {
    CONFIRMED: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/25',
    PROBABLE: 'bg-amber-500/15 text-amber-300 border-amber-500/25',
    UNCONFIRMED: 'bg-gray-500/15 text-gray-400 border-gray-500/25'
  };
  const labels: Record<string, string> = {
    CONFIRMED: 'Confirmado',
    PROBABLE: 'Probable',
    UNCONFIRMED: 'No confirmado'
  };
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider border ${colors[level] || colors.UNCONFIRMED}`}>
      {level === 'CONFIRMED' ? <Check size={9} /> : <Info size={9} />}
      {labels[level] || level}
    </span>
  );
}

// ── MAIN COMPONENT ──────────────────────────────────────────

export function PartsRepairsCatalog({ vehicle, onOpenIn3D, onStartRepair }: PartsRepairsCatalogProps) {
  const [catalogMode, setCatalogMode] = useState<'PROPRIETARY' | 'GUIDED_PILOT'>('PROPRIETARY');

  if (catalogMode === 'PROPRIETARY') {
    return (
      <ProprietaryPartsExplorer
        onOpenIn3D={onOpenIn3D}
        onOpenGuidedPilot={() => setCatalogMode('GUIDED_PILOT')}
      />
    );
  }

  return (
    <div className="relative">
      <button
        onClick={() => setCatalogMode('PROPRIETARY')}
        className="absolute right-4 top-4 z-40 border border-cyan-400/40 bg-slate-950/95 px-3 py-2 text-[10px] font-black uppercase text-cyan-200 shadow-[0_0_18px_rgba(34,211,238,.16)]"
      >
        Base completa
      </button>
      <GuidedPilotCatalog vehicle={vehicle} onOpenIn3D={onOpenIn3D} onStartRepair={onStartRepair} />
    </div>
  );
}

function GuidedPilotCatalog({ vehicle, onOpenIn3D, onStartRepair }: PartsRepairsCatalogProps) {
  const [catalogParts, setCatalogParts] = useState<DetailedPart[]>(SOURCE_BACKED_PARTS_CATALOG);
  const [catalogProcs, setCatalogProcs] = useState<GuidedRepairProcedure[]>(SOURCE_BACKED_REPAIR_PROCEDURES);
  const [authoringMode, setAuthoringMode] = useState(false);

  const [view, setView] = useState<CatalogView>('BROWSE');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSystem, setSelectedSystem] = useState<string | null>(null);
  const [selectedSubsystem, setSelectedSubsystem] = useState<string | null>(null);
  const [expandedSystems, setExpandedSystems] = useState<Set<string>>(new Set(['Suspensión']));
  const [selectedPart, setSelectedPart] = useState<DetailedPart | null>(null);
  const [selectedProcedure, setSelectedProcedure] = useState<GuidedRepairProcedure | null>(null);
  const [activeStepIdx, setActiveStepIdx] = useState(0);
  const [completedSteps, setCompletedSteps] = useState<Set<string>>(new Set());
  const [gateMessage, setGateMessage] = useState<string | null>(null);
  const [partDetailTab, setPartDetailTab] = useState<'INFO' | 'SPECS' | 'DTC' | 'REPAIR'>('INFO');

  // ── COMPUTED COUNTS ──

  const systemHierarchy = useMemo(() => {
    return SYSTEM_HIERARCHY.map(sys => ({
      ...sys,
      subsystems: sys.subsystems.map(sub => ({
        ...sub,
        count: catalogParts.filter(
          p => p.system === sys.system && p.subsystem === sub.subsystem
        ).length
      }))
    }));
  }, [catalogParts]);

  // ── FILTERED PARTS ──

  const filteredParts = useMemo(() => {
    let parts = [...catalogParts];

    if (selectedSystem) {
      parts = parts.filter(p => p.system === selectedSystem);
    }
    if (selectedSubsystem) {
      parts = parts.filter(p => p.subsystem === selectedSubsystem);
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      parts = parts.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.aliases.some(a => a.toLowerCase().includes(q)) ||
        p.category.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q) ||
        p.specification.oem_number.toLowerCase().includes(q) ||
        p.related_dtcs.some(d => d.toLowerCase().includes(q))
      );
    }
    return parts;
  }, [catalogParts, selectedSystem, selectedSubsystem, searchQuery]);

  // ── RELATED PROCEDURES FOR A PART ──

  const proceduresForPart = useCallback((partId: string): GuidedRepairProcedure[] => {
    return catalogProcs.filter(proc =>
      proc.steps.some(s =>
        catalogParts.find(p => p.id === partId)?.id &&
        (s.target_node_id === catalogParts.find(p => p.id === partId)?.id ||
         s.target_node_id.includes(partId.replace(/^part_/, '')))
      )
    );
  }, [catalogParts, catalogProcs]);

  // ── NAVIGATION ──

  const openPartDetail = useCallback((part: DetailedPart) => {
    setSelectedPart(part);
    setPartDetailTab('INFO');
    setView('PART_DETAIL');
  }, []);

  const openProcedureDetail = useCallback((proc: GuidedRepairProcedure) => {
    const progress = loadRepairProgress(proc.id);
    setSelectedProcedure(proc);
    setActiveStepIdx(0);
    setCompletedSteps(new Set(progress.completedStepIds));
    setGateMessage(progress.state === 'BLOCKED' ? 'El procedimiento quedó bloqueado por evidencia pendiente.' : null);
    setView('PROCEDURE_DETAIL');
  }, []);

  const goBack = useCallback(() => {
    if (view === 'PROCEDURE_DETAIL' && selectedPart) {
      setView('PART_DETAIL');
    } else {
      setView('BROWSE');
      setSelectedPart(null);
      setSelectedProcedure(null);
      setGateMessage(null);
    }
  }, [view, selectedPart]);

  const toggleSystem = useCallback((sys: string) => {
    setExpandedSystems(prev => {
      const next = new Set<string>(prev);
      if (next.has(sys)) next.delete(sys);
      else next.add(sys);
      return next;
    });
  }, []);

  const selectSubsystem = useCallback((sys: string, sub: string) => {
    setSelectedSystem(sys);
    setSelectedSubsystem(sub);
  }, []);

  const clearFilters = useCallback(() => {
    setSelectedSystem(null);
    setSelectedSubsystem(null);
    setSearchQuery('');
  }, []);

  // ── STEP COMPLETION ──

  const toggleStepComplete = useCallback((stepId: string) => {
    if (!selectedProcedure) return;
    const step = selectedProcedure.steps.find(item => item.id === stepId);
    if (!step) return;
    setCompletedSteps(prev => {
      const next = new Set(prev);
      if (next.has(stepId)) {
        next.delete(stepId);
        setGateMessage(null);
      } else {
        const gate = canCompleteRepairStep(step);
        if (!gate.allowed) {
          setGateMessage(gate.reason);
          saveRepairProgress({
            procedureId: selectedProcedure.id,
            packVersion: SOURCE_BACKED_PACK_META.packVersion,
            state: 'BLOCKED',
            completedStepIds: [...prev],
            blockedStepId: stepId,
            updatedAt: new Date().toISOString()
          });
          return prev;
        }
        next.add(stepId);
        setGateMessage(null);
      }
      saveRepairProgress({
        procedureId: selectedProcedure.id,
        packVersion: SOURCE_BACKED_PACK_META.packVersion,
        state: next.size === selectedProcedure.steps.length ? 'COMPLETED' : next.size > 0 ? 'IN_PROGRESS' : 'NOT_STARTED',
        completedStepIds: Array.from(next) as string[],
        blockedStepId: null,
        updatedAt: new Date().toISOString()
      });
      return next;
    });
  }, [selectedProcedure]);

  // ── RENDER ──

  if (authoringMode) {
    return (
      <div className="p-6 bg-slate-950/40 rounded-2xl border border-amber-500/15 shadow-[0_0_30px_rgba(245,158,11,0.05)]">
        <AuthoringConsole
          parts={catalogParts}
          procedures={catalogProcs}
          onSaveParts={setCatalogParts}
          onSaveProcedures={setCatalogProcs}
          onClose={() => setAuthoringMode(false)}
        />
      </div>
    );
  }

  // ═══════════════════════════════════════════════════════════
  // ── RENDER: BROWSE VIEW ──
  // ═══════════════════════════════════════════════════════════

  if (view === 'BROWSE') {
    return (
      <div className="space-y-6">
        {/* HEADER */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl flex items-center justify-center" style={{ background: 'linear-gradient(135deg, #06b6d4, #3b82f6)', boxShadow: '0 0 25px rgba(6,182,212,0.5)' }}>
              <Wrench size={20} className="text-black" />
            </div>
            <div>
              <h2 className="text-lg font-black tracking-wider text-white" style={{ fontFamily: 'JetBrains Mono, monospace' }}>
                PIEZAS Y REPARACIONES
              </h2>
              <p className="text-[10px] font-mono text-cyan-400/70 tracking-widest uppercase">
                Catálogo Técnico · {catalogParts.length} piezas · {catalogProcs.length} procedimientos
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {vehicle && (
              <div className="glass rounded-lg px-3 py-1.5 border border-cyan-500/20">
                <p className="text-[10px] font-mono text-cyan-300 font-bold">{vehicle.brand} {vehicle.model} {vehicle.year}</p>
              <p className="text-[9px] text-gray-500">{vehicle.engine_code_nullable || vehicle.engine || 'Motor'}</p>
              </div>
            )}
            <button
              onClick={() => setAuthoringMode(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/25 hover:bg-amber-500/20 transition-all text-xs font-bold font-mono"
            >
              <Shield size={13} />
              <span>Consola de Autoría</span>
            </button>
          </div>
        </div>

        {/* SEARCH BAR */}
        <div className="relative group">
          <div className="absolute inset-0 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity duration-500" style={{ background: 'linear-gradient(135deg, rgba(6,182,212,0.1), rgba(59,130,246,0.1))' }} />
          <div className="relative flex items-center gap-3 glass rounded-xl px-4 py-3 border border-white/5 group-hover:border-cyan-500/30 transition-colors">
            <Search size={16} className="text-gray-500 group-hover:text-cyan-400 transition-colors" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Buscar por nombre, alias, sistema o conjunto..."
              className="flex-1 bg-transparent text-sm text-white placeholder-gray-600 outline-none font-mono"
            />
            {(searchQuery || selectedSystem) && (
              <button
                onClick={clearFilters}
                className="flex items-center gap-1 px-2 py-1 rounded-lg bg-red-500/10 text-red-400 text-[10px] font-bold hover:bg-red-500/20 transition-colors"
              >
                <X size={10} /> Limpiar
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-12 gap-5">
          {/* ── LEFT: SYSTEM TREE ── */}
          <div className="col-span-4 space-y-2">
            <p className="text-[10px] font-mono font-bold text-gray-500 uppercase tracking-widest mb-2 flex items-center gap-1.5">
              <Layers size={11} /> Sistemas del Vehículo
            </p>

            {systemHierarchy.map(sys => {
              const isExpanded = expandedSystems.has(sys.label);
              const isActive = selectedSystem === sys.system;
              const totalParts = sys.subsystems.reduce((s, sub) => s + sub.count, 0);

              return (
                <div key={sys.system} className="rounded-xl overflow-hidden">
                  <button
                    onClick={() => {
                      toggleSystem(sys.label);
                      if (!isActive) {
                        setSelectedSystem(sys.system);
                        setSelectedSubsystem(null);
                      } else {
                        setSelectedSystem(null);
                        setSelectedSubsystem(null);
                      }
                    }}
                    className={`w-full flex items-center gap-2.5 px-3 py-2.5 text-left transition-all duration-300 ${
                      isActive
                        ? 'border border-cyan-500/30'
                        : 'border border-white/5 hover:border-white/10'
                    }`}
                    style={{
                      background: isActive
                        ? 'linear-gradient(135deg, rgba(6,182,212,0.12), rgba(59,130,246,0.08))'
                        : 'rgba(255,255,255,0.02)',
                      borderRadius: isExpanded ? '12px 12px 0 0' : '12px'
                    }}
                  >
                    <div className={`h-7 w-7 rounded-lg flex items-center justify-center bg-gradient-to-br ${sys.color}`}
                      style={{ boxShadow: isActive ? `0 0 15px ${sys.glowColor}` : 'none' }}>
                      {sys.icon}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className={`text-xs font-bold font-mono ${isActive ? 'text-white' : 'text-gray-300'}`}>{sys.label}</p>
                      <p className="text-[9px] text-gray-500">{totalParts} piezas</p>
                    </div>
                    {isExpanded ? <ChevronDown size={13} className="text-gray-500" /> : <ChevronRight size={13} className="text-gray-500" />}
                  </button>

                  {isExpanded && (
                    <div className="border border-t-0 border-white/5 rounded-b-xl overflow-hidden" style={{ background: 'rgba(0,0,0,0.2)' }}>
                      {sys.subsystems.map(sub => {
                        const isSubActive = selectedSubsystem === sub.subsystem;
                        return (
                          <button
                            key={sub.subsystem}
                            onClick={() => selectSubsystem(sys.system, sub.subsystem)}
                            className={`w-full flex items-center gap-2 px-4 py-2 text-left transition-all ${
                              isSubActive ? 'bg-cyan-500/10 text-cyan-300' : 'text-gray-400 hover:text-gray-200 hover:bg-white/3'
                            }`}
                          >
                            <div className={`h-1.5 w-1.5 rounded-full ${isSubActive ? 'bg-cyan-400' : 'bg-gray-600'}`} />
                            <span className="flex-1 text-[11px] font-mono">{sub.label}</span>
                            {sub.count > 0 && (
                              <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded-full ${
                                isSubActive ? 'bg-cyan-400/20 text-cyan-300' : 'bg-white/5 text-gray-500'
                              }`}>{sub.count}</span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* ── RIGHT: PARTS GRID ── */}
          <div className="col-span-8 space-y-3">
            {/* Active Filters */}
            {(selectedSystem || searchQuery) && (
              <div className="flex items-center gap-2 flex-wrap">
                <Filter size={11} className="text-gray-500" />
                {selectedSystem && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-300 text-[10px] font-bold border border-cyan-500/20">
                    {selectedSystem}
                    {selectedSubsystem && <> / {selectedSubsystem}</>}
                  </span>
                )}
                {searchQuery && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-violet-500/10 text-violet-300 text-[10px] font-bold border border-violet-500/20">
                    "{searchQuery}"
                  </span>
                )}
                <span className="text-[10px] text-gray-500 font-mono">{filteredParts.length} resultados</span>
              </div>
            )}

            {/* Parts List */}
            {filteredParts.length === 0 ? (
              <div className="glass rounded-xl border border-white/5 p-8 text-center">
                <Search size={28} className="mx-auto text-gray-600 mb-3" />
                <p className="text-sm text-gray-400 font-mono">No se encontraron piezas</p>
                <p className="text-[10px] text-gray-600 mt-1">Intente buscar por nombre, alias, sistema o conjunto</p>
              </div>
            ) : (
              <div className="space-y-2">
                {filteredParts.map(part => (
                  <button
                    key={part.id}
                    onClick={() => openPartDetail(part)}
                    className="w-full text-left glass rounded-xl border border-white/5 hover:border-cyan-500/25 p-3.5 transition-all duration-300 group hover:shadow-[0_0_25px_rgba(6,182,212,0.08)]"
                  >
                    <div className="flex items-start gap-3">
                      {/* Icon */}
                      <div className="h-9 w-9 rounded-lg flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-cyan-500/20 to-blue-500/20 border border-cyan-500/15 group-hover:border-cyan-500/30 transition-colors">
                        <Box size={15} className="text-cyan-400" />
                      </div>

                      {/* Info */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <p className="text-xs font-bold text-white font-mono group-hover:text-cyan-300 transition-colors truncate">
                            {part.name}
                          </p>
                          <ConfidenceBadge level={part.confidence_level} />
                        </div>
                        <p className="text-[10px] text-gray-500 font-mono truncate">{part.description}</p>
                        <div className="flex items-center gap-3 mt-1.5">
                          <span className="text-[9px] text-gray-600 font-mono">{part.system} / {part.subsystem}</span>
                          <span className="text-[9px] text-gray-600">·</span>
                          <span className="text-[9px] text-amber-400/70 font-mono font-bold">
                            {part.specification.oem_number || 'OEM pendiente de validación'}
                          </span>
                          {part.related_dtcs.length > 0 && (
                            <>
                              <span className="text-[9px] text-gray-600">·</span>
                              <span className="text-[9px] text-amber-400/60 font-mono">
                                {part.related_dtcs.join(', ')}
                              </span>
                            </>
                          )}
                        </div>
                      </div>

                      {/* Arrow */}
                      <ChevronRight size={14} className="text-gray-600 group-hover:text-cyan-400 transition-colors mt-1" />
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ═══════════════════════════════════════════════════════════
  // ── RENDER: PART DETAIL VIEW ──
  // ═══════════════════════════════════════════════════════════

  if (view === 'PART_DETAIL' && selectedPart) {
    const relatedProcs = catalogProcs.filter(proc =>
      proc.steps.some(step => {
        const nodeBase = selectedPart.id.replace('part_', '');
        return step.target_node_id.includes(nodeBase);
      })
    );

    return (
      <div className="space-y-5">
        {/* BACK BUTTON + BREADCRUMB */}
        <div className="flex items-center gap-2">
          <button
            onClick={goBack}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass border border-white/5 text-gray-400 hover:text-cyan-300 hover:border-cyan-500/20 transition-all text-xs font-mono"
          >
            <ChevronLeft size={13} /> Catálogo
          </button>
          <ChevronRight size={11} className="text-gray-600" />
          <span className="text-[10px] font-mono text-cyan-400">{selectedPart.system}</span>
          <ChevronRight size={11} className="text-gray-600" />
          <span className="text-[10px] font-mono text-gray-400">{selectedPart.subsystem}</span>
        </div>

        {/* PART HEADER */}
        <div className="glass rounded-2xl border border-cyan-500/15 p-5" style={{ background: 'linear-gradient(135deg, rgba(6,182,212,0.05), rgba(59,130,246,0.03))' }}>
          <div className="flex items-start justify-between mb-4">
            <div className="flex items-center gap-3">
              <div className="h-12 w-12 rounded-xl flex items-center justify-center bg-gradient-to-br from-cyan-500 to-blue-500" style={{ boxShadow: '0 0 30px rgba(6,182,212,0.4)' }}>
                <Box size={22} className="text-black" />
              </div>
              <div>
                <h2 className="text-base font-black text-white font-mono tracking-wide">{selectedPart.name}</h2>
                <p className="text-[10px] text-gray-500 font-mono">{selectedPart.aliases.join(' · ')}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <ConfidenceBadge level={selectedPart.confidence_level} />
              {onOpenIn3D && (
                <button
                  onClick={() => onOpenIn3D(selectedPart.id, selectedPart.id.replace('part_', ''))}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-500 text-black text-[10px] font-bold font-mono hover:shadow-[0_0_20px_rgba(6,182,212,0.5)] transition-all"
                >
                  <Eye size={11} /> VER EN 3D
                </button>
              )}
            </div>
          </div>

          <p className="text-xs text-gray-300 leading-relaxed">{selectedPart.description}</p>

          <div className="mt-4 border-l-2 border-amber-400/70 pl-3">
            <p className="text-[10px] font-bold text-amber-300 font-mono">COMPATIBILIDAD REQUIERE VERIFICACIÓN</p>
            <p className="text-[10px] text-gray-400 mt-1">{selectedPart.compatibility_message}</p>
            <p className="text-[9px] text-gray-500 mt-1">Modelo visual: esquema genérico, no dimensional ni OEM.</p>
          </div>

          <div className="grid grid-cols-4 gap-3 mt-4">
            <div className="rounded-lg p-2 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
              <p className="text-[9px] text-gray-500 font-mono mb-0.5">SISTEMA</p>
              <p className="text-[11px] text-white font-bold font-mono">{selectedPart.system}</p>
            </div>
            <div className="rounded-lg p-2 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
              <p className="text-[9px] text-gray-500 font-mono mb-0.5">POSICIÓN</p>
              <p className="text-[11px] text-white font-bold font-mono">{selectedPart.position}</p>
            </div>
            <div className="rounded-lg p-2 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
              <p className="text-[9px] text-gray-500 font-mono mb-0.5">CATEGORÍA</p>
              <p className="text-[11px] text-white font-bold font-mono">{selectedPart.category}</p>
            </div>
            <div className="rounded-lg p-2 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
              <p className="text-[9px] text-gray-500 font-mono mb-0.5">ENSAMBLAJE</p>
              <p className="text-[11px] text-white font-bold font-mono truncate">{selectedPart.assembly}</p>
            </div>
          </div>
        </div>

        {/* DETAIL TABS */}
        <div className="flex gap-1 p-1 rounded-xl" style={{ background: 'rgba(0,0,0,0.3)' }}>
          {([
            { id: 'INFO' as const, label: 'Información', icon: <Info size={12} /> },
            { id: 'SPECS' as const, label: 'Especificaciones', icon: <Cpu size={12} /> },
            { id: 'DTC' as const, label: 'DTCs y Síntomas', icon: <AlertTriangle size={12} /> },
            { id: 'REPAIR' as const, label: 'Reparación', icon: <Wrench size={12} /> }
          ]).map(tab => (
            <button
              key={tab.id}
              onClick={() => setPartDetailTab(tab.id)}
              className={`flex-1 flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-[10px] font-bold font-mono transition-all ${
                partDetailTab === tab.id
                  ? 'bg-cyan-500/15 text-cyan-300 border border-cyan-500/25'
                  : 'text-gray-500 hover:text-gray-300 border border-transparent'
              }`}
            >
              {tab.icon} {tab.label}
            </button>
          ))}
        </div>

        {/* TAB CONTENT */}
        {partDetailTab === 'INFO' && (
          <div className="glass rounded-xl border border-white/5 p-5 space-y-4">
            <div>
              <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Descripción Técnica</p>
              <p className="text-xs text-gray-300 leading-relaxed">{selectedPart.description}</p>
            </div>
            <div>
              <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Nombres Alternativos</p>
              <div className="flex flex-wrap gap-1.5">
                {selectedPart.aliases.map((alias, i) => (
                  <span key={i} className="px-2 py-0.5 rounded-full text-[10px] font-mono text-cyan-300/80 bg-cyan-500/8 border border-cyan-500/15">{alias}</span>
                ))}
              </div>
            </div>
            <div>
              <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Jerarquía</p>
              <div className="flex items-center gap-1.5 text-[10px] font-mono text-gray-400">
                <span className="text-cyan-400">{selectedPart.system}</span>
                <ArrowRight size={9} />
                <span>{selectedPart.subsystem}</span>
                <ArrowRight size={9} />
                <span>{selectedPart.assembly}</span>
                {selectedPart.subassembly && (
                  <>
                    <ArrowRight size={9} />
                    <span>{selectedPart.subassembly}</span>
                  </>
                )}
              </div>
            </div>
            <div>
              <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Trazabilidad</p>
              {(selectedPart.source_refs ?? []).map(source => (
                <div key={`${source.source_document_sha256}:${source.source_block_id}`} className="border-l-2 border-cyan-500/40 pl-3 py-1">
                  <p className="text-[10px] text-cyan-300 font-mono">{source.source_file_name} · {source.source_block_id}</p>
                  <p className="text-[9px] text-gray-500 font-mono">SHA-256 {source.source_document_sha256.slice(0, 16)}... · {source.review_status}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {partDetailTab === 'SPECS' && (
          <div className="glass rounded-xl border border-white/5 p-5 space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg p-3 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
                <p className="text-[9px] font-mono text-gray-500 mb-1">NÚMERO OEM</p>
                <p className="text-sm text-amber-300 font-bold font-mono">{selectedPart.specification.oem_number || 'No confirmado para esta variante'}</p>
              </div>
              <div className="rounded-lg p-3 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
                <p className="text-[9px] font-mono text-gray-500 mb-1">MATERIAL</p>
                <p className="text-xs text-gray-400 font-mono">{selectedPart.specification.material || 'Pendiente de validación'}</p>
              </div>
              <div className="rounded-lg p-3 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
                <p className="text-[9px] font-mono text-gray-500 mb-1">DIMENSIONES</p>
                <p className="text-xs text-gray-400 font-mono">{selectedPart.specification.dimensions || 'Pendiente de validación'}</p>
              </div>
              {selectedPart.specification.torque_nm && (
                <div className="rounded-lg p-3 border border-amber-500/20" style={{ background: 'rgba(251,191,36,0.05)' }}>
                  <p className="text-[9px] font-mono text-amber-400/70 mb-1">TORQUE</p>
                  <p className="text-sm text-amber-300 font-bold font-mono">{selectedPart.specification.torque_nm}</p>
                </div>
              )}
            </div>
            {selectedPart.specification.equivalent_numbers.length > 0 && (
              <div>
                <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Números Equivalentes</p>
                <div className="flex flex-wrap gap-1.5">
                  {selectedPart.specification.equivalent_numbers.map((num, i) => (
                    <span key={i} className="px-2 py-0.5 rounded-full text-[10px] font-mono text-violet-300 bg-violet-500/10 border border-violet-500/20">{num}</span>
                  ))}
                </div>
              </div>
            )}
            {selectedPart.specification.weight_kg && (
              <div className="rounded-lg p-3 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
                <p className="text-[9px] font-mono text-gray-500 mb-1">PESO</p>
                <p className="text-xs text-white font-mono">{selectedPart.specification.weight_kg} kg</p>
              </div>
            )}
          </div>
        )}

        {partDetailTab === 'DTC' && (
          <div className="glass rounded-xl border border-white/5 p-5 space-y-4">
            {selectedPart.related_dtcs.length > 0 ? (
              <div>
                <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Códigos DTC Relacionados</p>
                <div className="flex flex-wrap gap-2">
                  {selectedPart.related_dtcs.map((dtc, i) => (
                    <div key={i} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/20">
                      <Zap size={11} className="text-amber-400" />
                      <span className="text-xs font-bold text-amber-300 font-mono">{dtc}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <p className="text-xs text-gray-500 font-mono">No hay DTCs directamente vinculados a esta pieza.</p>
            )}
            <div>
              <p className="text-[10px] font-mono text-gray-500 uppercase tracking-wider mb-2">Síntomas de Fallo</p>
              <div className="space-y-1.5">
                {selectedPart.symptoms.map((sym, i) => (
                  <div key={i} className="flex items-start gap-2 px-3 py-2 rounded-lg border border-white/5" style={{ background: 'rgba(0,0,0,0.2)' }}>
                    <AlertTriangle size={11} className="text-amber-400 mt-0.5 flex-shrink-0" />
                    <p className="text-[11px] text-gray-300 font-mono">{sym}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {partDetailTab === 'REPAIR' && (
          <div className="space-y-3">
            {relatedProcs.length > 0 ? relatedProcs.map(proc => (
              <button
                key={proc.id}
                onClick={() => openProcedureDetail(proc)}
                className="w-full text-left glass rounded-xl border border-white/5 hover:border-emerald-500/25 p-4 transition-all group hover:shadow-[0_0_25px_rgba(52,211,153,0.08)]"
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="h-9 w-9 rounded-lg flex items-center justify-center bg-gradient-to-br from-emerald-500/20 to-green-500/20 border border-emerald-500/20 group-hover:border-emerald-500/40">
                      <Play size={15} className="text-emerald-400" />
                    </div>
                    <div>
                      <p className="text-xs font-bold text-white font-mono group-hover:text-emerald-300 transition-colors">{proc.title}</p>
                      <div className="flex items-center gap-2 mt-1">
                        <DifficultyBadge level={proc.difficulty} />
                        <SafetyBadge level={proc.safety_level} />
                        <span className="inline-flex items-center gap-1 text-[9px] text-gray-500 font-mono">
                          <Clock size={9} /> ~{proc.estimated_duration_min} min
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 text-[10px] text-gray-500 font-mono">
                    {proc.steps.length} pasos <ChevronRight size={12} className="group-hover:text-emerald-400" />
                  </div>
                </div>
              </button>
            )) : (
              <div className="glass rounded-xl border border-white/5 p-6 text-center">
                <Wrench size={24} className="mx-auto text-gray-600 mb-2" />
                <p className="text-xs text-gray-400 font-mono">No hay procedimientos de reparación disponibles para esta pieza.</p>
                <p className="text-[10px] text-gray-600 mt-1">Los procedimientos se añaden continuamente.</p>
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // ═══════════════════════════════════════════════════════════
  // ── RENDER: PROCEDURE DETAIL (Guided Repair Player) ──
  // ═══════════════════════════════════════════════════════════

  if (view === 'PROCEDURE_DETAIL' && selectedProcedure) {
    const activeStep = selectedProcedure.steps[activeStepIdx];
    const progress = (completedSteps.size / selectedProcedure.steps.length) * 100;

    return (
      <div className="space-y-5">
        {/* BACK + TITLE */}
        <div className="flex items-center gap-2">
          <button onClick={goBack} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass border border-white/5 text-gray-400 hover:text-cyan-300 hover:border-cyan-500/20 transition-all text-xs font-mono">
            <ChevronLeft size={13} /> Atrás
          </button>
        </div>

        {/* PROCEDURE HEADER */}
        <div className="glass rounded-2xl border border-emerald-500/15 p-5" style={{ background: 'linear-gradient(135deg, rgba(52,211,153,0.05), rgba(6,182,212,0.03))' }}>
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-3">
              <div className="h-11 w-11 rounded-xl flex items-center justify-center bg-gradient-to-br from-emerald-500 to-green-500" style={{ boxShadow: '0 0 30px rgba(52,211,153,0.4)' }}>
                <Wrench size={20} className="text-black" />
              </div>
              <div>
                <h2 className="text-sm font-black text-white font-mono tracking-wide">{selectedProcedure.title}</h2>
                <p className="text-[10px] text-gray-500 font-mono">{selectedProcedure.vehicle_applicability}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <DifficultyBadge level={selectedProcedure.difficulty} />
              <SafetyBadge level={selectedProcedure.safety_level} />
            </div>
          </div>

          {/* Progress bar */}
          <div className="flex items-center gap-3 mb-2">
            <div className="flex-1 h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(0,0,0,0.4)' }}>
              <div className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-cyan-400 transition-all duration-500" style={{ width: `${progress}%`, boxShadow: '0 0 10px rgba(52,211,153,0.5)' }} />
            </div>
            <span className="text-[10px] font-mono font-bold text-emerald-300">{Math.round(progress)}%</span>
          </div>

          <div className="flex items-center gap-4 text-[10px] text-gray-500 font-mono">
            <span className="flex items-center gap-1"><Clock size={10} /> Duración pendiente de validación</span>
            <span>{completedSteps.size}/{selectedProcedure.steps.length} pasos completados</span>
          </div>
        </div>

        {/* PREREQUISITES */}
        {selectedProcedure.prerequisites.length > 0 && (
          <div className="glass rounded-xl border border-amber-500/15 p-4" style={{ background: 'rgba(251,191,36,0.03)' }}>
            <p className="text-[10px] font-mono text-amber-400 font-bold uppercase tracking-wider mb-2">⚠ Prerrequisitos</p>
            <div className="space-y-1">
              {selectedProcedure.prerequisites.map((pre, i) => (
                <p key={i} className="text-[11px] text-gray-300 font-mono flex items-start gap-2">
                  <span className="text-amber-400 font-bold">{i + 1}.</span> {pre}
                </p>
              ))}
            </div>
          </div>
        )}

        <div className="grid grid-cols-12 gap-4">
          {/* STEPS LIST (LEFT) */}
          <div className="col-span-4 space-y-1.5">
            {selectedProcedure.steps.map((step, idx) => {
              const isActive = idx === activeStepIdx;
              const isCompleted = completedSteps.has(step.id);
              return (
                <button
                  key={step.id}
                  onClick={() => setActiveStepIdx(idx)}
                  className={`w-full text-left px-3 py-2.5 rounded-xl transition-all duration-200 flex items-center gap-2.5 ${
                    isActive
                      ? 'border border-cyan-500/30 shadow-[0_0_15px_rgba(6,182,212,0.1)]'
                      : isCompleted
                        ? 'border border-emerald-500/15'
                        : 'border border-white/5 hover:border-white/10'
                  }`}
                  style={{
                    background: isActive
                      ? 'linear-gradient(135deg, rgba(6,182,212,0.12), rgba(59,130,246,0.06))'
                      : isCompleted
                        ? 'rgba(52,211,153,0.05)'
                        : 'rgba(0,0,0,0.2)'
                  }}
                >
                  <div className={`h-6 w-6 rounded-lg flex items-center justify-center text-[10px] font-bold font-mono flex-shrink-0 ${
                    isCompleted
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                      : isActive
                        ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                        : 'bg-white/5 text-gray-500 border border-white/10'
                  }`}>
                    {isCompleted ? <Check size={11} /> : step.order}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className={`text-[10px] font-bold font-mono truncate ${
                      isActive ? 'text-cyan-300' : isCompleted ? 'text-emerald-300/80' : 'text-gray-400'
                    }`}>{step.title}</p>
                    <p className={`text-[9px] font-mono truncate ${
                      isActive ? 'text-gray-400' : 'text-gray-600'
                    }`}>{step.type}</p>
                  </div>
                </button>
              );
            })}
          </div>

          {/* STEP DETAIL (RIGHT) */}
          <div className="col-span-8">
            {activeStep && (
              <div className="glass rounded-xl border border-cyan-500/15 p-5 space-y-4" style={{ background: 'linear-gradient(135deg, rgba(6,182,212,0.04), rgba(0,0,0,0.2))' }}>
                {/* Step Header */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="h-8 w-8 rounded-lg flex items-center justify-center bg-gradient-to-br from-cyan-500 to-blue-500 text-xs font-bold text-black font-mono">
                      {activeStep.order}
                    </div>
                    <div>
                      <p className="text-xs font-bold text-white font-mono">{activeStep.title}</p>
                      <p className="text-[9px] font-mono text-cyan-400/60 uppercase">{activeStep.type}</p>
                    </div>
                  </div>
                  {onOpenIn3D && (
                    <button
                      onClick={() => onOpenIn3D(activeStep.target_node_id, activeStep.target_node_id)}
                      className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-cyan-500/10 text-cyan-300 text-[10px] font-bold font-mono border border-cyan-500/20 hover:bg-cyan-500/20 transition-colors"
                    >
                      <Eye size={10} /> Ver en 3D
                    </button>
                  )}
                </div>

                {/* Description */}
                <p className="text-xs text-gray-300 leading-relaxed">{activeStep.description}</p>

                {/* Tools */}
                {activeStep.required_tools && activeStep.required_tools.length > 0 && (
                  <div className="rounded-lg p-3 border border-white/5" style={{ background: 'rgba(0,0,0,0.3)' }}>
                    <p className="text-[9px] font-mono text-gray-500 uppercase tracking-wider mb-1.5">🔧 Herramientas</p>
                    <div className="flex flex-wrap gap-1.5">
                      {activeStep.required_tools.map((tool, i) => (
                        <span key={i} className="px-2 py-0.5 rounded-full text-[10px] font-mono text-blue-300 bg-blue-500/10 border border-blue-500/15">{tool}</span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Torque Spec */}
                {activeStep.torque_spec && (
                  <div className="rounded-lg p-3 border border-amber-500/20" style={{ background: 'rgba(251,191,36,0.05)' }}>
                    <p className="text-[9px] font-mono text-amber-400/70 uppercase tracking-wider mb-1">⟳ Especificación de Torque</p>
                    <p className="text-sm text-amber-300 font-bold font-mono">{activeStep.torque_spec}</p>
                    {activeStep.completion_gate === 'VERIFIED_TORQUE_REQUIRED' && (
                      <p className="text-[9px] text-amber-200/70 mt-1">Paso bloqueado hasta adjuntar una fuente técnica válida para esta variante.</p>
                    )}
                  </div>
                )}

                {/* Warning */}
                {activeStep.warning_notes && (
                  <div className="rounded-lg p-3 border border-red-500/20" style={{ background: 'rgba(239,68,68,0.05)' }}>
                    <p className="text-[9px] font-mono text-red-400/70 uppercase tracking-wider mb-1">⚠ Advertencia</p>
                    <p className="text-[11px] text-red-300/90 font-mono">{activeStep.warning_notes}</p>
                  </div>
                )}

                {/* Expected Measurement */}
                {activeStep.expected_measurement && (
                  <div className="rounded-lg p-3 border border-emerald-500/15" style={{ background: 'rgba(52,211,153,0.04)' }}>
                    <p className="text-[9px] font-mono text-emerald-400/70 uppercase tracking-wider mb-1">📏 Medición Esperada</p>
                    <p className="text-[11px] text-emerald-300 font-mono">{activeStep.expected_measurement}</p>
                  </div>
                )}

                {/* STEP ACTIONS */}
                {gateMessage && (
                  <div role="alert" className="border-l-2 border-red-400 pl-3 py-1">
                    <p className="text-[10px] text-red-300 font-mono">{gateMessage}</p>
                  </div>
                )}
                <div className="flex items-center justify-between pt-2 border-t border-white/5">
                  <button
                    onClick={() => setActiveStepIdx(Math.max(0, activeStepIdx - 1))}
                    disabled={activeStepIdx === 0}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-[10px] font-bold font-mono text-gray-400 border border-white/5 hover:border-white/15 disabled:opacity-30 transition-all"
                  >
                    <ChevronLeft size={11} /> Anterior
                  </button>

                  <button
                    onClick={() => toggleStepComplete(activeStep.id)}
                    className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-[10px] font-bold font-mono transition-all ${
                      completedSteps.has(activeStep.id)
                        ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                        : 'bg-gradient-to-r from-emerald-500 to-green-500 text-black hover:shadow-[0_0_20px_rgba(52,211,153,0.4)]'
                    }`}
                  >
                    <Check size={12} /> {completedSteps.has(activeStep.id) ? 'Completado ✓' : 'Marcar Completado'}
                  </button>

                  <button
                    onClick={() => setActiveStepIdx(Math.min(selectedProcedure.steps.length - 1, activeStepIdx + 1))}
                    disabled={activeStepIdx === selectedProcedure.steps.length - 1}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-[10px] font-bold font-mono text-cyan-300 border border-cyan-500/20 hover:bg-cyan-500/10 disabled:opacity-30 transition-all"
                  >
                    Siguiente <ChevronRight size={11} />
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* FINAL VERIFICATION (visible when all steps complete) */}
        {progress === 100 && selectedProcedure.final_verification.length > 0 && (
          <div className="glass rounded-xl border border-emerald-500/20 p-5" style={{ background: 'linear-gradient(135deg, rgba(52,211,153,0.08), rgba(6,182,212,0.04))' }}>
            <p className="text-xs font-bold font-mono text-emerald-300 flex items-center gap-2 mb-3">
              <Check size={14} /> Verificación Final
            </p>
            <div className="space-y-2">
              {selectedProcedure.final_verification.map((v, i) => (
                <div key={i} className="flex items-start gap-2 px-3 py-2 rounded-lg border border-emerald-500/10" style={{ background: 'rgba(0,0,0,0.2)' }}>
                  <Star size={10} className="text-emerald-400 mt-0.5 flex-shrink-0" />
                  <p className="text-[11px] text-gray-300 font-mono">{v}</p>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    );
  }

  // Fallback
  return null;
}
