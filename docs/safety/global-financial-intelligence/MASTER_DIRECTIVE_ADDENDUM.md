# ADDENDUM OBLIGATORIO — ELYSIUM SAFETY
## Seguridad Ciudadana, Evidencia Digital, Inteligencia Territorial e Investigación Financiera

> **Directiva Vinculante para la Orden Maestra de Implementación.**  
> Complementa la arquitectura de evidencia y la línea de inteligencia financiera, sin sustituir las instrucciones previamente establecidas en el plan maestro.  
> **Invariante Operativo:** *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

---

### 1. Objetivo del Producto

Construir y validar **Elysium Safety** como una plataforma modular orientada a:
$$\text{capturar} \longrightarrow \text{preservar} \longrightarrow \text{estructurar} \longrightarrow \text{georreferenciar} \longrightarrow \text{relacionar} \longrightarrow \text{analizar}$$
información relevante para la seguridad ciudadana y la investigación de interés público.

La plataforma tiene como misión evitar que información crítica quede dispersa entre teléfonos móviles, redes sociales, fotografías, vídeos, documentos aislados, mensajería y fuentes institucionales.

**Límites Constitucionales y de Autoridad:**
* La plataforma **no busca sustituir** a la Fuerza Pública (MSP), al Ministerio Público (Fiscalía General), al Organismo de Investigación Judicial (OIJ) ni al Poder Judicial.
* La plataforma **no decide culpabilidad**, no emite condenas penales, no sustituye competencias jurisdiccionales y **nunca presenta una denuncia ciudadana como un hecho judicialmente probado**.

---

### 2. Preservación de la Arquitectura Existente

**Inspección del Repositorio Real:**
* Se audita sobre el HEAD real (`feat/elysium-safety-intelligence-plan`), rama activa, migraciones de Room (versión 90), migraciones de Supabase (`supabase/migrations/`) y suites de prueba.
* **Componentes clave confirmados:**
  * `SafetyMapScreen.kt` (`android/app/src/main/kotlin/com/elysium369/meet/safety/ui/map/SafetyMapScreen.kt`).
  * Entidades científicas en Room: `SciEntityEntity`, `SciClaimEntity`, `SciHypothesisEntity`, `SciEventEntity`, `SciClaimEvidenceEntity` (puente reclamo–evidencia), `SciKnowledgeEventEntity`, `SciAuthorityAssertionEntity`.
  * Proyecciones públicas y privadas: `SafetyPrivateMapPoint`, `SafetyPublicPointEntity`, `SafetyPublicClaimEntity`, `SafetyPublicTimelineEntity`, `SafetyReportEntity`.
  * Cola durable local: `SafetyCommandOutboxEntity` (`safety_command_outbox`).
* **Trazabilidad de Extremo a Extremo:**
  $$\text{UI Android (Compose)} \xrightarrow{\text{ViewModel}} \text{Room Local (Outbox PENDING)} \xrightarrow{\text{Sync Gateway Client}} \text{Supabase / PostgreSQL RPC} \xrightarrow{\text{RLS + Custody Manifest}} \text{Receipt ACK}$$
* **Clasificación Rigurosa de Capacidades:**
  * Toda capacidad debe clasificarse como `IMPLEMENTED`, `PARTIALLY_IMPLEMENTED`, `DOCUMENTED_ONLY`, `SIMULATED`, `UNKNOWN` o `NOT_EXECUTED`.
* **Regla de No Eliminación:**
  * La exclusión de mecánica, diagnóstico automotriz, movilidad y marketplace aplica **estrictamente a la presentación institucional parlamentaria y a la navegación de demostración** (`PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY`).
  * **Ningún código, contrato, migración ni prueba del producto general se borra ni desintegra.**

---

### 3. Núcleo Funcional de Seguridad Ciudadana

#### A. Reportes Estructurados
* **Tipología de 8 categorías tipificadas:** Asaltos (`ASSAULT_ROBBERY`), Homicidios (`HOMICIDE`), Desapariciones (`MISSING_PERSON`), Situaciones sospechosas (`SUSPICIOUS_SITUATION`), Violencia (`VIOLENT_INCIDENT`), Narcotráfico y actividades de venta (`DRUG_SALE_ACTIVITY`), Emergencias (`EMERGENCY`), Incidentes de zona territorial (`ZONE_INCIDENT`).
* **Cronología dual:** Fecha y hora de ocurrencia formalmente diferenciadas de la fecha y hora de recepción/registro.
* **Georreferenciación:** Coordenadas espaciales conocidas, precisión estimada y celdas de agregación.
* **Modalidad y procedencia de la fuente:** Clasificación estricta entre testigo directo, familiar/vecino, fuente documental, periodística, registro público o institucional.
* **Material adjunto:** Identificadores estables y referencias a su procedencia original.
* **Límites epistémicos:** Estado de validación, registro de contradicciones y limitaciones declaradas.

