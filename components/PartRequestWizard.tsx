/**
 * Part Request Wizard (4 steps per Jor's spec)
 *
 *   1. Identificar pieza   — name, category, OEM optional, source.
 *   2. Compatibilidad      — vehicle, year/engine/trans, VIN optional,
 *                             preference, fires evaluateCompatibility().
 *   3. Entrega             — pickup/delivery, urgency, address.
 *   4. Publicar            — summary + warnings + "send to repuesteras".
 *
 * The wizard is data-only: it emits a finalized `DraftPartRequest` payload
 * plus a `compatibility` result to the parent via onPublish. The parent
 * decides what to do with it (push to Supabase, navigate, etc.).
 *
 * The wizard DOES NOT touch the App.tsx router. It is a standalone dialog-
 * shaped panel that any view in the app can drop in (e.g. ClientDashboard
 * or a future dedicated "Repuestos" page).
 *
 * Smart defaults from the SuggestionEngine:
 *   - When source = FROM_DTC or FROM_3D_COMPONENT, the wizard pre-fills
 *     the most appropriate suggested part name + position + category,
 *     and shows a small dropdown of alternates ranked by priority.
 */

import React, { useMemo, useState } from 'react';
import {
  X,
  ChevronRight,
  ChevronLeft,
  Wrench,
  Truck,
  Send,
  AlertTriangle,
} from 'lucide-react';

import {
  CompatibilityContext,
  evaluateCompatibility,
  PartPosition,
  PartPreference,
  PartSourceContext,
  PartSuggestion,
  suggestParts,
  VehicleFingerprint,
} from '../lib/parts';

import { CompatibilityPanel } from './CompatibilityPanel';

export interface DraftPartRequest {
  partName: string;
  category: string;
  position: PartPosition;
  preference: PartPreference;
  oemNumber?: string;
  photoUrls: string[];
  notes: string;
  sourceContext: PartSourceContext;
  dtcCodes: string[];
  vehicle: VehicleFingerprint;
  deliveryMode: 'PICKUP' | 'DELIVERY';
  deliveryAddress?: string;
  urgency: 'NORMAL' | 'HIGH' | 'CRITICAL';
}

interface PartRequestWizardProps {
  initialSourceContext?: PartSourceContext;
  initialDtcCodes?: string[];
  initialComponentSlug?: string;
  initialWorkOrderHint?: string;
  initialVehicle?: VehicleFingerprint;
  onCancel: () => void;
  onPublish: (payload: DraftPartRequest) => void;
}

type Step = 'identify' | 'compatibility' | 'delivery' | 'publish';

const STEP_ORDER: { key: Step; label: string }[] = [
  { key: 'identify', label: 'Pieza' },
  { key: 'compatibility', label: 'Compatibilidad' },
  { key: 'delivery', label: 'Entrega' },
  { key: 'publish', label: 'Publicar' },
];

