# MEET — DECLARACIÓN DE SERVICIOS EN PRIMER PLANO (FOREGROUND SERVICES)
## Google Play Console FGS Policy Compliance (Android 14+ / API 34+)

Para cumplir con la política de Google Play sobre permisos de tipo `FOREGROUND_SERVICE_*`, la consola solicita una justificación detallada y puede requerir un enlace a un video de demostración del flujo de usuario.

---

### 1. Servicio: OBD Scanner Connected Device
* **Permiso declarado:** `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`
* **Componente:** `com.elysium369.meet.core.obd.ObdForegroundService`
* **Tipo FGS:** `connectedDevice`
* **Justificación para Play Console:**
  > "MEET connects continuously to physical hardware OBD-II adapters (ELM327 Bluetooth/WiFi) plugged into the vehicle's diagnostic port. The connectedDevice foreground service maintains an uninterrupted telemetry stream while the vehicle engine is running, capturing real-time PIDs (coolant temperature, engine RPM, fuel trims, fault codes) even if the user switches to navigation apps or locks the screen, preventing catastrophic disconnects during active vehicle diagnosis."
* **Notificación persistente mostrada al usuario:**
  * *Título:* "MEET — Conexión OBD Activa"
  * *Texto:* "Transmitiendo telemetría en tiempo real desde el escáner del vehículo."

---

### 2. Servicio: Ride & Roadside Assistance Location Tracking
* **Permiso declarado:** `android.permission.FOREGROUND_SERVICE_LOCATION`
* **Componente:** `com.elysium369.meet.ride.location.RideLocationTrackingService`
* **Tipo FGS:** `location`
* **Justificación para Play Console:**
  > "During active roadside assistance and mechanical rescue dispatch missions, MEET utilizes a foreground location service to transmit precise GPS coordinates of the responding technician/tow truck to the stranded motorist in real time, ensuring navigation continuity, estimated arrival accuracy, and driver safety during transit."
* **Notificación persistente mostrada al usuario:**
  * *Título:* "Vanguard — Despacho Activo"
  * *Texto:* "Compartiendo ubicación en tiempo real para auxilio vial."

---

### 3. Servicio: Elysium Developer & Engineering Terminal
* **Permiso declarado:** `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
* **Componente:** `com.elysium369.meet.core.terminal.ElysiumTerminalService`
* **Tipo FGS:** `specialUse`
* **Subtipo declarado en Manifest:**
  ```xml
  <property
      android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="Terminal daemon and developer compilation environment maintenance" />
  ```
* **Justificación para Play Console:**
  > "The application provides an advanced automotive engineering environment with a built-in terminal daemon for technical workshop diagnostic tooling, CAN-bus scripting, and offline automotive knowledge retrieval. The specialUse foreground service keeps long-running compilation tasks and hardware diagnostics active when the technician temporarily backgrounds the app to consult service manuals."
* **Notificación persistente mostrada al usuario:**
  * *Título:* "Elysium Terminal Daemon"
  * *Texto:* "Manteniendo entorno técnico de compilación y diagnóstico activo."

---

### 4. Guión para el Video de Demostración (Si Google Play lo requiere)
Si los revisores de Google Play solicitan un video demostrativo de los servicios en primer plano:
1. **Paso 1:** Abrir la app MEET y presionar "Conectar Escáner OBD". Mostrar la notificación persistente "Conexión OBD Activa".
2. **Paso 2:** Salir a la pantalla de inicio de Android mientras el escáner continúa leyendo datos; mostrar que la notificación sigue activa y no se interrumpe la telemetría.
3. **Paso 3:** En la sección de Asistencia Vial / Viaje, iniciar un despacho; mostrar la notificación de seguimiento de ubicación en tiempo real.
