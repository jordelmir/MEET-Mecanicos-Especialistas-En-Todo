package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Catálogo de presets con identificadores estables (no strings).
 *
 * Cada preset tiene un [PresetId] inmutable que sirve como clave de lookup,
 * serialización y referencia estable entre módulos (engine catalog, UI, tests).
 *
 * Antes: el catálogo era una `List<FeaturePreset>` con strings como nombre.
 * Problema: para construir planes compuestos tipo V8 / inline-4, había que
 * buscar presets por `displayName`, frágil a renombres cosméticos.
 *
 * Ahora: `presetsById[id]` siempre devuelve el mismo preset — los IDs son
 * parte del contrato de dominio.
 */

/**
 * Identificadores estables del catálogo. Los nombres son en MAYÚSCULAS para
 * indicar uso programático (no decoración).
 */
internal enum class PresetId {
    // ── Genéricos (8) ──
    BOX, CYLINDER, TUBE, PLATE, SPHERE, CONE, PROFILE_L, PROFILE_U,
    // ── Motor / tren motriz (5) ──
    ENGINE_BLOCK, CYLINDER_HEAD, PISTON, CRANKSHAFT_JOURNAL, CONNECTING_ROD,
    // ── Tren de válvulas (3) ──
    INTAKE_VALVE, EXHAUST_VALVE, CAM_LOBE,
    // ── Ejes / transmisión (2) ──
    DRIVE_SHAFT, WHEEL_HUB,
    // ── Frenos (2) ──
    BRAKE_DISC, FLYWHEEL,
    // ── Hidráulica (1) ──
    MASTER_CYLINDER_BORE,
    // ── Otros (2) ──
    SPARK_PLUG_THREAD, BRAKE_PEDAL_ARM,
    // ── Suspensión (3) ──
    SHOCK_BODY, COIL_SPRING_APPROX, CONTROL_ARM,
    // ── Frenos extra (2) ──
    BRAKE_PAD, BRAKE_CALIPER,
    // ── Dirección (2) ──
    STEERING_COLUMN, TIE_ROD,
    // ── Carrocería (1) ──
    FENDER,
    // ── Eléctrico (2) ──
    BATTERY_TERMINAL, ALTERNATOR_PULLEY
}

/**
 * Preset paramétrico: tipo + nombre display + descripción corta + parámetros por defecto.
 * Solo se incluyen las primitivas soportadas por V1 del compilador geométrico.
 *
 * `@Serializable` para que pueda ser referenciado por `FeaturePlan`.
 * Visibilidad `internal`: usado por ForgePartEditorScreen y por tests del módulo.
 */
@Serializable
internal data class FeaturePreset(
    val id: PresetId,
    val type: FeatureType,
    val displayName: String,
    val shortSpec: String,
    val defaultParameters: Map<String, Double>
)

/**
 * Catálogo completo de presets. Es una `Map` para lookup determinista por ID;
 * también se expone como `List` para iteración ordenada (UI, catalog screens).
 */
