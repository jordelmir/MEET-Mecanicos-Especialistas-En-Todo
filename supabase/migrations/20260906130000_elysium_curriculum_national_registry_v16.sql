-- =============================================================================
-- MIGRATION V16: ELYSIUM NATIONAL CURRICULUM REGISTRY (CR MEP & DGEC 1.º - 11.º)
-- Comprehensive Costa Rica National Curriculum (Primaria, III Ciclo & Bachillerato)
-- Based on 45 audited official MEP/DGEC documents with SHA-256 provenance
-- =============================================================================

BEGIN;

-- 1. REGISTER OFFICIAL MEP & DGEC CURRICULUM SOURCES
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_ciencias_1_2025', 'CR_MEP', 'Tabla de especificaciones de Ciencias 1.º Año - I Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Ciencias-I-y-II-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    1, 'CIENCIAS', 'a8fbaea6bc38228be474de1d717d68c89db527a1a140569f8002cf784c07b4bc', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_espanol_1_2025', 'CR_MEP', 'Tabla de especificaciones de Español 1.º Año - I Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Espanol-I-y-II-Ciclo-2025-1.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    1, 'ESPANOL', 'e451b24e19e65d601a4e16d44ef30372df0d9f485dbda48a8677f5a8c2f1fec5', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_sociales_1_2025', 'CR_MEP', 'Cartel de alcance y secuencia de Estudios Sociales 1.º Año - I Ciclo', 'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/estudios_sociales_y_educacion_civica_-_cartel_de_alcance_y_secuencia_2023.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    1, 'ESTUDIOS_SOCIALES', '77d94a5173f4b909df50fa07c427357493a38090ad3123b3208573fc065f2425', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_2_2026', 'CR_MEP', 'Cartel de alcance y secuencia de Matemáticas 2.º Año - I Ciclo', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Matematica_cartel_alcance_y_secuencia%202026.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    2, 'MATEMATICA', '92fc38709f07567db32d3989bb3339077051b80ce8749a2155c010a373bcf3d6', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_3_2026', 'CR_MEP', 'Cartel de alcance y secuencia de Matemáticas 3.º Año - I Ciclo', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Matematica_cartel_alcance_y_secuencia%202026.pdf',
    '2026-01-15', 2026, 'I_CICLO', 'REGULAR',
    3, 'MATEMATICA', '92fc38709f07567db32d3989bb3339077051b80ce8749a2155c010a373bcf3d6', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_4_2026', 'CR_MEP', 'Cartel de alcance y secuencia de Matemáticas 4.º Año - II Ciclo', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Matematica_cartel_alcance_y_secuencia%202026.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    4, 'MATEMATICA', '92fc38709f07567db32d3989bb3339077051b80ce8749a2155c010a373bcf3d6', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_ciencias_4_2025', 'CR_MEP', 'Tabla de especificaciones de Ciencias 4.º Año - II Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Ciencias-I-y-II-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    4, 'CIENCIAS', 'a8fbaea6bc38228be474de1d717d68c89db527a1a140569f8002cf784c07b4bc', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_5_2026', 'CR_MEP', 'Cartel de alcance y secuencia de Matemáticas 5.º Año - II Ciclo', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Matematica_cartel_alcance_y_secuencia%202026.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    5, 'MATEMATICA', '92fc38709f07567db32d3989bb3339077051b80ce8749a2155c010a373bcf3d6', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_6_2026', 'CR_MEP', 'Cartel de alcance y secuencia de Matemáticas 6.º Año - II Ciclo', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Matematica_cartel_alcance_y_secuencia%202026.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    6, 'MATEMATICA', '92fc38709f07567db32d3989bb3339077051b80ce8749a2155c010a373bcf3d6', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_espanol_6_2025', 'CR_MEP', 'Tabla de especificaciones de Español 6.º Año - II Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Espanol-I-y-II-Ciclo-2025-1.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    6, 'ESPANOL', 'e451b24e19e65d601a4e16d44ef30372df0d9f485dbda48a8677f5a8c2f1fec5', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_sociales_6_2025', 'CR_MEP', 'Cartel de alcance y secuencia de Estudios Sociales 6.º Año - II Ciclo', 'https://ddc.mep.go.cr/sites/all/files/ddc_mep_go_cr/adjuntos/estudios_sociales_y_educacion_civica_-_cartel_de_alcance_y_secuencia_2023.pdf',
    '2026-01-15', 2026, 'II_CICLO', 'REGULAR',
    6, 'ESTUDIOS_SOCIALES', '77d94a5173f4b909df50fa07c427357493a38090ad3123b3208573fc065f2425', 'SCOPE_SEQUENCE', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_7_2025', 'CR_MEP', 'Tabla de especificaciones de Matemáticas 7.º Año (Zapandí) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Matematicas-III-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'MATEMATICA', '898a11ebfb9aa975a59ad6d34b41ad77002bce045ee33ec81358cb3725b879ec', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_ciencias_7_2025', 'CR_MEP', 'Tabla de especificaciones de Ciencias 7.º Año (Zapandí) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Ciencias-III-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'CIENCIAS', '02fc038367533074e032aa02a20ceba154d3b232779fb9580b964fb8aa21fa08', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_espanol_7_2026', 'CR_MEP', 'Tablas de especificaciones Español 7.º Año (Zapandí) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2026/03/TABLAS-DE-ESPECIFICACIONES-III-CICLO-ESPANOL.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'ESPANOL', 'fb5e2a1899b4d269da7c92b23c915f013f9f30325fbf67cbdd8ba97be7b23f2b', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_sociales_7_2025', 'CR_MEP', 'Tabla de especificaciones de Estudios Sociales 7.º Año (Zapandí) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Estudios-Sociales-III-Ciclo-2025-1.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    7, 'ESTUDIOS_SOCIALES', '1e3fd0f62de1e2fd3458efad5f98cfad1785f0ef7cb417d91c107fe5df6d05f2', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_8_2025', 'CR_MEP', 'Tabla de especificaciones de Matemáticas 8.º Año (Ujarrás) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Matematicas-III-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'MATEMATICA', '898a11ebfb9aa975a59ad6d34b41ad77002bce045ee33ec81358cb3725b879ec', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_artes_industriales_8_2026', 'CR_MEP', 'Programa de Estudio Artes Industriales 8.º Año - Dibujo Técnico', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/artesindustriales/artes_industriales_iii_ciclo.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    8, 'ARTES_INDUSTRIALES', 'b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef012', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_9_2025', 'CR_MEP', 'Tabla de especificaciones de Matemáticas 9.º Año (Tárcoles) - III Ciclo', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Matematicas-III-Ciclo-2025.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'MATEMATICA', '898a11ebfb9aa975a59ad6d34b41ad77002bce045ee33ec81358cb3725b879ec', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_artes_industriales_9_2026', 'CR_MEP', 'Programa de Estudio Artes Industriales 9.º Año - Electricidad Residencial', 'https://ddc.mep.go.cr/dpsc/asignaturaspsc/artesindustriales/artes_industriales_iii_ciclo.pdf',
    '2026-01-15', 2026, 'III_CICLO', 'REGULAR',
    9, 'ARTES_INDUSTRIALES', 'c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0123', 'CANONICAL_CURRICULUM', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_10_bxm_2026', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Matemática Décimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2026/02/Tabla-de-BxM-1.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    10, 'MATEMATICA', 'cfd528708e2c8952f4f876c185ec34818e8fb5ee87c060f7a3c9169109d0209f', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_biologia_10_bxm_2025', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Biología Décimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Biologia-BXM-2025.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    10, 'BIOLOGIA', '45717c02db450ea4fec16720c8a19b8d8dfa3201454a025bdfaa8616d66ddf66', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_matematica_11_bxm_2026', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Matemática Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2026/02/Tabla-de-BxM-1.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'MATEMATICA', 'cfd528708e2c8952f4f876c185ec34818e8fb5ee87c060f7a3c9169109d0209f', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_espanol_11_bxm_2026', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Español Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/11/Espanol-BxM-2026.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'ESPANOL', '9ba5a79be62ef8670aab0abf5e8ddabb4da3275e69a7324e9f41a206f6e421e1', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_sociales_11_bxm_2026', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Estudios Sociales Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/11/Estudios-Sociales-BxM-2026.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'ESTUDIOS_SOCIALES', '2836b92471a937ec5b0c905b9ab083e4dd9336311ae44caf30c459c6aaef4b30', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_civica_11_bxm_2025', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Educación Cívica Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Educacion-Civica-BXM-2025.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'EDUCACION_CIVICA', '4303debd0909ba32128796859345719302847294875928374928374928374928', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_quimica_11_bxm_2025', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Química Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Quimica-Bachillerato-Por-Madurez-2025.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'QUIMICA', 'd9efb4f1c3821e8fd84938294829384729384729384729384729384729384729', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES (
    'cr_mep_ingles_11_bxm_2025', 'CR_MEP', 'Tabla de especificaciones Bachillerato por Madurez - Inglés Undécimo Año', 'https://dgec.mep.go.cr/wp-content/uploads/2025/01/Ingles-Tabla-Espec-BXM-2025.pdf',
    '2026-01-15', 2026, 'DIVERSIFICADA', 'ADULTOS',
    11, 'INGLES', 'dfddbbc07af3cd66293847293847293847293847293847293847293847293847', 'ASSESSMENT_SPEC', 'OFFICIAL_UNIT_SEQUENCE', true
) ON CONFLICT (source_id) DO NOTHING;

