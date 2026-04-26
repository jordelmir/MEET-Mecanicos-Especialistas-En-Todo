import React, { useState, useEffect } from 'react';
import { WorkOrder, WorkOrderStatus, Mechanic, Service } from '../types';
import { Wrench, Clock, Maximize2, Minimize2, CheckCircle, Car } from 'lucide-react';
import { getStatusColor, getStatusLabel } from '../services/timeEngine';

interface TVDashboardProps {
  workOrders: WorkOrder[];
  mechanics: Mechanic[];
  services: Service[];
  onClose: () => void;
}

export function TVDashboard({ workOrders, mechanics, services, onClose }: TVDashboardProps) {
  const [now, setNow] = useState(new Date());
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(e => console.log(e));
      setIsFullscreen(true);
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen();
        setIsFullscreen(false);
      }
    }
  };

  const activeOrders = workOrders.filter(wo =>
    wo.status !== WorkOrderStatus.COMPLETED &&
    wo.status !== WorkOrderStatus.DELIVERED &&
    wo.status !== WorkOrderStatus.CANCELLED
  ).sort((a, b) => {
    // Sort by status priority then by start time
    if (a.status === WorkOrderStatus.IN_PROGRESS && b.status !== WorkOrderStatus.IN_PROGRESS) return -1;
    if (a.status !== WorkOrderStatus.IN_PROGRESS && b.status === WorkOrderStatus.IN_PROGRESS) return 1;
    return a.startTime.getTime() - b.startTime.getTime();
  });

  return (
    <div className="fixed inset-0 bg-black z-[9999] flex flex-col overflow-hidden animate-slide-up">
      {/* Header */}
      <div className="p-4 bg-steel-900 border-b border-white/10 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-forge-500/20 border border-forge-500/30 flex items-center justify-center">
            <Wrench size={24} className="text-forge-500" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white font-display tracking-wider">PANTALLA DE TALLER</h1>
            <p className="text-forge-500 font-mono text-sm">{now.toLocaleTimeString('es-CR')} · {activeOrders.length} órdenes activas</p>
          </div>
        </div>
        <div className="flex gap-2">
          <button onClick={toggleFullscreen} className="p-3 bg-white/5 hover:bg-white/10 rounded-xl text-white transition-all">
            {isFullscreen ? <Minimize2 size={24} /> : <Maximize2 size={24} />}
          </button>
          <button onClick={() => { if(isFullscreen) document.exitFullscreen(); onClose(); }} className="px-6 py-3 bg-red-500/20 hover:bg-red-500/30 text-red-400 font-bold rounded-xl transition-all font-mono text-sm tracking-wider">
            SALIR
          </button>
        </div>
      </div>

      {/* Grid */}
      <div className="flex-1 p-6 overflow-y-auto">
        {activeOrders.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-steel-500">
            <CheckCircle size={80} className="mb-4 opacity-50" />
            <h2 className="text-4xl font-bold">Sin órdenes activas</h2>
            <p className="text-xl mt-2 font-mono">El taller está al día</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
            {activeOrders.map(wo => {
              const mechanic = mechanics.find(m => m.id === wo.mechanicId);
              const service = services.find(s => s.id === wo.serviceId);
              const statusColors = getStatusColor(wo.status);

              // Calculate time progress
              let progressStr = '';
              let isOverdue = false;
              let timeColor = 'text-forge-500';

              if (wo.status === WorkOrderStatus.IN_PROGRESS && wo.actualStartTime) {
                const elapsedMs = now.getTime() - wo.actualStartTime.getTime();
                const totalMs = wo.estimatedMinutes * 60000;
                const remainingMs = totalMs - elapsedMs;

                if (remainingMs < 0) {
                  isOverdue = true;
                  timeColor = 'text-red-500';
                  const overdueMins = Math.floor(Math.abs(remainingMs) / 60000);
                  progressStr = `+${overdueMins} min retraso`;
                } else {
                  const remainingMins = Math.ceil(remainingMs / 60000);
                  progressStr = `${remainingMins} min restantes`;
                  if (remainingMins < 10) timeColor = 'text-yellow-400';
                }
              } else if (wo.status === WorkOrderStatus.WAITING_PARTS) {
                progressStr = 'EN PAUSA';
                timeColor = 'text-orange-400';
              } else {
                progressStr = `${wo.estimatedMinutes} min est.`;
                timeColor = 'text-steel-400';
              }

              return (
                <div key={wo.id} className="bg-steel-900 rounded-2xl border border-white/5 overflow-hidden flex flex-col shadow-2xl relative">
                  <div className="absolute top-0 left-0 w-full h-1" style={{ backgroundColor: statusColors.bg }}></div>
                  
                  <div className="p-5 flex-1">
                    <div className="flex justify-between items-start mb-4">
                      <div className="flex-1">
                        <h3 className="text-2xl font-bold text-white mb-1 leading-tight">{wo.vehicleInfo.brand} {wo.vehicleInfo.model}</h3>
                        <div className="text-lg font-mono text-forge-500 bg-forge-500/10 inline-block px-3 py-1 rounded-lg font-bold">
                          {wo.vehicleInfo.plate}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-bold uppercase tracking-wider mb-2" style={{ backgroundColor: statusColors.bg, color: statusColors.text }}>
                          <div className="w-2 h-2 rounded-full bg-current animate-pulse"></div>
                          {getStatusLabel(wo.status)}
                        </div>
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div className="bg-black/30 rounded-xl p-4 border border-white/5">
                        <p className="text-steel-400 text-xs font-bold uppercase tracking-wider mb-1">Servicio</p>
                        <p className="text-white text-lg font-medium">{service?.name || 'Servicio General'}</p>
                      </div>

                      <div className="flex items-center gap-4">
                        <div className="w-14 h-14 rounded-xl overflow-hidden border-2 border-steel-700 flex-shrink-0">
                          {mechanic?.avatar ? (
                            <img src={mechanic.avatar} alt="" className="w-full h-full object-cover" />
                          ) : (
                            <div className="w-full h-full bg-steel-800 flex items-center justify-center text-steel-400"><Wrench size={24}/></div>
                          )}
                        </div>
                        <div>
                          <p className="text-steel-400 text-xs font-bold uppercase tracking-wider mb-0.5">Mecánico</p>
                          <p className="text-white font-bold">{mechanic?.name || 'No asignado'}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="bg-black/40 p-4 border-t border-white/5 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Clock size={24} className={timeColor} />
                      <span className={`text-2xl font-bold font-mono ${timeColor}`}>
                        {progressStr}
                      </span>
                    </div>
                    {isOverdue && (
                      <div className="bg-red-500/20 text-red-500 text-xs font-bold px-3 py-1.5 rounded-lg animate-pulse">
                        ¡TIEMPO EXCEDIDO!
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
