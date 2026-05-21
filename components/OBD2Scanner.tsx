import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Search, AlertTriangle, Info, ShieldAlert, X, Wrench, Activity, Play, Square, Save, ChevronDown, Zap, Gauge, Radio, BarChart3, Download } from 'lucide-react';
import dtcDatabase from '../dtc_database.json';
import { OscilloscopeCanvas } from './OscilloscopeCanvas';
import { SignalGenerator, SignalAnalyzer, SIGNAL_LIBRARY, type SignalDiagnosis, type SignalDefinition } from '../services/signalAnalysis';
import type { OscilloscopeMeasurement, WorkOrder, Client } from '../types';

interface OBD2ScannerProps {
  onClose: () => void;
  currentUser?: Client;
  workOrders?: WorkOrder[];
  onSaveMeasurement?: (measurement: OscilloscopeMeasurement) => void;
}

type TabMode = 'dtc' | 'oscilloscope';

export function OBD2Scanner({ onClose, currentUser, workOrders, onSaveMeasurement }: OBD2ScannerProps) {
  const [activeTab, setActiveTab] = useState<TabMode>('oscilloscope');

  return (
    <div className="fixed inset-0 bg-black/90 backdrop-blur-md z-[90] flex items-center justify-center p-2 sm:p-4 animate-fade-in">
      <div className="bg-steel-900 rounded-2xl w-full max-w-4xl border border-forge-500/20 overflow-hidden flex flex-col shadow-[0_0_80px_rgba(0,240,255,0.08)] animate-slide-up" style={{ maxHeight: '95vh' }}>

        {/* Header */}
        <div className="p-4 border-b border-white/10 bg-black flex justify-between items-center flex-shrink-0">
          <div className="flex items-center gap-3">
            <div className="bg-forge-500/20 p-2 rounded-lg text-forge-500 border border-forge-500/30">
              <Activity size={20} />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-display tracking-wider">MEET Diagnostic Pro</h2>
              <p className="text-[9px] text-steel-400 font-mono uppercase tracking-widest mt-0.5">Motor de Análisis Profesional v1.0</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {/* Tab Buttons */}
            <div className="flex bg-black/50 rounded-lg border border-white/10 overflow-hidden">
              <button onClick={() => setActiveTab('oscilloscope')} className={`px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider transition-all ${activeTab === 'oscilloscope' ? 'bg-forge-500 text-black' : 'text-steel-400 hover:text-white'}`}>
                <span className="flex items-center gap-1"><Activity size={12} /> Osciloscopio</span>
              </button>
              <button onClick={() => setActiveTab('dtc')} className={`px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider transition-all ${activeTab === 'dtc' ? 'bg-forge-500 text-black' : 'text-steel-400 hover:text-white'}`}>
                <span className="flex items-center gap-1"><Search size={12} /> DTC</span>
              </button>
            </div>
            <button onClick={onClose} className="p-2 text-steel-400 hover:text-white bg-white/5 rounded-lg transition-colors">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {activeTab === 'dtc' ? <DTCAnalyzerTab /> : (
            <OscilloscopeTab currentUser={currentUser} workOrders={workOrders} onSaveMeasurement={onSaveMeasurement} />
          )}
        </div>

        <div className="bg-black/80 p-2 border-t border-white/5 text-center flex-shrink-0">
          <p className="text-[9px] text-steel-500 font-mono uppercase tracking-wider">
            Powered by MEET Engine AI · Análisis de Señales en Tiempo Real
          </p>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// TAB 1: DTC ANALYZER (preserved from original)
// ═══════════════════════════════════════════════════════════════

function DTCAnalyzerTab() {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<any>(null);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanCode = code.trim().toUpperCase();
    if (!cleanCode) return;
    setSearched(true); setLoading(true); setResult(null);
    try {
      await new Promise(resolve => setTimeout(resolve, 600));
      const data = dtcDatabase.find(dtc => dtc.code === cleanCode);
      if (!data) { setResult(null); } else {
        setResult({
          code: data.code, title: data.descriptionEs, desc: data.descriptionEn,
          fix: data.possibleCauses,
          severity: data.severity === 'HIGH' ? 'high' : data.severity === 'MODERATE' ? 'medium' : 'low'
        });
      }
    } catch { setResult(null); } finally { setLoading(false); }
  };

  return (
    <div className="p-6">
      <form onSubmit={handleSearch} className="mb-6">
        <label className="block text-xs font-bold text-steel-300 uppercase tracking-wider mb-2">Ingrese Código DTC (Ej: P0300)</label>
        <div className="relative">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-forge-500" size={20} />
          <input type="text" value={code} onChange={e => setCode(e.target.value)} placeholder="P0..."
            className="w-full bg-black border-2 border-steel-700 rounded-xl pl-12 pr-28 py-4 text-xl font-bold text-white uppercase focus:border-forge-500 outline-none transition-all placeholder:text-steel-600 font-mono" autoFocus />
          <button type="submit" disabled={loading} className="absolute right-2 top-1/2 -translate-y-1/2 bg-forge-500 text-black px-4 py-2 rounded-lg font-bold hover:bg-forge-400 transition-colors disabled:opacity-50">
            {loading ? '...' : 'Analizar'}
          </button>
        </div>
      </form>
      {loading && <div className="flex justify-center py-8"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-forge-500"></div></div>}
      {!loading && searched && (
        <div className="animate-fade-in">
          {result ? (
            <div className={`rounded-xl p-5 border-l-4 ${result.severity === 'high' ? 'bg-red-500/10 border-red-500' : result.severity === 'medium' ? 'bg-yellow-500/10 border-yellow-500' : 'bg-blue-500/10 border-blue-500'}`}>
              <div className="flex justify-between items-start mb-4">
                <div className="flex items-center gap-2">
                  {result.severity === 'high' ? <ShieldAlert className="text-red-500" /> : <Info className="text-forge-500" />}
                  <h3 className="text-2xl font-bold text-white font-mono">{result.code}</h3>
                </div>
                <span className={`px-3 py-1 rounded text-[10px] font-bold uppercase tracking-wider ${result.severity === 'high' ? 'bg-red-500/20 text-red-400' : result.severity === 'medium' ? 'bg-yellow-500/20 text-yellow-400' : 'bg-blue-500/20 text-blue-400'}`}>
                  {result.severity === 'high' ? 'Crítico' : result.severity === 'medium' ? 'Moderado' : 'Leve'}
                </span>
              </div>
              <h4 className="text-lg font-bold text-white mb-2">{result.title}</h4>
              <p className="text-steel-300 text-sm mb-4 leading-relaxed">{result.desc}</p>
              <div className="bg-black/30 rounded-lg p-4 border border-white/5">
                <h5 className="text-[10px] text-forge-500 font-mono uppercase tracking-widest mb-2 flex items-center gap-2"><Wrench size={12} /> Solución Recomendada</h5>
                <p className="text-white text-sm font-medium">{result.fix}</p>
              </div>
            </div>
          ) : (
            <div className="text-center py-8 glass-inner rounded-xl border border-steel-700/50">
              <AlertTriangle size={48} className="mx-auto text-steel-500 mb-4 opacity-50" />
              <p className="text-white font-bold text-lg">Código no encontrado</p>
              <p className="text-steel-400 text-sm mt-1">El código no existe en la base de datos o el formato es incorrecto.</p>
            </div>
          )}
        </div>
      )}
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
