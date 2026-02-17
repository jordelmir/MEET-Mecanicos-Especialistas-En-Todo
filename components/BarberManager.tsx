
import React, { useState } from 'react';
import { Barber } from '../types';
import { X, Plus, Edit2, Trash2, Save, User, Timer, Award, AlertTriangle, Key, Mail, Fingerprint, RefreshCw, Phone } from 'lucide-react';
import { AvatarSelector } from './AvatarSelector';

interface BarberManagerProps {
  barbers: Barber[];
  onAdd: (barber: Omit<Barber, 'id'>) => void;
  onUpdate: (barber: Barber) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

export const BarberManager: React.FC<BarberManagerProps> = ({ barbers, onAdd, onUpdate, onDelete, onClose }) => {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  
  // Form State
  const initialFormState = { 
      name: '', 
      email: '',
      phone: '',
      identification: '',
      accessCode: '',
      tier: 'JUNIOR' as 'JUNIOR' | 'SENIOR' | 'MASTER', 
      speedFactor: 1.0, 
      avatar: '' 
  };
  const [formData, setFormData] = useState(initialFormState);
  // New State to handle preview error locally in list view
  const [previewError, setPreviewError] = useState(false);

  const generateAccessCode = () => {
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      setFormData(prev => ({ ...prev, accessCode: code }));
  };

  const resetForm = () => {
      setFormData(initialFormState);
      setEditingId(null);
      setError(null);
      setPreviewError(false);
  };

  const handleEditClick = (barber: Barber) => {
      setEditingId(barber.id);
      setFormData({ 
          name: barber.name, 
          email: barber.email,
          phone: barber.phone || '',
          identification: barber.identification,
          accessCode: barber.accessCode,
          tier: barber.tier, 
          speedFactor: barber.speedFactor, 
          avatar: barber.avatar 
      });
      setError(null);
      setPreviewError(false);
  };

  const handleDeleteClick = (id: string) => {
      try {
          onDelete(id);
      } catch (err: any) {
          setError(err.message);
          setTimeout(() => setError(null), 5000);
      }
  };

  const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (!formData.name || !formData.identification) {
          setError("Nombre y Cédula son obligatorios");
          return;
      }
      
      if (!formData.accessCode) {
          setError("Debes generar un código de acceso antes de guardar.");
          return;
      }

      const avatarToUse = formData.avatar || `https://api.dicebear.com/9.x/avataaars/svg?seed=${formData.name}&backgroundColor=1a1a1a&radius=50`;
      
      if (editingId) {
          onUpdate({ id: editingId, ...formData, avatar: avatarToUse });
      } else {
          onAdd({ ...formData, avatar: avatarToUse });
      }
      resetForm();
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="bg-dark-800 rounded-xl border border-dark-600 shadow-2xl w-full max-w-2xl overflow-hidden flex flex-col max-h-[85vh]">
        
        {/* Header */}
        <div className="p-5 border-b border-dark-700 flex justify-between items-center bg-dark-900/50">
            <div className="flex items-center gap-3">
                <div className="bg-purple-500/20 p-2 rounded-lg text-purple-500">
                    <User size={20} />
                </div>
                <div>
                    <h2 className="text-xl font-bold text-white">Gestión de Equipo</h2>
                    <p className="text-xs text-gray-500">Administrar barberos y credenciales de acceso</p>
                </div>
            </div>
            <button onClick={onClose} className="text-gray-400 hover:text-white transition-colors">
                <X size={24} />
            </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto custom-scrollbar p-6">
            
            {/* Error Message */}
            {error && (
                <div className="bg-red-500/10 border border-red-500/50 p-3 rounded-lg flex items-center gap-3 mb-4 animate-in slide-in-from-top-2">
                    <AlertTriangle className="text-red-500 shrink-0" size={18} />
                    <p className="text-sm text-red-200">{error}</p>
                </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit} className="bg-dark-700/30 p-4 rounded-xl border border-dark-600 mb-6 transition-all focus-within:border-purple-500/50">
                <h3 className="text-sm font-bold text-gray-300 uppercase mb-3 flex items-center gap-2">
                    {editingId ? <Edit2 size={14}/> : <Plus size={14}/>} 
                    {editingId ? 'Editar Barbero' : 'Contratar Nuevo Talento'}
                </h3>
                
                <div className="flex flex-col md:flex-row gap-4 mb-4">
                    {/* Avatar Column - REPLACED WITH AVATAR SELECTOR */}
                    <div className="w-full md:w-1/3 shrink-0 flex flex-col items-center gap-2">
                        <div className="w-24 h-24 rounded-full bg-dark-600 border-2 border-dark-500 overflow-hidden relative group flex items-center justify-center mb-2">
                            {formData.avatar && !previewError ? (
                                <img 
                                    src={formData.avatar} 
                                    alt="Avatar" 
                                    className="w-full h-full object-cover" 
                                    onError={() => setPreviewError(true)}
                                />
                            ) : (
                                previewError ? <AlertTriangle className="text-red-500" size={24}/> : <User className="text-gray-500" size={32} />
                            )}
                        </div>
                        
                        <AvatarSelector 
                            currentAvatar={formData.avatar}
                            name={formData.name || 'Barber'}
                            onAvatarChange={(url) => {
                                setFormData({...formData, avatar: url});
                                setPreviewError(false);
                            }}
                        />
                    </div>

                    {/* Fields Column */}
                    <div className="flex-1 space-y-3">
                        
                        {/* IDENTITY CONTAINER */}
                        <div className="bg-dark-900/50 border border-dark-600 rounded-lg p-3 space-y-3">
                            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">Datos Personales y Contacto</label>
                            
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                <div className="md:col-span-2">
                                    <input 
                                        type="text" 
                                        placeholder="Nombre Completo" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg px-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm"
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        required
                                    />
                                </div>
                                <div className="relative">
                                    <Fingerprint size={14} className="absolute left-3 top-3 text-gray-500" />
                                    <input 
                                        type="text" 
                                        placeholder="Cédula" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm font-mono"
                                        value={formData.identification}
                                        onChange={(e) => setFormData({...formData, identification: e.target.value.replace(/\s/g, '')})}
                                        required
                                    />
                                </div>
                                <div className="relative">
                                    <Phone size={14} className="absolute left-3 top-3 text-purple-500" />
                                    <input 
                                        type="text" 
                                        placeholder="Teléfono" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm"
                                        value={formData.phone}
                                        onChange={(e) => setFormData({...formData, phone: e.target.value})}
                                    />
                                </div>
                            </div>
                        </div>

                        {/* OTHER DETAILS */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                            <div className="relative col-span-2 md:col-span-1">
                                <Mail size={14} className="absolute left-3 top-3 text-gray-500" />
                                <input 
                                    type="email" 
                                    placeholder="Correo Electrónico" 
                                    className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm"
                                    value={formData.email}
                                    onChange={(e) => setFormData({...formData, email: e.target.value})}
                                />
                            </div>
                            
                            <div className="relative">
                                <Award size={14} className="absolute left-3 top-3 text-gray-500" />
                                <select 
                                    className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm appearance-none"
                                    value={formData.tier}
                                    onChange={(e) => setFormData({...formData, tier: e.target.value as any})}
                                >
                                    <option value="JUNIOR">Junior (Aprendiz)</option>
                                    <option value="SENIOR">Senior (Experimentado)</option>
                                    <option value="MASTER">Master (Elite)</option>
                                </select>
                            </div>

                            <div className="relative">
                                <label className="text-[9px] font-bold text-gray-400 uppercase tracking-wider block mb-1 flex items-center gap-1">
                                    Factor de Tiempo <span className="text-gray-600 font-normal normal-case">(1.0 = Estándar)</span>
                                </label>
                                <div className="relative">
                                    <Timer size={14} className="absolute left-3 top-3 text-gray-500" />
                                    <input 
                                        type="number" 
                                        placeholder="1.0" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-purple-500 focus:outline-none text-sm font-mono"
                                        value={formData.speedFactor}
                                        onChange={(e) => setFormData({...formData, speedFactor: parseFloat(e.target.value) || 1.0})}
                                        step="0.1"
                                        min="0.5"
                                        max="2.0"
                                    />
                                </div>
                                <p className="text-[9px] text-gray-500 mt-1.5 leading-tight">
                                    Define el promedio de <strong>minutos por corte</strong>. 
                                    <br/>Ej: 1.2 = 20% más lento (más minutos).
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Credentials Display */}
                <div className="bg-dark-800/50 p-3 rounded-lg border border-purple-500/30 flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <div className="bg-purple-500/20 p-1.5 rounded text-purple-400"><Key size={14}/></div>
                        <div>
                            <div className="text-[10px] text-gray-400 uppercase font-bold">Código de Acceso</div>
                            <div className="text-xs text-gray-500">Requerido para iniciar sesión</div>
                        </div>
                    </div>
                    
                    {formData.accessCode ? (
                        <div className="flex items-center gap-3">
                            <span className="font-mono text-xl font-bold text-purple-400 tracking-widest bg-dark-900 px-3 py-1 rounded border border-dark-700">
                                {formData.accessCode}
                            </span>
                            <button 
                                type="button" 
                                onClick={generateAccessCode}
                                className="p-2 bg-dark-700 hover:bg-dark-600 rounded-lg text-gray-400 hover:text-white transition-colors"
                                title="Regenerar Código"
                            >
                                <RefreshCw size={14} />
                            </button>
                        </div>
                    ) : (
                        <button 
                            type="button" 
                            onClick={generateAccessCode}
                            className="bg-purple-600/20 hover:bg-purple-600/30 text-purple-400 border border-purple-500/50 px-3 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center gap-2"
                        >
                            <Key size={12} /> Generar Credenciales
                        </button>
                    )}
                </div>

                <div className="flex justify-end gap-2 mt-3">
                    {editingId && (
                        <button type="button" onClick={resetForm} className="text-xs text-gray-400 hover:text-white px-3 py-2">
                            Cancelar
                        </button>
                    )}
                    <button type="submit" className="bg-purple-600 text-white text-xs font-bold px-4 py-2 rounded-lg hover:bg-purple-500 flex items-center gap-2 shadow-lg shadow-purple-600/20">
                        <Save size={14} /> {editingId ? 'Actualizar Perfil' : 'Contratar Barbero'}
                    </button>
                </div>
            </form>

            {/* List */}
            <div className="space-y-2">
                {barbers.map(barber => (
                    <div key={barber.id} className="group flex items-center justify-between p-3 rounded-lg border border-dark-700 bg-dark-800/50 hover:border-dark-500 transition-all">
                        <div className="flex items-center gap-4">
                            <img src={barber.avatar} alt={barber.name} className="w-10 h-10 rounded-full border border-dark-600 object-cover" />
                            <div>
                                <div className="font-medium text-white text-sm flex items-center gap-2">
                                    {barber.name}
                                    <span className={`text-[9px] px-1.5 py-0.5 rounded border ${
                                        barber.tier === 'MASTER' ? 'bg-yellow-500/10 border-yellow-500 text-yellow-500' :
                                        barber.tier === 'SENIOR' ? 'bg-blue-500/10 border-blue-500 text-blue-400' :
                                        'bg-gray-700 border-gray-600 text-gray-400'
                                    }`}>
                                        {barber.tier}
                                    </span>
                                </div>
                                <div className="text-xs text-gray-500 flex flex-col gap-0.5 mt-0.5 font-mono">
                                    <span>ID: {barber.identification}</span>
                                    {barber.phone && <span className="flex items-center gap-1 text-purple-400"><Phone size={10} /> {barber.phone}</span>}
                                </div>
                            </div>
                        </div>
                        <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button 
                                onClick={() => handleEditClick(barber)}
                                className="p-2 text-gray-400 hover:text-purple-500 hover:bg-dark-700 rounded-lg"
                                title="Editar"
                            >
                                <Edit2 size={16} />
                            </button>
                            <button 
                                onClick={() => handleDeleteClick(barber.id)}
                                className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-900/20 rounded-lg"
                                title="Eliminar"
                            >
                                <Trash2 size={16} />
                            </button>
                        </div>
                    </div>
                ))}
            </div>

        </div>
      </div>
    </div>
  );
};
