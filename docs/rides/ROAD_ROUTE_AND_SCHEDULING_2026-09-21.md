# Ruta vial y programación de viajes — 2026-09-21

## Resultado

- El selector de recogida y destino usa el mismo proveedor vial resiliente que el mapa principal.
- Mientras la ruta vial se calcula o no está disponible, el selector no dibuja una línea recta que pueda confundirse con una ruta transitable.
- El cálculo se actualiza con una pausa breve al mover el pin para evitar saturar el proveedor de rutas.
- La programación exige origen y destino seleccionados desde la búsqueda geográfica; ya no crea coordenadas de ejemplo.
- El flujo permite elegir fecha, hora, viaje único o recurrente y añadir indicaciones de recogida.
- La validación de dominio rechaza coordenadas no finitas o fuera del rango mundial.

## Límite de autoridad

Un viaje programado queda persistido en Room como intención del usuario. La interfaz no afirma que exista conductor reservado: cualquier asignación o cambio operativo debe regresar desde la autoridad del servidor, conforme a la constitución de Viajes.

## Verificación

- `RideMapModelsTest`: el modo vial no admite una línea recta de respaldo.
- `RideScheduleEngineTest`: rechaza ubicaciones sin resolver o inválidas.
- `verify-rides.sh fast` y compilación Android deben permanecer verdes antes de publicar el APK.
