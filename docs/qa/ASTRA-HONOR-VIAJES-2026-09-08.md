# Prueba Viajes — Honor Magic V2 — Astra

Estado: EN CURSO. No se han confirmado aprobaciones en Trust Center.

## Alcance autorizado
Registro pasajero y chofer con datos ficticios y fotografías arbitrarias, exclusivamente para probar el flujo. No representan identidad ni documentación real. No se solicitarán viajes reales.

## Evidencia
- Honor VER-N49, ADB inalámbrico, perfil principal 0.
- Primera instalación: debug 4.23.6 (56), APK 2026-09-07 11:40, SHA-256 78bbc7beb7dbcece150563348426363e08bb47a9e86dfa8fee279eeb6a5e0fdb. Instalación Success, arranque Status ok en 1372 ms, proceso 23389 y actividad en primer plano.
- Pasajero: Prueba Astra PASAJERO, teléfono ficticio 00000000. Tres capturas mediante cámara del dispositivo. Tras enviar, pantalla muestra revisión pendiente. Recepción remota aún no confirmada.
- Chofer: Prueba Astra CHOFER, 00000000, astra.rides@example.invalid, 1990-01-15. Se alcanzó paso 2 de 6 (vehículo).
- Al retomar el 8 de septiembre, paquete ausente del perfil principal. No atribuir a fallo de MEET: causa no investigada. Nueva APK debug disponible de 00:36, misma versión 4.23.6 (56), SHA-256 bcfb55bfcf26a6847c13dbda02fb70efac9c92bf0eb522080efbd4a9667202d7. Reinstalación en curso.
- Capturas y XML de primera sesión: /tmp/meet-astra-rides-20260907/.

## Lista de mejoras y verificaciones
1. **P1 — Estado de entrega del expediente.** Pantalla dice «está siendo revisada» después de guardar PENDING local, antes de acreditar recepción remota. Código revisado: ObdViewModel guarda local antes de submit; fallo remoto usa Toast temporal. Distinguir guardado, subiendo, recibido, revisión y error persistente/reintento. No se afirma que este envío haya fallado.
2. **P1 — Aislamiento de pruebas.** No se encontró bandera sandbox en el flujo de aprobación revisado. Las decisiones se auditan y APPROVED habilita operación. Implementar expedientes de prueba claramente diferenciados y excluidos de operación real.
3. **P2 — Teléfono.** 00000000 permitió enviar pasajero y avanzar datos de chofer. Añadir validación contextual y diferenciar teléfono declarado de verificado.
4. **P2 — Cambio de rol poco visible.** Tras registro pasajero, menú no ofrece registro chofer; existe interruptor superior sin descripción accesible observada. Ofrecer acción explícita y etiqueta de estado/rol.
5. **P2 — Accesibilidad de fotografías.** Captura marcada visualmente con visto verde, sin estado textual equivalente en XML observado. Añadir estado capturada/pendiente y acción de reemplazo accesibles.
6. **P2 — Indicadores de conexión.** EN VIVO antes de activar cuenta y LOCAL tras envío pueden confundirse con estado del expediente. LOCAL corresponde a proyección de viajes, no prueba entrega. Precisar el significado en UI.
7. **Pendiente — Recuperación.** Verificar restauración de borrador chofer al volver y tras reinicio, sin atribuir desinstalación a la app.
8. **Pendiente — Trust Center.** Confirmar recepción de ambos expedientes, evidencia accesible, MFA, motivos de prueba y persistencia de decisiones antes de declarar aprobado.

