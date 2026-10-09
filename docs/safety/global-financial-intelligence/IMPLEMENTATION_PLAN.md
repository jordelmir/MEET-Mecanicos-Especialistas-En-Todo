# Elysium Safety — Plan Maestro de Implementación y Aceptación
## Seguridad ciudadana, evidencia digital, inteligencia territorial e investigación de interés público

**Repositorio:** jordelmir/MEET-Mecanicos-Especialistas-En-Todo  
**Rama de trabajo:** feat/elysium-safety-intelligence-plan  
**PR de trabajo:** #56, abierto en borrador; no fusionado durante esta auditoría  
**SHA de línea base de esta revisión:** 76805b54fa62012f9a4bd6457c6698da862d76c0

> Principio operativo: añadir capacidades sin borrar ni degradar funciones, contratos, migraciones o módulos existentes. La presentación para instituciones sí se limita a Elysium Safety; el producto completo conserva mecánica, movilidad y los demás módulos.
>
> **Evidencia ≠ culpabilidad. Reporte ≠ hecho confirmado. Anomalía ≠ delito. Correlación ≠ causalidad. Hash ≠ veracidad.**

---

## 1. Misión del producto

Elysium Safety es la capa tecnológica orientada a seguridad ciudadana para capturar, preservar, estructurar, georreferenciar, relacionar, revisar y analizar información relacionada con incidentes. Debe ayudar a evitar que material potencialmente relevante quede disperso entre teléfonos, archivos, conversaciones, publicaciones y distintas fuentes.

No sustituye a la Fuerza Pública, al OIJ, al Ministerio Público ni al Poder Judicial. No determina culpabilidad, no convierte automáticamente denuncias en hechos probados y no concede a un reportante permiso para vigilar a otras personas o divulgar información sensible.

## 2. Dos dominios complementarios, con límites explícitos

### 2.1 Elysium Safety: línea principal para ciudadanía e instituciones

- Reportes estructurados con categoría, relato, tiempo, ubicación y relación de la persona con la información.
- Fotografías, audios y documentos adjuntos; referencias a enlaces de vídeo cuando el modelo vigente los admite.
- Preservación local cifrada, cálculo de huellas y seguimiento del estado de carga/verificación.
- Mapa con filtros espaciales/temporales y separación entre proyecciones públicas y reportes privados.
- Expedientes, cronología, afirmaciones, hipótesis, relaciones y procedencia.
- Estados de conocimiento, contradicciones y revisión humana.
- Políticas de publicación, acceso y divulgación con autoridad de servidor.
- Intercambio institucional solo cuando exista autorización, convenio, credenciales, controles y pruebas de extremo a extremo.

### 2.2 Inteligencia financiera de interés público: módulo independiente

- Normalización de fuentes documentales legítimamente accesibles.
- Registros corporativos, contratos públicos, auditorías, resoluciones y sanciones cuando el uso esté autorizado.
- Relaciones sustentadas en identificadores fuertes y documentos citables.
- Reglas deterministas explicables para detectar patrones documentados, con cobertura y explicaciones alternativas.
- Revisión humana, privacidad, controles de acceso y exportaciones autorizadas.

La línea financiera no es requisito para que el núcleo ciudadano funcione y no debe dominar una demostración legislativa centrada en seguridad. No existe un conector externo operativo por el mero hecho de tener un normalizador o una regla de dominio.

## 3. Reglas de implementación no negociables

