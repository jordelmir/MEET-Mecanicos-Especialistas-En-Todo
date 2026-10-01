# Primer release — recorridos y lógica de negocio del APK

Revisión de código local basada en HEAD e4c379ddb00b275e390908232b8a70f83b0a0f81 y WIP preservado. Sólo Android y contratos de servidor usados por Android. No afirmar revisión física completa, mercado mundial activo ni release listo a partir de compilar. Los tres informes de commits adjuntos a esta carpeta contienen trazas concretas y defectos de baseline; este documento registra dirección de convergencia y criterio de aceptación.

## Veredicto actual

### Actualización contra main, HEAD y servidor activo

Después de fetch el 26 de septiembre, HEAD y origin/main son e4c379ddb00b275e390908232b8a70f83b0a0f81. El release v4.27.0 corresponde a 70c20438; los avances locales sin commit son una tercera capa que debe inspeccionarse y probarse por separado. No derivar avance ni funcionalidad de la fecha del tag.

Metadatos del servidor MEET verificados en esta ronda: existen universal_service_requests y universal_service_offers; no aparecen las tablas legacy service_requests/service_bids ni tablas commerce. placeServiceBid todavía intenta insertar en service_bids y primero persiste una oferta Room: no demuestra publicación en internet. No hay migraciones registradas >=20260925000000. Esto requiere reconciliar los contratos reales, no agregar éxito local.

El servidor mantiene las funciones de revisión manual ride_owner_wallet_topup_queue_v1, ride_owner_decide_wallet_topup_v1 y meet_is_platform_owner. El formulario de recarga de servicios se reconecta a submitWalletTopup con comprobante privado y referencia; devuelve pendiente después del recibo remoto y no modifica saldo JSON ni envía automáticamente una oferta. La billetera de servicios local y el ledger de viajes aún requieren convergencia antes de cobrar comisiones de servicios.

P0 confirmado en pg_proc: ride_driver_wallet_credit_v1 admite authenticated y no verifica aprobación. Corrección mínima preparada en 20260926183000_wallet_credit_helper_service_role_only.sql, probada localmente y pendiente de autorización para servidor activo.

Evidencia nueva: 17 pruebas unitarias seleccionadas, 3 instrumentadas de comunicaciones en Honor y paridad TS/Kotlin de hashes/precios PASS. El APK instalado con esta evidencia es debug; el release firmado con R8 se compila aparte. No atribuir al APK instalado los cambios de recarga, mensajes de emergencia o reglas R8 añadidos después.

Destino confirmado por el dueño: Google Play, prueba interna, Costa Rica primero. Mantener saldo y SINPE con revisión manual del dueño; no sustituir el proveedor de cobro. Alta de otro mercado requiere evidencia y configuración. El saldo debe provenir del ledger remoto después de aprobación, nunca de pulsar Confirmar en el APK. La compra de capacidades digitales en Play requiere revisar Play Billing; la excepción de servicios físicos no convierte automáticamente agentes premium en servicios físicos.

**NO-GO para release público completo mientras permanezcan P0 de comercio/servicios y faltan pruebas de dos cuentas/dispositivos/proveedores financieros.** La intención es producto mundial; la cobertura operacional debe activarse con configuración y evidencia por mercado. No eliminar funcionalidades ya integradas: conservar borradores, historial y navegación, cerrar autoridad y presentar estados pendientes reales.

## Comparación funcional

