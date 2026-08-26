package com.elysium369.meet.core.humanity

object AutomotiveCapabilitySeed {

    val domains = listOf(
        KnowledgeDomain(
            id = "automotive.fundamentals",
            name = "Fundamentos Automotrices",
            description = "Principios esenciales de motores de combustión interna, sistemas de fluidos y seguridad en taller.",
            iconGlyph = "⚙",
        ),
        KnowledgeDomain(
            id = "automotive.electrical",
            name = "Electricidad Automotriz",
            description = "Ley de Ohm, uso correcto de multímetro, pruebas de caída de voltaje y diagnóstico de sensores.",
            iconGlyph = "⚡",
        ),
        KnowledgeDomain(
            id = "automotive.diagnostics",
            name = "Diagnóstico Clínico OBD-II",
            description = "Lectura de DTCs, interpretación de Freeze Frame, pruebas dirigidas y verificación pre/post reparación.",
            iconGlyph = "🩺",
        ),
    )

    val skills = listOf(
        Skill(
            id = "automotive.measure_voltage",
            domainId = "automotive.electrical",
            name = "Medición de Voltaje DC de Batería",
            description = "Comprobar el estado de carga y salud del acumulador de 12V con multímetro en bornes.",
            requiredKnowledgeIds = listOf("automotive.electrical.voltage", "automotive.electrical.multimeter"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            minimumEvidenceForMastery = 2,
        ),
        Skill(
            id = "automotive.scan_dtc",
            domainId = "automotive.diagnostics",
            name = "Escaneo Clínico OBD-II y Freeze Frame",
            description = "Identificar códigos de falla y parámetros del motor al momento del evento sin inventar causas.",
            requiredKnowledgeIds = listOf("automotive.diagnostics.obd2_basics"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            minimumEvidenceForMastery = 3,
        ),
        Skill(
            id = "automotive.isolate_misfire_p0301",
            domainId = "automotive.diagnostics",
            name = "Aislamiento Metódico de Misfire (P0301)",
            description = "Intercambio cruzado de bobina/bujía y verificación de compresión/inyección antes de reemplazar piezas.",
            requiredKnowledgeIds = listOf("automotive.diagnostics.misfire_fundamentals"),
            prerequisiteSkillIds = listOf("automotive.scan_dtc", "automotive.measure_voltage"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            minimumEvidenceForMastery = 3,
        ),
    )

    val missions = listOf(
        Mission(
            id = "mission.battery_test_multimeter",
            domainId = "automotive.electrical",
            title = "Diagnóstico Básico de Batería con Multímetro",
            goal = "Determinar si el acumulador de 12V tiene carga óptima en reposo (>12.6V) y durante el arranque (>9.6V).",
            requiredSkillIds = listOf("automotive.measure_voltage"),
            targetObjectTypes = listOf("battery", "multimeter"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            steps = listOf(
                MissionStep(
                    stepNumber = 1,
                    title = "Seguridad e Inspección Visual",
                    instruction = "Verifica que el motor esté apagado. Revisa que los bornes de la batería no tengan sulfatación excesiva o grietas.",
                    stepType = MissionStepType.VISUAL_INSPECTION,
                    safetyCheckNote = "Usa gafas de protección. No fumes ni provoques chispas cerca de la batería.",
                ),
                MissionStep(
                    stepNumber = 2,
                    title = "Ajuste del Multímetro en DC 20V",
                    instruction = "Gira el selector del multímetro a la escala de Voltaje Directo (20V DC). Conecta la punta negra en COM y la roja en V/Ω.",
                    stepType = MissionStepType.KNOWLEDGE_CHECK,
                ),
                MissionStep(
                    stepNumber = 3,
                    title = "Medición en Reposo",
                    instruction = "Coloca la punta roja en el borne positivo (+) y la punta negra en el borne negativo (-). Registra la lectura.",
                    stepType = MissionStepType.PHYSICAL_MEASUREMENT,
                    expectedEvidenceType = EvidenceType.MEASUREMENT,
                ),
                MissionStep(
                    stepNumber = 4,
                    title = "Interpretación de Carga",
                    instruction = "12.6V o más = 100% de carga. 12.4V = 75%. 12.2V = 50%. Menos de 12.0V = Descargada.",
                    stepType = MissionStepType.VERIFICATION,
                    expectedEvidenceType = EvidenceType.ASSESSMENT,
                ),
            ),
        ),
        Mission(
            id = "mission.misfire_isolation_p0301",
            domainId = "automotive.diagnostics",
            title = "Aislamiento Metódico de Misfire (P0301)",
            goal = "Diagnosticar la causa raíz del fallo de combustión en el cilindro 1 mediante pruebas de intercambio cruzado antes de comprar piezas.",
            requiredSkillIds = listOf("automotive.scan_dtc", "automotive.isolate_misfire_p0301"),
            targetObjectTypes = listOf("engine", "ignition_coil", "spark_plug", "obd_scanner"),
            safetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            steps = listOf(
                MissionStep(
                    stepNumber = 1,
                    title = "Revisión de Freeze Frame y Escaneo Inicial",
                    instruction = "Conecta el escáner OBD y analiza las RPM, temperatura de refrigerante y Short/Long Term Fuel Trims en el momento de la falla.",
                    stepType = MissionStepType.DIAGNOSTIC_EXECUTION,
                    expectedEvidenceType = EvidenceType.OBD_SESSION,
                ),
                MissionStep(
                    stepNumber = 2,
                    title = "Intercambio Cruzado de Bobina (Swap Test)",
                    instruction = "Mueve la bobina del cilindro 1 al cilindro 2 sin cambiar la bujía. Borra DTCs y realiza prueba de manejo.",
                    stepType = MissionStepType.PHYSICAL_MEASUREMENT,
                    safetyCheckNote = "Espera a que el motor esté frío para evitar quemaduras.",
                ),
                MissionStep(
                    stepNumber = 3,
                    title = "Re-Escaneo Post-Prueba",
                    instruction = "Si la falla migra a P0302: La bobina está defectuosa. Si la falla permanece en P0301: Inspecciona bujía, inyector o compresión.",
                    stepType = MissionStepType.VERIFICATION,
                    expectedEvidenceType = EvidenceType.DIAGNOSTIC_REPORT,
                ),
            ),
        ),
    )
}