1. **Siempre a más:** los cambios son aditivos; no se borran módulos generales ni se reemplazan contratos válidos por un modelo paralelo.
2. **La demostración es un modo de presentación:** la navegación institucional muestra reportes, evidencia, mapa, expedientes, cronología, observatorio e investigación, pero no elimina mecánica, diagnóstico, movilidad ni marketplace del producto completo.
3. **Una sola fuente de verdad:** reutilizar entidades, Room, repositorios, outbox, RPC, migraciones, proyecciones y políticas que ya correspondan. No duplicar tablas o estados por conveniencia visual.
4. **Estados distintos para problemas distintos:** no mezclar epistemología, sincronización, permisos, moderación y disponibilidad técnica.
5. **Nada sintético en producción:** las pruebas pueden usar fixtures rotulados; la interfaz operativa no debe presentar datos ficticios como incidentes, recibos, fuentes o integraciones reales.
6. **Servidor autoritativo:** el cliente no puede concederse roles, validar su propia evidencia, elevar estados de conocimiento ni autorizar una divulgación.
7. **Datos insuficientes son un resultado válido:** sin cobertura, procedencia o corroboración suficientes, mostrar incertidumbre en lugar de una alerta positiva.
8. **Protección de fuentes:** ubicaciones, nombres, metadatos y originales sensibles no se exponen únicamente porque una pantalla o un endpoint sean accesibles.
9. **La documentación no es evidencia de ejecución:** cada prueba requiere comando, SHA y resultado real.
10. **Ninguna fusión o publicación automática:** la rama de trabajo no se fusiona por esta orden; la preparación de producción requiere gates independientes.

## 4. Estado observado en código: línea base y limitaciones

**Gate de compilación de esta línea base:** CI #37878488345 falló en `:app:compileDebugKotlin` dentro de `SafetyInstitutionalPresentationScreen.kt`, antes de validar el APK. Los errores observados fueron un paquete incorrecto para `rememberSaveable`, imports faltantes para `Box`/`clip` y una función de leyenda sin definición. Esta falla antecede al incremento de corrección documentado en el tracker; ningún APK de la línea base debe presentarse como validado.


Este cuadro resume la inspección estática del SHA de línea base declarado arriba. No equivale a una ejecución de Gradle, a pruebas contra Supabase desplegado ni a validación física del APK.

| Capacidad | Estado de línea base | Evidencia de código observada | Trabajo pendiente |
|---|---|---|---|
| Formulario de reporte | PARCIALMENTE IMPLEMENTADO | SafetyReportScreen y SafetyReportViewModel: flujo de varios pasos, categoría, relación con la información, relato, tiempo, ubicación y adjuntos | Reproducir el envío completo, comprobar errores, persistencia y recibo remoto |
| Evidencia local | IMPLEMENTADO EN CÓDIGO; extremo a extremo NO EJECUTADO | SafetyEvidenceRepository y SafetyEvidencePolicy: allowlist de tipos, límite de 20 MB, cifrado AEAD local y SHA-256 de los bytes leídos | Probar cambio de sesión, archivo corrupto, cancelación, archivo grande y restauración |
| Verificación remota de bytes | PARCIALMENTE IMPLEMENTADO | SafetyEvidenceVerificationGateway y función safety-evidence-verify comparan huella y cantidad de bytes | Ejecutar en staging, validar permisos/storage/RLS y comprobar recibo persistido |
| Mapa territorial | PARCIALMENTE IMPLEMENTADO | SafetyMapScreen y SafetyMapViewModel: capas, rangos temporales, lista, puntos públicos/privados y detalle | Auditar exposición geográfica por ruta, prueba de publicación y cobertura real de cada capa |
| Cronología | PARCIALMENTE IMPLEMENTADO | SafetyTimelinesScreen y su ViewModel, con marcas temporales y relaciones de fuente/evidencia | Comprobar orden temporal, cambios/correcciones y relación con recibos reales |
| Expedientes | PARCIALMENTE IMPLEMENTADO | SafetyCasesScreen y SafetyCaseDetailScreen | Probar ciclo de vida, permisos, conflicto, correcciones y contrapruebas de extremo a extremo |
| Núcleo científico | IMPLEMENTADO EN CÓDIGO; activación remota NO VERIFICADA | SciEntityEntity, SciClaimEntity, SciHypothesisEntity y SciEventEntity; SafetyScienceRepository y SupabaseScientificGateway | Verificar gates, RPC, proyecciones y resultados desplegados en staging |
| Estados de conocimiento | IMPLEMENTADO EN CÓDIGO, con brecha semántica por revisar | TruthStateMapping y EvidenceAssertionState; algunos estados del OS se reducen a INSUFFICIENT_EVIDENCE en la capa científica | Preservar el estado original junto al resultado conservador; no perder la diferencia entre SIMULATED, NOT_INTEGRATED, NOT_EXECUTED y UNKNOWN |
| Custodia criptográfica | IMPLEMENTADO EN CÓDIGO; ejecución actual NO VERIFICADA | CustodyProtocolV2, CustodyChain, MerkleTree, firmas y verificador TypeScript | Ejecutar paridad en el SHA exacto; probar firmas, formatos, pruebas de inclusión y alteraciones adversarias |
| Reglas de inteligencia financiera | PARCIALMENTE IMPLEMENTADO | FinancialObservationReviewPolicy y ProcurementConcentrationRule con pruebas en el repo | Ejecutar tests; validar datos completos, independencia real de las fuentes y revisión humana |
| Normalizador documental genérico | IMPLEMENTADO COMO LÓGICA DE DOMINIO | PublicProcurementRecordNormalizer normaliza campos, valida URL y calcula huella de campos normalizados | No es un conector ni una ingesta; construir un adaptador legal real solo después de validar fuente, permisos y pruebas |
| Intercambio institucional | NO VERIFICADO / PENDIENTE DE PILOTO | Gateway con verificación de JWT de máquina, vinculación mTLS y configuración por entorno | Configuración autorizada, acuerdo de integración, despliegue de prueba y pruebas completas |
| Presentación institucional | IMPLEMENTADA EN CÓDIGO; experiencia física NO VERIFICADA | SafetyInstitutionalPresentationScreen, hub de Safety y UI tests | CI, emulador y un dispositivo físico con el APK del mismo SHA |

