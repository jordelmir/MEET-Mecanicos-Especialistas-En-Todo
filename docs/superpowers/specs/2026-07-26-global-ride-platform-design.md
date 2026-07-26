# MEET Viajes — Diseño de plataforma mundial de movilidad

Fecha: 2026-07-26

Estado: aprobado por orden final del propietario

Mercado piloto: Costa Rica

Alcance de arquitectura: mundial

## 1. Objetivo

Convertir `PRO → Red de Reparación → Viajes / Ride` en una plataforma real de
movilidad para pasajeros y conductores, integrada con el circuito mecánico de
MEET. La experiencia debe conservar lo que ya funciona —ofertas, contraofertas,
chat, calificación y seguimiento del viaje— y sustituir progresivamente los
datos locales de demostración por identidad, despacho, ubicación y contabilidad
verificables.

El producto no afirmará que un vehículo es seguro por ausencia de DTC ni
presentará telemetría simulada como real. Todo dato mecánico compartido mostrará
fuente, vehículo, instante de captura y antigüedad.

## 2. Enfoques considerados

### A. Mejorar únicamente la pantalla y la base Room actual

Es el camino más rápido y permite una demostración visual, pero no crea una red
entre teléfonos, no impide manipulación de tarifas o saldo y no sirve para un
lanzamiento real.

### B. Conectar el monolito actual directamente a Supabase

Permite multiusuario temprano, pero mezcla UI, ubicación, dinero, privacidad y
persistencia dentro de `RideServiceScreen` y `ObdViewModel`. Aumenta el riesgo de
condiciones de carrera, filtración de ubicación y cobros duplicados.

### C. Plataforma modular con servidor autoritativo y caché offline

Es el enfoque seleccionado. Supabase/Postgres gobierna identidad, estados,
ofertas, consentimiento y contabilidad; Room conserva una proyección offline;
MapLibre renderiza; los proveedores de mapas, ruteo, geocodificación y recarga
son intercambiables. Requiere más estructura inicial, pero evita otra
reescritura cuando MEET escale.

## 3. Principios no negociables

1. Costa Rica primero, sin acoplar moneda, idioma, documentos o emergencias a un
   único país.
2. El servidor es autoridad para viajes, permisos, saldo y transiciones.
3. Ningún cobro se decide solo en el cliente.
4. La comisión es exactamente 5 % (`500` puntos básicos) y se liquida una sola
   vez al completar.
5. Ubicación exacta y mecánica del vehículo son privadas por defecto.
6. El conductor controla voluntariamente la publicación de telemetría, DTC,
   mantenimiento y repuestos.
7. El pasajero conoce qué se comparte, su fuente y su vigencia.
8. Cancelaciones de seguridad nunca reciben una penalización automática.
9. No se usan servidores públicos comunitarios de OSM/Nominatim como
   infraestructura de producción.
10. No se activa un método de pago que incumpla la política de la tienda o la
    regulación del mercado.

## 4. Arquitectura

### 4.1 Android

- `RidePresentation`: pantallas Compose, accesibilidad, modo pasajero/conductor.
- `RideDomain`: dinero, estados, permisos, cancelaciones y reglas puras.
- `RideRepository`: interfaz única para comandos y proyecciones.
- `RideLocalData`: Room como caché, cola de salida y último estado confirmado.
- `RideRemoteData`: PostgREST, RPC autoritativas y Realtime.
- `RideMap`: MapLibre Native y adaptadores de estilo/rutas/geocodificación.
- `RideLocation`: GPS, precisión, antigüedad, reducción de frecuencia y
  publicación autorizada.
- `RideVehicleTrust`: consentimiento y resumen mecánico verificable.
- `RideWallet`: lectura del libro mayor y solicitudes de recarga.

La pantalla monolítica actual se mantiene durante la transición y se divide por
fronteras, sin eliminar flujos integrados.

### 4.2 Backend

Postgres/Supabase contiene:

