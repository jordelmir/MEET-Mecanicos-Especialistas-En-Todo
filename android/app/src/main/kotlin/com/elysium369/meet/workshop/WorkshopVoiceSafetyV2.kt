package com.elysium369.meet.workshop

enum class WorkshopVoiceCommandType(val isDestructive: Boolean) {
    READ_RPM(isDestructive = false),
    READ_DTC(isDestructive = false),
    READ_COOLANT_TEMP(isDestructive = false),
    OPEN_DIAGNOSTICS(isDestructive = false),
    CLEAR_DTC(isDestructive = true),
    ACTUATOR_TEST(isDestructive = true),
    ECU_WRITE(isDestructive = true),
}

data class VoiceExecutionPreconditions(
    val vehicleSpeedKmh: Float,
    val engineRpm: Float,
    val isIgnitionOn: Boolean,
    val isEngineRunning: Boolean,
)

sealed interface VoiceCommandAuthorization {
    data class Authorized(val command: WorkshopVoiceCommandType) : VoiceCommandAuthorization
    data class Blocked(val command: WorkshopVoiceCommandType, val violationReason: String) : VoiceCommandAuthorization
}

/**
 * WorkshopVoiceSafetyV2 — Enforces strict hardware safety interlocks for voice commands.
 * Destructive active diagnostic commands can NEVER execute from voice intent alone.
 */
object WorkshopVoiceSafetyV2 {

    fun evaluateAuthorization(
        command: WorkshopVoiceCommandType,
        preconditions: VoiceExecutionPreconditions,
        voiceIntentRecognized: Boolean,
        onScreenUserConfirmed: Boolean,
    ): VoiceCommandAuthorization {
        if (!voiceIntentRecognized) {
            return VoiceCommandAuthorization.Blocked(command, "Voice recognition confidence insufficient")
        }

        // Safe Read-Only Commands
        if (!command.isDestructive) {
            return VoiceCommandAuthorization.Authorized(command)
        }

        // Destructive Commands Gate
        if (!onScreenUserConfirmed) {
            return VoiceCommandAuthorization.Blocked(
                command,
                "Destructive command '${command.name}' requires explicit on-screen visual confirmation"
            )
        }

        // Vehicle Safety Interlock: Vehicle must be stationary
        if (preconditions.vehicleSpeedKmh > 0.5f) {
            return VoiceCommandAuthorization.Blocked(
                command,
                "Safety interlock violated: Vehicle is moving (${preconditions.vehicleSpeedKmh} km/h)"
            )
        }

        // For CLEAR_DTC or ECU_WRITE: Engine must be OFF with Ignition ON
        if (command == WorkshopVoiceCommandType.CLEAR_DTC || command == WorkshopVoiceCommandType.ECU_WRITE) {
            if (preconditions.isEngineRunning || preconditions.engineRpm > 50f) {
                return VoiceCommandAuthorization.Blocked(
                    command,
                    "Safety interlock violated: Engine must be OFF (RPM=0) for ${command.name}"
                )
            }
            if (!preconditions.isIgnitionOn) {
                return VoiceCommandAuthorization.Blocked(
                    command,
                    "Safety interlock violated: Ignition must be ON (Key ON / Engine OFF)"
                )
            }
        }

        return VoiceCommandAuthorization.Authorized(command)
    }

    fun sanitizeVoiceTranscriptForLogging(transcript: String): String {
        // Redacts personal names, locations, phone numbers, or free-form text from production logs
        return "VOICE_INTENT_REDACTED_LEN_${transcript.length}"
    }
}