### 4.1 Persistencia científica y evidencia

En las migraciones científicas inspeccionadas existen tablas como safety_scientific_claims, safety_scientific_claim_evidence, safety_scientific_events, safety_scientific_hypotheses, safety_scientific_evidence_references, safety_scientific_cases y safety_scientific_case_items. En las migraciones revisadas no se encontró una tabla llamada literalmente safety_scientific_evidence_bridge; antes de crear una nueva, se debe demostrar qué relación existente no cubre el flujo requerido.

Las migraciones de autoridad, inmutabilidad y RPC están presentes en código. Su presencia no demuestra que la base desplegada tenga esas migraciones aplicadas ni que las políticas se hayan validado en ejecución. Algunas feature gates científicas se siembran desactivadas de forma conservadora.

### 4.2 Limitaciones de verificación de esta línea base

- El análisis aquí documentado es revisión del código de GitHub, no checkout local.
- No se ejecutó Gradle, Vitest, tests de PostgreSQL/RLS, RPCs en Supabase ni pruebas de Android físico en esta revisión.
- Los runs CI consultados para la línea base estaban en cola o pendientes, sin conclusión.
- No se verificó el APK resultante de este SHA ni se afirma que se haya instalado en un teléfono.
- Cualquier documento previo con un SHA diferente o resultados de tests antiguos no debe utilizarse como certificado de este HEAD.

## 5. Arquitectura objetivo y recorrido vertical

### 5.1 Recorrido del reporte

