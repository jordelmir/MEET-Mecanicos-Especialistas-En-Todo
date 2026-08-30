# MEET — Trust Center Delivery V2

Versión Android: `4.22.0` (`versionCode 48`).

## Problema corregido

Los formularios podían guardar perfiles locales sin entregar una solicitud al
backend. Además, el Centro de Confianza cargaba una sola vez, no estaba suscrito
a Realtime y no incluía todas las capacidades profesionales. El resultado era
una interfaz vacía aunque el usuario creyera haber terminado el registro.

## Cambios

- RPC V2 unificada e idempotente para 14 categorías.
- Recibo y auditoría con correlación extremo a extremo.
- Reintento durable al autenticar o volver a abrir el flujo.
- Registro de talleres, cerrajería y operador de flota conectado a la cola.
- WebSocket como wake-up, catch-up REST, heartbeat y reconexión exponencial.
- Conteos y colas de pendientes, aprobados, rechazados y suspendidos.
- TOTP dentro de Android para satisfacer el requisito AAL2 sin degradarlo.
- Logging estructurado y telemetría sin PII.

## Evidencia exigida para publicación

- pruebas unitarias Android;
- integración PostgreSQL de categorías, RLS, AAL2, auditoría y publicación;
- migraciones remotas y prueba WebSocket real;
- APK única firmada, firma/hash comprobados e instalación física por ADB;
- commit en `main` y release pública con el mismo APK verificado.

## Validación remota del 29 de agosto de 2026

El proyecto Supabase enlazado aceptó las migraciones `20260829000000`,
`20260829010000` y `20260829020000`. El probe remoto creó un usuario temporal, recibió 14 recibos,
observó un `INSERT` por WebSocket, confirmó la denegación RLS de la cola maestra,
reintentó idempotentemente y eliminó todos sus datos. El mismo gate confirmó un
único `PLATFORM_OWNER` activo asociado a la cuenta maestra.

## Endurecimiento adicional de Viajes

La verificación de publicación también descubrió y corrigió tres RPC heredadas
que habían quedado desalineadas del esquema actual:

- Auto-match usa `fare_minor` y `eta_seconds` y delega la asignación al kernel
  transaccional, versionado e idempotente existente.
- La calificación queda persistida una sola vez por viaje completado; un pasajero
  no puede inflar repetidamente la reputación del conductor.
- Las atestaciones de pago usan exclusivamente la tarifa aceptada o completada.
  No existe monto sintético de respaldo y un cliente no puede declarar
  `BANK_CONFIRMED`; esa prueba queda reservada a una futura ingestión bancaria
  confiable.

## Conexión ECU heredada endurecida

La negociación del perfil Hyundai Accent/Verna 2005 ahora prioriza ISO 9141-2
antes de las variantes KWP y usa la cabecera funcional OBD-II `68 6A F1`. En
protocolos ISO/KWP se configura primero la velocidad de inicialización y luego
se selecciona el protocolo. Esto evita gastar la mayor parte de la ventana de
conexión en rutas menos probables y evita iniciar ISO con una cabecera dirigida
incorrecta.

Los adaptadores que se identifican como `ELM327 v2.1` se reconocen como clones
y usan temporización determinista orientada a estabilidad (`ATAT0`); los demás
adaptadores compatibles conservan temporización adaptativa conservadora. Esta
clasificación no los presenta como peligrosos ni bloquea su uso. Una conexión solo
se declara verificada después de decodificar una respuesta Mode 01 PID 00 válida:
el banner ELM demuestra únicamente que el adaptador respondió, nunca que una ECU
respondió.

La suite automatizada cubre orden de comandos, cabecera, temporización, protocolo
seleccionado y ausencia de falsos positivos. La comunicación física completa
sigue requiriendo vehículo con contacto encendido y adaptador disponible durante
la prueba ADB; ninguna simulación sustituye esa evidencia.
