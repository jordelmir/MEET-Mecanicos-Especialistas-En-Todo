# Pagos y cobro de comisión

## Estrategias previstas

- `PREPAID_DRIVER_WALLET`;
- `TENANT_MASTER_WALLET`;
- `AUTOMATIC_SPLIT_PAYMENT`;
- `POSTPAID_INVOICE`;
- `AUTHORIZED_CREDIT_LINE`.

Cada tenant y jurisdicción seleccionará una estrategia backend. Android no
decide si una deuda está autorizada.

## Efectivo

El pasajero paga al conductor. Al asignar se reserva la comisión estimada; al
completar se captura la comisión final y se libera cualquier diferencia. Una
insuficiencia posterior puede generar receivable o límite para aceptar nuevos
viajes, pero nunca interrumpe un viaje activo ni bloquea una emergencia.

## SINPE

Seleccionar SINPE no confirma pago. El estado requiere referencia única y uno
de estos comprobantes:

- confirmación real del banco/procesador;
- conciliación operativa;
- evidencia manual marcada `PENDING_REVIEW`.

Una imagen nunca equivale automáticamente a settlement. Duplicados y montos se
reconcilian por referencia, actor, moneda e importe.

## Pago electrónico

El flujo autorizado es payment intent → autorización → captura → settlement o
split → webhook autenticado. Webhooks y refunds son idempotentes; chargebacks y
correcciones crean journals compensatorios.

Google Play Billing no se usa para pagar transporte físico. La integración de
un procesador se habilitará únicamente cuando existan credenciales, contrato y
webhooks reales; la APK no mostrará un pago ficticio como confirmado.
