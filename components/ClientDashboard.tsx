import React, { useState } from 'react';
import { WorkOrder, Client, Service, Mechanic, WorkOrderStatus, VehicleInfo } from '../types';
import { Calendar, Clock, MapPin, CheckCircle, Wrench, ChevronRight, XCircle, Search, AlertCircle, Plus, Edit2, Save, Gauge, Smartphone, Activity, ShieldAlert, Info } from 'lucide-react';
import { formatDuration } from '../services/timeEngine';

interface ClientDashboardProps {
  currentUser: Client;
  workOrders: WorkOrder[];
  services: Service[];
  mechanics: Mechanic[];
  freeWashThreshold: number;
  onBookNew: () => void;
  onCancelOrder: (id: string) => void;
  onUpdateUser?: (client: Client) => void;
  onSimulateAPKScan?: () => void;
}

export function ClientDashboard({ currentUser, workOrders, services, mechanics, freeWashThreshold, onBookNew, onCancelOrder, onUpdateUser, onSimulateAPKScan }: ClientDashboardProps) {
  const [editingMileage, setEditingMileage] = useState<number | null>(null);
  const [tempMileage, setTempMileage] = useState<number>(0);
  const [isAddingVehicle, setIsAddingVehicle] = useState(false);
  const [newVehicle, setNewVehicle] = useState<Partial<VehicleInfo>>({});

  const handleSaveMileage = (index: number) => {
    if (onUpdateUser && tempMileage >= 0) {
      const updatedUser = { ...currentUser };
      updatedUser.vehicles[index].mileage = tempMileage;
      onUpdateUser(updatedUser);
    }
    setEditingMileage(null);
  };

  const handleAddVehicle = () => {
    if (onUpdateUser && newVehicle.plate && newVehicle.brand && newVehicle.model) {
      const vehicle: VehicleInfo = {
        plate: newVehicle.plate,
        brand: newVehicle.brand,
        model: newVehicle.model,
        year: newVehicle.year || new Date().getFullYear(),
        color: newVehicle.color || 'Desconocido',
        mileage: newVehicle.mileage || 0,
        fuelType: newVehicle.fuelType as any || 'Gasolina',
      };
      const updatedUser = { ...currentUser };
      updatedUser.vehicles.push(vehicle);
      onUpdateUser(updatedUser);
      setIsAddingVehicle(false);
      setNewVehicle({});
    }
  };

  const userOrders = workOrders.filter(wo => wo.clientId === currentUser.id);
  const [viewingHistoryOrder, setViewingHistoryOrder] = useState<WorkOrder | null>(null);

  const getPreventiveRecommendations = () => {
    const recs: any[] = [];
    currentUser.vehicles.forEach(v => {
      const km = v.mileage;
      if (km === 0) return;
      
      const addRec = (interval: number, component: string, action: string, threshold: number, isCritical: boolean = false) => {
        const next = Math.ceil(km / interval) * interval;
        const diff = next - km;
        if (diff <= threshold) {
          recs.push({
            vehicle: `${v.brand} ${v.model}`,
            plate: v.plate,
            component,
            action: `${action} a los ${next.toLocaleString()} km`,
            urgency: diff <= 0 ? 'high' : (isCritical ? 'high' : (diff <= threshold / 2 ? 'medium' : 'low')),
            dueAt: next,
            diff
          });
        }
      };

      addRec(5000, 'Aceite de Motor', 'Cambio sugerido', 1000);
      addRec(40000, 'Aceite de Transmisión', 'Cambio programado', 3000, true);
      addRec(30000, 'Bujías', 'Reemplazo recomendado', 2000);
      addRec(80000, 'Faja/Cadena Distribución', 'Inspección/Cambio crítico', 5000, true);
      addRec(20000, 'Frenos', 'Inspección de pastillas y discos', 2000, true);
      addRec(15000, 'Filtros (Aire/Cabina)', 'Reemplazo preventivo', 1500);
      addRec(40000, 'Refrigerante', 'Drenado y llenado', 3000);
    });
    
    return recs.sort((a, b) => {
      const w = { high: 0, medium: 1, low: 2 };
      if (w[a.urgency as keyof typeof w] !== w[b.urgency as keyof typeof w]) return w[a.urgency as keyof typeof w] - w[b.urgency as keyof typeof w];
      return a.diff - b.diff;
    }).slice(0, 5); // top 5 recommendations
  };

  const recommendations = getPreventiveRecommendations();
  
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
                        {wo.price > freeWashThreshold && (
                          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider mb-2 bg-green-500/10 text-green-400 ml-2 border border-green-500/20">
                            🏷️ LAVADO Y ASPIRADO GRATIS
                          </div>
                        )}
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
          <div className="flex items-center justify-between pt-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <Wrench className="text-forge-500" size={24} />
              Mis Vehículos
            </h2>
            <button 
              onClick={() => setIsAddingVehicle(!isAddingVehicle)} 
              className="text-xs font-bold text-forge-400 hover:text-forge-300 flex items-center gap-1"
            >
              {isAddingVehicle ? 'Cancelar' : <><Plus size={14} /> Agregar Vehículo</>}
            </button>
          </div>

          {isAddingVehicle && (
            <div className="glass-inner p-4 rounded-xl border border-forge-500/30 mb-4 animate-slide-up grid grid-cols-2 sm:grid-cols-3 gap-3">
              <input type="text" placeholder="Placa (ej. ABC-123)" className="bg-steel-900 border border-steel-600 rounded p-2 text-sm text-white" value={newVehicle.plate || ''} onChange={e => setNewVehicle({...newVehicle, plate: e.target.value})} />
              <input type="text" placeholder="Marca (ej. Toyota)" className="bg-steel-900 border border-steel-600 rounded p-2 text-sm text-white" value={newVehicle.brand || ''} onChange={e => setNewVehicle({...newVehicle, brand: e.target.value})} />
              <input type="text" placeholder="Modelo (ej. Yaris)" className="bg-steel-900 border border-steel-600 rounded p-2 text-sm text-white" value={newVehicle.model || ''} onChange={e => setNewVehicle({...newVehicle, model: e.target.value})} />
              <input type="number" placeholder="Año" className="bg-steel-900 border border-steel-600 rounded p-2 text-sm text-white" value={newVehicle.year || ''} onChange={e => setNewVehicle({...newVehicle, year: +e.target.value})} />
              <input type="number" placeholder="Kilometraje Inicial" className="bg-steel-900 border border-steel-600 rounded p-2 text-sm text-white" value={newVehicle.mileage || ''} onChange={e => setNewVehicle({...newVehicle, mileage: +e.target.value})} />
              <button onClick={handleAddVehicle} className="bg-forge-500 text-black font-bold rounded p-2 hover:bg-forge-400 transition-colors flex items-center justify-center gap-2">
                <Save size={16} /> Guardar
              </button>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {currentUser.vehicles.map((v, i) => (
              <div key={i} className="glass-inner p-4 rounded-xl flex flex-col gap-2 hover:border-forge-500/30 transition-all">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 rounded-lg bg-steel-800 flex items-center justify-center border border-white/10 text-xl shadow-inner flex-shrink-0">
                    🚗
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-bold text-white truncate">{v.brand} {v.model}</div>
                    <div className="text-xs text-steel-400 font-mono mt-0.5 truncate">Placa: {v.plate} · {v.year}</div>
                  </div>
                </div>
                <div className="flex items-center justify-between mt-2 pt-2 border-t border-white/5">
                  {editingMileage === i ? (
                    <div className="flex items-center gap-2 w-full">
                      <span className="text-[10px] text-steel-400 font-mono">KM:</span>
                      <input 
                        type="number" 
                        value={tempMileage} 
                        onChange={e => setTempMileage(+e.target.value)}
                        className="bg-steel-900 border border-forge-500 rounded px-2 py-1 text-xs text-forge-500 font-mono outline-none flex-1"
                        autoFocus
                        onKeyDown={e => e.key === 'Enter' && handleSaveMileage(i)}
                      />
                      <button onClick={() => handleSaveMileage(i)} className="text-green-400 p-1 hover:bg-green-400/10 rounded">
                        <CheckCircle size={14} />
                      </button>
                      <button onClick={() => setEditingMileage(null)} className="text-red-400 p-1 hover:bg-red-400/10 rounded">
                        <XCircle size={14} />
                      </button>
                    </div>
                  ) : (
                    <>
                      <div className="text-xs text-forge-500 font-mono flex items-center gap-1">
                        <Gauge size={12} /> {v.mileage.toLocaleString()} km
                      </div>
                      <button 
                        onClick={() => { setEditingMileage(i); setTempMileage(v.mileage); }}
                        className="text-[10px] text-steel-400 hover:text-forge-400 flex items-center gap-1 transition-colors bg-white/5 px-2 py-1 rounded-md"
                      >
                        <Edit2 size={10} /> Actualizar KM
                      </button>
                    </>
                  )}
                </div>
              </div>
            ))}
            {currentUser.vehicles.length === 0 && (
              <div className="col-span-full text-center p-4 text-steel-400 text-sm">
                No tienes vehículos registrados. Agrega uno nuevo o se crearán al hacer una cita.
              </div>
            )}
          </div>

          {/* Escaneos OBD2 desde APK */}
          <div className="mt-8 pt-6 border-t border-white/5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-bold text-white flex items-center gap-2">
                <Smartphone className="text-forge-500" size={24} />
                Diagnósticos desde App MEET
              </h2>
              {onSimulateAPKScan && (
                <button 
                  onClick={onSimulateAPKScan}
                  className="text-xs font-bold bg-forge-500/10 text-forge-400 hover:bg-forge-500/20 px-3 py-1.5 rounded flex items-center gap-2 transition-colors border border-forge-500/30"
                >
                  <Activity size={14} /> Simular Escaneo
                </button>
              )}
            </div>
            
            <div className="space-y-4">
              {(!currentUser.scans || currentUser.scans.length === 0) ? (
                <div className="glass-inner p-6 rounded-2xl text-center border-dashed border-2 border-steel-600">
                  <Activity size={32} className="text-steel-500 mx-auto mb-3 opacity-50" />
                  <h3 className="text-white font-bold mb-1">Sin escaneos recientes</h3>
                  <p className="text-steel-400 text-sm">Conecta tu escáner OBD2 desde la App móvil de MEET para ver el estado de tu vehículo en tiempo real.</p>
                </div>
              ) : (
                currentUser.scans.sort((a, b) => b.date.getTime() - a.date.getTime()).map(scan => (
                  <div key={scan.id} className={`glass-inner p-5 rounded-xl border-l-4 ${
                    scan.severity === 'high' ? 'border-red-500 bg-red-500/5' :
                    scan.severity === 'medium' ? 'border-yellow-500 bg-yellow-500/5' :
                    'border-blue-500 bg-blue-500/5'
                  }`}>
                    <div className="flex justify-between items-start mb-3">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          {scan.severity === 'high' ? <ShieldAlert size={16} className="text-red-500" /> : <Info size={16} className="text-forge-500" />}
                          <span className="font-bold text-white tracking-wider text-sm">REPORTE OBD2</span>
                        </div>
                        <div className="text-xs text-steel-400 font-mono">{scan.date.toLocaleString()} · Placa: {scan.vehiclePlate}</div>
                      </div>
                      <div className={`px-2 py-1 rounded text-[10px] font-bold uppercase tracking-wider ${
                        scan.severity === 'high' ? 'bg-red-500/20 text-red-400' :
                        scan.severity === 'medium' ? 'bg-yellow-500/20 text-yellow-400' :
                        'bg-blue-500/20 text-blue-400'
                      }`}>
                        {scan.severity === 'high' ? 'Crítico' : scan.severity === 'medium' ? 'Moderado' : 'Leve'}
                      </div>
                    </div>
                    
                    <div className="flex flex-wrap gap-2 mb-3">
                      {scan.dtcCodes.map(code => (
                        <span key={code} className="bg-steel-900 border border-white/10 px-2 py-1 rounded text-xs text-white font-mono font-bold shadow-sm">
                          {code}
                        </span>
                      ))}
                    </div>
                    
                    {scan.notes && (
                      <p className="text-sm text-steel-300 italic">"{scan.notes}"</p>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Recomendaciones Preventivas */}
          <div className="mt-8">
            <h2 className="text-xl font-bold text-white flex items-center gap-2 mb-4">
              <AlertCircle className="text-forge-500" size={24} />
              Recomendaciones Preventivas
            </h2>
            <div className="space-y-3">
              {recommendations.length === 0 ? (
                <div className="glass-inner p-4 rounded-xl text-center text-steel-400 text-sm">
                  Todos tus vehículos están al día. ¡Excelente!
                </div>
              ) : (
                recommendations.map((rec, i) => (
                  <div key={i} className="glass-inner p-4 rounded-xl border-l-2" style={{ borderLeftColor: rec.urgency === 'high' ? '#ef4444' : rec.urgency === 'medium' ? '#f59e0b' : '#3b82f6' }}>
                    <div className="flex justify-between items-start">
                      <div>
                        <div className="text-white font-bold text-sm">{rec.component}</div>
                        <div className="text-steel-400 text-xs mt-1">{rec.action}</div>
                        <div className="text-[10px] text-forge-500 font-mono mt-2">{rec.vehicle} · {rec.plate}</div>
                      </div>
                      <div className={`px-2 py-1 rounded text-[9px] font-bold uppercase tracking-wider ${
                        rec.urgency === 'high' ? 'bg-red-500/20 text-red-400' : 
                        rec.urgency === 'medium' ? 'bg-yellow-500/20 text-yellow-400' : 'bg-blue-500/20 text-blue-400'
                      }`}>
                        {rec.urgency === 'high' ? 'Urgente' : rec.urgency === 'medium' ? 'Pronto' : 'Preventivo'}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
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
                    <div key={wo.id} onClick={() => setViewingHistoryOrder(wo)} className="p-3 rounded-xl hover:bg-white/5 transition-colors cursor-pointer group">
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

      {/* History Detail Modal */}
      {viewingHistoryOrder && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-fade-in">
          <div className="glass rounded-2xl p-6 w-full max-w-lg border border-forge-500/30 animate-slide-up">
            <div className="flex justify-between items-start mb-6">
              <div>
                <h3 className="text-xl font-bold text-white font-display tracking-wider">Detalle del Servicio</h3>
                <p className="text-steel-400 text-sm font-mono mt-1">{viewingHistoryOrder.startTime.toLocaleDateString()} · {viewingHistoryOrder.vehicleInfo.plate}</p>
              </div>
              <button onClick={() => setViewingHistoryOrder(null)} className="text-steel-400 hover:text-white bg-white/5 p-2 rounded-lg">
                <XCircle size={20} />
              </button>
            </div>
            
            <div className="space-y-4">
              <div className="bg-steel-900/50 p-4 rounded-xl border border-white/5">
                <div className="text-[10px] text-steel-500 uppercase tracking-wider font-bold mb-1">Servicio Realizado</div>
                <div className="text-white font-bold">{services.find(s => s.id === viewingHistoryOrder.serviceId)?.name || 'Servicio'}</div>
                {viewingHistoryOrder.price > freeWashThreshold && (
                  <div className="mt-2 text-[10px] text-green-400 bg-green-500/10 border border-green-500/20 px-2 py-1 rounded-full inline-block font-bold">
                    🏷️ INCLUYÓ LAVADO Y ASPIRADO GRATIS
                  </div>
                )}
              </div>

              <div className="bg-steel-900/50 p-4 rounded-xl border border-white/5">
                <div className="text-[10px] text-steel-500 uppercase tracking-wider font-bold mb-1">Diagnóstico / Notas de reparación</div>
                <div className="text-steel-200 text-sm">{viewingHistoryOrder.diagnosticNotes || viewingHistoryOrder.notes || 'Revisión y mantenimiento general según protocolo.'}</div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="bg-steel-900/50 p-4 rounded-xl border border-white/5">
                  <div className="text-[10px] text-steel-500 uppercase tracking-wider font-bold mb-1">Mecánico a cargo</div>
                  <div className="text-white text-sm">{mechanics.find(m => m.id === viewingHistoryOrder.mechanicId)?.name || 'General'}</div>
                </div>
                <div className="bg-steel-900/50 p-4 rounded-xl border border-white/5">
                  <div className="text-[10px] text-steel-500 uppercase tracking-wider font-bold mb-1">Costo Total</div>
                  <div className="text-forge-400 font-bold text-lg font-mono">₡{viewingHistoryOrder.price.toLocaleString()}</div>
                </div>
              </div>
            </div>
            
            <div className="mt-6 flex justify-end">
              <button onClick={() => setViewingHistoryOrder(null)} className="px-6 py-2 bg-white/10 hover:bg-white/20 text-white font-bold rounded-lg transition-colors">
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