internal val presetsById: Map<PresetId, FeaturePreset> = mapOf(
    // ── Genéricos (8) ──
    PresetId.BOX to FeaturePreset(
        PresetId.BOX, FeatureType.BOX, "Caja", "100×50×30",
        mapOf("length" to 100.0, "width" to 50.0, "height" to 30.0)
    ),
    PresetId.CYLINDER to FeaturePreset(
        PresetId.CYLINDER, FeatureType.CYLINDER, "Cilindro", "Ø50×100",
        mapOf("diameter" to 50.0, "height" to 100.0)
    ),
    PresetId.TUBE to FeaturePreset(
        PresetId.TUBE, FeatureType.TUBE, "Tubo", "Ø60/Ø40×100",
        mapOf("outerDiameter" to 60.0, "innerDiameter" to 40.0, "height" to 100.0)
    ),
    PresetId.PLATE to FeaturePreset(
        PresetId.PLATE, FeatureType.PLATE, "Placa", "200×100×5",
        mapOf("length" to 200.0, "width" to 100.0, "thickness" to 5.0)
    ),
    PresetId.SPHERE to FeaturePreset(
        PresetId.SPHERE, FeatureType.SPHERE, "Esfera", "Ø50",
        mapOf("diameter" to 50.0)
    ),
    PresetId.CONE to FeaturePreset(
        PresetId.CONE, FeatureType.CONE, "Cono", "Ø80→Ø20×60",
        mapOf("baseDiameter" to 80.0, "topDiameter" to 20.0, "height" to 60.0)
    ),
    PresetId.PROFILE_L to FeaturePreset(
        PresetId.PROFILE_L, FeatureType.PROFILE_L, "Perfil L", "50×50×5",
        mapOf("leg1" to 50.0, "leg2" to 50.0, "thickness" to 5.0)
    ),
    PresetId.PROFILE_U to FeaturePreset(
        PresetId.PROFILE_U, FeatureType.PROFILE_U, "Perfil U", "100×50×5",
        mapOf("width" to 100.0, "height" to 50.0, "thickness" to 5.0)
    ),

    // ── Motor / tren motriz (5) ──
    PresetId.ENGINE_BLOCK to FeaturePreset(
        PresetId.ENGINE_BLOCK, FeatureType.BOX, "Bloque de motor", "600×400×500",
        mapOf("length" to 600.0, "width" to 400.0, "height" to 500.0)
    ),
    PresetId.CYLINDER_HEAD to FeaturePreset(
        PresetId.CYLINDER_HEAD, FeatureType.BOX, "Cabeza de cilindro", "500×200×100",
        mapOf("length" to 500.0, "width" to 200.0, "height" to 100.0)
    ),
    PresetId.PISTON to FeaturePreset(
        PresetId.PISTON, FeatureType.CYLINDER, "Pistón", "Ø80×80",
        mapOf("diameter" to 80.0, "height" to 80.0)
    ),
    PresetId.CRANKSHAFT_JOURNAL to FeaturePreset(
        PresetId.CRANKSHAFT_JOURNAL, FeatureType.CYLINDER, "Muñón de cigüeñal", "Ø60×30",
        mapOf("diameter" to 60.0, "height" to 30.0)
    ),
    PresetId.CONNECTING_ROD to FeaturePreset(
        PresetId.CONNECTING_ROD, FeatureType.PROFILE_U, "Biela", "150×50×10",
        mapOf("width" to 150.0, "height" to 50.0, "thickness" to 10.0)
    ),

    // ── Tren de válvulas (3) ──
    PresetId.INTAKE_VALVE to FeaturePreset(
        PresetId.INTAKE_VALVE, FeatureType.CONE, "Válvula de admisión", "Ø30→Ø8×100",
        mapOf("baseDiameter" to 30.0, "topDiameter" to 8.0, "height" to 100.0)
    ),
    PresetId.EXHAUST_VALVE to FeaturePreset(
        PresetId.EXHAUST_VALVE, FeatureType.CONE, "Válvula de escape", "Ø35→Ø10×100",
        mapOf("baseDiameter" to 35.0, "topDiameter" to 10.0, "height" to 100.0)
    ),
    PresetId.CAM_LOBE to FeaturePreset(
        PresetId.CAM_LOBE, FeatureType.SPHERE, "Lóbulo de árbol de levas", "Ø25",
        mapOf("diameter" to 25.0)
    ),

    // ── Ejes / transmisión (2) ──
    PresetId.DRIVE_SHAFT to FeaturePreset(
        PresetId.DRIVE_SHAFT, FeatureType.CYLINDER, "Árbol de transmisión", "Ø30×600",
        mapOf("diameter" to 30.0, "height" to 600.0)
    ),
    PresetId.WHEEL_HUB to FeaturePreset(
        PresetId.WHEEL_HUB, FeatureType.TUBE, "Cubo de rueda", "Ø100/Ø60×80",
        mapOf("outerDiameter" to 100.0, "innerDiameter" to 60.0, "height" to 80.0)
    ),

    // ── Frenos (2) ──
    PresetId.BRAKE_DISC to FeaturePreset(
        PresetId.BRAKE_DISC, FeatureType.CYLINDER, "Disco de freno", "Ø280×25",
        mapOf("diameter" to 280.0, "height" to 25.0)
    ),
    PresetId.FLYWHEEL to FeaturePreset(
        PresetId.FLYWHEEL, FeatureType.PLATE, "Volante (flywheel)", "Ø300×30",
        mapOf("length" to 300.0, "width" to 300.0, "thickness" to 30.0)
    ),

    // ── Hidráulica (1) ──
    PresetId.MASTER_CYLINDER_BORE to FeaturePreset(
        PresetId.MASTER_CYLINDER_BORE, FeatureType.CYLINDER, "Bore de cilindro maestro", "Ø25×150",
        mapOf("diameter" to 25.0, "height" to 150.0)
    ),

    // ── Otros (2) ──
    PresetId.SPARK_PLUG_THREAD to FeaturePreset(
        PresetId.SPARK_PLUG_THREAD, FeatureType.CYLINDER, "Rosca de bujía", "Ø14×25",
        mapOf("diameter" to 14.0, "height" to 25.0)
    ),
    PresetId.BRAKE_PEDAL_ARM to FeaturePreset(
        PresetId.BRAKE_PEDAL_ARM, FeatureType.PROFILE_L, "Brake pedal arm", "120×30×8",
        mapOf("leg1" to 120.0, "leg2" to 30.0, "thickness" to 8.0)
    ),

    // ── Suspensión (3) ──
    PresetId.SHOCK_BODY to FeaturePreset(
        PresetId.SHOCK_BODY, FeatureType.CYLINDER, "Cuerpo de amortiguador", "Ø50×400",
        mapOf("diameter" to 50.0, "height" to 400.0)
    ),
    PresetId.COIL_SPRING_APPROX to FeaturePreset(
        PresetId.COIL_SPRING_APPROX, FeatureType.CYLINDER, "Muelle helicoidal (aprox)", "Ø100×300",
        mapOf("diameter" to 100.0, "height" to 300.0)
    ),
    PresetId.CONTROL_ARM to FeaturePreset(
        PresetId.CONTROL_ARM, FeatureType.PROFILE_L, "Brazo de control", "400×80×15",
        mapOf("leg1" to 400.0, "leg2" to 80.0, "thickness" to 15.0)
    ),

    // ── Frenos extra (2) ──
    PresetId.BRAKE_PAD to FeaturePreset(
        PresetId.BRAKE_PAD, FeatureType.PLATE, "Pastilla de freno", "150×60×15",
        mapOf("length" to 150.0, "width" to 60.0, "thickness" to 15.0)
    ),
    PresetId.BRAKE_CALIPER to FeaturePreset(
        PresetId.BRAKE_CALIPER, FeatureType.BOX, "Mordaza de freno", "200×150×100",
        mapOf("length" to 200.0, "width" to 150.0, "height" to 100.0)
    ),

    // ── Dirección (2) ──
    PresetId.STEERING_COLUMN to FeaturePreset(
        PresetId.STEERING_COLUMN, FeatureType.CYLINDER, "Columna de dirección", "Ø40×600",
        mapOf("diameter" to 40.0, "height" to 600.0)
    ),
    PresetId.TIE_ROD to FeaturePreset(
        PresetId.TIE_ROD, FeatureType.CYLINDER, "Barra de dirección", "Ø20×800",
        mapOf("diameter" to 20.0, "height" to 800.0)
    ),

    // ── Carrocería (1) ──
    PresetId.FENDER to FeaturePreset(
        PresetId.FENDER, FeatureType.PLATE, "Guardafango", "1500×800×2",
        mapOf("length" to 1500.0, "width" to 800.0, "thickness" to 2.0)
    ),

    // ── Eléctrico (2) ──
    PresetId.BATTERY_TERMINAL to FeaturePreset(
        PresetId.BATTERY_TERMINAL, FeatureType.CYLINDER, "Terminal de batería", "Ø18×30",
        mapOf("diameter" to 18.0, "height" to 30.0)
    ),
    PresetId.ALTERNATOR_PULLEY to FeaturePreset(
        PresetId.ALTERNATOR_PULLEY, FeatureType.CYLINDER, "Polea de alternador", "Ø80×25",
        mapOf("diameter" to 80.0, "height" to 25.0)
    )
)

/**
 * Lista ordenada de presets para iteración (UI, catalog screens).
 * El orden refleja agrupación lógica: genéricos → motor → transmisión →
 * frenos → hidráulica → otros → suspensión → carrocería → eléctrico.
 */
internal val featurePresets: List<FeaturePreset> = presetsById.values.toList()

/**
 * Lookup directo por ID. Lanza `NoSuchElementException` si el ID no existe.
 *
 * Preferido sobre búsqueda por `displayName` porque es estable a renombres.
 */
internal fun preset(id: PresetId): FeaturePreset =
    presetsById[id] ?: throw NoSuchElementException("PresetId $id no existe en catálogo")
