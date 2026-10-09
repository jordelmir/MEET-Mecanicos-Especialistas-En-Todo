# Elysium Safety — Guía de experiencia visual y presentación institucional

## Objetivo

Mostrar una experiencia clara y futurista para presentar Seguridad, evidencia, mapa y revisión institucional. El modo institucional reutiliza pantallas reales de Safety. No cambia permisos, no sustituye la autoridad del servidor y no oculta el resto de los módulos del producto general.

## 1. Cabecera holográfica

La cabecera usa Canvas/Compose para una apariencia HUD: retícula, scanline, brillo radial, anillos cian/magenta, núcleo luminoso y una inclinación 3D sutil. La estética es una ilusión de profundidad sobre Canvas; no debe presentarse como una escena 3D física ni a costa de legibilidad o accesibilidad.

Los colores usan la paleta Elysium existente. Las animaciones deben ser breves, estables y respetar restauración de estado y el reloj de tests.

## 2. Flujo interactivo de seis etapas

La presentación coloca el flujo antes del inventario extenso de pantallas:

1. **Capturar:** abrir el formulario de reporte.
2. **Preservar:** volver al formulario para adjuntar material y revisar el estado de evidencia; la tarjeta no adjunta por sí sola.
3. **Ubicar:** abrir el mapa y sus filtros/capas disponibles.
4. **Relacionar:** abrir la investigación científica y vincular fuentes/afirmaciones/hipótesis existentes.
5. **Contrastar:** revisar procedencia, evidencia contradictoria y limitaciones dentro de las herramientas existentes.
6. **Revisar:** abrir los expedientes disponibles para la sesión actual.

Cada tarjeta navega a un destino real. El flujo no implica que haya un intercambio institucional operativo ni que todos los registros tengan datos o permisos disponibles.

## 3. Señal epistemológica visible en el mapa

La pantalla de mapa debe exponer un aviso compacto y legible: un punto equivale a un registro accesible con el nivel de exposición configurado, no necesariamente a un delito confirmado; una capa vacía puede indicar cobertura incompleta.

La interfaz no puede sustituir la validación del servidor. Antes de un piloto, verificar el origen de geoDisclosure, la precisión mostrada en cada detalle, los metadatos secundarios y las proyecciones públicas.

## 4. Visuales añadidos a Safety

- **Hub:** entrada institucional con borde neón y animación.
- **Reportes:** panel de estado derivado de los contadores reales de reportes, pendientes y errores.
- **Expedientes:** panel con total, filtrados, abiertos y en revisión, a partir del estado existente.
- **Cronología:** panel temporal con hitos y vínculos a fuentes/evidencia. Las sumas de vínculos no deben etiquetarse como fuentes únicas.
- **Mapa:** aviso de lectura responsable; los filtros y límites ya existentes siguen controlando qué datos se muestran.
- **Presentación:** flujo de seis etapas accionable más guía de cinco pasos, destinos y aviso de gobernanza.

No introducir contadores, casos, fuentes o incidentes sintéticos en pantallas operativas.

## 5. Semántica para la audiencia

- REGISTRADO: alguien presentó información, no es una confirmación.
- CORROBORADO: existen fuentes adicionales, cuya independencia debe evaluarse.
- PENDIENTE: falta revisión, dato o integración autorizada.
- REFUTADO/CORREGIDO: se conserva la contradicción y el historial.

Cuando el dominio tenga un estado canónico, mostrar el valor o su representación documentada. No inventar un enum visual que contradiga el modelo real.

## 6. Criterios de aceptación

- [ ] La cabecera y las acciones principales son visibles en un teléfono.
- [ ] El flujo de seis etapas se desplaza, se lee y sus tarjetas abren las rutas existentes.
- [ ] La guía de cinco pasos permite avanzar y volver.
- [ ] El aviso del mapa diferencia registro accesible de hecho confirmado.
- [ ] Reportes, expedientes y cronología muestran valores reales de su estado, sin inflar métricas.
- [ ] Las vistas vacías no crean casos o puntos de ejemplo en el modo operativo.
- [ ] No se presenta la integración institucional como activa sin pruebas de extremo a extremo.
- [ ] Los tests Compose comprueban etiquetas clave, desplazamiento y callbacks.
- [ ] Gradle y tests de Android instrumentados pasan para el SHA exacto.
- [ ] Se revisa en emulador y luego en un Android físico; compilación no equivale a validación visual física.
- [ ] El APK instalado se vincula al SHA, checksum y resultado de CI.

## 7. Protocolo de comprobación

1. Confirmar SHA y rama de la ejecución de CI.
2. Revisar compilación, tests unitarios y tests instrumentados.
3. Obtener el artefacto de esa misma ejecución, si CI lo genera.
4. Instalar ese APK exacto por ADB en el dispositivo seleccionado.
5. Recorrer Safety → Presentación institucional → reporte/mapa/investigación/expedientes.
6. Guardar captura y resultado de navegación si se logra la prueba física.
7. Marcar NOT_EXECUTED cualquier paso que no se haya realizado.

## 8. No negociables

- La interfaz no decide culpabilidad ni crea puntuaciones de criminalidad.
- El hash no se presenta como prueba de veracidad.
- Un botón de presentación no otorga permisos ni autoriza la divulgación.
- La demostración institucional excluye módulos ajenos a Safety de la navegación de esa experiencia, pero el producto integral conserva todos sus módulos.