| Recorrido | Modelo correcto | Lo que exige MEET y evidencia pendiente |
|---|---|---|
| Alta/acceso | Cuenta verificada, recuperación y sesión aislada | Principal autenticado distinto de ID del teléfono. Cambio de cuenta limpia autorizaciones y radios; caché etiquetada por dueño. No inferir país ni publicar PII. |
| Garage → OBD | Vehículo elegido → conexión intencional → datos medidos | Conexión sólo tras intención, Cancel interrumpe, desconexión invalida mediciones. VIN/OEM para compatibilidad; prueba vehículo/ELM todavía necesaria. |
| DTC → guía → servicio | Hallazgo observado y aplicabilidad → explicación → acción | Separar hipótesis de diagnóstico físico; guía requiere vehículo/evidencia. No inventar valores cuando OBD no está disponible. |
| Reporte certificado → historial | Snapshot inmutable → firma/hash → QR/verificación → versión nueva | QR minimal sin VIN/teléfono/placa. Hash original persistido, no reconstrucción de estrellas/fechas al reabrir. Paridad y proceso muerto deben pasar. |
| Viaje pasajero → conductor | Dirección confirmada → ruta → precio/condiciones → solicitud → match → PIN → viaje → cierre | Cotización distingue estimación de precio comprometido. Estado/versión originados en RPC; incertidumbre de red conserva outbox. Esta ronda retira cancelación local/ACK por timeout y versión inventada. Faltan dos cuentas y ELM/vehículo para journeys que lo requieran. |
| Cancelación | Solicitud → política vigente → recibo → proyección | Nunca prometer ausencia de cargo global ni cierre sólo por botón. El cargo depende de mercado/estado, debe ser mostrado antes de confirmar cuando aplicable. No esconder un viaje potencialmente vivo después de timeout. |
| Servicios/proveedores | Publicación → oferta → aceptación → ejecución → evidencia → cierre/pago → rating bilateral | Código antiguo todavía tiene aceptación/escrow/cierre locales y proyección no aislada por principal. Catálogo editado ahora preserva profileId/tipo real y espera persistencia local; no finge sincronización remota. Bloqueo P0 abierto. |
| Comercio/entrega | Precio server-owned → autorización de pago → misión con actor → PIN verificado → entrega → liquidación/reembolso | Entrega no equivale a pago. No ESCROW_HELD por seleccionar SINPE; no RELEASED/REFUNDED desde Room. RPC fuente necesita actor binding/FSM/PIN privado/idempotencia. Bloqueo P0 abierto. |
| Mensajes/QR | Identidad → aceptación/QR → conversación → cifrado → cola → recibo servidor → recepción distinta | Implementados transport, RPC, worker, QR cámara y contacto. SQL preparado y probado local; pendiente despliegue y conversación real entre dos cuentas. Históricos sin clave son explícitamente no descifrables. |
| Cercanía/Mesh | Descubrir → comparar código → aceptar → paquete firmado → recepción durable → ACK firmado | Nearby directo integrado; no afirmar relay multisalto/store-carry-forward completo. Caché de claves previamente autorizadas y límite 24h; reconcilia online con mismo eventId. Prueba de dos teléfonos pendiente. |
| Safety/Civilis | Declaración privada → evidencia → custodia → contraste → publicación autoritativa → contraprueba/rectificación | Procedencia/demografía conservadas y storage reforzado. Pin exacto sigue privado. Fuente institucional no prueba respuesta institucional. Original remoto privado STANDARD, no E2EE para fuentes de alto riesgo. Investigación/temporal/dependencia de fuentes sigue expansión pendiente. |
| PRO/agentes | Oferta clara → compra proveedor → verificación backend → entitlement → refund/revocación | Compra Pending no desbloquea. El gateway de compra real requiere integración/cuenta de tienda; no convertir token local en propiedad. Avatar/animación no conceden autorización. |
| EVAIR | Intención → control/campo real → política → dominio → evidencia | Registro semántico y callbacks existentes se preservan. Contexto real reemplaza user_active; un plan se anuncia no ejecutado. Falta convergencia operacional de capacidades materiales; no se crea un ejecutor paralelo ficticio. |

## Qué tomar de modelos existentes

Uber muestra una estimación/precio antes de solicitar, basado en recorrido y cargos aplicables. Su política de cancelación distingue aceptación/espera y ubicación; no trasladar porcentajes ni minutos de otro mercado como regla mundial. [Precio anticipado](https://www.uber.com/us/en/ride/how-it-works/upfront-pricing/), [cancelación](https://help.uber.com/riders/article/am-i-charged-for-cancelling-a-trip-?nodeId=5f6415dc-dfdb-4d64-927a-66bb06bc4f82).

Stripe Connect separa el cargo al cliente de transferencias a proveedores; un reembolso y la reversión de transferencia son operaciones que requieren reconciliación propia. Es un patrón de autoridad, no una recomendación de disponibilidad para cualquier país ni una afirmación de cuenta Stripe configurada. [Flujo móvil de cargos/transferencias](https://docs.stripe.com/connect/separate-charges-and-transfers?integration=mobile&platform=android).

Google Play exige verificar la compra en backend y no concede entitlement al estado PENDING. Vincular token/cuenta y procesar revocaciones; el APK no certifica su propia compra. [Seguridad de Play Billing](https://developer.android.com/google/play/billing/security).

Los buckets privados requieren autorización de acceso; privacidad de storage no significa cifrado de extremo a extremo. Límites de upload deben estar en servidor antes de registro, además de la validación Android. [Buckets Supabase](https://supabase.com/docs/guides/storage/buckets/fundamentals), [control de acceso](https://supabase.com/docs/guides/storage/security/access-control).

## Contrato mundial

- Clave de mercado explícita (país/región/ciudad), moneda ISO, importes minor enteros, locale y zona horaria; alias de casa/destino pertenecen al usuario, no coordenadas prefijadas.
- Activación por capacidades realmente disponibles: routing/geocoding, despacho, proveedores, pago, soporte y canales institucionales. La ausencia de cobertura devuelve indisponible, nunca San José ficticio.
- Separar precios estimados, ofertas negociadas y cargos confirmados. Rutas geodésicas estimadas no son calles; GPS sin señal no produce una ubicación de proveedor.
- Un recibo por operación idempotente, una autoridad de estado por dominio, roles explícitos y ledger de dinero server-owned. Reintento/reinicio/cambio de cuenta no multiplica cobros.
- No afirmar servicio de emergencia, protección absoluta o respuesta oficial a partir de mensaje/animación/caso creado.

## Gate de salida

Por recorrido habilitado: cliente/proveedor independientes; solicitud doble y carrera; respuesta perdida después de commit; offline/proceso muerto; account switch; bloqueo/revocación; cancelación; importe/divisa/cargo; historial; soporte. Conservar logs sin PII y manifest source/hash/APK/Room/migrations. Tests unitarios, fixtures SQL, Android instrumental, hardware físico y verificación backend son evidencias distintas.

Release firmado sólo después de cerrar defectos P0 habilitados y pasar journeys reales. Preparar canal elegido por el dueño (Google Play prueba interna o APK GitHub firmado), changelog preciso y rollback compatible. Nunca llamar 100% a la mera presencia de pantallas o al estado compilado.
