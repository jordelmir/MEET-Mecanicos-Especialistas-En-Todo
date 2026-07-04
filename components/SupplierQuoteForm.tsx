/**
 * Supplier Quote Form
 *
 * Full-supplier-side form to submit a quote against an open PartRequest.
 * All 12 fields from the spec are first-class inputs:
 *
 *   - partName, brand, partNumber, oemNumber
 *   - condition (PartCondition)
 *   - availability (PartAvailability)
 *   - price + currency
 *   - includesDelivery + deliveryFee
 *   - estimatedDeliveryHours
 *   - warrantyDays
 *   - photoUrls[]
 *   - compatibilityConfidence + compatibilityNotes
 *   - expiresInHours
 *
 * The form enforces the same anti-fraud rules as the rest of the pipeline
 * (USED requires photo, EXACT requires OEM/PN + notes, etc.) via
 * validateQuote() in lib/parts/quote.ts. Submit is BLOCKED while there is
 * any validation error.
 *
 * Risk-parts (brakes, fuel system, airbag, etc.) force an explicit install
 * confirmation checkbox before submit, matching the rule in Jor's spec.
 */

import React, { useMemo, useState } from 'react';
import { X, Send, AlertTriangle, ShieldAlert } from 'lucide-react';

import {
  buildQuoteFromForm,
  CompatibilityConfidence,
  isRiskPartForQuote,
  PartAvailability,
  PartCondition,
  SUPPLIER_QUOTE_AVAILABILITIES,
  SUPPLIER_QUOTE_COMPAT,
  SUPPLIER_QUOTE_CONDITIONS,
  SupplierQuoteFormInput,
  validateQuote,
} from '../lib/parts';

interface SupplierQuoteFormProps {
  requestId: string;
  /** Pre-filled from the request; the form fills them as read-only. */
  partName: string;
  defaultCurrency?: string;
  defaultExpiresInHours?: number;
  onCancel: () => void;
  onSubmit: (payload: ReturnType<typeof buildQuoteFromForm>) => void;
}

const inputClass =
  'w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 font-mono text-xs text-white placeholder-steel-400 focus:border-forge-500 outline-none';
const labelClass =
  'font-mono text-[10px] text-steel-300 uppercase tracking-wide mb-1 block';

