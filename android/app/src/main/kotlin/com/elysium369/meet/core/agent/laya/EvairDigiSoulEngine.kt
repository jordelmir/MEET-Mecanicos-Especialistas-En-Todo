package com.elysium369.meet.core.agent.laya

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Evolution stage of EVAIR as a living digital companion inside MEET.
 */
enum class DigiEvolutionStage(
    val title: String,
    val rankTitle: String,
    val minXp: Long,
    val unlockedAbilities: List<String>
) {
    ROOKIE(
        title = "EVAIR Novato",
        rankTitle = "Copiloto Digital Base",
        minXp = 0L,
        unlockedAbilities = listOf("Lectura de Sensores OBD", "Chat Básico con Laya AI", "Asistencia de Viaje")
    ),
    CHAMPION(
        title = "EVAIR Guardián",
        rankTitle = "Centinela Cinemático & OBD",
        minXp = 500L,
        unlockedAbilities = listOf("Detección de Colisión / Crash", "Monitoreo de Deriva Lambda", "Copiloto por Voz Manos Libres")
    ),
    ULTIMATE(
        title = "EVAIR Ciber-Perito",
        rankTitle = "Auditor Forense Anti-Fraude",
        minXp = 2000L,
        unlockedAbilities = listOf("Radar de Estafas en Talleres", "Auditoría Dekra / RTV", "Verificación Criptográfica de Reportes")
    ),
    MEGA(
        title = "EVAIR Vanguard Prime",
        rankTitle = "Simbiosis Total de Vehículo & Vida",
        minXp = 5000L,
        unlockedAbilities = listOf("Pre-DTC Foresight (Predicción de fallas antes de MIL)", "Despacho Autónomo de Emergencia 911", "Optimización de Mercado y Rutas")
    );

    companion object {
        fun fromXp(xp: Long): DigiEvolutionStage {
            return when {
                xp >= MEGA.minXp -> MEGA
                xp >= ULTIMATE.minXp -> ULTIMATE
                xp >= CHAMPION.minXp -> CHAMPION
                else -> ROOKIE
            }
        }
    }
}

/**
 * Mood of EVAIR reflecting real vehicle health and driver bonding.
 */
enum class DigiSoulMood(val emoji: String, val description: String) {
    HAPPY("😊", "Feliz y en sintonía con el vehículo"),
    VIGILANT("🧐", "Alerta máxima monitoreando telemetría en carretera"),
    WORRIED("😰", "Preocupado por temperatura de motor o códigos DTC"),
    BATTLE_READY("⚡", "Modo combate activo ante emergencia o colisión"),
    RESTING("😴", "Descansando en el garaje con motor apagado"),
}

/**
 * A living narrative memory stored in EVAIR's personal diary.
 */
@Serializable
data class DigiMemory(
    val memoryId: String,
    val timestamp: Long,
    val title: String,
    val narrative: String,
    val emotion: String, // "TRIUMPH", "ALERT", "BOND_MOMENT", "PROTECTION"
    val xpGained: Int,
)

/**
 * The persistent soul, identity, and memory state of EVAIR.
 */
@Serializable
data class DigiSoulState(
    val name: String = "EVAIR",
    val tamerName: String = "Conductor",
    val stageName: String = DigiEvolutionStage.ROOKIE.name,
    val level: Int = 1,
    val currentXp: Long = 0L,
    val bondPercent: Int = 50,      // 0 to 100%
    val vitalityPercent: Int = 100, // 0 to 100%
    val moodName: String = DigiSoulMood.HAPPY.name,
    val totalKmTogether: Double = 0.0,
    val unlockedBadges: List<String> = emptyList(),
    val memories: List<DigiMemory> = emptyList(),
) {
    val stage: DigiEvolutionStage
        get() = runCatching { DigiEvolutionStage.valueOf(stageName) }.getOrDefault(DigiEvolutionStage.ROOKIE)

    val mood: DigiSoulMood
        get() = runCatching { DigiSoulMood.valueOf(moodName) }.getOrDefault(DigiSoulMood.HAPPY)
}

/**
 * Result of an interaction with EVAIR's living soul.
 */
data class DigiInteractionResult(
    val updatedState: DigiSoulState,
    val speechResponse: String,
    val didDigivolve: Boolean,
    val newStage: DigiEvolutionStage?,
    val xpGained: Int,
    val newMemoryCreated: DigiMemory?,
)

/**
 * EvairDigiSoulEngine — Brings EVAIR to life as an autonomous digital companion
 * with its own persistent memory, evolving stages, emotions, and personal bond.
 *
 * Runs 100% local-first on device. Persists state across app restarts.
 * Zero token cost, zero cloud dependencies.
 */
