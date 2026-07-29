# Contrato de superficie de datos Android 4.6.1

Fecha: 2026-07-28

## Riesgos cerrados

- `AiTestReceiver` vive únicamente en el source set `debug`. El APK release no
  puede recibir el intent ADB que ejecuta pruebas de proveedores de IA.
- El respaldo administrado por Android queda deshabilitado para evitar que
  bases locales, evidencia, preferencias y secretos de aplicación entren en un
  backup genérico. Los flujos explícitos de exportación o nube de MEET no se
  modifican.
- `FileProvider` ya no comparte la raíz externa ni directorios completos con
  `path="."`. Solo autoriza reportes, manuales, exportaciones de telemetría,
  capturas de verificación y QR temporales.

## Compatibilidad

Las exportaciones CSV pasan a
`getExternalFilesDir(null)/TelemetryExports/`. Reportes, manuales, capturas y
QR conservan sus directorios existentes. Los URI siguen siendo temporales y
requieren `FLAG_GRANT_READ_URI_PERMISSION`.

## Compuerta automática

`AndroidDataSurfaceContractTest` impide reintroducir el receptor en `main`,
activar respaldos de plataforma o ampliar silenciosamente las raíces del
`FileProvider`.

El lint global tenía deuda previa: 21 errores, 1.272 advertencias y 49
sugerencias en esta fotografía. `lint-baseline.xml` conserva el inventario
visible y hace fallar cada incidencia nueva; no convierte las incidencias
registradas en código correcto. Los ciclos posteriores deben reducir esa línea
base sin ampliarla.
