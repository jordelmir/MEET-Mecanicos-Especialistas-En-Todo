---
name: mep-national-curriculum
description: "Master implementation skill for the Costa Rican National Curriculum (MEP Grades 1.º to 11.º & Bachillerato por Madurez DGEC). Use when implementing, auditing, expanding, or verifying primary, secondary, and diversified educational tracks, Bloom 2-Sigma Socratic tutoring, FSRS spaced repetition, explorable Compose sandboxes, ISCO-08 vocational bridges, or cryptographic certified diplomas in MEET / Elysium OS."
---

# MEP National Curriculum (1.º a 11.º & BxM) — Master Skill

> **Principio Rector (`AGENTS.md`):**  
> *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*  
> El subsistema educativo de MEET / Elysium OS unifica la formación académica completa del Ministerio de Educación Pública (MEP) de Costa Rica con la capacitación técnica vocacional, la maéutica socrática con IA y la economía de servicios reales.

---

## 1. Guía de Activación (Trigger Guide)

Activa esta skill inmediatamente cuando la tarea involucre:
- **Malla Curricular MEP:** Diseñar, expandir, auditar o modificar pistas curriculares (`CurriculumTrack`) de 1.º a 11.º año o Bachillerato por Madurez (BxM / EDAD).
- **Unidades y Conceptos Oficiales:** Mapear distribuciones mensuales (febrero a diciembre) o temarios de la DGEC a modelos de datos canónicos (`CourseUnitData`, `CurriculumConceptData`).
- **Tutor Socrático de IA (Bloom 2-Sigma):** Implementar o ajustar andamiajes de 3 niveles, modelos mentales de experto, conceptos erróneos frecuentes (`misconceptions`) y analogías de taller mecánico/físico.
- **Motor de Memoria FSRS:** Configurar o calcular probabilidades de retención ($R = (1 + \text{factor} \cdot \frac{t}{S})^{-\alpha}$) y agendamiento de repasos espaciados.
- **Laboratorios Interactivos (Sandboxes Nativos en Compose):** Modificar o integrar sandboxes explorables (Geometría Analítica, Circuitos Eléctricos con multímetro, Dinámica Newtoniana).
- **Puentes Vocacionales a la Economía Real:** Conectar competencias técnicas de colegio con códigos de ocupación internacional ISCO-08 (`3118` CAD, `7411` Electricistas, `7126` Fontaneros, `7231` Mecánica Automotriz).
- **Diplomas y Micro-Credenciales Criptográficas:** Generar certificados con hash Merkle root SHA-256 y código QR canónico de 6 campos (Regla 4 de `AGENTS.md`).
- **Navegación Matricial de UI:** Mantener o refactorizar el `GradeAndSubjectMatrixNavigator` en Jetpack Compose para que todas las materias sean inmediatamente accesibles ("todo sale").

---

## 2. Invariantes Arquitectónicas y de Seguridad Absolutas

Cualquier cambio de código en este subsistema debe respetar estrictamente estos cuatro pilares:

### Invariante 1: Verdad Epistémica y Cero Datos Inventados (Regla 1 de `AGENTS.md`)
- **Nunca inventar planes de estudio ni competencias.** Todo contenido debe tener procedencia trazable a documentos oficiales del MEP o DGEC con hash SHA-256.
- Textos honestos permitidos cuando falte información:
  - `"Dato no capturado"`
  - `"Pendiente de validación"`
  - `"Confianza limitada"`
  - `"Requiere prueba física"`

### Invariante 2: Invariante Constitucional de Credenciales
$$\text{KNOWLEDGE} \neq \text{COMPETENCE} \neq \text{CREDENTIAL}$$
- Completar una simulación o curso otorga estado `DEMONSTRATED` o `ELYSIUM_VERIFIED`.
- **JAMÁS promover silenciosamente a `EXTERNALLY_CREDENTIALLED`** sin prueba externa autoritativa (licencia legal, colegiatura profesional o título oficial de la autoridad competente).

### Invariante 3: DAG de Prerequisitos No Circular y Zona de Desarrollo Próximo (ZDP)
- Las dependencias entre conceptos deben formar un Grafo Acíclico Dirigido (DAG) estricto.
- Ningún concepto avanzado (ej. BxM) puede desbloquearse si sus prerequisitos troncales (ej. 9.º año) no han alcanzado el umbral de maestría verificado ($\text{mastery} \ge 0.85$).
- Verificación obligatoria: `SELECT COUNT(*) FROM public.curriculum_prerequisites` y prueba de no-circularidad en `tests/education/verify-national-curriculum-registry-e2e.sh`.

### Invariante 4: Privacidad del Menor y Criptografía Forense (Ley 8968 y Regla 4)
- En cuentas de estudiantes menores de edad, rige `LearnerPrivacyLevel.PROTECTED_STUDENT` con anonimización de identidad.
- En el código QR del diploma certificado se incluyen **únicamente los 6 campos canónicos**:
  1. `report_id` (o `diploma_id`)
  2. `integrity_hash` (Merkle root SHA-256)
  3. `vehicle_id` / `learner_id`
  4. `generated_at`
  5. `report_type`
  6. `verifier_url`
- **NUNCA incluir nombres completos, cédulas, teléfonos o correos en el payload QR.**

---

## 3. Estructura de la Malla Curricular Nacional (40 Tracks)

