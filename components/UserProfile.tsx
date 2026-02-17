
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { Client, CutPreferences, GlobalStyleOptions, Role, Appointment } from '../types';
import { X, Star, Calendar, Scissors, Award, History, Edit3, Save, MessageSquare, Info, Plus, Check, Hash, Smile, Zap, AlertCircle, Layout, Sparkles, ChevronRight, User, Trash2, Clock, AlertTriangle, PhoneCall, Fingerprint, Tag } from 'lucide-react';
import { AvatarSelector } from './AvatarSelector';
import { formatTime, canClientCancel } from '../services/timeEngine';

interface UserProfileProps {
  client: Client;
  shopRules?: string;
  globalOptions: GlobalStyleOptions; 
  userRole: Role;
  userAppointments: Appointment[];
  onClose: () => void;
  onUpdatePreferences: (clientId: string, prefs: CutPreferences) => void;
  onUpdateProfile: (updatedData: Partial<Client>) => void; 
  onCancelAppointment: (id: string, reason?: string) => void;
}

// --- SUB-COMPONENT: STYLE SELECTOR ---
interface StyleSelectorProps {
  title: string;
  icon: React.ElementType;
  value: string;
  options: string[];
  onSelect: (val: string) => void;
  onAddCustom: (val: string) => void;
}

