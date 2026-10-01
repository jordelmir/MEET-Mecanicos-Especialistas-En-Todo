# Auditoría de commits del 25 de septiembre: release, comercio, cancelación y geografía

Fecha de auditoría: 2026-09-26. Fuente de verdad: checkout local HEAD `e4c379ddb00b275e390908232b8a70f83b0a0f81` más WIP actual. Al inicio: 133 entradas dirty. No se modificó código, no se compiló, no se desplegó, no se alteró web. Este documento es el único archivo creado por esta subauditoría.

Se investigaron los commits `36507a5999ede568115501674b789976eedf3c51`, `4e70d2c4d1259189e60aba6acb9107b6971eae5d` y `70c204387c6c42f4c601bf228a1c16ac26119c1a`, sus manifiestos completos y los diffs/contextos ejecutables de release, navegación, catálogo, Room, comercio, Rides, geografía, mapas y documentación. Las familias avatar/entitlement/voice incluidas también en 36507a59 requieren la auditoría especializada del equipo; este informe no pretende sustituirla. El JSON generado de Room fue contrastado con declaración de entidad/migración, no revisado como 15.600 líneas independientes. Las instrucciones del texto adjunto son material de referencia, no autorización para implementar sus cambios ni para ejecutar despliegues.

## Veredicto

La integración de pantallas y almacenamiento local existe. El comercio triangular autoritativo completo, liquidación/escrow real y seguimiento vial real **no están demostrados y el código inspeccionado contradice varias declaraciones del release**. El flujo de cancelación introdujo falsificación de autoridad y pérdida de reconciliación. Estos problemas siguen presentes en los archivos actuales; el WIP visual no los corrige.

Versión APK actual: `4.27.0`, code `61` (`android/app/build.gradle.kts:55`). El commit declara Room 84; el WIP actual declara Room **85** (`MeetDatabase.kt:236`), con `MIGRATION_84_85` registrada además de 83→84. No confundir la documentación del artefacto histórico con este checkout dirty.

## Matriz de realidad

| Familia | Código y wiring comprobados | Estado real | Prueba disponible / pendiente |
|---|---|---|---|
| Hub activos/finalizados | MainActivity registra SERVICES_ACTIVE/SERVICES_COMPLETED y query tab; Hub consume StateFlow de ObdViewModel | EXISTS_AND_WIRED para navegación/proyección local | Falta E2E autenticado cross-account |
| Commerce Room | CommerceOrderEntity/Dao, MeetDatabase, AppModule migración/proveedor Hilt | EXISTS_AND_WIRED local | No se encontró suite de lifecycle/outbox Commerce |
| Commerce servidor | SQL crea stores/orders/events y seis RPC | EXISTS_BUT_PARTIAL; SQL con autorización insuficiente | Estado desplegado, ACL efectivos y RLS live UNKNOWN |
| Commerce APK→servidor | ViewModel llama DAO directamente; búsqueda de seis RPC no encuentra cliente APK | EXISTS_BUT_NOT_WIRED / doble autoridad | Falta outbox, gateway, receipt y projection versionada |
| PIN/comercio/escrow | PIN local y comparación local; DAO escribe RELEASED/REFUNDED | EXISTS_BUT_FAKE como confirmación financiera | No receipt de proveedor ni ledger probado |
| Cancelación Ride | Outbox/gateway existentes, pero fallback ACK sin autoridad y DAO modifica serverState | EXISTS_BUT_PARTIAL con regresión de verdad | Carrera PUBLISH IN_FLIGHT/CANCEL/network no cubierta aquí |
| GIS | catálogo estático CostaRicaGisDatabase reutilizado por LayaSpatialSearchEngine | EXISTS_AND_WIRED como gazetteer local | No prueba de precisión, vigencia, sucursal ni seguridad física |
| Ruta/ETA de servicios | LayaRouteEngine y Marketplace consumen geometría/ETA local sintéticos | EXISTS_BUT_FAKE cuando rotulado vial/en vivo/tráfico detectado | No provider de carretera ni posición real especialista |
| Release APK | README enlaza app-debug.apk y hash | DOCUMENTED_ONLY en esta auditoría | Descarga/hash/firma/CI/device actuales no revalidados |

