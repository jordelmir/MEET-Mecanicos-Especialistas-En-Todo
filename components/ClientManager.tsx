
import React, { useState, useMemo } from 'react';
import { Client, VehicleInfo } from '../types';
import { X, Plus, Pencil, Trash2, Users, Search, Car, ChevronDown, ChevronUp, Phone, Mail } from 'lucide-react';

interface ClientManagerProps {
  clients: Client[];
  onAdd: (data: any) => Client;
  onUpdate: (client: Client) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

export function ClientManager({ clients, onAdd, onUpdate, onDelete, onClose }: ClientManagerProps) {
  const [search, setSearch] = useState('');
  const [expandedClient, setExpandedClient] = useState<string | null>(null);

  const filtered = useMemo(() => {
    if (!search) return clients;
    const q = search.toLowerCase();
    return clients.filter(c =>
      c.name.toLowerCase().includes(q) ||
      c.phone.includes(q) ||
      c.identification.includes(q) ||
      c.vehicles.some(v => v.plate.toLowerCase().includes(q) || v.brand.toLowerCase().includes(q))
    );
  }, [clients, search]);

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-2xl max-h-[85vh] glass rounded-2xl overflow-hidden flex flex-col">
        <div className="p-5 border-b border-white/5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Users size={20} className="text-forge-500" />
            Gestión de Clientes
          </h2>
          <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1 border border-transparent hover:border-white/10">
            <span className="text-xs font-mono hidden sm:inline-block pr-1">Cerrar</span>
            <X size={18} />
          </button>
        </div>

        {/* Search */}
        <div className="p-4 border-b border-white/5">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-300" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar por nombre, teléfono, cédula o placa..."
              className="w-full bg-steel-800 border border-steel-500 rounded-lg pl-9 pr-4 py-2 font-mono text-xs text-white placeholder-steel-300 focus:border-forge-500 outline-none"
            />
          </div>
        </div>

        {/* Client List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {filtered.map(client => (
            <div key={client.id} className="glass-inner rounded-xl overflow-hidden">
              <div
                className="p-3 flex items-center gap-3 cursor-pointer hover:bg-white/[0.02] transition-colors"
                onClick={() => setExpandedClient(expandedClient === client.id ? null : client.id)}
              >
                <div className="w-10 h-10 rounded-lg overflow-hidden border border-steel-500 flex-shrink-0">
                  {client.avatar ? (
                    <img src={client.avatar} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full bg-steel-700 flex items-center justify-center text-steel-300 font-bold text-sm">
                      {client.name[0]}
                    </div>
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-bold text-white">{client.name}</div>
                  <div className="font-mono text-[10px] text-steel-300 flex items-center gap-3">
                    <span className="flex items-center gap-1"><Phone size={9} />{client.phone}</span>
                    <span className="flex items-center gap-1"><Car size={9} />{client.vehicles.length} vehículos</span>
                    <span className="text-forge-500">{client.loyaltyPoints} pts</span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={(e) => { e.stopPropagation(); onDelete(client.id); }} className="p-1.5 rounded text-steel-300 hover:text-red-400 transition-colors">
                    <Trash2 size={14} />
                  </button>
                  {expandedClient === client.id ? <ChevronUp size={14} className="text-steel-400" /> : <ChevronDown size={14} className="text-steel-400" />}
                </div>
              </div>

              {/* Expanded Details */}
              {expandedClient === client.id && (
                <div className="p-3 border-t border-white/5 space-y-2 animate-slide-up">
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="flex items-center gap-1.5 text-steel-300"><Mail size={11} />{client.email}</div>
                    <div className="flex items-center gap-1.5 text-steel-300">ID: {client.identification}</div>
                  </div>
                  {client.notes && <p className="text-[10px] text-steel-300 italic">{client.notes}</p>}
                  
                  {/* Vehicles */}
                  <div className="space-y-1">
                    <p className="font-mono text-[10px] text-forge-500 uppercase tracking-wider">Vehículos</p>
                    {client.vehicles.map((v, i) => (
                      <div key={i} className="flex items-center gap-2 p-2 rounded-lg bg-steel-800/50">
                        <Car size={12} className="text-forge-500" />
                        <span className="font-mono text-xs text-forge-500 font-bold">{v.plate}</span>
                        <span className="text-xs text-white">{v.brand} {v.model} {v.year}</span>
                        <span className="text-[10px] text-steel-300">{v.color} · {v.mileage.toLocaleString()} km</span>
                      </div>
                    ))}
                  </div>

                  {/* Recent History */}
                  {client.serviceHistory.length > 0 && (
                    <div className="space-y-1">
                      <p className="font-mono text-[10px] text-steel-300 uppercase tracking-wider">Últimos servicios</p>
                      {client.serviceHistory.slice(0, 3).map(h => (
                        <div key={h.id} className="flex items-center justify-between text-[10px] text-steel-300">
                          <span>{h.serviceName}</span>
                          <span className="font-mono text-forge-500">₡{h.price.toLocaleString()}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
