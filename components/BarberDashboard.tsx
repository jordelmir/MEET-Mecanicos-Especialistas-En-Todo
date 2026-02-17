
import React, { useState, useEffect, useMemo } from 'react';
import { Appointment, AppointmentStatus, Service, Barber } from '../types';
import { Play, CheckSquare, Clock, User, Scissors, Gauge, CheckCircle2, XCircle, LogIn, Trophy, Zap, Award, BarChart3, Crown, LayoutDashboard, History } from 'lucide-react';
import { formatTime } from '../services/timeEngine';
import { BarChart, Bar, XAxis, Tooltip, ResponsiveContainer, Cell, CartesianGrid } from 'recharts';
import { MetricsPanel } from './MetricsPanel';

interface BarberDashboardProps {
    barberId: string;
    currentBarber?: Barber; 
    barbers: Barber[]; // Need all barbers for leaderboard
    appointments: Appointment[];
    services: Service[];
    onStatusChange: (id: string, status: AppointmentStatus) => void;
    onUpdateBarber?: (barber: Barber) => void; 
    openHour: number;
    closeHour: number;
}

export const BarberDashboard: React.FC<BarberDashboardProps> = ({ 
    barberId, currentBarber, barbers = [], appointments, services, onStatusChange, onUpdateBarber,
    openHour, closeHour
}) => {
    const [now, setNow] = useState(new Date());
    const [rankingPeriod, setRankingPeriod] = useState<'today' | 'week' | 'month'>('today');
    
    // VIEW MODE SWITCH: OPERATIONAL vs FINANCIAL
    const [viewMode, setViewMode] = useState<'OPERATIONAL' | 'ANALYTICS'>('OPERATIONAL');

    useEffect(() => {
        const interval = setInterval(() => setNow(new Date()), 1000);
        return () => clearInterval(interval);
    }, []);

    // Filter appointments for today
    const todaysAppointments = useMemo(() => {
        return appointments.filter(a => 
            a.startTime.getDate() === now.getDate() && 
            a.startTime.getMonth() === now.getMonth() &&
            a.startTime.getFullYear() === now.getFullYear() &&
            a.status !== AppointmentStatus.CANCELLED
        ).sort((a, b) => a.startTime.getTime() - b.startTime.getTime());
    }, [appointments, now]);

    // Derived States
    const activeCut = todaysAppointments.find(a => a.status === AppointmentStatus.IN_PROGRESS);
    
    // The Queue
    const queue = todaysAppointments.filter(a => 
        (a.status === AppointmentStatus.SCHEDULED || a.status === AppointmentStatus.CHECKED_IN) &&
        a.startTime.getTime() > now.setHours(0,0,0,0)
    );

    // Completed List (Today Only) for the list view
    const completedListToday = todaysAppointments.filter(a => a.status === AppointmentStatus.COMPLETED).sort((a,b) => (b.actualEndTime?.getTime() || 0) - (a.actualEndTime?.getTime() || 0));

    // --- PERSONAL METRICS CALCULATION (FOR ANALYTICS VIEW) ---
    const personalMetrics = useMemo(() => {
        // Only calculate for the currently selected day (Today)
        const myTodaysAppointments = appointments.filter(a => 
            a.barberId === barberId &&
            a.startTime.getDate() === now.getDate() &&
            a.status !== AppointmentStatus.CANCELLED
        );

        // Capacity is calculated based on ONE barber (myself)
        const totalMinutesAvailable = (closeHour - openHour) * 60; 
        
        let bookedMinutes = 0;
        let completedCount = 0;
        let revenue = 0;

        myTodaysAppointments.forEach(apt => {
            const duration = (apt.expectedEndTime.getTime() - apt.startTime.getTime()) / 60000;
            bookedMinutes += duration;
            revenue += apt.price;
            if (apt.status === AppointmentStatus.COMPLETED) completedCount++;
        });

        const deadTime = Math.max(0, totalMinutesAvailable - bookedMinutes);

        return {
          dailyOccupancy: totalMinutesAvailable > 0 ? Math.round((bookedMinutes / totalMinutesAvailable) * 100) : 0,
          deadTimeMinutes: Math.round(deadTime),
          revenue,
          appointmentsCompleted: completedCount,
          appointmentsTotal: myTodaysAppointments.length
        };
    }, [appointments, now, barberId, openHour, closeHour]);

    // Filter appointments for MetricsPanel (Must include history, but scoped to this barber)
    const myAllAppointments = useMemo(() => {
        return appointments.filter(a => a.barberId === barberId);
    }, [appointments, barberId]);


    // --- LEADERBOARD & XP CALCULATION ---
    const metricsXP = useMemo(() => {
        const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const startOfWeek = new Date(now);
        const day = startOfWeek.getDay() || 7; 
        if(day !== 1) startOfWeek.setHours(-24 * (day - 1));
        startOfWeek.setHours(0,0,0,0);
        const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

        const allCompleted = appointments.filter(a => a.status === AppointmentStatus.COMPLETED);
        const myCompleted = allCompleted.filter(a => a.barberId === barberId);
        const pointsMonth = myCompleted.filter(a => a.startTime >= startOfMonth).length;

        const currentLevel = Math.floor(pointsMonth / 20) + 1;
        const progressToNextLevel = ((pointsMonth % 20) / 20) * 100;

        const leaderboardData = barbers.map(barber => {
            const barberCompleted = allCompleted.filter(a => a.barberId === barber.id);
            let score = 0;
            if (rankingPeriod === 'today') {
                score = barberCompleted.filter(a => a.startTime >= startOfDay).length;
            } else if (rankingPeriod === 'week') {
                score = barberCompleted.filter(a => a.startTime >= startOfWeek).length;
            } else {
                score = barberCompleted.filter(a => a.startTime >= startOfMonth).length;
            }
            return {
                id: barber.id,
                name: barber.name.split(' ')[0],
                points: score,
                isCurrent: barber.id === barberId
            };
        }).sort((a, b) => b.points - a.points);

        return { pointsMonth, currentLevel, progressToNextLevel, leaderboardData };
    }, [appointments, now, barbers, barberId, rankingPeriod]); 

    // --- PROFESSIONAL STOPWATCH LOGIC ---
    const ProfessionalStopwatch = ({ startTime, expectedDuration }: { startTime: Date, expectedDuration: number }) => {
        const elapsedSeconds = Math.max(0, Math.floor((now.getTime() - startTime.getTime()) / 1000));
        const totalSecondsExpected = expectedDuration * 60;
        const progressPercentage = Math.min(100, (elapsedSeconds / totalSecondsExpected) * 100);
        const isOvertime = elapsedSeconds > totalSecondsExpected;

        const formatMainDisplay = (totalSecs: number) => {
            const h = Math.floor(totalSecs / 3600);
            const m = Math.floor((totalSecs % 3600) / 60);
            const s = totalSecs % 60;
            return (
                <div className="flex items-baseline gap-1 font-mono tracking-tighter">
                    <span>{String(m).padStart(2, '0')}</span>
                    <span className="text-4xl text-gray-600 animate-pulse">:</span>
                    <span>{String(s).padStart(2, '0')}</span>
                </div>
            );
        };

        const radius = 120;
        const circumference = 2 * Math.PI * radius;
        const strokeDashoffset = circumference - (progressPercentage / 100) * circumference;

        return (
            <div className="flex flex-col items-center w-full relative z-10 py-6">
                <div className="relative flex items-center justify-center">
                    <div className="relative w-[280px] h-[280px] flex items-center justify-center">
                        <svg className="w-full h-full rotate-[-90deg]">
                            <circle cx="140" cy="140" r={radius} stroke="rgba(255,255,255,0.05)" strokeWidth="6" fill="transparent" />
                             <circle
                                cx="140" cy="140" r={radius}
                                stroke={isOvertime ? '#ef4444' : '#10B981'}
                                strokeWidth="6" fill="transparent"
                                strokeDasharray={circumference}
                                strokeDashoffset={strokeDashoffset}
                                strokeLinecap="round"
                                className="transition-all duration-1000 ease-linear"
                            />
                        </svg>
                        <div className={`absolute w-[200px] h-[200px] rounded-full blur-[80px] opacity-20 transition-colors duration-1000 ${isOvertime ? 'bg-red-600' : 'bg-brand-500'}`}></div>
                        <div className="absolute flex flex-col items-center justify-center">
                            <div className={`font-mono text-6xl font-black tabular-nums flex items-center justify-center transition-colors duration-300 drop-shadow-[0_4px_10px_rgba(0,0,0,0.5)] ${isOvertime ? 'text-red-500' : 'text-white'}`}>
                                {formatMainDisplay(elapsedSeconds)}
                            </div>
                            <div className="text-[9px] uppercase font-bold tracking-[0.3em] mt-2 text-brand-500/80">Tiempo Real</div>
                        </div>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="space-y-6 mb-6 animate-in fade-in duration-500">
            
            {/* --- VIEW MODE SWITCHER --- */}
            <div className="flex justify-center mb-2">
                <div className="bg-dark-900 border border-white/10 p-1 rounded-xl flex gap-1 relative z-20 shadow-lg">
                    <button 
                        onClick={() => setViewMode('OPERATIONAL')}
                        className={`flex items-center gap-2 px-6 py-2 rounded-lg text-sm font-bold transition-all ${viewMode === 'OPERATIONAL' ? 'bg-brand-500 text-black shadow-lg shadow-brand-500/20' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}
                    >
                        <Scissors size={16} /> Operación
                    </button>
                    <button 
                        onClick={() => setViewMode('ANALYTICS')}
                        className={`flex items-center gap-2 px-6 py-2 rounded-lg text-sm font-bold transition-all ${viewMode === 'ANALYTICS' ? 'bg-brand-500 text-black shadow-lg shadow-brand-500/20' : 'text-gray-400 hover:text-white hover:bg-white/5'}`}
                    >
                        <BarChart3 size={16} /> Finanzas & Analytics
                    </button>
                </div>
            </div>

            {/* --- OPERATIONAL VIEW --- */}
            {viewMode === 'OPERATIONAL' && (
                <>
                {/* ROW 1: ACTIVE OPERATION + QUEUE */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-[500px]">
                    
                    {/* LEFT: ACTIVE STATION (7 Cols) */}
                    <div className="lg:col-span-7 flex flex-col">
                        {activeCut ? (
                            <div className="glass-morphism rounded-3xl overflow-hidden relative flex flex-col flex-1 transition-all duration-500 border-brand-500/20 hover:border-brand-500/40 shadow-[0_0_50px_rgba(240,180,41,0.05)]">
                                <div className="p-6 flex justify-between items-center relative z-20 border-b border-white/5 bg-dark-900/30">
                                    <div className="flex items-center gap-3">
                                        <div className="relative flex items-center justify-center w-3 h-3">
                                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-500 opacity-75"></span>
                                            <span className="relative inline-flex rounded-full h-2 w-2 bg-red-600"></span>
                                        </div>
                                        <span className="text-xs font-mono uppercase tracking-[0.2em] text-red-500 font-bold">En Servicio • Live</span>
                                    </div>
                                    <div className="flex items-center gap-2 text-[10px] font-mono text-gray-400 glass-morphism-inner px-3 py-1.5 rounded-full">
                                        <Clock size={10} className="text-brand-500"/> INICIO: {formatTime(activeCut.actualStartTime || activeCut.startTime)}
                                    </div>
                                </div>

                                <div className="flex-1 flex flex-col items-center justify-center relative py-4">
                                    <div className="mb-2 relative z-10 text-center">
                                        <h2 className="text-4xl md:text-5xl font-black text-white tracking-tight drop-shadow-lg mb-2">{activeCut.clientName}</h2>
                                        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-brand-500/30 bg-brand-500/10 backdrop-blur-md">
                                            <Scissors size={14} className="text-brand-500" />
                                            <span className="text-sm font-bold text-white tracking-wide">
                                                {services.find(s => s.id === activeCut.serviceId)?.name || 'Servicio'}
                                            </span>
                                        </div>
                                    </div>

                                    <ProfessionalStopwatch 
                                        startTime={activeCut.actualStartTime || activeCut.startTime} 
                                        expectedDuration={(activeCut.expectedEndTime.getTime() - activeCut.startTime.getTime()) / 60000} 
                                    />

                                    <div className="w-full max-w-sm px-6 pb-2 relative z-20 mt-4">
                                        <button 
                                            onClick={() => onStatusChange(activeCut.id, AppointmentStatus.COMPLETED)}
                                            className="group w-full glass-morphism-inner bg-emerald-500/10 hover:bg-emerald-500/20 border-emerald-500/30 hover:border-emerald-500/60 text-white font-bold py-4 rounded-2xl shadow-lg transition-all flex items-center justify-between px-6 relative overflow-hidden"
                                        >
                                            <div className="flex flex-col items-start">
                                                <span className="text-sm font-black text-white uppercase tracking-wider group-hover:text-emerald-400 transition-colors">Finalizar Corte</span>
                                                <span className="text-[10px] text-emerald-500/70 font-mono">REGISTRAR SALIDA</span>
                                            </div>
                                            <div className="bg-emerald-500 text-black p-3 rounded-xl z-10 group-hover:scale-110 transition-transform shadow-[0_0_20px_rgba(16,185,129,0.4)]">
                                                <CheckSquare size={24} strokeWidth={3} />
                                            </div>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div className="glass-morphism rounded-3xl p-8 flex flex-col items-center justify-center text-center flex-1 relative overflow-hidden group min-h-[450px]">
                                <div className="absolute inset-0 flex items-center justify-center opacity-5 pointer-events-none">
                                    <div className="w-[300px] h-[300px] border border-gray-700/50 rounded-full animate-[spin_30s_linear_infinite]"></div>
                                </div>
                                <div className="w-20 h-20 glass-morphism-inner rounded-full flex items-center justify-center mb-6 border border-white/5 shadow-[0_0_50px_rgba(0,0,0,0.5)] relative z-10 backdrop-blur-md">
                                    <Gauge size={32} className="text-gray-500" />
                                </div>
                                <h2 className="text-2xl font-black text-gray-300 relative z-10 tracking-tight">Estación Disponible</h2>
                                <p className="text-gray-500 max-w-xs mt-2 text-xs font-mono relative z-10 leading-relaxed">
                                    SELECCIONA UN CLIENTE DE LA LISTA DE ESPERA PARA INICIAR EL SERVICIO.
                                </p>
                            </div>
                        )}
                    </div>

                    {/* RIGHT: QUEUE (5 Cols) */}
                    <div className="lg:col-span-5 flex flex-col h-full">
                        <div className="glass-morphism rounded-3xl flex-1 flex flex-col overflow-hidden border-brand-500/10 h-full">
                            <div className="p-5 border-b border-white/10 bg-dark-900/50 backdrop-blur flex justify-between items-center sticky top-0 z-20">
                                <h3 className="text-gray-200 text-sm font-black uppercase tracking-widest flex items-center gap-2">
                                    <User size={16} className="text-brand-500" /> Cola de Espera
                                </h3>
                                <span className="text-[10px] bg-brand-500 text-black font-bold px-2 py-0.5 rounded-full">{queue.length}</span>
                            </div>
                            <div className="flex-1 overflow-y-auto custom-scrollbar p-3 space-y-2 relative">
                                {queue.length === 0 ? (
                                    <div className="h-full flex flex-col items-center justify-center text-gray-600 opacity-50 space-y-2">
                                        <Scissors size={24} />
                                        <p className="text-xs font-mono uppercase">Lista vacía</p>
                                    </div>
                                ) : (
                                    queue.map((apt, idx) => {
                                        const isNext = idx === 0;
                                        const isCheckedIn = apt.status === AppointmentStatus.CHECKED_IN;
                                        return (
                                            <div key={apt.id} className={`group relative overflow-hidden rounded-xl border transition-all duration-300 ${isNext ? 'bg-brand-500/5 border-brand-500/30' : 'bg-dark-900/30 border-white/5 hover:border-white/10'}`}>
                                                <div className="p-4 flex flex-col gap-3">
                                                    <div className="flex justify-between items-start">
                                                        <div className="flex items-center gap-3">
                                                            <span className={`font-mono text-xs font-bold px-1.5 py-0.5 rounded ${isNext ? 'bg-brand-500 text-black' : 'bg-dark-800 text-gray-400'}`}>{formatTime(apt.startTime)}</span>
                                                            <div>
                                                                <div className="text-white font-bold text-sm leading-none">{apt.clientName}</div>
                                                                <div className="text-[10px] text-gray-500 mt-1 truncate max-w-[120px]">{services.find(s => s.id === apt.serviceId)?.name}</div>
                                                            </div>
                                                        </div>
                                                        {isCheckedIn && <span className="flex items-center gap-1 text-[9px] font-bold text-emerald-500 bg-emerald-500/10 px-2 py-1 rounded border border-emerald-500/20"><CheckCircle2 size={10} /> EN SALA</span>}
                                                    </div>
                                                    <div className="flex gap-2 mt-1">
                                                        {activeCut ? (
                                                            <div className="w-full py-2 text-center text-[10px] text-gray-600 font-mono border border-dashed border-gray-800 rounded">ESPERANDO TURNO</div>
                                                        ) : (
                                                            <>
                                                                {!isCheckedIn ? (
                                                                    <button onClick={() => onStatusChange(apt.id, AppointmentStatus.CHECKED_IN)} className="flex-1 bg-dark-800 hover:bg-dark-700 text-gray-300 border border-white/10 rounded-lg py-2 text-xs font-bold flex items-center justify-center gap-2 transition-colors"><LogIn size={14} /> Check In</button>
                                                                ) : (
                                                                    <button onClick={() => onStatusChange(apt.id, AppointmentStatus.IN_PROGRESS)} className="flex-1 bg-brand-500 hover:bg-brand-400 text-black border border-brand-400 rounded-lg py-3 text-xs font-bold flex items-center justify-center gap-2 transition-all shadow-[0_0_15px_rgba(240,180,41,0.2)] animate-pulse-slow"><Play size={14} fill="black" /> COMENZAR CORTE</button>
                                                                )}
                                                                {!isCheckedIn && <button onClick={() => onStatusChange(apt.id, AppointmentStatus.CANCELLED)} className="w-10 bg-dark-800 hover:bg-red-900/20 hover:border-red-500/50 border border-white/10 rounded-lg flex items-center justify-center text-gray-500 hover:text-red-500 transition-colors"><XCircle size={16} /></button>}
                                                            </>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                        </div>
                    </div>
                </div>

                {/* ROW 2: TELEMETRY & COMPLETED LOG (12 Cols) */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    
                    {/* COMPLETED LIST (6 Cols) */}
                    <div className="lg:col-span-6 glass-morphism rounded-3xl overflow-hidden border-brand-500/10 min-h-[300px] flex flex-col">
                        <div className="p-5 border-b border-white/10 bg-dark-900/50 backdrop-blur flex justify-between items-center">
                            <h3 className="text-gray-200 text-sm font-black uppercase tracking-widest flex items-center gap-2">
                                <History size={16} className="text-emerald-500" /> Historial de Producción
                            </h3>
                            <span className="text-[10px] bg-dark-800 text-gray-400 font-bold px-2 py-0.5 rounded-full border border-white/10">HOY</span>
                        </div>
                        
                        <div className="flex-1 overflow-y-auto custom-scrollbar p-0">
                            {completedListToday.length === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-gray-600 opacity-50 py-10">
                                    <Trophy size={32} className="mb-2" />
                                    <p className="text-xs font-mono uppercase">Sin cortes finalizados</p>
                                </div>
                            ) : (
                                <table className="w-full text-left border-collapse">
                                    <thead className="bg-white/5 text-[10px] text-gray-500 uppercase font-mono sticky top-0 backdrop-blur-md z-10">
                                        <tr>
                                            <th className="px-4 py-3 font-medium">Cliente</th>
                                            <th className="px-4 py-3 font-medium text-center">Hora Final</th>
                                            <th className="px-4 py-3 font-medium text-right">Tiempo Total</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-white/5 text-sm">
                                        {completedListToday.map(apt => {
                                            // Precise Calculation
                                            let timeDisplay = "--";
                                            let isFast = true;
                                            
                                            if (apt.actualStartTime && apt.actualEndTime) {
                                                const diffMs = apt.actualEndTime.getTime() - apt.actualStartTime.getTime();
                                                const mins = Math.floor(diffMs / 60000);
                                                const secs = Math.floor((diffMs % 60000) / 1000);
                                                
                                                // Exact formatting requested by user
                                                timeDisplay = mins > 0 ? `${mins}m ${secs}s` : `${secs}s`;
                                                
                                                const service = services.find(s => s.id === apt.serviceId);
                                                const expectedMins = service?.durationMinutes || 30;
                                                isFast = mins <= expectedMins;
                                            }

                                            const service = services.find(s => s.id === apt.serviceId);

                                            return (
                                                <tr key={apt.id} className="hover:bg-white/5 transition-colors group">
                                                    <td className="px-4 py-3">
                                                        <div className="font-bold text-white group-hover:text-brand-400 transition-colors">{apt.clientName}</div>
                                                        <div className="text-[10px] text-gray-500">{service?.name}</div>
                                                    </td>
                                                    <td className="px-4 py-3 text-center font-mono text-xs text-gray-400">
                                                        {apt.actualEndTime ? formatTime(apt.actualEndTime) : '--:--'}
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        <div className={`inline-flex items-center gap-1.5 px-2 py-1 rounded font-mono font-bold text-xs border ${isFast ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' : 'bg-red-500/10 border-red-500/30 text-red-400'}`}>
                                                            {timeDisplay}
                                                            {isFast ? <Zap size={10}/> : <Clock size={10}/>}
                                                        </div>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    </div>

                    {/* CHARTS / TELEMETRY (6 Cols) */}
                    <div className="lg:col-span-6 flex flex-col gap-6">
                        
                        {/* TOP STATS ROW - POINTS SYSTEM UPGRADE */}
                        <div className="grid grid-cols-1 gap-4">
                            {/* GAMIFIED POINTS CARD - Now Full Width */}
                            <div className="glass-morphism p-4 rounded-2xl flex flex-col justify-between relative overflow-hidden group border border-brand-500/20 min-h-[140px]">
                                <div className="absolute right-0 top-0 p-3 opacity-10 group-hover:opacity-30 transition-opacity">
                                    <Trophy size={80} className="text-brand-500" />
                                </div>
                                <div className="flex justify-between items-start relative z-10">
                                    <div className="text-[10px] text-brand-500 uppercase font-bold tracking-wider flex items-center gap-1">
                                        <Award size={12} /> Puntos (XP)
                                    </div>
                                    <div className="text-[9px] bg-brand-500 text-black px-1.5 py-0.5 rounded font-bold">NVL {metricsXP.currentLevel}</div>
                                </div>
                                
                                <div className="flex items-baseline gap-1 mt-1 relative z-10">
                                    <span className="text-5xl font-black text-white font-mono tracking-tighter">{metricsXP.pointsMonth}</span>
                                    <span className="text-sm text-gray-500 font-bold uppercase">PTS (MES)</span>
                                </div>
                                
                                <div className="relative z-10 mt-3">
                                    <div className="flex justify-between text-[10px] text-gray-400 mb-1.5 font-mono uppercase font-bold">
                                        <span>Progreso Nivel {metricsXP.currentLevel + 1}</span>
                                        <span>{metricsXP.pointsMonth % 20} / 20</span>
                                    </div>
                                    <div className="w-full bg-dark-900 h-2 rounded-full overflow-hidden border border-white/5">
                                        <div className="bg-gradient-to-r from-brand-600 to-brand-400 h-full transition-all duration-1000 shadow-[0_0_10px_rgba(240,180,41,0.5)]" style={{width: `${metricsXP.progressToNextLevel}%`}}></div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* COMPETITIVE LEADERBOARD CHART */}
                        <div className="glass-morphism p-5 rounded-3xl flex-1 border-brand-500/10 relative">
                            <div className="flex justify-between items-center mb-4">
                                <h3 className="text-gray-400 text-xs font-bold uppercase tracking-widest flex items-center gap-2">
                                    <Crown size={14} className="text-brand-500" /> Tabla de Líderes
                                </h3>
                                
                                {/* Time Frame Selector */}
                                <div className="flex bg-dark-900 rounded-lg p-0.5 border border-white/10">
                                    {['today', 'week', 'month'].map((p) => (
                                        <button 
                                            key={p}
                                            onClick={() => setRankingPeriod(p as any)} 
                                            className={`px-2 py-0.5 text-[9px] font-bold rounded uppercase transition-all ${rankingPeriod === p ? 'bg-brand-500 text-black shadow-sm' : 'text-gray-500 hover:text-white'}`}
                                        >
                                            {p === 'today' ? 'Hoy' : p === 'week' ? 'Sem' : 'Mes'}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            <div className="h-40 w-full">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <BarChart data={metricsXP.leaderboardData}>
                                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(255,255,255,0.05)" />
                                            <XAxis 
                                                dataKey="name" 
                                                axisLine={false} 
                                                tickLine={false} 
                                                tick={{fill: '#9CA3AF', fontSize: 10, fontWeight: 700}} 
                                                dy={10}
                                            />
                                            <Tooltip 
                                                cursor={{fill: 'rgba(255,255,255,0.05)'}} 
                                                contentStyle={{ backgroundColor: '#1E1E1E', borderColor: '#333', color: '#fff', fontSize: '12px' }} 
                                                itemStyle={{ color: '#fff', fontWeight: 'bold' }}
                                                formatter={(value: number) => [`${value} Puntos`, 'Total']}
                                            />
                                            <Bar dataKey="points" radius={[4, 4, 0, 0]} barSize={40}>
                                                {metricsXP.leaderboardData.map((entry, index) => (
                                                    <Cell 
                                                        key={`cell-${index}`} 
                                                        fill={entry.isCurrent ? '#f0b429' : index === 0 ? '#8b5cf6' : '#4b5563'} 
                                                        stroke={entry.isCurrent ? 'rgba(255,255,255,0.5)' : 'none'}
                                                        strokeWidth={entry.isCurrent ? 2 : 0}
                                                    />
                                                ))}
                                            </Bar>
                                        </BarChart>
                                    </ResponsiveContainer>
                            </div>
                        </div>
                    </div>

                </div>
                </>
            )}

            {/* --- ANALYTICS VIEW (INTEGRATED METRICS PANEL) --- */}
            {viewMode === 'ANALYTICS' && (
                <div className="animate-in fade-in zoom-in-95 duration-500">
                    <MetricsPanel 
                        metrics={personalMetrics} 
                        appointments={myAllAppointments} // Pass filtered appointments (history of THIS barber only)
                        currentDate={now} 
                        services={services} 
                        openHour={openHour}
                        closeHour={closeHour}
                    />
                </div>
            )}
        </div>
    );
};
