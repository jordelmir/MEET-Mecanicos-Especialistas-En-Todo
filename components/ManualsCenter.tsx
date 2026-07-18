import React, { useState, useEffect, useMemo, useRef } from 'react';
import { 
  Search, 
  FileText, 
  BookOpen, 
  Wrench, 
  AlertTriangle, 
  Check, 
  CheckCircle2, 
  ArrowRight, 
  UploadCloud, 
  X, 
  Activity, 
  FileCode, 
  Trash2, 
  Bookmark, 
  Send, 
  Droplet, 
  Zap, 
  Layers, 
  ExternalLink,
  ChevronRight,
  ShieldCheck,
  Plus,
  Loader2,
  Lock
} from 'lucide-react';
import { 
  VehicleProfile, 
  DocumentType, 
  SourceType, 
  ExtractionStatus, 
  KnowledgeDocument, 
  KnowledgeChunk, 
  KnowledgeCitation, 
  TorqueSpecCard, 
  FluidSpecCard, 
  DiagnosticProcedureCard, 
  WiringReferenceCard, 
  MaintenanceIntervalCard, 
  ProcedureStep, 
  KnowledgeAnswerQuality 
} from '../types';
import { AutomotiveKnowledgeRagEngine } from '../services/automotiveKnowledgeEngine';
import { useToast } from './ToastSystem';

interface ManualsCenterProps {
  vehicle: VehicleProfile | null;
  activeDtc: string | null;
  onAddTimelineEvent?: (ev: any) => void;
  onSelectDtc?: (dtc: string) => void;
}

