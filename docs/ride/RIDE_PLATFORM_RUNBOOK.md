# MEET Viajes — Runbook de plataforma

Fecha: 2026-07-26  
Versión Android: 4.2.0 (`versionCode 19`)  
Piloto: Costa Rica  
Arquitectura: mundial y multimoneda

## Estado verificable

La versión 4.2.0 entrega una base ejecutable y auditable:

- dinero en unidades menores ISO‑4217, sin `Double` en el dominio financiero;
- comisión exacta de 500 puntos básicos (5 %);
- regalía idempotente de `100000 CRC` para el piloto;
- reserva al aceptar, captura únicamente al completar y liberación al cancelar;
- máquina de estados, autorización por actor y PIN para abordaje;
- cancelaciones normalizadas, con revisión especial de seguridad y sin tarifa
  automática durante el piloto;
- ledger y eventos inmutables;
- esquema Supabase con RLS, RPC transaccionales e idempotencia;
- posiciones GPS efímeras separadas para pasajero y conductor;
- evidencia vehicular por categoría, fuente, vigencia y consentimiento;
- MapLibre con marcadores independientes para GPS del pasajero, recogida,
  destino y conductor;
- centro voluntario de privacidad mecánica;
- DTC, mantenimiento, repuestos y reportes sin datos inventados;
- eliminación de autoaprobaciones, identidades, teléfonos, vehículos,
  calificaciones y viajes simulados en el flujo productivo.

## Límites honestos de esta versión

La interfaz existente todavía usa Room como proyección local. El esquema remoto
ya está listo, pero la publicación mundial requiere conectar el repositorio
Android autenticado a las RPC, Realtime y Broadcast privados. Hasta completar
esa unión, la propia interfaz indica que la sincronización entre dispositivos
está pendiente: no afirma falsamente que dos teléfonos estén conectados.

La verificación de conductor y pasajero necesita un proceso administrativo
remoto. La APK ya no contiene botones de autoaprobación. No debe habilitarse el
despacho público hasta que la identidad local esté enlazada a `auth.uid()` y la
revisión documental ocurra del lado servidor.

## Despliegue de base de datos

Aplicar primero:

```bash
supabase db push
```

La migración principal es:

```text
supabase/migrations/20260726010000_ride_platform_foundation.sql
```

Comprobar su contrato:

```bash
bash tests/ride/verify-ride-migration.sh
```

Contiene 13 tablas `ride_*`, RLS en todas ellas y las RPC:

- `ride_grant_promotional_balance`;
- `ride_accept_offer`;
- `ride_cancel_trip`;
- `ride_complete_trip`.

Las aplicaciones cliente no escriben directamente el ledger ni los eventos.
La evidencia mecánica solo puede insertarla el conductor asignado con viaje
activo y consentimiento vigente; al revocar, deja de ser visible inmediatamente.

## Saldo y recargas

`RIDE_PLAY_BILLING_POLICY_APPROVED` es `false` por defecto. No cambiarlo sin:

1. dictamen escrito sobre política de Google Play para el producto exacto;
2. proveedor autorizado para financiar servicios físicos;
3. verificación de compra en backend;
4. conciliación, devolución, fraude, impuestos y soporte por mercado;
5. pruebas de idempotencia y recuperación.

El libro mayor está desacoplado mediante `WalletFundingProvider`, por lo que un
proveedor autorizado puede incorporarse sin reescribir el cálculo de saldo.

## Mapa

El renderizador es MapLibre Native. El estilo se configura con:

```text
RIDE_MAP_STYLE_URL
```

El valor piloto usa OpenFreeMap. Antes de tráfico mundial se debe operar una
infraestructura propia o contratada con SLA, atribución y presupuesto. El
renderizador permite migrar a PMTiles/OpenMapTiles y ruteo Valhalla sin cambiar
el dominio del viaje.

No se deben usar los servidores públicos comunitarios de OpenStreetMap o
Nominatim como backend masivo.

## Privacidad y retención

- La ubicación se publica solo para participantes autenticados y viaje activo.
- Cada posición tiene secuencia, captura y caducidad máxima de cinco minutos.
- La evidencia mecánica se comparte por categoría.
- DTC y telemetría incluyen fuente y antigüedad.
- Ausencia de DTC no significa “vehículo seguro”.
- No se comparte VIN completo, documentos o historial de rutas en el mapa.
- La finalización o cancelación revoca la selección local del viaje.

## Puertas de lanzamiento

No autorizar pasajeros reales hasta que estén verdes:

1. identidad remota y revisión administrativa;
2. repositorio Android ↔ RPC/Realtime con pruebas de dos dispositivos;
3. proveedor de pagos/recarga legal y conciliado;
4. centro de seguridad, soporte y respuesta a emergencias por país;
5. seguro, términos, privacidad y obligaciones de transporte de Costa Rica;
6. ruteo/ETA/geocodificación con SLA;
7. pruebas de carga, pérdida de señal, doble aceptación, reintentos y fraude;
8. observabilidad, alertas y rollback;
9. accesibilidad y pruebas en teléfonos de gama baja;
10. piloto cerrado antes de expansión.

## Verificación local

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Verificación transversal:

```bash
bash tests/parity/ci-verify.sh
```

Prueba en dispositivo cuando esté disponible:

```bash
adb install -r -d android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.elysium369.meet/.MainActivity
adb shell dumpsys activity activities
adb shell pidof com.elysium369.meet
adb logcat -d -t 300
```