#### B. Gestión de Evidencia Digital
* Identificadores estables (`EVID-xxx` / UUID) para fotos, videos y documentos.
* **Huella criptográfica SHA-256 calculada sobre los bytes exactos originales.**
* Vínculo formal explícito: `Archivo Original` $\longleftrightarrow$ `Reporte` $\longleftrightarrow$ `Afirmaciones respaldadas`.
* Registro inmutable de incorporación, accesos autorizados y exportaciones (`safety_evidence_audit_log`).
* **Separación estricta:** Originales intactos en almacenamiento privado protegido frente a derivados (miniaturas, transcripciones de audio, metadatos derivados).
* Manejo transparente de transferencias incompletas, errores de red y sincronización pendiente.

#### C. Cronología Investigativa
* Línea de tiempo estructurada con separación estricta entre tiempo de ocurrencia y tiempo de conocimiento.
* Distinción visual y ontológica entre acontecimientos observados, testimonios atribuidos, hipótesis de trabajo y análisis formal.
* Vínculos entre eventos condicionados a respaldo documental comprobable; en su defecto, marcados explícitamente como `HIPOTÉTICOS`.
* Capacidad de registrar y preservar evidencia contradictoria que debilite o refute una hipótesis.

#### D. Inteligencia Territorial
* Visualización cartográfica con clasificación de visibilidad: reportes privados, incidentes bajo verificación y datos confirmados.
* Filtros temporales (24h, 7d, 30d, 1y) y tipológicos.
* Tendencias y agrupaciones territoriales reproducibles sin juicios de culpabilidad.
* **Blindaje y Salvaguarda de Vida:**
  * Desplazamiento y dispersión geográfica mínima de **25 km** en la visualización pública residencial (`PublicGeoDisclosure.COARSE_GRID_25KM_PLUS`).
  * Supresión absoluta de domicilios privados, identidades de víctimas o fuentes de información que faciliten represalias.
  * **Un marcador cartográfico nunca equivale a un delito probado ni imputa autoría.**

---

### 4. Modelo Epistemológico y Cadena de Evidencia

#### Jerarquía de Estados de Conocimiento
$$\mathbf{OBSERVED} \longrightarrow \mathbf{AUTHORITATIVE} \longrightarrow \mathbf{DERIVED} \longrightarrow \mathbf{ESTIMATED} \longrightarrow \mathbf{UNKNOWN}$$

* `OBSERVED`: Acontecimiento aportado por testigo, ciudadano o informante. No constituye hecho probado.
* `AUTHORITATIVE`: Confirmación o peritaje emitido por autoridad legalmente calificada (OIJ, Fiscalía, peritos oficiales).
* `DERIVED`: Conclusión analítica resultante del cruce reproducible de fuentes primarias.
* `ESTIMATED`: Aproximación probabilística o modelo con incertidumbre declarada.
* `UNKNOWN`: Dato no capturado o no verificado. Jamás se inventa información.

#### Cadena Investigativa Inviolable
$$\mathbf{Evento} \longrightarrow \mathbf{Afirmación} \longrightarrow \mathbf{Fuente} \longrightarrow \mathbf{Evidencia} \longrightarrow \mathbf{Hipótesis} \longrightarrow \mathbf{Análisis} \longrightarrow \mathbf{Decisión}$$

#### Reglas de Integridad Probatoria:
1. **El hash SHA-256 corresponde a bytes físicos concretos.**
2. **Una firma digital Ed25519 valida la autoría y relación con la clave privada, NO la verdad fáctica del testimonio.**
3. **Un código QR resuelve a un registro autorizado verificable, sin exponer datos privados ni vulnerar el anonimato.**
4. **Un árbol de Merkle prueba únicamente inclusión e integridad de las hojas que contiene.**
5. **Prohibición comercial:** El sistema prohíbe terminantemente afirmar "admisibilidad judicial automática", "inmutabilidad absoluta universal" o "certificación institucional" sin convenio o validación técnica específica.