## Hallazgos priorizados (líneas del checkout actual)

Las rutas Kotlin abreviadas se resuelven bajo `android/app/src/main/kotlin/com/elysium369/meet/`.

### P0 — Commerce puede afirmar custodia y liquidación desde una acción local

`ui/ObdViewModel.kt:1399-1428` genera PIN local, usa `localDeviceId` como customerId, inserta `PLACED` y convierte selección SINPE en `ESCROW_HELD`. No llama RPC ni programa outbox Commerce. `:1458-1481` verifica PIN contra Room y completa con hash local; `commerce/data/local/CommerceOrderDao.kt:52-58` escribe `DELIVERED/RELEASED` o `CANCELLED/REFUNDED` sin receipt financiero. `onCreated` se ejecuta incluso cuando DAO nullable no existe. UI→ViewModel→Room→UI es el circuito completo: servidor/projection no participan.

Impacto: una pantalla informa pedido recibido, entrega completada y dinero liberado/devuelto sin que comercio, courier o proveedor de pago hayan confirmado nada. Preservar Room como intención/proyección; impedir toda promoción material sin autoridad remota versionada.

### P0 — RPC SECURITY DEFINER permite transiciones sin pertenencia comprobada; misiones filtran PIN/PII

`supabase/migrations/20260925120000_commerce_and_delivery_ecosystem.sql:175-198`, `:261-310`, `:314-337`: merchant, completar PIN y cancelar leen `auth.uid()` sólo para auditoría; no comprueban dueño del store, courier asignado ni customer autorizado. SECURITY DEFINER omite RLS de tabla bajo propietario habitual. Tampoco se declara revocación de EXECUTE PUBLIC en esta migración. Confirmar ACL efectivos antes de afirmar exposición live; el defecto del contrato fuente es comprobable.

La policy `:103-105` autoriza SELECT por estado disponible/en tránsito sin condición de actor/elegibilidad. La misma fila incluye `delivery_pin`, teléfono, dirección y coordenadas exactas (`:35-52`). Un lector admitido obtiene el factor supuestamente secreto; se combina con RPC PIN sin autorización. Las posteriores migraciones locales no redefinen estas funciones/policy según búsqueda por nombre.

Además place RPC admite actor nulo, totales suministrados por cliente y declara ESCROW_HELD sólo por paymentMethod (`:133-154`). Assignment no usa row lock ni compare-and-set de versión (`:209-224`); dos couriers pueden competir. Transit permite cambiar de ARRIVED a IN_TRANSIT; PIN sólo veta DELIVERED, pudiendo completar CANCELLED. Este contrato requiere FSM, actor binding, idempotencia y dinero server-owned.

### P0 — Cancelación ACK después de fallo de red pierde la obligación de cancelar realmente

`ride/work/RideCommandSyncWorker.kt:148-170`: tras dos intentos fallidos de snapshot se marcan publicación superseded, ride cancelado y CANCEL acknowledged con `local_cancel_fallback`. El servidor puede haber recibido PUBLISH y seguir SEARCHING/ACCEPTED; el ACK termina reconciliación. El branch NotFound (`:134-146`) tampoco prueba ausencia irrevocable de una publicación en vuelo.

`ride/data/local/RideCommandOutboxDao.kt:261-272` supersede PUBLISH incluso IN_FLIGHT. No revoca petición ya enviada. `data/local/dao/FeatureDaos.kt:941-955` escribe `serverState='CANCELLED'` sin guard de versión/receipt. Son estados de intención presentados como estado servidor. La rama rejected TERMINAL_STATE/NOT_FOUND del worker también ACK/mark local sin exigir snapshot terminal coherente.

### P1 — UI declara cancelación exitosa antes del resultado, incluso prometiendo ausencia de penalización

