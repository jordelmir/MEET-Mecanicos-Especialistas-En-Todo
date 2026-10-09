# Elysium Safety — Guía de flujo y presentación visual institucional

## Objetivo

Esta guía define cómo debe presentarse Elysium Safety durante una demostración institucional. La experiencia debe ser visible, entendible y navegable desde la aplicación Android instalada; no basta con que existan modelos de dominio, enums, contratos o pruebas unitarias.

La pantalla no debe presentar cifras sintéticas como métricas reales ni atribuir integraciones que todavía no están conectadas. La visualización institucional es una capa de navegación y explicación, no una autorización de acceso ni una prueba de que los servicios remotos estén operativos.

## Ruta de acceso en Android

1. Abrir la aplicación Elysium Vanguard.
2. Entrar al módulo **Elysium Seguridad** desde la pantalla principal.
3. En el hub de Safety, abrir **Presentación institucional · Elysium Safety**.
4. Desde la pantalla institucional se puede abrir cada destino enlazado: reporte, mapa, mis reportes, expedientes, cronología, observatorio e investigación científica.
5. Volver mediante la flecha de navegación. La pantalla institucional debe ser una ruta real del NavHost, no un diálogo temporal que se pueda confundir con una pantalla no integrada.

Ruta de navegación: safety → safety/institutional-presentation → destino elegido.

## Jerarquía visual

### 1. Encabezado y propuesta de valor

El encabezado identifica claramente **ELYSIUM SAFETY** y **PRESENTACIÓN INSTITUCIONAL**. La primera tarjeta resume el propósito:

> Información trazable para decisiones responsables.

Debe aclararse desde el inicio que un reporte no equivale a una confirmación, una anomalía no demuestra un delito y la aplicación no sustituye la autoridad competente.

### 2. Acciones principales

Los dos CTA de mayor visibilidad son:

- **Crear reporte**: abre el formulario de incidentes ya existente.
- **Abrir mapa**: abre el mapa de Safety ya existente.

Estas acciones se muestran antes de las explicaciones extensas para que el recorrido principal no quede enterrado al final de una lista.

### 3. Guía operativa de cinco pasos

**01 — Registra el acontecimiento.** Describe qué ocurrió, cuándo y dónde. Distingue la observación directa de la información recibida de terceros. Los datos desconocidos deben permanecer como desconocidos, no completarse por inferencia.

**02 — Conserva el material original.** Adjunta material que la persona tenga derecho a compartir. Mantén las transformaciones o anotaciones separadas del original. Evita exponer identidades vulnerables, domicilios o datos de ubicación que no sean necesarios.

**03 — Contextualiza el tiempo y el territorio.** Usa el mapa y la cronología para organizar los registros. La coincidencia temporal, la proximidad geográfica y la ausencia de datos no demuestran causalidad ni responsabilidad individual.

**04 — Contrasta antes de concluir.** Vincula afirmaciones con fuentes concretas. Distingue originales de copias o republicaciones. Conserva las contradicciones y las explicaciones alternativas; no las borres para hacer más convincente una hipótesis.

**05 — Solicita revisión autorizada.** La comunicación externa requiere destinatarios y procedimientos autorizados. Una pantalla disponible no implica que exista una integración con una institución ni que el usuario tenga permisos para divulgar un expediente.

### 4. Significado de los estados

Los rótulos de la interfaz tienen una semántica de producto, no deben utilizarse como sustitutos automáticos de los estados reales del dominio:

- **REGISTRADO**: alguien presentó información; no implica verificación.
- **CORROBORADO**: existen fuentes adicionales que deben evaluarse por su procedencia e independencia.
- **PENDIENTE**: faltan revisión, datos o una integración autorizada.
- **REFUTADO / CORREGIDO**: se conserva la contradicción o corrección trazable; no debe borrarse silenciosamente el historial.

Cuando el dominio exponga estados autoritativos distintos, la interfaz debe mostrar los estados canónicos del contrato existente. No debe crear una segunda taxonomía incompatible para conseguir una presentación más atractiva.

## Estado de integración que debe mostrarse con honestidad

### Enlazado a pantallas existentes

La pantalla institucional conduce a las vistas Android existentes de reporte, mapa, mis reportes, expedientes, cronología, observatorio e investigación científica. Cada destino debe evaluarse mediante navegación real y pruebas UI; la mera existencia de una función composable no demuestra que el usuario pueda alcanzarla.

### Fuentes públicas y documentales en los flujos existentes

- [ ] Las fuentes públicas, registros documentales y expedientes se vinculan desde los flujos existentes de reporte, caso, cronología e investigación; no se crea una pantalla dependiente de un proveedor.
- [ ] Cada registro conserva URL de origen, fecha de consulta, integridad del contenido y procedencia verificable cuando esos datos existen.
- [ ] Los conectores no se presentan como activos sin captura real, permisos, cobertura documentada y pruebas de extremo a extremo.
- [ ] La concentración estadística es una señal explicable, nunca una conclusión de corrupción ni una atribución automática de culpabilidad.
- [ ] Los registros incompletos o con procedencia no verificada producen insuficiencia de datos, no alertas positivas.
### Intercambio institucional

