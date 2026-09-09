# Viajes: seguimiento y cierre verificables

La pantalla del viaje observa ahora su registro persistido: las confirmaciones de solicitud, asignación, llegada, PIN, inicio y cierre dejan de quedar ocultas detrás de una copia antigua. Una cancelación confirmada limpia la selección. La ausencia temporal del registro oculta el panel y permite recuperarlo cuando reaparece.

La calificación del conductor sólo se guarda tras respuesta autoritativa positiva; el reintento usa un identificador local estable. Las propinas consultan el esquema canónico, bloquean el viaje para evitar duplicados concurrentes, validan actor/importe/moneda y distinguen registro de instrucción de pago confirmado. El resumen presenta exclusivamente el total final confirmado, sin inventar ajustes ni métodos de pago.

La comprobación facial usa un componente de prueba de presencia con secuencia ojos abiertos/cerrados/abiertos, instrucciones de encuadre y luz, reinicio y liberación de cámara. La presencia local no acredita identidad ni sustituye la verificación del conductor.

CI incluye los tests del paquete ride, el recorrido canónico PostgreSQL y regresión de arranque PostGIS. Las funciones pertenecientes a una extensión se conservan y las coordenadas de prueba funcionan con PostGIS nativo y el sustituto local.

## Evidencia en ejecución

- Tests iniciales de dominio de Viajes: PASS.
- PostgreSQL canónico: PASS, incluido recorrido completo, PIN, cancelación, calificación, propina y aceptación concurrente (1 ganador entre 100).
- PostgreSQL V8, V10 y V11: PASS local; PostGIS nativo se verifica en CI.
- Honor VER_N49: instalación y arranque PASS; dos pruebas instrumentadas de persistencia/observación PASS.
- Validación final de todas las modificaciones, rostro real del usuario y CI remoto: pendiente al redactar esta nota.

No se ha simulado un pago real, una identidad facial ni un viaje físico en producción. La prueba instrumentada reabre Room; no equivale por sí sola a matar y restaurar todo el proceso Android.