- perfiles de movilidad;
- vehículos habilitados para conducir;
- solicitudes, ofertas y asignaciones;
- eventos inmutables y estado materializado del viaje;
- posiciones efímeras;
- consentimiento por viaje y categoría;
- preguntas mecánicas;
- libro mayor, reservas, recargas y comisión;
- cancelaciones, seguridad, calificaciones y disputas.

Las operaciones críticas usan funciones RPC transaccionales e idempotentes.
RLS limita cada fila al pasajero, conductor asignado o rol operativo autorizado.
No se distribuye `service_role` dentro de Android.

### 4.3 Tiempo real

- Postgres/RPC conserva el estado durable.
- Broadcast privado transmite posiciones y telemetría de alta frecuencia.
- Presence indica disponibilidad/conectividad, no movimiento.
- Cada mensaje lleva `trip_id`, secuencia, instante, precisión y caducidad.
- Al reconectar, el cliente descarta eventos viejos y consulta el estado
  autoritativo antes de continuar.

## 5. Mapa mundial y navegación

MapLibre Native será el renderizador. La configuración admite:

- proveedor visual remoto durante la etapa piloto;
- estilo MEET propio;
- mosaicos PMTiles/OpenMapTiles propios;
- caché regional y futura navegación offline;
- Valhalla propio para ruta, ETA, matriz, map matching y alternativas;
- geocodificador propio antes de operación masiva.

Marcadores distintos:

- pasajero GPS en vivo;
- punto de recogida elegido;
- destino;
- conductor;
- ruta planificada y recorrido real.

El conductor ve el GPS exacto del pasajero solamente después de aceptar y hasta
finalización/cancelación. El pasajero ve al conductor desde la aceptación. Una
barra de precisión distingue posición exacta, aproximada, antigua o sin señal.
El punto elegido no se sustituye silenciosamente por el GPS: ambos se muestran.

## 6. Máquina de estados del viaje

Estados:

`DRAFT → SEARCHING → OFFERED → ASSIGNED → DRIVER_EN_ROUTE → ARRIVED →
PASSENGER_ONBOARD → IN_PROGRESS → COMPLETED`

Salidas laterales:

`CANCELLED`, `EXPIRED`, `DISPUTED`, `SAFETY_HOLD`.

Reglas principales:

- solo el pasajero crea y confirma su solicitud;
- solo un conductor elegible puede ofertar;
- aceptar una oferta asigna un único conductor de forma atómica;
- llegada, PIN de abordaje e inicio son eventos separados;
- completar exige viaje iniciado, tarifa final confirmada y clave de
  idempotencia;
- ninguna aplicación puede saltar estados escribiendo directamente una fila;
- todo cambio guarda actor, razón, versión, instante y correlación.

## 7. Saldo, regalía y comisión

### 7.1 Dinero

Todo importe se almacena en unidad menor y moneda ISO‑4217. No se usa `Double`.
Cada mercado define:

- moneda de cobro;
- saldo promocional inicial;
- productos de recarga permitidos;
- redondeo;
- impuestos y restricciones;
- proveedor de recarga habilitado.

Costa Rica inicia con `100000 CRC` de saldo promocional por conductor elegible.
Otros países deben tener una equivalencia configurada y auditada; no se
inventan tasas. La configuración puede empaquetarse como respaldo firmado en la
APK, pero el servidor conserva la versión autoritativa.

### 7.2 Libro mayor

El saldo nunca es un número mutable aislado. Se deriva de asientos inmutables:

- `PROMOTIONAL_GRANT`;
- `TOP_UP_PENDING`;
- `TOP_UP_CONFIRMED`;
- `COMMISSION_RESERVED`;
- `COMMISSION_CAPTURED`;
- `COMMISSION_RELEASED`;
- `REFUND`;
- `ADJUSTMENT`, solo con motivo y actor operativo.

Al aceptar una oferta se reserva 5 % de la tarifa acordada sin descontarlo. Al
completar, la reserva se captura una sola vez. Al cancelar o expirar se libera.
La comisión se calcula con `500` puntos básicos y regla de redondeo declarada
por moneda. Un conductor sin saldo disponible suficiente no puede aceptar otro
viaje, pero nunca se abandona un viaje ya iniciado.

