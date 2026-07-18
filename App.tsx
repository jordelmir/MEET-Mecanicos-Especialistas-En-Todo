
import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { INITIAL_WORK_ORDERS, MECHANICS as INITIAL_MECHANICS, SERVICES as DEFAULT_SERVICES, DEFAULT_OPEN_HOUR, DEFAULT_CLOSE_HOUR, INITIAL_CLIENTS, MOCK_ADMIN_USER, SERVICE_CATALOG } from './constants';
import { WorkOrder, Role, WorkOrderStatus, Metrics, Client, ServiceHistoryItem, Service, Mechanic, VehicleInfo, OscilloscopeMeasurement } from './types';
import { Timeline } from './components/Timeline';
import { MetricsPanel } from './components/MetricsPanel';
import { MechanicDashboard } from './components/MechanicDashboard';
import { WorkOrderWizard } from './components/WorkOrderWizard';
import { ServiceManager } from './components/ServiceManager';
import { MechanicManager } from './components/MechanicManager';
import { ClientManager } from './components/ClientManager';
import { WorkOrderEditor } from './components/WorkOrderEditor';
import { ShopSettings } from './components/ShopSettings';
import { ServiceCatalogView } from './components/ServiceCatalogView';
import { LoginPage } from './components/LoginPage';
import { IndustrialBackground } from './components/IndustrialBackground';
import { AnalyticsPanel } from './components/AnalyticsPanel';
import { CommandPalette } from './components/CommandPalette';
import { WorkOrderReceipt } from './components/WorkOrderReceipt';
import { useToast } from './components/ToastSystem';
import { PlatformCommandCenter } from './components/PlatformCommandCenter';
import { canTransitionStatus, getStatusLabel, validateSchedule } from './services/timeEngine';
import { saveState, loadState } from './services/storage';
import { createId } from './services/ids';
import { ClientDashboard } from './components/ClientDashboard';
import { UserProfileModal } from './components/UserProfileModal';
import { TVDashboard } from './components/TVDashboard';
import { Wrench, User, Plus, Settings, Users, ChevronDown, LogOut, Gauge, BarChart3, Car, BookOpen, ClipboardList, Search, FileText, Monitor, AlertTriangle, Radio, Activity, Video } from 'lucide-react';
import { GarageDashboard } from './components/GarageDashboard';
import ManualsCenter from './components/ManualsCenter';
import { VehicleProfile, VehicleDigitalTwin, VehicleTimelineEvent, PredictiveMaintenanceAlert, MaintenanceRecord, DashcamSession, DashcamClip, DrivingEvent } from './types';
import { generatePredictiveAlerts } from './services/garageEngine';
import { VisualDiagnosticsView } from './components/VisualDiagnosticsView';
import { PartsRepairsCatalog } from './components/PartsRepairsCatalog';
import { AnalyticsDebugPanel } from './components/analytics/AnalyticsDebugPanel';
import { analytics } from './src/analytics/analyticsClient';
import { AnalyticsConsentManager } from './src/analytics/analyticsConsent';
import { ANALYTICS_EVENTS } from './src/analytics/analyticsEvents';
import { ANALYTICS_FUNNELS } from './src/analytics/analyticsFunnels';
import { useAnalyticsLifecycle, useAnalyticsScreen } from './src/analytics/analyticsHooks';
import type { AnalyticsConsentState } from './src/analytics/analyticsTypes';
import { useBrand } from './lib/BrandModuleRegistry';

const OBD2Scanner = React.lazy(() =>
  import('./components/OBD2Scanner').then(module => ({ default: module.OBD2Scanner }))
);

const LiveLinkDashboard = React.lazy(() =>
  import('./components/LiveLinkDashboard').then(module => ({ default: module.LiveLinkDashboard }))
);

const FleetDashboard = React.lazy(() => import('./components/FleetDashboard'));
const WorkshopCRM = React.lazy(() => import('./components/WorkshopCRM'));
const VerifiedCompanyPanel = React.lazy(() => import('./components/VerifiedCompanyPanel'));
const AdCampaignConsole = React.lazy(() => import('./components/AdCampaignConsole'));
const GDPRComplianceView = React.lazy(() => import('./components/GDPRComplianceView'));
const SubscriptionCheckout = React.lazy(() => import('./components/SubscriptionCheckout'));
const PayoutsView = React.lazy(() => import('./components/PayoutsView'));
const DashcamHUDLazy = React.lazy(() =>
  import('./components/DashcamHUD').then(module => ({ default: module.DashcamHUD }))
);

