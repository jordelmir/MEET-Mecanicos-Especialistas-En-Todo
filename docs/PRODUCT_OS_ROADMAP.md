# Plataforma MEET / Elysium Vanguard

> Autoridad y seguridad diagnóstica: [Diagnostic Safety, Causal & Conformance Kernel](architecture/DIAGNOSTIC-SAFETY-CAUSAL-KERNEL.md).

> Ejecución Vehicle Truth OS 4.17: [matriz de implementación y gates de evidencia](vehicle-truth/MEET-4.17-VEHICLE-TRUTH-IMPLEMENTATION.md).

MEET apunta a ser un sistema operativo automotriz: diagnostico, telemetria, reparacion, taller, marketplace, reportes y aprendizaje.

## Implementado En Esta Fase

- Gate CI/CD 4.16.0 endurecido y verificado en debug, release R8, paridad,
  escaneo de secretos y Android real. Evidencia completa en
  [MEET 4.16.0 — CI/CD y verificación Android](releases/MEET-4.16.0-CI-ANDROID-VERIFICATION.md).

- Onboarding con perfil de uso: usuario, mecanico, taller o flota.
- Preferencia inicial de adaptador: Bluetooth clasico, BLE o WiFi.
- Idioma detectado y persistido.
- Home como centro de mando: estado, recomendacion, siguiente accion y modo de uso.
- Demo de entrenamiento explicita, separada de escaneo real y sin persistir historial de vehiculo.
- Banner de demo en DTC para no confundir codigos de practica con lecturas reales.
- Calidad estimada de adaptador en el flujo de conexion.
- Pruebas activas bloqueadas si no hay conexion real, si el enlace es inestable o si el voltaje esta bajo.
- Status bar en espanol y errores con color de error.

## Reglas De Producto

- La app no debe fingir datos reales. Si es demo, se rotula como demo.
- El usuario siempre debe ver una proxima accion clara.
- Las funciones bidireccionales requieren conexion estable, voltaje sano y confirmacion.
- El modo usuario debe ser guiado; el modo mecanico/taller/flota puede ser mas denso.
- La IA debe recomendar pruebas de confirmacion antes de sugerir piezas.

## Siguientes Fases Recomendadas

- Wizard de creacion de vehiculo desde primer launch.
- Perfil de adaptador persistente con handshake, protocolo, latencia y comandos soportados.
- Android analytics nativo para medir conexion, escaneo, DTC, paywall y embudos.
- Reportes PDF con QR verificable en web.
- Casos de Repair Network convertidos a conocimiento estructurado.
- Play Integrity API y auditoria local de acciones criticas.
- Pruebas maestras con 50 vehiculos reales y matriz de adaptadores.
