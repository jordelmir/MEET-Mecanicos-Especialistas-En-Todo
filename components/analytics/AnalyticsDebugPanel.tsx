import React, { useEffect, useState } from 'react';
import { RefreshCcw, Send, ShieldCheck } from 'lucide-react';
import { analytics } from '../../src/analytics/analyticsClient';
import { AnalyticsConsentManager } from '../../src/analytics/analyticsConsent';
import type { AnalyticsConsentState, AnalyticsDebugSnapshot } from '../../src/analytics/analyticsTypes';

const consentOptions: Array<{ value: AnalyticsConsentState; label: string; description: string }> = [
  { value: 'enabled', label: 'Completo', description: 'Uso, retención, embudos, errores y monetización.' },
  { value: 'essential_only', label: 'Esencial', description: 'Solo apertura, sesión y errores críticos.' },
  { value: 'disabled', label: 'Desactivado', description: 'No envía analytics no esenciales.' },
];

export function AnalyticsDebugPanel() {
  const [snapshot, setSnapshot] = useState<AnalyticsDebugSnapshot | null>(null);
  const [isFlushing, setIsFlushing] = useState(false);

  const refresh = async () => {
    setSnapshot(await analytics.debugSnapshot());
  };

  useEffect(() => {
    void refresh();
    const interval = window.setInterval(() => void refresh(), 5_000);
    return () => window.clearInterval(interval);
  }, []);

  const setConsent = (consent: AnalyticsConsentState) => {
    AnalyticsConsentManager.setConsent(consent);
    void refresh();
  };

  const flushNow = async () => {
    setIsFlushing(true);
    try {
      await analytics.flush();
    } finally {
      setIsFlushing(false);
      await refresh();
    }
  };

  if (!snapshot) {
    return (
      <div className="min-h-screen bg-steel-950 text-white p-8 font-mono">
        Cargando analytics...
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#05070d] text-white p-6">
      <div className="max-w-6xl mx-auto space-y-5">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <p className="font-mono text-xs text-forge-500 uppercase tracking-[0.3em]">MEET Internal</p>
            <h1 className="text-2xl font-black">Analytics Debug</h1>
          </div>
          <div className="flex gap-2">
            <button onClick={refresh} className="glass-inner px-3 py-2 rounded-lg flex items-center gap-2 text-sm">
              <RefreshCcw size={16} />
              Actualizar
            </button>
            <button onClick={flushNow} className="bg-forge-500 text-black px-3 py-2 rounded-lg flex items-center gap-2 text-sm font-bold">
              <Send size={16} />
              {isFlushing ? 'Sincronizando...' : 'Flush now'}
            </button>
          </div>
        </div>

        <div className="grid md:grid-cols-3 gap-3">
          <InfoTile label="anonymous_id" value={snapshot.anonymousId} />
          <InfoTile label="session_id" value={snapshot.sessionId} />
          <InfoTile label="eventos pendientes" value={String(snapshot.pendingEvents)} />
        </div>

        <section className="glass rounded-xl p-4">
          <h2 className="font-bold mb-3 flex items-center gap-2">
            <ShieldCheck size={18} className="text-forge-500" />
            Privacidad
          </h2>
          <div className="grid md:grid-cols-3 gap-3">
            {consentOptions.map(option => (
              <button
                key={option.value}
                onClick={() => setConsent(option.value)}
                className={`text-left p-3 rounded-lg border transition-all ${
                  snapshot.consent === option.value
                    ? 'border-forge-500 bg-forge-500/10'
                    : 'border-white/10 bg-white/5 hover:border-white/25'
                }`}
              >
                <div className="font-bold">{option.label}</div>
                <div className="text-xs text-steel-300 mt-1">{option.description}</div>
              </button>
            ))}
          </div>
        </section>

        <section className="grid lg:grid-cols-2 gap-4">
          <DebugList
            title="Últimos 100 eventos"
            rows={snapshot.recentEvents.map(event => `${event.timestamp} ${event.event_name} ${JSON.stringify(event.properties)}`)}
          />
          <DebugList title="Errores recientes" rows={snapshot.recentErrors} />
        </section>

        <p className="font-mono text-xs text-steel-400">Último sync: {snapshot.lastFlushAt ?? 'sin flush exitoso todavía'}</p>
      </div>
    </div>
  );
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="glass rounded-xl p-4">
      <div className="font-mono text-[10px] uppercase tracking-widest text-steel-300">{label}</div>
      <div className="mt-2 text-sm break-all text-forge-400">{value}</div>
    </div>
  );
}

function DebugList({ title, rows }: { title: string; rows: string[] }) {
  return (
    <div className="glass rounded-xl p-4 min-h-[280px]">
      <h2 className="font-bold mb-3">{title}</h2>
      <div className="space-y-2 max-h-[420px] overflow-auto pr-2">
        {rows.length === 0 ? (
          <p className="text-steel-400 text-sm">Sin datos todavía.</p>
        ) : rows.map((row, index) => (
          <pre key={`${index}-${row.slice(0, 12)}`} className="text-[11px] whitespace-pre-wrap rounded-lg bg-black/30 p-2 text-steel-200">
            {row}
          </pre>
        ))}
      </div>
    </div>
  );
}

