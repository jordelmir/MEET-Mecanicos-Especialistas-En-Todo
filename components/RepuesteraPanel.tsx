/**
 * Repuestera Panel
 *
 * The repuestera-side dashboard. Six tabs per the spec:
 *
 *   - Solicitudes        : list of OPEN PartRequests visible to the repuestera.
 *   - Mis cotizaciones   : quotes the repuestera has already submitted, with
 *                          status and the create / accept cycle.
 *   - Inventario         : (placeholder) optional local inventory. Wired in
 *                          a later PR — for now, the tab is a no-op.
 *   - Ventas             : quotes ACCEPTED, with totals and the
 *                          historial-del-vehículo summary.
 *   - Reputación         : rating + reviews + dispute rate.
 *   - Configuración      : the repuestera's profile (name, hours, brands).
 *
 * The panel is data-only. All write actions fire callbacks to the parent.
 * This keeps the panel unit-testable in isolation and avoids coupling
 * it to Supabase.
 */

import React, { useMemo, useState } from 'react';
import {
  X,
  Wrench,
  Package,
  DollarSign,
  Star,
  Settings,
  Inbox,
  Send,
} from 'lucide-react';

import {
  evaluateCompatibility,
  CompatibilityContext,
  PartPosition,
  CompatibilityConfidence,
  VehicleFingerprint,
} from '../lib/parts';

import { SupplierQuoteForm } from './SupplierQuoteForm';
import { SalesHistoryBadge } from './SalesHistoryBadge';

type Tab =
  | 'solicitudes'
  | 'cotizaciones'
  | 'inventario'
  | 'ventas'
  | 'reputacion'
  | 'configuracion';

const TABS: { key: Tab; label: string; icon: React.ReactNode }[] = [
  { key: 'solicitudes', label: 'Solicitudes', icon: <Inbox size={14} /> },
  { key: 'cotizaciones', label: 'Mis cotizaciones', icon: <Send size={14} /> },
  { key: 'inventario', label: 'Inventario', icon: <Package size={14} /> },
  { key: 'ventas', label: 'Ventas', icon: <DollarSign size={14} /> },
  { key: 'reputacion', label: 'Reputación', icon: <Star size={14} /> },
  { key: 'configuracion', label: 'Configuración', icon: <Settings size={14} /> },
];

interface RepuesteraPanelProps {
  profile: SupplierProfile;
  openRequests: PartRequestSummary[];
  myQuotes: SupplierQuoteSummary[];
  onCancel: () => void;
  onSubmitQuote: (payload: {
    requestId: string;
    quote: ReturnType<typeof import('../lib/parts').buildQuoteFromForm>;
  }) => void;
}

