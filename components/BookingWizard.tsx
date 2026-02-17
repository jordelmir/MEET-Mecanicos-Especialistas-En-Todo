
import React, { useState, useEffect } from 'react';
import { Barber, Service, Appointment, Client, Role } from '../types';
import { generateSmartGrid, formatTime, formatDate } from '../services/timeEngine';
import { Calendar, ChevronRight, Check, User, Scissors, Clock, Sparkles, ChevronLeft, Search, Plus, Phone, Mail, History, Camera, StickyNote, Trash2, AlertTriangle, X, Info, Star, Home, RefreshCw, Fingerprint, Key, Save, Lock } from 'lucide-react';

interface BookingWizardProps {
  barbers: Barber[];
  services: Service[];
  clients: Client[];
  existingAppointments: Appointment[];
  shopRules: string;
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number; // New Prop
  // Context Props
  currentUser: Client; 
  currentRole: Role;
  // Actions
  onBook: (clientId: string, clientName: string, barberId: string, serviceId: string, time: Date) => void;
  onCancel: () => void;
  onCreateClient: (client: Omit<Client, 'id' | 'bookingHistory' | 'points' | 'joinDate'>) => Client;
  onUpdateClient: (client: Client) => void;
  onDeleteClient: (clientId: string) => void;
}

