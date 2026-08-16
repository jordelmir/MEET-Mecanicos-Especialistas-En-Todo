# Protocolo de verificación física Android

Este protocolo se ejecuta únicamente cuando el propietario vuelve a disponer
del dispositivo. No usa ni acepta emuladores.

## Preparación

1. Activar `Wireless debugging` en el Honor y conectarlo a la misma red del Mac.
2. Si el emparejamiento expiró, ejecutar `adb pair IP:PUERTO` e introducir el
   código temporal mostrado por Android.
3. Conectar al puerto principal mostrado por Android con `adb connect IP:PUERTO`.
4. Confirmar que `adb devices` muestra el dispositivo como `device`.
5. Compilar el APK con la configuración protegida local:

   ```bash
   cd android
   ./gradlew :app:assembleDebug
   cd ..
   ```

## Gate automatizado

Desde la raíz del repositorio:

```bash
bash tools/android/verify-physical-device.sh
```

Si hay más de un dispositivo:

```bash
bash tools/android/verify-physical-device.sh --serial SERIAL_ADB
```

El gate falla de forma cerrada si detecta un emulador, un APK sin permiso de
Internet, `targetSdk` menor que 36, ausencia del host Supabase de producción,
una referencia a `localhost`, instalación o lanzamiento fallidos, proceso
muerto, actividad fuera de primer plano, red Android no validada, crash o ANR.

CI ejecuta el mismo contrato sobre el APK sin usar ADB mediante `--apk-only`.
Así se verifican anticipadamente API 36, Internet y backend productivo; no se
presenta ese preflight como sustituto de la prueba en el Honor.

La evidencia queda en `artifacts/physical-device/`, directorio ignorado por
Git. Incluye SHA-256 del APK, identidad y parche del dispositivo, versión
instalada, salida de lanzamiento, estado de actividad, conectividad y logcat de
la ventana observada. No recopila credenciales ni datos personales de la app.

## Comprobación funcional online dirigida

Después del gate automatizado, realizar manualmente y documentar:

1. Abrir el enlace nuevo de acceso; debe resolver al dominio HTTPS de
   producción y nunca a `localhost`.
2. Iniciar sesión como `jordelmir@gmail.com`.
3. Confirmar que solo la cuenta maestra ve el Centro de Verificación.
4. Abrir la cola de usuarios/conductores y comprobar estados pendiente,
   aprobado y rechazado sin autoaprobar registros.
5. Cerrar sesión e iniciar con una cuenta normal; el Centro de Verificación no
   debe aparecer y sus RPC deben denegar el acceso.
6. Repetir con Wi-Fi desactivado para confirmar un error de red honesto y luego
   recuperar la sesión al restaurar conectividad.

Una captura visual no sustituye el resultado del script, y el resultado del
script no sustituye estas comprobaciones de autorización visibles.