1. **Captura:** la interfaz valida los campos obligatorios y mantiene explícitos los valores desconocidos.
2. **Construcción de payload:** se distinguen tiempo del hecho y tiempo de registro; ubicación y precisión conservan su fuente.
3. **Evidencia local:** se valida tipo/tamaño, se leen los bytes, se cifra localmente y se calcula SHA-256 sobre los bytes efectivamente capturados.
4. **Persistencia local:** Room y los estados locales representan la cola/estado pendiente; no afirman recepción remota.
5. **Comando idempotente:** el outbox permite reintentos sin duplicar el mismo comando.
6. **Autoridad remota:** el servidor deriva la identidad desde la sesión validada, aplica reglas y persiste.
7. **Recibo:** la interfaz solo declara éxito remoto cuando el servidor devuelve un recibo válido y verificable.
8. **Proyección pública:** los registros elegibles se publican con controles de divulgación, exposición geográfica y procedencia.
9. **Revisión:** expedientes, hipótesis y cronología conectan fuentes y contradicciones; una hipótesis no eleva por sí sola el estado de conocimiento.

Cada tramo se debe validar por separado y de extremo a extremo. Si falta un paso, el estado queda pendiente o desconocido.

### 5.2 Recorrido de evidencia

- Preservar el original de acuerdo con la base legal y política de retención.
- Identificar el archivo con ID estable y metadatos de mínima necesidad.
- Registrar bytes, tamaño, tipo, fuente y momento de incorporación.
- Vincularlo al reporte y, cuando aplique, a afirmaciones concretas.
- Separar original, miniatura, transcripción, OCR, resumen y análisis derivado.
- Verificar el archivo remoto comparando su hash y tamaño reales.
- Registrar el resultado como MATCH, MISMATCH, QUARANTINED o ERROR según el contrato real.
- Mantener historial de acceso, descarga, exportación, corrección y retiro.
- Un hash o una firma no demuestra veracidad, origen humano ni admisibilidad judicial.

### 5.3 Recorrido territorial

- Filtrar por rango temporal, categoría y zona.
- Diferenciar puntos públicos publicados de reportes privados de la sesión.
- Comunicar la exposición geográfica y las limitaciones del conjunto.
- Separar conteos observados, registros corroborados y datos de fuente competente cuando el contrato lo soporte.
- Nunca usar el número de puntos como equivalente automático al número de delitos.
- No interpretar una capa vacía como ausencia de incidentes.
- Evitar revelar domicilios, ubicaciones de fuentes o metadatos que faciliten represalias.

### 5.4 Recorrido científico

Evento → afirmación → fuente → evidencia → hipótesis → análisis → revisión.

Cada relación debe tener una fuente rastreable o aparecer como candidata/hipótesis explícita. Las contradicciones deben conservarse. La conclusión es una decisión humana con atribución y permiso, no una salida autónoma de IA.

## 6. Modelo de acceso y divulgación

Clases de información a separar:

- Privada aportada por fuente.
- Reporte ciudadano pendiente de revisión.
- Expediente restringido.
- Material autorizado para divulgación.
- Estadística territorial agregada.
- Registro público original o fuente documental permitida.
- Proyección institucional autorizada.

Controles requeridos:

- Autorización en servidor, Storage, RPC y consultas; ocultar un control Android no es seguridad.
- Identidad y organización obtenidas de claims validados; nunca confiar en actorId o role enviado por el cliente.
- Mínimo privilegio, separación por organización, auditoría de acceso y expiración de URLs firmadas.
- Redacción de metadatos secundarios, EXIF, nombres de archivo, miniaturas y referencias anidadas.
- Exportaciones con destinatario/alcance, autorización y evento de auditoría.
- Procesos documentados de impugnación, corrección, retiro, preservación legal y retención.
- Pruebas negativas para usuarios sin permisos y accesos cruzados entre organizaciones.

## 7. Línea de inteligencia financiera: límites y requisitos

La línea financiera es secundaria y modular. Reutiliza el núcleo científico donde proceda, pero no presume que exista ingestión activa.

