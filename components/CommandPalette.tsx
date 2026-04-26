
import React, { useState, useEffect, useRef, useMemo } from 'react';
import { WorkOrder, Service, Mechanic, Client } from '../types';
import { getStatusLabel } from '../services/timeEngine';
import { Search, X, Car, Wrench, User, ArrowRight, Command } from 'lucide-react';

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  workOrders: WorkOrder[];
  clients: Client[];
  mechanics: Mechanic[];
  services: Service[];
  onSelectWorkOrder: (order: WorkOrder) => void;
  onNavigate: (action: string) => void;
}

type ResultItem = {
  id: string;
  type: 'order' | 'client' | 'mechanic' | 'service' | 'action';
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  data?: any;
};

export function CommandPalette({
  isOpen, onClose, workOrders, clients, mechanics, services, onSelectWorkOrder, onNavigate
}: CommandPaletteProps) {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [isOpen]);

  // Global keyboard shortcut
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        if (isOpen) onClose(); else onNavigate('__open_palette');
      }
      if (e.key === 'Escape' && isOpen) onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen, onClose, onNavigate]);

  const results: ResultItem[] = useMemo(() => {
    const q = query.toLowerCase().trim();
    const items: ResultItem[] = [];

    // Quick Actions (always show if no query)
    if (!q || 'nueva orden'.includes(q) || 'crear'.includes(q)) {
      items.push({ id: 'act-new', type: 'action', icon: <Wrench size={14} className="text-forge-500" />, title: 'Nueva Orden de Trabajo', subtitle: 'Crear nueva orden', data: 'new_order' });
    }
    if (!q || 'catálogo'.includes(q) || 'catalogo'.includes(q)) {
      items.push({ id: 'act-catalog', type: 'action', icon: <Search size={14} className="text-blue-400" />, title: 'Abrir Catálogo de Servicios', subtitle: 'Ver todos los servicios disponibles', data: 'catalog' });
    }

    if (!q) return items.slice(0, 6);

    // Search work orders
    workOrders.forEach(wo => {
      const searchable = `${wo.clientName} ${wo.vehicleInfo.plate} ${wo.vehicleInfo.brand} ${wo.vehicleInfo.model} ${wo.id}`.toLowerCase();
      if (searchable.includes(q)) {
        items.push({
          id: wo.id,
          type: 'order',
          icon: <Car size={14} className="text-forge-500" />,
          title: `${wo.vehicleInfo.plate} — ${wo.clientName}`,
          subtitle: `${getStatusLabel(wo.status)} · ${services.find(s => s.id === wo.serviceId)?.name || ''}`,
          data: wo,
        });
      }
    });

    // Search clients
    clients.forEach(c => {
      const searchable = `${c.name} ${c.phone} ${c.identification} ${c.vehicles.map(v => v.plate).join(' ')}`.toLowerCase();
      if (searchable.includes(q)) {
        items.push({
          id: c.id,
          type: 'client',
          icon: <User size={14} className="text-green-400" />,
          title: c.name,
          subtitle: `${c.phone} · ${c.vehicles.length} vehículos`,
        });
      }
    });

    // Search mechanics
    mechanics.forEach(m => {
      const searchable = `${m.name} ${m.specialty} ${m.email}`.toLowerCase();
      if (searchable.includes(q)) {
        items.push({
          id: m.id,
          type: 'mechanic',
          icon: <Wrench size={14} className="text-blue-400" />,
          title: m.name,
          subtitle: `${m.specialty} · ${Math.round(m.efficiencyFactor * 100)}% eficiencia`,
        });
      }
    });

    return items.slice(0, 10);
  }, [query, workOrders, clients, mechanics, services]);

  const handleSelect = (item: ResultItem) => {
    if (item.type === 'action') {
      onNavigate(item.data);
    } else if (item.type === 'order') {
      onSelectWorkOrder(item.data);
    }
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => Math.min(prev + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter' && results[selectedIndex]) {
      handleSelect(results[selectedIndex]);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[9998] flex items-start justify-center pt-[15vh]" onClick={onClose}>
      <div
        className="w-full max-w-lg glass rounded-2xl shadow-2xl overflow-hidden animate-slide-up"
        onClick={e => e.stopPropagation()}
      >
        {/* Search Input */}
        <div className="flex items-center gap-3 p-4 border-b border-white/5">
          <Search size={18} className="text-forge-500 flex-shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={e => { setQuery(e.target.value); setSelectedIndex(0); }}
            onKeyDown={handleKeyDown}
            placeholder="Buscar órdenes, clientes, placas, mecánicos..."
            className="flex-1 bg-transparent text-white text-sm font-medium outline-none placeholder-steel-300"
          />
          <kbd className="hidden sm:flex items-center gap-1 px-2 py-0.5 rounded bg-steel-700 text-steel-300 text-[10px] font-mono border border-steel-500">
            ESC
          </kbd>
        </div>

        {/* Results */}
        <div className="max-h-[400px] overflow-y-auto p-2">
          {results.length === 0 && query && (
            <div className="p-6 text-center">
              <p className="text-sm text-steel-300">Sin resultados para "{query}"</p>
            </div>
          )}
          {results.map((item, i) => (
            <button
              key={item.id}
              onClick={() => handleSelect(item)}
              onMouseEnter={() => setSelectedIndex(i)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-left transition-all ${
                i === selectedIndex ? 'bg-forge-500/10 border border-forge-500/20' : 'hover:bg-white/[0.03] border border-transparent'
              }`}
            >
              <div className="w-8 h-8 rounded-lg glass-inner flex items-center justify-center flex-shrink-0">
                {item.icon}
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-white truncate">{item.title}</div>
                <div className="text-[10px] text-steel-300 truncate">{item.subtitle}</div>
              </div>
              {i === selectedIndex && (
                <ArrowRight size={12} className="text-forge-500 flex-shrink-0" />
              )}
            </button>
          ))}
        </div>

        {/* Footer */}
        <div className="p-3 border-t border-white/5 flex items-center justify-between">
          <span className="font-mono text-[9px] text-steel-400">
            {results.length} resultado(s)
          </span>
          <div className="flex items-center gap-2">
            <kbd className="px-1.5 py-0.5 rounded bg-steel-700 text-steel-300 text-[9px] font-mono border border-steel-500">↑↓</kbd>
            <span className="text-[9px] text-steel-400">Navegar</span>
            <kbd className="px-1.5 py-0.5 rounded bg-steel-700 text-steel-300 text-[9px] font-mono border border-steel-500">↵</kbd>
            <span className="text-[9px] text-steel-400">Seleccionar</span>
          </div>
        </div>
      </div>
    </div>
  );
}
