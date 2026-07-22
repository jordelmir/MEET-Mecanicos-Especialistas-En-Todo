# MEET Android 4.0.0 - Atlas 3D de sistemas

Fecha: 2026-07-18  
Estado: pre-release tecnico, APK debug para validacion  
Version Android: `4.0.0` (`versionCode 16`)

## Entrega

Esta entrega integra en una sola APK los avances locales pendientes de
reportes, marketplace, conocimiento propietario, IA, OBD, monetizacion y el
nuevo atlas 3D mecanico. El visor deja de limitar el detalle al motor L4 y
extiende el mismo contrato de inspeccion a:

- admision y sobrealimentacion;
- transmision automatica y tren motriz;
- suspension delantera y trasera;
- direccion, frenos y ruedas;
- bateria, fusibles, reles y distribucion de potencia;
- arneses y conectores;
- ECU/ECM, TCM y control ABS;
- sensores y actuadores.
- iluminación, HVAC, seguridad pasiva y ADAS;
- carrocería, limpiaparabrisas, interior, infotainment y acceso;
- híbridos/EV, fluidos y desgaste, fasteners, sellos y hardware;
- índice funcional y reglas como topología informativa.

## Activos 3D nuevos

| Activo | Mallas | Triangulos | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `intake_boost` | 76 | 28,304 | 1,683,848 | `c4bbae8a208de48695e7715a607451524651bf4df0c34e06ff010789ed5ee9d9` |
| `transmission_drivetrain` | 109 | 45,548 | 3,122,388 | `38e5496f2d01c1606dca9327e33637b499ecf61a3a0e3fe285ce43e3a443954c` |
| `suspension` | 58 | 25,920 | 760,204 | `9207dd60ca5afde166e1076de294f829946de114f227888a09aed72e537d8ee6` |
| `steering_brakes_wheels` | 145 | 52,668 | 3,982,412 | `a0321724ed261a7a3aec70c645b8b58e387ff7856d7e0b430337d0126001e484` |
| `electrical_control` | 140 | 59,684 | 5,294,060 | `c3b49d0e94f2f91a8e24585caf83e0c8cd59f749532035435a44eba7f3fe4ce8` |

Los cinco GLB iniciales fueron elevados posteriormente a detalle D3 y se
complementan con trece GLB extendidos. Los nuevos activos suman aproximadamente
17 MB y se cargan por sistema activo. Sus
manifiestos declaran que no son mallas OEM, dimensionales ni de fabricacion.

## Atlas extendido

| Activo | Mallas | Triángulos | Bytes |
|---|---:|---:|---:|
| `lighting` | 82 | 21,908 | 1,108,416 |
| `hvac` | 98 | 20,056 | 1,527,776 |
| `passive_safety` | 69 | 16,600 | 939,372 |
| `adas` | 69 | 13,880 | 1,309,452 |
| `body` | 117 | 16,968 | 1,337,620 |
| `wipers` | 46 | 10,048 | 696,072 |
| `interior` | 72 | 13,400 | 1,216,056 |
| `infotainment` | 75 | 15,976 | 1,287,072 |
| `access` | 36 | 7,720 | 736,848 |
| `hybrid_ev` | 133 | 25,744 | 2,034,120 |
| `fluids` | 76 | 19,288 | 1,753,648 |
| `hardware` | 61 | 4,832 | 288,860 |
| `functional_overview` | 71 | 13,684 | 987,336 |

El generador extendido produce también el contrato Kotlin, por lo que las
familias de malla, alias literales, etapas y rutas se versionan juntas. Los
subconjuntos seleccionados en pantalla limitan las familias visibles del GLB.

## APK

- archivo local de construccion: `android/app/build/outputs/apk/debug/app-debug.apk`;
- tamaño: `136,229,830` bytes;
- SHA-256: `a247a6a7fd10d2d73b941f863e30a547e1c8ee71e5846256577d225b976fdaa1`;
- instalacion en emulador Android API 35: `adb install -r -d`, resultado `Success`;
- arranque frio: actividad `com.elysium369.meet/.MainActivity`, resultado `Status: ok`;
- instalacion fisica de este hash exacto: pendiente de reconectar el Android.

## Conocimiento propietario buscable

- indice offline FTS4 sobre los 74.648 bloques literales;
- 4.753 piezas, 297 casos reales y 77 tablas localizables por contenido;
- filtros por sistema y rol documental;
- tablas renderizadas por filas y celdas sin cambiar el texto fuente;
- resultados con documento, orden, bloque y SHA-256;
- contexto IA citado disponible desde Piezas y Motor 3D para cualquier entidad
  propietaria;
- indice comprimido de 8.622.091 bytes, validado contra el SHA del corpus antes
  de abrirse.

## Validacion

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: correcto;
- pruebas de contratos/activos 3D: formato GLB 2, familias requeridas, SHA-256,
  limite movil y aliases literales: correctas;
- generación determinista de los trece activos: correcta;
- auditoría WebGL 1440x1000 y 390x844: 13/13 cargados, canvas no vacíos;
- `bash tests/parity/ci-verify.sh`: TypeScript y Kotlin identicos;
- smoke test Android API 35: busqueda `osciloscopio` devuelve 115 bloques,
  abre la tabla exacta, muestra sus celdas, navega a la pieza relacionada y
  presenta contexto IA citado sin excepciones SQLite ni cierre de proceso;
- `npm test`: 180 pruebas correctas;
- `npm run build`: correcto;
- sincronizacion Codex/Mavis/Google Antigravity: `ALREADY_INTEGRATED`.

## Limites honestos

La APK publicada es una compilacion debug para inspeccion y pruebas. No
sustituye un AAB/APK firmado de produccion. El atlas es L2 generico: ayuda a
identificar, seleccionar y comprender orden de servicio, pero cualquier dato
dimensional, compatibilidad instalada o fabricacion requiere evidencia
adicional y confirmacion fisica.
