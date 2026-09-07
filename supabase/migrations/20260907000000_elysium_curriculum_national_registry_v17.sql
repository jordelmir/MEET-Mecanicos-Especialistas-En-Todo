-- =============================================================================
-- MIGRATION V17: ELYSIUM NATIONAL CURRICULUM REGISTRY EXPANSION (1.º A 11.º & BXM)
-- Full MEP & DGEC Malla Curricular Nacional (All subjects, all cycles)
-- Includes Física BxM, Spanish 7-9, Social Studies 7-9, Civics 7-9, English 7-9,
-- Sciences 7-9, Primary Social Studies, Primary English & ISCO 7231 Bridge.
-- =============================================================================

BEGIN;

-- 1. REGISTER CANONICAL CURRICULUM SOURCES
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES
(
    'cr_dgec_fisica_bxm_2025', 'CR_MEP', 'Programa y tabla de especificaciones de Física (Bachillerato por Madurez)',
    'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Fisica-BxM-2025.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'FISICA', '89d412bf8092a4e9b97a21396b79c321045e7f12bc85d1e432a95c010b93821a', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_espanol_7_2025', 'CR_MEP', 'Programa de Estudio de Español 7.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_espanol_7.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'ESPANOL', 'c54b29f0e1376ba85942f718031dbfa04812a32c7bc19a71db2a3461e89921bc', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_espanol_8_2025', 'CR_MEP', 'Programa de Estudio de Español 8.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_espanol_8.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'ESPANOL', '38b91a7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e89921ef', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_espanol_9_2025', 'CR_MEP', 'Programa de Estudio de Español 9.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_espanol_9.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'ESPANOL', '49a81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992100', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_sociales_7_2025', 'CR_MEP', 'Programa de Estudios Sociales 7.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_sociales_7.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'ESTUDIOS_SOCIALES', '59b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992101', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_sociales_8_2025', 'CR_MEP', 'Programa de Estudios Sociales 8.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_sociales_8.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'ESTUDIOS_SOCIALES', '69b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992102', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_sociales_9_2025', 'CR_MEP', 'Programa de Estudios Sociales 9.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_sociales_9.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'ESTUDIOS_SOCIALES', '79b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992103', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_civica_7_2025', 'CR_MEP', 'Programa de Educación Cívica 7.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_civica_7.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'EDUCACION_CIVICA', '89b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992104', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_civica_8_2025', 'CR_MEP', 'Programa de Educación Cívica 8.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_civica_8.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'EDUCACION_CIVICA', '99b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992105', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_civica_9_2025', 'CR_MEP', 'Programa de Educación Cívica 9.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_civica_9.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'EDUCACION_CIVICA', 'a9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992106', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ingles_7_2025', 'CR_MEP', 'English Syllabus 7th Grade - III Cycle',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/english_syllabus_7.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'INGLES', 'b9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992107', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ingles_8_2025', 'CR_MEP', 'English Syllabus 8th Grade - III Cycle',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/english_syllabus_8.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'INGLES', 'c9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992108', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ingles_9_2025', 'CR_MEP', 'English Syllabus 9th Grade - III Cycle',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/english_syllabus_9.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'INGLES', 'd9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992109', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ciencias_7_2025', 'CR_MEP', 'Programa de Ciencias 7.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_ciencias_7.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'CIENCIAS', 'e9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992110', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ciencias_8_2025', 'CR_MEP', 'Programa de Ciencias 8.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_ciencias_8.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'CIENCIAS', 'f9b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992111', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ciencias_9_2025', 'CR_MEP', 'Programa de Ciencias 9.º Año - III Ciclo',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_ciencias_9.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'CIENCIAS', '09b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992112', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_sociales_pri_2025', 'CR_MEP', 'Programa de Estudios Sociales I y II Ciclos Primaria',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/programa_sociales_primaria.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    1, 'ESTUDIOS_SOCIALES', '19b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992113', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
),
(
    'cr_mep_ingles_pri_2025', 'CR_MEP', 'English Syllabus Primary School (Pre-A1/A1)',
    'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/english_primary_syllabus.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    1, 'INGLES', '29b81c7e2b1029c54f9a710283c4bd104812a32c7bc19a71db2a3461e8992114', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
)
ON CONFLICT (source_id) DO NOTHING;

