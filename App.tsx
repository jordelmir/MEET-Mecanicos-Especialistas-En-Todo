
import React, { useState, useMemo } from 'react';
import { INITIAL_APPOINTMENTS, BARBERS as INITIAL_BARBERS, SERVICES as DEFAULT_SERVICES, DEFAULT_OPEN_HOUR, DEFAULT_CLOSE_HOUR, INITIAL_CLIENTS, MOCK_ADMIN_USER, DEFAULT_STYLE_OPTIONS } from './constants';
import { Appointment, Role, AppointmentStatus, Metrics, Client, BookingHistoryItem, Service, CutPreferences, Barber, GlobalStyleOptions } from './types';
import { Timeline } from './components/Timeline';
import { MetricsPanel } from './components/MetricsPanel';
import { BarberDashboard } from './components/BarberDashboard'; 
import { BookingWizard } from './components/BookingWizard';
import { ServiceManager } from './components/ServiceManager';
import { BarberManager } from './components/BarberManager';
import { ClientManager } from './components/ClientManager';
import { AppointmentEditor } from './components/AppointmentEditor';
import { UserProfile } from './components/UserProfile';
import { ShopRulesEditor } from './components/ShopRulesEditor';
import { StyleOptionsEditor } from './components/StyleOptionsEditor';
import { LoginPage } from './components/LoginPage'; 
import { MatrixBackground } from './components/MatrixBackground'; // Visual Upgrade
import { calculateEndTime, canClientCancel } from './services/timeEngine';
import { CancellationAnalysis } from './components/CancellationAnalysis'; // New Component
import { Scissors, User, LayoutDashboard, Menu, Plus, Settings, FileText, Users, ChevronDown, Bell, LogOut, Briefcase, Lock, Tag, Gauge, BarChart3, AlertCircle } from 'lucide-react';

