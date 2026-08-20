# Subsis­tema Linux PRoot y Google Antigravity CLI en MEET (Android)

> **"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."** — *Operating Charter MEET 2026*

Este documento describe la arquitectura, implementación técnica, comandos explícitos y verificación en hardware real del subsistema terminal de **MEET (Mecánicos Especialistas En Todo) / Elysium Vanguard**, permitiendo la ejecución de contenedores Linux completos (**Alpine Linux**, **Ubuntu 22.04 LTS**, **Debian 13 Trixie**) y la suite de comandos de **Google Antigravity CLI** directamente en dispositivos Android sin requerir permisos de superusuario (`root`).

---

## 1. Arquitectura del Subsis­tema

El subsistema terminal de MEET se compone de tres capas desacopladas y de alta eficiencia:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        INTERFAZ DE USUARIO                            │
│           TerminalScreen.kt (CRT Glow UI, Monospace, Pestañas)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      GESTOR LOCAL DE TERMINAL                          │
│               LocalShellManager.kt (StateFlow, Coroutines)              │
│       • Enrutador de comandos (pkg install, antigravity, db, ai)       │
│       • Servidor HTTP de Telemetría y Control Local (127.0.0.1:8082)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        ▼                                                       ▼
┌────────────────────────────────┐            ┌────────────────────────────────┐
│      ANDROID HOST SANDBOX      │            │   CONTENEDOR LINUX PROOT       │
│ • /system/bin/sh               │            │ • libproot.so + libtalloc.so   │
│ • libbusybox.so (64 symlinks)  │            │ • Bindings: /dev, /proc, /sys  │
│ • SQLite3 nativo               │            │ • Binding: /bin/meet           │
│ • /files/bin/ (Scripts CLI)    │            │ • /etc/resolv.conf (DNS)       │
│ • DNS resolv local             │            │ • /usr/local/bin/antigravity   │
└────────────────────────────────┘            └────────────────────────────────┘
```

---

## 2. Solución a los Retos de Seguridad de Android 10+ (targetSDK 34)

En Android 10 y versiones superiores, el sistema operativo impone políticas estrictas de seguridad:
1. **Políticas W^X (Write XOR Execute)**: Los archivos ejecutables o scripts ubicados en el almacenamiento interno de la app (`filesDir`) no pueden ejecutarse directamente (`error=13, Permission denied`).
2. **Dynamic Linker Isolation**: Los binarios ejecutables nativos requieren que `LD_LIBRARY_PATH` apunte explícitamente a `nativeLibraryDir` (`/data/app/.../lib/arm64`) para resolver dependencias compartidas como `libtalloc.so`.

### Implementación Técnica de la Solución:
- **Llamada Nativa Directa a PRoot**: El proceso de inicio de la shell (`startShellInternal`) ejecuta directamente el binario ELF extraído en el directorio de librerías nativas:
  ```kotlin
  val pb = ProcessBuilder(
      nativeLibProot.absolutePath,
      "--link2symlink",
      "-0",
      "-w", "/root",
      "-r", distroDir.absolutePath,
      "-b", "/dev",
      "-b", "/sys",
      "-b", "/proc",
      "-b", "${binDir.absolutePath}:/bin/meet",
      "/bin/sh"
  )
  pb.environment()["LD_LIBRARY_PATH"] = appContext.applicationInfo.nativeLibraryDir
  pb.environment()["PROOT_LOADER"] = nativeLibLoader.absolutePath
  pb.environment()["PROOT_TMP_DIR"] = appContext.cacheDir.absolutePath
  pb.environment()["HOME"] = "/root"
  pb.environment()["TERM"] = "xterm-256color"
  ```
- **Aplanamiento Automático de Rootfs**: Al descargar archivos tar empaquetados con subcarpetas (como `debian-trixie-aarch64`), el instalador inspecciona la estructura post-extracción y promueve automáticamente los directorios del sistema (`bin`, `usr`, `etc`, `lib`, `var`) a la raíz del contenedor.

---

## 3. Distribuciones Linux Soportadas y Endpoints Oficiales

| Distribución | Versión | Arquitectura | Tamaño Descarga | Formato | URL Oficial Verificada |
|---|---|---|---|---|---|
| **Alpine Linux** | 3.19.1 | `aarch64` | `~3.2 MB` | `.tar.gz` | `https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz` |
| **Ubuntu Linux** | 22.04.2 LTS (Jammy) | `aarch64` | `~27.6 MB` | `.tar.gz` | `https://partner-images.canonical.com/core/jammy/current/ubuntu-jammy-core-cloudimg-arm64-root.tar.gz` |
| **Debian GNU/Linux**| 13 (Trixie) | `aarch64` | `~35.4 MB` | `.tar.xz` | `https://github.com/termux/proot-distro/releases/download/v4.29.0/debian-trixie-aarch64-pd-v4.29.0.tar.xz` |

