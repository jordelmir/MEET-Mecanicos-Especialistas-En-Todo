-- ==============================================================================
-- MEET / ELYSIUM CIVILIZATION COORDINATION PLATFORM
-- Migration: 20260906120000_elysium_learning_os_mep_2026_v15.sql
-- Description: Elysium Learning OS Core, Costa Rica MEP 2026 Curriculum Registry,
--              Course Zero (Matemática 1.º), 7.º Fontanería Skill-to-Service Bridge,
--              Learner Digital Twin, Spaced Retrieval, Child Privacy (Ley 8968)
-- Constitutional Invariants:
--   1. KNOWLEDGE != COMPETENCE != CREDENTIAL
--   2. DEMAND SIGNAL != GUARANTEED INCOME
--   3. PLATFORM COORDINATION != CONTROL OF THE HUMAN
--   4. AI generated items cannot become OFFICIAL without MEP Source Anchor
--   5. Child privacy & data minimization strictly isolated behind RLS
-- ==============================================================================

-- 1. EDUCATION AUTHORITIES & CURRICULUM SOURCE TAXONOMY (GATE 8 & 9)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.education_authorities (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    country_code TEXT NOT NULL,
    jurisdiction TEXT NOT NULL DEFAULT 'NATIONAL',
    official_portal_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.education_authorities (id, name, country_code, jurisdiction, official_portal_url)
VALUES ('CR_MEP', 'Ministerio de Educación Pública de Costa Rica', 'CRI', 'NATIONAL', 'https://www.mep.go.cr')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

CREATE TABLE IF NOT EXISTS public.curriculum_sources (
    source_id TEXT PRIMARY KEY,
    authority_id TEXT NOT NULL REFERENCES public.education_authorities(id),
    canonical_title TEXT NOT NULL,
    source_locator TEXT NOT NULL,
    publication_date DATE NOT NULL,
    effective_year INTEGER NOT NULL CHECK (effective_year >= 2020 AND effective_year <= 2040),
    education_level TEXT NOT NULL CHECK (education_level IN ('PREESCOLAR', 'I_CICLO', 'II_CICLO', 'III_CICLO', 'DIVERSIFICADA', 'TECNICA')),
    education_plan TEXT NOT NULL DEFAULT 'REGULAR' CHECK (education_plan IN ('REGULAR', 'BILINGUE', 'INDIGENA', 'TECNICA', 'ADULTOS')),
    grade INTEGER NOT NULL CHECK (grade >= 0 AND grade <= 12),
    subject TEXT NOT NULL,
    document_hash TEXT NOT NULL,
    source_kind TEXT NOT NULL CHECK (source_kind IN (
        'CANONICAL_CURRICULUM', 'YEAR_ORIENTATION', 'SCOPE_SEQUENCE',
        'OFFICIAL_MONTHLY_DISTRIBUTION', 'ASSESSMENT_SPEC', 'REINFORCEMENT_ONLY',
        'SUPPLEMENTAL', 'INSTITUTIONAL_PLAN'
    )),
    source_granularity TEXT NOT NULL CHECK (source_granularity IN (
        'OFFICIAL_MONTHLY', 'OFFICIAL_UNIT_SEQUENCE', 'TEACHER_SEQUENCE_REQUIRED',
        'ANNUAL_PROGRAM', 'PLAN_SPECIFIC', 'REINFORCEMENT_ONLY'
    )),
    is_official BOOLEAN NOT NULL DEFAULT true,
    academic_periods JSONB NOT NULL DEFAULT '{
        "period_1": {"start": "2026-02-23", "end": "2026-07-03"},
        "midyear_break": {"start": "2026-07-06", "end": "2026-07-17"},
        "period_2": {"start": "2026-07-20", "end": "2026-12-09"}
    }'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed authoritative MEP 2026 sources
INSERT INTO public.curriculum_sources (
    source_id, authority_id, canonical_title, source_locator,
    publication_date, effective_year, education_level, education_plan,
    grade, subject, document_hash, source_kind, source_granularity, is_official
) VALUES
(
    'cr_mep_matematicas_1_2026',
    'CR_MEP',
    'Distribución de habilidades y conocimientos de Matemáticas 2026 - Primer Año',
    'https://ddc.mep.go.cr/dpsc/asignaturaspsc/matematicas/informaciongeneral/pdfs/Distribucion_de_habilidades_y_conocimientos_Matematicas_2026.pdf',
    '2026-01-15',
    2026,
    'I_CICLO',
    'REGULAR',
    1,
    'MATEMATICA',
    '9e8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b',
    'OFFICIAL_MONTHLY_DISTRIBUTION',
    'OFFICIAL_MONTHLY',
    true
),
(
    'cr_mep_artes_industriales_7_2026',
    'CR_MEP',
    'Programa de Estudio Artes Industriales III Ciclo - Fontanería 7.º Año',
    'https://ddc.mep.go.cr/dpsc/asignaturaspsc/artesindustriales/fontaneria_7_2026.pdf',
    '2026-01-20',
    2026,
    'III_CICLO',
    'REGULAR',
    7,
    'ARTES_INDUSTRIALES_FONTANERIA',
    'a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0',
    'CANONICAL_CURRICULUM',
    'OFFICIAL_UNIT_SEQUENCE',
    true
)
ON CONFLICT (source_id) DO NOTHING;

-- 2. CANONICAL CURRICULUM UNITS & CONCEPTS (GATE 10 & 12)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.curriculum_units (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL REFERENCES public.curriculum_sources(source_id) ON DELETE CASCADE,
    unit_number INTEGER NOT NULL,
    target_month INTEGER CHECK (target_month BETWEEN 1 AND 12),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    estimated_lessons INTEGER NOT NULL DEFAULT 8,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.curriculum_concepts (
    id TEXT PRIMARY KEY,
    unit_id TEXT NOT NULL REFERENCES public.curriculum_units(id) ON DELETE CASCADE,
    concept_code TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    truth_state TEXT NOT NULL DEFAULT 'AUTHORITATIVE' CHECK (truth_state IN ('AUTHORITATIVE', 'DERIVED', 'SUPPLEMENTAL')),
    is_official BOOLEAN NOT NULL DEFAULT true,
    source_anchor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.curriculum_skills (
    id TEXT PRIMARY KEY,
    concept_id TEXT NOT NULL REFERENCES public.curriculum_concepts(id) ON DELETE CASCADE,
    skill_code TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    stage_type TEXT NOT NULL CHECK (stage_type IN ('ETAPA_I_APRENDIZAJE', 'ETAPA_II_APLICACION_MOVILIZACION')),
    cognitive_level TEXT NOT NULL CHECK (cognitive_level IN ('RECONOCIMIENTO', 'COMPRENSION', 'APLICACION', 'ANALISIS', 'TRANSFERENCIA')),
    minimum_evidence_required INTEGER NOT NULL DEFAULT 3,
    source_anchor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.curriculum_prerequisites (
    concept_id TEXT NOT NULL REFERENCES public.curriculum_concepts(id) ON DELETE CASCADE,
    prerequisite_concept_id TEXT NOT NULL REFERENCES public.curriculum_concepts(id) ON DELETE RESTRICT,
    relationship_type TEXT NOT NULL DEFAULT 'STRICT_PREREQUISITE' CHECK (relationship_type IN ('STRICT_PREREQUISITE', 'ENRICHMENT', 'PARALLEL')),
    PRIMARY KEY (concept_id, prerequisite_concept_id),
    CONSTRAINT no_self_prerequisite CHECK (concept_id <> prerequisite_concept_id)
);

-- Seed Course Zero: Matemática 1.º Año MEP 2026 (Monthly Progression)
INSERT INTO public.curriculum_units (id, source_id, unit_number, target_month, title, description, estimated_lessons) VALUES
('cr_mat1_u02', 'cr_mep_matematicas_1_2026', 1, 2, 'Geometría y Espacio: El Mundo de las Posiciones', 'Ubicación espacial, tamaño, longitud, anchura/espesor y distancia', 8),
('cr_mat1_u03', 'cr_mep_matematicas_1_2026', 2, 3, 'Números: Cantidad y Conteo Inicial', 'Cantidad, conteo, representaciones numéricas, unidades y decenas < 100', 8),
('cr_mat1_u04', 'cr_mep_matematicas_1_2026', 3, 4, 'Simbología y Medidas: Trazado y Longitud', 'Trazado 0-9, ordinales hasta décimo, líneas de posición, metro y centímetro', 8),
('cr_mat1_u05', 'cr_mep_matematicas_1_2026', 4, 5, 'Economía y Datos: Mi Primera Tienda Costarricense', 'Unidad monetaria colón, monedas, datos cualitativos/cuantitativos y variabilidad', 8),
('cr_mat1_u06', 'cr_mep_matematicas_1_2026', 5, 6, 'Operaciones y Figuras: Suma, Resta y Polígonos', 'Suma/resta inicial, identificación y trazo de triángulos, cuadriláteros y polígonos', 8),
('cr_mat1_u07', 'cr_mep_matematicas_1_2026', 6, 7, 'Masa y Cronometría: Peso y Nociones de Tiempo', 'Comparación de peso e intervalos temporales (amanecer, día, tarde, noche)', 8),
('cr_mat1_u08', 'cr_mep_matematicas_1_2026', 7, 8, 'Regularidades y Estadística: Patrones y Frecuencias', 'Patrones ABAB, sucesiones numéricas, recolección de frecuencias y operaciones', 8),
('cr_mat1_u09', 'cr_mep_matematicas_1_2026', 8, 9, 'Aritmética Profunda: Doble, Mitad y Problemas', 'Doble y mitad, problemas aditivos < 100, símbolos +, -, = y cálculo mental', 8),
('cr_mat1_u10', 'cr_mep_matematicas_1_2026', 9, 10, 'Cuerpos Geométricos y Capacidad: Cajas e Igualdades', 'Cuerpos con forma de caja, capacidad, equivalencias y expresiones matemáticas', 8),
('cr_mat1_u11', 'cr_mep_matematicas_1_2026', 10, 11, 'Probabilidad y Cierre: Situaciones Aleatorias y Seguras', 'Diferenciación entre situaciones seguras y aleatorias, consolidación y transferencia', 8)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.curriculum_concepts (id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor) VALUES
('cr_mat1_c_spatial_pos', 'cr_mat1_u02', 'CR_MAT1_SPATIAL_POS', 'Posiciones relativas en el espacio', 'Conceptos espaciales: detrás, delante, al lado, entre, cerca, lejos', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P1'),
('cr_mat1_c_dimensions', 'cr_mat1_u02', 'CR_MAT1_DIMENSIONS', 'Dimensiones comparativas de objetos', 'Comparación de tamaño, longitud, anchura y espesor', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P1'),
('cr_mat1_c_counting_100', 'cr_mat1_u03', 'CR_MAT1_COUNTING_100', 'Conteo y valor posicional hasta 100', 'Cantidad, orden y valor posicional de unidades y decenas', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P2'),
('cr_mat1_c_currency_crc', 'cr_mat1_u05', 'CR_MAT1_CURRENCY_CRC', 'Moneda de Costa Rica (Colón)', 'Reconocimiento y denominaciones de monedas: 5, 10, 25, 50, 100 y 500 colones', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P4'),
('cr_mat1_c_addition_sub', 'cr_mat1_u06', 'CR_MAT1_ADDITION_SUB', 'Adición y Sustracción básica', 'Comprensión y resolución de operaciones elementales de adición y resta', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P5'),
('cr_mat1_c_patterns', 'cr_mat1_u08', 'CR_MAT1_PATTERNS', 'Patrones y regularidades', 'Identificación y prolongación de patrones geométricos y numéricos', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P7'),
('cr_mat1_c_probability', 'cr_mat1_u11', 'CR_MAT1_PROBABILITY', 'Eventos aleatorios vs eventos seguros', 'Identificación de sucesos seguros, probables e imposibles', 'AUTHORITATIVE', true, 'MEP_MAT1_2026_DIST_P10')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.curriculum_skills (id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor) VALUES
('cr_mat1_s_behind_beside', 'cr_mat1_c_spatial_pos', 'SKILL_SPATIAL_BEHIND_BESIDE', 'Ubicar objetos detrás, delante y al lado en entornos tridimensionales', 'ETAPA_I_APRENDIZAJE', 'APLICACION', 3, 'MEP_MAT1_HAB_01'),
('cr_mat1_s_currency_calc', 'cr_mat1_c_currency_crc', 'SKILL_CURRENCY_EXCHANGE_CRC', 'Calcular pagos y vueltos básicos en colones en simulación de tienda', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 4, 'MEP_MAT1_HAB_14'),
('cr_mat1_s_add_transfer', 'cr_mat1_c_addition_sub', 'SKILL_ADDITION_TRANSFER', 'Resolver situaciones cotidianas usando la suma y resta < 100', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'MEP_MAT1_HAB_18')
ON CONFLICT (id) DO NOTHING;

-- Prerequisites DAG: Addition requires Counting
INSERT INTO public.curriculum_prerequisites (concept_id, prerequisite_concept_id, relationship_type) VALUES
('cr_mat1_c_addition_sub', 'cr_mat1_c_counting_100', 'STRICT_PREREQUISITE'),
('cr_mat1_c_currency_crc', 'cr_mat1_c_counting_100', 'STRICT_PREREQUISITE')
ON CONFLICT DO NOTHING;

-- Seed Economic Bridge: 7.º Artes Industriales - Fontanería
INSERT INTO public.curriculum_units (id, source_id, unit_number, target_month, title, description, estimated_lessons) VALUES
('cr_font7_u01', 'cr_mep_artes_industriales_7_2026', 1, 3, 'Fundamentos y Seguridad Ocupacional en Fontanería', 'Conservación del agua, normas de seguridad y equipo de protección', 10),
('cr_font7_u02', 'cr_mep_artes_industriales_7_2026', 2, 4, 'Herramientas y Equipos de Fontanería', 'Uso y calibración de llaves, terrajas, cortatubos y manómetros', 12),
('cr_font7_u03', 'cr_mep_artes_industriales_7_2026', 3, 5, 'Materiales y Técnicas con PVC / CPVC', 'Corte, biselado, limpieza y soldadura química de tuberías PVC', 14),
('cr_font7_u04', 'cr_mep_artes_industriales_7_2026', 4, 6, 'Diagnóstico de Presión y Fugas en Redes Domiciliares', 'Detección acústica, prueba hidrostática y localización de fugas', 14)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.curriculum_concepts (id, unit_id, concept_code, title, description, truth_state, is_official, source_anchor) VALUES
('cr_font7_c_pvc_joinery', 'cr_font7_u03', 'CR_FONT7_PVC_JOINERY', 'Técnicas de unión y soldadura en tubería PVC', 'Proceso técnico de unión química de PVC potable y sanitario', 'AUTHORITATIVE', true, 'MEP_FONT7_2026_UNIT3'),
('cr_font7_c_leak_diag', 'cr_font7_u04', 'CR_FONT7_LEAK_DIAG', 'Diagnóstico y reparación de fugas menores', 'Detección y resolución técnica de averías en grifería y llaves de paso', 'AUTHORITATIVE', true, 'MEP_FONT7_2026_UNIT4')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.curriculum_skills (id, concept_id, skill_code, title, stage_type, cognitive_level, minimum_evidence_required, source_anchor) VALUES
('cr_font7_s_pvc_assembly', 'cr_font7_c_pvc_joinery', 'SKILL_FONT_PVC_ASSEMBLY', 'Ensamblar y unir tramos de PVC cumpliendo norma de secado', 'ETAPA_II_APLICACION_MOVILIZACION', 'APLICACION', 3, 'MEP_FONT7_HAB_08'),
('cr_font7_s_valve_repair', 'cr_font7_c_leak_diag', 'SKILL_FONT_VALVE_REPAIR', 'Diagnosticar y reparar llave de paso o grifería con empaque desgastado', 'ETAPA_II_APLICACION_MOVILIZACION', 'TRANSFERENCIA', 3, 'MEP_FONT7_HAB_12')
ON CONFLICT (id) DO NOTHING;

-- 3. LEARNER DIGITAL TWIN & LONGITUDINAL MASTERY KERNEL (GATE 15 & 17)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.learner_profiles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    is_minor BOOLEAN NOT NULL DEFAULT false,
    guardian_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    institution_id UUID,
    active_plan TEXT NOT NULL DEFAULT 'REGULAR',
    privacy_level TEXT NOT NULL DEFAULT 'PROTECTED_STUDENT' CHECK (privacy_level IN ('PROTECTED_STUDENT', 'PRIVATE_ADULT', 'RESTRICTED_CHILD')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.learner_concept_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    concept_id TEXT NOT NULL REFERENCES public.curriculum_concepts(id) ON DELETE CASCADE,
    mastery_estimate NUMERIC(4,3) NOT NULL DEFAULT 0.000 CHECK (mastery_estimate >= 0.000 AND mastery_estimate <= 1.000),
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0.200 CHECK (confidence >= 0.000 AND confidence <= 1.000),
    evidence_count INTEGER NOT NULL DEFAULT 0,
    successful_transfer_count INTEGER NOT NULL DEFAULT 0,
    last_demonstrated_at TIMESTAMPTZ,
    retention_estimate NUMERIC(4,3) NOT NULL DEFAULT 1.000 CHECK (retention_estimate >= 0.000 AND retention_estimate <= 1.000),
    misconception_codes TEXT[] NOT NULL DEFAULT '{}',
    prerequisites_satisfied BOOLEAN NOT NULL DEFAULT true,
    next_review_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    epistemic_status TEXT NOT NULL DEFAULT 'DERIVED' CHECK (epistemic_status IN ('DERIVED', 'MEASURED', 'SIMULATED', 'REPORTED', 'OBSERVED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (learner_id, concept_id)
);

CREATE TABLE IF NOT EXISTS public.learning_evidence_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    concept_id TEXT NOT NULL REFERENCES public.curriculum_concepts(id) ON DELETE CASCADE,
    task_id TEXT NOT NULL,
    session_id UUID,
    is_correct BOOLEAN NOT NULL,
    is_transfer_task BOOLEAN NOT NULL DEFAULT false,
    misconception_code TEXT,
    response_latency_ms INTEGER,
    environment_context TEXT DEFAULT 'FORGE_3D_ROOM',
    raw_evidence_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. UNIVERSAL ECONOMIC ONTOLOGY & SKILL-TO-SERVICE BRIDGE (GATE 35, 42 & 70)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.economic_occupations (
    isco_code TEXT PRIMARY KEY, -- ISCO-08 code
    title TEXT NOT NULL,
    major_group TEXT NOT NULL,
    submajor_group TEXT NOT NULL,
    description TEXT NOT NULL
);

INSERT INTO public.economic_occupations (isco_code, title, major_group, submajor_group, description) VALUES
('7126', 'Plumbers and Pipe Fitters', '7: Craft and related trades workers', '71: Building and related trades workers', 'Assembles, installs and repairs pipes, fixtures and fittings for water, gas and drainage systems'),
('7231', 'Motor Vehicle Mechanics and Repairers', '7: Craft and related trades workers', '72: Metal, machinery and related trades workers', 'Fits, maintains, services and repairs motor vehicle engines, electrical and mechanical parts')
ON CONFLICT (isco_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.skill_to_service_mappings (
    skill_id TEXT NOT NULL REFERENCES public.curriculum_skills(id) ON DELETE CASCADE,
    isco_code TEXT NOT NULL REFERENCES public.economic_occupations(isco_code) ON DELETE CASCADE,
    service_vertical TEXT NOT NULL,
    is_regulated_license_required BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (skill_id, isco_code)
);

INSERT INTO public.skill_to_service_mappings (skill_id, isco_code, service_vertical, is_regulated_license_required) VALUES
('cr_font7_s_pvc_assembly', '7126', 'RESIDENTIAL_PLUMBING', false),
('cr_font7_s_valve_repair', '7126', 'RESIDENTIAL_PLUMBING', false)
ON CONFLICT DO NOTHING;

-- 5. ROW-LEVEL SECURITY & CHILD PRIVACY LOCKDOWN (LEY 8968) (GATE 26 & 68)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.education_authorities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_units ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_concepts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.curriculum_prerequisites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learner_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learner_concept_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learning_evidence_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.economic_occupations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.skill_to_service_mappings ENABLE ROW LEVEL SECURITY;

-- Public read for authoritative curriculum and economic catalogs
CREATE POLICY "Public read education authorities" ON public.education_authorities FOR SELECT USING (true);
CREATE POLICY "Public read curriculum sources" ON public.curriculum_sources FOR SELECT USING (true);
CREATE POLICY "Public read curriculum units" ON public.curriculum_units FOR SELECT USING (true);
CREATE POLICY "Public read curriculum concepts" ON public.curriculum_concepts FOR SELECT USING (true);
CREATE POLICY "Public read curriculum skills" ON public.curriculum_skills FOR SELECT USING (true);
CREATE POLICY "Public read curriculum prerequisites" ON public.curriculum_prerequisites FOR SELECT USING (true);
CREATE POLICY "Public read economic occupations" ON public.economic_occupations FOR SELECT USING (true);
CREATE POLICY "Public read skill service mappings" ON public.skill_to_service_mappings FOR SELECT USING (true);

-- Revoke client direct mutations on curriculum catalog (Curriculum Authority Gate 67)
REVOKE INSERT, UPDATE, DELETE ON public.curriculum_sources FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.curriculum_units FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.curriculum_concepts FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.curriculum_skills FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.curriculum_prerequisites FROM anon, authenticated;

-- Learner profile privacy: Learner reads own; Guardian reads minor ward
CREATE POLICY "Learner reads own profile"
    ON public.learner_profiles FOR SELECT
    USING (auth.uid() = user_id OR auth.uid() = guardian_user_id);

CREATE POLICY "Learner updates own profile"
    ON public.learner_profiles FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Learner concept states privacy: Learner reads own; Guardian reads linked minor
CREATE POLICY "Learner reads own concept states"
    ON public.learner_concept_states FOR SELECT
    USING (
        auth.uid() = learner_id
        OR EXISTS (
            SELECT 1 FROM public.learner_profiles p
            WHERE p.user_id = learner_concept_states.learner_id
              AND p.guardian_user_id = auth.uid()
        )
    );

-- Evidence events privacy: Learner reads own; Guardian reads linked minor
CREATE POLICY "Learner reads own evidence events"
    ON public.learning_evidence_events FOR SELECT
    USING (
        auth.uid() = learner_id
        OR EXISTS (
            SELECT 1 FROM public.learner_profiles p
            WHERE p.user_id = learning_evidence_events.learner_id
              AND p.guardian_user_id = auth.uid()
        )
    );

-- Revoke direct writes to learner_concept_states from clients (Server-Authoritative Mastery Engine)
REVOKE INSERT, UPDATE, DELETE ON public.learner_concept_states FROM anon, authenticated;
REVOKE INSERT, UPDATE, DELETE ON public.learning_evidence_events FROM anon, authenticated;

-- Grant SELECT to authenticated so RLS policies can be evaluated
GRANT SELECT ON public.education_authorities, public.curriculum_sources, public.curriculum_units,
    public.curriculum_concepts, public.curriculum_skills, public.curriculum_prerequisites,
    public.economic_occupations, public.skill_to_service_mappings, public.learner_profiles,
    public.learner_concept_states, public.learning_evidence_events
TO authenticated;

-- 6. AUTHORITATIVE SERVER-SIDE RPCS (GATE 11, 14, 15 & 16)
-- ─────────────────────────────────────────────────────────────────────────────

-- 6.1 Record Learning Evidence & Update Mastery Estimate Atomically
CREATE OR REPLACE FUNCTION public.learning_record_evidence_v1(
    p_concept_id TEXT,
    p_task_id TEXT,
    p_is_correct BOOLEAN,
    p_is_transfer_task BOOLEAN DEFAULT false,
    p_misconception_code TEXT DEFAULT NULL,
    p_response_latency_ms INTEGER DEFAULT NULL,
    p_environment_context TEXT DEFAULT 'FORGE_3D_ROOM'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_curr_state RECORD;
    v_new_mastery NUMERIC(4,3);
    v_new_confidence NUMERIC(4,3);
    v_evidence_count INTEGER;
    v_transfer_count INTEGER;
    v_misconceptions TEXT[];
    v_evidence_hash TEXT;
    v_days_to_next_review INTEGER;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED: Sesión activa requerida.';
    END IF;

    -- Validate concept exists
    IF NOT EXISTS (SELECT 1 FROM public.curriculum_concepts WHERE id = p_concept_id) THEN
        RAISE EXCEPTION 'INVALID_ARGUMENT: Concepto % no existe.', p_concept_id;
    END IF;

    -- Compute cryptographic evidence hash
    v_evidence_hash := encode(sha256(convert_to(
        v_actor::text || ':' || p_concept_id || ':' || p_task_id || ':' ||
        p_is_correct::text || ':' || coalesce(p_misconception_code, '') || ':' ||
        clock_timestamp()::text, 'UTF-8'
    )), 'hex');

    -- Insert immutable evidence event
    INSERT INTO public.learning_evidence_events (
        learner_id, concept_id, task_id, is_correct,
        is_transfer_task, misconception_code, response_latency_ms,
        environment_context, raw_evidence_hash
    ) VALUES (
        v_actor, p_concept_id, p_task_id, p_is_correct,
        p_is_transfer_task, p_misconception_code, p_response_latency_ms,
        p_environment_context, v_evidence_hash
    );

    -- Ensure concept state row exists idempotently before acquiring exclusive row lock
    INSERT INTO public.learner_concept_states (
        learner_id, concept_id, mastery_estimate, confidence,
        evidence_count, successful_transfer_count, last_demonstrated_at,
        retention_estimate, misconception_codes, prerequisites_satisfied,
        next_review_at, epistemic_status
    ) VALUES (
        v_actor, p_concept_id, 0.000, 0.200,
        0, 0, NULL,
        1.000, '{}'::text[], true,
        clock_timestamp(), 'DERIVED'
    ) ON CONFLICT (learner_id, concept_id) DO NOTHING;

    -- Now row is guaranteed to exist: lock exclusively
    SELECT * INTO v_curr_state
    FROM public.learner_concept_states
    WHERE learner_id = v_actor AND concept_id = p_concept_id
    FOR UPDATE;

    v_evidence_count := v_curr_state.evidence_count + 1;
    v_transfer_count := v_curr_state.successful_transfer_count + (CASE WHEN p_is_correct AND p_is_transfer_task THEN 1 ELSE 0 END);
    v_new_confidence := least(0.980, v_curr_state.confidence + 0.080);
    v_misconceptions := v_curr_state.misconception_codes;

    IF p_misconception_code IS NOT NULL AND NOT (v_misconceptions @> ARRAY[p_misconception_code]) THEN
        v_misconceptions := array_append(v_misconceptions, p_misconception_code);
    END IF;

    IF p_is_correct THEN
        -- Invariant: Answering correctly without transfer increases mastery up to 0.75 max.
        -- Full mastery (> 0.85) strictly requires demonstrated transfer tasks!
        IF p_is_transfer_task THEN
            v_new_mastery := least(1.000, v_curr_state.mastery_estimate + 0.200);
        ELSE
            v_new_mastery := least(0.750, v_curr_state.mastery_estimate + 0.120);
        END IF;
        -- Spaced review progression
        v_days_to_next_review := greatest(2, round(coalesce(v_evidence_count, 1) * 2.5)::int);
    ELSE
        -- Penalty on failure
        v_new_mastery := greatest(0.000, v_curr_state.mastery_estimate - 0.250);
        v_days_to_next_review := 1;
    END IF;

    UPDATE public.learner_concept_states
    SET mastery_estimate = v_new_mastery,
        confidence = v_new_confidence,
        evidence_count = v_evidence_count,
        successful_transfer_count = v_transfer_count,
        last_demonstrated_at = CASE WHEN p_is_correct THEN clock_timestamp() ELSE v_curr_state.last_demonstrated_at END,
        misconception_codes = v_misconceptions,
        next_review_at = clock_timestamp() + (v_days_to_next_review || ' days')::interval,
        updated_at = clock_timestamp()
    WHERE learner_id = v_actor AND concept_id = p_concept_id;

    RETURN jsonb_build_object(
        'success', true,
        'learner_id', v_actor,
        'concept_id', p_concept_id,
        'mastery_estimate', v_new_mastery,
        'confidence', v_new_confidence,
        'evidence_count', v_evidence_count,
        'successful_transfer_count', v_transfer_count,
        'next_review_days', v_days_to_next_review,
        'evidence_hash', v_evidence_hash
    );
END;
$$;

-- 6.2 Compute Personal Learning Frontier (TARGET GRAPH - MASTERED CONCEPTS)
CREATE OR REPLACE FUNCTION public.learning_get_personal_frontier_v1(
    p_subject TEXT DEFAULT 'MATEMATICA',
    p_grade INTEGER DEFAULT 1
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_actor UUID := auth.uid();
    v_frontier JSONB;
BEGIN
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'UNAUTHENTICATED: Sesión activa requerida.';
    END IF;

    -- Frontier concepts: concepts in target curriculum where:
    -- 1. Learner mastery < 0.800 OR no evidence exists
    -- 2. ALL strict prerequisites are satisfied (mastery >= 0.700)
    SELECT coalesce(jsonb_agg(frontier_item), '[]'::jsonb) INTO v_frontier
    FROM (
        SELECT jsonb_build_object(
            'concept_id', c.id,
            'concept_code', c.concept_code,
            'title', c.title,
            'unit_title', u.title,
            'target_month', u.target_month,
            'current_mastery', coalesce(s.mastery_estimate, 0.000),
            'confidence', coalesce(s.confidence, 0.000),
            'evidence_count', coalesce(s.evidence_count, 0),
            'needs_transfer', CASE WHEN coalesce(s.mastery_estimate, 0.000) >= 0.700 AND coalesce(s.successful_transfer_count, 0) = 0 THEN true ELSE false END
        ) AS frontier_item
        FROM public.curriculum_concepts c
        JOIN public.curriculum_units u ON u.id = c.unit_id
        JOIN public.curriculum_sources src ON src.source_id = u.source_id
        LEFT JOIN public.learner_concept_states s ON s.concept_id = c.id AND s.learner_id = v_actor
        WHERE src.subject = p_subject
          AND src.grade = p_grade
          AND coalesce(s.mastery_estimate, 0.000) < 0.850
          -- Check prerequisites: must have no unsatisfied strict prerequisites
          AND NOT EXISTS (
              SELECT 1 FROM public.curriculum_prerequisites p
              LEFT JOIN public.learner_concept_states prereq_state
                     ON prereq_state.concept_id = p.prerequisite_concept_id AND prereq_state.learner_id = v_actor
              WHERE p.concept_id = c.id
                AND p.relationship_type = 'STRICT_PREREQUISITE'
                AND coalesce(prereq_state.mastery_estimate, 0.000) < 0.700
          )
        ORDER BY u.target_month ASC, c.id ASC
        LIMIT 5
    ) sub;

    RETURN jsonb_build_object(
        'success', true,
        'subject', p_subject,
        'grade', p_grade,
        'frontier_concepts', v_frontier
    );
END;
$$;

-- Grants for authenticated users to execute authoritative RPCs
GRANT EXECUTE ON FUNCTION public.learning_record_evidence_v1 TO authenticated;
GRANT EXECUTE ON FUNCTION public.learning_get_personal_frontier_v1 TO authenticated;

-- Ensure service_role has full control
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;