export const BookingWizard: React.FC<BookingWizardProps> = ({ 
  barbers, services, clients, existingAppointments, shopRules, openHour, closeHour, timeSliceMinutes,
  currentUser, currentRole,
  onBook, onCancel, onCreateClient, onUpdateClient, onDeleteClient
}) => {
  
  const isClientView = currentRole === Role.CLIENT;

  // Step 6 is "Success / Confirmed" state
  const [step, setStep] = useState<1 | 2 | 3 | 4 | 5 | 6>(isClientView ? 2 : 1);
  
  // Selection State
  // If Client View, selectedClient IS the currentUser. Strict binding.
  const [selectedClient, setSelectedClient] = useState<Client | null>(isClientView ? currentUser : null);
  
  const [selectedService, setSelectedService] = useState<Service | null>(null);
  const [selectedBarber, setSelectedBarber] = useState<Barber | null>(null);
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [selectedTime, setSelectedTime] = useState<Date | null>(null);

  // Client Search/Create State (Admin Only)
  const [searchTerm, setSearchTerm] = useState('');
  const [isCreatingClient, setIsCreatingClient] = useState(false);
  
  // ROBUST FORM STATE (Parity with ClientManager)
  const [newClientForm, setNewClientForm] = useState({ 
      name: '', 
      phone: '', 
      email: '', 
      identification: '',
      accessCode: '',
      avatar: '', 
      notes: '' 
  });
  
  const [clientToDelete, setClientToDelete] = useState<Client | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // Calculate Smart Grid (All Slots with Status) with dynamic timeSlice
  const slotsGrid = selectedBarber && selectedService 
    ? generateSmartGrid(selectedDate, selectedBarber.id, selectedService.durationMinutes, selectedBarber.speedFactor, existingAppointments, openHour, closeHour, timeSliceMinutes)
    : [];

  const changeDate = (days: number) => {
    const newDate = new Date(selectedDate);
    newDate.setDate(newDate.getDate() + days);
    const today = new Date();
    today.setHours(0,0,0,0);
    if (newDate >= today) {
        setSelectedDate(newDate);
        setSelectedTime(null);
    }
  };

  // Admin Only: Filter clients
  const filteredClients = !isClientView ? clients.filter(c => 
    c.name.toLowerCase().includes(searchTerm.toLowerCase()) || 
    c.phone.includes(searchTerm) ||
    c.identification.includes(searchTerm)
  ) : [];

  // --- HELPER FUNCTIONS FOR CREATION ---
  const generateAccessCode = () => {
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      setNewClientForm(prev => ({ ...prev, accessCode: code }));
  };

  const generateRandomAvatar = () => {
      setNewClientForm(prev => ({
          ...prev, 
          avatar: `https://i.pravatar.cc/150?img=${Math.floor(Math.random() * 70)}`
      }));
  };

  const handleCreateClient = () => {
    // Validation strictness same as ClientManager
    if (!newClientForm.name || !newClientForm.identification) {
        setFormError("Nombre y Cédula son obligatorios.");
        return;
    }
    
    if (!newClientForm.accessCode) {
        setFormError("Debes generar un código de acceso.");
        return;
    }
    
    const avatarToUse = newClientForm.avatar || `https://ui-avatars.com/api/?name=${newClientForm.name}&background=random`;

    // Generate required fields for Client
    const clientPayload = {
        ...newClientForm,
        avatar: avatarToUse
    };

    const newClient = onCreateClient(clientPayload);
    setSelectedClient(newClient);
    setIsCreatingClient(false);
    setFormError(null);
  };

  const handleNotesChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      if (selectedClient) {
          const updated = { ...selectedClient, notes: e.target.value };
          setSelectedClient(updated);
          onUpdateClient(updated);
      }
  };

  const confirmDelete = () => {
    if (clientToDelete) {
      onDeleteClient(clientToDelete.id);
      if (selectedClient?.id === clientToDelete.id) {
        setSelectedClient(null);
      }
      setClientToDelete(null);
    }
  };

  const startClientCreation = () => {
      const isLikelyPhone = /^[\d\-\+\(\)\s]+$/.test(searchTerm) && searchTerm.length > 6;
      setNewClientForm({
          name: !isLikelyPhone ? searchTerm : '',
          phone: isLikelyPhone ? searchTerm : '',
          email: '',
          identification: '',
          accessCode: '',
          avatar: '',
          notes: ''
      });
      setIsCreatingClient(true);
      setFormError(null);
  };
  
  const handleFinalBooking = () => {
       if (selectedClient && selectedBarber && selectedService && selectedTime) {
            onBook(selectedClient.id, selectedClient.name, selectedBarber.id, selectedService.id, selectedTime);
            setStep(6); // Go to Success Screen
       }
  };

  const resetWizard = () => {
      // Keep client selected if in Client Mode
      setSelectedService(null);
      setSelectedBarber(null);
      setSelectedTime(null);
      setStep(isClientView ? 2 : 1);
      if (!isClientView) {
          setSelectedClient(null);
          setSearchTerm('');
      }
  };

  // Helper to determine total steps for progress bar
  const totalSteps = 5;
  const progressPercentage = Math.min(100, (step / totalSteps) * 100);

  return (
    <div className="bg-dark-900/50 rounded-xl shadow-2xl border border-white/5 max-w-md mx-auto overflow-hidden h-[680px] flex flex-col relative font-sans backdrop-blur-md">
      
      {/* Visual Progress Bar (Hidden on Success Step) */}
      {step !== 6 && (
        <div className="absolute top-0 left-0 h-1.5 bg-dark-700 w-full z-20">
            <div className="h-full bg-brand-500 transition-all duration-500 ease-out shadow-[0_0_10px_rgba(240,180,41,0.5)]" style={{ width: `${progressPercentage}%` }}></div>
        </div>
      )}

      {/* Header (Hidden on Success Step) */}
      {step !== 6 && (
        <div className="p-6 border-b border-white/10 bg-dark-900/80 backdrop-blur z-10">
            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-xl font-bold text-white flex items-center gap-2 tracking-tight">
                        {step === 5 ? <Check className="text-emerald-500" /> : <Calendar className="text-brand-500" size={20} />}
                        {step === 5 ? 'Confirmar Reserva' : 'Agendar Cita'}
                    </h2>
                    <p className="text-xs text-gray-500 mt-1 font-medium">
                        {isClientView ? 'Experiencia Personalizada' : 'Modo Operador'} • Paso {step} de 5
                    </p>
                </div>
                {isClientView && (
                    <div className="w-10 h-10 rounded-full border border-dark-600 overflow-hidden">
                        <img src={currentUser.avatar} className="w-full h-full object-cover" alt="Me" />
                    </div>
                )}
            </div>
        </div>
      )}

      <div className="p-6 flex-1 overflow-y-auto custom-scrollbar relative">
        
        {/* Step 1: Client Identification (ADMIN ONLY) */}
        {!isClientView && step === 1 && (
            <div className="space-y-4 animate-in fade-in slide-in-from-right-4 duration-300">
                <h3 className="text-lg font-bold text-gray-200">
                    {isCreatingClient ? 'Registrar Nuevo Cliente' : 'Identificar Cliente'}
                </h3>
                
                {!isCreatingClient ? (
                    <>
                        <div className="relative group">
                            <Search className="absolute left-3 top-3 text-gray-500 group-focus-within:text-brand-500 transition-colors" size={18} />
                            <input 
                                type="text"
                                placeholder="Buscar por nombre, cédula o teléfono..."
                                className="w-full bg-dark-700 border border-dark-600 rounded-xl py-3 pl-10 pr-4 text-white focus:border-brand-500 focus:ring-1 focus:ring-brand-500 focus:outline-none transition-all"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                autoFocus
                            />
                        </div>

                        <div className="space-y-2 mt-2 max-h-[220px] overflow-y-auto pr-1 custom-scrollbar">
                            {filteredClients.map(client => (
                                <button
                                    key={client.id}
                                    onClick={() => setSelectedClient(client)}
                                    className={`w-full text-left p-3 rounded-xl border flex items-center justify-between group transition-all relative ${selectedClient?.id === client.id ? 'bg-brand-500/10 border-brand-500 ring-1 ring-brand-500/50' : 'bg-dark-700/30 border-dark-600/50 hover:bg-dark-700 hover:border-dark-500'}`}
                                >
                                    <div className="flex items-center gap-3">
                                        <div className="w-10 h-10 rounded-full bg-dark-600 flex items-center justify-center overflow-hidden border border-dark-500 shrink-0">
                                            {client.avatar ? (
                                                <img src={client.avatar} alt={client.name} className="w-full h-full object-cover" />
                                            ) : (
                                                <User size={18} className="text-gray-400" />
                                            )}
                                        </div>
                                        <div>
                                            <div className="font-bold text-white text-sm group-hover:text-brand-400 transition-colors">{client.name}</div>
                                            <div className="text-[10px] text-gray-500 flex flex-col gap-0.5 mt-0.5">
                                                <span className="flex items-center gap-1"><Phone size={10}/> {client.phone}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                      {selectedClient?.id === client.id && <div className="bg-brand-500 text-black p-1 rounded-full"><Check size={12} strokeWidth={3} /></div>}
                                      <div 
                                        className="opacity-0 group-hover:opacity-100 transition-opacity p-2 hover:bg-red-500/20 rounded-lg text-gray-500 hover:text-red-500 cursor-pointer z-10"
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          setClientToDelete(client);
                                        }}
                                        title="Eliminar Perfil"
                                      >
                                        <Trash2 size={14} />
                                      </div>
                                    </div>
                                </button>
                            ))}
                            {filteredClients.length === 0 && searchTerm && (
                                <div className="text-center py-6 text-gray-500 text-sm bg-dark-700/20 rounded-xl border border-dashed border-dark-600">
                                    <User size={24} className="mx-auto mb-2 opacity-50"/>
                                    No se encontraron clientes "{searchTerm}".
                                </div>
                            )}
                        </div>

                        <div className="pt-4 border-t border-dark-700 mt-2">
                            {selectedClient ? (
                                <div className="space-y-4 animate-in fade-in slide-in-from-bottom-2">
                                    <div>
                                        <label className="text-xs text-brand-400 uppercase font-bold flex items-center gap-2 mb-2">
                                            <StickyNote size={12}/> Notas Internas
                                        </label>
                                        <textarea
                                            className="w-full bg-dark-700 border border-dark-600 rounded-xl p-3 text-sm text-white focus:border-brand-500 focus:outline-none resize-none h-20"
                                            placeholder="Preferencias del cliente, alergias, o notas..."
                                            value={selectedClient.notes || ''}
                                            onChange={handleNotesChange}
                                        />
                                    </div>
                                </div>
                            ) : (
                                <button 
                                    onClick={startClientCreation}
                                    className="w-full py-3.5 rounded-xl border border-dashed border-dark-500 text-gray-400 hover:text-white hover:border-brand-500 hover:bg-dark-700 transition-all flex items-center justify-center gap-2 font-medium"
                                >
                                    <Plus size={16} /> Registrar Nuevo Cliente
                                </button>
                            )}
                        </div>
                    </>
                ) : (
                    // --- PROFESSIONAL CLIENT CREATION FORM ---
                    <div className="space-y-4 animate-in fade-in slide-in-from-right-4">
                        
                         {formError && (
                            <div className="bg-red-500/10 border border-red-500/50 p-3 rounded-lg flex items-center gap-3">
                                <AlertTriangle className="text-red-500 shrink-0" size={16} />
                                <p className="text-xs text-red-200">{formError}</p>
                            </div>
                        )}

                        <div className="flex gap-4">
                             {/* Avatar Section */}
                            <div className="shrink-0 flex flex-col items-center">
                                <div 
                                    className="w-20 h-20 rounded-full bg-dark-700 border-2 border-dark-600 overflow-hidden relative group cursor-pointer" 
                                    onClick={generateRandomAvatar}
                                >
                                    {newClientForm.avatar ? (
                                        <img src={newClientForm.avatar} alt="Avatar" className="w-full h-full object-cover" />
                                    ) : (
                                        <User className="w-full h-full p-5 text-gray-500" />
                                    )}
                                    <div className="absolute inset-0 bg-black/50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                                        <Camera size={20} className="text-white" />
                                    </div>
                                </div>
                                <button type="button" onClick={generateRandomAvatar} className="text-[10px] text-brand-500 mt-2 hover:underline text-center w-full">
                                    Generar Foto
                                </button>
                            </div>
                            
                            {/* Main Info */}
                            <div className="flex-1 space-y-3">
                                <div className="bg-dark-900/50 border border-dark-600 rounded-lg p-3 space-y-3">
                                    <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">Datos Personales y Contacto</label>
                                    
                                    <input 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg py-2 px-3 text-white focus:border-brand-500 focus:outline-none text-xs" 
                                        placeholder="Nombre Completo"
                                        value={newClientForm.name}
                                        onChange={e => setNewClientForm({...newClientForm, name: e.target.value})}
                                    />
                                    
                                    <div className="relative">
                                        <Fingerprint size={12} className="absolute left-3 top-2.5 text-gray-500" />
                                        <input 
                                            className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-8 pr-3 py-2 text-white focus:border-brand-500 focus:outline-none text-xs font-mono" 
                                            placeholder="Cédula / ID"
                                            value={newClientForm.identification}
                                            onChange={e => setNewClientForm({...newClientForm, identification: e.target.value.replace(/\s/g, '')})}
                                        />
                                    </div>

                                    <div className="relative">
                                        <Phone size={12} className="absolute left-3 top-2.5 text-brand-500" />
                                        <input 
                                            className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-8 pr-3 py-2 text-white focus:border-brand-500 focus:outline-none text-xs" 
                                            placeholder="Teléfono"
                                            value={newClientForm.phone}
                                            onChange={e => setNewClientForm({...newClientForm, phone: e.target.value})}
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Extra Info */}
                         <div className="relative">
                            <Mail size={12} className="absolute left-3 top-2.5 text-gray-500" />
                            <input 
                                type="email" 
                                placeholder="Email (Opcional)" 
                                className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-8 pr-3 py-2 text-white focus:border-brand-500 focus:outline-none text-xs"
                                value={newClientForm.email}
                                onChange={(e) => setNewClientForm({...newClientForm, email: e.target.value})}
                            />
                        </div>

                         {/* Credentials Display */}
                        <div className="bg-dark-800/50 p-3 rounded-lg border border-brand-500/30 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <div className="bg-brand-500/20 p-1.5 rounded text-brand-500"><Key size={14}/></div>
                                <div>
                                    <div className="text-[10px] text-gray-400 uppercase font-bold">Código de Acceso</div>
                                    <div className="text-[10px] text-gray-500">Credencial única de 6 dígitos</div>
                                </div>
                            </div>
                             {newClientForm.accessCode ? (
                                <div className="flex items-center gap-3">
                                    <span className="font-mono text-lg font-bold text-brand-500 tracking-widest bg-dark-900 px-2 py-0.5 rounded border border-dark-700">
                                        {newClientForm.accessCode}
                                    </span>
                                    <button 
                                        type="button" 
                                        onClick={generateAccessCode}
                                        className="p-1.5 bg-dark-700 hover:bg-dark-600 rounded-lg text-gray-400 hover:text-white transition-colors"
                                        title="Regenerar Código"
                                    >
                                        <RefreshCw size={12} />
                                    </button>
                                </div>
                            ) : (
                                <button 
                                    type="button" 
                                    onClick={generateAccessCode}
                                    className="bg-brand-600/20 hover:bg-brand-600/30 text-brand-400 border border-brand-500/50 px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-2"
                                >
                                    <Key size={10} /> Generar Credenciales
                                </button>
                            )}
                        </div>

                        <div className="flex gap-2 pt-2">
                             <button onClick={() => { setIsCreatingClient(false); setFormError(null); }} className="flex-1 py-2 text-sm text-gray-400 hover:text-white border border-transparent hover:border-dark-600 rounded-lg">Cancelar</button>
                             <button 
                                onClick={handleCreateClient}
                                className="flex-1 bg-brand-500 text-black font-bold py-2 rounded-lg hover:bg-brand-400 flex items-center justify-center gap-2"
                            >
                                <Save size={14} /> Guardar Perfil
                             </button>
                        </div>
                    </div>
                )}
            </div>
        )}

        {/* Step 2: Service Selection */}
        {step === 2 && (
            <div className="space-y-4 animate-in fade-in slide-in-from-right-4 duration-300">
                {isClientView && (
                    <div className="mb-4">
                        <h3 className="text-xl font-bold text-white">Hola, {currentUser.name.split(' ')[0]} 👋</h3>
                        <p className="text-sm text-gray-400">¿Qué estilo buscas hoy?</p>
                    </div>
                )}
                {!isClientView && <h3 className="text-lg font-bold text-gray-200">Seleccionar Tratamiento</h3>}
                
                <div className="grid grid-cols-1 gap-3">
                    {services.map(service => (
                        <button
                            key={service.id}
                            onClick={() => setSelectedService(service)}
                            className={`relative text-left p-4 rounded-xl border transition-all duration-200 group overflow-hidden ${selectedService?.id === service.id ? 'bg-brand-500 border-brand-500 shadow-lg shadow-brand-500/20' : 'bg-dark-700/40 border-dark-600 hover:border-gray-500 hover:bg-dark-700'}`}
                        >
                            {/* Selected Indicator */}
                            {selectedService?.id === service.id && (
                                <div className="absolute right-0 top-0 p-2 text-black opacity-20">
                                    <Scissors size={60} />
                                </div>
                            )}

                            <div className="flex justify-between items-start relative z-10">
                                <div>
                                    <span className={`font-bold text-lg block ${selectedService?.id === service.id ? 'text-black' : 'text-white group-hover:text-brand-400'}`}>{service.name}</span>
                                    <div className={`flex items-center gap-2 mt-1 text-sm ${selectedService?.id === service.id ? 'text-black/70' : 'text-gray-400'}`}>
                                        <Clock size={14} />
                                        <span>{service.durationMinutes} minutos</span>
                                    </div>
                                </div>
                                <span className={`font-bold font-mono text-lg ${selectedService?.id === service.id ? 'text-black' : 'text-brand-400'}`}>
                                    ₡{service.price.toLocaleString('es-CR')}
                                </span>
                            </div>
                        </button>
                    ))}
                </div>
            </div>
        )}

        {/* Step 3: Barber Selection */}
        {step === 3 && (
            <div className="space-y-4 animate-in fade-in slide-in-from-right-4 duration-300">
                <h3 className="text-lg font-bold text-gray-200">Elige tu Especialista</h3>
                <div className="grid grid-cols-1 gap-3">
                    {barbers.map(barber => (
                        <button
                            key={barber.id}
                            onClick={() => setSelectedBarber(barber)}
                            className={`flex items-center gap-4 p-4 rounded-xl border transition-all duration-200 ${selectedBarber?.id === barber.id ? 'bg-gradient-to-r from-dark-800 to-dark-700 border-brand-500 ring-1 ring-brand-500 shadow-xl' : 'bg-dark-700/40 border-dark-600 hover:border-gray-500 hover:bg-dark-700'}`}
                        >
                            <div className="relative">
                                <img src={barber.avatar} className="w-16 h-16 rounded-full object-cover border-2 border-dark-500" alt="" />
                                {barber.tier === 'MASTER' && (
                                    <div className="absolute -bottom-1 -right-1 bg-yellow-500 text-black text-[9px] font-bold px-1.5 py-0.5 rounded-full border border-dark-800">PRO</div>
                                )}
                            </div>
                            
                            <div className="text-left flex-1">
                                <div className="font-bold text-white text-lg">{barber.name}</div>
                                <div className="flex items-center gap-2 mt-1">
                                   <span className={`text-[10px] font-bold px-2 py-0.5 rounded uppercase tracking-wide border ${
                                       barber.tier === 'MASTER' ? 'bg-yellow-500/10 border-yellow-500 text-yellow-500' : 
                                       barber.tier === 'SENIOR' ? 'bg-blue-500/10 border-blue-500 text-blue-400' :
                                       'bg-gray-700 border-gray-600 text-gray-400'
                                   }`}>
                                       {barber.tier}
                                   </span>
                                </div>
                            </div>
                            {selectedBarber?.id === barber.id && <div className="bg-brand-500 text-black rounded-full p-1.5"><Check size={18} strokeWidth={3} /></div>}
                        </button>
                    ))}
                </div>
            </div>
        )}

        {/* Step 4: Date & Time - ENHANCED VISUALIZATION */}
        {step === 4 && (
            <div className="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
                
                {/* Date Selector */}
                <div className="flex items-center justify-between bg-dark-700/50 p-2 rounded-xl border border-dark-600">
                    <button onClick={() => changeDate(-1)} className="p-3 hover:bg-dark-600 rounded-lg text-gray-400 hover:text-white transition-colors">
                        <ChevronLeft size={20} />
                    </button>
                    <div className="text-center">
                        <div className="text-xs text-gray-400 uppercase tracking-wider font-semibold mb-1">Fecha Seleccionada</div>
                        <div className="text-white font-bold text-xl capitalize">{formatDate(selectedDate)}</div>
                    </div>
                    <button onClick={() => changeDate(1)} className="p-3 hover:bg-dark-600 rounded-lg text-gray-400 hover:text-white transition-colors">
                        <ChevronRight size={20} />
                    </button>
                </div>

                <div>
                    <div className="flex justify-between items-center mb-4">
                        <h3 className="text-lg font-bold text-gray-200">Horarios Disponibles</h3>
                        <div className="flex items-center gap-2 text-xs">
                             <span className="flex items-center gap-1.5 text-brand-400 bg-brand-900/10 px-2 py-1 rounded border border-brand-900/30">
                                 <Sparkles size={10} className="text-brand-500 fill-brand-500 animate-pulse" />
                                 Recomendado por AI
                             </span>
                        </div>
                    </div>

                    {slotsGrid.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-12 text-gray-500 bg-dark-700/20 rounded-xl border border-dark-700 border-dashed">
                            <Clock size={40} className="mb-3 opacity-30 text-red-400" />
                            <p className="font-medium text-gray-400">Sin disponibilidad.</p>
                            <p className="text-xs mt-1">Intenta con otra fecha o barbero.</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-4 gap-2 max-h-[300px] overflow-y-auto custom-scrollbar pr-1">
                            {slotsGrid.map((slot, idx) => {
                                const isDisabled = slot.status !== 'AVAILABLE';
                                const isSelected = selectedTime?.getTime() === slot.time.getTime();
                                const isPerfectMatch = slot.score === 'AI_PERFECT_MATCH';
                                const isOptimal = slot.score === 'OPTIMAL';

                                return (
                                    <button
                                        key={idx}
                                        disabled={isDisabled}
                                        onClick={() => setSelectedTime(slot.time)}
                                        className={`relative py-3 rounded-lg border text-sm transition-all flex flex-col items-center justify-center gap-1 overflow-hidden
                                            ${isSelected
                                                ? 'bg-brand-500 text-black font-bold border-brand-500 shadow-lg shadow-brand-500/20 scale-[1.02] z-10' 
                                                : isDisabled 
                                                    ? 'bg-dark-800/40 border-dark-700 text-gray-600 cursor-not-allowed opacity-50'
                                                    : 'bg-dark-700/30 border-dark-600 text-gray-300 hover:border-emerald-500/50 hover:bg-dark-600'
                                            }
                                        `}
                                    >
                                        {/* Status Indicators */}
                                        {isDisabled && (
                                            <div className="absolute inset-0 bg-dark-900/40 flex items-center justify-center">
                                                {slot.status === 'LOCKED' ? <Lock size={12} className="opacity-40" /> : <div className="w-16 h-[1px] bg-gray-700 rotate-45"></div>}
                                            </div>
                                        )}

                                        {/* Availability Dot */}
                                        {!isDisabled && !isSelected && (
                                            <div className={`absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full ${isPerfectMatch ? 'bg-brand-500 shadow-[0_0_8px_rgba(240,180,41,0.8)] animate-pulse' : 'bg-emerald-500'}`}></div>
                                        )}

                                        {isPerfectMatch && !isSelected && !isDisabled && (
                                            <div className="absolute inset-0 border border-brand-500/30 rounded-lg pointer-events-none"></div>
                                        )}

                                        <span className={`text-xs font-mono ${isSelected ? 'font-bold' : ''}`}>{formatTime(slot.time)}</span>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>
                
                <div className="bg-dark-800/50 p-3 rounded-lg border border-white/5 flex gap-4 text-[10px] text-gray-500 justify-center">
                    <div className="flex items-center gap-1.5"><div className="w-2 h-2 bg-emerald-500 rounded-full"></div> Disponible</div>
                    <div className="flex items-center gap-1.5"><div className="w-2 h-2 bg-dark-700 border border-dark-500 rounded-full relative overflow-hidden"><div className="absolute inset-0 bg-gray-500/20"></div></div> Ocupado</div>
                    <div className="flex items-center gap-1.5"><div className="w-2 h-2 bg-brand-500 rounded-full animate-pulse shadow-[0_0_5px_rgba(240,180,41,0.5)]"></div> Match Perfecto</div>
                </div>

            </div>
        )}

        {/* Step 5: Summary (Boarding Pass Style) */}
        {step === 5 && selectedClient && selectedBarber && selectedService && selectedTime && (
             <div className="space-y-6 animate-in zoom-in-95 duration-300">
                
                {/* Shop Rules Alert */}
                {shopRules && (
                    <div className="bg-blue-500/10 border border-blue-500/30 p-4 rounded-xl">
                        <div className="flex items-center gap-2 mb-2 text-blue-400 font-bold text-xs uppercase tracking-wide">
                            <Info size={14} /> Información Importante
                        </div>
                        <div className="text-sm text-gray-300 whitespace-pre-line leading-relaxed pl-2">
                            {shopRules}
                        </div>
                    </div>
                )}

                <div className="bg-gradient-to-b from-dark-700 to-dark-800 rounded-2xl border border-dark-600 overflow-hidden shadow-2xl relative">
                    
                    {/* Top Tear Line Decoration */}
                    <div className="h-2 bg-brand-500 w-full"></div>

                    <div className="p-6 space-y-6">
                        <div className="flex justify-between items-start">
                            <div>
                                <h3 className="text-2xl font-black text-white uppercase tracking-tight">Resumen</h3>
                                <p className="text-gray-400 text-xs uppercase tracking-widest mt-1">Ticket de Cita</p>
                            </div>
                            <div className="text-right">
                                <div className="text-brand-500 font-mono font-bold text-xl">₡{selectedService.price.toLocaleString('es-CR')}</div>
                                <div className="text-[10px] text-gray-500 uppercase">Precio Estimado</div>
                            </div>
                        </div>

                        <div className="flex items-center gap-4 bg-dark-900/50 p-4 rounded-xl border border-dark-700/50">
                             <img 
                                src={selectedBarber.avatar} 
                                className="w-12 h-12 rounded-full border-2 border-brand-500 object-cover" 
                                alt="" 
                             />
                             <div>
                                <div className="text-xs text-gray-500 uppercase font-bold">Barbero Asignado</div>
                                <div className="text-white font-bold text-lg">{selectedBarber.name}</div>
                             </div>
                        </div>

                        <div className="grid grid-cols-2 gap-6">
                            <div>
                                <div className="text-xs text-gray-500 uppercase font-bold mb-1">Fecha</div>
                                <div className="text-white font-medium capitalize flex items-center gap-2">
                                    <Calendar size={14} className="text-brand-500"/>
                                    {formatDate(selectedDate)}
                                </div>
                            </div>
                            <div>
                                <div className="text-xs text-gray-500 uppercase font-bold mb-1">Hora</div>
                                <div className="text-white font-medium flex items-center gap-2">
                                    <Clock size={14} className="text-brand-500"/>
                                    {formatTime(selectedTime)}
                                </div>
                            </div>
                        </div>

                        <div className="pt-6 border-t border-dark-600/50 border-dashed">
                             <div className="flex justify-between items-center">
                                 <div>
                                     <div className="text-xs text-gray-500 uppercase font-bold mb-1">Servicio</div>
                                     <div className="text-white font-bold text-lg">{selectedService.name}</div>
                                 </div>
                                 <Scissors className="text-dark-600" size={32} />
                             </div>
                        </div>
                    </div>
                </div>
             </div>
        )}

        {/* Step 6: SUCCESS SCREEN (New) */}
        {step === 6 && (
            <div className="flex flex-col items-center justify-center h-full animate-in zoom-in-95 duration-500">
                <div className="w-20 h-20 bg-emerald-500 rounded-full flex items-center justify-center shadow-[0_0_30px_rgba(16,185,129,0.5)] mb-6">
                    <Check size={40} strokeWidth={4} className="text-black" />
                </div>
                <h2 className="text-3xl font-black text-white tracking-tight mb-2">¡Cita Confirmada!</h2>
                <p className="text-gray-400 text-center max-w-[80%] mb-8">
                    Tu espacio ha sido reservado con éxito. Te esperamos en Chronos Barber.
                </p>
                <div className="flex flex-col gap-3 w-full max-w-xs">
                    <button 
                        onClick={resetWizard}
                        className="bg-dark-700 text-white py-3 rounded-xl font-bold hover:bg-dark-600 flex items-center justify-center gap-2 border border-dark-600"
                    >
                        <RefreshCw size={18} /> Agendar Otra
                    </button>
                    {isClientView && (
                         <div className="text-center mt-4">
                            <p className="text-xs text-gray-500">Puedes ver tus citas en tu Perfil.</p>
                         </div>
                    )}
                </div>
            </div>
        )}

      </div>

      {/* Footer Actions (Hidden on Success Step) */}
      {step !== 6 && (
        <div className="p-6 border-t border-white/10 bg-dark-900/50 flex justify-between items-center z-10 backdrop-blur">
            <button 
                onClick={step === (isClientView ? 2 : 1) ? onCancel : () => setStep(prev => prev - 1 as any)}
                className="text-gray-400 hover:text-white font-medium text-sm transition-colors px-4 py-2 hover:bg-dark-800 rounded-lg"
            >
                {step === (isClientView ? 2 : 1) ? 'Cancelar' : 'Volver'}
            </button>

            <button
                disabled={
                    (step === 1 && !selectedClient) || 
                    (step === 2 && !selectedService) || 
                    (step === 3 && !selectedBarber) || 
                    (step === 4 && !selectedTime) ||
                    (step === 1 && isCreatingClient) // Disable next if still filling form
                }
                onClick={() => {
                    if (step === 5) {
                        handleFinalBooking();
                    } else {
                        setStep(prev => prev + 1 as any);
                    }
                }}
                className="bg-brand-500 text-black px-8 py-3 rounded-xl font-bold hover:bg-brand-400 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 shadow-[0_0_20px_rgba(240,180,41,0.3)] hover:shadow-[0_0_25px_rgba(240,180,41,0.5)] transition-all transform hover:-translate-y-0.5 active:scale-95"
            >
                {step === 5 ? 'Confirmar' : 'Siguiente'}
                {step !== 5 && <ChevronRight size={16} strokeWidth={3} />}
            </button>
        </div>
      )}

       {/* Delete Confirmation Modal (Admin Only) */}
       {clientToDelete && (
        <div className="absolute inset-0 bg-dark-900/90 backdrop-blur-sm z-50 flex items-center justify-center p-6 animate-in fade-in duration-200">
            <div className="glass-morphism-inner bg-dark-800 border border-red-900/50 rounded-xl p-6 shadow-2xl w-full max-w-sm">
                <div className="flex justify-center mb-4">
                    <div className="w-12 h-12 rounded-full bg-red-900/30 flex items-center justify-center text-red-500">
                        <AlertTriangle size={24} />
                    </div>
                </div>
                <h3 className="text-lg font-bold text-white text-center mb-2">¿Eliminar Cliente?</h3>
                <p className="text-gray-400 text-sm text-center mb-6">
                    ¿Estás seguro que deseas eliminar a <span className="font-bold text-gray-200">{clientToDelete.name}</span>?
                </p>
                <div className="flex gap-3">
                    <button 
                        onClick={() => setClientToDelete(null)}
                        className="flex-1 py-2.5 rounded-lg border border-dark-600 text-gray-300 hover:bg-dark-700 hover:text-white transition-colors text-sm font-medium"
                    >
                        Cancelar
                    </button>
                    <button 
                        onClick={confirmDelete}
                        className="flex-1 py-2.5 rounded-lg bg-red-600 text-white hover:bg-red-500 shadow-lg shadow-red-900/20 transition-all text-sm font-bold"
                    >
                        Eliminar
                    </button>
                </div>
            </div>
        </div>
       )}

    </div>
  );
};