- Usar exclusivamente fuentes y documentos accesibles de forma lícita y para el propósito autorizado.
- Registrar autoridad emisora, URL/identificador, fecha de publicación y recuperación, jurisdicción, método de extracción, huella de bytes originales, huella de datos normalizados, versión del parser y limitaciones.
- No convertir un digest de campos normalizados en una afirmación de hash byte-exacto del documento original.
- No fusionar entidades por nombre parecido, apellido, dirección compartida o semejanza textual.
- Vincular empresas/contratos mediante identificadores fuertes y documento de respaldo; si faltan, el resultado permanece sin resolver.
- Empezar con PostgreSQL relacional e índices reales; no incorporar otra base de grafos sin benchmarks.
- Reglas deterministas: fórmula, entradas, ventana, umbral justificado, cobertura mínima, falsos positivos, alternativas y versión.
- Datos insuficientes o sin procedencia verificada producen INSUFFICIENT_DATA.
- La riqueza visible, vehículos, joyería, vivienda o estilo de vida no generan un score de criminalidad ni un expediente público.
- La IA puede extraer candidatos, resumir y señalar discrepancias con fuentes; no verifica por sí sola, no publica acusaciones, no cambia estados y no se concede permisos.
- Toda señal queda sujeta a revisión humana y a contraevidencia.

## 8. Plan por fases

### Fase 0 — Baseline y contrato de verdad
- Fijar SHA, rama, migraciones, árbol, herramientas, pruebas y estado real de CI.
- Actualizar baseline audit con archivos y símbolos vistos.
- Etiquetar capacidades con IMPLEMENTED, PARTIALLY_IMPLEMENTED, DOCUMENTED_ONLY, SIMULATED, UNKNOWN, NOT_INTEGRATED y NOT_EXECUTED según evidencia.
- Reconciliar documentos antiguos que atribuyen PASS a otros SHA.
- Salida: BASELINE_AUDIT.md y matriz de capacidades.
- Gate: no comenzar cambios de esquema sin mapa de tabla/contrato y consumidores.

### Fase 1 — Presentación institucional y UX de Safety
- Mostrar solo reportes, evidencia, mapa, expedientes, cronología, observatorio e investigación en el modo institucional.
- Mantener el resto del sistema integral intacto.
- Hacer visible la secuencia captura → preservación → georreferenciación → relación de fuentes → contraste → revisión.
- Aplicar una estética futurista/neón consistente, tipografía legible, contraste, jerarquía y animaciones breves que respeten accesibilidad.
- Las tarjetas deben navegar a destinos reales; no agregar CTAs que solo aparenten funcionar.
- Las vistas vacías describen cobertura real y acción recuperable.
- Tests: renderizado, scroll, callbacks, estado de guía, leyenda de verdad y disclaimers.
- Gate: ejecutar UI test, revisar emulador y luego teléfono físico.

### Fase 2 — Reporte estructurado y preservación de los datos
- Auditar categoría, relato, tiempo del evento, tiempo de registro, ubicación, precisión, fuente/relación, adjuntos y campos opcionales.
- Distinguir fuente directa, testimonio, fuente documental y otros valores solo si el modelo real lo permite.
- No inventar valores cuando faltan.
- Probar reanudación, cancelación, cierre de pantalla, cambio de sesión y duplicación de envío.
- Gate: no afirmar persistencia remota sin confirmar RPC/recibo.

### Fase 3 — Evidencia y cadena de custodia
- Recorrer staging local, AEAD, SHA-256, outbox, carga, storage, verificador, recibo y manifest.
- Verificar byte count y hash desde los bytes recibidos por el servidor.
- Probar archivo vacío, MIME engañoso, archivo grande, bytes modificados, storage 404/403/5xx y credenciales expiradas.
- Separar claramente original y derivados; registrar método y versión de toda transformación.
- Verificar firmas, checkpoint, árbol de Merkle, ruta de verificación y control de claves.
- Gate: una coincidencia de hash no se presenta como verdad documental.