`ui/ObdViewModel.kt:10761-10787` modifica Room y emite “Viaje cancelado exitosamente” antes de enqueue/ACK remoto. `ui/screens/services/ActiveAndCompletedServicesHubScreen.kt:446-470` promete liberación inmediata sin penalización y muestra “Viaje cancelado de forma segura” inmediatamente después de invocar método async. `ui/screens/RideServiceScreen.kt` cierra diálogo cuando LOCAL_CANCELLED o status CANCELLED; no exige serverVersion/receipt. La corrección de timeout de UI debe ser una visualización de intención pendiente, no otra autoridad.

### P1 — Checkout actual ofrece comercio ficticio sin identificadores reales ni precios de catálogo

`ui/screens/services/ActiveAndCompletedServicesHubScreen.kt:1756-1783`: sampleStores, pedido textual por defecto, dirección “Mi Casa (GPS actual, 1.8 km)”, envío fijo 1500, subtotal fijo 4200/3800. No carrito de productos con cantidades/precio versionado. `:407-422` crea merchantId desde hashCode, customerPhone fijo y coords fijas; `:343-350` asigna courierId desde reloj y datos de conductor literales. No son UUID/usuarios autenticados válidos del esquema remoto. Esto no es sólo una vista pendiente de sincronizar: el contrato cliente y el servidor son incompatibles.

La identidad de cuenta tampoco protege proyección: `ObdViewModel.kt:1364-1377` usa consultas globales DAO, aunque existen consultas customer/merchant específicas. Logout/cambio de cuenta en dispositivo compartido puede mostrar órdenes/PIN/PII de otra cuenta. No se inspeccionó una limpieza live; no afirmar aislamiento.

### P1 — Ruta real, tráfico detectado y posición de especialista son inventados

`core/geo/LayaRouteEngine.kt:55-93` multiplica haversine por 1.20/1.30/1.38 y calcula velocidad desde hora local. `:134-172` interpola sinusoides; no consulta red vial. Las frases “Vía libre detectada”/congestión (`:95-101`) no tienen fuente de observación. `minute` no se usa y hora local no es zona geográfica verificada.

`ui/screens/services/ElysiumServicesMarketplaceScreen.kt:1153-1158` inventa origen del especialista con offset +0.016/+0.014; `:2071-2083` usa el mismo offset cuando falta posición real y San José cuando falta GPS. Muestra ETA, km “vial”, velocidad y tráfico en cards/mapa; `:2096-2103` puede disparar proximidad/arribo desde esa estimación ficticia. No elevar interpolación artística a route authority ni usarla para cobrar, llegar o notificar.

`core/geo/ElysiumRouteCompanion.kt:45-69` convierte gazetteer cercano en “seguro”, cámaras/24h/vigilancia activa sin evidencia. Centroide/categoría/alias local es candidato geográfico; no prueba sucursal exacta, disponibilidad ni condición física de seguridad. El catálogo local sí debe preservarse como offline candidate source con provenance/confianza explícitas.

### P1 — Hash Commerce y claims de release no cumplen contrato de prueba

Kotlin `ObdViewModel.kt:1476-1479` hashea timestamp **milisegundos** y suffix `DELIVERED_PIN_VERIFIED`; SQL `:280-287` usa epoch textual **segundos** y `PIN_VERIFIED_ESCROW_RELEASED`. No son bytes iguales. No se encontró prueba parity específica Commerce. El digest SQL bajo search_path vacío requiere verificar namespace de extensión; no se probó ejecutándolo.

Release doc `docs/releases/2026-09-25-elysium-vanguard-ai-os-4.27.0-commerce-and-active-services.md` afirma RLS estricto, dual ledger y lista nombres RPC distintos de los creados en SQL. El SQL no crea un ledger financiero ni integra settlement externo. Las capturas Honor/Xiaomi demuestran pantalla histórica, no tres actores autenticados/escrow real. `70c20438` añade enlace debug/hash; no cambia autoridad ni entrega pruebas nuevas.

## WIP que ya existe y no debe borrarse ni declararse reparación de estos P0