**Estado: pendiente de piloto autorizado.** No afirmar una conexión operativa con Fuerza Pública, OIJ, Ministerio Público o Poder Judicial sin convenio/base jurídica, credenciales, permisos, auditoría y pruebas de extremo a extremo verificables.

## Criterios de aceptación visual y funcional

- [ ] El módulo Safety tiene una entrada reconocible desde el home vigente.
- [ ] El hub contiene una tarjeta visible de presentación institucional.
- [ ] Al tocar la tarjeta se abre la ruta institucional de pantalla completa dentro del NavHost; no un diálogo efímero.
- [ ] El encabezado, CTA primarios, cinco pasos y leyenda de estados son visibles y desplazables en pantallas móviles.
- [ ] Crear reporte y Abrir mapa abren los destinos actuales y el botón de regresar vuelve a una pantalla válida.
- [ ] Las vistas vacías describen que no hay registros accesibles; no inventan incidentes, fuentes, adjudicaciones, métricas ni resultados de investigaciones.
- [ ] La disponibilidad remota se refleja con los estados de conectividad del modelo. No etiquetar ONLINE a partir de una respuesta de error ni de un valor por defecto.
- [ ] Las fuentes externas solo se presentan como integradas cuando existe captura real, procedencia verificable, permisos y pruebas de extremo a extremo.
- [ ] Las pruebas Compose verifican renderizado, scroll a secciones inferiores y callbacks de los CTA.
- [ ] El pipeline compila debug, ejecuta unit tests y tests de UI instrumentados. El build exitoso por sí solo no significa que el usuario haya instalado esa versión.
- [ ] La validación física confirma que el APK construido desde el SHA probado se instala y que la ruta se ve en el dispositivo.

## Protocolo de validación y entrega

1. Confirmar que CI valida el SHA actual del PR, no un commit anterior.
2. Esperar a que finalicen compilación y pruebas. Identificar expresamente cualquier control fallido o que siga pendiente.
3. Publicar/descargar el artefacto generado por esa misma ejecución y confirmar su nombre, versión y SHA de origen.
4. Instalar ese APK exacto en el dispositivo; una APK de una release anterior o de main no incluirá los cambios de una rama abierta.
5. Abrir la ruta de acceso descrita arriba y recorrer los CTA y la guía de principio a fin.
6. Guardar capturas de las pantallas reales y el resultado de UI tests vinculados al SHA. Si no se ejecutó una prueba física, marcarla NOT_EXECUTED.

## Restricciones no negociables

- La UI no decide culpabilidad, no produce puntuaciones de criminalidad ni acusa personas.
- Un hash confirma la identidad de bytes comparados, no la veracidad del contenido.
- Una presentación institucional no concede privilegios adicionales y no modifica políticas RLS ni autoridad del servidor.
- No se borran ni reducen módulos del sistema operativo integral: esta es una vista enfocada y aditiva para el público institucional.


## Actualización visual: holografía 3D y guía animada

La cabecera institucional utiliza una composición Canvas con retícula de HUD, perspectiva, brillo radial, anillos orbitales cian/magenta, núcleo luminoso con pulsación y ligera inclinación 3D. Los movimientos son sutiles para conservar la legibilidad y limitar la distracción.

La guía pasa de cinco tarjetas largas a un stepper persistente. Cada etapa presenta su explicación, un consejo específico, indicador 1–5, barra de progreso, transición animada y una acción real hacia la pantalla existente correspondiente. La guía recuerda qué automatiza y qué no automatiza la aplicación: no presenta un botón visual como si adjuntara evidencia, remitiera un caso o validara una integración.

CI compila las pruebas Android instrumentadas y las ejecuta en un emulador API 36. Esto es distinto de una prueba física en un teléfono y debe reportarse por separado.

## Criterios de aceptación de esta mejora

- [ ] La cabecera 3D tiene retícula, scanline, anillos orbitales y movimiento de perspectiva.
- [ ] Los colores se consumen del sistema de paleta Elysium; no reemplazan la configuración guardada por el usuario.
- [ ] La guía permite avanzar, retroceder y reiniciar y conserva el paso al recomponer/restaurar estado.
- [ ] La acción principal cambia según la etapa y abre un destino de Safety real.
- [ ] La tarjeta de entrada institucional del hub tiene un borde neón animado sin alterar la navegación de los otros módulos.
- [ ] Las pruebas instrumentadas se ejecutan en un emulador Android durante CI.
- [ ] No se declaran verificados el APK instalado físicamente ni las integraciones de fuentes externas o institucionales sin evidencia separada.
