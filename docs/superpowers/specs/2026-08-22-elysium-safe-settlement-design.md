# Elysium Safe Settlement — comisión universal, custodia y liquidación SINPE

**Fecha:** 2026-08-22

**Estado:** diseño aprobado por el propietario

**Decisión comercial:** MEET retiene una comisión de 500 puntos base (5%) sobre todo servicio o venta liquidada a un proveedor.
**Ámbito inicial:** reparaciones, talleres, grúas, viajes, repuestos, inspecciones y Servicios Elysium.

## 1. Resultado esperado

MEET debe transformar cada solicitud aceptada en una transacción verificable:

1. El cliente conoce el alcance, precio, moneda, proveedor y política de comisión antes de pagar.
2. El cliente aporta fondos por un canal SINPE conciliable.
3. Los fondos quedan segregados y reservados para una orden concreta; no son ingresos de MEET.
4. El proveedor ejecuta el servicio y presenta la evidencia exigida por su vertical y nivel de riesgo.
5. El cliente acepta, disputa o deja vencer una ventana de revisión claramente informada.
6. El backend decide de forma autoritativa si el pago puede liquidarse.
7. MEET reconoce el 5% y ordena el pago del 95% al proveedor verificado.
8. Cada movimiento queda en un libro mayor inmutable de doble entrada y puede reconstruirse sin confiar en la interfaz Android.

La aplicación nunca debe mostrar como acreditado, reservado, pagado o ganado un monto que no tenga evidencia bancaria y un asiento autoritativo correspondiente.

## 2. Principios no negociables

- **Dinero entero y tipado:** todos los montos usan unidades menores en `Long`/`bigint` y moneda ISO 4217. CRC usa colones enteros; USD usa centavos. Se prohíbe `Double` y `Float` en contratos monetarios.
- **Autoridad del servidor:** Android propone comandos; PostgreSQL ejecuta transiciones, calcula comisiones y escribe el ledger en una sola transacción.
- **Fail closed:** una orden no se liquida si faltan identidad, contrato, financiación conciliada, evidencia, autorización o política versionada.
- **Idempotencia obligatoria:** todo comando financiero exige una clave única con ámbito de actor y operación. Repetirlo produce la misma respuesta, nunca un segundo cargo o pago.
- **Fondos segregados:** dinero de clientes, comisión todavía no devengada e ingresos propios de MEET son saldos contables distintos y deben corresponder a cuentas bancarias o de custodia separadas.
- **Evidencia antes de liquidación:** una captura de pantalla, un estado local o un botón pulsado no prueban pago ni trabajo terminado.
- **Contratos congelados:** precio, comisión, alcance, proveedor, moneda y política de evidencia se versionan al aceptar la oferta. Los cambios posteriores crean una enmienda aceptada por ambas partes.
- **Correcciones compensatorias:** los asientos publicados no se editan ni eliminan. Reembolsos, reversos y ajustes crean transacciones compensatorias.
- **Menor privilegio:** clientes y proveedores no pueden insertar asientos, reconciliar depósitos, declarar payouts exitosos ni decidir disputas.
- **Verdad de producto:** mientras no exista un custodio/integración productiva aprobada, la UI debe indicar `Configuración financiera pendiente`; no debe simular saldo transferible.

## 3. Modelo regulatorio y operativo

### 3.1 Arquitectura elegida

SINPE es el medio de entrada y salida, pero la custodia productiva se realiza mediante una cuenta empresarial segregada o un proveedor de servicios de pago/banco autorizado. No se utilizará una cuenta SINPE personal del propietario.

El adaptador de pagos debe soportar dos modalidades explícitas:

- `REGULATED_CUSTODY`: integración productiva con un custodio que entregue referencias, eventos firmados, conciliación y payouts.
- `CONTROLLED_PILOT`: conciliación operativa por personal autorizado sobre una cuenta empresarial segregada. No ofrece recargas generales ni saldo retirable y requiere aprobación legal/contable antes de activarse.

`SIMULATED`, `SCREENSHOT_ACCEPTED` y `PERSONAL_SINPE` no son modalidades válidas de producción.

### 3.2 Puertas de despliegue

La funcionalidad de custodia y payouts permanece deshabilitada en producción hasta contar con:

