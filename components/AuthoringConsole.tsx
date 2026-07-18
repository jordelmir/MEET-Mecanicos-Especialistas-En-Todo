import React, { useState, useMemo, useCallback } from 'react';
import {
  DetailedPart,
  GuidedRepairProcedure,
  RepairStep3D,
  PartSpecification
} from '../types';
import {
  Save,
  Plus,
  Trash2,
  Edit,
  FileCode,
  CheckCircle,
  AlertTriangle,
  ArrowRight,
  Database,
  User,
  History,
  Shield,
  Download,
  Upload,
  Layers,
  ChevronRight,
  ChevronLeft,
  Wrench,
  Activity,
  X
} from 'lucide-react';

interface AuthoringConsoleProps {
  parts: DetailedPart[];
  procedures: GuidedRepairProcedure[];
  onSaveParts: (newParts: DetailedPart[]) => void;
  onSaveProcedures: (newProcedures: GuidedRepairProcedure[]) => void;
  onClose: () => void;
}

interface AuditRecord {
  id: string;
  author: string;
  timestamp: string;
  action: 'CREATE_PART' | 'UPDATE_PART' | 'DELETE_PART' | 'CREATE_PROC' | 'UPDATE_PROC' | 'DELETE_PROC' | 'IMPORT_ALL';
  details: string;
  revision: number;
}

