# Guía Maestra de Despliegue en Producción & Google Play Store (MEET)

---

## 1. Justificación de Permisos para Google Play Console

### A. Ubicación en Primer Plano y Segundo Plano (`FOREGROUND_SERVICE_LOCATION` y `ACCESS_BACKGROUND_LOCATION`)

**Declaración Oficial para la Ficha de Revisión de Google Play:**
> "MEET (Elysium Vanguard) es una plataforma de transporte colaborativo y diagnóstico vehicular. El permiso de ubicación en segundo plano y servicio en primer plano de ubicación (`FOREGROUND_SERVICE_LOCATION`) es indispensable y estrictamente necesario para el rol de Chofer mientras transporta pasajeros o navega hacia el punto de recogida con aplicaciones de navegación externas (como Waze o Google Maps). Permite transmitir la telemetría en tiempo real al pasajero para su seguridad personal, calcular la llegada a paradas y detectar situaciones de emergencia o desvío de ruta, incluso cuando la aplicación está minimizada o la pantalla del teléfono está apagada. Para los usuarios en rol pasajero, la ubicación se utiliza únicamente durante el uso activo de la app para fijar el punto de partida."

**Instrucciones para el Video Demostrativo exigido por Google Play:**
1. Iniciar sesión como chofer verificado.
2. Aceptar un viaje desde la tarjeta flotante de despacho.
3. Presionar "Navegar con Waze" (la app pasa a segundo plano).
4. Mostrar la notificación persistente en la barra de estado: `"Rastreo GPS de Viaje Activo"`.
5. Volver a MEET y finalizar el viaje.

---

## 2. Sección de Seguridad de los Datos (Data Safety Form)

| Categoría | Tipo de Dato | Recopilado | Compartido | Propósito |
|---|---|---|---|---|
| **Ubicación** | Ubicación precisa / aproximada | Sí | No (sólo con la contraparte del viaje) | Funcionalidad de la app (Navegación, despacho, cálculo de tarifa). |
| **Información Personal** | Nombre, teléfono, correo | Sí | No | Autenticación, contacto operativo y verificación de choferes. |
| **Información Financiera** | Comprobante / Referencia SINPE | Sí | No | Conciliación de recargas a la billetera del conductor. |
| **Fotos y Videos** | Comprobante de pago, selfie de vida | Sí | No | Verificación KYC y validación bancaria. |
| **Diagnóstico del Dispositivo** | Datos OBD-II (DTCs, RPM, velocidad) | Sí | No | Diagnóstico vehicular y generación de reportes periciales certificados. |

*Todos los datos se transmiten sobre conexiones seguras cifradas HTTPS/TLS 1.3 con certificados SHA-256.*

---

## 3. Arquitectura y Vinculación de SINPE Móvil (`jordelmir@gmail.com`)

### Configuración del Flujo Automático:

1. **Recepción en Gmail**:
   - Las transferencias bancarias de Costa Rica (BAC San José, Banco Nacional, Banco de Costa Rica) envían un correo de confirmación a `jordelmir@gmail.com`.
2. **Reenvío Automático al Webhook**:
   - En Gmail (`jordelmir@gmail.com` -> Configuración -> Filtros y Reenvío):
     - Crear filtro: `from:(notificaciones@baccredomatic.com OR bancobcr@bancobcr.com OR bncr@bncr.fi.cr) "SINPE Móvil"`
     - Acción: Reenviar a la dirección del webhook de Cloudflare / Edge Function:
       `https://<project-ref>.supabase.co/functions/v1/sinpe-email-webhook`
3. **Procesamiento y Acreditación**:
   - La Edge Function `sinpe-email-webhook` ejecuta el regex bancario, extrae:
     - `monto_crc`
     - `numero_referencia`
     - `banco`
   - Invoca el RPC `sinpe_ingest_email_receipt_v1`.
   - Si el chofer ya había ingresado el comprobante en su app, se acredita instantáneamente en su billetera MEET.
   - Si el chofer aún no lo había ingresado, queda registrado como disponible para que al tocar "VERIFICAR", se acredite en milisegundos.

---

## 4. Firma de Release (`meet-release-key.jks`)

Para generar el Keystore oficial de producción:
```bash
keytool -genkey -v -keystore meet-release-key.jks -alias meet-release-alias -keyalg RSA -keysize 2048 -validity 10000
```
Agregar a `android/local.properties`:
```properties
KEYSTORE_PATH=/ruta/a/meet-release-key.jks
KEYSTORE_PASSWORD=<tu_password_keystore>
KEY_ALIAS=meet-release-alias
KEY_PASSWORD=<tu_password_alias>
```
Compilación de paquete AAB para Google Play:
```bash
cd android && ./gradlew bundleRelease
```