- persona jurídica y cuenta empresarial identificadas;
- contrato con custodio/banco o dictamen jurídico para el piloto controlado;
- procedimiento de identificación de clientes y proveedores, prevención de fraude y gestión de operaciones sospechosas;
- política de privacidad, términos de custodia, comisión, cancelación, disputa y reembolso aceptados y versionados;
- facturación y tratamiento fiscal de la comisión definidos por un profesional competente;
- reconciliación diaria y separación comprobable entre fondos de terceros e ingresos propios.

Estas son dependencias de activación, no excusas para degradar los controles técnicos.

## 4. Política comercial del 5%

La política canónica inicial es:

```text
policy_code        = MEET_PROVIDER_COMMISSION_V1
rate_basis_points  = 500
payer              = PROVIDER
basis              = CAPTURED_SETTLEMENT_AMOUNT
rounding            = HALF_UP
effective_currency = ORDER_CURRENCY
```

Para un servicio de ₡100.000:

- bruto liquidado: ₡100.000;
- comisión MEET: ₡5.000;
- pago neto al proveedor: ₡95.000.

Reglas:

- La comisión se calcula en el backend mediante multiplicación segura y redondeo definido.
- MEET no devenga comisión al crear la orden, recibir una oferta o recibir una recarga; la devenga al liquidar un monto capturado.
- Un reembolso total antes de liquidación no genera comisión.
- Una liquidación parcial genera 5% únicamente sobre el monto liquidado.
- Un reembolso posterior crea una compensación de cliente, proveedor y comisión según la resolución de disputa.
- Las propinas, impuestos, costos bancarios y gastos reembolsables se modelan como líneas separadas. No entran a la base salvo que una política posterior, versionada y aceptada lo autorice.
- No existen porcentajes codificados en pantallas o clientes. Toda regla procede del contrato autoritativo congelado.

## 5. Cobertura de verticales

| Vertical | Transacción | Evidencia mínima antes de liquidar | Imágenes por paso |
| --- | --- | --- | --- |
| Reparación/taller | `REPAIR_SERVICE` | orden aceptada, diagnóstico, evidencia antes/después, trabajo ejecutado, importe final y confirmación del flujo de cierre | obligatorias |
| Grúa/rescate | `TOW_SERVICE` | origen/destino contratados, llegada, recogida y entrega con tiempos/ubicación o evidencia equivalente | obligatorias |
| Viaje | `RIDE_SERVICE` | conductor y vehículo verificados, recorrido iniciado/finalizado, monto contratado y ausencia de disputa activa | excluidas de este sistema |
| Repuestos | `PARTS_ORDER` | SKU/OEM o compatibilidad declarada, estado del artículo, entrega y recepción; VIN/OEM cuando se afirme aplicabilidad | obligatorias |
| Inspección | `INSPECTION_SERVICE` | alcance, checklist, informe firmado/hash y entrega al cliente | obligatorias |
| Servicios Elysium | `UNIVERSAL_SERVICE` | plantilla de evidencia según definición, modalidad y nivel de riesgo | obligatorias |

El motor no debe imponer una única evidencia genérica. Cada `service_definition` referencia una `evidence_policy_version`.

### 5.1 Evidencia visual obligatoria por momento

Todo servicio distinto de `RIDE_SERVICE` se descompone en uno o más pasos de ejecución. Cada paso declarado exige, como mínimo, un conjunto visual `BEFORE` y otro `AFTER` antes de poder completarse.

Ejemplo:

```text
Paso: Cambiar tubo de refrigerante
  BEFORE -> tubo instalado, daño/fuga y área de trabajo antes de intervenir
  AFTER  -> tubo nuevo instalado, conexiones terminadas y área final
```

No es suficiente subir dos imágenes genéricas al final de la orden. Cada imagen queda vinculada a:

- orden, versión contractual y paso concreto;
- fase `BEFORE` o `AFTER`;
- autor autenticado y rol;
- hora declarada por el dispositivo y hora recibida por el servidor;
- origen `CAMERA_CAPTURE`, `GALLERY_IMPORT`, `DOCUMENT_RENDER` o `SCREEN_CAPTURE`;
- hash SHA-256, tipo MIME, tamaño y dimensiones;
- manifiesto de lote y versión de evidencia;
- consentimiento/advertencia de privacidad cuando corresponda.

El flujo por paso es:

