# MEET — GUÍA PASO A PASO: FORMULARIO DE SEGURIDAD DE LOS DATOS (DATA SAFETY)
## Google Play Console Compliance Guide

Este documento contiene las respuestas exactas que deben seleccionarse en el cuestionario de **Seguridad de los datos** en Google Play Console para evitar rechazos de auditoría.

---

### 1. Resumen General de Recopilación y Uso Compartido
* **¿Tu aplicación recopila o comparte alguno de los tipos de datos de usuario obligatorios?**
  👉 **SÍ**
* **¿Todos los datos de usuario recopilados por tu aplicación están cifrados en tránsito?**
  👉 **SÍ** (Cumple TLS 1.3 de extremo a extremo para todas las conexiones de red).
* **¿Proporcionas un mecanismo para que los usuarios soliciten la eliminación de sus datos?**
  👉 **SÍ** (Ruta nativa en la app: *Ajustes > Eliminar Cuenta* y URL web pública: ).

---

### 2. Desglose de Tipos de Datos Específicos

#### A. Ubicación (Location)
1. **Ubicación aproximada (Approximate location)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida con terceros? 👉 **NO**
   * ¿Es temporal (ephemeral)? 👉 **NO**
   * ¿Es necesaria o el usuario puede desactivarla? 👉 **El usuario puede desactivarla / es opcional**.
   * ¿Para qué fines se utiliza?
     * ☑ **Funcionalidad de la aplicación** (Localización de talleres cercanos y cálculo de despacho de auxilio vial).

2. **Ubicación precisa (Precise location)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida con terceros? 👉 **NO**
   * ¿Es temporal? 👉 **NO**
   * ¿Es necesaria? 👉 **El usuario puede desactivarla / es opcional**.
   * ¿Para qué fines se utiliza?
     * ☑ **Funcionalidad de la aplicación** (Navegación paso a paso de auxilio vial / viajes de conductor en primer plano).

#### B. Información personal (Personal info)
1. **Nombre (Name)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación** (Gestión de cuenta de usuario / perfil de mecánico / cliente).
2. **Dirección de correo electrónico (Email address)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación** y ☑ **Administración de cuentas** (Autenticación Supabase Auth).
3. **Identificadores de usuario (User IDs)**
   * ¿Recopilada? 👉 **SÍ** (UUID de Supabase).
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación**.

#### C. Información financiera (Financial info)
* **Historial de compras (Purchase history)**
   * ¿Recopilada? 👉 **SÍ** (Vía Google Play Billing).
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación** (Habilitación de paquetes de reportes PDF y suscripciones Pro).
* *Nota:* Los datos de tarjeta de crédito/débito son procesados directamente por Google Play Billing; la app MEET **NO** recopila números de tarjeta ni datos bancarios directamente.

#### D. Fotos y videos (Photos and videos)
1. **Fotos (Photos)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación** (Fotos de evidencia de averías mecánicas y daños en inspección pericial del vehículo).

#### E. Audio (Audio files)
1. **Grabaciones de voz o sonido (Voice or sound recordings)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida? 👉 **NO**
   * ¿Es temporal (ephemeral)? 👉 **SÍ** (El audio de Push-to-Talk y llamadas LiveKit se transmite en tiempo real y no se almacena permanentemente en el dispositivo ni se vende).
   * ¿Fines? 👉 ☑ **Funcionalidad de la aplicación** (Canal de voz mecánico/cliente).

#### F. Información y rendimiento de la aplicación (App info and performance)
1. **Diagnóstico de fallos (Crash logs) y Diagnóstico (Diagnostics)**
   * ¿Recopilada? 👉 **SÍ** (Métricas de rendimiento de red y depuración).
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Estadísticas (Analytics)** y ☑ **Prevención del fraude y seguridad**.

#### G. Dispositivo u otros identificadores (Device or other IDs)
1. **Identificadores del dispositivo u otros (Device or other IDs)**
   * ¿Recopilada? 👉 **SÍ**
   * ¿Compartida? 👉 **NO**
   * ¿Fines? 👉 ☑ **Prevención del fraude, seguridad y cumplimiento** (Vinculación criptográfica de sesión y aprovisionamiento de dispositivo).

---

### 3. Declaración Expresa de Datos de Diagnóstico Automotriz (OBD-II)
* Los parámetros PID de diagnóstico automotriz (RPM, temperatura, voltajes, códigos DTC) son tratados como **datos locales en el dispositivo (Local-First)**.
* No se comercializan con corredores de datos ni con redes publicitarias de terceros.
