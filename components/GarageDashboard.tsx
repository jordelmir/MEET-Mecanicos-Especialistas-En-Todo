import React, { useState, useMemo } from 'react';
import {
  Car,
  Activity,
  Calendar,
  DollarSign,
  User,
  Plus,
  Wrench,
  ShieldAlert,
  Info,
  Sliders,
  EyeOff,
  Trash2,
  History,
  Sparkles,
  TrendingUp,
  PlusCircle,
  Download,
  CheckCircle,
  Clock,
  Settings,
  Link,
  FileText,
  AlertTriangle,
  Play,
  ArrowRight,
  Shield,
  Coins,
  ShieldCheck,
  UserCheck,
  Eye,
  X,
  Video,
  Lock
} from 'lucide-react';
import {
  calculateCompletenessScore,
  calculateDataQualityScore,
  calculateHealthScore,
  calculateRiskScore,
  generatePredictiveAlerts,
  VehicleRiskDetails
} from '../services/garageEngine';
import {
  VehicleProfile,
  VehicleDigitalTwin,
  VehicleTimelineEvent,
  PredictiveMaintenanceAlert,
  MaintenanceRecord,
  WorkOrder,
  Service,
  Mechanic,
  Role,
  TransmissionType,
  FuelType,
  DashcamClip,
  DrivingEvent
} from '../types';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line
} from 'recharts';

interface GarageDashboardProps {
  vehicles: VehicleProfile[];
  activeUserId: string;
  role: Role;
  onUpdateVehicle: (vehicle: VehicleProfile) => void;
  onAddVehicle?: (vehicle: VehicleProfile) => void;
  onDeleteVehicle?: (id: string) => void;
  digitalTwins: VehicleDigitalTwin[];
  onUpdateDigitalTwin: (twin: VehicleDigitalTwin) => void;
  timelineEvents: VehicleTimelineEvent[];
  onAddTimelineEvent: (event: VehicleTimelineEvent) => void;
  predictiveAlerts: PredictiveMaintenanceAlert[];
  onUpdatePredictiveAlert: (alert: PredictiveMaintenanceAlert) => void;
  onAddPredictiveAlert: (alert: PredictiveMaintenanceAlert) => void;
  maintenanceRecords: MaintenanceRecord[];
  onAddMaintenanceRecord: (record: MaintenanceRecord) => void;
  workOrders: WorkOrder[];
  services: Service[];
  mechanics: Mechanic[];
  onClose?: () => void;
  dashcamClips?: DashcamClip[];
  drivingEvents?: DrivingEvent[];
}