```text
DECLARED
  -> BEFORE_REQUIRED
  -> BEFORE_COMPLETE
  -> WORK_IN_PROGRESS
  -> AFTER_REQUIRED
  -> AFTER_COMPLETE
  -> REVIEWABLE
  -> ACCEPTED | DISPUTED
```

Reglas autoritativas:

- No se puede iniciar normalmente un paso sin al menos una imagen `BEFORE` válida.
- No se puede marcar completado sin al menos una imagen `AFTER` válida.
- No se puede enviar la orden a satisfacción ni liquidación si cualquier paso obligatorio está incompleto.
- Reemplazar o borrar una imagen ya publicada está prohibido. Una corrección crea una nueva versión y conserva la anterior.
- Las imágenes importadas de galería se rotulan como tales; nunca se presentan como captura en vivo.
- La app calcula el hash local, pero el backend vuelve a calcularlo al cerrar la carga.
- La comparación antes/después ayuda a revisar, pero ningún algoritmo afirma por sí solo que el trabajo fue correcto.
- El cliente puede disputar un paso específico y señalar la imagen o ausencia correspondiente.
- En una emergencia de seguridad, el proveedor puede diferir la captura previa con un motivo tipado y evidencia tan pronto sea seguro; la excepción no permite liquidar sin revisión administrativa.

Para servicios físicos, las imágenes representan el objeto, espacio o condición intervenida. Para servicios digitales o profesionales, representan el estado/entregable inicial y el resultado visible, con redacción y controles de privacidad cuando haya información sensible. Si el servicio no puede documentarse visualmente sin exponer datos protegidos, el proveedor solicita una excepción de privacidad; MEET define evidencia sustituta y la liquidación requiere revisión humana.

Los viajes se excluyen expresamente de esta obligación por paso. Sus controles existentes de conductor, ruta, llegada y finalización continúan vigentes, pero no se les añade un requisito de fotografías antes/después.

## 6. Ciclo financiero canónico

### 6.1 Estados de financiación

```text
CREATED
  -> AWAITING_FUNDS
  -> FUNDS_PENDING_RECONCILIATION
  -> FUNDED
  -> RESERVED_FOR_ORDER
  -> RELEASE_ELIGIBLE
  -> SETTLED
  -> PAYOUT_PENDING
  -> PAID_OUT
```

Ramas controladas:

```text
AWAITING_FUNDS -> EXPIRED
FUNDS_PENDING_RECONCILIATION -> REJECTED
FUNDED/RESERVED_FOR_ORDER -> REFUND_PENDING -> REFUNDED
RESERVED_FOR_ORDER/RELEASE_ELIGIBLE -> DISPUTED -> RESOLVED
PAYOUT_PENDING -> PAYOUT_FAILED -> PAYOUT_PENDING
```

No se permiten saltos decididos por el cliente Android.

### 6.2 Estados de ejecución del servicio

La máquina financiera observa, pero no reemplaza, la máquina de cada vertical:

```text
CONTRACTED -> FUNDED -> IN_PROGRESS -> PROOF_SUBMITTED
-> CUSTOMER_REVIEW -> ACCEPTED -> RELEASE_ELIGIBLE
```

Una disputa mueve la orden a `DISPUTED` y congela el monto reservado. Un estado local `COMPLETED` no basta para liberar fondos.

### 6.3 Ventana de satisfacción

- El cliente recibe el entregable y un resumen de evidencia.
- Puede aceptar o abrir disputa durante la ventana contractual.
- El proveedor no puede aceptar en nombre del cliente.
- El silencio del cliente no produce un payout instantáneo. Al vencer la ventana, el motor revisa la suficiencia de evidencia y el nivel de riesgo.
- Servicios estándar con evidencia completa pueden pasar a revisión administrativa acelerada.
- Servicios elevados/restringidos, anomalías, montos altos o evidencia incompleta requieren decisión humana.
- Toda decisión registra actor, razón, política, evidencia utilizada y hora del servidor.

## 7. Cuentas y libro mayor

### 7.1 Cuentas contables mínimas

