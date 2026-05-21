# 🔐 Guía Definitiva: Firma de APK/AAB — MEET (com.elysium369.meet)

> **Proyecto:** MEET - Mecánicos Especialistas En Todo  
> **Package ID:** `com.elysium369.meet`  
> **Última actualización:** Mayo 2026

---

## 📁 Ubicación de Archivos Clave

```
android/
├── meet-release.jks          ← 🔑 Keystore de producción (NUNCA borrar)
├── app/placeholder.jks       ← Keystore placeholder (no usar en producción)
├── local.properties          ← SDK path + Supabase credentials (NO se sube a git)
├── gradle.properties         ← JVM args (SÍ se sube a git)
└── app/build.gradle.kts      ← Configuración de firma (líneas 39-46)
```

---

## 🔑 Credenciales del Keystore de Producción

> [!CAUTION]
> **ESTOS DATOS SON SAGRADOS. Si se pierden, NO se puede actualizar la app en Google Play.**
> Google Play vincula la firma del keystore a la app para siempre.

| Campo | Valor |
|---|---|
| **Archivo** | `android/meet-release.jks` |
| **Store Password** | `Meet2026Elite!` |
| **Key Alias** | `meet-key` |
| **Key Password** | `Meet2026Elite!` |
| **Tipo** | PKCS12 / RSA 2048-bit |
| **Validez** | 16 Mayo 2026 → 01 Oct 2053 (~27 años) |
| **CN (Propietario)** | `CN=MEET Diagnostics, OU=Elysium369, O=Elysium369, L=Mexico, ST=Mexico, C=MX` |
| **SHA1** | `C4:01:02:E4:86:8A:31:7D:3D:9D:27:08:70:1D:97:13:48:FD:2F:E1` |
| **SHA256** | `43:1E:B9:D1:5C:F6:28:79:97:A3:64:6E:FA:2C:E7:78:DB:B9:9D:45:9C:40:8C:D2:5B:F3:FA:0E:1D:3C:CB:89` |

> [!IMPORTANT]
> **Verificado ✅** (Mayo 2026). Para re-verificar en el futuro:
> ```bash
> keytool -list -v -keystore android/meet-release.jks -storepass 'Meet2026Elite!'
> ```

---

## 🏗️ Cómo Funciona la Firma en Este Proyecto

El archivo `app/build.gradle.kts` (líneas 39-46) lee las credenciales desde **propiedades de Gradle** que se pasan en la línea de comandos:

```kotlin
signingConfigs {
    create("release") {
        storeFile = project.findProperty("KEYSTORE_PATH")?.let { file(it) }
                    ?: signingConfigs.getByName("debug").storeFile
        storePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: "android"
        keyAlias = project.findProperty("KEY_ALIAS") as String? ?: "androiddebugkey"
        keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: "android"
    }
}
```

**Esto significa:** Las credenciales se pasan con `-P` en el comando de Gradle. Si no se pasan, usa el keystore de debug (NO sirve para Google Play).

---

## 🚀 Comandos para Firmar (Copy & Paste)

### Opción A: Generar APK Firmado (para instalar directo en teléfonos)

```bash
cd android && ./gradlew clean assembleRelease \
  -PKEYSTORE_PATH=../meet-release.jks \
  -PKEYSTORE_PASSWORD='Meet2026Elite!' \
  -PKEY_ALIAS=meet-key \
  -PKEY_PASSWORD='Meet2026Elite!'
```

**El APK firmado sale en:**
```
android/app/build/outputs/apk/release/app-release.apk
```

### Opción B: Generar AAB Firmado (para subir a Google Play Console)

```bash
cd android && ./gradlew clean bundleRelease \
  -PKEYSTORE_PATH=../meet-release.jks \
  -PKEYSTORE_PASSWORD='Meet2026Elite!' \
  -PKEY_ALIAS=meet-key \
  -PKEY_PASSWORD='Meet2026Elite!'
```

**El AAB firmado sale en:**
```
android/app/build/outputs/bundle/release/app-release.aab
```

### Copiar el resultado a la carpeta releases:

```bash
# Para APK:
cp android/app/build/outputs/apk/release/app-release.apk releases/MEET-vX.X.X-release.apk

# Para AAB:
cp android/app/build/outputs/bundle/release/app-release.aab releases/MEET-vX.X.X-release.aab
```

---

## 🆕 Crear un Keystore NUEVO (Solo si se necesita uno nuevo)

> [!WARNING]
> **SOLO hacer esto si NO tienes un keystore existente o si estás creando una app completamente nueva.**
> Si ya subiste una versión a Google Play con un keystore, DEBES usar el mismo keystore para siempre.

```bash
keytool -genkey -v \
  -keystore android/meet-release.jks \
  -alias meet-release-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass Meet2026Elite! \
  -keypass Meet2026Elite! \
  -dname "CN=MEET App, OU=Elysium369, O=Elysium369, L=San Jose, S=San Jose, C=CR"
```

**Parámetros explicados:**
| Param | Significado |
|---|---|
| `-keystore` | Ruta donde se guarda el archivo .jks |
| `-alias` | Nombre interno de la llave (lo necesitas para firmar) |
| `-keyalg RSA` | Algoritmo de encriptación (siempre RSA) |
| `-keysize 2048` | Largo de la llave (2048 es el mínimo para Play Store) |
| `-validity 10000` | Días de validez (~27 años) |
| `-storepass` | Contraseña del almacén (la que pides para abrir el .jks) |
| `-keypass` | Contraseña de la llave específica (puede ser igual a storepass) |
| `-dname` | Datos del certificado (nombre, organización, país) |