### Fase 4 — Epistemología y procedencia
- Reutilizar el contrato existente de TruthState y EvidenceAssertionState.
- Mantener el estado original del sistema junto con cualquier clasificación conservadora calculada.
- Corregir pérdida de semántica donde estados como SIMULATED, NOT_INTEGRATED, NOT_EXECUTED y UNKNOWN se colapsen en un único estado.
- No redefinir los enums de forma paralela en Kotlin, TypeScript y SQL.
- Cada transición requiere actor validado, motivo, evidencia y metodología cuando el contrato vigente lo disponga.
- Gate: pruebas de que una observación o salida de IA no escala automáticamente a AUTHORITATIVE.

### Fase 5 — Mapa e inteligencia territorial
- Probar filtros por categoría, rango temporal y zona.
- Revisar cada ruta de renderizado y publicación de coordenadas, incluyendo detalle, lista, notificaciones y exportación.
- Documentar precisión, agregación, cobertura, antigüedad y retraso de privacidad.
- Mostrar el estado de los datos sin afirmar que todos los puntos son delitos confirmados.
- Añadir pruebas de reidentificación y de ausencia de fuga de coordenadas sensibles.
- Gate: RLS/proyección en servidor y presentación Android deben coincidir.

### Fase 6 — Cronología, expedientes y revisión
- Vincular los tiempos de ocurrencia, conocimiento, registro y vigencia según el contrato.
- Relacionar eventos, afirmaciones, hipótesis y evidencia con referencias explícitas.
- Mantener contradicciones, refutaciones y correcciones sin borrado silencioso.
- Separar métricas de elementos únicos de conteos de relaciones/vínculos para no inflar cifras.
- Gate: un segundo revisor puede reconstruir la secuencia desde sus fuentes.

### Fase 7 — Privacidad, permisos y publicaciones
- Mapear roles a políticas reales, no a etiquetas visuales.
- Probar aislamiento entre usuarios/organizaciones por consultas directas, RPC y objetos Storage.
- Probar exportación y metadatos de archivos derivados.
- Aplicar corrección, impugnación, retiro y retención conforme a la política y base jurídica.
- Gate: ninguna divulgación sensible se autoriza por estado local ni por IA.

### Fase 8 — Persistencia y sincronización
- Mapear ViewModel → repositorio → Room → outbox → RPC → PostgreSQL → recibo → proyección.
- Verificar idempotencia, reintentos, leases, conflicto de versión, cambio de sesión y recuperación offline.
- No reportar ONLINE ni SUCCESS basado en el valor inicial de un estado o en una respuesta no validada.
- Gate: pruebas de extremo a extremo con recibo real desde entorno de prueba.

### Fase 9 — Autoridad científica en PostgreSQL
- Mapear migraciones, claves, constraints, triggers, RLS, grants, feature gates y RPCs reales.
- Ejecutar migraciones desde las versiones de esquema que el producto soporta.
- Comprobar que tablas append-only rechazan UPDATE/DELETE y que las correcciones generan nuevos eventos.
- Verificar que los gates desactivados realmente bloquean el uso y que errores de control fallan cerrados.
- Gate: suite SQL/RLS en staging, no solo lectura de migraciones.

### Fase 10 — Fuentes documentales externas, sin proveedor supuesto
- Definir el contrato común del adaptador y la política legal por fuente.
- Empezar con fixtures rotulados; no confundirlos con ingesta real.
- Un conector real requiere permiso, autenticación, límites de tasa, cobertura, paginación, recuperación, tiempos de consulta, hash byte-exacto y pruebas ante cambios de esquema.
- Conservar originales solo cuando lo permita la base jurídica/retención; en los demás casos, registrar limitación y mínimo metadato legal.
- Gate: fuente real identificada y autorizada, reproducción independiente y ejecución en staging.

