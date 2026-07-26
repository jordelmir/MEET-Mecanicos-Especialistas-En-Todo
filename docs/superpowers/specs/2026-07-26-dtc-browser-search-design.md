# Diseño — búsqueda web contextual por DTC

Fecha: 2026-07-26
Estado: aprobado por el usuario

## Objetivo

Cada resultado DTC de MEET debe ofrecer una acción visible para abrir una
búsqueda de Google en el navegador disponible en el dispositivo.

La consulta siempre contiene el DTC seleccionado. Cuando existe un vehículo
activo, también incluye únicamente los datos técnicos guardados para ese
vehículo:

- marca;
- modelo;
- año;
- transmisión manual o automática;
- cilindrada en centímetros cúbicos.

Sin vehículo activo, la consulta contiene solamente el DTC. La función no
incluye descripción, causas sugeridas, VIN, placa, usuario, ubicación ni
identificadores internos.

## Decisión de plataforma

Se usará un `Intent.ACTION_VIEW` navegable con una URL HTTPS de Google. No se
fijará el paquete de Chrome: Android abrirá Chrome, Firefox, Edge, Brave o el
navegador predeterminado disponible.

Esto conserva la intención del usuario —abrir Internet y ejecutar la
búsqueda— sin introducir una dependencia frágil de una marca de navegador.

Si ningún navegador puede atender HTTPS, MEET permanecerá estable y mostrará
un mensaje claro en vez de producir un crash.

## Contrato de consulta

El DTC se normaliza a mayúsculas y debe cumplir el formato OBD-II de cinco
caracteres: sistema `P`, `B`, `C` o `U` seguido de cuatro caracteres
hexadecimales.

Ejemplos:

```text
P0303
P0303 Hyundai Accent Verna 2005 automático 1600 cc
P0700 Toyota Corolla 2014 manual 1798 cc
```

Los campos vacíos o inválidos se omiten; nunca se sustituyen con valores
genéricos. La transmisión se presenta como `automático` o `manual` solo cuando
el valor persistido permite determinarlo.

La URL se construye con parámetros codificados:

```text
https://www.google.com/search?q=<consulta codificada>
```

No se concatenan fragmentos sin escapar y no se permite cambiar host, scheme
o path desde datos del usuario.

## Arquitectura

### `DtcGoogleSearchQueryBuilder`

Unidad Kotlin pura y sin dependencia Android. Recibe DTC y un contexto
vehicular mínimo. Devuelve una consulta válida o `null` si el DTC no es seguro.

El contexto mínimo evita acoplar la política de privacidad a Room, Supabase o
Compose. Un adaptador explícito traduce el vehículo activo al contexto
permitido.

### `DtcBrowserSearchLauncher`

Frontera Android responsable de:

1. convertir la consulta aprobada en una URL Google HTTPS;
2. crear un intent `ACTION_VIEW` con categoría `BROWSABLE`;
3. abrir el navegador del sistema;
4. capturar ausencia de actividad o rechazo de seguridad sin cerrar MEET.

### Superficies Compose

El botón `🔎 BUSCAR EN GOOGLE` aparece:

- en tarjetas de DTC activo;
- en tarjetas de DTC pendiente;
- en tarjetas de DTC permanente;
- en resultados de búsqueda manual.

La acción usa el mismo vehículo activo observado por `DtcScreen`. No consulta
VIN ni ejecuta red dentro de MEET; la navegación y la petición pertenecen al
navegador.

## Privacidad y límites

- No se envía VIN, placa, teléfono, GPS, notas o evidencia del diagnóstico.
- La búsqueda web es una fuente externa y no eleva la autoridad del grafo
  automotriz de MEET.
- Un resultado de Google no confirma una falla ni compatibilidad.
- La búsqueda no habilita automáticamente reemplazo o compra de repuestos.
- MEET no afirma que haya conexión: delega la URL al navegador, que presenta
  su propio estado offline.

## Verificación

Las pruebas unitarias cubren:

- DTC sin vehículo;
- DTC normalizado;
- vehículo automático;
- vehículo manual;
- cilindrada exacta;
- campos vehiculares parciales;
- transmisión desconocida;
- DTC inválido;
- exclusión de VIN y placa;
- host y path Google fijos.

La verificación de entrega incluye compilación Android, suite focalizada,
suite relevante, ensamblado APK e instalación/lanzamiento real por ADB.

## Rollback

La función es aditiva: retirar el botón y los dos componentes nuevos devuelve
el flujo anterior. No cambia Room, Supabase, reportes certificados, contratos
de hash, conocimiento automotriz ni Motor 3D.
