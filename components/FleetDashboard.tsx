import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Car,
  Building2,
  Users,
  FileText,
  AlertTriangle,
  ShieldCheck,
  Settings,
  DollarSign,
  Activity,
  MapPin,
  Plus,
  CheckCircle,
  XCircle,
  Clock,
  Link2,
  Wrench,
  RefreshCw,
  Send,
  Lock,
  FileCode,
  Check,
  Award,
  Eye,
  Download,
  Info,
  Zap,
  AlertCircle,
  Wifi,
  WifiOff,
  UserCheck,
  Compass,
  FileSpreadsheet,
  FileDown
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  BarChart,
  Bar,
  Cell,
  PieChart,
  Pie
} from 'recharts';
import { saveState, loadState } from '../services/storage';

// --- TYPES & INTERFACES (Frontend-specific subset of types.ts) ---
import {
  FleetOrganization,
  FleetBranch,
  FleetVehicle,
  FleetDriver,
  DvirInspection,
  DvirChecklistItem,
  DvirObdSnapshot,
  FleetEvidence,
  DvirSignature,
  FleetAlert,
  FleetMaintenanceTask,
  FleetWorkOrder,
  FleetCostEntry,
  FleetTrip,
  FleetSyncQueueItem,
  FleetPlan,
  FleetRole,
  FleetVehicleStatus,
  FleetDriverStatus,
  InspectionType,
  DvirStatus,
  OverallResult,
  ItemStatus,
  ChecklistSeverity,
  FleetAlertType,
  FleetAlertStatus,
  FleetMaintenancePriority,
  FleetMaintenanceStatus,
  FleetCostType
} from '../types';

