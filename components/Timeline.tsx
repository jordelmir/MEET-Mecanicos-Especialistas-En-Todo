
import React, { useState, useEffect } from 'react';
import { Appointment, Barber, AppointmentStatus, Service } from '../types';
import { formatTime, getStatusColor, formatDate } from '../services/timeEngine';
import { Clock, CheckCircle, AlertTriangle, Play, CheckSquare, ChevronLeft, ChevronRight, MoreVertical, Edit3, Radio } from 'lucide-react';

interface TimelineProps {
  barbers: Barber[];
  appointments: Appointment[];
  services: Service[]; 
  currentDate: Date;
  openHour: number;
  closeHour: number;
  timeSliceMinutes: number; // New Prop for dynamic grid
  onStatusChange: (appointmentId: string, status: AppointmentStatus) => void;
  onDateChange: (date: Date) => void;
  onEditAppointment: (appointment: Appointment) => void; 
}

export const Timeline: React.FC<TimelineProps> = ({ 
  barbers, appointments, services, currentDate, openHour, closeHour, timeSliceMinutes, onStatusChange, onDateChange, onEditAppointment 
}) => {
  const [now, setNow] = useState(new Date());

  // Simulate clock tick
  useEffect(() => {
    const interval = setInterval(() => setNow(new Date()), 60000);
    return () => clearInterval(interval);
  }, []);

  // Filter appointments for the current day view
  const todaysAppointments = appointments.filter(a => 
    a.startTime.getDate() === currentDate.getDate() &&
    a.startTime.getMonth() === currentDate.getMonth() &&
    a.startTime.getFullYear() === currentDate.getFullYear()
  );

  // Calculate timeline dimensions
  const totalMinutes = (closeHour - openHour) * 60;
  const pixelsPerMinute = 2.5; 
  const timelineHeight = totalMinutes * pixelsPerMinute;

  const getTopOffset = (date: Date) => {
    const hours = date.getHours();
    const minutes = date.getMinutes();
    const minutesSinceOpen = (hours - openHour) * 60 + minutes;
    return Math.max(0, minutesSinceOpen * pixelsPerMinute);
  };

  const getHeight = (start: Date, end: Date) => {
    const diffMs = end.getTime() - start.getTime();
    const diffMins = Math.ceil(diffMs / 60000);
    return diffMins * pixelsPerMinute;
  };

  const getCurrentTimeOffset = () => {
    // Only show if today
    if (now.getDate() !== currentDate.getDate()) return -1;
    return getTopOffset(now);
  };

  const changeDate = (days: number) => {
      const newDate = new Date(currentDate);
      newDate.setDate(newDate.getDate() + days);
      onDateChange(newDate);
  }

  const isToday = now.toDateString() === currentDate.toDateString();

  // Time markers
  const hours = Array.from({ length: closeHour - openHour }, (_, i) => i + openHour);
  
  // Calculate Grid Lines based on Time Slice
  const gridLinesPerHour = 60 / timeSliceMinutes;
  const totalGridLines = (closeHour - openHour) * gridLinesPerHour;

  return (
    <div className="flex flex-col bg-dark-900/50 rounded-xl relative">
      
      {/* Date Navigation Header - Glassmorphism */}
      <div className="glass-morphism flex items-center justify-between px-6 py-4 border-b border-white/10 relative overflow-hidden group/header z-30 rounded-t-xl">
         {/* Decorative tech accents */}
         <div className="absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent via-white/10 to-transparent opacity-50"></div>
         
         <button 
            onClick={() => changeDate(-1)} 
            className="group relative flex items-center justify-center w-10 h-10 border border-white/10 bg-white/5 rounded hover:border-brand-500/50 hover:bg-white/10 transition-all active:scale-95"
         >
            <ChevronLeft size={18} className="text-gray-500 group-hover:text-brand-400 transition-colors"/>
         </button>

         <div className="flex flex-col items-center select-none">
             <div className="flex items-center gap-3 mb-1.5">
                 <div className={`w-1.5 h-1.5 rounded-full shadow-[0_0_8px_rgba(16,185,129,0.5)] ${isToday ? 'bg-emerald-500 animate-pulse' : 'bg-gray-600'}`}></div>
                 <span className={`text-[10px] font-mono uppercase tracking-[0.25em] ${isToday ? 'text-emerald-500/80' : 'text-gray-500'}`}>
                    {isToday ? 'SISTEMA EN LÍNEA' : 'MODO HISTÓRICO'}
                 </span>
             </div>
             
             <div className="flex items-baseline gap-3 relative z-10">
                <span className="text-2xl font-black text-white tracking-tighter uppercase font-sans drop-shadow-lg capitalize">
                    {currentDate.toLocaleDateString('es-CR', { weekday: 'long' })}
                </span>
                <span className="text-2xl font-thin text-gray-600">|</span>
                <span className="text-2xl font-mono font-bold text-brand-500 tracking-tighter drop-shadow-[0_0_15px_rgba(240,180,41,0.2)]">
                    {currentDate.toLocaleDateString('es-CR', { day: '2-digit' })}
                </span>
                <span className="text-lg font-bold uppercase tracking-wide text-gray-400 self-end pb-0.5">
                    {currentDate.toLocaleDateString('es-CR', { month: 'short' })}. {currentDate.getFullYear()}
                </span>
             </div>
         </div>

         <button 
            onClick={() => changeDate(1)} 
            className="group relative flex items-center justify-center w-10 h-10 border border-white/10 bg-white/5 rounded hover:border-brand-500/50 hover:bg-white/10 transition-all active:scale-95"
         >
            <ChevronRight size={18} className="text-gray-500 group-hover:text-brand-400 transition-colors"/>
         </button>
      </div>

      {/* Horizontal Scroll Container for Header + Grid */}
      <div className="overflow-x-auto custom-scrollbar relative">
        <div className="min-w-max">
            
            {/* Header (Barbers) - Sticky relative to document scroll */}
            <div className="flex border-b border-white/5 bg-dark-900/95 z-20 sticky top-16 shadow-lg backdrop-blur-xl">
                <div className="w-16 flex-shrink-0 border-r border-white/5 p-2 flex items-center justify-center bg-dark-900/95 backdrop-blur sticky left-0 z-30 shadow-[4px_0_24px_-4px_rgba(0,0,0,0.5)]">
                    <div className="text-[10px] text-gray-500 font-mono text-center leading-tight">HORA<br/>LOCAL</div>
                </div>
                {barbers.map(barber => (
                <div key={barber.id} className="w-[200px] flex-shrink-0 p-3 border-r border-white/5 flex items-center gap-3 bg-dark-900/95 backdrop-blur">
                    <div className="relative group cursor-pointer">
                        <img src={barber.avatar} alt={barber.name} className="w-10 h-10 rounded-full border-2 border-dark-600 transition-all group-hover:border-brand-500" />
                        <div className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-dark-800 flex items-center justify-center text-[8px] font-bold ${barber.speedFactor <= 1 ? 'bg-emerald-500 text-black' : 'bg-yellow-500 text-black'}`}>
                            {barber.speedFactor}x
                        </div>
                    </div>
                    <div className="min-w-0">
                        <h3 className="font-bold text-gray-200 text-sm truncate">{barber.name}</h3>
                        <p className="text-[10px] text-gray-500 uppercase tracking-wide flex items-center gap-1">
                            {barber.tier}
                        </p>
                    </div>
                </div>
                ))}
            </div>

            {/* Timeline Grid */}
            <div className="relative bg-dark-900/80">
                <div className="flex relative" style={{ height: `${timelineHeight}px` }}>
                    
                    {/* Time Axis - Sticky Left */}
                    <div className="w-16 flex-shrink-0 border-r border-white/5 bg-dark-900/90 z-20 backdrop-blur-sm sticky left-0 shadow-[4px_0_24px_-4px_rgba(0,0,0,0.5)]">
                        {hours.map(hour => (
                        <div 
                            key={hour} 
                            className="absolute w-full border-t border-white/5 text-right pr-2 text-[10px] text-gray-500 font-mono"
                            style={{ top: (hour - openHour) * 60 * pixelsPerMinute }}
                        >
                            <span className="relative -top-2 bg-dark-900/80 px-1 rounded">{hour > 12 && hour !== 24 ? hour - 12 : hour}{hour >= 12 && hour !== 24 ? 'pm' : 'am'}</span>
                        </div>
                        ))}
                    </div>

                    {/* Current Time Indicator */}
                    {getCurrentTimeOffset() >= 0 && (
                        <div 
                            className="absolute z-30 w-full border-t border-red-500/80 pointer-events-none flex items-center"
                            style={{ top: getCurrentTimeOffset() }}
                        >
                            <div className="sticky left-0 w-16 text-right pr-2 text-[9px] font-bold text-red-500 -mt-2 bg-dark-900/90 backdrop-blur rounded-r z-40">
                                {now.toLocaleTimeString('es-CR', {hour: '2-digit', minute:'2-digit'})}
                            </div>
                            <div className="absolute left-16 w-full h-[1px] bg-red-500/50 shadow-[0_0_8px_rgba(239,68,68,0.6)]"></div>
                        </div>
                    )}

                    {/* Columns */}
                    {barbers.map(barber => {
                        const barberAppointments = todaysAppointments.filter(a => a.barberId === barber.id && a.status !== AppointmentStatus.CANCELLED);
                        
                        return (
                        <div key={barber.id} className="w-[200px] flex-shrink-0 relative border-r border-white/5 group/col">
                            
                            {/* Grid Lines - DYNAMIC based on timeSliceMinutes */}
                            {Array.from({length: totalGridLines}).map((_, i) => (
                                <div 
                                    key={i} 
                                    className={`absolute w-full border-t ${i % gridLinesPerHour === 0 ? 'border-white/10' : 'border-white/5'}`}
                                    style={{ top: i * timeSliceMinutes * pixelsPerMinute }}
                                ></div>
                            ))}
                            
                            <div className="absolute inset-0 bg-white/0 group-hover/col:bg-white/[0.02] pointer-events-none transition-colors"></div>

                            {/* Appointments */}
                            {barberAppointments.map(apt => {
                                const top = getTopOffset(apt.startTime);
                                const totalHeight = getHeight(apt.startTime, apt.expectedEndTime);
                                const service = services.find(s => s.id === apt.serviceId);
                                const baseDuration = service ? service.durationMinutes : 30; 
                                const baseHeight = baseDuration * pixelsPerMinute;
                                const hasEfficiencyLoss = barber.speedFactor > 1;
                                const isLive = apt.status === AppointmentStatus.IN_PROGRESS;

                                return (
                                    <div
                                        key={apt.id}
                                        className={`absolute left-1 right-1 rounded-md border shadow-lg group transition-all duration-200 hover:z-20 hover:scale-[1.02] ${getStatusColor(apt.status)} ${isLive ? 'border-red-500 ring-1 ring-red-500/50' : ''}`}
                                        style={{ top, height: totalHeight }}
                                    >
                                        {/* Visualizing the "Efficiency Cost" */}
                                        {hasEfficiencyLoss && (
                                            <div 
                                                className="absolute bottom-0 left-0 right-0 bg-[url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAAIklEQVQIW2NkQAKrVq36zwjjgzjwqonyQAwGk06AAmw7ADtpDdzKo15nAAAAAElFTkSuQmCC')] opacity-20 pointer-events-none"
                                                style={{ height: totalHeight - baseHeight }}
                                            ></div>
                                        )}

                                        <div className="flex justify-between items-start h-full flex-col p-2 relative overflow-hidden">
                                            <div className="w-full relative z-10">
                                                <div className="flex justify-between items-start">
                                                    <span className="font-bold text-xs truncate w-2/3 text-white drop-shadow-md">{apt.clientName}</span>
                                                    {isLive && (
                                                        <span className="flex items-center gap-1 text-[8px] font-black text-red-500 bg-red-950/80 px-1.5 py-0.5 rounded border border-red-500/30 animate-pulse">
                                                            <Radio size={8} /> LIVE
                                                        </span>
                                                    )}
                                                </div>
                                                <div className="flex items-center justify-between mt-0.5">
                                                    <div className="text-[10px] font-mono opacity-90 bg-black/20 px-1 rounded inline-block">{formatTime(apt.startTime)}</div>
                                                    <div className="text-[10px] font-mono text-gray-300 opacity-80">₡{apt.price.toLocaleString('es-CR')}</div>
                                                </div>
                                            </div>
                                            
                                            {/* Quick Actions Overlay */}
                                            <div className="hidden group-hover:flex gap-1 mt-auto w-full justify-end bg-dark-900/80 backdrop-blur-md p-1 rounded-md border border-white/10 shadow-2xl animate-in fade-in slide-in-from-bottom-2 duration-200">
                                                <button 
                                                    onClick={() => onEditAppointment(apt)}
                                                    className="p-1.5 hover:bg-dark-600 rounded text-gray-300 hover:text-white transition-colors border-r border-white/10" title="Modificar"
                                                >
                                                    <Edit3 size={14} />
                                                </button>

                                                {/* States Buttons Redundant if using Dashboard, but kept for direct calendar manipulation */}
                                                {apt.status === AppointmentStatus.SCHEDULED && (
                                                    <button onClick={() => onStatusChange(apt.id, AppointmentStatus.CHECKED_IN)} className="p-1.5 hover:bg-green-500 rounded text-green-200 hover:text-black transition-colors" title="Llegó"><CheckSquare size={14} /></button>
                                                )}
                                                {apt.status === AppointmentStatus.CHECKED_IN && (
                                                    <button onClick={() => onStatusChange(apt.id, AppointmentStatus.IN_PROGRESS)} className="p-1.5 hover:bg-brand-500 rounded text-brand-200 hover:text-black transition-colors" title="Iniciar"><Play size={14} /></button>
                                                )}
                                                {apt.status === AppointmentStatus.IN_PROGRESS && (
                                                    <button onClick={() => onStatusChange(apt.id, AppointmentStatus.COMPLETED)} className="p-1.5 hover:bg-emerald-500 rounded text-emerald-200 hover:text-black transition-colors" title="Finalizar"><CheckCircle size={14} /></button>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                        );
                    })}
                </div>
            </div>
        </div>
      </div>
    </div>
  );
};