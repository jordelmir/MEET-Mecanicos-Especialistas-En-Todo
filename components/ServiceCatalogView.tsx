
import React, { useState, useMemo } from 'react';
import { CatalogSection, ServiceCategory } from '../types';
import { SERVICE_CATALOG } from '../constants';
import { getCategoryBadge } from '../services/timeEngine';
import { X, Search, ChevronDown, ChevronUp, Download, Printer } from 'lucide-react';

interface ServiceCatalogViewProps {
  onClose: () => void;
}

export function ServiceCatalogView({ onClose }: ServiceCatalogViewProps) {
  const [search, setSearch] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(SERVICE_CATALOG.map(c => c.id)));

  const filters = [
    { key: 'all', label: 'Todos' },
    { key: 'rep', label: 'Reparación' },
    { key: 'cam', label: 'Cambio' },
    { key: 'mant', label: 'Mantenimiento' },
    { key: 'diag', label: 'Diagnóstico' },
  ];

  const filteredCatalog = useMemo(() => {
    return SERVICE_CATALOG.map(section => {
      const items = section.items.filter(item => {
        const matchFilter = activeFilter === 'all' || item.category === activeFilter;
        const matchSearch = !search || item.name.toLowerCase().includes(search.toLowerCase());
        return matchFilter && matchSearch;
      });
      return { ...section, items };
    }).filter(section => section.items.length > 0);
  }, [search, activeFilter]);

  const totalItems = useMemo(() => 
    filteredCatalog.reduce((sum, s) => sum + s.items.length, 0)
  , [filteredCatalog]);

  const toggleSection = (id: string) => {
    setExpandedSections(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const exportCSV = () => {
    let csv = 'Categoría,Servicio,Tipo,Tiempo Estimado (min)\n';
    SERVICE_CATALOG.forEach(cat => {
      cat.items.forEach(item => {
        const { label } = getCategoryBadge(item.category);
        csv += `"${cat.title}","${item.name}","${label}","${item.estimatedMinutes || ''}"\n`;
      });
    });
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'catalogo_taller.csv';
    a.click();
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-4xl max-h-[90vh] glass rounded-2xl overflow-hidden flex flex-col">
        {/* Header */}
        <div className="p-5 border-b border-white/5 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-white font-display tracking-wider">CATÁLOGO DE SERVICIOS</h2>
            <p className="font-mono text-[10px] text-steel-300 mt-1">{totalItems} servicios encontrados</p>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={exportCSV} className="p-2 rounded-lg glass-inner glass-hover text-steel-200 hover:text-forge-500 transition-all" title="Exportar CSV">
              <Download size={16} />
            </button>
            <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all">
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Toolbar */}
        <div className="p-4 border-b border-white/5 flex flex-wrap gap-3 items-center">
          <div className="relative flex-1 min-w-[200px] max-w-md">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-300" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar servicio..."
              className="w-full bg-steel-800 border border-steel-500 rounded-lg pl-9 pr-4 py-2 font-mono text-xs text-white placeholder-steel-300 focus:border-forge-500 outline-none"
            />
          </div>
          <div className="flex gap-1.5 flex-wrap">
            {filters.map(f => (
              <button
                key={f.key}
                onClick={() => setActiveFilter(f.key)}
                className={`px-3 py-1.5 rounded-full font-mono text-[10px] font-bold transition-all ${
                  activeFilter === f.key
                    ? 'bg-forge-500 text-black'
                    : 'glass-inner text-steel-200 hover:text-white'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* Catalog List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {filteredCatalog.map(section => (
            <div key={section.id} className="glass-inner rounded-xl overflow-hidden">
              {/* Section Header */}
              <button
                onClick={() => toggleSection(section.id)}
                className="w-full flex items-center gap-3 p-3 hover:bg-white/[0.02] transition-colors"
              >
                <span className="text-xl">{section.icon}</span>
                <span className="font-display text-lg tracking-wider text-forge-500 flex-1 text-left">{section.title}</span>
                <span className="font-mono text-[10px] text-steel-300 px-2 py-0.5 rounded-full bg-steel-800">
                  {section.items.length}
                </span>
                {expandedSections.has(section.id) ? (
                  <ChevronUp size={16} className="text-steel-300" />
                ) : (
                  <ChevronDown size={16} className="text-steel-300" />
                )}
              </button>

              {/* Items */}
              {expandedSections.has(section.id) && (
                <div className="border-t border-white/5">
                  {section.items.map((item, i) => {
                    const badge = getCategoryBadge(item.category);
                    return (
                      <div
                        key={i}
                        className="flex items-center gap-3 px-4 py-2.5 border-b border-white/[0.02] last:border-0 hover:bg-white/[0.02] transition-colors"
                      >
                        <span className="font-mono text-[10px] text-steel-400 w-8 text-right flex-shrink-0">{i + 1}</span>
                        <span className="text-sm text-white flex-1">{item.name}</span>
                        <span className={`px-2 py-0.5 rounded text-[9px] font-mono font-bold uppercase ${badge.className}`}>
                          {badge.label}
                        </span>
                        {item.estimatedMinutes && (
                          <span className="font-mono text-[10px] text-steel-300 w-16 text-right">
                            {item.estimatedMinutes >= 60
                              ? `${Math.floor(item.estimatedMinutes / 60)}h${item.estimatedMinutes % 60 > 0 ? ` ${item.estimatedMinutes % 60}m` : ''}`
                              : `${item.estimatedMinutes}m`
                            }
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
