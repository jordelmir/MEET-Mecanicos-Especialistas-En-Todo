import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  Search, AlertTriangle, Info, ShieldAlert, X, Wrench, Activity, Play, 
  Square, Save, ChevronDown, Zap, Gauge, Radio, BarChart3, Download,
  Cpu, CheckCircle2, History, RotateCcw, Link2, Sparkles, BookOpen, AlertCircle, RefreshCw
} from 'lucide-react';
import dtcDatabase from '../dtc_database.json';
import { OscilloscopeCanvas } from './OscilloscopeCanvas';
import { SignalGenerator, SignalAnalyzer, SIGNAL_LIBRARY, type SignalDiagnosis, type SignalDefinition } from '../services/signalAnalysis';
import type { OscilloscopeMeasurement, WorkOrder, Client } from '../types';

interface OBD2ScannerProps {
  onClose: () => void;
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onSaveMeasurement?: (measurement: OscilloscopeMeasurement) => void;
  onUpdateWorkOrder?: (id: string, updates: Partial<WorkOrder>) => void;
}

type TabMode = 'dtc' | 'oscilloscope';

export function OBD2Scanner({ onClose, currentUser, workOrders, onSaveMeasurement, onUpdateWorkOrder }: OBD2ScannerProps) {
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
                className={`px-4 py-2 text-[10px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1.5 ${activeTab === 'oscilloscope' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Activity size={12} /> Osciloscopio
              </button>
              <button 
                onClick={() => setActiveTab('dtc')} 
                className={`px-4 py-2 text-[10px] font-black uppercase tracking-wider transition-all rounded-lg flex items-center gap-1.5 ${activeTab === 'dtc' ? 'bg-forge-500 text-black shadow-md' : 'text-steel-400 hover:text-white'}`}
              >
                <Search size={12} /> Analizador DTC
              </button>
            </div>
            <button onClick={onClose} className="p-2 text-steel-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-xl transition-all border border-white/5">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto bg-gradient-to-b from-steel-900 via-steel-900 to-steel-950">
          {activeTab === 'dtc' ? (
            <DTCAnalyzerTab 
              currentUser={currentUser} 
              workOrders={workOrders} 
              onUpdateWorkOrder={onUpdateWorkOrder} 
            />
          ) : (
            <OscilloscopeTab currentUser={currentUser} workOrders={workOrders} onSaveMeasurement={onSaveMeasurement} />
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
}

function DTCAnalyzerTab({ currentUser, workOrders, onUpdateWorkOrder }: DTCAnalyzerTabProps) {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<any>(null);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  
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

  // Suggestions Autocomplete Logic
  useEffect(() => {
    if (code.trim().length < 2) {
      setSuggestions([]);
      return;
    }
    const cleanQuery = code.trim().replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
    const timer = setTimeout(() => {
      const matches: any[] = [];
      for (const item of dtcDatabase) {
        const cleanItemCode = item.code.replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
        if (cleanItemCode.startsWith(cleanQuery)) {
          matches.push(item);
          if (matches.length >= 5) break;
        }
      }
      setSuggestions(matches);
    }, 120);
    return () => clearTimeout(timer);
  }, [code]);

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

    setTimeout(() => {
      // 1. Try exact match (punctuation insensitive)
      const data = dtcDatabase.find(dtc => dtc.code.replace(/[^a-zA-Z0-9]/g, '').toUpperCase() === cleanCode);
      
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
        const partialMatches = dtcDatabase.filter(dtc => 
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
            
            <form onSubmit={(e) => handleSearch(e)} className="relative">
              <div className="relative">
                <input 
                  type="text" 
                  value={code} 
                  onChange={e => { setCode(e.target.value); setShowSuggestions(true); }}
                  onFocus={() => setShowSuggestions(true)}
                  placeholder="Ingrese código (ej: P0300)" 
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