export function RepuesteraPanel({
  profile,
  openRequests,
  myQuotes,
  onCancel,
  onSubmitQuote,
}: RepuesteraPanelProps) {
  const [tab, setTab] = useState<Tab>('solicitudes');
  const [quoting, setQuoting] = useState<PartRequestSummary | null>(null);

  const acceptedQuotes = useMemo(
    () => myQuotes.filter((q) => q.status === 'ACCEPTED'),
    [myQuotes],
  );

  // Sort accepted quotes by composite value (price * warranty / ETA). This
  // is intentionally a local re-rank so the panel is independent of the
  // PR-2 ranking engine and can ship in PR-3.
  const acceptedRanked = useMemo(
    () =>
      [...acceptedQuotes].sort(
        (a, b) => acceptedScore(b, profile.ratingAvg) - acceptedScore(a, profile.ratingAvg),
      ),
    [acceptedQuotes, profile.ratingAvg],
  );

  if (quoting) {
    return (
      <SupplierQuoteForm
        requestId={quoting.id}
        partName={quoting.partName}
        onCancel={() => setQuoting(null)}
        onSubmit={(quote) => {
          onSubmitQuote({ requestId: quoting.id, quote });
          setQuoting(null);
        }}
      />
    );
  }

  return (
    <div className="p-5 max-w-3xl" data-testid="repuestera-panel">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Wrench size={20} className="text-forge-500" />
            Panel de Repuestera
          </h2>
          <p className="font-mono text-[10px] text-steel-300 mt-1">
            {profile.businessName} · {profile.canton}, {profile.province}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <SalesHistoryBadge
            ratingAvg={profile.ratingAvg}
            totalSales={profile.totalSales}
            claimRate={profile.claimRate}
          />
          <button
            onClick={onCancel}
            className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all border border-transparent hover:border-white/10"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-1 mb-5 overflow-x-auto pb-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[10px] font-mono font-bold whitespace-nowrap transition-all ${
              tab === t.key
                ? 'bg-forge-500 text-black'
                : 'text-steel-300 glass-inner hover:text-white'
            }`}
            data-testid={`repuestera-tab-${t.key}`}
          >
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      <div className="min-h-[300px]">
        {tab === 'solicitudes' && (
          <SolicitudesTab
            requests={openRequests}
            onQuote={(r) => setQuoting(r)}
          />
        )}

        {tab === 'cotizaciones' && (
          <CotizacionesTab quotes={myQuotes} />
        )}

        {tab === 'inventario' && (
          <EmptyState
            title="Inventario local"
            hint="El inventario local se activa en una PR posterior. Por ahora la cotización es manual y rápida."
          />
        )}

        {tab === 'ventas' && (
          <VentasTab ranked={acceptedRanked} accepted={acceptedQuotes} />
        )}

        {tab === 'reputacion' && <ReputacionTab profile={profile} />}

        {tab === 'configuracion' && <ConfiguracionTab profile={profile} />}
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                 Sub-tabs                                   */
/* -------------------------------------------------------------------------- */

function SolicitudesTab({
  requests,
  onQuote,
}: {
  requests: PartRequestSummary[];
  onQuote: (r: PartRequestSummary) => void;
}) {
  if (requests.length === 0) {
    return <EmptyState title="Sin solicitudes" hint="Vuelve más tarde." />;
  }
  return (
    <div className="space-y-2" data-testid="solicitudes-list">
      {requests.map((r) => {
        // Defense in depth: the same compatibility engine runs client-side
        // to surface warnings the repuestera should see before quoting.
        const verdict = evaluateCompatibility(
          rToCompatibilityContext(r),
        );
        const hasBlock = verdict.warnings.some((w) => w.severity === 'BLOCK');
        return (
          <div
            key={r.id}
            className={`rounded-lg border bg-steel-800/40 p-3 ${
              hasBlock
                ? 'border-red-500/40'
                : 'border-steel-500/30'
            }`}
          >
            <div className="flex items-start justify-between gap-2 mb-2">
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  {r.partName}
                  {hasBlock && (
                    <span className="text-[10px] font-mono text-red-300 px-1.5 py-0.5 rounded border border-red-500/30 bg-red-500/10">
                      ALERTA
                    </span>
                  )}
                </div>
                <div className="font-mono text-[10px] text-steel-300 mt-0.5">
                  {r.category} · {r.position}
                </div>
                {r.dtcCodes.length > 0 && (
                  <div className="font-mono text-[10px] text-amber-300 mt-1">
                    DTCs: {r.dtcCodes.join(', ')}
                  </div>
                )}
              </div>
              <button
                onClick={() => onQuote(r)}
                className="px-3 py-1.5 rounded-lg text-[10px] font-mono font-bold bg-forge-500 text-black hover:bg-forge-600 transition-all"
                data-testid={`solicitud-cotizar-${r.id}`}
              >
                Cotizar
              </button>
            </div>
            {verdict.warnings.length > 0 && (
              <div className="text-[10px] text-amber-200 mt-1 leading-snug space-y-0.5">
                {verdict.warnings.slice(0, 2).map((w, i) => (
                  <div key={i} className="flex items-start gap-1">
                    <span>⚠</span>
                    <span>{w.message}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function CotizacionesTab({ quotes }: { quotes: SupplierQuoteSummary[] }) {
  if (quotes.length === 0) {
    return (
      <EmptyState
        title="Sin cotizaciones enviadas"
        hint="Crea tu primera desde la pestaña de Solicitudes."
      />
    );
  }
  return (
    <div className="space-y-2">
      {quotes.map((q) => {
        const tone = statusTone(q.status);
        return (
          <div
            key={q.id}
            className="rounded-lg border border-steel-500/30 bg-steel-800/40 p-3"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  {q.partName}
                  <span
                    className={`text-[10px] font-mono px-1.5 py-0.5 rounded border ${tone.bg} ${tone.border} ${tone.text}`}
                  >
                    {q.status}
                  </span>
                </div>
                <div className="font-mono text-[10px] text-steel-300 mt-0.5">
                  {q.brand} · {q.condition} · {q.compatibilityConfidence}
                </div>
              </div>
              <div className="text-right">
                <div className="text-sm font-bold text-white">
                  ${q.price.toFixed(0)} {q.currency}
                </div>
                <div className="font-mono text-[10px] text-steel-300">
                  {q.estimatedDeliveryHours}h
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function VentasTab({
  ranked,
  accepted,
}: {
  ranked: SupplierQuoteSummary[];
  accepted: SupplierQuoteSummary[];
}) {
  if (accepted.length === 0) {
    return (
      <EmptyState
        title="Sin ventas cerradas"
        hint="Las cotizaciones aceptadas aparecerán aquí y se adjuntarán al historial del vehículo del cliente."
      />
    );
  }
  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-steel-500/30 bg-steel-800/30 p-3">
        <div className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
          Ventas cerradas
        </div>
        <div className="text-2xl font-bold text-white mt-1">
          {accepted.length}
        </div>
        <div className="font-mono text-[10px] text-steel-300">
          Total bruto: ${accepted.reduce((acc, q) => acc + q.price, 0).toFixed(0)}
        </div>
      </div>
      <div className="space-y-2">
        {ranked.map((q) => (
          <div
            key={q.id}
            className="rounded-lg border border-steel-500/30 bg-steel-800/40 p-3"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold text-white">{q.partName}</div>
                <div className="font-mono text-[10px] text-steel-300 mt-0.5">
                  {q.brand} · {q.condition} · {q.compatibilityConfidence}
                </div>
              </div>
              <div className="text-right">
                <div className="text-sm font-bold text-white">
                  ${q.price.toFixed(0)} {q.currency}
                </div>
                <div className="font-mono text-[10px] text-steel-300">
                  {q.estimatedDeliveryHours}h
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Local composite score for the repuestera's accepted-sales ranking.
 * Independent of the PR-2 ranking engine so this PR can ship in parallel.
 */
function acceptedScore(
  q: SupplierQuoteSummary,
  ratingAvg: number,
): number {
  const warranty = Math.max(0, Math.min(1, q.warrantyDays / 180));
  const eta = Math.max(0, 1 - q.estimatedDeliveryHours / 168);
  const reputation = Math.max(0, Math.min(1, ratingAvg / 5));
  return warranty * 0.4 + eta * 0.3 + reputation * 0.3;
}

function ReputacionTab({ profile }: { profile: SupplierProfile }) {
  return (
    <div className="space-y-2">
      <Stat label="Rating" value={`${profile.ratingAvg.toFixed(2)} ★`} />
      <Stat label="Ventas totales" value={`${profile.totalSales}`} />
      <Stat label="Tasa de disputa" value={`${(profile.claimRate * 100).toFixed(1)}%`} />
      <Stat
        label="Verificación"
        value={profile.verificationStatus ?? 'UNVERIFIED'}
        tone={profile.verificationStatus === 'ELITE_SUPPLIER' ? 'good' : 'neutral'}
      />
    </div>
  );
}

function ConfiguracionTab({ profile }: { profile: SupplierProfile }) {
  return (
    <div className="space-y-2">
      <Stat label="Negocio" value={profile.businessName} />
      <Stat label="Razón social" value={profile.legalName ?? '—'} />
      <Stat label="Provincia" value={profile.province ?? '—'} />
      <Stat label="Cantón" value={profile.canton ?? '—'} />
      <Stat label="Dirección" value={profile.address} />
      <Stat label="Teléfono" value={profile.phone} />
      <Stat label="WhatsApp" value={profile.whatsapp || profile.phone} />
      <Stat label="Email" value={profile.email ?? '—'} />
      <Stat
        label="Delivery / Pickup"
        value={`${profile.deliveryEnabled ? 'Sí' : 'No'} / ${profile.pickupEnabled ? 'Sí' : 'No'}`}
      />
      <Stat label="Radio (km)" value={`${profile.serviceRadiusKm.toFixed(0)}`} />
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*                                Atoms                                       */
/* -------------------------------------------------------------------------- */

function EmptyState({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="rounded-lg border border-steel-500/30 bg-steel-800/30 p-6 text-center">
      <p className="text-sm font-bold text-white mb-1">{title}</p>
      <p className="text-xs text-steel-300">{hint}</p>
    </div>
  );
}

function Stat({
  label,
  value,
  tone = 'neutral',
}: {
  label: string;
  value: string;
  tone?: 'neutral' | 'good';
}) {
  const valueClass =
    tone === 'good' ? 'text-emerald-300' : 'text-white';
  return (
    <div className="flex items-center justify-between rounded-lg border border-steel-500/30 bg-steel-800/30 px-3 py-2">
      <span className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
        {label}
      </span>
      <span className={`text-xs font-bold ${valueClass}`}>{value}</span>
    </div>
  );
}

function statusTone(status: string): {
  bg: string;
  border: string;
  text: string;
} {
  switch (status) {
    case 'ACCEPTED':
      return {
        bg: 'bg-emerald-500/10',
        border: 'border-emerald-500/30',
        text: 'text-emerald-300',
      };
    case 'REJECTED':
    case 'EXPIRED':
    case 'CANCELLED':
      return {
        bg: 'bg-red-500/10',
        border: 'border-red-500/30',
        text: 'text-red-300',
      };
    case 'SENT':
    default:
      return {
        bg: 'bg-cyan-500/10',
        border: 'border-cyan-500/30',
        text: 'text-cyan-300',
      };
  }
}

/* -------------------------------------------------------------------------- */
/*                          Compatibility helper                              */
/* -------------------------------------------------------------------------- */

function rToCompatibilityContext(r: PartRequestSummary): CompatibilityContext {
  return {
    vehicle: r.vehicle ?? {},
    partName: r.partName,
    category: r.category,
    position: r.position,
    dtcCodes: r.dtcCodes,
    photoUrls: r.photoUrls,
  };
}

/* -------------------------------------------------------------------------- */
/*                         Local summary types                                */
/* -------------------------------------------------------------------------- */

export interface PartRequestSummary {
  id: string;
  partName: string;
  category: string;
  position: PartPosition;
  dtcCodes: string[];
  photoUrls: string[];
  vehicle?: VehicleFingerprint;
}

export interface SupplierQuoteSummary {
  id: string;
  requestId: string;
  partName: string;
  brand: string;
  condition: string;
  compatibilityConfidence: CompatibilityConfidence;
  price: number;
  currency: string;
  estimatedDeliveryHours: number;
  warrantyDays: number;
  status: 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';
}

export interface SupplierProfile {
  businessName: string;
  legalName?: string;
  phone: string;
  whatsapp?: string;
  email?: string;
  province?: string;
  canton?: string;
  address: string;
  deliveryEnabled: boolean;
  pickupEnabled: boolean;
  serviceRadiusKm: number;
  ratingAvg: number;
  totalSales: number;
  claimRate: number;
  verificationStatus?:
    | 'UNVERIFIED'
    | 'PHONE_VERIFIED'
    | 'BUSINESS_VERIFIED'
    | 'INVENTORY_VERIFIED'
    | 'ELITE_SUPPLIER'
    | 'SUSPENDED';
}
