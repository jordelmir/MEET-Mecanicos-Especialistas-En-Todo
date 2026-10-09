# Elysium Safety — Lista viva de tareas

**Rama:** `feat/elysium-safety-intelligence-plan`  
**SHA de partida de esta actualización:** `8e46697b289eb0b10b7fe47f4e72f5f27b97c7ef`  
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

## Slice C — Adjuntos de evidencia multimedia

- [x] Habilitar adjuntos directos de video MP4/WebM —ambos ya permitidos por el RPC existente— en la allowlist Android.
- [x] Añadir ambos MIME types al selector de documentos y al texto de ayuda.
- [x] Etiquetar los adjuntos de video y conservar el flujo alternativo de enlaces para archivos mayores de 20 MB.
- [x] Añadir tests unitarios de la allowlist y el límite existente.
- [ ] Compilar y ejecutar tests Kotlin/UI para el SHA final.
- [ ] Verificar la subida de video en staging, incluidos casos MATCH/MISMATCH/error del verificador remoto.
- [ ] Comprobar apertura/descarga del video después de reiniciar el proceso en un dispositivo.

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
- **CI del SHA anterior:** las ejecuciones consultadas estaban en cola; sin conclusión.
- **CI del SHA de esta actualización:** consultar después de publicar el commit.