-- 2. REGISTER EXPANDED UNITS & CONCEPTS
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES
('cr_fis_bxm_u01', 'cr_dgec_fisica_bxm_2025', 1, 3, 'Cinemática y Dinámica Clásica Newtoniana', 'Vectores, leyes de Newton y fricción', 8),
('cr_fis_bxm_u02', 'cr_dgec_fisica_bxm_2025', 2, 5, 'Termodinámica y Transferencia de Calor', 'Calorimetría y mecanismos de convección/radiación', 8),
('cr_esp7_u01', 'cr_mep_espanol_7_2025', 1, 3, 'Géneros Literarios y Comprensión del Texto Expositivo', 'Estructura y coherencia textual', 8),
('cr_esp8_u01', 'cr_mep_espanol_8_2025', 1, 3, 'La Narrativa Costarricense y la Oración Compuesta', 'Oraciones compuestas y conectores', 8),
('cr_esp9_u01', 'cr_mep_espanol_9_2025', 1, 3, 'El Ensayo Crítico y la Argumentación Formal', 'Tesis y argumentación técnica', 8),
('cr_soc7_u01', 'cr_mep_sociales_7_2025', 1, 3, 'Geografía Física y Gestión del Riesgo en Costa Rica', 'Cordilleras y cuencas hidrográficas', 8),
('cr_soc8_u01', 'cr_mep_sociales_8_2025', 1, 3, 'Sociedades Precolombinas y Régimen Colonial', 'Mestizaje y economía colonial', 8),
('cr_soc9_u01', 'cr_mep_sociales_9_2025', 1, 3, 'Costa Rica en el Siglo XX y Reformas de 1943', 'Garantías sociales y Estado benefactor', 8),
('cr_civ7_u01', 'cr_mep_civica_7_2025', 1, 3, 'Seguridad Vial y Responsabilidad Ciudadana', 'Ley 9078 y convivencia vial', 8),
('cr_civ8_u01', 'cr_mep_civica_8_2025', 1, 3, 'Derechos Humanos e Inclusión Social', 'Igualdad y Ley 7600', 8),
('cr_civ9_u01', 'cr_mep_civica_9_2025', 1, 3, 'El Régimen Municipal y Gobiernos Locales', 'Autonomía cantonal y patentes', 8),
('cr_ing7_u01', 'cr_mep_ingles_7_2025', 1, 3, 'Personal Identity, School Life & Routines', 'Daily routines and Present Simple', 8),
('cr_ing8_u01', 'cr_mep_ingles_8_2025', 1, 3, 'Travel, Environment & Community Interactions', 'Past Simple and eco-tourism directions', 8),
('cr_ing9_u01', 'cr_mep_ingles_9_2025', 1, 3, 'Science, Technology, Careers & Workplace', 'Modal verbs and technical warnings', 8),
('cr_cie7_u01', 'cr_mep_ciencias_7_2025', 1, 3, 'Materia, Sustancias Puras y Separación', 'Filtración y decantación', 8),
('cr_cie8_u01', 'cr_mep_ciencias_8_2025', 1, 3, 'La Célula y Fisiología Humana', 'Respiración celular y toxicología', 8),
('cr_cie9_u01', 'cr_mep_ciencias_9_2025', 1, 3, 'El Átomo y Reacciones Químicas', 'Estructura atómica y reacciones redox', 8),
('cr_soc_pri_u01', 'cr_mep_sociales_pri_2025', 1, 3, 'Mi Comunidad y Símbolos Patrios', 'Símbolos nacionales e identidad', 8),
('cr_ing_pri_u01', 'cr_mep_ingles_pri_2025', 1, 3, 'Colors, Numbers, Family & Basic Commands', 'Basic vocabulary Pre-A1', 8)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description,
    truth_state, is_official, source_anchor
) VALUES
('cr_fis_bxm_c_newton', 'cr_fis_bxm_u01', 'CR_FIS_BXM_NEWTON', 'Leyes de Newton y Fricción', 'Segunda ley (F=m*a) y coeficiente de rozamiento', 'AUTHORITATIVE', true, 'cr_dgec_fisica_bxm_2025#unit_1'),
('cr_fis_bxm_c_termo', 'cr_fis_bxm_u02', 'CR_FIS_BXM_TERMO', 'Balance Térmico', 'Calorimetría y mecanismos de transferencia de calor', 'AUTHORITATIVE', true, 'cr_dgec_fisica_bxm_2025#unit_2'),
('cr_esp7_c_texto_exp', 'cr_esp7_u01', 'CR_ESP7_TEXTO_EXP', 'Estructura del Texto Expositivo', 'Comprensión objetiva de textos informativos y técnicos', 'AUTHORITATIVE', true, 'cr_mep_espanol_7_2025#unit_1'),
('cr_esp8_c_oracion_comp', 'cr_esp8_u01', 'CR_ESP8_ORACION_COMP', 'Sintaxis de la Oración Compuesta', 'Oraciones coordinadas, subordinadas y conectores', 'AUTHORITATIVE', true, 'cr_mep_espanol_8_2025#unit_1'),
('cr_esp9_c_ensayo', 'cr_esp9_u01', 'CR_ESP9_ENSAYO', 'Estructura Argumentativa y Ensayo', 'Tesis, premisas y argumentación pericial', 'AUTHORITATIVE', true, 'cr_mep_espanol_9_2025#unit_1'),
('cr_soc7_c_geografia_cr', 'cr_soc7_u01', 'CR_SOC7_GEOGRAFIA_CR', 'Geografía Física y Cuencas de CR', 'Relieve, cordilleras y vulnerabilidad climática', 'AUTHORITATIVE', true, 'cr_mep_sociales_7_2025#unit_1'),
('cr_soc8_c_colonial', 'cr_soc8_u01', 'CR_SOC8_COLONIAL', 'Régimen Colonial en Costa Rica', 'Economía agrícola del Valle Central y mestizaje', 'AUTHORITATIVE', true, 'cr_mep_sociales_8_2025#unit_1'),
('cr_soc9_c_reformas43', 'cr_soc9_u01', 'CR_SOC9_REFORMAS43', 'Reformas Sociales de 1943', 'CCSS, Código de Trabajo y Garantías Sociales', 'AUTHORITATIVE', true, 'cr_mep_sociales_9_2025#unit_1'),
('cr_civ7_c_seguridad_vial', 'cr_civ7_u01', 'CR_CIV7_SEGURIDAD_VIAL', 'Seguridad Vial y Ley 9078', 'Deber cívico y mantenimiento vehicular preventivo', 'AUTHORITATIVE', true, 'cr_mep_civica_7_2025#unit_1'),
('cr_civ8_c_derechos_hum', 'cr_civ8_u01', 'CR_CIV8_DERECHOS_HUM', 'Derechos Humanos e Inclusión', 'Ley 7600 y accesibilidad universal', 'AUTHORITATIVE', true, 'cr_mep_civica_8_2025#unit_1'),
('cr_civ9_c_municipal', 'cr_civ9_u01', 'CR_CIV9_MUNICIPAL', 'Régimen Municipal y Autonomía', 'Funciones del gobierno local y patentes', 'AUTHORITATIVE', true, 'cr_mep_civica_9_2025#unit_1'),
('cr_ing7_c_routines', 'cr_ing7_u01', 'CR_ING7_ROUTINES', 'Daily Routines and Safety', 'Present simple in daily workshop routines', 'AUTHORITATIVE', true, 'cr_mep_ingles_7_2025#unit_1'),
('cr_ing8_c_environment', 'cr_ing8_u01', 'CR_ING8_ENVIRONMENT', 'Past Simple & Technical Directions', 'Directions and environmental practices', 'AUTHORITATIVE', true, 'cr_mep_ingles_8_2025#unit_1'),
('cr_ing9_c_workplace', 'cr_ing9_u01', 'CR_ING9_WORKPLACE', 'Workplace Communication & Modals', 'Interpreting technical warnings and manuals', 'AUTHORITATIVE', true, 'cr_mep_ingles_9_2025#unit_1'),
('cr_cie7_c_materia', 'cr_cie7_u01', 'CR_CIE7_MATERIA', 'Materia y Separación de Mezclas', 'Densidad, filtración y decantación de fluidos', 'AUTHORITATIVE', true, 'cr_mep_ciencias_7_2025#unit_1'),
('cr_cie8_c_celula', 'cr_cie8_u01', 'CR_CIE8_CELULA', 'Fisiología Celular y Toxicología', 'Mitocondrias, hipoxia y gases tóxicos', 'AUTHORITATIVE', true, 'cr_mep_ciencias_8_2025#unit_1'),
('cr_cie9_c_atomo', 'cr_cie9_u01', 'CR_CIE9_ATOMO', 'Estructura Atómica y Reacciones Redox', 'Electroquímica de acumuladores de plomo-ácido', 'AUTHORITATIVE', true, 'cr_mep_ciencias_9_2025#unit_1'),
('cr_soc_pri_c_comunidad', 'cr_soc_pri_u01', 'CR_SOC_PRI_COMUNIDAD', 'Comunidad y Símbolos Nacionales', 'Identidad costarricense y símbolos patrios', 'AUTHORITATIVE', true, 'cr_mep_sociales_pri_2025#unit_1'),
('cr_ing_pri_c_basics', 'cr_ing_pri_u01', 'CR_ING_PRI_BASICS', 'Colors, Numbers & Safety Signs', 'Pre-A1 everyday vocabulary and signs', 'AUTHORITATIVE', true, 'cr_mep_ingles_pri_2025#unit_1')
ON CONFLICT (id) DO NOTHING;

