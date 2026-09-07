# Puentes Vocacionales y Económicos: MEP hacia ISCO-08

Este documento especifica la integración entre las competencias académicas del Currículo Nacional MEP (Costa Rica) y la Clasificación Internacional Uniforme de Ocupaciones (CIUO-08 / ISCO-08 de la OIT - Organización Internacional del Trabajo).

---

## 1. Filosofía de la Integración: De la Teoría a la Productividad Real

En Elysium OS / MEET, la educación formal no es un fin abstracto ni un ejercicio estéril de memorización. Cada concepto y habilidad curricular oficial del MEP tiene un **vector de transferencia económica directa** hacia ocupaciones productivas de alto valor técnico y social.

El estudiante o aprendiz no solo aprende cómo despejar una ecuación o cómo funciona la segunda ley de Newton: descubre y ejercita **cómo esa ley le permite diagnosticar un fallo de frenos ABS en un vehículo**, calcular la caída de tensión en un arnés eléctrico, o redactar un dictamen técnico pericial admisible ante un tribunal.

---

## 2. Matriz Maestra de Mapeo MEP $\leftrightarrow$ ISCO-08

| Materia MEP | Habilidad / Concepto MEP | Código ISCO-08 | Título Ocupacional ISCO-08 | Aplicación Técnica y Productiva en el Taller / Industria |
|---|---|---|---|---|
| **Física BxM / Colegios** | Dinámica vehicular, fricción y leyes de Newton (`cr_fis_s_vehicular_dynamics`) | `7231` | Mecánicos y reparadores de vehículos de motor | Diagnóstico de sistemas de frenos, cálculo de distancia de frenado según coeficiente de fricción ($\mu$), alineación y estabilidad dinámica de chasis. |
| **Física BxM / Colegios** | Circuitos de corriente continua y Ley de Ohm (`cr_fis_s_electrical_circuits`) | `7412` | Mecánicos y ajustadores electricistas | Diagnóstico de multiplexado CAN-bus, medición de consumo parásito, dimensionamiento de fusibles y relevadores, caída de tensión en cableado automotriz. |
| **Física BxM / Colegios** | Termodinámica, calorimetría y ciclos térmicos (`cr_fis_s_thermodynamics`) | `7127` | Mecánicos de climatización y refrigeración | Diagnóstico del sistema de aire acondicionado automotriz (R134a/R1234yf), eficiencia del ciclo Carnot, cálculo de disipación térmica del radiador. |
| **Física BxM / Colegios** | Óptica y ondas electromagnéticas (`cr_fis_s_optics`) | `3114` | Técnicos en ingeniería electrónica | Calibración de sensores ADAS (radar milimétrico, cámaras estereoscópicas para mantenimiento de carril y frenado autónomo de emergencia). |
| **Matemáticas Secundario** | Geometría analítica, trigonometría y vectores (`cr_mat10_c_circ_geom`, `cr_mat10_c_rectas`) | `3112` / `7214` | Técnicos en ingeniería civil / Trazadores y montadores de estructuras | Alineación láser 3D de chasis y carrocería en banco de estirado, cálculo de ángulos de dirección (Camber, Caster, Toe) y empuje. |
| **Matemáticas Secundario** | Funciones lineales y cuadráticas (`cr_mat9_c_notables`, `cr_mat10_c_parabolas`) | `3322` / `1324` | Agentes comerciales / Directores de abastecimiento y distribución | Optimización de curvas de consumo de combustible por RPM, proyección de costos de mantenimiento de flotas de transporte pesado. |
| **Matemáticas Secundario** | Estadística descriptiva y probabilidades (`cr_mat11_c_stats`) | `3313` / `4312` | Técnicos en contabilidad / Empleados de cálculo de costos y estadísticas | Análisis de confiabilidad de repuestos (MTBF - Mean Time Between Failures), cálculo de reservas para garantías mecánicas, control estadístico de calidad (Six Sigma). |
| **Español Secundario** | Comprensión lectora técnica e inferencial (`cr_esp7_c_compr_lectora`) | `3341` | Supervisores de oficina y talleres | Interpretación rigurosa de manuales de servicio de fábrica (OEM Factory Service Manuals) y boletines de servicio técnico (TSB) sin errores de procedimiento. |
| **Español Secundario** | Redacción técnica y argumentativa (`cr_esp_s_informe_pericial`) | `2422` / `3343` | Especialistas en políticas / Secretarios administrativos y periciales | Redacción de dictámenes forenses de colisión, reportes certificados de inspección pre-compra, reclamaciones formales ante aseguradoras y juzgados de tránsito. |
| **Estudios Sociales** | Geografía económica y desarrollo sostenible (`cr_soc9_c_desarrollo_sostenible`) | `2133` | Profesionales de la protección medioambiental | Gestión y disposición de residuos peligrosos (aceite usado, ácido de baterías de plomo, refrigerantes fluorados), cumplimiento de normativa de emisiones vehiculares (RTV/Dekra). |
| **Educación Cívica** | Marco constitucional y derechos laborales (`cr_civ7_c_constitucion`) | `3353` / `2423` | Inspectores de trabajo / Especialistas en recursos humanos | Cumplimiento del Código de Trabajo de Costa Rica, seguridad ocupacional y protocolos de riesgo laboral ante el INS (Instituto Nacional de Seguros) y CCSS. |
| **Inglés Secundario** | Inglés técnico para ingeniería y diagnóstico (`cr_ing7_c_tech_lexicon`) | `3512` | Técnicos en asistencia al usuario de TIC y soporte | Interpretación de códigos de falla OBD-II (Freeze Frame, Readiness Monitors, PID Live Data) con terminología SAE J1979 e interacción con proveedores internacionales de autopartes. |

