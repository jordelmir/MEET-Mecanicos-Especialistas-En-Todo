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


### Phase 8: Android Integration & Institutional Presentation
- Dedicated Android Presentation Mode (`PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY`).
- `SafetyInstitutionalDashboardScreen` & `SafetyPresentationModeStore`: Executive parliamentary dashboard displaying the 7 Core Capabilities and approved opening declaration.
- Mode switcher: Tabbed focus mode (`📑 Vista por Pestaña`) vs Full continuous reading (`📄 Documento Completo`).
- Interactive visual components:
  - `SafetyIncidentTypologyExplorer` (Interactive exploration of all 8 parliamentary categories with sample SHA-256 hashes, occurrence vs reporting timestamps, and structured evidence criteria)
  - `SafetyEvidenceManagerPanel` (Interactive SHA-256 integrity inspection, 1-byte tamper simulation, and custody protocol)
  - `SafetyTerritorialIntelligenceConsole` (Cantonal breakdown: San José, Limón, Puntarenas, Desamparados, Alajuela, temporal windows, and 25 km public residential blur)
  - `SafetyEpistemicTraceabilityCard` (OBSERVED -> AUTHORITATIVE -> DERIVED -> ESTIMATED -> UNKNOWN)
  - `SafetyEvidenceChainVisualizer` (Evento -> Afirmación -> Hipótesis -> Evidencia -> Análisis -> Conclusión)
  - `SafetyInstitutionalBridgeCard` (Puente Ciudadano -> Fuerza Pública / OIJ / Ministerio Público / Poder Judicial)
  - `SafetyInstitutionalBriefExportDialog` (Generador modal de expediente forense exportable con QR y SHA-256 manifest)
  - `SafetyFinancialIntelligenceCard` (SICOP Costa Rica & Salvaguarda Riqueza visible ≠ Delito)
- Strict navigation filtering in `MainActivity.kt`:
  - Automotive OBD diagnostics, rides dispatch, and marketplace modules completely suppressed from view/navigation.
  - Outer top status bars, palette customization icon, 3D companion overlays, and ride call overlays strictly hidden when `isInstitutionalPresentation == true`.
  - Back stack policy (`MeetBackStackPolicy`) hardened so back navigation never exits to the automotive home during parliamentary presentation.
- Preserves 100% of the entire codebase and modules intact without deleting any contracts (abiding by *"Todo en uno. Siempre a más, nunca a menos"*).
- *Status:* **IMPLEMENTED & VERIFIED**.

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

| Documento | Propósito y contenido |
|---|---|
| `docs/safety/global-financial-intelligence/MASTER_DIRECTIVE_ADDENDUM.md` | Addendum obligatorio de seguridad ciudadana, evidencia digital, inteligencia territorial y límites legales |
| `docs/safety/global-financial-intelligence/BASELINE_AUDIT.md` | Audit of exact HEAD, test evidence, end-to-end trace, and capability classification matrix |
| `docs/safety/global-financial-intelligence/AUTHORIZED_PUBLIC_CLAIMS.md` | Marco de afirmaciones públicas autorizadas, demostradas vs piloto vs prohibidas |
| `docs/safety/global-financial-intelligence/COSTA_RICA_PILOT_PLAN.md` | Protocolo de piloto controlado verificable con OIJ, Fuerza Pública y Fiscalía en Costa Rica |
| `docs/safety/global-financial-intelligence/THREAT_MODEL.md` | Privacy perimeter, adversary analysis, whistleblower safety |
| `docs/safety/global-financial-intelligence/DOMAIN_MODEL.md` | Formal entity specifications and epistemic state contracts |
| `docs/safety/global-financial-intelligence/SOURCE_ADAPTER_CONTRACT.md` | Lawful public records ingestion and SICOP schema |
| `docs/safety/global-financial-intelligence/DATA_PROTECTION_AND_DISCLOSURE.md` | Data classification, two-person rule, metadata stripping |
| `docs/safety/global-financial-intelligence/ARCHITECTURE_DECISIONS.md` | ADR-001 through ADR-005 technical rationales |
| `docs/safety/global-financial-intelligence/INSTITUTIONAL_BRIEF_ES.md` | Complete Spanish brief for the Legislative Assembly of Costa Rica |