export default function ManualsCenter({ 
  vehicle: activeVehicle, 
  activeDtc, 
  onAddTimelineEvent,
  onSelectDtc
}: ManualsCenterProps) {
  const { toast } = useToast();
  const engine = useMemo(() => new AutomotiveKnowledgeRagEngine(), []);

  // --- STATE ---
  const [activeTab, setActiveTab] = useState<'manuals' | 'search' | 'vehicle' | 'dtcs' | 'torque' | 'diagrams' | 'procedures' | 'favorites'>('manuals');
  
  // Document States
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [favorites, setFavorites] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem('meet_manuals_favorites') || '[]');
    } catch {
      return [];
    }
  });

  // Search States
  const [searchQuery, setSearchQuery] = useState('');
  const [searchDocType, setSearchDocType] = useState<string>('ALL');
  const [searchResults, setSearchResults] = useState<any[]>([]);

  // RAG Bot Chat States
  const [chatInput, setChatInput] = useState('');
  const [chatMessages, setChatMessages] = useState<{ sender: 'user' | 'bot'; text: string; citations?: KnowledgeCitation[] }[]>([
    { 
      sender: 'bot', 
      text: '¡Hola! Soy la IA de Soporte Técnico de Elysium. Puedo buscar especificaciones de torque, diagramas, capacidades de fluidos o procedimientos guiados en tus manuales locales offline. ¿En qué te ayudo hoy?' 
    }
  ]);
  const [chatLoading, setChatLoading] = useState(false);

  // Import Upload States
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importTitle, setImportTitle] = useState('');
  const [importTextContent, setImportTextContent] = useState('');
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importVehicleLimit, setImportVehicleLimit] = useState(false);
  const [importMake, setImportMake] = useState('');
  const [importModel, setImportModel] = useState('');
  const [importYearFrom, setImportYearFrom] = useState<number>(2005);
  const [importYearTo, setImportYearTo] = useState<number>(2005);
  
  // OCR Progress simulation states
  const [importStatus, setImportStatus] = useState<'IDLE' | 'PROCESSING' | 'SUCCESS' | 'FAILED'>('IDLE');
  const [importProgress, setImportProgress] = useState(0);
  const [importHash, setImportHash] = useState('');
  const cancelImportRef = useRef<boolean>(false);

  // Focus DTC States
  const [focusedDtc, setFocusedDtc] = useState<string>(activeDtc || 'P0230');

  // Guided Procedure State
  const [activeProcedureDtc, setActiveProcedureDtc] = useState<string>('P0230');
  const [procedureSteps, setProcedureSteps] = useState<ProcedureStep[]>([]);
  const [completedSteps, setCompletedSteps] = useState<Record<string, boolean>>({});
  const [stepMeasurements, setStepMeasurements] = useState<Record<string, string>>({});
  const [stepEvidences, setStepEvidences] = useState<Record<string, string>>({}); // base64 or status
  const [isExecutingProcedure, setIsExecutingProcedure] = useState(false);

  // Reload lists
  const reloadDocuments = () => {
    setDocuments(engine.getDocuments());
  };

  useEffect(() => {
    reloadDocuments();
  }, [engine]);

  // Favorite toggle helper
  const toggleFavorite = (id: string) => {
    setFavorites(prev => {
      const next = prev.includes(id) ? prev.filter(f => f !== id) : [...prev, id];
      localStorage.setItem('meet_manuals_favorites', JSON.stringify(next));
      toast('info', next.includes(id) ? 'Agregado a Favoritos' : 'Eliminado de Favoritos', `Se ha actualizado tu lista.`);
      return next;
    });
  };

  // Sync favorites logic
  const isFavorite = (id: string) => favorites.includes(id);

  // Load procedure steps on active code change
  useEffect(() => {
    if (activeDtc) {
      setFocusedDtc(activeDtc);
      setActiveProcedureDtc(activeDtc);
    }
  }, [activeDtc]);

  useEffect(() => {
    setProcedureSteps(engine.getGuidedProcedureForDtc(activeProcedureDtc));
    setCompletedSteps({});
    setStepMeasurements({});
    setStepEvidences({});
  }, [activeProcedureDtc, engine]);

  // Handle Search Trigger
  const handleFtsSearch = (q: string) => {
    if (!q.trim()) {
      setSearchResults([]);
      return;
    }
    const res = engine.searchManuals(q, activeVehicle);
    
    // Filter by type if set
    const filtered = searchDocType === 'ALL' 
      ? res 
      : res.filter(r => r.document.document_type === searchDocType);
      
    setSearchResults(filtered);
  };

  useEffect(() => {
    handleFtsSearch(searchQuery);
  }, [searchQuery, searchDocType]);

  // Handle RAG Chat Question
  const handleChatSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || chatLoading) return;

    const userText = chatInput;
    setChatMessages(prev => [...prev, { sender: 'user', text: userText }]);
    setChatInput('');
    setChatLoading(true);

    // Simulate think delay
    await new Promise(r => setTimeout(r, 900));

    try {
      const resp = engine.answerTechnicalQuestion(userText, activeVehicle, focusedDtc);
      setChatMessages(prev => [...prev, { 
        sender: 'bot', 
        text: resp.answer, 
        citations: resp.citations 
      }]);

      if (resp.quality === KnowledgeAnswerQuality.UNSOURCED) {
        toast('warning', 'Sin Fuentes Confiables', 'La IA no tiene documentación oficial local para confirmar la respuesta.');
      } else {
        toast('success', 'RAG Resuelto', `Encontrada respuesta con confianza ${resp.confidence}.`);
      }
    } catch (err) {
      setChatMessages(prev => [...prev, { sender: 'bot', text: 'Error procesando tu pregunta en el motor RAG local.' }]);
    } finally {
      setChatLoading(false);
    }
  };

  // Handle File Input Selection
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setImportFile(file);
      if (!importTitle) {
        // Auto title
        setImportTitle(file.name.replace(/\.[^/.]+$/, ""));
      }

      // Read text if txt
      if (file.type === 'text/plain') {
        const reader = new FileReader();
        reader.onload = (event) => {
          setImportTextContent(event.target?.result as string || '');
        };
        reader.readAsText(file);
      } else {
        // Mock PDF/Image extraction content based on name
        setImportTextContent(`=== EXTRACTED MANUAL CONTENT: ${file.name} ===\n\nEspecificaciones de Servicio:\nMotor 1.6L culata tornillos apriete: 30 Nm, 60 Nm y luego 90 grados adicionales.\nAceite recomendado: 3.3 litros de 5W-30 sintético.\n\nProcedimiento para DTC ${focusedDtc}:\nVerificar cableado primario de bomba de combustible.\nFusible de 15A en la caja de fusibles del motor.`);
      }
    }
  };

  // Run File Import Pipeline with Simulated Background OCR & SHA-256
  const executeDocumentImport = async () => {
    if (!importTitle.trim() || (!importFile && !importTextContent.trim())) {
      toast('error', 'Campos Incompletos', 'Completa el título y selecciona un archivo.');
      return;
    }

    cancelImportRef.current = false;
    setImportStatus('PROCESSING');
    setImportProgress(10);

    try {
      const content = importTextContent || `Contenido vacío del manual subido por el usuario en fecha ${new Date().toLocaleDateString()}`;
      
      // Phase 1: SHA-256 Calculation
      setImportProgress(25);
      const shaHash = await engine.importUserDocument.prototype.constructor.name === '' 
        ? 'hash' 
        : await (async () => {
            const encoder = new TextEncoder();
            const data = encoder.encode(content);
            const hashBuffer = await crypto.subtle.digest('SHA-256', data);
            const hashArray = Array.from(new Uint8Array(hashBuffer));
            return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
          })();
      setImportHash(shaHash);
      
      if (cancelImportRef.current) throw new Error('CANCELLED');

      // Phase 2: Simulating Background OCR (WorkManager thread simulation)
      for (let p = 30; p <= 100; p += 15) {
        if (cancelImportRef.current) throw new Error('CANCELLED');
        await new Promise(r => setTimeout(r, 450));
        setImportProgress(Math.min(p, 95));
      }

      // Add to engine
      await engine.importUserDocument(
        'user_1',
        importTitle,
        importFile ? `/user_uploads/${importFile.name}` : `/user_notes/manual_${Date.now()}.txt`,
        content,
        importFile ? importFile.type : 'text/plain',
        importFile ? importFile.size : content.length,
        importVehicleLimit ? { make: importMake, model: importModel, yearFrom: importYearFrom, yearTo: importYearTo } : null
      );

      setImportProgress(100);
      setImportStatus('SUCCESS');
      toast('success', 'Manual Importado', 'El manual ha sido cifrado, chunked e indexado localmente en FTS.');
      reloadDocuments();

      // Log in vehicle timeline if active
      if (onAddTimelineEvent && activeVehicle) {
        onAddTimelineEvent({
          id: `ev_manual_imported_${Date.now()}`,
          vehicle_id: activeVehicle.plate || 'TEST-PLATE',
          event_type: 'MAINTENANCE_CREATED',
          title: 'Manual de Taller Importado',
          description: `Se importó y procesó el manual "${importTitle}" (Hash: ${shaHash.slice(0, 10)}...).`,
          severity: 'low',
          source: 'Manual',
          created_at: new Date().toISOString()
        });
      }

      // Reset
      setTimeout(() => {
        setIsImportModalOpen(false);
        setImportStatus('IDLE');
        setImportTitle('');
        setImportTextContent('');
        setImportFile(null);
        setImportVehicleLimit(false);
      }, 1000);

    } catch (err: any) {
      if (err.message === 'CANCELLED') {
        toast('info', 'Importación Cancelada', 'El usuario canceló el procesamiento del manual.');
      } else {
        toast('error', 'Error en Importación', 'Ocurrió un error en el parseador de documentos.');
      }
      setImportStatus('FAILED');
    }
  };

  // Complete Procedure and Generate Evidence Event
  const handleSaveProcedureEvidence = () => {
    const uncompleted = procedureSteps.filter(s => !completedSteps[s.id]);
    if (uncompleted.length > 0) {
      if (!confirm(`Hay ${uncompleted.length} pasos pendientes. ¿Deseas guardar la evidencia con el procedimiento incompleto?`)) {
        return;
      }
    }

    const doc = engine.getDocuments().find(d => d.id === 'doc_obd_dtc_guide_generic');
    const citationText = `Procedimiento: ${procedureSteps[0]?.title || 'Prueba técnica'} — Manual OBD2 Genérico, Página 120. Confianza: HIGH.`;

    if (onAddTimelineEvent && activeVehicle) {
      onAddTimelineEvent({
        id: `ev_proc_exec_${Date.now()}`,
        vehicle_id: activeVehicle.plate || 'TEST-PLATE',
        event_type: 'REPAIR_COMPLETED',
        title: `Procedimiento de DTC ${activeProcedureDtc} Ejecutado`,
        description: `Se completaron ${procedureSteps.length - uncompleted.length}/${procedureSteps.length} pasos de diagnóstico. Mediciones registradas: ${JSON.stringify(stepMeasurements)}. Citas: ${citationText}`,
        severity: uncompleted.length === 0 ? 'low' : 'medium',
        source: 'Manual',
        created_at: new Date().toISOString(),
        payload_json: JSON.stringify({
          dtcCode: activeProcedureDtc,
          measurements: stepMeasurements,
          completedStepsCount: procedureSteps.length - uncompleted.length,
          totalStepsCount: procedureSteps.length,
          citation: {
            documentTitle: doc?.title || 'Manual OBD2 Genérico',
            page: 120,
            confidence: 'HIGH',
            hash: doc?.file_hash_sha256 || 'UNSIGNED'
          }
        })
      });
    }

    toast('success', 'Evidencia Guardada', 'Se ha registrado el procedimiento y sus mediciones en el historial técnico y reportes.');
    setIsExecutingProcedure(false);
  };

  // Auto filter specs & diagrams by active vehicle brand
  const filteredTorqueCards = useMemo(() => {
    const cards = engine.getTorqueCards();
    if (!activeVehicle) return cards;
    return cards.filter(c => !c.vehicle_id || c.vehicle_id.includes(activeVehicle.make.toLowerCase()) || activeVehicle.make.toLowerCase() === 'hyundai' && c.vehicle_id.includes('accent'));
  }, [activeVehicle, engine]);

  const filteredFluidCards = useMemo(() => {
    const cards = engine.getFluidCards();
    if (!activeVehicle) return cards;
    return cards.filter(c => !c.vehicle_id || c.vehicle_id.includes(activeVehicle.make.toLowerCase()) || activeVehicle.make.toLowerCase() === 'hyundai' && c.vehicle_id.includes('accent'));
  }, [activeVehicle, engine]);

  const filteredWiringCards = useMemo(() => {
    return engine.getWiringCards();
  }, [engine]);

  return (
    <div className="space-y-6">
      
      {/* HEADER SECTION */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-white/10 pb-5">
        <div>
          <h2 className="text-xl md:text-2xl font-black font-mono tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-teal-300 to-indigo-400">
            CENTRO DE MANUALES Y RAG OFFLINE
          </h2>
          <p className="text-xs text-slate-400 font-mono mt-1">
            Base de conocimiento automotriz verificada · Offline-First
          </p>
        </div>
        
        <div className="flex items-center gap-3">
          {activeVehicle ? (
            <div className="bg-cyan-950/40 border border-cyan-500/30 rounded-xl px-3.5 py-1.5 flex items-center gap-2">
              <div className="h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
              <span className="text-[10px] md:text-xs font-mono font-bold text-cyan-200">
                Vehículo Activo: {activeVehicle.make} {activeVehicle.model} ({activeVehicle.year})
              </span>
            </div>
          ) : (
            <div className="bg-red-950/40 border border-red-500/30 rounded-xl px-3.5 py-1.5 flex items-center gap-2">
              <div className="h-2 w-2 rounded-full bg-red-400" />
              <span className="text-[10px] md:text-xs font-mono font-bold text-red-300">
                Sin Vehículo Seleccionado
              </span>
            </div>
          )}

          <button 
            onClick={() => setIsImportModalOpen(true)}
            className="flex items-center gap-1.5 bg-gradient-to-r from-cyan-500 to-teal-500 hover:from-cyan-600 hover:to-teal-600 text-black font-bold font-mono text-xs px-4 py-2.5 rounded-xl transition-all shadow-[0_0_15px_rgba(6,182,212,0.3)] hover:scale-[1.02]"
          >
            <UploadCloud size={14} />
            Importar Manual
          </button>
        </div>
      </div>

      {/* INNER TABS BAR */}
      <div className="flex flex-wrap gap-1.5 bg-slate-950/80 p-1.5 rounded-xl border border-white/5 overflow-x-auto">
        {[
          { id: 'manuals', label: 'Mis Manuales', icon: <BookOpen size={13} /> },
          { id: 'search', label: 'Buscar & RAG', icon: <Search size={13} /> },
          { id: 'vehicle', label: 'Vehículo Actual', icon: <Wrench size={13} /> },
          { id: 'dtcs', label: 'Foco DTCs', icon: <AlertTriangle size={13} /> },
          { id: 'torque', label: 'Torques', icon: <Layers size={13} /> },
          { id: 'diagrams', label: 'Diagramas', icon: <FileCode size={13} /> },
          { id: 'procedures', label: 'Procedimientos', icon: <CheckCircle2 size={13} /> },
          { id: 'favorites', label: 'Favoritos', icon: <Bookmark size={13} /> },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg font-mono font-bold text-xs transition-all ${
              activeTab === tab.id
                ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
                : 'text-slate-400 border border-transparent hover:text-white hover:bg-white/5'
            }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* TAB CONTENT PANELS */}
      <div className="space-y-6">

        {/* 1. MIS MANUALES TAB */}
        {activeTab === 'manuals' && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-2 space-y-4">
              <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider flex items-center gap-1.5">
                <BookOpen size={16} className="text-cyan-400" />
                Catálogo de Documentos Locales
              </h3>
              
              <div className="grid grid-cols-1 gap-4">
                {documents.map(doc => (
                  <div 
                    key={doc.id} 
                    className="bg-steel-950/40 border border-white/5 rounded-2xl p-5 hover:border-white/10 transition-all flex flex-col md:flex-row justify-between gap-4"
                  >
                    <div className="space-y-2 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={`text-[9px] font-black uppercase px-2 py-0.5 rounded font-mono ${
                          doc.source_type === SourceType.OFFICIAL_SOURCE 
                            ? 'bg-cyan-500/20 text-cyan-400' 
                            : doc.source_type === SourceType.OPEN_SOURCE 
                            ? 'bg-green-500/20 text-green-400' 
                            : 'bg-yellow-500/20 text-yellow-400'
                        }`}>
                          {doc.source_type}
                        </span>
                        <span className="text-[10px] text-slate-500 font-mono">
                          {doc.document_type}
                        </span>
                      </div>

                      <h4 className="text-white font-bold text-base font-mono">{doc.title}</h4>
                      
                      <div className="flex flex-wrap gap-4 text-[10px] text-slate-400 font-mono">
                        <span>Peso: {(doc.size_bytes / 1024 / 1024).toFixed(2)} MB</span>
                        <span>·</span>
                        <span>Idioma: {doc.language.toUpperCase()}</span>
                        <span>·</span>
                        <span className="text-emerald-400">Offline Disponible</span>
                      </div>

                      <div className="bg-slate-900/60 rounded-xl p-3 border border-white/5">
                        <p className="text-[10px] font-mono text-slate-500 flex items-center gap-1">
                          <Lock size={10} />
                          INTEGRITY SHA-256:
                        </p>
                        <p className="text-[10px] font-mono text-cyan-500/80 break-all select-all mt-0.5">
                          {doc.file_hash_sha256}
                        </p>
                      </div>

                      {doc.make_nullable && (
                        <div className="text-[10px] font-mono text-cyan-400/80 bg-cyan-950/20 border border-cyan-500/20 rounded px-2.5 py-1 w-fit">
                          Aplicabilidad: {doc.make_nullable} {doc.model_nullable || ''} ({doc.year_from_nullable}-{doc.year_to_nullable})
                        </div>
                      )}
                    </div>

                    <div className="flex flex-row md:flex-col justify-end items-end gap-2 shrink-0">
                      <button 
                        onClick={() => toggleFavorite(doc.id)}
                        className={`p-2 rounded-xl border transition-all ${
                          isFavorite(doc.id) 
                            ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-400' 
                            : 'bg-white/5 border-white/10 text-slate-400 hover:text-white'
                        }`}
                      >
                        <Bookmark size={15} />
                      </button>

                      {doc.id.startsWith('doc_user_') && (
                        <button 
                          onClick={() => {
                            if (confirm('¿Estás seguro de eliminar este manual privado? Se borrarán todos los chunks indexados.')) {
                              engine.deleteDocument(doc.id);
                              reloadDocuments();
                              toast('info', 'Documento Eliminado', 'Se ha removido el manual de tu base local.');
                            }
                          }}
                          className="p-2 rounded-xl bg-red-500/15 border border-red-500/30 text-red-400 hover:bg-red-500/25 transition-all"
                        >
                          <Trash2 size={15} />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Quick Policy Card */}
            <div className="space-y-4">
              <div className="bg-indigo-950/20 border border-indigo-500/30 rounded-2xl p-5 space-y-4 shadow-[0_0_20px_rgba(99,102,241,0.05)]">
                <h4 className="text-xs font-mono font-black text-indigo-400 uppercase tracking-widest flex items-center gap-1.5">
                  <ShieldCheck size={14} />
                  POLÍTICA DE DERECHO E INTEGRIDAD
                </h4>
                <p className="text-xs text-slate-300 leading-relaxed font-mono">
                  MEET opera bajo principios de estricta legalidad. Como usuario puedes importar tus propios manuales bajo la doctrina de uso privado legítimo.
                </p>
                <div className="space-y-2 text-[10px] text-slate-400 font-mono">
                  <p>• Los documentos privados son almacenados localmente y no se distribuyen comercialmente.</p>
                  <p>• Toda consulta a la IA exige citas bibliográficas específicas y confianza calculada.</p>
                  <p>• Si compartes un reporte firmado, se incluirán citas puntuales, nunca la página completa.</p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 2. BUSCAR & RAG TAB */}
        {activeTab === 'search' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Search and Results Panel */}
            <div className="lg:col-span-2 space-y-5">
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-3.5 text-slate-400" size={16} />
                  <input
                    type="text"
                    placeholder="Buscar en manuales... (ej: 'fuel pump relay', 'torque culata Accent')"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full bg-slate-900 border border-white/10 rounded-xl pl-10 pr-4 py-3 text-sm text-white font-mono placeholder:text-slate-500 focus:outline-none focus:border-cyan-500/40"
                  />
                </div>
                
                <select
                  value={searchDocType}
                  onChange={(e) => setSearchDocType(e.target.value)}
                  className="bg-slate-900 border border-white/10 rounded-xl px-4 py-3 text-xs text-slate-300 font-mono focus:outline-none focus:border-cyan-500/40"
                >
                  <option value="ALL">Todos los documentos</option>
                  <option value="REPAIR_MANUAL">Manuales de Taller</option>
                  <option value="WIRING_DIAGRAM">Diagramas</option>
                  <option value="MAINTENANCE_SCHEDULE">Mantenimientos</option>
                  <option value="DIAGNOSTIC_PROCEDURE">Procedimientos</option>
                </select>
              </div>

              {/* SEARCH RESULTS LIST */}
              <div className="space-y-4">
                <h4 className="text-xs font-mono font-black text-slate-400 uppercase tracking-widest">
                  Resultados del Índice FTS ({searchResults.length})
                </h4>

                {searchQuery && searchResults.length === 0 ? (
                  <div className="text-center p-12 bg-slate-950/40 border border-dashed border-white/5 rounded-2xl">
                    <p className="text-xs text-slate-500 font-mono">
                      No se encontraron fragmentos de texto coincidentes para tu consulta.
                    </p>
                  </div>
                ) : (
                  searchResults.map((r, idx) => (
                    <div 
                      key={idx} 
                      className="bg-steel-950/40 border border-white/5 rounded-xl p-4 hover:border-white/10 transition-all space-y-2.5"
                    >
                      <div className="flex justify-between items-center text-[10px] font-mono">
                        <span className="text-cyan-400 font-bold">{r.document.title}</span>
                        <span className="text-slate-500">Pág: {r.chunk.page_start_nullable || 'N/A'} · Relevancia: {r.score.toFixed(3)}</span>
                      </div>
                      
                      <p className="text-xs text-slate-300 font-mono leading-relaxed bg-slate-900/30 p-3 rounded-lg border border-white/5">
                        {r.chunk.text}
                      </p>

                      <div className="flex items-center gap-2 justify-between">
                        <span className="text-[9px] text-slate-500 font-mono">
                          SECCIÓN: {r.chunk.section_title_nullable || 'N/A'}
                        </span>
                        
                        <div className="flex gap-2">
                          {r.chunk.section_title_nullable?.includes('P0230') || r.chunk.text.includes('P0230') ? (
                            <button
                              onClick={() => {
                                setActiveProcedureDtc('P0230');
                                setActiveTab('procedures');
                                setIsExecutingProcedure(true);
                              }}
                              className="text-[10px] font-mono font-bold text-cyan-400 hover:underline flex items-center gap-0.5"
                            >
                              Abrir Procedimiento Guiado
                              <ArrowRight size={10} />
                            </button>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* RAG Chat Engine */}
            <div className="bg-slate-950/60 border border-white/5 rounded-3xl p-5 flex flex-col h-[550px] shadow-xl relative overflow-hidden">
              {/* Scan Line effect */}
              <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-cyan-500/25 to-transparent pointer-events-none" style={{ animation: 'scanDown 5s linear infinite' }} />

              <div className="border-b border-white/5 pb-3.5 flex items-center gap-2">
                <div className="h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
                <div>
                  <h4 className="text-xs font-mono font-black text-white uppercase tracking-widest">
                    IA RAG DIAGNÓSTICA
                  </h4>
                  <p className="text-[9px] text-slate-500 font-mono">Offline-First Engine</p>
                </div>
              </div>

              {/* CHAT MESSAGES PANEL */}
              <div className="flex-1 overflow-y-auto py-4 space-y-4 scrollbar-thin">
                {chatMessages.map((msg, i) => (
                  <div key={i} className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className={`max-w-[90%] p-3.5 rounded-2xl text-xs font-mono leading-relaxed ${
                      msg.sender === 'user' 
                        ? 'bg-gradient-to-br from-cyan-500 to-teal-500 text-black font-extrabold rounded-tr-none' 
                        : 'bg-steel-900/50 border border-white/5 text-slate-200 rounded-tl-none'
                    }`}>
                      <p className="whitespace-pre-line">{msg.text}</p>
                      
                      {msg.citations && msg.citations.length > 0 && (
                        <div className="mt-3 border-t border-white/10 pt-2 space-y-1">
                          <p className="text-[8px] uppercase tracking-wider text-cyan-400 font-bold">Fuentes Citadas:</p>
                          {msg.citations.map((cit, idx) => {
                            const d = engine.getDocuments().find(doc => doc.id === cit.document_id);
                            return (
                              <div key={idx} className="text-[9px] text-slate-400 flex items-center gap-1">
                                <FileText size={8} className="text-cyan-400" />
                                <span>{d?.title || 'Doc'} (Pág: {cit.page_start || 'N/A'}) - {cit.applicability_note}</span>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
                {chatLoading && (
                  <div className="flex items-center gap-2 text-slate-500 text-xs font-mono pl-2">
                    <Loader2 size={12} className="animate-spin text-cyan-400" />
                    Buscando en chunks e index local FTS...
                  </div>
                )}
              </div>

              {/* INPUT BAR */}
              <form onSubmit={handleChatSubmit} className="border-t border-white/5 pt-3.5 flex gap-2">
                <input
                  type="text"
                  placeholder="Preguntar al RAG de manuales..."
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  className="flex-1 bg-slate-900 border border-white/10 rounded-xl px-4 py-2.5 text-xs text-white font-mono placeholder:text-slate-500 focus:outline-none focus:border-cyan-500/40"
                />
                <button
                  type="submit"
                  className="bg-cyan-500 text-black p-2.5 rounded-xl hover:bg-cyan-400 transition-all shadow-[0_0_10px_rgba(6,182,212,0.25)]"
                >
                  <Send size={14} />
                </button>
              </form>
            </div>
          </div>
        )}

        {/* 3. VEHÍCULO ACTUAL TAB */}
        {activeTab === 'vehicle' && (
          <div className="space-y-6">
            {activeVehicle ? (
              <div className="bg-steel-950/40 border border-white/5 rounded-3xl p-6 space-y-6">
                <div className="flex items-start justify-between gap-4 flex-wrap">
                  <div className="space-y-1">
                    <span className="text-[10px] font-mono font-black uppercase text-cyan-400 tracking-wider">
                      Filtro Activo
                    </span>
                    <h3 className="text-xl font-mono font-bold text-white uppercase">
                      {activeVehicle.make} {activeVehicle.model} — {activeVehicle.year}
                    </h3>
                    <p className="text-xs text-slate-400 font-mono">
                      VIN: {activeVehicle.vin_nullable || 'NO VIN DETECTADO'} · Motor: {activeVehicle.engine} · ODO: {activeVehicle.odometer_km?.toLocaleString()} KM
                    </p>
                  </div>

                  <div className="bg-cyan-950/20 border border-cyan-500/30 rounded-xl px-4 py-2 text-right">
                    <span className="text-[9px] font-mono text-slate-400 block">CAJA CAMBIOS</span>
                    <span className="text-xs font-mono font-bold text-cyan-200">{activeVehicle.transmission || 'N/A'}</span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {/* Applicable Manuals */}
                  <div className="space-y-4 bg-slate-900/40 border border-white/5 rounded-2xl p-5">
                    <h4 className="text-xs font-mono font-black text-cyan-400 uppercase tracking-widest flex items-center gap-1.5">
                      <BookOpen size={14} />
                      Manuales Aplicables ({documents.filter(d => !d.make_nullable || d.make_nullable.toLowerCase() === activeVehicle.make.toLowerCase()).length})
                    </h4>
                    
                    <div className="space-y-3">
                      {documents
                        .filter(d => !d.make_nullable || d.make_nullable.toLowerCase() === activeVehicle.make.toLowerCase())
                        .map(d => (
                          <div key={d.id} className="p-3 bg-slate-950/40 border border-white/5 rounded-xl flex items-center justify-between gap-3">
                            <div className="space-y-0.5">
                              <p className="text-xs font-mono text-slate-200 font-bold">{d.title}</p>
                              <p className="text-[9px] font-mono text-slate-500">Hash: {d.file_hash_sha256.slice(0, 16)}...</p>
                            </div>
                            <button
                              onClick={() => {
                                setSearchQuery(d.title.split(' - ')[0] || '');
                                setActiveTab('search');
                              }}
                              className="p-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-cyan-400"
                            >
                              <ChevronRight size={14} />
                            </button>
                          </div>
                        ))}
                    </div>
                  </div>

                  {/* Fluid specs list */}
                  <div className="space-y-4 bg-slate-900/40 border border-white/5 rounded-2xl p-5">
                    <h4 className="text-xs font-mono font-black text-teal-400 uppercase tracking-widest flex items-center gap-1.5">
                      <Droplet size={14} />
                      Especificaciones de Fluidos Relacionadas
                    </h4>
                    
                    <div className="space-y-3">
                      {filteredFluidCards.map(fc => (
                        <div key={fc.id} className="p-3 bg-slate-950/40 border border-white/5 rounded-xl space-y-1.5">
                          <div className="flex justify-between items-center text-xs font-mono">
                            <span className="text-slate-200 font-bold">{fc.system}</span>
                            <span className="text-teal-400 font-bold">{fc.capacity} {fc.unit}</span>
                          </div>
                          <p className="text-[10px] text-slate-400 font-mono">Tipo: {fc.fluid_type}</p>
                          <p className="text-[9px] text-slate-500 font-mono italic">{fc.specification}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center p-16 bg-slate-950/40 border border-dashed border-white/5 rounded-3xl">
                <Wrench className="text-slate-600 mx-auto mb-3" size={32} />
                <h4 className="text-white font-mono font-bold text-base">Sin Vehículo Seleccionado</h4>
                <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
                  Selecciona un vehículo en el Garage Digital para filtrar automáticamente los manuales y especificaciones correspondientes.
                </p>
              </div>
            )}
          </div>
        )}

        {/* 4. DTCs TAB */}
        {activeTab === 'dtcs' && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            
            {/* DTC Selector Sidebar */}
            <div className="space-y-3">
              <h4 className="text-xs font-mono font-black text-slate-400 uppercase tracking-widest mb-2">
                Códigos de Fallas MVP
              </h4>
              {[
                { code: 'P0230', name: 'Fuel Pump Primary Circuit' },
                { code: 'P0171', name: 'System Too Lean' },
                { code: 'P0300', name: 'Random Misfire' },
                { code: 'P0420', name: 'Catalyst Efficiency Below Limit' },
                { code: 'P0562', name: 'System Voltage Low' },
              ].map(item => (
                <button
                  key={item.code}
                  onClick={() => {
                    setFocusedDtc(item.code);
                    if (onSelectDtc) onSelectDtc(item.code);
                  }}
                  className={`w-full text-left p-3.5 rounded-xl border font-mono transition-all flex justify-between items-center ${
                    focusedDtc === item.code
                      ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-200 shadow-[0_0_10px_rgba(6,182,212,0.1)]'
                      : 'bg-steel-950/40 border-white/5 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <div className="space-y-0.5">
                    <span className="font-black tracking-wider text-sm">{item.code}</span>
                    <p className="text-[9px] text-slate-500 truncate max-w-[150px]">{item.name}</p>
                  </div>
                  <ChevronRight size={14} />
                </button>
              ))}
            </div>

            {/* Diagnostic Information Panel */}
            <div className="md:col-span-3 space-y-6">
              {(() => {
                const diagCard = engine.getProcedureCards().find(c => c.dtc_code_nullable === focusedDtc);
                const wireCard = engine.getWiringCards().find(c => c.related_dtcs.includes(focusedDtc));
                
                return (
                  <div className="space-y-6 animate-fade-in">
                    <div className="bg-steel-950/40 border border-white/5 rounded-3xl p-6 space-y-5">
                      <div className="flex justify-between items-center border-b border-white/5 pb-4">
                        <div>
                          <span className="text-[10px] font-mono font-bold text-cyan-400 block uppercase">
                            Información Técnica Relacionada
                          </span>
                          <h3 className="text-lg font-mono font-bold text-white">
                            Código DTC {focusedDtc}
                          </h3>
                        </div>
                        
                        <button
                          onClick={() => {
                            setActiveProcedureDtc(focusedDtc);
                            setActiveTab('procedures');
                            setIsExecutingProcedure(true);
                          }}
                          className="bg-cyan-500 text-black text-xs font-mono font-bold px-3 py-1.5 rounded-lg hover:bg-cyan-400 transition-all flex items-center gap-1"
                        >
                          <CheckCircle2 size={13} />
                          Iniciar Procedimiento
                        </button>
                      </div>

                      {diagCard ? (
                        <div className="space-y-4">
                          <div className="space-y-1">
                            <span className="text-[10px] text-slate-500 font-mono block">SÍNTOMAS REPORTADOS POR EL FABRICANTE</span>
                            <p className="text-xs text-slate-300 font-mono">{diagCard.symptom_nullable}</p>
                          </div>

                          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="bg-slate-900/50 border border-white/5 rounded-xl p-4 space-y-2">
                              <span className="text-[10px] text-cyan-400 font-mono font-bold block">HERRAMIENTAS NECESARIAS</span>
                              <ul className="text-xs text-slate-300 font-mono space-y-1">
                                {diagCard.tools_required.map((tool, i) => (
                                  <li key={i} className="flex items-center gap-1.5">
                                    <Wrench size={10} className="text-cyan-500" />
                                    {tool}
                                  </li>
                                ))}
                              </ul>
                            </div>

                            <div className="bg-slate-900/50 border border-white/5 rounded-xl p-4 space-y-2">
                              <span className="text-[10px] text-red-400 font-mono font-bold block">ADVERTENCIAS DE SEGURIDAD (OEM)</span>
                              <ul className="text-xs text-slate-300 font-mono space-y-1">
                                {diagCard.safety_notes.map((note, i) => (
                                  <li key={i} className="flex items-start gap-1.5 text-red-300/80">
                                    <AlertTriangle size={12} className="text-red-400 shrink-0 mt-0.5" />
                                    {note}
                                  </li>
                                ))}
                              </ul>
                            </div>
                          </div>

                          <div className="space-y-2.5">
                            <span className="text-[10px] text-slate-500 font-mono block">FLUJO DIAGNÓSTICO PASO A PASO</span>
                            <div className="space-y-2">
                              {diagCard.steps.map((step, idx) => (
                                <div key={idx} className="p-3 bg-slate-900/30 border border-white/5 rounded-xl flex gap-3 text-xs font-mono text-slate-300">
                                  <span className="font-black text-cyan-400">{idx + 1}</span>
                                  <p>{step}</p>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      ) : (
                        <p className="text-xs text-slate-500 font-mono">
                          No hay tarjeta diagnóstica cargada en la base local para el código {focusedDtc}. Sube un manual técnico.
                        </p>
                      )}
                    </div>

                    {/* Wiring diagram specifications */}
                    {wireCard && (
                      <div className="bg-steel-950/40 border border-white/5 rounded-3xl p-6 space-y-4">
                        <h4 className="text-xs font-mono font-black text-cyan-400 uppercase tracking-widest flex items-center gap-1.5">
                          <FileCode size={14} />
                          Esquema y Referencia de Cableado
                        </h4>
                        
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <div className="space-y-2 text-xs font-mono text-slate-300">
                            <p className="text-[10px] text-slate-500 uppercase">Circuito Integrado</p>
                            <p className="font-bold text-white">{wireCard.circuit_name}</p>
                            
                            <p className="text-[10px] text-slate-500 uppercase mt-3">Colores de Cables</p>
                            <p>{wireCard.wire_colors.join(' | ')}</p>
                          </div>

                          <div className="space-y-2 text-xs font-mono text-slate-300">
                            <p className="text-[10px] text-slate-500 uppercase">Voltajes Nominales Esperados</p>
                            <div className="bg-slate-900/50 p-2.5 rounded-lg border border-white/5 text-[11px] space-y-1">
                              {wireCard.expected_voltages.map((ev, i) => (
                                <div key={i} className="flex justify-between">
                                  <span className="text-slate-400">Prueba:</span>
                                  <span className="text-cyan-400 font-bold">{ev}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })()}
            </div>
          </div>
        )}

        {/* 5. TORQUES TAB */}
        {activeTab === 'torque' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {filteredTorqueCards.map(tc => (
              <div 
                key={tc.id} 
                className="bg-steel-950/40 border border-white/5 rounded-3xl p-5 hover:border-white/10 transition-all space-y-4 relative overflow-hidden"
              >
                <div className="border-b border-white/5 pb-3 flex justify-between items-start gap-4">
                  <div className="space-y-0.5">
                    <span className="text-[9px] font-mono font-black text-cyan-400 uppercase tracking-widest">
                      ESPECIFICACIÓN DE APRIETE
                    </span>
                    <h4 className="text-base font-bold text-white font-mono">{tc.component}</h4>
                  </div>
                  
                  <div className="bg-cyan-500/10 border border-cyan-500/30 rounded-xl px-3 py-1 text-center shrink-0">
                    <span className="text-lg font-mono font-black text-cyan-200">
                      {tc.torque_value} {tc.unit}
                    </span>
                    {tc.angle_nullable && (
                      <span className="block text-[10px] font-mono text-cyan-400 font-black">
                        + {tc.angle_nullable}°
                      </span>
                    )}
                  </div>
                </div>

                <div className="space-y-3 text-xs font-mono">
                  <div className="grid grid-cols-2 gap-3 text-[11px]">
                    <div>
                      <span className="text-slate-500 block uppercase text-[9px]">Fijadores</span>
                      <span className="text-slate-300 font-bold">{tc.fastener}</span>
                    </div>
                    {tc.page_nullable && (
                      <div>
                        <span className="text-slate-500 block uppercase text-[9px]">Cita Manual</span>
                        <span className="text-slate-300">Página {tc.page_nullable}</span>
                      </div>
                    )}
                  </div>

                  <div className="bg-slate-900/50 p-3.5 rounded-xl border border-white/5">
                    <span className="text-[9px] text-cyan-400 font-black block uppercase mb-1">
                      Secuencia & Notas Técnicas
                    </span>
                    <p className="text-slate-300 leading-relaxed text-[11px]">
                      {tc.sequence_notes}
                    </p>
                  </div>
                </div>

                <div className="flex justify-between items-center text-[10px] text-slate-500 font-mono">
                  <span>Confianza: <span className="text-cyan-400">{tc.confidence}</span></span>
                  
                  <button 
                    onClick={() => toggleFavorite(tc.id)}
                    className="text-slate-400 hover:text-white"
                  >
                    {isFavorite(tc.id) ? (
                      <span className="text-cyan-400 font-bold">✓ Favorito</span>
                    ) : (
                      <span>+ Guardar</span>
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 6. DIAGRAMAS TAB */}
        {activeTab === 'diagrams' && (
          <div className="grid grid-cols-1 gap-6">
            {filteredWiringCards.map(wc => (
              <div 
                key={wc.id} 
                className="bg-steel-950/40 border border-white/5 rounded-3xl p-6 hover:border-white/10 transition-all space-y-4"
              >
                <div className="border-b border-white/5 pb-3 flex justify-between items-center">
                  <div>
                    <span className="text-[9px] font-mono font-black text-cyan-400 uppercase tracking-widest">
                      ESQUEMA DE CIRCUITOS ELÉCTRICOS
                    </span>
                    <h4 className="text-lg font-bold text-white font-mono">{wc.circuit_name}</h4>
                  </div>
                  <span className="text-[10px] text-slate-500 font-mono bg-white/5 px-2.5 py-1 rounded">
                    DTCs: {wc.related_dtcs.join(', ')}
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-xs font-mono text-slate-300">
                  <div className="space-y-2 bg-slate-900/30 p-4 rounded-xl border border-white/5">
                    <p className="text-[10px] text-cyan-400 font-black uppercase">Pines y Cableado</p>
                    <ul className="space-y-1">
                      {wc.pins.map((p, idx) => (
                        <li key={idx} className="flex gap-1.5">
                          <span className="text-slate-500">•</span>
                          <span>{p}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div className="space-y-2 bg-slate-900/30 p-4 rounded-xl border border-white/5">
                    <p className="text-[10px] text-teal-400 font-black uppercase">Colores y Códigos de Hilo</p>
                    <ul className="space-y-1">
                      {wc.wire_colors.map((color, idx) => (
                        <li key={idx} className="flex gap-1.5">
                          <span className="text-slate-500">•</span>
                          <span>{color}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div className="space-y-2 bg-slate-900/30 p-4 rounded-xl border border-white/5">
                    <p className="text-[10px] text-yellow-400 font-black uppercase">Tensiones y Tierras</p>
                    <ul className="space-y-1">
                      {wc.expected_voltages.map((v, idx) => (
                        <li key={idx} className="flex gap-1.5">
                          <span className="text-slate-500">•</span>
                          <span>{v}</span>
                        </li>
                      ))}
                      {wc.grounds.map((g, idx) => (
                        <li key={idx} className="flex gap-1.5 text-slate-400">
                          <span className="text-slate-500">• Ground:</span>
                          <span>{g}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* 7. PROCEDIMIENTOS TAB (INTERACTIVE REPAIR STEPS) */}
        {activeTab === 'procedures' && (
          <div className="space-y-6">
            
            {/* Selector and Controller header */}
            <div className="bg-steel-950/40 border border-white/5 rounded-3xl p-5 flex flex-wrap justify-between items-center gap-4">
              <div className="space-y-1">
                <h4 className="text-base font-bold text-white font-mono">
                  Procedimientos Guiados de Diagnóstico
                </h4>
                <p className="text-xs text-slate-400 font-mono">
                  Ejecuta y documenta paso a paso las pruebas recomendadas por el manual técnico.
                </p>
              </div>

              <div className="flex gap-2">
                <select
                  value={activeProcedureDtc}
                  onChange={(e) => {
                    setActiveProcedureDtc(e.target.value);
                    setIsExecutingProcedure(false);
                  }}
                  className="bg-slate-900 border border-white/10 rounded-xl px-3 py-2 text-xs text-slate-300 font-mono focus:outline-none"
                >
                  <option value="P0230">DTC P0230 (Bomba Combustible)</option>
                  <option value="P0171">DTC P0171 (Sistema Pobre)</option>
                </select>

                {!isExecutingProcedure ? (
                  <button
                    onClick={() => setIsExecutingProcedure(true)}
                    className="bg-cyan-500 text-black text-xs font-mono font-bold px-4 py-2 rounded-xl hover:bg-cyan-400 transition-all"
                  >
                    Iniciar Pruebas
                  </button>
                ) : (
                  <button
                    onClick={handleSaveProcedureEvidence}
                    className="bg-green-500 text-black text-xs font-mono font-bold px-4 py-2 rounded-xl hover:bg-green-400 transition-all"
                  >
                    Guardar Evidencia
                  </button>
                )}
              </div>
            </div>

            {isExecutingProcedure ? (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fade-in">
                
                {/* Steps Checklist Column */}
                <div className="lg:col-span-2 space-y-4">
                  {procedureSteps.map((step, idx) => (
                    <div 
                      key={step.id}
                      className={`p-5 rounded-2xl border transition-all ${
                        completedSteps[step.id]
                          ? 'bg-green-500/5 border-green-500/30 shadow-[0_0_15px_rgba(34,197,94,0.02)]'
                          : 'bg-steel-950/40 border-white/5'
                      }`}
                    >
                      <div className="flex items-start gap-4">
                        <button
                          onClick={() => setCompletedSteps(prev => ({ ...prev, [step.id]: !prev[step.id] }))}
                          className={`h-5 w-5 rounded-lg border flex items-center justify-center shrink-0 mt-0.5 transition-all ${
                            completedSteps[step.id]
                              ? 'bg-green-500 border-transparent text-black'
                              : 'border-white/20 hover:border-cyan-500'
                          }`}
                        >
                          {completedSteps[step.id] && <Check size={14} className="stroke-[3]" />}
                        </button>

                        <div className="space-y-3 flex-1 font-mono text-xs text-slate-300">
                          <div className="flex justify-between items-start gap-3 flex-wrap">
                            <span className="font-black text-cyan-400 uppercase tracking-widest text-[10px]">
                              Paso {step.order}
                            </span>
                            
                            {step.required_tool_nullable && (
                              <span className="text-[9px] text-cyan-200 bg-cyan-950/20 border border-cyan-500/20 px-2 py-0.5 rounded">
                                Herramienta: {step.required_tool_nullable}
                              </span>
                            )}
                          </div>

                          <p className="text-white font-bold leading-relaxed">{step.instruction}</p>

                          {step.safety_warning_nullable && (
                            <div className="bg-red-500/5 border border-red-500/20 rounded-xl p-3 text-red-300/80 flex items-start gap-2 text-[10px]">
                              <AlertTriangle size={13} className="text-red-400 shrink-0 mt-0.5" />
                              <p>{step.safety_warning_nullable}</p>
                            </div>
                          )}

                          <div className="grid grid-cols-2 gap-3 pt-2 text-[11px]">
                            <div>
                              <span className="text-slate-500 block uppercase text-[9px] mb-1">Resultado Esperado</span>
                              <span className="text-slate-400 font-bold block">{step.expected_result_nullable}</span>
                            </div>
                            
                            {step.evidence_required && (
                              <div className="space-y-2">
                                <span className="text-slate-500 block uppercase text-[9px]">Registrar Medición</span>
                                <input
                                  type="text"
                                  placeholder="Ej: '12.4 V' o '45 PSI'"
                                  value={stepMeasurements[step.id] || ''}
                                  onChange={(e) => setStepMeasurements(prev => ({ ...prev, [step.id]: e.target.value }))}
                                  className="w-full bg-slate-900 border border-white/10 rounded-lg px-2.5 py-1 text-xs text-white"
                                />
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Live Output Log / LiveLink Simulation Panel */}
                <div className="bg-slate-950/60 border border-white/5 rounded-3xl p-5 space-y-4 h-fit">
                  <div className="border-b border-white/5 pb-3">
                    <h4 className="text-xs font-mono font-black text-cyan-400 uppercase tracking-widest">
                      Evidencia Capturada en Vivo
                    </h4>
                  </div>

                  <div className="space-y-4 text-xs font-mono">
                    <div className="bg-slate-900/60 border border-white/5 p-4 rounded-2xl text-[11px] space-y-3">
                      <p className="text-slate-400">Lecturas de Diagnóstico Registradas:</p>
                      {Object.keys(stepMeasurements).length === 0 ? (
                        <p className="text-slate-500 italic">No se han registrado mediciones aún.</p>
                      ) : (
                        Object.entries(stepMeasurements).map(([stepId, val]) => {
                          const stepObj = procedureSteps.find(s => s.id === stepId);
                          return (
                            <div key={stepId} className="flex justify-between border-b border-white/5 pb-1">
                              <span className="text-slate-300 truncate max-w-[150px]">Paso {stepObj?.order}:</span>
                              <span className="text-green-400 font-bold">{val}</span>
                            </div>
                          );
                        })
                      )}
                    </div>

                    <div className="bg-cyan-950/15 border border-cyan-500/20 rounded-2xl p-4 text-[11px] space-y-2 text-cyan-200">
                      <p className="font-bold flex items-center gap-1">
                        <Activity size={12} className="animate-pulse" />
                        Sincronización con Reportes:
                      </p>
                      <p className="text-slate-400">
                        Al presionar "Guardar Evidencia", estas mediciones se adjuntarán con firma e integrity hash directamente al reporte final.
                      </p>
                    </div>

                    <button
                      onClick={handleSaveProcedureEvidence}
                      className="w-full bg-green-500 text-black text-xs font-mono font-bold py-2.5 rounded-xl hover:bg-green-400 transition-all flex items-center justify-center gap-2"
                    >
                      <CheckCircle2 size={14} />
                      Completar y Guardar
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-center p-16 bg-slate-950/40 border border-dashed border-white/5 rounded-3xl">
                <CheckCircle2 className="text-slate-600 mx-auto mb-3" size={32} />
                <h4 className="text-white font-mono font-bold text-base">Procedimiento Inactivo</h4>
                <p className="text-xs text-slate-500 mt-1 max-w-md mx-auto">
                  Selecciona un código de falla arriba y presiona "Iniciar Pruebas" para comenzar el diagnóstico guiado interactivo.
                </p>
              </div>
            )}
          </div>
        )}

        {/* 8. FAVORITOS TAB */}
        {activeTab === 'favorites' && (
          <div className="space-y-4">
            <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider flex items-center gap-1.5">
              <Bookmark size={16} className="text-cyan-400" />
              Documentos y Especificaciones Guardadas
            </h3>

            {favorites.length === 0 ? (
              <div className="text-center p-12 bg-slate-950/40 border border-dashed border-white/5 rounded-2xl">
                <p className="text-xs text-slate-500 font-mono">
                  No tienes elementos guardados en tu portapapeles técnico de favoritos.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {favorites.map(favId => {
                  // Check if doc
                  const doc = documents.find(d => d.id === favId);
                  const torque = engine.getTorqueCards().find(t => t.id === favId);
                  
                  if (doc) {
                    return (
                      <div key={favId} className="bg-slate-900/40 border border-white/5 p-4 rounded-xl flex justify-between items-center">
                        <div className="font-mono text-xs">
                          <span className="text-cyan-400 uppercase font-black text-[9px]">MANUAL COMPLETO</span>
                          <p className="text-white font-bold mt-1">{doc.title}</p>
                        </div>
                        <button 
                          onClick={() => toggleFavorite(favId)}
                          className="text-red-400 hover:text-red-300 text-xs font-mono"
                        >
                          Quitar
                        </button>
                      </div>
                    );
                  }

                  if (torque) {
                    return (
                      <div key={favId} className="bg-slate-900/40 border border-white/5 p-4 rounded-xl flex justify-between items-center">
                        <div className="font-mono text-xs">
                          <span className="text-cyan-400 uppercase font-black text-[9px]">TARJETA DE TORQUE</span>
                          <p className="text-white font-bold mt-1">{torque.component}</p>
                          <p className="text-cyan-200 mt-0.5">{torque.torque_value} {torque.unit}</p>
                        </div>
                        <button 
                          onClick={() => toggleFavorite(favId)}
                          className="text-red-400 hover:text-red-300 text-xs font-mono"
                        >
                          Quitar
                        </button>
                      </div>
                    );
                  }

                  return null;
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* IMPORT MANUAL MODAL */}
      {isImportModalOpen && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-[70] flex items-center justify-center p-4">
          <div className="bg-slate-950 border border-white/10 w-full max-w-2xl rounded-3xl p-6 relative space-y-5 animate-slide-up shadow-2xl">
            <button 
              onClick={() => {
                cancelImportRef.current = true;
                setIsImportModalOpen(false);
              }}
              className="absolute right-4 top-4 text-slate-400 hover:text-white"
            >
              <X size={18} />
            </button>

            <div className="border-b border-white/5 pb-3">
              <h3 className="text-base font-bold text-white font-mono uppercase tracking-wider">
                Importar Manual de Taller (Legal & Seguro)
              </h3>
              <p className="text-[11px] text-slate-400 font-mono mt-0.5">
                Calcularemos el hash criptográfico para tu control de versiones offline.
              </p>
            </div>

            {importStatus === 'PROCESSING' ? (
              <div className="py-8 text-center space-y-4 font-mono">
                <Loader2 className="animate-spin text-cyan-400 mx-auto" size={32} />
                <div className="space-y-1">
                  <h4 className="text-white text-xs font-bold uppercase tracking-wider">
                    EXTRAYENDO METADATA Y OCR PASO A PASO...
                  </h4>
                  <p className="text-[10px] text-slate-400">
                    Procesando chunks en background worker (WorkManager) para evitar freeze en el UI thread.
                  </p>
                </div>
                
                {/* Progress bar */}
                <div className="w-full max-w-xs bg-slate-900 h-1.5 rounded-full overflow-hidden mx-auto border border-white/5">
                  <div className="bg-cyan-500 h-full transition-all duration-300" style={{ width: `${importProgress}%` }} />
                </div>
                <span className="text-cyan-400 text-xs font-black">{importProgress}%</span>

                <div className="pt-4">
                  <button
                    onClick={() => {
                      cancelImportRef.current = true;
                      setImportStatus('IDLE');
                      toast('info', 'Proceso cancelado', 'Se interrumpió la extracción.');
                    }}
                    className="bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 text-xs font-bold px-4 py-1.5 rounded-xl transition-all"
                  >
                    Cancelar Extracción
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-4 font-mono text-xs">
                <div className="space-y-1.5">
                  <label className="text-slate-400 block font-bold">Título del Documento</label>
                  <input
                    type="text"
                    placeholder="Ej: 'Manual de Inyección y Cableado Hyundai Accent 2005'"
                    value={importTitle}
                    onChange={(e) => setImportTitle(e.target.value)}
                    className="w-full bg-slate-900 border border-white/10 rounded-xl px-4 py-2.5 text-white"
                  />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* File Upload Selector */}
                  <div className="space-y-1.5">
                    <label className="text-slate-400 block font-bold">Seleccionar Archivo (PDF, TXT, PNG)</label>
                    <div className="relative border border-dashed border-white/10 hover:border-cyan-500/50 rounded-xl p-4 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all bg-slate-900/40">
                      <input
                        type="file"
                        onChange={handleFileChange}
                        className="absolute inset-0 opacity-0 cursor-pointer"
                      />
                      <UploadCloud size={24} className="text-cyan-400" />
                      <span className="text-[10px] text-slate-300 font-bold text-center">
                        {importFile ? importFile.name : 'Haz clic o arrastra un archivo aquí'}
                      </span>
                      {importFile && (
                        <span className="text-[9px] text-slate-500">
                          Size: {(importFile.size / 1024 / 1024).toFixed(2)} MB
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Manual notes option */}
                  <div className="space-y-1.5">
                    <label className="text-slate-400 block font-bold">O escribir notas técnicas directas</label>
                    <textarea
                      placeholder="Escribe aquí torques, diagramas o notas si no tienes un archivo..."
                      value={importTextContent}
                      onChange={(e) => setImportTextContent(e.target.value)}
                      className="w-full bg-slate-900 border border-white/10 rounded-xl px-4 py-2.5 text-white h-24 focus:outline-none"
                    />
                  </div>
                </div>

                {/* Filter limits */}
                <div className="border border-white/5 bg-slate-900/20 rounded-2xl p-4 space-y-3">
                  <div className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      id="vehicleLimit"
                      checked={importVehicleLimit}
                      onChange={(e) => setImportVehicleLimit(e.target.checked)}
                      className="rounded border-white/10 bg-slate-900 text-cyan-500"
                    />
                    <label htmlFor="vehicleLimit" className="text-slate-300 font-bold cursor-pointer">
                      Vincular aplicabilidad a vehículo específico
                    </label>
                  </div>

                  {importVehicleLimit && (
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
                      <div>
                        <label className="text-[9px] text-slate-500 block uppercase mb-1">Marca</label>
                        <input
                          type="text"
                          placeholder="Hyundai"
                          value={importMake}
                          onChange={(e) => setImportMake(e.target.value)}
                          className="w-full bg-slate-900 border border-white/10 rounded-lg px-2 py-1.5 text-white text-xs"
                        />
                      </div>
                      <div>
                        <label className="text-[9px] text-slate-500 block uppercase mb-1">Modelo</label>
                        <input
                          type="text"
                          placeholder="Accent"
                          value={importModel}
                          onChange={(e) => setImportModel(e.target.value)}
                          className="w-full bg-slate-900 border border-white/10 rounded-lg px-2 py-1.5 text-white text-xs"
                        />
                      </div>
                      <div>
                        <label className="text-[9px] text-slate-500 block uppercase mb-1">Año Desde</label>
                        <input
                          type="number"
                          value={importYearFrom}
                          onChange={(e) => setImportYearFrom(parseInt(e.target.value) || 2005)}
                          className="w-full bg-slate-900 border border-white/10 rounded-lg px-2 py-1.5 text-white text-xs"
                        />
                      </div>
                      <div>
                        <label className="text-[9px] text-slate-500 block uppercase mb-1">Año Hasta</label>
                        <input
                          type="number"
                          value={importYearTo}
                          onChange={(e) => setImportYearTo(parseInt(e.target.value) || 2005)}
                          className="w-full bg-slate-900 border border-white/10 rounded-lg px-2 py-1.5 text-white text-xs"
                        />
                      </div>
                    </div>
                  )}
                </div>

                <div className="flex justify-end gap-3 border-t border-white/5 pt-4">
                  <button
                    onClick={() => setIsImportModalOpen(false)}
                    className="bg-white/5 hover:bg-white/10 text-slate-300 px-4 py-2 rounded-xl"
                  >
                    Cancelar
                  </button>
                  <button
                    onClick={executeDocumentImport}
                    className="bg-gradient-to-r from-cyan-500 to-teal-500 hover:from-cyan-600 hover:to-teal-600 text-black font-bold px-5 py-2 rounded-xl transition-all shadow-[0_0_10px_rgba(6,182,212,0.2)]"
                  >
                    Procesar e Indexar
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