El saldo promocional:

- no es efectivo;
- no se transfiere ni retira;
- no se compra;
- se consume antes o según política explícita frente al saldo recargado;
- no puede concederse dos veces a la misma identidad.

### 7.3 Recarga y Google Play

`WalletFundingProvider` desacopla el libro mayor del medio de compra. Se
implementan contratos para catálogo, compra, confirmación y restauración.

Google Play Billing permanece detrás de `PLAY_BILLING_POLICY_APPROVED`. Google
indica que Play Billing no admite pagos por servicios físicos como transporte;
por ello MEET no activará en producción créditos de comisión mediante Play
Billing sin una aprobación o clasificación válida. Los tokens de compra, si el
proveedor resulta autorizado, siempre se verifican en backend antes de acreditar
saldo y nunca se confía en la respuesta local.

## 8. Privacidad y pasaporte mecánico

El consentimiento se configura por viaje:

- ubicación exacta;
- telemetría básica;
- DTC activos;
- historial DTC;
- mantenimiento;
- repuestos instalados;
- reportes certificados.

Todos comienzan apagados salvo la ubicación estrictamente necesaria para
ejecutar el viaje, que se explica y limita temporalmente. El conductor obtiene
una vista previa exacta antes de publicar y puede revocar categorías mecánicas
en cualquier momento.

Telemetría permitida cuando existe fuente real:

- velocidad;
- RPM;
- temperatura de refrigerante;
- voltaje;
- nivel de combustible cuando el PID es compatible;
- estado de conexión, precisión y antigüedad.

La vista del pasajero muestra `Dato no capturado`, `OBD no disponible`,
`Confianza limitada` o `Requiere prueba física` cuando corresponde.

El pasaporte del vehículo puede mostrar:

- marca/modelo/año y variante seleccionada;
- estado de verificación de documentos sin exponer sus imágenes;
- momento del último escaneo;
- resumen de DTC con severidad, no una garantía de seguridad;
- mantenimiento/repuestos con evidencia;
- vínculos a reportes certificados respetando su payload QR mínimo.

VIN, placa, teléfono, domicilio, documentos e historial de rutas no se publican
por defecto.

## 9. Preguntas y comunicación

El pasajero puede preguntar al conductor sobre el vehículo dentro del viaje.
Las respuestas son mensajes del conductor, no certificaciones de MEET. Los
datos mecánicos anexados incluyen referencia a su evidencia. Se conservan chat,
mensajes rápidos y audio, con límites, estado de entrega y reporte de abuso.

## 10. Seguridad y cancelaciones

Funciones:

- PIN/QR de abordaje;
- contacto de confianza y compartir viaje;
- botón de emergencia configurable por país;
- comprobación de identidad y vehículo;
- alerta por desviación de ruta, parada prolongada o pérdida de señal;
- llamada/chat sin publicar números cuando exista proveedor;
- registro inmutable de eventos relevantes;
- centro de incidentes y disputa.

Razones de cancelación normalizadas:

- preocupación de seguridad;
- menor no acompañado;
- falta de silla infantil requerida;
- más pasajeros que cinturones disponibles;
- conductor/pasajero no coincide;
- vehículo no coincide;
- acoso o conducta inapropiada;
- objeto o actividad prohibida;
- punto inaccesible o peligroso;
- emergencia médica;
- avería o condición insegura del vehículo;
- pasajero/conductor no aparece;
- espera excesiva;
- ubicación o destino incorrecto;
- cambio de planes;
- solicitud duplicada o accidental;
- otro motivo con texto acotado.

La UI explica quién canceló y qué efecto tendrá antes de confirmar. Casos de
seguridad se enrutan a revisión y no generan castigo automático.

## 11. Experiencia de pasajero

