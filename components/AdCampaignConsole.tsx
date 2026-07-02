import React from 'react';
import { Radio } from 'lucide-react';

export default function AdCampaignConsole() {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className="rounded-lg border border-violet-300/20 bg-violet-300/10 p-3 text-violet-300"><Radio size={22} /></div>
        <div>
          <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-violet-300">Campaign Console</p>
          <h2 className="text-2xl font-black text-white">Anuncios y promociones del ecosistema</h2>
        </div>
      </div>
      <div className="mt-5 rounded-lg border border-white/10 bg-white/[0.04] p-4 text-sm text-slate-300">
        Segmenta ofertas por ciudad, tipo de vehiculo, DTC frecuente, taller verificado y disponibilidad de repuestos.
      </div>
    </section>
  );
}