### Fase 11 — Motor explicable de señales financieras
- No usar riqueza visible ni reportes de estilo de vida como regla de sospecha.
- Aplicar requisitos de muestra, procedencia verificable, grupos independientes y discrepancia documentada.
- Versionar reglas y registrar datos de entrada, fórmula, hipótesis alternativas y resultado reproducible.
- Verificar que fuentes no verificadas o copias de una sola fuente nunca generen una señal escalada.
- Gate: tests de falsos positivos y explicación alternativa, sin datos fabricados.

### Fase 12 — Asistente de investigación con IA
- Resumen, extracción de candidatos, cronología neutra, comparación de documentos y preguntas de investigación.
- Afirmaciones rastreables a fuente; separar cita literal, afirmación de la fuente e inferencia del modelo.
- Tratar documentos externos como datos no confiables frente a prompt injection.
- La IA no modifica evidencia, no promueve estados, no publica, no realiza acciones de ejecución y no decide permisos.
- Gate: red-team de prompt injection y revisión humana obligatoria.

### Fase 13 — Interoperabilidad institucional
- Acordar institución, persona responsable, alcance de datos, finalidad, base jurídica, autenticación y registro de acceso.
- Usar el gateway existente si satisface la arquitectura y los controles demostrados.
- Pilotar primero con datos de prueba o con datos reales autorizados.
- No afirmar integración en vivo antes de comprobar ambos sentidos, recepción, error, reintento, auditoría y aislamiento.
- Gate: prueba de extremo a extremo, credenciales rotables y aceptación formal del socio.

### Fase 14 — Materiales institucionales
- Demostración separada de módulos generales, sin eliminar esos módulos.
- Brief para diputados que distingue código existente, funciones verificadas, piloto pendiente y extensión futura.
- Guion de recorrido con reporte, evidencia, mapa, cronología y análisis.
- No presentar el dominio financiero como capacidad desplegada si aún está en diseño/normalización.
- Gate: cada afirmación del material apunta a código, prueba o etiqueta de estado.

### Fase 15 — Pruebas de aceptación completas
- Reporte no corroborado nunca aparece como hecho confirmado.
- Observación de riqueza visible sola produce insuficiencia de evidencia.
- Modificar bytes tras un hash causa fallo de verificación.
- Hash correcto no se interpreta como veracidad de contenido.
- Relación analítica se remonta a una fuente o se marca hipótesis.
- Usuario no autorizado no puede leer ni exportar evidencia restringida.
- Punto público no filtra domicilio o fuente vulnerable.
- Afirmación contradicha preserva historial y contraevidencia.
- Error remoto no crea recibo ficticio.
- Reintentos no duplican reportes, evidencia o eventos de custodia.
- Corrección no elimina el registro previo ni el historial de decisiones.
- Todos los módulos preexistentes pasan regresiones.

### Fase 16 — Operación, recuperación y producción
- Reproducibilidad desde SHA exacto.
- Migraciones ensayadas desde esquemas soportados.
- RLS y aislamiento validados contra PostgreSQL.
- Fuente real autorizada y trazabilidad verificable.
- Sincronización Android-servidor verificada.
- Backup/restore, rotación de claves, observabilidad e incident response probados.
- Límites de volumen, tamaño, rate limit y almacenamiento medidos.
- Artefacto y checksum vinculados a una ejecución que pasa gates.
- Gate: ninguna etiqueta SIMULATED, UNKNOWN o NOT_EXECUTED se presenta como VERIFIED.

### Fase 17 — Internacionalización y sostenibilidad
- Adaptadores separados por jurisdicción, base legal, idioma, formato y retención.
- Normalizar fechas, moneda e identificadores sin destruir el valor original.
- Conversiones monetarias solo con fuente de tipo de cambio y timestamp registrados.
- Validar comprador real antes de proyectar ingresos B2B.
- No vender identidades de fuentes, expedientes privados ni supresión de resultados.
- Gate: evaluación jurídica, seguridad de tenants y términos de procesamiento de datos.

## 9. Pruebas mínimas que deben existir

