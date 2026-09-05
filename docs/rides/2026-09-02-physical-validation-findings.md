# Hallazgos de validacion fisica — 2 de septiembre de 2026

Este inventario registra observaciones reproducidas en los dispositivos reales
durante la ronda de flujo. No declara ningun flujo como aprobado hasta que se
aplique la correccion, se construya una APK nueva y se vuelva a comprobar.

## Bloqueantes de veracidad y aprobacion

1. **P0 — Telemetria OBD mostrada sin conexion.** En el Xiaomi M2101K6R, la
   pantalla de escaner conserva el estado global `DESCONECTADO` pero presenta
   `100%`, `CORE OPERATIVO OPTIMO`, sensores dentro de rango y monitoreo OBD en
   tiempo real. Ningun valor de salud, diagnostico o sensor puede mostrarse como
   medido hasta existir una sesion OBD conectada y datos capturados. La pantalla
   debe explicar honestamente que OBD no esta disponible y ofrecer la accion de
   conexion. El modulo PRO reproduce el mismo defecto al declarar `ESTABLE` con
   `0 Hz` y `0 ms` mientras permanece desconectado.
2. **P0 — Solicitud de conductor sin evidencia revisable.** La cola remota
   contiene la solicitud `cde96eaa-64fb-4908-87e9-851cdceb49ee` de tipo
   `RIDE_DRIVER` en `PENDING`, pero con cero objetos de evidencia. El backend
   correctamente impedira aprobarla. La aplicacion no puede degradar en silencio
   al contrato antiguo cuando faltan fotos/documentos requeridos: debe preservar
   los adjuntos de forma durable, reintentar su entrega y dejar el estado como
   pendiente de envio si no hay evidencia revisable.

## Adaptacion de pantalla pequena

3. **P1 — Encabezado Adaptive Home recortado.** En Xiaomi, el titulo
   `COMMAND CENTER` se muestra como `COMMAN` / `CENTER`; la D queda oculta por
   la competencia de ancho con los controles del encabezado.
4. **P1 — Encabezado del escaner comprimido.** En Xiaomi, el control de estado
   se presenta visualmente como `EN [interruptor] ES CONECTAR`. Debe convertirse
   en una accion clara, legible y accesible en disposicion estrecha.
5. **P1 — Pestanas del escaner.** Las pestanas horizontales dejan texto cortado
   y no comunican bien que se puede desplazar. Deben mantener objetivos tactiles,
   etiquetas completas o abreviadas accesibles y una pista de desplazamiento.

## Evidencia de esta ronda

- APK instalada en ambos telefonos: `4.23.3 (53)`.
- HONOR Magic V2: arranque confirmado sin error fatal de WorkManager.
- Xiaomi M2101K6R: arranque y entrada al escaner confirmados; se reprodujeron
  los hallazgos 1, 4 y 5 sin cierre de la aplicacion.
