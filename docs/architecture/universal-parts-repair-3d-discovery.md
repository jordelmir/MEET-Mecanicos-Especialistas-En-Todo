# Descubrimiento: catalogo universal, reparaciones y motor 3D

**Fecha de corte:** 2026-07-16  
**Estado:** diseno aprobado para implementacion incremental  
**Vertical piloto:** Hyundai Accent / Verna 2005 1.6 AT, suspension delantera  
**Regla central:** todo dato tecnico debe conservar fuente, alcance, confianza y estado de revision.

## 1. Inventario real de fuentes

La carpeta solicitada contiene dos archivos y ningun modelo 3D, base SQLite, CSV ni archivo multimedia independiente:

| Fuente | SHA-256 | Bloques extraidos | Candidatos | Mediciones candidatas |
|---|---|---:|---:|---:|
| `Document (16).docx` | `09f2926a22542a4e7be24e50f2a4f4c42674f32958e8e541683fbb0cf76352d7` | 44,106 | 10,322 | 417 |
| `Document (17).docx` | `baf4add3f22202fc7d66f7b7f4aee549d90780f1891da6fa66ffbc2db1820824` | 30,542 | 7,134 | 147 |

El pipeline reproducible produjo 17,456 elementos de revision, cero elementos publicables automaticamente y cero contradicciones detectadas por las reglas actuales. Cero contradicciones detectadas no equivale a verificacion tecnica.

## 2. Hallazgos y riesgos

1. Los documentos son una fuente amplia de taxonomia, aliases, relaciones BOM y borradores de procedimiento.
2. No incluyen una cadena de autoridad independiente suficiente para declarar OEM, torque, pinout o compatibilidad exacta como verificados.
3. El catalogo web previo contenia 50 piezas marcadas `CONFIRMED`, todas con OEM y varios torques sin una referencia verificable enlazada. Esa ruta debe quedar fuera de produccion.
4. La app ya tiene motores 3D procedurales, catalogo visual, Parts Marketplace, conocimiento, reportes e historial. Crear otra arquitectura completa aumentaria divergencia.
5. Room esta en version 42 y el arbol compartido tiene trabajo activo. Una migracion de base de datos no es necesaria para demostrar esta vertical y ampliaria el riesgo.
6. No hay mallas OEM del Accent/Verna. La primera escena de suspension sera un esquema 3D generico, rotulado honestamente, con nodos semanticos estables.

## 3. Arquitectura seleccionada

```text
DOCX inmutable
  -> extraccion determinista + hashes
  -> candidatos de revision
  -> glosario curado de entidades
  -> pack piloto JSON (REVIEW_REQUIRED)
       |-> Web TypeScript
       |-> Android Kotlin
       |-> Busqueda / compatibilidad / procedimientos
       |-> bindings semanticos 3D
       `-> trazabilidad de fuente
```

Un solo pack versionado es la fuente compartida de la vertical. Web y Android no mantienen copias manuales de OEM, torques o procedimientos. Cada entidad conserva:

- identificador estable y aliases;
- sistema, subsistema, conjunto, posicion y tipo de parte;
- referencias a documento, hash, bloque, hash del texto y ruta de seccion;
- estado de publicacion y confianza;
- politica de compatibilidad y evidencias requeridas;
- binding 3D semantico y calidad visual declarada;
- especificaciones tecnicas anulables, nunca rellenadas por conveniencia.

## 4. Contratos funcionales

### Catalogo

- El pack piloto contiene al menos 50 entidades de suspension, direccion, freno y tren delantero.
- Una entidad puede existir como taxonomia con `UNVERIFIED` y `REVIEW_REQUIRED`.
- Busqueda indexa nombre, aliases, sistema, subsistema y texto fuente autorizado.
- Los identificadores son unicos y no cambian entre runtimes.

### Compatibilidad

- El resultado inicial es `REQUIRES_VERIFICATION`.
- `EXACT` requiere VIN + evidencia OEM, tupla cerrada aprobada o confirmacion visual documentada conforme a `AGENTS.md`.
- El selector del vehiculo no convierte por si solo una pieza generica en compatible exacta.

### Procedimientos

- Los pasos son atomicos y tienen precondiciones, advertencias, herramientas, evidencia y nodo 3D opcionales.
- El piloto se ejecuta como `TRAINING_ONLY_REVIEW_REQUIRED`.
- Un paso que requiere torque numerico queda bloqueado si no existe un claim verificado para la variante.
- El cierre exige inspeccion, alineacion cuando aplique y prueba final; no certifica una reparacion solo por pulsar una casilla.

### 3D

- Los nodos usan los mismos IDs semanticos que el catalogo.
- La escena inicial es `GENERIC_SCHEMATIC`, no una replica OEM ni una afirmacion dimensional.
- La seleccion desde catalogo abre la escena de suspension, enfoca el nodo y puede mostrar separacion/explosion didactica.

### Progreso

- Estados: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`.
- Android persiste progreso en `SharedPreferences`; web usa almacenamiento local versionado.
- Completar pasos no eleva confianza ni convierte un valor no verificado en verificado.

