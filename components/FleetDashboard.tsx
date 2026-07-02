import React from 'react';
import { Car, MapPin, ShieldCheck } from 'lucide-react';

export default function FleetDashboard() {
  return (
    <EcosystemPanel
      title="Vanguard Fleet"
      eyebrow="Flota conectada"
      icon={<Car size={22} />}
      accent="text-emerald-300"
      items={[
        ['Unidades activas', '12'],
        ['Alertas criticas', '2'],
        ['Rutas en monitoreo', '5'],
      ]}
      notes={[
        'Vista ejecutiva para controlar salud, ubicacion y evidencia de flota.',
        'Lista para conectar telemetria real del APK y eventos Supabase.',
      ]}
    />
  );
}

function EcosystemPanel({ title, eyebrow, icon, accent, items, notes }: any) {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className={`rounded-lg border border-white/10 bg-white/5 p-3 ${accent}`}>{icon}</div>
        <div>
          <p className={`font-mono text-[11px] font-bold uppercase tracking-[0.22em] ${accent}`}>{eyebrow}</p>
          <h2 className="text-2xl font-black text-white">{title}</h2>
        </div>
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-3">
        {items.map(([label, value]: [string, string]) => (
          <div key={label} className="rounded-lg border border-white/10 bg-white/[0.04] p-4">
            <div className="text-2xl font-black text-white">{value}</div>
            <div className="mt-1 text-xs font-bold uppercase text-slate-400">{label}</div>
          </div>
        ))}
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-2">
        {notes.map((note: string, index: number) => (
          <div key={note} className="flex gap-3 rounded-lg border border-white/10 bg-black/20 p-4 text-sm text-slate-300">
            {index === 0 ? <MapPin className="mt-0.5 text-cyan-300" size={18} /> : <ShieldCheck className="mt-0.5 text-emerald-300" size={18} />}
            <span>{note}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
