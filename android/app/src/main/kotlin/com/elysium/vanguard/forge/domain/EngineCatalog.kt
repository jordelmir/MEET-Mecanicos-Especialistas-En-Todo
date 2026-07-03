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

    // ─────────── Tren motriz eléctrico / híbrido ───────────

    /**
     * Powertrain eléctrico: batería (box grande) + 4 stators cilíndricos
     * (uno por rueda) + 1 inversor central (cilindro pequeño).
     *
     * Layout: batería en Y+, stators distribuidos en plano XZ, inversor en
     * centro. No centra origen — está alineado a la posición física esperada.
     */
    val electricPowertrain: CompositePlan = CompositePlan(
        name = "Electric powertrain V1",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
            // Batería grande sobre el bloque
            SingleFeaturePlan(
                preset = preset(PresetId.PLATE),
                positionOffset = Vector3Data(0.0, 250.0, 0.0)
            ),
            // 4 stators como pequeños cilindros (radial)
            CircularPatternPlan(
                preset = preset(PresetId.MASTER_CYLINDER_BORE),
                count = 4,
                radius = 700.0,
                axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
                startAngleRad = Math.PI / 4
            ),
            // Inversor central (cilindro pequeño)
            SingleFeaturePlan(
                preset = preset(PresetId.SPARK_PLUG_THREAD),
                positionOffset = Vector3Data(0.0, 100.0, 0.0)
            )
        )
    )

    /**
     * Tren motriz híbrido: motor V8 + 1 motor eléctrico (drive motor) +
     * pack de baterías.
     *
     * Reusa el V8 del catálogo como sub-composición. El drive motor es un
     * cilindro grande sobre el eje Y, alineado con el block.
     */
    val hybridV8: CompositePlan = CompositePlan(
        name = "Hybrid V8 powertrain",
        children = listOf(
            v8,
            SingleFeaturePlan(
                preset = preset(PresetId.DRIVE_SHAFT),
                positionOffset = Vector3Data(0.0, 200.0, 0.0)
            )
        ),
        centerOrigin = true
    )

    // ─────────── Transmisión ───────────

    /**
     * Transmisión manual 5-velocidades:
     *  - 1 input shaft (cilindro longitudinal)
     *  - 1 output shaft (cilindro paralelo)
     *  - 5 engranajes en input shaft (cilindros pequeños equiespaciados)
     *  - 5 engranajes en output shaft (cilindros pequeños equiespaciados)
     */
    val manualTransmission5spd: CompositePlan = CompositePlan(
        name = "Manual 5-speed transmission",
        children = listOf(
            // Input shaft
            SingleFeaturePlan(
                preset = preset(PresetId.DRIVE_SHAFT),
                positionOffset = Vector3Data(-150.0, 0.0, 0.0)
            ),
            // Output shaft
            SingleFeaturePlan(
                preset = preset(PresetId.DRIVE_SHAFT),
                positionOffset = Vector3Data(150.0, 0.0, 0.0)
            ),
            // 5 gears en input shaft a lo largo de X
            LinearArrayPlan(
                preset = preset(PresetId.CYLINDER),
                count = 5,
                spacing = 30.0,
                axis = LinearArrayPlan.Axis.X
            ),
            // 5 gears en output shaft a lo largo de X
            LinearArrayPlan(
                preset = preset(PresetId.CYLINDER),
                count = 5,
                spacing = 35.0,
                axis = LinearArrayPlan.Axis.X
            )
        )
    )

    // ─────────── Suspensión ───────────

    /**
     * Esquina de suspensión McPherson:
     *  - 1 shock body (cilindro vertical)
     *  - 1 muelle helicoidal (cilindro coaxial)
     *  - 1 brazo de control (perfil L)
     *  - 1 cubo de rueda (tubo)
     *
     * Útil para análisis cinemático de suspensión.
     */
    val suspensionCorner: CompositePlan = CompositePlan(
        name = "Suspension corner (McPherson)",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.SHOCK_BODY)),
            SingleFeaturePlan(
                preset = preset(PresetId.COIL_SPRING_APPROX),
                positionOffset = Vector3Data(0.0, 50.0, 0.0)
            ),
            SingleFeaturePlan(
                preset = preset(PresetId.CONTROL_ARM),
                positionOffset = Vector3Data(0.0, -100.0, 0.0)
            ),
            SingleFeaturePlan(
                preset = preset(PresetId.WHEEL_HUB),
                positionOffset = Vector3Data(0.0, -200.0, 0.0)
            )
        )
    )

    // ─────────── Frenos ───────────

    /**
     * Conjunto de freno de disco:
     *  - 1 disco (cylinder thin)
     *  - 1 mordaza (box)
     *  - 2 pastillas (plates)
     */
    val brakeAssembly: CompositePlan = CompositePlan(
        name = "Disc brake assembly",
        children = listOf(
            SingleFeaturePlan(preset(PresetId.BRAKE_DISC)),
            // Mordaza sobre el disco
            SingleFeaturePlan(
                preset = preset(PresetId.BRAKE_CALIPER),
                positionOffset = Vector3Data(0.0, 100.0, 0.0)
            ),
            // 2 pastillas en lados opuestos de la mordaza
            LinearArrayPlan(
                preset = preset(PresetId.BRAKE_PAD),
                count = 2,
                spacing = 20.0,
                axis = LinearArrayPlan.Axis.Z
            )
        )
    )

    /**
     * Lista plana para iteración (UI, catalog screens).
     */
    val allEngines: List<CompositePlan> = listOf(
        v6, v8, v10, v12, boxer6,
        inline3, inline4, inline5, inline6,
        fourWheels,
        electricPowertrain, hybridV8,
        manualTransmission5spd,
        suspensionCorner, brakeAssembly
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