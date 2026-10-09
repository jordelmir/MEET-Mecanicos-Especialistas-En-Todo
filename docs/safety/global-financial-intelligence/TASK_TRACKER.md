# Elysium Safety — Lista viva de tareas

**Rama:** `feat/elysium-safety-intelligence-plan`  
**SHA de partida de esta actualización:** `76805b54fa62012f9a4bd6457c6698da862d76c0`  
**Estado:** rama de trabajo, PR #56 abierto en borrador; `main` no modificada por este cambio.

Marcar una tarea solo cuando el cambio esté escrito. Compilación, ejecución de tests, staging y dispositivo físico son verificaciones diferentes.

## Slice A — Línea base y contratos

- [x] Inspeccionar árbol real y localizar presentación, mapa, reportes, evidencia, outbox, entidades científicas, RPC, migraciones y gateway.
- [x] Confirmar que no se encontró una tabla literal `safety_scientific_evidence_bridge` en las migraciones científicas revisadas; mapear relaciones existentes antes de crear un bridge nuevo.
- [x] Generalizar normalización documental y retirar dependencia de proveedor específico de la UI institucional.
- [x] Añadir guardia de procedencia para no escalar concentración cuando la fuente esté sin verificar.
- [x] Añadir prueba de regresión para procedencia no verificada.
- [x] Reconciliar plan, auditoría, dominio, contrato de fuentes, brief y guía visual con la línea base; no heredar PASS de otro SHA.

## Slice B — Presentación institucional y experiencia Safety

- [x] Mantener cabecera futurista, neón y animación Compose.
- [x] Conectar el flujo captura → preservación → ubicación → relación → contraste → revisión con pantallas Safety existentes.
- [x] Añadir una advertencia territorial: un punto accesible no equivale a delito confirmado; una capa vacía puede reflejar cobertura incompleta.
- [x] Corregir las expectativas de la prueba UI que aún buscaban la tarjeta anterior.
- [x] Añadir prueba de callbacks para verificar rutas de las seis etapas.
- [x] Añadir prueba UI para la advertencia epistemológica del mapa.
- [ ] Confirmar CI en el SHA final.
- [ ] Instalar el APK de ese mismo SHA en Android y verificar visualmente la ruta completa.

## Slice C — Evidencia multimedia (decisión de arquitectura: video por enlace)

- [x] Mantener videos fuera de las cargas directas a Elysium para proteger ancho de banda, coste de almacenamiento y capacidad del servidor.
- [x] Conservar el campo de enlace compartible de video dentro del reporte; el archivo alojado permanece en la plataforma externa elegida por quien reporta.
- [x] Retirar MP4/WebM de la allowlist y del selector Android aunque el RPC acepte técnicamente esos MIME types: el contrato cliente es intencionalmente más restrictivo.
- [x] Mantener etiquetas de visualización heredada para no romper registros existentes.
- [x] Añadir prueba unitaria que impide la carga directa de video.
- [ ] Compilar y ejecutar tests Kotlin/UI para el SHA final.
- [ ] Validar en staging la persistencia del enlace, su escape/normalización, permisos de lectura y comportamiento ante URLs no válidas o enlaces retirados.
- [ ] Verificar que el recibo de reporte no afirme que el video fue preservado dentro de Elysium: el sistema conserva el enlace, no los bytes remotos.

## Slice D — Reporte, evidencia, outbox, autoridad y mapa

- [ ] Ejecutar report → adjunto → Room/outbox → RPC → PostgreSQL → recibo real → proyección autorizada → mapa → expediente.
- [ ] Probar reintento idempotente, fallo de red, cambio de sesión, archivo corrupto, hash distinto, MIME/tamaño rechazado.
- [ ] Demostrar que el éxito remoto requiere recibo válido del servidor.
- [ ] Auditar fuga por coordenadas, EXIF, nombres de archivo, miniaturas, notificaciones y exportaciones.
- [ ] Preservar el estado epistemológico original cuando una regla de revisión devuelve insuficiencia; planificar cambio de contrato solo si el código real lo requiere.

## Slice E — Fuentes documentales e inteligencia financiera

- [x] Mantener un normalizador genérico; no declarar ingesta de una fuente externa activa.
- [x] Distinguir digest de campos normalizados de hash byte-exacto del original.
- [ ] Implementar un adaptador solo tras verificar fuente, acceso y uso autorizado.
- [ ] Validar cobertura, paginación, reanudación, rate limits, deduplicación, revocación y drift de esquema.
- [ ] Validar entidades con identificadores fuertes, independencia real de fuentes e hipótesis alternativas.
- [ ] Mantener la regla: riqueza visible por sí sola nunca produce una clasificación criminal.

## Slice F — Seguridad y operación institucional

- [ ] Ejecutar PostgreSQL/RLS, RPC, grants y aislamiento de organizaciones en staging autorizado.
- [ ] Verificar correcciones, impugnaciones, retiradas y exportaciones auditadas.
- [ ] Ejecutar pruebas adversariales de prompt injection antes de ampliar funciones de IA.
- [ ] Pilotar interoperabilidad solo con institución participante y datos autorizados.
- [ ] Probar backup/restore, gestión de claves, incident response y límites operativos antes de producción.

## Resultado de verificación hasta ahora

- **Gradle local en esta sesión:** NOT_EXECUTED.
- **Supabase staging / RLS:** NOT_EXECUTED.
- **APK instalado físicamente:** NOT_EXECUTED.
- **CI del SHA base `76805b54...`:** no está verde. El workflow CI #37878488345 falló al compilar la pantalla institucional por imports y `StatusLegendRow`; el siguiente SHA debe volver a compilarse.
- **CI del SHA de esta actualización:** consultar después de publicar el commit.


