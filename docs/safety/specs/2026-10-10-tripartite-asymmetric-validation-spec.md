# Especificación Técnica: Motor de Validación Tripartita Asimétrica (A, B, C) de Safety

**Fecha:** 2026-10-10  
**Estado:** Aprobado para Implementación  
**Autores:** Antigravity AI Engine & Jor Delmir (Elysium Vanguard OS)  
**Marco Constitucional:** Constitución de Safety (Artículo 8) & Carta Operativa AGENTS.md

---

## 1. Visión y Principio Operativo

En consonancia con el principio rector del proyecto MEET:
> *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

El subsistema de Safety garantiza que la verdad y la alerta comunitaria **no dependan del beneplácito ni de los tiempos de respuesta del Estado**. Si las instituciones estatales no responden o deciden archivar una denuncia, la sociedad civil organizada retiene la soberanía para validar hechos y proyectarlos al mapa territorial público.

### Estamentos de Validación (Grupos A, B y C)

1. **Grupo A (Prensa & Investigación Documental)**:
   - Periodistas de investigación, medios de comunicación independientes, consorcios periodísticos y analistas de fuentes abiertas (OSINT).
   - Rol: Corroboración de hechos, análisis testimonial, evidencia audiovisual contrastada y verificación espaciotemporal.
   - Identificador de Rol: `TRUST_REVIEWER` / `JOURNALISTIC_REVIEWER`.

2. **Grupo B (Juristas & Especialistas en Derecho / DDHH)**:
   - Abogados litigantes, colegios de abogados, clínicas jurídicas universitarias y organizaciones defensoras de derechos humanos.
   - Rol: Tipificación jurídica, consistencia probatoria, legalidad formal y encuadre en garantías procesales.
   - Identificador de Rol: `LEGAL_REVIEWER`.

3. **Grupo C (Autoridades Públicas & Órganos Jurisdiccionales - Demostrativo / Reserva)**:
   - Organismos de investigación judicial (OIJ), Ministerio Público / Fiscalía, Policía Nacional, Defensoría de los Habitantes, Ministerios.
   - Rol: Recepción institucional, apertura de causa penal y certificación oficial de medidas cautelares o allanamientos.
   - Identificador de Rol: `AUTHORITY_REVIEWER`.
   - **Regla de Operación Asimétrica**: El Grupo C es **opcional, demostrativo y no bloqueante**. Permanece en estado `"Bloqueado: Pendiente de Convenio Marco"` en la consola operativa ordinaria, pero cuenta con simulación interactiva para presentaciones parlamentarias (Diputados) y un formulario de pre-registro institucional.

---

## 2. Reglas de Validación y Proyección al Mapa

### Regla 2.1 — Umbral de Proyección Territorial (1 de 3)
* Un reporte ciudadano en estado `OBSERVED` (bóveda privada) pasa a `PUBLISHED` en el mapa territorial abierto cuando recibe la calificación aprobatoria de **al menos uno (1)** de los tres estamentos:
  $$\text{Proyección al Mapa} \iff \text{Validado}(A) \lor \text{Validado}(B) \lor \text{Validado}(C)$$
* **Principio de No-Veto**: La ausencia o inacción del Grupo C (Autoridades) **nunca** frena ni censura la publicación promovida por el Grupo A o el Grupo B.

### Regla 2.2 — Trazabilidad y Badges en el Mapa
En el mapa y en las tarjetas de detalle de incidentes, el reporte muestra explícitamente qué revisores han validado el caso:
* `[A]`: *Validación Periodística* (Alerta temprana documentada).
* `[B]`: *Validación Jurídica* (Consistencia legal y tipificación).
* `[A + B]`: *Validación Civil Plena* (Máxima fiabilidad ciudadana e independiente).
* `[C: DEMO]`: *Canal de Autoridad en Reserva / Muestra Institucional*.
* `[A + B + C]`: *Validación Tripartita de Estado*.

---

## 3. Arquitectura de Componentes Modificados

1. **`SafetyPublicPointEntity.kt` & Adaptadores**:
   - Soporte para flags y conteos de validación por estamento:
     - `journalisticSourceCount` (Grupo A)
     - `publicRecordSourceCount` (Grupo B)
     - `institutionalSourceCount` (Grupo C)
     - Métodos auxiliares para determinar los badges activos `[A]`, `[B]`, `[C]`.

2. **`SafetyTwoPersonValidationCard.kt` -> Evolución Tripartita**:
   - Expansión a 3 revisores: Revisor A (Prensa), Revisor B (Jurídico), Revisor C (Autoridad Pública - Muestra).
   - Control interactivo que demuestra la regla "1 de 3 proyecta al mapa".
   - Slot C bloqueado con advertencia de convenio marco y botón demo para simular la incorporación institucional ante diputados.

3. **`SafetyReviewerAccreditationScreen.kt`**:
   - Selector de perfiles de acreditación:
     - Revisor A (Periodismo)
     - Revisor B (Derecho / Jurídico)
     - Revisor C (Entidad Pública - Demo con campos de institución, oficio y firma gubernamental).

4. **`SafetyMapScreen.kt`**:
   - Inclusión de chips visibles `[A] Prensa`, `[B] Jurídico`, `[C] Autoridad` en la tarjeta de detalle `PublicPointDetail`.

5. **`SafetyInstitutionalDashboardScreen.kt`**:
   - Muestra de la consola tripartita integrada para la asamblea legislativa y observadores internacionales.
