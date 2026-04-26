
import React from 'react';
import { WorkOrder, Service, Mechanic, Client } from '../types';
import { getStatusLabel, formatDuration } from '../services/timeEngine';
import { X, Printer, Download } from 'lucide-react';

interface WorkOrderReceiptProps {
  workOrder: WorkOrder;
  service: Service | undefined;
  mechanic: Mechanic | undefined;
  client: Client | undefined;
  onClose: () => void;
}

export function WorkOrderReceipt({ workOrder, service, mechanic, client, onClose }: WorkOrderReceiptProps) {
  const handlePrint = () => {
    const printContent = document.getElementById('receipt-content');
    if (!printContent) return;
    const win = window.open('', '', 'width=800,height=600');
    if (!win) return;
    win.document.write(`
      <html>
        <head>
          <title>Orden de Trabajo — ${workOrder.id}</title>
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: 'Segoe UI', Tahoma, sans-serif; padding: 30px; color: #111; background: #fff; }
            .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px solid #00f0ff; padding-bottom: 16px; margin-bottom: 20px; }
            .logo { font-size: 28px; font-weight: 900; letter-spacing: 2px; }
            .logo span { color: #00f0ff; }
            .order-id { font-family: monospace; font-size: 13px; color: #666; margin-top: 4px; }
            .meta { text-align: right; font-size: 12px; color: #666; }
            .meta strong { display: block; font-size: 14px; color: #111; }
            .section { margin-bottom: 20px; }
            .section-title { font-size: 11px; letter-spacing: 2px; text-transform: uppercase; color: #999; font-weight: 700; margin-bottom: 8px; border-bottom: 1px solid #eee; padding-bottom: 4px; }
            .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
            .field { font-size: 12px; }
            .field .label { color: #999; font-size: 10px; text-transform: uppercase; letter-spacing: 1px; }
            .field .value { font-weight: 600; margin-top: 2px; }
            .divider { border: none; border-top: 1px dashed #ddd; margin: 20px 0; }
            .total { text-align: right; font-size: 24px; font-weight: 900; color: #00f0ff; margin-top: 12px; }
            .total-label { font-size: 11px; color: #999; text-transform: uppercase; letter-spacing: 2px; }
            .footer { margin-top: 40px; text-align: center; font-size: 10px; color: #ccc; border-top: 1px solid #eee; padding-top: 12px; }
            .status { display: inline-block; padding: 4px 12px; border-radius: 4px; font-size: 11px; font-weight: 700; background: #f0f0f0; margin-top: 4px; }
            .notes { background: #f9f9f9; padding: 12px; border-radius: 6px; font-size: 12px; color: #555; margin-top: 8px; border-left: 3px solid #00f0ff; }
            .signature { margin-top: 40px; display: flex; gap: 40px; }
            .signature .sig-line { flex: 1; text-align: center; }
            .signature .sig-line .line { border-top: 1px solid #333; margin-top: 50px; padding-top: 8px; font-size: 11px; color: #666; }
          </style>
        </head>
        <body>
          ${printContent.innerHTML}
        </body>
      </html>
    `);
    win.document.close();
    win.focus();
    setTimeout(() => { win.print(); win.close(); }, 300);
  };

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
      <div className="w-full max-w-2xl glass rounded-2xl overflow-hidden">
        {/* Toolbar */}
        <div className="p-4 border-b border-white/5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white">Orden de Trabajo</h2>
          <div className="flex gap-2">
            <button onClick={handlePrint} className="flex items-center gap-2 px-4 py-2 bg-forge-500 text-black font-bold rounded-lg text-xs font-mono hover:bg-forge-400 transition-all">
              <Printer size={14} /> Imprimir
            </button>
            <button onClick={onClose} className="p-2 rounded-lg text-steel-300 hover:text-white hover:bg-white/5 transition-all flex items-center gap-1">
              <span className="text-xs font-mono hidden sm:inline-block pr-1">Volver</span>
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Receipt Preview (Dark styled for screen) */}
        <div className="p-6 max-h-[75vh] overflow-y-auto">
          <div id="receipt-content">
            {/* Header */}
            <div className="header" style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '3px solid #00f0ff', paddingBottom: '16px', marginBottom: '20px' }}>
              <div>
                <div className="logo" style={{ fontSize: '28px', fontWeight: 900, letterSpacing: '2px' }}>
                  ME<span style={{ color: '#00f0ff' }}>ET</span>
                </div>
                <div style={{ fontFamily: 'monospace', fontSize: '13px', color: '#666', marginTop: '4px' }}>
                  Orden #{workOrder.id.toUpperCase()}
                </div>
              </div>
              <div style={{ textAlign: 'right', fontSize: '12px', color: '#666' }}>
                <strong style={{ display: 'block', fontSize: '14px', color: '#111' }}>
                  {workOrder.startTime.toLocaleDateString('es-CR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                </strong>
                <div style={{ marginTop: '4px' }}>Hora: {workOrder.startTime.toLocaleTimeString('es-CR', { hour: '2-digit', minute: '2-digit' })}</div>
                <div style={{ display: 'inline-block', padding: '4px 12px', borderRadius: '4px', fontSize: '11px', fontWeight: 700, background: '#f0f0f0', marginTop: '4px' }}>
                  {getStatusLabel(workOrder.status)}
                </div>
              </div>
            </div>

            {/* Client & Vehicle */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '20px' }}>
              <div>
                <div style={{ fontSize: '11px', letterSpacing: '2px', textTransform: 'uppercase', color: '#999', fontWeight: 700, marginBottom: '8px', borderBottom: '1px solid #eee', paddingBottom: '4px' }}>
                  Datos del Cliente
                </div>
                <div style={{ fontSize: '12px' }}>
                  <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Nombre</span><br /><strong>{workOrder.clientName}</strong></div>
                  {client && (
                    <>
                      <div style={{ marginTop: '6px' }}><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Teléfono</span><br /><strong>{client.phone}</strong></div>
                      <div style={{ marginTop: '6px' }}><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Cédula</span><br /><strong>{client.identification}</strong></div>
                    </>
                  )}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '11px', letterSpacing: '2px', textTransform: 'uppercase', color: '#999', fontWeight: 700, marginBottom: '8px', borderBottom: '1px solid #eee', paddingBottom: '4px' }}>
                  Datos del Vehículo
                </div>
                <div style={{ fontSize: '12px' }}>
                  <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Placa</span><br /><strong style={{ color: '#00f0ff', fontSize: '16px' }}>{workOrder.vehicleInfo.plate}</strong></div>
                  <div style={{ marginTop: '6px' }}><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Vehículo</span><br /><strong>{workOrder.vehicleInfo.brand} {workOrder.vehicleInfo.model} {workOrder.vehicleInfo.year}</strong></div>
                  <div style={{ marginTop: '6px' }}><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Kilometraje</span><br /><strong>{workOrder.vehicleInfo.mileage.toLocaleString()} km</strong></div>
                </div>
              </div>
            </div>

            {/* Service Details */}
            <div style={{ marginBottom: '20px' }}>
              <div style={{ fontSize: '11px', letterSpacing: '2px', textTransform: 'uppercase', color: '#999', fontWeight: 700, marginBottom: '8px', borderBottom: '1px solid #eee', paddingBottom: '4px' }}>
                Servicio Realizado
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '12px' }}>
                <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Servicio</span><br /><strong>{service?.name || 'N/A'}</strong></div>
                <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Mecánico Asignado</span><br /><strong>{mechanic?.name || 'N/A'}</strong></div>
                <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Duración Estimada</span><br /><strong>{formatDuration(workOrder.estimatedMinutes)}</strong></div>
                <div><span style={{ color: '#999', fontSize: '10px', textTransform: 'uppercase' }}>Especialidad</span><br /><strong>{mechanic?.specialty || 'N/A'}</strong></div>
              </div>
            </div>

            {/* Notes */}
            {(workOrder.notes || workOrder.diagnosticNotes) && (
              <div style={{ marginBottom: '20px' }}>
                <div style={{ fontSize: '11px', letterSpacing: '2px', textTransform: 'uppercase', color: '#999', fontWeight: 700, marginBottom: '8px', borderBottom: '1px solid #eee', paddingBottom: '4px' }}>
                  Observaciones
                </div>
                {workOrder.notes && (
                  <div style={{ background: '#f9f9f9', padding: '12px', borderRadius: '6px', fontSize: '12px', color: '#555', borderLeft: '3px solid #00f0ff' }}>
                    {workOrder.notes}
                  </div>
                )}
                {workOrder.diagnosticNotes && (
                  <div style={{ background: '#f9f9f9', padding: '12px', borderRadius: '6px', fontSize: '12px', color: '#555', borderLeft: '3px solid #2563eb', marginTop: '8px' }}>
                    <strong>Diagnóstico:</strong> {workOrder.diagnosticNotes}
                  </div>
                )}
              </div>
            )}

            {/* Pricing */}
            <div style={{ borderTop: '1px dashed #ddd', marginTop: '20px', paddingTop: '20px' }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '11px', color: '#999', textTransform: 'uppercase', letterSpacing: '2px' }}>Total</div>
                <div style={{ fontSize: '32px', fontWeight: 900, color: '#00f0ff', marginTop: '4px' }}>
                  ₡{workOrder.price.toLocaleString('es-CR', { minimumFractionDigits: 2 })}
                </div>
              </div>
            </div>

            {/* Signatures */}
            <div style={{ marginTop: '40px', display: 'flex', gap: '40px' }}>
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ borderTop: '1px solid #333', marginTop: '50px', paddingTop: '8px', fontSize: '11px', color: '#666' }}>
                  Firma del Cliente
                </div>
              </div>
              <div style={{ flex: 1, textAlign: 'center' }}>
                <div style={{ borderTop: '1px solid #333', marginTop: '50px', paddingTop: '8px', fontSize: '11px', color: '#666' }}>
                  Firma del Taller
                </div>
              </div>
            </div>

            {/* Footer */}
            <div style={{ marginTop: '30px', textAlign: 'center', fontSize: '10px', color: '#ccc', borderTop: '1px solid #eee', paddingTop: '12px' }}>
              MEET — Mecánicos Especialistas En Todo · Este documento es un comprobante de servicio
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
