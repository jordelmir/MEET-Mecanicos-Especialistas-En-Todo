import React from 'react';
import { FileText } from 'lucide-react';

export default function GDPRComplianceView() {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className="rounded-lg border border-rose-300/20 bg-rose-300/10 p-3 text-rose-300"><FileText size={22} /></div>
        <div>
          <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-rose-300">Privacidad</p>
          <h2 className="text-2xl font-black text-white">Consentimiento, datos y auditoria</h2>
        </div>
      </div>
      <div className="mt-5 rounded-lg border border-white/10 bg-white/[0.04] p-4 text-sm text-slate-300">
        Panel para revisar consentimiento, exportacion de datos, retencion, telemetria y eventos anonimizados.
      </div>
    </section>
  );
}
