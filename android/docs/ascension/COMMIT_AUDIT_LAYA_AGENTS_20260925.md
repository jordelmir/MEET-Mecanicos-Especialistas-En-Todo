# Auditoría Android de Laya, EVAIR y Agent Store — 25 septiembre 2026

Baseline solicitado: `e4c379dd`. Commits revisados: `16bb6da4`, `4407dd20`, `baafbe22`, `767e5ed0`. Se compararon diffs históricos, contexto Kotlin actual y WIP. Alcance: APK; sin builds, ejecución de tests, cambios funcionales ni cambios web. Skill: elite-code-review. Adjunto ASCENSION utilizado sólo como especificación comparativa, no orden de implementación.

## Resultado

No se puede considerar funcional completo el conjunto entregado. Hay UI real y motores locales, pero faltan ejecución autoritativa de acciones, persistencia productiva de companion, compra real y conexión de varios motores al runtime. Los tests presentes ejercitan objetos aislados; su existencia no prueba el APK ni autoridad remota. No se detectó un P0 de ejecución física nueva en los cuatro commits; los defectos de falsa emergencia y compra son P1 de entrega y verdad.

## Hallazgos P1 actuales

1. **Voz anuncia llamada y ubicación transmitida sin ejecutar ninguna.** `android/app/src/main/kotlin/com/elysium369/meet/core/agent/laya/VoiceLayaBridge.kt:55` devuelve “Enlazando al 911 ... compartiendo tu ubicación” y `:66` repite en inglés. `VoiceCommandManager.kt` llama al bridge y sólo sintetiza voz; el resultado no lleva ejecución ni evidencia. El asistente textual también declara protocolo activado en `LayaAssistantEngine.kt:188`. Trigger: consulta “auxilio choque emergencia 911”. Consecuencia: usuario puede creer que se solicitó ayuda. Corrección requerida: hablar de acción propuesta hasta callback real y confirmación de autoridad. WIP del modal de viaje ya aclara indisponibilidad (`ActiveRideTrackingScreen.kt:448`), pero no corrige estas respuestas.

2. **Compra actual nunca inicia una compra ni entrega recibo.** WIP `core/agentstore/data/PlayStoreAgentPurchaseCoordinator.kt:23` sólo concatena agente/producto/reloj, SHA-256 y `Pending` en `:37`; no BillingClient, checkout, receipt RPC o polling. `AgentStoreViewModel.kt:163` invoca ese stub; `purchaseState` en `:161` no se consume en `AgentStoreScreen`. Por tanto pulsar Desbloquear no compra y el fallo pendiente no se muestra. El commit `767e5ed0` concedía entitlement local inmediatamente; WIP elimina correctamente ese bypass, pero su reemplazo no es funcional. Corrección requerida: iniciar facturación real, verificar recibo remotamente y mostrar Pending/Rejected/Verified sin equipar antes de snapshot.

3. **Derechos adquiridos no se cargan al abrir la tienda y no se vinculan al cambio de cuenta.** `AgentStoreViewModel.kt:59` inicializa sólo TTS. Las únicas llamadas a `entitlementRepository.refresh()` están en `:170` y `:175` después del botón de compra. La repo singleton conserva Available, userEntitlements y equippedAgentId sin observer de principal; `AgentEntitlementRepository.kt:59` verifica sólo pertenencia, no principal ni caducidad. En un arranque, usuario comprado aparece bloqueado hasta compra; tras cambio de cuenta, snapshot previo puede seguir presentado. Esto no demuestra bypass servidor, pero sí cruce de proyección y autorización local. Corrección: refresh por sesión/entrada, limpiar inmediatamente al cambiar principal y comprobar ownership contextual. `equipAgent` en VM `:146` tampoco verifica entitlement.

4. **DigiSoul productivo no persiste y el widget no comparte su estado.** `EvairDigiSoulEngine.kt:390` crea shared con storageDir null; `:343` sale sin escribir. El test de disco provee carpeta explícita, distinta de la fábrica productiva. Equipar en `AgentStoreViewModel.kt:151` sí registra memoria en shared, perdida al matar proceso; `EvairDigiPetWidget.kt:43` crea otra instancia y `:47` captura una sola lectura, sin StateFlow. Búsqueda productiva no encuentra un caller del widget. Corrección: repositorio con storage app/principal, estado observable y wiring real. WIP de overlay no conecta esta engine shared.

5. **EAOS UI instancia un motor vacío sin DAO y declara autonomía.** `MeetExecutiveCommandCenterScreen.kt:55` usa `AutonomousOperationsEngine()`; `:69` captura lista inicial. `:260` muestra “Cero incidentes ... autonomía total”. El motor actual acepta DAOs opcionales (`AutonomousOperationsEngine.kt:27`) pero defaults son null y mapas locales `:32`; la pantalla no los inyecta, no observa eventos y llama consultas con snapshot null en `:382`/`:423`. Aprobar/rechazar no prueba permiso owner ni transición servidor. WIP agrega persistencia opcional al motor, pero no repara esta fábrica. Corrección: snapshot autenticado, estados unavailable/unknown y proyección durable observada; ningún texto de autonomía sin evidencia.

