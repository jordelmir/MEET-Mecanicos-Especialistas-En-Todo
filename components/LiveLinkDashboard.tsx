import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Wifi, WifiOff, Activity, Gauge, Thermometer, Zap, Fuel, AlertTriangle, X, Radio } from 'lucide-react';

// ═══════════════════════════════════════════════════════════════
// LIVE LINK DASHBOARD — Real-Time Telemetry from MEET APK
// Connects via WebSocket to the phone's embedded Ktor server
// ═══════════════════════════════════════════════════════════════

interface TelemetrySnapshot {
  rpm: number;
  speed: number;
  coolantTemp: number;
  intakeTemp: number;
  throttlePos: number;
  engineLoad: number;
  fuelPressure: number;
  timingAdvance: number;
  mafRate: number;
  voltage: number;
  fuelTrim1: number;
  fuelTrim2: number;
  healthScore: number;
  activeDtcs: string[];
  vehicleName: string;
}

interface LiveLinkMessage {
  type: 'welcome' | 'telemetry' | 'dtc_alert';
  payload: string;
  timestamp: number;
}

interface Props {
  onClose: () => void;
}

export const LiveLinkDashboard: React.FC<Props> = ({ onClose }) => {
  const [ipAddress, setIpAddress] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [telemetry, setTelemetry] = useState<TelemetrySnapshot | null>(null);
  const [lastUpdate, setLastUpdate] = useState<number>(0);
  const [messageCount, setMessageCount] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);

  const connect = useCallback(() => {
    if (!ipAddress.trim()) {
      setError('Ingrese la dirección IP del teléfono');
      return;
    }

    setIsConnecting(true);
    setError(null);

    const url = ipAddress.includes('://') 
      ? ipAddress.replace('http', 'ws') + '/live'
      : `ws://${ipAddress.trim()}:8765/live`;

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        setIsConnecting(false);
        setError(null);
      };

      ws.onmessage = (event) => {
        try {
          const msg: LiveLinkMessage = JSON.parse(event.data);
          setMessageCount(prev => prev + 1);
          setLastUpdate(Date.now());

          if (msg.type === 'telemetry') {
            const data: TelemetrySnapshot = JSON.parse(msg.payload);
            setTelemetry(data);
          } else if (msg.type === 'dtc_alert') {
            const dtcs: string[] = JSON.parse(msg.payload);
            setTelemetry(prev => prev ? { ...prev, activeDtcs: dtcs } : null);
          }
        } catch (e) {
          console.warn('LiveLink parse error:', e);
        }
      };

      ws.onclose = () => {
        setIsConnected(false);
        setIsConnecting(false);
        wsRef.current = null;
      };

      ws.onerror = () => {
        setError('No se pudo conectar. Verifique que el teléfono esté en la misma red WiFi y que Live Link esté activo.');
        setIsConnecting(false);
        setIsConnected(false);
      };
    } catch (e) {
      setError('Dirección inválida');
      setIsConnecting(false);
    }
  }, [ipAddress]);

  const disconnect = useCallback(() => {
    wsRef.current?.close();
    wsRef.current = null;
    setIsConnected(false);
    setTelemetry(null);
    setMessageCount(0);
  }, []);

  useEffect(() => {
    return () => { wsRef.current?.close(); };
  }, []);

  // ── Gauge component ──
  const GaugeCard = ({ label, value, unit, max, color, icon: Icon }: {
    label: string; value: number; unit: string; max: number; color: string; icon: any;
  }) => {
    const pct = Math.min(100, (value / max) * 100);
    return (
      <div className="glass-inner rounded-xl p-4 border border-white/5">
        <div className="flex items-center gap-2 mb-2">
          <Icon size={14} style={{ color }} />
          <span className="text-xs text-gray-400 font-medium">{label}</span>
        </div>
        <div className="text-2xl font-bold text-white tracking-tight">
          {typeof value === 'number' ? value.toFixed(value % 1 !== 0 ? 1 : 0) : '—'}
          <span className="text-xs text-gray-500 ml-1">{unit}</span>
        </div>
        <div className="mt-2 h-1.5 rounded-full bg-steel-700 overflow-hidden">
          <div 
            className="h-full rounded-full transition-all duration-500" 
            style={{ width: `${pct}%`, backgroundColor: color }}
          />
        </div>
      </div>
    );
  };

  return (
    <div className="fixed inset-0 bg-black/90 backdrop-blur-md z-[70] flex flex-col animate-slide-up overflow-auto">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-white/10">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg ${isConnected ? 'bg-green-500/20 text-green-400' : 'bg-steel-700 text-gray-400'}`}>
            <Radio size={20} />
          </div>
          <div>
            <h2 className="text-lg font-bold text-white">Live Link Dashboard</h2>
            <p className="text-xs text-gray-400">
              {isConnected 
                ? `Conectado • ${messageCount} mensajes recibidos`
                : 'Telemetría en tiempo real desde la app MEET'
              }
            </p>
          </div>
        </div>
        <button onClick={onClose} className="p-2 text-gray-400 hover:text-white transition-colors">
          <X size={20} />
        </button>
      </div>

      <div className="flex-1 overflow-auto p-4 md:p-6">
        {/* Connection Panel */}
        {!isConnected && (
          <div className="max-w-md mx-auto mb-6">
            <div className="glass rounded-xl p-6 border border-white/10">
              <div className="flex items-center gap-2 mb-4">
                <Wifi size={18} className="text-forge-500" />
                <h3 className="text-white font-bold">Conectar al Teléfono</h3>
              </div>
              
              <p className="text-sm text-gray-400 mb-4">
                Ingrese la dirección IP que muestra la app MEET en la pantalla Live Link. 
                Ambos dispositivos deben estar en la misma red WiFi.
              </p>

              <div className="flex gap-2">
                <input
                  type="text"
                  value={ipAddress}
                  onChange={e => setIpAddress(e.target.value)}
                  placeholder="192.168.1.100"
                  className="flex-1 px-4 py-3 bg-steel-800 border border-steel-600 rounded-lg text-white text-sm font-mono placeholder-gray-500 focus:outline-none focus:border-forge-500 transition-colors"
                  onKeyDown={e => e.key === 'Enter' && connect()}
                />
                <button
                  onClick={connect}
                  disabled={isConnecting}
                  className="px-6 py-3 bg-forge-500 text-black font-bold text-sm rounded-lg hover:bg-forge-400 transition-all disabled:opacity-50 whitespace-nowrap"
                >
                  {isConnecting ? 'Conectando...' : 'Conectar'}
                </button>
              </div>

              {error && (
                <div className="mt-3 flex items-start gap-2 text-red-400 text-xs bg-red-500/10 p-3 rounded-lg border border-red-500/20">
                  <WifiOff size={14} className="mt-0.5 shrink-0" />
                  <span>{error}</span>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Connected — Telemetry Dashboard */}
        {isConnected && (
          <>
            {/* Status bar */}
            <div className="flex items-center justify-between mb-4 glass-inner rounded-lg px-4 py-2 border border-green-500/20">
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
                <span className="text-green-400 text-xs font-bold">EN VIVO</span>
                {telemetry?.vehicleName && (
                  <span className="text-gray-400 text-xs ml-2">• {telemetry.vehicleName}</span>
                )}
              </div>
              <button onClick={disconnect} className="text-xs text-red-400 hover:text-red-300 font-bold">
                Desconectar
              </button>
            </div>

            {/* Health Score */}
            {telemetry && telemetry.healthScore >= 0 && (
              <div className="mb-4 glass rounded-xl p-4 border border-white/10 text-center">
                <div className="text-xs text-gray-400 mb-1">Health Score</div>
                <div className={`text-5xl font-black ${
                  telemetry.healthScore >= 80 ? 'text-green-400' :
                  telemetry.healthScore >= 60 ? 'text-yellow-400' : 'text-red-400'
                }`}>
                  {telemetry.healthScore}
                </div>
              </div>
            )}

            {/* Gauge Grid */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 mb-4">
              <GaugeCard label="RPM" value={telemetry?.rpm || 0} unit="rpm" max={8000} color="#39FF14" icon={Gauge} />
              <GaugeCard label="Velocidad" value={telemetry?.speed || 0} unit="km/h" max={240} color="#00AAFF" icon={Activity} />
              <GaugeCard label="Temp. Refrigerante" value={telemetry?.coolantTemp || 0} unit="°C" max={130} color="#FF6B35" icon={Thermometer} />
              <GaugeCard label="Voltaje" value={telemetry?.voltage || 0} unit="V" max={16} color="#FFD700" icon={Zap} />
              <GaugeCard label="Carga Motor" value={telemetry?.engineLoad || 0} unit="%" max={100} color="#9D4EDD" icon={Activity} />
              <GaugeCard label="Pos. Acelerador" value={telemetry?.throttlePos || 0} unit="%" max={100} color="#39FF14" icon={Gauge} />
              <GaugeCard label="Fuel Trim S1" value={telemetry?.fuelTrim1 || 0} unit="%" max={25} color="#00BFFF" icon={Fuel} />
              <GaugeCard label="MAF" value={telemetry?.mafRate || 0} unit="g/s" max={300} color="#FF003C" icon={Activity} />
            </div>

            {/* Active DTCs */}
            {telemetry?.activeDtcs && telemetry.activeDtcs.length > 0 && (
              <div className="glass rounded-xl p-4 border border-red-500/30 bg-red-500/5">
                <div className="flex items-center gap-2 mb-2">
                  <AlertTriangle size={16} className="text-red-400" />
                  <span className="text-red-400 font-bold text-sm">Códigos DTC Activos</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {telemetry.activeDtcs.map(dtc => (
                    <span key={dtc} className="px-3 py-1 bg-red-500/20 text-red-300 rounded-lg text-xs font-mono font-bold border border-red-500/30">
                      {dtc}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* No data yet */}
            {!telemetry && (
              <div className="text-center py-12 text-gray-400">
                <Activity size={32} className="mx-auto mb-3 animate-pulse" />
                <p className="text-sm">Esperando datos de telemetría...</p>
                <p className="text-xs mt-1">Inicie una sesión de diagnóstico en la app MEET</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};