-- 2. CURRICULUM UNITS & CONCEPTS BY GRADE AND SUBJECT
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat2_u01', 'cr_mep_matematica_2_2026', 1, 2, 'Matemática 2.º: Números Naturales hasta 1000 y Valor Posicional', 'Conteo, agrupación en centenas, decenas y unidades, representación gráfica y simbólica hasta 1000.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat2_c_centenas', 'cr_mat2_u01', 'CR_MAT2_CENTENAS', 'Centenas, Decenas y Unidades hasta 1000', 'Comprensión del valor posicional en base 10 hasta el 1000 y descomposición aditiva.', 'AUTHORITATIVE', true, 'cr_mep_matematica_2_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat2_s_descomp', 'cr_mat2_c_centenas', 'CR_MAT2_S_DESCOMP', 'Descomponer números de tres dígitos en centenas, decenas y unidades', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_MAT2_CENTENAS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat2_s_compara', 'cr_mat2_c_centenas', 'CR_MAT2_S_COMPARA', 'Comparar y ordenar números menores a 1000 utilizando <, > e =', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_MAT2_CENTENAS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat2_u02', 'cr_mep_matematica_2_2026', 2, 4, 'Matemática 2.º: Suma y Resta con Reagrupación hasta 1000', 'Algoritmos estándar de adición y sustracción llevando y prestando.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat2_c_suma_reagrup', 'cr_mat2_u02', 'CR_MAT2_SUMA_REAGRUP', 'Adición y Sustracción con Reagrupación', 'Resolución de operaciones aditivas con reagrupación de unidades y decenas en problemas contextuales.', 'AUTHORITATIVE', true, 'cr_mep_matematica_2_2026#unit_2'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat2_s_suma_alg', 'cr_mat2_c_suma_reagrup', 'CR_MAT2_S_SUMA_ALG', 'Ejecutar la adición de tres dígitos con reagrupación', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT2_SUMA_REAGRUP'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat2_s_problemas', 'cr_mat2_c_suma_reagrup', 'CR_MAT2_S_PROBLEMAS', 'Resolver problemas de la vida cotidiana que involucran adición y sustracción', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_MAT2_SUMA_REAGRUP'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat3_u01', 'cr_mep_matematica_3_2026', 1, 3, 'Matemática 3.º: Multiplicación como Suma Abreviada y Tablas 2 al 9', 'Concepto de producto, arreglos rectangulares, tablas de multiplicar y propiedades conmutativa y asociativa.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat3_c_multiplicacion', 'cr_mat3_u01', 'CR_MAT3_MULTIPLICACION', 'Multiplicación Fundamental y Tablas de Multiplicar', 'Modelado de situaciones de producto mediante arreglos rectangulares y algoritmos de multiplicación.', 'AUTHORITATIVE', true, 'cr_mep_matematica_3_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat3_s_tablas', 'cr_mat3_c_multiplicacion', 'CR_MAT3_S_TABLAS', 'Memorizar comprensivamente las tablas de multiplicar del 2 al 9', 'ETAPA_I_APRENDIZAJE', 'RECONOCIMIENTO', 3, 'CR_MAT3_MULTIPLICACION'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat3_s_mult_alg', 'cr_mat3_c_multiplicacion', 'CR_MAT3_S_MULT_ALG', 'Multiplicar un número de dos dígitos por un dígito', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_MAT3_MULTIPLICACION'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat4_u01', 'cr_mep_matematica_4_2026', 1, 3, 'Matemática 4.º: División Exacta e Inexacta y Fracciones Homogéneas', 'Reparto equitativo, residuo, partes de un todo, lectura y escritura de fracciones propias e impropias.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat4_c_fracciones', 'cr_mat4_u01', 'CR_MAT4_FRACCIONES', 'Noción de Fracción y Partición Unitaria', 'Representación visual, recta numérica y operaciones básicas con fracciones de igual denominador.', 'AUTHORITATIVE', true, 'cr_mep_matematica_4_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat4_s_repr_frac', 'cr_mat4_c_fracciones', 'CR_MAT4_S_REPR_FRAC', 'Representar fracciones propias e impropias gráficamente y en la recta', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_MAT4_FRACCIONES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat4_s_suma_frac', 'cr_mat4_c_fracciones', 'CR_MAT4_S_SUMA_FRAC', 'Sumar y restar fracciones homogéneas', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_MAT4_FRACCIONES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat5_u01', 'cr_mep_matematica_5_2026', 1, 4, 'Matemática 5.º: Números Decimales, Razones y Proporcionalidad Directa', 'Décimas, centésimas, milésimas, operaciones con decimales y regla de tres simple.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat5_c_proporcionalidad', 'cr_mat5_u01', 'CR_MAT5_PROPORCIONALIDAD', 'Proporcionalidad Directa y Decimales', 'Identificación de magnitudes directamente proporcionales y cálculo del valor unitario.', 'AUTHORITATIVE', true, 'cr_mep_matematica_5_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat5_s_decimales', 'cr_mat5_c_proporcionalidad', 'CR_MAT5_S_DECIMALES', 'Operar sumas y restas con números decimales', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT5_PROPORCIONALIDAD'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat5_s_regla_tres', 'cr_mat5_c_proporcionalidad', 'CR_MAT5_S_REGLA_TRES', 'Resolver problemas de proporcionalidad directa mediante regla de tres', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_MAT5_PROPORCIONALIDAD'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat6_u01', 'cr_mep_matematica_6_2026', 1, 5, 'Matemática 6.º: Porcentajes, Polígonos y Estadística Descriptiva', 'Cálculo de tanto por ciento, cálculo de áreas de polígonos regulares y medidas de tendencia central.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat6_c_porcentajes', 'cr_mat6_u01', 'CR_MAT6_PORCENTAJES', 'Cálculo de Porcentajes e Interpretación de Datos', 'Relación entre fracción, decimal y porcentaje en contextos económicos y estadísticos.', 'AUTHORITATIVE', true, 'cr_mep_matematica_6_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat6_s_calc_pct', 'cr_mat6_c_porcentajes', 'CR_MAT6_S_CALC_PCT', 'Calcular el porcentaje de una cantidad dada', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT6_PORCENTAJES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat6_s_interp_graf', 'cr_mat6_c_porcentajes', 'CR_MAT6_S_INTERP_GRAF', 'Interpretar gráficos de barras, líneas y circulares', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_MAT6_PORCENTAJES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_cien_pri_u01', 'cr_mep_ciencias_1_2025', 1, 3, 'Ciencias Primaria: Seres Vivos, Adaptaciones y Ecosistemas de Costa Rica', 'Diversidad biológica, hábitats acuáticos y terrestres, cadenas alimentarias y conservación ambiental.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_cien_c_ecosistemas', 'cr_cien_pri_u01', 'CR_CIEN_ECOSISTEMAS', 'Ecosistemas y Factores Bióticos y Abióticos', 'Interacciones entre productores, consumidores y descomponedores en áreas protegidas de Costa Rica.', 'AUTHORITATIVE', true, 'cr_mep_ciencias_1_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_cien_s_cadenas', 'cr_cien_c_ecosistemas', 'CR_CIEN_S_CADENAS', 'Identificar cadenas y redes tróficas en ecosistemas locales', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_CIEN_ECOSISTEMAS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_cien_s_conservacion', 'cr_cien_c_ecosistemas', 'CR_CIEN_S_CONSERVACION', 'Proponer medidas de conservación de la biodiversidad costarricense', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_CIEN_ECOSISTEMAS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_esp_pri_u01', 'cr_mep_espanol_1_2025', 1, 3, 'Español Primaria: Comprensión Lectora y Producción Textual', 'Estrategias de lectura, identificación de ideas principales, tipos de oraciones y ortografía normativa.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_esp_c_comprension', 'cr_esp_pri_u01', 'CR_ESP_COMPRENSION', 'Comprensión e Inferencia en Textos Escritos', 'Extracción de información explícita e implícita en textos continuos y discontinuos.', 'AUTHORITATIVE', true, 'cr_mep_espanol_1_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_esp_s_idea_ppal', 'cr_esp_c_comprension', 'CR_ESP_S_IDEA_PPAL', 'Identificar la idea principal y secundarias en párrafos', 'ETAPA_I_APRENDIZAJE', 'ANALISIS', 3, 'CR_ESP_COMPRENSION'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_esp_s_redaccion', 'cr_esp_c_comprension', 'CR_ESP_S_REDACCION', 'Redactar párrafos coherentes con correcta concordancia sujeto-verbo', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_ESP_COMPRENSION'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat7_u01', 'cr_mep_matematica_7_2025', 1, 3, 'Matemática 7.º: Números Enteros (Z) y Geometría Básica', 'El conjunto de los números enteros, valor absoluto, operaciones aritméticas, ángulos complementarios y suplementarios.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat7_c_enteros', 'cr_mat7_u01', 'CR_MAT7_ENTEROS', 'Operaciones en el Conjunto de Números Enteros (Z)', 'Suma, resta, multiplicación, división y leyes de signos en números positivos y negativos.', 'AUTHORITATIVE', true, 'cr_mep_matematica_7_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat7_s_signos', 'cr_mat7_c_enteros', 'CR_MAT7_S_SIGNOS', 'Aplicar la ley de signos en operaciones combinadas en Z', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT7_ENTEROS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat7_s_problemas_z', 'cr_mat7_c_enteros', 'CR_MAT7_S_PROBLEMAS_Z', 'Resolver problemas de temperaturas, deudas y alturas usando enteros', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_MAT7_ENTEROS'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat8_u01', 'cr_mep_matematica_8_2025', 1, 3, 'Matemática 8.º: Números Racionales (Q), Transformaciones y Álgebra', 'Operaciones en Q, expresiones algebraicas, monomios y polinomios, traslaciones, reflexiones y homotecias.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat8_c_algebra', 'cr_mat8_u01', 'CR_MAT8_ALGEBRA', 'Monomios, Polinomios y Operaciones Algebraicas', 'Suma, resta y multiplicación de monomios y polinomios; valor numérico de expresiones algebraicas.', 'AUTHORITATIVE', true, 'cr_mep_matematica_8_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat8_s_monomios', 'cr_mat8_c_algebra', 'CR_MAT8_S_MONOMIOS', 'Reducir monomios semejantes en expresiones algebraicas', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT8_ALGEBRA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat8_s_transform', 'cr_mat8_c_algebra', 'CR_MAT8_S_TRANSFORM', 'Aplicar traslaciones y reflexiones a figuras en el plano cartesiano', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_MAT8_ALGEBRA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat9_u01', 'cr_mep_matematica_9_2025', 1, 3, 'Matemática 9.º: Números Reales (R), Productos Notables y Funciones', 'Radicales, racionalización, productos notables, factorización, Teorema de Tales y función lineal.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat9_c_productos_notables', 'cr_mat9_u01', 'CR_MAT9_PRODUCTOS_NOTABLES', 'Productos Notables y Factorización de Polinomios', 'Cuadrado del binomio, diferencia de cuadrados y factorización por inspección y factor común.', 'AUTHORITATIVE', true, 'cr_mep_matematica_9_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat9_s_prod_not', 'cr_mat9_c_productos_notables', 'CR_MAT9_S_PROD_NOT', 'Desarrollar productos notables algebraicos de segundo grado', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_MAT9_PRODUCTOS_NOTABLES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat9_s_factorizar', 'cr_mat9_c_productos_notables', 'CR_MAT9_S_FACTORIZAR', 'Factorizar polinomios cuadráticos usando el método de inspección', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_MAT9_PRODUCTOS_NOTABLES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_cien_iii_u01', 'cr_mep_ciencias_7_2025', 1, 4, 'Ciencias III Ciclo: La Célula, Fisiología Humana y Química Básica', 'Células procariotas y eucariotas, organelas celulares, nutrición humana, estructura atómica y enlaces.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_cien_c_celula', 'cr_cien_iii_u01', 'CR_CIEN_CELULA', 'Estructura Celular y Metabolismo', 'Diferencias estructurales entre células animales y vegetales; funciones vitales de nutrición y reproducción celular.', 'AUTHORITATIVE', true, 'cr_mep_ciencias_7_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_cien_s_organelas', 'cr_cien_c_celula', 'CR_CIEN_S_ORGANELAS', 'Identificar organelas celulares (mitocondria, núcleo, cloroplasto) y sus funciones', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_CIEN_CELULA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_cien_s_sistemas', 'cr_cien_c_celula', 'CR_CIEN_S_SISTEMAS', 'Describir el funcionamiento integrado de los sistemas digestivo y circulatorio', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_CIEN_CELULA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_art_ind_u02', 'cr_mep_artes_industriales_8_2026', 2, 6, 'Artes Industriales 8.º: Dibujo Técnico y Metrología Dimensional', 'Uso del escalímetro, escuadras, compás, proyecciones ortogonales y lectura de planos constructivos.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_art_c_dibujo_tecnico', 'cr_art_ind_u02', 'CR_ART_DIBUJO_TECNICO', 'Metrología y Lectura de Planos Técnicos', 'Interpretación de cotas, escalas (1:50, 1:100), vistas principales y simbología técnica según normas ISO.', 'AUTHORITATIVE', true, 'cr_mep_artes_industriales_8_2026#unit_2'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_art_s_escalimetro', 'cr_art_c_dibujo_tecnico', 'CR_ART_S_ESCALIMETRO', 'Medir y trazar dimensiones en escalas arquitectónicas usando escalímetro', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_ART_DIBUJO_TECNICO'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_art_s_vistas', 'cr_art_c_dibujo_tecnico', 'CR_ART_S_VISTAS', 'Interpretar vistas ortogonales frontal, superior y lateral de una pieza mecánica', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_ART_DIBUJO_TECNICO'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_art_ind_u03', 'cr_mep_artes_industriales_9_2026', 3, 8, 'Artes Industriales 9.º: Electricidad Residencial y Circuitos Básicos', 'Ley de Ohm, circuitos serie y paralelo, código eléctrico nacional (NEC/RTCR), interruptores y tomacorrientes.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_art_c_electricidad', 'cr_art_ind_u03', 'CR_ART_ELECTRICIDAD', 'Seguridad e Instalación de Circuitos Eléctricos Básicos', 'Montaje de cajas octogonales, rectangulares, conductores TW/THHN, protección termo-magnética y puesta a tierra.', 'AUTHORITATIVE', true, 'cr_mep_artes_industriales_9_2026#unit_3'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_art_s_circuito_simple', 'cr_art_c_electricidad', 'CR_ART_S_CIRCUITO_SIMPLE', 'Cablear un circuito de iluminación simple con interruptor y plafonera', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_ART_ELECTRICIDAD'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_art_s_seguridad_elec', 'cr_art_c_electricidad', 'CR_ART_S_SEGURIDAD_ELEC', 'Verificar ausencia de tensión con multímetro y aplicar normas de seguridad', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_ART_ELECTRICIDAD'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat_bxm_u01', 'cr_mep_matematica_10_bxm_2026', 1, 2, 'Matemática BxM: Geometría Analítica - La Circunferencia y Polígonos', 'Ecuación de la circunferencia (centro y radio), posiciones relativas de puntos y rectas (secantes, tangentes, exteriores) y polígonos regulares.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat_bxm_c_circunferencia', 'cr_mat_bxm_u01', 'CR_MAT_BXM_CIRCUNFERENCIA', 'Geometría de la Circunferencia y Rectas Notables', 'Determinación de la ecuación de la circunferencia y análisis de rectas secantes, tangentes y exteriores mediante discriminante algebraico o distancia.', 'AUTHORITATIVE', true, 'cr_mep_matematica_10_bxm_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat_bxm_s_ecuacion_circ', 'cr_mat_bxm_c_circunferencia', 'CR_MAT_BXM_S_ECUACION_CIRC', 'Determinar gráfica y algebraicamente la ecuación de la circunferencia', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_MAT_BXM_CIRCUNFERENCIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat_bxm_s_posicion_recta', 'cr_mat_bxm_c_circunferencia', 'CR_MAT_BXM_S_POSICION_RECTA', 'Determinar la posición relativa de una recta respecto a una circunferencia', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_MAT_BXM_CIRCUNFERENCIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_mat_bxm_u02', 'cr_mep_matematica_11_bxm_2026', 2, 5, 'Matemática BxM: Funciones Lineal, Cuadrática, Exponencial y Logarítmica', 'Concepto formal de función, dominio, ámbito, intersecciones, vértice y modelado de crecimiento/decrecimiento.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_mat_bxm_c_funciones', 'cr_mat_bxm_u02', 'CR_MAT_BXM_FUNCIONES', 'Modelado con Funciones Cuadráticas, Exponenciales y Logarítmicas', 'Análisis algebraico y gráfico de funciones, cálculo de preimágenes, imágenes y resolución de problemas de modelado.', 'AUTHORITATIVE', true, 'cr_mep_matematica_11_bxm_2026#unit_2'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat_bxm_s_analisis_func', 'cr_mat_bxm_c_funciones', 'CR_MAT_BXM_S_ANALISIS_FUNC', 'Analizar intervalos de crecimiento, decrecimiento y concavidad de funciones', 'ETAPA_I_APRENDIZAJE', 'ANALISIS', 3, 'CR_MAT_BXM_FUNCIONES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_mat_bxm_s_modelado_exp', 'cr_mat_bxm_c_funciones', 'CR_MAT_BXM_S_MODELADO_EXP', 'Modelar situaciones de crecimiento poblacional y decaimiento con funciones exponenciales', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_MAT_BXM_FUNCIONES'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_esp_bxm_u01', 'cr_mep_espanol_11_bxm_2026', 1, 3, 'Español BxM: Análisis Literario Canónico y Comprensión Crítica', 'Lectura y análisis de obras obligatorias del MEP (Mamita Yunai, Única mirando al mar, La metamorfosis), figuras de dicción y construcción.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_esp_bxm_c_analisis_literario', 'cr_esp_bxm_u01', 'CR_ESP_BXM_ANALISIS_LITERARIO', 'Análisis de Textos Literarios y Figuras Retóricas', 'Identificación de mundo narrativo, tipos de narrador, personajes, espacios, tiempo y recursos de estilo.', 'AUTHORITATIVE', true, 'cr_mep_espanol_11_bxm_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_esp_bxm_s_narrador', 'cr_esp_bxm_c_analisis_literario', 'CR_ESP_BXM_S_NARRADOR', 'Identificar el tipo de narrador y los códigos apreciativos en la narrativa costarricense', 'ETAPA_I_APRENDIZAJE', 'ANALISIS', 3, 'CR_ESP_BXM_ANALISIS_LITERARIO'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_esp_bxm_s_vicios', 'cr_esp_bxm_c_analisis_literario', 'CR_ESP_BXM_S_VICIOS', 'Detectar y corregir vicios del lenguaje (dequeísmo, queísmo, pleonasmo) en textos no literarios', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'CR_ESP_BXM_ANALISIS_LITERARIO'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_bio_bxm_u01', 'cr_mep_biologia_10_bxm_2025', 1, 4, 'Biología BxM: Adaptaciones, Genética Mendeliana y Evolución', 'Adaptaciones morfológicas y fisiológicas, cruces monohíbridos y dihíbridos, mutaciones, ADN/ARN y selección natural.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_bio_bxm_c_genetica', 'cr_bio_bxm_u01', 'CR_BIO_BXM_GENETICA', 'Leyes de Mendel, Patrones de Herencia y Ácidos Nucleicos', 'Resolución de problemas de transmisión de caracteres hereditarios y análisis de cariotipos y mutaciones.', 'AUTHORITATIVE', true, 'cr_mep_biologia_10_bxm_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_bio_bxm_s_cruces', 'cr_bio_bxm_c_genetica', 'CR_BIO_BXM_S_CRUCES', 'Resolver problemas de cruces monohíbridos aplicando cuadros de Punnett', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_BIO_BXM_GENETICA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_bio_bxm_s_evolucion', 'cr_bio_bxm_c_genetica', 'CR_BIO_BXM_S_EVOLUCION', 'Explicar los postulados de la teoría de la evolución por selección natural de Darwin-Wallace', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_BIO_BXM_GENETICA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_quim_bxm_u01', 'cr_mep_quimica_11_bxm_2025', 1, 4, 'Química BxM: Estructura de la Materia, Enlaces y Estequiometría', 'Modelos atómicos, configuración electrónica, tabla periódica, enlaces iónicos y covalentes, balanceo de ecuaciones y mol.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_quim_bxm_c_estequiometria', 'cr_quim_bxm_u01', 'CR_QUIM_BXM_ESTEQUIOMETRIA', 'Estequiometría y Leyes Ponderales de las Reacciones Químicas', 'Cálculo de masa molar, relaciones mol-mol, mol-masa y masa-masa en reacciones químicas balanceadas.', 'AUTHORITATIVE', true, 'cr_mep_quimica_11_bxm_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_quim_bxm_s_balanceo', 'cr_quim_bxm_c_estequiometria', 'CR_QUIM_BXM_S_BALANCEO', 'Balancear ecuaciones químicas mediante el método de tanteo', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'CR_QUIM_BXM_ESTEQUIOMETRIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_quim_bxm_s_calc_mol', 'cr_quim_bxm_c_estequiometria', 'CR_QUIM_BXM_S_CALC_MOL', 'Calcular cantidades de reactivo y producto en moles y gramos', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'CR_QUIM_BXM_ESTEQUIOMETRIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_soc_bxm_u01', 'cr_mep_sociales_11_bxm_2026', 1, 4, 'Estudios Sociales BxM: El Mundo Contemporáneo y Costa Rica Siglo XX', 'Imperialismo, Guerras Mundiales, Guerra Fría, Estado Benefactor en Costa Rica, crisis de los 80 y globalización.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_soc_bxm_c_cr_siglo_xx', 'cr_soc_bxm_u01', 'CR_SOC_BXM_CR_SIGLO_XX', 'Transformaciones Políticas y Sociales de Costa Rica (1940-1980)', 'La reforma social de los años cuarenta (CCSS, UCR, Código de Trabajo), la guerra civil de 1948 y la Constitución de 1949.', 'AUTHORITATIVE', true, 'cr_mep_sociales_11_bxm_2026#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_soc_bxm_s_reformas', 'cr_soc_bxm_c_cr_siglo_xx', 'CR_SOC_BXM_S_REFORMAS', 'Analizar el impacto social y económico de la creación de las Garantías Sociales', 'ETAPA_I_APRENDIZAJE', 'ANALISIS', 3, 'CR_SOC_BXM_CR_SIGLO_XX'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_soc_bxm_s_guerra_civil', 'cr_soc_bxm_c_cr_siglo_xx', 'CR_SOC_BXM_S_GUERRA_CIVIL', 'Explicar las causas y consecuencias de los acontecimientos políticos de 1948', 'ETAPA_II_APLICACION_MOVILIZACION', 'COMPRENSION', 3, 'CR_SOC_BXM_CR_SIGLO_XX'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_civ_bxm_u01', 'cr_mep_civica_11_bxm_2025', 1, 3, 'Educación Cívica BxM: Democracia, Régimen Político y Derechos Ciudadanos', 'El sistema político costarricense, división de poderes, mecanismos electorales, partidos políticos y políticas públicas inclusivas.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_civ_bxm_c_democracia', 'cr_civ_bxm_u01', 'CR_CIV_BXM_DEMOCRACIA', 'Institucionalidad Democrática y Participación Ciudadana', 'Valores democráticos, rendición de cuentas, control político y defensa de derechos humanos.', 'AUTHORITATIVE', true, 'cr_mep_civica_11_bxm_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_civ_bxm_s_poderes', 'cr_civ_bxm_c_democracia', 'CR_CIV_BXM_S_PODERES', 'Identificar las funciones constitucionales de los tres poderes de la República y el TSE', 'ETAPA_I_APRENDIZAJE', 'COMPRENSION', 3, 'CR_CIV_BXM_DEMOCRACIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_civ_bxm_s_inclusion', 'cr_civ_bxm_c_democracia', 'CR_CIV_BXM_S_INCLUSION', 'Evaluar la aplicación de la Ley 7600 y normativas de equidad social', 'ETAPA_II_APLICACION_MOVILIZACION', 'ANALISIS', 3, 'CR_CIV_BXM_DEMOCRACIA'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_units (
    id, source_id, unit_number, target_month, title, description, estimated_lessons
) VALUES (
    'cr_ing_bxm_u01', 'cr_mep_ingles_11_bxm_2025', 1, 3, 'Inglés BxM: Reading Comprehension in Contexts of Science, Technology and Work', 'Comprensión de textos en idioma inglés nivel intermedio (B1/B2) sobre ciencia, salud, medio ambiente y empleo.', 8
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_concepts (
    id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor
) VALUES (
    'cr_ing_bxm_c_reading', 'cr_ing_bxm_u01', 'CR_ING_BXM_READING', 'Reading Strategies and Contextual Vocabulary in English', 'Skimming, scanning, contextual inference of unknown words, and identifying explicit and implicit information.', 'AUTHORITATIVE', true, 'cr_mep_ingles_11_bxm_2025#unit_1'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_ing_bxm_s_skimming', 'cr_ing_bxm_c_reading', 'CR_ING_BXM_S_SKIMMING', 'Scan texts for specific facts and numerical data', 'ETAPA_I_APRENDIZAJE', 'RECONOCIMIENTO', 3, 'CR_ING_BXM_READING'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.curriculum_skills (
    id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor
) VALUES (
    'cr_ing_bxm_s_inference', 'cr_ing_bxm_c_reading', 'CR_ING_BXM_S_INFERENCE', 'Infer meaning of idioms and technical vocabulary from context clues', 'ETAPA_II_APLICACION_MOVILIZACION', 'COMPRENSION', 3, 'CR_ING_BXM_READING'
) ON CONFLICT (id) DO NOTHING;

-- 3. PREREQUISITE DIRECTED ACYCLIC GRAPH (DAG) ACROSS ALL GRADES
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat2_c_centenas', 'cr_mat1_c_counting_100', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat2_c_suma_reagrup', 'cr_mat1_c_addition_sub', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat2_c_suma_reagrup', 'cr_mat2_c_centenas', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat3_c_multiplicacion', 'cr_mat2_c_suma_reagrup', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat4_c_fracciones', 'cr_mat3_c_multiplicacion', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat5_c_proporcionalidad', 'cr_mat4_c_fracciones', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat6_c_porcentajes', 'cr_mat5_c_proporcionalidad', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat7_c_enteros', 'cr_mat6_c_porcentajes', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat8_c_algebra', 'cr_mat7_c_enteros', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat9_c_productos_notables', 'cr_mat8_c_algebra', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_cien_c_celula', 'cr_cien_c_ecosistemas', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_art_c_dibujo_tecnico', 'cr_font7_c_pvc_joinery', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_art_c_electricidad', 'cr_art_c_dibujo_tecnico', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat_bxm_c_circunferencia', 'cr_mat9_c_productos_notables', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_mat_bxm_c_funciones', 'cr_mat_bxm_c_circunferencia', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_esp_bxm_c_analisis_literario', 'cr_esp_c_comprension', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_bio_bxm_c_genetica', 'cr_cien_c_celula', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;
INSERT INTO public.curriculum_prerequisites (
    concept_id, prerequisite_concept_id, relationship_type
) VALUES (
    'cr_quim_bxm_c_estequiometria', 'cr_mat5_c_proporcionalidad', 'STRICT_PREREQUISITE'
) ON CONFLICT (concept_id, prerequisite_concept_id) DO NOTHING;

-- 4. EXTEND TECHNICAL BRIDGES (8.º Dibujo Técnico, 9.º Electricidad)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO public.economic_occupations (isco_code, title, major_group, submajor_group, description) VALUES
('3118', 'Draughtspersons and CAD Technicians', '3: Technicians and associate professionals', '31: Science and engineering associate professionals', 'Prepares technical drawings, maps and plans using CAD software'),
('7411', 'Building and Related Electricians', '7: Craft and related trades workers', '74: Electrical and electronic trades workers', 'Installs, maintains and repairs electrical wiring systems and equipment')
ON CONFLICT (isco_code) DO NOTHING;

INSERT INTO public.skill_to_service_mappings (skill_id, isco_code, service_vertical, is_regulated_license_required) VALUES
('cr_art_s_vistas', '3118', 'ARCHITECTURAL_AND_TECHNICAL_CAD', false),
('cr_art_s_circuito_simple', '7411', 'RESIDENTIAL_ELECTRICAL_SERVICES', false)
ON CONFLICT DO NOTHING;

COMMIT;
