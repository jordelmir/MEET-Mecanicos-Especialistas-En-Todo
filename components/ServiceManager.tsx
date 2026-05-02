
import React, { useState } from 'react';
import { Service, ServiceCategory } from '../types';
import { formatDuration } from '../services/timeEngine';
import { X, Plus, Pencil, Trash2, Wrench, Search, DollarSign, Clock, LayoutGrid, Save } from 'lucide-react';

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
  const [searchTerm, setSearchTerm] = useState('');
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

  const filteredServices = services.filter(s => s.name.toLowerCase().includes(searchTerm.toLowerCase()));

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-[100] flex items-center justify-center p-4 animate-slide-up">
      <div className="w-full max-w-4xl glass rounded-3xl overflow-hidden shadow-[0_0_50px_rgba(0,240,255,0.15)] flex flex-col max-h-[85vh] border border-forge-500/20">
        
        {/* Header */}
        <div className="p-6 border-b border-white/10 bg-gradient-to-r from-steel-900 to-steel-800 flex items-center justify-between relative overflow-hidden">
          <div className="absolute top-0 right-0 w-64 h-64 bg-forge-500/10 blur-[100px] rounded-full pointer-events-none"></div>
          <div className="relative z-10">
            <h2 className="text-2xl font-bold text-white flex items-center gap-3 tracking-tight">
              <div className="p-2 bg-forge-500/20 rounded-xl text-forge-400 border border-forge-500/30">
                <DollarSign size={24} />
              </div>
              Gestión de Precios y Servicios
            </h2>
            <p className="text-steel-400 text-sm mt-1">Configuración maestra de catálogo y tarifas de taller.</p>
          </div>
          <button onClick={onClose} className="px-4 py-2 rounded-full bg-white/5 text-steel-300 hover:text-white hover:bg-white/10 transition-all border border-white/5 relative z-10 flex items-center gap-2">
            <span className="text-sm font-mono font-bold">Cerrar</span>
            <X size={18} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden bg-steel-950/50">
          
          {/* Left Panel: List */}
          <div className={`flex-1 flex flex-col border-r border-white/5 ${isAdding ? 'hidden md:flex' : 'flex'}`}>
            <div className="p-4 border-b border-white/5">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-500" size={16} />
                <input 
                  type="text" 
                  placeholder="Buscar servicio..." 
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full bg-steel-900 border border-steel-700 rounded-xl pl-9 pr-4 py-2.5 text-sm text-white focus:border-forge-500 focus:ring-1 focus:ring-forge-500 outline-none transition-all shadow-inner"
                />
              </div>
            </div>
            
            <div className="flex-1 overflow-y-auto p-2 space-y-2">
              {filteredServices.map(svc => (
                <div key={svc.id} className="group relative p-3 rounded-2xl hover:bg-steel-800 border border-transparent hover:border-steel-600 transition-all cursor-pointer overflow-hidden">
                  <div className="absolute inset-y-0 left-0 w-1 bg-forge-500 opacity-0 group-hover:opacity-100 transition-opacity"></div>
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <div className={`px-2 py-0.5 rounded text-[9px] font-mono font-bold uppercase badge-${svc.category}`}>
                          {svc.category}
                        </div>
                        <h3 className="text-sm font-bold text-gray-200 group-hover:text-white transition-colors">{svc.name}</h3>
                      </div>
                      <div className="flex items-center gap-4 mt-2">
                        <span className="flex items-center gap-1 text-[11px] font-mono text-steel-400">
                          <Clock size={12} /> {formatDuration(svc.estimatedMinutes)}
                        </span>
                        <span className="flex items-center gap-1 text-[12px] font-bold text-forge-400">
                          ₡{svc.basePrice.toLocaleString()}
                        </span>
                      </div>
                    </div>
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(svc)} className="p-1.5 rounded-lg bg-steel-700 text-steel-300 hover:text-white hover:bg-forge-500 transition-all">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => onDelete(svc.id)} className="p-1.5 rounded-lg bg-steel-700 text-steel-300 hover:text-white hover:bg-red-500 transition-all">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
              {filteredServices.length === 0 && (
                <div className="text-center p-8 text-steel-500 text-sm">
                  No se encontraron servicios.
                </div>
              )}
            </div>
            
            {!isAdding && (
              <div className="p-4 border-t border-white/5">
                <button onClick={() => setIsAdding(true)} className="w-full flex items-center justify-center gap-2 bg-forge-500/10 text-forge-400 hover:bg-forge-500 hover:text-black border border-forge-500/20 rounded-xl py-3 text-sm font-bold transition-all shadow-[0_0_15px_rgba(0,240,255,0.1)] hover:shadow-[0_0_20px_rgba(0,240,255,0.3)]">
                  <Plus size={18} strokeWidth={3} /> Agregar Nuevo Servicio
                </button>
              </div>
            )}
          </div>

          {/* Right Panel: Editor */}
          {isAdding && (
            <div className="flex-1 bg-steel-900 border-l border-white/5 p-6 flex flex-col relative overflow-y-auto">
              <button onClick={() => { setIsAdding(false); setEditing(null); }} className="absolute top-4 right-4 p-2 text-steel-400 hover:text-white md:hidden">
                <X size={20} />
              </button>
              
              <h3 className="text-xl font-bold text-white mb-6 flex items-center gap-2">
                <LayoutGrid className="text-forge-500" size={20} />
                {editing ? 'Editar Tarifa' : 'Nuevo Servicio'}
              </h3>
              
              <div className="space-y-5">
                <div>
                  <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1.5 block">Nombre del Servicio</label>
                  <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="Ej. Cambio de Aceite Sintético"
                    className="w-full bg-steel-950 border border-steel-700 rounded-xl px-4 py-3 text-sm text-white focus:border-forge-500 outline-none transition-all shadow-inner" />
                </div>
                
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1.5 block">Categoría</label>
                    <div className="relative">
                      <select value={form.category} onChange={e => setForm({...form, category: e.target.value as ServiceCategory})}
                        className="w-full bg-steel-950 border border-steel-700 rounded-xl pl-4 pr-10 py-3 text-sm text-white focus:border-forge-500 outline-none transition-all shadow-inner appearance-none">
                        <option value="mant">Mantenimiento</option>
                        <option value="rep">Reparación</option>
                        <option value="cam">Cambio/Repuesto</option>
                        <option value="diag">Diagnóstico</option>
                      </select>
                      <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-steel-500">▼</div>
                    </div>
                  </div>
                  
                  <div>
                    <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1.5 block">Duración (Minutos)</label>
                    <div className="relative">
                      <Clock className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-500" size={16} />
                      <input type="number" value={form.estimatedMinutes} onChange={e => setForm({...form, estimatedMinutes: +e.target.value})}
                        className="w-full bg-steel-950 border border-steel-700 rounded-xl pl-9 pr-4 py-3 text-sm text-white font-mono focus:border-forge-500 outline-none transition-all shadow-inner" />
                    </div>
                  </div>
                </div>
                
                <div>
                  <label className="text-xs font-bold text-forge-400 uppercase tracking-wider mb-1.5 block">Precio Base (₡)</label>
                  <div className="relative">
                    <DollarSign className="absolute left-4 top-1/2 -translate-y-1/2 text-forge-500" size={20} />
                    <input type="number" value={form.basePrice} onChange={e => setForm({...form, basePrice: +e.target.value})} placeholder="0"
                      className="w-full bg-steel-950 border-2 border-forge-500/30 rounded-xl pl-11 pr-4 py-4 text-xl font-bold text-white font-mono focus:border-forge-500 outline-none transition-all shadow-[inset_0_2px_10px_rgba(0,0,0,0.5)]" />
                  </div>
                  <p className="text-xs text-steel-500 mt-2">El precio final puede ser ajustado por el mecánico al generar la orden.</p>
                </div>

                <div>
                  <label className="text-xs font-bold text-steel-400 uppercase tracking-wider mb-1.5 block">Descripción (Opcional)</label>
                  <textarea value={form.description} onChange={e => setForm({...form, description: e.target.value})} placeholder="Detalles operativos del servicio..." rows={3}
                    className="w-full bg-steel-950 border border-steel-700 rounded-xl px-4 py-3 text-sm text-white focus:border-forge-500 outline-none transition-all shadow-inner resize-none" />
                </div>
              </div>
              
              <div className="mt-8 pt-6 border-t border-white/5 flex gap-3 mt-auto">
                <button onClick={() => { setIsAdding(false); setEditing(null); }} className="flex-1 py-3 rounded-xl border border-steel-600 text-steel-300 font-bold hover:bg-steel-800 hover:text-white transition-all">
                  Cancelar
                </button>
                <button onClick={handleSave} className="flex-[2] bg-forge-500 text-black font-bold py-3 rounded-xl shadow-[0_0_20px_rgba(0,240,255,0.4)] hover:bg-forge-400 hover:shadow-[0_0_30px_rgba(0,240,255,0.6)] transform hover:scale-[1.02] transition-all flex items-center justify-center gap-2">
                  <Save size={18} /> {editing ? 'Guardar Cambios' : 'Registrar Servicio'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