export default function App() {
  const { t } = useBrand();
  // ── AUTH STATE ──
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [role, setRole] = useState<Role>(Role.ADMIN);
  const [loggedInUser, setLoggedInUser] = useState<Client>(MOCK_ADMIN_USER);

	  const { toast } = useToast();
  const lazyModalFallback = (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-[70] flex items-center justify-center p-4">
      <div className="glass rounded-xl px-6 py-4 font-mono text-xs font-bold text-forge-500">
        Cargando modulo...
      </div>
    </div>
  );
  const ecosystemFallback = (
    <div className="glass rounded-2xl border border-cyan-500/20 p-8 text-center shadow-[0_0_30px_rgba(34,211,238,0.08)]">
      <div className="mx-auto mb-4 h-10 w-10 rounded-xl border border-cyan-400/25 bg-cyan-400/10 flex items-center justify-center">
        <Gauge size={19} className="text-cyan-200 animate-pulse" />
      </div>
      <p className="font-mono text-xs font-bold uppercase tracking-[0.22em] text-cyan-200">Cargando modulo</p>
    </div>
  );

  // ── DATA STATE (with localStorage persistence) ──
  const [workOrders, setWorkOrders] = useState<WorkOrder[]>(() => loadState('workOrders', INITIAL_WORK_ORDERS));
  const [clients, setClients] = useState<Client[]>(() => loadState('clients', INITIAL_CLIENTS));
  const [services, setServices] = useState<Service[]>(() => loadState('services', DEFAULT_SERVICES));
  const [mechanics, setMechanics] = useState<Mechanic[]>(() => loadState('mechanics', INITIAL_MECHANICS));
  const [catalog, setCatalog] = useState<any[]>(() => loadState('catalog', SERVICE_CATALOG));

  // ── SHOP SETTINGS ──
  const [shopRules, setShopRules] = useState<string>(() => loadState('shopRules', "1. Verificar el vehículo al recibir con el cliente presente.\n2. Notificar al cliente antes de realizar trabajos adicionales.\n3. Garantía de 30 días en mano de obra."));
  const [openHour, setOpenHour] = useState<number>(() => loadState('openHour', DEFAULT_OPEN_HOUR));
  const [closeHour, setCloseHour] = useState<number>(() => loadState('closeHour', DEFAULT_CLOSE_HOUR));
  const [timeSliceMinutes, setTimeSliceMinutes] = useState<number>(() => loadState('timeSlice', 30));
  const [freeWashThreshold, setFreeWashThreshold] = useState<number>(() => loadState('freeWashThreshold', 45000));

  // ── PERSIST STATE ──
  useEffect(() => { saveState('workOrders', workOrders); }, [workOrders]);
  useEffect(() => { saveState('clients', clients); }, [clients]);
  useEffect(() => { saveState('services', services); }, [services]);
  useEffect(() => { saveState('mechanics', mechanics); }, [mechanics]);
  useEffect(() => { saveState('catalog', catalog); }, [catalog]);
  useEffect(() => { saveState('shopRules', shopRules); }, [shopRules]);
  useEffect(() => { saveState('openHour', openHour); }, [openHour]);
  useEffect(() => { saveState('closeHour', closeHour); }, [closeHour]);
  useEffect(() => { saveState('timeSlice', timeSliceMinutes); }, [timeSliceMinutes]);
  useEffect(() => { saveState('freeWashThreshold', freeWashThreshold); }, [freeWashThreshold]);

  // ── GARAGE STATE ──
  const [vehicles, setVehicles] = useState<VehicleProfile[]>(() => {
    const stored = loadState('vehicles', []);
    if (stored.length > 0) return stored;
    const migrated: VehicleProfile[] = [];
    INITIAL_CLIENTS.forEach(c => {
      c.vehicles.forEach((v, index) => {
        migrated.push({
          id: `veh_${c.id}_${index}`,
          owner_user_id: c.id,
          nickname: `${v.brand} ${v.model}`,
          make: v.brand,
          model: v.model,
          year: v.year,
          trim_nullable: null,
          engine: '1.6L',
          engine_code_nullable: null,
          transmission: 'AUTOMATIC',
          fuel_type: 'GASOLINE',
          vin_nullable: v.vin || `VIN-${c.id}-${index}`,
          plate_nullable: v.plate,
          odometer_km: v.mileage,
          country: 'Costa Rica',
          province_nullable: null,
          color_nullable: v.color,
          photo_uri_nullable: null,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
          plate: v.plate,
          brand: v.brand,
          color: v.color,
          mileage: v.mileage
        });
      });
    });
    return migrated;
  });

  const [digitalTwins, setDigitalTwins] = useState<VehicleDigitalTwin[]>(() => {
    const stored = loadState('digitalTwins', []);
    if (stored.length > 0) return stored;
    const initialTwins: VehicleDigitalTwin[] = [];
    INITIAL_CLIENTS.forEach(c => {
      c.vehicles.forEach((v, index) => {
        initialTwins.push({
          vehicle_id: `veh_${c.id}_${index}`,
          baseline_created_at: new Date().toISOString(),
          baseline_confidence: 90,
          normal_idle_rpm_min: 750,
          normal_idle_rpm_max: 820,
          normal_voltage_min: 13.8,
          normal_voltage_max: 14.4,
          normal_ect_min: 88,
          normal_ect_max: 96,
          normal_fuel_trim_min: -3,
          normal_fuel_trim_max: 3,
          normal_maf_min: 3,
          normal_maf_max: 6,
          normal_map_min: 30,
          normal_map_max: 42,
          driving_profile: 'MIXED',
          health_score: 100,
          risk_score: 0,
          last_updated_at: new Date().toISOString()
        });
      });
    });
    return initialTwins;
  });

  const [timelineEvents, setTimelineEvents] = useState<VehicleTimelineEvent[]>(() => {
    const stored = loadState('timelineEvents', []);
    if (stored.length > 0) return stored;
    const initialEvents: VehicleTimelineEvent[] = [];
    INITIAL_CLIENTS.forEach(c => {
      c.vehicles.forEach((v, index) => {
        const vId = `veh_${c.id}_${index}`;
        initialEvents.push({
          id: `ev_init_${vId}`,
          vehicle_id: vId,
          event_type: 'VEHICLE_CREATED',
          title: 'Historial de Vehículo Creado',
          description: `Se inicializó el expediente técnico de ${v.brand} ${v.model}.`,
          severity: 'low',
          source: 'System',
          related_report_id_nullable: null,
          related_work_order_id_nullable: null,
          related_part_request_id_nullable: null,
          related_livelink_id_nullable: null,
          created_at: new Date().toISOString()
        });
      });
    });
    return initialEvents;
  });

  const [predictiveAlerts, setPredictiveAlerts] = useState<PredictiveMaintenanceAlert[]>(() => loadState('predictiveAlerts', []));
  const [maintenanceRecords, setMaintenanceRecords] = useState<MaintenanceRecord[]>(() => {
    const stored = loadState('maintenanceRecords', []);
    if (stored.length > 0) return stored;
    const initialRecords: MaintenanceRecord[] = [];
    INITIAL_CLIENTS.forEach(c => {
      c.vehicles.forEach((v, index) => {
        const vId = `veh_${c.id}_${index}`;
        initialRecords.push({
          id: `maint_init_${vId}`,
          vehicle_id: vId,
          type: 'OIL_CHANGE',
          title: 'Alineación y Cambio de Aceite Inicial',
          odometer_km: v.mileage - 2000,
          date: new Date().toISOString(),
          provider_id_nullable: null,
          provider_name: 'Taller MEET',
          cost_nullable: 25000,
          currency: 'CRC',
          parts_used: ['Filtro de Aceite', 'Aceite Sintético Castrol'],
          notes: 'Inspección de cortesía realizada.',
          photos: [],
          report_id_nullable: null,
          created_at: new Date().toISOString()
        });
      });
    });
    return initialRecords;
  });

  useEffect(() => { saveState('vehicles', vehicles); }, [vehicles]);
  useEffect(() => { saveState('digitalTwins', digitalTwins); }, [digitalTwins]);
  useEffect(() => { saveState('timelineEvents', timelineEvents); }, [timelineEvents]);
  useEffect(() => { saveState('predictiveAlerts', predictiveAlerts); }, [predictiveAlerts]);
  useEffect(() => { saveState('maintenanceRecords', maintenanceRecords); }, [maintenanceRecords]);

  // ── DASHCAM / CAJA NEGRA STATE ──
  const [dashcamSessions, setDashcamSessions] = useState<DashcamSession[]>(() => loadState('dashcamSessions', []));
  const [dashcamClips, setDashcamClips] = useState<DashcamClip[]>(() => loadState('dashcamClips', []));
  const [drivingEvents, setDrivingEvents] = useState<DrivingEvent[]>(() => loadState('drivingEvents', []));
  useEffect(() => { saveState('dashcamSessions', dashcamSessions); }, [dashcamSessions]);
  useEffect(() => { saveState('dashcamClips', dashcamClips); }, [dashcamClips]);
  useEffect(() => { saveState('drivingEvents', drivingEvents); }, [drivingEvents]);

  // ── UI STATE ──
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);
  const [isServiceManagerOpen, setIsServiceManagerOpen] = useState(false);
  const [isMechanicManagerOpen, setIsMechanicManagerOpen] = useState(false);
  const [isClientManagerOpen, setIsClientManagerOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isCatalogOpen, setIsCatalogOpen] = useState(false);
  const [editingWorkOrder, setEditingWorkOrder] = useState<WorkOrder | null>(null);
  const [adminViewMode, setAdminViewMode] = useState<'DASHBOARD' | 'WORKSTATION'>('DASHBOARD');
  const [vanguardTab, setVanguardTab] = useState<'DASHBOARD' | 'FLEET' | 'CRM' | 'VERIFIED' | 'CAMPAIGNS' | 'GDPR' | 'SUBSCRIPTIONS' | 'PAYOUTS' | 'GARAGE' | 'TOPOLOGY' | 'MANUALS' | 'HUD_DASHCAM' | 'PARTS'>('DASHBOARD');
  const [activeDtcFocus, setActiveDtcFocus] = useState<string | null>(null);
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [isPaletteOpen, setIsPaletteOpen] = useState(false);
  const [receiptWorkOrder, setReceiptWorkOrder] = useState<WorkOrder | null>(null);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isTVModeOpen, setIsTVModeOpen] = useState(false);
  const [isOBD2Open, setIsOBD2Open] = useState(false);
  const [isLiveLinkOpen, setIsLiveLinkOpen] = useState(false);
  const [analyticsConsent, setAnalyticsConsent] = useState<AnalyticsConsentState>(() => AnalyticsConsentManager.getConsent());

  useAnalyticsLifecycle(isAuthenticated ? loggedInUser.id : null);

  useEffect(() => {
    return AnalyticsConsentManager.subscribe(setAnalyticsConsent);
  }, []);

  const handleAnalyticsConsentChange = useCallback((consent: AnalyticsConsentState) => {
    AnalyticsConsentManager.setConsent(consent);
    setAnalyticsConsent(consent);
  }, []);

  const analyticsScreenName = useMemo(() => {
    if (!isAuthenticated) return 'Login';
    if (isOBD2Open) return 'OBD2 Scanner';
    if (isLiveLinkOpen) return 'Live Link';
    if (isBookingModalOpen) return 'Nueva Orden';
    if (isCatalogOpen) return 'Catálogo';
    if (isMechanicManagerOpen) return 'Mecánicos';
    if (isClientManagerOpen) return 'Clientes';
    if (isServiceManagerOpen) return 'Servicios';
    if (isSettingsOpen) return 'Config';
    if (isTVModeOpen) return 'TV';
    if (role === Role.CLIENT) return 'Cliente';
    return adminViewMode === 'WORKSTATION' ? 'Estación' : 'Dashboard';
  }, [
    adminViewMode,
    isAuthenticated,
    isBookingModalOpen,
    isCatalogOpen,
    isClientManagerOpen,
    isLiveLinkOpen,
    isMechanicManagerOpen,
    isOBD2Open,
    isServiceManagerOpen,
    isSettingsOpen,
    isTVModeOpen,
    role,
  ]);

  useAnalyticsScreen(analyticsScreenName, { role, admin_view_mode: adminViewMode });

  const openBooking = useCallback((source: string) => {
    analytics.track(ANALYTICS_EVENTS.NEW_ORDER_CLICKED, { source, role });
    analytics.funnelStep(ANALYTICS_FUNNELS.workshopOrder.name, 'new_order_clicked', { source, role });
    setIsBookingModalOpen(true);
  }, [role]);

  const closeBooking = useCallback((reason: string = 'user_closed') => {
    analytics.funnelAbandoned(ANALYTICS_FUNNELS.workshopOrder.name, 'new_order_clicked', {
      reason_if_known: reason,
      route: analyticsScreenName,
    });
    setIsBookingModalOpen(false);
  }, [analyticsScreenName]);

  const isAnalyticsDebugRoute = typeof window !== 'undefined' &&
    import.meta.env.VITE_ENABLE_ANALYTICS_DEBUG === 'true' &&
    window.location.pathname === '/analytics-debug';

  // ── DERIVED STATE ──
  const visibleMechanics = useMemo(() => {
    if (role === Role.MECHANIC) {
      return mechanics.filter(m => m.id === loggedInUser.id);
    }
    return mechanics;
  }, [mechanics, role, loggedInUser.id]);

  const visibleWorkOrders = useMemo(() => {
    if (role === Role.MECHANIC) {
      return workOrders.filter(wo => wo.mechanicId === loggedInUser.id);
    }
    return workOrders;
  }, [workOrders, role, loggedInUser.id]);

  // ── LOGIN ──
  const handleLogin = async (identity: string, code: string) => {
    setAuthError(null);

    // 1. Admin
    if (
      (identity === MOCK_ADMIN_USER.identification || identity === MOCK_ADMIN_USER.email) &&
      code === MOCK_ADMIN_USER.accessCode
    ) {
      setRole(Role.ADMIN);
      setLoggedInUser(MOCK_ADMIN_USER);
      setIsAuthenticated(true);
      analytics.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: 'login_success', role: Role.ADMIN });
      return;
    }

    // 2. Mechanics
    const mechFound = mechanics.find(m =>
      (m.identification === identity || m.email === identity) && m.accessCode === code
    );
    if (mechFound) {
      setRole(Role.MECHANIC);
      const mechAsUser: Client = {
        id: mechFound.id, name: mechFound.name, phone: mechFound.phone,
        email: mechFound.email, identification: mechFound.identification,
        accessCode: mechFound.accessCode, vehicles: [], serviceHistory: [],
        joinDate: new Date(), loyaltyPoints: 0, avatar: mechFound.avatar,
        notes: `Staff: ${mechFound.specialty}`,
      };
      setLoggedInUser(mechAsUser);
      setIsAuthenticated(true);
      analytics.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: 'login_success', role: Role.MECHANIC });
      return;
    }

    // 3. Clients
    const clientFound = clients.find(c =>
      (c.identification === identity || c.email === identity) && c.accessCode === code
    );
    if (clientFound) {
      setRole(Role.CLIENT);
      setLoggedInUser(clientFound);
      setIsAuthenticated(true);
      analytics.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: 'login_success', role: Role.CLIENT });
      toast('success', 'Sesión Iniciada', `Bienvenido, ${clientFound.name}`);
      return;
    }

    setAuthError("Credenciales inválidas. Verifica tu Cédula/Email y Código.");
    toast('error', 'Error de Autenticación', 'Credenciales inválidas');
  };

  const handleRegister = async (data: { name: string; email: string; phone: string; identification: string; accessCode: string }) => {
    setAuthError(null);
    if (clients.some(c => c.identification === data.identification || c.email === data.email)) {
      setAuthError("Ya existe un usuario con ese correo o cédula.");
      toast('error', 'Error de Registro', 'El usuario ya existe');
      return;
    }
    
    const newClient: Client = {
      id: `c${Date.now()}`, 
      ...data,
      vehicles: [],
      serviceHistory: [], 
      loyaltyPoints: 0, 
      joinDate: new Date(),
    };
    
    setClients(prev => [...prev, newClient]);
    setRole(Role.CLIENT);
    setLoggedInUser(newClient);
    setIsAuthenticated(true);
    analytics.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: 'registration_success', role: Role.CLIENT });
    toast('success', 'Cuenta Creada', t(`Bienvenido a MEET, ${newClient.name.split(' ')[0]}`));
  };

  const handleLogout = () => {
    analytics.track(ANALYTICS_EVENTS.SESSION_ENDED, { reason: 'logout' });
    setIsAuthenticated(false);
    setRole(Role.ADMIN);
    setAuthError(null);
    setAdminViewMode('DASHBOARD');
    toast('info', 'Sesión Cerrada', 'Has salido del sistema');
  };

  // ── METRICS ──
  const metrics: Metrics = useMemo(() => {
    const todaysOrders = visibleWorkOrders.filter(wo =>
      wo.startTime.getFullYear() === currentDate.getFullYear() &&
      wo.startTime.getMonth() === currentDate.getMonth() &&
      wo.startTime.getDate() === currentDate.getDate() &&
      wo.status !== WorkOrderStatus.CANCELLED
    );

    const totalMinutes = (closeHour - openHour) * 60 * visibleMechanics.length;
    let bookedMinutes = 0;
    let completedCount = 0;
    let revenue = 0;

    todaysOrders.forEach(wo => {
      const duration = (wo.estimatedEndTime.getTime() - wo.startTime.getTime()) / 60000;
      bookedMinutes += duration;
      if (wo.status === WorkOrderStatus.COMPLETED || wo.status === WorkOrderStatus.DELIVERED) {
        revenue += wo.price;
        completedCount++;
      }
    });

    const idleTime = Math.max(0, totalMinutes - bookedMinutes);

    return {
      dailyOccupancy: totalMinutes > 0 ? Math.round((bookedMinutes / totalMinutes) * 100) : 0,
      idleTimeMinutes: Math.round(idleTime),
      revenue,
      ordersCompleted: completedCount,
      ordersTotal: todaysOrders.length,
    };
  }, [visibleWorkOrders, currentDate, visibleMechanics.length, openHour, closeHour]);

  // ── STATUS CHANGE ──
  const handleStatusChange = (id: string, newStatus: WorkOrderStatus) => {
    const currentOrder = workOrders.find(w => w.id === id);
    if (!currentOrder) return;
    if (!canTransitionStatus(currentOrder.status, newStatus)) {
      toast('warning', 'Cambio de estado bloqueado', `${getStatusLabel(currentOrder.status)} no puede pasar directamente a ${getStatusLabel(newStatus)}`);
      return;
    }

    setWorkOrders(prev => prev.map(wo => {
      if (wo.id !== id) return wo;

      const updates: any = { status: newStatus };

      if (newStatus === WorkOrderStatus.IN_PROGRESS) {
        updates.actualStartTime = new Date();
      } else if (newStatus === WorkOrderStatus.COMPLETED) {
        updates.actualEndTime = new Date();
        if (wo.actualStartTime) {
          updates.estimatedMinutes = Math.round((updates.actualEndTime.getTime() - wo.actualStartTime.getTime()) / 60000);
        }
      }

      return { ...wo, ...updates };
    }));

    // Update client service history on completion
    if (newStatus === WorkOrderStatus.COMPLETED) {
      const wo = workOrders.find(w => w.id === id);
      if (wo && wo.status !== WorkOrderStatus.COMPLETED) {
        const service = services.find(s => s.id === wo.serviceId);
        const mech = mechanics.find(m => m.id === wo.mechanicId);
        if (service && mech) {
	          const historyItem: ServiceHistoryItem = {
	            id: createId('hist'),
            date: wo.startTime,
            serviceName: service.name,
            mechanicName: mech.name,
            price: wo.price,
            vehicleInfo: `${wo.vehicleInfo.brand} ${wo.vehicleInfo.model} ${wo.vehicleInfo.year}`,
            notes: wo.notes,
          };

          setClients(prevClients => prevClients.map(c =>
            c.id === wo.clientId
              ? { ...c, serviceHistory: [historyItem, ...c.serviceHistory], lastVisit: new Date(), loyaltyPoints: c.loyaltyPoints + 1 }
              : c
          ));
        }
      }
      toast('success', 'Orden Completada', `El trabajo de ${wo?.vehicleInfo.plate} ha finalizado`);
    } else {
      toast('info', 'Estado Actualizado', `Nuevo estado: ${getStatusLabel(newStatus)}`);
    }
  };

	  // ── UPDATE WORK ORDER ──
	  const handleUpdateWorkOrder = (id: string, updates: { price: number; estimatedMinutes: number; startTime?: Date; vehicleMileage?: number }) => {
    const currentOrder = workOrders.find(wo => wo.id === id);
    if (!currentOrder) return;
    const mechanic = mechanics.find(m => m.id === currentOrder.mechanicId);
    const baseService = services.find(s => s.id === currentOrder.serviceId);
    const serviceForValidation = baseService
      ? { ...baseService, estimatedMinutes: updates.estimatedMinutes }
      : undefined;
    const validation = validateSchedule({
      mechanic,
      service: serviceForValidation,
      startTime: updates.startTime || currentOrder.startTime,
      existingOrders: workOrders,
      openHour,
      closeHour,
      timeSliceMinutes,
      excludeOrderId: id,
      allowPastStart: true,
    });

    if (!validation.valid) {
      toast('error', 'No se pudo actualizar la orden', validation.errors[0]);
      return;
    }

	    setWorkOrders(prev => prev.map(wo => {
      if (wo.id !== id) return wo;
      const startToUse = updates.startTime || wo.startTime;
      const newEnd = new Date(startToUse.getTime() + updates.estimatedMinutes * 60000);
      
      const newVehicleInfo = { ...wo.vehicleInfo };
      if (updates.vehicleMileage !== undefined && updates.vehicleMileage !== wo.vehicleInfo.mileage) {
        newVehicleInfo.mileage = updates.vehicleMileage;
        
        // Also update the client's vehicle mileage
        setClients(clientsPrev => clientsPrev.map(client => {
          if (client.id === wo.clientId) {
            const updatedClient = {
              ...client,
              vehicles: client.vehicles.map(v => 
                v.plate === wo.vehicleInfo.plate ? { ...v, mileage: updates.vehicleMileage! } : v
              )
            };
            if (loggedInUser.id === client.id) setLoggedInUser(updatedClient);
            return updatedClient;
          }
          return client;
        }));
      }

      return { ...wo, price: updates.price, estimatedMinutes: updates.estimatedMinutes, startTime: startToUse, estimatedEndTime: newEnd, vehicleInfo: newVehicleInfo };
    }));
    setEditingWorkOrder(null);
    toast('success', 'Orden Actualizada', 'Los detalles se guardaron correctamente');
  };

	  // ── CANCEL ──
	  const handleCancelWorkOrder = (orderId: string, reason?: string) => {
    const currentOrder = workOrders.find(wo => wo.id === orderId);
    if (!currentOrder) return;
    if (!canTransitionStatus(currentOrder.status, WorkOrderStatus.CANCELLED)) {
      toast('warning', 'Cancelación bloqueada', `Una orden ${getStatusLabel(currentOrder.status)} no puede cancelarse desde este flujo.`);
      return;
    }

	    setWorkOrders(prev => prev.map(wo =>
      wo.id === orderId
        ? { ...wo, status: WorkOrderStatus.CANCELLED, cancellationReason: reason || 'Cancelada', cancellationDate: new Date() }
        : wo
    ));
    toast('warning', 'Orden Cancelada', 'La orden ha sido anulada');
  };

  // ── CRUD HANDLERS ──
	  const handleCreateClient = (clientData: any): Client => {
	    const newClient: Client = {
	      id: createId('c'), ...clientData,
      vehicles: clientData.vehicles || [],
      serviceHistory: [], loyaltyPoints: 0, joinDate: new Date(),
    };
    setClients(prev => [...prev, newClient]);
    toast('success', 'Cliente Creado', `${newClient.name} ha sido registrado`);
    return newClient;
  };

  const handleUpdateClient = (updatedClient: Client) => {
    setClients(prev => prev.map(c => c.id === updatedClient.id ? updatedClient : c));
    if (loggedInUser.id === updatedClient.id) setLoggedInUser(updatedClient);
    toast('success', 'Cliente Actualizado', 'Información guardada con éxito');
  };

  const handleDeleteClient = (clientId: string) => {
    const hasOrders = workOrders.some(wo => wo.clientId === clientId);
    if (hasOrders) {
      toast('warning', 'Cliente con historial', 'No se puede eliminar un cliente que ya tiene órdenes asociadas.');
      return;
    }
    setClients(prev => prev.filter(c => c.id !== clientId));
    toast('info', 'Cliente Eliminado');
  };

	  const handleAddService = (serviceData: Omit<Service, 'id'>) => {
	    setServices(prev => [...prev, { id: createId('s'), ...serviceData }]);
    toast('success', 'Servicio Agregado');
  };
  const handleUpdateService = (svc: Service) => {
    setServices(prev => prev.map(s => s.id === svc.id ? svc : s));
    toast('success', 'Servicio Actualizado');
  };
  const handleDeleteService = (id: string) => {
    if (workOrders.some(wo => wo.serviceId === id)) {
      toast('warning', 'Servicio en uso', 'No se puede eliminar un servicio con órdenes asociadas.');
      return;
    }
    setServices(prev => prev.filter(s => s.id !== id));
    toast('info', 'Servicio Eliminado');
  };

	  const handleAddMechanic = (data: Omit<Mechanic, 'id'>) => {
	    setMechanics(prev => [...prev, { id: createId('m'), ...data }]);
    toast('success', 'Mecánico Registrado');
  };
  const handleUpdateMechanic = (mech: Mechanic) => {
    setMechanics(prev => prev.map(m => m.id === mech.id ? mech : m));
    if (loggedInUser.id === mech.id) setLoggedInUser(prev => ({ ...prev, avatar: mech.avatar, name: mech.name }));
    toast('success', 'Mecánico Actualizado');
  };
  const handleDeleteMechanic = (id: string) => {
    if (workOrders.some(wo => wo.mechanicId === id)) {
      toast('warning', 'Mecánico con historial', 'No se puede eliminar un mecánico que ya tiene órdenes asociadas.');
      return;
    }
    setMechanics(prev => prev.filter(m => m.id !== id));
    toast('info', 'Mecánico Eliminado');
  };

  const handleUpdateCurrentUser = (updatedUser: any) => {
    if (role === Role.CLIENT) {
      handleUpdateClient(updatedUser);
    } else if (role === Role.MECHANIC) {
      handleUpdateMechanic(updatedUser);
    } else if (role === Role.ADMIN) {
      setLoggedInUser(updatedUser);
      toast('success', 'Perfil Actualizado');
    }
  };

  const handleUpdateSettings = (settings: { rules: string; openHour: number; closeHour: number; timeSlice: number; freeWashThreshold: number }) => {
    setShopRules(settings.rules);
    setOpenHour(settings.openHour);
    setCloseHour(settings.closeHour);
    setTimeSliceMinutes(settings.timeSlice);
    setFreeWashThreshold(settings.freeWashThreshold);
    toast('success', 'Configuración Guardada', 'Las preferencias del taller se han actualizado');
  };

  // ── BOOKING ──
  const handleBook = (clientId: string, clientName: string, mechanicId: string, serviceId: string, time: Date, vehicle: VehicleInfo, notes?: string) => {
    const mech = mechanics.find(m => m.id === mechanicId);
    const service = services.find(s => s.id === serviceId);
    const validation = validateSchedule({
      mechanic: mech,
      service,
      startTime: time,
      existingOrders: workOrders,
      openHour,
      closeHour,
      timeSliceMinutes,
    });

    if (!validation.valid || !mech || !service) {
      toast('error', 'No se pudo crear la orden', validation.errors[0] || 'Datos de reserva inválidos');
      return;
    }

	    const newOrder: WorkOrder = {
	      id: createId('wo'),
      clientId, clientName, mechanicId, serviceId,
      vehicleInfo: vehicle,
      startTime: time, estimatedEndTime: validation.endTime,
      status: WorkOrderStatus.RECEIVED,
      price: service.basePrice,
      estimatedMinutes: validation.realDurationMinutes,
      notes,
    };

    setWorkOrders(prev => [...prev, newOrder]);
    setIsBookingModalOpen(false);
    setCurrentDate(time);
    analytics.funnelStep(ANALYTICS_FUNNELS.workshopOrder.name, 'order_created', {
      service_id: serviceId,
      mechanic_id: mechanicId,
      role,
    });
    analytics.track(ANALYTICS_EVENTS.ORDER_CREATED, {
      service_id: serviceId,
      mechanic_id: mechanicId,
      estimated_minutes: validation.realDurationMinutes,
      price: service.basePrice,
      role,
    });
    toast('success', 'Orden Creada', `Para ${vehicle.plate} a las ${time.toLocaleTimeString([],{hour: '2-digit', minute:'2-digit'})}`);
  };

  const handleSimulateAPKScan = (scanResult: any) => {
    let finalScan = scanResult;
    // Defensive check: if scanResult is a React Event or missing dtcCodes, generate a valid payload
    if (!scanResult || !scanResult.dtcCodes || typeof scanResult.preventDefault === 'function') {
      const vehiclePlate = loggedInUser.vehicles?.[0]?.plate || 'ABC-123';
      const codes = ['P0300', 'P0171', 'P0420', 'P0115', 'P0302'];
      const randomCodes = [codes[Math.floor(Math.random() * codes.length)]];
      if (Math.random() > 0.5) {
        randomCodes.push(codes[Math.floor(Math.random() * codes.length)]);
      }
      const uniqueCodes = Array.from(new Set(randomCodes));
	      finalScan = {
	        id: createId('scan'),
        date: new Date(),
        vehiclePlate,
        dtcCodes: uniqueCodes,
        severity: uniqueCodes.includes('P0300') || uniqueCodes.includes('P0302') ? 'high' : 'medium',
        notes: `Simulación de escaneo OBD2. Códigos detectados: ${uniqueCodes.join(', ')}.`
      };
    }

    // Ensure date is a Date object (important if parsing stored JSON)
    if (finalScan && typeof finalScan.date === 'string') {
      finalScan.date = new Date(finalScan.date);
    } else if (finalScan && !(finalScan.date instanceof Date)) {
      finalScan.date = new Date();
    }

    const updatedClient = {
      ...loggedInUser,
      scans: [finalScan, ...(loggedInUser.scans || [])]
    };
    handleUpdateClient(updatedClient);

    // Update garage ecosystem if vehicle exists
    const matchingVeh = vehicles.find(v => v.plate === finalScan.vehiclePlate);
    if (matchingVeh) {
      setTimelineEvents(prev => [
        {
          id: `ev_obd_${Date.now()}`,
          vehicle_id: matchingVeh.id,
          event_type: 'OBD_CONNECTED',
          title: 'Sesión OBD Sincronizada',
          description: `Conexión exitosa con el adaptador. Parámetros de abordo leídos.`,
          severity: 'low',
          source: 'OBD',
          related_report_id_nullable: null,
          related_work_order_id_nullable: null,
          related_part_request_id_nullable: null,
          related_livelink_id_nullable: null,
          created_at: new Date().toISOString()
        },
        ...prev
      ]);

      if (finalScan.dtcCodes && finalScan.dtcCodes.length > 0) {
        setActiveDtcFocus(finalScan.dtcCodes[0]);
        setTimelineEvents(prev => [
          {
            id: `ev_dtc_${Date.now()}`,
            vehicle_id: matchingVeh.id,
            event_type: 'DTC_DETECTED',
            title: `DTC Detectado(s): ${finalScan.dtcCodes.join(', ')}`,
            description: finalScan.notes || `Códigos de falla detectados en el sistema de autodiagnóstico.`,
            severity: finalScan.severity === 'high' ? 'high' : 'medium',
            source: 'OBD',
            payload_json: JSON.stringify({ dtcCodes: finalScan.dtcCodes }),
            related_report_id_nullable: null,
            related_work_order_id_nullable: null,
            related_part_request_id_nullable: null,
            related_livelink_id_nullable: null,
            created_at: new Date().toISOString()
          },
          ...prev
        ]);

        // Generate predictive alerts
        const mockVoltages = [14.2, 13.8, finalScan.dtcCodes.includes('P0230') ? 13.1 : 14.1];
        const mockEcts = [88, 92, finalScan.dtcCodes.includes('P0300') ? 104 : 90];
        const newAlerts = generatePredictiveAlerts(matchingVeh.id, matchingVeh.odometer_km, finalScan.dtcCodes, mockVoltages, mockEcts);
        setPredictiveAlerts(prev => {
          const filtered = prev.filter(al => !newAlerts.some(na => na.component === al.component && al.status === 'active'));
          return [...newAlerts, ...filtered];
        });

        // Lower health score on digital twin
        setDigitalTwins(prev => prev.map(dt => {
          if (dt.vehicle_id === matchingVeh.id) {
            return {
              ...dt,
              health_score: 74,
              risk_score: 65,
              last_updated_at: new Date().toISOString()
            };
          }
          return dt;
        }));
      } else {
        setTimelineEvents(prev => [
          {
            id: `ev_dtc_clear_${Date.now()}`,
            vehicle_id: matchingVeh.id,
            event_type: 'DTC_CLEARED',
            title: 'Códigos de Falla Limpios',
            description: 'Autodiagnóstico completado exitosamente. Sin códigos DTC activos.',
            severity: 'low',
            source: 'OBD',
            related_report_id_nullable: null,
            related_work_order_id_nullable: null,
            related_part_request_id_nullable: null,
            related_livelink_id_nullable: null,
            created_at: new Date().toISOString()
          },
          ...prev
        ]);

        // Restore health score on digital twin
        setDigitalTwins(prev => prev.map(dt => {
          if (dt.vehicle_id === matchingVeh.id) {
            return {
              ...dt,
              health_score: 100,
              risk_score: 0,
              last_updated_at: new Date().toISOString()
            };
          }
          return dt;
        }));
      }
    }

    toast('success', 'Escaneo Recibido', t('Datos de OBD2 sincronizados desde la App MEET'));
  };

  const handleSaveOscilloscopeMeasurement = (measurement: OscilloscopeMeasurement) => {
    // Save to client profile
    const updatedClient = {
      ...loggedInUser,
      oscilloscopeMeasurements: [measurement, ...(loggedInUser.oscilloscopeMeasurements || [])].slice(0, 50)
    };
    handleUpdateClient(updatedClient);

    // Also attach to active work order if one exists
    if (measurement.workOrderId) {
      setWorkOrders(prev => prev.map(wo => {
        if (wo.id === measurement.workOrderId) {
          return { ...wo, oscilloscopeMeasurements: [measurement, ...(wo.oscilloscopeMeasurements || [])] };
        }
        return wo;
      }));
    }

    toast('success', 'Medición Guardada', `${measurement.signalName} — ${measurement.severity === 'normal' ? 'Nominal' : measurement.severity === 'warning' ? 'Atención' : 'Crítico'}`);
  };

  const handleUpdateWorkOrderDetails = (id: string, updates: Partial<WorkOrder>) => {
    setWorkOrders(prev => prev.map(wo => wo.id === id ? { ...wo, ...updates } : wo));
    toast('success', 'Orden Actualizada', 'Código DTC vinculado correctamente');
  };

  // ── RENDER ──
  if (isAnalyticsDebugRoute) {
    return <AnalyticsDebugPanel />;
  }

  if (!isAuthenticated) {
    return <LoginPage onLogin={handleLogin} onRegister={handleRegister} error={authError} />;
  }

  const showDashboard = role === Role.ADMIN || role === Role.MECHANIC;
  const currentMechanic = role === Role.MECHANIC
    ? mechanics.find(m => m.id === loggedInUser.id)
    : (role === Role.ADMIN && adminViewMode === 'WORKSTATION')
      ? mechanics[0]
      : null;

  return (
    <div className="min-h-screen text-gray-100 font-sans relative">
      <IndustrialBackground />

      <div className="relative z-10 min-h-screen flex flex-col">
        {/* ── NAV BAR ── */}
        <nav className="fixed top-0 w-full z-50 flex flex-nowrap items-center justify-start md:justify-between px-3 md:px-6 overflow-x-auto hide-scrollbar gap-4 md:gap-0" style={{ height: '64px', background: 'linear-gradient(135deg, rgba(3,5,8,0.95) 0%, rgba(7,10,15,0.97) 50%, rgba(3,5,8,0.95) 100%)', backdropFilter: 'blur(30px) saturate(200%)', WebkitBackdropFilter: 'blur(30px) saturate(200%)', borderBottom: '1px solid rgba(0,240,255,0.15)', boxShadow: '0 4px 40px rgba(0,0,0,0.6), 0 0 60px rgba(0,240,255,0.08), inset 0 1px 0 rgba(255,255,255,0.04)' }}>
          <div className="flex items-center gap-3 shrink-0 mr-auto md:mr-0">
            <div className="relative p-2 rounded-xl text-black" style={{ background: 'linear-gradient(135deg, #00f0ff 0%, #00c2cf 50%, #00f0ff 100%)', boxShadow: '0 0 25px rgba(0,240,255,0.6), 0 0 50px rgba(0,240,255,0.2), inset 0 1px 0 rgba(255,255,255,0.3)' }}>
              <Wrench size={20} strokeWidth={2.5} className="drop-shadow-lg" />
              <div className="absolute inset-0 rounded-xl animate-ping opacity-20" style={{ background: 'rgba(0,240,255,0.4)' }} />
            </div>
            <span className="font-bold text-xl tracking-tight text-white inline whitespace-nowrap" style={{ textShadow: '0 0 20px rgba(0,240,255,0.4)' }}>
              Elysium <span className="text-forge-500" style={{ textShadow: '0 0 15px rgba(0,240,255,0.6), 0 0 30px rgba(0,240,255,0.3)' }}>Vanguard</span>
            </span>
          </div>

          <div className="flex items-center gap-2 md:gap-4 shrink-0">
            {role === Role.ADMIN && (
              <div className="flex items-center gap-1 md:gap-2 glass-inner p-1 rounded-full border border-white/5">
                <button
                  onClick={() => {
                    analytics.moduleOpened('TV', { role });
                    setIsTVModeOpen(true);
                  }}
                  className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 text-xs font-bold rounded-full transition-all bg-forge-500/10 text-forge-400 hover:bg-forge-500/20 md:mr-2 whitespace-nowrap"
                  title="Abrir Pantalla de Taller (TV)"
                >
                  <Monitor size={14} />
                  <span>TV</span>
                </button>
                <div className="w-px h-4 bg-steel-700 md:mr-2"></div>
                <button
                  onClick={() => {
                    analytics.track(ANALYTICS_EVENTS.DASHBOARD_VIEWED, { role, mode: 'management' });
                    analytics.moduleOpened('Gerencia', { role });
                    setAdminViewMode('DASHBOARD');
                  }}
                  className={`flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 text-xs font-bold rounded-full transition-all whitespace-nowrap ${
                    adminViewMode === 'DASHBOARD' ? 'bg-steel-600 text-white shadow-sm' : 'text-gray-400 hover:text-white'
                  }`}
                >
                  <BarChart3 size={14} />
                  <span>Gerencia</span>
                </button>
                <button
                  onClick={() => {
                    analytics.moduleOpened('Estación', { role });
                    setAdminViewMode('WORKSTATION');
                  }}
                  className={`flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 text-xs font-bold rounded-full transition-all whitespace-nowrap ${
                    adminViewMode === 'WORKSTATION' ? 'bg-forge-500 text-black shadow-sm' : 'text-gray-400 hover:text-white'
                  }`}
                >
                  <Gauge size={14} />
                  <span>Estación</span>
                </button>
              </div>
            )}

            <div className="flex items-center gap-2 md:gap-3 pl-2 md:pl-4 border-l border-white/10 h-8">
              {/* OBD2 Button */}
              <button
                onClick={() => {
                  analytics.track(ANALYTICS_EVENTS.OBD2_SCANNER_OPENED, { role });
                  analytics.moduleOpened('OBD2 Scanner', { role });
                  analytics.funnelStep(ANALYTICS_FUNNELS.scanner.name, 'obd2_scanner_opened', { role });
                  setIsOBD2Open(true);
                }}
                className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 rounded-lg glass-inner text-forge-500 hover:text-forge-400 hover:border-forge-500/50 transition-all text-[10px] font-mono font-bold whitespace-nowrap"
              >
                <AlertTriangle size={12} />
                OBD2 Scanner
              </button>
              {/* Live Link Button */}
              <button
                onClick={() => {
                  analytics.track(ANALYTICS_EVENTS.LIVE_LINK_OPENED, { role });
                  analytics.moduleOpened('Live Link', { role });
                  setIsLiveLinkOpen(true);
                }}
                className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 rounded-lg glass-inner text-green-400 hover:text-green-300 hover:border-green-500/50 transition-all text-[10px] font-mono font-bold whitespace-nowrap"
              >
                <Radio size={12} />
                Live Link
              </button>
              
              {/* ⌘K Search */}
              <button
                onClick={() => {
                  analytics.track(ANALYTICS_EVENTS.SEARCH_PERFORMED, { source: 'command_palette_button', role });
                  setIsPaletteOpen(true);
                }}
                className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 rounded-lg glass-inner text-steel-300 hover:text-white hover:border-forge-500/30 transition-all text-[10px] font-mono whitespace-nowrap"
              >
                <Search size={12} />
                Buscar
                <kbd className="hidden md:inline-block ml-1 px-1.5 py-0.5 rounded bg-steel-700 text-steel-400 text-[9px] border border-steel-500">⌘K</kbd>
              </button>

              <div className="text-right block leading-tight ml-2">
                <div className="text-xs font-bold text-white whitespace-nowrap">{loggedInUser.name}</div>
                <div className="text-[10px] text-forge-500 font-mono tracking-wide uppercase whitespace-nowrap">
                  {role === Role.ADMIN ? 'Administrador' : role === Role.MECHANIC ? 'Mecánico' : 'Cliente'}
                </div>
              </div>

              {(role === Role.MECHANIC || role === Role.ADMIN) && (
                <button
                  onClick={() => {
                    analytics.moduleOpened('Config', { role, source: 'navbar_icon' });
                    setIsSettingsOpen(true);
                  }}
                  className="p-1 md:p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-full transition-colors shrink-0"
                  title="Configuración"
                >
                  <Settings size={18} />
                </button>
              )}

              <button
                onClick={() => setIsProfileModalOpen(true)}
                className="group relative flex items-center gap-1 md:gap-2 rounded-full hover:bg-white/10 transition-all p-0.5 pr-1 shrink-0"
                title="Perfil"
              >
                <div className="relative w-8 h-8 md:w-9 md:h-9 rounded-full bg-steel-600 border-2 border-steel-500 group-hover:border-forge-500 transition-colors shadow-lg overflow-hidden shrink-0">
                  {loggedInUser.avatar ? (
                    <img src={loggedInUser.avatar} alt="Profile" className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center bg-gradient-to-tr from-forge-600 to-forge-400">
                      <User size={16} className="text-black" />
                    </div>
                  )}
                </div>
                <ChevronDown size={14} className="text-gray-500 group-hover:text-white transition-colors mr-1 shrink-0" />
              </button>

              <button
                onClick={handleLogout}
                className="ml-1 p-1 md:p-2 text-gray-400 hover:text-red-500 transition-colors shrink-0"
                title="Cerrar Sesión"
              >
                <LogOut size={18} />
              </button>
            </div>
          </div>
        </nav>

        {/* ── MAIN CONTENT ── */}
        <main className="pt-24 px-4 md:px-6 flex-1 flex flex-col pb-4">
          {/* 🚀 VANGUARD AUTOMOTIVE ECOSYSTEM HUB — High-Tech Neon Navigation */}
          <div className="mb-8 flex flex-wrap gap-2.5 p-3 rounded-2xl relative overflow-hidden" style={{ background: 'linear-gradient(135deg, rgba(3,5,8,0.8) 0%, rgba(7,10,15,0.9) 50%, rgba(3,5,8,0.8) 100%)', border: '1px solid rgba(0,240,255,0.2)', boxShadow: '0 0 40px rgba(0,240,255,0.08), 0 8px 32px rgba(0,0,0,0.4), inset 0 1px 0 rgba(255,255,255,0.05)' }}>
            {/* Animated scan line */}
            <div className="absolute inset-0 pointer-events-none overflow-hidden rounded-2xl">
              <div className="absolute w-full h-px bg-gradient-to-r from-transparent via-forge-500/40 to-transparent" style={{ animation: 'scanDown 4s ease-in-out infinite', top: 0 }} />
            </div>
            {[
              { id: 'DASHBOARD', label: 'Dashboard', icon: <BarChart3 size={15} />, activeColor: 'from-cyan-400 to-cyan-600', activeShadow: '0 0 30px rgba(0,240,255,0.6), 0 0 60px rgba(0,240,255,0.2)', hoverColor: 'hover:text-cyan-400 hover:border-cyan-500/40 hover:shadow-[0_0_20px_rgba(0,240,255,0.25)]' },
              { id: 'GARAGE', label: 'Garage', icon: <Car size={15} />, activeColor: 'from-cyan-400 to-emerald-500', activeShadow: '0 0 30px rgba(6,182,212,0.6), 0 0 60px rgba(6,182,212,0.2)', hoverColor: 'hover:text-cyan-400 hover:border-cyan-500/40 hover:shadow-[0_0_20px_rgba(6,182,212,0.25)]' },
              { id: 'MANUALS', label: 'Manuales RAG', icon: <BookOpen size={15} />, activeColor: 'from-cyan-400 to-indigo-600', activeShadow: '0 0 30px rgba(99,102,241,0.6), 0 0 60px rgba(99,102,241,0.2)', hoverColor: 'hover:text-cyan-400 hover:border-cyan-500/40 hover:shadow-[0_0_20px_rgba(99,102,241,0.25)]' },
              { id: 'TOPOLOGY', label: 'Topología 3D', icon: <Activity size={15} />, activeColor: 'from-cyan-400 to-blue-500', activeShadow: '0 0 30px rgba(34,211,238,0.6), 0 0 60px rgba(34,211,238,0.2)', hoverColor: 'hover:text-cyan-400 hover:border-cyan-500/40 hover:shadow-[0_0_20px_rgba(34,211,238,0.25)]' },
              { id: 'PARTS', label: 'Piezas y Reparaciones', icon: <Wrench size={15} />, activeColor: 'from-orange-400 to-red-500', activeShadow: '0 0 30px rgba(251,146,60,0.6), 0 0 60px rgba(251,146,60,0.2)', hoverColor: 'hover:text-orange-400 hover:border-orange-500/40 hover:shadow-[0_0_20px_rgba(251,146,60,0.25)]' },
              { id: 'HUD_DASHCAM', label: 'Cámara HUD', icon: <Video size={15} />, activeColor: 'from-rose-400 to-pink-600', activeShadow: '0 0 30px rgba(251,113,133,0.6), 0 0 60px rgba(251,113,133,0.2)', hoverColor: 'hover:text-rose-400 hover:border-rose-500/40 hover:shadow-[0_0_20px_rgba(251,113,133,0.25)]' },
              { id: 'CRM', label: 'Taller CRM', icon: <Users size={15} />, activeColor: 'from-blue-400 to-blue-600', activeShadow: '0 0 30px rgba(59,130,246,0.6), 0 0 60px rgba(59,130,246,0.2)', hoverColor: 'hover:text-blue-400 hover:border-blue-500/40 hover:shadow-[0_0_20px_rgba(59,130,246,0.25)]' },
              { id: 'FLEET', label: 'Vanguard Fleet', icon: <Car size={15} />, activeColor: 'from-green-400 to-emerald-600', activeShadow: '0 0 30px rgba(74,222,128,0.6), 0 0 60px rgba(74,222,128,0.2)', hoverColor: 'hover:text-green-400 hover:border-green-500/40 hover:shadow-[0_0_20px_rgba(74,222,128,0.25)]' },
              { id: 'VERIFIED', label: 'B2B Verified', icon: <Wrench size={15} />, activeColor: 'from-yellow-400 to-amber-600', activeShadow: '0 0 30px rgba(250,204,21,0.6), 0 0 60px rgba(250,204,21,0.2)', hoverColor: 'hover:text-yellow-400 hover:border-yellow-500/40 hover:shadow-[0_0_20px_rgba(250,204,21,0.25)]' },
              { id: 'CAMPAIGNS', label: 'Anuncios', icon: <Radio size={15} />, activeColor: 'from-purple-400 to-violet-600', activeShadow: '0 0 30px rgba(192,132,252,0.6), 0 0 60px rgba(192,132,252,0.2)', hoverColor: 'hover:text-purple-400 hover:border-purple-500/40 hover:shadow-[0_0_20px_rgba(192,132,252,0.25)]' },
              { id: 'PAYOUTS', label: 'Pagos', icon: <ClipboardList size={15} />, activeColor: 'from-emerald-400 to-teal-600', activeShadow: '0 0 30px rgba(52,211,153,0.6), 0 0 60px rgba(52,211,153,0.2)', hoverColor: 'hover:text-emerald-400 hover:border-emerald-500/40 hover:shadow-[0_0_20px_rgba(52,211,153,0.25)]' },
              { id: 'SUBSCRIPTIONS', label: 'Planes', icon: <Gauge size={15} />, activeColor: 'from-orange-400 to-amber-600', activeShadow: '0 0 30px rgba(251,146,60,0.6), 0 0 60px rgba(251,146,60,0.2)', hoverColor: 'hover:text-orange-400 hover:border-orange-500/40 hover:shadow-[0_0_20px_rgba(251,146,60,0.25)]' },
              { id: 'GDPR', label: 'Privacidad', icon: <FileText size={15} />, activeColor: 'from-red-400 to-rose-600', activeShadow: '0 0 30px rgba(248,113,113,0.6), 0 0 60px rgba(248,113,113,0.2)', hoverColor: 'hover:text-red-400 hover:border-red-500/40 hover:shadow-[0_0_20px_rgba(248,113,113,0.25)]' },
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => {
                  analytics.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: `tab_${tab.id.toLowerCase()}`, role });
                  setVanguardTab(tab.id as any);
                }}
                className={`flex items-center gap-2.5 px-5 py-3 rounded-xl font-mono font-bold text-xs tracking-wider border transition-all duration-300 transform active:scale-95 ${
                  vanguardTab === tab.id
                    ? `bg-gradient-to-r ${tab.activeColor} text-black border-transparent font-extrabold translate-y-[-2px] scale-[1.05]`
                    : `text-gray-400 border-white/5 ${tab.hoverColor} hover:translate-y-[-1px]`
                }`}
                style={vanguardTab === tab.id ? { boxShadow: tab.activeShadow } : { background: 'rgba(255,255,255,0.03)' }}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* ── ECOSYSTEM VIEW SWITCHER ── */}
          {vanguardTab === 'MANUALS' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-cyan-500/25 shadow-[0_0_30px_rgba(6,182,212,0.1)]">
              <ManualsCenter
                vehicle={vehicles.find(v => v.owner_user_id === loggedInUser.id) || vehicles[0] || null}
                activeDtc={activeDtcFocus}
                onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
                onSelectDtc={(dtc) => setActiveDtcFocus(dtc)}
              />
            </div>
          )}

          {vanguardTab === 'TOPOLOGY' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-cyan-500/25 shadow-[0_0_30px_rgba(6,182,212,0.1)]">
              <VisualDiagnosticsView
                vehicle={vehicles.find(v => v.owner_user_id === loggedInUser.id) || vehicles[0] || null}
                activeDtc={activeDtcFocus || (loggedInUser.scans?.[0]?.dtcCodes?.[0]) || 'P0230'}
                onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
                workOrders={workOrders}
                onAddMaintenanceRecord={(rec) => setMaintenanceRecords(prev => [rec, ...prev])}
                onAddPredictiveAlert={(al) => setPredictiveAlerts(prev => [al, ...prev])}
                onUpdateDigitalTwin={(dt) => setDigitalTwins(prev => {
                  if (prev.some(item => item.vehicle_id === dt.vehicle_id)) {
                    return prev.map(item => item.vehicle_id === dt.vehicle_id ? dt : item);
                  }
                  return [...prev, dt];
                })}
              />
            </div>
          )}

          {vanguardTab === 'PARTS' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-orange-500/25 shadow-[0_0_30px_rgba(251,146,60,0.1)]">
              <PartsRepairsCatalog
                vehicle={vehicles.find(v => v.owner_user_id === loggedInUser.id) || vehicles[0] || null}
                onOpenIn3D={(partId, nodeId) => {
                  setActiveDtcFocus(null);
                  setVanguardTab('TOPOLOGY' as any);
                }}
                onStartRepair={(procedureId) => {
                  setVanguardTab('TOPOLOGY' as any);
                }}
              />
            </div>
          )}

          {vanguardTab === 'HUD_DASHCAM' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-rose-500/25 shadow-[0_0_30px_rgba(251,113,133,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <DashcamHUDLazy
                  vehicles={vehicles}
                  activeUserId={loggedInUser.id}
                  role={role}
                  onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
                  sessions={dashcamSessions}
                  clips={dashcamClips}
                  events={drivingEvents}
                  onAddSession={(s) => setDashcamSessions(prev => [s, ...prev])}
                  onAddClip={(c) => setDashcamClips(prev => [c, ...prev])}
                  onAddEvent={(e) => setDrivingEvents(prev => [e, ...prev])}
                  onDeleteClip={(id) => setDashcamClips(prev => prev.filter(c => c.id !== id))}
                  onToggleLockClip={(id) => setDashcamClips(prev => prev.map(c => c.id === id ? { ...c, locked: !c.locked } : c))}
                />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'GARAGE' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-cyan-500/25 shadow-[0_0_30px_rgba(6,182,212,0.1)]">
              <GarageDashboard
                vehicles={vehicles}
                activeUserId={loggedInUser.id}
                role={role}
                onUpdateVehicle={(v) => {
                  setVehicles(prev => prev.map(item => item.id === v.id ? v : item));
                  // Synchronize back to clients state if necessary
                  setClients(prevClients => prevClients.map(c => {
                    if (c.id === v.owner_user_id) {
                      return {
                        ...c,
                        vehicles: c.vehicles.map(ov => ov.plate === v.plate ? v : ov)
                      };
                    }
                    return c;
                  }));
                }}
                onAddVehicle={(v) => {
                  setVehicles(prev => [...prev, v]);
                  // Also append to client's vehicles list to keep in sync
                  setClients(prevClients => prevClients.map(c => {
                    if (c.id === v.owner_user_id) {
                      return {
                        ...c,
                        vehicles: [...c.vehicles, v]
                      };
                    }
                    return c;
                  }));
                }}
                onDeleteVehicle={(id) => {
                  const toDelete = vehicles.find(item => item.id === id);
                  setVehicles(prev => prev.filter(item => item.id !== id));
                  if (toDelete) {
                    setClients(prevClients => prevClients.map(c => {
                      if (c.id === toDelete.owner_user_id) {
                        return {
                          ...c,
                          vehicles: c.vehicles.filter(ov => ov.plate !== toDelete.plate)
                        };
                      }
                      return c;
                    }));
                  }
                }}
                digitalTwins={digitalTwins}
                onUpdateDigitalTwin={(dt) => setDigitalTwins(prev => {
                  if (prev.some(item => item.vehicle_id === dt.vehicle_id)) {
                    return prev.map(item => item.vehicle_id === dt.vehicle_id ? dt : item);
                  }
                  return [...prev, dt];
                })}
                timelineEvents={timelineEvents}
                onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
                predictiveAlerts={predictiveAlerts}
                onUpdatePredictiveAlert={(al) => setPredictiveAlerts(prev => prev.map(item => item.id === al.id ? al : item))}
                onAddPredictiveAlert={(al) => setPredictiveAlerts(prev => {
                  if (prev.some(item => item.id === al.id)) return prev;
                  return [al, ...prev];
                })}
                maintenanceRecords={maintenanceRecords}
                onAddMaintenanceRecord={(rec) => setMaintenanceRecords(prev => [rec, ...prev])}
                workOrders={workOrders}
                services={services}
                mechanics={mechanics}
                dashcamClips={dashcamClips}
                drivingEvents={drivingEvents}
              />
            </div>
          )}

          {vanguardTab === 'CRM' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-cyan-500/25 shadow-[0_0_30px_rgba(34,211,238,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <WorkshopCRM />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'FLEET' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-green-500/25 shadow-[0_0_30px_rgba(74,222,128,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <FleetDashboard />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'VERIFIED' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-yellow-500/25 shadow-[0_0_30px_rgba(250,204,21,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <VerifiedCompanyPanel />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'CAMPAIGNS' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-purple-500/25 shadow-[0_0_30px_rgba(192,132,252,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <AdCampaignConsole />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'PAYOUTS' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-emerald-500/25 shadow-[0_0_30px_rgba(52,211,153,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <PayoutsView />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'SUBSCRIPTIONS' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-orange-500/25 shadow-[0_0_30px_rgba(251,146,60,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <SubscriptionCheckout />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'GDPR' && (
            <div className="animate-slide-up glass p-6 rounded-2xl border border-red-500/25 shadow-[0_0_30px_rgba(248,113,113,0.1)]">
              <React.Suspense fallback={ecosystemFallback}>
                <GDPRComplianceView />
              </React.Suspense>
            </div>
          )}

          {vanguardTab === 'DASHBOARD' && (
            showDashboard ? (
              <>
                {/* Header Area */}
                <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4 animate-slide-up">
                  <div>
                    <h1 className="text-2xl font-bold tracking-tight flex items-center gap-2" style={{ color: '#00f0ff', textShadow: '0 0 15px rgba(0,240,255,0.4), 0 0 30px rgba(0,240,255,0.15)' }}>
                      {role === Role.MECHANIC ? 'Estación de Trabajo' : 'Centro de Operaciones'}
                    </h1>
                    <p className="text-gray-500 text-sm mt-1 font-mono tracking-wide">
                      {role === Role.MECHANIC
                        ? `Mecánico: ${loggedInUser.name}`
                        : adminViewMode === 'WORKSTATION'
                          ? 'Modo Operativo Activo (Vista de Mecánico)'
                          : 'Gestión integral de taller'
                      }
                    </p>
                  </div>

                  {/* Toolbar */}
                  <div className="flex flex-wrap gap-2.5">
                    {role === Role.ADMIN && adminViewMode === 'DASHBOARD' && (
                      <>
                        <button
                          onClick={() => {
                            analytics.track(ANALYTICS_EVENTS.CATALOG_OPENED, { role });
                            analytics.moduleOpened('Catálogo', { role });
                            setIsCatalogOpen(true);
                          }}
                          className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                          style={{
                            background: 'rgba(0,240,255,0.03)',
                            borderColor: 'rgba(255,255,255,0.06)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(0,240,255,0.3)';
                            e.currentTarget.style.color = '#00f0ff';
                            e.currentTarget.style.boxShadow = '0 0 15px rgba(0,240,255,0.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                            e.currentTarget.style.color = '';
                            e.currentTarget.style.boxShadow = '';
                          }}
                        >
                          <BookOpen size={15} />
                          Catálogo
                        </button>
                        <button
                          onClick={() => {
                            analytics.moduleOpened('Config', { role });
                            setIsSettingsOpen(true);
                          }}
                          className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                          style={{
                            background: 'rgba(0,240,255,0.03)',
                            borderColor: 'rgba(255,255,255,0.06)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(0,240,255,0.3)';
                            e.currentTarget.style.color = '#00f0ff';
                            e.currentTarget.style.boxShadow = '0 0 15px rgba(0,240,255,0.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                            e.currentTarget.style.color = '';
                            e.currentTarget.style.boxShadow = '';
                          }}
                        >
                          <Settings size={15} />
                          Config
                        </button>
                        <button
                          onClick={() => {
                            analytics.track(ANALYTICS_EVENTS.MECHANICS_OPENED, { role });
                            analytics.moduleOpened('Mecánicos', { role });
                            setIsMechanicManagerOpen(true);
                          }}
                          className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                          style={{
                            background: 'rgba(0,240,255,0.03)',
                            borderColor: 'rgba(255,255,255,0.06)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(77,141,255,0.3)';
                            e.currentTarget.style.color = '#4d8dff';
                            e.currentTarget.style.boxShadow = '0 0 15px rgba(77,141,255,0.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                            e.currentTarget.style.color = '';
                            e.currentTarget.style.boxShadow = '';
                          }}
                        >
                          <Users size={15} />
                          Mecánicos
                        </button>
                        <button
                          onClick={() => {
                            analytics.track(ANALYTICS_EVENTS.CLIENTS_OPENED, { role });
                            analytics.moduleOpened('Clientes', { role });
                            setIsClientManagerOpen(true);
                          }}
                          className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                          style={{
                            background: 'rgba(0,240,255,0.03)',
                            borderColor: 'rgba(255,255,255,0.06)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(57,255,20,0.3)';
                            e.currentTarget.style.color = '#39ff14';
                            e.currentTarget.style.boxShadow = '0 0 15px rgba(57,255,20,0.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                            e.currentTarget.style.color = '';
                            e.currentTarget.style.boxShadow = '';
                          }}
                        >
                          <Car size={15} />
                          Clientes
                        </button>
                        <button
                          onClick={() => {
                            analytics.track(ANALYTICS_EVENTS.SERVICES_OPENED, { role });
                            analytics.moduleOpened('Servicios', { role });
                            setIsServiceManagerOpen(true);
                          }}
                          className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                          style={{
                            background: 'rgba(0,240,255,0.03)',
                            borderColor: 'rgba(255,255,255,0.06)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(191,0,255,0.3)';
                            e.currentTarget.style.color = '#bf00ff';
                            e.currentTarget.style.boxShadow = '0 0 15px rgba(191,0,255,0.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                            e.currentTarget.style.color = '';
                            e.currentTarget.style.boxShadow = '';
                          }}
                        >
                          <ClipboardList size={15} />
                          Servicios
                        </button>
                      </>
                    )}
                    {(role === Role.ADMIN || role === Role.MECHANIC) && (
                      <>
                        {role === Role.MECHANIC && (
                          <button
                            onClick={() => {
                              analytics.track(ANALYTICS_EVENTS.CLIENTS_OPENED, { role, source: 'mechanic_toolbar' });
                              analytics.moduleOpened('Clientes', { role, source: 'mechanic_toolbar' });
                              setIsClientManagerOpen(true);
                            }}
                            className="flex items-center gap-2 px-3 py-2 rounded-xl font-mono font-bold text-xs tracking-wider text-gray-300 border transition-all duration-300 transform active:scale-95"
                            style={{
                              background: 'rgba(0,240,255,0.03)',
                              borderColor: 'rgba(255,255,255,0.06)',
                            }}
                            onMouseEnter={(e) => {
                              e.currentTarget.style.borderColor = 'rgba(0,240,255,0.3)';
                              e.currentTarget.style.color = '#00f0ff';
                              e.currentTarget.style.boxShadow = '0 0 15px rgba(0,240,255,0.15)';
                            }}
                            onMouseLeave={(e) => {
                              e.currentTarget.style.borderColor = 'rgba(255,255,255,0.06)';
                              e.currentTarget.style.color = '';
                              e.currentTarget.style.boxShadow = '';
                            }}
                          >
                            <Car size={15} />
                            Gestión de Clientes
                          </button>
                        )}
                        <button
                          onClick={() => openBooking('toolbar')}
                          className="flex items-center gap-2 text-black px-4 py-2.5 rounded-xl font-mono font-black text-xs tracking-[2px] uppercase transition-all duration-300 transform hover:scale-105 active:scale-95 shadow-[0_0_20px_rgba(0,240,255,0.3)] hover:shadow-[0_0_30px_rgba(0,240,255,0.5)]"
                          style={{
                            background: 'linear-gradient(135deg, #00f0ff 0%, #00c2cf 50%, #00f0ff 100%)',
                          }}
                        >
                          <Plus size={15} strokeWidth={3.5} />
                          Nueva Orden
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {/* Dashboard Content */}
                <PlatformCommandCenter
                  role={role}
                  adminViewMode={adminViewMode}
                  metrics={metrics}
                  workOrders={workOrders}
                  clients={clients}
                  mechanics={mechanics}
                  services={services}
                  currentDate={currentDate}
                  onNewOrder={() => openBooking('command_center')}
                  onOpenOBD2={() => setIsOBD2Open(true)}
                  onOpenLiveLink={() => setIsLiveLinkOpen(true)}
                  onOpenCatalog={() => setIsCatalogOpen(true)}
                  onOpenClients={() => setIsClientManagerOpen(true)}
                  onSetVanguardTab={(tab) => setVanguardTab(tab)}
                />

                {(role === Role.MECHANIC || (role === Role.ADMIN && adminViewMode === 'WORKSTATION')) ? (
                  <MechanicDashboard
                    mechanicId={role === Role.MECHANIC ? loggedInUser.id : 'm1'}
                    currentMechanic={currentMechanic || undefined}
                    mechanics={mechanics}
                    workOrders={visibleWorkOrders}
                    services={services}
                    onStatusChange={handleStatusChange}
                    onUpdateMechanic={handleUpdateMechanic}
                    openHour={openHour}
                    closeHour={closeHour}
                  />
                ) : (
                  <>
                    <MetricsPanel
                      metrics={metrics}
                      workOrders={visibleWorkOrders}
                      currentDate={currentDate}
                      services={services}
                      openHour={openHour}
                      closeHour={closeHour}
                    />
                    {/* Analytics Charts */}
                    <AnalyticsPanel
                      workOrders={workOrders}
                      mechanics={mechanics}
                      services={services}
                    />
                  </>
                )}

                {/* Timeline */}
                <div className="stat-card mt-6 relative overflow-visible">
                  <Timeline
                    mechanics={visibleMechanics}
                    workOrders={visibleWorkOrders}
                    services={services}
                    currentDate={currentDate}
                    openHour={openHour}
                    closeHour={closeHour}
                    timeSliceMinutes={timeSliceMinutes}
                    onStatusChange={handleStatusChange}
                    onDateChange={setCurrentDate}
                    onEditWorkOrder={setEditingWorkOrder}
                  />
                </div>
              </>
            ) : (
              // CLIENT VIEW
              <ClientDashboard
                currentUser={loggedInUser}
                workOrders={workOrders}
                services={services}
                mechanics={mechanics}
                freeWashThreshold={freeWashThreshold}
                onBookNew={() => openBooking('client_dashboard')}
                onCancelOrder={(id) => handleCancelWorkOrder(id, 'Cancelada por el cliente')}
                onUpdateUser={handleUpdateClient}
                onSimulateAPKScan={handleSimulateAPKScan}
                vehicles={vehicles}
                onUpdateVehicle={(v) => {
                  setVehicles(prev => prev.map(item => item.id === v.id ? v : item));
                  setClients(prevClients => prevClients.map(c => {
                    if (c.id === v.owner_user_id) {
                      return {
                        ...c,
                        vehicles: c.vehicles.map(ov => ov.plate === v.plate ? v : ov)
                      };
                    }
                    return c;
                  }));
                }}
                onAddVehicle={(v) => {
                  setVehicles(prev => [...prev, v]);
                  setClients(prevClients => prevClients.map(c => {
                    if (c.id === v.owner_user_id) {
                      return {
                        ...c,
                        vehicles: [...c.vehicles, v]
                      };
                    }
                    return c;
                  }));
                }}
                onDeleteVehicle={(id) => {
                  const toDelete = vehicles.find(item => item.id === id);
                  setVehicles(prev => prev.filter(item => item.id !== id));
                  if (toDelete) {
                    setClients(prevClients => prevClients.map(c => {
                      if (c.id === toDelete.owner_user_id) {
                        return {
                          ...c,
                          vehicles: c.vehicles.filter(ov => ov.plate !== toDelete.plate)
                        };
                      }
                      return c;
                    }));
                  }
                }}
                digitalTwins={digitalTwins}
                onUpdateDigitalTwin={(dt) => setDigitalTwins(prev => {
                  if (prev.some(item => item.vehicle_id === dt.vehicle_id)) {
                    return prev.map(item => item.vehicle_id === dt.vehicle_id ? dt : item);
                  }
                  return [...prev, dt];
                })}
                timelineEvents={timelineEvents}
                onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
                predictiveAlerts={predictiveAlerts}
                onUpdatePredictiveAlert={(al) => setPredictiveAlerts(prev => prev.map(item => item.id === al.id ? al : item))}
                onAddPredictiveAlert={(al) => setPredictiveAlerts(prev => {
                  if (prev.some(item => item.id === al.id)) return prev;
                  return [al, ...prev];
                })}
                maintenanceRecords={maintenanceRecords}
                onAddMaintenanceRecord={(rec) => setMaintenanceRecords(prev => [rec, ...prev])}
              />
            )
          )}
        </main>

        {/* Footer */}
        <footer className="text-center py-6 relative">
          <div className="mx-auto mb-3 h-px w-48" style={{ background: 'linear-gradient(90deg, transparent, rgba(0,240,255,0.2), transparent)' }} />
          <p className="font-mono text-[10px] tracking-[4px] uppercase" style={{ color: 'rgba(0,240,255,0.2)' }}>
            Elysium Vanguard — Vanguard Network © {new Date().getFullYear()}
          </p>
        </footer>
      </div>

      {/* ── MODALS ── */}

      {/* Booking Modal (For Admin, Mechanic, and Client) */}
      {isBookingModalOpen && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-[60] flex items-center justify-center p-4 animate-slide-up">
          <div className="w-full max-w-lg glass rounded-xl">
            <WorkOrderWizard
              mechanics={mechanics}
              services={services}
              clients={clients}
              existingOrders={workOrders}
              shopRules={shopRules}
              openHour={openHour}
              closeHour={closeHour}
              timeSliceMinutes={timeSliceMinutes}
              freeWashThreshold={freeWashThreshold}
              currentUser={loggedInUser}
              currentRole={role}
              onBook={handleBook}
              onCancel={() => closeBooking('modal_cancel')}
              onCreateClient={handleCreateClient}
              onUpdateClient={handleUpdateClient}
              onDeleteClient={handleDeleteClient}
            />
          </div>
        </div>
      )}

      {/* Service Manager */}
      {role === Role.ADMIN && isServiceManagerOpen && (
        <ServiceManager
          services={services}
          onAdd={handleAddService}
          onUpdate={handleUpdateService}
          onDelete={handleDeleteService}
          onClose={() => setIsServiceManagerOpen(false)}
        />
      )}

      {/* Mechanic Manager */}
      {role === Role.ADMIN && isMechanicManagerOpen && (
        <MechanicManager
          mechanics={mechanics}
          onAdd={handleAddMechanic}
          onUpdate={handleUpdateMechanic}
          onDelete={handleDeleteMechanic}
          onClose={() => setIsMechanicManagerOpen(false)}
        />
      )}

      {/* Client Manager (Admin & Mechanic) */}
      {(role === Role.ADMIN || role === Role.MECHANIC) && isClientManagerOpen && (
        <ClientManager
          clients={clients}
          onAdd={handleCreateClient}
          onUpdate={handleUpdateClient}
          onDelete={handleDeleteClient}
          onClose={() => setIsClientManagerOpen(false)}
        />
      )}

      {/* Shop Settings */}
      {(role === Role.ADMIN || role === Role.MECHANIC) && isSettingsOpen && (
        <ShopSettings
          currentRules={shopRules}
          currentOpenHour={openHour}
          currentCloseHour={closeHour}
          currentTimeSlice={timeSliceMinutes}
          currentFreeWashThreshold={freeWashThreshold}
          currentAnalyticsConsent={analyticsConsent}
          onAnalyticsConsentChange={handleAnalyticsConsentChange}
          onSave={handleUpdateSettings}
          onClose={() => setIsSettingsOpen(false)}
        />
      )}

      {/* Service Catalog Modal */}
      {isCatalogOpen && (
        <ServiceCatalogView
          catalog={catalog}
          onUpdateCatalog={setCatalog}
          role={role}
          onClose={() => setIsCatalogOpen(false)}
        />
      )}

	      {/* Live Link Dashboard */}
	      {isLiveLinkOpen && (
	        <React.Suspense fallback={lazyModalFallback}>
	          <LiveLinkDashboard onClose={() => {
                analytics.moduleExited('Live Link', { role });
                setIsLiveLinkOpen(false);
              }} />
	        </React.Suspense>
	      )}

	      {/* OBD2 Scanner Modal */}
	      {isOBD2Open && (
	        <React.Suspense fallback={lazyModalFallback}>
	          <OBD2Scanner
	            onClose={() => {
                  analytics.moduleExited('OBD2 Scanner', { role });
                  analytics.funnelAbandoned(ANALYTICS_FUNNELS.scanner.name, 'obd2_scanner_opened', {
                    reason_if_known: 'modal_closed',
                  });
                  setIsOBD2Open(false);
                }}
	            currentUser={loggedInUser}
	            workOrders={workOrders}
	            onSaveMeasurement={handleSaveOscilloscopeMeasurement}
	            onUpdateWorkOrder={handleUpdateWorkOrderDetails}
	            onAddTimelineEvent={(ev) => setTimelineEvents(prev => [ev, ...prev])}
	            onNavigateToManuals={(dtcCode) => {
	              if (dtcCode) setActiveDtcFocus(dtcCode);
	              setIsOBD2Open(false);
	              setVanguardTab('MANUALS');
	            }}
	          />
	        </React.Suspense>
	      )}

      {/* TV Dashboard Mode */}
      {isTVModeOpen && (
        <TVDashboard
          workOrders={workOrders}
          mechanics={mechanics}
          services={services}
          onClose={() => setIsTVModeOpen(false)}
        />
      )}

      {/* Work Order Editor */}
      {editingWorkOrder && (
        <WorkOrderEditor
          workOrder={editingWorkOrder}
          allWorkOrders={workOrders}
          serviceName={services.find(s => s.id === editingWorkOrder.serviceId)?.name || 'Servicio'}
          onClose={() => setEditingWorkOrder(null)}
          onSave={handleUpdateWorkOrder}
        />
      )}

      {/* Command Palette (⌘K) */}
      <CommandPalette
        isOpen={isPaletteOpen}
        onClose={() => setIsPaletteOpen(false)}
        workOrders={workOrders}
        clients={clients}
        mechanics={mechanics}
        services={services}
        onSelectWorkOrder={(wo) => setEditingWorkOrder(wo)}
          onNavigate={(action) => {
            if (action === '__open_palette') setIsPaletteOpen(true);
          else if (action === 'new_order') openBooking('command_palette');
          else if (action === 'catalog') {
            analytics.track(ANALYTICS_EVENTS.CATALOG_OPENED, { role, source: 'command_palette' });
            analytics.moduleOpened('Catálogo', { role, source: 'command_palette' });
            setIsCatalogOpen(true);
          }
        }}
      />

      {/* Work Order Receipt */}
      {receiptWorkOrder && (
        <WorkOrderReceipt
          workOrder={receiptWorkOrder}
          service={services.find(s => s.id === receiptWorkOrder.serviceId)}
          mechanic={mechanics.find(m => m.id === receiptWorkOrder.mechanicId)}
          client={clients.find(c => c.id === receiptWorkOrder.clientId)}
          freeWashThreshold={freeWashThreshold}
          onClose={() => setReceiptWorkOrder(null)}
        />
      )}
      {/* Profile Modal */}
      {isProfileModalOpen && (
        <UserProfileModal
          user={loggedInUser}
          role={role}
          onClose={() => setIsProfileModalOpen(false)}
          onUpdateUser={handleUpdateCurrentUser}
        />
      )}
    </div>
  );
}
