import React from 'react';
import { Activity, BookOpen, Car, ClipboardList, Gauge, Radio, Users, Wrench } from 'lucide-react';

type PlatformCommandCenterProps = {
  role?: string;
  adminViewMode?: string;
  metrics?: any;
  workOrders?: unknown[];
  clients?: unknown[];
  mechanics?: unknown[];
  services?: unknown[];
  currentDate?: Date;
  onNewOrder?: () => void;
  onOpenOBD2?: () => void;
  onOpenLiveLink?: () => void;
  onOpenCatalog?: () => void;
  onOpenClients?: () => void;
  onSetVanguardTab?: (tab: string) => void;
};

export function PlatformCommandCenter({
  role,
  adminViewMode,
  metrics,
  workOrders = [],
  clients = [],
  mechanics = [],
  services = [],
  currentDate,
  onNewOrder,
  onOpenOBD2,
  onOpenLiveLink,
  onOpenCatalog,
  onOpenClients,
  onSetVanguardTab,
}: PlatformCommandCenterProps) {
  const metricItems = [
    ['Ordenes', workOrders.length],
    ['Clientes', clients.length],
    ['Mecanicos', mechanics.length],
    ['Servicios', services.length],
  ];

  return (
    <section className="mb-6 grid gap-4 xl:grid-cols-[1.35fr_0.65fr]">
      <div className="glass rounded-2xl border border-cyan-400/20 p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-cyan-300">
              Vanguard Command Center
            </p>
            <h2 className="mt-2 text-2xl font-black text-white">Operacion del taller en vivo</h2>
            <p className="mt-2 text-sm text-slate-400">
              {role || 'ADMIN'} · {adminViewMode || 'DASHBOARD'} · {currentDate?.toLocaleDateString?.() || 'hoy'}
            </p>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {metricItems.map(([label, value]) => (
              <div key={label} className="rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2 text-center">
                <div className="text-xl font-black text-cyan-200">{String(value)}</div>
                <div className="mt-1 font-mono text-[10px] uppercase text-slate-500">{label}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="mt-5 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
          <CommandButton icon={<ClipboardList size={17} />} label="Nueva orden" onClick={onNewOrder} />
          <CommandButton icon={<Gauge size={17} />} label="Scanner OBD2" onClick={onOpenOBD2} />
          <CommandButton icon={<Radio size={17} />} label="LiveLink" onClick={onOpenLiveLink} />
          <CommandButton icon={<BookOpen size={17} />} label="Catalogo" onClick={onOpenCatalog} />
        </div>
      </div>

      <div className="glass rounded-2xl border border-emerald-400/20 p-5">
        <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-emerald-300">
          Ecosistema
        </p>
        <div className="mt-4 grid gap-2">
          <CommandButton icon={<Users size={17} />} label="Clientes" onClick={onOpenClients} />
          <CommandButton icon={<Car size={17} />} label="Fleet" onClick={() => onSetVanguardTab?.('FLEET')} />
          <CommandButton icon={<Wrench size={17} />} label="B2B Verified" onClick={() => onSetVanguardTab?.('VERIFIED')} />
          <CommandButton icon={<Activity size={17} />} label={`KPIs: ${Object.keys(metrics || {}).length}`} />
        </div>
      </div>
    </section>
  );
}

function CommandButton({ icon, label, onClick }: { icon: React.ReactNode; label: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[44px] items-center justify-center gap-2 rounded-lg border border-cyan-300/20 bg-cyan-300/10 px-3 py-2 text-xs font-black uppercase tracking-[0.14em] text-cyan-100 transition hover:border-cyan-200/60 hover:bg-cyan-300/20"
    >
      {icon}
      {label}
    </button>
  );
}
