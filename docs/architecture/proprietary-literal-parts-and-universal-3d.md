# Catalogo propietario literal y motor 3D universal

**Fecha:** 2026-07-16  
**Estado:** aprobado por el propietario para implementacion completa  
**Fuentes autoritativas del propietario:** `Document (16).docx` y `Document (17).docx`

## Objetivo

Integrar el contenido completo de ambos documentos en Piezas y en Diagnostico Visual 3D sin resumir, corregir ni sustituir el texto propietario. La aplicacion debe presentar el perfil principal como **Hyundai Accent/Verna 2005 · caja automatica · motor 1600 cc**. Cuando la fuente indique que ese vehiculo no equipa una pieza, se conserva literalmente el caso real que el documento usa como referencia.

## Invariantes de datos

1. Cada bloque extraido conserva sin cambios `text`, `textHash`, `blockId`, `order`, `kind`, `sectionPath`, documento y SHA-256 del documento.
2. La suma de bloques empaquetados debe ser exactamente 74.648: 44.106 de `Document (16).docx` y 30.542 de `Document (17).docx`.
3. Ningun bloque se elimina por no ser una pieza. Procedimientos, tablas, advertencias, casos reales y reglas se conservan como registros literales relacionados.
4. Las entidades visuales son indices sobre el corpus, no reescrituras. Su nombre visible es el texto original del encabezado o linea BOM que les dio origen.
5. `EXACT` sigue reservado para compatibilidad respaldada por la tupla cerrada o evidencia exigida por `AGENTS.md`. La aplicacion puede mostrar literalmente una afirmacion de la fuente sin elevar por ello el estado computado de compatibilidad.
6. La procedencia visible es `FUENTE PROPIETARIA DEL USUARIO`; no se presenta el corpus como dato inventado, generico ni generado por IA.

## Arquitectura

```text
DOCX propietario inmutable
  -> extraccion determinista existente
  -> generador de corpus literal
       |-> manifest.json
       |-> entity_index.json
       `-> sections/<documento>-<seccion>-<fragmento>.json
             |-> Android AssetManager (carga perezosa)
             `-> Web public assets (fetch perezoso)
  -> Piezas: sistemas, busqueda, detalle literal y casos reales
  -> Motor 3D universal: escena procedural por sistema y entidad
```

El manifiesto y el indice son compactos. Los 23 MB aproximados de texto fuente se dividen en fragmentos de tamano acotado; Android y web cargan solamente el fragmento que el usuario abre. Esto evita congelar la interfaz o duplicar el corpus completo en memoria.

## Clasificacion sin perdida

Cada bloque recibe un `recordRole`:

- `SECTION_TITLE`: limite o titulo documental.
- `COMPONENT`: encabezado de componente, linea BOM o nombre de pieza detectado.
- `REAL_CASE`: ejemplo o referencia real expresamente nombrada por la fuente.
- `TABLE`: tabla literal.
- `SOURCE_DETAIL`: cualquier otro dato, paso, advertencia o explicacion.

La clasificacion ayuda a navegar, pero no cambia el texto. Los bloques ambiguos permanecen en `SOURCE_DETAIL` y siguen disponibles mediante su seccion, busqueda contextual y detalle 3D.

## Contrato visual 3D

- Cada entidad `COMPONENT` tiene `sceneId`, `nodeId` y una semilla determinista calculada desde su ID.
- Los sistemas cubiertos incluyen estructura, motor, admision, sobrealimentacion, transmision, suspension, direccion, frenos, ruedas, electricidad, ECUs, sensores, actuadores, iluminacion, HVAC, seguridad pasiva, ADAS, carroceria, limpiaparabrisas, interior, infotainment, cierre, hibridos/EV, fluidos y hardware.
- Una escena muestra un conjunto acotado de nodos del sistema para conservar fluidez. La pieza seleccionada siempre se incluye, se centra y se resalta aunque no este en la primera pagina visual.
- La geometria es un **esquema procedural**, no una malla OEM ni una afirmacion dimensional.
- El visor mantiene movimiento continuo, pulso de seleccion, rejilla, profundidad, sombras y colores neon diferenciados por sistema, con controles de rotacion, zoom y vista de servicio.
- El panel asociado al nodo muestra el nombre original, caso real si existe, documento, orden, SHA abreviado y texto literal del fragmento.

## Aceptacion

- El generador es determinista y valida los dos SHA-256 de origen.
- Todos los bloques aparecen una sola vez en los fragmentos y conservan su hash.
- Todo ID del indice apunta a un bloque y fragmento existentes.
- Todos los sistemas tienen escena 3D y toda entidad puede convertirse en nodo de forma determinista.
- El perfil visible usa exactamente `Hyundai Accent/Verna 2005 · caja automatica · motor 1600 cc`.
- Los casos reales no aplicables se muestran con el texto original del documento.
- Busqueda, filtros, detalle literal y apertura 3D funcionan en Android y web.
- Pruebas Python, web, Kotlin, paridad, build APK y smoke test quedan verdes.