6. **Scanner dibuja acciones EVAIR que no hacen nada.** `ScannerScreen.kt:450` abre EvairAssistantSheet sin onActionTriggered y sin activeDtcs, pese a existir datos de DTC en ViewModel. `EvairAssistantSheet.kt:255` invoca callback nullable; buttons Scan/ViewDTC/Tow/PreITV son visibles e inertes. En viaje `ActiveRideTrackingScreen.kt:475` sólo implementa llamar/mensaje/safety/cancel y `:481` ignora SHARE_LOCATION y COPY_SINPE; acción `action_911` abre modal general, no marca 911. Corrección: mapear cada acción a capability/callback real o no ofrecerla.

7. **Integración de plataforma y física sólo declarada, no alcanzable.** Búsqueda de referencias en `android/app/src/main/kotlin` encuentra sólo definiciones para `LayaIntentResolver`, `EmissionsLayaEvaluator`, `QuoteAntiFraudLayaEvaluator`, `RideAnomalyLayaEvaluator`, `PhysicsAnomalyEngine`, `VehicleEpisodicMemory`, `compilePlanWithLaya`, y ninguna instancia productiva de ElysiumAgentBus. Existen tests directos, no wiring de capabilities ni ingestión sensores. No se puede afirmar ECU inteligente, antifraude, predicción o monitor de colisión operativo por estos commits. Corrección: converger con flujos actuales y evidenciar puntos de entrada antes de etiquetar capacidades activas/comprables.

8. **Motores desconectados promoverían falta de datos a verdad si se conectan tal cual.** `EmissionsLayaEvaluator.kt:164` considera aprobación si rejections está vacía; input con sólo vehicleYear y sensores null devuelve willPassDekra=true, 0.94 y confidence 0.95 (`:192`). `QuoteAntiFraudLayaEvaluator.kt:123` etiqueta EXACT cuando cada pieza trae cualquier OEM string, sin VIN/tuple/verificación, incluso lista vacía por all vacuo. `PhysicsAnomalyEngine.kt` emite kmUntilMil constantes y diagnósticos cerrados de una muestra. Estos defectos no están hoy demostrados alcanzables, pero bloquean activar las funciones con esas promesas. Corrección: MissingEvidence/Unknown, provenance y compatibilidad probable salvo evidencia cerrada.

## Trazabilidad de entrega

| Commit | UI → runtime | Persistencia | Autoridad | Tests en código / límite |
|---|---|---|---|---|
| 16bb6da4 | Scanner/ActiveRideTracking → EvairAssistantSheet → LayaAssistantEngine → LocalLayaEvaluator | Chat en remember, se pierde al cerrar/proceso | Respuestas de plantillas; callbacks parciales, no receipt de acción | LayaAssistantTest prueba strings e inputs sintéticos; no callback/UI/servidor |
| 4407dd20 | AgentBus semantic fallback; motores de emisiones/cotización/rides; compilePlanWithLaya añadidos | No store propio | Heurísticas y scores constantes; no autoridad de aprobación | LayaPlatformEcosystemTest construye motores/bus directos; única opción recibe ~0.999 y aparenta semántica |
| baafbe22 | VoiceCommandManager → VoiceLayaBridge → assistant/hash-index; widget companion declarado | Engine persiste sólo si recibe File; VehicleEpisodicMemory es mapa | Voz no ejecuta despacho; física sin sensor stream wiring | DigiSoul disk test sí prueba File explícito; deep intelligence tests modelan telemetry, no hardware |
| 767e5ed0 | Nav agent_store → Hilt VM → catálogo/preview/equip; Executive UI → motor nuevo | Equipped ID, entitlements y shared memory en RAM | Histórico: unlock local; WIP: Supabase read gateway + purchase stub Pending | AgentStoreTest actual usa TestGateway en memoria; no Billing/RLS/tamper remoto |

## WIP ya corregido: conservar

- EntitlementRepository elimina return true/grantEntitlement productivo y adopta Unknown/Unavailable fail-closed.
- Gateway consulta user_entitlements por usuario y status ACTIVE; DI registrada. Esto es wiring de lectura, no prueba RLS ni recibo de compra válido.
- PurchaseAndUnlock evita equipar en Pending/Rejected; no restaurar grant local.
- Catálogo/avatar cambian nombres originales Elysium; tests se actualizan a seis agentes.
- Modal de seguridad de viaje comunica que Guardian/live sharing no están disponibles.
- Motor EAOS permite DAOs, pero la pantalla sigue usando defaults null.

## Riesgos P2 y pruebas faltantes

- `LayaDecisionEngine` es keyword scorer por defecto, no una inferencia de pesos Laya/NPU; no endpoint/client configurado en factories productivas. Badge fijo LAYA 33ms no mide runtime.
- VoiceLayaBridge se construye de nuevo por consulta y no recibe rideContext/vehicleContext desde VoiceCommandManager. Inglés no emergencia retorna español.
- Widget petting histórico registra KM_DRIVEN +0.5km por toque, inventando distancia; no caller productivo actual encontrado.
- Preview propiedad se deduce del catálogo filtrado; compañero fuera de filtro puede parecer no poseído.
- TTS “Reproduciendo” se activa incluso sin TTS y sólo temporiza waveform 3.5s.
- Faltan evidencia de compra real y recuperación proceso, account-switch entitlement isolation, dispatch emergency execution truth, pruebas UI de acciones, OBD físico, premium tamper contra backend y APK firmado correspondiente.

## Límites de esta evidencia

Este reporte es auditoría estática. No se ejecutaron tests ni builds; no se validó APK instalado, OBD, sesión Supabase/RLS o Google Play. No se modificó WIP funcional. El estado de autoridad productiva queda NO VERIFICADO, aunque exista DAO/gateway/test fake.