---

## ✅ Verificar que un APK/AAB Está Firmado Correctamente

### Verificar APK:
```bash
# Ver info de firma del APK
jarsigner -verify -verbose -certs releases/MEET-vX.X.X-release.apk | head -20

# O con apksigner (más preciso):
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose releases/MEET-vX.X.X-release.apk
```

### Verificar AAB:
```bash
jarsigner -verify releases/MEET-vX.X.X-release.aab
```

### Ver el fingerprint SHA-256 del keystore (el que Google Play muestra):
```bash
keytool -list -v -keystore android/meet-release.jks -storepass 'Meet2026Elite!' | grep SHA256
```

---

## 📋 Checklist Pre-Release (Antes de Cada Versión)

- [ ] **1. Incrementar `versionCode`** en `app/build.gradle.kts` línea 26 (Google Play lo requiere estrictamente incremental)
- [ ] **2. Actualizar `versionName`** en `app/build.gradle.kts` línea 27 (lo que el usuario ve, ej: "3.3.3")
- [ ] **3. Verificar Supabase credentials** en `android/local.properties` (que estén puestas)
- [ ] **4. Compilar y firmar** con el comando `bundleRelease` (para Play Store) o `assembleRelease` (para APK directo)
- [ ] **5. Copiar a releases/** con nombre descriptivo
- [ ] **6. Probar en dispositivo** antes de subir a Play Store

```kotlin
// En app/build.gradle.kts (líneas 26-27) — ACTUALIZAR ANTES DE CADA RELEASE:
versionCode = 13         // ← INCREMENTAR (+1 por cada release a Play Store)
versionName = "3.4.0"    // ← Versión visible al usuario
```

---

## 🔒 Seguridad — Qué NO Subir a Git

El `.gitignore` del proyecto ya protege `local.properties`. Asegúrate de que estos archivos **NUNCA** estén en git:

```gitignore
# NUNCA subir a git:
local.properties          # Tiene Supabase keys y SDK path
*.jks                     # Keystores de firma
*.keystore                # Keystores de firma (formato antiguo)
```

**Verificar que NO están trackeados:**
```bash
git ls-files --cached | grep -E "\.jks|\.keystore|local.properties"
# Si sale algo = PELIGRO, hay que hacer git rm --cached <archivo>
```

---

## 🔄 Proceso Completo de Extremo a Extremo (Receta Rápida)

```bash
# 1. Ir al proyecto
cd "/Users/jordelmirsdevhome/Downloads/Web Apps/MEET Mecanicos Especialistas En Todo"

# 2. Editar versionCode y versionName en app/build.gradle.kts
#    versionCode = N+1
#    versionName = "X.Y.Z"

# 3. Build AAB firmado para Google Play
cd android && ./gradlew clean bundleRelease \
  -PKEYSTORE_PATH=../meet-release.jks \
  -PKEYSTORE_PASSWORD='Meet2026Elite!' \
  -PKEY_ALIAS=meet-key \
  -PKEY_PASSWORD='Meet2026Elite!'

# 4. Copiar el AAB a releases
cp app/build/outputs/bundle/release/app-release.aab \
   ../releases/MEET-vX.Y.Z-release.aab

# 5. (Opcional) Build APK para prueba directa en teléfono
./gradlew assembleRelease \
  -PKEYSTORE_PATH=../meet-release.jks \
  -PKEYSTORE_PASSWORD='Meet2026Elite!' \
  -PKEY_ALIAS=meet-key \
  -PKEY_PASSWORD='Meet2026Elite!'

# 6. Copiar APK
cp app/build/outputs/apk/release/app-release.apk \
   ../releases/MEET-vX.Y.Z-release.apk

# 7. Enviar APK al teléfono Android conectado por USB
adb push ../releases/MEET-vX.Y.Z-release.apk /sdcard/Download/

# 8. Subir AAB a Google Play Console
# → https://play.google.com/console → Tu App → Production → Create new release
```

---

## ❓ Troubleshooting

### "keystore password was incorrect"
```bash
# Verifica que la contraseña es correcta:
keytool -list -keystore android/meet-release.jks -storepass 'Meet2026Elite!'
```

### "Key was created with errors" / firma inválida
```bash
# Limpia todo y recompila:
cd android && ./gradlew clean && ./gradlew bundleRelease -P...
```

### "Version code X already exists" en Google Play
```
# Incrementa versionCode en app/build.gradle.kts
# Google Play requiere que cada upload tenga un versionCode MAYOR que el anterior
```

### El APK se instala pero crashea
```bash
# Revisa que las Supabase keys estén en local.properties:
cat android/local.properties | grep MEET_SUPABASE
# Deben aparecer MEET_SUPABASE_URL y MEET_SUPABASE_KEY
```

### Play Console dice "This APK is not signed"
```bash
# Estás usando el keystore de debug (fallback). Asegúrate de pasar las -P flags:
./gradlew bundleRelease \
  -PKEYSTORE_PATH=../meet-release.jks \
  -PKEYSTORE_PASSWORD=... \
  -PKEY_ALIAS=... \
  -PKEY_PASSWORD=...
```
