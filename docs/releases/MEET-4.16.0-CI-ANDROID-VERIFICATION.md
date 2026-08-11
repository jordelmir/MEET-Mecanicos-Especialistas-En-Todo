# MEET 4.16.0 — CI/CD y verificación Android

**Fecha:** 2026-08-11
**versionCode:** 44
**versionName:** 4.16.0
**Estado:** compilación debug y release verificadas; instalación real verificada.

## Incidentes corregidos

1. `DtcScreen` devolvía `Unit?` desde la acción que abre las relaciones 3D.
   El callback ahora declara y cumple el contrato `() -> Unit`.
2. El resumen de topología no contemplaba `ScanCompleteness.FAILED`.
   El estado fallido ahora se comunica sin afirmar ausencia de fallas.
3. El guard de producción dependía de una expresión de una sola línea y no
   reconocía la frontera segura `getCommandsByCategory` después del formateo.
4. Los tests de topología no aportaban procedencia a valores que el nuevo
   contrato de evidencia exige. Los fixtures ahora declaran procedencia
   `USER_CONFIRMED`; la producción continúa fallando de forma cerrada cuando
   la procedencia es desconocida.
5. El escáner histórico recorría todos los blobs con un proceso por objeto y
   confundía dos fixtures sintéticos revisados con credenciales. Ahora revisa
   incrementalmente los blobs introducidos por el rango del PR/push, mediante
   lectura batch y excepciones ancladas al SHA del blob.
6. El escáner del APK acumulaba `strings` en un archivo temporal enorme. Si el
   sistema mataba `grep`, podía reportar un falso positivo de éxito. Ahora
   recorre las entradas ZIP relevantes en bloques de 1 MiB, conserva solape
   entre bloques, limita memoria y falla ante cualquier error del escáner.
7. Dependency Review fallaba porque Dependency Graph estaba deshabilitado. El
   grafo fue habilitado en GitHub y el workflow comprueba su disponibilidad de
   forma explícita antes de invocar la acción.
8. CI sobrescribía `android/local.properties` para la compilación release. Las
   propiedades de rol/URL/clave Supabase y firma efímera ahora se inyectan por
   `-P`, sin modificar configuración local.
9. R8 requería dos referencias opcionales JVM de Ktor. Se añadieron reglas
   `dontwarn` únicamente para `ManagementFactory` y `RuntimeMXBean`; R8 sigue
   activo y estricto para el resto del producto.
10. Las acciones oficiales de checkout, Node, Java, Dependency Review y carga
    de artefactos fueron actualizadas a versiones Node 24 y fijadas por SHA.
    Los workflows dejaron de depender de acciones Node 20 obsoletas y el gate
    combinado instala sus dependencias TypeScript de forma determinista con
    `npm ci` antes de ejecutar la paridad.
11. El empaquetador Android puede terminar R8 y fallar de forma transitoria en
    `packageRelease` mediante `IncrementalSplitterRunnable`. CI conserva el log
    completo y permite un único reintento de `packageRelease` solamente cuando
    aparecen juntas esas dos firmas exactas. Errores de compilación, R8, firma
    o cualquier otra etapa continúan fallando sin reintento.

## Matriz de verificación local

| Gate | Resultado |
|---|---|
| Guards de producción | OK |
| Escaneo de secretos en fuentes | OK |
| Escaneo incremental de historial | OK, ~0.2 s en el rango local |
| Pruebas Android | 783 aprobadas |
| Pruebas TypeScript | 184 aprobadas |
| Android lint debug | OK; sin errores nuevos fuera del baseline |
| Paridad TypeScript ↔ Kotlin | OK, salida idéntica |
| `assembleDebug` | OK |
| `assembleRelease` + R8 | OK con firma efímera de CI |
| Firma APK release | APK Signature Scheme v2 verificado |
| Escaneo interno APK release | OK |

## Artefactos verificados

| Artefacto | Tamaño aproximado | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 312 MiB | `96b76913988c80311af538bfe381e2d2c191b7f1a20d5b1c650bc9f6c4356b18` |
| `app-release.apk` | 236 MiB | `54710702cf7b1fc3f0726c2f3d15d3310251f33693ba28d0cbbc73d0f97ab54f` |

La firma release usada en esta prueba es efímera y solo demuestra el pipeline
de CI. No sustituye la clave privada de publicación de Google Play.

## Evidencia en Android real

- Dispositivo: HONOR `VER-N49`, conectado mediante ADB inalámbrico.
- Instalación: `adb install -r -d` → `Success`.
- Inicio frío: `Status: ok`, `LaunchState: COLD`, `TotalTime: 1780 ms`.
- Actividad superior: `com.elysium369.meet/.MainActivity`.
- Proceso confirmado vivo después del inicio.
- Paquete instalado: `versionName=4.16.0`, `versionCode=44`.
- No se observaron entradas nuevas `FATAL EXCEPTION` del proceso durante la
  verificación de lanzamiento.

## Contrato operativo para siguientes rondas

Las pruebas, lint, compilación, instalación ADB y publicación se ejecutan solo
cuando el propietario lo ordena expresamente. Esta ronda fue autorizada de
forma explícita. El workflow remoto permanece como gate automático para PR y
push a `main` cuando cambian Android, diagnóstico, paridad, herramientas o el
propio workflow.
