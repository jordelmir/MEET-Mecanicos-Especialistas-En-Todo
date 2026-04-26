import React, { useState } from 'react';
import { Client, Mechanic, Role } from '../types';
import { User, Mail, Phone, Hash, Save, X, Edit3, Camera, MapPin, Calendar, Award, Wrench } from 'lucide-react';

interface UserProfileModalProps {
  user: any; // Client or Mechanic
  role: Role;
  onClose: () => void;
  onUpdateUser: (updatedData: any) => void;
}

export function UserProfileModal({ user, role, onClose, onUpdateUser }: UserProfileModalProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [form, setForm] = useState({
    name: user.name || '',
    phone: user.phone || '',
    email: user.email || '',
    identification: user.identification || '',
    avatar: user.avatar || ''
  });

  const handleSave = () => {
    onUpdateUser({ ...user, ...form });
    setIsEditing(false);
  };

  const getRoleLabel = () => {
    switch (role) {
      case Role.ADMIN: return 'Administrador';
      case Role.MECHANIC: return 'Mecánico Especialista';
      case Role.CLIENT: return 'Cliente VIP';
      default: return 'Usuario';
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-[100] flex items-center justify-center p-4 animate-slide-up">
      <div className="w-full max-w-md glass rounded-2xl overflow-hidden relative shadow-[0_0_50px_rgba(0,240,255,0.1)] border border-forge-500/20">
        
        {/* Cover / Header */}
        <div className="h-32 bg-gradient-to-br from-steel-800 to-steel-900 relative border-b border-white/5">
          <div className="absolute inset-0 opacity-20 industrial-grid"></div>
          <button onClick={onClose} className="absolute top-4 right-4 px-3 py-1.5 bg-black/40 backdrop-blur hover:bg-white/10 rounded-full text-white transition-all flex items-center gap-1.5">
            <span className="text-xs font-mono font-bold">Cerrar</span>
            <X size={14} />
          </button>
        </div>

        {/* Avatar */}
        <div className="relative px-6">
          <div className="absolute -top-12 border-4 border-[#030508] bg-steel-800 rounded-full w-24 h-24 flex items-center justify-center overflow-hidden shadow-xl group">
            {form.avatar ? (
              <img src={form.avatar} alt="Profile" className="w-full h-full object-cover" />
            ) : (
              <User size={40} className="text-forge-500" />
            )}
            {isEditing && (
              <div className="absolute inset-0 bg-black/60 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
                <Camera size={24} className="text-white" />
              </div>
            )}
          </div>
          
          <div className="flex justify-end pt-4 pb-2">
            {!isEditing ? (
              <button onClick={() => setIsEditing(true)} className="flex items-center gap-2 px-3 py-1.5 glass-inner rounded-full text-forge-500 hover:text-white hover:bg-forge-500/20 text-xs font-bold transition-all">
                <Edit3 size={14} /> Editar Perfil
              </button>
            ) : (
              <button onClick={handleSave} className="flex items-center gap-2 px-4 py-1.5 bg-forge-500 text-black rounded-full text-xs font-bold shadow-[0_0_15px_rgba(0,240,255,0.4)] transition-all">
                <Save size={14} /> Guardar
              </button>
            )}
          </div>
        </div>

        {/* Info */}
        <div className="px-6 pb-6 pt-2">
          {!isEditing ? (
            <div className="space-y-6">
              <div>
                <h2 className="text-2xl font-bold text-white tracking-tight">{user.name}</h2>
                <div className="text-sm font-mono text-forge-500 mt-1 uppercase tracking-wider">{getRoleLabel()}</div>
              </div>
              
              <div className="space-y-3">
                <div className="flex items-center gap-3 text-sm text-gray-300 bg-white/5 p-3 rounded-xl border border-white/5">
                  <Mail size={16} className="text-forge-400" />
                  <span className="truncate">{user.email}</span>
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-300 bg-white/5 p-3 rounded-xl border border-white/5">
                  <Phone size={16} className="text-forge-400" />
                  <span>{user.phone}</span>
                </div>
                <div className="flex items-center gap-3 text-sm text-gray-300 bg-white/5 p-3 rounded-xl border border-white/5">
                  <Hash size={16} className="text-forge-400" />
                  <span className="font-mono">{user.identification}</span>
                </div>
                
                {role === Role.CLIENT && (
                  <div className="flex items-center gap-3 text-sm text-gray-300 bg-white/5 p-3 rounded-xl border border-white/5">
                    <Award size={16} className="text-yellow-400" />
                    <span>{user.loyaltyPoints || 0} Puntos de Lealtad</span>
                  </div>
                )}
                
                {role === Role.MECHANIC && (
                  <div className="flex items-center gap-3 text-sm text-gray-300 bg-white/5 p-3 rounded-xl border border-white/5">
                    <Wrench size={16} className="text-forge-400" />
                    <span>Especialidad: {user.specialty}</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1 block">Nombre Completo</label>
                <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} className="w-full bg-steel-900 border border-steel-600 rounded-lg px-4 py-2.5 text-white focus:border-forge-500 outline-none transition-all" />
              </div>
              <div>
                <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1 block">Correo Electrónico</label>
                <input value={form.email} onChange={e => setForm({...form, email: e.target.value})} className="w-full bg-steel-900 border border-steel-600 rounded-lg px-4 py-2.5 text-white focus:border-forge-500 outline-none transition-all" />
              </div>
              <div>
                <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1 block">Teléfono</label>
                <input value={form.phone} onChange={e => setForm({...form, phone: e.target.value})} className="w-full bg-steel-900 border border-steel-600 rounded-lg px-4 py-2.5 text-white focus:border-forge-500 outline-none transition-all" />
              </div>
              <div>
                <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1 block">Identificación (Cédula)</label>
                <input value={form.identification} onChange={e => setForm({...form, identification: e.target.value})} className="w-full bg-steel-900 border border-steel-600 rounded-lg px-4 py-2.5 text-white focus:border-forge-500 outline-none transition-all" />
              </div>
              <div>
                <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1 block">URL del Avatar (Opcional)</label>
                <input value={form.avatar} onChange={e => setForm({...form, avatar: e.target.value})} className="w-full bg-steel-900 border border-steel-600 rounded-lg px-4 py-2.5 text-white focus:border-forge-500 outline-none transition-all" placeholder="https://..." />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
