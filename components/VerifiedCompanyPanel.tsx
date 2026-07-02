import React from 'react';
import { ShieldCheck } from 'lucide-react';

export default function VerifiedCompanyPanel() {
  return (
    <section>
      <div className="flex items-center gap-3">
        <div className="rounded-lg border border-amber-300/20 bg-amber-300/10 p-3 text-amber-300"><ShieldCheck size={22} /></div>
        <div>
          <p className="font-mono text-[11px] font-bold uppercase tracking-[0.22em] text-amber-300">Verified Network</p>
          <h2 className="text-2xl font-black text-white">Talleres, marcas y proveedores verificados</h2>
        </div>
      </div>
      <div className="mt-5 grid gap-3 md:grid-cols-3">
        {['KYC documental', 'Licencia comercial', 'Reputacion operativa'].map(item => (
          <div key={item} className="rounded-lg border border-amber-300/20 bg-amber-300/10 p-4 text-sm font-bold text-amber-100">
            {item}
          </div>
        ))}
      </div>
    </section>
  );
}