## Slice B2 — Reporte → afirmación → evidencia (ampliación local)

- [x] Sustituir la referencia sintética `report:<id>` por IDs UUID reales de adjuntos en el vínculo evidencia–afirmación.
- [x] Registrar nodos/aristas de procedencia para reporte, afirmación, evento, adjuntos y, si se crea, hipótesis.
- [x] Tratar adjuntos nuevos como `CONTEXTUALIZES`, sin asignarles apoyo probatorio automático.
- [x] No generar hipótesis ni hipótesis nula sintéticas cuando el análisis científico está desactivado.
- [x] Si el usuario activa análisis científico, exigir hipótesis, hipótesis nula y criterio de falsación explícitos.
- [x] Conservar `occurredAt=null` cuando el usuario no conoce la fecha del acontecimiento; registrar el tiempo de recepción por separado.
- [x] Hacer transaccional la proyección científica local.
- [x] Añadir una evaluación que conserva TruthState original junto al mapeo científico conservador.
- [ ] Conectar la proyección con el outbox científico remoto; esta implementación local no significa sincronización.
- [ ] Ejecutar pruebas Kotlin y revisar el resultado de CI.


## Slice G — Integridad del Observatorio (brecha P0 encontrada en revisión)

- [x] Eliminar estimaciones sintéticas de conteos de fuentes (mínimos, porcentajes y multiplicadores por punto).
- [x] No convertir el fallo de agregación remota en métricas marcadas como sensibles/disponibles.
- [x] No usar el punto/caché local para reconstruir un agregado que el servidor ha suprimido por privacidad.
- [x] Mostrar desglose de fuentes solo cuando la proyección remota autoriza esas métricas.
- [x] Añadir un estado explícito `UNAVAILABLE`; consulta fallida no equivale a conteos medidos en cero.
- [x] Añadir tests para el estado no disponible y los conteos reales de la proyección V3.
- [ ] Ejecutar Gradle/CI para validar el cambio y reparar posibles regresiones.


## Slice H — Fiabilidad de compilación y recibos de reportes

- [x] Corregir el import de `rememberSaveable` y los imports de Compose faltantes en la presentación institucional.
- [x] Añadir `StatusLegendRow` con jerarquía visual, estados legibles y descripciones accesibles.
- [x] Ampliar la prueba instrumentada para confirmar que se muestran los cuatro estados de conocimiento.
- [x] Hacer que solo `syncState == SYNCED` se represente como confirmación remota; no inferir éxito únicamente porque `serverState` tenga valor.
- [x] Retirar las afirmaciones de publicación y visibilidad comunitaria del recibo; recepción del servidor no equivale a publicación o remisión institucional.
- [x] Identificar la proyección científica como local y distinguirla de la sincronización remota.
- [x] Mostrar una advertencia visible cuando falle la proyección científica local.
- [x] Añadir pruebas unitarias puras para la clasificación del estado del recibo.
- [ ] Verificar compilación y pruebas en CI para el SHA resultante.
- [ ] Instalar el APK de ese mismo SHA en un Android y comprobar visualmente el recibo y el modo institucional.


## Slice J — Evidencia: recepción ≠ verificación

- [x] Introducir un mapper común para el ciclo de evidencia (STAGED, UPLOADING, UPLOADED, RECEIVED, RETRY, VERIFIED, QUARANTINED, FAILED y estados desconocidos).
- [x] Mostrar RECEIVED como “verificación de bytes pendiente”; solo VERIFIED indica que el verificador del servidor devolvió MATCH.
- [x] Mostrar el estado de evidencia también en el detalle territorial.
- [x] Eliminar “sincronizado mundialmente” y “atestación criptográfica” donde el código solo demuestra recepción/disponibilidad.
- [x] Evitar presentar una fuente ciudadana como verificada solo por su categoría.
- [x] Separar cifrado local y acceso a copias remotas controlado por servidor.
- [x] Añadir pruebas unitarias para la presentación del ciclo de evidencia.
- [ ] Ejecutar tests/compilación CI para el SHA resultante.
- [ ] Verificar en un dispositivo el ciclo STAGED → RECEIVED → VERIFIED / QUARANTINED con datos de prueba autorizados.


## Slice K — Privacidad y procedencia de la fuente declarada

- [x] Quitar promesas de anonimato absoluto/zero-knowledge que la implementación local no demuestra.
- [x] Aclarar que el rol de periodista/institución se declara en el cliente; no autentica identidad ni concede permisos.
- [x] Cambiar las descripciones del Observatorio para que los tipos de fuente no se interpreten como certificación.
- [x] Añadir política y pruebas unitarias para validar el formato HTTP(S) de referencias externas.
- [ ] Cifrar y migrar de forma segura los perfiles en SharedPreferences antes de ofrecer garantías fuertes de privacidad.


## Slice L — Seguimiento: reporte y actualización no son un hecho confirmado

- [x] El cronómetro mide tiempo desde el reporte o hasta una actualización aportada; no infiere impunidad, captura, justicia ni inacción oficial desde la categoría.
- [x] La antigua certificación local por prensa se presenta como actualización externa declarada y no verificada.
- [x] Cualquier usuario puede aportar una actualización sin adquirir autoridad institucional por declarar un rol.
- [x] Se exige referencia HTTP(S) absoluta, sin credenciales embebidas; validar el formato no autentica el contenido.
- [x] Se mantiene el seguimiento posterior sin convertirlo en hecho confirmado.
- [ ] Ejecutar Kotlin/Gradle/CI para este incremento.
- [ ] Migrar perfiles locales desde SharedPreferences plano a almacenamiento cifrado con Android Keystore antes de prometer protección fuerte.
- [ ] Probar el cronómetro en dispositivo y validar visualmente la separación entre actualización aportada y hecho corroborado.
