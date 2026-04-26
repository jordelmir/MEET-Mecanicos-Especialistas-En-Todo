
import React, { useState } from 'react';
import { Mechanic } from '../types';
import { X, Plus, Pencil, Trash2, Users } from 'lucide-react';

interface MechanicManagerProps {
  mechanics: Mechanic[];
  onAdd: (data: Omit<Mechanic, 'id'>) => void;
  onUpdate: (mech: Mechanic) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

const SPECIALTIES = ['GENERAL', 'MOTOR', 'ELECTRICO', 'TRANSMISION', 'SUSPENSION', 'DIESEL'] as const;

export function MechanicManager({ mechanics, onAdd, onUpdate, onDelete, onClose }: MechanicManagerProps) {
  const [editing, setEditing] = useState<Mechanic | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [form, setForm] = useState({
    name: '', phone: '', identification: '', accessCode: '', email: '',
    specialty: 'GENERAL' as Mechanic['specialty'],
    efficiencyFactor: 1.0,
    avatar: '',
    certifications: [] as string[],
  });

  const handleSave = () => {
    if (!form.name || !form.identification || !form.accessCode) return;
    const avatarUrl = form.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(form.name)}&background=e8a020&color=000&bold=true&size=150`;
    if (editing) {
      onUpdate({ ...editing, ...form, avatar: avatarUrl });
    } else {
      onAdd({ ...form, avatar: avatarUrl });
    }
    resetForm();
  };

  const resetForm = () => {
    setForm({ name: '', phone: '', identification: '', accessCode: '', email: '', specialty: 'GENERAL', efficiencyFactor: 1.0, avatar: '', certifications: [] });
    setEditing(null);
    setIsAdding(false);
  };

  const startEdit = (mech: Mechanic) => {
    setEditing(mech);
    setForm({ ...mech });
    setIsAdding(true);
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg glass rounded-2xl overflow-hidden">
        <div className="p-5 border-b border-white/5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Users size={20} className="text-forge-500" />
            Gestión de Mecánicos
          </h2>
          <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="p-4 max-h-[50vh] overflow-y-auto space-y-2">
          {mechanics.map(mech => (
            <div key={mech.id} className="glass-inner rounded-xl p-3 flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg overflow-hidden border border-steel-500 flex-shrink-0">
                <img src={mech.avatar} alt={mech.name} className="w-full h-full object-cover" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold text-white truncate">{mech.name}</div>
                <div className="font-mono text-[10px] text-forge-500 uppercase">{mech.specialty} · {Math.round(mech.efficiencyFactor * 100)}%</div>
              </div>
              <button onClick={() => startEdit(mech)} className="p-1.5 rounded text-steel-300 hover:text-forge-500 transition-colors">
                <Pencil size={14} />
              </button>
              <button onClick={() => onDelete(mech.id)} className="p-1.5 rounded text-steel-300 hover:text-red-400 transition-colors">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>

        {isAdding ? (
          <div className="p-4 border-t border-white/5 space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="Nombre completo"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
              <input value={form.phone} onChange={e => setForm({...form, phone: e.target.value})} placeholder="Teléfono"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <input value={form.identification} onChange={e => setForm({...form, identification: e.target.value})} placeholder="Cédula"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
              <input value={form.accessCode} onChange={e => setForm({...form, accessCode: e.target.value})} placeholder="Código acceso" maxLength={6}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <select value={form.specialty} onChange={e => setForm({...form, specialty: e.target.value as Mechanic['specialty']})}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none">
                {SPECIALTIES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
              <input type="number" step="0.05" min="0.5" max="2" value={form.efficiencyFactor} onChange={e => setForm({...form, efficiencyFactor: +e.target.value})} placeholder="Eficiencia"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            </div>
            <input value={form.email} onChange={e => setForm({...form, email: e.target.value})} placeholder="Email"
              className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            <div className="flex gap-2">
              <button onClick={handleSave} className="flex-1 bg-forge-500 text-black font-bold py-2 rounded-lg font-mono text-xs">
                {editing ? 'Actualizar' : 'Agregar'} Mecánico
              </button>
              <button onClick={resetForm} className="px-4 py-2 rounded-lg glass-inner text-steel-200 font-mono text-xs">Cancelar</button>
            </div>
          </div>
        ) : (
          <div className="p-4 border-t border-white/5">
            <button onClick={() => setIsAdding(true)} className="w-full flex items-center justify-center gap-2 glass-inner glass-hover rounded-lg py-2.5 font-mono text-xs text-forge-500 font-bold">
              <Plus size={14} /> Agregar Mecánico
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
