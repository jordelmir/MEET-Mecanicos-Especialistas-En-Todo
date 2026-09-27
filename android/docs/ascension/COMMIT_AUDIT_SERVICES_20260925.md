# Auditoría APK: Services Marketplace, cierre y hubs del 25 septiembre

Baseline verificado: `e4c379ddb00b275e390908232b8a70f83b0a0f81`. Commits asignados: `24ec6e38`, `246c3f55`, `e4c379dd`. Se contrastaron sus cambios Android con el checkout actual y los caminos de autoridad relacionados. El trabajo concurrente sigue modificando WIP: las referencias de línea corresponden a la inspección de este documento, no a una versión publicada.

Scope: APK. No cambios funcionales, compilaciones, instalaciones ni escrituras web. La especificación adjunta se utilizó como comparativa de verdad/evidencia, no como autorización para implementar todas sus fases. Las migraciones Supabase se leyeron únicamente para contrastar el contrato que llama Android; no se desplegaron.

Veredicto: **BLOCK para declarar cierre financiero/forense y operación multiusuario reales**. Hallazgos activos: **P0: 3; P1: 6**. Hay además un defecto P1 de baseline Safety con corrección de fuente WIP, sin prueba de despliegue. No se afirma auditoría E2E ni prueba de dispositivo.

## Trazabilidad observada

- Home/UniversalServices -> `elysium_services` -> `ElysiumServicesMarketplaceScreen` -> `ObdViewModel.serviceRequests` -> `MarketplaceDao.getRequests()` -> Room sin owner.
- Publicar -> `createServiceRequest()` -> `insertRequest(OPEN)` local -> intento directo de INSERT PostgREST `service_requests`; los errores sólo se registran. No receipt/proyección autorizada ni outbox CREATE en este camino.
- Ofertar -> `placeServiceBid()` -> upsert local -> INSERT directo `service_bids`. La UI anuncia envío sin observar éxito remoto.
- Aceptar -> `acceptBid()` -> `acceptBidAtomically()` -> Room `ACCEPTED/HELD` + evento/outbox -> UPDATE PostgREST directo. No RPC de transición autorizada ni serverVersion.
- Cerrar -> callback de calificación -> `completeMechanicRequest()` -> Room `COMPLETED/RELEASED` + ledger/outbox, luego UPDATE remoto. La pantalla fabrica y muestra certificado antes de obtener resultado.
- Perfil -> `registerAsProvider()` -> Room; actualización de perfil existente retorna tras guardar localmente. El catálogo no tiene una respuesta durable/remota observable por su botón Guardar.
- Safety conserva una arquitectura diferente y mejor: formulario -> VM -> outbox cifrado -> worker -> gateway RPC -> receipt/version -> proyección. El nuevo enum institucional de los commits no estaba aceptado por RPC v2; WIP añade v3.

## P0 activos

### S01 — Certificación y desembolso se anuncian sin ejecución financiera ni resultado de cierre

**Proveniencia:** `246c3f55`, permanece en WIP. `ElysiumServicesMarketplaceScreen.kt:832-887`; `ObdViewModel.kt:1886-1902`; `FeatureDaos.kt:147,257-284`.

Al confirmar estrellas, la UI llama una función Unit que lanza otra coroutine y, sin esperar, genera un hash local, inserta evento REPAIR_COMPLETED, gana XP y muestra `FONDOS LIBERADOS (RELEASED)`. El DAO puede rechazar un servicio no ACCEPTED; la red puede fallar. Ninguna de esas condiciones evita certificado y toast. `RELEASED` también se escribe localmente en SQL sin receipt de pago. `paymentId` no se valida en este cierre.

**Impacto:** historia del vehículo y certificado representan reparación/pago no demostrado. Repetir el callback puede generar múltiples reportes distintos aunque el cierre DAO sea idempotente.

**Corrección mínima requerida:** callback consume resultado tipado de comando durable; historia, XP y certificado se construyen exclusivamente desde proyección del cierre autorizado y recibo financiero. Si el servicio es efectivo/SINPE pendiente, mostrar ese estado; hash local no prueba release.

### S02 — Solicitudes privadas de servicios carecen de aislamiento de principal en Room y UI

**Proveniencia:** `24ec6e38`, ampliado por `e4c379dd`; estructura heredada, explotada por nueva pantalla. `FeatureEntities.kt:114-138`; `FeatureDaos.kt:73`; `ObdViewModel.kt:1354,2752-2768`; `ElysiumServicesMarketplaceScreen.kt:118,600,672`; `ActiveAndCompletedServicesHubScreen.kt:94,540-541,1315-1316`.

La entidad ServiceRequest no tiene requester/owner principal. DAO selecciona todas las filas; el listado titulado TUS SOLICITUDES filtra sólo estado. Cambiar cliente/especialista es un booleano visual. No aparece revalidación de pertenencia en acceptBid/completeMechanicRequest/cancelServiceRequest. El cloud poll select no especifica owner, aunque RLS remoto podría filtrar en servidor: no elimina registros de cuentas anteriores que ya existen localmente.

