import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  Search, AlertTriangle, Info, ShieldAlert, X, Wrench, Activity, Play, 
  Square, Save, ChevronDown, Zap, Gauge, Radio, BarChart3, Download,
  Cpu, CheckCircle2, History, RotateCcw, Link2, Sparkles, BookOpen, AlertCircle, RefreshCw
} from 'lucide-react';
import dtcDatabaseUrl from '../dtc_database.json?url';
import { OscilloscopeCanvas } from './OscilloscopeCanvas';
import { SignalGenerator, SignalAnalyzer, SIGNAL_LIBRARY, type SignalDiagnosis, type SignalDefinition } from '../services/signalAnalysis';
import type { OscilloscopeMeasurement, WorkOrder, Client } from '../types';
import { DiagnosticSnapshot } from '../lib/reports/types';
import {
  SafetyPreconditionEngine,
  ObdSnapshotEngine,
  BidirectionalExecutor,
  MOCK_CAPABILITIES,
  MOCK_COMMAND_PROFILES,
  DEFAULT_PROCEDURES,
  type BidirectionalCapability,
  type BidirectionalAction,
  type CommandProfile,
  type LiveTelemetry,
  type ExecutionResult,
  type ActionStatus
} from '../lib/bidirectional';

interface OBD2ScannerProps {
  onClose: () => void;
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onSaveMeasurement?: (measurement: OscilloscopeMeasurement) => void;
  onUpdateWorkOrder?: (id: string, updates: Partial<WorkOrder>) => void;
  onAddTimelineEvent?: (ev: any) => void;
  onNavigateToManuals?: (dtcCode?: string) => void;
}

type TabMode = 'dtc' | 'oscilloscope' | 'activeTests' | 'serviceResets';

interface DtcDatabaseItem {
  code: string;
  manufacturer?: string;
  descriptionEs: string;
  descriptionEn?: string;
  possibleCauses?: string;
  system?: string;
  severity?: string;
}

let dtcDatabasePromise: Promise<DtcDatabaseItem[]> | null = null;

const loadDtcDatabase = () => {
  if (!dtcDatabasePromise) {
    dtcDatabasePromise = fetch(dtcDatabaseUrl, { cache: 'force-cache' }).then(async response => {
      if (!response.ok) {
        throw new Error(`No se pudo cargar la base DTC (${response.status})`);
      }
      return response.json() as Promise<DtcDatabaseItem[]>;
    });
  }
  return dtcDatabasePromise;
};