1. Confirmar identidad y ubicación.
2. Elegir destino por búsqueda, mapa o favorito.
3. Ver ruta, ETA, rango transparente y oferta propia.
4. Comparar conductor, vehículo, ETA, calificación y datos compartidos.
5. Seguir al conductor en mapa.
6. Verificar PIN/QR.
7. Consultar estado del viaje, ruta y telemetría consentida.
8. Usar seguridad/chat/cancelación.
9. Recibir resumen, tarifa, comisión informativa cuando aplique y calificar.

## 12. Experiencia de conductor

1. Completar identidad y documentos.
2. Seleccionar vehículo habilitado.
3. Configurar qué datos mecánicos comparte.
4. Ver saldo promocional/recargado y disponibilidad.
5. Ponerse en línea.
6. Recibir solicitudes cercanas y ofertar/aceptar.
7. Ver GPS del pasajero y punto de recogida como marcadores separados.
8. Navegar, confirmar llegada y PIN.
9. Completar viaje.
10. Ver tarifa, comisión 5 %, saldo y asiento asociado.

No habrá `AUTO-APROBAR (DEV)` en variantes publicables.

## 13. Globalización

- idiomas y texto fuera de lógica;
- moneda/decimales mediante ISO‑4217;
- unidades, formato de dirección y teléfonos por región;
- zonas horarias IANA y fechas UTC;
- documentos y verificación configurables;
- números de emergencia y razones regulatorias por mercado;
- capacidades habilitadas por configuración, no por bifurcaciones del código.

## 14. Errores y degradación

- Sin red: se muestra último estado confirmado y se encolan comandos seguros;
  no se simula búsqueda de conductor.
- GPS antiguo: marcador degradado con antigüedad visible.
- Mapa no disponible: direcciones textuales, direcciones guardadas y reintento.
- Ruteo no disponible: no se inventa ETA; aparece `Estimación no disponible`.
- OBD desconectado: se detiene el stream y se conserva el último valor marcado
  como antiguo.
- Saldo desconocido: se bloquea una nueva aceptación, no el viaje activo.
- Evento duplicado: la idempotencia devuelve el resultado ya confirmado.
- Conflicto de estado: se descarta la mutación local y se recarga el servidor.

## 15. Verificación

### Dominio

- 5 % exacto en monedas de 0, 2 y 3 decimales;
- captura única ante reintentos;
- liberación en todas las cancelaciones;
- regalía única;
- máquina de estados y transiciones denegadas;
- caducidad/revocación de consentimiento.

### Backend

- RLS positiva y negativa por pasajero, conductor, extraño y operador;
- dos conductores no pueden aceptar la misma solicitud;
- eventos y saldos no se editan ni eliminan desde el cliente;
- posiciones/telemetría solo llegan al participante autorizado;
- token de recarga repetido no acredita dos veces.

### Android

- UI pasajero/conductor;
- mapa y marcadores distinguibles;
- exactitud y antigüedad;
- estados offline/reconexión;
- accesibilidad, modo oscuro y tamaños de pantalla;
- ausencia de autoaprobación en release.

### Dispositivo

Cuando el teléfono esté disponible y a temperatura segura:

1. instalar APK con `adb install -r -d`;
2. iniciar y confirmar proceso/foreground;
3. recorrer pasajero y conductor;
4. verificar mapa, permisos, cancelaciones y consentimiento;
5. revisar `logcat` por cierres o ANR;
6. documentar cualquier límite que dependa de dos dispositivos o backend.

## 16. Entregas por fases

1. Contratos de dominio, dinero, estados, consentimiento y cancelaciones.
2. Migración Supabase con RLS/RPC y proyección Room.
3. Mapa MapLibre, ubicaciones separadas y ruteo abstraído.
4. Flujos completos pasajero/conductor y seguridad.
5. Pasaporte mecánico, telemetría voluntaria y preguntas.
6. Saldo, regalía, comisión y adaptador de recarga en modo cumplimiento.
7. Observabilidad, pruebas, documentación, compilación y prueba ADB.

Cada fase debe quedar compilable y con pruebas. Ninguna marca visual de
“completo” sustituye evidencia multiusuario, autorización, dispositivo y
backend.