- `CUSTODY_CASH`: fondos reales recibidos y conciliados.
- `CUSTOMER_AVAILABLE:<customer>`: fondos no reservados del cliente.
- `ORDER_RESERVED:<order>`: fondos comprometidos con una orden.
- `PROVIDER_PAYABLE:<provider>`: obligación de pago al proveedor.
- `PLATFORM_COMMISSION_PAYABLE`: comisión devengada aún no transferida a ingresos propios.
- `PLATFORM_REVENUE`: ingresos propios realizados por MEET.
- `REFUND_PAYABLE:<customer>`: reembolsos aprobados pendientes.
- `PAYOUT_IN_TRANSIT:<payout>`: pagos enviados pero no confirmados.
- `PROCESSOR_FEES`: costos externos explícitos.

Los nombres expresan finalidad económica; no se mezclan con balances de UI.

### 7.2 Invariantes

- Débitos y créditos de cada transacción suman exactamente lo mismo por moneda.
- Ninguna cuenta queda negativa salvo una cuenta técnica autorizada por una política explícita.
- La suma de obligaciones a clientes, órdenes y proveedores nunca supera los fondos conciliados bajo custodia.
- Una orden solo puede tener una reserva activa por versión contractual.
- Una liquidación solo puede capturar una reserva no liquidada.
- Un payout confirmado referencia una instrucción y un identificador externo únicos.
- La comisión más el neto del proveedor equivale al bruto liquidado.
- No se mezclan CRC y USD en una misma transacción contable.

## 8. Modelo de datos autoritativo

Las migraciones serán aditivas y usarán UUID, timestamps del servidor, `version bigint`, constraints e índices explícitos.

### 8.1 Configuración y contratos

- `financial_rails`: adaptadores permitidos, modo, estado y versión de configuración; nunca contiene secretos del proveedor.
- `commission_policies`: código, vertical, 500 bps, base, redondeo, vigencia y estado.
- `evidence_policies`: requisitos por vertical, modalidad, riesgo y versión.
- `service_financial_contracts`: instantánea inmutable del contrato aceptado, precio, moneda, comisión, partes y hashes de términos/evidencia.
- `service_execution_steps`: pasos congelados por orden, secuencia, descripción, riesgo y estado de evidencia.
- `service_step_evidence_sets`: fase `BEFORE`/`AFTER`, paso, versión, manifiesto y estado de revisión.
- `service_media_objects`: objeto privado, autor, origen, metadatos seguros, hash calculado por servidor y estado de carga.
- `service_evidence_exceptions`: excepción de emergencia o privacidad, motivo tipado, evidencia sustituta y decisión administrativa.

La implementación aprovecha los contratos existentes `repair_evidence` y `report_evidence` mediante referencias/adaptadores hacia el manifiesto canónico; no copia archivos ni rompe hashes históricos. Las filas heredadas permanecen legibles con procedencia `LEGACY_UNSCOPED_STEP` hasta que puedan asociarse de forma inequívoca. La evidencia de viajes y sus tablas permanecen intactas y fuera del nuevo gate visual.

### 8.2 Fondos y conciliación

- `funding_intents`: referencia única, actor, importe esperado, moneda, expiración y orden opcional.
- `external_payment_events`: evento bancario deduplicado, payload hash, firma verificada, importe, moneda y referencia.
- `funding_reconciliations`: relación autoritativa entre evento externo e intención, con método y actor.
- `ledger_accounts`, `ledger_transactions`, `ledger_postings`: libro mayor de doble entrada, inmutable.
- `order_reservations`: monto reservado, contrato, estado y versión.

### 8.3 Cumplimiento y salida

- `settlement_decisions`: aceptación, expiración revisada, disputa o decisión administrativa con evidencia y política.
- `provider_payout_accounts`: teléfono/IBAN tokenizado, titular verificado, estado y proveedor.
- `payout_instructions`: neto, destino verificado, estado, idempotencia e identificador externo.
- `disputes`, `dispute_evidence`, `dispute_decisions`: expediente y resolución inmutables.
- `financial_audit_events`: eventos de seguridad y operación sin datos bancarios sensibles completos.

Las políticas RLS permiten a cada participante leer únicamente sus proyecciones. Las tablas de reconciliación, ledger, decisiones y payouts no admiten escritura de `anon` ni `authenticated`; las mutaciones pasan por RPC/Edge con autoridad de servicio y controles de actor.

## 9. Comandos autoritativos

Los siguientes contratos se implementan como funciones transaccionales versionadas o Edge Functions que llamen una única transacción de base de datos:

