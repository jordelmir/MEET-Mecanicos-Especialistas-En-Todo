# Atlas 3D extendido de sistemas del vehículo

**Estado:** Aprobado por orden autónoma del propietario, 2026-07-18  
**Alcance:** Completar las trece familias todavía representadas solo por esquemas procedurales.  
**Fuente literal:** corpus propietario derivado de `Document (16).docx` y `Document (17).docx`.

## Objetivo

MEET debe mostrar una representación 3D reconocible y cargada bajo demanda para cada sistema visible en el catálogo universal. La escena debe permitir seleccionar componentes con respaldo literal, aislarlos, enfocarlos y ejecutar un despiece progresivo por etapa de servicio.

Esta fase añade activos para:

1. Iluminación.
2. HVAC y climatización.
3. Seguridad pasiva.
4. ADAS y asistencia.
5. Carrocería exterior.
6. Limpiaparabrisas y lavado.
7. Interior.
8. Infotainment y comunicación.
9. Cierre, acceso e inmovilizador.
10. Híbridos y eléctricos.
11. Fluidos, consumibles y desgaste.
12. Fasteners, sellos y hardware.
13. Índice funcional y reglas.

## Decisión arquitectónica

Se generará un GLB independiente por sistema. `CompleteVehicleTwinView` continuará resolviendo el activo mediante `GenericVehicleSystemsAssetContract.assetForSystem(systemId)`, por lo que no se añade una ruta de render paralela ni una segunda fuente de verdad.

Cada activo tendrá:

- claves de malla estables con prefijo `system_mesh__`;
- grupos semánticos que representan los subconjuntos del manifiesto propietario;
- alias exactos tomados de `nameOriginal` para habilitar selección solo con evidencia fuente;
- etapas de servicio del 1 al 6 y offsets de despiece ilustrativos;
- manifiesto con hash SHA-256, conteos de mallas y triángulos;
- carga bajo demanda para evitar mantener los dieciocho GLB en memoria simultáneamente.

## Autoridad y honestidad

La autoridad geométrica permanece en `L2_GENERIC_CUTAWAY`. El nivel `D3_RECOGNIZABLE_INTERNALS` describe detalle visual, no metrología.

- No se afirmará que una pieza está instalada en el vehículo activo por aparecer en el atlas.
- No se publicarán medidas, materiales, torques, pinouts ni compatibilidades sin evidencia correspondiente.
- El sistema híbrido/EV mostrará arquitectura de alta tensión genérica y señalización de riesgo, nunca una instrucción para intervenir un vehículo energizado.
- Fluidos y consumibles se mostrarán como mapa de servicio; su presencia no constituye especificación de tipo, grado o volumen.
- El índice funcional será una topología informativa y no fingirá ser un conjunto físico.

## Lenguaje visual

La base seguirá siendo mecánica y legible: fundición oscura, aluminio, acero, cobre y polímeros. Los acentos tendrán significado:

- ámbar para potencia y alta corriente;
- naranja para alta tensión;
- cian para señal y diagnóstico;
- rojo para seguridad, fricción o riesgo;
- verde para control electrónico;
- blanco para luz, aire y superficies de inspección.

El brillo se limitará a lentes, indicadores, sensores y rutas activas. Las sombras y el contraste deben revelar volumen sin convertir toda la escena en neón uniforme.

## Cobertura por subconjunto

Cada sección del manifiesto debe quedar cubierta por al menos una clave semántica:

- iluminación: exterior e interior;
- HVAC: aire acondicionado, calefacción y ventilación;
- carrocería: paneles, puertas, vidrios y espejos;
- interior: tablero/controles, asientos y acabados;
- híbridos/EV: alta tensión y tracción eléctrica;
- las demás familias: todas sus secciones propietarias activas.

Los registros repetidos o narrativos conservados literalmente en los documentos continuarán visibles en la lista, pero solo los nombres que representan componentes físicos podrán reclamar una malla seleccionable.

## Rendimiento

- Máximo recomendado por activo nuevo: 5 MiB.
- Máximo agregado de los trece activos nuevos: 45 MiB.
- Cada activo debe contener al menos 20 mallas y 2,000 triángulos.
- La escena debe permanecer no vacía y correctamente encuadrada a 390x844 y 1440x1000.
- El cambio de sistema debe destruir nodos y materiales del activo anterior mediante el ciclo de vida existente.

## Verificación

1. Generar dos veces y comprobar hashes deterministas.
2. Validar manifiesto, claves requeridas, autoridad y límites de tamaño con pruebas Kotlin.
3. Verificar cobertura de los trece `systemId` y ausencia de colisiones.
4. Renderizar todos los GLB en una matriz Playwright de escritorio y móvil.
5. Comprobar píxeles no vacíos del canvas y revisar capturas.
6. Ejecutar pruebas Android, paridad TS/Kotlin y `assembleDebug`.
7. Instalar por ADB cuando exista un dispositivo conectado y revisar `FATAL EXCEPTION`.

## Criterio de finalización

La fase termina cuando los 26 sistemas del manifiesto tienen una representación especializada: vehículo de referencia, motor de cuatro cilindros o activo dedicado del atlas. Ninguna familia solicitada debe caer en la nube genérica como representación principal, y toda selección especializada debe seguir anclada a un registro literal del corpus.
