import React from 'react';
import { ClipboardList } from 'lucide-react';

export default function PayoutsView() {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className="rounded-lg border border-emerald-300/20 bg-emerald-300/10 p-3 text-emerald-300"><ClipboardList size={22} /></div>
        <div>
          <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-emerald-300">Ledger</p>
          <h2 className="text-2xl font-black text-white">Pagos, comisiones y liquidaciones</h2>
        </div>
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-3">
        {['Comisiones pendientes', 'Liquidado este mes', 'Disputas'].map((label, index) => (
          <div key={label} className="rounded-lg border border-white/10 bg-white/[0.04] p-4">
            <div className="text-2xl font-black text-white">{['$240', '$3,820', '0'][index]}</div>
            <div className="mt-1 text-xs font-bold uppercase text-slate-400">{label}</div>
          </div>
        ))}
      </div>
    </section>
  );
}
