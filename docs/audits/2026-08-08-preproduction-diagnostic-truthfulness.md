# Auditoría preproducción y veracidad diagnóstica — 2026-08-08

## Alcance

Este corte responde a las auditorías de preproducción y del circuito
`OBD → DTC → interpretación → UI → conocimiento → 3D`. La prioridad fue cerrar
fallos P0/P1 verificables sin fingir que una refactorización completa del producto
queda terminada en un solo cambio.

## Invariantes aplicados

- Una ausencia de respuesta nunca equivale a “0 DTC”.
- Un DTC confirmado no se presenta automáticamente como falla actual.
- No observar un código en un escaneo posterior no demuestra reparación.
- Un Freeze Frame sólo se asocia cuando la identidad reportada coincide con el DTC solicitado.
- Una relación DTC–componente no confirma que esa pieza esté dañada.
- Una arquitectura de motor desconocida no se convierte silenciosamente en L4.
- Un fallo de IA nunca se presenta como “estado nominal”.
- Un reporte firmado no se modifica en silencio ni se encadena contra borradores.
- Producción no puede heredar autenticación simulada, RLS abierto ni firma debug.

## Correcciones implementadas

### Identidad, backend y release

- La pantalla Android usa autenticación real de Supabase por email para iniciar sesión y registrarse.
- La verificación local de Viajes queda cerrada por defecto.
- El SQL heredado inseguro se bloquea deliberadamente y una migración nueva aplica políticas RLS fail-closed.
- Las escrituras económicas sensibles quedan reservadas al servidor.
- La variante release ya no cae en la llave debug y falla si falta una configuración de firma completa.
- `targetSdk` sube a 36 y la versión queda en `4.13.0` (`versionCode 41`).
- Se añadió una compuerta CI para detectar regresiones de estos invariantes.

### Certified Reports

- Creación de borrador, snapshot, evidencias y acciones ocurre dentro de una transacción Room.
- La firma resuelve la punta vigente de la cadena dentro de la transacción.
- La cadena sólo considera reportes terminales firmados y se ordena por tiempo de firma.
- Se eliminó `REPLACE` del padre para evitar cascadas destructivas.
- Firma, actualización de reporte y anulación son operaciones atómicas.
- Room vuelve a exportar el esquema para que futuras migraciones sean auditables.

### OBD, UDS y calidad de escaneo

- Se conservan los ocho bits de estado UDS y se corrige su significado.
- `confirmedDTC` sin `testFailed` se clasifica como histórico, no como falla actual.
- Cada servicio produce un resultado tipado: completo, sin DTC, sin respuesta, no soportado,
  respuesta negativa, timeout, malformado, parcial o fallido.
- El reporte declara cobertura `COMPLETE`, `PARTIAL`, `INCONCLUSIVE` o `FAILED`, con advertencias.
- La autorresolución fue sustituida por `NOT_OBSERVED_LAST_SCAN`; requiere que el mismo módulo
  vivo haya completado el bucket correspondiente y nunca escribe `resolvedAt`.
- Los logs exportados incluyen cobertura, resultado por módulo, servicio y respuesta cruda.

### Freeze Frame y UI veraz

- Primero se consulta la identidad del Frame 0 y se descarta una asociación cruzada.
- Los bytes de número de frame no se interpretan como datos PID.
- El estado de Freeze Frame ya no reutiliza el estado de sincronización cloud.
- Se eliminaron “SISTEMA OK”, “sin fallas” y equivalentes cuando la evidencia sólo cubre DTC.
- La lista DTC vacía distingue visualmente entre sin escaneo, no concluyente, parcial y completo;
  nunca atribuye cobertura a módulos si todavía no existe un reporte de escaneo.
- Se retiraron del radar los blips estáticos ECU/TCU/ABS/MIL/SRS que simulaban módulos detectados.

### DTC → 3D

- Cada tarjeta DTC incorpora una acción primaria **Ver relaciones en 3D**.
- El código de entrada resalta todas las relaciones estructuradas, no una única pieza arbitraria.
- La UI declara que esas relaciones no prueban una pieza dañada.
- Una arquitectura no identificada usa `UNKNOWN` y muestra un esquema educativo no OEM.
- El Hyundai Accent/Verna 2005 1.6 DOHC conserva la resolución explícita L4 documentada.

## Pruebas de regresión añadidas

- Decodificación Mode 03 con y sin byte de conteo.
- Conservación de los ocho bits UDS.
- Confirmado sin falla actual.
- Diferencia entre respuesta ausente y respuesta positiva vacía.
- Identidad Freeze Frame con byte de número de frame.
- Un servicio fallido no puede marcar un DTC previo como no observado.
- Un servicio concluyente vacío sí puede marcarlo como no observado, sin resolverlo.
- Atomicidad y cadena de Certified Reports.

## Trabajo arquitectónico posterior, no presentado como terminado

Estas recomendaciones siguen siendo válidas y deben ejecutarse en cortes medibles:

1. Extraer `ObdViewModel`, `ObdSession` y `MainActivity` en casos de uso, coordinadores y rutas tipadas.
2. Construir `ScanPlanCompiler`, fingerprint de adaptador y modos Rápido/Completo con cobertura estimable.
3. Unificar las superficies Scanner/DTC bajo un único `DiagnosticStory` basado en eventos reales.
4. Crear `DtcSpatialProjection` con roles causa/síntoma/condición/circuito y caminos físicos.
5. Llevar Knowledge Fabric a un kernel compilado con jerarquía explícita de autoridad y procedencia.
6. Separar dominios Room y mover migraciones fuera del módulo DI monolítico.
7. Completar firma criptográfica fuerte con custodia de claves y verificación pública end-to-end.
8. Modularizar LiveLink, sincronización, IA híbrida, contenido 3D descargable y control plane.

Nada de lo anterior debe declararse listo hasta que tenga contrato, pruebas y evidencia de ejecución.