---

## 3. Implementación en la Arquitectura de Software

### Capa Kotlin (`SkillToServiceBridge.kt`)
```kotlin
object SkillToServiceBridge {
    private val mappings = listOf(
        SkillToServiceMapping(
            skillId = "cr_fis_s_vehicular_dynamics",
            iscoCode = "7231",
            iscoTitle = "Motor vehicle mechanics and repairers",
            localTitle = "Diagnóstico Mecánico y Dinámica Vehicular",
            practicalApplication = "Cálculo de coeficientes de fricción, calibración de ABS y peritaje de distancia de frenado.",
            minimumMasteryLevel = 0.75f
        ),
        // Mapeos adicionales para electrónica, chasis, HVAC, etc.
    )

    fun getCareerOpportunitiesForSkill(skillId: String): List<SkillToServiceMapping> =
        mappings.filter { it.skillId == skillId }
}
```

### Capa Base de Datos PostgreSQL (`curriculum_skills` y `skill_to_service_mappings`)
```sql
INSERT INTO skill_to_service_mappings (
    id, skill_id, isco_unit_group_code, local_occupation_title, practical_application_description, min_competence_threshold
) VALUES (
    'bridge_fis_vehicular_dyn',
    'cr_fis_s_vehicular_dynamics',
    '7231',
    'Diagnóstico Mecánico y Dinámica Vehicular',
    'Cálculo de coeficientes de fricción, calibración de ABS y peritaje de distancia de frenado en taller mecánico profesional.',
    0.75
) ON CONFLICT (id) DO NOTHING;
```

---

## 4. Tareas de Transferencia de Taller en la Interfaz de Usuario

Cuando el aprendiz alcanza el umbral de competencia conceptual en un tema MEP (e.g. Leyes de Newton en Física BxM), la UI de `ElysiumLearningScreen` desbloquea automáticamente la **Tarea de Transferencia en Taller**:
- **Simulador Interactivo**: El usuario interactúa con el `AnalyticalGeometrySandbox` o el `ElectricalCircuitSandbox` para ajustar parámetros físicos reales.
- **Caso Clínico Automotriz**: Se le presenta un vehículo real con síntomas (e.g. pedal de freno esponjoso, código DTC P0500 de velocidad de sensor, distancia de frenado anómala en asfalto húmedo).
- **Validación Práctica**: Debe calcular el valor teórico, contrastarlo con la lectura del escáner y emitir una orden de reparación justificada técnicamente.