- Unitarias Kotlin para reglas, estados y normalizadores.
- Unitarias TypeScript para verificación de bytes, autenticación y parsers.
- Paridad cruzada Kotlin/TypeScript para formatos criptográficos.
- Integración PostgreSQL para constraints, triggers, RPC, RLS y grants.
- Contratos Android ↔ RPC: payload, recibo, error, idempotencia y versión.
- UI Compose para scroll, callbacks, estados vacíos y accesibilidad.
- Android físico para instalación, navegación y captura visual.
- Adversariales para datos falsos, prompt injection, metadatos y permisos.
- Reproducción por una segunda persona de un expediente de prueba.
- Reportes de resultados vinculados al SHA actual, con fallos sin ocultar.

## 10. Entregables de proyecto

1. BASELINE_AUDIT.md actualizado por SHA.
2. Esta IMPLEMENTATION_PLAN.md mantenida junto con el código.
3. DOMAIN_MODEL.md con contratos existentes y modelo objetivo.
4. SOURCE_ADAPTER_CONTRACT.md genérico, sin suponer una fuente operativa.
5. THREAT_MODEL.md, DATA_PROTECTION_AND_DISCLOSURE.md y ARCHITECTURE_DECISIONS.md reconciliados con el código.
6. INSTITUTIONAL_BRIEF_ES.md para la Asamblea Legislativa.
7. INSTITUTIONAL_UI_GUIDE.md con el recorrido visual y protocolo de verificación.
8. Matriz de capacidades y riesgos residuales con estado y evidencia.
9. Migraciones aditivas y pruebas de rollback cuando hagan falta.
10. Implementación vertical con pruebas, diff y comandos reproducibles.
11. Instrucciones locales para compilar, instalar y comprobar el APK de un SHA conocido.
12. Informe de aceptación que diferencia código presente, tests ejecutados, staging y dispositivo físico.

## 11. Orden de ejecución inmediato

La revisión también encontró que el Observatorio sintetizaba conteos de fuentes cuando faltaban datos. El Slice G de [TASK_TRACKER.md](TASK_TRACKER.md) corrige esa condición: los agregados provienen de la proyección V3 y, si falla, la UI declara que no hay métricas disponibles en lugar de inventarlas.

La lista viva [TASK_TRACKER.md](TASK_TRACKER.md) registra la implementación y las brechas restantes. La proyección de reportes enlaza IDs reales de evidencia local; el outbox científico remoto aún necesita integración y prueba.

El seguimiento verificable por fase está en [TASK_TRACKER.md](TASK_TRACKER.md). Cada casilla distingue el código escrito de las pruebas ejecutadas.

1. Corregir pruebas de UI que sigan esperando componentes ya retirados.
2. Completar la experiencia visual de la presentación institucional con un flujo accionable de seis etapas.
3. Añadir al mapa una explicación visible: punto accesible no equivale a incidente confirmado.
4. Fijar la matriz de capacidades desde un SHA y eliminar resultados de test atribuidos incorrectamente al HEAD.
5. Ejecutar CI y reparar errores únicamente con cambios justificados.
6. En checkout local, ejecutar unit tests Android, parity tests y build debug.
7. Realizar revisión de privacidad de puntos y descargas de evidencia antes del piloto.
8. Probar una ruta vertical completa en staging: reporte → adjunto → verificación remota → proyección autorizada → revisión.
9. Ejecutar pruebas en emulador y teléfono y documentar por separado cada resultado.
10. Continuar con el siguiente slice solo después de revisar los riesgos y mantener todos los módulos existentes.

## Criterio final de éxito

Elysium Safety debe convertir información dispersa en registros estructurados, trazables y revisables, sin fabricar datos, revelar fuentes sensibles, exagerar la cobertura ni transformar un reporte, una hipótesis o una correlación en una acusación. Su valor es que una segunda persona pueda seguir la evidencia hasta sus fuentes, identificar las limitaciones y reproducir el análisis.
