package com.elysium.nexus.fabric.scenes

import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import com.elysium.nexus.fabric.pairing.PairingResult
import kotlinx.coroutines.delay

/**
 * Scene definition: a sequence of coordinated multi-protocol actions
 * executed in order with configurable delays between steps.
 *
 * Example "Cinema Mode" scene:
 *   Step 1: TV Power ON (IR/IP) — delay 3s
 *   Step 2: Soundbar Power ON (IR) — delay 1s
 *   Step 3: Switch TV to HDMI 2 (IP command) — delay 0.5s
 *   Step 4: Set Volume to 40% — delay 0s
 *   Step 5: Dim living room lights to 10% (Home Assistant) — delay 0s
 *   Step 6: Launch Netflix on TV (IP command)
 */
data class Scene(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "🎬",
    val steps: List<SceneStep>,
    val isBuiltIn: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
)

data class SceneStep(
    val ordinal: Int,
    val targetDeviceId: String,
    val actionType: SceneActionType,
    val parameters: Map<String, String> = emptyMap(),
    val delayAfterMs: Long = 0L,
    val retryOnFail: Boolean = false,
    val maxRetries: Int = 2
)

enum class SceneActionType {
    POWER_ON,
    POWER_OFF,
    POWER_TOGGLE,
    VOLUME_SET,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE_TOGGLE,
    INPUT_SELECT,
    CHANNEL_SET,
    CHANNEL_UP,
    CHANNEL_DOWN,
    NAVIGATE_UP,
    NAVIGATE_DOWN,
    NAVIGATE_LEFT,
    NAVIGATE_RIGHT,
    NAVIGATE_SELECT,
    NAVIGATE_BACK,
    NAVIGATE_HOME,
    MEDIA_PLAY,
    MEDIA_PAUSE,
    MEDIA_STOP,
    MEDIA_NEXT,
    MEDIA_PREV,
    LAUNCH_APP,
    SEND_IR_CODE,
    SEND_CUSTOM_KEY,
    SET_BRIGHTNESS,
    SET_COLOR_TEMP,
    SET_LIGHT_COLOR,
    RUN_AUTOMATION
}

sealed class SceneExecutionResult {
    data class Success(val stepsCompleted: Int, val totalSteps: Int) : SceneExecutionResult()
    data class PartialFailure(val stepsCompleted: Int, val totalSteps: Int, val failedStep: Int, val error: String) : SceneExecutionResult()
    data class Error(val message: String) : SceneExecutionResult()
}

/**
 * Scene Execution Engine — executes multi-step coordinated actions
 * with timing, retry logic, and rollback-safe error handling.
 */
class SceneExecutionEngine {

    private var isExecuting = false

    suspend fun executeScene(
        scene: Scene,
        onStepStarted: (step: SceneStep) -> Unit = {},
        onStepCompleted: (step: SceneStep, success: Boolean) -> Unit = { _, _ -> },
        actionExecutor: suspend (step: SceneStep) -> Boolean
    ): SceneExecutionResult {
        if (isExecuting) return SceneExecutionResult.Error("Another scene is already executing")
        isExecuting = true

        try {
            val sortedSteps = scene.steps.sortedBy { it.ordinal }

            for ((index, step) in sortedSteps.withIndex()) {
                onStepStarted(step)

                var success = false
                var attempts = 0
                val maxAttempts = if (step.retryOnFail) step.maxRetries + 1 else 1

                while (!success && attempts < maxAttempts) {
                    attempts++
                    success = try {
                        actionExecutor(step)
                    } catch (e: Exception) {
                        false
                    }

                    if (!success && attempts < maxAttempts) {
                        delay(500L) // Brief delay between retries
                    }
                }

                onStepCompleted(step, success)

                if (!success) {
                    isExecuting = false
                    return SceneExecutionResult.PartialFailure(
                        stepsCompleted = index,
                        totalSteps = sortedSteps.size,
                        failedStep = index,
                        error = "Step ${index + 1} '${step.actionType}' failed after $attempts attempts"
                    )
                }

                // Delay between steps
                if (step.delayAfterMs > 0) {
                    delay(step.delayAfterMs)
                }
            }

            isExecuting = false
            return SceneExecutionResult.Success(sortedSteps.size, sortedSteps.size)
        } catch (e: Exception) {
            isExecuting = false
            return SceneExecutionResult.Error(e.message ?: "Unknown scene execution error")
        }
    }

    companion object {
        /**
         * Pre-built Cinema Mode scene template.
         */
        fun createCinemaModeTemplate(
            tvDeviceId: String,
            soundbarDeviceId: String? = null
        ): Scene {
            val steps = mutableListOf(
                SceneStep(0, tvDeviceId, SceneActionType.POWER_ON, delayAfterMs = 3000, retryOnFail = true),
                SceneStep(2, tvDeviceId, SceneActionType.INPUT_SELECT, mapOf("input" to "HDMI_1"), delayAfterMs = 1000),
                SceneStep(3, tvDeviceId, SceneActionType.VOLUME_SET, mapOf("level" to "35"), delayAfterMs = 500),
                SceneStep(4, tvDeviceId, SceneActionType.LAUNCH_APP, mapOf("app" to "netflix"))
            )

            soundbarDeviceId?.let {
                steps.add(1, SceneStep(1, it, SceneActionType.POWER_ON, delayAfterMs = 2000, retryOnFail = true))
            }

            return Scene(
                id = "scene_cinema_mode",
                name = "Modo Cine",
                description = "Enciende TV, Soundbar, configura HDMI 1, volumen 35%, y lanza Netflix",
                icon = "🎬",
                steps = steps,
                isBuiltIn = true
            )
        }

        /**
         * Pre-built Gaming Mode scene template.
         */
        fun createGamingModeTemplate(
            tvDeviceId: String,
            consoleDeviceId: String? = null
        ): Scene {
            return Scene(
                id = "scene_gaming_mode",
                name = "Modo Gaming",
                description = "Enciende TV, cambia a Game Mode, input HDMI 2",
                icon = "🎮",
                steps = listOf(
                    SceneStep(0, tvDeviceId, SceneActionType.POWER_ON, delayAfterMs = 3000, retryOnFail = true),
                    SceneStep(1, tvDeviceId, SceneActionType.INPUT_SELECT, mapOf("input" to "HDMI_2"), delayAfterMs = 1000),
                    SceneStep(2, tvDeviceId, SceneActionType.VOLUME_SET, mapOf("level" to "50"))
                ),
                isBuiltIn = true
            )
        }
    }
}