---

### 5. Separación de Dominios de Acceso y Publicación

Se establecen barreras lógicas estrictas tanto en interfaz como a nivel de servidor (Row-Level Security):
1. **Información Privada:** Datos confidenciales de la fuente y coordenadas sin procesar.
2. **Reportes en Revisión:** Datos ciudadanos en proceso de triaje.
3. **Expedientes Restringidos:** Casos de investigación con acceso limitado a fiscales o analistas asignados.
4. **Divulgación Aprobada:** Contenido autorizado tras revisión de daño y redacción de datos sensibles.
5. **Estadísticas Territoriales:** Agregaciones coarse-grid ($\ge$ 25 km) sin metadatos identificativos.
6. **Información Institucional:** Comunicados oficiales verificados.

**Privacidad de Archivos:**
* Eliminación obligatoria de metadatos EXIF (geolocalización de cámara, modelo de teléfono, serial) antes de cualquier publicación o derivación.
* Nombres de archivo aleatorizados (UUID) sin rutas locales que revelen nombres de usuario del denunciante.

---

### 6. Línea Complementaria: Inteligencia Financiera y Anticorrupción

* Desarrollada como **módulo investigativo complementario e independiente**, no como prerrequisito para el núcleo ciudadano de seguridad.
* **Fuentes Lícitas:** Contratación pública (SICOP Ley N.° 9986), Registro Nacional de la Propiedad, informes de la Contraloría General de la República (CGR), sentencias públicas.
* **Prioridad Relacional Auditada:** Tablas relacionales en PostgreSQL con identificadores fiscales unívocos (Cédula Jurídica / Cédula de Identidad). Se prohíbe fusionar personas o empresas basándose en similitud fonética o nombres compartidos.
* **Invariante Ético y Constitucional Inviolable:**
  $$\mathbf{Riqueza\ Personal\ Visible} \neq \mathbf{Delito\ ni\ Corrupción}$$
  * Un vehículo de gama alta, joyas, una residencia costosa o un estilo de vida visible **no constituyen por sí mismos prueba de corrupción, lavado de dinero ni delito**.
  * La plataforma **nunca generará puntuaciones de criminalidad** ni transformará denuncias ciudadanas de ostentación en imputaciones penales.
* **Motor de Anomalías Determinista:** Reglas matemáticas auditables (ej. concentración en compras públicas `ProcurementConcentrationRule`) con inclusión obligatoria de explicaciones legítimas alternativas (decretos de emergencia, proveedor exclusivo).
* **Rol de la Inteligencia Artificial:** La IA actúa únicamente como asistente de síntesis y extracción documental. **Tiene prohibido elevar estados epistémicos, emitir juicios de culpabilidad o modificar evidencia.**

---

### 7. Integración Android y Servidor

* **Arquitectura de Software:** Kotlin, Jetpack Compose, Arquitectura Limpia MVVM / MVI.
* **Persistencia Local:** Room Database como proyección local y soporte de operación fuera de línea (*Offline-First*).
* **Autoridad Remota:** Supabase / PostgreSQL con políticas RLS y RPCs transaccionales.
* **Operaciones Idempotentes:** Outbox persistente con claves de idempotencia UUID; acuses remotos con versión de servidor (`serverVersion > 0`).
* **Regla de Honestidad en Red:** Nunca mostrar como confirmado un reporte que permanezca localmente encolado, que no tenga recibo remoto o cuya sincronización haya fallado.
* **Compatibilidad de Migraciones:** Migraciones de Room y PostgreSQL continuas y aditivas, sin alterar tablas de versiones previas.

---

### 8. Presentación Institucional para Diputados

* **Aislamiento en Demostración:**
  * Ocultamiento total de módulos de mecánica, diagnósticos OBD, viajes y repuestos en la vista de los legisladores.
  * La reunión se enfoca exclusivamente en resolver el problema nacional de información de seguridad dispersa.
* **Estructura del Mensaje Institucional:**
  1. El problema: Evidencia perdida en redes sociales y teléfonos de particulares.
  2. Cómo se estructura un reporte en 8 categorías tipificadas.
  3. Cómo se calculan huellas SHA-256 y se verifica la cadena de custodia.
  4. Cómo se georreferencia sin confundir un reporte ciudadano con un delito probado.
  5. La distinción epistémica entre observación ciudadana y validación oficial.
  6. La entrega formal mediante expedientes forenses con código QR y manifiesto para OIJ y Fiscalía.
  7. Qué capacidades están demostradas, cuáles requieren piloto y cuáles son extensiones futuras.
