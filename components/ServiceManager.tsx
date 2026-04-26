
import React, { useState } from 'react';
import { Service, ServiceCategory } from '../types';
import { formatDuration } from '../services/timeEngine';
import { X, Plus, Pencil, Trash2, Wrench } from 'lucide-react';

interface ServiceManagerProps {
  services: Service[];
  onAdd: (data: Omit<Service, 'id'>) => void;
  onUpdate: (service: Service) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

export function ServiceManager({ services, onAdd, onUpdate, onDelete, onClose }: ServiceManagerProps) {
  const [editing, setEditing] = useState<Service | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [form, setForm] = useState({ name: '', category: ServiceCategory.MANT as ServiceCategory, estimatedMinutes: 60, basePrice: 0, description: '' });

  const handleSave = () => {
    if (!form.name) return;
    if (editing) {
      onUpdate({ ...editing, ...form });
    } else {
      onAdd(form);
    }
    setForm({ name: '', category: ServiceCategory.MANT, estimatedMinutes: 60, basePrice: 0, description: '' });
    setEditing(null);
    setIsAdding(false);
  };

  const startEdit = (svc: Service) => {
    setEditing(svc);
    setForm({ name: svc.name, category: svc.category, estimatedMinutes: svc.estimatedMinutes, basePrice: svc.basePrice, description: svc.description || '' });
    setIsAdding(true);
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-lg glass rounded-2xl overflow-hidden">
        <div className="p-5 border-b border-white/5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Wrench size={20} className="text-forge-500" />
            Gestión de Servicios
          </h2>
          <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="p-4 max-h-[60vh] overflow-y-auto space-y-2">
          {services.map(svc => (
            <div key={svc.id} className="glass-inner rounded-xl p-3 flex items-center gap-3">
              <div className={`px-2 py-1 rounded text-[9px] font-mono font-bold uppercase badge-${svc.category}`}>
                {svc.category}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-white truncate">{svc.name}</div>
                <div className="font-mono text-[10px] text-steel-300">{formatDuration(svc.estimatedMinutes)} · ₡{svc.basePrice.toLocaleString()}</div>
              </div>
              <button onClick={() => startEdit(svc)} className="p-1.5 rounded text-steel-300 hover:text-forge-500 transition-colors">
                <Pencil size={14} />
              </button>
              <button onClick={() => onDelete(svc.id)} className="p-1.5 rounded text-steel-300 hover:text-red-400 transition-colors">
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>

        {/* Add/Edit Form */}
        {isAdding ? (
          <div className="p-4 border-t border-white/5 space-y-3">
            <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="Nombre del servicio"
              className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            <div className="grid grid-cols-3 gap-2">
              <select value={form.category} onChange={e => setForm({...form, category: e.target.value as ServiceCategory})}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none">
                <option value="mant">Mantenimiento</option>
                <option value="rep">Reparación</option>
                <option value="cam">Cambio</option>
                <option value="diag">Diagnóstico</option>
              </select>
              <input type="number" value={form.estimatedMinutes} onChange={e => setForm({...form, estimatedMinutes: +e.target.value})} placeholder="Minutos"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
              <input type="number" value={form.basePrice} onChange={e => setForm({...form, basePrice: +e.target.value})} placeholder="Precio ₡"
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none" />
            </div>
            <div className="flex gap-2">
              <button onClick={handleSave} className="flex-1 bg-forge-500 text-black font-bold py-2 rounded-lg font-mono text-xs">
                {editing ? 'Actualizar' : 'Agregar'} Servicio
              </button>
              <button onClick={() => { setIsAdding(false); setEditing(null); }} className="px-4 py-2 rounded-lg glass-inner text-steel-200 font-mono text-xs">
                Cancelar
              </button>
            </div>
          </div>
        ) : (
          <div className="p-4 border-t border-white/5">
            <button onClick={() => setIsAdding(true)} className="w-full flex items-center justify-center gap-2 glass-inner glass-hover rounded-lg py-2.5 font-mono text-xs text-forge-500 font-bold">
              <Plus size={14} /> Agregar Servicio
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
