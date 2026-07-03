package com.elysium.vanguard.forge.domain

/**
 * Catálogo de **planes compuestos** listos para usar: motores completos (V6/V8,
 * bóxer, inline), transmisiones, suspensiones. Cada uno es un [CompositePlan]
 * que compila a N features al llamar `instantiate()`.
 *
 * Antes: había que componer engines manualmente en código cada vez.
 * Ahora: una sola línea `engineCatalog.v8Instantiate()` produce 9 features
 * (1 block + 8 cylinders) listas para el editor.
 *
 * Los engines están pre-configurados con dimensiones plausibles para un
 * sedán compacto. Ajustar valores para prototipos más grandes.
 *
 * **Uso**:
 *   val features = engineCatalog.v8.instantiate()
 *   part.featureTree += features
 *
 * Visibilidad `internal`: solo el módulo Forge compone engines. Si en el futuro
 * se quiere exponer a otros módulos, se eleva a `public`.
 */
internal object EngineCatalog {

    // ─────────── Motores en V (V6, V8, V10, V12) ───────────

    /**
     * Motor V6: bloque + 6 pistones en patrón circular plano XZ.
     *
     * Layout: ángulo inicial en Y+ (cilindro #0 arriba). Los cilindros se
     * distribuyen cada 60° en el plano horizontal. Asume banco de cilindros
     * a 60° de la vertical — típico de V6 compacto.
     */
    val v6: CompositePlan = engineVFamily(
        name = "V6 engine block",
        cylinderCount = 6,
        cylinderRadius = 70.0,
        bankAngleDeg = 60.0
    )

    /**
     * Motor V8: bloque + 8 pistones en patrón circular.
     * Mismo layout que V6, con 8 cilindros cada 45°.
     */
    val v8: CompositePlan = engineVFamily(
        name = "V8 engine block",
        cylinderCount = 8,
        cylinderRadius = 80.0,
        bankAngleDeg = 90.0
    )

    /**
     * Motor V10: 10 cilindros cada 36°.
     */
    val v10: CompositePlan = engineVFamily(
        name = "V10 engine block",
        cylinderCount = 10,
        cylinderRadius = 90.0,
        bankAngleDeg = 72.0
    )

    /**
     * Motor V12: 12 cilindros cada 30°.
     */
    val v12: CompositePlan = engineVFamily(
        name = "V12 engine block",
        cylinderCount = 12,
        cylinderRadius = 100.0,
        bankAngleDeg = 60.0
    )

    /**
     * Motor bóxer (flat-6): 6 pistones opuestos, 3 por bancada, en plano X.
     * Layout horizontal — los cilindros se oponen 180° entre sí.
     */
    val boxer6: CompositePlan = CompositePlan(
        name = "Boxer-6 flat engine",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            CircularPatternPlan(
                preset = preset(PresetId.PISTON),
                count = 6,
                radius = 90.0,
                axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
                startAngleRad = 0.0
            )
        ),
        centerOrigin = true
    )

    // ─────────── Motores en línea (Inline-3, 4, 5, 6) ───────────

    /**
     * Motor inline-4: bloque + 4 pistones en línea sobre eje X.
     * Spacing típico: 88mm entre cilindros.
     */
    val inline4: CompositePlan = CompositePlan(
        name = "Inline-4 engine",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            LinearArrayPlan(
                preset = preset(PresetId.PISTON),
                count = 4,
                spacing = 88.0,
                axis = LinearArrayPlan.Axis.X
            )
        ),
        centerOrigin = true
    )

    val inline3: CompositePlan = CompositePlan(
        name = "Inline-3 engine",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            LinearArrayPlan(
                preset = preset(PresetId.PISTON),
                count = 3,
                spacing = 88.0,
                axis = LinearArrayPlan.Axis.X
            )
        ),
        centerOrigin = true
    )

    val inline5: CompositePlan = CompositePlan(
        name = "Inline-5 engine",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            LinearArrayPlan(
                preset = preset(PresetId.PISTON),
                count = 5,
                spacing = 88.0,
                axis = LinearArrayPlan.Axis.X
            )
        ),
        centerOrigin = true
    )

    val inline6: CompositePlan = CompositePlan(
        name = "Inline-6 engine",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            LinearArrayPlan(
                preset = preset(PresetId.PISTON),
                count = 6,
                spacing = 88.0,
                axis = LinearArrayPlan.Axis.X
            )
        ),
        centerOrigin = true
    )

    // ─────────── Conjuntos auxiliares ───────────

    /**
     * 4 ruedas con sus discos de freno: cada rueda = tire + brake disc.
     */
    val fourWheels: CompositePlan = CompositePlan(
        name = "4-wheel set",
        children = listOf(
            CircularPatternPlan(
                preset = preset(PresetId.WHEEL_HUB),
                count = 4,
                radius = 1400.0,
                axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
                startAngleRad = Math.PI / 4  // primera rueda en X+/Y+
            ),
            // Cada disco de freno cerca de su cubo, ligeramente desplazado en Y.
            CircularPatternPlan(
                preset = preset(PresetId.BRAKE_DISC),
                count = 4,
                radius = 1400.0,
                axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
                startAngleRad = Math.PI / 4
            )
        )
    )

    /**
     * Lista plana para iteración (UI, catalog screens).
     */
    val allEngines: List<CompositePlan> = listOf(
        v6, v8, v10, v12, boxer6, inline3, inline4, inline5, inline6, fourWheels
    )

    // ─────────── Helpers ───────────

    /**
     * Construye un motor en V genérico con N cilindros y radio de bancada.
     * Llamado por v6/v8/v10/v12.
     */
    private fun engineVFamily(
        name: String,
        cylinderCount: Int,
        cylinderRadius: Double,
        bankAngleDeg: Double
    ): CompositePlan {
        val startAngleRad = Math.PI / 2.0  // primera bancada apunta arriba (Y+)
        return CompositePlan(
            name = name,
            children = listOf(
                SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
                CircularPatternPlan(
                    preset = preset(PresetId.PISTON),
                    count = cylinderCount,
                    radius = cylinderRadius,
                    axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
                    startAngleRad = startAngleRad
                )
            ),
            centerOrigin = true
        )
    }
}