- `create_funding_intent_v1(order_id, amount_minor, currency, idempotency_key)`
- `record_external_payment_event_v1(signed_event, idempotency_key)` — solo adaptador/servicio.
- `reconcile_funding_v1(funding_intent_id, external_event_id, idempotency_key)` — automático firmado o administrador autorizado.
- `reserve_order_funds_v1(order_id, contract_version, idempotency_key)`
- `submit_completion_proof_v1(order_id, evidence_manifest_hash, idempotency_key)`
- `declare_service_step_v1(order_id, title, sequence, expected_version, idempotency_key)`
- `request_step_media_upload_v1(step_id, phase, media_metadata, idempotency_key)`
- `finalize_step_media_upload_v1(upload_id, client_sha256, idempotency_key)`
- `complete_service_step_v1(step_id, before_manifest_sha256, after_manifest_sha256, expected_version, idempotency_key)`
- `request_evidence_exception_v1(step_id, reason_code, substitute_manifest_sha256, idempotency_key)`
- `accept_service_result_v1(order_id, expected_version, idempotency_key)`
- `open_service_dispute_v1(order_id, reason_code, evidence_manifest_hash, idempotency_key)`
- `decide_service_settlement_v1(order_id, decision, rationale, expected_version, idempotency_key)` — autoridad administrativa.
- `settle_service_v1(order_id, expected_version, idempotency_key)`
- `create_provider_payout_v1(settlement_id, payout_account_id, idempotency_key)`
- `record_payout_result_v1(payout_id, signed_external_result, idempotency_key)` — solo adaptador/servicio.
- `refund_service_v1(order_id, resolution_id, idempotency_key)`

Cada comando valida `auth.uid()`, rol, participación, identidad, versión optimista, estado anterior, política y evidencia. Las funciones `SECURITY DEFINER` fijan `search_path`, revocan ejecución pública y no aceptan identificadores de actor sustitutos cuando pueden obtenerse de la sesión.

## 10. Integración SINPE

La interfaz `PaymentRailAdapter` separa el dominio del proveedor bancario:

```text
createFundingReference()
verifyInboundEvent()
queryReconciliationStatus()
createPayoutInstruction()
verifyPayoutResult()
```

Requisitos:

- referencias aleatorias, de un solo uso, con expiración y checksum;
- eventos entrantes firmados o conciliados por archivos/API autenticados;
- deduplicación por identificador externo y hash;
- tolerancia a importes incorrectos, pagos fragmentados, pagos duplicados y referencia ausente;
- ninguna captura de pantalla acredita fondos;
- secretos únicamente en un gestor de secretos del backend;
- payout a un destino previamente verificado, nunca a un número escrito en el momento de retirar;
- reintentos idempotentes y conciliación independiente posterior;
- interruptor de emergencia para detener nuevas financiaciones o payouts sin alterar saldos.

Mientras no exista API bancaria, el piloto puede crear referencias y una bandeja de conciliación de cuatro ojos: una persona propone el match y otra lo aprueba para montos/riesgos configurados. El ledger solo se publica tras la aprobación.

## 11. Experiencia Android

### 11.1 Cliente

- Cotización muestra precio total, quién presta el servicio y que MEET retendrá 5% del pago al proveedor; el cliente no paga un recargo oculto.
- Pantalla de pago genera referencia e instrucciones SINPE, importe exacto y vencimiento.
- Estado `Verificando transferencia` hasta conciliación real.
- Línea de tiempo: financiado, reservado, trabajo iniciado, evidencia recibida, revisión, liquidación y cierre.
- Línea de tiempo visual por pasos con comparación lado a lado `Antes`/`Después`, descripción y estado de revisión.
- Revisión final compara contrato, entregables y evidencia; ofrece `Aceptar resultado` y `Reportar problema` con consecuencias claras.
- El saldo se presenta por buckets y origen; nunca como un único número ambiguo.

### 11.2 Proveedor

- Antes de ofertar acepta el contrato de comisión del 5%.
- Antes de iniciar, ve el checklist de pasos y captura el `Antes`; al terminar cada uno, captura el `Después` desde el mismo flujo.
- La cámara guía encuadre, enfoque y repetición sin afirmar autenticidad que el dispositivo no pueda probar.
- Cada oferta muestra bruto, comisión estimada y neto estimado.
- Tras liquidación muestra bruto capturado, comisión exacta y neto pagable.
- El retiro requiere identidad y destino verificados.
- El proveedor ve por qué una orden está retenida y qué evidencia falta, pero no puede alterar la decisión financiera.