export default function FleetDashboard() {
  // --- ROLE-BASED ACCESS CONTROL SIMULATOR ---
  const [currentUserRole, setCurrentUserRole] = useState<FleetRole>('ADMIN');
  const [currentUserId, setCurrentUserId] = useState<string>('usr_corp_admin');
  const [currentUserName, setCurrentUserName] = useState<string>('Jor Delmir');

  // --- OFFLINE-FIRST STATE ---
  const [isOnline, setIsOnline] = useState<boolean>(true);
  const [syncQueue, setSyncQueue] = useState<FleetSyncQueueItem[]>(() =>
    loadState('fleet_sync_queue', [])
  );
  const [syncConsoleLogs, setSyncConsoleLogs] = useState<string[]>(['Sincronización inicializada. Canales en línea.']);

  // --- ENTITIES STATE ---
  const [organizations, setOrganizations] = useState<FleetOrganization[]>(() => {
    const stored = loadState('fleet_organizations', []);
    if (stored.length > 0) return stored;
    return [
      {
        id: 'fleet_org_1',
        owner_user_id: 'usr_corp_admin',
        name: 'Logística Elysium Vanguard',
        legal_name_nullable: 'Elysium Vanguard S.A.',
        tax_id_nullable: 'TAX-998877-B',
        phone: '+506 8888-9999',
        email: 'flotas@elysiumvanguard.com',
        country: 'Costa Rica',
        province: 'San José',
        address_nullable: 'Complejo Industrial Santa Rosa, Bodega 4',
        plan: 'FLEET_PRO',
        status: 'ACTIVE',
        created_at: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        updated_at: new Date().toISOString()
      }
    ];
  });

  const [activeFleetId, setActiveFleetId] = useState<string>('fleet_org_1');

  const [branches, setBranches] = useState<FleetBranch[]>(() => {
    const stored = loadState('fleet_branches', []);
    if (stored.length > 0) return stored;
    return [
      { id: 'branch_1', fleet_id: 'fleet_org_1', name: 'Sucursal Central - San José', location: 'San José Centro', manager_user_id_nullable: 'usr_mgr_sanjose', created_at: new Date().toISOString() },
      { id: 'branch_2', fleet_id: 'fleet_org_1', name: 'Sucursal Oeste - Escazú', location: 'Escazú Guachipelín', manager_user_id_nullable: 'usr_mgr_escazu', created_at: new Date().toISOString() }
    ];
  });

  const [drivers, setDrivers] = useState<FleetDriver[]>(() => {
    const stored = loadState('fleet_drivers', []);
    if (stored.length > 0) return stored;
    return [
      {
        id: 'driver_1',
        fleet_id: 'fleet_org_1',
        user_id_nullable: 'usr_driver_carlos',
        full_name: 'Carlos Mendoza',
        phone: '+506 8765-4321',
        license_number_nullable: 'LIC-SJ-88726',
        license_expiration_nullable: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
        assigned_vehicle_id_nullable: 'f_veh_1',
        status: 'ACTIVE',
        created_at: new Date().toISOString()
      },
      {
        id: 'driver_2',
        fleet_id: 'fleet_org_1',
        user_id_nullable: 'usr_driver_ana',
        full_name: 'Ana Laura Rojas',
        phone: '+506 8321-7654',
        license_number_nullable: 'LIC-AL-11234',
        license_expiration_nullable: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(), // VENCIDA
        assigned_vehicle_id_nullable: null,
        status: 'ACTIVE',
        created_at: new Date().toISOString()
      }
    ];
  });

  const [vehicles, setVehicles] = useState<FleetVehicle[]>(() => {
    const stored = loadState('fleet_vehicles', []);
    if (stored.length > 0) return stored;
    return [
      {
        id: 'f_veh_1',
        fleet_id: 'fleet_org_1',
        branch_id_nullable: 'branch_1',
        vehicle_profile_id: 'veh_init_c1_0', // Toyota Corolla
        internal_code: 'EV-COROLLA-01',
        assigned_driver_id_nullable: 'driver_1',
        status: 'ACTIVE',
        odometer_km: 65000,
        last_dvir_id_nullable: 'dvir_init_1',
        last_health_score: 95,
        last_scan_at_nullable: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      },
      {
        id: 'f_veh_2',
        fleet_id: 'fleet_org_1',
        branch_id_nullable: 'branch_2',
        vehicle_profile_id: 'veh_init_c1_1', // Hyundai Tucson
        internal_code: 'EV-TUCSON-02',
        assigned_driver_id_nullable: null,
        status: 'IN_MAINTENANCE',
        odometer_km: 32000,
        last_dvir_id_nullable: null,
        last_health_score: 82,
        last_scan_at_nullable: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      }
    ];
  });

  const [dvirInspections, setDvirInspections] = useState<DvirInspection[]>(() => loadState('fleet_dvir_inspections', []));
  const [dvirItems, setDvirItems] = useState<DvirChecklistItem[]>(() => loadState('fleet_dvir_items', []));
  const [obdSnapshots, setObdSnapshots] = useState<DvirObdSnapshot[]>(() => loadState('fleet_obd_snapshots', []));
  const [dvirSignatures, setDvirSignatures] = useState<DvirSignature[]>(() => loadState('fleet_dvir_signatures', []));
  const [alerts, setAlerts] = useState<FleetAlert[]>(() => loadState('fleet_alerts', []));
  const [workOrders, setWorkOrders] = useState<FleetWorkOrder[]>(() => loadState('fleet_work_orders', []));
  const [maintenanceTasks, setMaintenanceTasks] = useState<FleetMaintenanceTask[]>(() => loadState('fleet_maintenance_tasks', []));
  
  const [costEntries, setCostEntries] = useState<FleetCostEntry[]>(() => {
    const stored = loadState('fleet_cost_entries', []);
    if (stored.length > 0) return stored;
    return [
      { id: 'cost_1', fleet_id: 'fleet_org_1', vehicle_id: 'f_veh_1', type: 'FUEL', amount: 35000, currency: 'CRC', provider_id_nullable: 'Gasolinera Uno', description: 'Carga de combustible regular', receipt_uri_nullable: null, related_work_order_id_nullable: null, created_at: new Date(Date.now() - 4 * 24 * 60 * 60 * 1000).toISOString() },
      { id: 'cost_2', fleet_id: 'fleet_org_1', vehicle_id: 'f_veh_2', type: 'MAINTENANCE', amount: 120000, currency: 'CRC', provider_id_nullable: 'Taller MEET Central', description: 'Afinamiento mayor y cambio de pastillas delanteras', receipt_uri_nullable: null, related_work_order_id_nullable: 'wo_init_1', created_at: new Date(Date.now() - 8 * 24 * 60 * 60 * 1000).toISOString() }
    ];
  });

  const [trips, setTrips] = useState<FleetTrip[]>(() => {
    const stored = loadState('fleet_trips', []);
    if (stored.length > 0) return stored;
    return [
      { id: 'trip_1', fleet_id: 'fleet_org_1', vehicle_id: 'f_veh_1', driver_id: 'driver_1', started_at: new Date(Date.now() - 6 * 3600 * 1000).toISOString(), ended_at: new Date(Date.now() - 5 * 3600 * 1000).toISOString(), distance_km: 42.5, eco_score: 92, harsh_brakes: 1, harsh_accels: 0, fuel_used_estimated: 3.8, created_at: new Date().toISOString() },
      { id: 'trip_2', fleet_id: 'fleet_org_1', vehicle_id: 'f_veh_1', driver_id: 'driver_1', started_at: new Date(Date.now() - 28 * 3600 * 1000).toISOString(), ended_at: new Date(Date.now() - 26 * 3600 * 1000).toISOString(), distance_km: 88.2, eco_score: 84, harsh_brakes: 3, harsh_accels: 2, fuel_used_estimated: 8.1, created_at: new Date().toISOString() }
    ];
  });

  const [evidence, setEvidence] = useState<FleetEvidence[]>(() => loadState('fleet_evidence', []));

  // --- SAVE STATE HOOKS ---
  useEffect(() => { saveState('fleet_organizations', organizations); }, [organizations]);
  useEffect(() => { saveState('fleet_branches', branches); }, [branches]);
  useEffect(() => { saveState('fleet_drivers', drivers); }, [drivers]);
  useEffect(() => { saveState('fleet_vehicles', vehicles); }, [vehicles]);
  useEffect(() => { saveState('fleet_dvir_inspections', dvirInspections); }, [dvirInspections]);
  useEffect(() => { saveState('fleet_dvir_items', dvirItems); }, [dvirItems]);
  useEffect(() => { saveState('fleet_obd_snapshots', obdSnapshots); }, [obdSnapshots]);
  useEffect(() => { saveState('fleet_dvir_signatures', dvirSignatures); }, [dvirSignatures]);
  useEffect(() => { saveState('fleet_alerts', alerts); }, [alerts]);
  useEffect(() => { saveState('fleet_work_orders', workOrders); }, [workOrders]);
  useEffect(() => { saveState('fleet_maintenance_tasks', maintenanceTasks); }, [maintenanceTasks]);
  useEffect(() => { saveState('fleet_cost_entries', costEntries); }, [costEntries]);
  useEffect(() => { saveState('fleet_trips', trips); }, [trips]);
  useEffect(() => { saveState('fleet_evidence', evidence); }, [evidence]);
  useEffect(() => { saveState('fleet_sync_queue', syncQueue); }, [syncQueue]);

  // --- UI NAVIGATION ---
  const [activeTab, setActiveTab] = useState<
    'SUMMARY' | 'VEHICLES' | 'DRIVERS' | 'DVIR' | 'ALERTS' | 'MAINTENANCE' | 'WORK_ORDERS' | 'COSTS' | 'LIVELINK' | 'CONFIG'
  >('SUMMARY');

  // --- SELECTION & FORM MODAL STATES ---
  const [showAddOrgModal, setShowAddOrgModal] = useState(false);
  const [showAddVehicleModal, setShowAddVehicleModal] = useState(false);
  const [showAddDriverModal, setShowAddDriverModal] = useState(false);
  const [showNewDvirModal, setShowNewDvirModal] = useState(false);
  const [showNewCostModal, setShowNewCostModal] = useState(false);
  const [selectedVehicleForDetail, setSelectedVehicleForDetail] = useState<string | null>(null);
  const [selectedDvirForDetail, setSelectedDvirForDetail] = useState<string | null>(null);

  // --- FORM FIELDS ---
  const [newOrgName, setNewOrgName] = useState('');
  const [newOrgLegal, setNewOrgLegal] = useState('');
  const [newOrgTaxId, setNewOrgTaxId] = useState('');
  const [newOrgPhone, setNewOrgPhone] = useState('');
  const [newOrgEmail, setNewOrgEmail] = useState('');
  const [newOrgPlan, setNewOrgPlan] = useState<FleetPlan>('FREE_FLEET');

  const [newVehCode, setNewVehCode] = useState('');
  const [newVehPlate, setNewVehPlate] = useState('');
  const [newVehBrand, setNewVehBrand] = useState('Hyundai');
  const [newVehModel, setNewVehModel] = useState('Accent Verna');
  const [newVehYear, setNewVehYear] = useState('2005');
  const [newVehOdo, setNewVehOdo] = useState('142000');
  const [newVehBranch, setNewVehBranch] = useState('');
  const [newVehDriver, setNewVehDriver] = useState('');

  const [newDriverName, setNewDriverName] = useState('');
  const [newDriverPhone, setNewDriverPhone] = useState('');
  const [newDriverLicense, setNewDriverLicense] = useState('');
  const [newDriverExpiry, setNewDriverExpiry] = useState('');

  // --- DVIR CREATOR STATE ---
  const [dvirVehId, setDvirVehId] = useState('');
  const [dvirType, setDvirType] = useState<InspectionType>('PRE_TRIP');
  const [dvirOdo, setDvirOdo] = useState(0);
  const [dvirChecklistStates, setDvirChecklistStates] = useState<Record<string, { status: ItemStatus; notes: string; photo_uri: string | null }>>({});
  
  // OBD simulator inside DVIR
  const [obdConnected, setObdConnected] = useState(false);
  const [simulatedP0230Active, setSimulatedP0230Active] = useState(true);
  const [obdCapturedSnapshot, setObdCapturedSnapshot] = useState<Partial<DvirObdSnapshot> | null>(null);
  const [isCapturingObd, setIsCapturingObd] = useState(false);

  // Signature inside DVIR
  const [signerName, setSignerName] = useState('');
  const [signerRole, setSignerRole] = useState<FleetRole>('DRIVER');
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [signatureUri, setSignatureUri] = useState<string | null>(null);

  // Cost Creator State
  const [costVehId, setCostVehId] = useState('');
  const [costType, setCostType] = useState<FleetCostType>('MAINTENANCE');
  const [costAmount, setCostAmount] = useState('');
  const [costDesc, setCostDesc] = useState('');
  const [costProvider, setCostProvider] = useState('');

  // --- LIVELINK MONITOR SIMULATOR ---
  const [liveVehId, setLiveVehId] = useState<string>('');
  const [liveTelemetry, setLiveTelemetry] = useState<{
    rpm: number;
    voltage: number;
    coolant: number;
    speed: number;
    load: number;
    connection: 'CONNECTED' | 'DISCONNECTED';
    p0230Detected: boolean;
  }>({
    rpm: 0,
    voltage: 12.4,
    coolant: 22,
    speed: 0,
    load: 0,
    connection: 'DISCONNECTED',
    p0230Detected: false
  });
  const liveIntervalRef = useRef<any | null>(null);
  const liveCanvasRef = useRef<HTMLCanvasElement | null>(null);

  // --- OFFLINE SYNC WORKER ---
  useEffect(() => {
    if (isOnline && syncQueue.length > 0) {
      triggerSyncProcess();
    }
  }, [isOnline, syncQueue.length]);

  const triggerSyncProcess = async () => {
    addConsoleLog(`Iniciando sincronización de cola de cambios offline (${syncQueue.length} pendientes)...`);
    const queue = [...syncQueue];
    
    // Process queue items one by one simulating network latency
    for (let i = 0; i < queue.length; i++) {
      const item = queue[i];
      addConsoleLog(`Procesando transacción [${item.idempotency_key.substring(0, 8)}] en tabla ${item.table} (Acción: ${item.action})...`);
      
      await new Promise(resolve => setTimeout(resolve, 800)); // Network delay
      
      if (Math.random() < 0.05) { // 5% simulated fail rate for retry
        addConsoleLog(`⚠️ Mismatch en handshake de red para transacción [${item.idempotency_key.substring(0, 8)}]. Reintentando en 2s (Exponencial)...`);
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
      
      addConsoleLog(`✅ Transmisión exitosa. Clave idempotente verificada con Supabase.`);
    }

    setSyncQueue([]);
    addConsoleLog('🎉 Sincronización corporativa completada. Servidores en la nube actualizados.');
  };

  const addConsoleLog = (text: string) => {
    setSyncConsoleLogs(prev => [`[${new Date().toLocaleTimeString()}] ${text}`, ...prev.slice(0, 19)]);
  };

  const toggleOnlineMode = () => {
    const nextState = !isOnline;
    setIsOnline(nextState);
    addConsoleLog(nextState ? 'Conexión restablecida. Red en la nube unificada.' : 'Modo fuera de línea activado. Cambios se acumularán localmente.');
  };

  // Push to local state and queue if offline
  const handleWriteAction = (table: string, action: 'CREATE' | 'UPDATE' | 'DELETE', payload: any) => {
    if (!isOnline) {
      const queueItem: FleetSyncQueueItem = {
        id: 'sync_' + Math.random().toString(36).substr(2, 9),
        idempotency_key: 'idem_' + Math.random().toString(36).substr(2, 12),
        action,
        table,
        payload,
        retry_count: 0,
        created_at: new Date().toISOString()
      };
      setSyncQueue(prev => [...prev, queueItem]);
      addConsoleLog(`Dispositivo Desconectado: Transacción encolada en cola local (${table} -> ${action}).`);
    } else {
      addConsoleLog(`Enviando transacción directa a Supabase: ${table} -> ${action}`);
    }
  };

  // Check RBAC limits
  const activeOrg = organizations.find(o => o.id === activeFleetId);
  const isDriver = currentUserRole === 'DRIVER';
  const isViewer = currentUserRole === 'VIEWER';
  const isAuditor = currentUserRole === 'AUDITOR';
  const isMechanic = currentUserRole === 'MECHANIC';

  // Limit access based on roles
  useEffect(() => {
    if (isDriver && activeTab !== 'DVIR' && activeTab !== 'LIVELINK') {
      setActiveTab('DVIR');
    }
  }, [currentUserRole, isDriver]);

  // Set default driver assigned vehicle odometer
  useEffect(() => {
    if (dvirVehId) {
      const v = vehicles.find(veh => veh.id === dvirVehId);
      if (v) setDvirOdo(v.odometer_km);
    }
  }, [dvirVehId]);

  // --- BUSINESS LOGIC ACTIONS ---

  const handleCreateOrg = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newOrgName || !newOrgPhone || !newOrgEmail) return;

    const newOrg: FleetOrganization = {
      id: 'fleet_org_' + Math.random().toString(36).substr(2, 9),
      owner_user_id: currentUserId,
      name: newOrgName,
      legal_name_nullable: newOrgLegal || null,
      tax_id_nullable: newOrgTaxId || null,
      phone: newOrgPhone,
      email: newOrgEmail,
      country: 'Costa Rica',
      province: null,
      address_nullable: null,
      plan: newOrgPlan,
      status: 'ACTIVE',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    };

    setOrganizations(prev => [...prev, newOrg]);
    setActiveFleetId(newOrg.id);
    handleWriteAction('fleet_organizations', 'CREATE', newOrg);
    setShowAddOrgModal(false);
    
    // reset
    setNewOrgName('');
    setNewOrgLegal('');
    setNewOrgTaxId('');
    setNewOrgPhone('');
    setNewOrgEmail('');
  };

  const handleCreateVehicle = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newVehCode || !newVehBrand || !newVehModel) return;

    const newVehId = 'f_veh_' + Math.random().toString(36).substr(2, 9);
    const newVeh: FleetVehicle = {
      id: newVehId,
      fleet_id: activeFleetId,
      branch_id_nullable: newVehBranch || null,
      vehicle_profile_id: 'veh_custom_' + Date.now(),
      internal_code: newVehCode,
      assigned_driver_id_nullable: newVehDriver || null,
      status: 'ACTIVE',
      odometer_km: parseInt(newVehOdo) || 0,
      last_dvir_id_nullable: null,
      last_health_score: 100,
      last_scan_at_nullable: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString()
    };

    setVehicles(prev => [...prev, newVeh]);

    // Handle back-pointer on driver if assigned
    if (newVehDriver) {
      setDrivers(prev =>
        prev.map(d =>
          d.id === newVehDriver ? { ...d, assigned_vehicle_id_nullable: newVehId } : d
        )
      );
    }

    handleWriteAction('fleet_vehicles', 'CREATE', newVeh);
    setShowAddVehicleModal(false);

    // reset
    setNewVehCode('');
    setNewVehPlate('');
    setNewVehBrand('Hyundai');
    setNewVehModel('Accent Verna');
    setNewVehYear('2005');
    setNewVehOdo('142000');
    setNewVehBranch('');
    setNewVehDriver('');
  };

  const autoFillHyundaiAccentVerna = () => {
    setNewVehCode('EV-ACCENT-VERNA-05');
    setNewVehPlate('VER-005');
    setNewVehBrand('Hyundai');
    setNewVehModel('Accent Verna');
    setNewVehYear('2005');
    setNewVehOdo('154200');
  };

  const handleCreateDriver = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDriverName || !newDriverPhone) return;

    const newDriver: FleetDriver = {
      id: 'driver_' + Math.random().toString(36).substr(2, 9),
      fleet_id: activeFleetId,
      user_id_nullable: 'usr_' + Math.random().toString(36).substr(2, 5),
      full_name: newDriverName,
      phone: newDriverPhone,
      license_number_nullable: newDriverLicense || null,
      license_expiration_nullable: newDriverExpiry ? new Date(newDriverExpiry).toISOString() : null,
      assigned_vehicle_id_nullable: null,
      status: 'ACTIVE',
      created_at: new Date().toISOString()
    };

    setDrivers(prev => [...prev, newDriver]);
    handleWriteAction('fleet_drivers', 'CREATE', newDriver);
    setShowAddDriverModal(false);

    setNewDriverName('');
    setNewDriverPhone('');
    setNewDriverLicense('');
    setNewDriverExpiry('');
  };

  // --- DVIR CAPTURE SYSTEM ---
  
  const handleObdCaptureSimulation = async () => {
    setIsCapturingObd(true);
    addConsoleLog('Conectando con transceptor OBD-II a través de Bluetooth...');
    await new Promise(resolve => setTimeout(resolve, 1500));

    if (!obdConnected) {
      setObdCapturedSnapshot(null);
      setIsCapturingObd(false);
      addConsoleLog('Error: Adaptador OBD no conectado. DVIR quedará sin evidencia OBD real.');
      return;
    }

    const snap: Partial<DvirObdSnapshot> = {
      connection_state: 'CONNECTED',
      adapter_quality: 'EXCELLENT',
      dtcs_active: simulatedP0230Active ? ['P0230'] : [],
      dtcs_pending: [],
      dtcs_permanent: [],
      readiness: { misfire: true, fuelSystem: true, components: true, catalyst: true },
      voltage: simulatedP0230Active ? 13.1 : 14.1, // 13.1V falls under primary pump electrical fail
      rpm_nullable: 850,
      coolant_temp_nullable: 88,
      odometer_nullable: dvirOdo,
      raw_hash: simulatedP0230Active 
        ? '71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e' // exact hash matches parity vectors
        : '756fc3429ffd2b66ea0a1453470b63c33e84e0831537dbba2d70cc9722e3dd99'
    };

    setObdCapturedSnapshot(snap);
    setIsCapturingObd(false);
    addConsoleLog(`Snapshot OBD capturado correctamente. Códigos activos detectados: [${snap.dtcs_active?.join(', ') || 'ninguno'}].`);
  };

  // Drawing pad logic
  const handleClearSignature = () => {
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        setSignatureUri(null);
      }
    }
  };

  const handleSaveSignature = () => {
    const canvas = canvasRef.current;
    if (canvas) {
      const uri = canvas.toDataURL();
      setSignatureUri(uri);
      addConsoleLog('Firma del conductor registrada y pre-hachada.');
    }
  };

  const drawOnCanvas = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    if (e.buttons !== 1) return; // Only draw when clicking
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#34d399'; // Emerald-400

    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    ctx.lineTo(x, y);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  const resetDvirForm = () => {
    setDvirVehId('');
    setDvirType('PRE_TRIP');
    setDvirOdo(0);
    setDvirChecklistStates({});
    setObdCapturedSnapshot(null);
    setSignatureUri(null);
    setSignerName('');
  };

  // Submit and lock DVIR
  const handleSubmitDvir = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!dvirVehId || !signerName || !signatureUri) {
      alert('Por favor complete el vehículo, nombre del firmante y registre su firma antes de enviar.');
      return;
    }

    const selectedVehicle = vehicles.find(v => v.id === dvirVehId);
    const selectedDriver = drivers.find(d => d.assigned_vehicle_id_nullable === dvirVehId || d.id === newVehDriver);
    const driverId = selectedDriver?.id || 'driver_unknown';

    // 1. Determine overall result
    let overallRes: OverallResult = 'PASS';
    let requiresRepair = false;

    // Check checklist items
    const failedItems = Object.keys(dvirChecklistStates).filter(
      key => dvirChecklistStates[key].status === 'FAILED'
    );
    const warningItems = Object.keys(dvirChecklistStates).filter(
      key => dvirChecklistStates[key].status === 'WARNING'
    );

    if (failedItems.length > 0) {
      overallRes = 'FAIL_MAJOR';
      requiresRepair = true;
    } else if (warningItems.length > 0) {
      overallRes = 'PASS_WITH_OBSERVATIONS';
    }

    // Check OBD Snapshot constraints
    const obdHasP0230 = obdCapturedSnapshot?.dtcs_active?.includes('P0230');
    if (obdHasP0230) {
      overallRes = 'OUT_OF_SERVICE'; // fuel pump fault is out of service!
      requiresRepair = true;
    }

    const dvirId = 'dvir_' + Math.random().toString(36).substr(2, 9);
    
    // Compute Cryptographic report signature hash (SHA-256)
    // Minimally formatted following parity guidelines
    const rawPayloadString = `${dvirVehId}|${driverId}|${Date.now()}|${overallRes}|${obdCapturedSnapshot?.raw_hash || 'NO_OBD'}`;
    
    // Simple mock SHA-256 generator
    const calculatedHash = await generateMockSha256(rawPayloadString);

    // Create DvirInspection object
    const newInspection: DvirInspection = {
      id: dvirId,
      fleet_id: activeFleetId,
      vehicle_id: dvirVehId,
      driver_id: driverId,
      inspection_type: dvirType,
      status: requiresRepair ? 'REQUIRES_REPAIR' : 'APPROVED',
      odometer_km: dvirOdo,
      location_lat_nullable: 9.9281, // San Jose Costa Rica Lat
      location_lng_nullable: -84.0907, // Long
      started_at: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
      completed_at_nullable: new Date().toISOString(),
      signed_at_nullable: new Date().toISOString(),
      overall_result: overallRes,
      report_id_nullable: 'rep_' + calculatedHash.substring(0, 16),
      created_at: new Date().toISOString()
    };

    // Create signature object
    const newSig: DvirSignature = {
      id: 'sig_' + Math.random().toString(36).substr(2, 9),
      inspection_id: dvirId,
      signer_user_id: currentUserId,
      signer_name: signerName,
      signer_role: signerRole,
      signature_uri: signatureUri,
      signed_at: new Date().toISOString(),
      hash_sha256: calculatedHash
    };

    // Update States
    setDvirInspections(prev => [newInspection, ...prev]);
    setDvirSignatures(prev => [newSig, ...prev]);

    // Save individual checklist items
    const createdItems: DvirChecklistItem[] = [];
    Object.keys(dvirChecklistStates).forEach(key => {
      const state = dvirChecklistStates[key];
      const item: DvirChecklistItem = {
        id: 'item_' + Math.random().toString(36).substr(2, 9),
        inspection_id: dvirId,
        category: getCategoryFromKey(key) as any,
        item_key: key,
        label: getLabelFromKey(key),
        status: state.status,
        severity: getSeverityFromKey(key),
        notes: state.notes,
        photo_required: state.status === 'FAILED',
        photo_uri_nullable: state.photo_uri,
        created_at: new Date().toISOString()
      };
      createdItems.push(item);
    });
    setDvirItems(prev => [...prev, ...createdItems]);

    // Save OBD snapshot
    if (obdCapturedSnapshot) {
      const newObd: DvirObdSnapshot = {
        inspection_id: dvirId,
        connection_state: obdCapturedSnapshot.connection_state || 'DISCONNECTED',
        adapter_quality: obdCapturedSnapshot.adapter_quality || 'UNKNOWN',
        dtcs_active: obdCapturedSnapshot.dtcs_active || [],
        dtcs_pending: obdCapturedSnapshot.dtcs_pending || [],
        dtcs_permanent: obdCapturedSnapshot.dtcs_permanent || [],
        readiness: obdCapturedSnapshot.readiness || {},
        voltage: obdCapturedSnapshot.voltage || 14.0,
        rpm_nullable: obdCapturedSnapshot.rpm_nullable || null,
        coolant_temp_nullable: obdCapturedSnapshot.coolant_temp_nullable || null,
        odometer_nullable: obdCapturedSnapshot.odometer_nullable || null,
        raw_hash: obdCapturedSnapshot.raw_hash || '',
        created_at: new Date().toISOString()
      };
      setObdSnapshots(prev => [newObd, ...prev]);
    }

    // Trigger Business Actions on Vehicle Status & Alerts
    let nextVehicleStatus: FleetVehicleStatus = 'ACTIVE';
    if (requiresRepair || obdHasP0230) {
      nextVehicleStatus = 'OUT_OF_SERVICE';
      
      // Auto-create alert
      const alertId = 'alert_' + Math.random().toString(36).substr(2, 9);
      const newAlert: FleetAlert = {
        id: alertId,
        fleet_id: activeFleetId,
        vehicle_id: dvirVehId,
        driver_id_nullable: driverId,
        alert_type: obdHasP0230 ? 'DTC_CRITICAL' : 'DVIR_FAILED',
        severity: 'CRITICAL',
        title: obdHasP0230 ? 'DTC Crítico Detectado: Circuito Bomba Combustible' : 'Inspección DVIR Reprobada',
        description: obdHasP0230 
          ? `Código P0230 activo en ${selectedVehicle?.internal_code}. Pérdida de alimentación primaria en riel.`
          : `El conductor reportó fallos críticos en el checklist de inspección para ${selectedVehicle?.internal_code}.`,
        status: 'OPEN',
        source: obdHasP0230 ? 'OBD' : 'DVIR',
        created_at: new Date().toISOString(),
        resolved_at_nullable: null
      };
      setAlerts(prev => [newAlert, ...prev]);

      // Auto-create Work Order
      const woId = 'wo_' + Math.random().toString(36).substr(2, 9);
      const newWO: FleetWorkOrder = {
        id: woId,
        fleet_id: activeFleetId,
        vehicle_id: dvirVehId,
        created_by_user_id: currentUserId,
        assigned_provider_id_nullable: 'provider_meet_taller',
        title: obdHasP0230 ? 'Corrección Eléctrica: Circuito Primario Bomba (P0230)' : 'Reparación por Defectos DVIR',
        description: obdHasP0230
          ? `Inspección física del fusible F7 (20A) e intercambio de relé de bomba de gasolina. Resolver DTC P0230.`
          : `Resolver defectos reportados en el DVIR: ${failedItems.map(getLabelFromKey).join(', ')}.`,
        source: obdHasP0230 ? 'DTC' : 'DVIR',
        status: 'OPEN',
        priority: 'CRITICAL',
        estimated_cost_nullable: obdHasP0230 ? 45000 : 25000,
        final_cost_nullable: null,
        started_at_nullable: null,
        completed_at_nullable: null,
        report_id_nullable: null
      };
      setWorkOrders(prev => [newWO, ...prev]);
      addConsoleLog(`Orden de trabajo automatizada [${woId.substring(0, 6)}] creada por fallo crítico.`);
    }

    // Update Vehicle
    setVehicles(prev =>
      prev.map(v =>
        v.id === dvirVehId
          ? {
              ...v,
              status: nextVehicleStatus,
              odometer_km: dvirOdo,
              last_dvir_id_nullable: dvirId,
              last_health_score: requiresRepair ? Math.max(v.last_health_score - 20, 50) : v.last_health_score,
              last_scan_at_nullable: obdCapturedSnapshot ? new Date().toISOString() : v.last_scan_at_nullable,
              updated_at: new Date().toISOString()
            }
          : v
      )
    );

    // Sync action queue write
    handleWriteAction('dvir_inspections', 'CREATE', newInspection);
    handleWriteAction('dvir_signatures', 'CREATE', newSig);

    setShowNewDvirModal(false);
    resetDvirForm();
    addConsoleLog('DVIR enviado exitosamente. Archivo cerrado y firmado con código hash unificado.');
  };

  const generateMockSha256 = async (str: string): Promise<string> => {
    // Generate simple deterministic mockup hash for offline compatibility
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash << 5) - hash + str.charCodeAt(i);
      hash |= 0;
    }
    return Math.abs(hash).toString(16).padStart(8, '0') + 'c9722e3dd99a0a1453470b63c33e84e0831537dbba2d70cc9722e';
  };

  const getCategoryFromKey = (key: string): string => {
    if (key.includes('freno')) return 'BRAKES';
    if (key.includes('luces')) return 'LIGHTS';
    if (key.includes('llanta')) return 'TIRES';
    if (key.includes('motor') || key.includes('fuga')) return 'MOTOR';
    if (key.includes('voltaje')) return 'BATTERY';
    if (key.includes('document')) return 'DOCUMENTS';
    return 'BODY';
  };

  const getLabelFromKey = (key: string): string => {
    switch (key) {
      case 'frenos': return 'Frenos y mordazas principales';
      case 'luces_noche': return 'Faros principales de conducción nocturna';
      case 'llantas_desgaste': return 'Desgaste e integridad de llantas';
      case 'fuga_combustible': return 'Fuga activa de combustible o fluidos inflamables';
      case 'voltaje_alternador': return 'Voltaje de batería y alternador';
      case 'papeles_al_dia': return 'Documentos vehiculares al día';
      default: return 'Inspección de carrocería general';
    }
  };

  const getSeverityFromKey = (key: string): ChecklistSeverity => {
    if (['frenos', 'fuga_combustible', 'luces_noche'].includes(key)) return 'CRITICAL';
    if (['llantas_desgaste', 'voltaje_alternador'].includes(key)) return 'HIGH';
    return 'LOW';
  };

  // Add Fleet Cost Entry
  const handleCreateCost = (e: React.FormEvent) => {
    e.preventDefault();
    if (!costVehId || !costAmount) return;

    const newCost: FleetCostEntry = {
      id: 'cost_' + Math.random().toString(36).substr(2, 9),
      fleet_id: activeFleetId,
      vehicle_id: costVehId,
      type: costType,
      amount: parseFloat(costAmount),
      currency: 'CRC',
      provider_id_nullable: costProvider || null,
      description: costDesc,
      receipt_uri_nullable: null,
      related_work_order_id_nullable: null,
      created_at: new Date().toISOString()
    };

    setCostEntries(prev => [newCost, ...prev]);
    handleWriteAction('fleet_cost_entries', 'CREATE', newCost);
    setShowNewCostModal(false);

    setCostVehId('');
    setCostAmount('');
    setCostDesc('');
    setCostProvider('');
  };

  // Complete work order in fleet
  const handleResolveWorkOrder = (woId: string) => {
    const wo = workOrders.find(w => w.id === woId);
    if (!wo) return;

    setWorkOrders(prev =>
      prev.map(w =>
        w.id === woId
          ? { ...w, status: 'COMPLETED', completed_at_nullable: new Date().toISOString(), final_cost_nullable: w.estimated_cost_nullable }
          : w
      )
    );

    // Resolve associated alerts
    setAlerts(prev =>
      prev.map(a =>
        a.vehicle_id === wo.vehicle_id && a.status === 'OPEN'
          ? { ...a, status: 'RESOLVED', resolved_at_nullable: new Date().toISOString() }
          : a
      )
    );

    // Update vehicle back to ACTIVE
    setVehicles(prev =>
      prev.map(v =>
        v.id === wo.vehicle_id
          ? { ...v, status: 'ACTIVE', last_health_score: 98, updated_at: new Date().toISOString() }
          : v
      )
    );

    // Record cost entry
    const newCost: FleetCostEntry = {
      id: 'cost_' + Math.random().toString(36).substr(2, 9),
      fleet_id: activeFleetId,
      vehicle_id: wo.vehicle_id,
      type: 'REPAIR',
      amount: wo.estimated_cost_nullable || 30000,
      currency: 'CRC',
      provider_id_nullable: 'Taller MEET Autocuidado',
      description: `Cierre de orden: ${wo.title}`,
      receipt_uri_nullable: null,
      related_work_order_id_nullable: woId,
      created_at: new Date().toISOString()
    };
    setCostEntries(prev => [newCost, ...prev]);

    handleWriteAction('fleet_work_orders', 'UPDATE', { id: woId, status: 'COMPLETED' });
    addConsoleLog(`Orden de trabajo [${woId.substring(0, 6)}] resuelta. Vehículo retornado al servicio activo.`);
  };

  // --- LIVELINK MONITOR SIMULATOR ---
  useEffect(() => {
    if (liveVehId) {
      // Connect simulated LiveLink
      setLiveTelemetry(prev => ({ ...prev, connection: 'CONNECTED' }));
      const v = vehicles.find(veh => veh.id === liveVehId);
      const isHyundaiVerna = v?.internal_code === 'EV-ACCENT-VERNA-05';

      liveIntervalRef.current = setInterval(() => {
        setLiveTelemetry(prev => {
          const baseRpm = 800 + Math.floor(Math.sin(Date.now() / 1000) * 40);
          const baseTemp = 86 + Math.floor(Math.sin(Date.now() / 5000) * 3);
          return {
            connection: 'CONNECTED',
            rpm: baseRpm,
            voltage: isHyundaiVerna ? 13.0 + (Math.random() * 0.2) : 14.1 + (Math.random() * 0.2),
            coolant: baseTemp,
            speed: 0,
            load: 22 + Math.floor(Math.random() * 5),
            p0230Detected: isHyundaiVerna
          };
        });
      }, 500);

      addConsoleLog(`Sesión LiveLink iniciada con el vehículo ${v?.internal_code}.`);
    } else {
      if (liveIntervalRef.current) {
        clearInterval(liveIntervalRef.current);
      }
      setLiveTelemetry(prev => ({ ...prev, connection: 'DISCONNECTED', rpm: 0 }));
    }

    return () => {
      if (liveIntervalRef.current) clearInterval(liveIntervalRef.current);
    };
  }, [liveVehId]);

  // Live Canvas Waveform (Draw pulse waves)
  useEffect(() => {
    if (!liveVehId || !liveCanvasRef.current) return;
    const canvas = liveCanvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animFrameId: number;
    let offset = 0;

    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.strokeStyle = liveTelemetry.p0230Detected ? '#f87171' : '#06b6d4'; // Red if P0230, Cyan otherwise
      ctx.lineWidth = 2.5;

      ctx.beginPath();
      for (let x = 0; x < canvas.width; x++) {
        // Draw oscilloscope wave
        let y = canvas.height / 2;
        
        // Add pulse peak depending on simulated RPM
        const waveFreq = 0.05 + (liveTelemetry.rpm / 20000);
        const noise = Math.sin(x * waveFreq + offset) * 15;
        const subHarmonic = Math.cos(x * 0.01 + offset / 2) * 5;
        
        y += noise + subHarmonic;
        
        if (x === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();

      offset += 0.15;
      animFrameId = requestAnimationFrame(render);
    };

    render();

    return () => cancelAnimationFrame(animFrameId);
  }, [liveVehId, liveTelemetry.rpm, liveTelemetry.p0230Detected]);

  // --- CHARTS & STATS CALCULATION ---
  const activeVehCount = vehicles.filter(v => v.status === 'ACTIVE').length;
  const inMaintCount = vehicles.filter(v => v.status === 'IN_MAINTENANCE').length;
  const outOfServiceCount = vehicles.filter(v => v.status === 'OUT_OF_SERVICE').length;

  const costByVehicleChartData = useMemo(() => {
    const dataMap: Record<string, number> = {};
    costEntries.forEach(c => {
      const v = vehicles.find(veh => veh.id === c.vehicle_id);
      const name = v ? v.internal_code : 'Otro';
      dataMap[name] = (dataMap[name] || 0) + c.amount;
    });

    return Object.keys(dataMap).map(k => ({ name: k, costo: dataMap[k] }));
  }, [costEntries, vehicles]);

  const costByCategoryData = useMemo(() => {
    const dataMap: Record<string, number> = {};
    costEntries.forEach(c => {
      dataMap[c.type] = (dataMap[c.type] || 0) + c.amount;
    });
    return Object.keys(dataMap).map(k => ({ name: k, value: dataMap[k] }));
  }, [costEntries]);

  const totalCost = costEntries.reduce((a, b) => a + b.amount, 0);

  // Return component views
  return (
    <div className="space-y-6 text-slate-100">
      
      {/* HEADER SECTION WITH SAAS PLAN & RBAC SWITCH */}
      <div className="flex flex-col gap-4 border-b border-white/10 pb-5 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Building2 size={24} className="text-emerald-400" />
            <h1 className="text-2xl font-black tracking-tight text-white">{activeOrg?.name || 'Vanguard Fleet Console'}</h1>
            <span className="rounded-full bg-emerald-500/20 px-2.5 py-0.5 font-mono text-xs font-bold uppercase tracking-wider text-emerald-400">
              {activeOrg?.plan || 'PRO'}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-400">
            SaaS B2B Portal · {activeOrg?.legal_name_nullable || 'Corporativo'} · RUC: {activeOrg?.tax_id_nullable || 'N/D'}
          </p>
        </div>

        {/* CONTROLS AREA */}
        <div className="flex flex-wrap items-center gap-2">
          
          {/* OFFLINE STATUS TOGGLE */}
          <button
            type="button"
            onClick={toggleOnlineMode}
            className={`flex items-center gap-2 rounded-lg border px-3 py-1.5 text-xs font-black uppercase transition ${
              isOnline
                ? 'border-cyan-500/30 bg-cyan-950/20 text-cyan-400 hover:bg-cyan-950/40'
                : 'border-amber-500/30 bg-amber-950/20 text-amber-400 hover:bg-amber-950/40'
            }`}
          >
            {isOnline ? <Wifi size={14} /> : <WifiOff size={14} />}
            {isOnline ? 'Online' : 'Offline'}
            {syncQueue.length > 0 && (
              <span className="rounded-full bg-amber-500 px-2 py-0.5 text-[10px] font-black text-black animate-pulse">
                {syncQueue.length}
              </span>
            )}
          </button>

          {/* RBAC SELECTOR */}
          <div className="flex items-center gap-1 rounded-lg border border-white/10 bg-black/40 px-2 py-1.5">
            <UserCheck size={14} className="text-slate-400" />
            <select
              value={currentUserRole}
              onChange={(e) => setCurrentUserRole(e.target.value as FleetRole)}
              className="bg-transparent font-mono text-xs font-bold text-white outline-none"
            >
              <option value="ADMIN">Admin / Owner</option>
              <option value="MANAGER">Manager</option>
              <option value="MECHANIC">Mecánico</option>
              <option value="DRIVER">Conductor</option>
              <option value="AUDITOR">Auditor Legal</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </div>

          {/* FLEET SELECTOR */}
          <select
            value={activeFleetId}
            onChange={(e) => setActiveFleetId(e.target.value)}
            className="rounded-lg border border-white/10 bg-black/40 px-2 py-1.5 font-mono text-xs font-bold text-white outline-none"
          >
            {organizations.map(org => (
              <option key={org.id} value={org.id}>{org.name}</option>
            ))}
            <option value="add_new_org">+ Crear Flota...</option>
          </select>

          {activeFleetId === 'add_new_org' && (
            <button
              onClick={() => { setShowAddOrgModal(true); setActiveFleetId(organizations[0]?.id || ''); }}
              className="flex items-center gap-1 rounded-lg bg-emerald-500 px-3 py-1.5 text-xs font-black uppercase text-black transition hover:bg-emerald-400"
            >
              <Plus size={14} /> Crear
            </button>
          )}
        </div>
      </div>

      {/* SYNC CONSOLE INDICATOR FOR OFFLINE DEVELOPMENT */}
      {syncQueue.length > 0 && (
        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-3 text-xs text-amber-300">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Zap size={14} className="animate-bounce" />
              <span className="font-bold">Modo Desconectado:</span> Tienes {syncQueue.length} transacciones en la cola local de sincronización.
            </div>
            <button
              onClick={toggleOnlineMode}
              className="rounded bg-amber-500 px-2 py-0.5 text-[10px] font-black text-black hover:bg-amber-400"
            >
              Forzar Sync
            </button>
          </div>
        </div>
      )}

      {/* DASHBOARD TAB BUTTONS */}
      <div className="no-scrollbar flex overflow-x-auto border-b border-white/5 pb-2">
        <div className="flex gap-1">
          {!isDriver && (
            <TabButton active={activeTab === 'SUMMARY'} icon={<Activity size={15} />} label="Resumen" onClick={() => setActiveTab('SUMMARY')} />
          )}
          {!isDriver && (
            <TabButton active={activeTab === 'VEHICLES'} icon={<Car size={15} />} label="Vehículos" onClick={() => setActiveTab('VEHICLES')} />
          )}
          {!isDriver && (
            <TabButton active={activeTab === 'DRIVERS'} icon={<Users size={15} />} label="Conductores" onClick={() => setActiveTab('DRIVERS')} />
          )}
          <TabButton active={activeTab === 'DVIR'} icon={<FileText size={15} />} label="DVIR" onClick={() => setActiveTab('DVIR')} />
          {!isDriver && (
            <TabButton active={activeTab === 'ALERTS'} icon={<AlertTriangle size={15} />} label="Alertas" onClick={() => setActiveTab('ALERTS')} />
          )}
          {!isDriver && (
            <TabButton active={activeTab === 'WORK_ORDERS'} icon={<Wrench size={15} />} label="Work Orders" onClick={() => setActiveTab('WORK_ORDERS')} />
          )}
          {!isDriver && !isMechanic && (
            <TabButton active={activeTab === 'COSTS'} icon={<DollarSign size={15} />} label="Costos" onClick={() => setActiveTab('COSTS')} />
          )}
          <TabButton active={activeTab === 'LIVELINK'} icon={<Link2 size={15} />} label="LiveLink" onClick={() => setActiveTab('LIVELINK')} />
          {!isDriver && (
            <TabButton active={activeTab === 'CONFIG'} icon={<Settings size={15} />} label="Configuración" onClick={() => setActiveTab('CONFIG')} />
          )}
        </div>
      </div>

      {/* --- TAB VIEWPORTS --- */}

      {/* TAB 1: SUMMARY / DASHBOARD */}
      {activeTab === 'SUMMARY' && !isDriver && (
        <div className="space-y-6">
          
          {/* SUMMARY KPI CARDS */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <KpiCard title="Unidades Totales" value={vehicles.length} label="Vehículos de Flota" icon={<Car size={18} className="text-cyan-400" />} />
            <KpiCard title="Activas" value={activeVehCount} label="Operando normal" icon={<CheckCircle size={18} className="text-emerald-400" />} />
            <KpiCard title="Fuera de Servicio" value={outOfServiceCount} label="Acción Requerida" icon={<XCircle size={18} className="text-red-400" />} />
            <KpiCard title="Alertas Críticas" value={alerts.filter(a => a.status === 'OPEN').length} label="DTCs + DVIR fallidos" icon={<AlertTriangle size={18} className="text-amber-400" />} />
            <KpiCard title="Costos Mensuales" value={`${totalCost.toLocaleString()} CRC`} label="Mantenimiento + Repuestos" icon={<DollarSign size={18} className="text-purple-400" />} />
          </div>

          {/* DYNAMIC CHARTS GRID */}
          <div className="grid gap-6 md:grid-cols-2">
            
            {/* COST BY VEHICLE CHART */}
            <div className="glass rounded-xl border border-white/10 p-5">
              <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-cyan-300">Costo Operativo por Vehículo</h3>
              <p className="text-xs text-slate-400 mb-4">Distribución total acumulada en mantenimiento y repuestos</p>
              <div className="h-[240px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={costByVehicleChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#ffffff08" />
                    <XAxis dataKey="name" stroke="#64748b" fontSize={10} />
                    <YAxis stroke="#64748b" fontSize={10} />
                    <Tooltip contentStyle={{ backgroundColor: '#090d16', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px' }} />
                    <Bar dataKey="costo" fill="#10b981">
                      {costByVehicleChartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={index % 2 === 0 ? '#10b981' : '#3b82f6'} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* SYNC CONSOLE LOGS FOR OFFLINE TRACKING */}
            <div className="glass rounded-xl border border-white/10 p-5">
              <div className="flex items-center justify-between border-b border-white/5 pb-3">
                <div>
                  <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-emerald-300">Consola de Sincronización</h3>
                  <p className="text-xs text-slate-400">Handshake local y sincronizaciones Supabase</p>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`h-2.5 w-2.5 rounded-full ${isOnline ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400'}`}></span>
                  <span className="font-mono text-[10px] uppercase text-slate-400">{isOnline ? 'Online' : 'Offline'}</span>
                </div>
              </div>
              
              <div className="mt-3 h-[200px] overflow-y-auto rounded-lg bg-black/40 p-3 font-mono text-[11px] text-slate-300 space-y-1.5">
                {syncConsoleLogs.map((log, idx) => (
                  <div key={idx} className={`${log.includes('✅') ? 'text-emerald-400' : log.includes('⚠️') ? 'text-amber-400' : 'text-slate-300'}`}>
                    {log}
                  </div>
                ))}
              </div>
            </div>

          </div>

          {/* ACTIVE ALERTS TABLE PREVIEW */}
          <div className="glass rounded-xl border border-white/10 p-5">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-red-300">Alertas Operacionales Abiertas</h3>
                <p className="text-xs text-slate-400">Eventos de seguridad y códigos DTC críticos sin resolver</p>
              </div>
              <button onClick={() => setActiveTab('ALERTS')} className="text-xs font-bold text-cyan-400 hover:underline">Ver todas</button>
            </div>

            {alerts.filter(a => a.status === 'OPEN').length === 0 ? (
              <div className="flex flex-col items-center justify-center py-6 text-slate-500">
                <ShieldCheck size={32} className="text-emerald-500/40 mb-2" />
                <p className="text-sm font-medium">No hay alertas abiertas. Flota segura y operativa.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-white/5 text-slate-400 font-mono">
                      <th className="py-2">Vehículo</th>
                      <th className="py-2">Tipo de Alerta</th>
                      <th className="py-2">Detalle</th>
                      <th className="py-2">Severidad</th>
                      <th className="py-2">Creada</th>
                    </tr>
                  </thead>
                  <tbody>
                    {alerts.filter(a => a.status === 'OPEN').map(alert => {
                      const veh = vehicles.find(v => v.id === alert.vehicle_id);
                      return (
                        <tr key={alert.id} className="border-b border-white/5 hover:bg-white/[0.02]">
                          <td className="py-2.5 font-bold">{veh?.internal_code || 'N/D'}</td>
                          <td className="py-2.5">
                            <span className="rounded bg-red-500/10 px-2 py-0.5 font-mono text-[10px] font-black text-red-400">
                              {alert.alert_type}
                            </span>
                          </td>
                          <td className="py-2.5 text-slate-300">{alert.description}</td>
                          <td className="py-2.5">
                            <span className="font-bold text-red-500">{alert.severity}</span>
                          </td>
                          <td className="py-2.5 text-slate-400">{new Date(alert.created_at).toLocaleString()}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: VEHICLES */}
      {activeTab === 'VEHICLES' && !isDriver && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Vehículos corporativos</h2>
              <p className="text-xs text-slate-400">Administración de expedientes de unidades y salud</p>
            </div>
            {!isViewer && (
              <button
                onClick={() => setShowAddVehicleModal(true)}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-2 text-xs font-black uppercase text-black transition hover:bg-emerald-400"
              >
                <Plus size={16} /> Añadir Vehículo
              </button>
            )}
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {vehicles.map(veh => {
              const driver = drivers.find(d => d.assigned_vehicle_id_nullable === veh.id);
              const branch = branches.find(b => b.id === veh.branch_id_nullable);
              return (
                <div key={veh.id} className="glass rounded-xl border border-white/10 p-5 space-y-4 flex flex-col justify-between">
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className={`h-2.5 w-2.5 rounded-full ${
                          veh.status === 'ACTIVE' ? 'bg-emerald-400' :
                          veh.status === 'IN_MAINTENANCE' ? 'bg-amber-400' : 'bg-red-500'
                        }`}></span>
                        <h3 className="text-lg font-black text-white">{veh.internal_code}</h3>
                      </div>
                      <span className="font-mono text-xs text-slate-400">Placa: {veh.plate_nullable || 'N/D'}</span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs text-slate-300">
                      <div>
                        <span className="text-slate-500">Modelo:</span>
                        <p className="font-bold">Hyundai Accent Verna 2005</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Odómetro:</span>
                        <p className="font-mono font-bold">{veh.odometer_km.toLocaleString()} KM</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Conductor:</span>
                        <p className="font-bold">{driver ? driver.full_name : 'No asignado'}</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Sucursal:</span>
                        <p className="font-bold">{branch ? branch.name.split(' - ')[1] : 'Central'}</p>
                      </div>
                    </div>
                  </div>

                  <div className="border-t border-white/5 pt-3 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-slate-400">Salud General</span>
                      <span className={`font-mono text-xs font-bold ${
                        veh.last_health_score > 90 ? 'text-emerald-400' :
                        veh.last_health_score > 75 ? 'text-amber-400' : 'text-red-400'
                      }`}>{veh.last_health_score}%</span>
                    </div>
                    
                    {/* ACTION BUTTONS */}
                    <div className="grid grid-cols-2 gap-1.5 pt-1">
                      <button
                        onClick={() => {
                          setDvirVehId(veh.id);
                          setDvirOdo(veh.odometer_km);
                          setShowNewDvirModal(true);
                        }}
                        className="rounded bg-white/5 border border-white/10 px-2 py-1.5 text-[10px] font-black uppercase text-slate-200 hover:bg-white/10 text-center"
                      >
                        Hacer DVIR
                      </button>
                      <button
                        onClick={() => {
                          setLiveVehId(veh.id);
                          setActiveTab('LIVELINK');
                        }}
                        className="rounded bg-cyan-950/20 border border-cyan-500/30 px-2 py-1.5 text-[10px] font-black uppercase text-cyan-400 hover:bg-cyan-950/40 text-center"
                      >
                        LiveLink
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 3: DRIVERS */}
      {activeTab === 'DRIVERS' && !isDriver && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Conductores Autorizados</h2>
              <p className="text-xs text-slate-400">Control de licencias, eco-scoring e incidentes de conducción</p>
            </div>
            {!isViewer && (
              <button
                onClick={() => setShowAddDriverModal(true)}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-2 text-xs font-black uppercase text-black transition hover:bg-emerald-400"
              >
                <Plus size={16} /> Agregar Conductor
              </button>
            )}
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            {drivers.map(driver => {
              const assignedVeh = vehicles.find(v => v.id === driver.assigned_vehicle_id_nullable);
              const driverTrips = trips.filter(t => t.driver_id === driver.id);
              const avgEco = driverTrips.length > 0
                ? Math.round(driverTrips.reduce((a, b) => a + b.eco_score, 0) / driverTrips.length)
                : 95;
              const hasExpLicense = driver.license_expiration_nullable 
                ? new Date(driver.license_expiration_nullable).getTime() < Date.now() 
                : false;

              return (
                <div key={driver.id} className="glass rounded-xl border border-white/10 p-5 flex flex-col justify-between gap-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 font-bold">
                        {driver.full_name.split(' ').map(n => n[0]).join('')}
                      </div>
                      <div>
                        <h3 className="font-black text-white">{driver.full_name}</h3>
                        <p className="text-xs text-slate-400">Tel: {driver.phone}</p>
                      </div>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 font-mono text-[10px] font-black uppercase ${
                      driver.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-800 text-slate-400'
                    }`}>{driver.status}</span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs border-t border-b border-white/5 py-3">
                    <div>
                      <span className="text-slate-500">Unidad Asignada:</span>
                      <p className="font-bold text-slate-200">{assignedVeh ? assignedVeh.internal_code : 'Ninguna'}</p>
                    </div>
                    <div>
                      <span className="text-slate-500">Licencia:</span>
                      <p className={`font-mono font-bold ${hasExpLicense ? 'text-red-400' : 'text-slate-200'}`}>
                        {driver.license_number_nullable || 'N/D'} 
                        {hasExpLicense && ' ⚠️ VENCIDA'}
                      </p>
                    </div>
                    <div>
                      <span className="text-slate-500">Eco-Score Promedio:</span>
                      <p className={`font-bold ${avgEco > 85 ? 'text-emerald-400' : 'text-amber-400'}`}>{avgEco} pts</p>
                    </div>
                    <div>
                      <span className="text-slate-500">Viajes Registrados:</span>
                      <p className="font-bold text-slate-200">{driverTrips.length} viajes</p>
                    </div>
                  </div>

                  <div className="flex justify-end gap-2 text-[10px] font-black uppercase">
                    <button className="text-slate-400 hover:text-white px-2 py-1">Ver Viajes</button>
                    <button className="text-cyan-400 hover:underline px-2 py-1">Historial DVIR</button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 4: DVIR INSPECTIONS */}
      {activeTab === 'DVIR' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Inspecciones de Seguridad (DVIR)</h2>
              <p className="text-xs text-slate-400">Reportes de inspección de vehículos pre y post viaje</p>
            </div>
            {!isViewer && !isAuditor && (
              <button
                onClick={() => { resetDvirForm(); setShowNewDvirModal(true); }}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-2 text-xs font-black uppercase text-black transition hover:bg-emerald-400"
              >
                <Plus size={16} /> Nueva Inspección
              </button>
            )}
          </div>

          {/* INSPECTIONS HISTORY LIST */}
          <div className="space-y-2">
            {dvirInspections.length === 0 ? (
              <div className="glass rounded-xl border border-white/10 p-8 text-center text-slate-500 flex flex-col items-center justify-center">
                <FileText size={40} className="mb-2 text-slate-600" />
                <p className="text-sm font-medium">No se han registrado inspecciones DVIR todavía.</p>
                <p className="text-xs text-slate-500 mt-1">Haga clic en "Nueva Inspección" para registrar un reporte.</p>
              </div>
            ) : (
              dvirInspections.map(insp => {
                const veh = vehicles.find(v => v.id === insp.vehicle_id);
                const driver = drivers.find(d => d.id === insp.driver_id);
                const sig = dvirSignatures.find(s => s.inspection_id === insp.id);
                const obd = obdSnapshots.find(o => o.inspection_id === insp.id);

                return (
                  <div key={insp.id} className="glass rounded-xl border border-white/10 p-5 space-y-3">
                    <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                      <div className="flex items-center gap-2">
                        <span className={`rounded px-2.5 py-0.5 font-mono text-[10px] font-black uppercase ${
                          insp.overall_result === 'PASS' ? 'bg-emerald-500/10 text-emerald-400' :
                          insp.overall_result === 'PASS_WITH_OBSERVATIONS' ? 'bg-amber-500/10 text-amber-400' : 'bg-red-500/10 text-red-400'
                        }`}>
                          {insp.overall_result}
                        </span>
                        <h3 className="font-bold text-white">{veh?.internal_code || 'Vehículo'}</h3>
                        <span className="text-xs text-slate-500">· {insp.inspection_type}</span>
                      </div>
                      <span className="font-mono text-xs text-slate-400">{new Date(insp.created_at).toLocaleString()}</span>
                    </div>

                    <div className="grid gap-2 text-xs text-slate-300 md:grid-cols-4">
                      <div>
                        <span className="text-slate-500">Inspector:</span>
                        <p className="font-bold">{driver?.full_name || 'Conductor'}</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Kilometraje:</span>
                        <p className="font-mono font-bold">{insp.odometer_km.toLocaleString()} KM</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Hash de Integridad:</span>
                        <p className="font-mono text-[10px] text-cyan-400 break-all">{sig?.hash_sha256 || 'N/A'}</p>
                      </div>
                      <div>
                        <span className="text-slate-500">Reporte Certificado:</span>
                        <div className="flex items-center gap-1 text-[10px] text-slate-400 font-bold bg-black/40 px-2 py-0.5 rounded w-max mt-0.5">
                          <Lock size={10} className="text-emerald-400" />
                          <span>{insp.report_id_nullable}</span>
                        </div>
                      </div>
                    </div>

                    {/* OBD snapshot result link */}
                    <div className="border-t border-white/5 pt-2 flex items-center justify-between text-[11px]">
                      <div>
                        {obd ? (
                          <div className="flex items-center gap-1.5 text-emerald-400 font-medium">
                            <Zap size={12} />
                            <span>Evidencia OBD unida: OBD-II conectado. Voltaje: {obd.voltage}V. DTCs: {obd.dtcs_active.length > 0 ? obd.dtcs_active.join(', ') : 'Ninguno'}.</span>
                          </div>
                        ) : (
                          <span className="text-slate-500 font-medium">⚠️ DVIR sin evidencia OBD real.</span>
                        )}
                      </div>

                      {/* Display redacted QR mockup details */}
                      <button
                        onClick={() => setSelectedDvirForDetail(selectedDvirForDetail === insp.id ? null : insp.id)}
                        className="text-cyan-400 font-bold uppercase hover:underline"
                      >
                        {selectedDvirForDetail === insp.id ? 'Ocultar Código QR' : 'Inspeccionar QR'}
                      </button>
                    </div>

                    {/* QR Code Reveal Section */}
                    {selectedDvirForDetail === insp.id && (
                      <div className="mt-4 p-4 rounded-lg bg-black/50 border border-cyan-500/20 flex flex-col md:flex-row gap-4 items-center animate-slide-up">
                        {/* Mockup QR Canvas */}
                        <div className="h-28 w-28 bg-white p-2 rounded flex flex-col items-center justify-center text-black">
                          {/* Emulated QR matrix */}
                          <div className="grid grid-cols-6 gap-0.5 h-full w-full opacity-90">
                            {Array.from({ length: 36 }).map((_, i) => (
                              <div key={i} className={`rounded-sm ${
                                i % 3 === 0 || i < 6 || i > 30 || i % 7 === 1 ? 'bg-black' : 'bg-transparent'
                              }`}></div>
                            ))}
                          </div>
                        </div>
                        <div className="flex-1 space-y-2 text-xs">
                          <h4 className="font-mono text-cyan-300 font-bold uppercase tracking-wider">Payload Oficial QR (Minimizado por Privacidad)</h4>
                          <p className="text-slate-400 text-[11px] leading-relaxed">
                            Cumpliendo con la regla 4 de seguridad, el QR no expone VIN, placas ni números de teléfono personales para evitar la recolección de datos no autorizada. Contiene únicamente:
                          </p>
                          <div className="grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-[10px] text-slate-300 bg-white/5 p-2 rounded border border-white/5">
                            <div><span className="text-slate-500">report_id:</span> {insp.report_id_nullable}</div>
                            <div><span className="text-slate-500">integrity_hash:</span> {sig?.hash_sha256.substring(0, 16)}...</div>
                            <div><span className="text-slate-500">vehicle_id:</span> {insp.vehicle_id.substring(0, 12)}...</div>
                            <div><span className="text-slate-500">generated_at:</span> {new Date(insp.created_at).getTime()}</div>
                            <div><span className="text-slate-500">report_type:</span> {insp.inspection_type}</div>
                            <div><span className="text-slate-500">verifier_url:</span> meet.com/verify</div>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* TAB 5: ALERTS */}
      {activeTab === 'ALERTS' && !isDriver && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Alertas Operativas</h2>
              <p className="text-xs text-slate-400">Control de fallos mecánicos, eléctricos y vencimiento de licencias</p>
            </div>
          </div>

          <div className="space-y-2">
            {alerts.length === 0 ? (
              <div className="glass rounded-xl border border-white/10 p-8 text-center text-slate-500">
                No hay alertas activas en la flota.
              </div>
            ) : (
              alerts.map(alert => {
                const veh = vehicles.find(v => v.id === alert.vehicle_id);
                return (
                  <div key={alert.id} className={`glass rounded-xl border p-5 flex flex-col md:flex-row justify-between md:items-center gap-4 ${
                    alert.status === 'RESOLVED' ? 'border-white/5 bg-white/[0.01]' :
                    alert.severity === 'CRITICAL' ? 'border-red-500/30 bg-red-500/[0.02]' : 'border-amber-500/20 bg-amber-500/[0.01]'
                  }`}>
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <span className={`h-2 w-2 rounded-full ${alert.status === 'RESOLVED' ? 'bg-slate-500' : 'bg-red-500'}`}></span>
                        <h3 className="font-bold text-white">{alert.title}</h3>
                        <span className="text-xs text-slate-500">({veh?.internal_code})</span>
                      </div>
                      <p className="text-xs text-slate-300">{alert.description}</p>
                      <div className="flex items-center gap-2 font-mono text-[10px] text-slate-400">
                        <span>Origen: {alert.source}</span>
                        <span>·</span>
                        <span>Creada: {new Date(alert.created_at).toLocaleString()}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {alert.status === 'OPEN' && !isViewer && (
                        <button
                          onClick={() => {
                            // Automatically resolve when assigning or creating repair
                            setAlerts(prev =>
                              prev.map(a => a.id === alert.id ? { ...a, status: 'RESOLVED', resolved_at_nullable: new Date().toISOString() } : a)
                            );
                            addConsoleLog(`Alerta [${alert.id.substring(0, 6)}] marcada como resuelta.`);
                          }}
                          className="rounded bg-emerald-500/10 border border-emerald-500/30 px-3 py-1.5 text-xs font-black uppercase text-emerald-400 hover:bg-emerald-500/20"
                        >
                          Resolver / Descartar
                        </button>
                      )}
                      {alert.status === 'RESOLVED' && (
                        <div className="flex items-center gap-1 text-slate-400 font-bold text-xs bg-white/5 px-3 py-1.5 rounded">
                          <Check size={14} className="text-emerald-400" />
                          <span>Resuelta</span>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* TAB 6: WORK ORDERS */}
      {activeTab === 'WORK_ORDERS' && !isDriver && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Órdenes de Trabajo</h2>
              <p className="text-xs text-slate-400">Control de mantenimiento correctivo y preventivo de unidades</p>
            </div>
          </div>

          <div className="space-y-2">
            {workOrders.length === 0 ? (
              <div className="glass rounded-xl border border-white/10 p-8 text-center text-slate-500">
                No hay órdenes de trabajo activas.
              </div>
            ) : (
              workOrders.map(wo => {
                const veh = vehicles.find(v => v.id === wo.vehicle_id);
                return (
                  <div key={wo.id} className="glass rounded-xl border border-white/10 p-5 space-y-3">
                    <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                      <div className="flex items-center gap-2">
                        <span className={`rounded px-2 py-0.5 font-mono text-[9px] font-black uppercase ${
                          wo.status === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'
                        }`}>
                          {wo.status}
                        </span>
                        <h3 className="font-bold text-white">{wo.title}</h3>
                        <span className="text-xs text-slate-500">· {veh?.internal_code}</span>
                      </div>
                      <span className="text-xs font-bold text-cyan-400">Origen: {wo.source}</span>
                    </div>

                    <p className="text-xs text-slate-300">{wo.description}</p>

                    <div className="flex flex-col gap-2 md:flex-row md:items-center justify-between border-t border-white/5 pt-3">
                      <div className="grid grid-cols-2 gap-4 text-[11px] text-slate-400 font-mono">
                        <div>Costo Estimado: <span className="font-bold text-white">{wo.estimated_cost_nullable?.toLocaleString() || 0} CRC</span></div>
                        {wo.final_cost_nullable && <div>Costo Real: <span className="font-bold text-emerald-400">{wo.final_cost_nullable?.toLocaleString()} CRC</span></div>}
                      </div>

                      {wo.status === 'OPEN' && (currentUserRole === 'ADMIN' || currentUserRole === 'MECHANIC') && (
                        <button
                          onClick={() => handleResolveWorkOrder(wo.id)}
                          className="flex items-center gap-1.5 rounded bg-emerald-500 px-3 py-1.5 text-xs font-black uppercase text-black hover:bg-emerald-400"
                        >
                          <Wrench size={12} /> CERRAR Y REPARAR
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* TAB 7: COSTS */}
      {activeTab === 'COSTS' && !isDriver && !isMechanic && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">Costos Operacionales</h2>
              <p className="text-xs text-slate-400">Registros financieros de combustibles, seguros y reparaciones</p>
            </div>
            {!isViewer && (
              <button
                onClick={() => setShowNewCostModal(true)}
                className="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-2 text-xs font-black uppercase text-black transition hover:bg-emerald-400"
              >
                <Plus size={16} /> Registrar Gasto
              </button>
            )}
          </div>

          <div className="grid gap-6 md:grid-cols-3">
            
            {/* PIE CHART COST BY CATEGORY */}
            <div className="glass rounded-xl border border-white/10 p-5 md:col-span-1">
              <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-cyan-300 mb-4">Costos por Categoría</h3>
              <div className="h-[200px] flex items-center justify-center">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={costByCategoryData}
                      cx="50%"
                      cy="50%"
                      innerRadius={40}
                      outerRadius={65}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {costByCategoryData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6'][index % 5]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#090d16', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              
              {/* Pie Legends */}
              <div className="grid grid-cols-2 gap-2 text-[10px] text-slate-400 font-mono mt-2">
                {costByCategoryData.map((c, idx) => (
                  <div key={c.name} className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ backgroundColor: ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6'][idx % 5] }}></span>
                    <span>{c.name}: {c.value.toLocaleString()} CRC</span>
                  </div>
                ))}
              </div>
            </div>

            {/* EXPENSES LOG TABLE */}
            <div className="glass rounded-xl border border-white/10 p-5 md:col-span-2 space-y-3">
              <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-white">Historial de Transacciones</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-white/5 text-slate-400 font-mono">
                      <th className="py-2">Vehículo</th>
                      <th className="py-2">Categoría</th>
                      <th className="py-2">Monto</th>
                      <th className="py-2">Detalles</th>
                      <th className="py-2">Proveedor</th>
                      <th className="py-2">Fecha</th>
                    </tr>
                  </thead>
                  <tbody>
                    {costEntries.map(c => {
                      const veh = vehicles.find(v => v.id === c.vehicle_id);
                      return (
                        <tr key={c.id} className="border-b border-white/5 hover:bg-white/[0.02]">
                          <td className="py-2 font-bold">{veh?.internal_code}</td>
                          <td className="py-2">
                            <span className="rounded bg-white/5 border border-white/10 px-2 py-0.5 text-[9px] font-mono">
                              {c.type}
                            </span>
                          </td>
                          <td className="py-2 font-mono font-bold text-white">{c.amount.toLocaleString()} {c.currency}</td>
                          <td className="py-2 text-slate-300">{c.description}</td>
                          <td className="py-2 text-slate-400">{c.provider_id_nullable || 'N/A'}</td>
                          <td className="py-2 text-slate-400">{new Date(c.created_at).toLocaleDateString()}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

          </div>
        </div>
      )}

      {/* TAB 8: LIVELINK TELEMETRY CONTAINER */}
      {activeTab === 'LIVELINK' && (
        <div className="space-y-6">
          <div className="flex flex-col gap-2 md:flex-row md:items-center justify-between">
            <div>
              <h2 className="text-xl font-black text-white">LiveLink de Flota B2B</h2>
              <p className="text-xs text-slate-400">Acceso a telemetría en tiempo real desde el APK unificado</p>
            </div>
            
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400">Seleccionar Vehículo:</span>
              <select
                value={liveVehId}
                onChange={(e) => setLiveVehId(e.target.value)}
                className="rounded-lg border border-white/10 bg-black/40 px-3 py-1.5 font-mono text-xs font-bold text-white outline-none"
              >
                <option value="">-- Seleccionar --</option>
                {vehicles.map(v => (
                  <option key={v.id} value={v.id}>{v.internal_code}</option>
                ))}
              </select>
            </div>
          </div>

          {liveVehId ? (
            <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
              
              {/* OSCILLOSCOPE SIGNAL REPLAY & DIGITAL TWIN DATAS */}
              <div className="glass rounded-xl border border-white/10 p-5 space-y-4">
                <div className="flex items-center justify-between border-b border-white/5 pb-3">
                  <div>
                    <h3 className="font-bold text-white">Osciloscopio / Telemetría en Vivo</h3>
                    <p className="text-xs text-slate-400">Canal digital primario de sensores a 1000ms de frecuencia</p>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="animate-pulse h-2 w-2 rounded-full bg-cyan-400"></span>
                    <span className="font-mono text-[9px] text-cyan-400 uppercase tracking-wider">Flujo UDP Activo</span>
                  </div>
                </div>

                {/* Simulated Canvas Oscilloscope Replay */}
                <div className="relative rounded-lg bg-black/80 border border-slate-800 p-2 overflow-hidden h-[180px]">
                  <canvas
                    ref={liveCanvasRef}
                    width={500}
                    height={160}
                    className="w-full h-full"
                  />
                  
                  {liveTelemetry.p0230Detected && (
                    <div className="absolute inset-0 bg-red-950/20 flex items-center justify-center backdrop-blur-[1px]">
                      <div className="rounded bg-red-950 border border-red-500 px-3 py-1.5 flex items-center gap-2 text-xs font-black text-red-400 uppercase tracking-wider animate-pulse">
                        <AlertTriangle size={14} /> P0230: Falla en Circuito Primario de Bomba de Gasolina
                      </div>
                    </div>
                  )}
                </div>

                {/* LIVE METRICS TILES */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                  <div className="bg-white/[0.02] border border-white/5 rounded-lg p-3">
                    <span className="font-mono text-[10px] text-slate-500 uppercase">RPM</span>
                    <p className="text-lg font-black font-mono text-cyan-300 mt-1">{liveTelemetry.rpm}</p>
                  </div>
                  <div className="bg-white/[0.02] border border-white/5 rounded-lg p-3">
                    <span className="font-mono text-[10px] text-slate-500 uppercase">Voltaje Adaptador</span>
                    <p className={`text-lg font-black font-mono mt-1 ${liveTelemetry.p0230Detected ? 'text-red-400' : 'text-emerald-400'}`}>
                      {liveTelemetry.voltage.toFixed(2)}V
                    </p>
                  </div>
                  <div className="bg-white/[0.02] border border-white/5 rounded-lg p-3">
                    <span className="font-mono text-[10px] text-slate-500 uppercase">Temp Refrigerante</span>
                    <p className="text-lg font-black font-mono text-slate-200 mt-1">{liveTelemetry.coolant}°C</p>
                  </div>
                  <div className="bg-white/[0.02] border border-white/5 rounded-lg p-3">
                    <span className="font-mono text-[10px] text-slate-500 uppercase">Carga Motor</span>
                    <p className="text-lg font-black font-mono text-slate-200 mt-1">{liveTelemetry.load}%</p>
                  </div>
                </div>
              </div>

              {/* ACTION COMMAND CENTER */}
              <div className="glass rounded-xl border border-white/10 p-5 space-y-4">
                <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400">Acciones Remotas LiveLink</h3>
                
                <div className="text-xs text-slate-300 leading-relaxed">
                  Esta sesión LiveLink está vinculada de forma exclusiva a la flota empresarial. Los conductores no pueden compartir o transmitir telemetría sin autorización del despachador central.
                </div>

                <div className="space-y-2 pt-2">
                  <button
                    onClick={() => {
                      addConsoleLog(`Solicitado escaneo OBD2 en caliente para vehículo ${liveVehId}.`);
                      alert('Comando de escaneo bidireccional enviado al APK unificado.');
                    }}
                    className="w-full flex items-center justify-center gap-2 rounded bg-cyan-900/40 border border-cyan-500/30 py-2 text-xs font-black uppercase text-cyan-200 hover:bg-cyan-900/60"
                  >
                    <Activity size={14} /> Solicitar Scan Completo
                  </button>
                  <button
                    onClick={() => {
                      addConsoleLog(`Comando de reinicio de PCM enviado para vehículo ${liveVehId}.`);
                      alert('Reinicio OBD de parámetros en caliente completado.');
                    }}
                    className="w-full flex items-center justify-center gap-2 rounded bg-slate-800 border border-white/10 py-2 text-xs font-black uppercase text-slate-200 hover:bg-slate-700"
                  >
                    <RefreshCw size={14} /> Forzar Borrado DTCs
                  </button>
                </div>
              </div>

            </div>
          ) : (
            <div className="glass rounded-xl border border-white/10 p-10 text-center text-slate-500">
              Por favor seleccione un vehículo arriba para inicializar el LiveLink y la telemetría unificada.
            </div>
          )}
        </div>
      )}

      {/* TAB 9: CONFIGURATION */}
      {activeTab === 'CONFIG' && !isDriver && (
        <div className="space-y-6">
          <div className="glass rounded-xl border border-white/10 p-5 space-y-4">
            <h3 className="text-lg font-black text-white">Administración de Sucursales</h3>
            <div className="grid gap-3">
              {branches.map(b => (
                <div key={b.id} className="flex justify-between items-center bg-white/[0.02] border border-white/5 p-3 rounded-lg text-xs">
                  <div>
                    <p className="font-bold text-white">{b.name}</p>
                    <span className="text-slate-500">Ubicación: {b.location}</span>
                  </div>
                  <span className="text-slate-400">Encargado: {b.manager_user_id_nullable}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="glass rounded-xl border border-white/10 p-5 space-y-4">
            <h3 className="text-lg font-black text-white">Niveles de Suscripción Corporativa</h3>
            <div className="grid gap-4 sm:grid-cols-4">
              <PricingTierCard name="FREE_FLEET" limit="Hasta 1 Vehículo" price="Gratis" active={activeOrg?.plan === 'FREE_FLEET'} description="DVIR básico y almacenamiento local de historiales." />
              <PricingTierCard name="FLEET_STARTER" limit="Hasta 5 Vehículos" price="$49 / mes" active={activeOrg?.plan === 'FLEET_STARTER'} description="Reportes PDF certificados, alertas operacionales básicas y garage." />
              <PricingTierCard name="FLEET_PRO" limit="Hasta 25 Vehículos" price="$199 / mes" active={activeOrg?.plan === 'FLEET_PRO'} description="LiveLink en vivo, salud predictiva, roles RBAC y exportación CSV." />
              <PricingTierCard name="FLEET_ENTERPRISE" limit="Vehículos Ilimitados" price="Custom" active={activeOrg?.plan === 'FLEET_ENTERPRISE'} description="Multi-sucursal, API, auditorías y soporte prioritario 24/7." />
            </div>
          </div>
        </div>
      )}

      {/* --- FORM MODALS --- */}

      {/* MODAL: CREATE ORGANIZATION */}
      {showAddOrgModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in">
          <form onSubmit={handleCreateOrg} className="glass rounded-2xl border border-emerald-500/30 p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <h3 className="text-lg font-black text-white">Crear Nueva Flota Corporativa</h3>
              <button type="button" onClick={() => setShowAddOrgModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Nombre de la Organización</label>
                <input
                  type="text"
                  required
                  placeholder="ej. Transur S.A."
                  value={newOrgName}
                  onChange={e => setNewOrgName(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                />
              </div>
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Nombre Jurídico</label>
                <input
                  type="text"
                  placeholder="ej. Transportes del Sur S.A."
                  value={newOrgLegal}
                  onChange={e => setNewOrgLegal(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                />
              </div>
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Cédula Jurídica / Tax ID</label>
                <input
                  type="text"
                  placeholder="ej. 3-101-123456"
                  value={newOrgTaxId}
                  onChange={e => setNewOrgTaxId(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Teléfono</label>
                  <input
                    type="text"
                    required
                    placeholder="+506 8888-8888"
                    value={newOrgPhone}
                    onChange={e => setNewOrgPhone(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Email</label>
                  <input
                    type="email"
                    required
                    placeholder="correo@flota.com"
                    value={newOrgEmail}
                    onChange={e => setNewOrgEmail(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
              </div>
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Plan Corporativo inicial</label>
                <select
                  value={newOrgPlan}
                  onChange={e => setNewOrgPlan(e.target.value as FleetPlan)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                >
                  <option value="FREE_FLEET">FREE_FLEET (Hasta 1 vehículo)</option>
                  <option value="FLEET_STARTER">FLEET_STARTER (Hasta 5 vehículos)</option>
                  <option value="FLEET_PRO">FLEET_PRO (Hasta 25 vehículos)</option>
                  <option value="FLEET_ENTERPRISE">FLEET_ENTERPRISE (Ilimitados)</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded bg-emerald-500 py-2.5 text-xs font-black uppercase text-black hover:bg-emerald-400 transition"
            >
              Registrar Flota
            </button>
          </form>
        </div>
      )}

      {/* MODAL: ADD VEHICLE */}
      {showAddVehicleModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in">
          <form onSubmit={handleCreateVehicle} className="glass rounded-2xl border border-emerald-500/30 p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <h3 className="text-lg font-black text-white">Añadir Vehículo a la Flota</h3>
              <button type="button" onClick={() => setShowAddVehicleModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>
            
            <div className="space-y-3 text-xs">
              
              {/* Shortcut auto-fill Hyundai */}
              <button
                type="button"
                onClick={autoFillHyundaiAccentVerna}
                className="w-full border border-dashed border-emerald-500/40 bg-emerald-500/5 rounded py-2 text-[10px] font-black uppercase text-emerald-400 hover:bg-emerald-500/10 text-center"
              >
                Auto-completar Hyundai Accent Verna 2005
              </button>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Código Interno</label>
                  <input
                    type="text"
                    required
                    placeholder="ej. FLEET-ACCENT-01"
                    value={newVehCode}
                    onChange={e => setNewVehCode(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Placa</label>
                  <input
                    type="text"
                    placeholder="ej. ABC-123"
                    value={newVehPlate}
                    onChange={e => setNewVehPlate(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Marca</label>
                  <input
                    type="text"
                    required
                    placeholder="ej. Hyundai"
                    value={newVehBrand}
                    onChange={e => setNewVehBrand(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Modelo</label>
                  <input
                    type="text"
                    required
                    placeholder="ej. Accent Verna"
                    value={newVehModel}
                    onChange={e => setNewVehModel(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Año</label>
                  <input
                    type="text"
                    required
                    placeholder="ej. 2005"
                    value={newVehYear}
                    onChange={e => setNewVehYear(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Odómetro Inicial (KM)</label>
                <input
                  type="number"
                  required
                  placeholder="ej. 154000"
                  value={newVehOdo}
                  onChange={e => setNewVehOdo(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Sucursal</label>
                  <select
                    value={newVehBranch}
                    onChange={e => setNewVehBranch(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  >
                    <option value="">Ninguna (Central)</option>
                    {branches.map(b => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Conductor Asignado</label>
                  <select
                    value={newVehDriver}
                    onChange={e => setNewVehDriver(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  >
                    <option value="">No asignar</option>
                    {drivers.map(d => (
                      <option key={d.id} value={d.id}>{d.full_name}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded bg-emerald-500 py-2.5 text-xs font-black uppercase text-black hover:bg-emerald-400 transition"
            >
              Agregar Unidad
            </button>
          </form>
        </div>
      )}

      {/* MODAL: ADD DRIVER */}
      {showAddDriverModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in">
          <form onSubmit={handleCreateDriver} className="glass rounded-2xl border border-emerald-500/30 p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <h3 className="text-lg font-black text-white">Registrar Conductor</h3>
              <button type="button" onClick={() => setShowAddDriverModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Nombre Completo</label>
                <input
                  type="text"
                  required
                  placeholder="ej. Carlos Mendoza"
                  value={newDriverName}
                  onChange={e => setNewDriverName(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                />
              </div>
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Teléfono de Contacto</label>
                <input
                  type="text"
                  required
                  placeholder="ej. +506 8765-4321"
                  value={newDriverPhone}
                  onChange={e => setNewDriverPhone(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Número de Licencia</label>
                  <input
                    type="text"
                    placeholder="ej. LIC-SJ-99276"
                    value={newDriverLicense}
                    onChange={e => setNewDriverLicense(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Vencimiento Licencia</label>
                  <input
                    type="date"
                    value={newDriverExpiry}
                    onChange={e => setNewDriverExpiry(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white focus:border-emerald-500 outline-none font-mono"
                  />
                </div>
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded bg-emerald-500 py-2.5 text-xs font-black uppercase text-black hover:bg-emerald-400 transition"
            >
              Registrar Conductor
            </button>
          </form>
        </div>
      )}

      {/* MODAL: DVIR WIZARD (INTERACTIVE PRE-TRIP CREATION WITH OBD CONTROLS) */}
      {showNewDvirModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-md overflow-y-auto animate-fade-in animate-slide-up">
          <form onSubmit={handleSubmitDvir} className="glass rounded-2xl border border-emerald-500/40 p-6 max-w-2xl w-full space-y-4 my-8">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <div className="flex items-center gap-2">
                <FileText className="text-emerald-400" />
                <h3 className="text-lg font-black text-white">Inspección Operativa Certificada (DVIR)</h3>
              </div>
              <button type="button" onClick={() => setShowNewDvirModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 text-xs">
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Vehículo a Inspeccionar</label>
                <select
                  required
                  value={dvirVehId}
                  onChange={e => setDvirVehId(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/60 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                >
                  <option value="">-- Seleccionar --</option>
                  {vehicles.map(v => (
                    <option key={v.id} value={v.id}>{v.internal_code} ({v.plate_nullable})</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Tipo de Inspección</label>
                  <select
                    value={dvirType}
                    onChange={e => setDvirType(e.target.value as InspectionType)}
                    className="w-full rounded border border-white/10 bg-black/60 px-3 py-2 text-white focus:border-emerald-500 outline-none"
                  >
                    <option value="PRE_TRIP">Pre-Trip (Salida)</option>
                    <option value="POST_TRIP">Post-Trip (Llegada)</option>
                    <option value="DAILY">Diario General</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Kilometraje Actual</label>
                  <input
                    type="number"
                    required
                    value={dvirOdo}
                    onChange={e => setDvirOdo(parseInt(e.target.value) || 0)}
                    className="w-full rounded border border-white/10 bg-black/60 px-3 py-2 text-white focus:border-emerald-500 outline-none font-mono"
                  />
                </div>
              </div>
            </div>

            {/* OBD DONGLE CONNECTOR WIDGET INTEGRATED */}
            <div className="rounded-xl border border-cyan-500/20 bg-cyan-950/10 p-4 space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-cyan-500/10 pb-2">
                <div className="flex items-center gap-2">
                  <Zap size={16} className={obdConnected ? 'text-cyan-400 animate-pulse' : 'text-slate-500'} />
                  <span className="text-xs font-bold text-white uppercase tracking-wider">Evidencia OBD-II en Caliente</span>
                </div>
                
                {/* Simulators */}
                <div className="flex flex-wrap gap-2 text-[10px]">
                  <label className="flex items-center gap-1 cursor-pointer text-slate-300 font-mono">
                    <input
                      type="checkbox"
                      checked={obdConnected}
                      onChange={e => setObdConnected(e.target.checked)}
                      className="rounded accent-cyan-400"
                    />
                    <span>Conectar Adaptador OBD</span>
                  </label>
                  
                  {obdConnected && (
                    <label className="flex items-center gap-1 cursor-pointer text-red-400 font-mono">
                      <input
                        type="checkbox"
                        checked={simulatedP0230Active}
                        onChange={e => setSimulatedP0230Active(e.target.checked)}
                        className="rounded accent-red-400"
                      />
                      <span>Simular DTC P0230 (Bomba Combustible)</span>
                    </label>
                  )}
                </div>
              </div>

              <div className="flex flex-col sm:flex-row items-center gap-3">
                <button
                  type="button"
                  disabled={isCapturingObd}
                  onClick={handleObdCaptureSimulation}
                  className="w-full sm:w-auto rounded bg-cyan-500 px-4 py-2 text-xs font-black uppercase text-black hover:bg-cyan-400 transition"
                >
                  {isCapturingObd ? 'Capturando...' : 'Intentar Capturar OBD'}
                </button>

                <div className="text-xs">
                  {obdCapturedSnapshot ? (
                    <div className="text-emerald-400 font-medium font-mono">
                      ✓ Snapshot OBD unida. Código activo: {obdCapturedSnapshot.dtcs_active?.includes('P0230') ? 'P0230 (CRÍTICO)' : 'Ninguno'}. Voltaje: {obdCapturedSnapshot.voltage}V.
                    </div>
                  ) : (
                    <div className="text-slate-400 font-mono">
                      Sin datos OBD asociados. Capture parámetros antes de guardar.
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* CHECKLIST SECTIONS */}
            <div className="space-y-3 text-xs">
              <h4 className="font-mono text-slate-400 font-bold uppercase tracking-wider">Chequeo de Puntos Críticos</h4>
              
              <div className="grid gap-3 sm:grid-cols-2">
                <ChecklistFieldItem
                  itemKey="frenos"
                  label="Frenos y mordazas principales (CRÍTICO)"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
                <ChecklistFieldItem
                  itemKey="luces_noche"
                  label="Faros principales de conducción nocturna (CRÍTICO)"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
                <ChecklistFieldItem
                  itemKey="llantas_desgaste"
                  label="Llantas: Desgaste, presión e integridad"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
                <ChecklistFieldItem
                  itemKey="fuga_combustible"
                  label="Fugas de combustible o fluidos inflamables (CRÍTICO)"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
                <ChecklistFieldItem
                  itemKey="voltaje_alternador"
                  label="Voltaje del Alternador / Voltímetro"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
                <ChecklistFieldItem
                  itemKey="papeles_al_dia"
                  label="Papeles vehiculares y extintor al día"
                  dvirStates={dvirChecklistStates}
                  setDvirStates={setDvirChecklistStates}
                />
              </div>
            </div>

            {/* ERROR BANNER IF OBD SNAPSHOT HAS CRITICAL P0230 OR CHECKLIST FAILED */}
            {((obdCapturedSnapshot?.dtcs_active?.includes('P0230')) || 
              Object.keys(dvirChecklistStates).some(k => dvirChecklistStates[k].status === 'FAILED')) && (
              <div className="rounded-lg border border-red-500/20 bg-red-950/20 p-3 text-xs text-red-300 space-y-1">
                <div className="flex items-center gap-2 font-bold">
                  <AlertTriangle size={14} className="text-red-400" />
                  <span>ALERTA DE SEGURIDAD CRÍTICA</span>
                </div>
                <p>
                  {obdCapturedSnapshot?.dtcs_active?.includes('P0230')
                    ? 'El código P0230 está activo en el OBD del vehículo. Por seguridad, se prohíbe el envío con resultado PASS limpio. El vehículo será enviado automáticamente a reparación urgente.'
                    : 'Puntos del checklist marcados en estado FAILED requieren corrección inmediata. Se denegará el despacho directo.'}
                </p>
              </div>
            )}

            {/* SIGNATURE & LOCK SECTION */}
            <div className="rounded-xl border border-white/10 bg-black/40 p-4 space-y-4">
              <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400">Firma Certificada del Conductor</h4>
              
              <div className="grid gap-3 sm:grid-cols-2 text-xs">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Nombre del Conductor</label>
                  <input
                    type="text"
                    required
                    placeholder="ej. Carlos Mendoza"
                    value={signerName}
                    onChange={e => setSignerName(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/60 px-3 py-2 text-white outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Rol en el Reporte</label>
                  <select
                    value={signerRole}
                    onChange={e => setSignerRole(e.target.value as FleetRole)}
                    className="w-full rounded border border-white/10 bg-black/60 px-3 py-2 text-white outline-none"
                  >
                    <option value="DRIVER">Driver / Operador</option>
                    <option value="MECHANIC">Mecánico de Flotas</option>
                    <option value="ADMIN">Administrador</option>
                  </select>
                </div>
              </div>

              {/* TACTILE SIGNATURE PAD SIMULATOR */}
              <div className="space-y-2">
                <label className="text-xs text-slate-400 font-bold block">Dibuje su firma en el panel inferior:</label>
                <div className="relative border border-slate-700 rounded bg-black/90 h-[100px] overflow-hidden">
                  <canvas
                    ref={canvasRef}
                    width={500}
                    height={100}
                    onMouseMove={drawOnCanvas}
                    className="w-full h-full cursor-crosshair"
                  />
                </div>
                <div className="flex gap-2 justify-end">
                  <button
                    type="button"
                    onClick={handleClearSignature}
                    className="rounded bg-white/5 border border-white/10 px-3 py-1 text-[10px] font-black uppercase text-slate-300 hover:bg-white/10"
                  >
                    Limpiar
                  </button>
                  <button
                    type="button"
                    onClick={handleSaveSignature}
                    className="rounded bg-emerald-500/10 border border-emerald-500/30 px-3 py-1 text-[10px] font-black uppercase text-emerald-400 hover:bg-emerald-500/20"
                  >
                    Confirmar Firma
                  </button>
                </div>
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded bg-emerald-500 py-3 text-xs font-black uppercase text-black hover:bg-emerald-400 transition"
            >
              Firmar y Bloquear DVIR
            </button>
          </form>
        </div>
      )}

      {/* MODAL: ADD COST */}
      {showNewCostModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm animate-fade-in animate-slide-up">
          <form onSubmit={handleCreateCost} className="glass rounded-2xl border border-emerald-500/30 p-6 max-w-md w-full space-y-4">
            <div className="flex items-center justify-between border-b border-white/5 pb-2">
              <h3 className="text-lg font-black text-white">Registrar Gasto Operacional</h3>
              <button type="button" onClick={() => setShowNewCostModal(false)} className="text-slate-400 hover:text-white">✕</button>
            </div>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Vehículo Asociado</label>
                <select
                  required
                  value={costVehId}
                  onChange={e => setCostVehId(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white outline-none"
                >
                  <option value="">-- Seleccionar --</option>
                  {vehicles.map(v => (
                    <option key={v.id} value={v.id}>{v.internal_code}</option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Tipo de Gasto</label>
                  <select
                    value={costType}
                    onChange={e => setCostType(e.target.value as FleetCostType)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white outline-none font-mono"
                  >
                    <option value="FUEL">Combustible (Fuel)</option>
                    <option value="MAINTENANCE">Mantenimiento preventivo</option>
                    <option value="REPAIR">Reparaciones mecánicas</option>
                    <option value="PARTS">Compra de Repuestos</option>
                    <option value="INSURANCE">Seguro vehicular</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <label className="text-slate-400 font-bold">Monto (CRC)</label>
                  <input
                    type="number"
                    required
                    placeholder="Monto en Colones"
                    value={costAmount}
                    onChange={e => setCostAmount(e.target.value)}
                    className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white outline-none font-mono"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Proveedor / Local</label>
                <input
                  type="text"
                  placeholder="ej. Gasolinera Uno"
                  value={costProvider}
                  onChange={e => setCostProvider(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white outline-none"
                />
              </div>

              <div className="space-y-1">
                <label className="text-slate-400 font-bold">Descripción corta</label>
                <input
                  type="text"
                  required
                  placeholder="ej. Cambio de aceite sintético"
                  value={costDesc}
                  onChange={e => setCostDesc(e.target.value)}
                  className="w-full rounded border border-white/10 bg-black/40 px-3 py-2 text-white outline-none"
                />
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded bg-emerald-500 py-2.5 text-xs font-black uppercase text-black hover:bg-emerald-400 transition"
            >
              Registrar Gasto
            </button>
          </form>
        </div>
      )}

    </div>
  );
}

// --- SUB-COMPONENTS & UTILS ---

function TabButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 border-b-2 px-4 py-2 font-mono text-xs font-black uppercase tracking-wider transition whitespace-nowrap ${
        active
          ? 'border-emerald-400 text-emerald-300'
          : 'border-transparent text-slate-400 hover:border-white/10 hover:text-slate-200'
      }`}
    >
      {icon}
      {label}
    </button>
  );
}

function KpiCard({ title, value, label, icon }: { title: string; value: string | number; label: string; icon: React.ReactNode }) {
  return (
    <div className="glass rounded-xl border border-white/10 bg-white/[0.02] p-4 flex items-start justify-between">
      <div className="space-y-1">
        <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-slate-500">{title}</span>
        <div className="text-2xl font-black text-white tracking-tight">{value}</div>
        <p className="text-[10px] text-slate-400">{label}</p>
      </div>
      <div className="rounded-lg bg-black/40 p-2 border border-white/5">{icon}</div>
    </div>
  );
}

function PricingTierCard({ name, limit, price, active, description }: { name: string; limit: string; price: string; active: boolean; description: string }) {
  return (
    <div className={`rounded-xl border p-4 flex flex-col justify-between h-[200px] text-left transition ${
      active
        ? 'border-emerald-500 bg-emerald-500/[0.03] shadow-[0_0_20px_rgba(16,185,129,0.1)]'
        : 'border-white/10 bg-white/[0.01]'
    }`}>
      <div className="space-y-1">
        <h4 className="font-mono text-xs font-black uppercase text-slate-300">{name.replace('_', ' ')}</h4>
        <div className="text-xl font-black text-white">{price}</div>
        <p className="font-mono text-[9px] text-emerald-400 uppercase tracking-wide">{limit}</p>
        <p className="text-[10px] text-slate-400 mt-2 leading-relaxed">{description}</p>
      </div>
      {active && (
        <span className="rounded bg-emerald-500 px-2 py-0.5 text-[9px] font-black uppercase text-black w-max mt-2">
          Plan Activo
        </span>
      )}
    </div>
  );
}

// Checklist Field Component inside DVIR Wizard
function ChecklistFieldItem({
  itemKey,
  label,
  dvirStates,
  setDvirStates
}: {
  itemKey: string;
  label: string;
  dvirStates: Record<string, { status: ItemStatus; notes: string; photo_uri: string | null }>;
  setDvirStates: React.Dispatch<React.SetStateAction<Record<string, { status: ItemStatus; notes: string; photo_uri: string | null }>>>;
}) {
  const currentState = dvirStates[itemKey] || { status: 'OK', notes: '', photo_uri: null };

  const setStatus = (status: ItemStatus) => {
    setDvirStates(prev => ({
      ...prev,
      [itemKey]: { ...currentState, status }
    }));
  };

  const setNotes = (notes: string) => {
    setDvirStates(prev => ({
      ...prev,
      [itemKey]: { ...currentState, notes }
    }));
  };

  // Simulate Photo Upload
  const handleSimulatePhoto = () => {
    setDvirStates(prev => ({
      ...prev,
      [itemKey]: { ...currentState, photo_uri: `https://meet-evidence-photos.com/dvir_${itemKey}_${Date.now()}.jpg` }
    }));
  };

  return (
    <div className="bg-white/[0.02] border border-white/5 rounded-lg p-3 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <span className="font-bold text-slate-200 leading-tight">{label}</span>
        
        {/* Toggle States */}
        <div className="flex gap-1">
          <button
            type="button"
            onClick={() => setStatus('OK')}
            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
              currentState.status === 'OK' ? 'bg-emerald-500 text-black' : 'bg-white/5 text-slate-400 hover:bg-white/10'
            }`}
          >
            OK
          </button>
          <button
            type="button"
            onClick={() => setStatus('WARNING')}
            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
              currentState.status === 'WARNING' ? 'bg-amber-500 text-black' : 'bg-white/5 text-slate-400 hover:bg-white/10'
            }`}
          >
            WARN
          </button>
          <button
            type="button"
            onClick={() => setStatus('FAILED')}
            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
              currentState.status === 'FAILED' ? 'bg-red-500 text-white' : 'bg-white/5 text-slate-400 hover:bg-white/10'
            }`}
          >
            FAIL
          </button>
        </div>
      </div>

      {/* Note and evidence controls for warning/failed items */}
      {(currentState.status === 'WARNING' || currentState.status === 'FAILED') && (
        <div className="space-y-1.5 pt-1 border-t border-white/5">
          <input
            type="text"
            placeholder="Describa el fallo/observación..."
            value={currentState.notes}
            onChange={e => setNotes(e.target.value)}
            className="w-full rounded border border-white/10 bg-black/60 px-2 py-1 text-[11px] text-white outline-none focus:border-cyan-500"
          />
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={handleSimulatePhoto}
              className="rounded bg-white/5 border border-white/10 px-2 py-1 text-[9px] font-black uppercase text-slate-300 hover:bg-white/10"
            >
              {currentState.photo_uri ? '✓ Foto Adjunta' : '📷 Capturar Evidencia Foto'}
            </button>
            {currentState.photo_uri && (
              <span className="font-mono text-[8px] text-slate-500 truncate max-w-[150px]">{currentState.photo_uri}</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

