import React from 'react';
import { WorkOrder, Client, Service, Mechanic, WorkOrderStatus } from '../types';
import { Calendar, Clock, MapPin, CheckCircle, Wrench, ChevronRight, XCircle, Search, AlertCircle, Plus } from 'lucide-react';
import { formatDuration } from '../services/timeEngine';

interface ClientDashboardProps {
  currentUser: Client;
  workOrders: WorkOrder[];
  services: Service[];
  mechanics: Mechanic[];
  onBookNew: () => void;
  onCancelOrder: (id: string) => void;
}

export function ClientDashboard({ currentUser, workOrders, services, mechanics, onBookNew, onCancelOrder }: ClientDashboardProps) {
  const userOrders = workOrders.filter(wo => wo.clientId === currentUser.id);
  
  const upcomingOrders = userOrders.filter(wo => 
    wo.status !== WorkOrderStatus.COMPLETED && 
    wo.status !== WorkOrderStatus.DELIVERED && 
    wo.status !== WorkOrderStatus.CANCELLED
  ).sort((a, b) => a.startTime.getTime() - b.startTime.getTime());

  const pastOrders = userOrders.filter(wo => 
    wo.status === WorkOrderStatus.COMPLETED || 
    wo.status === WorkOrderStatus.DELIVERED || 
    wo.status === WorkOrderStatus.CANCELLED
  ).sort((a, b) => b.startTime.getTime() - a.startTime.getTime());

  const getStatusLabel = (status: WorkOrderStatus) => {
    switch (status) {
      case WorkOrderStatus.RECEIVED: return { label: 'Recibido', color: 'text-blue-400', bg: 'bg-blue-400/10' };
      case WorkOrderStatus.DIAGNOSED: return { label: 'Diagnosticado', color: 'text-purple-400', bg: 'bg-purple-400/10' };
      case WorkOrderStatus.WAITING_PARTS: return { label: 'Esperando Repuestos', color: 'text-orange-400', bg: 'bg-orange-400/10' };
      case WorkOrderStatus.IN_PROGRESS: return { label: 'En Reparación', color: 'text-forge-500', bg: 'bg-forge-500/10' };
      case WorkOrderStatus.QUALITY_CHECK: return { label: 'Control Calidad', color: 'text-yellow-400', bg: 'bg-yellow-400/10' };
      case WorkOrderStatus.COMPLETED: return { label: 'Completado', color: 'text-green-400', bg: 'bg-green-400/10' };
      case WorkOrderStatus.DELIVERED: return { label: 'Entregado', color: 'text-gray-400', bg: 'bg-gray-400/10' };
      case WorkOrderStatus.CANCELLED: return { label: 'Cancelado', color: 'text-red-400', bg: 'bg-red-400/10' };
      default: return { label: status, color: 'text-gray-400', bg: 'bg-gray-400/10' };
    }
  };

  return (
    <div className="w-full max-w-6xl mx-auto space-y-8 animate-slide-up pb-10">
      
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-steel-800/50 p-6 rounded-2xl border border-white/5 backdrop-blur-md relative overflow-hidden">
        <div className="absolute top-0 right-0 w-64 h-64 bg-forge-500/10 blur-[100px] rounded-full pointer-events-none"></div>
        <div className="relative z-10">
          <h1 className="text-3xl font-bold text-white tracking-tight flex items-center gap-3">
            Bienvenido, {currentUser.name.split(' ')[0]}
          </h1>
          <p className="text-gray-400 text-sm mt-1">Gestiona tus vehículos y citas de taller fácilmente.</p>
        </div>
        <button onClick={onBookNew} className="relative z-10 flex items-center gap-2 bg-forge-500 text-black px-5 py-2.5 rounded-full font-bold shadow-[0_0_20px_rgba(0,240,255,0.3)] hover:scale-105 hover:bg-forge-400 transition-all">
          <Plus size={18} strokeWidth={3} />
          Agendar Nueva Cita
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column: Upcoming Appointments & Vehicles */}
        <div className="lg:col-span-2 space-y-6">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Calendar className="text-forge-500" size={24} />
            Citas Activas
          </h2>
          
          {upcomingOrders.length === 0 ? (
            <div className="glass-inner p-8 rounded-2xl text-center border-dashed border-2 border-steel-600">
              <div className="w-16 h-16 rounded-full bg-steel-800 flex items-center justify-center mx-auto mb-4">
                <AlertCircle size={32} className="text-steel-400" />
              </div>
              <h3 className="text-lg font-bold text-white mb-2">No tienes citas activas</h3>
              <p className="text-steel-400 text-sm mb-4">Agenda una revisión o servicio para mantener tu vehículo en perfectas condiciones.</p>
              <button onClick={onBookNew} className="text-forge-500 font-bold hover:underline text-sm">
                Agendar ahora →
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              {upcomingOrders.map(wo => {
                const service = services.find(s => s.id === wo.serviceId);
                const mechanic = mechanics.find(m => m.id === wo.mechanicId);
                const s = getStatusLabel(wo.status);
                
                return (
                  <div key={wo.id} className="glass p-5 rounded-2xl border-l-4" style={{ borderLeftColor: wo.status === WorkOrderStatus.RECEIVED ? '#00f0ff' : '#009ca8' }}>
                    <div className="flex justify-between items-start mb-4">
                      <div>
                        <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider mb-2 ${s.bg} ${s.color}`}>
                          <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
                          {s.label}
                        </div>
                        <h3 className="text-lg font-bold text-white">{service?.name || 'Servicio General'}</h3>
                        <p className="text-steel-300 text-sm font-mono mt-1">Placa: {wo.vehicleInfo.plate} - {wo.vehicleInfo.brand} {wo.vehicleInfo.model}</p>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold text-forge-400">
                          {wo.startTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </div>
                        <div className="text-xs text-steel-400 uppercase tracking-wider font-bold mt-1">
                          {wo.startTime.toLocaleDateString()}
                        </div>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-4 border-t border-white/5">
                      <div>
                        <div className="text-[10px] text-steel-500 uppercase font-bold mb-1">Mecánico</div>
                        <div className="text-sm text-gray-200">{mechanic?.name || 'Por asignar'}</div>
                      </div>
                      <div>
                        <div className="text-[10px] text-steel-500 uppercase font-bold mb-1">Duración Est.</div>
                        <div className="text-sm text-gray-200">{formatDuration(wo.estimatedMinutes)}</div>
                      </div>
                      <div>
                        <div className="text-[10px] text-steel-500 uppercase font-bold mb-1">Costo Est.</div>
                        <div className="text-sm text-gray-200">₡{wo.price.toLocaleString()}</div>
                      </div>
                      <div className="flex items-center justify-end">
                        <button onClick={() => onCancelOrder(wo.id)} className="text-xs text-red-400 hover:text-red-300 transition-colors flex items-center gap-1 font-bold">
                          <XCircle size={14} /> Cancelar
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Quick Vehicles List */}
          <h2 className="text-xl font-bold text-white flex items-center gap-2 pt-6">
            <Wrench className="text-forge-500" size={24} />
            Mis Vehículos
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {currentUser.vehicles.map((v, i) => (
              <div key={i} className="glass-inner p-4 rounded-xl flex items-center gap-4 hover:border-forge-500/30 transition-all cursor-pointer">
                <div className="w-12 h-12 rounded-lg bg-steel-800 flex items-center justify-center border border-white/10 text-xl shadow-inner">
                  🚗
                </div>
                <div>
                  <div className="font-bold text-white">{v.brand} {v.model}</div>
                  <div className="text-xs text-steel-400 font-mono mt-0.5">Placa: {v.plate} · {v.year}</div>
                </div>
              </div>
            ))}
            {currentUser.vehicles.length === 0 && (
              <div className="col-span-full text-center p-4 text-steel-400 text-sm">
                No tienes vehículos registrados. Se agregarán al crear tu primera cita.
              </div>
            )}
          </div>
        </div>

        {/* Right Column: History */}
        <div className="space-y-6">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Clock className="text-forge-500" size={24} />
            Historial
          </h2>
          
          <div className="glass rounded-2xl overflow-hidden flex flex-col h-[600px]">
            <div className="p-4 border-b border-white/5 bg-white/5">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-400" size={16} />
                <input 
                  type="text" 
                  placeholder="Buscar en historial..." 
                  className="w-full bg-steel-900 border border-steel-600 rounded-lg pl-9 pr-4 py-2 text-sm text-white focus:border-forge-500 outline-none transition-all"
                />
              </div>
            </div>
            
            <div className="flex-1 overflow-y-auto p-2 space-y-2">
              {pastOrders.length === 0 ? (
                <div className="text-center p-6 text-steel-500 text-sm">
                  Aún no hay historial de servicios completados.
                </div>
              ) : (
                pastOrders.map(wo => {
                  const s = getStatusLabel(wo.status);
                  const serviceName = services.find(srv => srv.id === wo.serviceId)?.name || 'Servicio';
                  return (
                    <div key={wo.id} className="p-3 rounded-xl hover:bg-white/5 transition-colors cursor-pointer group">
                      <div className="flex justify-between items-start mb-1">
                        <div className="font-bold text-gray-200 text-sm group-hover:text-forge-400 transition-colors">{serviceName}</div>
                        <div className={`text-[10px] font-bold px-2 py-0.5 rounded-sm uppercase ${s.color}`}>{s.label}</div>
                      </div>
                      <div className="text-xs text-steel-400 font-mono mb-2">{wo.startTime.toLocaleDateString()} · {wo.vehicleInfo.plate}</div>
                      <div className="flex justify-between items-center text-xs">
                        <span className="text-gray-400">₡{wo.price.toLocaleString()}</span>
                        <span className="text-forge-500 opacity-0 group-hover:opacity-100 transition-opacity flex items-center">
                          Ver detalles <ChevronRight size={12} />
                        </span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
