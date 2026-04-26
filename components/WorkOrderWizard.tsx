
import React, { useState, useMemo } from 'react';
import { WorkOrder, WorkOrderStatus, Mechanic, Service, Client, VehicleInfo } from '../types';
import { calculateEndTime, hasConflict, formatDuration } from '../services/timeEngine';
import { X, Car, Wrench, User, Clock, Calendar, ChevronRight, Plus, Search, AlertCircle } from 'lucide-react';
import { Role } from '../types';

interface WorkOrderWizardProps {
  mechanics: Mechanic[];
  services: Service[];
  clients: Client[];
  existingOrders: WorkOrder[];
  shopRules: string;
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number;
  freeWashThreshold: number;
  currentUser: Client;
  currentRole: Role;
  onBook: (clientId: string, clientName: string, mechanicId: string, serviceId: string, time: Date, vehicle: VehicleInfo) => void;
  onCancel: () => void;
  onCreateClient: (data: any) => Client;
  onUpdateClient: (client: Client) => void;
  onDeleteClient: (id: string) => void;
}

type Step = 'client' | 'vehicle' | 'service' | 'mechanic' | 'schedule' | 'confirm';

export function WorkOrderWizard({
  mechanics,
  services,
  clients,
  existingOrders,
  shopRules,
  openHour,
  closeHour,
  timeSliceMinutes,
  freeWashThreshold,
  currentUser,
  currentRole,
  onBook,
  onCancel,
  onCreateClient,
}: WorkOrderWizardProps) {
  const [step, setStep] = useState<Step>(currentRole === Role.CLIENT ? 'vehicle' : 'client');
  const [selectedClient, setSelectedClient] = useState<Client | null>(currentRole === Role.CLIENT ? currentUser : null);
  const [selectedVehicle, setSelectedVehicle] = useState<VehicleInfo | null>(null);
  const [selectedService, setSelectedService] = useState<Service | null>(null);
  const [selectedMechanic, setSelectedMechanic] = useState<Mechanic | null>(null);
  const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [selectedTime, setSelectedTime] = useState<string>('');
  const [searchClient, setSearchClient] = useState('');
  const [notes, setNotes] = useState('');

  const filteredClients = useMemo(() => {
    if (!searchClient) return clients;
    const q = searchClient.toLowerCase();
    return clients.filter(c =>
      c.name.toLowerCase().includes(q) ||
      c.phone.includes(q) ||
      c.identification.includes(q) ||
      c.vehicles.some(v => v.plate.toLowerCase().includes(q))
    );
  }, [clients, searchClient]);

  const availableSlots = useMemo(() => {
    if (!selectedMechanic || !selectedService) return [];
    const slots: string[] = [];
    const dateObj = new Date(selectedDate + 'T00:00:00');

    for (let h = openHour; h < closeHour; h++) {
      for (let m = 0; m < 60; m += timeSliceMinutes) {
        const slotTime = new Date(dateObj);
        slotTime.setHours(h, m, 0, 0);
        const endTime = calculateEndTime(slotTime, selectedService.estimatedMinutes, selectedMechanic.efficiencyFactor);

        if (endTime.getHours() * 60 + endTime.getMinutes() > closeHour * 60) continue;

        const conflict = hasConflict(selectedMechanic.id, slotTime, endTime, existingOrders);
        if (!conflict) {
          slots.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`);
        }
      }
    }
    return slots;
  }, [selectedMechanic, selectedService, selectedDate, openHour, closeHour, timeSliceMinutes, existingOrders]);

  const handleConfirm = () => {
    if (!selectedClient || !selectedVehicle || !selectedService || !selectedMechanic || !selectedTime) return;
    const dateObj = new Date(selectedDate + 'T00:00:00');
    const [h, m] = selectedTime.split(':').map(Number);
    dateObj.setHours(h, m, 0, 0);
    onBook(selectedClient.id, selectedClient.name, selectedMechanic.id, selectedService.id, dateObj, selectedVehicle);
  };

  const steps: { key: Step; label: string; icon: React.ReactNode }[] = [
    ...(currentRole !== Role.CLIENT ? [{ key: 'client' as Step, label: 'Cliente', icon: <User size={14} /> }] : []),
    { key: 'vehicle', label: 'Vehículo', icon: <Car size={14} /> },
    { key: 'service', label: 'Servicio', icon: <Wrench size={14} /> },
    { key: 'mechanic', label: 'Mecánico', icon: <User size={14} /> },
    { key: 'schedule', label: 'Horario', icon: <Calendar size={14} /> },
    { key: 'confirm', label: 'Confirmar', icon: <ChevronRight size={14} /> },
  ];

  return (
    <div className="p-5">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Wrench size={20} className="text-forge-500" />
            Nueva Orden de Trabajo
          </h2>
          <p className="font-mono text-[10px] text-steel-300 mt-1">Complete los pasos para crear la orden</p>
        </div>
        <button onClick={onCancel} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1 border border-transparent hover:border-white/10">
          <span className="text-xs font-mono hidden sm:inline-block pr-1">Cerrar</span>
          <X size={18} />
        </button>
      </div>

      {/* Step Indicator */}
      <div className="flex items-center gap-1 mb-6 overflow-x-auto pb-2">
        {steps.map((s, i) => (
          <React.Fragment key={s.key}>
            <button
              onClick={() => setStep(s.key)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[10px] font-mono font-bold whitespace-nowrap transition-all ${
                step === s.key
                  ? 'bg-forge-500 text-black'
                  : 'text-steel-300 glass-inner hover:text-white'
              }`}
            >
              {s.icon}
              {s.label}
            </button>
            {i < steps.length - 1 && <ChevronRight size={12} className="text-steel-400 flex-shrink-0" />}
          </React.Fragment>
        ))}
      </div>

      {/* Step Content */}
      <div className="min-h-[300px]">
        {/* Client Selection */}
        {step === 'client' && (
          <div className="space-y-3 animate-slide-up">
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-steel-300" />
              <input
                type="text"
                value={searchClient}
                onChange={e => setSearchClient(e.target.value)}
                placeholder="Buscar por nombre, teléfono, cédula o placa..."
                className="w-full bg-steel-800 border border-steel-500 rounded-lg pl-9 pr-4 py-2.5 font-mono text-xs text-white placeholder-steel-300 focus:border-forge-500 outline-none transition-all"
                autoFocus
              />
            </div>
            <div className="max-h-[280px] overflow-y-auto space-y-1.5">
              {filteredClients.map(client => (
                <button
                  key={client.id}
                  onClick={() => { setSelectedClient(client); setStep('vehicle'); }}
                  className={`w-full text-left p-3 rounded-lg transition-all flex items-center gap-3 ${
                    selectedClient?.id === client.id ? 'bg-forge-500/10 border border-forge-500/30' : 'glass-inner glass-hover'
                  }`}
                >
                  <div className="w-10 h-10 rounded-lg overflow-hidden flex-shrink-0 border border-steel-500">
                    {client.avatar ? (
                      <img src={client.avatar} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full bg-steel-700 flex items-center justify-center">
                        <User size={16} className="text-steel-300" />
                      </div>
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-bold text-white">{client.name}</div>
                    <div className="font-mono text-[10px] text-steel-300">{client.phone} · {client.vehicles.length} vehículo(s)</div>
                  </div>
                  <ChevronRight size={14} className="text-steel-400" />
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Vehicle Selection */}
        {step === 'vehicle' && selectedClient && (
          <div className="space-y-3 animate-slide-up">
            <p className="font-mono text-xs text-steel-300">Vehículos de <span className="text-white font-bold">{selectedClient.name}</span></p>
            {selectedClient.vehicles.length === 0 && (
              <div className="glass-inner rounded-xl p-6 text-center">
                <Car size={24} className="text-steel-400 mx-auto mb-2" />
                <p className="text-sm text-steel-300">No hay vehículos registrados</p>
              </div>
            )}
            <div className="grid gap-2">
              {selectedClient.vehicles.map((v, i) => (
                <button
                  key={i}
                  onClick={() => { setSelectedVehicle(v); setStep('service'); }}
                  className={`w-full text-left p-4 rounded-xl transition-all flex items-center gap-4 ${
                    selectedVehicle?.plate === v.plate ? 'bg-forge-500/10 border border-forge-500/30' : 'glass-inner glass-hover'
                  }`}
                >
                  <div className="w-12 h-12 rounded-xl bg-steel-700 flex items-center justify-center flex-shrink-0 border border-steel-500">
                    <Car size={20} className="text-forge-500" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-bold text-white">{v.brand} {v.model} <span className="text-steel-200 font-normal">{v.year}</span></div>
                    <div className="font-mono text-xs text-forge-500 mt-0.5">{v.plate}</div>
                    <div className="font-mono text-[10px] text-steel-300 mt-0.5">{v.color} · {v.mileage.toLocaleString()} km · {v.fuelType}</div>
                  </div>
                  <ChevronRight size={14} className="text-steel-400" />
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Service Selection */}
        {step === 'service' && (
          <div className="space-y-2 animate-slide-up">
            <p className="font-mono text-xs text-steel-300 mb-3">Seleccione el servicio a realizar</p>
            <div className="max-h-[300px] overflow-y-auto space-y-1.5">
              {services.map(svc => (
                <button
                  key={svc.id}
                  onClick={() => { setSelectedService(svc); setStep('mechanic'); }}
                  className={`w-full text-left p-3 rounded-lg transition-all flex items-center gap-3 ${
                    selectedService?.id === svc.id ? 'bg-forge-500/10 border border-forge-500/30' : 'glass-inner glass-hover'
                  }`}
                >
                  <div className={`px-2 py-1 rounded text-[9px] font-mono font-bold uppercase badge-${svc.category}`}>
                    {svc.category === 'rep' ? 'REP' : svc.category === 'cam' ? 'CAM' : svc.category === 'mant' ? 'MANT' : 'DIAG'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-white">{svc.name}</div>
                    {svc.description && <div className="text-[10px] text-steel-300 mt-0.5">{svc.description}</div>}
                  </div>
                  <div className="text-right flex-shrink-0">
                    <div className="font-mono text-sm font-bold text-forge-500">₡{svc.basePrice.toLocaleString()}</div>
                    <div className="font-mono text-[10px] text-steel-300">{formatDuration(svc.estimatedMinutes)}</div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Mechanic Selection */}
        {step === 'mechanic' && (
          <div className="space-y-2 animate-slide-up">
            <p className="font-mono text-xs text-steel-300 mb-3">Asignar mecánico</p>
            <div className="grid gap-2">
              {mechanics.map(mech => (
                <button
                  key={mech.id}
                  onClick={() => { setSelectedMechanic(mech); setStep('schedule'); }}
                  className={`w-full text-left p-4 rounded-xl transition-all flex items-center gap-4 ${
                    selectedMechanic?.id === mech.id ? 'bg-forge-500/10 border border-forge-500/30' : 'glass-inner glass-hover'
                  }`}
                >
                  <div className="w-12 h-12 rounded-xl overflow-hidden border border-steel-500 flex-shrink-0">
                    <img src={mech.avatar} alt={mech.name} className="w-full h-full object-cover" />
                  </div>
                  <div className="flex-1">
                    <div className="text-sm font-bold text-white">{mech.name}</div>
                    <div className="font-mono text-[10px] text-forge-500 uppercase">{mech.specialty}</div>
                    {mech.certifications && (
                      <div className="flex gap-1 mt-1 flex-wrap">
                        {mech.certifications.map(cert => (
                          <span key={cert} className="font-mono text-[8px] px-1.5 py-0.5 rounded bg-forge-500/10 text-forge-400 border border-forge-500/20">{cert}</span>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="text-right">
                    <div className="font-mono text-xs text-steel-200">{Math.round(mech.efficiencyFactor * 100)}%</div>
                    <div className="font-mono text-[9px] text-steel-400">Eficiencia</div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Schedule */}
        {step === 'schedule' && (
          <div className="space-y-4 animate-slide-up">
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-2">Fecha</label>
              <input
                type="date"
                value={selectedDate}
                onChange={e => { setSelectedDate(e.target.value); setSelectedTime(''); }}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-4 py-2.5 font-mono text-sm text-white focus:border-forge-500 outline-none"
              />
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-2">
                Horarios Disponibles ({availableSlots.length})
              </label>
              {availableSlots.length === 0 ? (
                <div className="glass-inner rounded-lg p-4 text-center">
                  <AlertCircle size={18} className="text-red-400 mx-auto mb-1" />
                  <p className="text-xs text-steel-300">No hay horarios disponibles en esta fecha</p>
                </div>
              ) : (
                <div className="grid grid-cols-4 sm:grid-cols-6 gap-1.5 max-h-[200px] overflow-y-auto">
                  {availableSlots.map(slot => (
                    <button
                      key={slot}
                      onClick={() => { setSelectedTime(slot); setStep('confirm'); }}
                      className={`py-2 px-2 rounded-lg font-mono text-xs font-bold transition-all text-center ${
                        selectedTime === slot
                          ? 'bg-forge-500 text-black'
                          : 'glass-inner text-steel-200 hover:bg-forge-500/10 hover:text-forge-500 hover:border-forge-500/30'
                      }`}
                    >
                      {slot}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div>
              <label className="block font-mono text-[10px] text-steel-300 uppercase tracking-wider mb-2">Notas (opcional)</label>
              <textarea
                value={notes}
                onChange={e => setNotes(e.target.value)}
                rows={2}
                placeholder="Síntomas, observaciones del cliente..."
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-4 py-2.5 font-mono text-xs text-white placeholder-steel-300 focus:border-forge-500 outline-none resize-none"
              />
            </div>
          </div>
        )}

        {/* Confirmation */}
        {step === 'confirm' && (
          <div className="space-y-4 animate-slide-up">
            <h3 className="font-mono text-xs text-steel-300 uppercase tracking-wider">Resumen de Orden</h3>
            <div className="glass-inner rounded-xl p-4 space-y-3">
              {[
                { label: 'Cliente', value: selectedClient?.name },
                { label: 'Vehículo', value: selectedVehicle ? `${selectedVehicle.brand} ${selectedVehicle.model} ${selectedVehicle.year} (${selectedVehicle.plate})` : '' },
                { label: 'Servicio', value: selectedService?.name },
                { label: 'Mecánico', value: selectedMechanic?.name },
                { label: 'Fecha', value: new Date(selectedDate + 'T00:00:00').toLocaleDateString('es-CR', { weekday: 'long', day: 'numeric', month: 'long' }) },
                { label: 'Hora', value: selectedTime },
                { label: 'Duración Est.', value: selectedService ? formatDuration(selectedService.estimatedMinutes) : '' },
                { label: 'Precio', value: selectedService ? `₡${selectedService.basePrice.toLocaleString('es-CR')}` : '' },
              ].map(item => (
                <div key={item.label} className="flex justify-between items-center py-1 border-b border-steel-600/20 last:border-0">
                  <span className="font-mono text-[10px] text-steel-300 uppercase">{item.label}</span>
                  <span className="text-sm font-medium text-white">{item.value}</span>
                </div>
              ))}
              {selectedService && selectedService.basePrice > freeWashThreshold && (
                <div className="mt-2 flex items-center justify-center p-2 rounded-lg bg-green-500/10 border border-green-500/20">
                  <span className="font-bold text-xs text-green-400 uppercase tracking-wider">🏷️ Incluye Lavado y Aspirado Gratis</span>
                </div>
              )}
            </div>

            {shopRules && (
              <div className="glass-inner rounded-lg p-3">
                <p className="font-mono text-[10px] text-forge-500 uppercase tracking-wider mb-1">Políticas del Taller</p>
                <p className="text-[10px] text-steel-300 whitespace-pre-line">{shopRules}</p>
              </div>
            )}

            <div className="flex gap-3 pt-2">
              <button
                onClick={onCancel}
                className="flex-1 border border-steel-600 hover:bg-steel-800 text-steel-300 font-bold py-3 rounded-lg transition-all font-mono text-sm tracking-wider uppercase"
              >
                Cancelar
              </button>
              <button
                onClick={handleConfirm}
                disabled={!selectedClient || !selectedVehicle || !selectedService || !selectedMechanic || !selectedTime}
                className="flex-[2] bg-forge-500 hover:bg-forge-400 disabled:opacity-50 disabled:cursor-not-allowed text-black font-bold py-3 rounded-lg transition-all font-mono text-sm tracking-wider uppercase forge-glow"
              >
                ✓ Confirmar Orden
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