- Identidad EV global/paleta/logo y responsive visual: cambios presentes; no toca Commerce authority ni el worker señalado.
- Room actual 85 y migración 84→85: progreso real de otras familias; no regresarlo a 84 para coincidir con release doc.
- Hub técnico ahora incluye comisión estimada 5% y ajuste layout/botón navegación.
- `ObdViewModel.cancelServiceRequest:1492-1511` WIP añade devolución de fee mediante specialties JSON/withTopUp local. **No corrige autoridad**: cancelar repetidamente puede volver a incrementar saldo; no guard idempotente/receipt observado en esa función. Debe revisarse junto a wallet authority, no borrar WIP sin reconciliar.
- No cambios actuales en LayaRouteEngine ni RideCommandSyncWorker que eliminen los fallos anteriores.

## Orden concreta de evolución (reusar clases reales)

1. **Commerce con patrón ya existente de Ride.** Evolucionar CommerceOrderEntity/Dao para principal/version/intent/outbox/projection; reusar garantías de RideCommandRepository/RideCommandSyncWorker/gateway (no copiar sus fallbacks defectuosos). Las acciones crear/avanzar/cancelar/PIN producen comando idempotente. Servidor valida actor/FSM/versión; projection rehidrata Room. Compra/escrow/refund requieren receipt financiero independiente. No crear otro set paralelo de pantallas.
2. **Unificar route truth.** Usar `RideRoutingProvider`, `OsrmRideRoutingProvider`, `ResilientRideRoutingProvider`, `RideRoadRoute.source/attribution` como puerto existente de carretera, adaptar a CommonMapPanel/GeoRoute para servicios. `LayaRouteEngine` sólo estimación explícita no vial; no fallback que invente ruta/GPS. Resultado debe portar request generation, capturedAt, provider, failure, confianza, stale; late response no actualiza destino nuevo. LayaSpatialSearchEngine/GIS conserva rol de candidate resolver con confirmación humana.
3. **GoldenJourneyTruthSuite como capas de evidencia.** Sus cinco tests actuales usan InMemoryTestRideCommandBus y SharedPreferences proxy (`:51-71`, `:282`); preservar pruebas unitarias pero no llamarlas E2E. Añadir Room real + Worker/gateway contract, fixtures Postgres aisladas, dos/ tres cuentas y comprobación projection/receipt. Matriz obligatoria: PUBLISH enviado→timeout→CANCEL; snapshot NotFound tardío; red caída tercer intento; duplicate delivery/restart; stranger merchant/PIN/cancel; PIN no visible a courier; no SUCCESS/RELEASED antes authority. Vincular cada resultado a source SHA y distinguir unit/integration/device/server.
4. **Semantic orchestrator existente, no nuevo cerebro paralelo.** `EvairInteractionOrchestrator`, `AgentUiRegistry`, `UiTargetResolver`, `VoiceFormBinder`, `SemanticUiGraph`, `GoalCompiler`, `AgentPolicyEngine` son base. Exponer controles Commerce/Rides con capability id, rol/principal, generation y sensibilidad; seleccionar callback real revalida control/policy; dictado sólo edita draft. `AgentResult`/evidence debe distinguir IntentAccepted/Enqueued/AuthorityConfirmed/ReceiptVerified; resolver resultado UI nunca convierte enqueue en ejecución remota.
5. **Gate de release por journey habilitado.** Extender `GoldenJourneyTruthSuite` y verify-release, no crear un “PASS” documental. Commerce se presenta como draft/no confirmado hasta journeys cross-account green; no retirar funcionalidad integrada. Generar manifest versionName/code/Room/schema/migration/source SHA/artifact hash, y conservar evidencia física del mismo APK. Claims de docs se actualizan al nivel de prueba disponible.

## Límites de esta auditoría

Se inspeccionó backend fuente sólo para verificar autoridad del APK. No se consultó Supabase live, no se leyó información de clientes ni se invocaron RPC materiales. No se revalidaron deployment/ACL/CI/artefacto firmado/instalaciones reales. Por tanto no se afirma vulnerabilidad explotada ni release vigente verificado; se afirma incumplimiento reproducible por lectura del contrato fuente y sus call sites actuales. No se ejecutaron builds/tests por instrucción del usuario; los nombres y fuentes de tests no equivalen a PASS.