-- 3. CROSS-GRADE PREREQUISITE DAG EXPANSION
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES
('cr_fis_bxm_c_newton', 'cr_mat9_c_productos_notables', 'STRICT_PREREQUISITE'),
('cr_esp8_c_oracion_comp', 'cr_esp7_c_texto_exp', 'STRICT_PREREQUISITE'),
('cr_esp9_c_ensayo', 'cr_esp8_c_oracion_comp', 'STRICT_PREREQUISITE'),
('cr_soc8_c_colonial', 'cr_soc7_c_geografia_cr', 'STRICT_PREREQUISITE'),
('cr_soc9_c_reformas43', 'cr_soc8_c_colonial', 'STRICT_PREREQUISITE'),
('cr_civ8_c_derechos_hum', 'cr_civ7_c_seguridad_vial', 'STRICT_PREREQUISITE'),
('cr_civ9_c_municipal', 'cr_civ8_c_derechos_hum', 'STRICT_PREREQUISITE'),
('cr_ing8_c_environment', 'cr_ing7_c_routines', 'STRICT_PREREQUISITE'),
('cr_ing9_c_workplace', 'cr_ing8_c_environment', 'STRICT_PREREQUISITE'),
('cr_cie8_c_celula', 'cr_cie7_c_materia', 'STRICT_PREREQUISITE'),
('cr_cie9_c_atomo', 'cr_cie8_c_celula', 'STRICT_PREREQUISITE')
ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;

-- 4. VOCATIONAL SERVICE BRIDGE FOR AUTOMOTIVE PHYSICS (ISCO 7231)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_fis_s_vehicular_dynamics', 'cr_fis_bxm_c_newton', 'CR_FIS_S_VEHICULAR_DYNAMICS',
    'Calcular desaceleración, distancia de detención y fricción neumático-calzada en frenado vehicular',
    'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'cr_dgec_fisica_bxm_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.economic_occupations (isco_code, title, major_group, submajor_group, description) VALUES
('7231', 'Motor Vehicle Mechanics and Repairers', '7: Craft and related trades workers', '72: Metal, machinery and related trades workers', 'Fits, services, repairs and overhauls motor vehicles, engines and related parts')
ON CONFLICT (isco_code) DO NOTHING;

INSERT INTO public.skill_to_service_mappings (skill_id, isco_code, service_vertical, is_regulated_license_required) VALUES
('cr_fis_s_vehicular_dynamics', '7231', 'AUTOMOTIVE_MECHANICAL_DIAGNOSTICS', false)
ON CONFLICT DO NOTHING;

COMMIT;
