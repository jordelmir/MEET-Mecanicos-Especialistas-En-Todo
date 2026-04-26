
import React, { useState } from 'react';
import { WorkOrder, WorkOrderStatus } from '../types';
import { formatDuration, getStatusLabel, getStatusColor } from '../services/timeEngine';
import { X, Car, DollarSign, Clock, Calendar, Save } from 'lucide-react';

interface WorkOrderEditorProps {
  workOrder: WorkOrder;
  allWorkOrders: WorkOrder[];
  serviceName: string;
  onClose: () => void;
  onSave: (id: string, updates: { price: number; estimatedMinutes: number; startTime?: Date }) => void;
}

export function WorkOrderEditor({ workOrder, serviceName, onClose, onSave }: WorkOrderEditorProps) {
  const [price, setPrice] = useState(workOrder.price);
  const [duration, setDuration] = useState(workOrder.estimatedMinutes);
  const [time, setTime] = useState(
    `${String(workOrder.startTime.getHours()).padStart(2, '0')}:${String(workOrder.startTime.getMinutes()).padStart(2, '0')}`
  );
  const [date, setDate] = useState(workOrder.startTime.toISOString().split('T')[0]);

  const handleSave = () => {
    const newStartTime = new Date(date + 'T00:00:00');
    const [h, m] = time.split(':').map(Number);
    newStartTime.setHours(h, m, 0, 0);
    onSave(workOrder.id, { price, estimatedMinutes: duration, startTime: newStartTime });
  };

  const colors = getStatusColor(workOrder.status);

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md glass rounded-2xl overflow-hidden animate-slide-up">
        <div className="p-5 border-b border-white/5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-bold text-white">Editar Orden</h2>
              <p className="font-mono text-xs text-steel-300 mt-0.5">{serviceName}</p>
            </div>
            <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all">
              <X size={18} />
            </button>
          </div>
          
          {/* Vehicle & Client Info */}
          <div className="mt-3 flex items-center gap-3 text-xs">
            <span className="flex items-center gap-1 font-mono text-forge-500">
              <Car size={12} />{workOrder.vehicleInfo.plate}
            </span>
            <span className="text-white">{workOrder.vehicleInfo.brand} {workOrder.vehicleInfo.model}</span>
            <span className="text-steel-300">{workOrder.clientName}</span>
          </div>
          
          {/* Status Badge */}
          <div className="mt-2">
            <span
              className="inline-flex items-center gap-1 px-2 py-1 rounded-full font-mono text-[10px] font-bold"
              style={{ background: colors.bg, color: colors.text, border: `1px solid ${colors.border}` }}
            >
              {getStatusLabel(workOrder.status)}
            </span>
          </div>
        </div>

        <div className="p-5 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                <DollarSign size={10} />Precio (₡)
              </label>
              <input
                type="number"
                value={price}
                onChange={e => setPrice(+e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-sm text-forge-500 font-bold focus:border-forge-500 outline-none"
              />
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                <Clock size={10} />Duración (min)
              </label>
              <input
                type="number"
                value={duration}
                onChange={e => setDuration(+e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-sm text-white focus:border-forge-500 outline-none"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                <Calendar size={10} />Fecha
              </label>
              <input
                type="date"
                value={date}
                onChange={e => setDate(e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-sm text-white focus:border-forge-500 outline-none"
              />
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-1.5">Hora Inicio</label>
              <input
                type="time"
                value={time}
                onChange={e => setTime(e.target.value)}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-sm text-white focus:border-forge-500 outline-none"
              />
            </div>
          </div>

          {workOrder.diagnosticNotes && (
            <div className="glass-inner rounded-lg p-3">
              <p className="font-mono text-[10px] text-forge-500 uppercase tracking-wider mb-1">Notas de Diagnóstico</p>
              <p className="text-xs text-steel-200">{workOrder.diagnosticNotes}</p>
            </div>
          )}

          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="flex-1 py-2.5 rounded-lg border border-steel-600 text-steel-300 font-bold hover:bg-steel-800 hover:text-white transition-all font-mono text-xs tracking-wider uppercase"
            >
              Cancelar
            </button>
            <button
              onClick={handleSave}
              className="flex-[2] flex items-center justify-center gap-2 bg-forge-500 text-black font-bold py-2.5 rounded-lg font-mono text-xs tracking-wider uppercase forge-glow hover:bg-forge-400 transition-all"
            >
              <Save size={14} />
              Guardar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
