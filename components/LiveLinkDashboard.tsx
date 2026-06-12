import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Wifi, WifiOff, Activity, Gauge, Thermometer, Zap, Fuel, AlertTriangle, X, Radio, Building2, Phone, User, Car, CheckCircle2, Clock, Send, ShieldAlert, Sparkles, Plus } from 'lucide-react';

// ═══════════════════════════════════════════════════════════════
// LIVE LINK B2B DASHBOARD — Portal Multi-Tenant para Talleres VIP (GAM Costa Rica)
// Combina telemetría en vivo por WebSocket y Alertas DTC remotas desde Supabase/Nube
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

interface ClientDtcAlert {
  id: string;
  workshopId: string;
  clientName: string;
  clientPhone: string;
  vehicleMake: string;
  vehicleModel: string;
  vehicleYear: number;
  vehiclePlate: string;
  dtcCode: string;
  dtcDescription: string;
  severity: 'high' | 'medium' | 'low';
  timestamp: Date;
  status: 'pending' | 'contacted' | 'scheduled' | 'dismissed';
}

interface WorkshopTenant {
  id: string;
  name: string;
  location: string;
  primaryColor: string;
  secondaryColor: string;
  logoText: string;
  specialty: string;
}

const GAM_WORKSHOPS: WorkshopTenant[] = [
  {
    id: 'w-hemo',
    name: 'Hemosa Automotriz VIP',
    location: 'San José — Barrio México',
    primaryColor: '#0e2f5c', // Azul Marino
    secondaryColor: '#3b82f6',
    logoText: 'HA',
    specialty: 'Especialistas en Audi, VW, Porsche y Skoda',
  },
  {
    id: 'w-arce',
    name: 'Taller y Lubricentro Arce',
    location: 'Alajuela — Centro',
    primaryColor: '#8b0000', // Rojo Vino
    secondaryColor: '#ef4444',
    logoText: 'ARCE',
    specialty: 'Alta Gama Europea (BMW, Mercedes Benz, Audi)',
  },
  {
    id: 'w-autot',
    name: 'Autotronica Heredia',
    location: 'Heredia — Centro',
    primaryColor: '#008050', // Verde Esmeralda
    secondaryColor: '#10b981',
    logoText: 'AT',
    specialty: 'Laboratorio Electromecánico y Escaneo Computarizado',
  },
  {
    id: 'w-bmw',
    name: 'Automecatrónica BMW',
    location: 'Cartago — La Lima / Taras',
    primaryColor: '#003399', // Azul BMW
    secondaryColor: '#60a5fa',
    logoText: 'AM',
    specialty: 'Servicio Élite BMW, Land Rover y Mini',
  },
];

const INITIAL_MOCK_ALERTS: ClientDtcAlert[] = [
  {
    id: 'alert-1',
    workshopId: 'w-hemo',
    clientName: 'Carlos Montero G.',
    clientPhone: '+50688881111',
    vehicleMake: 'Audi',
    vehicleModel: 'Q5 Quattro',
    vehicleYear: 2023,
    vehiclePlate: 'AUD-506',
    dtcCode: 'P0300',
    dtcDescription: 'Fallo de encendido detectado en múltiples cilindros (Misfire)',
    severity: 'high',
    timestamp: new Date(Date.now() - 1000 * 60 * 15), // hace 15 min
    status: 'pending',
  },
  {
    id: 'alert-2',
    workshopId: 'w-arce',
    clientName: 'Dra. María Elena Rojas',
    clientPhone: '+50688882222',
    vehicleMake: 'Mercedes Benz',
    vehicleModel: 'GLC 300',
    vehicleYear: 2022,
    vehiclePlate: 'MB-777',
    dtcCode: 'P0420',
    dtcDescription: 'Eficiencia del sistema catalítico por debajo del umbral (Banco 1)',
    severity: 'medium',
    timestamp: new Date(Date.now() - 1000 * 60 * 45), // hace 45 min
    status: 'contacted',
  },
  {
    id: 'alert-3',
    workshopId: 'w-autot',
    clientName: 'Ing. Javier Solís',
    clientPhone: '+50688883333',
    vehicleMake: 'Toyota',
    vehicleModel: 'Hilux Revo',
    vehicleYear: 2024,
    vehiclePlate: 'HLX-900',
    dtcCode: 'P0101',
    dtcDescription: 'Rendimiento / Rango del circuito del sensor de flujo de masa de aire (MAF)',
    severity: 'medium',
    timestamp: new Date(Date.now() - 1000 * 60 * 120),
    status: 'scheduled',
  },
];

