
import React, { useState, useMemo } from 'react';
import { CatalogSection, ServiceCategory, Role } from '../types';
import { getCategoryBadge } from '../services/timeEngine';
import { X, Search, ChevronDown, ChevronUp, Download, Printer, Edit2, Check } from 'lucide-react';

interface ServiceCatalogViewProps {
  catalog: CatalogSection[];
  role: Role;
  onUpdateCatalog: (catalog: CatalogSection[]) => void;
  onClose: () => void;
}

export function ServiceCatalogView({ catalog, role, onUpdateCatalog, onClose }: ServiceCatalogViewProps) {
  const [search, setSearch] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('all');
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(catalog.map(c => c.id)));
  
  // Edit State
  const [editingItem, setEditingItem] = useState<{ sectionId: string, itemIndex: number } | null>(null);
  const [editMinutes, setEditMinutes] = useState<number>(0);
  const [editPrice, setEditPrice] = useState<number>(0);

  const filters = [
    { key: 'all', label: 'Todos' },
    { key: 'rep', label: 'Reparación' },
    { key: 'cam', label: 'Cambio' },
    { key: 'mant', label: 'Mantenimiento' },
    { key: 'diag', label: 'Diagnóstico' },
  ];

  const filteredCatalog = useMemo(() => {
    return catalog.map(section => {
      const items = section.items.filter(item => {
        const matchFilter = activeFilter === 'all' || item.category === activeFilter;
        const matchSearch = !search || item.name.toLowerCase().includes(search.toLowerCase());
        return matchFilter && matchSearch;
      });
      return { ...section, items };
    }).filter(section => section.items.length > 0);
  }, [catalog, search, activeFilter]);

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

  const handleSaveItem = (sectionId: string, itemIndex: number) => {
    if (editMinutes > 0) {
      const newCatalog = [...catalog];
      const sectionIndex = newCatalog.findIndex(s => s.id === sectionId);
      if (sectionIndex !== -1) {
        newCatalog[sectionIndex].items[itemIndex].estimatedMinutes = editMinutes;
        newCatalog[sectionIndex].items[itemIndex].basePrice = editPrice;
        onUpdateCatalog(newCatalog);
      }
    }
    setEditingItem(null);
  };

  const exportCSV = () => {
    let csv = 'Categoría,Servicio,Tipo,Tiempo Estimado (min)\n';
    catalog.forEach(cat => {
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
            <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1">
              <span className="text-xs font-mono hidden sm:inline-block pr-1">Cerrar</span>
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
                          <div className="flex items-center gap-4 w-48 justify-end">
                            {editingItem?.sectionId === section.id && editingItem?.itemIndex === i ? (
                              <div className="flex items-center gap-1" onClick={e => e.stopPropagation()}>
                                <div className="flex flex-col gap-1 items-end mr-2">
                                  <div className="flex items-center gap-1">
                                    <span className="text-[9px] text-steel-400 font-mono">MIN:</span>
                                    <input
                                      type="number"
                                      value={editMinutes}
                                      onChange={e => setEditMinutes(+e.target.value)}
                                      className="w-12 bg-steel-900 border border-forge-500 rounded px-1 py-0.5 text-xs text-forge-500 font-mono outline-none text-right"
                                      onKeyDown={e => e.key === 'Enter' && handleSaveItem(section.id, i)}
                                    />
                                  </div>
                                  <div className="flex items-center gap-1">
                                    <span className="text-[9px] text-steel-400 font-mono">₡:</span>
                                    <input
                                      type="number"
                                      value={editPrice}
                                      onChange={e => setEditPrice(+e.target.value)}
                                      className="w-16 bg-steel-900 border border-forge-500 rounded px-1 py-0.5 text-xs text-forge-500 font-mono outline-none text-right"
                                      onKeyDown={e => e.key === 'Enter' && handleSaveItem(section.id, i)}
                                    />
                                  </div>
                                </div>
                                <div className="flex flex-col gap-1">
                                  <button onClick={() => handleSaveItem(section.id, i)} className="text-green-400 hover:text-green-300 p-1">
                                    <Check size={14} />
                                  </button>
                                  <button onClick={() => setEditingItem(null)} className="text-red-400 hover:text-red-300 p-1">
                                    <X size={14} />
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div className="flex items-center gap-4 group">
                                <div className="flex flex-col items-end">
                                  {item.basePrice !== undefined && item.basePrice > 0 && (
                                    <span className="font-mono text-xs font-bold text-forge-500">
                                      ₡{item.basePrice.toLocaleString()}
                                    </span>
                                  )}
                                  <span className="font-mono text-[10px] text-steel-300">
                                    {item.estimatedMinutes >= 60
                                      ? `${Math.floor(item.estimatedMinutes / 60)}h${item.estimatedMinutes % 60 > 0 ? ` ${item.estimatedMinutes % 60}m` : ''}`
                                      : `${item.estimatedMinutes}m`
                                    }
                                  </span>
                                </div>
                                {(role === Role.ADMIN || role === Role.MECHANIC) && (
                                  <button
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setEditingItem({ sectionId: section.id, itemIndex: i });
                                      setEditMinutes(item.estimatedMinutes || 0);
                                      setEditPrice(item.basePrice || 0);
                                    }}
                                    className="opacity-0 group-hover:opacity-100 transition-opacity text-steel-400 hover:text-forge-500 p-1"
                                  >
                                    <Edit2 size={14} />
                                  </button>
                                )}
                              </div>
                            )}
                          </div>
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