| Ciclo Educativo | Grados | Materias Oficiales Registradas en Elysium OS |
| :--- | :--- | :--- |
| **I Ciclo (Primaria)** | 1.º, 2.º, 3.º | • Matemática 1.º, 2.º, 3.º<br>• Ciencias Primaria<br>• Español Primaria<br>• Estudios Sociales Primaria<br>• Inglés Primaria (Pre-A1) |
| **II Ciclo (Primaria)** | 4.º, 5.º, 6.º | • Matemática 4.º, 5.º, 6.º<br>• Ciencias Primaria<br>• Español Primaria<br>• Estudios Sociales Primaria<br>• Inglés Primaria |
| **III Ciclo (Secundaria / EGB)** | 7.º, 8.º, 9.º | • **7.º Año:** Matemática 7 (Zapandí), Español 7, Ciencias 7, Estudios Sociales 7, Cívica 7, Inglés 7, Fontanería 7 (Artes Ind.)<br>• **8.º Año:** Matemática 8 (Ujarrás), Español 8, Ciencias 8, Estudios Sociales 8, Cívica 8, Inglés 8, Dibujo Técnico CAD 8 (Artes Ind.)<br>• **9.º Año:** Matemática 9 (Tárcoles), Español 9, Ciencias 9, Estudios Sociales 9, Cívica 9, Inglés 9, Electricidad Residencial 9 (Artes Ind.) |
| **Educación Diversificada & Adultos** | 10.º, 11.º / BxM | • **Matemática BxM:** Geometría analítica, funciones, estadística y probabilidad.<br>• **Español BxM:** Ensayo, narrativa, morfosintaxis.<br>• **Estudios Sociales BxM:** Geopolítica del siglo XX y Costa Rica contemporánea.<br>• **Educación Cívica BxM:** Democracia, derechos humanos y régimen electoral.<br>• **Inglés BxM:** Lectura técnica B1-B2 según MCER.<br>• **Biología BxM:** Genética y ecología.<br>• **Química BxM:** Estequiometría y enlaces.<br>• **Física BxM:** Cinemática, Leyes de Newton, Termodinámica y Electromagnetismo. |

---

## 4. Flujo de Trabajo para Nuevas Implementaciones o Refactorizaciones

### Paso 1: Mapeo Canónico en `NationalCurriculumCatalogSeed.kt`
1. Declarar el nuevo track en `enum class CurriculumTrack` con `displayName`, `cycleName`, `gradeNumber` y `subjectName`.
2. Definir su lista oficial de unidades didácticas mensuales (`CourseUnitData`).
3. Registrar al menos un concepto clave (`CurriculumConceptData`) con código estandarizado (ej. `CR_FIS_BXM_NEWTON`).
4. Incluir reactivos interactivos: al menos 1 reactivo de opción múltiple conceptual y 1 reactivo de **transferencia real o de taller mecánico** (`isTransferTask = true`).
5. Conectar el track en `getUnitsForTrack(track)` del catálogo.

### Paso 2: Andamiaje Socrático en `NationalCurriculumDeepKnowledge.kt`
Para cada concepto nuevo, definir:
- `coreIntuition`: Explicación intuitiva sin fórmulas vacías.
- `expertMentalModel`: Protocolo mental paso a paso de un profesional o científico.
- `misconceptions`: Distractores cognitivos reales con contraejemplos y preguntas de remediación socrática.
- `socraticHintTiers`: Tres pistas progresivas (Orientación Inicial → Principio Fundamental → Conexión Práctica).
- `vocationalEngineeringBridge`: Aplicación en ingeniería, automoción, industria o servicios.

### Paso 3: Puentes Ocupacionales en `SkillToServiceBridge.kt`
- Vincular las habilidades prácticas a su respectivo código ISCO-08 y vertical de servicio (`ARCHITECTURAL_AND_TECHNICAL_CAD`, `RESIDENTIAL_ELECTRICAL_SERVICES`, `AUTOMOTIVE_MECHANICAL_DIAGNOSTICS`, `RESIDENTIAL_PLUMBING`).

### Paso 4: Base de Datos y Migración SQL (Supabase / Postgres)
- Crear o actualizar la migración correspondiente en `supabase/migrations/`.
- Insertar en `curriculum_sources`, `curriculum_units`, `curriculum_concepts`, `curriculum_prerequisites`, `curriculum_skills` y `skill_to_service_mappings`.

### Paso 5: Experiencia de Usuario en `ElysiumLearningScreen.kt`
- Integrar la materia en el `GradeAndSubjectMatrixNavigator` asegurando que:
  - El selector de grados filtre instantáneamente las materias del año.
  - Los iconos temáticos identifiquen la disciplina (`🧮`, `📖`, `🔬`, `🧪`, `⚡`, `🗺️`, `⚖️`, `🇬🇧`, `🛠️`).
  - Si la materia es técnica o de ciencias físicas/eléctricas, se activen los Sandboxes nativos correspondientes.

---

## 5. Batería de Verificación Obligatoria

Antes de certificar cualquier cambio curricular, deben ejecutarse y aprobarse al 100%:

```bash
# 1. Pruebas Unitarias de Educación y ViewModel (40 tracks)
./android/gradlew -p android testDebugUnitTest --tests "com.elysium369.meet.education.*"

# 2. Verificación de Paridad Criptográfica Cross-Runtime (Regla 6 de AGENTS.md)
bash tests/parity/ci-verify.sh

# 3. Prueba E2E de Base de Datos y DAG Curricular
bash tests/education/verify-national-curriculum-registry-e2e.sh

# 4. Compilación Completa del APK de Android
./android/gradlew -p android assembleDebug
```

---

## 6. Documentos de Referencia Incluidos en la Skill

- [Malla Curricular Completa y Códigos Oficiales](references/malla-curricular-completa.md)
- [Arquitectura Pedagógica Socrática y Motor FSRS](references/arquitectura-socratic-pedagogica.md)
- [Invariantes Constitucionales y Protección de Datos](references/invariantes-seguridad-y-normativas.md)
- [Mapeo de Puentes Ocupacionales ISCO-08](references/puentes-economicos-isco08.md)