export function SupplierQuoteForm({
  requestId,
  partName,
  defaultCurrency = 'CRC',
  defaultExpiresInHours = 48,
  onCancel,
  onSubmit,
}: SupplierQuoteFormProps) {
  const [brand, setBrand] = useState('');
  const [partNumber, setPartNumber] = useState('');
  const [oemNumber, setOemNumber] = useState('');
  const [condition, setCondition] = useState<PartCondition>('NEW_OEM');
  const [availability, setAvailability] = useState<PartAvailability>('IN_STOCK');
  const [price, setPrice] = useState<number>(0);
  const [currency, setCurrency] = useState<string>(defaultCurrency);
  const [includesDelivery, setIncludesDelivery] = useState(false);
  const [deliveryFee, setDeliveryFee] = useState<number>(0);
  const [estimatedDeliveryHours, setEstimatedDeliveryHours] = useState<number>(24);
  const [warrantyDays, setWarrantyDays] = useState<number>(30);
  const [photoUrls, setPhotoUrls] = useState<string>('');
  const [compatibilityConfidence, setCompatibilityConfidence] =
    useState<CompatibilityConfidence>('MEDIUM');
  const [compatibilityNotes, setCompatibilityNotes] = useState('');
  const [expiresInHours, setExpiresInHours] = useState<number>(defaultExpiresInHours);
  const [installByQualifiedTech, setInstallByQualifiedTech] = useState(false);

  const form: SupplierQuoteFormInput = useMemo(
    () => ({
      partName,
      brand,
      partNumber,
      oemNumber,
      condition,
      availability,
      price,
      currency,
      includesDelivery,
      deliveryFee,
      estimatedDeliveryHours,
      warrantyDays,
      photoUrls: photoUrls.split(/\s*,\s*/),
      compatibilityConfidence,
      compatibilityNotes,
      expiresInHours,
    }),
    [
      partName,
      brand,
      partNumber,
      oemNumber,
      condition,
      availability,
      price,
      currency,
      includesDelivery,
      deliveryFee,
      estimatedDeliveryHours,
      warrantyDays,
      photoUrls,
      compatibilityConfidence,
      compatibilityNotes,
      expiresInHours,
    ],
  );

  const validation = useMemo(() => validateQuote(buildQuoteFromForm(form)), [form]);
  const isRisk = isRiskPartForQuote(partName);
  const canSubmit =
    validation.level !== 'BLOCK' && (!isRisk || installByQualifiedTech);

  return (
    <div className="p-5 max-w-2xl" data-testid="supplier-quote-form">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Send size={20} className="text-forge-500" />
            Cotizar Solicitud
          </h2>
          <p className="font-mono text-[10px] text-steel-300 mt-1">
            Request ID: {requestId} · Pieza: {partName}
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

      <div className="space-y-3">
        {/* Brand + part/oem row */}
        <div className="grid grid-cols-3 gap-2">
          <div>
            <label className={labelClass}>Marca</label>
            <input
              value={brand}
              onChange={(e) => setBrand(e.target.value)}
              placeholder="NGK, Bosch, …"
              className={inputClass}
              data-testid="quote-input-brand"
            />
          </div>
          <div>
            <label className={labelClass}>N° de parte</label>
            <input
              value={partNumber}
              onChange={(e) => setPartNumber(e.target.value)}
              placeholder="U5156"
              className={inputClass}
            />
          </div>
          <div>
            <label className={labelClass}>N° OEM</label>
            <input
              value={oemNumber}
              onChange={(e) => setOemNumber(e.target.value)}
              placeholder="27301-2B100"
              className={inputClass}
              data-testid="quote-input-oem"
            />
          </div>
        </div>

        {/* Condition + availability */}
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className={labelClass}>Condición</label>
            <select
              value={condition}
              onChange={(e) => setCondition(e.target.value as PartCondition)}
              className={inputClass}
            >
              {SUPPLIER_QUOTE_CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass}>Disponibilidad</label>
            <select
              value={availability}
              onChange={(e) => setAvailability(e.target.value as PartAvailability)}
              className={inputClass}
            >
              {SUPPLIER_QUOTE_AVAILABILITIES.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Price / currency / warranty */}
        <div className="grid grid-cols-3 gap-2">
          <div>
            <label className={labelClass}>Precio</label>
            <input
              type="number"
              value={price}
              min={0}
              onChange={(e) => setPrice(Number(e.target.value))}
              className={inputClass}
              data-testid="quote-input-price"
            />
          </div>
          <div>
            <label className={labelClass}>Moneda</label>
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              className={inputClass}
            >
              <option>CRC</option>
              <option>USD</option>
            </select>
          </div>
          <div>
            <label className={labelClass}>Garantía (días)</label>
            <input
              type="number"
              value={warrantyDays}
              min={0}
              onChange={(e) => setWarrantyDays(Number(e.target.value))}
              className={inputClass}
            />
          </div>
        </div>

        {/* Delivery */}
        <div className="grid grid-cols-3 gap-2">
          <label className="flex items-center gap-2 text-xs text-white font-mono">
            <input
              type="checkbox"
              checked={includesDelivery}
              onChange={(e) => setIncludesDelivery(e.target.checked)}
              className="accent-forge-500"
            />
            Incluye entrega
          </label>
          <div>
            <label className={labelClass}>Costo delivery</label>
            <input
              type="number"
              value={deliveryFee}
              min={0}
              disabled={!includesDelivery}
              onChange={(e) => setDeliveryFee(Number(e.target.value))}
              className={inputClass}
            />
          </div>
          <div>
            <label className={labelClass}>ETA (horas)</label>
            <input
              type="number"
              value={estimatedDeliveryHours}
              min={0}
              onChange={(e) => setEstimatedDeliveryHours(Number(e.target.value))}
              className={inputClass}
            />
          </div>
        </div>

        {/* Photos */}
        <div>
          <label className={labelClass}>
            Fotos (URLs separadas por coma)
          </label>
          <textarea
            rows={2}
            value={photoUrls}
            onChange={(e) => setPhotoUrls(e.target.value)}
            placeholder="https://…jpg, https://…jpg"
            className={inputClass}
            data-testid="quote-input-photos"
          />
        </div>

        {/* Compatibility */}
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className={labelClass}>Compatibilidad declarada</label>
            <select
              value={compatibilityConfidence}
              onChange={(e) =>
                setCompatibilityConfidence(e.target.value as CompatibilityConfidence)
              }
              className={inputClass}
            >
              {SUPPLIER_QUOTE_COMPAT.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass}>Expira en (horas)</label>
            <input
              type="number"
              value={expiresInHours}
              min={1}
              onChange={(e) => setExpiresInHours(Number(e.target.value))}
              className={inputClass}
            />
          </div>
        </div>

        <div>
          <label className={labelClass}>Notas de compatibilidad</label>
          <textarea
            rows={3}
            value={compatibilityNotes}
            onChange={(e) => setCompatibilityNotes(e.target.value)}
            placeholder="Verificado contra Hyundai Accent Verna 2005 1.6 G4FC, conector y amperaje coinciden…"
            className={inputClass}
          />
        </div>

        {/* Risk-part confirmation gate */}
        {isRisk && (
          <div className="rounded-lg border border-red-500/40 bg-red-500/10 p-3">
            <label className="flex items-start gap-2 cursor-pointer text-xs text-red-200">
              <input
                type="checkbox"
                checked={installByQualifiedTech}
                onChange={(e) => setInstallByQualifiedTech(e.target.checked)}
                className="mt-1 accent-red-500"
                data-testid="quote-risk-confirm"
              />
              <span>
                <ShieldAlert size={14} className="inline -mt-0.5 mr-1" />
                <strong>Pieza crítica de seguridad.</strong> Confirmo que
                indicaré al cliente que la instalación debe realizarla un
                técnico calificado. Una pieza incompatible puede causar falla
                mecánica, eléctrica o de seguridad.
              </span>
            </label>
          </div>
        )}

        {/* Validation panel */}
        {validation.errors.length > 0 && (
          <div className="rounded-lg border border-red-500/40 bg-red-500/10 p-3 text-xs text-red-300 space-y-1">
            <div className="font-mono font-bold uppercase tracking-wide text-[10px] flex items-center gap-1.5">
              <AlertTriangle size={12} /> Bloqueos
            </div>
            <ul className="space-y-0.5">
              {validation.errors.map((e, i) => (
                <li key={i}>▸ {e}</li>
              ))}
            </ul>
          </div>
        )}
        {validation.warnings.length > 0 && (
          <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-300 space-y-1">
            <div className="font-mono font-bold uppercase tracking-wide text-[10px] flex items-center gap-1.5">
              <AlertTriangle size={12} /> Advertencias
            </div>
            <ul className="space-y-0.5">
              {validation.warnings.map((e, i) => (
                <li key={i}>▸ {e}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="flex items-center justify-end gap-2 mt-6 pt-4 border-t border-steel-500/20">
        <button
          onClick={onCancel}
          className="px-3 py-1.5 rounded-lg text-xs font-mono font-bold text-steel-200 hover:bg-white/5"
        >
          Cancelar
        </button>
        <button
          onClick={() => onSubmit(buildQuoteFromForm(form))}
          disabled={!canSubmit}
          className="flex items-center gap-1 px-4 py-2 rounded-lg text-xs font-mono font-bold bg-forge-500 text-black disabled:opacity-30 disabled:cursor-not-allowed hover:bg-forge-600 transition-all"
          data-testid="quote-submit"
        >
          <Send size={14} /> Enviar cotización
        </button>
      </div>
    </div>
  );
}