export function PartRequestWizard({
  initialSourceContext = 'MANUAL',
  initialDtcCodes = [],
  initialComponentSlug,
  initialWorkOrderHint,
  initialVehicle,
  onCancel,
  onPublish,
}: PartRequestWizardProps) {
  // Pre-fill from suggestion engine.
  const suggestions = useMemo<PartSuggestion[]>(
    () =>
      suggestParts({
        source: suggestionSourceFor(initialSourceContext),
        dtcCodes: initialDtcCodes,
        componentSlug: initialComponentSlug,
        workOrderHint: initialWorkOrderHint,
      }),
    [initialSourceContext, initialDtcCodes, initialComponentSlug, initialWorkOrderHint],
  );

  const topSuggestion = suggestions[0] ?? null;

  const [step, setStep] = useState<Step>('identify');

  const [partName, setPartName] = useState<string>(topSuggestion?.partName ?? '');
  const [category, setCategory] = useState<string>(topSuggestion?.category ?? '');
  const [position, setPosition] = useState<PartPosition>(
    topSuggestion?.position ?? 'NOT_APPLICABLE',
  );
  const [oemNumber, setOemNumber] = useState<string>('');
  const [photoUrls, setPhotoUrls] = useState<string[]>([]);
  const [notes, setNotes] = useState<string>('');
  const [preference, setPreference] = useState<PartPreference>('ANY');

  const [vehicle, setVehicle] = useState<VehicleFingerprint>(initialVehicle ?? {});

  const [deliveryMode, setDeliveryMode] = useState<'PICKUP' | 'DELIVERY'>('PICKUP');
  const [deliveryAddress, setDeliveryAddress] = useState<string>('');
  const [urgency, setUrgency] = useState<'NORMAL' | 'HIGH' | 'CRITICAL'>('NORMAL');

  const [publishConfirmed, setPublishConfirmed] = useState<boolean>(false);

  // The compatibility verdict is recomputed whenever its inputs change.
  const compatibility = useMemo<CompatibilityContext & { partName: string }>(
    () => ({
      vehicle,
      partName,
      category,
      position,
      dtcCodes: initialDtcCodes,
      photoUrls,
    }),
    [vehicle, partName, category, position, initialDtcCodes, photoUrls],
  );
  const verdict = useMemo(
    () => evaluateCompatibility(compatibility),
    [compatibility],
  );

  const hasBlockingWarning = verdict.warnings.some((w) => w.severity === 'BLOCK');

  const handleSelectSuggestion = (s: PartSuggestion) => {
    setPartName(s.partName);
    setCategory(s.category);
    setPosition(s.position);
    if (s.disclaimer) setNotes(s.disclaimer);
  };

  const goNext = () => {
    if (step === 'identify') setStep('compatibility');
    else if (step === 'compatibility') setStep('delivery');
    else if (step === 'delivery') setStep('publish');
  };
  const goBack = () => {
    if (step === 'compatibility') setStep('identify');
    else if (step === 'delivery') setStep('compatibility');
    else if (step === 'publish') setStep('delivery');
  };

  const canProceed = (() => {
    if (step === 'identify') return partName.trim().length >= 3;
    if (step === 'compatibility') return true; // Even LOW/UNKNOWN can proceed.
    if (step === 'delivery')
      return deliveryMode === 'PICKUP' || deliveryAddress.trim().length >= 4;
    if (step === 'publish') return publishConfirmed;
    return false;
  })();

  return (
    <div className="p-5 max-w-2xl" data-testid="part-request-wizard">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Wrench size={20} className="text-forge-500" />
            Solicitar Repuesto
          </h2>
          <p className="font-mono text-[10px] text-steel-300 mt-1">
            Origen: {humanizeSource(initialSourceContext)}{' '}
            {initialDtcCodes.length > 0 && `· ${initialDtcCodes.join(', ')}`}
          </p>
        </div>
        <button
          onClick={onCancel}
          className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1 border border-transparent hover:border-white/10"
        >
          <span className="text-xs font-mono hidden sm:inline-block pr-1">
            Cancelar
          </span>
          <X size={18} />
        </button>
      </div>

      {/* Step Indicator */}
      <div className="flex items-center gap-1 mb-6 overflow-x-auto pb-2">
        {STEP_ORDER.map((s, i) => {
          const idx = STEP_ORDER.findIndex((x) => x.key === step);
          const active = s.key === step;
          return (
            <React.Fragment key={s.key}>
              <button
                onClick={() => (i <= idx ? setStep(s.key) : undefined)}
                className={`px-3 py-1.5 rounded-full text-[10px] font-mono font-bold whitespace-nowrap transition-all ${
                  active
                    ? 'bg-forge-500 text-black'
                    : 'text-steel-300 glass-inner hover:text-white'
                }`}
                disabled={i > idx}
                data-testid={`wizard-step-${s.key}`}
              >
                {i + 1}. {s.label}
              </button>
              {i < STEP_ORDER.length - 1 && (
                <ChevronRight size={12} className="text-steel-400 flex-shrink-0" />
              )}
            </React.Fragment>
          );
        })}
      </div>

      <div className="min-h-[300px]">
        {/* Step 1: Identify */}
        {step === 'identify' && (
          <div className="space-y-3 animate-slide-up">
            {suggestions.length > 0 && (
              <div>
                <div className="font-mono text-[10px] text-steel-300 uppercase tracking-wide mb-2">
                  Sugerencias priorizadas
                </div>
                <div className="space-y-1.5">
                  {suggestions.slice(0, 5).map((s, i) => (
                    <button
                      key={`${s.partName}-${i}`}
                      onClick={() => handleSelectSuggestion(s)}
                      className={`w-full text-left p-2.5 rounded-lg text-xs transition-all ${
                        partName === s.partName
                          ? 'bg-forge-500/10 border border-forge-500/30'
                          : 'glass-inner glass-hover'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex-1">
                          <div className="font-bold text-white flex items-center gap-2">
                            {s.partName}
                            {s.riskPart && (
                              <span className="text-[10px] font-mono text-red-300 px-1.5 py-0.5 rounded border border-red-500/30 bg-red-500/10">
                                ALTO RIESGO
                              </span>
                            )}
                          </div>
                          <div className="font-mono text-[10px] text-steel-300 mt-0.5">
                            {s.category} · {s.position}
                          </div>
                          {s.disclaimer && (
                            <div className="text-[10px] text-amber-300 mt-1 leading-snug">
                              <AlertTriangle size={10} className="inline -mt-0.5 mr-1" />
                              {s.disclaimer}
                            </div>
                          )}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div>
              <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                Nombre de la pieza
              </label>
              <input
                value={partName}
                onChange={(e) => setPartName(e.target.value)}
                placeholder="Ej. Bomba de combustible, Relé, Filtro de aire…"
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none mt-1"
                data-testid="wizard-input-partName"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                  Categoría
                </label>
                <input
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none mt-1"
                  data-testid="wizard-input-category"
                />
              </div>
              <div>
                <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                  Posición
                </label>
                <select
                  value={position}
                  onChange={(e) => setPosition(e.target.value as PartPosition)}
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none mt-1"
                >
                  {[
                    'NOT_APPLICABLE',
                    'ENGINE',
                    'TRANSMISSION',
                    'ELECTRICAL',
                    'BRAKES',
                    'BODY',
                    'INTERIOR',
                    'FUSE_BOX',
                    'FRONT_LEFT',
                    'FRONT_RIGHT',
                    'REAR_LEFT',
                    'REAR_RIGHT',
                    'CENTER',
                    'EXHAUST',
                  ].map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                Número OEM (opcional, sube la confianza)
              </label>
              <input
                value={oemNumber}
                onChange={(e) => setOemNumber(e.target.value)}
                placeholder="Ej. 31110-25000"
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none mt-1"
              />
            </div>

            <div>
              <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                Notas (opcional)
              </label>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none mt-1"
              />
            </div>

            {/* Expose the photo URL + preference as a small detail row. */}
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                  Preferencias
                </label>
                <select
                  value={preference}
                  onChange={(e) => setPreference(e.target.value as PartPreference)}
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white focus:border-forge-500 outline-none mt-1"
                >
                  {[
                    'ANY',
                    'OEM',
                    'AFTERMARKET',
                    'USED',
                    'REFURBISHED',
                    'PERFORMANCE',
                    'BUDGET',
                  ].map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                  Foto URL (opcional)
                </label>
                <input
                  placeholder="https://…"
                  onChange={(e) => setPhotoUrls(e.target.value ? [e.target.value] : [])}
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none mt-1"
                />
              </div>
            </div>
          </div>
        )}

        {/* Step 2: Compatibility */}
        {step === 'compatibility' && (
          <div className="space-y-3 animate-slide-up">
            <p className="font-mono text-[11px] text-steel-300">
              Datos del vehículo activo. Mientras más campos llenes, mayor la
              confianza del veredicto.
            </p>

            <div className="grid grid-cols-2 gap-2">
              <input
                placeholder="Marca (Hyundai)"
                value={vehicle.brand ?? ''}
                onChange={(e) => setVehicle({ ...vehicle, brand: e.target.value })}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none"
              />
              <input
                placeholder="Modelo (Accent Verna)"
                value={vehicle.model ?? ''}
                onChange={(e) => setVehicle({ ...vehicle, model: e.target.value })}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none"
              />
              <input
                placeholder="Año (2005)"
                type="number"
                value={vehicle.year ?? ''}
                onChange={(e) =>
                  setVehicle({
                    ...vehicle,
                    year: e.target.value ? Number(e.target.value) : undefined,
                  })
                }
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none"
              />
              <input
                placeholder="Motor (1.6 AT)"
                value={vehicle.engine ?? ''}
                onChange={(e) => setVehicle({ ...vehicle, engine: e.target.value })}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none"
              />
              <input
                placeholder="VIN (opcional)"
                value={vehicle.vin ?? ''}
                onChange={(e) => setVehicle({ ...vehicle, vin: e.target.value })}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none col-span-2"
                data-testid="wizard-input-vin"
              />
              <input
                placeholder="OEM específico (sube EXACT)"
                value={vehicle.oemNumber ?? oemNumber ?? ''}
                onChange={(e) => {
                  setOemNumber(e.target.value);
                  setVehicle({ ...vehicle, oemNumber: e.target.value });
                }}
                className="bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none col-span-2"
              />
            </div>

            <div className="pt-2">
              <CompatibilityPanel result={verdict} />
            </div>
          </div>
        )}

        {/* Step 3: Delivery */}
        {step === 'delivery' && (
          <div className="space-y-3 animate-slide-up">
            <div>
              <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                Modalidad
              </label>
              <div className="grid grid-cols-2 gap-2 mt-1">
                <button
                  onClick={() => setDeliveryMode('PICKUP')}
                  className={`p-3 rounded-lg text-xs font-mono font-bold transition-all ${
                    deliveryMode === 'PICKUP'
                      ? 'bg-forge-500 text-black'
                      : 'glass-inner glass-hover text-steel-200'
                  }`}
                >
                  🏬 Retiro en tienda
                </button>
                <button
                  onClick={() => setDeliveryMode('DELIVERY')}
                  className={`p-3 rounded-lg text-xs font-mono font-bold transition-all ${
                    deliveryMode === 'DELIVERY'
                      ? 'bg-forge-500 text-black'
                      : 'glass-inner glass-hover text-steel-200'
                  }`}
                >
                  <Truck size={12} className="inline" /> Delivery
                </button>
              </div>
            </div>

            {deliveryMode === 'DELIVERY' && (
              <div>
                <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                  Dirección de entrega
                </label>
                <textarea
                  rows={3}
                  value={deliveryAddress}
                  onChange={(e) => setDeliveryAddress(e.target.value)}
                  placeholder="Calle, avenida, número, referencias…"
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none mt-1"
                />
              </div>
            )}

            <div>
              <label className="font-mono text-[10px] text-steel-300 uppercase tracking-wide">
                Urgencia
              </label>
              <div className="grid grid-cols-3 gap-2 mt-1">
                {(['NORMAL', 'HIGH', 'CRITICAL'] as const).map((u) => (
                  <button
                    key={u}
                    onClick={() => setUrgency(u)}
                    className={`p-2 rounded-lg text-xs font-mono font-bold transition-all ${
                      urgency === u
                        ? 'bg-forge-500 text-black'
                        : 'glass-inner glass-hover text-steel-200'
                    }`}
                  >
                    {u}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Step 4: Publish */}
        {step === 'publish' && (
          <div className="space-y-3 animate-slide-up">
            <div className="rounded-lg border border-steel-500/30 bg-steel-800/30 p-3">
              <div className="font-mono text-[10px] text-steel-300 uppercase tracking-wide mb-2">
                Resumen
              </div>
              <ul className="text-xs text-white space-y-1">
                <li>
                  <strong>Pieza:</strong> {partName || '—'}
                </li>
                <li>
                  <strong>Categoría:</strong> {category || '—'} ·{' '}
                  <strong>Posición:</strong> {position}
                </li>
                <li>
                  <strong>OEM:</strong> {oemNumber || '—'}
                </li>
                <li>
                  <strong>Vehículo:</strong>{' '}
                  {(vehicle.brand || '') +
                    ' ' +
                    (vehicle.model || '') +
                    (vehicle.year ? ' ' + vehicle.year : '')}{' '}
                  {vehicle.engine && '(' + vehicle.engine + ')'}
                </li>
                <li>
                  <strong>Modalidad:</strong>{' '}
                  {deliveryMode === 'PICKUP' ? 'Retiro' : `Delivery → ${deliveryAddress}`}
                </li>
                <li>
                  <strong>Urgencia:</strong> {urgency}
                </li>
              </ul>
            </div>

            <CompatibilityPanel result={verdict} />

            {hasBlockingWarning && (
              <div className="flex items-start gap-2 rounded-lg border border-red-500/40 bg-red-500/10 p-3 text-xs text-red-300">
                <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                <span>
                  Hay una advertencia de tipo <strong>BLOCK</strong> visible
                  arriba. Revísala antes de publicar; si la pieza que elegiste
                  es realmente la correcta, podrás publicar, pero la app
                  priorizará repuesteras que ofrezcan las alternativas de
                  diagnóstico previo.
                </span>
              </div>
            )}

            <label className="flex items-start gap-2 cursor-pointer text-xs text-steel-200">
              <input
                type="checkbox"
                checked={publishConfirmed}
                onChange={(e) => setPublishConfirmed(e.target.checked)}
                className="mt-1 accent-forge-500"
                data-testid="wizard-publish-confirm"
              />
              <span>
                He leído la evaluación de compatibilidad y entiendo que la
                aplicación no garantiza compatibilidad; confirmo los datos.
              </span>
            </label>
          </div>
        )}
      </div>

      {/* Footer nav */}
      <div className="flex items-center justify-between mt-6 pt-4 border-t border-steel-500/20">
        <button
          onClick={goBack}
          disabled={step === 'identify'}
          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-mono font-bold text-steel-200 disabled:opacity-30 disabled:cursor-not-allowed hover:bg-white/5"
        >
          <ChevronLeft size={14} /> Atrás
        </button>

        {step !== 'publish' ? (
          <button
            onClick={goNext}
            disabled={!canProceed}
            className="flex items-center gap-1 px-4 py-2 rounded-lg text-xs font-mono font-bold bg-forge-500 text-black disabled:opacity-30 disabled:cursor-not-allowed hover:bg-forge-600 transition-all"
            data-testid="wizard-next"
          >
            Siguiente <ChevronRight size={14} />
          </button>
        ) : (
          <button
            onClick={() => {
              const payload: DraftPartRequest = {
                partName,
                category,
                position,
                preference,
                oemNumber: oemNumber || undefined,
                photoUrls,
                notes,
                sourceContext: initialSourceContext,
                dtcCodes: initialDtcCodes,
                vehicle,
                deliveryMode,
                deliveryAddress:
                  deliveryMode === 'DELIVERY' ? deliveryAddress : undefined,
                urgency,
              };
              onPublish(payload);
            }}
            disabled={!canProceed}
            className="flex items-center gap-1 px-4 py-2 rounded-lg text-xs font-mono font-bold bg-emerald-500 text-black disabled:opacity-30 disabled:cursor-not-allowed hover:bg-emerald-600 transition-all"
            data-testid="wizard-publish"
          >
            <Send size={14} /> Publicar solicitud
          </button>
        )}
      </div>
    </div>
  );
}

function humanizeSource(s: PartSourceContext): string {
  switch (s) {
    case 'MANUAL':
      return 'Manual';
    case 'FROM_DTC':
      return 'Desde DTC';
    case 'FROM_3D_COMPONENT':
      return 'Desde pieza 3D';
    case 'FROM_MECHANIC_WORK_ORDER':
      return 'Desde orden de mecánico';
    case 'FROM_MAINTENANCE_ALERT':
      return 'Alerta de mantenimiento';
    case 'FROM_PREPURCHASE_INSPECTION':
      return 'Inspección pre-compra';
  }
}

function suggestionSourceFor(s: PartSourceContext) {
  switch (s) {
    case 'FROM_DTC':
      return 'DTC' as const;
    case 'FROM_3D_COMPONENT':
      return '3D_COMPONENT' as const;
    case 'FROM_MECHANIC_WORK_ORDER':
      return 'WORK_ORDER' as const;
    case 'FROM_MAINTENANCE_ALERT':
    case 'FROM_PREPURCHASE_INSPECTION':
    case 'MANUAL':
    default:
      return 'MAINTENANCE_ALERT' as const;
  }
}