**Impacto demostrado por fuente:** cliente B en el mismo almacenamiento puede ver registros de A y activar callbacks locales sobre sus servicios. No se afirma que RLS permita mutación remota, porque no se verificó producción.

**Corrección requerida:** principal inmutable por fila, consultas por principal/rol, migración fail-closed de registros sin dueño y rechazo de comandos con actor ajeno; limpiar/cambiar proyección al cerrar sesión. Probar dos cuentas en mismo APK y dos dispositivos.

### S03 — WIP confirma saldo SINPE por texto local y cobra al ofertar

**Proveniencia:** **WIP, no atribuir a los tres commits**. `ElysiumServicesMarketplaceScreen.kt:703-728,749-820,3064-3240`; `ProviderServiceOffering.kt:153-239`; `ObdViewModel.kt:2466-2478`.

`onConfirmTopUp` acepta monto/ref, llama `withTopUp`, actualiza specialties JSON y anuncia recarga CONFIRMADA/saldo disponible sin verificación bancaria. Hay bono default de ₡5000 y balance/commissionBps client-editable. Además la deducción ocurre antes de `placeServiceBid`: una oferta rechazada o fallo de red puede consumir comisión. Actualizaciones async de un snapshot pueden perder deducciones o reescribir balances; no existe transición bancaria atómica en ese camino.

**Impacto:** saldo ficticio o alterable, referencias reusables y cargos sobre servicios no adjudicados. Es un bloqueo nuevo, no una reparación del escrow anterior.

**Corrección requerida:** recarga como intento pendiente y conciliación server-authoritative; saldo desde ledger/proyección; comisión atómica/idempotente en adjudicación según contrato financiero. No usar specialties como autoridad monetaria.

## P1 activos

### S04 — Seguimiento/ETA/SOS se presenta como real con proveedor inventado

**Proveniencia:** `246c3f55`; parte ETA ya en `24ec6e38`. `ElysiumServicesMarketplaceScreen.kt:1147-1158,2071-2104,2141-2150,899-911`; `LayaRouteEngine.kt:57-82,112`; `AuraSentinelNotificationCoordinator.kt:121-122`.

En modo cliente el especialista se ubica en `clientLat + 0.016`, `clientLon + 0.014`. Esa posición se marca PROVIDER_LIVE, produce ETA, tráfico y notificaciones de llegada. LayaRouteEngine multiplica Haversine y genera waypoints sintéticos; el tráfico deriva de hora local. Seleccionar refugio dispara aviso de baliza activa/registro anónimo aunque sólo asigna campos de formulario y muestra notificación local.

**Corrección requerida:** eliminar proveedor-live y llegada cuando no existe captura real/versionada; separar estimación ilustrativa con su provenance; SOS sólo confirmado desde receipt. Un refugio encontrado no equivale a baliza ni ruta trazada.

### S05 — El PIN de servicio es derivable y no valida inicio/cierre

**Proveniencia:** `24ec6e38`, propagado por `246c3f55`. `ElysiumServicesMarketplaceScreen.kt:1257-1267,2097-2102,2372`; `AuraSentinelNotificationCoordinator.kt:42,81`.

PIN = últimos cuatro caracteres requestId, no necesariamente dígitos. Se muestra a ambos modos, en un mapa alimentado por todas las solicitudes y en lockscreen pública. No existe callback de entrada/verify service PIN ni requisito PIN verified para completar. El botón de cierre usa sólo ACCEPTED.

**Corrección requerida:** challenge aleatorio servidor, visibility por rol, RPC con intentos/expiración y proyección de verificación antes de ejecución. Mientras falte, no venderlo como protección activa.

### S06 — Historial reconstruye certificados distintos con calificación inventada

**Proveniencia:** `e4c379dd`, activo en WIP. `ActiveAndCompletedServicesHubScreen.kt:1524-1540,1621-1636,1705-1720`.

Certificado de servicio al cerrar usa rep_svc + timestamp y hash de seis datos; historial usa cert_svc + ocho caracteres y hash requestId|priceOffer|COMPLETED. No carga el reporte firmado original. Fija cinco estrellas para servicio técnico y default cinco para otros. Los QR del historial tienen cinco campos, sin verifier_url y con requestId en lugar de vehicle_id; no hay imagen QR escaneable en el dialog compartido. Viajes afirma TARIFA LIQUIDADA simplemente por COMPLETED.

**Corrección requerida:** referencia persistente al reporte/versionado original, receipt financiero y rating capturado; QR canonical de seis campos desde artefacto real. Sin artefacto: Dato no capturado/Pendiente.

### S07 — Calificación bidireccional no persiste review ni ratings de ambos roles