## Continuación del 8 de septiembre
- Nueva instalación: `Success`; arranque frío 1144 ms, seguido de permisos Android. Registro chofer completado por el usuario durante la prueba. Después se observó «Ya registrado como Chofer de Viajes», «Verificación en revisión» y el diálogo «Documentos recibidos», con Prueba Astra CHOFER y PRUEBA ASTRA 2024.
- El usuario informó haber completado también los datos de pasajero. Pendiente corroborar pantalla y recepción remota: la conexión ADB dejó de responder en ese momento.
- Lecturas de UI y captura quedaron sin respuesta; se intentó `adb reconnect`. Después, `adb devices -l` no listó dispositivos y mDNS tampoco anunció servicios. Se solicitó USB o IP:puerto vigente. No se borraron registros ni se manipuló la base de datos.
- Trust Center NO alcanzado todavía. Ninguna aprobación ejecutada. La prueba sigue incompleta por falta de conexión al dispositivo.
- Hallazgo adicional fuera de Viajes: pantalla ampliada TEMP MOTOR mostraba 0 °C; al cerrarla, el panel indicaba OBD NO DISPONIBLE y SIN ENLACE. Revisar propagación del estado sin medición al reloj ampliado antes de mostrar valor numérico (captura current.png tomada a las 05:16). No se diagnosticó ni parcheó este punto.
- Mejora de onboarding: instalación solicitó micrófono y dispositivos cercanos antes de usar esas funciones. Preferir solicitudes contextuales por funcionalidad. Se rechazaron ambos para esta prueba de registro y se permitieron notificaciones/cámara.

## 2026-09-08 — instrumentación y gates adicionales

- Se añadió `RideObservability` con eventos de baja cardinalidad para cambio de modo, refresco de proyección, envío/aceptación de oferta y calificación. Los identificadores y errores libres se reducen; no se registran teléfonos, documentos, direcciones ni coordenadas.
- El modo chofer ahora se persiste por principal autenticado y sólo se activa cuando la verificación local concede acceso. Al activarlo dispara una sincronización inmediata de solicitudes y ofertas.
- El refresco remoto registra resultado y cantidad de filas; una falla de autenticación o RLS queda diferenciada de una respuesta vacía.
- Los tests `RideObservabilityTest`, `RideLifecycleTest` y `RideOfferAcceptanceContractTest` compilaron y pasaron en la primera ejecución completa posterior a la corrección de sanitización (`:app:testDebugUnitTest`, BUILD SUCCESSFUL; 35 tareas).
- `:app:compileDebugKotlin` pasó con advertencias preexistentes. El APK debug se generó (363,181,850 bytes).
- La instalación/arranque en el Honor quedó sin evidencia nueva porque la conexión ADB inalámbrica dejó las operaciones `adb install` bloqueadas; no se registra un falso “pasó”. Debe repetirse con una sesión ADB estable para capturar `MeetRidesEvent`, `MeetRides` y `AndroidRuntime`.
- El estado remoto observado antes de esta instrumentación seguía mostrando la solicitud en `SEARCHING` sin filas `ride_offers`; por eso la contraoferta no podía aparecer en el pasajero. El siguiente gate es confirmar, desde una sesión chofer aprobada, que el comando `SUBMIT_OFFER` llega a `ride_offers` y que la proyección del pasajero la refleja.

## 2026-09-08 — cierre al arrancar y salto de subasta

- Evidencia del Honor Magic V2 (`HONOR VER-N49`): `ApplicationExitInfo` y `data_app_crash` mostraron un `NullPointerException` en `ObdViewModel.kt:3582`; el colector de principal escribía `_rideDriverMode` antes de que la propiedad fuera inicializada. Se movió la `MutableStateFlow` antes del bloque `init`.
- La misma sesión reveló un segundo cierre al tener un viaje activo: `IllegalStateException` de Compose por anidar un componente desplazable vertical dentro de otro. Se eliminó el `verticalScroll` del panel de contraoferta del chofer y el `LazyColumn` de ofertas del pasajero dentro del contenedor desplazable del viaje activo.
- La build corregida compiló y se instaló por `adb push` + `pm install`; arranque verificado con `am start -W`, `PID=22773`, `topResumedActivity=com.elysium369.meet/.MainActivity` y sin un nuevo `data_app_crash` durante la ventana de observación.
- El botón de la tarjeta de solicitudes ya no reclama el viaje directamente: abre el panel de negociación para enviar una contraoferta autoritativa.
