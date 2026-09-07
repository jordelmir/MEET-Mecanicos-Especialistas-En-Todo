# Invariantes de Seguridad, Criptografía y Marco Normativo MEP / DGEC

Este documento codifica los invariantes no-negociables para cualquier agente (Codex, Mavis, Antigravity) o subsistema que opere sobre el módulo educativo y de certificación de Elysium OS / MEET.

---

## 1. Principio Constitucional Ontológico

$$\text{CONOCIMIENTO (KNOWLEDGE)} \neq \text{COMPETENCIA (COMPETENCE)} \neq \text{CREDENCIAL (CREDENTIAL)}$$

1. **Conocimiento**: Comprensión conceptual interna de leyes físicas, gramaticales, matemáticas o cívicas. Evaluado mediante razonamiento socrático, modelos mentales y resolución de problemas.
2. **Competencia**: Habilidad demostrable de transferencia en el mundo real (e.g. diagnosticar un sensor automotriz, redactar un recurso de amparo, auditar un estado financiero, calcular carga en un circuito eléctrico).
3. **Credencial**: Reconocimiento formal de acreditación emitido bajo la fe pública del Estado Costarricense (MEP / DGEC) o por Elysium OS con firma criptográfica.
4. **Regla de Separación**: Ningún agente ni interfaz puede asumir o promover que aprobar un quiz de opción múltiple equivale a competencia técnica de taller, ni que dominar una simulación constituye de facto título formal del MEP sin pasar por las pruebas oficiales de la DGEC.

---

## 2. Marco Normativo Oficial de Costa Rica

Toda estructuración curricular y evaluativa en Elysium OS respeta el bloque de legalidad educativa costarricense:

1. **Ley Fundamental de Educación (Ley N° 2160)**:
   - *Artículo 2*: Formación de ciudadanos conscientes de sus deberes y derechos, con sentido de responsabilidad y respeto a la dignidad humana.
   - *Artículo 3*: Desarrollo integral de la personalidad, estímulo a la capacidad crítica y preparación para el trabajo productivo.
2. **Reglamento de Evaluación de los Aprendizajes (REA - Decreto Ejecutivo N° 40862-MEP)**:
   - Evaluación diagnóstica, formativa y sumativa.
   - Rúbricas analíticas con escala de desempeño: Inicial, Intermedio, Avanzado.
   - La evaluación formativa alimenta el andamiaje Socrático sin penalizar la calificación sumativa inicial.
3. **Dirección de Gestión y Evaluación de la Calidad (DGEC)**:
   - **Bachillerato por Madurez Suficiente (BxM)**: Programa oficial para personas mayores de 18 años con noveno año aprobado (Decreto Ejecutivo N° 44100-MEP).
   - **Pruebas Nacionales Estandarizadas (PNE)**: Evaluación diagnóstica y sumativa de cierre de ciclo (6.º y 11.º).
   - Especificaciones técnicas de temarios (Objetivos, Contenidos, Habilidades específicas) por convocatoria anual (Convocatorias 01 y 02 de BxM).

---

## 3. Invariantes de Datos y Seguridad (AGENTS.md)

### Regla 1: Prohibición Absoluta de Fabricación de Datos
- **Nunca inventar datos curriculares o evaluativos.**
- Si un estándar del MEP está en revisión o no está capturado en la base de datos local, la única respuesta honesta permitida es:
  - `"Dato no capturado"`
  - `"Pendiente de validación curricular"`
  - `"Requiere confirmación oficial MEP/DGEC"`
  - `"Confianza limitada"`

### Regla 3: Inmutabilidad de Certificaciones y Reportes
- Una vez firmado un reporte de progreso o certificación de aprendizaje:
  - No se permiten ediciones silenciosas bajo ninguna circunstancia.
  - Toda corrección requiere generar una nueva versión encadenada mediante SHA-256 (`parent_report_hash`) o transicionar el estado a `VOIDED` con bitácora forense de auditoría.

### Regla 4: Privacidad en Códigos QR (Cero Fuga de PII)
- El payload del código QR de certificación está restringido estrictamente al esquema canónico de 6 campos:
  ```json
  {
    "report_id": "rep_cr_bxm_...",
    "integrity_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "subject_id": "cr_mat_bxm",
    "generated_at": 1772900000000,
    "report_type": "CERTIFIED_CURRICULUM_MASTERY",
    "verifier_url": "https://elysium369.com/verify/..."
  }
  ```
- **PROHIBIDO**: Incluir número de cédula completo, nombre y apellidos, número de teléfono, dirección física o correo electrónico dentro del código QR. La verificación de identidad se realiza exclusivamente a través del enlace criptográfico seguro en el servidor con autenticación de doble factor.

### Regla 6: Paridad Cross-Runtime (Kotlin ≡ TypeScript ≡ PostgreSQL)
- Todo cálculo de hash de integridad, calificación ponderada o serialización canónica debe producir **exactamente los mismos bytes** en Kotlin (Android App), TypeScript (Web / Node runtime) y PostgreSQL (Trigger / Functions).
- La suite de paridad `bash tests/parity/ci-verify.sh` debe mantenerse **100% en verde** en cada commit.

---

## 4. Integridad Criptográfica y Auditoría Forense

1. **Generación de Hash Canónico**:
   - Algoritmo: SHA-256.
   - Normalización de entrada: UTF-8, claves JSON ordenadas lexicográficamente, sin espacios en blanco superfluos (`compact formatting`).
2. **Firmas Digitales**:
   - Sello de tiempo con microsegundos UTC.
   - Identificador de la autoridad educativa emisora (`CR_MEP` o `CR_DGEC`).
3. **Verificación Offline**:
   - Cualquier inspector técnico, perito forense o supervisor del MEP debe poder validar la autenticidad matemática de un reporte impreso simplemente extrayendo el hash del QR y recalculando el SHA-256 sobre el payload certificado canónico.
