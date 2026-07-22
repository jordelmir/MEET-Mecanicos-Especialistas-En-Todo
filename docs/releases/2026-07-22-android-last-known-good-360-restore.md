# MEET Android 4.0.0 - restauración estable Motor 3D 360°

Fecha: 2026-07-22  
Estado: pre-release técnica, APK debug verificada en Android físico  
Versión Android: `4.0.0` (`versionCode 16`)  
Tag GitHub: `v4.0.0-360-restore.1`

## Motivo de esta entrega

Esta entrega recupera el último estado conocido como bueno antes de las
regeneraciones posteriores que degradaron la base propietaria, el catálogo de
piezas y el contexto de IA. La restauración parte de `c520c430` y reaplica
únicamente el conjunto verificado del Motor 3D 360° en `978b76e7`.

Los cambios posteriores no se eliminaron sin respaldo: permanecen aislados en
la rama local `codex/rescue-before-restore-20260722`, commit `7863e852`, para
una posible recuperación selectiva. Esa rama no forma parte de esta release.

## Estado funcional recuperado

- órbita horizontal y vertical continua de 360°;
- captura del gesto desde el primer contacto hasta retirar el último dedo;
- bloqueo del scroll de página cuando el gesto comienza dentro del visor 3D;
- zoom de inspección hasta el interior del vehículo y del motor;
- motor L4 genérico `1.2.0` con 346 mallas, 184.788 triángulos y 64 familias;
- bujías, bobinas, arneses, inyectores MPI, riel, alternador, arranque,
  transmisión auxiliar, mangueras y sensores principales;
- SHA-256 del GLB del motor:
  `7c3ff3f6c98c99b210b2e34bc88652e995d224ea0d5e3d2b99fff1b63f73020f`;
- catálogo propietario, piezas y contexto IA devueltos al estado estable previo.

## APK publicada

- archivo fuente: `android/app/build/outputs/apk/debug/app-debug.apk`;
- tamaño: `142.133.109` bytes;
- SHA-256:
  `ea585088e62589174fbd8a4852d4d738f56b9e2b5be260a17d56a51d5185b04e`;
- application ID: `com.elysium369.meet`;
- actividad: `com.elysium369.meet/.MainActivity`.

El APK se compiló antes de este cambio documental y no se volvió a ensamblar.
Por tanto, el activo publicado es exactamente el binario instalado y aceptado
en el Android físico, no una recompilación posterior.

## Validación

- `npm test`: 12 archivos y 180 pruebas correctas;
- `./gradlew clean :app:testDebugUnitTest`: correcto desde cero;
- `bash tests/parity/ci-verify.sh`: TypeScript y Kotlin idénticos;
- `./gradlew :app:assembleDebug`: correcto;
- `adb install` mediante transferencia local + `pm install -r -d`: `Success`;
- arranque frío y caliente: `Status: ok`;
- proceso estable durante seis comprobaciones consecutivas de cinco segundos;
- actividad confirmada en primer plano y ausencia de `FATAL EXCEPTION`, ANR y
  `AndroidRuntime` para el lanzamiento verificado.

Durante la prueba el teléfono registró fallos de resolución DNS contra el host
de Supabase. No causaron cierre y no se modificaron credenciales ni datos. La
disponibilidad del servicio remoto debe tratarse como una comprobación externa,
separada de la integridad del APK restaurado.

## Compatibilidad, datos y rollback

La instalación usó `-r -d`, por lo que conservó los datos existentes de la app;
no se ejecutó `pm clear` ni se desinstaló el paquete. Para volver al código
anterior basta con cambiar a `codex/detailed-3d-mechanical-systems`; para auditar
las modificaciones descartadas se usa la rama local de rescate indicada arriba.

Esta es una compilación debug para validación técnica. No sustituye un APK/AAB
firmado de producción ni eleva las mallas ilustrativas a geometría OEM.
