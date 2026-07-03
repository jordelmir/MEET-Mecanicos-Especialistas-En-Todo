package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Preset paramétrico: tipo + nombre display + descripción corta + parámetros por defecto.
 * Solo se incluyen las primitivas soportadas por V1 del compilador geométrico.
 *
 * `@Serializable` para que pueda ser referenciado por `FeaturePlan` (que también
 * es serializable). Visibilidad `internal`: usado por ForgePartEditorScreen y
 * por tests del módulo.
 */
@Serializable
internal data class FeaturePreset(
    val type: FeatureType,
    val displayName: String,
    val shortSpec: String,
    val defaultParameters: Map<String, Double>
)

/**
 * Catálogo de presets accesibles desde el editor.
 *
 * Se divide en dos bloques:
 *  - **GENÉRICOS** (8): primitivas básicas (caja, cilindro, tubo, etc.).
 *  - **AUTOMOTIVE** (15): piezas comunes en mecánica automotriz con dimensiones
 *    plausibles (mm) para piezas de un auto compacto / sedán medio.
 *
 * Cada preset crea un ParametricFeature con parámetros por defecto. Si el
 * FeatureType no está soportado V1, igual se agrega como MARK visual para
 * que el usuario lo edite manualmente.
 */
internal val featurePresets: List<FeaturePreset> = buildList {

    // ─────────── GENÉRICOS (8) ───────────

    add(FeaturePreset(
        FeatureType.BOX, "Caja", "100×50×30",
        mapOf("length" to 100.0, "width" to 50.0, "height" to 30.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Cilindro", "Ø50×100",
        mapOf("diameter" to 50.0, "height" to 100.0)
    ))
    add(FeaturePreset(
        FeatureType.TUBE, "Tubo", "Ø60/Ø40×100",
        mapOf("outerDiameter" to 60.0, "innerDiameter" to 40.0, "height" to 100.0)
    ))
    add(FeaturePreset(
        FeatureType.PLATE, "Placa", "200×100×5",
        mapOf("length" to 200.0, "width" to 100.0, "thickness" to 5.0)
    ))
    add(FeaturePreset(
        FeatureType.SPHERE, "Esfera", "Ø50",
        mapOf("diameter" to 50.0)
    ))
    add(FeaturePreset(
        FeatureType.CONE, "Cono", "Ø80→Ø20×60",
        mapOf("baseDiameter" to 80.0, "topDiameter" to 20.0, "height" to 60.0)
    ))
    add(FeaturePreset(
        FeatureType.PROFILE_L, "Perfil L", "50×50×5",
        mapOf("leg1" to 50.0, "leg2" to 50.0, "thickness" to 5.0)
    ))
    add(FeaturePreset(
        FeatureType.PROFILE_U, "Perfil U", "100×50×5",
        mapOf("width" to 100.0, "height" to 50.0, "thickness" to 5.0)
    ))

    // ─────────── AUTOMOTIVE (15) ───────────
    // Motor / tren motriz
    add(FeaturePreset(
        FeatureType.BOX, "Bloque de motor", "600×400×500",
        mapOf("length" to 600.0, "width" to 400.0, "height" to 500.0)
    ))
    add(FeaturePreset(
        FeatureType.BOX, "Cabeza de cilindro", "500×200×100",
        mapOf("length" to 500.0, "width" to 200.0, "height" to 100.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Pistón", "Ø80×80",
        mapOf("diameter" to 80.0, "height" to 80.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Muñón de cigüeñal", "Ø60×30",
        mapOf("diameter" to 60.0, "height" to 30.0)
    ))
    add(FeaturePreset(
        FeatureType.PROFILE_U, "Biela", "150×50×10",
        mapOf("width" to 150.0, "height" to 50.0, "thickness" to 10.0)
    ))

    // Válvulas / tren de válvulas
    add(FeaturePreset(
        FeatureType.CONE, "Válvula de admisión", "Ø30→Ø8×100",
        mapOf("baseDiameter" to 30.0, "topDiameter" to 8.0, "height" to 100.0)
    ))
    add(FeaturePreset(
        FeatureType.CONE, "Válvula de escape", "Ø35→Ø10×100",
        mapOf("baseDiameter" to 35.0, "topDiameter" to 10.0, "height" to 100.0)
    ))
    add(FeaturePreset(
        FeatureType.SPHERE, "Lóbulo de árbol de levas", "Ø25",
        mapOf("diameter" to 25.0)
    ))

    // Ejes / transmisión
    add(FeaturePreset(
        FeatureType.CYLINDER, "Árbol de transmisión", "Ø30×600",
        mapOf("diameter" to 30.0, "height" to 600.0)
    ))
    add(FeaturePreset(
        FeatureType.TUBE, "Cubo de rueda", "Ø100/Ø60×80",
        mapOf("outerDiameter" to 100.0, "innerDiameter" to 60.0, "height" to 80.0)
    ))

    // Frenos
    add(FeaturePreset(
        FeatureType.CYLINDER, "Disco de freno", "Ø280×25",
        mapOf("diameter" to 280.0, "height" to 25.0)
    ))
    add(FeaturePreset(
        FeatureType.PLATE, "Volante (flywheel)", "Ø300×30",
        mapOf("length" to 300.0, "width" to 300.0, "thickness" to 30.0)
    ))

    // Hidráulica / cilindros secundarios
    add(FeaturePreset(
        FeatureType.CYLINDER, "Bore de cilindro maestro", "Ø25×150",
        mapOf("diameter" to 25.0, "height" to 150.0)
    ))

    // Otros
    add(FeaturePreset(
        FeatureType.CYLINDER, "Rosca de bujía", "Ø14×25",
        mapOf("diameter" to 14.0, "height" to 25.0)
    ))
    add(FeaturePreset(
        FeatureType.PROFILE_L, "Brake pedal arm", "120×30×8",
        mapOf("leg1" to 120.0, "leg2" to 30.0, "thickness" to 8.0)
    ))

    // ─────────── AUTOMOTIVE · SUSPENSIÓN (3) ───────────

    add(FeaturePreset(
        FeatureType.CYLINDER, "Cuerpo de amortiguador", "Ø50×400",
        mapOf("diameter" to 50.0, "height" to 400.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Muelle helicoidal (aprox)", "Ø100×300",
        mapOf("diameter" to 100.0, "height" to 300.0)
    ))
    add(FeaturePreset(
        FeatureType.PROFILE_L, "Brazo de control", "400×80×15",
        mapOf("leg1" to 400.0, "leg2" to 80.0, "thickness" to 15.0)
    ))

    // ─────────── AUTOMOTIVE · FRENOS (2) ───────────

    add(FeaturePreset(
        FeatureType.PLATE, "Pastilla de freno", "150×60×15",
        mapOf("length" to 150.0, "width" to 60.0, "thickness" to 15.0)
    ))
    add(FeaturePreset(
        FeatureType.BOX, "Mordaza de freno", "200×150×100",
        mapOf("length" to 200.0, "width" to 150.0, "height" to 100.0)
    ))

    // ─────────── AUTOMOTIVE · DIRECCIÓN (2) ───────────

    add(FeaturePreset(
        FeatureType.CYLINDER, "Columna de dirección", "Ø40×600",
        mapOf("diameter" to 40.0, "height" to 600.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Barra de dirección", "Ø20×800",
        mapOf("diameter" to 20.0, "height" to 800.0)
    ))

    // ─────────── AUTOMOTIVE · CARROCERÍA (1) ───────────

    add(FeaturePreset(
        FeatureType.PLATE, "Guardafango", "1500×800×2",
        mapOf("length" to 1500.0, "width" to 800.0, "thickness" to 2.0)
    ))

    // ─────────── AUTOMOTIVE · ELÉCTRICO (2) ───────────

    add(FeaturePreset(
        FeatureType.CYLINDER, "Terminal de batería", "Ø18×30",
        mapOf("diameter" to 18.0, "height" to 30.0)
    ))
    add(FeaturePreset(
        FeatureType.CYLINDER, "Polea de alternador", "Ø80×25",
        mapOf("diameter" to 80.0, "height" to 25.0)
    ))
}