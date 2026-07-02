import React from 'react';
import { Users } from 'lucide-react';

export default function WorkshopCRM() {
  return <SimpleModule title="Taller CRM" eyebrow="Clientes y seguimiento" icon={<Users size={22} />} accent="text-blue-300" />;
}

function SimpleModule({ title, eyebrow, icon, accent }: any) {
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
        {['Prospectos', 'Ordenes abiertas', 'Retenciones'].map((label, index) => (
          <div key={label} className="rounded-lg border border-white/10 bg-white/[0.04] p-4">
            <div className="text-2xl font-black text-white">{[18, 7, '92%'][index]}</div>
            <div className="mt-1 text-xs font-bold uppercase text-slate-400">{label}</div>
          </div>
        ))}
      </div>
    </section>
  );
}
