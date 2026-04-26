# MEET OBD2 - Módulo Android Nativo

Este directorio contiene la aplicación nativa Android (APK) para la lectura de datos OBD2 del sistema MEET.
Ha sido construida de cero utilizando **Kotlin, Coroutines, Flow y Jetpack Compose**.

## Arquitectura Principal

*   **Motor OBD2 (`ObdSession.kt`)**: El corazón del sistema. Implementa una cola de comandos no bloqueante para evitar sobrecargar los chips ELM327.
*   **Negociador de Clones (`ElmNegotiator.kt`)**: Una secuencia vital que auto-detecta la velocidad del chip y recorta la basura de las respuestas (headers, ecos, espacios), habilitando compatibilidad total con clones chinos v1.5 y v2.1.
*   **KeepAlive (`ReconnectPolicy.kt`)**: Sistema anti-timeout que envía `AT RV` cada 4.5s para evitar que el adaptador se duerma.
*   **Supabase Sync (`SupabaseClient.kt`)**: Comparte la base de datos de la Web App MEET para mantener todo sincronizado en la nube de forma transparente.
*   **Agnostic AI (`GeminiDiagnostic.kt`)**: Sistema de Inteligencia Artificial intercambiable con soporte para fallback offline.

## Instrucciones de Setup

1.  **Abre Android Studio.**
2.  Ve a `File > Open...` y **selecciona únicamente la carpeta `android/`**. No selecciones la raíz del repositorio web.
3.  Android Studio descargará automáticamente la versión de Gradle correcta para compilar este módulo (típicamente 8.5).
4.  Crea o edita el archivo `local.properties` en la raíz de `android/` y agrega tus llaves:
    ```properties
    SUPABASE_URL=tu_url_aqui
    SUPABASE_ANON_KEY=tu_anon_key_aqui
    GEMINI_API_KEY=tu_api_key_aqui
    ```
5.  Haz clic en "Sync Project with Gradle Files".
6.  Para compilar el release APK final, corre `./gradlew assembleRelease` o usa el menú `Build > Build Bundle(s) / APK(s)`.

## Adaptadores Soportados

| Adaptador | Compatibilidad | Notas |
| :--- | :--- | :--- |
| **Vgate iCar Pro (BLE)** | 🟢 Excelente | Soporta MTU negotiation completo |
| **vLinker MC+** | 🟢 Excelente | Total soporte de comandos avanzados |
| **ELM327 v1.5 (WiFi/BT)** | 🟡 Buena | Fallback a delay largo requerido |
| **ELM327 v2.1 (Clones)** | 🟠 Regular | Depende de ElmNegotiator para sobrevivir |

---
*Powered by MEET Engine Architecture*