export default function App() {
  // Auth State
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [role, setRole] = useState<Role>(Role.ADMIN);
  const [loggedInUser, setLoggedInUser] = useState<Client>(MOCK_ADMIN_USER);

  // Data State
  const [appointments, setAppointments] = useState<Appointment[]>(INITIAL_APPOINTMENTS);
  const [clients, setClients] = useState<Client[]>(INITIAL_CLIENTS);
  const [services, setServices] = useState<Service[]>(DEFAULT_SERVICES);
  const [barbers, setBarbers] = useState<Barber[]>(INITIAL_BARBERS);
  const [styleOptions, setStyleOptions] = useState<GlobalStyleOptions>(DEFAULT_STYLE_OPTIONS);
  
  // Shop Settings State (Dynamic)
  const [shopRules, setShopRules] = useState<string>("1. Venir con el cabello lavado y sin gel.\n2. Llegar 5 minutos antes de la cita.\n3. Avisar cancelaciones con antelación.");
  const [openHour, setOpenHour] = useState<number>(DEFAULT_OPEN_HOUR);
  const [closeHour, setCloseHour] = useState<number>(DEFAULT_CLOSE_HOUR);
  // NEW: Dynamic Time Slice State
  const [timeSliceMinutes, setTimeSliceMinutes] = useState<number>(30); // Default to 30 mins as requested

  // UI State
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);
  const [isServiceManagerOpen, setIsServiceManagerOpen] = useState(false);
  const [isBarberManagerOpen, setIsBarberManagerOpen] = useState(false);
  const [isClientManagerOpen, setIsClientManagerOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isShopRulesOpen, setIsShopRulesOpen] = useState(false);
  const [isStyleEditorOpen, setIsStyleEditorOpen] = useState(false);
  const [isCancellationReportOpen, setIsCancellationReportOpen] = useState(false); // New state for report
  const [editingAppointment, setEditingAppointment] = useState<Appointment | null>(null);
  
  // ADMIN SPECIFIC STATE: View Mode (Dashboard vs Workstation)
  const [adminViewMode, setAdminViewMode] = useState<'DASHBOARD' | 'WORKSTATION'>('DASHBOARD');

  const [currentDate, setCurrentDate] = useState<Date>(new Date());

  // --- Derived State for Access Control ---
  
  // If role is BARBER, they only see themselves.
  // If role is ADMIN, they see ALL, UNLESS they are in "Workstation Mode" where they might want to focus on their own queue (or a specific barber).
  const visibleBarbers = useMemo(() => {
    if (role === Role.BARBER) {
      return barbers.filter(b => b.id === loggedInUser.id);
    }
    return barbers;
  }, [barbers, role, loggedInUser.id]);

  // If role is BARBER, they only see their appointments.
  // Admin sees all.
  const visibleAppointments = useMemo(() => {
      if (role === Role.BARBER) {
          return appointments.filter(a => a.barberId === loggedInUser.id);
      }
      return appointments;
  }, [appointments, role, loggedInUser.id]);


  // --- Login Handler (Secure) ---
  const handleLogin = async (identity: string, code: string) => {
      setAuthError(null);

      // 1. Check Admin
      if (
          (identity === MOCK_ADMIN_USER.identification || identity === MOCK_ADMIN_USER.email) && 
          code === MOCK_ADMIN_USER.accessCode
      ) {
          setRole(Role.ADMIN);
          setLoggedInUser(MOCK_ADMIN_USER);
          setIsAuthenticated(true);
          return;
      }

      // 2. Check Barbers
      const barberFound = barbers.find(b => 
          (b.identification === identity || b.email === identity) && b.accessCode === code
      );

      if (barberFound) {
          setRole(Role.BARBER); // CORRECT: Set specific Barber Role
          
          // Create a "Client-like" object for the barber to satisfy the User interface
          const barberAsUser: Client = {
              id: barberFound.id,
              name: barberFound.name,
              phone: '',
              email: barberFound.email,
              identification: barberFound.identification,
              accessCode: barberFound.accessCode,
              bookingHistory: [],
              joinDate: new Date(),
              points: 0,
              avatar: barberFound.avatar,
              notes: `Staff: ${barberFound.tier}`
          };
          setLoggedInUser(barberAsUser);
          setIsAuthenticated(true);
          return;
      }

      // 3. Check Clients
      const clientFound = clients.find(c => 
          (c.identification === identity || c.email === identity) && c.accessCode === code
      );

      if (clientFound) {
          setRole(Role.CLIENT);
          setLoggedInUser(clientFound);
          setIsAuthenticated(true);
          return;
      }

      setAuthError("Credenciales inválidas. Verifica tu Cédula/Email y Código.");
  };

  const handleLogout = () => {
      setIsAuthenticated(false);
      setRole(Role.ADMIN); // Reset to default for safety
      setAuthError(null);
      setAdminViewMode('DASHBOARD'); // Reset admin view
  };

  // Calculate Metrics Real-time (Scoped by visibleAppointments)
  const metrics: Metrics = useMemo(() => {
    // Only calculate for the currently selected day
    const todaysAppointments = visibleAppointments.filter(a => 
        a.startTime.getDate() === currentDate.getDate() &&
        a.status !== AppointmentStatus.CANCELLED
    );

    // Use visibleBarbers length for capacity
    const totalMinutesAvailable = (closeHour - openHour) * 60 * visibleBarbers.length;
    
    let bookedMinutes = 0;
    let completedCount = 0;
    let revenue = 0;

    todaysAppointments.forEach(apt => {
        const duration = (apt.expectedEndTime.getTime() - apt.startTime.getTime()) / 60000;
        bookedMinutes += duration;
        revenue += apt.price;
        if (apt.status === AppointmentStatus.COMPLETED) completedCount++;
    });

    const deadTime = Math.max(0, totalMinutesAvailable - bookedMinutes);

    return {
      dailyOccupancy: totalMinutesAvailable > 0 ? Math.round((bookedMinutes / totalMinutesAvailable) * 100) : 0,
      deadTimeMinutes: Math.round(deadTime),
      revenue,
      appointmentsCompleted: completedCount,
      appointmentsTotal: todaysAppointments.length
    };
  }, [visibleAppointments, currentDate, visibleBarbers.length, openHour, closeHour]);

  const handleStatusChange = (id: string, newStatus: AppointmentStatus) => {
    setAppointments(prev => prev.map(a => {
      if (a.id !== id) return a;
      
      const updates: any = { status: newStatus };

      // TIME TRACKING LOGIC
      if (newStatus === AppointmentStatus.IN_PROGRESS) {
          updates.actualStartTime = new Date(); // Start the stopwatch
      } else if (newStatus === AppointmentStatus.COMPLETED) {
          updates.actualEndTime = new Date(); // Stop the stopwatch
          
          // Calculate actual duration in minutes
          if (a.actualStartTime) {
              const diffMs = updates.actualEndTime.getTime() - a.actualStartTime.getTime();
              updates.durationMinutes = Math.round(diffMs / 60000); // Update record with REAL duration
          }
      }

      return { ...a, ...updates };
    }));

    // Update Client Stats if appointment is completed
    if (newStatus === AppointmentStatus.COMPLETED) {
        const apt = appointments.find(a => a.id === id);
        
        if (apt && apt.status !== AppointmentStatus.COMPLETED) {
            const service = services.find(s => s.id === apt.serviceId);
            const barber = barbers.find(b => b.id === apt.barberId);

            if (service && barber) {
                const historyItem: BookingHistoryItem = {
                    id: `hist-${Math.random().toString(36).substr(2, 9)}`,
                    date: apt.startTime,
                    serviceName: service.name,
                    barberName: barber.name,
                    price: apt.price,
                    notes: apt.notes ? `Nota de cita: ${apt.notes}` : undefined
                };

                setClients(prevClients => prevClients.map(c => 
                    c.id === apt.clientId 
                    ? { 
                        ...c, 
                        bookingHistory: [historyItem, ...c.bookingHistory], 
                        lastVisit: new Date(),
                        points: c.points + 1
                      }
                    : c
                ));
            }
        }
    }
  };

  const handleUpdateAppointment = (id: string, updates: { price: number; durationMinutes: number; startTime?: Date }) => {
    setAppointments(prev => prev.map(apt => {
      if (apt.id !== id) return apt;
      
      const startToUse = updates.startTime || apt.startTime;
      const newEndTime = new Date(startToUse.getTime() + updates.durationMinutes * 60000);
      
      return {
        ...apt,
        price: updates.price,
        durationMinutes: updates.durationMinutes,
        startTime: startToUse,
        expectedEndTime: newEndTime
      };
    }));
    setEditingAppointment(null);
  };

  // --- CANCELLATION HANDLER ---
  const handleCancelAppointment = (appointmentId: string, reason?: string) => {
      const apt = appointments.find(a => a.id === appointmentId);
      if (!apt) return;

      // Ensure reason is captured
      const updates: any = { 
          status: AppointmentStatus.CANCELLED,
          cancellationReason: reason || 'Cancelada por usuario',
          cancellationDate: new Date()
      };

      setAppointments(prev => prev.map(a => a.id === appointmentId ? { ...a, ...updates } : a));
  };

  // --- CRUD Handlers (Simplified) ---
  const handleCreateClient = (clientData: Omit<Client, 'id' | 'bookingHistory' | 'points' | 'joinDate'>): Client => {
      const newClient: Client = {
          id: `c${clients.length + 1}`,
          ...clientData,
          bookingHistory: [],
          points: 0,
          joinDate: new Date()
      };
      setClients(prev => [...prev, newClient]);
      return newClient;
  };
  
  const handleUpdateClient = (updatedClient: Client) => {
      setClients(prev => prev.map(c => c.id === updatedClient.id ? updatedClient : c));
      // Sync Session if needed
      if (loggedInUser.id === updatedClient.id) {
          setLoggedInUser(updatedClient);
      }
  };

  const handleDeleteClient = (clientId: string) => {
    setClients(prev => prev.filter(c => c.id !== clientId));
    setAppointments(prev => prev.filter(a => a.clientId !== clientId));
  };
  
  // Profile Updater (Polymorphic: Handles Barber or Client)
  const handleUpdateProfile = (updatedData: Partial<Client>) => {
      if (role === Role.CLIENT) {
          const updatedClient = { ...loggedInUser, ...updatedData };
          handleUpdateClient(updatedClient);
      } else if (role === Role.BARBER) {
          // If it's a barber, we must update the Barber list AND the current user session wrapper
          const updatedBarber: Barber = {
             ...barbers.find(b => b.id === loggedInUser.id)!,
             avatar: updatedData.avatar || '',
             // Can add more fields here if we allow editing name/email self-service
          };
          
          handleUpdateBarber(updatedBarber);
          
          // Update Session
          setLoggedInUser(prev => ({ ...prev, avatar: updatedBarber.avatar }));
      } else if (role === Role.ADMIN) {
           setLoggedInUser(prev => ({ ...prev, ...updatedData }));
      }
  };


  const handleUpdatePreferences = (clientId: string, prefs: CutPreferences) => setClients(prev => prev.map(c => c.id === clientId ? { ...c, preferences: prefs } : c));
  const handleAddService = (serviceData: Omit<Service, 'id'>) => setServices(prev => [...prev, { id: `s-${Math.random()}`, ...serviceData }]);
  const handleUpdateService = (updatedService: Service) => setServices(prev => prev.map(s => s.id === updatedService.id ? updatedService : s));
  const handleDeleteService = (serviceId: string) => setServices(prev => prev.filter(s => s.id !== serviceId));
  const handleAddBarber = (barberData: Omit<Barber, 'id'>) => setBarbers(prev => [...prev, { id: `b-${Math.random()}`, ...barberData }]);
  
  const handleUpdateBarber = (updatedBarber: Barber) => {
      setBarbers(prev => prev.map(b => b.id === updatedBarber.id ? updatedBarber : b));
      // Sync Session if needed
      if (loggedInUser.id === updatedBarber.id) {
          setLoggedInUser(prev => ({ ...prev, avatar: updatedBarber.avatar, name: updatedBarber.name }));
      }
  };

  const handleDeleteBarber = (barberId: string) => setBarbers(prev => prev.filter(b => b.id !== barberId));
  
  // Updated Settings Handler to include Time Slice
  const handleUpdateSettings = (settings: { rules: string; openHour: number; closeHour: number; timeSlice: number }) => {
      setShopRules(settings.rules);
      setOpenHour(settings.openHour);
      setCloseHour(settings.closeHour);
      setTimeSliceMinutes(settings.timeSlice);
  };
  const handleUpdateStyles = (newStyles: GlobalStyleOptions) => {
      setStyleOptions(newStyles);
  };

  // --- Booking ---
  const handleBook = (clientId: string, clientName: string, barberId: string, serviceId: string, time: Date) => {
    const barber = barbers.find(b => b.id === barberId)!;
    const service = services.find(s => s.id === serviceId)!;
    
    const realDuration = Math.ceil(service.durationMinutes * barber.speedFactor);
    const endTime = calculateEndTime(time, service.durationMinutes, barber.speedFactor);

    const newAppointment: Appointment = {
      id: Math.random().toString(36).substr(2, 9),
      clientId,
      clientName, 
      barberId,
      serviceId,
      startTime: time,
      expectedEndTime: endTime,
      status: AppointmentStatus.SCHEDULED,
      price: service.price,
      durationMinutes: realDuration
    };

    setAppointments(prev => [...prev, newAppointment]);
    setIsBookingModalOpen(false);
    setCurrentDate(time);
  };

  // --- RENDER ---

  if (!isAuthenticated) {
      return <LoginPage onLogin={handleLogin} error={authError} />;
  }

  // Define Layout Logic
  const showAdminDashboard = role === Role.ADMIN || role === Role.BARBER;
  
  const currentBarber = role === Role.BARBER 
      ? barbers.find(b => b.id === loggedInUser.id) 
      : (role === Role.ADMIN && adminViewMode === 'WORKSTATION') 
          ? barbers[0] // Assume Admin acts as the first barber (Master)
          : null;

  return (
    <div className="min-h-screen text-gray-100 font-sans selection:bg-brand-500/30 relative">
      
      {/* HIGH TECH BACKGROUND LAYER */}
      <MatrixBackground />
      
      {/* CONTENT LAYER - Z-Index 10 ensures it floats above the canvas */}
      <div className="relative z-10 min-h-screen flex flex-col">
        
        {/* Navigation Bar - NOW WITH GLASS MORPHISM */}
        <nav className="glass-morphism h-16 fixed top-0 w-full z-50 flex items-center justify-between px-4 md:px-6 shadow-lg border-b-0">
            <div className="flex items-center gap-3">
            <div className="bg-brand-500 p-1.5 rounded-lg text-black shadow-[0_0_15px_rgba(240,180,41,0.4)]">
                <Scissors size={20} strokeWidth={2.5} />
            </div>
            <span className="font-bold text-xl tracking-tight text-white hidden sm:inline">CHRONOS<span className="text-brand-500">.BARBER</span></span>
            </div>

            <div className="flex items-center gap-4">
            
            {role === Role.ADMIN && (
                <div className="flex items-center gap-2 glass-morphism-inner p-1 rounded-full border border-white/5">
                    <button 
                        onClick={() => setAdminViewMode('DASHBOARD')}
                        className={`flex items-center gap-2 px-3 py-1.5 text-xs font-bold rounded-full transition-all ${adminViewMode === 'DASHBOARD' ? 'bg-dark-700 text-white shadow-sm' : 'text-gray-400 hover:text-white'}`}
                    >
                        <BarChart3 size={14} />
                        <span className="hidden md:inline">Gerencia</span>
                    </button>
                    <button 
                        onClick={() => setAdminViewMode('WORKSTATION')}
                        className={`flex items-center gap-2 px-3 py-1.5 text-xs font-bold rounded-full transition-all ${adminViewMode === 'WORKSTATION' ? 'bg-brand-500 text-black shadow-sm' : 'text-gray-400 hover:text-white'}`}
                    >
                        <Gauge size={14} />
                        <span className="hidden md:inline">Mi Estación</span>
                    </button>
                </div>
            )}
            
            <div className="flex items-center gap-3 pl-4 border-l border-white/10 h-8">
                <div className="text-right hidden sm:block leading-tight">
                    <div className="text-xs font-bold text-white">{loggedInUser.name}</div>
                    <div className="text-[10px] text-brand-500 font-mono tracking-wide uppercase">
                        {role === Role.ADMIN ? 'Administrador' : role === Role.BARBER ? 'Barbero' : 'Cliente'}
                    </div>
                </div>

                {/* Show Config Button for Barbers as well */}
                {(role === Role.BARBER || role === Role.ADMIN) && (
                    <button
                        onClick={() => setIsShopRulesOpen(true)}
                        className="p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-full transition-colors mr-1"
                        title="Configuración de Agenda"
                    >
                        <Settings size={18} />
                    </button>
                )}

                <button 
                    onClick={() => setIsProfileOpen(true)}
                    className="group relative flex items-center gap-2 rounded-full hover:bg-white/10 transition-all p-0.5 pr-1 focus:outline-none focus:ring-2 focus:ring-brand-500/50"
                    title="Abrir Perfil"
                >
                    <div className="relative w-10 h-10 rounded-full bg-dark-600 border-2 border-dark-500 group-hover:border-brand-500 transition-colors shadow-lg overflow-hidden">
                        {loggedInUser.avatar ? (
                            <img src={loggedInUser.avatar} alt="Profile" className="w-full h-full object-cover" />
                        ) : (
                            <div className="w-full h-full flex items-center justify-center bg-gradient-to-tr from-brand-600 to-brand-400">
                                <User size={18} className="text-black" />
                            </div>
                        )}
                    </div>
                    <ChevronDown size={14} className="text-gray-500 group-hover:text-white transition-colors mr-1" />
                </button>

                <button 
                    onClick={handleLogout}
                    className="ml-2 text-gray-500 hover:text-red-500 transition-colors"
                    title="Cerrar Sesión"
                >
                    <LogOut size={18} />
                </button>
            </div>
            </div>
        </nav>

        {/* Main Content Area - with padding for navbar */}
        <main className="pt-24 px-4 md:px-6 flex-1 flex flex-col pb-4">
            
            {showAdminDashboard ? (
            <>
                <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-6 gap-4 animate-in fade-in slide-in-from-top-4 duration-500">
                <div>
                    <h1 className="text-2xl font-bold text-white tracking-tight flex items-center gap-2 drop-shadow-lg">
                        {role === Role.BARBER && <Lock size={20} className="text-gray-500"/>}
                        {role === Role.BARBER ? `Estación de Trabajo` : `Centro de Operaciones`}
                    </h1>
                    <p className="text-gray-400 text-sm mt-1 font-medium">
                        {role === Role.BARBER 
                            ? `Barbero: ${loggedInUser.name}` 
                            : adminViewMode === 'WORKSTATION' 
                                    ? 'Modo Operativo Activo (Vista de Barbero)' 
                                    : 'Gestión de rendimiento y agenda global'
                        }
                    </p>
                </div>
                
                {/* Management Toolbar - Only for ADMIN */}
                {role === Role.ADMIN && adminViewMode === 'DASHBOARD' && (
                    <div className="flex flex-wrap gap-3">
                         <button 
                            onClick={() => setIsCancellationReportOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-red-400 px-4 py-2 rounded-lg font-bold hover:bg-red-900/20 hover:text-red-300 border border-red-900/30 backdrop-blur transition-all"
                        >
                            <AlertCircle size={18} />
                            Reporte Cancelaciones
                        </button>
                        {/* Settings moved to nav bar for barbers, but kept here for explicit Admin Access */}
                        <button 
                            onClick={() => setIsShopRulesOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-gray-300 px-4 py-2 rounded-lg font-bold hover:bg-white/10 hover:text-white border border-white/5 backdrop-blur transition-all"
                        >
                            <Settings size={18} />
                            Configuración
                        </button>
                        <button 
                            onClick={() => setIsStyleEditorOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-gray-300 px-4 py-2 rounded-lg font-bold hover:bg-white/10 hover:text-white border border-white/5 backdrop-blur transition-all"
                        >
                            <Tag size={18} />
                            Estilos
                        </button>
                        <button 
                            onClick={() => setIsBarberManagerOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-gray-300 px-4 py-2 rounded-lg font-bold hover:bg-white/10 hover:text-white border border-white/5 backdrop-blur transition-all"
                        >
                            <Briefcase size={18} />
                            Staff
                        </button>
                        <button 
                            onClick={() => setIsClientManagerOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-gray-300 px-4 py-2 rounded-lg font-bold hover:bg-white/10 hover:text-white border border-white/5 backdrop-blur transition-all"
                        >
                            <Users size={18} />
                            Clientes
                        </button>
                        <button 
                            onClick={() => setIsServiceManagerOpen(true)}
                            className="flex items-center gap-2 glass-morphism-inner text-gray-300 px-4 py-2 rounded-lg font-bold hover:bg-white/10 hover:text-white border border-white/5 backdrop-blur transition-all"
                        >
                            <Scissors size={18} />
                            Servicios
                        </button>
                        <button 
                            onClick={() => setIsBookingModalOpen(true)}
                            className="flex items-center gap-2 bg-brand-500 text-black px-4 py-2 rounded-lg font-bold hover:bg-brand-400 shadow-[0_4px_20px_-5px_rgba(240,180,41,0.4)] transition-all transform hover:scale-105"
                        >
                            <Plus size={18} strokeWidth={3} />
                            Nueva Cita
                        </button>
                    </div>
                )}
                </div>

                {/* CONDITIONAL DASHBOARD: BARBER vs ADMIN (Dashboard vs Workstation) */}
                {(role === Role.BARBER || (role === Role.ADMIN && adminViewMode === 'WORKSTATION')) ? (
                    <BarberDashboard 
                        barberId={role === Role.BARBER ? loggedInUser.id : 'b1'} 
                        currentBarber={currentBarber || undefined}
                        barbers={barbers} 
                        appointments={visibleAppointments}
                        services={services}
                        onStatusChange={handleStatusChange}
                        onUpdateBarber={handleUpdateBarber}
                        openHour={openHour}
                        closeHour={closeHour}
                    />
                ) : (
                    <MetricsPanel 
                        metrics={metrics} 
                        appointments={visibleAppointments} 
                        currentDate={currentDate} 
                        services={services} 
                        openHour={openHour}
                        closeHour={closeHour}
                    />
                )}
                
                <div className="glass-morphism rounded-xl shadow-2xl flex flex-col relative overflow-visible mt-6">
                    <Timeline 
                        barbers={visibleBarbers} 
                        appointments={visibleAppointments} 
                        services={services}
                        currentDate={currentDate}
                        openHour={openHour}
                        closeHour={closeHour}
                        timeSliceMinutes={timeSliceMinutes} // Passed down for dynamic grid
                        onStatusChange={handleStatusChange}
                        onDateChange={setCurrentDate}
                        onEditAppointment={setEditingAppointment}
                    />
                </div>
            </>
            ) : (
                // CLIENT VIEW
            <div className="flex-1 flex items-center justify-center p-4 animate-in zoom-in-95 duration-500">
                <div className="w-full max-w-md space-y-4 relative z-10">
                    <div className="glass-morphism-inner p-4 rounded-lg flex items-start gap-3 backdrop-blur-md border border-brand-500/20">
                        <User className="text-brand-500 mt-1" size={20} />
                        <div>
                            <p className="text-sm text-brand-200 font-bold">Modo Cliente Activo</p>
                            <p className="text-xs text-gray-400 mt-1">
                                Bienvenido, {loggedInUser.name.split(' ')[0]}.
                            </p>
                        </div>
                    </div>
                    <div className="glass-morphism rounded-xl shadow-2xl">
                        <BookingWizard 
                            barbers={barbers} 
                            services={services} 
                            clients={clients}
                            existingAppointments={appointments}
                            shopRules={shopRules}
                            openHour={openHour}
                            closeHour={closeHour}
                            timeSliceMinutes={timeSliceMinutes} // Passed down
                            currentUser={loggedInUser}
                            currentRole={role}
                            onBook={handleBook}
                            onCancel={() => {}} // Client can't cancel out of their own view
                            onCreateClient={handleCreateClient}
                            onUpdateClient={handleUpdateClient}
                            onDeleteClient={handleDeleteClient}
                        />
                    </div>
                </div>
            </div>
            )}
        </main>
      </div>

      {/* Admin Booking Modal (Only for Admin to create appointments for others) */}
      {isBookingModalOpen && role === Role.ADMIN && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
            <div className="w-full max-w-lg transform transition-all scale-100 glass-morphism rounded-xl">
                <BookingWizard 
                    barbers={barbers} 
                    services={services} 
                    clients={clients}
                    existingAppointments={appointments}
                    shopRules={shopRules}
                    openHour={openHour}
                    closeHour={closeHour}
                    timeSliceMinutes={timeSliceMinutes} // Passed down
                    currentUser={loggedInUser}
                    currentRole={Role.ADMIN}
                    onBook={handleBook}
                    onCancel={() => setIsBookingModalOpen(false)}
                    onCreateClient={handleCreateClient}
                    onUpdateClient={handleUpdateClient}
                    onDeleteClient={handleDeleteClient}
                 />
            </div>
        </div>
      )}

      {/* Modals - Only render if user has permission (ADMIN or BARBER for specific ones) */}
      
      {/* Services Manager - Admin Only */}
      {role === Role.ADMIN && isServiceManagerOpen && (
        <ServiceManager 
            services={services}
            onAdd={handleAddService}
            onUpdate={handleUpdateService}
            onDelete={handleDeleteService}
            onClose={() => setIsServiceManagerOpen(false)}
        />
      )}
      
      {/* Barber Manager - Admin Only */}
      {role === Role.ADMIN && isBarberManagerOpen && (
        <BarberManager 
            barbers={barbers}
            onAdd={handleAddBarber}
            onUpdate={handleUpdateBarber}
            onDelete={handleDeleteBarber}
            onClose={() => setIsBarberManagerOpen(false)}
        />
      )}

      {/* Client Manager - Admin Only */}
      {role === Role.ADMIN && isClientManagerOpen && (
        <ClientManager 
            clients={clients}
            onAdd={handleCreateClient}
            onUpdate={handleUpdateClient}
            onDelete={handleDeleteClient}
            onClose={() => setIsClientManagerOpen(false)}
        />
      )}
      
      {/* Shop Rules / Time Slice Settings - Available to Admin AND Barber (as requested) */}
      {(role === Role.ADMIN || role === Role.BARBER) && isShopRulesOpen && (
        <ShopRulesEditor
            currentRules={shopRules}
            currentOpenHour={openHour}
            currentCloseHour={closeHour}
            currentTimeSlice={timeSliceMinutes}
            onSave={handleUpdateSettings}
            onClose={() => setIsShopRulesOpen(false)}
        />
      )}

      {/* Style Editor - Admin Only */}
      {role === Role.ADMIN && isStyleEditorOpen && (
        <StyleOptionsEditor
            currentOptions={styleOptions}
            onSave={handleUpdateStyles}
            onClose={() => setIsStyleEditorOpen(false)}
        />
      )}
      
      {/* Cancellation Report - Admin Only */}
      {role === Role.ADMIN && isCancellationReportOpen && (
        <CancellationAnalysis 
            appointments={appointments}
            onClose={() => setIsCancellationReportOpen(false)}
        />
      )}

      {/* Appointment Editor (Available to Admin and Barber) */}
      {editingAppointment && (
          <AppointmentEditor
            appointment={editingAppointment}
            allAppointments={appointments}
            serviceName={services.find(s => s.id === editingAppointment.serviceId)?.name || 'Servicio'}
            onClose={() => setEditingAppointment(null)}
            onSave={handleUpdateAppointment}
          />
      )}

      {/* User Profile / Dashboard Drawer */}
      {isProfileOpen && loggedInUser && (
          <UserProfile 
            client={loggedInUser} 
            shopRules={shopRules}
            globalOptions={styleOptions}
            userRole={role}
            userAppointments={appointments.filter(a => a.clientId === loggedInUser.id && a.status !== AppointmentStatus.CANCELLED)}
            onClose={() => setIsProfileOpen(false)}
            onUpdatePreferences={handleUpdatePreferences}
            onUpdateProfile={handleUpdateProfile} // Added profile update handler
            onCancelAppointment={handleCancelAppointment}
          />
      )}
    </div>
  );
}