const StyleSelector: React.FC<StyleSelectorProps> = ({ title, icon: Icon, value, options, onSelect, onAddCustom }) => {
    const [isAdding, setIsAdding] = useState(false);
    const [customVal, setCustomVal] = useState('');

    const handleAdd = () => {
        if (customVal.trim()) {
            onAddCustom(customVal.trim());
            onSelect(customVal.trim());
            setCustomVal('');
            setIsAdding(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') handleAdd();
        if (e.key === 'Escape') setIsAdding(false);
    };

    return (
        <div className="flex flex-col h-full bg-dark-800/40 border border-white/5 rounded-xl p-4 hover:border-brand-500/30 transition-colors group">
            <div className="flex items-center gap-2 mb-3">
                <div className="p-1.5 rounded-lg bg-dark-700 text-brand-500 group-hover:bg-brand-500 group-hover:text-black transition-colors">
                    <Icon size={14} />
                </div>
                <label className="text-xs text-gray-300 font-bold uppercase tracking-wide truncate">
                    {title}
                </label>
            </div>
            
            <div className="flex-1 flex flex-wrap content-start gap-2">
                {options.map(opt => (
                    <button
                        key={opt}
                        onClick={() => onSelect(opt)}
                        className={`px-3 py-1.5 rounded-md text-[11px] font-bold border transition-all ${
                            value === opt 
                            ? 'bg-brand-500 text-black border-brand-500 shadow-sm scale-105' 
                            : 'bg-dark-900 text-gray-400 border-dark-700 hover:border-gray-500 hover:text-white'
                        }`}
                    >
                        {opt}
                    </button>
                ))}
                
                {isAdding ? (
                    <div className="flex items-center gap-1 bg-dark-900 border border-brand-500/50 rounded-md px-2 py-1 animate-in zoom-in duration-200">
                        <input 
                            autoFocus
                            type="text" 
                            className="bg-transparent text-white text-[11px] outline-none w-20 font-bold placeholder-gray-600"
                            placeholder="..."
                            value={customVal}
                            onChange={(e) => setCustomVal(e.target.value)}
                            onKeyDown={handleKeyDown}
                            onBlur={() => !customVal && setIsAdding(false)}
                        />
                        <button onClick={handleAdd} className="text-brand-500 hover:text-white"><Check size={12} /></button>
                    </div>
                ) : (
                    <button 
                        onClick={() => setIsAdding(true)}
                        className="px-2 py-1.5 rounded-md text-[10px] font-bold border border-dashed border-dark-600 text-gray-500 hover:text-brand-400 hover:border-brand-500/50 hover:bg-brand-500/5 transition-all flex items-center gap-1"
                    >
                        <Plus size={10} />
                    </button>
                )}
            </div>
        </div>
    );
};

// --- SUB-COMPONENT: PRESS-TO-HOLD BUTTON ---
interface PressHoldButtonProps {
    onConfirm: () => void;
    label: string;
    isMobile: boolean;
}

const PressHoldButton: React.FC<PressHoldButtonProps> = ({ onConfirm, label }) => {
    const [progress, setProgress] = useState(0);
    const [isPressed, setIsPressed] = useState(false);
    const requestRef = useRef<number>();
    const startTimeRef = useRef<number>(0);
    const DURATION = 1500; // 1.5 seconds to confirm

    const animate = (time: number) => {
        if (!startTimeRef.current) startTimeRef.current = time;
        const elapsed = time - startTimeRef.current;
        const newProgress = Math.min((elapsed / DURATION) * 100, 100);
        
        setProgress(newProgress);

        if (newProgress < 100) {
            requestRef.current = requestAnimationFrame(animate);
        } else {
            onConfirm();
            setIsPressed(false); // Reset visual state but action fired
        }
    };

    const startPress = () => {
        setIsPressed(true);
        startTimeRef.current = 0;
        requestRef.current = requestAnimationFrame(animate);
    };

    const endPress = () => {
        setIsPressed(false);
        if (requestRef.current) {
            cancelAnimationFrame(requestRef.current);
        }
        setProgress(0);
    };

    return (
        <button
            className="relative w-full overflow-hidden rounded-xl bg-red-900/20 border border-red-500/50 h-16 md:h-14 group select-none touch-none active:scale-95 transition-transform"
            onMouseDown={startPress}
            onMouseUp={endPress}
            onMouseLeave={endPress}
            onTouchStart={startPress}
            onTouchEnd={endPress}
            style={{ WebkitUserSelect: 'none' }} // Prevent text selection on mobile
        >
            {/* Background Fill Animation */}
            <div 
                className="absolute inset-0 bg-red-600 transition-all ease-linear"
                style={{ width: `${progress}%` }}
            ></div>
            
            {/* Content Layer */}
            <div className="absolute inset-0 flex items-center justify-center gap-2 z-10 pointer-events-none">
                <Fingerprint size={24} className={`transition-colors ${progress > 50 ? 'text-white' : 'text-red-500'}`} />
                <span className={`text-xs md:text-sm font-bold uppercase tracking-widest transition-colors ${progress > 50 ? 'text-white' : 'text-red-400'}`}>
                    {progress === 100 ? '¡CONFIRMADO!' : isPressed ? 'MANTÉN PRESIONADO...' : label}
                </span>
            </div>
        </button>
    );
};


export const UserProfile: React.FC<UserProfileProps> = ({ client, shopRules, globalOptions, userRole, userAppointments, onClose, onUpdatePreferences, onUpdateProfile, onCancelAppointment }) => {
  const [activeTab, setActiveTab] = useState<'history' | 'preferences'>('history');
  
  // Avatar Editing State
  const [isEditingAvatar, setIsEditingAvatar] = useState(false);
  const [tempAvatar, setTempAvatar] = useState(client.avatar || '');
  const [imgError, setImgError] = useState(false);

  // Cancellation Wizard State
  const [cancelStep, setCancelStep] = useState<'IDLE' | 'REASON' | 'CALL_CHECK' | 'CONFIRM' | 'SUCCESS'>('IDLE');
  const [selectedAppointmentId, setSelectedAppointmentId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState<string>('');
  const [otherReason, setOtherReason] = useState('');

  const cancellationReasons = [
      "Emergencia Personal",
      "Enfermedad",
      "Cambio de Horario Laboral",
      "Tráfico / Transporte",
      "Insatisfacción Servicio Anterior",
      "Otro Motivo"
  ];

  // Preferences Form State
  // Initialize with client preferences or empty remarks
  const [prefs, setPrefs] = useState<CutPreferences>(client.preferences || { remarks: '' });

  // Handle local dynamic options (custom additions that aren't in global yet but user wants to save)
  // We reconstruct the full option list by merging Global Options + User Selected Option (if it's not in global)
  const optionLists = useMemo(() => {
      const lists: Record<string, string[]> = {};
      
      globalOptions.forEach(cat => {
          const userVal = prefs[cat.id];
          const globalItems = cat.items;
          // Ensure user's value is in the list even if custom
          const merged = userVal && !globalItems.includes(userVal) 
              ? [...globalItems, userVal] 
              : globalItems;
          lists[cat.id] = Array.from(new Set(merged));
      });
      
      return lists;
  }, [globalOptions, prefs]);

  // Helper for dynamic local additions (visual only until saved)
  const handleAddOption = (categoryId: string, value: string) => {
      setPrefs(prev => ({
          ...prev,
          [categoryId]: value
      }));
  };

  const handleSavePrefs = () => {
    onUpdatePreferences(client.id, prefs);
    onClose();
  };
  
  const handleSaveAvatar = () => {
      onUpdateProfile({ avatar: tempAvatar });
      setIsEditingAvatar(false);
  };

  // Helper to map icon ID
  const getCategoryIcon = (id: string) => {
      switch(id) {
          case 'sides': return Hash;
          case 'top': return Scissors;
          case 'beard': return Smile;
          case 'finish': return Zap;
          default: return Tag;
      }
  };

  // --- CANCELLATION LOGIC ---
  const startCancellation = (id: string) => {
      setSelectedAppointmentId(id);
      setCancelStep('REASON');
      setCancelReason('');
      setOtherReason('');
  };

  const handleReasonSelection = (reason: string) => {
      setCancelReason(reason);
      setCancelStep('CALL_CHECK'); // Proceed to call check
  };

  const finalizeCancellation = () => {
      if (selectedAppointmentId) {
          const finalReason = cancelReason === 'Otro Motivo' ? otherReason : cancelReason;
          // Trigger Success Animation State
          setCancelStep('SUCCESS');
          
          // Actual Execution
          setTimeout(() => {
              onCancelAppointment(selectedAppointmentId, finalReason);
              // Wait a bit then close/reset
              setTimeout(() => {
                  setCancelStep('IDLE');
                  setSelectedAppointmentId(null);
              }, 2000);
          }, 1500); // Wait for animation
      }
  };

  const upcomingAppointments = userAppointments.sort((a,b) => a.startTime.getTime() - b.startTime.getTime());

  return (
    <div className="fixed inset-0 z-[60] bg-dark-900/90 backdrop-blur-xl flex items-center justify-center animate-in fade-in duration-300 md:p-6 overflow-hidden">
      
      {/* MAIN CONTAINER: Responsive Grid with strict overflow control */}
      <div className="bg-[#0a0a0a] w-full h-full md:h-[90vh] md:rounded-3xl shadow-2xl border border-white/5 flex flex-col md:flex-row max-w-7xl overflow-hidden relative">
        
        {/* === LEFT PANEL: IDENTITY (Sidebar) === */}
        <div className="w-full md:w-[320px] lg:w-[380px] bg-dark-900 border-b md:border-b-0 md:border-r border-white/5 flex flex-col shrink-0 relative max-h-[30vh] md:max-h-full">
            
            {/* Background Texture */}
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(240,180,41,0.05),transparent_40%)] pointer-events-none"></div>

            {/* Mobile Close Button (Top Right of Identity) */}
            <button 
                onClick={onClose} 
                className="absolute top-4 right-4 z-50 p-2 text-gray-500 hover:text-white md:hidden"
            >
                <X size={24} />
            </button>

            {/* Identity Content */}
            <div className="p-6 md:p-8 flex flex-col items-center text-center h-full overflow-y-auto custom-scrollbar">
                
                {/* Avatar */}
                <div className="relative group mb-5 shrink-0">
                    <div className="w-20 h-20 md:w-32 md:h-32 lg:w-40 lg:h-40 rounded-full border-[3px] border-dark-700 overflow-hidden relative shadow-2xl bg-dark-800 flex items-center justify-center transition-all group-hover:border-brand-500/50">
                        {isEditingAvatar && imgError ? (
                             <AlertCircle className="text-red-500 animate-pulse" size={32} />
                        ) : (
                             <img 
                                key={isEditingAvatar ? tempAvatar : client.avatar}
                                src={isEditingAvatar ? tempAvatar : (client.avatar || `https://ui-avatars.com/api/?name=${client.name}&background=random`)} 
                                alt={client.name} 
                                className="w-full h-full object-cover"
                                onError={() => setImgError(true)}
                                onLoad={() => setImgError(false)}
                            />
                        )}
                        {!isEditingAvatar && (
                            <button 
                                className="absolute inset-0 bg-black/60 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-all cursor-pointer backdrop-blur-[2px]"
                                onClick={() => { setIsEditingAvatar(true); setTempAvatar(client.avatar || ''); setImgError(false); }}
                            >
                                <Edit3 size={24} className="text-white mb-1" />
                                <span className="text-[9px] font-bold text-white uppercase tracking-widest">Editar</span>
                            </button>
                        )}
                    </div>
                    {!isEditingAvatar && (
                        <div className="absolute -bottom-2 left-1/2 transform -translate-x-1/2 bg-dark-800 border border-dark-600 text-brand-500 text-[10px] font-bold px-3 py-1 rounded-full flex items-center gap-1 shadow-lg whitespace-nowrap z-10">
                            <Star size={10} fill="currentColor" />
                            <span>{client.points} XP</span>
                        </div>
                    )}
                </div>

                {isEditingAvatar ? (
                    <div className="w-full animate-in slide-in-from-top-4 duration-300">
                        <AvatarSelector 
                            currentAvatar={tempAvatar}
                            name={client.name}
                            onAvatarChange={(url) => { setTempAvatar(url); setImgError(false); }}
                        />
                        <div className="grid grid-cols-2 gap-2 mt-3">
                             <button onClick={() => { setIsEditingAvatar(false); setTempAvatar(client.avatar || ''); }} className="py-2.5 bg-dark-800 text-gray-400 text-xs font-bold rounded-lg hover:text-white">Cancelar</button>
                             <button onClick={handleSaveAvatar} className="py-2.5 bg-brand-500 text-black text-xs font-bold rounded-lg hover:bg-brand-400 shadow-lg">Guardar</button>
                        </div>
                    </div>
                ) : (
                    <>
                        <h2 className="text-2xl md:text-3xl font-black text-white tracking-tight leading-tight mb-1">{client.name}</h2>
                        <div className="flex items-center gap-2 mb-6">
                            <span className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-white/5 text-gray-400 border border-white/5">
                                Miembro desde {client.joinDate.getFullYear()}
                            </span>
                        </div>

                        {/* Quick Stats Grid */}
                        <div className="grid grid-cols-2 gap-3 w-full">
                            <div className="bg-dark-800/50 p-3 rounded-xl border border-white/5 flex flex-col items-center justify-center hover:bg-dark-800 transition-colors">
                                <Scissors size={18} className="text-brand-500 mb-1 opacity-80"/>
                                <span className="text-lg font-bold text-white leading-none">{client.bookingHistory.length}</span>
                                <span className="text-[9px] text-gray-500 uppercase font-bold mt-1">Cortes</span>
                            </div>
                            <div className="bg-dark-800/50 p-3 rounded-xl border border-white/5 flex flex-col items-center justify-center hover:bg-dark-800 transition-colors">
                                <Calendar size={18} className="text-blue-500 mb-1 opacity-80"/>
                                <span className="text-lg font-bold text-white leading-none">
                                    {client.lastVisit ? client.lastVisit.toLocaleDateString('es-CR', {day:'numeric', month:'short'}) : '-'}
                                </span>
                                <span className="text-[9px] text-gray-500 uppercase font-bold mt-1">Última Visita</span>
                            </div>
                        </div>

                        {/* Decoration */}
                        <div className="mt-auto pt-8 hidden md:block opacity-30">
                            <Sparkles className="text-brand-500 animate-pulse" size={24} />
                        </div>
                    </>
                )}
            </div>
        </div>

        {/* === RIGHT PANEL: WORKSPACE === */}
        <div className="flex-1 flex flex-col min-w-0 bg-[#0f0f0f] relative h-full">
            
            {/* Desktop Close Button */}
            <button 
                onClick={onClose} 
                className="absolute top-4 right-4 z-50 p-2 bg-dark-800 text-gray-400 hover:text-white rounded-full hover:bg-red-500/20 hover:text-red-500 transition-all hidden md:flex items-center justify-center border border-white/5"
            >
                <X size={18} />
            </button>

            {/* Navigation Tabs */}
            <div className="flex items-center border-b border-white/5 bg-dark-900/30 px-6 pt-2 sticky top-0 z-40 backdrop-blur-md shrink-0">
                {[
                    { id: 'history', label: 'Mis Citas & Historial', icon: History },
                    { id: 'preferences', label: 'Mi Estilo', icon: Award }
                ].map((tab) => (
                    <button 
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id as any)}
                        className={`flex items-center gap-2 px-6 py-4 text-xs font-bold uppercase tracking-widest relative transition-colors ${activeTab === tab.id ? 'text-white' : 'text-gray-600 hover:text-gray-400'}`}
                    >
                        <tab.icon size={14} className={activeTab === tab.id ? 'text-brand-500' : 'opacity-50'} />
                        {tab.label}
                        {activeTab === tab.id && (
                            <div className="absolute bottom-0 left-0 w-full h-0.5 bg-brand-500 shadow-[0_0_10px_rgba(240,180,41,0.5)]"></div>
                        )}
                    </button>
                ))}
            </div>

            {/* Scrollable Content - flex-1 and min-h-0 is CRITICAL for scrolling to work inside flex */}
            <div className="flex-1 overflow-y-auto custom-scrollbar p-4 md:p-8 min-h-0">
                
                {activeTab === 'history' && (
                    <div className="max-w-3xl mx-auto space-y-6 animate-in slide-in-from-right-4 duration-300 pb-20">
                        {shopRules && (
                            <div className="bg-blue-500/5 border-l-2 border-blue-500 p-4 rounded-r-lg flex gap-3">
                                <Info size={16} className="text-blue-400 shrink-0 mt-0.5" />
                                <div>
                                    <h4 className="text-blue-400 font-bold text-xs uppercase mb-1">Información del Local</h4>
                                    <p className="text-xs text-gray-400 whitespace-pre-line leading-relaxed">{shopRules}</p>
                                </div>
                            </div>
                        )}

                        {/* UPCOMING APPOINTMENTS SECTION */}
                        {upcomingAppointments.length > 0 && (
                            <div className="space-y-4">
                                <div className="flex items-center gap-2 text-emerald-500 pb-2 border-b border-emerald-900/30">
                                    <Calendar size={12} />
                                    <span className="text-[10px] font-bold uppercase tracking-widest">Próxima Cita</span>
                                </div>
                                
                                {upcomingAppointments.map(apt => {
                                    const canCancel = canClientCancel(apt.startTime);
                                    const isBeingCancelled = selectedAppointmentId === apt.id && cancelStep !== 'IDLE';

                                    // Dynamic Height Class: Expands when cancelling to fit the wizard comfortably on mobile
                                    const containerHeightClass = isBeingCancelled 
                                        ? 'min-h-[450px] md:min-h-[400px] border-red-500/30' 
                                        : '';

                                    return (
                                        <div key={apt.id} className={`bg-dark-800/80 border border-emerald-500/30 rounded-xl p-5 relative overflow-hidden group transition-all duration-300 ${containerHeightClass}`}>
                                            
                                            {/* CANCELLATION OVERLAY - WIZARD */}
                                            {isBeingCancelled && (
                                                <div className="absolute inset-0 z-20 bg-dark-900/95 backdrop-blur-sm flex flex-col items-center justify-center p-4 md:p-6 text-center animate-in fade-in duration-300 overflow-y-auto custom-scrollbar">
                                                    
                                                    {/* STEP 1: REASON */}
                                                    {cancelStep === 'REASON' && (
                                                        <div className="w-full max-w-sm animate-in slide-in-from-bottom-4 my-auto">
                                                            <div className="flex justify-between items-center mb-4 sticky top-0 bg-dark-900/0 backdrop-blur-sm py-2 z-10">
                                                                <h4 className="text-white font-bold text-sm">¿Por qué deseas cancelar?</h4>
                                                                <button onClick={() => { setCancelStep('IDLE'); setSelectedAppointmentId(null); }} className="p-1"><X size={20} className="text-gray-500 hover:text-white"/></button>
                                                            </div>
                                                            <div className="space-y-2.5 pb-4">
                                                                {cancellationReasons.map(r => (
                                                                    <button 
                                                                        key={r}
                                                                        onClick={() => handleReasonSelection(r)}
                                                                        className="w-full text-left p-3.5 rounded-lg border border-dark-600 hover:border-brand-500 hover:bg-dark-800 text-xs text-gray-300 hover:text-white transition-all flex justify-between group/btn active:scale-[0.98]"
                                                                    >
                                                                        {r} <ChevronRight size={14} className="opacity-0 group-hover/btn:opacity-100 transition-opacity" />
                                                                    </button>
                                                                ))}
                                                            </div>
                                                            {cancelReason === 'Otro Motivo' && (
                                                                <textarea 
                                                                    className="w-full mt-2 bg-dark-800 border border-dark-600 rounded-lg p-3 text-xs text-white outline-none focus:border-brand-500 min-h-[80px]"
                                                                    placeholder="Describe el motivo..."
                                                                    value={otherReason}
                                                                    onChange={e => setOtherReason(e.target.value)}
                                                                />
                                                            )}
                                                        </div>
                                                    )}

                                                    {/* STEP 2: CALL CHECK */}
                                                    {cancelStep === 'CALL_CHECK' && (
                                                        <div className="w-full max-w-sm animate-in slide-in-from-right-4 my-auto">
                                                            <div className="w-12 h-12 bg-orange-500/20 rounded-full flex items-center justify-center mx-auto mb-4 text-orange-500">
                                                                <PhoneCall size={24} />
                                                            </div>
                                                            <h4 className="text-white font-bold text-lg mb-2">Protocolo de Cancelación</h4>
                                                            <p className="text-xs text-gray-400 mb-6 leading-relaxed px-2">
                                                                Para evitar penalizaciones en tu perfil, es obligatorio notificar verbalmente al barbero sobre esta cancelación de último momento.
                                                            </p>
                                                            <div className="space-y-3">
                                                                <button 
                                                                    onClick={() => setCancelStep('CONFIRM')}
                                                                    className="w-full bg-orange-600 hover:bg-orange-500 text-white py-3.5 rounded-xl font-bold text-xs uppercase tracking-wide transition-all shadow-lg active:scale-95"
                                                                >
                                                                    Ya llamé al Barbero
                                                                </button>
                                                                <button 
                                                                    onClick={() => { setCancelStep('IDLE'); setSelectedAppointmentId(null); }}
                                                                    className="w-full text-gray-500 hover:text-white py-3 text-xs"
                                                                >
                                                                    Cancelar proceso
                                                                </button>
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* STEP 3: PRESS TO HOLD CONFIRM */}
                                                    {cancelStep === 'CONFIRM' && (
                                                        <div className="w-full max-w-xs animate-in zoom-in-95 my-auto">
                                                            <div className="mb-6 flex flex-col items-center">
                                                                <AlertCircle size={32} className="text-red-500 mb-2" />
                                                                <h4 className="text-red-500 font-bold text-sm uppercase tracking-wider text-center">Confirmación Final</h4>
                                                            </div>
                                                            
                                                            <PressHoldButton 
                                                                onConfirm={finalizeCancellation} 
                                                                label="MANTÉN PARA CANCELAR"
                                                                isMobile={true}
                                                            />
                                                            
                                                            <p className="text-[10px] text-gray-500 mt-6 text-center">
                                                                Esta acción no se puede deshacer.
                                                            </p>
                                                            <button 
                                                                onClick={() => { setCancelStep('IDLE'); setSelectedAppointmentId(null); }}
                                                                className="mt-4 text-gray-500 hover:text-white text-xs underline w-full py-2"
                                                            >
                                                                Volver atrás
                                                            </button>
                                                        </div>
                                                    )}

                                                    {/* STEP 4: SUCCESS ANIMATION */}
                                                    {cancelStep === 'SUCCESS' && (
                                                        <div className="flex flex-col items-center justify-center animate-in zoom-in duration-300 my-auto">
                                                            <div className="w-20 h-20 rounded-full border-4 border-emerald-500 flex items-center justify-center mb-4 relative">
                                                                <Check size={40} className="text-emerald-500" strokeWidth={4} />
                                                                <div className="absolute inset-0 rounded-full border-4 border-emerald-500 animate-ping opacity-75"></div>
                                                            </div>
                                                            <h3 className="text-xl font-bold text-white">Cancelación Exitosa</h3>
                                                            <p className="text-xs text-gray-500 mt-2">Tu agenda ha sido actualizada.</p>
                                                        </div>
                                                    )}

                                                </div>
                                            )}

                                            <div className="absolute top-0 right-0 p-4 opacity-10 pointer-events-none">
                                                <Clock size={80} className="text-emerald-500" />
                                            </div>
                                            
                                            {/* Normal Card Content */}
                                            <div className="flex justify-between items-start relative z-10">
                                                <div>
                                                    <div className="text-xs text-emerald-400 font-bold uppercase mb-1 flex items-center gap-2">
                                                        <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></div> Confirmada
                                                    </div>
                                                    <h3 className="text-xl font-bold text-white capitalize">
                                                        {apt.startTime.toLocaleDateString('es-CR', { weekday: 'long', day: 'numeric', month: 'long' })}
                                                    </h3>
                                                    <div className="text-3xl font-mono font-black text-white mt-1">
                                                        {formatTime(apt.startTime)}
                                                    </div>
                                                </div>
                                                {canCancel ? (
                                                    <button 
                                                        onClick={() => startCancellation(apt.id)}
                                                        className="bg-dark-900 hover:bg-red-900/30 border border-dark-600 hover:border-red-500/50 text-gray-400 hover:text-red-400 px-3 py-2 rounded-lg text-xs font-bold transition-all flex items-center gap-2"
                                                    >
                                                        <Trash2 size={14} /> Cancelar
                                                    </button>
                                                ) : (
                                                    <div className="flex flex-col items-end gap-1">
                                                        <div className="inline-flex items-center gap-2 text-[10px] text-orange-400 bg-orange-900/20 px-2 py-1 rounded border border-orange-900/40 relative z-10 font-bold">
                                                            <AlertTriangle size={10} /> 
                                                            Restricción de Tiempo
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                            {!canCancel && (
                                                <p className="text-[10px] text-gray-500 mt-3 relative z-10 border-t border-white/5 pt-2">
                                                    No se puede cancelar, solo <span className="text-white font-bold">45 minutos</span> antes de la cita y se requiere llamar al barbero.
                                                </p>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        )}


                        <div className="space-y-4 pt-6">
                            <div className="flex items-center gap-2 text-gray-500 pb-2 border-b border-white/5">
                                <Layout size={12} />
                                <span className="text-[10px] font-bold uppercase tracking-widest">Historial de Servicios</span>
                            </div>

                            {client.bookingHistory.length === 0 ? (
                                <div className="text-center py-12 border border-dashed border-dark-700 rounded-2xl bg-dark-800/20">
                                    <p className="text-gray-500 text-sm">No hay servicios anteriores registrados.</p>
                                </div>
                            ) : (
                                client.bookingHistory.map((item) => (
                                    <div key={item.id} className="flex gap-4 group">
                                        {/* Timeline connector */}
                                        <div className="flex flex-col items-center">
                                            <div className="w-2.5 h-2.5 rounded-full bg-dark-600 border border-dark-500 group-hover:bg-brand-500 group-hover:border-brand-400 transition-colors z-10"></div>
                                            <div className="w-px h-full bg-dark-700 -mt-1 group-last:hidden"></div>
                                        </div>
                                        
                                        {/* Card */}
                                        <div className="flex-1 bg-dark-800/40 border border-white/5 rounded-xl p-4 hover:bg-dark-800 transition-all hover:border-brand-500/20 mb-2">
                                            <div className="flex justify-between items-start mb-2">
                                                <div>
                                                    <h4 className="font-bold text-white text-sm">{item.serviceName}</h4>
                                                    <p className="text-[10px] text-gray-500 flex items-center gap-1.5 mt-1">
                                                        <Calendar size={10} />
                                                        {item.date.toLocaleDateString('es-CR', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' })}
                                                    </p>
                                                </div>
                                                <div className="text-right">
                                                    <span className="block font-mono font-bold text-brand-500 text-sm">₡{item.price.toLocaleString('es-CR')}</span>
                                                    <span className="text-[9px] text-emerald-500/80 uppercase font-bold">Completado</span>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-2 mt-3 pt-3 border-t border-white/5">
                                                <div className="w-5 h-5 rounded-full bg-dark-700 flex items-center justify-center text-[10px] text-gray-400">
                                                    <User size={10} />
                                                </div>
                                                <span className="text-xs text-gray-400">{item.barberName}</span>
                                            </div>
                                            {item.notes && (
                                                <div className="mt-2 bg-black/20 p-2 rounded text-[11px] text-gray-400 italic flex gap-2">
                                                    <MessageSquare size={12} className="shrink-0 mt-0.5" />
                                                    "{item.notes}"
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                )}

                {activeTab === 'preferences' && (
                    <div className="max-w-5xl mx-auto pb-10 animate-in slide-in-from-right-4 duration-300">
                        <div className="flex flex-col md:flex-row gap-4 mb-6 items-start md:items-center justify-between border-b border-white/5 pb-6">
                            <div>
                                <h2 className="text-xl font-bold text-white mb-1">Ficha Técnica de Estilo</h2>
                                <p className="text-xs text-gray-400 max-w-lg">
                                    Configura tus preferencias visuales. Tu barbero consultará esta guía antes de cada corte.
                                </p>
                            </div>
                            <button 
                                onClick={handleSavePrefs}
                                className="bg-brand-500 text-black px-6 py-2.5 rounded-lg font-bold text-xs uppercase tracking-wide hover:bg-brand-400 shadow-[0_0_20px_rgba(240,180,41,0.2)] transition-all flex items-center gap-2"
                            >
                                <Save size={16} /> Guardar Ficha
                            </button>
                        </div>

                        {/* BENTO GRID LAYOUT - DYNAMIC MAPPING */}
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                            
                            {/* REMARKS (Always fixed first) */}
                            <div className="col-span-1 md:col-span-2 lg:col-span-2 row-span-2">
                                <div className="h-full bg-dark-800/40 border border-white/5 rounded-xl p-5 hover:border-brand-500/30 transition-colors group flex flex-col">
                                    <div className="flex items-center gap-2 mb-4">
                                        <div className="p-1.5 rounded-lg bg-dark-700 text-brand-500"><MessageSquare size={16}/></div>
                                        <label className="text-sm text-gray-200 font-bold uppercase tracking-wide">Observaciones Clave</label>
                                    </div>
                                    <textarea 
                                        className="flex-1 w-full bg-dark-900 border border-dark-700 rounded-lg p-4 text-sm text-white focus:border-brand-500 focus:outline-none resize-none placeholder-gray-600 leading-relaxed"
                                        placeholder="Ej: Tengo un remolino en la coronilla, cuidado con la cicatriz en la ceja izquierda, prefiero las patillas en punta..."
                                        value={prefs.remarks}
                                        onChange={(e) => setPrefs({...prefs, remarks: e.target.value})}
                                    />
                                    <p className="text-[10px] text-gray-500 mt-2 text-right">Visible para el barbero en la cita.</p>
                                </div>
                            </div>
                            
                            {/* DYNAMIC CATEGORIES */}
                            {globalOptions.map((category) => (
                                <div key={category.id} className="lg:col-span-2">
                                    <StyleSelector 
                                        title={category.label}
                                        icon={getCategoryIcon(category.id)}
                                        value={prefs[category.id] || ''}
                                        options={optionLists[category.id] || []}
                                        onSelect={(val) => setPrefs({...prefs, [category.id]: val})}
                                        onAddCustom={(val) => handleAddOption(category.id, val)}
                                    />
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
      </div>
    </div>
  );
};