interface Props {
  onClose: () => void;
}

const buildLiveLinkWebSocketUrl = (input: string): string | null => {
  const raw = input.trim();
  if (!raw) return null;

  try {
    const hasScheme = raw.includes('://');
    const url = new URL(hasScheme ? raw : `http://${raw}`);
    if (!url.port) url.port = '8765';
    if (!url.pathname || url.pathname === '/') url.pathname = '/live';

    if (url.protocol === 'http:') url.protocol = 'ws:';
    else if (url.protocol === 'https:') url.protocol = 'wss:';
    else if (url.protocol !== 'ws:' && url.protocol !== 'wss:') return null;

    return url.toString();
  } catch {
    return null;
  }
};

export const LiveLinkDashboard: React.FC<Props> = ({ onClose }) => {
  // Pestañas del sistema
  const [activeTab, setActiveTab] = useState<'cloud_alerts' | 'local_wifi'>('cloud_alerts');

  // Estado Multi-Tenant (Taller Seleccionado)
  const [selectedWorkshop, setSelectedWorkshop] = useState<WorkshopTenant>(GAM_WORKSHOPS[0]);

  // Alertas DTC Remotas (Cloud)
  const [alerts, setAlerts] = useState<ClientDtcAlert[]>(INITIAL_MOCK_ALERTS);

  // Estado WebSocket Local
  const [ipAddress, setIpAddress] = useState('');
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [telemetry, setTelemetry] = useState<TelemetrySnapshot | null>(null);
  const [messageCount, setMessageCount] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);

  // Filtro de alertas por el taller activo
  const filteredAlerts = alerts.filter(a => a.workshopId === selectedWorkshop.id);

  // Sonido de alerta al entrar un código
  const playAlertSound = () => {
    try {
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, audioCtx.currentTime); // A5
      osc.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.3);
      gain.gain.setValueAtTime(0.3, audioCtx.currentTime);
      gain.gain.linearRampToValueAtTime(0, audioCtx.currentTime + 0.3);
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + 0.3);
    } catch (e) {
      // Audio context might be blocked by user policy
    }
  };

  // Simular la llegada de un código desde un cliente en carretera (Arma de Ventas)
  const handleSimulateRemoteAlert = () => {
    playAlertSound();
    const newAlert: ClientDtcAlert = {
      id: `alert-${Date.now()}`,
      workshopId: selectedWorkshop.id,
      clientName: 'Cliente VIP — ' + ['Don Fernando', 'Dra. Sofía', 'Lic. Rodrigo', 'Ing. Natalia'][Math.floor(Math.random() * 4)],
      clientPhone: '+506' + (80000000 + Math.floor(Math.random() * 9000000)).toString(),
      vehicleMake: ['BMW X5', 'Audi A4', 'Toyota Prado', 'Porsche Macan', 'Mercedes GLE'][Math.floor(Math.random() * 5)],
      vehicleModel: 'Turbo 4x4',
      vehicleYear: 2024,
      vehiclePlate: 'GAM-' + Math.floor(Math.random() * 999),
      dtcCode: ['P0300', 'P0171', 'P0420', 'P0700', 'P0102'][Math.floor(Math.random() * 5)],
      dtcDescription: 'Alerta Crítica: Sensor de oxígeno fuera de rango o fallo de inyección en banco 1.',
      severity: Math.random() > 0.5 ? 'high' : 'medium',
      timestamp: new Date(),
      status: 'pending',
    };
    setAlerts(prev => [newAlert, ...prev]);
  };

  const handleUpdateAlertStatus = (id: string, newStatus: ClientDtcAlert['status']) => {
    setAlerts(prev => prev.map(a => a.id === id ? { ...a, status: newStatus } : a));
  };

  // Conexión WebSocket Local
  const connectLocal = useCallback(() => {
    if (!ipAddress.trim()) {
      setError('Ingrese la dirección IP del teléfono');
      return;
    }
    setIsConnecting(true);
    setError(null);

    const url = buildLiveLinkWebSocketUrl(ipAddress);
    if (!url) {
      setError('Enlace Live Link inválido. Copie el enlace completo desde la app.');
      setIsConnecting(false);
      return;
    }
    if (!new URL(url).searchParams.get('token')) {
      setError('El enlace debe incluir el token de emparejamiento generado por la app.');
      setIsConnecting(false);
      return;
    }

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

  const disconnectLocal = useCallback(() => {
    wsRef.current?.close();
    wsRef.current = null;
    setIsConnected(false);
    setTelemetry(null);
    setMessageCount(0);
  }, []);

  useEffect(() => {
    return () => { wsRef.current?.close(); };
  }, []);

  const openWhatsApp = (phone: string, clientName: string, dtcCode: string) => {
    const text = `Hola ${clientName}, le saludamos de ${selectedWorkshop.name}. Nuestro sistema remoto de monitoreo detectó en su vehículo el código de avería ${dtcCode}. Tenemos el equipo y repuestos listos para revisarlo. ¿Gusta que le reservemos un espacio hoy mismo?`;
    window.open(`https://wa.me/${phone.replace('+', '')}?text=${encodeURIComponent(text)}`, '_blank');
  };

  const GaugeCard = ({ label, value, unit, max, color, icon: Icon }: {
    label: string; value: number; unit: string; max: number; color: string; icon: any;
  }) => {
    const pct = Math.min(100, (value / max) * 100);
    return (
      <div className="glass-inner rounded-xl p-4 border border-white/5 shadow-md">
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
            className="h-full rounded-full transition-all duration-500 shadow-[0_0_10px_rgba(255,255,255,0.2)]" 
            style={{ width: `${pct}%`, backgroundColor: color }}
          />
        </div>
      </div>
    );
  };

  return (
    <div className="fixed inset-0 bg-black/95 backdrop-blur-xl z-[70] flex flex-col animate-slide-up overflow-hidden">
      {/* ── BARRA SUPERIOR MULTI-TENANT (BRANDING DEL TALLER) ── */}
      <div 
        className="flex flex-col sm:flex-row sm:items-center justify-between p-4 sm:px-6 border-b transition-colors duration-500 gap-4"
        style={{ 
          backgroundColor: selectedWorkshop.primaryColor, 
          borderColor: `${selectedWorkshop.secondaryColor}40`,
          boxShadow: `0 4px 30px ${selectedWorkshop.primaryColor}80` 
        }}
      >
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center font-black text-white text-lg shadow-lg">
            {selectedWorkshop.logoText}
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-xl font-black text-white tracking-tight">{selectedWorkshop.name}</h2>
              <span className="px-2 py-0.5 rounded-full text-[10px] uppercase font-black tracking-widest bg-white text-black">Marca Blanca</span>
            </div>
            <p className="text-xs text-gray-200 opacity-90 flex items-center gap-1 font-medium mt-0.5">
              <Building2 size={12} className="opacity-70" />
              {selectedWorkshop.location} • <span className="text-white font-bold">{selectedWorkshop.specialty}</span>
            </p>
          </div>
        </div>

        {/* SELECTOR DE TALLER (PARA DEMOSTRACIÓN B2B) */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="text-xs font-bold text-gray-200 hidden md:block">Cambiar Taller VIP:</div>
          <select
            value={selectedWorkshop.id}
            onChange={e => {
              const ws = GAM_WORKSHOPS.find(w => w.id === e.target.value);
              if (ws) setSelectedWorkshop(ws);
            }}
            className="bg-black/40 border border-white/20 rounded-xl px-3 py-2 text-white font-bold text-xs focus:outline-none focus:border-white transition-all shadow-inner"
          >
            {GAM_WORKSHOPS.map(w => (
              <option key={w.id} value={w.id} className="bg-steel-900 text-white font-bold">
                {w.name} ({w.location.split('—')[0].trim()})
              </option>
            ))}
          </select>
          <button onClick={onClose} className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-xl transition-all ml-2">
            <X size={20} />
          </button>
        </div>
      </div>

      {/* ── BARRA DE PESTAÑAS Y ACCIONES DE DEMO ── */}
      <div className="glass-inner px-6 py-3 border-b border-white/10 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2 bg-steel-900/80 p-1 rounded-xl border border-steel-700">
          <button
            onClick={() => setActiveTab('cloud_alerts')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg font-bold text-xs transition-all whitespace-nowrap ${
              activeTab === 'cloud_alerts' ? 'bg-forge-500 text-black shadow-md' : 'text-gray-400 hover:text-white'
            }`}
          >
            <ShieldAlert size={16} />
            <span>Alertas Remotas en Vivo (Cloud DTC)</span>
          </button>
          <button
            onClick={() => setActiveTab('local_wifi')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg font-bold text-xs transition-all whitespace-nowrap ${
              activeTab === 'local_wifi' ? 'bg-forge-500 text-black shadow-md' : 'text-gray-400 hover:text-white'
            }`}
          >
            <Radio size={16} />
            <span>Telemetría en Taller (WiFi Local)</span>
          </button>
        </div>

        {/* BOTÓN DE DEMOSTRACIÓN DE VENTAS */}
        {activeTab === 'cloud_alerts' && (
          <button
            onClick={handleSimulateRemoteAlert}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all transform hover:scale-105 shadow-[0_0_20px_rgba(255,215,0,0.4)] bg-gradient-to-r from-amber-500 to-yellow-400 text-black"
          >
            <Sparkles size={16} className="animate-spin text-black" />
            <span>Simular Escaneo de Cliente en Carretera</span>
          </button>
        )}
      </div>

      {/* ── CONTENIDO PRINCIPAL ── */}
      <div className="flex-1 overflow-y-auto p-4 md:p-6 bg-gradient-to-b from-black/50 to-steel-950">
        
        {/* PESTAÑA 1: ALERTAS REMOTAS CLOUD (EL ARMA DE RETENCIÓN B2B) */}
        {activeTab === 'cloud_alerts' && (
          <div className="max-w-6xl mx-auto space-y-6">
            
            {/* Banner de Valor Comercial */}
            <div className="glass rounded-2xl p-6 border border-forge-500/30 bg-gradient-to-r from-forge-500/10 via-steel-900/50 to-transparent relative overflow-hidden">
              <div className="absolute top-0 right-0 w-96 h-96 bg-forge-500/10 rounded-full blur-3xl pointer-events-none" />
              <div className="relative z-10 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 text-forge-400 text-xs font-mono font-bold uppercase tracking-wider mb-1">
                    <Radio size={14} className="animate-pulse" />
                    Telemetría Cloud Multi-Tenant
                  </div>
                  <h3 className="text-xl font-black text-white">Centro de Monitoreo de Clientes VIP</h3>
                  <p className="text-sm text-gray-300 mt-1 max-w-2xl">
                    Cuando un cliente de <span className="font-bold text-white">{selectedWorkshop.name}</span> conecta su escáner en carretera, el error DTC se transmite a este panel. El asesor de servicio puede contactar al cliente de inmediato y agendar la cita.
                  </p>
                </div>
                <div className="flex items-center gap-3 bg-black/60 p-3 rounded-xl border border-white/10 font-mono text-xs">
                  <div className="text-center px-3 border-r border-white/10">
                    <div className="text-gray-400">Total Alertas</div>
                    <div className="text-lg font-black text-white">{filteredAlerts.length}</div>
                  </div>
                  <div className="text-center px-3 border-r border-white/10">
                    <div className="text-red-400">Pendientes</div>
                    <div className="text-lg font-black text-red-400">{filteredAlerts.filter(a => a.status === 'pending').length}</div>
                  </div>
                  <div className="text-center px-3">
                    <div className="text-green-400">Agendados</div>
                    <div className="text-lg font-black text-green-400">{filteredAlerts.filter(a => a.status === 'scheduled').length}</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Listado de Alertas DTC */}
            <div className="space-y-4">
              <h4 className="text-sm font-bold text-gray-400 uppercase tracking-widest font-mono flex items-center gap-2">
                <span>Alertas Entrantes en Tiempo Real</span>
                <span className="w-2 h-2 rounded-full bg-forge-500 animate-ping" />
              </h4>

              {filteredAlerts.length === 0 ? (
                <div className="glass rounded-2xl p-12 text-center text-gray-400 border border-white/5">
                  <ShieldAlert size={48} className="mx-auto mb-4 text-gray-600 animate-bounce" />
                  <p className="text-lg font-bold text-white">No hay alertas activas para este taller</p>
                  <p className="text-xs text-gray-400 mt-1">Haz clic en "Simular Escaneo de Cliente en Carretera" para ver el sistema en acción.</p>
                </div>
              ) : (
                filteredAlerts.map(alert => (
                  <div 
                    key={alert.id}
                    className={`glass rounded-2xl p-5 border transition-all duration-300 hover:border-white/30 ${
                      alert.status === 'pending' ? 'border-red-500/50 bg-red-500/5 shadow-[0_0_25px_rgba(239,68,68,0.15)]' :
                      alert.status === 'contacted' ? 'border-yellow-500/50 bg-yellow-500/5' : 'border-green-500/30 bg-green-500/5'
                    }`}
                  >
                    <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4">
                      
                      {/* Datos del Cliente y Carro */}
                      <div className="flex items-start gap-4">
                        <div className={`p-3 rounded-xl font-mono font-black text-sm border flex items-center justify-center min-w-[70px] ${
                          alert.severity === 'high' ? 'bg-red-500 text-white border-red-400 shadow-[0_0_15px_rgba(239,68,68,0.5)]' :
                          'bg-yellow-500 text-black border-yellow-400'
                        }`}>
                          {alert.dtcCode}
                        </div>

                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-black text-white text-base">{alert.clientName}</span>
                            <span className="text-xs text-gray-400 font-mono">• {alert.clientPhone}</span>
                          </div>
                          <div className="flex items-center gap-2 mt-1 text-xs text-gray-300 font-medium">
                            <Car size={14} className="text-forge-500 shrink-0" />
                            <span className="font-bold text-white">{alert.vehicleMake} {alert.vehicleModel} ({alert.vehicleYear})</span>
                            <span className="px-2 py-0.5 rounded bg-steel-800 border border-steel-700 text-gray-300 font-mono text-[10px]">
                              Placa: {alert.vehiclePlate}
                            </span>
                          </div>
                          <p className="text-sm text-gray-200 mt-2 font-medium bg-black/40 p-2.5 rounded-xl border border-white/5 flex items-start gap-2">
                            <AlertTriangle size={16} className="text-red-400 mt-0.5 shrink-0" />
                            <span>{alert.dtcDescription}</span>
                          </p>
                        </div>
                      </div>

                      {/* Estado y Botón de Acción Proactiva (WhatsApp / Cita) */}
                      <div className="flex items-center justify-between lg:justify-end w-full lg:w-auto gap-3 pt-4 lg:pt-0 border-t lg:border-t-0 border-white/10 shrink-0">
                        <div className="text-left lg:text-right">
                          <div className="text-[11px] text-gray-400 flex items-center gap-1 justify-start lg:justify-end">
                            <Clock size={12} />
                            <span>{alert.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                          </div>
                          <div className="mt-1">
                            {alert.status === 'pending' && <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-red-500/20 text-red-400 border border-red-500/30">Pendiente de Contacto</span>}
                            {alert.status === 'contacted' && <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-yellow-500/20 text-yellow-400 border border-yellow-500/30">Cliente Contactado</span>}
                            {alert.status === 'scheduled' && <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-green-500/20 text-green-400 border border-green-500/30">Cita Agendada</span>}
                          </div>
                        </div>

                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => openWhatsApp(alert.clientPhone, alert.clientName, alert.dtcCode)}
                            className="flex items-center gap-2 bg-green-600 text-white font-bold px-4 py-2 rounded-xl text-xs hover:bg-green-500 transition-all shadow-[0_0_15px_rgba(22,163,74,0.4)]"
                            title="Llamar o escribir al cliente de inmediato"
                          >
                            <Phone size={14} />
                            <span>Contactar (WhatsApp)</span>
                          </button>

                          <div className="flex gap-1 bg-steel-800 p-1 rounded-xl border border-steel-700">
                            {alert.status !== 'contacted' && (
                              <button
                                onClick={() => handleUpdateAlertStatus(alert.id, 'contacted')}
                                className="px-2 py-1 text-[10px] font-bold text-yellow-400 hover:bg-yellow-500/20 rounded-lg transition-all"
                              >
                                Contactado
                              </button>
                            )}
                            {alert.status !== 'scheduled' && (
                              <button
                                onClick={() => handleUpdateAlertStatus(alert.id, 'scheduled')}
                                className="px-2 py-1 text-[10px] font-bold text-green-400 hover:bg-green-500/20 rounded-lg transition-all"
                              >
                                Agendar Cita
                              </button>
                            )}
                          </div>
                        </div>

                      </div>

                    </div>
                  </div>
                ))
              )}
            </div>

          </div>
        )}

        {/* PESTAÑA 2: TELEMETRÍA EN TALLER WIF LOCAL */}
        {activeTab === 'local_wifi' && (
          <div className="max-w-6xl mx-auto space-y-6">
            {!isConnected ? (
              <div className="max-w-md mx-auto my-12">
                <div className="glass rounded-2xl p-8 border border-white/10 shadow-2xl">
                  <div className="flex items-center gap-3 mb-6">
                    <div className="p-3 rounded-xl bg-forge-500/20 text-forge-400 border border-forge-500/30">
                      <Wifi size={24} />
                    </div>
                    <div>
                      <h3 className="text-xl font-black text-white tracking-tight">Conexión WiFi en Taller</h3>
                      <p className="text-xs text-gray-400 mt-0.5">Telemetría en tiempo real desde el vehículo</p>
                    </div>
                  </div>
                  
                  <p className="text-sm text-gray-300 mb-6 leading-relaxed">
                    Pegue el enlace seguro que muestra la app MEET en la pantalla <span className="font-bold text-white">Live Link</span>. Ambos dispositivos deben estar en la misma red WiFi del taller.
                  </p>

                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={ipAddress}
                      onChange={e => setIpAddress(e.target.value)}
                      placeholder="http://192.168.1.100:8765/live?token=..."
                      className="flex-1 px-4 py-3 bg-steel-900 border border-steel-700 rounded-xl text-white text-sm font-mono placeholder-gray-500 focus:outline-none focus:border-forge-500 transition-colors"
                      onKeyDown={e => e.key === 'Enter' && connectLocal()}
                    />
                    <button
                      onClick={connectLocal}
                      disabled={isConnecting}
                      className="px-6 py-3 bg-forge-500 text-black font-black text-sm rounded-xl hover:bg-forge-400 transition-all disabled:opacity-50 shadow-[0_0_20px_rgba(0,240,255,0.4)] whitespace-nowrap"
                    >
                      {isConnecting ? 'Conectando...' : 'Conectar'}
                    </button>
                  </div>

                  {error && (
                    <div className="mt-4 flex items-start gap-2 text-red-400 text-xs bg-red-500/10 p-4 rounded-xl border border-red-500/20">
                      <WifiOff size={16} className="mt-0.5 shrink-0" />
                      <span className="font-medium">{error}</span>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <>
                {/* Status bar */}
                <div className="flex items-center justify-between mb-6 glass rounded-xl px-6 py-4 border border-green-500/30 bg-green-500/5">
                  <div className="flex items-center gap-3">
                    <div className="w-3 h-3 rounded-full bg-green-400 animate-ping shadow-[0_0_15px_#22c55e]" />
                    <span className="text-green-400 text-sm font-black tracking-wider uppercase">EN VIVO</span>
                    <div className="w-px h-4 bg-white/20 mx-2" />
                    <span className="text-white font-bold text-sm">{telemetry?.vehicleName || 'Vehículo en Diagnóstico'}</span>
                    <span className="text-gray-400 text-xs font-mono">• {messageCount} paquetes recibidos</span>
                  </div>
                  <button onClick={disconnectLocal} className="px-4 py-2 bg-red-500/20 text-red-400 hover:bg-red-500 hover:text-white rounded-xl text-xs font-bold transition-all border border-red-500/30">
                    Desconectar
                  </button>
                </div>

                {/* Health Score */}
                {telemetry && telemetry.healthScore >= 0 && (
                  <div className="mb-6 glass rounded-2xl p-6 border border-white/10 text-center max-w-md mx-auto bg-gradient-to-t from-steel-900/50 to-transparent">
                    <div className="text-xs text-gray-400 mb-1 font-mono font-bold uppercase tracking-wider">Health Score del Vehículo</div>
                    <div className={`text-6xl font-black tracking-tighter ${
                      telemetry.healthScore >= 80 ? 'text-green-400 drop-shadow-[0_0_20px_rgba(34,197,94,0.4)]' :
                      telemetry.healthScore >= 60 ? 'text-yellow-400 drop-shadow-[0_0_20px_rgba(234,179,8,0.4)]' : 'text-red-400 drop-shadow-[0_0_20px_rgba(239,68,68,0.4)]'
                    }`}>
                      {telemetry.healthScore}
                      <span className="text-2xl font-bold text-gray-500 ml-1">/100</span>
                    </div>
                  </div>
                )}

                {/* Gauge Grid */}
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
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
                  <div className="glass rounded-2xl p-6 border border-red-500/50 bg-red-500/10 shadow-[0_0_30px_rgba(239,68,68,0.2)]">
                    <div className="flex items-center gap-3 mb-4">
                      <div className="p-2.5 bg-red-500 text-white rounded-xl shadow-lg">
                        <AlertTriangle size={20} />
                      </div>
                      <div>
                        <h4 className="text-red-400 font-black text-lg">Códigos de Avería DTC Detectados en Vivo</h4>
                        <p className="text-xs text-gray-300">Estos códigos acaban de ser capturados por el escáner conectado en la bahía</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-3">
                      {telemetry.activeDtcs.map(dtc => (
                        <span key={dtc} className="px-4 py-2 bg-red-950/80 text-red-300 rounded-xl text-sm font-mono font-black border border-red-500/40 shadow-md flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-red-500 animate-ping" />
                          <span>{dtc}</span>
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}

      </div>
    </div>
  );
};
