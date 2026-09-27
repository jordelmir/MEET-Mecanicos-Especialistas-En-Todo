# Mensajes multimedia / llamadas — evidencia por versión

Compilación debug: BUILD SUCCESSFUL, 8m39s. 23 tests sin fallos: EVAIR safety14, orchestration2, cifrado de llamadas3, regresión de llamadas de Viajes4.
APK SHA256: `39c33a1235ffe5cf187209b2630708a5aa03c3118c7c5e1a23299ac21d0c50ad`.
Xiaomi: instalación actualizada Success y MainActivity Statusok, 3046ms; PID20830, crash buffer de ese proceso sin registros. No corresponde todavía a la última pantalla unificada de servicios ni a últimos registros semánticos añadidos después.
Honor quedó desconectado; se solicitó reconexión. No atribuir pruebas anteriores al nuevo APK.

Desplegadas migraciones communication_media_events y communication_realtime_calls. Contrato SQL desechable pasó tipos AUDIO/IMAGE, reintentos, caller no puede responderse, tercero no accede clave ni termina llamada, receptor acepta y estado final elimina clave.

Las imágenes JPEG y notas AAC/MP4 se validan y cifran, con límite512KiB y vínculo a dueño, cola persistente conectada. Llamadas reutilizan hardware de Viajes, con autorización por participantes y AESGCM sobre realtime. ACTIVE requiere marco de par autenticado, no solo RPC aceptado. Clave efímera emitida por servidor: no afirmar cifrado ciego al servidor ni secreto hacia adelante.

No afirmar prueba de audio bidireccional, llamadas con aplicación cerrada, push o notas/imágenes recibidas físicamente hasta capturar evidencia con ambos teléfonos. Las llamadas actuales dependen del proceso/conectividad; no hay nueva integración push/foreground microphone en esta ronda.