### 11.3 Propietario/operaciones

- Centro de control con fondos custodiados, obligaciones, comisión devengada, ingresos realizados, payouts, reembolsos, conciliaciones y diferencias.
- Cola priorizada por riesgo, monto, antigüedad y evidencia incompleta.
- Vista de expediente con contrato, participantes, eventos, evidencia, ledger y conciliación.
- Acciones de aprobar/rechazar requieren motivo; las sensibles exigen autenticación reforzada y, según umbral, segundo aprobador.
- No se muestran VIN, teléfonos, cuentas o documentos completos si no son necesarios.

## 12. Riesgo, fraude y privacidad

- Verificación de propiedad del teléfono/IBAN de payout.
- Identidad del proveedor vinculada al principal activo y a un perfil de proveedor aprobado; no se admiten IDs locales genéricos.
- Límites por usuario, proveedor, dispositivo, día, orden y etapa de verificación.
- Detección de referencias reutilizadas, pagos circulares, cuentas compartidas, cambios recientes de payout, múltiples cuentas/dispositivos y patrones de disputas.
- Retención reforzada tras cambio de destino de payout.
- Hashes de manifiestos de evidencia; medios privados con URLs firmadas y expiración.
- Logs sin secretos, SINPE completo, documentos o información personal innecesaria.
- Exportación de expediente y trazabilidad para auditoría, soporte y conciliación.

## 13. Manejo de fallos

- **Pago sin referencia:** queda no asignado en conciliación; no acredita automáticamente al usuario equivocado.
- **Importe menor/mayor:** permanece pendiente hasta completar, devolver o resolver administrativamente.
- **Evento duplicado:** devuelve el resultado previo sin nuevos asientos.
- **Backend caído después del banco:** la reconciliación recupera el evento; no depende del teléfono del cliente.
- **Payout incierto:** permanece `PAYOUT_PENDING/UNKNOWN`; no se reenvía hasta consultar al proveedor externo.
- **Cliente y proveedor discrepan:** fondos congelados, expediente de disputa y resolución compensatoria.
- **Proveedor no entrega:** cancelación conforme al contrato y reembolso desde la reserva.
- **Cliente no responde:** revisión por política y riesgo; no apropiación automática del dinero.
- **Diferencia contable:** detener payouts, alertar y exigir conciliación; nunca “arreglar” editando saldos.
- **Proveedor de pagos no disponible:** se bloquean operaciones nuevas; las existentes conservan estado y pueden reconciliarse después.

## 14. Observabilidad y conciliación

Métricas mínimas:

- volumen bruto financiado y liquidado por vertical/moneda;
- comisión devengada y realizada;
- fondos conciliados, reservados y sin asignar;
- tiempo de conciliación, aceptación y payout;
- tasa de disputas, reembolsos y fallos de payout;
- diferencias entre saldo bancario/custodio y obligaciones del ledger;
- comandos duplicados, conflictos de versión y rechazos de autorización.

Un proceso diario produce una prueba de conciliación firmada con saldos iniciales, entradas, salidas, saldos finales, obligaciones, diferencias y actor/versión del proceso.

## 15. Estrategia de entrega

### Fase 0 — Contrato y guardas

- Unificar tipos monetarios duplicados y eliminar `Double` de Servicios Elysium.
- Incorporar política universal de 500 bps y pruebas de paridad.
- Añadir feature flags fail-closed para financiación, custodia y payouts.

### Fase 1 — Ledger y contratos

- Migraciones aditivas, RLS, ledger de doble entrada y RPCs de contrato/reserva.
- Adaptadores de reparación, grúa, viajes, repuestos, inspección y universal al kernel común.

### Fase 2 — Evidencia y satisfacción

- Manifiestos de evidencia por vertical, revisión del cliente, disputas y consola del propietario.
- Captura visual antes/después por cada paso no-viaje, almacenamiento privado, hashes y excepciones controladas.

### Fase 3 — SINPE controlado

- Referencias, conciliación empresarial, destinos verificados y payouts con controles operativos.
- La activación depende de completar las puertas regulatorias y bancarias de la sección 3.2.