* **La línea financiera global** se presenta como extensión independiente para periodistas o auditores, sin mezclarla con el piloto de seguridad ciudadana.

---

### 9. Piloto Verificable en Costa Rica

* **Alcance:** Piloto acotado territorialmente (ej. cantón piloto con fuentes y autoridades coordinadas).
* **Criterios de Éxito:**
  1. Registro de incidentes con tipología clara y ocurrencia vs. reporte.
  2. Vínculo de al menos un elemento probatorio con huella SHA-256 verificada.
  3. Visualización territorial aplicando el desenfoque de protección de 25 km.
  4. Demostración de persistencia local y sincronización con recibo remoto verificado.
  5. Capacidad de reproducción independiente por un segundo revisor forense.
* **Honestidad en Resultados:** Si los datos del piloto no evidencian un patrón delictivo o concentración, ese resultado debe declararse con total transparencia.

---

### 10. Pruebas de Aceptación Obligatorias

1. **Un reporte no corroborado jamás aparece como delito probado.**
2. **Una observación sobre riqueza visible no produce clasificación delictiva.**
3. **Un archivo alterado en 1 solo byte falla la verificación SHA-256.**
4. **Una huella criptográfica idéntica demuestra integridad de bytes, no la veracidad de la afirmación.**
5. **Toda relación analítica se remonta a una fuente documentada o queda marcada como hipótesis.**
6. **Un usuario no autorizado no puede consultar ni descargar evidencia privada.**
7. **La divulgación pública cartográfica aplica desenfoque $\ge$ 25 km y no filtra identidades.**
8. **Una afirmación refutada conserva su historial y la evidencia contradictoria.**
9. **Un error remoto nunca sintetiza un acuse de recibo ficticio.**
10. **Las capacidades preexistentes del sistema completo se mantienen operativas sin regresiones.**

---

### 11. Entregables y Estado de Ejecución

| Entregable | Ubicación | Estado |
|---|---|---|
| **Auditoría de Línea Base & HEAD** | `docs/safety/global-financial-intelligence/BASELINE_AUDIT.md` | VERIFICADA |
| **Directiva Maestra & Addendum** | `docs/safety/global-financial-intelligence/MASTER_DIRECTIVE_ADDENDUM.md` | INCORPORADA |
| **Plan de Implementación Maestro** | `docs/safety/global-financial-intelligence/IMPLEMENTATION_PLAN.md` | ACTUALIZADO |
| **Afirmaciones Públicas Autorizadas** | `docs/safety/global-financial-intelligence/AUTHORIZED_PUBLIC_CLAIMS.md` | GENERADO |
| **Plan del Piloto para Costa Rica** | `docs/safety/global-financial-intelligence/COSTA_RICA_PILOT_PLAN.md` | GENERADO |
| **Panel Parlamentario Android** | `SafetyInstitutionalDashboardScreen.kt` | IMPLEMENTADO |
| **Explorador de Tipologías (8)** | `SafetyIncidentTypologyExplorer.kt` | IMPLEMENTADO |
| **Gestión de Evidencia & Custodia** | `SafetyEvidenceManagerPanel.kt` | IMPLEMENTADO |
| **Inteligencia Territorial & 25km Blur** | `SafetyTerritorialIntelligenceConsole.kt` | IMPLEMENTADO |
| **Trazabilidad Epistémica** | `SafetyEpistemicTraceabilityCard.kt` | IMPLEMENTADO |
| **Cadena de Evidencia** | `SafetyEvidenceChainVisualizer.kt` | IMPLEMENTADO |
| **Puente Institucional & Expediente** | `SafetyInstitutionalBridgeCard.kt` / `SafetyInstitutionalBriefExportDialog.kt` | IMPLEMENTADO |
| **Inteligencia SICOP & Regla Riqueza** | `SafetyFinancialIntelligenceCard.kt` / `FinancialObservationReviewPolicy.kt` | IMPLEMENTADO |
| **Suites de Pruebas Unitarias** | `SafetyInstitutionalCapabilitiesTest.kt` y relacionadas | IMPLEMENTADO |
