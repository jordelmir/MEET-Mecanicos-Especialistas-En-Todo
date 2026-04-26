
import React, { useState } from 'react';
import { X, Settings } from 'lucide-react';

interface ShopSettingsProps {
  currentRules: string;
  currentOpenHour: number;
  currentCloseHour: number;
  currentTimeSlice: number;
  onSave: (settings: { rules: string; openHour: number; closeHour: number; timeSlice: number }) => void;
  onClose: () => void;
}

export function ShopSettings({ currentRules, currentOpenHour, currentCloseHour, currentTimeSlice, onSave, onClose }: ShopSettingsProps) {
  const [rules, setRules] = useState(currentRules);
  const [openHour, setOpenHour] = useState(currentOpenHour);
  const [closeHour, setCloseHour] = useState(currentCloseHour);
  const [timeSlice, setTimeSlice] = useState(currentTimeSlice);

  const handleSave = () => {
    onSave({ rules, openHour, closeHour, timeSlice });
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md glass rounded-2xl overflow-hidden">
        <div className="p-5 border-b border-white/5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Settings size={20} className="text-forge-500" />
            Configuración del Taller
          </h2>
          <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all">
            <X size={18} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5">Apertura</label>
              <select value={openHour} onChange={e => setOpenHour(+e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none">
                {Array.from({length: 24}, (_,i) => <option key={i} value={i}>{String(i).padStart(2,'0')}:00</option>)}
              </select>
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5">Cierre</label>
              <select value={closeHour} onChange={e => setCloseHour(+e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none">
                {Array.from({length: 24}, (_,i) => <option key={i} value={i}>{String(i).padStart(2,'0')}:00</option>)}
              </select>
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5">Intervalo</label>
              <select value={timeSlice} onChange={e => setTimeSlice(+e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none">
                {[15, 30, 45, 60].map(v => <option key={v} value={v}>{v} min</option>)}
              </select>
            </div>
          </div>

          <div>
            <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5">Reglas del Taller</label>
            <textarea
              value={rules}
              onChange={e => setRules(e.target.value)}
              rows={5}
              className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none resize-none"
            />
          </div>

          <div className="flex gap-2">
            <button onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-steel-600 text-steel-300 font-bold hover:bg-steel-800 hover:text-white transition-all font-mono text-xs tracking-wider uppercase">
              Cancelar
            </button>
            <button onClick={handleSave} className="flex-[2] bg-forge-500 text-black font-bold py-2.5 rounded-lg font-mono text-xs tracking-wider uppercase">
              Guardar Configuración
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