### Fase 4 — Custodia automatizada

- Adaptador productivo con eventos firmados, conciliación continua y payouts automatizados.
- Pruebas en sandbox, simulación de fallos y verificación financiera externa antes del rollout general.

Cada fase es aditiva. Ninguna sustituye las máquinas de reparación, viajes o marketplace ya existentes.

## 16. Verificación obligatoria

### Unidad y propiedades

- 5% exacto y redondeo en límites, cero, montos grandes y overflow.
- `gross = commission + provider_net` para todo monto generado.
- sumatoria cero del ledger por transacción y moneda.
- ninguna secuencia válida produce saldo negativo o doble liquidación.

### Base de datos

- concurrencia al reservar, aceptar, disputar, liquidar, reembolsar y pagar.
- reintentos con la misma idempotencia y rechazo con payload distinto.
- RLS: clientes/proveedores no escriben ledger, reconciliación, decisiones o payouts.
- `SECURITY DEFINER`, `search_path`, privilegios y actor derivados de sesión.
- migración hacia adelante y rollback operativo sin borrar asientos.

### Integración

- evento SINPE duplicado, tardío, parcial, excedente, sin referencia y con firma inválida.
- caída entre llamada externa y commit local.
- payout `UNKNOWN`, timeout y posterior confirmación.
- disputa durante aceptación y liquidación concurrentes.

### Android

- UI nunca convierte montos mediante `Double`.
- todos los servicios no-viaje bloquean la finalización si falta `BEFORE` o `AFTER` en cualquier paso obligatorio.
- viajes permanecen fuera del requisito visual sin perder sus verificaciones propias.
- carga interrumpida, reintento, imagen duplicada, hash divergente y corrección versionada no pierden ni sustituyen evidencia publicada.
- proceso recreado, offline y reintentos no duplican comandos.
- estado local obsoleto no muestra fondos disponibles ni permite aceptar dos veces.
- instalación, lanzamiento, navegación, proceso en primer plano y ausencia de crash en Android físico.

### Gates del repositorio

- pruebas unitarias Android y web;
- lint y compilación de instrumentación;
- paridad TS/Kotlin para contratos monetarios byte-exactos;
- guardas de producción y secretos;
- migraciones Supabase verificadas en base limpia y actualización;
- APK release firmada y sin credenciales de depuración;
- comprobación de CI remoto y artefacto publicado antes de declarar producción.

## 17. Criterios de aceptación

El sistema está listo para producción únicamente cuando:

1. Una orden real puede financiarse, reservarse, ejecutarse, aceptarse, liquidarse y pagarse sin edición manual de base de datos.
2. Repetir cualquier comando no duplica dinero.
3. El ledger reconstruye todos los saldos y concilia exactamente con el custodio.
4. MEET reconoce exactamente 5% y el proveedor recibe exactamente 95% del monto liquidado, salvo líneas excluidas explícitamente.
5. Una disputa congela fondos y solo una resolución autorizada los mueve.
6. Ningún cliente, proveedor o APK puede acreditar pagos o declarar payouts.
7. Cada vertical exige su evidencia real antes de ser elegible para liberación.
8. Las puertas jurídicas, empresariales, bancarias, fiscales y de identidad están documentadas como cumplidas.
9. Los tests financieros, de concurrencia, seguridad, CI, APK y Android físico están verdes con evidencia conservada.
10. Toda orden no-viaje conserva un par `BEFORE`/`AFTER` verificable por cada paso obligatorio, mientras `RIDE_SERVICE` permanece explícitamente fuera de ese requisito visual.

## 18. Fuentes regulatorias de diseño

- Banco Central de Costa Rica, *Reglamento del Sistema de Pagos*, edición vigente desde 2025-12-12.
- Banco Central de Costa Rica, normas de servicios SINPE para entidades y personas publicadas en 2026.
- Procuraduría General de la República, dictamen sobre proveedores de servicios de pago y administración de recursos de terceros.
- Ministerio de Economía, Industria y Comercio, criterios de comercio electrónico, información al consumidor, pagos y reclamaciones.

Esta especificación convierte esas fuentes en controles técnicos prudentes; no sustituye el dictamen jurídico, fiscal, bancario o de cumplimiento necesario para activar custodia productiva.