---

## 4. Comandos Explícitos Disponibles

### A. Gestión e Instalación de Distribuciones Linux
```bash
# Instalar Alpine Linux (Ultra liviano ~3.2MB)
pkg install alpine

# Instalar Ubuntu 22.04 LTS (~27.6MB)
pkg install ubuntu

# Instalar Debian GNU/Linux 13 (~35.4MB)
pkg install debian
```

### B. Comandos de Google Antigravity CLI (`antigravity` o alias `agy`)
Disponible nativamente en Android Host y en todas las distribuciones Linux instaladas:

```bash
# Ver ayuda y lista de comandos del motor
antigravity --help
# o simplemente:
agy --help

# Estado en vivo de telemetría OBD-II, vehículo activo y servidor de control
antigravity status

# Escaneo forense de los subsistemas y cálculo de integridad SHA-256
antigravity scan

# Catálogo completo de las 47 habilidades autónomas de ingeniería
antigravity skills

# Consulta de diagnósticos y causas para un código de falla DTC
antigravity dtc P0300

# Módulo de vuelo clásico antigravitatorio
antigravity fly
```

### C. Gestión de Paquetes dentro de cada Contenedor Linux
Una vez dentro del entorno correspondiente:

- **En Alpine Linux**:
  ```bash
  apk update
  apk add curl wget python3 nodejs git
  ```

- **En Ubuntu / Debian**:
  ```bash
  apt update
  apt install -y curl wget python3 python3-pip git
  ```

### D. Comandos Integrados de MEET
```bash
# Consulta SQL directa a la base de datos vehicular de MEET
db SELECT make, model, year, plate FROM vehicles;

# Consulta de razonamiento diagnóstico mediante IA
ai Diagnostica pérdida de potencia y código P0171 en motor 1.6L

# Ver estado de red y DNS inyectado
cat /etc/resolv.conf
```

---

## 5. Pruebas y Validación en Dispositivo Físico

- **Dispositivo**: Honor Magic V2 Foldable (`2156x2344`, `aarch64`, Android 13/14).
- **Conexión ADB**: `192.168.1.11:42319`.
- **Resultados de Ejecución en Vivo**:
  1. **Alpine Linux**:
     ```
     NAME="Alpine Linux"
     VERSION_ID=3.19.1
     PRETTY_NAME="Alpine Linux v3.19"
     ```
  2. **Ubuntu Linux**:
     ```
     PRETTY_NAME="Ubuntu 22.04.2 LTS"
     NAME="Ubuntu"
     VERSION_ID="22.04"
     VERSION="22.04.2 LTS (Jammy Jellyfish)"
     ```
  3. **Debian GNU/Linux**:
     ```
     PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
     NAME="Debian GNU/Linux"
     VERSION_ID="13"
     ```
  4. **Antigravity Status**:
     ```
     === ESTADO DE GOOGLE ANTIGRAVITY ENGINE ===
     • Conexión OBD: DISCONNECTED
     • Telemetría en vivo: 0 PIDs monitoreados (RPM: 0, Speed: 0 km/h)
     • Eco Score: 100 / 100 | Distancia: 0.0 km
     • Vehículo Activo: Hyundai Accent Verna 2005 (Placa: BCQ278)
     • Entorno Shell: Android Host (aarch64) + Soporte PRoot Linux (Alpine, Debian, Ubuntu)
     • Servidor Control Local: http://127.0.0.1:8082 [ACTIVO]
     ```
