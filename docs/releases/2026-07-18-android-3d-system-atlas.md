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

## Activos 3D nuevos

| Activo | Mallas | Triangulos | Bytes | SHA-256 |
|---|---:|---:|---:|---|
| `intake_boost` | 76 | 28,304 | 1,683,848 | `c4bbae8a208de48695e7715a607451524651bf4df0c34e06ff010789ed5ee9d9` |
| `transmission_drivetrain` | 109 | 45,548 | 3,122,388 | `38e5496f2d01c1606dca9327e33637b499ecf61a3a0e3fe285ce43e3a443954c` |
| `suspension` | 58 | 25,920 | 760,204 | `9207dd60ca5afde166e1076de294f829946de114f227888a09aed72e537d8ee6` |
| `steering_brakes_wheels` | 145 | 52,668 | 3,982,412 | `a0321724ed261a7a3aec70c645b8b58e387ff7856d7e0b430337d0126001e484` |
| `electrical_control` | 140 | 59,684 | 5,294,060 | `c3b49d0e94f2f91a8e24585caf83e0c8cd59f749532035435a44eba7f3fe4ce8` |

Los cinco GLB suman menos de 16 MB y se cargan por sistema activo. Sus
manifiestos declaran que no son mallas OEM, dimensionales ni de fabricacion.

## APK

- archivo local de construccion: `android/app/build/outputs/apk/debug/app-debug.apk`;
- tamano: `122,163,215` bytes;
- SHA-256: `cf60602ff7f4f1027acc7b92f0c607a15fd5e31519287bca87ebc510d7b4b163`;
- instalacion fisica: `adb install -r -d`, resultado `Success`;
- arranque frio: actividad `com.elysium369.meet/.MainActivity`, resultado `Status: ok`.

## Validacion

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: correcto;
- pruebas de contratos/activos 3D: formato GLB 2, familias requeridas, SHA-256,
  limite movil y aliases literales: correctas;
- `bash tests/parity/ci-verify.sh`: TypeScript y Kotlin identicos;
- `npm test`: 180 pruebas correctas;
- `npm run build`: correcto;
- sincronizacion Codex/Mavis/Google Antigravity: `ALREADY_INTEGRATED`.

## Limites honestos

La APK publicada es una compilacion debug para inspeccion y pruebas. No
sustituye un AAB/APK firmado de produccion. El atlas es L2 generico: ayuda a
identificar, seleccionar y comprender orden de servicio, pero cualquier dato
dimensional, compatibilidad instalada o fabricacion requiere evidencia
adicional y confirmacion fisica.
