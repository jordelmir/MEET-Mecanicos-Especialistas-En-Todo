# SupremeBass Neon dentro de la APK

Origen: https://github.com/jordelmir/SupremeBass-Neon/releases/tag/v1.2.0
Commit consultado: 436ffd34e5e5d413a2115c52e50fc4a310185b87.

Una entrada `supreme_bass` en Inicio Classic y Adaptive reemplaza las entradas
separadas de Bass Boost, Subwoofer Tune, Visualizador Neon, Presets Vehículo y
Respuesta Frecuencia. Las rutas antiguas permanecen compatibles; no aparecen
en los catálogos de Inicio.

El módulo adapta AudioEngine y el servicio del origen a controles Compose.
Conserva presets 100–400%, slider y persistencia de la ganancia solicitada.
Usa LoudnessEnhancer y, si el dispositivo no permite controlarlo, Equalizer.
Convierte amplitud a millibels con 2000*log10(porcentaje/100), en lugar del
multiplicador de ganancia del origen. Respeta el rango real del ecualizador.

Se activa sólo al pulsar Activar. Nunca arranca por leer preferencias ni por
reinicio. El servicio visible tiene acción Detener, se libera al retirar la tarea
y no mantiene wake locks ni vigilancia periódica agresiva. Los callbacks de
reproducción y dispositivos tienen debounce y se eliminan al parar.

Efecto conectado representa control del efecto Android, no prueba de que
YouTube u otro reproductor lo apliquen. La disponibilidad se muestra y los
fallos no se presentan como éxito. No hay FFT ni métricas sintéticas.
La APK no incorpora la publicidad, credenciales ni aplicación independiente.

SupremeBoostPolicyTest cubre la conversión física y límites de ganancia.
