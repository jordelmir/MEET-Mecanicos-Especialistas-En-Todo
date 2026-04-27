import React, { useState } from 'react';
import { Search, AlertTriangle, Info, ShieldAlert, X, Wrench } from 'lucide-react';

import dtcDatabase from '../dtc_database.json';

const OBD2_DATABASE: Record<string, { title: string; desc: string; fix: string; severity: 'high' | 'medium' | 'low' }> = {};

dtcDatabase.forEach((item: any) => {
  OBD2_DATABASE[item.code] = {
    title: item.descriptionEs || 'Descripción no disponible',
    desc: item.descriptionEn || '',
    fix: item.possibleCauses || 'Revisar manual del fabricante o realizar escaneo profundo.',
    severity: item.severity === 'HIGH' ? 'high' : item.severity === 'MODERATE' ? 'medium' : 'low'
  };
});

interface OBD2ScannerProps {
  onClose: () => void;
}

export function OBD2Scanner({ onClose }: OBD2ScannerProps) {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<any>(null);
  const [searched, setSearched] = useState(false);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanCode = code.trim().toUpperCase();
    if (!cleanCode) return;
    
    setSearched(true);
    if (OBD2_DATABASE[cleanCode]) {
      setResult({ code: cleanCode, ...OBD2_DATABASE[cleanCode] });
    } else {
      setResult(null);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[90] flex items-center justify-center p-4 animate-fade-in">
      <div className="glass rounded-2xl w-full max-w-lg border border-forge-500/30 overflow-hidden flex flex-col shadow-[0_0_50px_rgba(0,240,255,0.1)] animate-slide-up">
        
        {/* Header */}
        <div className="p-5 border-b border-white/10 bg-steel-900 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <div className="bg-forge-500/20 p-2 rounded-lg text-forge-500 border border-forge-500/30">
              <AlertTriangle size={20} />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white font-display tracking-wider">Analizador OBD2</h2>
              <p className="text-[10px] text-steel-400 font-mono uppercase tracking-widest mt-0.5">Diagnóstico Inteligente de Códigos</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 text-steel-400 hover:text-white bg-white/5 rounded-lg transition-colors">
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          <form onSubmit={handleSearch} className="mb-6">
            <label className="block text-xs font-bold text-steel-300 uppercase tracking-wider mb-2">Ingrese Código (Ej: P0300)</label>
            <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-forge-500" size={20} />
              <input
                type="text"
                value={code}
                onChange={e => setCode(e.target.value)}
                placeholder="P0..."
                className="w-full bg-steel-900 border-2 border-steel-700 rounded-xl pl-12 pr-4 py-4 text-xl font-bold text-white uppercase focus:border-forge-500 outline-none transition-all placeholder:text-steel-600 font-mono"
                autoFocus
              />
              <button type="submit" className="absolute right-2 top-1/2 -translate-y-1/2 bg-forge-500 text-black px-4 py-2 rounded-lg font-bold hover:bg-forge-400 transition-colors">
                Analizar
              </button>
            </div>
          </form>

          {searched && (
            <div className="animate-fade-in">
              {result ? (
                <div className={`rounded-xl p-5 border-l-4 ${
                  result.severity === 'high' ? 'bg-red-500/10 border-red-500' :
                  result.severity === 'medium' ? 'bg-yellow-500/10 border-yellow-500' :
                  'bg-blue-500/10 border-blue-500'
                }`}>
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex items-center gap-2">
                      {result.severity === 'high' ? <ShieldAlert className="text-red-500" /> : <Info className="text-forge-500" />}
                      <h3 className="text-2xl font-bold text-white font-mono">{result.code}</h3>
                    </div>
                    <span className={`px-3 py-1 rounded text-[10px] font-bold uppercase tracking-wider ${
                      result.severity === 'high' ? 'bg-red-500/20 text-red-400' :
                      result.severity === 'medium' ? 'bg-yellow-500/20 text-yellow-400' :
                      'bg-blue-500/20 text-blue-400'
                    }`}>
                      {result.severity === 'high' ? 'Crítico' : result.severity === 'medium' ? 'Moderado' : 'Leve'}
                    </span>
                  </div>

                  <h4 className="text-lg font-bold text-white mb-2">{result.title}</h4>
                  <p className="text-steel-300 text-sm mb-4 leading-relaxed">{result.desc}</p>

                  <div className="bg-black/30 rounded-lg p-4 border border-white/5">
                    <h5 className="text-[10px] text-forge-500 font-mono uppercase tracking-widest mb-2 flex items-center gap-2">
                      <Wrench size={12} /> Solución Recomendada
                    </h5>
                    <p className="text-white text-sm font-medium">{result.fix}</p>
                  </div>
                </div>
              ) : (
                <div className="text-center py-8 glass-inner rounded-xl border border-steel-700/50">
                  <AlertTriangle size={48} className="mx-auto text-steel-500 mb-4 opacity-50" />
                  <p className="text-white font-bold text-lg">Código no encontrado</p>
                  <p className="text-steel-400 text-sm mt-1">Nuestra IA no tiene este código en la base de datos o el formato es incorrecto.</p>
                </div>
              )}
            </div>
          )}
        </div>
        
        <div className="bg-steel-900/50 p-4 border-t border-white/5 text-center">
          <p className="text-[10px] text-steel-500 font-mono uppercase tracking-wider">
            Powered by MEET Engine AI · 2026 Database
          </p>
        </div>
      </div>
    </div>
  );
}