export function OBD2Scanner({ 
  onClose, 
  currentUser, 
  workOrders, 
  onSaveMeasurement, 
  onUpdateWorkOrder, 
  onAddTimelineEvent,
  onNavigateToManuals 
}: OBD2ScannerProps) {
  const [activeTab, setActiveTab] = useState<TabMode>('oscilloscope');

  return (
    <div className="fixed inset-0 bg-steel-950/85 backdrop-blur-xl z-[90] flex items-center justify-center p-2 sm:p-4 animate-fade-in">
      <div className="bg-steel-900 rounded-3xl w-full max-w-5xl border border-forge-500/30 overflow-hidden flex flex-col shadow-[0_0_80px_rgba(0,240,255,0.12)] animate-slide-up" style={{ maxHeight: '95vh' }}>

        {/* Header */}
        <div className="p-4 sm:p-5 border-b border-white/10 bg-black/90 flex justify-between items-center flex-shrink-0 relative">
          <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-forge-500 to-transparent"></div>
          <div className="flex items-center gap-3">
            <div className="bg-forge-500/20 p-2.5 rounded-xl text-forge-500 border border-forge-500/30 shadow-[0_0_15px_rgba(0,240,255,0.2)]">
              <Cpu size={22} className="animate-pulse-slow" />
            </div>
            <div>
              <h2 className="text-xl font-black text-white font-display tracking-wider flex items-center gap-2">
                MEET <span className="text-forge-500">DIAGNOSTIC PRO</span>
              </h2>
              <p className="text-[9px] text-forge-400/80 font-mono uppercase tracking-widest mt-0.5">Motor de Análisis en Tiempo Real • OBD2 & Osciloscopio</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {/* Tab Buttons */}
            <div className="flex bg-steel-950 p-1 rounded-xl border border-white/5 overflow-hidden">
              <button 
                onClick={() => setActiveTab('oscilloscope')} 
                className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1 ${activeTab === 'oscilloscope' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Activity size={10} /> Osciloscopio
              </button>
              <button 
                onClick={() => setActiveTab('dtc')} 
                className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1 ${activeTab === 'dtc' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Search size={10} /> Analizador DTC
              </button>
              <button 
                onClick={() => setActiveTab('activeTests')} 
                className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1 ${activeTab === 'activeTests' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Zap size={10} /> Pruebas Activas
              </button>
              <button 
                onClick={() => setActiveTab('serviceResets')} 
                className={`px-3 py-1.5 text-[9px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1 ${activeTab === 'serviceResets' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Wrench size={10} /> Resets Servicio
              </button>
            </div>
            <button onClick={onClose} className="p-2 text-steel-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-xl transition-all border border-white/5">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto bg-gradient-to-b from-steel-900 via-steel-900 to-steel-950">
          {activeTab === 'dtc' && (
            <DTCAnalyzerTab 
              currentUser={currentUser} 
              workOrders={workOrders} 
              onUpdateWorkOrder={onUpdateWorkOrder} 
              onNavigateToManuals={onNavigateToManuals}
            />
          )}
          {activeTab === 'oscilloscope' && (
            <OscilloscopeTab currentUser={currentUser} workOrders={workOrders} onSaveMeasurement={onSaveMeasurement} />
          )}
          {activeTab === 'activeTests' && (
            <ActiveTestsTab 
              currentUser={currentUser} 
              workOrders={workOrders} 
              onAddTimelineEvent={onAddTimelineEvent} 
            />
          )}
          {activeTab === 'serviceResets' && (
            <ServiceResetsTab 
              currentUser={currentUser} 
              workOrders={workOrders} 
              onAddTimelineEvent={onAddTimelineEvent} 
            />
          )}
        </div>

        <div className="bg-black/90 p-3 border-t border-white/5 text-center flex-shrink-0 flex items-center justify-between px-6">
          <p className="text-[9px] text-steel-500 font-mono uppercase tracking-wider mx-auto">
            Powered by MEET Engine AI · Análisis de Señales y Códigos OBD2 en Costa Rica
          </p>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// TAB 1: DTC ANALYZER (Modernized Visuals & Simulated Auto-Scanner)
// ═══════════════════════════════════════════════════════════════

interface DTCAnalyzerTabProps {
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onUpdateWorkOrder?: (id: string, updates: Partial<WorkOrder>) => void;
  onNavigateToManuals?: (dtcCode?: string) => void;
}

function DTCAnalyzerTab({ currentUser, workOrders, onUpdateWorkOrder, onNavigateToManuals }: DTCAnalyzerTabProps) {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<any>(null);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dtcDatabase, setDtcDatabase] = useState<DtcDatabaseItem[]>([]);
  const [dtcDbLoading, setDtcDbLoading] = useState(false);
  const [dtcDbError, setDtcDbError] = useState<string | null>(null);
  
  // Search state extensions
  const [suggestions, setSuggestions] = useState<any[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searchHistory, setSearchHistory] = useState<string[]>(['P0300', 'P0171', 'P0420']);
  const [searchResults, setSearchResults] = useState<any[]>([]); // For partial matches
  
  // Auto-Scan Simulation state
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [scanLogs, setScanLogs] = useState<string[]>([]);
  const [scannedCodes, setScannedCodes] = useState<string[]>([]);
  const [scanCompleted, setScanCompleted] = useState(false);
  
  // Telemetry stream state during scan
  const [liveTelemetry, setLiveTelemetry] = useState({
    rpm: 0,
    speed: 0,
    temp: 0,
    voltage: 0,
    load: 0,
    maf: 0
  });

  // Active Work Order link state
  const [selectedWOId, setSelectedWOId] = useState('');
  const [linkSuccess, setLinkSuccess] = useState(false);

  const ensureDtcDatabase = useCallback(async () => {
    if (dtcDatabase.length > 0) return dtcDatabase;

    setDtcDbLoading(true);
    setDtcDbError(null);
    try {
      const database = await loadDtcDatabase();
      setDtcDatabase(database);
      return database;
    } catch (error) {
      console.error('[MEET:DTC_DATABASE_LOAD]', error);
      setDtcDbError('No se pudo cargar la base de codigos DTC. Revisa conexion o assets del build.');
      return [];
    } finally {
      setDtcDbLoading(false);
    }
  }, [dtcDatabase]);

  useEffect(() => {
    ensureDtcDatabase();
  }, [ensureDtcDatabase]);

  // Suggestions Autocomplete Logic
  useEffect(() => {
    if (code.trim().length < 2) {
      setSuggestions([]);
      return;
    }
    const cleanQuery = code.trim().replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
    let cancelled = false;
    const timer = setTimeout(() => {
      ensureDtcDatabase().then(database => {
        if (cancelled) return;
        const matches: DtcDatabaseItem[] = [];
        for (const item of database) {
          const cleanItemCode = item.code.replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
          if (cleanItemCode.startsWith(cleanQuery)) {
            matches.push(item);
            if (matches.length >= 5) break;
          }
        }
        setSuggestions(matches);
      });
    }, 120);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [code, ensureDtcDatabase]);

  // Handle manual code search
  const handleSearch = (e?: React.FormEvent, searchCode?: string) => {
    if (e) e.preventDefault();
    const targetCode = (searchCode || code).trim().toUpperCase();
    const cleanCode = targetCode.replace(/[^a-zA-Z0-9]/g, '');
    if (!cleanCode) return;

    setSearched(true); 
    setLoading(true); 
    setResult(null);
    setSearchResults([]);
    setShowSuggestions(false);

    // Save to history
    setSearchHistory(prev => {
      const next = prev.filter(c => c !== targetCode);
      return [targetCode, ...next].slice(0, 5);
    });

    setTimeout(async () => {
      const database = await ensureDtcDatabase();
      // 1. Try exact match (punctuation insensitive)
      const data = database.find(dtc => dtc.code.replace(/[^a-zA-Z0-9]/g, '').toUpperCase() === cleanCode);
      
      if (data) {
        setResult({
          code: data.code,
          manufacturer: data.manufacturer,
          title: data.descriptionEs,
          desc: data.descriptionEn,
          fix: data.possibleCauses,
          system: data.system,
          severity: data.severity === 'HIGH' ? 'high' : data.severity === 'MODERATE' ? 'medium' : 'low'
        });
      } else {
        // 2. Try partial search (starts-with)
        const partialMatches = database.filter(dtc => 
          dtc.code.toUpperCase().includes(cleanCode) || 
          dtc.descriptionEs.toUpperCase().includes(cleanCode)
        ).slice(0, 8);
        setSearchResults(partialMatches);
      }
      setLoading(false);
    }, 500);
  };

  // Simulated ECU Scanner Logic
  const startEcuScan = () => {
    setIsScanning(true);
    setScanProgress(0);
    setScanLogs(['Iniciando comunicación con la ECU del vehículo...']);
    setScannedCodes([]);
    setScanCompleted(false);
    setResult(null);
    setSearched(false);

    const logsList = [
      'Conectando vía interfaz OBD-II ELM327... OK',
      'Detectando protocolo de comunicación: ISO 15765-4 (CAN)... OK',
      'Estableciendo canal serie a 500kbps... OK',
      'Escaneando Módulo de Control de Tren Motriz (PCM)...',
      'Leyendo códigos de falla activos en memoria... OK',
      'Escaneando Módulo de Control de Transmisión (TCM)...',
      'Chequeando sensores de revoluciones y embrague... OK',
      'Verificando sistema de Frenos Antibloqueo (ABS)...',
      'Comprobando sensores de velocidad de rueda... OK',
      'Escaneando Módulos de Carrocería (BCM) y Climatización... OK',
      'Verificando Bus de datos CAN-Gateway... OK',
      'Analizando historial de fallas pendientes...',
      'Filtrando códigos temporales de encendido en frío... OK',
      'Escaneo completado. Extrayendo reporte de averías DTC...'
    ];

    let progress = 0;
    const interval = setInterval(() => {
      progress += 5;
      setScanProgress(progress);

      // Random telemetry fluctuations
      setLiveTelemetry({
        rpm: Math.floor(Math.random() * 300) + 750,
        speed: Math.random() > 0.8 ? Math.floor(Math.random() * 20) : 0,
        temp: Math.floor(Math.random() * 2) + 92,
        voltage: +(Math.random() * 0.4 + 13.8).toFixed(2),
        load: Math.floor(Math.random() * 10) + 15,
        maf: +(Math.random() * 1.5 + 4.2).toFixed(2)
      });

      // Add logs sequentially based on progress
      const logIndex = Math.floor((progress / 100) * logsList.length);
      setScanLogs(prev => {
        const currentLogs = [...prev];
        const nextLog = logsList[logIndex];
        if (nextLog && !currentLogs.includes(nextLog)) {
          currentLogs.push(nextLog);
        }
        return currentLogs;
      });

      if (progress >= 100) {
        clearInterval(interval);
        setTimeout(() => {
          // Generate 2-3 random codes as active
          const demoCodes = ['P0300', 'P0171', 'P0420', 'P0102', 'P0700'];
          const count = Math.floor(Math.random() * 2) + 1; // 1 or 2 codes
          const shuffled = [...demoCodes].sort(() => 0.5 - Math.random());
          const found = shuffled.slice(0, count);
          
          setScannedCodes(found);
          setIsScanning(false);
          setScanCompleted(true);
        }, 300);
      }
    }, 180);
  };

  const handleClearCodes = () => {
    setLoading(true);
    setTimeout(() => {
      setScannedCodes([]);
      setScanCompleted(false);
      setResult(null);
      setSearched(false);
      setLoading(false);
      alert('✅ Todos los códigos de falla OBD2 han sido borrados de la ECU del vehículo y la luz MIL (Check Engine) se ha restablecido.');
    }, 1200);
  };

  // Link DTC code to Work Order
  const handleLinkDtcToWorkOrder = () => {
    if (!selectedWOId || !result || !onUpdateWorkOrder) return;
    
    const wo = workOrders?.find(w => w.id === selectedWOId);
    if (!wo) return;

    const currentNotes = wo.diagnosticNotes || wo.notes || '';
    const newNote = `[DTC Detectado: ${result.code}] - ${result.title} (${result.severity === 'high' ? 'Crítico' : 'Moderado'}). Posibles Causas: ${result.fix}`;
    const updatedNotes = currentNotes ? `${currentNotes}\n\n${newNote}` : newNote;

    onUpdateWorkOrder(selectedWOId, {
      diagnosticNotes: updatedNotes,
      status: wo.status === 'RECEIVED' ? 'DIAGNOSED' as any : wo.status
    });

    setLinkSuccess(true);
    setTimeout(() => setLinkSuccess(false), 3000);
  };

  const activeWorkOrders = workOrders?.filter(wo => 
    wo.status !== 'COMPLETED' && 
    wo.status !== 'DELIVERED' && 
    wo.status !== 'CANCELLED'
  ) || [];

  return (
    <div className="p-4 sm:p-6 max-w-6xl mx-auto">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* COLUMNA IZQUIERDA: CONTROLES & ESCANER */}
        <div className="lg:col-span-5 space-y-5">
          
          {/* Panel de Busqueda Manual */}
          <div className="glass rounded-2xl p-4 border border-white/10 shadow-lg relative">
            <h3 className="text-xs font-black text-white uppercase tracking-widest font-mono mb-3 flex items-center gap-2">
              <Search size={14} className="text-forge-500" />
              Búsqueda Manual de Códigos
            </h3>
            {dtcDbError && (
              <div className="mb-3 rounded-xl border border-red-500/25 bg-red-500/10 px-3 py-2 text-[11px] text-red-200">
                {dtcDbError}
              </div>
            )}
            
            <form onSubmit={(e) => handleSearch(e)} className="relative">
              <div className="relative">
                <input 
                  type="text" 
                  value={code} 
                  onChange={e => { setCode(e.target.value); setShowSuggestions(true); }}
                  onFocus={() => setShowSuggestions(true)}
                  placeholder={dtcDbLoading ? 'Cargando base DTC...' : 'Ingrese código (ej: P0300)'} 
                  className="w-full bg-steel-950 border border-steel-700 focus:border-forge-500 rounded-xl pl-4 pr-12 py-3 text-sm font-bold text-white uppercase outline-none transition-all placeholder:text-steel-600 font-mono tracking-wider"
                />
                <button 
                  type="submit" 
                  disabled={loading || isScanning}
                  className="absolute right-1 top-1/2 -translate-y-1/2 bg-forge-500 hover:bg-forge-400 text-black p-2 rounded-lg font-bold transition-all disabled:opacity-50"
                >
                  <Search size={16} />
                </button>
              </div>

              {/* Autocomplete Dropdown */}
              {showSuggestions && suggestions.length > 0 && (
                <div className="absolute left-0 right-0 mt-2 bg-steel-950 border border-forge-500/30 rounded-xl overflow-hidden shadow-2xl z-50 divide-y divide-white/5 animate-slide-up">
                  {suggestions.map((item, idx) => (
                    <button
                      key={idx}
                      type="button"
                      onClick={() => {
                        setCode(item.code);
                        setShowSuggestions(false);
                        handleSearch(undefined, item.code);
                      }}
                      className="w-full text-left px-4 py-2.5 hover:bg-white/5 transition-colors flex items-center justify-between"
                    >
                      <span className="font-mono font-bold text-white text-xs tracking-wider">{item.code}</span>
                      <span className="text-[10px] text-steel-400 font-medium truncate max-w-[200px]">{item.descriptionEs}</span>
                    </button>
                  ))}
                </div>
              )}
            </form>

            {/* Quick Suggestions / Common Codes */}
            <div className="mt-4">
              <div className="text-[9px] text-steel-500 font-mono uppercase tracking-wider mb-2">Códigos Comunes:</div>
              <div className="flex flex-wrap gap-1.5">
                {['P0300', 'P0171', 'P0420', 'P0102', 'P0700'].map(commonCode => (
                  <button 
                    key={commonCode}
                    onClick={() => { setCode(commonCode); handleSearch(undefined, commonCode); }}
                    className="text-[10px] font-mono font-bold bg-steel-800/80 hover:bg-forge-500/20 hover:text-forge-400 border border-white/5 hover:border-forge-500/30 text-steel-300 px-2.5 py-1 rounded-md transition-all"
                  >
                    {commonCode}
                  </button>
                ))}
              </div>
            </div>

            {/* Search History */}
            {searchHistory.length > 0 && (
              <div className="mt-3.5 pt-3.5 border-t border-white/5">
                <div className="text-[9px] text-steel-500 font-mono uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <History size={10} />
                  Búsquedas Recientes:
                </div>
                <div className="flex flex-wrap gap-1">
                  {searchHistory.map((hist, i) => (
                    <button
                      key={i}
                      onClick={() => { setCode(hist); handleSearch(undefined, hist); }}
                      className="text-[9px] font-mono bg-black/40 text-steel-400 px-2 py-0.5 rounded border border-white/5 hover:text-white transition-all"
                    >
                      {hist}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Panel de Auto-Escaneo de ECU */}
          <div className="glass rounded-2xl p-5 border border-white/10 shadow-lg bg-gradient-to-tr from-steel-950/80 to-steel-900/50 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-forge-500/5 blur-3xl rounded-full"></div>
            
            <div className="flex justify-between items-center mb-4">
              <div>
                <h3 className="text-sm font-black text-white uppercase tracking-wider">Simulador OBD-II en Bahía</h3>
                <p className="text-[10px] text-steel-400 mt-0.5">Diagnostique fallas activas directamente en el taller</p>
              </div>
              <Activity size={18} className="text-forge-500 animate-pulse" />
            </div>

            {!isScanning && !scanCompleted ? (
              <button
                onClick={startEcuScan}
                className="w-full bg-forge-500 hover:bg-forge-400 text-black font-black py-3 rounded-xl text-xs uppercase tracking-widest flex items-center justify-center gap-2 shadow-[0_0_20px_rgba(0,240,255,0.25)] transition-all hover:scale-[1.02]"
              >
                <Sparkles size={14} className="animate-spin text-black" />
                Iniciar Auto-Escaneo de ECU
              </button>
            ) : isScanning ? (
              <div className="space-y-4">
                <div className="flex justify-between items-center font-mono text-xs">
                  <span className="text-forge-400 font-bold animate-pulse">Escaneando ECU...</span>
                  <span className="text-white font-bold">{scanProgress}%</span>
                </div>
                {/* Progress bar */}
                <div className="h-2 w-full bg-steel-950 rounded-full overflow-hidden border border-white/5">
                  <div className="h-full bg-forge-500 rounded-full transition-all duration-300 shadow-[0_0_10px_rgba(0,240,255,0.5)]" style={{ width: `${scanProgress}%` }}></div>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex gap-2">
                  <button
                    onClick={startEcuScan}
                    className="flex-1 bg-white/5 hover:bg-white/10 border border-white/10 text-white font-bold py-2.5 rounded-lg text-[11px] uppercase tracking-wider transition-all"
                  >
                    Repetir Escaneo
                  </button>
                  <button
                    onClick={handleClearCodes}
                    className="flex-1 bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-400 font-bold py-2.5 rounded-lg text-[11px] uppercase tracking-wider transition-all"
                  >
                    Borrar Códigos
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* COLUMNA DERECHA: RESULTADO DE DIAGNÓSTICO O PANTALLA DE SIMULACIÓN */}
        <div className="lg:col-span-7">
          
          {/* MIENTRAS ESCANEA: Mostrar terminal con logs y telemetria en vivo */}
          {isScanning && (
            <div className="glass rounded-3xl p-5 border border-forge-500/20 bg-black/95 shadow-inner animate-fade-in flex flex-col min-h-[350px]">
              <div className="flex items-center justify-between border-b border-white/10 pb-3 mb-4">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-red-500 animate-ping"></span>
                  <span className="font-mono text-xs text-red-400 font-bold">LECTURA ECU EN VIVO</span>
                </div>
                <span className="text-[10px] text-steel-500 font-mono">OBD-II DATASTREAM</span>
              </div>
              
              {/* Live telemetry numbers */}
              <div className="grid grid-cols-3 gap-2 mb-4">
                {[
                  { label: 'RPM', val: liveTelemetry.rpm, unit: 'rpm' },
                  { label: 'VELOCIDAD', val: liveTelemetry.speed, unit: 'km/h' },
                  { label: 'TEMP REFR.', val: liveTelemetry.temp, unit: '°C' },
                  { label: 'VOLTAJE', val: liveTelemetry.voltage, unit: 'V' },
                  { label: 'CARGA MOTOR', val: liveTelemetry.load, unit: '%' },
                  { label: 'MAF', val: liveTelemetry.maf, unit: 'g/s' }
                ].map((item, idx) => (
                  <div key={idx} className="bg-steel-900/50 p-2.5 rounded-xl border border-white/5 text-center">
                    <div className="text-[7px] text-steel-500 font-mono uppercase">{item.label}</div>
                    <div className="text-xs font-mono font-bold text-forge-400 mt-0.5">{item.val}<span className="text-[9px] text-steel-500 ml-0.5">{item.unit}</span></div>
                  </div>
                ))}
              </div>

              {/* Terminal Logs */}
              <div className="flex-1 bg-black/80 rounded-xl p-4 border border-steel-800 font-mono text-[10px] text-emerald-400 space-y-1.5 overflow-y-auto max-h-[180px] shadow-inner select-none">
                {scanLogs.map((log, i) => (
                  <div key={i} className="flex items-start gap-1">
                    <span className="text-emerald-600 shrink-0">&gt;</span>
                    <span>{log}</span>
                  </div>
                ))}
                <div className="w-1.5 h-3 bg-emerald-400 inline-block animate-pulse ml-1"></div>
              </div>
            </div>
          )}

          {/* ESCANEO COMPLETADO: Listar códigos encontrados */}
          {!isScanning && scanCompleted && (
            <div className="glass rounded-3xl p-5 border border-white/10 shadow-lg animate-fade-in space-y-5">
              <div className="flex justify-between items-center border-b border-white/5 pb-3">
                <div className="flex items-center gap-2">
                  <CheckCircle2 size={16} className="text-green-400" />
                  <span className="font-bold text-white text-sm">REPORTE DE AUTO-ESCANEO</span>
                </div>
                <span className="text-[10px] text-steel-400 font-mono">Finalizado hace unos instantes</span>
              </div>

              {scannedCodes.length === 0 ? (
                <div className="text-center py-10 bg-green-500/5 border border-green-500/20 rounded-2xl">
                  <CheckCircle2 size={40} className="mx-auto text-green-400 mb-3" />
                  <h4 className="text-white font-bold text-base">ECU Limpia - Sin códigos de falla</h4>
                  <p className="text-xs text-steel-400 mt-1 max-w-sm mx-auto">No se han detectado códigos de avería (DTC) activos en la unidad de control.</p>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="bg-red-500/5 border border-red-500/20 p-4 rounded-xl flex items-start gap-3">
                    <AlertTriangle className="text-red-400 shrink-0 mt-0.5" size={18} />
                    <div>
                      <h4 className="text-white font-bold text-sm">Códigos de Falla Detectados ({scannedCodes.length})</h4>
                      <p className="text-[11px] text-steel-400 mt-0.5">Se encontraron averías activas. Haga clic en cualquiera de los códigos para ver el análisis de diagnóstico completo.</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    {scannedCodes.map(c => {
                      // Lookup to find descriptions
                      const dbItem = dtcDatabase.find(item => item.code === c);
                      const isHigh = dbItem?.severity === 'HIGH';
                      return (
                        <button
                          key={c}
                          onClick={() => { setCode(c); handleSearch(undefined, c); }}
                          className={`flex flex-col text-left p-4 rounded-2xl border transition-all hover:scale-[1.02] ${
                            isHigh 
                              ? 'bg-red-500/5 hover:bg-red-500/10 border-red-500/30 hover:border-red-500/60 shadow-[0_0_15px_rgba(239,68,68,0.05)]' 
                              : 'bg-yellow-500/5 hover:bg-yellow-500/10 border-yellow-500/30 hover:border-yellow-500/60'
                          }`}
                        >
                          <div className="flex justify-between items-center w-full mb-1">
                            <span className="font-mono font-black text-lg text-white tracking-wider">{c}</span>
                            <span className={`text-[8px] font-black uppercase tracking-wider px-2 py-0.5 rounded ${
                              isHigh ? 'bg-red-500/20 text-red-400' : 'bg-yellow-500/20 text-yellow-400'
                            }`}>
                              {isHigh ? 'Crítico' : 'Moderado'}
                            </span>
                          </div>
                          <span className="text-[11px] text-gray-300 font-bold truncate w-full">{dbItem?.descriptionEs || 'Avería OBD2'}</span>
                          <span className="text-[9px] text-steel-500 font-mono mt-2 flex items-center gap-1">
                            Ver detalles & soluciones &rarr;
                          </span>
                          {onNavigateToManuals && (
                            <span
                              className="text-[9px] text-indigo-400 font-mono mt-1.5 flex items-center gap-1 hover:text-indigo-300 transition-colors"
                              onClick={(e) => { e.stopPropagation(); onNavigateToManuals(c); }}
                            >
                              📖 Consultar Manual Técnico
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* MOSTRAR EL RESULTADO DE UN DTC ESPECÍFICO */}
          {!isScanning && searched && (
            <div className="animate-fade-in space-y-4">
              {result ? (
                <div className={`glass rounded-3xl p-5 border-l-4 border transition-all ${
                  result.severity === 'high' 
                    ? 'bg-red-500/5 border-red-500 border-t-white/10 border-b-white/10 border-r-white/10 shadow-[0_8px_32px_rgba(239,68,68,0.1)]' 
                    : result.severity === 'medium' 
                      ? 'bg-yellow-500/5 border-yellow-500 border-t-white/10 border-b-white/10 border-r-white/10' 
                      : 'bg-blue-500/5 border-blue-500 border-t-white/10 border-b-white/10 border-r-white/10'
                }`}>
                  
                  {/* Result Header */}
                  <div className="flex justify-between items-start mb-4 border-b border-white/5 pb-3">
                    <div className="flex items-center gap-3">
                      <div className={`p-2.5 rounded-xl border ${
                        result.severity === 'high' ? 'bg-red-500/10 border-red-500/30 text-red-400' : 'bg-forge-500/10 border-forge-500/30 text-forge-400'
                      }`}>
                        {result.severity === 'high' ? <ShieldAlert size={20} /> : <Info size={20} />}
                      </div>
                      <div>
                        <h3 className="text-2xl font-black text-white font-mono tracking-wider">{result.code}</h3>
                        <p className="text-[9px] text-steel-400 font-mono uppercase mt-0.5">Sistema: {result.system} • Fabricante: {result.manufacturer || 'Estándar Genérico'}</p>
                        {onNavigateToManuals && (
                          <button
                            onClick={() => onNavigateToManuals(result.code)}
                            className="mt-2 flex items-center gap-1.5 text-[10px] font-mono text-indigo-400 hover:text-indigo-300 bg-indigo-500/10 hover:bg-indigo-500/15 border border-indigo-500/20 rounded-lg px-3 py-1.5 transition-all"
                          >
                            📖 Consultar en Centro de Manuales Técnicos
                          </button>
                        )}
                      </div>
                    </div>
                    <span className={`px-2.5 py-1 rounded-lg text-[9px] font-black uppercase tracking-widest ${
                      result.severity === 'high' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                      result.severity === 'medium' ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30' :
                      'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                    }`}>
                      {result.severity === 'high' ? 'Crítico (MIL)' : result.severity === 'medium' ? 'Moderado' : 'Leve'}
                    </span>
                  </div>

                  {/* Descriptions */}
                  <div className="space-y-3">
                    <div>
                      <div className="text-[8px] text-steel-500 font-mono uppercase tracking-widest mb-1">Descripción del Fallo (ES)</div>
                      <h4 className="text-base font-bold text-white leading-snug">{result.title}</h4>
                    </div>
                    {result.desc && (
                      <div>
                        <div className="text-[8px] text-steel-500 font-mono uppercase tracking-widest mb-1">Technical Reference (EN)</div>
                        <p className="text-xs text-steel-400 leading-relaxed font-medium">{result.desc}</p>
                      </div>
                    )}
                  </div>

                  {/* Possible Causes */}
                  <div className="mt-4 pt-4 border-t border-white/5 space-y-2">
                    <h5 className="text-[10px] text-forge-500 font-mono uppercase tracking-widest flex items-center gap-1.5">
                      <AlertCircle size={12} />
                      Posibles Causas de la Avería
                    </h5>
                    <div className="bg-black/35 rounded-xl p-3 border border-white/5 text-xs text-steel-300 leading-relaxed whitespace-pre-line font-medium">
                      {result.fix}
                    </div>
                  </div>

                  {/* Wrench Box */}
                  <div className="mt-4 bg-forge-500/10 rounded-2xl p-4 border border-forge-500/20 flex gap-3">
                    <div className="bg-forge-500/20 p-2 rounded-xl text-forge-500 border border-forge-500/30 shrink-0 h-10 w-10 flex items-center justify-center">
                      <Wrench size={18} />
                    </div>
                    <div>
                      <h5 className="text-xs font-bold text-white uppercase tracking-wider">Plan de Inspección MEET</h5>
                      <p className="text-xs text-steel-200 mt-1">Verifique visualmente conectores, cableado corroído y mangueras de vacío antes de reemplazar sensores. Escanee componentes en tiempo real con osciloscopio si el problema persiste.</p>
                    </div>
                  </div>

                  {/* Vincular a Orden de Trabajo Panel */}
                  {onUpdateWorkOrder && activeWorkOrders.length > 0 && (
                    <div className="mt-4 pt-4 border-t border-white/5 space-y-2">
                      <h5 className="text-[10px] text-steel-400 font-mono uppercase tracking-widest flex items-center gap-1.5">
                        <Link2 size={12} />
                        Vincular Diagnóstico a Cita Activa
                      </h5>
                      <div className="flex gap-2">
                        <select
                          value={selectedWOId}
                          onChange={e => setSelectedWOId(e.target.value)}
                          className="flex-1 bg-steel-950 border border-steel-700 rounded-lg px-3 py-2 text-white text-xs font-bold focus:outline-none focus:border-forge-500"
                        >
                          <option value="">Seleccione una orden de trabajo...</option>
                          {activeWorkOrders.map(wo => (
                            <option key={wo.id} value={wo.id}>
                              {wo.clientName.split(' ')[0]} - {wo.vehicleInfo.plate} ({wo.vehicleInfo.brand})
                            </option>
                          ))}
                        </select>
                        <button
                          onClick={handleLinkDtcToWorkOrder}
                          disabled={!selectedWOId}
                          className="bg-forge-500 hover:bg-forge-400 disabled:opacity-40 text-black font-bold px-4 py-2 rounded-lg text-xs transition-colors shrink-0 flex items-center gap-1.5"
                        >
                          <Link2 size={12} />
                          Vincular
                        </button>
                      </div>
                      {linkSuccess && (
                        <div className="text-[10px] text-green-400 font-bold font-mono animate-fade-in">
                          ✅ Código {result.code} adjuntado con éxito a la orden de trabajo.
                        </div>
                      )}
                    </div>
                  )}

                </div>
              ) : (
                <div className="text-center py-12 glass rounded-3xl border border-steel-800 flex flex-col items-center">
                  <AlertTriangle size={48} className="text-steel-600 mb-4 opacity-50" />
                  <h4 className="text-white font-bold text-lg">Código no encontrado</h4>
                  <p className="text-steel-400 text-xs mt-1 max-w-sm">No existe una coincidencia exacta para "{code}". Compruebe la sintaxis o pruebe un código diferente.</p>
                </div>
              )}

              {/* Partial search matches list */}
              {searchResults.length > 0 && (
                <div className="glass rounded-3xl p-5 border border-white/5 space-y-3.5">
                  <h5 className="text-[10px] text-steel-400 font-mono uppercase tracking-wider">Códigos Similares Encontrados:</h5>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {searchResults.map(match => (
                      <button
                        key={match.code}
                        onClick={() => { setCode(match.code); handleSearch(undefined, match.code); }}
                        className="text-left p-3 bg-white/5 hover:bg-white/10 rounded-xl border border-white/5 transition-colors flex justify-between items-center group"
                      >
                        <div>
                          <div className="font-mono font-bold text-xs text-white group-hover:text-forge-400 transition-colors">{match.code}</div>
                          <div className="text-[10px] text-steel-400 mt-0.5 truncate max-w-[200px]">{match.descriptionEs}</div>
                        </div>
                        <ChevronDown size={14} className="text-steel-500 -rotate-90" />
                      </button>
                    ))}
                  </div>
                </div>
              )}

            </div>
          )}

          {/* VISTA POR DEFECTO: Radar animado y ciber shield */}
          {!isScanning && !scanCompleted && !searched && (
            <div className="glass rounded-3xl p-10 border border-white/5 shadow-lg flex flex-col items-center justify-center text-center min-h-[350px] relative overflow-hidden bg-gradient-to-b from-steel-900/50 to-transparent">
              {/* Radial scanning ring */}
              <div className="relative w-28 h-28 mb-6 flex items-center justify-center">
                <div className="absolute inset-0 rounded-full border border-forge-500/20 animate-ping"></div>
                <div className="absolute inset-2 rounded-full border border-forge-500/10 animate-pulse-slow"></div>
                <div className="w-16 h-16 rounded-full bg-forge-500/10 border-2 border-forge-500/30 flex items-center justify-center text-forge-500 shadow-[0_0_20px_rgba(0,240,255,0.15)]">
                  <Cpu size={28} className="animate-pulse" />
                </div>
              </div>

              <h3 className="text-lg font-bold text-white tracking-wide">Listo para Diagnóstico OBD-II</h3>
              <p className="text-xs text-steel-400 mt-2 max-w-sm leading-relaxed">
                Inicie el escaneo automático para inspeccionar los sensores de la ECU en tiempo real, o ingrese manualmente un código de falla DTC para consultar causas y reparaciones sugeridas.
              </p>

              {/* Quick info list */}
              <div className="mt-6 flex gap-4 text-[10px] text-steel-500 font-mono">
                <span className="flex items-center gap-1"><CheckCircle2 size={10} className="text-forge-500" /> Bluetooth ELM327 listo</span>
                <span className="flex items-center gap-1"><CheckCircle2 size={10} className="text-forge-500" /> Protocolo CAN-Bus online</span>
              </div>
            </div>
          )}

        </div>

      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// TAB 2: PROFESSIONAL OSCILLOSCOPE
// ═══════════════════════════════════════════════════════════════

function OscilloscopeTab({ currentUser, workOrders, onSaveMeasurement }: {
  currentUser?: Client; workOrders?: WorkOrder[]; onSaveMeasurement?: (m: OscilloscopeMeasurement) => void;
}) {
  const [selectedSignal, setSelectedSignal] = useState<SignalDefinition>(SIGNAL_LIBRARY[0]);
  const [isRunning, setIsRunning] = useState(false);
  const [dataBuffer, setDataBuffer] = useState<number[]>([]);
  const [timeDiv, setTimeDiv] = useState(50);
  const [triggerLevel, setTriggerLevel] = useState(0);
  const [showSignalPicker, setShowSignalPicker] = useState(false);
  const [diagnosis, setDiagnosis] = useState<SignalDiagnosis | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [savedMsg, setSavedMsg] = useState('');

  const generatorRef = useRef<SignalGenerator | null>(null);
  const analyzerRef = useRef(new SignalAnalyzer());
  const startTimeRef = useRef(0);
  const intervalRef = useRef<number>(0);
  const maxPoints = 400;

  // Initialize generator when signal changes
  useEffect(() => {
    generatorRef.current = new SignalGenerator(selectedSignal);
    generatorRef.current.setNoiseLevel(0.03);
    setDataBuffer([]);
    setDiagnosis(null);
    setTriggerLevel((selectedSignal.minNominal + selectedSignal.maxNominal) / 2);
  }, [selectedSignal]);

  const startCapture = useCallback(() => {
    if (!generatorRef.current) return;
    setDataBuffer([]);
    setDiagnosis(null);
    setSavedMsg('');
    startTimeRef.current = performance.now();
    setIsRunning(true);

    intervalRef.current = window.setInterval(() => {
      const elapsed = (performance.now() - startTimeRef.current) / 1000;
      const value = generatorRef.current!.generate(elapsed);
      setDataBuffer(prev => {
        const next = [...prev, value];
        return next.length > maxPoints ? next.slice(-maxPoints) : next;
      });
    }, timeDiv);
  }, [timeDiv]);

  const stopCapture = useCallback(() => {
    clearInterval(intervalRef.current);
    setIsRunning(false);
    // Auto-analyze on stop
    if (dataBuffer.length > 20) {
      setIsAnalyzing(true);
      setTimeout(() => {
        const result = analyzerRef.current.analyze(dataBuffer, dataBuffer.length * timeDiv, selectedSignal);
        setDiagnosis(result);
        setIsAnalyzing(false);
      }, 800);
    }
  }, [dataBuffer, timeDiv, selectedSignal]);

  useEffect(() => { return () => clearInterval(intervalRef.current); }, []);

  const handleSave = () => {
    if (!diagnosis || !onSaveMeasurement) return;
    const activeWO = workOrders?.find(wo => wo.status === 'IN_PROGRESS' || wo.status === 'DIAGNOSED');
    const measurement: OscilloscopeMeasurement = {
      id: `osc-${Date.now()}`,
      timestamp: new Date(),
      signalType: selectedSignal.id,
      signalName: selectedSignal.nameEs,
      pidCode: selectedSignal.pidCode,
      durationMs: diagnosis.metrics.durationMs,
      sampleCount: diagnosis.metrics.sampleCount,
      metrics: {
        frequency: diagnosis.metrics.frequency,
        amplitude: diagnosis.metrics.amplitude,
        vpp: diagnosis.metrics.vpp,
        rms: diagnosis.metrics.rms,
        thd: diagnosis.metrics.thd,
        dutyCycle: diagnosis.metrics.dutyCycle,
        mean: diagnosis.metrics.mean,
        min: diagnosis.metrics.min,
        max: diagnosis.metrics.max,
        stability: diagnosis.metrics.stability,
        noiseLevel: diagnosis.metrics.noiseLevel,
      },
      diagnosis: diagnosis.diagnosisText,
      recommendation: diagnosis.recommendationText,
      severity: diagnosis.overallSeverity,
      confidenceScore: diagnosis.confidenceScore,
      vehiclePlate: currentUser?.vehicles?.[0]?.plate,
      workOrderId: activeWO?.id,
      waveformSnapshot: dataBuffer.length > 200
        ? dataBuffer.filter((_, i) => i % Math.ceil(dataBuffer.length / 200) === 0)
        : [...dataBuffer],
    };
    onSaveMeasurement(measurement);
    setSavedMsg('✅ Medición guardada');
    setTimeout(() => setSavedMsg(''), 3000);
  };

  const severityColor = (s: string) => s === 'critical' ? '#ef4444' : s === 'warning' ? '#f59e0b' : '#22c55e';
  const severityLabel = (s: string) => s === 'critical' ? 'CRÍTICO' : s === 'warning' ? 'ATENCIÓN' : 'NOMINAL';

  return (
    <div className="p-4 space-y-4">
      {/* Signal Selector & Controls */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative">
          <button onClick={() => setShowSignalPicker(!showSignalPicker)}
            className="flex items-center gap-2 bg-black border border-forge-500/30 rounded-lg px-3 py-2 text-sm text-white hover:border-forge-500 transition-all">
            <Radio size={14} className="text-forge-500" />
            <span className="font-bold">{selectedSignal.nameEs}</span>
            <span className="text-steel-400 text-xs font-mono">({selectedSignal.pidCode})</span>
            <ChevronDown size={14} className="text-steel-400" />
          </button>
          {showSignalPicker && (
            <div className="absolute top-full left-0 mt-1 bg-steel-900 border border-forge-500/30 rounded-xl shadow-2xl z-50 w-72 max-h-64 overflow-y-auto">
              {SIGNAL_LIBRARY.map(sig => (
                <button key={sig.id} onClick={() => { setSelectedSignal(sig); setShowSignalPicker(false); if (isRunning) { clearInterval(intervalRef.current); setIsRunning(false); } }}
                  className={`w-full text-left px-4 py-2.5 hover:bg-white/5 transition-colors border-b border-white/5 last:border-0 ${selectedSignal.id === sig.id ? 'bg-forge-500/10' : ''}`}>
                  <div className="flex justify-between items-center">
                    <span className="text-white text-sm font-bold">{sig.nameEs}</span>
                    <span className="text-forge-500 text-[10px] font-mono">{sig.pidCode}</span>
                  </div>
                  <div className="text-steel-400 text-[10px] mt-0.5">{sig.unit} · {sig.category}</div>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex items-center gap-1 bg-black/50 rounded-lg border border-white/10 px-2 py-1">
          <span className="text-[9px] text-steel-400 font-mono">T/DIV</span>
          {[20, 50, 100, 200].map(t => (
            <button key={t} onClick={() => setTimeDiv(t)} className={`px-2 py-1 text-[10px] font-mono rounded ${timeDiv === t ? 'bg-forge-500 text-black font-bold' : 'text-steel-400 hover:text-white'}`}>
              {t}ms
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2 ml-auto">
          {!isRunning ? (
            <button onClick={startCapture} className="flex items-center gap-2 px-4 py-2 bg-green-500 text-black font-bold rounded-lg hover:bg-green-400 transition-all text-sm shadow-[0_0_20px_rgba(34,197,94,0.3)]">
              <Play size={16} /> INICIAR
            </button>
          ) : (
            <button onClick={stopCapture} className="flex items-center gap-2 px-4 py-2 bg-red-500 text-white font-bold rounded-lg hover:bg-red-400 transition-all text-sm animate-pulse shadow-[0_0_20px_rgba(239,68,68,0.3)]">
              <Square size={16} /> DETENER
            </button>
          )}
          {diagnosis && onSaveMeasurement && (
            <button onClick={handleSave} className="flex items-center gap-2 px-3 py-2 bg-forge-500/20 text-forge-400 font-bold rounded-lg hover:bg-forge-500/30 transition-all text-sm border border-forge-500/30">
              <Save size={14} /> Guardar
            </button>
          )}
        </div>
      </div>

      {savedMsg && <div className="text-center text-green-400 text-sm font-bold animate-fade-in">{savedMsg}</div>}

      {/* Canvas Viewport */}
      <div className="bg-black rounded-xl border border-forge-500/20 overflow-hidden shadow-[inset_0_0_30px_rgba(0,0,0,0.5)]" style={{ height: '300px' }}>
        <OscilloscopeCanvas
          data={dataBuffer}
          isRunning={isRunning}
          timeDiv={timeDiv}
          voltsDiv={1}
          triggerLevel={triggerLevel}
          color="rgb(0,255,100)"
          showGrid={true}
          signalUnit={selectedSignal.unit}
          minNominal={selectedSignal.minNominal}
          maxNominal={selectedSignal.maxNominal}
        />
      </div>

      {/* Live Metrics Bar */}
      {dataBuffer.length > 1 && (
        <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
          {[
            { label: 'ACTUAL', value: dataBuffer[dataBuffer.length - 1]?.toFixed(1) || '—', unit: selectedSignal.unit },
            { label: 'MIN', value: Math.min(...dataBuffer).toFixed(1), unit: selectedSignal.unit },
            { label: 'MAX', value: Math.max(...dataBuffer).toFixed(1), unit: selectedSignal.unit },
            { label: 'MEDIA', value: (dataBuffer.reduce((a, b) => a + b, 0) / dataBuffer.length).toFixed(1), unit: selectedSignal.unit },
            { label: 'Vpp', value: (Math.max(...dataBuffer) - Math.min(...dataBuffer)).toFixed(2), unit: selectedSignal.unit },
            { label: 'MUESTRAS', value: `${dataBuffer.length}`, unit: '' },
          ].map((m, i) => (
            <div key={i} className="bg-black/50 rounded-lg p-2 border border-white/5 text-center">
              <div className="text-[8px] text-steel-500 font-mono uppercase tracking-wider">{m.label}</div>
              <div className="text-white font-bold text-sm font-mono">{m.value}<span className="text-steel-400 text-[9px] ml-0.5">{m.unit}</span></div>
            </div>
          ))}
        </div>
      )}

      {/* Analysis Panel */}
      {isAnalyzing && (
        <div className="bg-blue-500/10 border border-blue-500/30 rounded-xl p-4 animate-fade-in">
          <div className="flex items-center gap-3">
            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-forge-500"></div>
            <span className="text-white font-bold text-sm">Analizando armónicos, THD y patrones de anomalía...</span>
          </div>
        </div>
      )}

      {diagnosis && !isAnalyzing && (
        <div className={`rounded-xl border-l-4 p-4 animate-fade-in`} style={{ borderLeftColor: severityColor(diagnosis.overallSeverity), backgroundColor: `${severityColor(diagnosis.overallSeverity)}10` }}>
          <div className="flex justify-between items-start mb-3">
            <div className="flex items-center gap-2">
              <BarChart3 size={18} style={{ color: severityColor(diagnosis.overallSeverity) }} />
              <span className="font-bold text-white text-sm">RESULTADO DEL ANÁLISIS</span>
            </div>
            <span className="px-2 py-1 rounded text-[9px] font-bold uppercase tracking-wider" style={{ backgroundColor: `${severityColor(diagnosis.overallSeverity)}20`, color: severityColor(diagnosis.overallSeverity) }}>
              {severityLabel(diagnosis.overallSeverity)} · {diagnosis.confidenceScore.toFixed(0)}% confianza
            </span>
          </div>

          <pre className="text-steel-200 text-xs font-mono whitespace-pre-wrap leading-relaxed mb-3">{diagnosis.diagnosisText}</pre>

          {/* Metrics Grid */}
          <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 mb-3">
            {[
              { l: 'Frecuencia', v: `${diagnosis.metrics.frequency.toFixed(1)} Hz` },
              { l: 'RMS', v: `${diagnosis.metrics.rms.toFixed(2)}` },
              { l: 'THD', v: `${(diagnosis.metrics.thd * 100).toFixed(1)}%` },
              { l: 'Duty Cycle', v: `${diagnosis.metrics.dutyCycle.toFixed(0)}%` },
              { l: 'Estabilidad', v: `${diagnosis.metrics.stability.toFixed(0)}%` },
              { l: 'Ruido', v: `${(diagnosis.metrics.noiseLevel * 100).toFixed(0)}%` },
            ].map((m, i) => (
              <div key={i} className="bg-black/30 rounded p-2 text-center border border-white/5">
                <div className="text-[8px] text-steel-500 font-mono uppercase">{m.l}</div>
                <div className="text-white font-bold text-xs font-mono">{m.v}</div>
              </div>
            ))}
          </div>

          {/* Recommendation */}
          <div className="bg-black/30 rounded-lg p-3 border border-white/5">
            <h5 className="text-[9px] text-forge-500 font-mono uppercase tracking-widest mb-1 flex items-center gap-1">
              <Wrench size={10} /> Recomendación Técnica
            </h5>
            <p className="text-white text-xs">{diagnosis.recommendationText}</p>
          </div>

          {/* Anomalies */}
          {diagnosis.anomalies.length > 0 && (
            <div className="mt-3 space-y-1">
              <h5 className="text-[9px] text-steel-400 font-mono uppercase tracking-widest">Anomalías Detectadas ({diagnosis.anomalies.length})</h5>
              {diagnosis.anomalies.map((a, i) => (
                <div key={i} className="flex items-start gap-2 text-xs">
                  <span className={`mt-0.5 w-2 h-2 rounded-full flex-shrink-0 ${a.severity === 'critical' ? 'bg-red-500' : 'bg-yellow-500'}`}></span>
                  <span className="text-steel-300">{a.description}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Signal Description */}
      <div className="bg-black/30 rounded-lg p-3 border border-white/5">
        <div className="flex items-center gap-2 mb-1">
          <Zap size={12} className="text-forge-500" />
          <span className="text-[9px] text-forge-500 font-mono uppercase tracking-widest">{selectedSignal.nameEs} — Info</span>
        </div>
        <p className="text-steel-400 text-xs leading-relaxed">{selectedSignal.description}</p>
        <div className="flex gap-4 mt-2 text-[10px] text-steel-500 font-mono">
          <span>Rango: {selectedSignal.minNominal}–{selectedSignal.maxNominal} {selectedSignal.unit}</span>
          <span>Tipo: {selectedSignal.waveformType}</span>
          <span>Sistema: {selectedSignal.category}</span>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// TAB 3: PRUEBAS ACTIVAS (Bidirectional Controls)
// ═══════════════════════════════════════════════════════════════

interface ActiveTestsTabProps {
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onAddTimelineEvent?: (ev: any) => void;
}

export function ActiveTestsTab({ currentUser, workOrders, onAddTimelineEvent }: ActiveTestsTabProps) {
  const [selectedCap, setSelectedCap] = useState<BidirectionalCapability | null>(null);
  const [currentStep, setCurrentStep] = useState(1);
  const [understandRisk, setUnderstandRisk] = useState(false);
  const [holdProgress, setHoldProgress] = useState(0);
  const [isHolding, setIsHolding] = useState(false);
  const [executionLogs, setExecutionLogs] = useState<string[]>([]);
  const [actionStatus, setActionStatus] = useState<ActionStatus>('CREATED');
  const [executionResult, setExecutionResult] = useState<ExecutionResult | null>(null);
  const [ecuErrorMessage, setEcuErrorMessage] = useState<string | null>(null);
  
  // Simulated Manual Inputs
  const [pressureValue, setPressureValue] = useState('');
  const [manualResult, setManualResult] = useState<'PASS' | 'FAIL' | 'INCONCLUSIVE'>('PASS');
  const [manualNotes, setManualNotes] = useState('');

  // Local Telemetry Controls (for testing Safety Gates)
  const [telemetry, setTelemetry] = useState<LiveTelemetry>({
    rpm: 0,
    speed: 0,
    temp: 85,
    voltage: 12.2,
    load: 0,
    maf: 0,
    parkingBrakeOn: true,
    brakePedalPressed: false,
    transmissionParkOrNeutral: true,
    doorsClosed: true,
    fuelLevel: 45,
    adapterQuality: 85,
  });

  const [activeDtcCodes, setActiveDtcCodes] = useState<string[]>(['P0230']); // Default mock active DTC (fuel pump circuit)

  const holdIntervalRef = useRef<any>(null);

  // Preconditions Check
  const precheck = selectedCap 
    ? SafetyPreconditionEngine.evaluatePreconditions(selectedCap, telemetry, activeDtcCodes)
    : { passed: false, failedConditions: [], reason: 'Ninguna prueba seleccionada.', alternativeSuggestion: '' };

  // Trigger holding button simulation
  useEffect(() => {
    if (isHolding) {
      holdIntervalRef.current = setInterval(() => {
        setHoldProgress(prev => {
          if (prev >= 100) {
            clearInterval(holdIntervalRef.current);
            setIsHolding(false);
            handleExecuteAction();
            return 100;
          }
          return prev + 5; // Takes 2 seconds (20 steps * 100ms)
        });
      }, 100);
    } else {
      if (holdIntervalRef.current) {
        clearInterval(holdIntervalRef.current);
      }
      setHoldProgress(0);
    }

    return () => {
      if (holdIntervalRef.current) clearInterval(holdIntervalRef.current);
    };
  }, [isHolding]);

  const handleStartHold = () => {
    if (!understandRisk && (selectedCap?.riskLevel === 'HIGH' || selectedCap?.riskLevel === 'CRITICAL')) return;
    if (!precheck.passed) return;
    setIsHolding(true);
  };

  const handleEndHold = () => {
    setIsHolding(false);
  };

  // Execution Flow
  const handleExecuteAction = async () => {
    if (!selectedCap) return;

    setCurrentStep(4);
    setActionStatus('PRECHECK_RUNNING');
    setExecutionLogs([]);
    setEcuErrorMessage(null);
    setExecutionResult(null);

    // Step 1: Capture Pre-Snapshot
    const preSnapshot = ObdSnapshotEngine.capture(
      currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      telemetry,
      activeDtcCodes,
      `Pre-scan: Prueba activa de ${selectedCap.displayName}`
    );
    
    setActionStatus('WAITING_CONFIRMATION');
    await new Promise(r => setTimeout(r, 600));

    setActionStatus('EXECUTING');
    
    // Command profile lookup
    const profile = MOCK_COMMAND_PROFILES[selectedCap.commandProfileId || ''] || {
      id: 'custom',
      actionKey: selectedCap.actionKey,
      protocol: selectedCap.protocol,
      requestBytes: '30 01 00 00',
      positiveResponsePattern: '70',
      negativeResponsePatterns: [],
      timeoutMs: 1000,
      retries: 2,
      requiresSecurityAccess: false
    };

    const actionRecord: BidirectionalAction = {
      id: `act_${Date.now()}`,
      capabilityId: selectedCap.id,
      vehicleId: currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      userId: currentUser?.id || 'mecanico_1',
      status: 'EXECUTING',
      requestedAt: new Date().toISOString(),
      startedAt: new Date().toISOString(),
      completedAt: null,
      failedAt: null,
      preSnapshotId: preSnapshot.id,
      postSnapshotId: null,
      result: null,
      errorMessage: null,
      auditHash: `audit_${Math.random().toString(16).substring(2, 8)}`,
    };

    // Execute UDS Command Profile serial queue
    const res = await BidirectionalExecutor.executeAction(actionRecord, profile, (log) => {
      setExecutionLogs(prev => [...prev, log]);
    });

    setActionStatus('VERIFYING');
    await new Promise(r => setTimeout(r, 800));

    const postSnapshot = ObdSnapshotEngine.capture(
      currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      {
        ...telemetry,
        ...(res.postTelemetryChanges || {})
      },
      activeDtcCodes,
      `Post-scan: Prueba activa de ${selectedCap.displayName}`
    );

    // Check results
    const isCompleted = res.result === 'SUCCESS';
    setActionStatus(isCompleted ? 'COMPLETED' : 'FAILED');
    setExecutionResult(res.result);
    setEcuErrorMessage(res.ecuError || null);

    // Save Timeline Event & Report
    if (onAddTimelineEvent) {
      onAddTimelineEvent({
        id: `ev_${Date.now()}`,
        vehicle_id: currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
        event_type: isCompleted ? 'ACTIVE_TEST_EXECUTED' : 'ACTION_FAILED',
        title: `${isCompleted ? 'Prueba Activa Completada' : 'Prueba Activa Fallida'} — ${selectedCap.displayName}`,
        description: `Resultado ECU: ${res.result}. ${res.ecuError || ''}. Snapshot pre/post guardado de manera íntegra.`,
        severity: isCompleted ? 'low' : 'high',
        source: 'OBD',
        created_at: new Date().toISOString(),
        payload_json: JSON.stringify({
          actionId: actionRecord.id,
          capabilityKey: selectedCap.actionKey,
          result: res.result,
          logs: res.logs,
          preSnapshotHash: preSnapshot.id,
          postSnapshotHash: postSnapshot.id,
        })
      });
    }

    // Progression
    setCurrentStep(5);
  };

  const handleSaveManualEvidence = () => {
    if (!selectedCap) return;
    
    // Clear code if pump test is successful and manual confirmation works
    let updatedCodes = [...activeDtcCodes];
    if (selectedCap.actionKey === 'fuel_pump' && manualResult === 'PASS') {
      updatedCodes = activeDtcCodes.filter(c => c !== 'P0230');
      setActiveDtcCodes(updatedCodes);
    }

    if (onAddTimelineEvent) {
      onAddTimelineEvent({
        id: `ev_${Date.now()}_man`,
        vehicle_id: currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
        event_type: 'REPAIR_COMPLETED',
        title: `Evidencia Manual: ${selectedCap.displayName}`,
        description: `Resultado manual ingresado: ${manualResult}. Medición de presión: ${pressureValue || 'N/A'} PSI. Notas: ${manualNotes}`,
        severity: manualResult === 'PASS' ? 'low' : 'medium',
        source: 'Manual',
        created_at: new Date().toISOString(),
      });
    }

    alert('✅ Evidencia y notas técnicas guardadas. Los resultados se han vinculado al historial del Garage Digital y reporte final.');
    
    // Reset wizard
    setSelectedCap(null);
    setCurrentStep(1);
    setUnderstandRisk(false);
    setHoldProgress(0);
    setPressureValue('');
    setManualNotes('');
  };

  return (
    <div className="p-4 sm:p-6 max-w-6xl mx-auto space-y-6">
      
      {/* CONTROL DE SIMULACIÓN DE CONDICIONES (Para Auditoría / Test de Safety Gates) */}
      <div className="bg-steel-950/60 rounded-2xl p-4 border border-forge-500/20">
        <h4 className="text-xs font-mono font-black text-forge-400 uppercase tracking-widest mb-3 flex items-center gap-1.5">
          <Activity size={14} className="animate-pulse" />
          Consola del Vehículo (Simulador OBD-II)
        </h4>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Velocidad</label>
            <select 
              value={telemetry.speed}
              onChange={(e) => setTelemetry(prev => ({ ...prev, speed: parseInt(e.target.value) }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="0">0 km/h (Estacionario)</option>
              <option value="15">15 km/h (En Marcha)</option>
              <option value="50">50 km/h (En Marcha)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Voltaje Batería</label>
            <select 
              value={telemetry.voltage}
              onChange={(e) => setTelemetry(prev => ({ ...prev, voltage: parseFloat(e.target.value) }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="12.2">12.2 V (Estable)</option>
              <option value="14.2">14.2 V (Alternador)</option>
              <option value="10.8">10.8 V (Batería Baja)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Estado Motor</label>
            <select 
              value={telemetry.rpm === 0 ? 'off' : 'running'}
              onChange={(e) => setTelemetry(prev => ({ ...prev, rpm: e.target.value === 'off' ? 0 : 800 }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="off">Apagado (0 RPM)</option>
              <option value="running">Ralentí (800 RPM)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Temperatura Refrig.</label>
            <select 
              value={telemetry.temp}
              onChange={(e) => setTelemetry(prev => ({ ...prev, temp: parseInt(e.target.value) }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="85">85 °C (Nominal)</option>
              <option value="118">118 °C (Sobrecalentado)</option>
              <option value="50">50 °C (Frío)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Frenos / Caja</label>
            <div className="flex gap-2">
              <button 
                onClick={() => setTelemetry(prev => ({ ...prev, parkingBrakeOn: !prev.parkingBrakeOn }))}
                className={`flex-1 text-[9px] font-bold py-1.5 rounded-lg border font-mono transition-colors ${telemetry.parkingBrakeOn ? 'bg-forge-500/20 text-forge-400 border-forge-500/30' : 'bg-steel-900 text-steel-500 border-steel-700'}`}
              >
                F.MANO
              </button>
              <button 
                onClick={() => setTelemetry(prev => ({ ...prev, brakePedalPressed: !prev.brakePedalPressed }))}
                className={`flex-1 text-[9px] font-bold py-1.5 rounded-lg border font-mono transition-colors ${telemetry.brakePedalPressed ? 'bg-forge-500/20 text-forge-400 border-forge-500/30' : 'bg-steel-900 text-steel-500 border-steel-700'}`}
              >
                FRENO
              </button>
            </div>
          </div>
        </div>
        
        {activeDtcCodes.includes('P0230') && (
          <div className="mt-3 bg-red-500/10 border border-red-500/25 rounded-xl p-3 flex gap-2 items-center">
            <AlertTriangle className="text-red-400 shrink-0" size={16} />
            <p className="text-[11px] text-red-200">
              <span className="font-bold">IA Diagnóstica MEET:</span> Se detectó código <span className="font-mono font-bold bg-black/40 px-1 py-0.5 rounded text-white">P0230</span> (Bomba Combustible). 
              Se recomienda encarecidamente ejecutar la <span className="font-bold">Prueba Activa de Bomba de Gasolina</span> para descartar si el fallo es eléctrico (relé/fusible) o mecánico (bomba).
            </p>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* COLUMNA IZQUIERDA: LISTA DE CAPACIDADES */}
        <div className="lg:col-span-5 space-y-4">
          <div className="glass rounded-2xl p-4 border border-white/10 shadow-lg bg-steel-950/40">
            <h3 className="text-xs font-black text-white uppercase tracking-widest font-mono mb-4 flex items-center gap-1.5">
              <Zap size={14} className="text-forge-500" />
              Acciones de Control Bidireccional
            </h3>
            
            <div className="space-y-2">
              {MOCK_CAPABILITIES.filter(c => c.actionType === 'ACTIVE_TEST' || c.actionType === 'RESTRICTED').map(cap => {
                const isSelected = selectedCap?.id === cap.id;
                const isBlocked = cap.actionType === 'RESTRICTED';
                
                return (
                  <button
                    key={cap.id}
                    onClick={() => {
                      setSelectedCap(cap);
                      setCurrentStep(1);
                      setHoldProgress(0);
                      setUnderstandRisk(false);
                      setExecutionLogs([]);
                    }}
                    className={`w-full text-left p-3.5 rounded-xl border transition-all flex justify-between items-start ${
                      isSelected 
                        ? 'bg-forge-500/15 border-forge-500/50 shadow-[0_0_15px_rgba(0,240,255,0.08)]' 
                        : isBlocked
                          ? 'bg-steel-950/30 border-red-500/20 opacity-60 hover:opacity-80'
                          : 'bg-steel-900/60 hover:bg-steel-900 border-white/5 hover:border-white/15'
                    }`}
                  >
                    <div className="space-y-1 pr-2">
                      <div className="flex items-center gap-1.5">
                        <span className="font-bold text-white text-xs leading-snug">{cap.displayName}</span>
                        {isBlocked && (
                          <span className="bg-red-500/20 text-red-400 border border-red-500/30 rounded px-1.5 py-0.5 text-[7px] uppercase tracking-wider font-mono">
                            Bloqueado
                          </span>
                        )}
                      </div>
                      <p className="text-[10px] text-steel-400 line-clamp-2 leading-relaxed">{cap.description}</p>
                    </div>
                    
                    <div className="text-right shrink-0">
                      <span className={`text-[7px] font-black uppercase tracking-wider px-1.5 py-0.5 rounded ${
                        cap.riskLevel === 'CRITICAL' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                        cap.riskLevel === 'HIGH' ? 'bg-orange-500/20 text-orange-400 border border-orange-500/30' :
                        'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30'
                      }`}>
                        {cap.riskLevel}
                      </span>
                      <div className="text-[8px] text-steel-500 font-mono mt-2">{cap.system.split(' ')[0]}</div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* COLUMNA DERECHA: FLUJO GUIADO */}
        <div className="lg:col-span-7">
          {selectedCap ? (
            <div className="glass rounded-3xl p-5 border border-white/10 shadow-lg space-y-5 relative bg-steel-950/30">
              
              {/* Wizard Steps indicator */}
              <div className="flex items-center justify-between border-b border-white/5 pb-3">
                <div className="flex items-center gap-2">
                  <div className="bg-forge-500/10 text-forge-400 border border-forge-500/30 rounded-lg p-1.5">
                    <Wrench size={14} />
                  </div>
                  <div>
                    <h4 className="text-xs font-mono font-bold text-steel-500 uppercase tracking-widest">Procedimiento Guiado</h4>
                    <span className="text-xs font-black text-white">{selectedCap.displayName}</span>
                  </div>
                </div>
                
                <div className="flex gap-1.5">
                  {[1, 2, 3, 4, 5].map(step => (
                    <span 
                      key={step} 
                      className={`w-2.5 h-2.5 rounded-full border transition-all ${
                        currentStep === step 
                          ? 'bg-forge-500 border-forge-400 shadow-[0_0_8px_rgba(0,240,255,0.4)]'
                          : currentStep > step
                            ? 'bg-forge-500/50 border-forge-500/30'
                            : 'bg-steel-950 border-steel-700'
                      }`}
                    ></span>
                  ))}
                </div>
              </div>

              {/* STEP 1: INFORMACIÓN */}
              {currentStep === 1 && (
                <div className="space-y-4 animate-fade-in">
                  <div className="space-y-1.5">
                    <h5 className="text-xs font-bold text-white uppercase tracking-wider">Paso 1: Medidas de Seguridad y Preparación</h5>
                    <p className="text-xs text-steel-400 leading-relaxed font-medium">
                      Antes de enviar señales electrónicas directas a la ECU, verifique manualmente las siguientes condiciones mecánicas básicas para evitar cortocircuitos o daños colaterales.
                    </p>
                  </div>

                  <div className="bg-steel-950/60 border border-white/5 rounded-2xl p-4 divide-y divide-white/5 text-xs text-steel-300 space-y-3 font-medium">
                    <div className="flex gap-2 items-start pt-1">
                      <span className="bg-forge-500/10 border border-forge-500/30 text-forge-400 font-bold px-1.5 rounded font-mono">1</span>
                      <p>Inspeccione visualmente el componente afectado para asegurar que no haya cables expuestos o conectores sulfatados.</p>
                    </div>
                    <div className="flex gap-2 items-start pt-3">
                      <span className="bg-forge-500/10 border border-forge-500/30 text-forge-400 font-bold px-1.5 rounded font-mono">2</span>
                      <p>Para pruebas de presión de combustible, conecte un manómetro físico seguro en el puerto Schrader del riel.</p>
                    </div>
                    <div className="flex gap-2 items-start pt-3">
                      <span className="bg-forge-500/10 border border-forge-500/30 text-forge-400 font-bold px-1.5 rounded font-mono">3</span>
                      <p>Mantenga un extintor clase B/C en la cercanía de la bahía si evalúa circuitos de combustible.</p>
                    </div>
                  </div>

                  <button
                    onClick={() => setCurrentStep(2)}
                    className="w-full bg-forge-500 hover:bg-forge-400 text-black font-black py-2.5 rounded-xl text-xs uppercase tracking-wider transition-all"
                  >
                    Medidas Completadas. Continuar &rarr;
                  </button>
                </div>
              )}

              {/* STEP 2: PRECONDICIONES (Safety Gates) */}
              {currentStep === 2 && (
                <div className="space-y-4 animate-fade-in">
                  <div className="space-y-1">
                    <h5 className="text-xs font-bold text-white uppercase tracking-wider">Paso 2: Validación de Compuerta de Seguridad (Safety Gate)</h5>
                    <p className="text-xs text-steel-400 leading-relaxed font-medium">
                      El sistema MEET interroga la telemetría en tiempo real de la ECU para garantizar que el vehículo está detenido y bajo parámetros térmicos y eléctricos seguros.
                    </p>
                  </div>

                  <div className="space-y-2">
                    {selectedCap.requiredConditions.map(cond => {
                      const failed = precheck.failedConditions.includes(cond);
                      
                      const labelMap: Record<string, string> = {
                        vehicle_stationary: 'Vehículo Estacionario (Velocidad = 0 km/h)',
                        battery_voltage_min: 'Voltaje Batería >= 11.8V (Batería Estable)',
                        engine_off: 'Motor Apagado (RPM = 0)',
                        engine_running: 'Motor Encendido (RPM >= 500)',
                        ignition_on: 'Ignición en posición ON (Contacto colocado)',
                        coolant_temp_max: 'Temperatura de motor segura (< 115°C)',
                        coolant_temp_min: 'Temperatura de motor de servicio (>= 70°C)',
                        parking_brake_on: 'Freno de mano aplicado',
                      };

                      return (
                        <div 
                          key={cond}
                          className={`flex items-center justify-between p-3 rounded-xl border transition-colors ${
                            failed 
                              ? 'bg-red-500/5 border-red-500/25 text-red-200' 
                              : 'bg-green-500/5 border-green-500/25 text-green-200'
                          }`}
                        >
                          <span className="text-xs font-bold font-mono">{labelMap[cond] || cond}</span>
                          <span className={`text-[9px] font-black uppercase px-2 py-0.5 rounded ${
                            failed ? 'bg-red-500/25 text-red-400' : 'bg-green-500/25 text-green-400'
                          }`}>
                            {failed ? 'BLOQUEADO' : 'OK'}
                          </span>
                        </div>
                      );
                    })}
                  </div>

                  {!precheck.passed ? (
                    <div className="bg-red-500/10 border border-red-500/20 p-4 rounded-xl space-y-2">
                      <h6 className="text-xs font-bold text-red-400 uppercase tracking-wider flex items-center gap-1.5">
                        <ShieldAlert size={14} /> ACCIÓN BLOQUEADA POR SEGURIDAD
                      </h6>
                      <p className="text-[11px] text-red-200 leading-relaxed font-mono">
                        {precheck.reason}
                      </p>
                      <div className="text-[10px] font-bold text-steel-400 mt-2 bg-black/35 p-2.5 rounded-lg border border-white/5 leading-relaxed">
                        <span className="text-forge-400">Acción Sugerida:</span> {precheck.alternativeSuggestion}
                      </div>
                    </div>
                  ) : (
                    <div className="bg-green-500/10 border border-green-500/20 p-3 rounded-xl flex items-center gap-2 text-[11px] text-green-200 font-mono">
                      <CheckCircle2 size={16} className="text-green-400 shrink-0" />
                      <span>Compuerta de seguridad superada. Todos los sensores están listos.</span>
                    </div>
                  )}

                  <div className="flex gap-3">
                    <button
                      onClick={() => setCurrentStep(1)}
                      className="flex-1 bg-white/5 hover:bg-white/10 border border-white/10 text-white font-bold py-2.5 rounded-xl text-xs transition-all"
                    >
                      &larr; Volver
                    </button>
                    <button
                      onClick={() => setCurrentStep(3)}
                      disabled={!precheck.passed}
                      className="flex-1 bg-forge-500 hover:bg-forge-400 disabled:opacity-40 text-black font-black py-2.5 rounded-xl text-xs uppercase tracking-wider transition-all"
                    >
                      Continuar &rarr;
                    </button>
                  </div>
                </div>
              )}

              {/* STEP 3: ADVERTENCIA */}
              {currentStep === 3 && (
                <div className="space-y-4 animate-fade-in">
                  <div className="bg-orange-500/10 border border-orange-500/35 p-4 rounded-2xl space-y-3">
                    <div className="flex items-center gap-2 text-orange-400 border-b border-orange-500/15 pb-2">
                      <ShieldAlert size={20} />
                      <span className="font-black text-sm uppercase tracking-wider">ADVERTENCIA DE RESPONSABILIDAD</span>
                    </div>
                    
                    <p className="text-xs text-orange-200 leading-relaxed font-medium">
                      Esta es una <span className="font-bold text-white">acción bidireccional activa</span>. MEET enviará comandos de diagnóstico UDS que ignorarán el control automático del motor. 
                      Al proceder, usted asume total responsabilidad de cualquier anomalía física.
                    </p>

                    <div className="space-y-1.5 text-[11px] text-orange-300 font-mono">
                      <div>• Nivel de riesgo de la prueba: <span className="text-white font-bold">{selectedCap.riskLevel}</span></div>
                      <div>• Impacto del sistema: <span className="text-white font-bold">{selectedCap.system}</span></div>
                      <div>• Protocolo utilizado: <span className="text-white font-bold">{selectedCap.protocol}</span></div>
                    </div>
                  </div>

                  {(selectedCap.riskLevel === 'HIGH' || selectedCap.riskLevel === 'CRITICAL' || selectedCap.riskLevel === 'MEDIUM') && (
                    <label className="flex items-start gap-3 bg-black/35 p-3 rounded-xl border border-white/5 cursor-pointer select-none">
                      <input 
                        type="checkbox" 
                        checked={understandRisk}
                        onChange={(e) => setUnderstandRisk(e.target.checked)}
                        className="mt-0.5 accent-forge-500"
                      />
                      <span className="text-xs text-steel-200 font-medium">
                        Entiendo el riesgo y he conectado manómetros / elementos de seguridad física en el vehículo.
                      </span>
                    </label>
                  )}

                  <div className="space-y-2">
                    {(selectedCap.riskLevel === 'HIGH' || selectedCap.riskLevel === 'CRITICAL' || selectedCap.riskLevel === 'MEDIUM') ? (
                      <div className="relative">
                        <button
                          onMouseDown={handleStartHold}
                          onMouseUp={handleEndHold}
                          onMouseLeave={handleEndHold}
                          onTouchStart={handleStartHold}
                          onTouchEnd={handleEndHold}
                          disabled={!understandRisk}
                          className="w-full bg-orange-500 hover:bg-orange-400 disabled:opacity-40 text-black font-black py-3 rounded-xl text-xs uppercase tracking-widest relative overflow-hidden transition-all select-none"
                        >
                          <span className="relative z-10">Mantenga presionado 2 segundos para confirmar</span>
                          <div 
                            className="absolute top-0 left-0 bottom-0 bg-white/20 transition-all duration-100"
                            style={{ width: `${holdProgress}%` }}
                          ></div>
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={handleExecuteAction}
                        className="w-full bg-forge-500 hover:bg-forge-400 text-black font-black py-3 rounded-xl text-xs uppercase tracking-widest transition-all"
                      >
                        Confirmar y Ejecutar Prueba Activa
                      </button>
                    )}
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => setCurrentStep(2)}
                      className="flex-1 bg-white/5 hover:bg-white/10 border border-white/10 text-white font-bold py-2.5 rounded-xl text-xs transition-all"
                    >
                      &larr; Volver
                    </button>
                    <button
                      onClick={() => { setSelectedCap(null); setCurrentStep(1); }}
                      className="flex-1 bg-red-500/10 hover:bg-red-500/25 border border-red-500/20 text-red-400 font-bold py-2.5 rounded-xl text-xs transition-all"
                    >
                      Cancelar Todo
                    </button>
                  </div>
                </div>
              )}

              {/* STEP 4: EJECUCIÓN */}
              {currentStep === 4 && (
                <div className="space-y-4 animate-fade-in">
                  <div className="flex justify-between items-center">
                    <h5 className="text-xs font-bold text-white uppercase tracking-wider">Paso 4: Transmisión Serial en Canal CAN</h5>
                    <span className="text-[10px] text-forge-400 font-mono animate-pulse font-bold uppercase">
                      {actionStatus === 'PRECHECK_RUNNING' ? 'Inicializando...' :
                       actionStatus === 'EXECUTING' ? 'Transmitiendo...' :
                       actionStatus === 'VERIFYING' ? 'Validando ECU...' : 'Listo'}
                    </span>
                  </div>

                  <div className="h-1.5 w-full bg-steel-950 rounded-full overflow-hidden border border-white/5">
                    <div 
                      className={`h-full rounded-full transition-all duration-300 ${
                        actionStatus === 'FAILED' ? 'bg-red-500' : 'bg-forge-500'
                      }`}
                      style={{ 
                        width: actionStatus === 'PRECHECK_RUNNING' ? '15%' :
                               actionStatus === 'EXECUTING' ? '60%' :
                               actionStatus === 'VERIFYING' ? '90%' : '100%' 
                      }}
                    ></div>
                  </div>

                  {/* Terminal Logger Output */}
                  <div className="bg-black/95 border border-steel-800 rounded-2xl p-4 font-mono text-[10px] text-emerald-400 space-y-1.5 h-44 overflow-y-auto select-none shadow-inner">
                    {executionLogs.map((log, i) => (
                      <div key={i} className="flex items-start gap-1">
                        <span className="text-emerald-700 shrink-0">&gt;</span>
                        <span>{log}</span>
                      </div>
                    ))}
                    {(actionStatus === 'EXECUTING' || actionStatus === 'VERIFYING') && (
                      <div className="w-1.5 h-3 bg-emerald-400 inline-block animate-pulse ml-1"></div>
                    )}
                  </div>
                </div>
              )}

              {/* STEP 5: REGISTRO */}
              {currentStep === 5 && (
                <div className="space-y-4 animate-fade-in">
                  <div className="p-4 rounded-2xl border flex gap-3 items-start bg-black/40 border-white/5">
                    <div className="shrink-0 p-2.5 rounded-xl bg-steel-900 border border-white/10">
                      {executionResult === 'SUCCESS' ? (
                        <CheckCircle2 className="text-green-400" size={24} />
                      ) : (
                        <ShieldAlert className="text-red-400" size={24} />
                      )}
                    </div>
                    <div>
                      <h5 className="text-xs font-bold text-white uppercase tracking-wider">
                        Resultado del Comando: {executionResult === 'SUCCESS' ? 'Éxito Electrónico' : 'Fallo de ECU / Adaptador'}
                      </h5>
                      <p className="text-[11px] text-steel-400 leading-relaxed mt-1 font-medium">
                        {executionResult === 'SUCCESS' 
                          ? 'La ECU del vehículo respondió positivamente al comando UDS. El actuador eléctrico se ha forzado temporalmente.'
                          : ecuErrorMessage || 'El comando no pudo completarse debido a un timeout o error del bus de comunicación.'}
                      </p>
                    </div>
                  </div>

                  {/* Step 4 Fuel Pump Test manual registry */}
                  {selectedCap.actionKey === 'fuel_pump' && (
                    <div className="bg-steel-950/60 p-4 rounded-xl border border-forge-500/10 space-y-3">
                      <h6 className="text-[10px] text-forge-500 font-mono uppercase tracking-widest">Anotaciones de Presión y Evidencia</h6>
                      
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Presión Registrada (PSI)</label>
                          <input
                            type="text"
                            value={pressureValue}
                            onChange={(e) => setPressureValue(e.target.value)}
                            placeholder="Ej. 45"
                            className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2.5 py-1.5 text-xs text-white font-mono"
                          />
                        </div>
                        <div>
                          <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Diagnóstico Físico</label>
                          <select
                            value={manualResult}
                            onChange={(e) => setManualResult(e.target.value as any)}
                            className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2.5 py-1.5 text-xs text-white font-mono"
                          >
                            <option value="PASS">PASS (Bomba genera presión y suena)</option>
                            <option value="FAIL">FAIL (Bomba no suena o no genera presión)</option>
                            <option value="INCONCLUSIVE">Inconcluso (Verificar cableado)</option>
                          </select>
                        </div>
                      </div>
                      
                      <div>
                        <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Notas Técnicas Adicionales</label>
                        <textarea
                          rows={2}
                          value={manualNotes}
                          onChange={(e) => setManualNotes(e.target.value)}
                          placeholder="Ingrese observaciones..."
                          className="w-full bg-steel-900 border border-steel-700 rounded-lg p-2 text-xs text-white"
                        />
                      </div>
                    </div>
                  )}

                  {/* Manual Reset Confirm for general components */}
                  {selectedCap.actionKey !== 'fuel_pump' && (
                    <div className="bg-steel-950/60 p-4 rounded-xl border border-white/5 space-y-3">
                      <h6 className="text-[10px] text-steel-400 font-mono uppercase tracking-widest font-bold">Resultado Físico Observado</h6>
                      <div className="flex gap-2">
                        {['PASS', 'FAIL', 'INCONCLUSIVE'].map(r => (
                          <button
                            key={r}
                            onClick={() => setManualResult(r as any)}
                            className={`flex-1 text-[10px] font-bold py-2 rounded-lg border transition-all ${
                              manualResult === r 
                                ? 'bg-forge-500/20 text-forge-400 border-forge-500/40 shadow-inner' 
                                : 'bg-steel-900 text-steel-400 border-steel-700'
                            }`}
                          >
                            {r}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  <button
                    onClick={handleSaveManualEvidence}
                    className="w-full bg-forge-500 hover:bg-forge-400 text-black font-black py-3 rounded-xl text-xs uppercase tracking-widest transition-all"
                  >
                    Guardar Evidencia & Finalizar
                  </button>
                </div>
              )}

            </div>
          ) : (
            <div className="glass rounded-3xl p-10 border border-white/5 shadow-lg flex flex-col items-center justify-center text-center min-h-[380px] relative overflow-hidden bg-gradient-to-b from-steel-900/50 to-transparent">
              <div className="relative w-28 h-28 mb-6 flex items-center justify-center">
                <div className="absolute inset-0 rounded-full border border-forge-500/10 animate-ping"></div>
                <div className="w-16 h-16 rounded-full bg-forge-500/5 border border-forge-500/20 flex items-center justify-center text-forge-500 shadow-[0_0_15px_rgba(0,240,255,0.05)]">
                  <Zap size={24} className="animate-pulse" />
                </div>
              </div>

              <h3 className="text-lg font-bold text-white tracking-wide">Panel de Pruebas Activas Bidireccionales</h3>
              <p className="text-xs text-steel-400 mt-2 max-w-sm leading-relaxed">
                Seleccione una de las capacidades de actuadores disponibles a la izquierda para iniciar el flujo guiado. Recuerde validar el manual del fabricante.
              </p>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}


// ═══════════════════════════════════════════════════════════════
// TAB 4: SERVICE RESETS (Electronic and Manual procedures)
// ═══════════════════════════════════════════════════════════════

interface ServiceResetsTabProps {
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onAddTimelineEvent?: (ev: any) => void;
}

export function ServiceResetsTab({ currentUser, workOrders, onAddTimelineEvent }: ServiceResetsTabProps) {
  const [selectedCap, setSelectedCap] = useState<BidirectionalCapability | null>(null);
  const [resetLogs, setResetLogs] = useState<string[]>([]);
  const [isExecuting, setIsExecuting] = useState(false);
  const [resetCompleted, setResetCompleted] = useState(false);
  const [snapshotPre, setSnapshotPre] = useState<DiagnosticSnapshot | null>(null);
  const [snapshotPost, setSnapshotPost] = useState<DiagnosticSnapshot | null>(null);
  const [comparison, setComparison] = useState<any>(null);

  // Local Telemetry Controls (for testing Safety Gates)
  const [telemetry, setTelemetry] = useState<LiveTelemetry>({
    rpm: 0,
    speed: 0,
    temp: 82,
    voltage: 12.3,
    load: 0,
    maf: 0,
    parkingBrakeOn: true,
    brakePedalPressed: false,
    transmissionParkOrNeutral: true,
    doorsClosed: true,
    fuelLevel: 45,
    adapterQuality: 90,
  });

  const activeDtcCodes = ['P0230']; // Default mock active DTCs

  // Procedure lookup
  const currentProcedure = selectedCap 
    ? DEFAULT_PROCEDURES.find(p => p.actionKey === selectedCap.actionKey)
    : null;

  const precheck = selectedCap
    ? SafetyPreconditionEngine.evaluatePreconditions(selectedCap, telemetry, activeDtcCodes)
    : { passed: false, failedConditions: [], reason: 'Ninguno seleccionado.' };

  const handleRunElectronicReset = async () => {
    if (!selectedCap || !precheck.passed) return;

    setIsExecuting(true);
    setResetLogs([]);
    setResetCompleted(false);

    // Capture pre snapshot
    const preSnap = ObdSnapshotEngine.capture(
      currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      telemetry,
      activeDtcCodes,
      `Pre-scan: Reset de Servicio Electrónico de ${selectedCap.displayName}`
    );
    setSnapshotPre(preSnap);

    const profile = MOCK_COMMAND_PROFILES[selectedCap.commandProfileId || ''] || {
      id: 'custom_reset',
      actionKey: selectedCap.actionKey,
      protocol: selectedCap.protocol,
      requestBytes: '2E AA 00',
      positiveResponsePattern: '6E',
      negativeResponsePatterns: [],
      timeoutMs: 1000,
      retries: 2,
      requiresSecurityAccess: true
    };

    const actionRecord: BidirectionalAction = {
      id: `reset_${Date.now()}`,
      capabilityId: selectedCap.id,
      vehicleId: currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      userId: currentUser?.id || 'mecanico_1',
      status: 'EXECUTING',
      requestedAt: new Date().toISOString(),
      startedAt: new Date().toISOString(),
      completedAt: null,
      failedAt: null,
      preSnapshotId: preSnap.id,
      postSnapshotId: null,
      result: null,
      errorMessage: null,
      auditHash: `audit_${Math.random().toString(16).substring(2, 8)}`,
    };

    const res = await BidirectionalExecutor.executeAction(actionRecord, profile, (log) => {
      setResetLogs(prev => [...prev, log]);
    });

    const isSuccess = res.result === 'SUCCESS';
    setIsExecuting(false);
    setResetCompleted(true);

    const postSnap = ObdSnapshotEngine.capture(
      currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
      {
        ...telemetry,
        ...(res.postTelemetryChanges || {})
      },
      [], // Cleared DTC codes during reset simulations
      `Post-scan: Reset de Servicio Electrónico de ${selectedCap.displayName}`
    );
    setSnapshotPost(postSnap);

    const diff = ObdSnapshotEngine.compare(preSnap, postSnap);
    setComparison(diff);

    // Save Timeline Event & Report
    if (onAddTimelineEvent) {
      onAddTimelineEvent({
        id: `ev_${Date.now()}`,
        vehicle_id: currentUser?.vehicles?.[0]?.plate || 'TEST-PLATE',
        event_type: isSuccess ? 'SERVICE_RESET_EXECUTED' : 'ACTION_FAILED',
        title: `${isSuccess ? 'Reset de Servicio Completado' : 'Reset de Servicio Fallido'} — ${selectedCap.displayName}`,
        description: `Resultado: ${res.result}. ${res.ecuError || ''}. Comparativa de snapshot disponible en Garage Digital.`,
        severity: isSuccess ? 'low' : 'high',
        source: 'OBD',
        created_at: new Date().toISOString(),
        payload_json: JSON.stringify({
          actionId: actionRecord.id,
          capabilityKey: selectedCap.actionKey,
          result: res.result,
          preSnapshotHash: preSnap.id,
          postSnapshotHash: postSnap.id,
          clearedDtcsCount: diff.clearedDtcs.length
        })
      });
    }
  };

  return (
    <div className="p-4 sm:p-6 max-w-6xl mx-auto space-y-6">
      
      {/* SIMULATOR CONTROLS */}
      <div className="bg-steel-950/60 rounded-2xl p-4 border border-forge-500/20">
        <h4 className="text-xs font-mono font-black text-forge-400 uppercase tracking-widest mb-3 flex items-center gap-1.5">
          <Activity size={14} className="animate-pulse" />
          Simulación de Precondiciones de Mantenimiento
        </h4>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Voltaje Batería</label>
            <select 
              value={telemetry.voltage}
              onChange={(e) => setTelemetry(prev => ({ ...prev, voltage: parseFloat(e.target.value) }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="12.3">12.3 V (Contacto ON)</option>
              <option value="10.5">10.5 V (Batería Baja)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Marcha Seleccionada</label>
            <select 
              value={telemetry.transmissionParkOrNeutral ? 'P' : 'D'}
              onChange={(e) => setTelemetry(prev => ({ ...prev, transmissionParkOrNeutral: e.target.value === 'P' }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="P">Park/Neutral (Estacionario)</option>
              <option value="D">Drive (Caja Engranada)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Freno Estacionamiento</label>
            <select 
              value={telemetry.parkingBrakeOn ? 'on' : 'off'}
              onChange={(e) => setTelemetry(prev => ({ ...prev, parkingBrakeOn: e.target.value === 'on' }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="on">Freno Puesto (ON)</option>
              <option value="off">Freno Suelto (OFF)</option>
            </select>
          </div>
          <div>
            <label className="text-[9px] text-steel-400 block uppercase font-mono mb-1">Calidad de Enlace OBD2</label>
            <select 
              value={telemetry.adapterQuality}
              onChange={(e) => setTelemetry(prev => ({ ...prev, adapterQuality: parseInt(e.target.value) }))}
              className="w-full bg-steel-900 border border-steel-700 rounded-lg px-2 py-1.5 text-xs text-white font-mono"
            >
              <option value="90">90% (Excelente)</option>
              <option value="40">40% (Glitch / Señal Débil)</option>
            </select>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* COLUMNA IZQUIERDA: LISTA DE RESETS */}
        <div className="lg:col-span-5 space-y-4">
          <div className="glass rounded-2xl p-4 border border-white/10 shadow-lg bg-steel-950/40">
            <h3 className="text-xs font-black text-white uppercase tracking-widest font-mono mb-4 flex items-center gap-1.5">
              <Wrench size={14} className="text-forge-500" />
              Resets de Mantenimiento / Servicio
            </h3>

            <div className="space-y-2">
              {MOCK_CAPABILITIES.filter(c => c.actionType === 'SERVICE_RESET' || c.actionType === 'ADAPTATION').map(cap => {
                const isSelected = selectedCap?.id === cap.id;
                
                return (
                  <button
                    key={cap.id}
                    onClick={() => {
                      setSelectedCap(cap);
                      setResetCompleted(false);
                      setResetLogs([]);
                      setSnapshotPre(null);
                      setSnapshotPost(null);
                      setComparison(null);
                    }}
                    className={`w-full text-left p-3 rounded-xl border transition-all flex justify-between items-center ${
                      isSelected 
                        ? 'bg-forge-500/15 border-forge-500/50 shadow-[0_0_15px_rgba(0,240,255,0.08)]' 
                        : 'bg-steel-900/60 hover:bg-steel-900 border-white/5 hover:border-white/15'
                    }`}
                  >
                    <div>
                      <div className="font-bold text-white text-xs leading-snug">{cap.displayName}</div>
                      <p className="text-[10px] text-steel-400 mt-1 leading-relaxed">{cap.description}</p>
                    </div>
                    
                    <span className="bg-steel-950 border border-white/5 text-steel-400 rounded px-1.5 py-0.5 text-[8px] uppercase font-mono tracking-wider shrink-0 ml-2">
                      {cap.supportConfidence === 'CONFIRMED' ? 'Confirmado' : 'Probable'}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* COLUMNA DERECHA: PROCEDIMIENTO MANUAL Y BOTÓN */}
        <div className="lg:col-span-7">
          {selectedCap ? (
            <div className="glass rounded-3xl p-5 border border-white/10 shadow-lg space-y-5 bg-steel-950/30">
              
              <div className="flex justify-between items-start border-b border-white/5 pb-3">
                <div>
                  <h4 className="text-xs font-mono font-bold text-forge-500 uppercase tracking-widest">{selectedCap.system}</h4>
                  <h3 className="text-base font-black text-white">{selectedCap.displayName}</h3>
                </div>
                <span className={`px-2 py-0.5 rounded text-[8px] font-black uppercase tracking-wider ${
                  selectedCap.riskLevel === 'LOW' ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'
                }`}>
                  Riesgo {selectedCap.riskLevel}
                </span>
              </div>

              {/* OPCIÓN A: RESET ELECTRÓNICO */}
              <div className="bg-steel-950/60 border border-white/5 p-4 rounded-2xl space-y-4">
                <h5 className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                  <Cpu size={14} className="text-forge-500" />
                  Método 1: Comando Electrónico OBD
                </h5>
                <p className="text-[11px] text-steel-400 leading-relaxed font-medium">
                  MEET transmitirá un comando directo sobre el bus CAN para limpiar el buffer de memoria no volátil de la ECU.
                </p>

                {/* Precheck Warnings */}
                {!precheck.passed && (
                  <div className="bg-red-500/10 border border-red-500/25 p-3 rounded-xl text-[10px] text-red-200 leading-relaxed font-mono font-medium">
                    ⚠️ Comando bloqueado: {precheck.reason}
                  </div>
                )}

                {!resetCompleted ? (
                  <button
                    onClick={handleRunElectronicReset}
                    disabled={isExecuting || !precheck.passed}
                    className="w-full bg-forge-500 hover:bg-forge-400 disabled:opacity-40 text-black font-black py-2.5 rounded-xl text-xs uppercase tracking-wider transition-all"
                  >
                    {isExecuting ? 'Ejecutando Reset OBD...' : 'Ejecutar Restablecimiento Electrónico'}
                  </button>
                ) : (
                  <div className="space-y-3">
                    <div className="p-3 bg-green-500/10 border border-green-500/25 rounded-xl text-green-200 text-xs font-mono">
                      ✅ Restablecimiento electrónico finalizado con éxito.
                    </div>

                    {comparison && (
                      <div className="bg-black/35 rounded-xl p-3 border border-white/5 space-y-2 text-[10px] font-mono">
                        <div className="text-forge-500 uppercase tracking-widest font-black text-[9px]">Resultado de Verificación de Snapshots</div>
                        <div>• Códigos de falla borrados: <span className="text-green-400 font-bold">{comparison.clearedDtcs.length > 0 ? comparison.clearedDtcs.join(', ') : 'Ninguno'}</span></div>
                        <div>• Cambios en voltaje: <span className="text-white">{comparison.voltageDelta.toFixed(2)}V</span></div>
                        <div>• Variación RPM ralentí: <span className="text-white">{comparison.rpmDelta} RPM</span></div>
                      </div>
                    )}
                  </div>
                )}

                {/* Logs Output */}
                {resetLogs.length > 0 && (
                  <div className="bg-black border border-steel-800 rounded-xl p-3 font-mono text-[9px] text-emerald-400 h-24 overflow-y-auto">
                    {resetLogs.map((log, idx) => (
                      <div key={idx} className="flex gap-1">
                        <span className="text-emerald-700">&gt;</span>
                        <span>{log}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* OPCIÓN B: PROCEDIMIENTO MANUAL */}
              {currentProcedure && (
                <div className="border border-white/5 p-4 rounded-2xl space-y-3 bg-steel-900/50">
                  <h5 className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-1.5">
                    <BookOpen size={14} className="text-forge-500" />
                    Método 2: Procedimiento Manual de Tablero (Reserva de Taller)
                  </h5>
                  <p className="text-[11px] text-steel-400 leading-relaxed font-medium">
                    Si el módulo electrónico no responde o el adaptador OBD no es compatible, complete el ciclo manual usando los controles del tablero:
                  </p>

                  <div className="space-y-2 bg-black/35 p-3 rounded-xl border border-white/5">
                    {currentProcedure.steps.map((step, idx) => (
                      <div key={idx} className="flex gap-2 items-start text-xs text-steel-300">
                        <span className="bg-white/5 border border-white/10 text-forge-400 rounded px-1.5 font-bold font-mono text-[10px]">{idx + 1}</span>
                        <p className="leading-relaxed font-medium">{step}</p>
                      </div>
                    ))}
                  </div>

                  <div className="bg-yellow-500/10 border border-yellow-500/20 p-3 rounded-xl text-[10px] text-yellow-300 font-mono leading-relaxed font-medium">
                    <span className="font-bold">Advertencias:</span> {currentProcedure.warnings.join(' ')}
                  </div>
                </div>
              )}

            </div>
          ) : (
            <div className="glass rounded-3xl p-10 border border-white/5 shadow-lg flex flex-col items-center justify-center text-center min-h-[380px] relative overflow-hidden bg-gradient-to-b from-steel-900/50 to-transparent">
              <div className="relative w-28 h-28 mb-6 flex items-center justify-center">
                <div className="absolute inset-0 rounded-full border border-forge-500/10 animate-ping"></div>
                <div className="w-16 h-16 rounded-full bg-forge-500/5 border border-forge-500/20 flex items-center justify-center text-forge-500 shadow-[0_0_15px_rgba(0,240,255,0.05)]">
                  <Wrench size={22} className="animate-pulse" />
                </div>
              </div>

              <h3 className="text-lg font-bold text-white tracking-wide">Panel de Resets de Servicio y Calibración</h3>
              <p className="text-xs text-steel-400 mt-2 max-w-sm leading-relaxed">
                Seleccione una tarea de mantenimiento a la izquierda para ver el procedimiento manual paso a paso o realizar el reset electrónico mediante comandos UDS directos.
              </p>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}

