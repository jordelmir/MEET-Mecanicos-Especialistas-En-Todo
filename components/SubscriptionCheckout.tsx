import React from 'react';
import { Gauge } from 'lucide-react';

export default function SubscriptionCheckout() {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className="rounded-lg border border-orange-300/20 bg-orange-300/10 p-3 text-orange-300"><Gauge size={22} /></div>
        <div>
          <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-orange-300">Planes</p>
          <h2 className="text-2xl font-black text-white">Suscripciones profesionales</h2>
        </div>
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-3">
        {['Starter', 'Workshop Pro', 'Fleet Elite'].map((plan, index) => (
          <div key={plan} className="rounded-lg border border-orange-300/20 bg-orange-300/10 p-4">
            <div className="text-lg font-black text-white">{plan}</div>
            <div className="mt-2 text-sm text-slate-300">{['Diagnostico base', 'IA + reportes + CRM', 'Flotas + API + SLA'][index]}</div>
          </div>
        ))}
      </div>
    </section>
  );
}
