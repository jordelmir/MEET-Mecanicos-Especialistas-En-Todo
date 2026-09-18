# Viajes: PIN, cierre y calificación (16 de septiembre de 2026)

## Fallos demostrados en el código

1. En `BuildConfig.DEBUG`, `verifyRideBoardingPin` iniciaba el viaje con una actualización local y un `UPDATE` directo. Cuando el PIN no existía en Room, cualquier número de cuatro dígitos pasaba. La ruta omitía la verificación, vencimiento y bloqueo de intentos de `ride_verify_boarding_pin_v2`.
2. `updateRideStatus` en debug marcaba `COMPLETED` y mostraba éxito antes de obtener un recibo del servidor. El `UPDATE` remoto era de mejor esfuerzo; el pasajero podía seguir viendo el estado anterior.
3. La calificación en debug se guardaba localmente antes de confirmar la autoridad. La vía productiva rechazaba al chofer porque `RideRatingSubmission` sólo admitía al pasajero.
4. Una respuesta `INVALID`, `LOCKED` o `EXPIRED_OR_USED` del PIN es un resultado de negocio del RPC, no una transición del viaje. El cliente la trataba como aceptación genérica. La cola podía dejar el estado local `PENDING`, impidiendo reintentar.
5. La proyección del viaje completado no levantaba automáticamente el diálogo de calificación en el otro dispositivo, aunque Room recibiera `COMPLETED`.
6. Había excepciones de depuración para oferta propia, vehículo no verificado y selección de viaje del chofer. Esas excepciones impedían usar el APK debug como prueba del comportamiento de producción.
7. El mapeador remoto conservaba el chofer, vehículo y teléfono anteriores si Room consideraba el viaje activo, incluso cuando el servidor cambiaba la asignación o el propietario. Cinco pruebas existentes de aislamiento fallaban.

## Reparación

- PIN, llegada, inicio y cierre usan los comandos con versión, actor e idempotencia del servidor en ambos tipos de APK. Sólo el servidor puede confirmar `PASSENGER_ONBOARD` y `COMPLETED`.
- Los rechazos del PIN quedan en la cola con su motivo visible; una proyección autoritativa devuelve el viaje a su estado vigente y permite un nuevo intento.
- El cierre confirmado por la proyección dispara la calificación del participante correcto. La calificación del chofer ahora usa `ride_record_passenger_feedback_v1`, con autenticación, viaje completado, asignación exacta y una sola calificación por viaje.
- Una calificación no se persiste ni se anuncia como exitosa si falla la confirmación remota. El comentario sigue siendo una anotación local; la calificación numérica es la parte autoritativa.
- Se eliminaron las excepciones debug observadas en selección del viaje y elegibilidad de ofertas.
- La aceptación directa del chofer ahora encola `CLAIM` y espera la asignación confirmada; ya no escribe `ASSIGNED` local ni remotamente por adelantado.
- La proyección usa siempre el estado y la asignación del servidor, conserva datos privados sólo cuando propietario, chofer y vehículo siguen siendo los mismos, e ignora una versión remota más antigua.

## Verificación y límites

- Pruebas dirigidas: PIN, autorización y persistencia de calificación, y mapeo de un `COMPLETED` remoto sobre un `IN_PROGRESS` local.
- APK debug compilado e instalado en Honor VER_N49; `am start -W` informó `Status: ok`, `LaunchState: COLD`, `MainActivity` quedó `topResumedActivity`, `pidof` devolvió un proceso y el log de ese arranque no mostró `FATAL EXCEPTION`.
- La interfaz del APK nuevo abrió Viajes en ambos roles. El Centro de Viajes del chofer mostró `0 solicitud(es) disponible(s)` y la pestaña del pasajero mostró `SIN VIAJES ACTIVOS`. No había un viaje real para ejecutar PIN, cierre y calificación con dos cuentas en el dispositivo.
- Confirmar la migración en el historial remoto y ejecutar un viaje real con dos cuentas autenticadas antes de afirmar prueba de punta a punta. Un único dispositivo con cero solicitudes visibles no proporciona esa prueba.
- La aceptación de ofertas ahora usa el comando autoritativo `ACCEPT_OFFER`; sigue pendiente la prueba física completa con dos cuentas y dos procesos.

## Revisión posterior de despacho, presencia y saldo (17 de septiembre)

- El informe sobre `main` era correcto para su revisión anterior. En el árbol actual, el Centro y el tablero ya emiten `CLAIM` y el PIN no tiene bypass de cuatro dígitos. La nueva política compartida clasifica solicitudes elegibles, propias, ocultas y vencidas. Una solicitud de la misma cuenta aparece como publicada pero no autoasignable.
- Supabase ya tenía el disparador `ride_requests_reject_self_assignment`, activo en producción, que impide asignar un conductor igual al pasajero. El cliente también rechaza autoasignación antes de encolar.
- El único registro de presencia `AVAILABLE` consultado en producción tenía `last_seen_at` vencido. El APK ahora renueva presencia cada 30 segundos con GPS reciente, secuencia leída del servidor y recibo de `ride_update_driver_location_v1`. La UI muestra si Supabase confirmó la presencia.
- La única carrera `COMPLETED` observada en producción tenía tarifa ₡1.800 y captura de comisión ₡90. No había carreras completadas sin captura en esa consulta. La función antigua `complete_ride` omitía la comisión; la migración `20260917010000` desplegada la redirige a `ride_complete_trip_v2`.
- El saldo del chofer se consulta de nuevo al recibir una proyección `COMPLETED`. La tarjeta distingue saldo pendiente de consulta y reserva activa. Las pestañas “Finalizados” de ambos roles muestran viajes cerrados y reemplazan la sección “Historial” del perfil.
- Se retiró otra mutación optimista de `CANCELLED`: las cancelaciones se encolan con actor, motivo e idempotencia. La cola sólo cierra localmente una publicación que prueba no haber salido; en los demás casos espera la respuesta autoritativa.
- Un segundo toque de “Aceptar” reutiliza el `CLAIM` todavía activo en Room, incluso tras reiniciar el proceso. Un rechazo terminal permite intentar de nuevo cuando la causa se corrija; no se reutiliza para siempre una clave fallida.
- No se ha demostrado aún una carrera real de extremo a extremo entre dos cuentas en dos dispositivos. El teléfono Honor pasó a estado ADB `offline` durante la validación de esta iteración; no se atribuye al APK una prueba física que no ocurrió.
- El control de paridad TS/Kotlin terminó conforme y el conjunto actual de pruebas Android registró 2.080 casos, sin fallos ni errores. El contrato estático de autoridad de Viajes también pasó.