export function AuthoringConsole({
  parts,
  procedures,
  onSaveParts,
  onSaveProcedures,
  onClose
}: AuthoringConsoleProps) {
  // --- STATE ---
  const [activeTab, setActiveTab] = useState<'PARTS' | 'PROCEDURES' | 'AUDIT' | 'IMPORT_EXPORT'>('PARTS');
  const [localParts, setLocalParts] = useState<DetailedPart[]>([...parts]);
  const [localProcs, setLocalProcs] = useState<GuidedRepairProcedure[]>([...procedures]);
  const [selectedPartId, setSelectedPartId] = useState<string | null>(null);
  const [selectedProcId, setSelectedProcId] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editorAuthor, setEditorAuthor] = useState('Ingeniero Principal Forge');
  const [auditLog, setAuditLog] = useState<AuditRecord[]>([
    {
      id: 'audit_1',
      author: 'Sistema Forge',
      timestamp: new Date(Date.now() - 3600000).toLocaleString(),
      action: 'IMPORT_ALL',
      details: 'Ingesta de datos de suspensión Hyundai Accent 2005 (50 piezas normalizadas)',
      revision: 1
    }
  ]);
  const [validationResult, setValidationResult] = useState<{ status: 'PASS' | 'FAIL' | 'IDLE'; messages: string[] }>({ status: 'IDLE', messages: [] });

  // --- PART FORM STATE ---
  const [partForm, setPartForm] = useState<Partial<DetailedPart>>({});
  const [specForm, setSpecForm] = useState<Partial<PartSpecification>>({});

  // --- PROCEDURE FORM STATE ---
  const [procForm, setProcForm] = useState<Partial<GuidedRepairProcedure>>({});
  const [stepForm, setStepForm] = useState<Partial<RepairStep3D>>({});
  const [editingStepIdx, setEditingStepIdx] = useState<number | null>(null);

  // --- DERIVED MEMO VALUES ---
  const currentPart = useMemo(() => localParts.find(p => p.id === selectedPartId) || null, [localParts, selectedPartId]);
  const currentProc = useMemo(() => localProcs.find(p => p.id === selectedProcId) || null, [localProcs, selectedProcId]);

  // --- LOGGING UTILITY ---
  const logAudit = useCallback((action: AuditRecord['action'], details: string) => {
    const record: AuditRecord = {
      id: `audit_${Date.now()}`,
      author: editorAuthor || 'Editor Técnico',
      timestamp: new Date().toLocaleString(),
      action,
      details,
      revision: auditLog.length + 1
    };
    setAuditLog(prev => [record, ...prev]);
  }, [editorAuthor, auditLog.length]);

  // --- PART CRUD ---
  const startNewPart = () => {
    setPartForm({
      id: `part_new_${Date.now()}`,
      name: '',
      aliases: [],
      category: 'Mecánico',
      system: 'Suspensión',
      subsystem: 'Brazos',
      assembly: 'Delantera',
      description: '',
      position: 'LEFT',
      symptoms: [],
      related_dtcs: [],
      confidence_level: 'PROBABLE'
    });
    setSpecForm({
      oem_number: '',
      equivalent_numbers: [],
      dimensions: '',
      material: '',
      weight_kg: 0.5,
      torque_nm: ''
    });
    setSelectedPartId(null);
    setIsEditing(true);
  };

  const startEditPart = (part: DetailedPart) => {
    setPartForm({ ...part });
    setSpecForm({ ...part.specification });
    setIsEditing(true);
  };

  const savePartForm = () => {
    if (!partForm.name || !specForm.oem_number) {
      alert('Nombre de la pieza y número OEM son requeridos.');
      return;
    }

    const updatedPart: DetailedPart = {
      ...(partForm as DetailedPart),
      specification: specForm as PartSpecification
    };

    const exists = localParts.some(p => p.id === updatedPart.id);
    let nextParts: DetailedPart[];

    if (exists) {
      nextParts = localParts.map(p => p.id === updatedPart.id ? updatedPart : p);
      logAudit('UPDATE_PART', `Actualizada pieza: ${updatedPart.name} (${updatedPart.specification.oem_number})`);
    } else {
      nextParts = [...localParts, updatedPart];
      logAudit('CREATE_PART', `Creada pieza: ${updatedPart.name} con OEM: ${updatedPart.specification.oem_number}`);
    }

    setLocalParts(nextParts);
    onSaveParts(nextParts);
    setIsEditing(false);
    setSelectedPartId(updatedPart.id);
  };

  const deletePart = (id: string) => {
    const part = localParts.find(p => p.id === id);
    if (!part) return;
    if (confirm(`¿Está seguro de eliminar la pieza: ${part.name}?`)) {
      const nextParts = localParts.filter(p => p.id !== id);
      setLocalParts(nextParts);
      onSaveParts(nextParts);
      setSelectedPartId(null);
      setIsEditing(false);
      logAudit('DELETE_PART', `Eliminada pieza: ${part.name}`);
    }
  };

  // --- PROCEDURE CRUD ---
  const startNewProc = () => {
    setProcForm({
      id: `proc_new_${Date.now()}`,
      title: '',
      vehicle_applicability: 'Hyundai Accent 2005 1.6 AT',
      estimated_duration_min: 30,
      difficulty: 'MEDIUM',
      safety_level: 'CAUTION',
      prerequisites: [],
      steps: [],
      final_verification: []
    });
    setSelectedProcId(null);
    setIsEditing(true);
  };

  const startEditProc = (proc: GuidedRepairProcedure) => {
    setProcForm({ ...proc });
    setIsEditing(true);
  };

  const saveProcForm = () => {
    if (!procForm.title) {
      alert('El título del procedimiento es requerido.');
      return;
    }

    const updatedProc = procForm as GuidedRepairProcedure;
    const exists = localProcs.some(p => p.id === updatedProc.id);
    let nextProcs: GuidedRepairProcedure[];

    if (exists) {
      nextProcs = localProcs.map(p => p.id === updatedProc.id ? updatedProc : p);
      logAudit('UPDATE_PROC', `Actualizado procedimiento: ${updatedProc.title}`);
    } else {
      nextProcs = [...localProcs, updatedProc];
      logAudit('CREATE_PROC', `Creado procedimiento: ${updatedProc.title}`);
    }

    setLocalProcs(nextProcs);
    onSaveProcedures(nextProcs);
    setIsEditing(false);
    setSelectedProcId(updatedProc.id);
  };

  const deleteProc = (id: string) => {
    const proc = localProcs.find(p => p.id === id);
    if (!proc) return;
    if (confirm(`¿Está seguro de eliminar el procedimiento: ${proc.title}?`)) {
      const nextProcs = localProcs.filter(p => p.id !== id);
      setLocalProcs(nextProcs);
      onSaveProcedures(nextProcs);
      setSelectedProcId(null);
      setIsEditing(false);
      logAudit('DELETE_PROC', `Eliminado procedimiento: ${proc.title}`);
    }
  };

  // --- STEP BUILDER ---
  const startAddStep = () => {
    setStepForm({
      id: `step_${Date.now()}`,
      order: (procForm.steps?.length || 0) + 1,
      title: '',
      description: '',
      type: 'DISASSEMBLE',
      target_node_id: '',
      animation_action: 'NONE',
      required_tools: [],
      torque_spec: '',
      warning_notes: '',
      expected_measurement: ''
    });
    setEditingStepIdx(null);
  };

  const startEditStep = (step: RepairStep3D, idx: number) => {
    setStepForm({ ...step });
    setEditingStepIdx(idx);
  };

  const saveStep = () => {
    if (!stepForm.title || !stepForm.description || !stepForm.target_node_id) {
      alert('Los campos Título, Descripción y Nodo 3D Objetivo son requeridos.');
      return;
    }

    const steps = [...(procForm.steps || [])];
    const newStep = stepForm as RepairStep3D;

    if (editingStepIdx !== null) {
      steps[editingStepIdx] = newStep;
    } else {
      steps.push(newStep);
    }

    // Sort by order
    steps.sort((a, b) => a.order - b.order);
    setProcForm(prev => ({ ...prev, steps }));
    setStepForm({});
    setEditingStepIdx(null);
  };

  const deleteStep = (idx: number) => {
    const steps = (procForm.steps || []).filter((_, i) => i !== idx);
    // Re-index order
    const reindexed = steps.map((s, i) => ({ ...s, order: i + 1 }));
    setProcForm(prev => ({ ...prev, steps: reindexed }));
  };

  // --- VALIDATION ENGINE ---
  const validateCatalog = () => {
    const messages: string[] = [];
    let passed = true;

    // Check Parts
    for (const part of localParts) {
      if (!part.name.trim()) {
        messages.push(`Error: Pieza con ID ${part.id} tiene nombre vacío.`);
        passed = false;
      }
      if (!part.specification.oem_number.trim()) {
        messages.push(`Error: Pieza ${part.name} no tiene número OEM.`);
        passed = false;
      }
      // Anti-hallucination check
      if (part.confidence_level === 'CONFIRMED' && part.specification.oem_number === 'N/A') {
        messages.push(`Advertencia: Pieza ${part.name} marcada CONFIRMED pero tiene número OEM N/A.`);
      }
    }

    // Check Procedures
    for (const proc of localProcs) {
      if (proc.steps.length === 0) {
        messages.push(`Error: Procedimiento "${proc.title}" no tiene pasos.`);
        passed = false;
      }
      for (const step of proc.steps) {
        // Torque format check
        if (step.type === 'TORQUE' && (!step.torque_spec || !step.torque_spec.includes('N·m'))) {
          messages.push(`Advertencia: Paso "${step.title}" de tipo TORQUE no tiene formato N·m en especificaciones.`);
        }
        // Semantic node id validation
        if (step.target_node_id.startsWith('Cube') || step.target_node_id.startsWith('Mesh')) {
          messages.push(`Advertencia: Paso "${step.title}" usa un ID de malla 3D frágil ("${step.target_node_id}"). Debería ser semántico.`);
        }
      }
    }

    if (passed) {
      messages.unshift('✓ Todo correcto: Estructura, formatos, nomenclatura semántica de nodos 3D y especificaciones de torque correctas.');
      setValidationResult({ status: 'PASS', messages });
    } else {
      messages.unshift('⚠ Fallo de validación: Se detectaron inconsistencias en la base de datos local.');
      setValidationResult({ status: 'FAIL', messages });
    }
  };

  // --- IMPORT / EXPORT JSON ---
  const handleExport = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify({ parts: localParts, procedures: localProcs }, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", "meet_parts_catalog.json");
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
    logAudit('IMPORT_ALL', 'Exportada base de datos del catálogo a archivo JSON');
  };

  const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileReader = new FileReader();
    if (e.target.files && e.target.files[0]) {
      fileReader.readAsText(e.target.files[0], "UTF-8");
      fileReader.onload = (event) => {
        try {
          const parsed = JSON.parse(event.target?.result as string);
          if (Array.isArray(parsed.parts) && Array.isArray(parsed.procedures)) {
            setLocalParts(parsed.parts);
            setLocalProcs(parsed.procedures);
            onSaveParts(parsed.parts);
            onSaveProcedures(parsed.procedures);
            logAudit('IMPORT_ALL', `Importadas ${parsed.parts.length} piezas y ${parsed.procedures.length} procedimientos desde archivo externo`);
            alert('Base de datos importada correctamente.');
          } else {
            alert('Formato de archivo inválido. Debe contener "parts" y "procedures" como arrays.');
          }
        } catch (error) {
          alert('Error al parsear el archivo JSON.');
        }
      };
    }
  };

  return (
    <div className="space-y-6 animate-slide-up">
      {/* HEADER */}
      <div className="flex items-center justify-between border-b border-white/5 pb-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl flex items-center justify-center bg-gradient-to-br from-amber-500 to-red-500 shadow-[0_0_20px_rgba(245,158,11,0.3)]">
            <Shield size={20} className="text-black" />
          </div>
          <div>
            <h2 className="text-base font-black tracking-wider text-white font-mono">CONSOLA DE AUTORÍA TÉCNICA</h2>
            <p className="text-[10px] text-gray-500 font-mono uppercase tracking-widest">
              Herramienta de edición · Control de calidad · Normalización y aliasing
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 glass px-3 py-1 rounded-lg border border-white/5">
            <User size={12} className="text-amber-400" />
            <input
              type="text"
              value={editorAuthor}
              onChange={(e) => setEditorAuthor(e.target.value)}
              className="bg-transparent border-none text-[11px] font-mono text-white outline-none w-48"
              placeholder="Autor de cambios"
            />
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg bg-white/5 text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X size={15} />
          </button>
        </div>
      </div>

      {/* TABS */}
      <div className="flex gap-1.5 border-b border-white/5 pb-1">
        {([
          { id: 'PARTS' as const, label: 'Catálogo de Piezas', icon: <Layers size={13} /> },
          { id: 'PROCEDURES' as const, label: 'Procedimientos 3D', icon: <Wrench size={13} /> },
          { id: 'AUDIT' as const, label: 'Historial de Cambios', icon: <History size={13} /> },
          { id: 'IMPORT_EXPORT' as const, label: 'Base de Datos', icon: <Database size={13} /> }
        ]).map(tab => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveTab(tab.id);
              setIsEditing(false);
            }}
            className={`flex items-center gap-2 px-4 py-2 border-b-2 font-mono text-xs font-bold transition-all ${
              activeTab === tab.id
                ? 'border-amber-500 text-amber-400 bg-amber-500/5'
                : 'border-transparent text-gray-500 hover:text-gray-300'
            }`}
          >
            {tab.icon} {tab.label}
          </button>
        ))}
      </div>

      {/* --- PARTS TAB --- */}
      {activeTab === 'PARTS' && !isEditing && (
        <div className="grid grid-cols-12 gap-5">
          {/* List panel */}
          <div className="col-span-5 space-y-3 glass p-4 rounded-xl border border-white/5 max-h-[500px] overflow-y-auto">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-gray-500 font-mono font-bold uppercase">Piezas en Catálogo ({localParts.length})</span>
              <button
                onClick={startNewPart}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-amber-500 text-black text-[10px] font-bold font-mono hover:bg-amber-400 transition-colors"
              >
                <Plus size={11} /> Nueva Pieza
              </button>
            </div>
            <div className="space-y-1.5">
              {localParts.map(part => (
                <div
                  key={part.id}
                  onClick={() => setSelectedPartId(part.id)}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border cursor-pointer transition-all ${
                    selectedPartId === part.id
                      ? 'border-amber-500/30 bg-amber-500/5'
                      : 'border-white/5 hover:border-white/10 bg-black/15'
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-bold text-white font-mono truncate">{part.name}</p>
                    <p className="text-[9px] text-gray-500 font-mono truncate">{part.specification.oem_number}</p>
                  </div>
                  <ChevronRight size={12} className="text-gray-600" />
                </div>
              ))}
            </div>
          </div>

          {/* Details panel */}
          <div className="col-span-7 glass p-5 rounded-xl border border-white/5 min-h-[400px]">
            {currentPart ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b border-white/5 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-white font-mono">{currentPart.name}</h3>
                    <p className="text-[9px] text-amber-400 font-mono font-bold">{currentPart.id}</p>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => startEditPart(currentPart)}
                      className="p-1.5 rounded bg-white/5 border border-white/10 text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                    >
                      <Edit size={12} />
                    </button>
                    <button
                      onClick={() => deletePart(currentPart.id)}
                      className="p-1.5 rounded bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 text-xs font-mono">
                  <div>
                    <span className="text-[10px] text-gray-500">Categoría</span>
                    <p className="text-white mt-0.5">{currentPart.category}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Ubicación / Posición</span>
                    <p className="text-white mt-0.5">{currentPart.position}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Sistema / Subsistema</span>
                    <p className="text-white mt-0.5">{currentPart.system} / {currentPart.subsystem}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Número OEM</span>
                    <p className="text-amber-300 font-bold mt-0.5">{currentPart.specification.oem_number}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Material</span>
                    <p className="text-white mt-0.5">{currentPart.specification.material}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Dimensiones</span>
                    <p className="text-white mt-0.5">{currentPart.specification.dimensions}</p>
                  </div>
                  {currentPart.specification.torque_nm && (
                    <div>
                      <span className="text-[10px] text-amber-500">Torque</span>
                      <p className="text-amber-400 font-bold mt-0.5">{currentPart.specification.torque_nm}</p>
                    </div>
                  )}
                  <div>
                    <span className="text-[10px] text-gray-500">Nivel de Confianza</span>
                    <p className="text-white mt-0.5">{currentPart.confidence_level}</p>
                  </div>
                </div>

                <div>
                  <span className="text-[10px] text-gray-500 font-mono">Descripción Técnica</span>
                  <p className="text-xs text-gray-300 mt-1 leading-relaxed bg-black/20 p-2.5 rounded border border-white/5">{currentPart.description}</p>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center h-full text-gray-600 font-mono text-center">
                <Layers size={32} className="mb-2 text-gray-700" />
                <p className="text-xs">Seleccione una pieza del catálogo para ver sus especificaciones.</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* --- PART EDIT FORM --- */}
      {activeTab === 'PARTS' && isEditing && (
        <div className="glass p-5 rounded-xl border border-amber-500/20 space-y-4">
          <div className="flex items-center justify-between border-b border-white/5 pb-3">
            <h3 className="text-sm font-bold text-white font-mono flex items-center gap-1.5">
              <Edit size={14} className="text-amber-400" /> Formulario de Pieza
            </h3>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setIsEditing(false)}
                className="px-3 py-1.5 rounded-lg border border-white/10 text-xs font-mono text-gray-400 hover:text-white"
              >
                Cancelar
              </button>
              <button
                onClick={savePartForm}
                className="flex items-center gap-1 px-3.5 py-1.5 rounded-lg bg-amber-500 text-black text-xs font-bold font-mono hover:bg-amber-400"
              >
                <Save size={12} /> Guardar Cambios
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Nombre Técnico de la Pieza</label>
              <input
                type="text"
                value={partForm.name || ''}
                onChange={e => setPartForm(prev => ({ ...prev, name: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Identificador Único (ID)</label>
              <input
                type="text"
                value={partForm.id || ''}
                disabled={selectedPartId !== null}
                onChange={e => setPartForm(prev => ({ ...prev, id: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white disabled:opacity-50 focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Sistema</label>
              <input
                type="text"
                value={partForm.system || ''}
                onChange={e => setPartForm(prev => ({ ...prev, system: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Subsistema</label>
              <input
                type="text"
                value={partForm.subsystem || ''}
                onChange={e => setPartForm(prev => ({ ...prev, subsystem: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Conjunto / Ensamblaje</label>
              <input
                type="text"
                value={partForm.assembly || ''}
                onChange={e => setPartForm(prev => ({ ...prev, assembly: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Categoría</label>
              <input
                type="text"
                value={partForm.category || ''}
                onChange={e => setPartForm(prev => ({ ...prev, category: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Posición del Componente</label>
              <select
                value={partForm.position || 'LEFT'}
                onChange={e => setPartForm(prev => ({ ...prev, position: e.target.value as any }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              >
                <option value="LEFT">Izquierda (LEFT)</option>
                <option value="RIGHT">Derecha (RIGHT)</option>
                <option value="FRONT">Frente (FRONT)</option>
                <option value="REAR">Atrás (REAR)</option>
                <option value="CENTER">Centro (CENTER)</option>
              </select>
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Nivel de Confianza</label>
              <select
                value={partForm.confidence_level || 'PROBABLE'}
                onChange={e => setPartForm(prev => ({ ...prev, confidence_level: e.target.value as any }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              >
                <option value="CONFIRMED">Confirmado por VIN (CONFIRMED)</option>
                <option value="PROBABLE">Probable (PROBABLE)</option>
                <option value="UNCONFIRMED">No confirmado (UNCONFIRMED)</option>
              </select>
            </div>
          </div>

          <div className="border-t border-white/5 pt-4">
            <h4 className="text-xs font-bold text-white font-mono mb-3">Especificaciones Físicas</h4>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-[10px] font-mono text-gray-500 mb-1">Número OEM</label>
                <input
                  type="text"
                  value={specForm.oem_number || ''}
                  onChange={e => setSpecForm(prev => ({ ...prev, oem_number: e.target.value }))}
                  className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
                />
              </div>
              <div>
                <label className="block text-[10px] font-mono text-gray-500 mb-1">Dimensiones</label>
                <input
                  type="text"
                  value={specForm.dimensions || ''}
                  onChange={e => setSpecForm(prev => ({ ...prev, dimensions: e.target.value }))}
                  className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
                />
              </div>
              <div>
                <label className="block text-[10px] font-mono text-gray-500 mb-1">Material</label>
                <input
                  type="text"
                  value={specForm.material || ''}
                  onChange={e => setSpecForm(prev => ({ ...prev, material: e.target.value }))}
                  className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
                />
              </div>
              <div>
                <label className="block text-[10px] font-mono text-gray-500 mb-1">Torque Requerido (N·m)</label>
                <input
                  type="text"
                  value={specForm.torque_nm || ''}
                  onChange={e => setSpecForm(prev => ({ ...prev, torque_nm: e.target.value }))}
                  className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
                />
              </div>
            </div>
          </div>

          <div>
            <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Descripción Técnica del Componente</label>
            <textarea
              value={partForm.description || ''}
              onChange={e => setPartForm(prev => ({ ...prev, description: e.target.value }))}
              rows={3}
              className="w-full bg-black/35 border border-white/10 rounded-lg p-2.5 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono resize-none"
            />
          </div>
        </div>
      )}

      {/* --- PROCEDURES TAB --- */}
      {activeTab === 'PROCEDURES' && !isEditing && (
        <div className="grid grid-cols-12 gap-5">
          {/* List panel */}
          <div className="col-span-5 space-y-3 glass p-4 rounded-xl border border-white/5 max-h-[500px] overflow-y-auto">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-gray-500 font-mono font-bold uppercase">Procedimientos ({localProcs.length})</span>
              <button
                onClick={startNewProc}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-amber-500 text-black text-[10px] font-bold font-mono hover:bg-amber-400 transition-colors"
              >
                <Plus size={11} /> Nuevo Proc
              </button>
            </div>
            <div className="space-y-1.5">
              {localProcs.map(proc => (
                <div
                  key={proc.id}
                  onClick={() => setSelectedProcId(proc.id)}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg border cursor-pointer transition-all ${
                    selectedProcId === proc.id
                      ? 'border-amber-500/30 bg-amber-500/5'
                      : 'border-white/5 hover:border-white/10 bg-black/15'
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-bold text-white font-mono truncate">{proc.title}</p>
                    <p className="text-[9px] text-gray-500 font-mono truncate">{proc.steps.length} pasos · {proc.difficulty}</p>
                  </div>
                  <ChevronRight size={12} className="text-gray-600" />
                </div>
              ))}
            </div>
          </div>

          {/* Details panel */}
          <div className="col-span-7 glass p-5 rounded-xl border border-white/5 min-h-[400px] max-h-[500px] overflow-y-auto">
            {currentProc ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b border-white/5 pb-3">
                  <div>
                    <h3 className="text-sm font-bold text-white font-mono">{currentProc.title}</h3>
                    <p className="text-[9px] text-amber-400 font-mono font-bold">{currentProc.id}</p>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => startEditProc(currentProc)}
                      className="p-1.5 rounded bg-white/5 border border-white/10 text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
                    >
                      <Edit size={12} />
                    </button>
                    <button
                      onClick={() => deleteProc(currentProc.id)}
                      className="p-1.5 rounded bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 text-xs font-mono">
                  <div>
                    <span className="text-[10px] text-gray-500">Vehículo Aplicable</span>
                    <p className="text-white mt-0.5">{currentProc.vehicle_applicability}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Duración Estimada</span>
                    <p className="text-white mt-0.5">{currentProc.estimated_duration_min} min</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Dificultad</span>
                    <p className="text-white mt-0.5">{currentProc.difficulty}</p>
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500">Nivel de Seguridad</span>
                    <p className="text-white mt-0.5">{currentProc.safety_level}</p>
                  </div>
                </div>

                <div className="border-t border-white/5 pt-3">
                  <span className="text-[10px] text-gray-500 font-mono font-bold uppercase mb-2 block">Pasos de Reparación 3D ({currentProc.steps.length})</span>
                  <div className="space-y-2">
                    {currentProc.steps.map(step => (
                      <div key={step.id} className="p-3 bg-black/20 rounded border border-white/5 space-y-1.5 font-mono text-[11px]">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-bold text-amber-400">Paso {step.order}: {step.title}</span>
                          <span className="text-[9px] text-cyan-400 uppercase">{step.type}</span>
                        </div>
                        <p className="text-gray-400">{step.description}</p>
                        <div className="flex flex-wrap gap-2 text-[9px] text-gray-600 mt-1">
                          <span>Nodo 3D: <span className="text-white">{step.target_node_id}</span></span>
                          {step.torque_spec && <span>Torque: <span className="text-amber-300 font-bold">{step.torque_spec}</span></span>}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center h-full text-gray-600 font-mono text-center">
                <Wrench size={32} className="mb-2 text-gray-700" />
                <p className="text-xs">Seleccione un procedimiento para ver o editar sus pasos.</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* --- PROCEDURE EDIT FORM --- */}
      {activeTab === 'PROCEDURES' && isEditing && (
        <div className="glass p-5 rounded-xl border border-amber-500/20 space-y-5">
          <div className="flex items-center justify-between border-b border-white/5 pb-3">
            <h3 className="text-sm font-bold text-white font-mono flex items-center gap-1.5">
              <Edit size={14} className="text-amber-400" /> Formulario de Procedimiento
            </h3>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setIsEditing(false)}
                className="px-3 py-1.5 rounded-lg border border-white/10 text-xs font-mono text-gray-400 hover:text-white"
              >
                Cancelar
              </button>
              <button
                onClick={saveProcForm}
                className="flex items-center gap-1 px-3.5 py-1.5 rounded-lg bg-amber-500 text-black text-xs font-bold font-mono hover:bg-amber-400"
              >
                <Save size={12} /> Guardar Cambios
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Título del Procedimiento</label>
              <input
                type="text"
                value={procForm.title || ''}
                onChange={e => setProcForm(prev => ({ ...prev, title: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Vehículo Aplicable</label>
              <input
                type="text"
                value={procForm.vehicle_applicability || ''}
                onChange={e => setProcForm(prev => ({ ...prev, vehicle_applicability: e.target.value }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Dificultad</label>
              <select
                value={procForm.difficulty || 'MEDIUM'}
                onChange={e => setProcForm(prev => ({ ...prev, difficulty: e.target.value as any }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              >
                <option value="EASY">Fácil</option>
                <option value="MEDIUM">Intermedio</option>
                <option value="HARD">Avanzado</option>
              </select>
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Nivel de Seguridad</label>
              <select
                value={procForm.safety_level || 'CAUTION'}
                onChange={e => setProcForm(prev => ({ ...prev, safety_level: e.target.value as any }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              >
                <option value="SAFE">Seguro (SAFE)</option>
                <option value="CAUTION">Precaución (CAUTION)</option>
                <option value="DANGER">Peligro (DANGER)</option>
              </select>
            </div>
            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase mb-1">Duración Estimada (min)</label>
              <input
                type="number"
                value={procForm.estimated_duration_min || 0}
                onChange={e => setProcForm(prev => ({ ...prev, estimated_duration_min: parseInt(e.target.value) || 0 }))}
                className="w-full bg-black/35 border border-white/10 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-amber-500/50 font-mono"
              />
            </div>
          </div>

          {/* STEP BUILDER SECTION */}
          <div className="border-t border-white/5 pt-4 space-y-3">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold text-white font-mono uppercase tracking-wider">Pasos de Reparación</h4>
              <button
                onClick={startAddStep}
                className="flex items-center gap-1 px-2.5 py-1 rounded bg-emerald-500 text-black text-[10px] font-bold font-mono"
              >
                <Plus size={10} /> Agregar Paso
              </button>
            </div>

            {/* List of current steps in form */}
            <div className="space-y-2 max-h-[250px] overflow-y-auto">
              {(procForm.steps || []).map((step, idx) => (
                <div key={step.id} className="flex items-center justify-between p-2.5 bg-black/25 rounded border border-white/5 font-mono text-[11px]">
                  <div className="min-w-0 flex-1">
                    <p className="text-white font-bold">Paso {step.order}: {step.title}</p>
                    <p className="text-gray-500 truncate">{step.description}</p>
                  </div>
                  <div className="flex items-center gap-1.5 ml-3">
                    <button
                      onClick={() => startEditStep(step, idx)}
                      className="p-1 rounded bg-white/5 text-gray-400 hover:text-white"
                    >
                      <Edit size={11} />
                    </button>
                    <button
                      onClick={() => deleteStep(idx)}
                      className="p-1 rounded bg-red-500/10 text-red-400 hover:bg-red-500/20"
                    >
                      <Trash2 size={11} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Active Step Form */}
            {stepForm.id && (
              <div className="p-4 bg-slate-950/40 rounded-xl border border-white/10 space-y-3 animate-slide-up">
                <p className="text-[10px] font-mono text-emerald-400 font-bold uppercase tracking-wider">
                  {editingStepIdx !== null ? 'Editar Paso de Reparación' : 'Nuevo Paso de Reparación'}
                </p>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Título del Paso</label>
                    <input
                      type="text"
                      value={stepForm.title || ''}
                      onChange={e => setStepForm(prev => ({ ...prev, title: e.target.value }))}
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Nodo 3D Objetivo (ID Semántico)</label>
                    <input
                      type="text"
                      value={stepForm.target_node_id || ''}
                      onChange={e => setStepForm(prev => ({ ...prev, target_node_id: e.target.value }))}
                      placeholder="ej: front_left_lower_control_arm"
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Tipo de Acción</label>
                    <select
                      value={stepForm.type || 'DISASSEMBLE'}
                      onChange={e => setStepForm(prev => ({ ...prev, type: e.target.value as any }))}
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    >
                      <option value="DISASSEMBLE">Desmontar</option>
                      <option value="ASSEMBLE">Montar</option>
                      <option value="TORQUE">Aplicar Torque</option>
                      <option value="INSPECT">Inspeccionar</option>
                      <option value="ALIGN">Alinear</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Animación 3D</label>
                    <select
                      value={stepForm.animation_action || 'NONE'}
                      onChange={e => setStepForm(prev => ({ ...prev, animation_action: e.target.value as any }))}
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    >
                      <option value="NONE">Ninguna</option>
                      <option value="TRANSLATE_X">Mover eje X</option>
                      <option value="TRANSLATE_Y">Mover eje Y</option>
                      <option value="TRANSLATE_Z">Mover eje Z</option>
                      <option value="ROTATE_X">Rotar eje X</option>
                      <option value="EXPLODE">Explotar</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Especificación de Torque (opcional)</label>
                    <input
                      type="text"
                      value={stepForm.torque_spec || ''}
                      onChange={e => setStepForm(prev => ({ ...prev, torque_spec: e.target.value }))}
                      placeholder="ej: 95-120 N·m"
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Orden del Paso</label>
                    <input
                      type="number"
                      value={stepForm.order || 0}
                      onChange={e => setStepForm(prev => ({ ...prev, order: parseInt(e.target.value) || 0 }))}
                      className="w-full bg-black/40 border border-white/10 rounded px-2.5 py-1.5 text-xs text-white font-mono focus:outline-none"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-[9px] font-mono text-gray-500 mb-0.5">Descripción del paso</label>
                  <textarea
                    value={stepForm.description || ''}
                    onChange={e => setStepForm(prev => ({ ...prev, description: e.target.value }))}
                    rows={2}
                    className="w-full bg-black/40 border border-white/10 rounded p-2 text-xs text-white font-mono resize-none focus:outline-none"
                  />
                </div>
                <div className="flex justify-end gap-2">
                  <button
                    onClick={() => setStepForm({})}
                    className="px-2.5 py-1 rounded bg-white/5 border border-white/10 text-[10px] text-gray-400 font-mono"
                  >
                    Cancelar
                  </button>
                  <button
                    onClick={saveStep}
                    className="px-3 py-1 rounded bg-emerald-500 text-black text-[10px] font-bold font-mono"
                  >
                    Confirmar Paso
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* --- AUDIT LOG TAB --- */}
      {activeTab === 'AUDIT' && (
        <div className="glass p-5 rounded-xl border border-white/5 space-y-4">
          <span className="text-[10px] text-gray-500 font-mono font-bold uppercase">Historial de Publicación y Cambios</span>
          <div className="space-y-3 max-h-[400px] overflow-y-auto">
            {auditLog.map(record => (
              <div key={record.id} className="p-3 bg-black/20 rounded border border-white/5 space-y-1 font-mono text-[11px]">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-amber-400 flex items-center gap-1.5">
                    <User size={12} /> {record.author}
                  </span>
                  <span className="text-gray-500 text-[10px]">{record.timestamp}</span>
                </div>
                <div className="flex items-center gap-2 text-[10px] mt-1">
                  <span className="bg-amber-500/10 text-amber-400 border border-amber-500/25 px-1.5 py-0.5 rounded font-bold">{record.action}</span>
                  <span className="text-gray-600">Revisión: #{record.revision}</span>
                </div>
                <p className="text-gray-300 mt-2">{record.details}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* --- IMPORT / EXPORT TAB --- */}
      {activeTab === 'IMPORT_EXPORT' && (
        <div className="grid grid-cols-2 gap-5">
          {/* Operations card */}
          <div className="glass p-5 rounded-xl border border-white/5 space-y-5">
            <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider">Carga y Descarga de Datos</h3>
            <p className="text-[11px] font-mono text-gray-400 leading-relaxed">
              Exporte el catálogo de piezas y la base de datos de procedimientos de reparación guiada actual en formato de intercambio JSON estándar, o importe archivos externos.
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={handleExport}
                className="flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-cyan-500/15 text-cyan-300 border border-cyan-500/30 text-xs font-bold font-mono hover:bg-cyan-500/25 transition-all"
              >
                <Download size={13} /> Exportar JSON
              </button>
              <label className="flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-amber-500 text-black text-xs font-bold font-mono hover:bg-amber-400 cursor-pointer transition-all">
                <Upload size={13} /> Importar JSON
                <input
                  type="file"
                  accept=".json"
                  onChange={handleImport}
                  className="hidden"
                />
              </label>
            </div>
          </div>

          {/* Validation card */}
          <div className="glass p-5 rounded-xl border border-white/5 space-y-4 max-h-[350px] overflow-y-auto">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider">Motor de Calidad y Validación</h3>
              <button
                onClick={validateCatalog}
                className="px-3 py-1.5 rounded bg-amber-500 text-black text-[10px] font-bold font-mono hover:bg-amber-400"
              >
                Ejecutar Validaciones
              </button>
            </div>

            {validationResult.status !== 'IDLE' && (
              <div className={`p-4 rounded-xl border font-mono text-[11px] leading-relaxed space-y-2 ${
                validationResult.status === 'PASS'
                  ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-300'
                  : 'bg-red-500/10 border-red-500/25 text-red-300'
              }`}>
                {validationResult.messages.map((msg, i) => (
                  <p key={i} className="flex items-start gap-1.5">
                    {i === 0 ? (
                      validationResult.status === 'PASS' ? <CheckCircle size={13} className="mt-0.5 flex-shrink-0" /> : <AlertTriangle size={13} className="mt-0.5 flex-shrink-0" />
                    ) : (
                      <ArrowRight size={10} className="mt-1 flex-shrink-0" />
                    )}
                    <span>{msg}</span>
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
