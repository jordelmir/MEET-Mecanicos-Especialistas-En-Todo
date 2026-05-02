
import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { INITIAL_WORK_ORDERS, MECHANICS as INITIAL_MECHANICS, SERVICES as DEFAULT_SERVICES, DEFAULT_OPEN_HOUR, DEFAULT_CLOSE_HOUR, INITIAL_CLIENTS, MOCK_ADMIN_USER, SERVICE_CATALOG } from './constants';
import { WorkOrder, Role, WorkOrderStatus, Metrics, Client, ServiceHistoryItem, Service, Mechanic, VehicleInfo } from './types';
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
import { calculateEndTime, getStatusLabel } from './services/timeEngine';
import { saveState, loadState } from './services/storage';
import { ClientDashboard } from './components/ClientDashboard';
import { UserProfileModal } from './components/UserProfileModal';
import { TVDashboard } from './components/TVDashboard';
import { OBD2Scanner } from './components/OBD2Scanner';
import { Wrench, User, Plus, Settings, Users, ChevronDown, LogOut, Gauge, BarChart3, Car, BookOpen, ClipboardList, Search, FileText, Monitor, AlertTriangle } from 'lucide-react';

export default function App() {
  // ── AUTH STATE ──
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [role, setRole] = useState<Role>(Role.ADMIN);
  const [loggedInUser, setLoggedInUser] = useState<Client>(MOCK_ADMIN_USER);

  const { toast } = useToast();

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

  // ── UI STATE ──
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);
  const [isServiceManagerOpen, setIsServiceManagerOpen] = useState(false);
  const [isMechanicManagerOpen, setIsMechanicManagerOpen] = useState(false);
  const [isClientManagerOpen, setIsClientManagerOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isCatalogOpen, setIsCatalogOpen] = useState(false);
  const [editingWorkOrder, setEditingWorkOrder] = useState<WorkOrder | null>(null);
  const [adminViewMode, setAdminViewMode] = useState<'DASHBOARD' | 'WORKSTATION'>('DASHBOARD');
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [isPaletteOpen, setIsPaletteOpen] = useState(false);
  const [receiptWorkOrder, setReceiptWorkOrder] = useState<WorkOrder | null>(null);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isTVModeOpen, setIsTVModeOpen] = useState(false);
  const [isOBD2Open, setIsOBD2Open] = useState(false);

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
    toast('success', 'Cuenta Creada', `Bienvenido a MEET, ${newClient.name.split(' ')[0]}`);
  };

  const handleLogout = () => {
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
      revenue += wo.price;
      if (wo.status === WorkOrderStatus.COMPLETED || wo.status === WorkOrderStatus.DELIVERED) completedCount++;
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
            id: `hist-${Math.random().toString(36).substr(2, 9)}`,
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
      id: `c${Date.now()}`, ...clientData,
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
    setClients(prev => prev.filter(c => c.id !== clientId));
    setWorkOrders(prev => prev.filter(wo => wo.clientId !== clientId));
    toast('info', 'Cliente Eliminado');
  };

  const handleAddService = (serviceData: Omit<Service, 'id'>) => {
    setServices(prev => [...prev, { id: `s-${Date.now()}`, ...serviceData }]);
    toast('success', 'Servicio Agregado');
  };
  const handleUpdateService = (svc: Service) => {
    setServices(prev => prev.map(s => s.id === svc.id ? svc : s));
    toast('success', 'Servicio Actualizado');
  };
  const handleDeleteService = (id: string) => {
    setServices(prev => prev.filter(s => s.id !== id));
    toast('info', 'Servicio Eliminado');
  };

  const handleAddMechanic = (data: Omit<Mechanic, 'id'>) => {
    setMechanics(prev => [...prev, { id: `m-${Date.now()}`, ...data }]);
    toast('success', 'Mecánico Registrado');
  };
  const handleUpdateMechanic = (mech: Mechanic) => {
    setMechanics(prev => prev.map(m => m.id === mech.id ? mech : m));
    if (loggedInUser.id === mech.id) setLoggedInUser(prev => ({ ...prev, avatar: mech.avatar, name: mech.name }));
    toast('success', 'Mecánico Actualizado');
  };
  const handleDeleteMechanic = (id: string) => {
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
  const handleBook = (clientId: string, clientName: string, mechanicId: string, serviceId: string, time: Date, vehicle: VehicleInfo) => {
    const mech = mechanics.find(m => m.id === mechanicId)!;
    const service = services.find(s => s.id === serviceId)!;

    const realDuration = Math.ceil(service.estimatedMinutes / mech.efficiencyFactor);
    const endTime = calculateEndTime(time, service.estimatedMinutes, mech.efficiencyFactor);

    const newOrder: WorkOrder = {
      id: `wo-${Date.now()}`,
      clientId, clientName, mechanicId, serviceId,
      vehicleInfo: vehicle,
      startTime: time, estimatedEndTime: endTime,
      status: WorkOrderStatus.RECEIVED,
      price: service.basePrice,
      estimatedMinutes: realDuration,
    };

    setWorkOrders(prev => [...prev, newOrder]);
    setIsBookingModalOpen(false);
    setCurrentDate(time);
    toast('success', 'Orden Creada', `Para ${vehicle.plate} a las ${time.toLocaleTimeString([],{hour: '2-digit', minute:'2-digit'})}`);
  };

  const handleSimulateAPKScan = (scanResult: any) => {
    const updatedClient = {
      ...loggedInUser,
      scans: [scanResult, ...(loggedInUser.scans || [])]
    };
    handleUpdateClient(updatedClient);
    toast('success', 'Escaneo Recibido', 'Datos de OBD2 sincronizados desde la App MEET');
  };

  // ── RENDER ──
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
        <nav className="glass h-16 sm:h-20 md:h-16 fixed top-0 w-full z-50 flex flex-nowrap items-center justify-start md:justify-between px-3 md:px-6 shadow-lg overflow-x-auto hide-scrollbar gap-4 md:gap-0">
          <div className="flex items-center gap-3 shrink-0 mr-auto md:mr-0">
            <div className="bg-forge-500 p-1.5 rounded-lg text-black shadow-[0_0_15px_rgba(0, 240, 255,0.4)]">
              <Wrench size={20} strokeWidth={2.5} />
            </div>
            <span className="font-bold text-xl tracking-tight text-white inline whitespace-nowrap">
              ME<span className="text-forge-500">ET</span>
            </span>
          </div>

          <div className="flex items-center gap-2 md:gap-4 shrink-0">
            {role === Role.ADMIN && (
              <div className="flex items-center gap-1 md:gap-2 glass-inner p-1 rounded-full border border-white/5">
                <button
                  onClick={() => setIsTVModeOpen(true)}
                  className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 text-xs font-bold rounded-full transition-all bg-forge-500/10 text-forge-400 hover:bg-forge-500/20 md:mr-2 whitespace-nowrap"
                  title="Abrir Pantalla de Taller (TV)"
                >
                  <Monitor size={14} />
                  <span>TV</span>
                </button>
                <div className="w-px h-4 bg-steel-700 md:mr-2"></div>
                <button
                  onClick={() => setAdminViewMode('DASHBOARD')}
                  className={`flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 text-xs font-bold rounded-full transition-all whitespace-nowrap ${
                    adminViewMode === 'DASHBOARD' ? 'bg-steel-600 text-white shadow-sm' : 'text-gray-400 hover:text-white'
                  }`}
                >
                  <BarChart3 size={14} />
                  <span>Gerencia</span>
                </button>
                <button
                  onClick={() => setAdminViewMode('WORKSTATION')}
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
                onClick={() => setIsOBD2Open(true)}
                className="flex items-center gap-1 md:gap-2 px-2 md:px-3 py-1.5 rounded-lg glass-inner text-forge-500 hover:text-forge-400 hover:border-forge-500/50 transition-all text-[10px] font-mono font-bold whitespace-nowrap"
              >
                <AlertTriangle size={12} />
                OBD2 Scanner
              </button>
              
              {/* ⌘K Search */}
              <button
                onClick={() => setIsPaletteOpen(true)}
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
                  onClick={() => setIsSettingsOpen(true)}
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
          {showDashboard ? (
            <>
              {/* Header Area */}
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4 animate-slide-up">
                <div>
                  <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
                    {role === Role.MECHANIC ? 'Estación de Trabajo' : 'Centro de Operaciones'}
                  </h1>
                  <p className="text-gray-400 text-sm mt-1 font-medium">
                    {role === Role.MECHANIC
                      ? `Mecánico: ${loggedInUser.name}`
                      : adminViewMode === 'WORKSTATION'
                        ? 'Modo Operativo Activo (Vista de Mecánico)'
                        : 'Gestión integral de taller'
                    }
                  </p>
                </div>

                {/* Toolbar */}
                <div className="flex flex-wrap gap-2">
                  {role === Role.ADMIN && adminViewMode === 'DASHBOARD' && (
                    <>
                      <button
                        onClick={() => setIsCatalogOpen(true)}
                        className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                      >
                        <BookOpen size={16} />
                        Catálogo
                      </button>
                      <button
                        onClick={() => setIsSettingsOpen(true)}
                        className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                      >
                        <Settings size={16} />
                        Config
                      </button>
                      <button
                        onClick={() => setIsMechanicManagerOpen(true)}
                        className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                      >
                        <Users size={16} />
                        Mecánicos
                      </button>
                      <button
                        onClick={() => setIsClientManagerOpen(true)}
                        className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                      >
                        <Car size={16} />
                        Clientes
                      </button>
                      <button
                        onClick={() => setIsServiceManagerOpen(true)}
                        className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                      >
                        <ClipboardList size={16} />
                        Servicios
                      </button>
                    </>
                  )}
                  {(role === Role.ADMIN || role === Role.MECHANIC) && (
                    <>
                      {role === Role.MECHANIC && (
                        <button
                          onClick={() => setIsClientManagerOpen(true)}
                          className="flex items-center gap-2 glass-inner text-gray-300 px-3 py-2 rounded-lg font-bold text-xs hover:bg-white/10 hover:text-white border border-white/5 transition-all"
                        >
                          <Car size={16} />
                          Gestión de Clientes
                        </button>
                      )}
                      <button
                        onClick={() => setIsBookingModalOpen(true)}
                        className="flex items-center gap-2 bg-forge-500 text-black px-4 py-2 rounded-lg font-bold text-xs hover:bg-forge-400 shadow-[0_4px_20px_-5px_rgba(0, 240, 255,0.4)] transition-all transform hover:scale-105"
                      >
                        <Plus size={16} strokeWidth={3} />
                        Nueva Orden
                      </button>
                    </>
                  )}
                </div>
              </div>

              {/* Dashboard Content */}
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
              <div className="glass rounded-xl shadow-2xl flex flex-col relative overflow-visible mt-6">
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
              onBookNew={() => setIsBookingModalOpen(true)}
              onCancelOrder={(id) => handleCancelWorkOrder(id, 'Cancelada por el cliente')}
              onUpdateUser={handleUpdateClient}
              onSimulateAPKScan={handleSimulateAPKScan}
            />
          )}
        </main>

        {/* Footer */}
        <footer className="text-center py-4 font-mono text-[10px] text-steel-400 tracking-wider border-t border-white/5">
          MEET — MECÁNICOS ESPECIALISTAS EN TODO © {new Date().getFullYear()}
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
              onCancel={() => setIsBookingModalOpen(false)}
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

      {/* OBD2 Scanner Modal */}
      {isOBD2Open && (
        <OBD2Scanner onClose={() => setIsOBD2Open(false)} />
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
          else if (action === 'new_order') setIsBookingModalOpen(true);
          else if (action === 'catalog') setIsCatalogOpen(true);
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