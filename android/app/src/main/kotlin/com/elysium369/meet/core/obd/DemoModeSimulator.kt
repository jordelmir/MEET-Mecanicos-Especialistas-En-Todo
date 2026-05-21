package com.elysium369.meet.core.obd

/**
 * DemoModeSimulator — Genera datos OBD2 simulados para demo/entrenamiento.
 * Permite usar TODA la app sin vehículo conectado.
 * Simula escenarios: ralentí normal, aceleración, falla de motor, sobrecalentamiento.
 */
class DemoModeSimulator {

    enum class Scenario {
        IDLE_NORMAL,       // Motor saludable en ralentí
        CITY_DRIVING,      // Manejo urbano
        HIGHWAY_CRUISE,    // Autopista constante
        HARD_ACCELERATION, // Aceleración fuerte
        ENGINE_MISFIRE,    // Falla de encendido
        OVERHEATING,       // Sobrecalentamiento gradual
        RICH_MIXTURE,      // Mezcla rica (fuel trims altos)
        TURBO_BOOST        // Vehículo turbo bajo boost
    }

    private var tick = 0L
    var currentScenario = Scenario.IDLE_NORMAL

    fun generateFrame(): Map<String, Float> {
        tick++
        val t = tick * 0.1
        val noise = (Math.random() * 2 - 1).toFloat()

        return when (currentScenario) {
            Scenario.IDLE_NORMAL -> mapOf(
                "010C" to (780f + noise * 20),  // RPM
                "010D" to 0f,                    // Speed
                "0105" to (90f + noise),          // Coolant
                "0104" to (22f + noise * 2),      // Load
                "010F" to (35f + noise),          // IAT
                "0111" to (15f + noise),          // Throttle
                "0110" to (3.5f + noise * 0.3f),  // MAF
                "010B" to (35f + noise * 2),      // MAP
                "0106" to (2f + noise * 3),       // STFT1
                "0107" to (-1f + noise),          // LTFT1
                "0142" to (14.2f + noise * 0.1f), // Voltage
                "010E" to (12f + noise)           // Timing
            )
            Scenario.CITY_DRIVING -> {
                val speedCycle = (30 + 25 * Math.sin(t * 0.3)).toFloat()
                mapOf(
                    "010C" to (1200f + speedCycle * 15 + noise * 30),
                    "010D" to speedCycle.coerceAtLeast(0f),
                    "0105" to (92f + noise),
                    "0104" to (35f + speedCycle * 0.5f),
                    "010F" to (38f + noise),
                    "0111" to (20f + speedCycle * 0.4f),
                    "0110" to (8f + speedCycle * 0.2f),
                    "010B" to (45f + speedCycle * 0.3f),
                    "0106" to (1f + noise * 2),
                    "0107" to (noise * 2),
                    "0142" to (14.1f + noise * 0.05f),
                    "010E" to (18f + noise * 2)
                )
            }
            Scenario.HIGHWAY_CRUISE -> mapOf(
                "010C" to (2200f + noise * 30),
                "010D" to (110f + noise * 3),
                "0105" to (91f + noise * 0.5f),
                "0104" to (32f + noise * 2),
                "010F" to (42f + noise),
                "0111" to (28f + noise * 2),
                "0110" to (22f + noise),
                "010B" to (55f + noise * 2),
                "0106" to (noise * 2),
                "0107" to (noise),
                "0142" to (14.3f + noise * 0.05f),
                "010E" to (25f + noise)
            )
            Scenario.HARD_ACCELERATION -> {
                val rpmRamp = (3000 + (tick % 50) * 80).toFloat().coerceAtMost(6500f)
                mapOf(
                    "010C" to rpmRamp,
                    "010D" to (rpmRamp / 50f),
                    "0105" to (95f + noise),
                    "0104" to (85f + noise * 5),
                    "0111" to (95f + noise * 3),
                    "0110" to (80f + rpmRamp * 0.01f),
                    "010B" to (90f + noise * 3),
                    "0106" to (-5f + noise * 3),
                    "0107" to (-3f + noise),
                    "0142" to (13.8f + noise * 0.1f),
                    "010E" to (30f + noise * 3),
                    "010F" to (45f + noise)
                )
            }
            Scenario.ENGINE_MISFIRE -> mapOf(
                "010C" to (750f + noise * 100 + if (tick % 5 == 0L) -200f else 0f),
                "010D" to 0f,
                "0105" to (88f + noise),
                "0104" to (25f + noise * 10),
                "0111" to (15f + noise * 5),
                "0110" to (3f + noise * 1.5f),
                "0106" to (12f + noise * 8),
                "0107" to (8f + noise * 3),
                "0142" to (13.9f + noise * 0.2f),
                "010E" to (5f + noise * 5),
                "010F" to (36f + noise),
                "010B" to (38f + noise * 5)
            )
            Scenario.OVERHEATING -> {
                val temp = (85f + (tick % 200) * 0.15f).coerceAtMost(120f)
                mapOf(
                    "010C" to (800f + noise * 25),
                    "010D" to 0f,
                    "0105" to temp,
                    "0104" to (28f + (temp - 85f) * 0.5f),
                    "0111" to (15f + noise),
                    "0110" to (3.2f + noise * 0.3f),
                    "0106" to (3f + (temp - 85f) * 0.3f),
                    "0107" to (2f + noise),
                    "0142" to (14.0f + noise * 0.1f),
                    "010E" to (10f - (temp - 85f) * 0.2f),
                    "010F" to (45f + (temp - 85f) * 0.3f),
                    "010B" to (36f + noise * 2)
                )
            }
            Scenario.RICH_MIXTURE -> mapOf(
                "010C" to (780f + noise * 30),
                "010D" to 0f,
                "0105" to (90f + noise),
                "0104" to (30f + noise * 3),
                "0111" to (16f + noise * 2),
                "0110" to (5.5f + noise * 0.5f),
                "0106" to (-18f + noise * 3),
                "0107" to (-12f + noise * 2),
                "0142" to (14.1f + noise * 0.1f),
                "010E" to (8f + noise * 2),
                "010F" to (37f + noise),
                "010B" to (32f + noise * 3)
            )
            Scenario.TURBO_BOOST -> {
                val boost = (5f + (tick % 40) * 0.5f).coerceAtMost(18f)
                mapOf(
                    "010C" to (3500f + boost * 100 + noise * 40),
                    "010D" to (80f + boost * 3),
                    "0105" to (93f + noise),
                    "0104" to (60f + boost * 2),
                    "0111" to (70f + boost),
                    "0110" to (40f + boost * 3),
                    "010B" to (101.3f + boost * 6.895f),
                    "0133" to 101.3f,
                    "0106" to (-3f + noise * 3),
                    "0107" to (-2f + noise),
                    "0142" to (13.9f + noise * 0.1f),
                    "010E" to (15f - boost * 0.3f),
                    "010F" to (48f + noise)
                )
            }
        }
    }

    fun getScenarioDescription(): String = when (currentScenario) {
        Scenario.IDLE_NORMAL -> "🟢 Motor saludable en ralentí — Todos los valores normales"
        Scenario.CITY_DRIVING -> "🚗 Manejo urbano — Velocidad variable, stop-and-go"
        Scenario.HIGHWAY_CRUISE -> "🛣️ Autopista crucero — RPM y velocidad constantes"
        Scenario.HARD_ACCELERATION -> "🏎️ Aceleración fuerte — WOT, RPM al máximo"
        Scenario.ENGINE_MISFIRE -> "🔴 Falla de encendido — RPM inestable, fuel trims altos"
        Scenario.OVERHEATING -> "🌡️ Sobrecalentamiento — Temperatura subiendo gradualmente"
        Scenario.RICH_MIXTURE -> "⛽ Mezcla rica — Fuel trims negativos excesivos"
        Scenario.TURBO_BOOST -> "💨 Turbo bajo boost — MAP sobre atmosférica"
    }
}