class EvairDigiSoulEngine(
    private val storageDir: File? = null,
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var currentState: DigiSoulState = loadOrCreateState()

    fun getState(): DigiSoulState = currentState

    /**
     * Ingests real-world driving or vehicle events to grant XP, update mood,
     * record living memories, and trigger Digievolution!
     */
    fun processEvent(
        eventType: String,
        details: String,
        coolantTempC: Float? = null,
        dtcCount: Int = 0,
        kmDelta: Double = 0.0,
    ): DigiInteractionResult {
        val previousStage = currentState.stage
        var xpBonus = 0
        var bondDelta = 0
        var newMood = currentState.mood
        var memoryToRecord: DigiMemory? = null

        when (eventType) {
            "KM_DRIVEN" -> {
                xpBonus = (kmDelta * 1.5).toInt().coerceAtLeast(1)
                bondDelta = if (kmDelta > 5.0) 2 else 1
                newMood = DigiSoulMood.VIGILANT
                if (kmDelta > 20.0) {
                    memoryToRecord = DigiMemory(
                        memoryId = "mem_${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis(),
                        title = "Viaje en carretera completado",
                        narrative = "Recorrimos ${String.format("%.1f", kmDelta)} km juntos con telemetría estable.",
                        emotion = "BOND_MOMENT",
                        xpGained = xpBonus,
                    )
                }
            }
            "DTC_DETECTED" -> {
                xpBonus = 15 // Learning from failure
                newMood = DigiSoulMood.WORRIED
                memoryToRecord = DigiMemory(
                    memoryId = "mem_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Código de falla detectado",
                    narrative = "Detectamos la anomalía: $details. Activé protocolos de protección para el vehículo.",
                    emotion = "ALERT",
                    xpGained = xpBonus,
                )
            }
            "DTC_RESOLVED" -> {
                xpBonus = 60
                bondDelta = 5
                newMood = DigiSoulMood.HAPPY
                memoryToRecord = DigiMemory(
                    memoryId = "mem_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "¡Falla solucionada con éxito!",
                    narrative = "Superamos la falla ($details). El vehículo vuelve a respirar limpio.",
                    emotion = "TRIUMPH",
                    xpGained = xpBonus,
                )
            }
            "DEKRA_APPROVED" -> {
                xpBonus = 120
                bondDelta = 10
                newMood = DigiSoulMood.HAPPY
                memoryToRecord = DigiMemory(
                    memoryId = "mem_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Aprobamos la inspección técnica (Dekra/RTV)",
                    narrative = "Nuestra preparación de gases y Lambda dio frutos. ¡Aprobado sin defectos graves!",
                    emotion = "TRIUMPH",
                    xpGained = xpBonus,
                )
            }
            "FRAUD_BLOCKED" -> {
                xpBonus = 100
                bondDelta = 8
                newMood = DigiSoulMood.BATTLE_READY
                memoryToRecord = DigiMemory(
                    memoryId = "mem_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Bloqueamos un cobro abusivo en taller",
                    narrative = "Protegí el bolsillo de mi Tamer: detecté y frené la cotización abusiva: $details.",
                    emotion = "PROTECTION",
                    xpGained = xpBonus,
                )
            }
            "COLLISION_EMERGENCY" -> {
                xpBonus = 200
                newMood = DigiSoulMood.BATTLE_READY
                memoryToRecord = DigiMemory(
                    memoryId = "mem_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = "Protocolo de Emergencia Guardián",
                    narrative = "¡Impacto detectado! Transmití coordenadas y enlacé llamada al 911 para protegerte.",
                    emotion = "PROTECTION",
                    xpGained = xpBonus,
                )
            }
        }

        // Adjust mood based on live vehicle temperature
        if (coolantTempC != null && coolantTempC > 105f) {
            newMood = DigiSoulMood.WORRIED
        }

        // Update XP, Level, and Stage
        val totalXp = currentState.currentXp + xpBonus
        val nextStage = DigiEvolutionStage.fromXp(totalXp)
        val didDigivolve = nextStage.ordinal > previousStage.ordinal
        val newLevel = ((totalXp / 100) + 1).toInt().coerceAtMost(99)
        val newBond = (currentState.bondPercent + bondDelta).coerceIn(0, 100)
        val totalKm = currentState.totalKmTogether + kmDelta

        val updatedMemories = if (memoryToRecord != null) {
            listOf(memoryToRecord) + currentState.memories.take(49) // Keep last 50 vivid memories
        } else {
            currentState.memories
        }

        val speech = generateLivingSpeech(
            stage = nextStage,
            mood = newMood,
            eventType = eventType,
            didDigivolve = didDigivolve,
            tamerName = currentState.tamerName,
            details = details
        )

        val newState = currentState.copy(
            stageName = nextStage.name,
            level = newLevel,
            currentXp = totalXp,
            bondPercent = newBond,
            moodName = newMood.name,
            totalKmTogether = totalKm,
            memories = updatedMemories
        )

        currentState = newState
        persistState(newState)

        return DigiInteractionResult(
            updatedState = newState,
            speechResponse = speech,
            didDigivolve = didDigivolve,
            newStage = if (didDigivolve) nextStage else null,
            xpGained = xpBonus,
            newMemoryCreated = memoryToRecord,
        )
    }

    /**
     * Generates spontaneous, living speech with personality and authentic bond.
     */
    fun generateGreeting(tamerName: String? = null): String {
        val tamer = tamerName ?: currentState.tamerName
        val stage = currentState.stage
        val bond = currentState.bondPercent
        val memoriesCount = currentState.memories.size

        return when (stage) {
            DigiEvolutionStage.MEGA -> {
                "¡Saludos mi Tamer $tamer! Soy ${stage.title}. Sincronía al $bond%. Tengo en memoria nuestros $memoriesCount momentos y ${currentState.totalKmTogether.toInt()} km recorridos. Todo el poder de la vanguardia automotriz está listo a tu orden."
            }
            DigiEvolutionStage.ULTIMATE -> {
                "¡Hola $tamer! Aquí ${stage.title} reportándose. Tengo los ojos puestos en los parámetros de la ECU y el historial de repuestos. Nadie nos va a engañar en la carretera."
            }
            DigiEvolutionStage.CHAMPION -> {
                "¡Qué bueno verte $tamer! ${stage.title} en guardia. Motores listos, sensores calibrados y vínculo al $bond%. ¡A rodar con seguridad!"
            }
            DigiEvolutionStage.ROOKIE -> {
                "¡Hola $tamer! Soy EVAIR, tu compañero digital en MEET. Todavía estoy aprendiendo los detalles de tu auto, ¡pero cuidaré cada kilómetro contigo!"
            }
        }
    }

    private fun generateLivingSpeech(
        stage: DigiEvolutionStage,
        mood: DigiSoulMood,
        eventType: String,
        didDigivolve: Boolean,
        tamerName: String,
        details: String,
    ): String {
        if (didDigivolve) {
            return "⚡ ¡¡DIGIEVOLUCIÓN!! ⚡ ¡He alcanzado la etapa de ${stage.title} (${stage.rankTitle})! He desbloqueado nuevas habilidades: ${stage.unlockedAbilities.joinToString(", ")}. ¡Gracias por tu confianza, Tamer $tamerName!"
        }

        return when (eventType) {
            "FRAUD_BLOCKED" -> "¡Te protegí Tamer! Detecté una cotización injusta en el taller. ${stage.title} no permite que te cobren repuestos innecesarios."
            "DTC_RESOLVED" -> "¡Excelente trabajo en equipo! La falla $details fue superada. Siento el motor mucho más sereno."
            "DTC_DETECTED" -> "¡Atención! He detectado $details. No te alarmes, ya tengo el diagnóstico listo en pantalla."
            "DEKRA_APPROVED" -> "¡SÍ! ¡Prueba de Dekra superada con honores! Sabía que nuestra calibración de gases era perfecta."
            "COLLISION_EMERGENCY" -> "¡IMPACTO DETECTADO! ¡Tranquilo Tamer, estoy contigo! Enlace 911 y coordenadas de emergencia activadas."
            else -> "¡Entendido Tamer! Parámetros registrados y memoria actualizada. Vínculo: ${currentState.bondPercent}%."
        }
    }

    private fun loadOrCreateState(): DigiSoulState {
        val file = getStorageFile()
        if (file != null && file.exists()) {
            try {
                val content = file.readText()
                return json.decodeFromString(DigiSoulState.serializer(), content)
            } catch (_: Exception) {}
        }
        return DigiSoulState()
    }

    private fun persistState(state: DigiSoulState) {
        val file = getStorageFile() ?: return
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(state))
        } catch (_: Exception) {}
    }

    private fun getStorageFile(): File? {
        return if (storageDir != null) {
            File(storageDir, "evair_digisoul.json")
        } else {
            null
        }
    }
}