## 5. Vertical critica de aceptacion

```text
Piezas y reparaciones
  -> Suspension delantera
  -> Brazo inferior izquierdo / tijereta
  -> Estado de compatibilidad y evidencia faltante
  -> Ver ubicacion 3D
  -> Inspeccion o sustitucion guiada
  -> Desmontaje / montaje animado
  -> Torque bloqueado hasta fuente verificada
  -> Alineacion y prueba final
  -> Evidencia disponible para historial/reporte
```

La vertical falla si muestra un OEM o torque inventado, si llama exacta a la compatibilidad sin evidencia o si la escena generica se presenta como modelo OEM.

## 6. Plan de migracion conservador

1. Generar y validar el pack piloto desde las extracciones reproducibles.
2. Cambiar consumidores web al pack seguro y dejar de importar seeds tecnicos no trazables.
3. Añadir lector/validador Kotlin y persistencia ligera de progreso.
4. Integrar pantalla Android, navegacion y escena de suspension.
5. Agregar pruebas de contrato en Python, TypeScript y Kotlin.
6. Ejecutar paridad, pruebas, build web, build Android y smoke test en dispositivo si existe.
7. Solo despues evaluar persistencia Room/Supabase y modelos 3D firmados, con migracion y rollback propios.

## 7. Propiedad paralela A-G

| Frente | Propietario sugerido | Archivos criticos | Regla de union |
|---|---|---|---|
| A. Ingesta y pack | Codex | `tools/knowledge/**`, assets de catalogo | Determinismo y hashes |
| B. Dominio Android | Codex | `core/catalog/**` | Contrato igual al JSON |
| C. UI Android | Codex | pantalla, rutas, Home | No degradar rutas existentes |
| D. 3D semantico | Google Antigravity | `core/engine3d/**` | IDs del pack, no IDs paralelos |
| E. Web | Mavis | servicios/componentes TSX | Consumir el mismo pack |
| F. Reports/Marketplace | Mavis + Codex | reportes, compatibilidad, historial | A + B + sync, nunca elegir un lado |
| G. Verificacion | agente que integra | pruebas, parity, builds, ADB | Evidencia de comando y resultado |

El script de sync debe auditar worktrees Codex, Mavis y Google Antigravity. No mezcla ramas de respaldo por intuicion, no descarta un arbol sucio y solo crea `sync/codex-mavis-*` cuando hay una union real pendiente.

## 8. Definicion de terminado

- Pack determinista con 50 o mas piezas y referencias fuente validas.
- Cero OEM, torque, material o dimension publicados sin claim verificado.
- Tres o mas procedimientos conservadores con gates de seguridad.
- Busqueda, detalle, compatibilidad, ubicacion 3D y progreso funcionales.
- La tijereta izquierda completa la vertical critica en web y Android.
- Tests Python, TypeScript y Kotlin verdes.
- Paridad TS/Kotlin, build web y APK verdes.
- Resultado ADB documentado cuando haya dispositivo conectado.