**Proveniencia:** `24ec6e38`, `246c3f55`. `ElysiumServicesMarketplaceScreen.kt:831-884,1627-1725`; `FeatureEntities.kt:114-138`.

`reviewText` se entrega al callback pero nunca se almacena. Stars/tags quedan en un objeto dialog y evento timeline, sin rating de servicio por actor/contraparte ni RPC. Specialist sólo tiene un toggle; no hay rating recíproco independiente. Al reabrir historial aparecen cinco estrellas sintéticas.

**Corrección requerida:** registro de rating por serviceId + actorRole, autoridad y unicidad; conservar texto/tags y leer proyección. No describir el dialog como reputación bidireccional implementada.

### S08 — Configuración proveedor puede guardar defaults sobre datos reales

**Proveniencia:** `e4c379dd`, activo. `ProviderServiceCatalogConfigScreen.kt:54-93,122-156`; `ObdViewModel.kt:2299-2339`.

`profiles` empieza vacío; `existingProfile` cambia después pero selectedCategory/profileData/campos se inicializan en remember sin clave ni reconciliación de carga. Abrir frío puede mostrar plantilla mecánico en vez del perfil real. Guardar siempre registra SERVICE_PROVIDER aunque el perfil seleccionado sea otra clase; anuncia guardado y navega atrás antes del resultado. En rama existing del VM se guarda Room y retorna sin actualizar contrato remoto del catálogo.

**Corrección requerida:** cargar explícitamente profileId y estado Loading/Ready, conservar drafts por ID con protección de ediciones humanas, guardar perfil correcto y navegar sólo después de resultado durable. No fabricar licencia `MEET-PRO-CR`.

### S09 — WIP outbox durable no admite operaciones que emite Marketplace

**Proveniencia:** cruce entre productor heredado utilizado por commits y dispatcher **WIP**. `FeatureDaos.kt:422-469` produce operation UPSERT_VANGUARD_EVENT. `VanguardOutboxDispatcher.kt:88-92` exige operación en SUPPORTED_TOPICS; mapa únicamente certified_reports.upsert.

Acceptance/completion registran eventos y encolan trabajador pero su operación no se puede entregar por el dispatcher actual. Resultado: FAILED y finalmente DEAD_LETTER, mientras la UI ya muestra transición local/escrow. UPDATE directo es best-effort y no sustituye retransmisión durable.

**Corrección requerida:** preservar consumidor de eventos legado o implementar dispatcher con topic/operation compatible y payload real; separar event publication de command authority. Prueba contractual producer->worker con aceptación/cierre reales; no añadir noop success.

## Defecto de baseline parcialmente corregido por WIP

`246c3f55` añadió SourceRelation.INSTITUTIONAL (`SafetyDomain.kt:50`) y formulario lo serializa. Baseline gateway llamaba safety_create_report_v2; migration `20260919160821_safety_authority_integrity.sql:69-76` rechazaba INSTITUTIONAL con INVALID_SOURCE_RELATION. **P1 baseline**.

WIP observado cambia gateway a safety_create_report_v3 (`SupabaseSafetyCommandGateway.kt:78`) y `20260926181000_safety_report_provenance_v3.sql:87-94` admite enum. Clasificar **fuente corregida parcialmente**, no producción corregida: migración/gateway nuevos aún requieren despliegue compatible y receipt de reporte institucional. No contar como hallazgo activo adicional duplicado.

## Qué WIP sí mejora y qué no demuestra

- Marketplace/hub reorganizan filas Radar/Waze/contraoferta y comisión para pantallas pequeñas; mejora de layout, no autoridad.
- PIN comercio comprueba estado y código en Room; no resuelve PIN del servicio ni receipt financiero de delivery.
- Safety adopta v3 con procedencia; conserva source counts desde proyecciones, una base válida.
- Dispatcher Vanguard ahora exige upsert remoto antes de marcar delivered para certified_reports: mejora real, pero incompatible con eventos servicios arriba.
- Defaults/persistencia/theme son cambios independientes y se preservaron. No se revirtió ninguno de los 133 dirty declarados.

## Pruebas y evidencias

LayaSpatialSearchEngineTest cubre heurísticas/gazetteer, no operación Marketplace. MarketplaceCloudSyncPolicyTest prueba apagar polling legacy si no existe schema; ese comportamiento no prueba que publicar solicitud funcione entre cuentas. DAO transactions hacen claim/completion localmente idempotentes: positivo, pero no son server authority. No se ejecutaron tests ni builds por instrucción explícita.

Para cerrar estos commits como funcionales se requiere: dos cuentas, publicación offline/reintento, aceptación concurrente remota, PIN verificado, cierre rechazado sin certificado, report hash original al reabrir/proceso muerto, pago pendiente vs conciliado, y perfil real después de carga fría. Todo con APK/source SHA y pruebas separadas de claims locales. Este documento no concede producción verificada ni publicación remota.