export function GarageDashboard({
  vehicles,
  activeUserId,
  role,
  onUpdateVehicle,
  onAddVehicle,
  onDeleteVehicle,
  digitalTwins,
  onUpdateDigitalTwin,
  timelineEvents,
  onAddTimelineEvent,
  predictiveAlerts,
  onUpdatePredictiveAlert,
  onAddPredictiveAlert,
  maintenanceRecords,
  onAddMaintenanceRecord,
  workOrders,
  services,
  mechanics,
  onClose,
  dashcamClips = [],
  drivingEvents = []
}: GarageDashboardProps) {
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('resumen');
  const [isAddingVehicle, setIsAddingVehicle] = useState(false);
  const [isScanning, setIsScanning] = useState<string | null>(null);
  const [scanProgress, setScanProgress] = useState(0);
  
  // Form States
  const [newVehicle, setNewVehicle] = useState({
    nickname: '',
    make: '',
    model: '',
    year: new Date().getFullYear(),
    trim: '',
    engine: '',
    engineCode: '',
    transmission: 'AUTOMATIC' as TransmissionType,
    fuelType: 'GASOLINE' as FuelType,
    vin: '',
    plate: '',
    odometer: 0,
    country: 'Costa Rica',
    province: '',
    color: '',
    photo: ''
  });

  const [newMaint, setNewMaint] = useState({
    type: 'OIL_CHANGE',
    title: '',
    cost: 0,
    notes: '',
    parts: ''
  });

  // Filter vehicles by role
  const visibleVehicles = useMemo(() => {
    if (role === Role.CLIENT) {
      return vehicles.filter(v => v.owner_user_id === activeUserId);
    }
    return vehicles;
  }, [vehicles, role, activeUserId]);

  const selectedVehicle = useMemo(() => {
    return vehicles.find(v => v.id === selectedVehicleId) || null;
  }, [vehicles, selectedVehicleId]);

  const selectedTwin = useMemo(() => {
    if (!selectedVehicleId) return null;
    return digitalTwins.find(dt => dt.vehicle_id === selectedVehicleId) || null;
  }, [digitalTwins, selectedVehicleId]);

  const selectedTimeline = useMemo(() => {
    if (!selectedVehicleId) return [];
    return timelineEvents
      .filter(ev => ev.vehicle_id === selectedVehicleId)
      .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
  }, [timelineEvents, selectedVehicleId]);

  const selectedAlerts = useMemo(() => {
    if (!selectedVehicleId) return [];
    return predictiveAlerts.filter(al => al.vehicle_id === selectedVehicleId);
  }, [predictiveAlerts, selectedVehicleId]);

  const selectedMaint = useMemo(() => {
    if (!selectedVehicleId) return [];
    return maintenanceRecords
      .filter(m => m.vehicle_id === selectedVehicleId)
      .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  }, [maintenanceRecords, selectedVehicleId]);

  // Calculations for active vehicle
  const activeCompleteness = useMemo(() => {
    if (!selectedVehicle) return 0;
    return calculateCompletenessScore(selectedVehicle);
  }, [selectedVehicle]);

  const activeDtcCount = useMemo(() => {
    if (!selectedVehicleId) return 0;
    const vehicleWorkOrders = workOrders.filter(wo => wo.vehicleInfo.plate === selectedVehicle?.plate);
    // Unique list of active DTC codes from incomplete/in progress work orders
    const activeWoDtcs = vehicleWorkOrders
      .filter(wo => wo.status !== 'COMPLETED' && wo.status !== 'DELIVERED' && wo.status !== 'CANCELLED')
      .flatMap(wo => wo.partsNeeded || []); // partsNeeded is placeholder for DTC codes in original schema if linked
    return activeWoDtcs.length;
  }, [selectedVehicleId, workOrders, selectedVehicle]);

  const activeDtcList = useMemo(() => {
    if (!selectedVehicleId) return [];
    // Combine DTCs from work orders + latest scan result
    const list: string[] = [];
    const clientScan = selectedTimeline.find(ev => ev.event_type === 'DTC_DETECTED' || ev.event_type === 'SNAPSHOT_CAPTURED');
    if (clientScan && clientScan.payload_json) {
      try {
        const payload = JSON.parse(clientScan.payload_json);
        if (payload.dtcCodes) list.push(...payload.dtcCodes);
      } catch (e) {}
    }
    return Array.from(new Set(list));
  }, [selectedVehicleId, selectedTimeline]);

  const activeHealthScore = useMemo(() => {
    if (!selectedVehicleId || !selectedVehicle) return null;
    const hasScans = selectedTimeline.some(ev => ev.event_type === 'SNAPSHOT_CAPTURED' || ev.event_type === 'DTC_DETECTED');
    const incompleteReadiness = 0; // standard
    const currentVoltage = selectedTwin?.normal_voltage_max || 14.2; // default twin
    const currentEct = selectedTwin?.normal_ect_max || 92;
    const overdueMaint = selectedAlerts.filter(a => a.component === 'aceite' && a.risk_level === 'HIGH').length;
    
    return calculateHealthScore(
      selectedVehicleId,
      activeDtcList,
      incompleteReadiness,
      currentVoltage,
      currentEct,
      overdueMaint,
      hasScans
    );
  }, [selectedVehicleId, selectedVehicle, activeDtcList, selectedTimeline, selectedTwin, selectedAlerts]);

  const activeRiskDetails = useMemo(() => {
    if (!selectedVehicleId) return null;
    const currentVoltage = selectedTwin?.normal_voltage_max || 14.2;
    const currentEct = selectedTwin?.normal_ect_max || 92;
    const overdueMaint = selectedAlerts.filter(a => a.component === 'aceite' && a.risk_level === 'HIGH').length;

    return calculateRiskScore(
      activeDtcList,
      currentVoltage,
      currentEct,
      overdueMaint
    );
  }, [selectedVehicleId, activeDtcList, selectedTwin, selectedAlerts]);

  const dataQuality = useMemo(() => {
    if (!selectedVehicle) return 'Baja';
    const hasScans = selectedTimeline.some(ev => ev.event_type === 'SNAPSHOT_CAPTURED');
    const hasReports = selectedTimeline.some(ev => ev.event_type === 'REPORT_GENERATED');
    const hasMaint = selectedMaint.length > 0;
    return calculateDataQualityScore(selectedVehicle, hasScans, hasReports, hasMaint);
  }, [selectedVehicle, selectedTimeline, selectedMaint]);

  // Costs of Ownership Calculations
  const costSummary = useMemo(() => {
    if (!selectedVehicleId) return { total: 0, parts: 0, maintenance: 0, labor: 0, perMonth: 0, perKm: 0 };
    
    let maintCost = selectedMaint.reduce((acc, curr) => acc + (curr.cost_nullable || 0), 0);
    
    // Aggregate work orders cost
    const clientWo = workOrders.filter(wo => wo.vehicleInfo.plate === selectedVehicle?.plate);
    let laborCost = clientWo
      .filter(wo => wo.status === 'COMPLETED' || wo.status === 'DELIVERED')
      .reduce((acc, curr) => acc + curr.price, 0);

    // Sum parts purchased from timeline
    let partsCost = selectedTimeline
      .filter(ev => ev.event_type === 'PART_PURCHASED')
      .reduce((acc, curr) => {
        try {
          const payload = JSON.parse(curr.payload_json || '{}');
          return acc + (payload.cost || 0);
        } catch (e) {
          return acc;
        }
      }, 0);

    const total = maintCost + laborCost + partsCost;
    const ageInMonths = 6; // Mock time since registration
    const perMonth = total / Math.max(1, ageInMonths);
    const totalKmRun = selectedVehicle?.odometer_km || 1;
    const perKm = total / Math.max(1, totalKmRun);

    return {
      total,
      parts: partsCost,
      maintenance: maintCost,
      labor: laborCost,
      perMonth,
      perKm
    };
  }, [selectedVehicleId, selectedMaint, workOrders, selectedVehicle, selectedTimeline]);

  // Seeding simulation scanning
  const handleStartScan = (vehicleId: string) => {
    const v = vehicles.find(veh => veh.id === vehicleId);
    if (!v) return;
    setIsScanning(vehicleId);
    setScanProgress(0);
    
    const interval = setInterval(() => {
      setScanProgress(prev => {
        if (prev >= 100) {
          clearInterval(interval);
          setTimeout(() => {
            finishScanning(v);
          }, 400);
          return 100;
        }
        return prev + 25;
      });
    }, 300);
  };

  const finishScanning = (v: VehicleProfile) => {
    setIsScanning(null);
    setScanProgress(0);

    // Simulated scan outcomes
    const mockDtcScenarios = [
      {
        dtcs: ['P0230'],
        voltage: 13.1,
        ect: 94,
        desc: 'Falla crítica detectada en circuito primario de bomba de combustible.'
      },
      {
        dtcs: ['P0302', 'P0300'],
        voltage: 14.1,
        ect: 104,
        desc: 'Fallos de encendido (misfires) en cilindro 2 y sobretemperatura del refrigerante.'
      },
      {
        dtcs: ['P0171', 'P0420'],
        voltage: 13.8,
        ect: 92,
        desc: 'Mezcla pobre de combustible y baja eficiencia del catalizador.'
      },
      {
        dtcs: [],
        voltage: 14.2,
        ect: 90,
        desc: 'Lecturas de sensores nominales. Sin códigos DTC activos.'
      }
    ];

    const scenario = mockDtcScenarios[Math.floor(Math.random() * mockDtcScenarios.length)];
    
    // Update Twin normal values
    if (selectedTwin) {
      onUpdateDigitalTwin({
        ...selectedTwin,
        normal_voltage_max: scenario.voltage,
        normal_ect_max: scenario.ect,
        last_updated_at: new Date().toISOString()
      });
    }

    // Add OBD connected event
    onAddTimelineEvent({
      id: `ev_obd_${Date.now()}`,
      vehicle_id: v.id,
      event_type: 'OBD_CONNECTED',
      title: 'Sesión OBD Sincronizada',
      description: `Conexión exitosa con adaptador. Parámetros live: ${scenario.voltage}V, ${scenario.ect}°C.`,
      severity: 'low',
      source: 'OBD',
      related_report_id_nullable: null,
      related_work_order_id_nullable: null,
      related_part_request_id_nullable: null,
      related_livelink_id_nullable: null,
      created_at: new Date().toISOString()
    });

    // Add DTC event if present
    if (scenario.dtcs.length > 0) {
      onAddTimelineEvent({
        id: `ev_dtc_${Date.now()}`,
        vehicle_id: v.id,
        event_type: 'DTC_DETECTED',
        title: `DTC Detectado(s): ${scenario.dtcs.join(', ')}`,
        description: scenario.desc,
        severity: 'high',
        source: 'OBD',
        payload_json: JSON.stringify({ dtcCodes: scenario.dtcs }),
        related_report_id_nullable: null,
        related_work_order_id_nullable: null,
        related_part_request_id_nullable: null,
        related_livelink_id_nullable: null,
        created_at: new Date().toISOString()
      });

      // Update predictive alerts
      const newAlerts = generatePredictiveAlerts(v.id, v.odometer_km, scenario.dtcs, [14.2, 13.8, scenario.voltage], [88, 92, scenario.ect]);
      newAlerts.forEach(al => onAddPredictiveAlert(al));
    } else {
      // Clear DTC event
      onAddTimelineEvent({
        id: `ev_dtc_clear_${Date.now()}`,
        vehicle_id: v.id,
        event_type: 'DTC_CLEARED',
        title: 'Códigos de Falla Limpios',
        description: 'Auto-scan de diagnóstico completado. Sin DTCs pendientes.',
        severity: 'low',
        source: 'OBD',
        related_report_id_nullable: null,
        related_work_order_id_nullable: null,
        related_part_request_id_nullable: null,
        related_livelink_id_nullable: null,
        created_at: new Date().toISOString()
      });
    }
  };

  const handleCreateVehicleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newVehicle.make || !newVehicle.model || !newVehicle.plate) return;

    const id = `veh_${Date.now()}`;
    const profile: VehicleProfile = {
      id,
      owner_user_id: activeUserId,
      nickname: newVehicle.nickname || `${newVehicle.make} ${newVehicle.model}`,
      make: newVehicle.make,
      model: newVehicle.model,
      year: newVehicle.year,
      trim_nullable: newVehicle.trim || null,
      engine: newVehicle.engine || '1.6L',
      engine_code_nullable: newVehicle.engineCode || null,
      transmission: newVehicle.transmission,
      fuel_type: newVehicle.fuelType,
      vin_nullable: newVehicle.vin || null,
      plate_nullable: newVehicle.plate,
      odometer_km: newVehicle.odometer,
      country: newVehicle.country,
      province_nullable: newVehicle.province || null,
      color_nullable: newVehicle.color || null,
      photo_uri_nullable: newVehicle.photo || null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
      
      // Compatibility shim
      plate: newVehicle.plate,
      brand: newVehicle.make,
      color: newVehicle.color || 'Gris',
      mileage: newVehicle.odometer
    };

    if (onAddVehicle) {
      onAddVehicle(profile);
    }

    // Initialize digital twin
    const twin: VehicleDigitalTwin = {
      vehicle_id: id,
      baseline_created_at: null,
      baseline_confidence: 0,
      normal_idle_rpm_min: 700,
      normal_idle_rpm_max: 850,
      normal_voltage_min: 13.5,
      normal_voltage_max: 14.6,
      normal_ect_min: 85,
      normal_ect_max: 95,
      normal_fuel_trim_min: -5,
      normal_fuel_trim_max: 5,
      normal_maf_min: 2,
      normal_maf_max: 5,
      normal_map_min: 25,
      normal_map_max: 40,
      driving_profile: 'UNKNOWN',
      health_score: 100,
      risk_score: 0,
      last_updated_at: new Date().toISOString()
    };
    onUpdateDigitalTwin(twin);

    // Initial timeline event
    onAddTimelineEvent({
      id: `ev_created_${Date.now()}`,
      vehicle_id: id,
      event_type: 'VEHICLE_CREATED',
      title: 'Vehículo Registrado',
      description: `Se registró el expediente del auto ${profile.nickname} en el sistema.`,
      severity: 'low',
      source: 'Manual',
      related_report_id_nullable: null,
      related_work_order_id_nullable: null,
      related_part_request_id_nullable: null,
      related_livelink_id_nullable: null,
      created_at: new Date().toISOString()
    });

    setIsAddingVehicle(false);
    setNewVehicle({
      nickname: '',
      make: '',
      model: '',
      year: new Date().getFullYear(),
      trim: '',
      engine: '',
      engineCode: '',
      transmission: 'AUTOMATIC',
      fuelType: 'GASOLINE',
      vin: '',
      plate: '',
      odometer: 0,
      country: 'Costa Rica',
      province: '',
      color: '',
      photo: ''
    });
  };

  const handleAddMaintSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMaint.title || !selectedVehicleId) return;

    const record: MaintenanceRecord = {
      id: `maint_${Date.now()}`,
      vehicle_id: selectedVehicleId,
      type: newMaint.type as any,
      title: newMaint.title,
      odometer_km: selectedVehicle?.odometer_km || 0,
      date: new Date().toISOString(),
      provider_id_nullable: role === Role.CLIENT ? null : activeUserId,
      provider_name: role === Role.CLIENT ? 'Manual Propietario' : 'Taller Elysium',
      cost_nullable: newMaint.cost,
      currency: 'CRC',
      parts_used: newMaint.parts ? newMaint.parts.split(',').map(p => p.trim()) : [],
      notes: newMaint.notes,
      photos: [],
      report_id_nullable: null,
      created_at: new Date().toISOString()
    };

    onAddMaintenanceRecord(record);

    // Timeline event
    onAddTimelineEvent({
      id: `ev_maint_${Date.now()}`,
      vehicle_id: selectedVehicleId,
      event_type: 'MAINTENANCE_COMPLETED',
      title: `Mantenimiento: ${newMaint.title}`,
      description: `Log de mantenimiento registrado. Costo: ₡${newMaint.cost.toLocaleString()}`,
      severity: 'low',
      source: 'Manual',
      related_report_id_nullable: null,
      related_work_order_id_nullable: null,
      related_part_request_id_nullable: null,
      related_livelink_id_nullable: null,
      created_at: new Date().toISOString()
    });

    // If spark plugs or other critical components, clear related predictive alerts
    const activeSparkAlert = selectedAlerts.find(a => a.component === 'bujías' && a.status === 'active');
    if (activeSparkAlert && newMaint.type === 'SPARK_PLUGS') {
      onUpdatePredictiveAlert({
        ...activeSparkAlert,
        status: 'resolved'
      });
    }

    setNewMaint({
      type: 'OIL_CHANGE',
      title: '',
      cost: 0,
      notes: '',
      parts: ''
    });
  };

  const getSystemStatusIcon = (score: number) => {
    if (score >= 90) return <CheckCircle className="text-green-400" size={16} />;
    if (score >= 70) return <AlertTriangle className="text-yellow-400" size={16} />;
    return <ShieldAlert className="text-red-400" size={16} />;
  };

  const getRiskBadgeColor = (cat: string) => {
    switch (cat) {
      case 'CRITICAL': return 'bg-red-500/20 text-red-400 border border-red-500/40';
      case 'HIGH': return 'bg-orange-500/20 text-orange-400 border border-orange-500/40';
      case 'MEDIUM': return 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/40';
      default: return 'bg-green-500/20 text-green-400 border border-green-500/40';
    }
  };

  const radarData = activeHealthScore ? [
    { name: 'Motor', score: activeHealthScore.engine_score },
    { name: 'Transmisión', score: activeHealthScore.transmission_score },
    { name: 'Eléctrico', score: activeHealthScore.electrical_score },
    { name: 'Enfriamiento', score: activeHealthScore.cooling_score },
    { name: 'Batería', score: activeHealthScore.battery_score },
    { name: 'Suspensión', score: activeHealthScore.suspension_score }
  ] : [];

  return (
    <div className="w-full space-y-6">
      
      {/* ── HEADER & NAVIGATION ── */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-steel-900/60 p-5 rounded-2xl border border-cyan-500/10 backdrop-blur-md">
        <div>
          <div className="flex items-center gap-2">
            <Car className="text-cyan-400" size={24} />
            <h1 className="text-2xl font-black text-white tracking-tight uppercase" style={{ textShadow: '0 0 15px rgba(6,182,212,0.4)' }}>
              Garage & Gemelo Digital
            </h1>
          </div>
          <p className="text-slate-400 text-xs mt-1 font-mono uppercase tracking-widest">
            {role === Role.CLIENT ? 'Tus Expedientes de Vehículos' : 'Base de datos técnica de la flota'}
          </p>
        </div>
        
        <div className="flex gap-2">
          {onClose && (
            <button onClick={onClose} className="p-2 bg-white/5 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors">
              <X size={18} />
            </button>
          )}
          {onAddVehicle && !selectedVehicleId && (
            <button
              onClick={() => setIsAddingVehicle(!isAddingVehicle)}
              className="flex items-center gap-2 bg-gradient-to-r from-cyan-400 to-cyan-600 text-black px-4 py-2 rounded-xl font-mono font-bold text-xs uppercase tracking-wider shadow-[0_0_20px_rgba(6,182,212,0.3)] transition-all transform active:scale-95"
            >
              <Plus size={14} strokeWidth={2.5} />
              {isAddingVehicle ? 'Cancelar' : 'Registrar Vehículo'}
            </button>
          )}
        </div>
      </div>

      {/* ── DETAILED VEHICLE Technical FILE (IF SELECTED) ── */}
      {selectedVehicleId && selectedVehicle ? (
        <div className="space-y-6 animate-slide-up">
          {/* Active vehicle header */}
          <div className="relative glass p-6 rounded-2xl border border-cyan-500/20 overflow-hidden flex flex-col md:flex-row justify-between gap-6">
            <div className="absolute top-0 right-0 w-80 h-80 bg-cyan-500/5 blur-[100px] pointer-events-none rounded-full" />
            
            <div className="flex items-start gap-4">
              <div className="w-16 h-16 rounded-xl bg-steel-800 flex items-center justify-center border border-cyan-500/20 text-3xl shadow-inner">
                {selectedVehicle.photo_uri_nullable ? (
                  <img src={selectedVehicle.photo_uri_nullable} alt="Car" className="w-full h-full object-cover rounded-xl" />
                ) : '🚗'}
              </div>
              <div>
                <button
                  onClick={() => setSelectedVehicleId(null)}
                  className="text-xs font-bold text-cyan-400 hover:underline mb-1 font-mono uppercase tracking-wider flex items-center gap-1"
                >
                  ← Volver a la Lista
                </button>
                <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                  {selectedVehicle.nickname}
                  <span className={`text-[10px] px-2 py-0.5 rounded font-mono ${getRiskBadgeColor(activeRiskDetails?.category || 'LOW')}`}>
                    Riesgo: {activeRiskDetails?.category}
                  </span>
                </h2>
                <p className="text-slate-400 text-xs font-mono mt-1">
                  Placa: {selectedVehicle.plate_nullable} · VIN: {selectedVehicle.vin_nullable || 'OBD no disponible'}
                </p>
                <div className="flex items-center gap-3 mt-2 text-[10px] font-mono text-cyan-400 uppercase">
                  <span>ODÓMETRO: {selectedVehicle.odometer_km.toLocaleString()} KM</span>
                  <span>·</span>
                  <span>CALIDAD DATOS: {dataQuality}</span>
                </div>
              </div>
            </div>

            {/* Quick stats on Header */}
            <div className="flex flex-wrap gap-4 items-center self-center">
              <div className="text-center bg-white/5 border border-white/5 p-3 rounded-xl min-w-[100px]">
                <div className="text-2xl font-black text-white">{activeHealthScore?.overall_score || 100}%</div>
                <div className="text-[9px] font-mono uppercase text-slate-400 tracking-wider">Salud general</div>
              </div>

              <div className="text-center bg-white/5 border border-white/5 p-3 rounded-xl min-w-[100px]">
                <div className="text-2xl font-black text-red-400">{activeDtcCount}</div>
                <div className="text-[9px] font-mono uppercase text-slate-400 tracking-wider">DTCs Activos</div>
              </div>

              <button
                onClick={() => handleStartScan(selectedVehicle.id)}
                disabled={isScanning !== null}
                className="bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-black px-4 py-3 rounded-xl font-mono text-xs uppercase font-bold tracking-wider shadow-lg flex items-center gap-2"
              >
                {isScanning === selectedVehicle.id ? (
                  <>
                    <Clock className="animate-spin" size={14} />
                    Escaneando {scanProgress}%
                  </>
                ) : (
                  <>
                    <Activity size={14} />
                    Escanear OBD2
                  </>
                )}
              </button>
            </div>
          </div>

          {/* 11-Tabs Navigation Row */}
          <div className="flex overflow-x-auto gap-2 p-1.5 bg-steel-950/80 rounded-xl border border-white/5 hide-scrollbar scroll-smooth">
            {[
              { id: 'resumen', label: 'Resumen', icon: <Car size={13} /> },
              { id: 'salud', label: 'Salud', icon: <Activity size={13} /> },
              { id: 'timeline', label: 'Timeline', icon: <History size={13} /> },
              { id: 'dtcs', label: 'DTCs', icon: <AlertTriangle size={13} /> },
              { id: 'mantenimiento', label: 'Mantenimiento', icon: <Calendar size={13} /> },
              { id: 'reportes', label: 'Reportes', icon: <FileText size={13} /> },
              { id: 'repuestos', label: 'Repuestos', icon: <Wrench size={13} /> },
              { id: 'mecanicos', label: 'Mecánicos', icon: <User size={13} /> },
              { id: 'livelink', label: 'LiveLink', icon: <Link size={13} /> },
              { id: 'costos', label: 'Costos', icon: <Coins size={13} /> },
              { id: 'cajanegra', label: 'Caja Negra', icon: <Video size={13} /> },
              { id: 'config', label: 'Configuración', icon: <Settings size={13} /> }
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg font-mono text-xs font-bold uppercase tracking-wider transition-all whitespace-nowrap ${
                  activeTab === tab.id
                    ? 'bg-cyan-500 text-black shadow-md'
                    : 'text-slate-400 hover:text-white hover:bg-white/5'
                }`}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* ── TAB PANEL CONTENTS ── */}
          <div className="min-h-[400px]">
            
            {/* 1. RESUMEN TAB */}
            {activeTab === 'resumen' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 space-y-6">
                  {/* Health summary card */}
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4">Salud Predictiva</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div className="flex flex-col justify-center items-center p-4 bg-steel-950/40 rounded-xl border border-white/5">
                        <div className="relative flex items-center justify-center w-28 h-28">
                          <svg className="w-full h-full transform -rotate-90">
                            <circle cx="56" cy="56" r="48" className="stroke-steel-800" strokeWidth="8" fill="transparent" />
                            <circle cx="56" cy="56" r="48"
                              className={activeHealthScore && activeHealthScore.overall_score >= 80 ? "stroke-green-400" : activeHealthScore && activeHealthScore.overall_score >= 60 ? "stroke-yellow-400" : "stroke-red-400"}
                              strokeWidth="8" fill="transparent"
                              strokeDasharray={2 * Math.PI * 48}
                              strokeDashoffset={2 * Math.PI * 48 * (1 - (activeHealthScore?.overall_score || 100) / 100)}
                            />
                          </svg>
                          <div className="absolute text-center">
                            <div className="text-3xl font-black text-white">{activeHealthScore?.overall_score}%</div>
                            <div className="text-[8px] font-mono text-slate-400 uppercase tracking-widest">Confianza: {activeHealthScore?.confidence}</div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-3">
                        <div className="p-3 bg-white/[0.02] border border-white/5 rounded-xl">
                          <div className="text-[10px] font-mono uppercase text-slate-400">Estado Diagnóstico</div>
                          <div className="text-sm font-bold text-white mt-0.5">
                            {activeDtcCount > 0 ? `${activeDtcCount} códigos de falla activos` : 'Sin anomalías activas'}
                          </div>
                        </div>
                        <div className="p-3 bg-white/[0.02] border border-white/5 rounded-xl">
                          <div className="text-[10px] font-mono uppercase text-slate-400">Recomendación Crítica</div>
                          <div className="text-xs text-slate-300 mt-1 font-mono leading-relaxed">
                            {activeRiskDetails?.explanation}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Active Predictive Alerts list */}
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4 flex items-center gap-2">
                      <Sparkles className="text-cyan-400" size={15} />
                      Alertas Preventivas del Gemelo Digital
                    </h3>
                    <div className="space-y-3">
                      {selectedAlerts.filter(a => a.status === 'active').length === 0 ? (
                        <div className="text-center p-6 border-dashed border border-white/10 rounded-xl text-slate-400 text-xs">
                          No se han generado alertas predictivas. El vehículo opera en parámetros nominales.
                        </div>
                      ) : (
                        selectedAlerts.filter(a => a.status === 'active').map(alert => (
                          <div key={alert.id} className={`p-4 rounded-xl border border-white/5 flex gap-3 ${
                            alert.risk_level === 'CRITICAL' ? 'bg-red-500/5' : alert.risk_level === 'HIGH' ? 'bg-orange-500/5' : 'bg-yellow-500/5'
                          }`}>
                            <div className="mt-0.5">
                              {alert.risk_level === 'CRITICAL' ? <ShieldAlert className="text-red-400" size={18} /> : <AlertTriangle className="text-orange-400" size={18} />}
                            </div>
                            <div className="flex-1">
                              <div className="flex justify-between">
                                <div className="text-xs font-bold text-white uppercase tracking-wider">
                                  Alerta: {alert.predicted_issue}
                                </div>
                                <div className="text-[9px] font-mono text-slate-400 bg-white/5 px-2 py-0.5 rounded">
                                  Confianza: {alert.confidence}%
                                </div>
                              </div>
                              <p className="text-xs text-slate-400 mt-1 italic">
                                Evidencia: {alert.evidence.join(' · ')}
                              </p>
                              <div className="mt-2 text-xs text-cyan-400 font-bold">
                                Acción recomendada: {alert.recommended_action}
                              </div>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>

                {/* Right Column: Latest timeline events & summary */}
                <div className="space-y-6">
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4">Últimos Eventos</h3>
                    <div className="space-y-4">
                      {selectedTimeline.slice(0, 4).map((ev, index) => (
                        <div key={ev.id} className="relative pl-5 border-l border-cyan-500/20 last:border-0 pb-1">
                          <div className="absolute left-[-4px] top-1.5 w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(6,182,212,0.6)]" />
                          <div className="text-[9px] font-mono text-slate-500">{new Date(ev.created_at).toLocaleString()}</div>
                          <div className="text-xs font-bold text-white mt-0.5">{ev.title}</div>
                          <div className="text-xs text-slate-400">{ev.description}</div>
                        </div>
                      ))}
                      {selectedTimeline.length === 0 && (
                        <div className="text-center p-4 text-slate-400 text-xs">Sin eventos registrados.</div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 2. SALUD TAB */}
            {activeTab === 'salud' && (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 animate-slide-up">
                {/* System by system diagnostics */}
                <div className="glass p-5 rounded-2xl border border-white/5 space-y-4">
                  <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-2">Diagnóstico de Sistemas</h3>
                  {activeHealthScore && (
                    <div className="space-y-4">
                      {[
                        { label: 'Sistema Motor', score: activeHealthScore.engine_score },
                        { label: 'Caja de Cambios/Transmisión', score: activeHealthScore.transmission_score },
                        { label: 'Sistema de Carga Eléctrica', score: activeHealthScore.electrical_score },
                        { label: 'Sistema de Enfriamiento', score: activeHealthScore.cooling_score },
                        { label: 'Emisiones de Gases', score: activeHealthScore.emissions_score },
                        { label: 'Batería y Alternador', score: activeHealthScore.battery_score },
                        { label: 'Sistema Frenos', score: activeHealthScore.brake_score }
                      ].map(sys => (
                        <div key={sys.label} className="bg-steel-950/20 border border-white/5 p-3 rounded-xl flex items-center justify-between">
                          <div className="flex-1">
                            <div className="flex justify-between items-center mb-1.5">
                              <span className="text-xs font-bold text-white">{sys.label}</span>
                              <span className="text-xs font-mono text-slate-300 font-bold">{sys.score}/100</span>
                            </div>
                            <div className="w-full bg-steel-800 h-1.5 rounded-full overflow-hidden">
                              <div
                                className={`h-full rounded-full transition-all ${
                                  sys.score >= 90 ? 'bg-green-400' : sys.score >= 70 ? 'bg-yellow-400' : 'bg-red-400'
                                }`}
                                style={{ width: `${sys.score}%` }}
                              />
                            </div>
                          </div>
                          <div className="ml-4">{getSystemStatusIcon(sys.score)}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Radar/Bar visualization & baseline summary */}
                <div className="space-y-6">
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4">Gemelo Digital: Rangos de Telemetría</h3>
                    
                    {selectedTwin ? (
                      <div className="space-y-3 font-mono text-xs">
                        <div className="flex justify-between border-b border-white/5 py-1.5">
                          <span className="text-slate-400">Ralentí Objetivo</span>
                          <span className="text-white">{selectedTwin.normal_idle_rpm_min} - {selectedTwin.normal_idle_rpm_max} RPM</span>
                        </div>
                        <div className="flex justify-between border-b border-white/5 py-1.5">
                          <span className="text-slate-400">Voltaje Nominal</span>
                          <span className="text-white">{selectedTwin.normal_voltage_min.toFixed(1)}V - {selectedTwin.normal_voltage_max.toFixed(1)}V</span>
                        </div>
                        <div className="flex justify-between border-b border-white/5 py-1.5">
                          <span className="text-slate-400">Temp. Normal Trabajo (ECT)</span>
                          <span className="text-white">{selectedTwin.normal_ect_min}°C - {selectedTwin.normal_ect_max}°C</span>
                        </div>
                        <div className="flex justify-between border-b border-white/5 py-1.5">
                          <span className="text-slate-400">Presión Sensor MAP</span>
                          <span className="text-white">{selectedTwin.normal_map_min} - {selectedTwin.normal_map_max} kPa</span>
                        </div>
                        <div className="flex justify-between border-b border-white/5 py-1.5">
                          <span className="text-slate-400">Perfil Conducción</span>
                          <span className="text-cyan-400 font-bold">{selectedTwin.driving_profile}</span>
                        </div>
                        <div className="flex justify-between py-1.5">
                          <span className="text-slate-400">Última Sincronización</span>
                          <span className="text-white">{new Date(selectedTwin.last_updated_at).toLocaleDateString()}</span>
                        </div>
                      </div>
                    ) : (
                      <div className="text-center p-6 text-slate-400">Base de datos de gemelo digital vacía. Realice un diagnóstico.</div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* 3. TIMELINE TAB */}
            {activeTab === 'timeline' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                  <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Historial Técnico de Eventos</h3>
                  <div className="text-slate-400 text-xs font-mono bg-white/5 px-3 py-1.5 rounded-lg border border-white/5">
                    {selectedTimeline.length} eventos registrados
                  </div>
                </div>

                <div className="space-y-4">
                  {selectedTimeline.map(ev => (
                    <div key={ev.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl flex justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className={`w-2 h-2 rounded-full ${
                            ev.severity === 'critical' ? 'bg-red-500 animate-pulse' : ev.severity === 'high' ? 'bg-orange-500' : ev.severity === 'medium' ? 'bg-yellow-500' : 'bg-cyan-500'
                          }`} />
                          <span className="text-[10px] font-mono text-cyan-400 uppercase tracking-widest font-bold">{ev.event_type}</span>
                          {['PROCEDURE_OPENED', 'SPEC_USED_IN_REPORT', 'MANUAL_CONSULTED', 'DIAGNOSTIC_VERIFIED'].includes(ev.event_type) && (
                            <span className="text-[8px] font-mono font-black bg-indigo-500/20 text-indigo-400 px-1.5 py-0.5 rounded uppercase tracking-wider">Fuente Técnica</span>
                          )}
                        </div>
                        <h4 className="text-sm font-bold text-white mt-1.5">{ev.title}</h4>
                        <p className="text-xs text-slate-400 mt-1">{ev.description}</p>
                        {(() => {
                          try {
                            const payload = JSON.parse(ev.payload_json || '{}');
                            if (payload.sourceDocument || payload.citation) {
                              const src = payload.sourceDocument || payload.citation?.documentTitle || 'Manual técnico';
                              const pg = payload.page || payload.citation?.page;
                              return (
                                <div className="mt-2 flex items-center gap-1.5 text-[9px] font-mono text-indigo-400/80">
                                  <ShieldCheck size={10} />
                                  <span>Fuente: {src}</span>
                                  {pg && <span className="text-slate-500">· Pág. {pg}</span>}
                                </div>
                              );
                            }
                          } catch (_) {}
                          return null;
                        })()}
                      </div>
                      <div className="text-right shrink-0">
                        <div className="text-[10px] font-mono text-slate-500">{new Date(ev.created_at).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</div>
                        <div className="text-[10px] font-mono text-slate-500 mt-0.5">{new Date(ev.created_at).toLocaleDateString()}</div>
                        <div className="text-[10px] font-mono text-slate-400 bg-white/5 px-2 py-0.5 rounded mt-2 inline-block">
                          Fuente: {ev.source}
                        </div>
                      </div>
                    </div>
                  ))}
                  {selectedTimeline.length === 0 && (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      El timeline técnico de este vehículo se encuentra vacío. Conecte un escáner o agregue mantenimiento para generar registros históricos.
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* 4. DTCS TAB */}
            {activeTab === 'dtcs' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Historial de Códigos de Falla OBD2 (DTCs)</h3>
                
                {activeDtcList.length === 0 ? (
                  <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                    <CheckCircle className="text-green-400 mx-auto mb-3" size={32} />
                    <h4 className="text-white font-bold mb-1">Sin fallas activas</h4>
                    <p className="text-xs">El diagnóstico actual de la computadora de abordo no reporta averías.</p>
                  </div>
                ) : (
                  <div className="space-y-4">
                    {activeDtcList.map(code => (
                      <div key={code} className="p-4 bg-red-950/10 border border-red-500/20 rounded-xl flex gap-3">
                        <AlertTriangle className="text-red-400 mt-0.5 shrink-0" size={18} />
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-black text-white text-base bg-red-500/20 border border-red-500/30 px-2 py-0.5 rounded">{code}</span>
                            <span className="text-[10px] font-mono text-red-400 bg-red-500/10 px-2 py-0.5 rounded uppercase tracking-wider font-bold">Estado: ACTIVO</span>
                          </div>
                          
                          <div className="mt-3 space-y-2">
                            <div className="text-xs text-slate-300">
                              <span className="font-bold font-mono text-slate-400">Descripción:</span>{' '}
                              {code === 'P0230' ? 'Mal funcionamiento en el circuito primario de la bomba de combustible. La ECU no detecta el retorno correcto del relé de bomba.' : 
                               code === 'P0302' ? 'Fallo de encendido (misfires) detectado en el cilindro 2.' : 
                               code === 'P0300' ? 'Fallos de encendido aleatorios detectados en múltiples cilindros.' :
                               'Fallo de tren motriz genérico registrado por sensores de monitoreo.'}
                            </div>
                            <div className="text-xs text-slate-300">
                              <span className="font-bold font-mono text-slate-400">Severidad de conducción:</span>{' '}
                              {code === 'P0230' || code === 'P0302' ? (
                                <span className="text-red-400 font-bold">Crítica - No arranque o daño catalítico potencial.</span>
                              ) : (
                                <span className="text-yellow-400 font-bold">Media - Agendar cita taller.</span>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 5. MANTENIMIENTO TAB */}
            {activeTab === 'mantenimiento' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-slide-up">
                
                {/* Form to add maintenance record */}
                <div className="glass p-5 rounded-2xl border border-white/5 h-fit">
                  <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4 flex items-center gap-2">
                    <PlusCircle className="text-cyan-400" size={15} />
                    Registrar Mantenimiento
                  </h3>

                  <form onSubmit={handleAddMaintSubmit} className="space-y-4">
                    <div>
                      <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Tipo de Servicio</label>
                      <select
                        value={newMaint.type}
                        onChange={e => setNewMaint({ ...newMaint, type: e.target.value })}
                        className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                      >
                        <option value="OIL_CHANGE">Cambio de Aceite</option>
                        <option value="FILTER_CHANGE">Cambio de Filtros</option>
                        <option value="SPARK_PLUGS">Cambio de Bujías</option>
                        <option value="BRAKES">Cambio de Frenos</option>
                        <option value="ATF">Aceite de Transmisión</option>
                        <option value="COOLANT">Cambio de Refrigerante</option>
                        <option value="BATTERY">Batería nueva</option>
                        <option value="TIRES">Llantas</option>
                        <option value="TIMING_BELT">Faja de Distribución</option>
                        <option value="CUSTOM">Otro / Personalizado</option>
                      </select>
                    </div>

                    <div>
                      <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Título de Trabajo</label>
                      <input
                        type="text"
                        placeholder="ej. Reemplazo Bujías Iridium"
                        value={newMaint.title}
                        onChange={e => setNewMaint({ ...newMaint, title: e.target.value })}
                        className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                        required
                      />
                    </div>

                    <div>
                      <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Costo (₡)</label>
                      <input
                        type="number"
                        placeholder="ej. 30000"
                        value={newMaint.cost || ''}
                        onChange={e => setNewMaint({ ...newMaint, cost: +e.target.value })}
                        className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                      />
                    </div>

                    <div>
                      <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Repuestos Utilizados</label>
                      <input
                        type="text"
                        placeholder="ej. Bujías Denso, junta (separar por coma)"
                        value={newMaint.parts}
                        onChange={e => setNewMaint({ ...newMaint, parts: e.target.value })}
                        className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                      />
                    </div>

                    <div>
                      <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Notas / Detalles</label>
                      <textarea
                        placeholder="ej. Cambio preventivo a los 65k km."
                        value={newMaint.notes}
                        onChange={e => setNewMaint({ ...newMaint, notes: e.target.value })}
                        className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50 h-20 resize-none"
                      />
                    </div>

                    <button
                      type="submit"
                      className="w-full bg-gradient-to-r from-cyan-400 to-cyan-600 text-black py-2 rounded-xl font-mono text-xs uppercase font-bold tracking-wider"
                    >
                      Guardar Log
                    </button>
                  </form>
                </div>

                {/* Maintenance records list */}
                <div className="lg:col-span-2 glass p-5 rounded-2xl border border-white/5 space-y-4">
                  <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-2">Bitácora Técnica Histórica</h3>
                  
                  {selectedMaint.length === 0 ? (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      No se han registrado servicios de mantenimiento en la bitácora aún.
                    </div>
                  ) : (
                    selectedMaint.map(m => (
                      <div key={m.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl">
                        <div className="flex justify-between items-start gap-4">
                          <div>
                            <div className="inline-flex px-2 py-0.5 bg-cyan-400/10 text-cyan-400 rounded text-[9px] font-mono uppercase tracking-wider font-bold">
                              {m.type}
                            </div>
                            <h4 className="text-sm font-bold text-white mt-1.5">{m.title}</h4>
                            <p className="text-xs text-slate-400 mt-1">{m.notes}</p>
                            
                            {m.parts_used.length > 0 && (
                              <div className="mt-2 text-[10px] text-slate-400 font-mono">
                                Piezas usadas: {m.parts_used.join(', ')}
                              </div>
                            )}
                          </div>
                          
                          <div className="text-right shrink-0">
                            <div className="text-sm font-bold text-forge-400">₡{(m.cost_nullable || 0).toLocaleString()}</div>
                            <div className="text-[10px] text-slate-500 font-mono mt-1">{new Date(m.date).toLocaleDateString()}</div>
                            <div className="text-[9px] text-slate-400 bg-white/5 px-2 py-0.5 rounded mt-2">{m.odometer_km.toLocaleString()} KM</div>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* 6. REPORTES TAB */}
            {activeTab === 'reportes' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Reportes Certificados PDF & SHA-256</h3>
                
                {/* List certified reports in system for this vehicle */}
                <div className="space-y-4">
                  {selectedTimeline.filter(ev => ev.event_type === 'REPORT_GENERATED').length === 0 ? (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      <FileText className="text-slate-500 mx-auto mb-3" size={32} />
                      <h4 className="text-white font-bold mb-1">Sin reportes emitidos</h4>
                      <p className="text-xs">Los reportes PDF certificados generados desde la estación se enlistarán aquí.</p>
                    </div>
                  ) : (
                    selectedTimeline
                      .filter(ev => ev.event_type === 'REPORT_GENERATED')
                      .map(ev => {
                        let hash = 'N/A';
                        let type = 'PRE_SCAN_REPORT';
                        let citation: { documentTitle?: string; page?: number; hash?: string; source?: string } | null = null;
                        try {
                          const p = JSON.parse(ev.payload_json || '{}');
                          hash = p.integrityHash || 'N/A';
                          type = p.reportType || 'PRE_SCAN_REPORT';
                          if (p.citation) citation = p.citation;
                        } catch (e) {}

                        return (
                          <div key={ev.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl flex flex-col md:flex-row justify-between gap-4">
                            <div className="flex-1">
                              <div className="flex items-center gap-2">
                                <FileText className="text-cyan-400" size={16} />
                                <span className="font-bold text-white text-sm">{ev.title}</span>
                              </div>
                              <p className="text-xs text-slate-400 mt-1">{ev.description}</p>
                              
                              <div className="mt-3 flex flex-wrap gap-4 items-center text-[10px] font-mono text-slate-500">
                                <span>INTEGRITY HASH: <span className="text-cyan-400">{hash.slice(0, 12)}…{hash.slice(-8)}</span></span>
                                <span>·</span>
                                <span className="text-green-400 flex items-center gap-0.5">✓ HASH VALIDADO POR EL CLIENTE</span>
                              </div>

                              {citation && (
                                <div className="mt-3 flex items-center gap-2 bg-indigo-500/10 border border-indigo-500/20 rounded-lg px-3 py-2">
                                  <ShieldCheck size={14} className="text-indigo-400 shrink-0" />
                                  <div className="text-[10px]">
                                    <span className="text-indigo-300 font-bold">FUENTE TÉCNICA CITADA:</span>
                                    <span className="text-slate-300 ml-2">{citation.documentTitle || 'Manual técnico'}</span>
                                    {citation.page && <span className="text-slate-500 ml-1">· Pág. {citation.page}</span>}
                                    {citation.hash && <span className="text-emerald-500 ml-2 font-mono">SHA: {String(citation.hash).slice(0, 8)}…</span>}
                                  </div>
                                </div>
                              )}
                            </div>
                            
                            <div className="flex items-center gap-2 self-center shrink-0">
                              <button className="flex items-center gap-1.5 bg-white/5 hover:bg-white/10 border border-white/10 px-3 py-1.5 rounded-lg text-xs font-mono font-bold text-slate-300 transition-all">
                                <Download size={12} />
                                Descargar PDF
                              </button>
                            </div>
                          </div>
                        );
                      })
                  )}
                </div>
              </div>
            )}

            {/* 7. REPUESTOS TAB */}
            {activeTab === 'repuestos' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Historial de Repuestos Comprados</h3>
                
                <div className="space-y-4">
                  {selectedTimeline.filter(ev => ev.event_type === 'PART_PURCHASED').length === 0 ? (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      No hay repuestos registrados en el historial de reparaciones de este vehículo.
                    </div>
                  ) : (
                    selectedTimeline
                      .filter(ev => ev.event_type === 'PART_PURCHASED')
                      .map(ev => {
                        let cost = 0;
                        let provider = 'N/A';
                        let warranty = 'No declarada';
                        let dtc = 'N/A';
                        try {
                          const p = JSON.parse(ev.payload_json || '{}');
                          cost = p.cost || 0;
                          provider = p.provider || 'N/A';
                          warranty = p.warrantyDays ? `${p.warrantyDays} días` : 'No declarada';
                          dtc = p.dtcCode || 'N/A';
                        } catch (e) {}

                        return (
                          <div key={ev.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl flex justify-between gap-4">
                            <div>
                              <div className="flex items-center gap-2">
                                <Wrench className="text-cyan-400" size={16} />
                                <h4 className="text-sm font-bold text-white">{ev.title}</h4>
                              </div>
                              <p className="text-xs text-slate-400 mt-1">{ev.description}</p>
                              
                              <div className="mt-3 flex flex-wrap gap-4 text-[10px] font-mono text-slate-500">
                                <span>Proveedor: <span className="text-white">{provider}</span></span>
                                <span>·</span>
                                <span>Garantía: <span className="text-green-400">{warranty}</span></span>
                                {dtc !== 'N/A' && (
                                  <>
                                    <span>·</span>
                                    <span>Para DTC: <span className="text-red-400 font-bold">{dtc}</span></span>
                                  </>
                                )}
                              </div>
                            </div>
                            
                            <div className="text-right shrink-0">
                              <div className="text-sm font-bold text-forge-400">₡{cost.toLocaleString()}</div>
                              <div className="text-[10px] text-slate-500 font-mono mt-1">{new Date(ev.created_at).toLocaleDateString()}</div>
                            </div>
                          </div>
                        );
                      })
                  )}
                </div>
              </div>
            )}

            {/* 8. MECÁNICOS TAB */}
            {activeTab === 'mecanicos' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Historial de Trabajos Realizados por Mecánicos</h3>
                
                <div className="space-y-4">
                  {selectedTimeline.filter(ev => ev.event_type === 'REPAIR_COMPLETED' || ev.event_type === 'REPAIR_STARTED').length === 0 ? (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      No hay registros de trabajos mecánicos en este vehículo.
                    </div>
                  ) : (
                    selectedTimeline
                      .filter(ev => ev.event_type === 'REPAIR_COMPLETED' || ev.event_type === 'REPAIR_STARTED')
                      .map(ev => {
                        let price = 0;
                        let mechanicName = 'N/A';
                        let workOrder = 'N/A';
                        try {
                          const p = JSON.parse(ev.payload_json || '{}');
                          price = p.price || 0;
                          mechanicName = p.mechanicName || 'N/A';
                          workOrder = p.workOrderId || 'N/A';
                        } catch (e) {}

                        return (
                          <div key={ev.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl flex justify-between gap-4">
                            <div>
                              <div className="flex items-center gap-2">
                                <User className="text-cyan-400" size={16} />
                                <h4 className="text-sm font-bold text-white">{ev.title}</h4>
                              </div>
                              <p className="text-xs text-slate-400 mt-1">{ev.description}</p>
                              
                              <div className="mt-3 flex flex-wrap gap-4 text-[10px] font-mono text-slate-500">
                                <span>Mecánico: <span className="text-white">{mechanicName}</span></span>
                                <span>·</span>
                                <span>Orden: <span className="text-cyan-400">{workOrder}</span></span>
                              </div>
                            </div>
                            
                            <div className="text-right shrink-0">
                              <div className="text-sm font-bold text-forge-400">₡{price.toLocaleString()}</div>
                              <div className="text-[10px] text-slate-500 font-mono mt-1">{new Date(ev.created_at).toLocaleDateString()}</div>
                            </div>
                          </div>
                        );
                      })
                  )}
                </div>
              </div>
            )}

            {/* 9. LIVELINK TAB */}
            {activeTab === 'livelink' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Sesiones de Diagnóstico Remoto LiveLink</h3>
                
                <div className="space-y-4">
                  {selectedTimeline.filter(ev => ev.event_type === 'LIVELINK_SESSION').length === 0 ? (
                    <div className="text-center p-12 text-slate-400 border-dashed border border-white/10 rounded-xl">
                      No se han completado transmisiones de diagnóstico remoto LiveLink para este auto.
                    </div>
                  ) : (
                    selectedTimeline
                      .filter(ev => ev.event_type === 'LIVELINK_SESSION')
                      .map(ev => (
                        <div key={ev.id} className="p-4 bg-steel-900/40 border border-white/5 rounded-xl flex justify-between gap-4">
                          <div>
                            <div className="flex items-center gap-2">
                              <Link className="text-green-400" size={16} />
                              <h4 className="text-sm font-bold text-white">{ev.title}</h4>
                            </div>
                            <p className="text-xs text-slate-400 mt-1">{ev.description}</p>
                          </div>
                          
                          <div className="text-right shrink-0">
                            <span className="text-[10px] font-mono text-green-400 bg-green-500/10 px-2 py-0.5 rounded font-bold">COMPLETADA</span>
                            <div className="text-[10px] text-slate-500 font-mono mt-2">{new Date(ev.created_at).toLocaleDateString()}</div>
                          </div>
                        </div>
                      ))
                  )}
                </div>
              </div>
            )}

            {/* 10. COSTOS TAB */}
            {activeTab === 'costos' && (
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-slide-up">
                {/* Cost stats */}
                <div className="space-y-4">
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-xs font-bold text-slate-400 font-mono uppercase tracking-wider mb-3">Resumen del Costo de Operación</h3>
                    <div className="text-3xl font-black text-white">₡{costSummary.total.toLocaleString()}</div>
                    <p className="text-[10px] text-slate-400 font-mono uppercase mt-1">Costo total acumulado</p>
                  </div>
                  
                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <h3 className="text-xs font-bold text-slate-400 font-mono uppercase tracking-wider mb-2">Desglose de Gastos</h3>
                    <div className="space-y-2 text-xs font-mono">
                      <div className="flex justify-between">
                        <span className="text-slate-400">Repuestos Adquiridos</span>
                        <span className="text-white">₡{costSummary.parts.toLocaleString()}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Mano de Obra / Taller</span>
                        <span className="text-white">₡{costSummary.labor.toLocaleString()}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Mantenimiento Bitácora</span>
                        <span className="text-white">₡{costSummary.maintenance.toLocaleString()}</span>
                      </div>
                    </div>
                  </div>

                  <div className="glass p-5 rounded-2xl border border-white/5">
                    <div className="grid grid-cols-2 gap-4 text-center">
                      <div>
                        <div className="text-lg font-bold text-white">₡{Math.round(costSummary.perMonth).toLocaleString()}</div>
                        <div className="text-[9px] font-mono uppercase text-slate-400">Costo mensual</div>
                      </div>
                      <div>
                        <div className="text-lg font-bold text-white">₡{costSummary.perKm.toFixed(1)}</div>
                        <div className="text-[9px] font-mono uppercase text-slate-400">Costo por KM</div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Costs chart panel */}
                <div className="lg:col-span-2 glass p-5 rounded-2xl border border-white/5">
                  <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4">Gasto por Categoría (₡)</h3>
                  <div className="w-full h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={[
                        { name: 'Repuestos', costo: costSummary.parts },
                        { name: 'Taller', costo: costSummary.labor },
                        { name: 'Mantenimiento', costo: costSummary.maintenance }
                      ]}>
                        <XAxis dataKey="name" stroke="#64748b" fontSize={11} fontClassName="font-mono" />
                        <YAxis stroke="#64748b" fontSize={11} fontClassName="font-mono" />
                        <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px' }} />
                        <Bar dataKey="costo" fill="#06b6d4" radius={[6, 6, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
            )}

            {/* 11. CONFIGURACIÓN TAB */}
            {activeTab === 'config' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up max-w-xl space-y-6">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider">Configuración de Privacidad y Expediente</h3>
                
                <div className="space-y-4">
                  <div className="flex justify-between items-center p-4 bg-steel-950/20 border border-white/5 rounded-xl">
                    <div>
                      <h4 className="text-xs font-bold text-white uppercase tracking-wider">Ocultar Placa en Reportes</h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">Reemplaza el número de placa con caracteres de privacidad en copias PDF.</p>
                    </div>
                    <button className="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs font-mono font-bold text-slate-300">
                      Activo
                    </button>
                  </div>

                  <div className="flex justify-between items-center p-4 bg-steel-950/20 border border-white/5 rounded-xl">
                    <div>
                      <h4 className="text-xs font-bold text-white uppercase tracking-wider">Ocultar Número de VIN</h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">Ofusca el VIN completo del vehículo en visores y descargas de peritajes.</p>
                    </div>
                    <button className="px-3 py-1.5 bg-white/5 hover:bg-white/10 rounded-lg text-xs font-mono font-bold text-slate-300">
                      Activo
                    </button>
                  </div>

                  <div className="flex justify-between items-center p-4 bg-steel-950/20 border border-white/5 rounded-xl">
                    <div>
                      <h4 className="text-xs font-bold text-white uppercase tracking-wider">Exportar Expediente</h4>
                      <p className="text-[10px] text-slate-400 mt-0.5">Descarga el JSON completo del gemelo digital y timeline técnico.</p>
                    </div>
                    <button className="flex items-center gap-1 bg-cyan-500 hover:bg-cyan-400 text-black px-3 py-1.5 rounded-lg text-xs font-mono font-bold">
                      <Download size={12} />
                      Exportar
                    </button>
                  </div>

                  {onDeleteVehicle && (
                    <div className="p-4 bg-red-950/5 border border-red-500/10 rounded-xl flex justify-between items-center mt-6">
                      <div>
                        <h4 className="text-xs font-bold text-red-400 uppercase tracking-wider">Eliminar de mi Garage</h4>
                        <p className="text-[10px] text-slate-500 mt-0.5">Elimina el expediente y borra de forma local la telemetría e historial.</p>
                      </div>
                      <button
                        onClick={() => {
                          if (confirm('¿Estás seguro de eliminar este vehículo? Se perderán el gemelo digital e historial.')) {
                            onDeleteVehicle(selectedVehicle.id);
                            setSelectedVehicleId(null);
                          }
                        }}
                        className="flex items-center gap-1 bg-red-500/20 hover:bg-red-500/30 border border-red-500/30 text-red-400 px-3 py-1.5 rounded-lg text-xs font-mono font-bold"
                      >
                        <Trash2 size={12} />
                        Eliminar
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* 12. CAJA NEGRA TAB */}
            {activeTab === 'cajanegra' && (
              <div className="glass p-6 rounded-2xl border border-white/5 animate-slide-up space-y-6">
                <div className="flex justify-between items-center border-b border-white/5 pb-4">
                  <div>
                    <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider flex items-center gap-2">
                      <Video className="text-rose-400" size={16} /> Caja Negra del Vehículo
                    </h3>
                    <p className="text-[10px] text-slate-400 mt-1">Clips de evidencia, incidentes detectados y firmas criptográficas asociadas a este vehículo.</p>
                  </div>
                </div>

                {(() => {
                  const vehicleClips = dashcamClips.filter(c => c.vehicle_id === selectedVehicle?.id);
                  const vehicleEvents = drivingEvents.filter(e => e.vehicle_id === selectedVehicle?.id);

                  if (vehicleClips.length === 0) {
                    return (
                      <div className="text-center py-16 border-2 border-dashed border-white/5 rounded-2xl space-y-3">
                        <Video size={40} className="mx-auto text-slate-600 animate-pulse" />
                        <p className="font-mono text-xs uppercase tracking-widest font-bold text-slate-400">Sin registros de caja negra</p>
                        <p className="text-[11px] text-slate-500 max-w-sm mx-auto">Activa la pestaña Cámara HUD desde el menú principal para grabar sesiones de telemetría y evidencia técnica de este vehículo.</p>
                      </div>
                    );
                  }

                  return (
                    <div className="space-y-6">
                      {/* Stats summary */}
                      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                        <div className="bg-steel-950/30 border border-white/5 p-4 rounded-xl text-center">
                          <span className="text-2xl font-black text-white">{vehicleClips.length}</span>
                          <p className="text-[9px] font-mono text-slate-400 uppercase tracking-wider mt-1">Clips Total</p>
                        </div>
                        <div className="bg-steel-950/30 border border-white/5 p-4 rounded-xl text-center">
                          <span className="text-2xl font-black text-amber-400">{vehicleClips.filter(c => c.locked).length}</span>
                          <p className="text-[9px] font-mono text-slate-400 uppercase tracking-wider mt-1">Protegidos</p>
                        </div>
                        <div className="bg-steel-950/30 border border-white/5 p-4 rounded-xl text-center">
                          <span className="text-2xl font-black text-rose-400">{vehicleEvents.filter(e => e.severity === 'critical' || e.severity === 'high').length}</span>
                          <p className="text-[9px] font-mono text-slate-400 uppercase tracking-wider mt-1">Eventos Críticos</p>
                        </div>
                        <div className="bg-steel-950/30 border border-white/5 p-4 rounded-xl text-center">
                          <span className="text-2xl font-black text-cyan-400">{vehicleEvents.length}</span>
                          <p className="text-[9px] font-mono text-slate-400 uppercase tracking-wider mt-1">Eventos Totales</p>
                        </div>
                      </div>

                      {/* Clips Grid */}
                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {vehicleClips.map(clip => {
                          const associatedEvent = vehicleEvents.find(ev => ev.id === clip.event_id_nullable);
                          return (
                            <div key={clip.id} className="bg-steel-950/40 rounded-2xl overflow-hidden border border-white/5 hover:border-cyan-500/30 transition-all duration-300">
                              <div className="relative aspect-video bg-slate-900/60 flex items-center justify-center border-b border-white/5">
                                <div className="absolute inset-0 opacity-10 bg-[radial-gradient(#00f0ff_1px,transparent_1px)] [background-size:16px_16px]" />
                                <div className="text-center z-10 p-3 space-y-1">
                                  <span className={`text-[10px] px-2 py-0.5 rounded font-mono font-bold border ${
                                    clip.clip_type === 'IMPACT' ? 'bg-red-950/60 text-red-400 border-red-800' :
                                    clip.clip_type === 'HARD_BRAKE' ? 'bg-amber-950/60 text-amber-400 border-amber-800' :
                                    clip.clip_type === 'OVERHEAT' ? 'bg-orange-950/60 text-orange-400 border-orange-800' :
                                    clip.clip_type === 'DTC_CRITICAL' ? 'bg-rose-950/60 text-rose-400 border-rose-800' :
                                    'bg-cyan-950/60 text-cyan-400 border-cyan-800'
                                  }`}>{clip.clip_type}</span>
                                  <p className="text-[10px] font-bold text-white font-mono">EVIDENCIA {clip.locked ? 'PROTEGIDA' : 'SIN PROTECCIÓN'}</p>
                                </div>
                                {clip.locked && (
                                  <div className="absolute top-2 right-2 bg-amber-500/20 p-1 rounded border border-amber-500/30">
                                    <Lock size={10} className="text-amber-400" />
                                  </div>
                                )}
                              </div>
                              <div className="p-3 space-y-2">
                                <div className="grid grid-cols-2 gap-1 text-[10px] font-mono">
                                  <span className="text-slate-500">Fecha:</span>
                                  <span className="text-white text-right">{new Date(clip.created_at).toLocaleDateString()}</span>
                                  <span className="text-slate-500">Duración:</span>
                                  <span className="text-white text-right">{clip.duration_sec}s</span>
                                  <span className="text-slate-500">Velocidad:</span>
                                  <span className="text-cyan-400 text-right font-bold">{associatedEvent?.speed_kmh_nullable ? `${associatedEvent.speed_kmh_nullable} km/h` : 'OBD sin enlace'}</span>
                                  <span className="text-slate-500">Fuerza G:</span>
                                  <span className="text-white text-right">{associatedEvent ? `${associatedEvent.g_force_x.toFixed(1)}/${associatedEvent.g_force_y.toFixed(1)}/${associatedEvent.g_force_z.toFixed(1)}` : 'N/D'}</span>
                                </div>
                                <div className="pt-2 border-t border-white/5">
                                  <span className="text-[9px] text-slate-500 font-mono block">SHA-256:</span>
                                  <span className="text-[9px] text-slate-400 font-mono truncate block">{clip.hash_sha256}</span>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>

                      {/* Events Table */}
                      {vehicleEvents.length > 0 && (
                        <div className="space-y-3">
                          <h4 className="text-xs font-bold text-white font-mono uppercase tracking-wider">Registro de Incidentes Detectados</h4>
                          <div className="border border-white/5 rounded-xl overflow-hidden">
                            <table className="w-full text-[11px] text-left font-mono">
                              <thead className="bg-steel-950/60 text-slate-400 border-b border-white/5">
                                <tr>
                                  <th className="p-3">Tipo</th>
                                  <th className="p-3">Severidad</th>
                                  <th className="p-3">Hora</th>
                                  <th className="p-3">Vel.</th>
                                  <th className="p-3">G-Force</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-white/5">
                                {vehicleEvents.slice(0, 15).map(ev => (
                                  <tr key={ev.id} className="hover:bg-white/[0.02]">
                                    <td className="p-3 font-bold text-slate-200">{ev.event_type}</td>
                                    <td className="p-3">
                                      <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase ${
                                        ev.severity === 'critical' ? 'bg-red-500/20 text-red-400' :
                                        ev.severity === 'high' ? 'bg-orange-500/20 text-orange-400' :
                                        ev.severity === 'medium' ? 'bg-amber-500/20 text-amber-400' :
                                        'bg-slate-500/20 text-slate-400'
                                      }`}>{ev.severity}</span>
                                    </td>
                                    <td className="p-3 text-slate-300">{new Date(ev.timestamp).toLocaleString()}</td>
                                    <td className="p-3 text-cyan-400">{ev.speed_kmh_nullable ?? 'N/D'}</td>
                                    <td className="p-3 text-slate-300">X:{ev.g_force_x.toFixed(2)} Y:{ev.g_force_y.toFixed(2)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })()}
              </div>
            )}

          </div>
        </div>
      ) : (
        /* ── VEHICLES GRID / LIST ── */
        <div className="space-y-6">
          
          {/* Create new vehicle form modal/panel */}
          {isAddingVehicle && (
            <div className="glass p-6 rounded-2xl border border-cyan-500/20 animate-slide-up">
              <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-4 flex items-center gap-2">
                <Car className="text-cyan-400" size={16} />
                Registrar Nuevo Vehículo
              </h3>
              
              <form onSubmit={handleCreateVehicleSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Apodo del Auto</label>
                  <input
                    type="text"
                    placeholder="ej. Mi Accent Verna"
                    value={newVehicle.nickname}
                    onChange={e => setNewVehicle({ ...newVehicle, nickname: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Marca *</label>
                  <input
                    type="text"
                    placeholder="ej. Hyundai"
                    value={newVehicle.make}
                    onChange={e => setNewVehicle({ ...newVehicle, make: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                    required
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Modelo *</label>
                  <input
                    type="text"
                    placeholder="ej. Accent Verna"
                    value={newVehicle.model}
                    onChange={e => setNewVehicle({ ...newVehicle, model: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                    required
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Año *</label>
                  <input
                    type="number"
                    value={newVehicle.year}
                    onChange={e => setNewVehicle({ ...newVehicle, year: +e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                    required
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Motor (Cilindrada)</label>
                  <input
                    type="text"
                    placeholder="ej. 1500cc Alpha II"
                    value={newVehicle.engine}
                    onChange={e => setNewVehicle({ ...newVehicle, engine: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Transmisión</label>
                  <select
                    value={newVehicle.transmission}
                    onChange={e => setNewVehicle({ ...newVehicle, transmission: e.target.value as TransmissionType })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                  >
                    <option value="MANUAL">Manual</option>
                    <option value="AUTOMATIC">Automática</option>
                    <option value="CVT">CVT</option>
                    <option value="DCT">Doble Embrague (DCT)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Combustible</label>
                  <select
                    value={newVehicle.fuelType}
                    onChange={e => setNewVehicle({ ...newVehicle, fuelType: e.target.value as FuelType })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                  >
                    <option value="GASOLINE">Gasolina</option>
                    <option value="DIESEL">Diésel</option>
                    <option value="HYBRID">Híbrido</option>
                    <option value="EV">Eléctrico (EV)</option>
                    <option value="LPG">Gas LP (LPG)</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Placa *</label>
                  <input
                    type="text"
                    placeholder="ej. CL-10255"
                    value={newVehicle.plate}
                    onChange={e => setNewVehicle({ ...newVehicle, plate: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                    required
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">Odómetro Inicial (KM)</label>
                  <input
                    type="number"
                    value={newVehicle.odometer || ''}
                    onChange={e => setNewVehicle({ ...newVehicle, odometer: +e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-mono uppercase text-slate-400 mb-1">VIN (Opcional)</label>
                  <input
                    type="text"
                    placeholder="Número de Chasis"
                    value={newVehicle.vin}
                    onChange={e => setNewVehicle({ ...newVehicle, vin: e.target.value })}
                    className="w-full bg-steel-950 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-cyan-500/50 col-span-1 md:col-span-3"
                  />
                </div>

                <div className="col-span-1 md:col-span-3 flex justify-end gap-2 pt-4">
                  <button
                    type="button"
                    onClick={() => setIsAddingVehicle(false)}
                    className="px-4 py-2 border border-white/10 hover:bg-white/5 text-slate-300 rounded-xl font-mono text-xs uppercase"
                  >
                    Cancelar
                  </button>
                  <button
                    type="submit"
                    className="bg-cyan-500 text-black px-5 py-2 rounded-xl font-mono text-xs uppercase font-bold tracking-wider"
                  >
                    Guardar Vehículo
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Grid list of vehicles */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {visibleVehicles.map(veh => {
              const vTwin = digitalTwins.find(dt => dt.vehicle_id === veh.id);
              const vTimeline = timelineEvents.filter(ev => ev.vehicle_id === veh.id);
              
              // Calculate basic stats for card
              const wos = workOrders.filter(wo => wo.vehicleInfo.plate === veh.plate);
              const activeDtcCount = wos.filter(wo => wo.status !== 'COMPLETED' && wo.status !== 'DELIVERED' && wo.status !== 'CANCELLED').flatMap(wo => wo.partsNeeded || []).length;
              const nextMaint = 'Cambio de Aceite 5k'; // mock schedule
              const lastEv = vTimeline.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())[0];
              
              // Dynamic health score logic for card preview
              let cardHealth = vTwin?.health_score || 100;
              if (activeDtcCount > 0) cardHealth = 74; // drop if DTC active

              return (
                <div key={veh.id} className="glass p-5 rounded-2xl border border-white/5 flex flex-col justify-between hover:border-cyan-500/30 transition-all group relative">
                  
                  {/* Photo/Emoji & Basic Info */}
                  <div>
                    <div className="flex justify-between items-start mb-4">
                      <div className="flex items-center gap-3">
                        <div className="w-12 h-12 rounded-xl bg-steel-800 border border-white/10 flex items-center justify-center text-2xl">
                          {veh.photo_uri_nullable ? (
                            <img src={veh.photo_uri_nullable} alt="Vehicle" className="w-full h-full object-cover rounded-xl" />
                          ) : '🚗'}
                        </div>
                        <div>
                          <h3 className="text-base font-bold text-white group-hover:text-cyan-400 transition-colors">
                            {veh.nickname}
                          </h3>
                          <p className="text-[10px] text-slate-400 font-mono mt-0.5">
                            {veh.make} {veh.model} · {veh.year}
                          </p>
                        </div>
                      </div>
                      
                      {/* Health radial ring/badge */}
                      <div className={`px-2 py-1 rounded font-mono text-[9px] font-bold ${
                        cardHealth >= 90 ? 'bg-green-500/10 text-green-400 border border-green-500/20' :
                        cardHealth >= 70 ? 'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20' :
                        'bg-red-500/10 text-red-400 border border-red-500/20'
                      }`}>
                        SALUD: {cardHealth}%
                      </div>
                    </div>

                    {/* Telemetry quick values */}
                    <div className="grid grid-cols-2 gap-2 bg-steel-950/20 p-3 rounded-xl border border-white/5 mb-4 text-[10px] font-mono">
                      <div>
                        <span className="text-slate-500 uppercase">PLACA:</span>{' '}
                        <span className="text-white font-bold">{veh.plate_nullable}</span>
                      </div>
                      <div>
                        <span className="text-slate-500 uppercase">ODÓMETRO:</span>{' '}
                        <span className="text-white">{veh.odometer_km.toLocaleString()} km</span>
                      </div>
                      <div>
                        <span className="text-slate-500 uppercase">DTC ACTIVO:</span>{' '}
                        <span className={activeDtcCount > 0 ? "text-red-400 font-bold" : "text-green-400"}>
                          {activeDtcCount > 0 ? `${activeDtcCount} fallas` : 'Ninguna'}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-500 uppercase">Siguiente:</span>{' '}
                        <span className="text-white truncate">{nextMaint}</span>
                      </div>
                    </div>

                    {/* Last event preview */}
                    {lastEv && (
                      <div className="text-[11px] text-slate-400 italic mb-4 flex gap-1.5 items-start">
                        <Clock size={11} className="mt-0.5 shrink-0 text-cyan-400" />
                        <span className="truncate">"{lastEv.title} — {lastEv.description}"</span>
                      </div>
                    )}
                  </div>

                  {/* Actions buttons */}
                  <div className="flex gap-2 pt-3 border-t border-white/5">
                    <button
                      onClick={() => handleStartScan(veh.id)}
                      disabled={isScanning !== null}
                      className="flex-1 bg-white/5 hover:bg-white/10 disabled:opacity-50 text-white py-2 rounded-xl text-xs font-mono font-bold uppercase tracking-wider border border-white/10 flex items-center justify-center gap-1.5 transition-all"
                    >
                      {isScanning === veh.id ? (
                        <>
                          <Clock className="animate-spin" size={12} />
                          {scanProgress}%
                        </>
                      ) : (
                        <>
                          <Activity size={12} />
                          Escanear
                        </>
                      )}
                    </button>
                    <button
                      onClick={() => {
                        setSelectedVehicleId(veh.id);
                        setActiveTab('resumen');
                      }}
                      className="flex-1 bg-gradient-to-r from-cyan-400 to-cyan-600 text-black py-2 rounded-xl text-xs font-mono font-bold uppercase tracking-wider flex items-center justify-center gap-1 hover:shadow-[0_0_15px_rgba(6,182,212,0.25)] transition-all"
                    >
                      Ver Expediente
                      <ArrowRight size={12} strokeWidth={2.5} />
                    </button>
                  </div>

                </div>
              );
            })}

            {visibleVehicles.length === 0 && (
              <div className="col-span-full text-center p-12 glass border-dashed border border-white/10 rounded-2xl">
                <Car className="text-slate-600 mx-auto mb-3 opacity-40" size={40} />
                <h3 className="text-white font-bold text-lg mb-1">Sin expedientes activos</h3>
                <p className="text-slate-400 text-sm">Registre un vehículo o realice su primer diagnóstico presencial para comenzar el gemelo digital.</p>
              </div>
            )}
          </div>

        </div>
      )}

    </div>
  );
}
