# Mapa preciso, abordaje y operación del conductor

**Versión Android:** 4.8.0  
**Fecha:** 2026-08-01

## Cámara y selección por pin

- El gesto del usuario toma control de la cámara. Las actualizaciones GPS ya no ejecutan un `animateCamera` que interrumpa el zoom.
- El encuadre automático sólo se aplica al cargar/cambiar la geometría estática o al pulsar recentrar.
- La vista solicita al contenedor no interceptar el gesto mientras hay uno o más dedos sobre MapLibre.
- Recogida y destino se editan en un selector de pantalla completa; la lista del formulario ya no compite por los gestos del mapa.
- El pin permanece fijo y el usuario arrastra el mapa por debajo. Pellizco, doble toque y botones `+`, `−` y `◎` permiten afinar o volver al punto inicial.
- Las coordenadas con seis decimales se actualizan al terminar cada movimiento y se confirman de forma explícita.
- Las coordenadas manuales dejan de ser el flujo principal; el usuario conserva búsqueda, lugares guardados y pin.

## Llegada y abordaje autoritativos

`ride_driver_arrived_v3` falla cerrado si:

- el actor no es el conductor asignado;
- el viaje no está `DRIVER_EN_ROUTE`;
- la versión cambió;
- el GPS tiene más de 30 segundos, está en el futuro o reporta precisión peor que 75 m;
- la distancia Haversine al pin de recogida supera 100 m.

La app aplica la misma regla para explicar el bloqueo, pero la RPC vuelve a calcularla. Una alteración del cliente no permite marcar llegada.

El pasajero ve el espacio de PIN después de la asignación. Al confirmarse la llegada, solicita automáticamente un PIN privado al servidor. El PIN se almacena con hash, tiene límite de intentos y sólo una verificación correcta mueve el viaje a `PASSENGER_ONBOARD`; sólo entonces el conductor puede solicitar `START`.

## Seguridad de presencia

- Primera conexión del día: cámara frontal y prueba ojos abiertos → cerrados → abiertos.
- Caducidad: 12 horas y siempre al cambiar de día local.
- Detección: ML Kit empaquetado en la APK, usable sin descargar un modelo en carretera.
- Persistencia: SHA-256 del fotograma de evidencia, resultado de parpadeo y ventana de validez. No se crea una plantilla de reconocimiento facial.
- Cancelar la prueba devuelve a modo pasajero; no habilita operación de conductor.

## Operación

- Menú superior izquierdo: Perfil, Historial, Soporte, Autos/flotillas, Ganancias y regreso a PRO.
- Flotillas: alta de múltiples vehículos como `PENDING`; sólo uno puede quedar activo y sólo después de `VERIFIED`.
- Destino Casa: ubicación guardada voluntariamente y ranking local de solicitudes por proximidad del destino a casa. No altera la asignación autoritativa.
- Reputación: finalización se reduce si un viaje asignado termina cancelado. La aceptación exacta procede de ofertas autoritativas, no de un porcentaje inventado en el cliente.
- Ganancias: ventanas día, semana, mes, año y tres años calculadas sólo desde viajes completados.

## Avisos e inteligencia vial

- Canal local deduplicado para conductor disponible sin viaje (enfriamiento de 30 minutos).
- Aviso una sola vez cuando el ETA restante de la ruta completa entra entre 6 y 8 minutos al destino final.
- `ride_push_outbox` deja eventos autoritativos para entrega push. La fila no se presenta como prueba de entrega: producción necesita un worker con credenciales del proveedor push.
- Reportes: tráfico lento/muy lento, vehículo varado, bache, obstáculo, cierre, contravía, policía, tránsito, policía pública, policía de tránsito, reductor y calle inundada.
- Velocímetro pequeño sobre el mapa con velocidad GPS; no se rotula como dato OBD.

## Marcadores

- Conductor: dragón de fuego rojo propietario Elysium, generado por geometría Canvas y sin copiar personajes licenciados.
- Pasajero: silueta humana completa.
- La fábrica de iconos queda separada por rol para incorporar animaciones y temas posteriores.

## Verificación mínima

```bash
cd android
./gradlew :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'
./gradlew :app:lintDebug :app:assembleDebug
```

La prueba de aceptación exige además: zoom manual sin salto, pin confirmado, bloqueo fuera de 100 m, PIN inválido rechazado, inicio posterior a PIN válido, permiso de notificación y prueba de parpadeo en un dispositivo físico.

Prueba física 2026-08-01 en Honor VER-N49: el arrastre cambió el centro de `9.919699, -84.117038` a `9.924807, -84.114659`; el botón `+` respondió, `CONFIRMAR PIN` cerró el selector y el formulario mostró `RECOGIDA FIJADA · CAMBIAR PIN`. El proceso permaneció activo sin `FATAL EXCEPTION`.
